package com.aicsp.user.service.impl;

import com.aicsp.common.util.DistributedIdUtils;
import com.aicsp.user.dto.resource.ApiResourceDTO;
import com.aicsp.user.dto.resource.ResourceSyncResponse;
import com.aicsp.user.dto.resource.ResourceTreeNode;
import com.aicsp.user.entity.ApiResource;
import com.aicsp.user.mapper.ApiResourceMapper;
import com.aicsp.user.service.ResourceService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceServiceImpl implements ResourceService {
    private static final Pattern CLASS_MAPPING = Pattern.compile("@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\\s*(?:\\(([^)]*)\\))?");
    private static final Pattern METHOD = Pattern.compile("(?s)(/\\*\\*.*?\\*/)?\\s*@((?:Get|Post|Put|Delete|Patch|Request)Mapping)\\s*(?:\\(([^)]*)\\))?[\\s\\S]*?public\\s+[^=;]+?\\s+(\\w+)\\s*\\(");
    private final ApiResourceMapper mapper;
    private final Path projectRoot;

    public ResourceServiceImpl(ApiResourceMapper mapper, @Value("${aicsp.resource.project-root:..}") String projectRoot) {
        this.mapper = mapper;
        this.projectRoot = Path.of(projectRoot).toAbsolutePath().normalize();
    }

    @Override
    public List<ApiResourceDTO> listResources() {
        return mapper.selectAll().stream().map(this::toDto).toList();
    }

    @Override
    public List<ResourceTreeNode> tree() {
        Map<String, ResourceTreeNode> services = new LinkedHashMap<>();
        for (ApiResourceDTO dto : listResources()) {
            ResourceTreeNode service = services.computeIfAbsent(dto.getServiceName(), key -> new ResourceTreeNode(key, key, "service"));
            String controllerId = dto.getServiceName() + ":" + dto.getControllerPath();
            ResourceTreeNode controller = service.getChildren().stream().filter(node -> node.getId().equals(controllerId)).findFirst().orElseGet(() -> {
                ResourceTreeNode node = new ResourceTreeNode(controllerId, dto.getControllerPath(), "controller");
                service.getChildren().add(node);
                return node;
            });
            ResourceTreeNode api = new ResourceTreeNode(String.valueOf(dto.getId()), dto.getHttpMethod() + " " + dto.getPath() + " · " + dto.getMethodName(), "api");
            api.setResource(dto);
            controller.getChildren().add(api);
        }
        return new ArrayList<>(services.values());
    }

    @Override
    @Transactional
    public ResourceSyncResponse sync() {
        List<ApiResource> scanned = scanSources();
        int inserted = 0;
        int updated = 0;
        for (ApiResource resource : scanned) {
            ApiResource exists = mapper.selectByCode(resource.getResourceCode());
            if (exists == null) {
                resource.setId(DistributedIdUtils.nextId());
                mapper.insert(resource);
                inserted++;
            } else {
                mapper.update(resource);
                updated++;
            }
        }
        return new ResourceSyncResponse(scanned.size(), inserted, updated);
    }

    @Scheduled(fixedDelayString = "${aicsp.resource.sync-interval-ms:3600000}", initialDelayString = "${aicsp.resource.sync-initial-delay-ms:60000}")
    public void scheduledSync() {
        sync();
    }

    @Override
    @Transactional
    public void assignRoleResources(Long roleId, List<Long> resourceIds) {
        mapper.deleteRoleResources(roleId);
        if (resourceIds != null) {
            resourceIds.forEach(resourceId -> mapper.insertRoleResource(DistributedIdUtils.nextId(), roleId, resourceId));
        }
    }

    @Override
    public List<Long> roleResourceIds(Long roleId) {
        return mapper.selectResourceIdsByRoleId(roleId);
    }

    private List<ApiResource> scanSources() {
        List<ApiResource> resources = new ArrayList<>();
        try (var paths = Files.walk(projectRoot)) {
            paths.filter(path -> path.toString().endsWith("Controller.java"))
                    .filter(path -> path.toString().contains("src\\main\\java") || path.toString().contains("src/main/java"))
                    .forEach(path -> scanController(path, resources));
        } catch (IOException e) {
            throw new IllegalStateException("资源扫描失败", e);
        }
        return resources;
    }

    private void scanController(Path path, List<ApiResource> resources) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            String serviceName = resolveServiceName(path);
            String controllerPath = resolveControllerPath(path);
            String controllerName = path.getFileName().toString().replace(".java", "");
            String basePath = firstPath(source.substring(0, Math.min(source.length(), source.indexOf("public class") > 0 ? source.indexOf("public class") : source.length())));
            Matcher matcher = METHOD.matcher(source);
            while (matcher.find()) {
                String javadoc = cleanJavadoc(matcher.group(1));
                String mapping = matcher.group(2);
                String args = matcher.group(3);
                String methodName = matcher.group(4);
                String httpMethod = httpMethod(mapping, args);
                String apiPath = normalize(basePath, firstPath(args));
                ApiResource resource = new ApiResource();
                resource.setResourceCode(serviceName + ":" + httpMethod + ":" + apiPath);
                resource.setServiceName(serviceName);
                resource.setControllerPath(controllerPath);
                resource.setControllerName(controllerName);
                resource.setHttpMethod(httpMethod);
                resource.setPath(apiPath);
                resource.setMethodName(methodName);
                resource.setDescription(javadoc.isBlank() ? methodName : javadoc);
                resource.setEnabled(true);
                resources.add(resource);
            }
        } catch (Exception ignored) {
        }
    }

    private String resolveServiceName(Path path) {
        for (Path part : projectRoot.relativize(path)) {
            if (part.toString().endsWith("-service")) return part.toString();
        }
        return "unknown-service";
    }

    private String resolveControllerPath(Path path) {
        String value = path.toString().replace('\\', '/');
        int index = value.indexOf("src/main/java/");
        return index < 0 ? path.getFileName().toString() : value.substring(index + "src/main/java/".length());
    }

    private String firstPath(String args) {
        if (args == null) return "";
        Matcher matcher = Pattern.compile("\"([^\"]*)\"").matcher(args);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String httpMethod(String mapping, String args) {
        if (!"RequestMapping".equals(mapping)) return mapping.replace("Mapping", "").toUpperCase();
        if (args != null && args.contains("RequestMethod.POST")) return "POST";
        if (args != null && args.contains("RequestMethod.PUT")) return "PUT";
        if (args != null && args.contains("RequestMethod.DELETE")) return "DELETE";
        if (args != null && args.contains("RequestMethod.PATCH")) return "PATCH";
        return "GET";
    }

    private String normalize(String base, String path) {
        String merged = ("/" + base + "/" + path).replaceAll("/++", "/");
        return merged.length() > 1 && merged.endsWith("/") ? merged.substring(0, merged.length() - 1) : merged;
    }

    private String cleanJavadoc(String javadoc) {
        if (javadoc == null) return "";
        return javadoc.replaceAll("(?s)/\\*\\*|\\*/", "").replaceAll("(?m)^\\s*\\* ?", "").replaceAll("@.*", "").trim();
    }

    private ApiResourceDTO toDto(ApiResource resource) {
        return ApiResourceDTO.builder().id(resource.getId()).resourceCode(resource.getResourceCode()).serviceName(resource.getServiceName()).controllerPath(resource.getControllerPath()).controllerName(resource.getControllerName()).httpMethod(resource.getHttpMethod()).path(resource.getPath()).methodName(resource.getMethodName()).description(resource.getDescription()).enabled(resource.getEnabled()).build();
    }
}

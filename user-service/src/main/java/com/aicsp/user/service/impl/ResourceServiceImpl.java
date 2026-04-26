package com.aicsp.user.service.impl;

import com.aicsp.common.util.DistributedIdUtils;
import com.aicsp.user.dto.resource.ApiResourceDTO;
import com.aicsp.user.dto.resource.ResourceSyncResponse;
import com.aicsp.user.dto.resource.ResourceTreeNode;
import com.aicsp.user.entity.ApiResource;
import com.aicsp.user.entity.Role;
import com.aicsp.user.mapper.ApiResourceMapper;
import com.aicsp.user.mapper.RoleMapper;
import com.aicsp.user.service.ResourceService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceServiceImpl implements ResourceService {
    private static final Pattern METHOD = Pattern.compile("(?s)(/\\*\\*.*?\\*/)?\\s*@((?:Get|Post|Put|Delete|Patch|Request)Mapping)\\s*(?:\\(([^)]*)\\))?\\s*(?:@\\w+(?:\\([^)]*\\))?\\s*)*public\\s+(.+?)\\s+(\\w+)\\s*\\((.*?)\\)\\s*\\{");
    private static final Pattern IMPORT = Pattern.compile("(?m)^\\s*import\\s+([\\w.]+);");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+);");
    private static final Pattern FIELD = Pattern.compile("(?m)^\\s*(?:@[\\w.]+(?:\\([^)]*\\))?\\s*)*(?:private|public|protected)\\s+(?!static\\b)(?!final\\b)([^=;]+?)\\s+(\\w+)\\s*(?:=.*?)?;");

    private final ApiResourceMapper mapper;
    private final RoleMapper roleMapper;
    private final Path projectRoot;

    public ResourceServiceImpl(ApiResourceMapper mapper, RoleMapper roleMapper, @Value("${aicsp.resource.project-root:..}") String projectRoot) {
        this.mapper = mapper;
        this.roleMapper = roleMapper;
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
            ResourceTreeNode parent = service;
            String packagePath = "";
            for (String part : packageParts(dto.getControllerPath())) {
                packagePath = packagePath.isBlank() ? part : packagePath + "/" + part;
                parent = child(parent, "package:" + dto.getServiceName() + ":" + packagePath, part, "package");
            }
            ResourceTreeNode controller = child(parent, "controller:" + dto.getServiceName() + ":" + dto.getControllerPath(), controllerLabel(dto.getControllerPath()), "controller");
            ResourceTreeNode api = new ResourceTreeNode(String.valueOf(dto.getId()), apiLabel(dto), "api");
            api.setResource(dto);
            controller.getChildren().add(api);
        }
        return new ArrayList<>(services.values());
    }

    private ResourceTreeNode child(ResourceTreeNode parent, String id, String label, String type) {
        return parent.getChildren().stream()
                .filter(node -> node.getId().equals(id))
                .findFirst()
                .orElseGet(() -> {
                    ResourceTreeNode node = new ResourceTreeNode(id, label, type);
                    parent.getChildren().add(node);
                    return node;
                });
    }

    private List<String> packageParts(String controllerPath) {
        if (controllerPath == null || controllerPath.isBlank()) {
            return List.of();
        }
        String normalized = controllerPath.replace('\\', '/');
        int fileIndex = normalized.lastIndexOf('/');
        if (fileIndex <= 0) {
            return List.of();
        }
        return List.of(normalized.substring(0, fileIndex).split("/"));
    }

    private String controllerLabel(String controllerPath) {
        if (controllerPath == null || controllerPath.isBlank()) {
            return "Controller";
        }
        String normalized = controllerPath.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1);
        return filename.endsWith(".java") ? filename.substring(0, filename.length() - ".java".length()) : filename;
    }

    private String apiLabel(ApiResourceDTO dto) {
        return dto.getPath() == null || dto.getPath().isBlank() ? dto.getMethodName() : dto.getPath();
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

    @Override
    @Transactional
    public void updateResource(Long id, ApiResourceDTO request) {
        ApiResource resource = mapper.selectById(id);
        if (resource == null) {
            throw new IllegalArgumentException("资源不存在");
        }
        if (request.getEnabled() != null) {
            mapper.updateEnabled(id, request.getEnabled());
        }
    }

    @Scheduled(fixedDelayString = "${aicsp.resource.sync-interval-ms:3600000}", initialDelayString = "${aicsp.resource.sync-initial-delay-ms:60000}")
    public void scheduledSync() {
        sync();
    }

    @Override
    @Transactional
    public void assignRoleResources(Long roleId, List<Long> resourceIds) {
        Role role = roleMapper.selectById(roleId);
        if (role == null || role.getEnabled() == null || !role.getEnabled()) {
            throw new IllegalArgumentException("角色不存在或已停用");
        }
        mapper.deleteRoleResources(roleId);
        if (resourceIds != null) {
            resourceIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .filter(resourceId -> {
                        ApiResource resource = mapper.selectById(resourceId);
                        return resource != null && Boolean.TRUE.equals(resource.getEnabled());
                    })
                    .forEach(resourceId -> mapper.insertRoleResource(DistributedIdUtils.nextId(), roleId, resourceId));
        }
    }

    @Override
    public List<Long> roleResourceIds(Long roleId) {
        Role role = roleMapper.selectById(roleId);
        if (role != null && "SUPER_ADMIN".equals(role.getRoleCode())) {
            return mapper.selectAll().stream().filter(resource -> Boolean.TRUE.equals(resource.getEnabled())).map(ApiResource::getId).toList();
        }
        return mapper.selectResourceIdsByRoleId(roleId);
    }

    private List<ApiResource> scanSources() {
        List<ApiResource> resources = new ArrayList<>();
        Map<String, Path> classIndex = buildClassIndex();
        if (!Files.isDirectory(projectRoot)) {
            return resources;
        }
        try (var paths = Files.walk(projectRoot)) {
            paths.filter(path -> path.toString().endsWith("Controller.java"))
                    .filter(path -> path.toString().contains("src\\main\\java") || path.toString().contains("src/main/java"))
                    .forEach(path -> scanController(path, classIndex, resources));
        } catch (IOException ignored) {
            return resources;
        }
        return resources;
    }

    private Map<String, Path> buildClassIndex() {
        Map<String, Path> index = new LinkedHashMap<>();
        if (!Files.isDirectory(projectRoot)) {
            return index;
        }
        try (var paths = Files.walk(projectRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("src\\main\\java") || path.toString().contains("src/main/java"))
                    .forEach(path -> index.put(path.getFileName().toString().replace(".java", ""), path));
        } catch (IOException ignored) {
            return index;
        }
        return index;
    }

    private void scanController(Path path, Map<String, Path> classIndex, List<ApiResource> resources) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            String serviceName = resolveServiceName(path);
            String controllerPath = resolveControllerPath(path);
            String controllerName = path.getFileName().toString().replace(".java", "");
            String packageName = packageName(source);
            Map<String, String> imports = imports(source);
            String baseSource = source.substring(0, Math.min(source.length(), source.indexOf("public class") > 0 ? source.indexOf("public class") : source.length()));
            String basePath = firstPath(baseSource);
            Matcher matcher = METHOD.matcher(source);
            while (matcher.find()) {
                String mapping = matcher.group(2);
                String mappingArgs = matcher.group(3);
                String returnType = normalizeType(matcher.group(4));
                String methodName = matcher.group(5);
                String parameterSource = matcher.group(6);
                String httpMethod = httpMethod(mapping, mappingArgs);
                String apiPath = normalize(basePath, firstPath(mappingArgs));

                List<MethodParameter> parameters = parseParameters(parameterSource, imports, packageName);
                ApiResource resource = new ApiResource();
                resource.setResourceCode(serviceName + ":" + httpMethod + ":" + apiPath);
                resource.setServiceName(serviceName);
                resource.setControllerPath(controllerPath);
                resource.setControllerName(controllerName);
                resource.setHttpMethod(httpMethod);
                resource.setPath(apiPath);
                resource.setMethodName(methodName);
                resource.setDescription(description(matcher.group(1), methodName, httpMethod, apiPath));
                resource.setRequestExample(json(buildRequestExample(parameters, apiPath, classIndex, new LinkedHashSet<>())));
                resource.setResponseExample(json(buildResponseExample(returnType, imports, packageName, classIndex, new LinkedHashSet<>())));
                resource.setEnabled(true);
                resources.add(resource);
            }
        } catch (Exception ignored) {
            // 单个 Controller 解析失败不能影响其他资源同步和业务接口运行。
        }
    }

    private List<MethodParameter> parseParameters(String parameterSource, Map<String, String> imports, String packageName) {
        if (parameterSource == null || parameterSource.isBlank()) {
            return List.of();
        }
        List<MethodParameter> parameters = new ArrayList<>();
        for (String part : splitTopLevel(parameterSource, ',')) {
            String value = part.trim();
            if (value.isBlank()) {
                continue;
            }
            String annotation = parameterAnnotation(value);
            String cleaned = value.replaceAll("@[\\w.]+(?:\\([^)]*\\))?\\s*", "").replace("final ", "").trim();
            int splitIndex = cleaned.lastIndexOf(' ');
            if (splitIndex <= 0) {
                continue;
            }
            String type = normalizeType(cleaned.substring(0, splitIndex));
            String name = cleaned.substring(splitIndex + 1).trim();
            parameters.add(new MethodParameter(type, name, annotationName(value, name), annotation, resolveType(type, imports, packageName)));
        }
        return parameters;
    }

    private String parameterAnnotation(String value) {
        if (value.contains("@RequestBody")) return "body";
        if (value.contains("@ModelAttribute")) return "body";
        if (value.contains("@RequestPart")) return "formData";
        if (value.contains("@PathVariable")) return "path";
        if (value.contains("@RequestParam")) return "query";
        if (value.contains("@RequestHeader")) return "headers";
        return "runtime";
    }

    private String annotationName(String value, String fallback) {
        Matcher direct = Pattern.compile("@[\\w.]+\\(\\s*\"([^\"]+)\"").matcher(value);
        if (direct.find()) {
            return direct.group(1);
        }
        Matcher named = Pattern.compile("(?:value|name)\\s*=\\s*\"([^\"]+)\"").matcher(value);
        return named.find() ? named.group(1) : fallback;
    }

    private Map<String, Object> buildRequestExample(List<MethodParameter> parameters, String apiPath, Map<String, Path> classIndex, Set<String> visiting) {
        Map<String, Object> example = new LinkedHashMap<>();
        Map<String, Object> path = new LinkedHashMap<>();
        Map<String, Object> query = new LinkedHashMap<>();
        Map<String, Object> headers = new LinkedHashMap<>();
        Map<String, Object> formData = new LinkedHashMap<>();
        Object body = null;

        for (String pathName : pathVariables(apiPath)) {
            path.put(pathName, sampleValue("String", pathName, classIndex, visiting));
        }
        for (MethodParameter parameter : parameters) {
            Object sample = sampleValue(parameter.qualifiedType(), parameter.name(), classIndex, visiting);
            switch (parameter.annotation()) {
                case "path" -> path.put(parameter.requestName(), sample);
                case "query" -> query.put(parameter.requestName(), sample);
                case "headers" -> headers.put(parameter.requestName(), sample);
                case "formData" -> formData.put(parameter.requestName(), sample);
                case "body" -> body = sample;
                default -> {
                    if (!isRuntimeOnly(parameter.type())) {
                        query.put(parameter.requestName(), sample);
                    }
                }
            }
        }

        if (!path.isEmpty()) example.put("path", path);
        if (!query.isEmpty()) example.put("query", query);
        if (!headers.isEmpty()) example.put("headers", headers);
        if (!formData.isEmpty()) example.put("formData", formData);
        if (body != null) example.put("body", body);
        return example;
    }

    private Object buildResponseExample(String returnType, Map<String, String> imports, String packageName, Map<String, Path> classIndex, Set<String> visiting) {
        return sampleValue(resolveType(returnType, imports, packageName), "data", classIndex, visiting);
    }

    private Object sampleValue(String type, String name, Map<String, Path> classIndex, Set<String> visiting) {
        String normalized = normalizeType(type);
        String simple = simpleType(normalized);
        if (normalized.equals("?")) return null;
        if (normalized.equals("void") || normalized.equals("Void")) return null;
        if (isString(simple)) return sampleString(name);
        if (isInteger(simple)) return 1;
        if (isLong(simple)) return 1001L;
        if (isDecimal(simple)) return 1.00;
        if (isBoolean(simple)) return true;
        if (isDateTime(simple)) return OffsetDateTime.now().toString();
        if (simple.equals("MultipartFile") || simple.equals("File") || simple.equals("Part")) return "<binary-file>";
        if (simple.equals("Map")) return Map.of("key", "value");
        if (simple.equals("Object")) return Map.of();
        if (simple.equals("ServerHttpRequest") || simple.equals("HttpServletRequest")) return Map.of();
        if (simple.equals("ServerSentEvent")) return Map.of("event", "message", "data", sampleValue(firstGeneric(normalized), "data", classIndex, visiting));
        if (simple.equals("Mono") || simple.equals("Flux")) return sampleValue(firstGeneric(normalized), name, classIndex, visiting);
        if (simple.equals("List") || simple.equals("Set") || simple.equals("Collection")) return List.of(sampleValue(firstGeneric(normalized), name, classIndex, visiting));
        if (simple.equals("R")) {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("succeed", true);
            wrapper.put("message", "success");
            wrapper.put("data", sampleValue(firstGeneric(normalized), "data", classIndex, visiting));
            return wrapper;
        }

        Path classPath = classIndex.get(simple);
        if (classPath == null || visiting.contains(simple)) {
            return Map.of();
        }
        visiting.add(simple);
        Map<String, Object> object = new LinkedHashMap<>();
        try {
            String source = Files.readString(classPath, StandardCharsets.UTF_8);
            Map<String, String> imports = imports(source);
            String packageName = packageName(source);
            Matcher matcher = FIELD.matcher(source);
            while (matcher.find()) {
                String fieldType = resolveType(normalizeType(matcher.group(1)), imports, packageName);
                String fieldName = matcher.group(2);
                if (!fieldName.equals("serialVersionUID")) {
                    object.put(fieldName, sampleValue(fieldType, fieldName, classIndex, visiting));
                }
            }
        } catch (Exception ignored) {
            return Map.of();
        } finally {
            visiting.remove(simple);
        }
        return object;
    }

    private boolean isRuntimeOnly(String type) {
        String simple = simpleType(type);
        return simple.equals("ServerHttpRequest") || simple.equals("HttpServletRequest") || simple.equals("HttpServletResponse");
    }

    private List<String> pathVariables(String apiPath) {
        List<String> names = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\{([^}]+)}").matcher(apiPath == null ? "" : apiPath);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private String description(String javadoc, String methodName, String httpMethod, String apiPath) {
        String parsed = cleanJavadoc(javadoc);
        if (!parsed.isBlank()) {
            return parsed;
        }
        return "用途：调用 " + methodName + " 接口。\n参数：请参考请求示例。\n返回：请参考返回示例。";
    }

    private String cleanJavadoc(String javadoc) {
        if (javadoc == null || javadoc.isBlank()) {
            return "";
        }
        String content = javadoc.replaceAll("(?s)/\\*\\*|\\*/", "").replaceAll("(?m)^\\s*\\* ?", "").trim();
        List<String> usage = new ArrayList<>();
        List<String> params = new ArrayList<>();
        List<String> returns = new ArrayList<>();
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("@param ")) {
                String value = line.substring("@param ".length()).trim();
                int firstSpace = value.indexOf(' ');
                params.add(firstSpace > 0 ? value.substring(0, firstSpace) + "：" + value.substring(firstSpace + 1).trim() : value);
            } else if (line.startsWith("@return ")) {
                returns.add(line.substring("@return ".length()).trim());
            } else if (!line.startsWith("@")) {
                usage.add(line);
            }
        }
        List<String> description = new ArrayList<>();
        if (!usage.isEmpty()) description.add(String.join(" ", usage));
        if (!params.isEmpty()) description.add("参数：" + String.join("；", params));
        if (!returns.isEmpty()) description.add("返回：" + String.join("；", returns));
        return String.join("\n", description);
    }

    private String json(Object value) {
        StringBuilder builder = new StringBuilder();
        appendJson(builder, value);
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendJson(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String string) {
            builder.append('"').append(escape(string)).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            builder.append(value);
        } else if (value instanceof Map<?, ?> map) {
            builder.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) builder.append(',');
                first = false;
                builder.append('"').append(escape(String.valueOf(entry.getKey()))).append('"').append(':');
                appendJson(builder, entry.getValue());
            }
            builder.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            builder.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) builder.append(',');
                first = false;
                appendJson(builder, item);
            }
            builder.append(']');
        } else {
            appendJson(builder, String.valueOf(value));
        }
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String resolveType(String type, Map<String, String> imports, String packageName) {
        String normalized = normalizeType(type);
        String simple = simpleType(normalized);
        String resolved = imports.getOrDefault(simple, simple);
        if (!resolved.contains(".") && classLooksLocal(simple) && packageName != null && !packageName.isBlank()) {
            resolved = packageName + "." + simple;
        }
        String generic = genericContent(normalized);
        if (generic.isBlank()) {
            return resolved;
        }
        List<String> resolvedGenerics = splitTopLevel(generic, ',').stream()
                .map(item -> resolveType(item, imports, packageName))
                .toList();
        return resolved + "<" + String.join(",", resolvedGenerics) + ">";
    }

    private Map<String, String> imports(String source) {
        Map<String, String> imports = new LinkedHashMap<>();
        Matcher matcher = IMPORT.matcher(source);
        while (matcher.find()) {
            String qualifiedName = matcher.group(1);
            imports.put(qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1), qualifiedName);
        }
        return imports;
    }

    private String packageName(String source) {
        Matcher matcher = PACKAGE.matcher(source);
        return matcher.find() ? matcher.group(1) : "";
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

    private String normalizeType(String type) {
        return type == null ? "" : type.replaceAll("\\s+", "").replace("?extends", "").replace("?super", "");
    }

    private String simpleType(String type) {
        String raw = type;
        int genericIndex = raw.indexOf('<');
        if (genericIndex >= 0) raw = raw.substring(0, genericIndex);
        int packageIndex = raw.lastIndexOf('.');
        return packageIndex >= 0 ? raw.substring(packageIndex + 1) : raw;
    }

    private String firstGeneric(String type) {
        String generic = genericContent(type);
        if (generic.isBlank()) return "Object";
        return splitTopLevel(generic, ',').getFirst();
    }

    private String genericContent(String type) {
        int start = type.indexOf('<');
        int end = type.lastIndexOf('>');
        return start >= 0 && end > start ? type.substring(start + 1, end) : "";
    }

    private List<String> splitTopLevel(String value, char separator) {
        List<String> result = new ArrayList<>();
        int angleDepth = 0;
        int parenDepth = 0;
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '<') angleDepth++;
            if (c == '>') angleDepth--;
            if (c == '(') parenDepth++;
            if (c == ')') parenDepth--;
            if (c == separator && angleDepth == 0 && parenDepth == 0) {
                result.add(value.substring(start, i).trim());
                start = i + 1;
            }
        }
        result.add(value.substring(start).trim());
        return result.stream().filter(item -> !item.isBlank()).toList();
    }

    private boolean classLooksLocal(String simple) {
        if (simple == null || simple.isBlank()) {
            return false;
        }
        return !isString(simple) && !isInteger(simple) && !isLong(simple) && !isDecimal(simple) && !isBoolean(simple)
                && !isDateTime(simple) && Character.isUpperCase(simple.charAt(0));
    }

    private boolean isString(String type) {
        return type.equals("String") || type.equals("CharSequence");
    }

    private boolean isInteger(String type) {
        return type.equals("Integer") || type.equals("int") || type.equals("Short") || type.equals("short") || type.equals("Byte") || type.equals("byte");
    }

    private boolean isLong(String type) {
        return type.equals("Long") || type.equals("long");
    }

    private boolean isDecimal(String type) {
        return type.equals("Double") || type.equals("double") || type.equals("Float") || type.equals("float") || type.equals("BigDecimal");
    }

    private boolean isBoolean(String type) {
        return type.equals("Boolean") || type.equals("boolean");
    }

    private boolean isDateTime(String type) {
        return type.equals("OffsetDateTime") || type.equals("LocalDateTime") || type.equals("LocalDate") || type.equals("Date") || type.equals("Instant");
    }

    private String sampleString(String name) {
        String lower = name == null ? "" : name.toLowerCase();
        if (lower.contains("token") || lower.contains("authorization")) return "Bearer access-token";
        if (lower.contains("email")) return "user@example.com";
        if (lower.contains("phone")) return "13800000000";
        if (lower.contains("password")) return "******";
        if (lower.contains("userid")) return "U1001";
        if (lower.contains("tenantid")) return "default";
        if (lower.contains("sessionid")) return "S1001";
        if (lower.contains("method")) return "GET";
        if (lower.contains("path")) return "/api/example";
        if (lower.contains("code")) return "EXAMPLE_CODE";
        if (lower.contains("name")) return "示例名称";
        if (lower.contains("title")) return "示例标题";
        if (lower.contains("message") || lower.contains("content")) return "示例内容";
        return "string";
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

    private ApiResourceDTO toDto(ApiResource resource) {
        return ApiResourceDTO.builder()
                .id(resource.getId())
                .resourceCode(resource.getResourceCode())
                .serviceName(resource.getServiceName())
                .controllerPath(resource.getControllerPath())
                .controllerName(resource.getControllerName())
                .httpMethod(resource.getHttpMethod())
                .path(resource.getPath())
                .methodName(resource.getMethodName())
                .description(resource.getDescription())
                .requestExample(resource.getRequestExample())
                .responseExample(resource.getResponseExample())
                .enabled(resource.getEnabled())
                .build();
    }

    private record MethodParameter(String type, String name, String requestName, String annotation, String qualifiedType) {
    }
}

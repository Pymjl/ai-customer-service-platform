package com.aicsp.user.dto.resource;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class ResourceTreeNode {
    private String id;
    private String label;
    private String type;
    private ApiResourceDTO resource;
    private List<ResourceTreeNode> children = new ArrayList<>();

    public ResourceTreeNode(String id, String label, String type) {
        this.id = id;
        this.label = label;
        this.type = type;
    }
}

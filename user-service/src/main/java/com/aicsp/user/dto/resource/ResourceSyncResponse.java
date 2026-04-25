package com.aicsp.user.dto.resource;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResourceSyncResponse {
    private int scanned;
    private int inserted;
    private int updated;
}

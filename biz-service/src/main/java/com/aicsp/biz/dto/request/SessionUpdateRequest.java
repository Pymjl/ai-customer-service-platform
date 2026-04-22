package com.aicsp.biz.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SessionUpdateRequest {

    private String title;
    private Integer status;
}

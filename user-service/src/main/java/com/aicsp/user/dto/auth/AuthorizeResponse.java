package com.aicsp.user.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthorizeResponse {
    private boolean allowed;
}

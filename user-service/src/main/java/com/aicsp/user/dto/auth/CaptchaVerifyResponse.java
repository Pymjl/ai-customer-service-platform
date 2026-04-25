package com.aicsp.user.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CaptchaVerifyResponse {
    private String captchaToken;
}

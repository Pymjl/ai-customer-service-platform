package com.aicsp.user.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CaptchaChallengeResponse {
    private String challengeId;
    private String backgroundImage;
    private String sliderImage;
    private int width;
    private int height;
    private int sliderWidth;
    private int sliderY;
    private int expiresIn;
}

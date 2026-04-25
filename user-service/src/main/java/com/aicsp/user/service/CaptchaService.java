package com.aicsp.user.service;

import com.aicsp.user.dto.auth.CaptchaChallengeResponse;
import com.aicsp.user.dto.auth.CaptchaVerifyResponse;

public interface CaptchaService {
    CaptchaChallengeResponse createChallenge();
    CaptchaVerifyResponse verify(String challengeId, int x);
    void consumeToken(String token);
}

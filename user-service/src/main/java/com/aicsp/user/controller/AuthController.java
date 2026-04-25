package com.aicsp.user.controller;

import com.aicsp.common.result.R;
import com.aicsp.user.dto.auth.AuthorizeRequest;
import com.aicsp.user.dto.auth.AuthorizeResponse;
import com.aicsp.user.dto.auth.CaptchaChallengeResponse;
import com.aicsp.user.dto.auth.CaptchaVerifyRequest;
import com.aicsp.user.dto.auth.CaptchaVerifyResponse;
import com.aicsp.user.dto.auth.IntrospectionResponse;
import com.aicsp.user.dto.auth.LoginRequest;
import com.aicsp.user.dto.auth.RegisterRequest;
import com.aicsp.user.dto.auth.TokenResponse;
import com.aicsp.user.dto.auth.UserProfile;
import com.aicsp.user.service.AuthService;
import com.aicsp.user.service.CaptchaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final CaptchaService captchaService;

    @GetMapping("/captcha")
    public R<CaptchaChallengeResponse> captcha() {
        return R.ok(captchaService.createChallenge());
    }

    @PostMapping("/captcha/verify")
    public R<CaptchaVerifyResponse> verifyCaptcha(@Valid @RequestBody CaptchaVerifyRequest request) {
        return R.ok(captchaService.verify(request.getChallengeId(), request.getX()));
    }

    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return R.ok();
    }

    @PostMapping(value = "/register-with-avatar", consumes = "multipart/form-data")
    public R<Void> registerWithAvatar(@Valid @ModelAttribute RegisterRequest request,
            @org.springframework.web.bind.annotation.RequestPart(value = "avatar", required = false) MultipartFile avatar) {
        authService.register(request, avatar);
        return R.ok();
    }

    @PostMapping("/login")
    public R<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    @GetMapping("/me")
    public R<UserProfile> me(@RequestHeader("Authorization") String authorization) {
        return R.ok(authService.me(authorization));
    }

    @PostMapping("/authorize")
    public R<AuthorizeResponse> authorize(@RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody AuthorizeRequest request) {
        return R.ok(new AuthorizeResponse(authService.authorize(authorization, request.getHttpMethod(), request.getPath())));
    }

    @PostMapping("/introspect")
    public R<IntrospectionResponse> introspect(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return R.ok(authService.introspect(authorization));
    }
}

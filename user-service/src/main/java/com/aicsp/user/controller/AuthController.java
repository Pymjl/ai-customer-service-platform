package com.aicsp.user.controller;

import com.aicsp.common.result.R;
import com.aicsp.user.dto.auth.AuthorizeRequest;
import com.aicsp.user.dto.auth.AuthorizeResponse;
import com.aicsp.user.dto.auth.CaptchaChallengeResponse;
import com.aicsp.user.dto.auth.CaptchaVerifyRequest;
import com.aicsp.user.dto.auth.CaptchaVerifyResponse;
import com.aicsp.user.dto.auth.IntrospectionResponse;
import com.aicsp.user.dto.auth.LoginRequest;
import com.aicsp.user.dto.auth.LogoutRequest;
import com.aicsp.user.dto.auth.RefreshTokenRequest;
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

    /**
     * 用途：创建滑块验证码挑战，用于登录和注册前的人机校验。
     *
     * @return 验证码挑战信息，包含挑战 ID、背景图、滑块图、尺寸和过期时间
     */
    @GetMapping("/captcha")
    public R<CaptchaChallengeResponse> captcha() {
        return R.ok(captchaService.createChallenge());
    }

    /**
     * 用途：校验滑块验证码位置，成功后签发一次性验证码令牌。
     *
     * @param request 验证请求，包含挑战 ID 和滑块横向位置
     * @return 验证结果，包含后续登录或注册使用的 captchaToken
     */
    @PostMapping("/captcha/verify")
    public R<CaptchaVerifyResponse> verifyCaptcha(@Valid @RequestBody CaptchaVerifyRequest request) {
        return R.ok(captchaService.verify(request.getChallengeId(), request.getX()));
    }

    /**
     * 用途：注册普通用户账号。
     *
     * @param request 注册请求，包含账号、密码、租户、基础资料和验证码令牌
     * @return 空结果，表示注册成功
     */
    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return R.ok();
    }

    /**
     * 用途：注册普通用户账号并上传头像。
     *
     * @param request 注册请求，包含账号、密码、租户、基础资料和验证码令牌
     * @param avatar 头像文件，可为空
     * @return 空结果，表示注册成功
     */
    @PostMapping(value = "/register-with-avatar", consumes = "multipart/form-data")
    public R<Void> registerWithAvatar(@Valid @ModelAttribute RegisterRequest request,
            @org.springframework.web.bind.annotation.RequestPart(value = "avatar", required = false) MultipartFile avatar) {
        authService.register(request, avatar);
        return R.ok();
    }

    /**
     * 用途：用户登录并签发访问令牌与刷新令牌。
     *
     * @param request 登录请求，包含用户名、密码和验证码令牌
     * @return 登录结果，包含 accessToken、refreshToken、过期时间和用户资料
     */
    @PostMapping("/login")
    public R<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    /**
     * 用途：使用刷新令牌换取新的访问令牌。
     *
     * @param request 刷新令牌请求，包含 refreshToken
     * @return 新的令牌结果，包含 accessToken、refreshToken、过期时间和用户资料
     */
    @PostMapping("/refresh")
    public R<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return R.ok(authService.refresh(request));
    }

    /**
     * 用途：注销当前会话并吊销刷新令牌。
     *
     * @param request 注销请求，包含 refreshToken
     * @return 空结果，表示注销成功
     */
    @PostMapping("/logout")
    public R<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return R.ok();
    }

    /**
     * 用途：查询当前登录用户资料。
     *
     * @param authorization Bearer 访问令牌
     * @return 当前用户资料，包含用户基础信息和角色编码列表
     */
    @GetMapping("/me")
    public R<UserProfile> me(@RequestHeader("Authorization") String authorization) {
        return R.ok(authService.me(authorization));
    }

    /**
     * 用途：网关调用的接口鉴权，判断指定请求是否允许访问。
     *
     * @param authorization Bearer 访问令牌，可为空
     * @param request 鉴权请求，包含 HTTP 方法和请求路径
     * @return 鉴权结果，allowed 表示是否允许访问
     */
    @PostMapping("/authorize")
    public R<AuthorizeResponse> authorize(@RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody AuthorizeRequest request) {
        return R.ok(new AuthorizeResponse(authService.authorize(authorization, request.getHttpMethod(), request.getPath())));
    }

    /**
     * 用途：解析并检查访问令牌状态。
     *
     * @param authorization Bearer 访问令牌，可为空
     * @return 令牌自省结果，包含 active、用户 ID、租户、用户名和角色编码
     */
    @PostMapping("/introspect")
    public R<IntrospectionResponse> introspect(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return R.ok(authService.introspect(authorization));
    }
}

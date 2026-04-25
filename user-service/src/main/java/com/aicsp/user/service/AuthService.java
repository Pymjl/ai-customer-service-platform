package com.aicsp.user.service;

import com.aicsp.user.dto.auth.IntrospectionResponse;
import com.aicsp.user.dto.auth.LoginRequest;
import com.aicsp.user.dto.auth.LogoutRequest;
import com.aicsp.user.dto.auth.RefreshTokenRequest;
import com.aicsp.user.dto.auth.RegisterRequest;
import com.aicsp.user.dto.auth.TokenResponse;
import com.aicsp.user.dto.auth.UserProfile;
import org.springframework.web.multipart.MultipartFile;

public interface AuthService {
    TokenResponse login(LoginRequest request);
    TokenResponse refresh(RefreshTokenRequest request);
    void logout(LogoutRequest request);
    void register(RegisterRequest request);
    void register(RegisterRequest request, MultipartFile avatar);
    UserProfile me(String authorization);
    IntrospectionResponse introspect(String authorization);
    boolean authorize(String authorization, String httpMethod, String path);
}

package com.aicsp.biz.security;

import com.aicsp.common.constant.HeaderConstants;
import com.aicsp.common.exception.BizException;
import com.aicsp.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class RequestIdentityProvider {

    private final HttpServletRequest request;

    public RequestIdentity current() {
        String userId = request.getHeader(HeaderConstants.X_USER_ID);
        String tenantId = request.getHeader(HeaderConstants.X_TENANT_ID);
        String rolesHeader = request.getHeader(HeaderConstants.X_USER_ROLES);
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(tenantId)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少用户身份头");
        }
        List<String> roles = StringUtils.hasText(rolesHeader)
                ? Arrays.stream(rolesHeader.split(",")).map(String::trim).filter(StringUtils::hasText).toList()
                : List.of();
        return new RequestIdentity(userId, tenantId, roles);
    }
}

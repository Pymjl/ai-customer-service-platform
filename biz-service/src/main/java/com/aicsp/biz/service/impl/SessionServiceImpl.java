package com.aicsp.biz.service.impl;

import com.aicsp.biz.dto.request.SessionCreateRequest;
import com.aicsp.biz.dto.request.SessionUpdateRequest;
import com.aicsp.biz.dto.response.SessionDTO;
import com.aicsp.biz.entity.Session;
import com.aicsp.biz.mapper.SessionMapper;
import com.aicsp.biz.mapper.converter.SessionConverter;
import com.aicsp.biz.security.RequestIdentity;
import com.aicsp.biz.security.RequestIdentityProvider;
import com.aicsp.biz.service.SessionService;
import com.aicsp.common.exception.BizException;
import com.aicsp.common.exception.ErrorCode;
import com.aicsp.common.util.DistributedIdUtils;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final SessionMapper sessionMapper;
    private final SessionConverter sessionConverter;
    private final RequestIdentityProvider identityProvider;

    @Override
    public List<SessionDTO> listSessions() {
        RequestIdentity identity = identityProvider.current();
        return sessionConverter.toDTOList(sessionMapper.listByOwner(identity.userId(), identity.tenantId()));
    }

    @Override
    @Transactional
    public void createSession(SessionCreateRequest request) {
        RequestIdentity identity = identityProvider.current();
        request.setUserId(identity.userId());
        request.setTenantId(identity.tenantId());
        Session session = sessionConverter.toEntity(request);
        if (session.getStatus() == null) {
            session.setStatus(1);
        }
        if (!StringUtils.hasText(session.getTitle())) {
            session.setTitle("新会话");
        }
        sessionMapper.insertIgnore(session);
    }

    @Override
    @Transactional
    public void updateSession(String sessionId, SessionUpdateRequest request) {
        RequestIdentity identity = identityProvider.current();
        int updated = sessionMapper.updateByOwner(sessionId, identity.userId(), identity.tenantId(),
                request.getTitle(), request.getStatus());
        if (updated == 0) {
            throw new BizException(ErrorCode.SESSION_NOT_EXIST, "会话不存在或无权访问");
        }
    }

    @Override
    @Transactional
    public void deleteSession(String sessionId) {
        RequestIdentity identity = identityProvider.current();
        int deleted = sessionMapper.softDeleteByOwner(sessionId, identity.userId(), identity.tenantId());
        if (deleted == 0) {
            throw new BizException(ErrorCode.SESSION_NOT_EXIST, "会话不存在或无权访问");
        }
    }

    @Override
    @Transactional
    public void ensureSession(String sessionId, String userId, String tenantId) {
        Session session = new Session();
        session.setId(DistributedIdUtils.nextId());
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTenantId(tenantId);
        session.setTitle("智能客服会话");
        session.setStatus(1);
        session.setCreatedBy(0L);
        session.setUpdatedBy(0L);
        session.setDeleted(false);
        sessionMapper.insertIgnore(session);
    }
}

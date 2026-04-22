package com.aicsp.biz.service.impl;

import com.aicsp.biz.dto.request.SessionCreateRequest;
import com.aicsp.biz.dto.response.SessionDTO;
import com.aicsp.biz.entity.Session;
import com.aicsp.biz.mapper.SessionMapper;
import com.aicsp.biz.mapper.converter.SessionConverter;
import com.aicsp.biz.service.SessionService;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final SessionMapper sessionMapper;
    private final SessionConverter sessionConverter;

    @Override
    public List<SessionDTO> listSessions() {
        return Collections.emptyList();
    }

    @Override
    public void createSession(SessionCreateRequest request) {
        Session session = sessionConverter.toEntity(request);
        if (session.getStatus() == null) {
            session.setStatus(1);
        }
    }

    @Override
    public void ensureSession(String sessionId, String userId, String tenantId) {
    }
}

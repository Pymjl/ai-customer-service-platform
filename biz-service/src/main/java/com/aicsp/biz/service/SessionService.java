package com.aicsp.biz.service;

import com.aicsp.biz.dto.request.SessionCreateRequest;
import com.aicsp.biz.dto.response.SessionDTO;
import java.util.List;

public interface SessionService {

    List<SessionDTO> listSessions();

    void createSession(SessionCreateRequest request);

    void ensureSession(String sessionId, String userId, String tenantId);
}

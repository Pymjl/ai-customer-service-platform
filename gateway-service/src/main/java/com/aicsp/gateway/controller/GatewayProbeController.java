package com.aicsp.gateway.controller;

import com.aicsp.common.result.R;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gateway")
public class GatewayProbeController {

    /**
     * 用途：网关健康探测。
     *
     * @return 探测结果，包含服务名称和 ok 状态
     */
    @GetMapping("/ping")
    public R<Map<String, String>> ping() {
        return R.ok(Map.of("service", "gateway-service", "status", "ok"));
    }
}

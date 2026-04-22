package com.aicsp.stream.controller;

import com.aicsp.common.result.R;
import com.aicsp.stream.service.InternalQueryService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class InternalController {

    private final InternalQueryService internalQueryService;

    @GetMapping("/internal/{functionName}")
    public Mono<R<Map<String, Object>>> query(@PathVariable String functionName,
                                              @RequestParam Map<String, String> params) {
        return internalQueryService.query(functionName, params).map(R::ok);
    }
}

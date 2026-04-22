package com.aicsp.common.util;

import java.util.UUID;

public final class TraceIdUtils {

    private TraceIdUtils() {
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }
}

package com.aicsp.common.util;

import java.lang.management.ManagementFactory;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicLong;

public final class DistributedIdUtils {

    private static final long EPOCH = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
    private static final long WORKER_ID = resolveWorkerId();
    private static final AtomicLong LAST_TIMESTAMP = new AtomicLong(-1L);
    private static final AtomicLong SEQUENCE = new AtomicLong(0L);

    private DistributedIdUtils() {
    }

    public static long nextId() {
        while (true) {
            long currentTimestamp = currentTimestamp();
            long lastTimestamp = LAST_TIMESTAMP.get();
            if (currentTimestamp < lastTimestamp) {
                currentTimestamp = lastTimestamp;
            }
            if (currentTimestamp == lastTimestamp) {
                long sequence = SEQUENCE.incrementAndGet() & SEQUENCE_MASK;
                if (sequence == 0) {
                    currentTimestamp = waitNextMillis(lastTimestamp);
                    if (LAST_TIMESTAMP.compareAndSet(lastTimestamp, currentTimestamp)) {
                        SEQUENCE.set(0L);
                        return buildId(currentTimestamp, 0L);
                    }
                    continue;
                }
                return buildId(currentTimestamp, sequence);
            }
            if (LAST_TIMESTAMP.compareAndSet(lastTimestamp, currentTimestamp)) {
                SEQUENCE.set(0L);
                return buildId(currentTimestamp, 0L);
            }
        }
    }

    private static long buildId(long timestamp, long sequence) {
        return ((timestamp - EPOCH) << (WORKER_ID_BITS + SEQUENCE_BITS))
                | (WORKER_ID << SEQUENCE_BITS)
                | sequence;
    }

    private static long waitNextMillis(long lastTimestamp) {
        long timestamp = currentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimestamp();
        }
        return timestamp;
    }

    private static long currentTimestamp() {
        return System.currentTimeMillis();
    }

    private static long resolveWorkerId() {
        String configuredWorkerId = System.getProperty("aicsp.worker-id", System.getenv("AICSP_WORKER_ID"));
        if (configuredWorkerId != null && !configuredWorkerId.isBlank()) {
            return Long.parseLong(configuredWorkerId) & MAX_WORKER_ID;
        }
        long hash = ManagementFactory.getRuntimeMXBean().getName().hashCode();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                byte[] hardwareAddress = interfaces.nextElement().getHardwareAddress();
                if (hardwareAddress != null) {
                    for (byte value : hardwareAddress) {
                        hash = 31 * hash + value;
                    }
                }
            }
        } catch (Exception ignored) {
            hash = 31 * hash + new SecureRandom().nextInt();
        }
        return Math.floorMod(hash, MAX_WORKER_ID + 1);
    }
}

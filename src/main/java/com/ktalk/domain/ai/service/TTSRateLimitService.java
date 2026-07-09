package com.ktalk.domain.ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TTSRateLimitService {

    private final Map<String, Deque<Long>> requestHistory = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    @Value("${TTS_USER_REQUESTS_PER_MINUTE:6}")
    private int requestsPerMinute;

    public boolean tryAcquire(String clientKey) {
        long now = clock.millis();
        long windowStart = now - 60_000L;
        Deque<Long> timestamps = requestHistory.computeIfAbsent(clientKey, ignored -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= Math.max(1, requestsPerMinute)) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}

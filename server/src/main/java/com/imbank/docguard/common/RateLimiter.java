package com.imbank.docguard.common;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 서버 메모리의 이동 창(sliding window) 속도 제한입니다. 서버 한 대 기준이며, 여러 대로 늘리면 DB 나 Redis 로 옮깁니다. (SECURITY.md 8장)
 */
@Component
public class RateLimiter {

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter() {
        this(Clock.systemUTC());
    }

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * 허용되면 기록하고 true, 한도를 넘으면 기록하지 않고 false 를 돌려줍니다.
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = clock.millis();
        long from = now - window.toMillis();
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() <= from) {
                deque.pollFirst();
            }
            if (deque.size() >= limit) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    /** 한도를 넘으면 429 오류를 던집니다. */
    public void check(String key, int limit, Duration window, String message) {
        if (!tryAcquire(key, limit, window)) {
            throw ApiException.tooManyRequests(message);
        }
    }

    /** 테스트에서 상태를 비웁니다. */
    public void clear() {
        hits.clear();
    }
}

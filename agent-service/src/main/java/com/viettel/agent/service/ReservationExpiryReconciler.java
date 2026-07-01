package com.viettel.agent.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ReservationExpiryReconciler {
    private static final String EXPIRY_INDEX = "reservation_expiry_index";
    private static final int BATCH_SIZE = 100;

    private final StringRedisTemplate redis;
    private final AgentService agentService;

    public ReservationExpiryReconciler(StringRedisTemplate redis, AgentService agentService) {
        this.redis = redis;
        this.agentService = agentService;
    }

    @Scheduled(fixedDelayString = "${app.reservation.reconciliation-interval:1s}")
    public void reconcile() {
        Set<String> due = redis.opsForZSet().rangeByScore(
                EXPIRY_INDEX, 0, System.currentTimeMillis(), 0, BATCH_SIZE);
        if (due == null) return;
        due.forEach(agentService::releaseExpired);
    }
}

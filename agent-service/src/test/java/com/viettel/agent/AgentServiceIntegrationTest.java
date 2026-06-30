package com.viettel.agent;

import com.viettel.agent.api.AgentProfileRequest;
import com.viettel.agent.api.AgentStateResponse;
import com.viettel.agent.api.ReserveRequest;
import com.viettel.agent.exception.AgentConflictException;
import com.viettel.agent.service.AgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class AgentServiceIntegrationTest extends RedisIntegrationTest {
    @Autowired AgentService agentService;
    @Autowired StringRedisTemplate redis;
    @Autowired RedisConnectionFactory connectionFactory;

    @BeforeEach
    void cleanRedis() {
        connectionFactory.getConnection().serverCommands().flushAll();
    }

    @Test
    void concurrentReservationsNeverExceedCapacity() throws Exception {
        profileAndOnline(1, 3, Set.of("support"));
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch ready = new CountDownLatch(12);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 12; i++) {
                UUID conversationId = UUID.randomUUID();
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        agentService.reserve(new ReserveRequest(conversationId, "support"));
                        return true;
                    } catch (AgentConflictException ignored) {
                        return false;
                    }
                }));
            }
            ready.await();
            start.countDown();
            long successes = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) successes++;
            }
            assertThat(successes).isEqualTo(3);
            assertThat(agentService.online(1).currentConversations()).isEqualTo(3);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void fullBreakAndOfflineRemoveAgentFromEverySkillPool() {
        profileAndOnline(2, 1, Set.of("support", "billing"));
        UUID first = UUID.randomUUID();
        agentService.reserve(new ReserveRequest(first, "support"));
        assertNotInPools(2, "support", "billing");

        agentService.release(2, first);
        agentService.requestBreak(2);
        assertThat(agentService.online(2).status()).isEqualTo("break");
        assertNotInPools(2, "support", "billing");

        agentService.available(2);
        agentService.offline(2);
        assertNotInPools(2, "support", "billing");
    }

    @Test
    void stalePoolEntryCannotBypassStateGate() {
        agentService.updateProfile(3, profile(1, Set.of("support")));
        redis.opsForSet().add("available_agents:support", "3");
        assertThatThrownBy(() -> agentService.reserve(new ReserveRequest(UUID.randomUUID(), "support")))
                .isInstanceOf(AgentConflictException.class);
        assertThat(agentService.offline(3).currentConversations()).isZero();
    }

    @Test
    void reserveConfirmAndReleaseAreIdempotent() {
        profileAndOnline(4, 2, Set.of("support"));
        UUID conversationId = UUID.randomUUID();
        long firstAgent = agentService.reserve(new ReserveRequest(conversationId, "support")).agentId();
        long replayedAgent = agentService.reserve(new ReserveRequest(conversationId, "support")).agentId();
        assertThat(replayedAgent).isEqualTo(firstAgent);
        assertThat(agentService.online(4).currentConversations()).isEqualTo(1);

        agentService.confirm(conversationId);
        agentService.confirm(conversationId);
        assertThat(agentService.online(4).currentConversations()).isEqualTo(1);

        agentService.release(4, conversationId);
        agentService.release(4, conversationId);
        assertThat(agentService.online(4).currentConversations()).isZero();
    }

    @Test
    void releaseTransitionsWaitingToBreakAndExpiryRestoresCapacity() {
        profileAndOnline(5, 2, Set.of("support"));
        UUID explicit = UUID.randomUUID();
        agentService.reserve(new ReserveRequest(explicit, "support"));
        assertThat(agentService.requestBreak(5).status()).isEqualTo("waiting_to_break");
        assertThat(agentService.release(5, explicit).status()).isEqualTo("break");

        agentService.available(5);
        UUID expiring = UUID.randomUUID();
        agentService.reserve(new ReserveRequest(expiring, "support"));
        assertThat(agentService.requestBreak(5).status()).isEqualTo("waiting_to_break");
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            AgentStateResponse state = agentService.online(5);
            assertThat(state.currentConversations()).isZero();
            assertThat(state.status()).isEqualTo("break");
            assertThat(redis.opsForSet().isMember("available_agents:support", "5")).isFalse();
        });

        agentService.available(5);
        agentService.reserve(new ReserveRequest(UUID.randomUUID(), "support"));
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            AgentStateResponse state = agentService.online(5);
            assertThat(state.currentConversations()).isZero();
            assertThat(redis.opsForSet().isMember("available_agents:support", "5")).isTrue();
        });
    }

    @Test
    void offlinePreservesStatusAcrossReconnect() {
        profileAndOnline(6, 1, Set.of("support"));
        agentService.requestBreak(6);
        AgentStateResponse offline = agentService.offline(6);
        assertThat(offline.state()).isEqualTo("offline");
        assertThat(offline.status()).isEqualTo("break");
        AgentStateResponse online = agentService.online(6);
        assertThat(online.status()).isEqualTo("break");
        assertNotInPools(6, "support");
    }

    private void profileAndOnline(long agentId, int max, Set<String> skills) {
        agentService.updateProfile(agentId, profile(max, skills));
        agentService.online(agentId);
    }

    private AgentProfileRequest profile(int max, Set<String> skills) {
        return new AgentProfileRequest(max, skills, Set.of("team-a"), Set.of("webchat"));
    }

    private void assertNotInPools(long agentId, String... skills) {
        for (String skill : skills) {
            assertThat(redis.opsForSet().isMember("available_agents:" + skill, Long.toString(agentId))).isFalse();
        }
    }
}

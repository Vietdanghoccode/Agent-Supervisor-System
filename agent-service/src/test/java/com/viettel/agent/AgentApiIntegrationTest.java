package com.viettel.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AgentApiIntegrationTest extends RedisIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired RedisConnectionFactory connectionFactory;

    @BeforeEach
    void cleanRedis() {
        connectionFactory.getConnection().serverCommands().flushAll();
    }

    @Test
    void validatesProfileAndUsesProblemDetails() throws Exception {
        mockMvc.perform(put("/agents/1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"maxConversations":0,"skills":[],"teams":[],"channels":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"));

        mockMvc.perform(post("/agents/99/online"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void rejectsOfflineTransitionAndCapacityBelowCurrent() throws Exception {
        createProfile(7, 3);
        mockMvc.perform(post("/agents/7/break"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/agents/7/online")).andExpect(status().isOk());
        mockMvc.perform(post("/agents/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"%s","skill":"support"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value(7));
        mockMvc.perform(post("/agents/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"%s","skill":"support"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk());

        mockMvc.perform(put("/agents/7/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"maxConversations":1,"skills":["support"],"teams":[],"channels":[]}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void queuesPollsAndCancelsWhenNoAgentIsAvailable() throws Exception {
        UUID conversationId = UUID.randomUUID();
        mockMvc.perform(post("/agents/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"conversationId":"%s","skill":"support"}
                                """.formatted(conversationId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.conversationId").value(conversationId.toString()))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.agentId").doesNotExist());

        mockMvc.perform(get("/reservations/" + conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"));

        mockMvc.perform(delete("/reservations/" + conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(delete("/reservations/" + conversationId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/reservations/not-a-uuid/confirm"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"));
    }

    private void createProfile(long agentId, int max) throws Exception {
        mockMvc.perform(put("/agents/" + agentId + "/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"maxConversations":%d,"skills":["support"],"teams":[],"channels":[]}
                                """.formatted(max)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("offline"))
                .andExpect(jsonPath("$.status").value("available"));
    }
}

package com.viettel.conversation;

import com.viettel.conversation.api.CreateConversationRequest;
import com.viettel.conversation.repository.OutboxEventRepository;
import com.viettel.conversation.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class ConversationTransactionRollbackTest extends PostgresIntegrationTest {
    @Autowired ConversationService conversationService;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean
    OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM conversations");
    }

    @Test
    void rollsBackConversationAndMessageWhenOutboxWriteFails() {
        when(outboxEventRepository.save(any())).thenThrow(new RuntimeException("simulated outbox failure"));

        CreateConversationRequest request = new CreateConversationRequest(
                1L, "Tôi cần hỗ trợ", "webchat", "support");
        assertThatThrownBy(() -> conversationService.create("rollback-1", request))
                .hasMessageContaining("simulated outbox failure");

        assertThat(count("conversations")).isZero();
        assertThat(count("messages")).isZero();
        assertThat(count("outbox_events")).isZero();
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}

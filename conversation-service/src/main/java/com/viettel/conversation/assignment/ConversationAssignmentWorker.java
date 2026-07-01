package com.viettel.conversation.assignment;

import com.viettel.conversation.domain.ConversationStatus;
import com.viettel.conversation.exception.AgentServiceException;
import com.viettel.conversation.exception.ConversationConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.assignment.worker.enabled", havingValue = "true")
public class ConversationAssignmentWorker {
    private static final Logger log = LoggerFactory.getLogger(ConversationAssignmentWorker.class);

    private final AssignmentJdbcRepository repository;
    private final AgentServiceClient agentServiceClient;
    private final AssignmentWorkerProperties properties;
    private final Clock clock;

    ConversationAssignmentWorker(AssignmentJdbcRepository repository,
                                 AgentServiceClient agentServiceClient,
                                 AssignmentWorkerProperties properties) {
        this.repository = repository;
        this.agentServiceClient = agentServiceClient;
        this.properties = properties;
        this.clock = Clock.systemUTC();
    }

    @Scheduled(fixedDelayString = "${app.assignment.worker.poll-interval:1s}")
    public void processNextBatch() {
        Instant now = clock.instant();
        List<ClaimedConversation> conversations = repository.claim(
                properties.batchSize(), now, properties.assigningTimeout());
        for (ClaimedConversation conversation : conversations) {
            process(conversation);
        }
    }

    private void process(ClaimedConversation conversation) {
        try {
            if (conversation.originalStatus() == ConversationStatus.QUEUED) {
                pollQueued(conversation);
            } else {
                reserveWaiting(conversation);
            }
        } catch (AgentServiceException | ConversationConflictException exception) {
            returnToRetryableState(conversation);
            log.warn("Assignment attempt for conversation {} will be retried: {}",
                    conversation.id(), exception.getMessage());
        }
    }

    private void reserveWaiting(ClaimedConversation conversation) {
        AgentReservationResponse reservation = agentServiceClient.reserve(
                conversation.id(), normalizedSkill(conversation.skill()));
        applyReservation(conversation, reservation);
    }

    private void pollQueued(ClaimedConversation conversation) {
        AgentReservationResponse reservation = agentServiceClient.reservation(conversation.id());
        applyReservation(conversation, reservation);
    }

    private void applyReservation(ClaimedConversation conversation, AgentReservationResponse reservation) {
        if ("WAITING".equals(reservation.status())) {
            repository.markQueued(conversation.id(), clock.instant());
            return;
        }
        if ("RESERVED".equals(reservation.status())) {
            AgentConfirmationResponse confirmation = agentServiceClient.confirm(conversation.id());
            repository.markAssigned(conversation.id(), confirmation.agentId(), clock.instant());
            return;
        }
        if ("CONFIRMED".equals(reservation.status()) && reservation.agentId() != null) {
            repository.markAssigned(conversation.id(), reservation.agentId(), clock.instant());
            return;
        }
        returnToRetryableState(conversation);
        log.warn("Unsupported Agent reservation status {} for conversation {}",
                reservation.status(), conversation.id());
    }

    private void returnToRetryableState(ClaimedConversation conversation) {
        if (conversation.originalStatus() == ConversationStatus.QUEUED) {
            repository.markQueued(conversation.id(), clock.instant());
        } else {
            repository.markWaiting(conversation.id(), clock.instant());
        }
    }

    private String normalizedSkill(String skill) {
        if (skill == null || skill.isBlank()) {
            return properties.defaultSkill();
        }
        return skill.trim();
    }
}

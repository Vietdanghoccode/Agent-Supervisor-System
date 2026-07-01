package com.viettel.conversation.assignment;

import com.viettel.conversation.exception.AgentServiceException;
import com.viettel.conversation.exception.ConversationConflictException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
public class AgentServiceClient {
    private final RestClient restClient;

    public AgentServiceClient(RestClient.Builder builder, AssignmentWorkerProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = builder
                .baseUrl(properties.agentServiceUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public AgentReservationResponse reserve(UUID conversationId, String skill) {
        try {
            return restClient.post()
                    .uri("/agents/reserve")
                    .body(new AgentReservationRequest(conversationId, skill))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,
                            (request, response) -> {
                                throw new ConversationConflictException("Agent reservation conflict");
                            })
                    .body(AgentReservationResponse.class);
        } catch (ConversationConflictException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new AgentServiceException("Agent Service reserve request failed", exception);
        }
    }

    public AgentReservationResponse reservation(UUID conversationId) {
        try {
            return restClient.get()
                    .uri("/reservations/{conversationId}", conversationId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,
                            (request, response) -> {
                                throw new ConversationConflictException("Agent reservation does not exist");
                            })
                    .body(AgentReservationResponse.class);
        } catch (ConversationConflictException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new AgentServiceException("Agent Service reservation lookup failed", exception);
        }
    }

    public AgentConfirmationResponse confirm(UUID conversationId) {
        try {
            return restClient.post()
                    .uri("/reservations/{conversationId}/confirm", conversationId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,
                            (request, response) -> {
                                throw new ConversationConflictException("Agent reservation cannot be confirmed");
                            })
                    .body(AgentConfirmationResponse.class);
        } catch (ConversationConflictException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new AgentServiceException("Agent Service confirm request failed", exception);
        }
    }

    public void cancel(UUID conversationId) {
        try {
            restClient.delete()
                    .uri("/reservations/{conversationId}", conversationId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,
                            (request, response) -> {
                                throw new ConversationConflictException("Agent reservation cannot be cancelled");
                            })
                    .toBodilessEntity();
        } catch (ConversationConflictException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new AgentServiceException("Agent Service cancel request failed", exception);
        }
    }

    public void release(long agentId, UUID conversationId) {
        try {
            restClient.post()
                    .uri("/agents/{agentId}/release", agentId)
                    .body(new AgentReleaseRequest(conversationId))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError,
                            (request, response) -> {
                                throw new ConversationConflictException("Agent assignment cannot be released");
                            })
                    .toBodilessEntity();
        } catch (ConversationConflictException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new AgentServiceException("Agent Service release request failed", exception);
        }
    }
}

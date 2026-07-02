package com.viettel.conversation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
class IdempotencyFingerprint {
    private final ObjectMapper objectMapper;

    IdempotencyFingerprint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String calculate(NormalizedCreateRequest request) {
        Map<String, Object> canonicalPayload = new LinkedHashMap<>();
        canonicalPayload.put("customerId", request.customerId());
        canonicalPayload.put("clientMessageId", request.clientMessageId());
        canonicalPayload.put("message", request.message());
        canonicalPayload.put("channel", request.channel());
        canonicalPayload.put("skill", request.skill());
        try {
            byte[] json = objectMapper.writeValueAsString(canonicalPayload).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot calculate idempotency fingerprint", exception);
        }
    }
}

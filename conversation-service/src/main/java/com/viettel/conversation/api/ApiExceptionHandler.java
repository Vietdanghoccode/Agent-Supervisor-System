package com.viettel.conversation.api;

import com.viettel.conversation.exception.AgentServiceException;
import com.viettel.conversation.exception.BadRequestException;
import com.viettel.conversation.exception.ConversationConflictException;
import com.viettel.conversation.exception.ConversationNotFoundException;
import com.viettel.conversation.exception.IdempotencyConflictException;
import com.viettel.conversation.exception.ForbiddenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(BadRequestException.class)
    ProblemDetail handleBadRequest(BadRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return problem(HttpStatus.BAD_REQUEST, "Validation failed", detail);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail handleMissingHeader(MissingRequestHeaderException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request",
                "Header " + exception.getHeaderName() + " is required");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request body is missing or malformed");
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail handleConflict(IdempotencyConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Idempotency conflict", exception.getMessage());
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    ProblemDetail handleNotFound(ConversationNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Conversation not found", exception.getMessage());
    }

    @ExceptionHandler(ConversationConflictException.class)
    ProblemDetail handleConversationConflict(ConversationConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Conversation conflict", exception.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail handleForbidden(ForbiddenException exception) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", exception.getMessage());
    }

    @ExceptionHandler(AgentServiceException.class)
    ProblemDetail handleAgentService(AgentServiceException exception) {
        return problem(HttpStatus.BAD_GATEWAY, "Agent Service unavailable", exception.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("about:blank"));
        return problem;
    }
}

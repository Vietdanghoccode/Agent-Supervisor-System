package com.viettel.auth.controller;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientResponseException;
@RestControllerAdvice
public class ApiExceptionHandler {
 @ExceptionHandler(ResponseStatusException.class)
 ProblemDetail status(ResponseStatusException e){return ProblemDetail.forStatusAndDetail(e.getStatusCode(),e.getReason()==null?"Request failed":e.getReason());}
 @ExceptionHandler(MethodArgumentNotValidException.class)
 ProblemDetail validation(MethodArgumentNotValidException e){return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Request validation failed");}
 @ExceptionHandler(RestClientResponseException.class)
 ProblemDetail downstream(RestClientResponseException e){return ProblemDetail.forStatusAndDetail(e.getStatusCode(),"Provisioning request failed");}
}

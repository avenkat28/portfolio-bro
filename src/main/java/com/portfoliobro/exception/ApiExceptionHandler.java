package com.portfoliobro.exception;

import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    public record FieldError(String field, String message) {}

    @ExceptionHandler(PortfolioNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(PortfolioNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setProperty("code", "PORTFOLIO_NOT_FOUND");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(TradeConflictException.class)
    ResponseEntity<ProblemDetail> tradeConflict(TradeConflictException exception) {
        return problem(HttpStatus.CONFLICT, exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(OversellException.class)
    ResponseEntity<ProblemDetail> oversell(OversellException exception) {
        return problem(HttpStatus.CONFLICT, "OVERSELL", exception.getMessage());
    }

    @ExceptionHandler(InvalidTradeException.class)
    ResponseEntity<ProblemDetail> invalidTrade(InvalidTradeException exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_TRADE", exception.getMessage());
    }

    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> lockTimeout(Exception exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "LOCK_TIMEOUT", "Portfolio is busy; retry with the same idempotency key");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return ResponseEntity.status(status).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "Request validation failed");
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage())).toList();
        problem.setProperty("fieldErrors", errors);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail detail ? detail : ProblemDetail.forStatus(status);
        // Framework parse errors can contain implementation details: expose a safe message instead.
        if (status.value() == 400 && !(exception instanceof MethodArgumentNotValidException)) {
            problem.setDetail("Malformed request or invalid parameter");
        }
        problem.setProperty("code", "HTTP_" + status.value());
        return super.handleExceptionInternal(exception, problem, headers, status, request);
    }
}

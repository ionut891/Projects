package com.awin.transactions.api;

import com.awin.transactions.domain.IllegalStateTransitionException;
import com.awin.transactions.service.AmountValidationException;
import com.awin.transactions.service.ConcurrentModificationException;
import com.awin.transactions.service.TransactionNotFoundException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain and service exceptions into RFC 7807 {@link ProblemDetail} responses so the
 * controllers stay free of try/catch noise and the response shape is consistent across endpoints.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TransactionNotFoundException.class)
    public ProblemDetail handleNotFound(TransactionNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AmountValidationException.class)
    public ProblemDetail handleAmountValidation(AmountValidationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ProblemDetail handleIllegalTransition(IllegalStateTransitionException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setProperty("transactionId", ex.getTransactionId());
        pd.setProperty("currentStatus", ex.getFrom());
        pd.setProperty("requestedStatus", ex.getTo());
        return pd;
    }

    @ExceptionHandler(ConcurrentModificationException.class)
    public ProblemDetail handleConcurrent(ConcurrentModificationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBeanValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, detail.isEmpty() ? "Validation failed" : detail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body");
    }
}

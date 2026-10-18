package kz.govflow;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record ApiError(int status, String message, Map<String, String> fields) {}

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError(400, "Check the request fields", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformed(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "Invalid JSON, identifier, or status value");
    }

    @ExceptionHandler(InvalidTransitionException.class)
    ResponseEntity<ApiError> transition(InvalidTransitionException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> concurrentChange(OptimisticLockingFailureException exception) {
        return error(HttpStatus.CONFLICT, "Request changed concurrently. Refresh and retry.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> missing(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(
                new ApiError(exception.getStatusCode().value(), exception.getReason(), Map.of()));
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), message, Map.of()));
    }
}

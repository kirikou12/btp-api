package mr.btp.api.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import mr.btp.api.common.i18n.ApiMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    private final ApiMessageSource apiMessageSource;

    public GlobalExceptionHandler(ApiMessageSource apiMessageSource) {
        this.apiMessageSource = apiMessageSource;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException exception, HttpServletRequest request) {
        Map<String, Object> extras = new java.util.HashMap<>();
        if (exception.getMessageCode() != null) {
            extras.put("code", exception.getMessageCode());
        }
        return build(exception.getStatus(), apiMessageSource.resolve(exception), request, extras);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> apiMessageSource.getMessage(error, error.getDefaultMessage()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return build(HttpStatus.BAD_REQUEST, apiMessageSource.getMessage("error.validation.failed", "Validation failed"), request, Map.of("errors", errors));
    }

    @ExceptionHandler({ConstraintViolationException.class, BadCredentialsException.class, AccessDeniedException.class})
    public ResponseEntity<Map<String, Object>> handleSecurity(Exception exception, HttpServletRequest request) {
        if (exception instanceof AccessDeniedException) {
            return build(
                    HttpStatus.FORBIDDEN,
                    apiMessageSource.getMessage("error.auth.forbidden", "Access denied"),
                    request,
                    Map.of()
            );
        }
        if (exception instanceof BadCredentialsException) {
            return build(
                    HttpStatus.BAD_REQUEST,
                    apiMessageSource.getMessage("error.auth.invalid-credentials", "Invalid email or password"),
                    request,
                    Map.of()
            );
        }
        String message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? apiMessageSource.getMessage("error.validation.failed", "Validation failed")
                : exception.getMessage();
        return build(HttpStatus.BAD_REQUEST, message, request, Map.of());
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(org.springframework.web.multipart.MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return build(
                HttpStatus.PAYLOAD_TOO_LARGE,
                apiMessageSource.getMessage("error.upload.too-large", "File is too large. Max size allowed is 20MB."),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception exception, HttpServletRequest request) {
        String message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? apiMessageSource.getMessage("error.generic.unexpected", "Something went wrong.")
                : exception.getMessage();
        return build(HttpStatus.INTERNAL_SERVER_ERROR, message, request, Map.of());
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status,
                                                      String message,
                                                      HttpServletRequest request,
                                                      Map<String, Object> extras) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());
        body.putAll(extras);
        return ResponseEntity.status(status).body(body);
    }
}

package com.cocoscap.challenge.adapter.in.web.error;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.filter.ServerHttpObservationFilter;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps framework exceptions to RFC 9457 Problem Details (ADR-007).
 *
 * <p>Domain exception handlers are registered here by each capability.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String CODE_PROPERTY = "code";
    private static final String ERRORS_PROPERTY = "errors";
    private static final String PROBLEM_CODE_ATTRIBUTE = "problem.code";
    private static final String PROBLEM_INSTANCE_ATTRIBUTE = "problem.instance";
    /**
     * {@link ServerHttpObservationFilter} keeps the server observation on the request and only
     * exposes the context. {@link Observation#error(Throwable)} notifies tracing handlers;
     * {@code Context#setError} does not, so the span would stay successful in Tempo.
     */
    private static final String SERVER_OBSERVATION_ATTRIBUTE =
            ServerHttpObservationFilter.class.getName() + ".observation";
    private static final String REASON_REQUIRED = "is required";
    private static final String REASON_INVALID_TYPE = "has an invalid type";
    private static final String REASON_INVALID = "is invalid";

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return problemResponse(ProblemCode.MALFORMED_REQUEST, ProblemCode.MALFORMED_REQUEST.status(), ex, null, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(new ValidationError(fieldError.getField(), reasonForConstraint(fieldError.getCode())));
        }
        return problemResponse(ProblemCode.VALIDATION_ERROR, ProblemCode.VALIDATION_ERROR.status(), ex, errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String parameterName = result.getMethodParameter().getParameterName();
            String field = parameterName != null ? parameterName : "parameter";
            result.getResolvableErrors().forEach(error -> errors.add(new ValidationError(field, REASON_INVALID)));
        }
        return problemResponse(ProblemCode.VALIDATION_ERROR, ProblemCode.VALIDATION_ERROR.status(), ex, errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            ServletRequestBindingException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String field = bindingFieldName(ex);
        List<ValidationError> errors = List.of(new ValidationError(field, REASON_REQUIRED));
        return problemResponse(ProblemCode.VALIDATION_ERROR, ProblemCode.VALIDATION_ERROR.status(), ex, errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<ValidationError> errors = List.of(new ValidationError(ex.getParameterName(), REASON_REQUIRED));
        return problemResponse(ProblemCode.VALIDATION_ERROR, ProblemCode.VALIDATION_ERROR.status(), ex, errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingPathVariable(
            MissingPathVariableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ValidationError> errors = List.of(new ValidationError(ex.getVariableName(), REASON_REQUIRED));
        return problemResponse(ProblemCode.VALIDATION_ERROR, ProblemCode.VALIDATION_ERROR.status(), ex, errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field = ex.getPropertyName() != null ? ex.getPropertyName() : "parameter";
        List<ValidationError> errors = List.of(new ValidationError(field, REASON_INVALID_TYPE));
        return problemResponse(ProblemCode.VALIDATION_ERROR, ProblemCode.VALIDATION_ERROR.status(), ex, errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problemResponse(ProblemCode.UNSUPPORTED_REQUEST, HttpStatus.NOT_FOUND, ex, null, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return problemResponse(ProblemCode.UNSUPPORTED_REQUEST, HttpStatus.METHOD_NOT_ALLOWED, ex, null, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return problemResponse(ProblemCode.UNSUPPORTED_REQUEST, HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex, null, request);
    }

    /**
     * Catch-all for unexpected failures. Domain exception handlers are registered above this by each
     * capability.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnhandledException(Exception ex, WebRequest request) {
        return problemResponse(ProblemCode.INTERNAL_ERROR, ProblemCode.INTERNAL_ERROR.status(), ex, null, request);
    }

    private ResponseEntity<Object> problemResponse(
            ProblemCode code,
            @Nullable HttpStatus status,
            Exception ex,
            @Nullable List<ValidationError> errors,
            WebRequest request) {
        HttpStatus resolvedStatus = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        String instance = "urn:uuid:" + UUID.randomUUID();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(resolvedStatus, code.detail());
        problem.setTitle(code.title());
        problem.setType(URI.create(code.typeUri()));
        problem.setInstance(URI.create(instance));
        problem.setProperty(CODE_PROPERTY, code.name());
        if (errors != null && !errors.isEmpty()) {
            problem.setProperty(ERRORS_PROPERTY, errors);
        }

        attachProblemToHttpObservation(request, code, instance, ex, resolvedStatus.is5xxServerError());

        if (resolvedStatus.is5xxServerError()) {
            log.error("Request failed code={} instance={}", code.name(), instance, ex);
        } else {
            log.info("Request failed code={} instance={}", code.name(), instance);
        }

        return ResponseEntity.status(resolvedStatus)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /**
     * High-cardinality identifiers for Tempo lookup (ADR-009). Never published as metric tags.
     * Missing observation context (a web slice without the server observation filter) is ignored.
     */
    private static void attachProblemToHttpObservation(
            WebRequest request, ProblemCode code, String instance, Exception ex, boolean serverError) {
        if (!(request instanceof ServletWebRequest servletWebRequest)) {
            return;
        }
        HttpServletRequest httpRequest = servletWebRequest.getRequest();
        ServerHttpObservationFilter.findObservationContext(httpRequest).ifPresent(context -> {
            context.addHighCardinalityKeyValue(KeyValue.of(PROBLEM_CODE_ATTRIBUTE, code.name()));
            context.addHighCardinalityKeyValue(KeyValue.of(PROBLEM_INSTANCE_ATTRIBUTE, instance));
            if (serverError) {
                recordServerError(httpRequest, context, ex);
            }
        });
    }

    private static void recordServerError(
            HttpServletRequest request, ServerRequestObservationContext context, Exception ex) {
        Object current = request.getAttribute(SERVER_OBSERVATION_ATTRIBUTE);
        if (current instanceof Observation observation && observation.getContext() == context) {
            observation.error(ex);
            return;
        }
        context.setError(ex);
    }

    private static String bindingFieldName(ServletRequestBindingException ex) {
        if (ex instanceof org.springframework.web.bind.MissingRequestHeaderException missingHeader) {
            return missingHeader.getHeaderName();
        }
        return "request";
    }

    private static String reasonForConstraint(@Nullable String constraintCode) {
        if (constraintCode == null) {
            return REASON_INVALID;
        }
        return switch (constraintCode) {
            case "NotNull", "NotBlank", "NotEmpty" -> REASON_REQUIRED;
            case "Positive" -> "must be greater than 0";
            case "PositiveOrZero" -> "must be greater than or equal to 0";
            case "Min", "DecimalMin" -> "must be greater than or equal to the minimum";
            case "Max", "DecimalMax" -> "must be less than or equal to the maximum";
            case "Size" -> "has an invalid size";
            case "Pattern" -> "has an invalid format";
            default -> REASON_INVALID;
        };
    }
}

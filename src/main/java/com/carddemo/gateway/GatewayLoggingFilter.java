/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 *
 * CardDemo - Gateway Logging Filter
 * 
 * This file implements comprehensive logging and tracing capabilities for the Spring Cloud Gateway,
 * providing structured JSON logging, correlation ID management, and distributed tracing integration.
 * 
 * Implements requirements from Section 5.4.2 Logging and Tracing Strategy:
 * - Structured JSON logging through Logback configuration with consistent field formats
 * - Application-specific trace correlation using Spring Cloud Sleuth
 * - Comprehensive error condition logging through standardized exception handling patterns
 * - Performance logging with method execution time tracking using AOP interceptors
 * 
 * Original COBOL reference patterns from:
 * - CSMSG01Y.cpy: Common message structures for standardized logging formats
 * - CSMSG02Y.cpy: Abend/error handling patterns for comprehensive error logging
 */

package com.carddemo.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * GatewayLoggingFilter - Spring Cloud Gateway Global Filter
 * 
 * Provides comprehensive logging, correlation ID management, and distributed tracing
 * capabilities for all API requests flowing through the gateway. Implements enterprise-grade
 * structured logging patterns and performance monitoring as specified in Section 5.4.2.
 * 
 * Key Features:
 * - Correlation ID generation and propagation for distributed tracing
 * - Structured JSON logging for centralized log aggregation (ELK stack compatible)
 * - Performance metrics collection for monitoring and alerting
 * - Spring Security context integration for audit trail purposes
 * - Request/response logging with configurable detail levels
 * - Error condition logging with standardized error handling patterns
 * 
 * Integration with Cross-Cutting Concerns:
 * - Spring Cloud Sleuth: Automatic trace correlation across service boundaries
 * - Spring Boot Actuator: Performance metrics exposed via /actuator/prometheus
 * - Spring Security: User context extraction for audit logging
 * - Micrometer: Custom metrics for business-specific monitoring
 * 
 * Original COBOL Pattern Equivalents:
 * - CSMSG01Y.cpy common messages -> Structured JSON log entry formats
 * - CSMSG02Y.cpy abend handling -> Comprehensive error logging with context preservation
 */
@Component
public class GatewayLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(GatewayLoggingFilter.class);
    private static final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    
    // Correlation ID header constants
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String TRACE_ID_HEADER = "X-Trace-ID";
    private static final String SPAN_ID_HEADER = "X-Span-ID";
    
    // MDC context keys for structured logging
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_SPAN_ID = "spanId";
    private static final String MDC_USER_ID = "userId";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_REQUEST_PATH = "requestPath";
    private static final String MDC_HTTP_METHOD = "httpMethod";
    private static final String MDC_REQUEST_SIZE = "requestSize";
    private static final String MDC_RESPONSE_SIZE = "responseSize";
    private static final String MDC_RESPONSE_STATUS = "responseStatus";
    private static final String MDC_PROCESSING_TIME = "processingTimeMs";
    private static final String MDC_CLIENT_IP = "clientIp";
    private static final String MDC_USER_AGENT = "userAgent";
    
    // Performance monitoring constants
    private static final String METRIC_REQUESTS_TOTAL = "gateway.requests.total";
    private static final String METRIC_REQUEST_DURATION = "gateway.request.duration";
    private static final String METRIC_REQUEST_SIZE = "gateway.request.size";
    private static final String METRIC_RESPONSE_SIZE = "gateway.response.size";
    
    // High-priority filter order for early request processing
    private static final int FILTER_ORDER = -1000;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // Performance monitoring metrics
    private Timer requestDurationTimer;
    private Counter requestCounter;
    private Counter errorCounter;
    
    /**
     * Initialize performance monitoring metrics during Spring bean lifecycle.
     * Creates Micrometer metrics for request duration, request count, and error count
     * that will be exposed via Spring Boot Actuator endpoints.
     */
    @PostConstruct
    public void initializeMetrics() {
        // Timer for measuring request processing duration
        requestDurationTimer = Timer.builder(METRIC_REQUEST_DURATION)
                .description("Gateway request processing duration")
                .tag("component", "gateway")
                .register(meterRegistry);
        
        // Counter for total request count with tags for monitoring
        requestCounter = Counter.builder(METRIC_REQUESTS_TOTAL)
                .description("Total gateway requests processed")
                .tag("component", "gateway")
                .register(meterRegistry);
        
        // Counter for error tracking with status code tagging
        errorCounter = Counter.builder("gateway.errors.total")
                .description("Total gateway errors encountered")
                .tag("component", "gateway")
                .register(meterRegistry);
        
        logger.info("GatewayLoggingFilter metrics initialized successfully");
    }
    
    /**
     * Global filter implementation for request/response logging and tracing.
     * 
     * Processing Flow:
     * 1. Extract/generate correlation IDs for distributed tracing
     * 2. Set up MDC context for structured logging
     * 3. Log incoming request with full context
     * 4. Process request through filter chain with timing
     * 5. Log response with performance metrics
     * 6. Clean up MDC context to prevent memory leaks
     * 
     * @param exchange ServerWebExchange containing request and response
     * @param chain GatewayFilterChain for continuing request processing
     * @return Mono<Void> for reactive processing continuation
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        
        // Generate or extract correlation ID for distributed tracing
        String correlationId = extractOrGenerateCorrelationId(request);
        String requestId = UUID.randomUUID().toString();
        
        // Add correlation headers to response for client tracking
        response.getHeaders().add(CORRELATION_ID_HEADER, correlationId);
        response.getHeaders().add("X-Request-ID", requestId);
        
        // Set up MDC context for structured logging
        setupMDCContext(request, correlationId, requestId);
        
        // Log incoming request with structured JSON format
        logIncomingRequest(request, correlationId, requestId);
        
        // Increment request counter for monitoring
        requestCounter.increment(
            "method", request.getMethodValue(),
            "path", sanitizePath(request.getPath().value())
        );
        
        // Process request through filter chain with reactive security context
        return ReactiveSecurityContextHolder.getContext()
                .cast(SecurityContext.class)
                .map(SecurityContext::getAuthentication)
                .defaultIfEmpty(createAnonymousAuthentication())
                .flatMap(authentication -> {
                    // Add user context to MDC for audit logging
                    addUserContextToMDC(authentication);
                    
                    // Continue processing with timing and error handling
                    return chain.filter(exchange)
                            .doOnSuccess(aVoid -> {
                                // Log successful response with performance metrics
                                long processingTime = System.currentTimeMillis() - startTime;
                                logResponse(exchange, correlationId, requestId, processingTime, null);
                                recordSuccessMetrics(exchange, processingTime);
                            })
                            .doOnError(throwable -> {
                                // Log error response with exception details
                                long processingTime = System.currentTimeMillis() - startTime;
                                logResponse(exchange, correlationId, requestId, processingTime, throwable);
                                recordErrorMetrics(exchange, throwable);
                            });
                })
                .contextWrite(Context.of(MDC_CORRELATION_ID, correlationId))
                .doFinally(signalType -> {
                    // Clean up MDC context to prevent memory leaks
                    cleanupMDCContext();
                });
    }
    
    /**
     * Extract correlation ID from request headers or generate new one.
     * Supports multiple header names for compatibility with different clients.
     * 
     * @param request ServerHttpRequest to extract correlation ID from
     * @return String correlation ID for distributed tracing
     */
    private String extractOrGenerateCorrelationId(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        
        // Try multiple header names for correlation ID
        String correlationId = headers.getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null) {
            correlationId = headers.getFirst("X-Request-ID");
        }
        if (correlationId == null) {
            correlationId = headers.getFirst("X-B3-TraceId");
        }
        
        // Generate new correlation ID if none found
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        
        return correlationId;
    }
    
    /**
     * Set up MDC (Mapped Diagnostic Context) for structured logging.
     * Populates common request attributes that will be included in all log entries.
     * 
     * @param request ServerHttpRequest containing request details
     * @param correlationId String correlation ID for tracing
     * @param requestId String unique request identifier
     */
    private void setupMDCContext(ServerHttpRequest request, String correlationId, String requestId) {
        MDC.put(MDC_CORRELATION_ID, correlationId);
        MDC.put(MDC_REQUEST_ID, requestId);
        MDC.put(MDC_REQUEST_PATH, request.getPath().value());
        MDC.put(MDC_HTTP_METHOD, request.getMethodValue());
        MDC.put(MDC_CLIENT_IP, getClientIpAddress(request));
        MDC.put(MDC_USER_AGENT, request.getHeaders().getFirst("User-Agent"));
        
        // Add Spring Cloud Sleuth trace IDs if available
        String traceId = request.getHeaders().getFirst("X-B3-TraceId");
        String spanId = request.getHeaders().getFirst("X-B3-SpanId");
        if (traceId != null) {
            MDC.put(MDC_TRACE_ID, traceId);
        }
        if (spanId != null) {
            MDC.put(MDC_SPAN_ID, spanId);
        }
        
        // Add request size for performance monitoring
        String contentLength = request.getHeaders().getFirst("Content-Length");
        if (contentLength != null) {
            MDC.put(MDC_REQUEST_SIZE, contentLength);
        }
    }
    
    /**
     * Add user authentication context to MDC for audit trail purposes.
     * Extracts user ID and roles from Spring Security Authentication object.
     * 
     * @param authentication Authentication object from Spring Security context
     */
    private void addUserContextToMDC(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            MDC.put(MDC_USER_ID, authentication.getName());
            MDC.put("userRoles", authentication.getAuthorities().toString());
        } else {
            MDC.put(MDC_USER_ID, "anonymous");
            MDC.put("userRoles", "ROLE_ANONYMOUS");
        }
    }
    
    /**
     * Log incoming request with structured JSON format.
     * Creates comprehensive log entry including all request details for audit trail.
     * 
     * @param request ServerHttpRequest to log
     * @param correlationId String correlation ID for tracing
     * @param requestId String unique request identifier
     */
    private void logIncomingRequest(ServerHttpRequest request, String correlationId, String requestId) {
        try {
            ObjectNode logEntry = objectMapper.createObjectNode();
            logEntry.put("eventType", "GATEWAY_REQUEST_RECEIVED");
            logEntry.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            logEntry.put("correlationId", correlationId);
            logEntry.put("requestId", requestId);
            logEntry.put("method", request.getMethodValue());
            logEntry.put("path", request.getPath().value());
            logEntry.put("query", request.getQueryParams().toString());
            logEntry.put("clientIp", getClientIpAddress(request));
            logEntry.put("userAgent", request.getHeaders().getFirst("User-Agent"));
            logEntry.put("contentType", request.getHeaders().getFirst("Content-Type"));
            logEntry.put("contentLength", request.getHeaders().getFirst("Content-Length"));
            
            // Add security headers for audit purposes
            logEntry.put("authorization", request.getHeaders().getFirst("Authorization") != null ? "present" : "absent");
            logEntry.put("origin", request.getHeaders().getFirst("Origin"));
            logEntry.put("referer", request.getHeaders().getFirst("Referer"));
            
            logger.info("Gateway request received: {}", logEntry.toString());
            
            // Audit log for compliance and security monitoring
            auditLogger.info("REQUEST_AUDIT correlationId={} method={} path={} clientIp={} userId={}",
                    correlationId,
                    request.getMethodValue(),
                    sanitizePath(request.getPath().value()),
                    getClientIpAddress(request),
                    MDC.get(MDC_USER_ID));
                    
        } catch (Exception e) {
            logger.warn("Failed to log incoming request: {}", e.getMessage());
        }
    }
    
    /**
     * Log response with performance metrics and error details if applicable.
     * Creates comprehensive log entry including response status, timing, and error information.
     * 
     * @param exchange ServerWebExchange containing request and response
     * @param correlationId String correlation ID for tracing
     * @param requestId String unique request identifier
     * @param processingTime long request processing time in milliseconds
     * @param throwable Throwable error if request failed, null for success
     */
    private void logResponse(ServerWebExchange exchange, String correlationId, String requestId, 
                           long processingTime, Throwable throwable) {
        try {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();
            
            // Update MDC with response details
            HttpStatus statusCode = response.getStatusCode();
            if (statusCode != null) {
                MDC.put(MDC_RESPONSE_STATUS, String.valueOf(statusCode.value()));
            }
            MDC.put(MDC_PROCESSING_TIME, String.valueOf(processingTime));
            
            ObjectNode logEntry = objectMapper.createObjectNode();
            logEntry.put("eventType", throwable == null ? "GATEWAY_REQUEST_COMPLETED" : "GATEWAY_REQUEST_ERROR");
            logEntry.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            logEntry.put("correlationId", correlationId);
            logEntry.put("requestId", requestId);
            logEntry.put("method", request.getMethodValue());
            logEntry.put("path", request.getPath().value());
            logEntry.put("processingTimeMs", processingTime);
            
            if (statusCode != null) {
                logEntry.put("statusCode", statusCode.value());
                logEntry.put("statusText", statusCode.getReasonPhrase());
            }
            
            // Add response headers for debugging
            String contentLength = response.getHeaders().getFirst("Content-Length");
            if (contentLength != null) {
                logEntry.put("responseSize", contentLength);
                MDC.put(MDC_RESPONSE_SIZE, contentLength);
            }
            
            // Add error details if request failed
            if (throwable != null) {
                logEntry.put("errorClass", throwable.getClass().getSimpleName());
                logEntry.put("errorMessage", throwable.getMessage());
                
                // Log stack trace for debugging (but not in structured log entry)
                logger.error("Gateway request error: {}", logEntry.toString(), throwable);
            } else {
                logger.info("Gateway request completed: {}", logEntry.toString());
            }
            
            // Performance audit log for SLA monitoring
            auditLogger.info("PERFORMANCE_AUDIT correlationId={} method={} path={} statusCode={} processingTimeMs={} userId={}",
                    correlationId,
                    request.getMethodValue(),
                    sanitizePath(request.getPath().value()),
                    statusCode != null ? statusCode.value() : "unknown",
                    processingTime,
                    MDC.get(MDC_USER_ID));
                    
        } catch (Exception e) {
            logger.warn("Failed to log response: {}", e.getMessage());
        }
    }
    
    /**
     * Record success metrics for performance monitoring.
     * Updates Micrometer metrics that are exposed via Spring Boot Actuator.
     * 
     * @param exchange ServerWebExchange containing request and response details
     * @param processingTime long request processing time in milliseconds
     */
    private void recordSuccessMetrics(ServerWebExchange exchange, long processingTime) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        
        // Record request duration timer with tags for detailed analysis
        Timer.Sample sample = Timer.start(meterRegistry);
        sample.stop(Timer.builder(METRIC_REQUEST_DURATION)
                .tag("method", request.getMethodValue())
                .tag("path", sanitizePath(request.getPath().value()))
                .tag("status", response.getStatusCode() != null ? 
                     String.valueOf(response.getStatusCode().value()) : "unknown")
                .register(meterRegistry));
        
        // Record request size metrics if available
        String contentLength = request.getHeaders().getFirst("Content-Length");
        if (contentLength != null) {
            try {
                meterRegistry.summary(METRIC_REQUEST_SIZE)
                        .record(Double.parseDouble(contentLength));
            } catch (NumberFormatException e) {
                logger.debug("Invalid content length: {}", contentLength);
            }
        }
        
        // Record response size metrics if available
        String responseLength = response.getHeaders().getFirst("Content-Length");
        if (responseLength != null) {
            try {
                meterRegistry.summary(METRIC_RESPONSE_SIZE)
                        .record(Double.parseDouble(responseLength));
            } catch (NumberFormatException e) {
                logger.debug("Invalid response length: {}", responseLength);
            }
        }
    }
    
    /**
     * Record error metrics for monitoring and alerting.
     * Increments error counters with detailed tags for root cause analysis.
     * 
     * @param exchange ServerWebExchange containing request details
     * @param throwable Throwable error that occurred during processing
     */
    private void recordErrorMetrics(ServerWebExchange exchange, Throwable throwable) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        
        // Increment error counter with detailed tags
        errorCounter.increment(
            "method", request.getMethodValue(),
            "path", sanitizePath(request.getPath().value()),
            "errorType", throwable.getClass().getSimpleName(),
            "status", response.getStatusCode() != null ? 
                     String.valueOf(response.getStatusCode().value()) : "unknown"
        );
        
        // Record error timing for performance analysis
        Timer.builder("gateway.error.duration")
                .tag("errorType", throwable.getClass().getSimpleName())
                .register(meterRegistry);
    }
    
    /**
     * Extract client IP address from request headers with proxy support.
     * Checks multiple headers commonly used by load balancers and proxies.
     * 
     * @param request ServerHttpRequest to extract IP from
     * @return String client IP address or "unknown" if not determinable
     */
    private String getClientIpAddress(ServerHttpRequest request) {
        // Check forwarded headers from load balancers/proxies
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // Fall back to remote address if available
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }
    
    /**
     * Sanitize path for logging to prevent log injection attacks.
     * Removes potential malicious characters while preserving path structure.
     * 
     * @param path String request path to sanitize
     * @return String sanitized path safe for logging
     */
    private String sanitizePath(String path) {
        if (path == null) {
            return "unknown";
        }
        
        // Remove potentially dangerous characters for log injection prevention
        return path.replaceAll("[\r\n\t]", "_")
                  .replaceAll("[<>\"'&]", "_");
    }
    
    /**
     * Create anonymous authentication object for requests without authentication.
     * Provides consistent user context for audit logging.
     * 
     * @return Authentication anonymous authentication object
     */
    private Authentication createAnonymousAuthentication() {
        return new Authentication() {
            @Override
            public String getName() {
                return "anonymous";
            }
            
            @Override
            public boolean isAuthenticated() {
                return false;
            }
            
            @Override
            public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
                // No-op for anonymous authentication
            }
            
            @Override
            public Object getCredentials() {
                return null;
            }
            
            @Override
            public Object getDetails() {
                return null;
            }
            
            @Override
            public Object getPrincipal() {
                return "anonymous";
            }
            
            @Override
            public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
                return java.util.Collections.singletonList(() -> "ROLE_ANONYMOUS");
            }
        };
    }
    
    /**
     * Clean up MDC context to prevent memory leaks.
     * Removes all MDC keys set by this filter.
     */
    private void cleanupMDCContext() {
        MDC.remove(MDC_CORRELATION_ID);
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
        MDC.remove(MDC_USER_ID);
        MDC.remove(MDC_REQUEST_ID);
        MDC.remove(MDC_REQUEST_PATH);
        MDC.remove(MDC_HTTP_METHOD);
        MDC.remove(MDC_REQUEST_SIZE);
        MDC.remove(MDC_RESPONSE_SIZE);
        MDC.remove(MDC_RESPONSE_STATUS);
        MDC.remove(MDC_PROCESSING_TIME);
        MDC.remove(MDC_CLIENT_IP);
        MDC.remove(MDC_USER_AGENT);
        MDC.remove("userRoles");
    }
    
    /**
     * Filter order for Spring Cloud Gateway filter chain.
     * High priority (negative value) ensures this filter runs early in the chain.
     * 
     * @return int filter order value
     */
    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }
}
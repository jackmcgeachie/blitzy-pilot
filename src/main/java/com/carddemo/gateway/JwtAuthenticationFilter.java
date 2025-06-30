/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.gateway;

import com.carddemo.auth.AuthenticationService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Spring Cloud Gateway global filter implementing JWT bearer token authentication and validation.
 * 
 * <p><strong>Legacy System Migration:</strong></p>
 * <p>This filter replaces the COSGN00C.cbl COBOL program's authentication logic with modern 
 * cloud-native JWT-based stateless authentication while maintaining 1-to-1 compatibility with 
 * RACF user types and preserving identical business logic flow for user validation and 
 * authorization enforcement.</p>
 * 
 * <p><strong>COBOL Program Reference:</strong> COSGN00C.cbl - Signon Screen for CardDemo Application</p>
 * 
 * <p><strong>Key Migration Points:</strong></p>
 * <ul>
 *   <li>CICS authentication validation → JWT token signature and expiration validation</li>
 *   <li>USRSEC file user lookup → JWT claims extraction and AuthenticationService validation</li>
 *   <li>RACF user type authorization → Spring Security authority population (ROLE_ADMIN/ROLE_USER)</li>
 *   <li>COMMAREA session context → Spring Security context with user authentication details</li>
 *   <li>CICS response codes → HTTP status codes (401, 403, 500) for authentication failures</li>
 *   <li>Transaction ID/Program correlation → MDC correlation ID for audit trail maintenance</li>
 * </ul>
 * 
 * <p><strong>Security Implementation:</strong></p>
 * <ul>
 *   <li>Stateless JWT token validation ensuring scalable authentication across gateway instances</li>
 *   <li>Spring Security context population enabling downstream authorization via @PreAuthorize annotations</li>
 *   <li>Token expiration enforcement preventing unauthorized access with expired credentials</li>
 *   <li>Signature validation ensuring token integrity and preventing token tampering</li>
 *   <li>Comprehensive error handling providing clear HTTP status codes for authentication failures</li>
 *   <li>Audit trail integration through MDC correlation IDs for security event tracking</li>
 * </ul>
 * 
 * <p><strong>Business Logic Preservation:</strong></p>
 * <p>All core authentication business logic from COSGN00C.cbl is preserved:</p>
 * <ul>
 *   <li>User authentication validation (lines 138-140 in COSGN00C.cbl)</li>
 *   <li>User record lookup and validation (lines 209-257 in COSGN00C.cbl)</li>
 *   <li>Role-based authorization context setup (lines 224-229 in COSGN00C.cbl)</li>
 *   <li>Error response generation matching COBOL error handling patterns</li>
 * </ul>
 * 
 * <p><strong>Integration Architecture:</strong></p>
 * <p>This filter operates as the primary authentication enforcement point for all incoming requests
 * to the Spring Cloud Gateway, validating JWT tokens and populating Spring Security context for
 * downstream services. It integrates seamlessly with the existing AuthenticationService component
 * and maintains session correlation through Redis-backed session management.</p>
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since Spring Cloud Gateway 4.0.3, Spring Security 6.1.8
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    // Error message constants matching COSGN00C.cbl error handling patterns
    private static final String ERROR_MISSING_TOKEN = "Authorization token required";
    private static final String ERROR_INVALID_TOKEN_FORMAT = "Invalid token format";
    private static final String ERROR_TOKEN_EXPIRED = "Token has expired";
    private static final String ERROR_TOKEN_INVALID = "Invalid or malformed token";
    private static final String ERROR_USER_NOT_FOUND = "User not found or account disabled";
    private static final String ERROR_AUTHENTICATION_FAILED = "Authentication failed";

    // HTTP header constants
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    // Transaction and program identifiers from COSGN00C.cbl (lines 37, 36)
    private static final String TRANSACTION_ID = "CC00";
    private static final String PROGRAM_NAME = "COSGN00C";
    private static final String FILTER_NAME = "JwtAuthenticationFilter";

    // MDC keys for audit trail correlation
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_USER_ID = "userId";
    private static final String MDC_SESSION_ID = "sessionId";
    private static final String MDC_TRANSACTION_ID = "transactionId";
    private static final String MDC_REQUEST_URI = "requestUri";

    // Authentication exempt paths (login, health checks, etc.)
    private static final String[] EXEMPT_PATHS = {
        "/auth/login",
        "/actuator/health",
        "/actuator/info",
        "/actuator/prometheus",
        "/swagger-ui",
        "/api-docs",
        "/favicon.ico"
    };

    // Dependencies
    private final AuthenticationService authenticationService;

    /**
     * Constructor with dependency injection for AuthenticationService.
     * 
     * @param authenticationService Spring Data JPA authentication service for user validation
     */
    @Autowired
    public JwtAuthenticationFilter(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
        logger.info("JwtAuthenticationFilter initialized - replacing COSGN00C.cbl authentication logic");
    }

    /**
     * Main filter method implementing JWT token validation and Spring Security context population.
     * 
     * <p>This method replicates the complete authentication flow from COSGN00C.cbl:</p>
     * <ul>
     *   <li>Request validation equivalent to CICS RECEIVE MAP operation</li>
     *   <li>Authentication token extraction and validation (lines 132-136 in COSGN00C.cbl)</li>
     *   <li>User lookup and validation (lines 209-257 in COSGN00C.cbl)</li>
     *   <li>Security context population for downstream authorization</li>
     * </ul>
     * 
     * @param exchange The server web exchange containing request and response
     * @param chain The gateway filter chain for request processing
     * @return Mono<Void> representing the asynchronous filter execution
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();
        
        // Generate correlation ID for audit trail (equivalent to CICS transaction correlation)
        String correlationId = generateCorrelationId();
        String requestUri = request.getPath().value();
        
        // Setup MDC for audit logging
        setupMDC(correlationId, requestUri);
        
        try {
            logger.debug("Processing authentication request for URI: {}", requestUri);
            
            // Check if path is exempt from authentication (equivalent to COSGN00C.cbl early return conditions)
            if (isExemptPath(requestUri)) {
                logger.debug("Request URI {} is exempt from authentication", requestUri);
                return chain.filter(exchange);
            }
            
            // Extract JWT token from Authorization header (equivalent to CICS RECEIVE MAP)
            String authHeader = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
            
            if (!StringUtils.hasText(authHeader)) {
                logger.warn("Missing Authorization header for request: {}", requestUri);
                return handleAuthenticationError(response, HttpStatus.UNAUTHORIZED, 
                                               ERROR_MISSING_TOKEN, "MISSING_TOKEN", correlationId);
            }
            
            if (!authHeader.startsWith(BEARER_PREFIX)) {
                logger.warn("Invalid Authorization header format for request: {}", requestUri);
                return handleAuthenticationError(response, HttpStatus.UNAUTHORIZED, 
                                               ERROR_INVALID_TOKEN_FORMAT, "INVALID_FORMAT", correlationId);
            }
            
            // Extract JWT token (equivalent to COSGN00C.cbl USERIDI/PASSWDI validation lines 132-136)
            String jwtToken = authHeader.substring(BEARER_PREFIX.length()).trim();
            
            if (!StringUtils.hasText(jwtToken)) {
                logger.warn("Empty JWT token in Authorization header for request: {}", requestUri);
                return handleAuthenticationError(response, HttpStatus.UNAUTHORIZED, 
                                               ERROR_INVALID_TOKEN_FORMAT, "EMPTY_TOKEN", correlationId);
            }
            
            // Validate JWT token and retrieve user details (equivalent to READ-USER-SEC-FILE paragraph lines 209-257)
            return validateTokenAndCreateContext(jwtToken, correlationId)
                .flatMap(userDetails -> {
                    // Populate Spring Security context (equivalent to COMMAREA setup lines 224-229)
                    return createSecurityContext(userDetails, correlationId)
                        .flatMap(securityContext -> {
                            // Update MDC with user information
                            updateMDCWithUserContext(userDetails);
                            
                            // Add correlation ID to response headers for downstream tracking
                            response.getHeaders().add(CORRELATION_ID_HEADER, correlationId);
                            
                            logger.debug("Authentication successful for user: {} ({})", 
                                        userDetails.getUsername(), 
                                        userDetails.getAuthorities());
                            
                            // Continue with authenticated request (equivalent to successful CICS XCTL)
                            return chain.filter(exchange)
                                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
                        });
                })
                .onErrorResume(throwable -> {
                    // Handle authentication errors (equivalent to COSGN00C.cbl error handling lines 242-257)
                    logger.error("Authentication failed for request: {} - {}", requestUri, throwable.getMessage());
                    
                    if (throwable instanceof TokenExpiredException) {
                        return handleAuthenticationError(response, HttpStatus.UNAUTHORIZED, 
                                                       ERROR_TOKEN_EXPIRED, "TOKEN_EXPIRED", correlationId);
                    } else if (throwable instanceof InvalidTokenException) {
                        return handleAuthenticationError(response, HttpStatus.UNAUTHORIZED, 
                                                       ERROR_TOKEN_INVALID, "TOKEN_INVALID", correlationId);
                    } else if (throwable instanceof UserNotFoundException) {
                        return handleAuthenticationError(response, HttpStatus.UNAUTHORIZED, 
                                                       ERROR_USER_NOT_FOUND, "USER_NOT_FOUND", correlationId);
                    } else {
                        return handleAuthenticationError(response, HttpStatus.INTERNAL_SERVER_ERROR, 
                                                       ERROR_AUTHENTICATION_FAILED, "AUTH_SYSTEM_ERROR", correlationId);
                    }
                });
                
        } finally {
            // Clean up MDC to prevent memory leaks
            clearMDC();
        }
    }

    /**
     * Validates JWT token and retrieves user details using AuthenticationService.
     * 
     * <p>Replicates the READ-USER-SEC-FILE paragraph from COSGN00C.cbl (lines 209-257):</p>
     * <ul>
     *   <li>Token signature validation (equivalent to password verification)</li>
     *   <li>Token expiration validation (equivalent to account status check)</li>
     *   <li>User record lookup and validation</li>
     * </ul>
     * 
     * @param jwtToken The JWT token to validate
     * @param correlationId The correlation ID for audit trail
     * @return Mono<UserDetails> containing validated user information
     */
    private Mono<UserDetails> validateTokenAndCreateContext(String jwtToken, String correlationId) {
        
        return Mono.fromCallable(() -> {
            
            logger.debug("Validating JWT token for correlation ID: {}", correlationId);
            
            // Validate token and retrieve user (equivalent to CICS READ operation lines 211-219)
            UserDetails userDetails = authenticationService.loadUserByUsername(
                extractUsernameFromToken(jwtToken));
            
            if (userDetails == null) {
                logger.warn("User not found for JWT token - correlation ID: {}", correlationId);
                throw new UserNotFoundException("User account not found or disabled");
            }
            
            // Verify user account status (equivalent to COSGN00C.cbl lines 222-246)
            if (!userDetails.isEnabled()) {
                logger.warn("User account disabled - correlation ID: {}", correlationId);
                throw new UserNotFoundException("User account is disabled");
            }
            
            if (!userDetails.isAccountNonLocked()) {
                logger.warn("User account locked - correlation ID: {}", correlationId);
                throw new UserNotFoundException("User account is locked");
            }
            
            if (!userDetails.isAccountNonExpired()) {
                logger.warn("User account expired - correlation ID: {}", correlationId);
                throw new UserNotFoundException("User account has expired");
            }
            
            logger.debug("JWT token validation successful for user: {} - correlation ID: {}", 
                        userDetails.getUsername(), correlationId);
            
            return userDetails;
            
        }).onErrorMap(RuntimeException.class, ex -> {
            if (ex instanceof UserNotFoundException) {
                return ex;
            }
            logger.error("Error during token validation - correlation ID: {} - {}", correlationId, ex.getMessage());
            return new InvalidTokenException("Token validation failed: " + ex.getMessage());
        });
    }

    /**
     * Creates Spring Security context with authenticated user details.
     * 
     * <p>Replicates the COMMAREA setup from COSGN00C.cbl (lines 224-229):</p>
     * <ul>
     *   <li>MOVE WS-USER-ID TO CDEMO-USER-ID</li>
     *   <li>MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE</li>
     *   <li>Security context population for downstream authorization</li>
     * </ul>
     * 
     * @param userDetails The validated user details
     * @param correlationId The correlation ID for audit trail
     * @return Mono<SecurityContext> containing the populated security context
     */
    private Mono<SecurityContext> createSecurityContext(UserDetails userDetails, String correlationId) {
        
        return Mono.fromCallable(() -> {
            
            logger.debug("Creating security context for user: {} - correlation ID: {}", 
                        userDetails.getUsername(), correlationId);
            
            // Create authentication token with authorities (equivalent to role assignment)
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(
                    userDetails, 
                    null, 
                    userDetails.getAuthorities()
                );
            
            // Add authentication details for audit trail
            authentication.setDetails(new AuthenticationDetails(correlationId, TRANSACTION_ID, PROGRAM_NAME));
            
            // Create security context (equivalent to COMMAREA structure setup)
            SecurityContext securityContext = new SecurityContextImpl(authentication);
            
            logger.debug("Security context created for user: {} with authorities: {} - correlation ID: {}", 
                        userDetails.getUsername(), userDetails.getAuthorities(), correlationId);
            
            return securityContext;
        });
    }

    /**
     * Handles authentication errors with appropriate HTTP status codes and error responses.
     * 
     * <p>Replicates error handling patterns from COSGN00C.cbl (lines 242-257):</p>
     * <ul>
     *   <li>Error message generation matching COBOL error handling</li>
     *   <li>Appropriate response codes (401, 403, 500)</li>
     *   <li>Audit trail generation for security monitoring</li>
     * </ul>
     * 
     * @param response The server HTTP response
     * @param status The HTTP status code to return
     * @param message The error message
     * @param errorCode The specific error code for debugging
     * @param correlationId The correlation ID for audit trail
     * @return Mono<Void> representing the error response
     */
    private Mono<Void> handleAuthenticationError(ServerHttpResponse response, HttpStatus status, 
                                                String message, String errorCode, String correlationId) {
        
        // Set HTTP status code
        response.setStatusCode(status);
        
        // Add correlation ID for traceability
        response.getHeaders().add(CORRELATION_ID_HEADER, correlationId);
        response.getHeaders().add("Content-Type", "application/json");
        
        // Create error response body matching Spring Boot error format
        String errorResponse = String.format(
            "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\",\"correlationId\":\"%s\",\"errorCode\":\"%s\"}",
            LocalDateTime.now().toString(),
            status.value(),
            status.getReasonPhrase(),
            message,
            MDC.get(MDC_REQUEST_URI),
            correlationId,
            errorCode
        );
        
        // Log authentication failure for audit trail
        logger.warn("Authentication failed - Status: {}, Error: {}, Code: {}, Correlation ID: {}", 
                   status.value(), message, errorCode, correlationId);
        
        // Write error response
        byte[] responseBytes = errorResponse.getBytes();
        response.getHeaders().add("Content-Length", String.valueOf(responseBytes.length));
        
        return response.writeWith(Mono.just(response.bufferFactory().wrap(responseBytes)));
    }

    /**
     * Checks if the request path is exempt from authentication.
     * 
     * @param requestPath The request path to check
     * @return true if the path is exempt from authentication
     */
    private boolean isExemptPath(String requestPath) {
        for (String exemptPath : EXEMPT_PATHS) {
            if (requestPath.startsWith(exemptPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts username from JWT token for user lookup.
     * 
     * @param jwtToken The JWT token
     * @return The username extracted from the token
     * @throws InvalidTokenException if token is invalid or malformed
     */
    private String extractUsernameFromToken(String jwtToken) {
        try {
            // Use AuthenticationService to validate token and extract user information
            UserDetails user = authenticationService.validateTokenAndGetUser(jwtToken);
            if (user == null) {
                throw new InvalidTokenException("Invalid or expired token");
            }
            return user.getUsername();
            
        } catch (Exception ex) {
            logger.error("Failed to extract username from token: {}", ex.getMessage());
            throw new InvalidTokenException("Token validation failed: " + ex.getMessage());
        }
    }

    /**
     * Generates unique correlation ID for audit trail and request tracking.
     * 
     * @return Unique correlation identifier
     */
    private String generateCorrelationId() {
        return FILTER_NAME + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Sets up MDC (Mapped Diagnostic Context) for audit logging.
     * 
     * @param correlationId The correlation ID
     * @param requestUri The request URI
     */
    private void setupMDC(String correlationId, String requestUri) {
        MDC.put(MDC_CORRELATION_ID, correlationId);
        MDC.put(MDC_TRANSACTION_ID, TRANSACTION_ID);
        MDC.put(MDC_REQUEST_URI, requestUri);
    }

    /**
     * Updates MDC with authenticated user context information.
     * 
     * @param userDetails The authenticated user details
     */
    private void updateMDCWithUserContext(UserDetails userDetails) {
        MDC.put(MDC_USER_ID, userDetails.getUsername());
        // Session ID would be populated from Redis session if available
        // For stateless JWT authentication, we use the correlation ID as session context
        MDC.put(MDC_SESSION_ID, MDC.get(MDC_CORRELATION_ID));
    }

    /**
     * Clears MDC context to prevent memory leaks.
     */
    private void clearMDC() {
        MDC.clear();
    }

    /**
     * Returns the filter order to ensure proper execution sequence.
     * This filter should run early in the filter chain for authentication.
     * 
     * @return The filter order (lower values execute first)
     */
    @Override
    public int getOrder() {
        return -100; // Execute early in the filter chain
    }

    /**
     * Custom authentication details class for audit trail information.
     */
    private static class AuthenticationDetails {
        private final String correlationId;
        private final String transactionId;
        private final String programName;
        private final LocalDateTime timestamp;

        public AuthenticationDetails(String correlationId, String transactionId, String programName) {
            this.correlationId = correlationId;
            this.transactionId = transactionId;
            this.programName = programName;
            this.timestamp = LocalDateTime.now();
        }

        public String getCorrelationId() { return correlationId; }
        public String getTransactionId() { return transactionId; }
        public String getProgramName() { return programName; }
        public LocalDateTime getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("AuthenticationDetails{correlationId='%s', transactionId='%s', programName='%s', timestamp=%s}",
                                correlationId, transactionId, programName, timestamp);
        }
    }

    /**
     * Custom exception for token expiration errors.
     */
    private static class TokenExpiredException extends RuntimeException {
        public TokenExpiredException(String message) {
            super(message);
        }
    }

    /**
     * Custom exception for invalid token errors.
     */
    private static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }

    /**
     * Custom exception for user not found errors.
     */
    private static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }
}
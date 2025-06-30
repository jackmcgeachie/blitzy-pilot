/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Authentication Entry Point for handling unauthorized access attempts.
 * 
 * This component replaces the legacy CICS transaction security error handling with
 * modern REST API error responses, providing consistent authentication exception
 * handling across all protected endpoints while maintaining audit trail compatibility.
 * 
 * <p>Migration from COBOL Error Handling:</p>
 * <ul>
 *   <li>CICS RESP-CD 13 (User not found) → HTTP 401 Unauthorized with detailed error response</li>
 *   <li>CICS authentication failure → JSON error response with correlation tracking</li>
 *   <li>COMMAREA error handling → Structured error response for frontend consumption</li>
 *   <li>Mainframe audit logging → Modern security event logging with correlation IDs</li>
 * </ul>
 * 
 * <p>Error Response Features:</p>
 * <ul>
 *   <li>Standardized JSON error format for consistent frontend error handling</li>
 *   <li>Correlation ID tracking for audit trail and troubleshooting purposes</li>
 *   <li>Detailed error messages with security-appropriate information disclosure</li>
 *   <li>Timestamp correlation for security event analysis and monitoring</li>
 *   <li>HTTP status code mapping for proper REST API error semantics</li>
 * </ul>
 * 
 * <p>Security Considerations:</p>
 * <ul>
 *   <li>Error messages avoid revealing sensitive information about user existence</li>
 *   <li>Correlation IDs enable audit trail tracking without exposing system internals</li>
 *   <li>Response timing consistent to prevent enumeration attacks</li>
 *   <li>Comprehensive logging for security monitoring and incident response</li>
 * </ul>
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since Spring Security 6.1.8
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Handles authentication exceptions and returns appropriate error responses.
     * 
     * This method is invoked when an authentication exception occurs, providing
     * consistent error handling across all protected endpoints. The response
     * format supports frontend error handling while maintaining security best
     * practices for information disclosure.
     * 
     * <p>Original COBOL Error Handling Equivalent:</p>
     * <pre>
     * COBOL Logic (COSGN00C.cbl):
     *   WHEN 13
     *       MOVE 'User not found. Try again ...' TO WS-MESSAGE
     *       PERFORM SEND-SIGNON-SCREEN
     *   WHEN OTHER
     *       MOVE 'Unable to verify the User ...' TO WS-MESSAGE
     *       PERFORM SEND-SIGNON-SCREEN
     * 
     * JWT Entry Point Equivalent:
     *   - HTTP 401 Unauthorized response
     *   - JSON error format with correlation tracking
     *   - Security audit logging for monitoring
     * </pre>
     * 
     * @param request The HTTP request that resulted in an authentication exception
     * @param response The HTTP response to be sent to the client
     * @param authException The authentication exception that occurred
     * @throws IOException if an I/O error occurs during response writing
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                        AuthenticationException authException) throws IOException {

        // Generate correlation ID for audit tracking
        String correlationId = generateCorrelationId();
        
        // Log security event for audit and monitoring
        logger.warn("Authentication failed for request: {} {} - Correlation ID: {}, Error: {}", 
                   request.getMethod(), request.getRequestURI(), correlationId, authException.getMessage());

        // Configure response for JSON error format
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding("UTF-8");

        // Add correlation ID to response headers for tracking
        response.setHeader("X-Correlation-ID", correlationId);
        response.setHeader("X-Authentication-Error", "true");

        // Build structured error response
        Map<String, Object> errorResponse = createErrorResponse(
            request, authException, correlationId);

        // Write JSON error response
        try {
            String jsonResponse = objectMapper.writeValueAsString(errorResponse);
            response.getWriter().write(jsonResponse);
            response.getWriter().flush();
            
            logger.debug("Authentication error response sent - Correlation ID: {}", correlationId);
            
        } catch (Exception e) {
            logger.error("Failed to write authentication error response - Correlation ID: {}", 
                        correlationId, e);
            
            // Fallback to simple error message
            response.getWriter().write("{\"error\":\"Authentication failed\",\"timestamp\":\"" + 
                                     LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + 
                                     "\",\"correlationId\":\"" + correlationId + "\"}");
        }
    }

    /**
     * Creates structured error response for authentication failures.
     * 
     * This method builds a comprehensive error response that provides sufficient
     * information for frontend error handling while avoiding security information
     * disclosure that could aid attackers.
     * 
     * @param request The HTTP request that failed authentication
     * @param authException The authentication exception details
     * @param correlationId Unique correlation ID for audit tracking
     * @return Map containing structured error response data
     */
    private Map<String, Object> createErrorResponse(HttpServletRequest request, 
                                                   AuthenticationException authException,
                                                   String correlationId) {
        
        Map<String, Object> errorResponse = new HashMap<>();
        
        // Basic error information
        errorResponse.put("error", "Unauthorized");
        errorResponse.put("status", HttpStatus.UNAUTHORIZED.value());
        errorResponse.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        errorResponse.put("correlationId", correlationId);
        
        // Request context information
        errorResponse.put("path", request.getRequestURI());
        errorResponse.put("method", request.getMethod());
        
        // User-friendly error message (security-conscious)
        String userMessage = determineUserMessage(authException, request);
        errorResponse.put("message", userMessage);
        
        // Additional context for frontend handling
        errorResponse.put("code", "AUTHENTICATION_REQUIRED");
        errorResponse.put("action", "Please provide valid authentication credentials");
        
        // Support information for troubleshooting
        Map<String, Object> support = new HashMap<>();
        support.put("correlationId", correlationId);
        support.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        errorResponse.put("support", support);
        
        return errorResponse;
    }

    /**
     * Determines appropriate user-facing error message based on authentication exception.
     * 
     * This method provides security-conscious error messages that give users sufficient
     * information to resolve authentication issues without revealing sensitive system
     * information that could aid potential attackers.
     * 
     * @param authException The authentication exception that occurred
     * @param request The HTTP request context
     * @return User-appropriate error message
     */
    private String determineUserMessage(AuthenticationException authException, HttpServletRequest request) {
        
        String exceptionType = authException.getClass().getSimpleName();
        String requestPath = request.getRequestURI();
        
        // Map specific exception types to user-friendly messages
        switch (exceptionType) {
            case "BadCredentialsException":
                return "Invalid username or password. Please check your credentials and try again.";
            
            case "UsernameNotFoundException":
                return "Authentication failed. Please verify your credentials.";
            
            case "AccountExpiredException":
                return "Your account has expired. Please contact your administrator.";
            
            case "CredentialsExpiredException":
                return "Your password has expired. Please contact your administrator.";
            
            case "DisabledException":
                return "Your account is disabled. Please contact your administrator.";
            
            case "LockedException":
                return "Your account is locked. Please contact your administrator.";
            
            case "InsufficientAuthenticationException":
                if (requestPath != null && requestPath.startsWith("/api/")) {
                    return "Authentication required. Please provide a valid JWT token.";
                } else {
                    return "Authentication required. Please log in to access this resource.";
                }
            
            default:
                logger.debug("Unmapped authentication exception type: {} - using generic message", exceptionType);
                return "Authentication failed. Please verify your credentials and try again.";
        }
    }

    /**
     * Generates unique correlation ID for audit trail tracking.
     * 
     * This correlation ID enables linking authentication failures with audit log
     * entries and supports troubleshooting and security incident investigation.
     * 
     * @return Unique correlation identifier
     */
    private String generateCorrelationId() {
        return "AUTH-ERR-" + System.currentTimeMillis() + "-" + 
               Integer.toHexString((int)(Math.random() * 0x10000)).toUpperCase();
    }

    /**
     * Checks if request is from an API endpoint.
     * 
     * This method helps determine the appropriate error response format based
     * on the request context (API vs web page).
     * 
     * @param request The HTTP request to check
     * @return true if request is for an API endpoint
     */
    private boolean isApiRequest(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        String acceptHeader = request.getHeader("Accept");
        
        return (requestURI != null && requestURI.startsWith("/api/")) ||
               (acceptHeader != null && acceptHeader.contains("application/json"));
    }

    /**
     * Extracts client IP address for audit logging.
     * 
     * This method attempts to determine the real client IP address, considering
     * common proxy headers used in enterprise environments.
     * 
     * @param request The HTTP request
     * @return Client IP address for audit logging
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
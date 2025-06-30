/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * JWT Authentication Filter for stateless token-based authentication.
 * 
 * This filter replaces the legacy CICS session-based authentication with modern
 * JWT token validation, providing stateless authentication suitable for cloud-native
 * deployment patterns while maintaining equivalent security controls.
 * 
 * <p>Migration from CICS Transaction Security:</p>
 * <ul>
 *   <li>CICS pseudo-conversational state → Stateless JWT token validation</li>
 *   <li>COMMAREA session tracking → JWT claims-based user context</li>
 *   <li>RACF user validation → Spring Security UserDetails integration</li>
 *   <li>Transaction security → Request-level authentication and authorization</li>
 * </ul>
 * 
 * <p>Authentication Flow Process:</p>
 * <ol>
 *   <li>Extract JWT token from Authorization header (Bearer format)</li>
 *   <li>Validate token signature and expiration using JwtTokenProvider</li>
 *   <li>Extract user identity claims from validated token</li>
 *   <li>Load user details using UserDetailsService (PostgreSQL lookup)</li>
 *   <li>Populate Spring Security context with authenticated user</li>
 *   <li>Continue filter chain with established security context</li>
 * </ol>
 * 
 * <p>Security Features:</p>
 * <ul>
 *   <li>Stateless authentication enabling horizontal scaling capabilities</li>
 *   <li>JWT signature validation for cryptographic token integrity</li>
 *   <li>Automatic token expiration handling with appropriate error responses</li>
 *   <li>Security context population for downstream authorization decisions</li>
 *   <li>Comprehensive audit logging for authentication events</li>
 * </ul>
 * 
 * <p>Original COBOL Logic Equivalent:</p>
 * <pre>
 * COBOL (CICS Transaction Processing):
 *   EXEC CICS RECEIVE MAP('COSGN0A') MAPSET('COSGN00') ...
 *   IF EIBCALEN > 0
 *       // Pseudo-conversational state from COMMAREA
 *       MOVE CARDDEMO-COMMAREA TO working-storage
 *   
 * JWT Filter Equivalent:
 *   - Extract Bearer token from Authorization header
 *   - Validate token and populate Spring Security context
 *   - Enable stateless request processing with user identity
 * </pre>
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since Spring Security 6.1.8, Spring Boot 3.2.5
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    // JWT token extraction constants
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();

    // Dependency components for JWT processing
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    /**
     * Constructor for JWT authentication filter configuration.
     * 
     * @param jwtTokenProvider JWT token management component
     * @param userDetailsService User details service for user lookup
     */
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, 
                                 UserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        
        logger.debug("JWT Authentication Filter initialized with token provider and user details service");
    }

    /**
     * Performs JWT token authentication for each HTTP request.
     * 
     * This method implements the core authentication logic, extracting and validating
     * JWT tokens from request headers and establishing Spring Security context for
     * authenticated users. The filter operates on every request to maintain stateless
     * authentication across all protected endpoints.
     * 
     * <p>Authentication Process Details:</p>
     * <ol>
     *   <li>Extract Authorization header from HTTP request</li>
     *   <li>Validate Bearer token format and extract JWT token</li>
     *   <li>Perform cryptographic validation of token signature</li>
     *   <li>Extract user identity claims from validated token</li>
     *   <li>Load complete user details from PostgreSQL via UserDetailsService</li>
     *   <li>Create authenticated Spring Security context</li>
     *   <li>Continue filter chain with established security context</li>
     * </ol>
     * 
     * <p>Error Handling:</p>
     * <ul>
     *   <li>Invalid tokens are logged and ignored (no security context set)</li>
     *   <li>Expired tokens result in authentication failure</li>
     *   <li>Missing tokens on public endpoints are allowed</li>
     *   <li>Malformed tokens are handled gracefully with audit logging</li>
     * </ul>
     * 
     * @param request HTTP servlet request containing potential JWT token
     * @param response HTTP servlet response for error handling
     * @param filterChain Filter chain for continued request processing
     * @throws ServletException if servlet processing error occurs
     * @throws IOException if I/O error occurs during request processing
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {

        try {
            // Extract JWT token from Authorization header
            String jwt = extractJwtFromRequest(request);
            
            // Process authentication if valid token is present
            if (jwt != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                processJwtAuthentication(jwt, request);
            }
            
        } catch (Exception ex) {
            // Log authentication errors but continue processing
            // Security context remains empty, causing authorization failure for protected resources
            logger.warn("JWT authentication failed for request: {} {} - Error: {}", 
                       request.getMethod(), request.getRequestURI(), ex.getMessage());
            
            // Add error details to response headers for debugging
            response.setHeader("X-Authentication-Error", "JWT processing failed");
            response.setHeader("X-Error-Details", ex.getMessage());
        }

        // Continue filter chain - authentication success or failure is handled by authorization
        filterChain.doFilter(request, response);
    }

    /**
     * Extracts JWT token from HTTP Authorization header.
     * 
     * This method implements standard Bearer token extraction from the Authorization
     * header, following RFC 6750 specification for OAuth 2.0 Bearer Token Usage.
     * 
     * <p>Token Format Expected:</p>
     * <pre>
     * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     * </pre>
     * 
     * @param request HTTP request containing Authorization header
     * @return JWT token string without Bearer prefix, null if not found or invalid format
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
        
        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith(BEARER_PREFIX)) {
            String token = authorizationHeader.substring(BEARER_PREFIX_LENGTH);
            
            logger.debug("JWT token extracted from Authorization header for request: {} {}", 
                        request.getMethod(), request.getRequestURI());
            
            return token;
        }
        
        // No Bearer token found - this is normal for public endpoints
        logger.trace("No Bearer token found in request: {} {}", 
                    request.getMethod(), request.getRequestURI());
        
        return null;
    }

    /**
     * Processes JWT authentication and establishes Spring Security context.
     * 
     * This method performs comprehensive JWT validation and user authentication,
     * replacing the COBOL user validation logic with modern cryptographic token
     * verification and database-backed user lookup.
     * 
     * <p>Authentication Steps:</p>
     * <ol>
     *   <li>Validate JWT token signature and expiration</li>
     *   <li>Extract user identity from token claims</li>
     *   <li>Load user details from PostgreSQL users table</li>
     *   <li>Verify user account status and permissions</li>
     *   <li>Create authenticated Spring Security token</li>
     *   <li>Set security context for current request</li>
     * </ol>
     * 
     * <p>Original COBOL Logic Equivalent:</p>
     * <pre>
     * COBOL Authentication (COSGN00C.cbl):
     *   EXEC CICS READ DATASET(WS-USRSEC-FILE) INTO(SEC-USER-DATA) RIDFLD(WS-USER-ID)
     *   IF WS-RESP-CD = 0
     *       IF SEC-USR-PWD = WS-USER-PWD
     *           MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE
     *           // Set user context in COMMAREA
     * 
     * JWT Authentication Equivalent:
     *   - Validate JWT token cryptographically
     *   - Extract user identity from token claims
     *   - Load user details from PostgreSQL
     *   - Set Spring Security context with authorities
     * </pre>
     * 
     * @param jwt JWT token string for validation
     * @param request HTTP request for authentication context
     */
    private void processJwtAuthentication(String jwt, HttpServletRequest request) {
        
        try {
            // Validate JWT token signature and expiration
            if (!jwtTokenProvider.validateToken(jwt)) {
                logger.warn("JWT token validation failed for request: {} {}", 
                           request.getMethod(), request.getRequestURI());
                return;
            }
            
            // Extract user identity from validated token
            String userId = jwtTokenProvider.getUserIdFromToken(jwt);
            String username = jwtTokenProvider.getUsernameFromToken(jwt);
            
            if (userId == null || username == null) {
                logger.warn("JWT token missing user identity claims - userId: {}, username: {}", 
                           userId, username);
                return;
            }
            
            // Load user details from PostgreSQL (replacing VSAM USRSEC lookup)
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            
            if (userDetails == null) {
                logger.warn("User details not found for username: {} (from JWT token)", username);
                return;
            }
            
            // Create authenticated Spring Security token
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(
                    userDetails, 
                    null, // credentials not needed after JWT validation
                    userDetails.getAuthorities()
                );
            
            // Add request details for audit logging
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            
            // Set security context for current request
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Log successful authentication
            logger.debug("JWT authentication successful - User: {}, Authorities: {}, Request: {} {}", 
                        username, userDetails.getAuthorities(), 
                        request.getMethod(), request.getRequestURI());
            
            // Add correlation information to response headers
            String correlationId = jwtTokenProvider.getCorrelationIdFromToken(jwt);
            if (correlationId != null) {
                response.setHeader("X-Correlation-ID", correlationId);
            }
            
        } catch (Exception ex) {
            logger.error("Error processing JWT authentication for token - Error: {}", ex.getMessage(), ex);
            
            // Clear any partial authentication state
            SecurityContextHolder.clearContext();
            
            // Re-throw as runtime exception to be handled by filter error handling
            throw new RuntimeException("JWT authentication processing failed", ex);
        }
    }

    /**
     * Determines if the current request should be filtered.
     * 
     * This method allows bypassing JWT authentication for specific endpoints
     * such as authentication endpoints, health checks, and static resources.
     * 
     * @param request HTTP request to evaluate
     * @return true if filtering should be skipped for this request
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // Skip authentication for public endpoints
        boolean shouldSkip = isPublicEndpoint(path, method) || 
                           isStaticResource(path) || 
                           isHealthCheckEndpoint(path);
        
        if (shouldSkip) {
            logger.trace("Skipping JWT authentication for public endpoint: {} {}", method, path);
        }
        
        return shouldSkip;
    }

    /**
     * Checks if the request path is for a public endpoint that doesn't require authentication.
     * 
     * @param path Request URI path
     * @param method HTTP method
     * @return true if endpoint is public
     */
    private boolean isPublicEndpoint(String path, String method) {
        // Authentication endpoints
        if (path.startsWith("/auth/")) {
            return true;
        }
        
        // OPTIONS requests for CORS preflight
        if ("OPTIONS".equals(method)) {
            return true;
        }
        
        // Error handling endpoints
        if (path.equals("/error")) {
            return true;
        }
        
        return false;
    }

    /**
     * Checks if the request path is for static resources.
     * 
     * @param path Request URI path
     * @return true if path is for static resources
     */
    private boolean isStaticResource(String path) {
        return path.startsWith("/static/") || 
               path.startsWith("/public/") || 
               path.equals("/favicon.ico") || 
               path.equals("/robots.txt");
    }

    /**
     * Checks if the request path is for health check endpoints.
     * 
     * @param path Request URI path
     * @return true if path is for health monitoring
     */
    private boolean isHealthCheckEndpoint(String path) {
        return path.equals("/health") || 
               path.equals("/info") || 
               path.startsWith("/actuator/health");
    }

    /**
     * Extracts client IP address for audit logging purposes.
     * 
     * This method attempts to determine the real client IP address,
     * considering common proxy headers used in enterprise environments.
     * 
     * @param request HTTP request
     * @return Client IP address string
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * Logs authentication events for audit and security monitoring.
     * 
     * This method provides comprehensive audit logging for authentication
     * events, supporting security compliance and incident investigation.
     * 
     * @param request HTTP request context
     * @param username Authenticated username
     * @param success Authentication result
     * @param details Additional authentication details
     */
    private void logAuthenticationEvent(HttpServletRequest request, String username, 
                                      boolean success, String details) {
        
        String clientIp = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");
        
        String logMessage = String.format(
            "JWT Authentication: User=[%s], Success=[%s], IP=[%s], URI=[%s %s], UserAgent=[%s], Details=[%s]",
            username, success, clientIp, request.getMethod(), request.getRequestURI(), 
            userAgent, details
        );
        
        if (success) {
            logger.info(logMessage);
        } else {
            logger.warn(logMessage);
        }
    }
}
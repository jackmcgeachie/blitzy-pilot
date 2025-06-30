/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller providing authentication endpoints for the CardDemo application.
 * 
 * <p>This controller replaces the legacy COSGN00C COBOL program by exposing authentication
 * functionality through modern REST API endpoints. It maintains identical business logic
 * and error handling patterns while integrating with Spring Security JWT tokens and 
 * Redis-backed session management.</p>
 * 
 * <p><strong>Legacy COBOL Program Reference:</strong> COSGN00C.cbl - Signon Screen for CardDemo Application</p>
 * 
 * <p><strong>Functional Equivalence:</strong></p>
 * <ul>
 *   <li>User credential validation matching COBOL field validation logic</li>
 *   <li>Error messages identical to COSGN00C error message patterns</li>
 *   <li>Session state management equivalent to CICS COMMAREA functionality</li>
 *   <li>User type-based routing logic replicating COBOL XCTL patterns</li>
 * </ul>
 * 
 * <p><strong>Technical Architecture:</strong></p>
 * <ul>
 *   <li>Spring Security integration with JWT bearer tokens</li>
 *   <li>Redis-backed session store for pseudo-conversational state</li>
 *   <li>CORS configuration for React frontend integration</li>
 *   <li>Comprehensive input validation with Bean Validation API</li>
 * </ul>
 * 
 * <p><strong>Endpoints:</strong></p>
 * <ul>
 *   <li>POST /auth/login - User authentication with username/password credentials</li>
 *   <li>POST /auth/logout - Session termination and JWT token invalidation</li>
 * </ul>
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since 1.0
 * @see AuthenticationRequest
 * @see AuthenticationResponse
 * @see com.carddemo.auth.AuthenticationService
 */
@RestController
@RequestMapping("/auth")
@CrossOrigin(
    origins = {"http://localhost:3000", "http://localhost:8080"},
    allowCredentials = "true",
    allowedHeaders = {"*"},
    methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS}
)
public class AuthController implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    /**
     * Authentication service dependency for user credential validation.
     * 
     * <p>This service contains the core authentication logic migrated from COSGN00C.cbl,
     * including user lookup, password validation, and JWT token generation.</p>
     */
    @Autowired
    private AuthenticationService authenticationService;

    /**
     * Error message constants matching COSGN00C.cbl error messages.
     * 
     * <p>These constants ensure exact error message compatibility with the legacy system,
     * maintaining identical user experience and API contract.</p>
     */
    private static final String ERROR_MSG_USER_ID_REQUIRED = "Please enter User ID ...";
    private static final String ERROR_MSG_PASSWORD_REQUIRED = "Please enter Password ...";
    private static final String ERROR_MSG_WRONG_PASSWORD = "Wrong Password. Try again ...";
    private static final String ERROR_MSG_USER_NOT_FOUND = "User not found. Try again ...";
    private static final String ERROR_MSG_SYSTEM_ERROR = "Unable to verify the User ...";
    private static final String ERROR_MSG_INVALID_KEY = "Invalid key pressed. Use ENTER to login or PF3 to exit.";
    private static final String ERROR_MSG_INVALID_REQUEST = "Invalid request format";
    private static final String SUCCESS_MSG_LOGOUT = "Thank you for using CardDemo Application";

    /**
     * HTTP status and response constants.
     */
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    /**
     * Authenticates a user with username and password credentials.
     * 
     * <p>This endpoint replaces the COSGN00C COBOL program's authentication logic by:</p>
     * <ul>
     *   <li>Validating user credentials against PostgreSQL users table (replaces USRSEC VSAM file)</li>
     *   <li>Generating JWT tokens for successful authentication</li>
     *   <li>Creating Redis session entries for pseudo-conversational state management</li>
     *   <li>Determining navigation routes based on user type (admin vs regular user)</li>
     * </ul>
     * 
     * <p><strong>COBOL Program Flow Mapping:</strong></p>
     * <ul>
     *   <li>Lines 118-130: Field validation for username and password (now Bean Validation)</li>
     *   <li>Lines 132-136: Convert inputs to uppercase (AuthenticationRequest.getUppercase methods)</li>
     *   <li>Lines 211-257: User lookup and password validation (AuthenticationService.authenticate)</li>
     *   <li>Lines 230-240: Route determination based on user type (AuthenticationResponse.determineNextRoute)</li>
     * </ul>
     * 
     * <p><strong>Request Format:</strong></p>
     * <pre>
     * POST /auth/login
     * Content-Type: application/json
     * 
     * {
     *   "username": "USER123",
     *   "password": "PASSWORD"
     * }
     * </pre>
     * 
     * <p><strong>Response Format (Success):</strong></p>
     * <pre>
     * HTTP 200 OK
     * {
     *   "success": true,
     *   "token": "eyJhbGciOiJIUzI1NiIs...",
     *   "userId": "USER123",
     *   "userType": "U",
     *   "role": "ROLE_USER",
     *   "nextRoute": "/main/menu",
     *   "sessionId": "session-uuid",
     *   "expiresIn": 28800
     * }
     * </pre>
     * 
     * <p><strong>Response Format (Error):</strong></p>
     * <pre>
     * HTTP 401 Unauthorized
     * {
     *   "success": false,
     *   "message": "Wrong Password. Try again ...",
     *   "errorCode": "INVALID_PASSWORD"
     * }
     * </pre>
     * 
     * @param request the authentication request containing username and password
     * @param bindingResult validation results from Bean Validation
     * @param httpRequest HTTP servlet request for session management
     * @param httpResponse HTTP servlet response for header configuration
     * @return ResponseEntity containing authentication response with JWT token or error details
     */
    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(
            @Valid @RequestBody AuthenticationRequest request,
            BindingResult bindingResult,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        logger.info("Authentication request received for user: {}", 
                   request.getUsername() != null ? request.getUsername() : "null");

        try {
            // Validate request format and required fields
            // Replicates COBOL lines 118-130: field validation logic
            if (hasValidationErrors(bindingResult)) {
                return handleValidationErrors(bindingResult);
            }

            // Additional business validation matching COBOL logic
            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                logger.warn("Authentication failed: Empty username provided");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(AuthenticationResponse.failure(ERROR_MSG_USER_ID_REQUIRED, "MISSING_USERNAME"));
            }

            if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
                logger.warn("Authentication failed: Empty password provided for user: {}", request.getUsername());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(AuthenticationResponse.failure(ERROR_MSG_PASSWORD_REQUIRED, "MISSING_PASSWORD"));
            }

            // Convert to uppercase matching COBOL FUNCTION UPPER-CASE logic (lines 132-136)
            String uppercaseUsername = request.getUppercaseUsername();
            String uppercasePassword = request.getUppercasePassword();

            logger.debug("Processing authentication for uppercase username: {}", uppercaseUsername);

            // Delegate to authentication service for credential validation
            // Replicates COBOL lines 211-257: READ-USER-SEC-FILE paragraph logic
            AuthenticationResponse response = authenticationService.authenticate(uppercaseUsername, uppercasePassword);

            if (response.isSuccess()) {
                // Configure session management for pseudo-conversational state
                // Replicates CICS COMMAREA functionality
                HttpSession session = httpRequest.getSession(true);
                session.setAttribute("userId", response.getUserId());
                session.setAttribute("userType", response.getUserType());
                session.setAttribute("sessionCorrelationId", response.getSessionCorrelationId());
                session.setAttribute("fromTransactionId", "CC00"); // COSGN00C transaction ID
                session.setAttribute("fromProgram", "COSGN00C");
                session.setMaxInactiveInterval(1800); // 30 minutes timeout

                // Set CORS headers for React frontend integration
                httpResponse.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
                httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
                httpResponse.setHeader("Access-Control-Expose-Headers", "Authorization");

                // Add JWT token to response header
                if (response.getToken() != null) {
                    httpResponse.setHeader(HEADER_AUTHORIZATION, TOKEN_PREFIX + response.getToken());
                }

                logger.info("Authentication successful for user: {} with type: {}", 
                           response.getUserId(), response.getUserType());

                return ResponseEntity.ok()
                    .header("Content-Type", CONTENT_TYPE_JSON)
                    .body(response);

            } else {
                // Handle authentication failures with specific error messages
                // Maintains COBOL error message patterns
                logger.warn("Authentication failed for user: {} - {}", 
                           uppercaseUsername, response.getMessage());

                HttpStatus status = determineErrorStatus(response.getErrorCode());
                return ResponseEntity.status(status).body(response);
            }

        } catch (Exception e) {
            // Handle system errors equivalent to COBOL HANDLE ABEND
            logger.error("System error during authentication for user: {}", 
                        request.getUsername(), e);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AuthenticationResponse.failure(ERROR_MSG_SYSTEM_ERROR, "SYSTEM_ERROR"));
        }
    }

    /**
     * Terminates user session and invalidates JWT token.
     * 
     * <p>This endpoint provides logout functionality equivalent to PF3 key processing
     * in the original COSGN00C program. It performs:</p>
     * <ul>
     *   <li>JWT token invalidation through authentication service</li>
     *   <li>Redis session cleanup for distributed session management</li>
     *   <li>HTTP session termination</li>
     *   <li>Audit logging for security compliance</li>
     * </ul>
     * 
     * <p><strong>COBOL Program Reference:</strong> Lines 88-90: PF3 key handling with thank you message</p>
     * 
     * <p><strong>Request Format:</strong></p>
     * <pre>
     * POST /auth/logout
     * Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
     * </pre>
     * 
     * <p><strong>Response Format:</strong></p>
     * <pre>
     * HTTP 200 OK
     * {
     *   "success": true,
     *   "message": "Thank you for using CardDemo Application"
     * }
     * </pre>
     * 
     * @param httpRequest HTTP servlet request for session access
     * @param httpResponse HTTP servlet response for header configuration
     * @return ResponseEntity containing logout confirmation message
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        logger.info("Logout request received");

        try {
            // Extract JWT token from Authorization header
            String authHeader = httpRequest.getHeader(HEADER_AUTHORIZATION);
            String token = null;
            String userId = null;

            if (authHeader != null && authHeader.startsWith(TOKEN_PREFIX)) {
                token = authHeader.substring(TOKEN_PREFIX.length());
            }

            // Get user information from session before invalidation
            HttpSession session = httpRequest.getSession(false);
            if (session != null) {
                userId = (String) session.getAttribute("userId");
                
                // Invalidate HTTP session
                session.invalidate();
                logger.debug("HTTP session invalidated for user: {}", userId);
            }

            // Invalidate JWT token through authentication service
            if (token != null) {
                authenticationService.invalidateToken(token);
                logger.debug("JWT token invalidated for user: {}", userId);
            }

            // Set CORS headers for React frontend
            httpResponse.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
            httpResponse.setHeader("Access-Control-Allow-Credentials", "true");

            // Create response matching COBOL PF3 thank you message (line 89)
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", SUCCESS_MSG_LOGOUT);
            response.put("timestamp", LocalDateTime.now());

            logger.info("Logout successful for user: {}", userId != null ? userId : "unknown");

            return ResponseEntity.ok()
                .header("Content-Type", CONTENT_TYPE_JSON)
                .body(response);

        } catch (Exception e) {
            logger.error("Error during logout process", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error during logout process");
            errorResponse.put("timestamp", LocalDateTime.now());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
        }
    }

    /**
     * Validates HTTP method access for authentication endpoints.
     * 
     * <p>This endpoint handles unsupported HTTP methods and provides appropriate
     * error responses matching COBOL invalid key handling patterns.</p>
     * 
     * @return ResponseEntity with method not allowed status
     */
    @RequestMapping(
        value = {"", "/"},
        method = {RequestMethod.GET, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH}
    )
    public ResponseEntity<Map<String, Object>> handleUnsupportedMethods() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", ERROR_MSG_INVALID_KEY);
        response.put("supportedMethods", new String[]{"POST"});
        response.put("endpoints", new String[]{"/auth/login", "/auth/logout"});

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .header("Allow", "POST, OPTIONS")
            .body(response);
    }

    /**
     * Handles preflight CORS requests for React frontend integration.
     * 
     * @param httpResponse HTTP response for CORS header configuration
     * @return ResponseEntity with CORS headers configured
     */
    @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> handleCorsPreflightRequest(HttpServletResponse httpResponse) {
        httpResponse.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
        httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        httpResponse.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
        httpResponse.setHeader("Access-Control-Max-Age", "3600");

        return ResponseEntity.ok().build();
    }

    /**
     * Configures CORS settings for React frontend integration.
     * 
     * <p>This configuration enables cross-origin requests from the React development
     * server and production deployments while maintaining security best practices.</p>
     * 
     * @param registry CORS configuration registry
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/auth/**")
            .allowedOrigins("http://localhost:3000", "http://localhost:8080", "https://carddemo.company.com")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }

    /**
     * Checks for Bean Validation errors in the authentication request.
     * 
     * @param bindingResult validation results from @Valid annotation processing
     * @return true if validation errors exist, false otherwise
     */
    private boolean hasValidationErrors(BindingResult bindingResult) {
        return bindingResult.hasErrors();
    }

    /**
     * Handles Bean Validation errors with appropriate HTTP responses.
     * 
     * <p>Maps validation constraint violations to COBOL-compatible error messages
     * maintaining identical user experience with the legacy system.</p>
     * 
     * @param bindingResult validation results containing field errors
     * @return ResponseEntity with validation error details
     */
    private ResponseEntity<AuthenticationResponse> handleValidationErrors(BindingResult bindingResult) {
        StringBuilder errorMessage = new StringBuilder();
        String errorCode = "VALIDATION_ERROR";

        for (FieldError error : bindingResult.getFieldErrors()) {
            String field = error.getField();
            String message = error.getDefaultMessage();

            // Map field validation errors to COBOL error messages
            if ("username".equals(field)) {
                if (message.contains("blank") || message.contains("required")) {
                    errorMessage.append(ERROR_MSG_USER_ID_REQUIRED);
                    errorCode = "MISSING_USERNAME";
                } else {
                    errorMessage.append(message);
                    errorCode = "INVALID_USERNAME";
                }
            } else if ("password".equals(field)) {
                if (message.contains("blank") || message.contains("required")) {
                    errorMessage.append(ERROR_MSG_PASSWORD_REQUIRED);
                    errorCode = "MISSING_PASSWORD";
                } else {
                    errorMessage.append(message);
                    errorCode = "INVALID_PASSWORD";
                }
            } else {
                errorMessage.append(message);
            }

            if (bindingResult.getFieldErrors().size() > 1) {
                errorMessage.append(" ");
            }
        }

        logger.warn("Validation error: {}", errorMessage.toString());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(AuthenticationResponse.failure(errorMessage.toString(), errorCode));
    }

    /**
     * Determines appropriate HTTP status code based on authentication error type.
     * 
     * <p>Maps authentication service error codes to standard HTTP status codes
     * while maintaining RESTful API design principles.</p>
     * 
     * @param errorCode error code from authentication service
     * @return appropriate HTTP status for the error type
     */
    private HttpStatus determineErrorStatus(String errorCode) {
        if (errorCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        switch (errorCode) {
            case "USER_NOT_FOUND":
            case "INVALID_PASSWORD":
                return HttpStatus.UNAUTHORIZED;
            case "MISSING_USERNAME":
            case "MISSING_PASSWORD":
            case "VALIDATION_ERROR":
                return HttpStatus.BAD_REQUEST;
            case "ACCOUNT_LOCKED":
            case "ACCOUNT_DISABLED":
                return HttpStatus.FORBIDDEN;
            case "SYSTEM_ERROR":
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Primary authentication service implementing Spring Security-based user authentication
 * with JWT tokens, BCrypt password hashing, and role-based authorization.
 * 
 * <p><strong>Legacy System Migration:</strong></p>
 * <p>This service replaces the COSGN00C.cbl COBOL program with modern cloud-native 
 * authentication patterns while maintaining 1-to-1 compatibility with RACF user types 
 * and preserving identical business logic flow for user validation and menu navigation.</p>
 * 
 * <p><strong>COBOL Program Reference:</strong> COSGN00C.cbl - Signon Screen for CardDemo Application</p>
 * 
 * <p><strong>Key Migration Points:</strong></p>
 * <ul>
 *   <li>VSAM USRSEC file operations → PostgreSQL users table via JPA repository</li>
 *   <li>Plain text password comparison → BCrypt password hashing with strength factor 10</li>
 *   <li>RACF user types ('A'/'U') → Spring Security authorities (ROLE_ADMIN/ROLE_USER)</li>
 *   <li>CICS XCTL program transfers → HTTP redirect responses to appropriate menu endpoints</li>
 *   <li>COMMAREA structure → Redis-backed session management with user context</li>
 *   <li>COBOL field validation → Java Bean Validation with custom validators</li>
 * </ul>
 * 
 * <p><strong>Security Implementation:</strong></p>
 * <ul>
 *   <li>JWT token generation containing user identity claims (user_id, username, role_code)</li>
 *   <li>Configurable token expiration (default 8 hours) for enterprise security requirements</li>
 *   <li>Session management through Redis for pseudo-conversational state preservation</li>
 *   <li>Comprehensive error handling matching COBOL error message patterns</li>
 *   <li>Audit logging integration for security event capture and compliance</li>
 * </ul>
 * 
 * <p><strong>Business Logic Preservation:</strong></p>
 * <p>All core authentication business logic from COSGN00C.cbl is preserved:</p>
 * <ul>
 *   <li>User ID and password validation (lines 118-130 in COSGN00C.cbl)</li>
 *   <li>User record lookup and authentication (lines 209-257 in COSGN00C.cbl)</li>
 *   <li>Role-based menu navigation (lines 230-240 in COSGN00C.cbl)</li>
 *   <li>Error message handling with exact text preservation</li>
 * </ul>
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since Spring Boot 3.2.5, Spring Security 6.1.8
 */
@Service
@Transactional
public class AuthenticationService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);

    // Error message constants matching COSGN00C.cbl error messages (lines 120, 125, 242, 249, 254)
    private static final String ERROR_USER_ID_REQUIRED = "Please enter User ID ...";
    private static final String ERROR_PASSWORD_REQUIRED = "Please enter Password ...";
    private static final String ERROR_WRONG_PASSWORD = "Wrong Password. Try again ...";
    private static final String ERROR_USER_NOT_FOUND = "User not found. Try again ...";
    private static final String ERROR_UNABLE_TO_VERIFY = "Unable to verify the User ...";
    private static final String SUCCESS_MESSAGE = "Authentication successful";

    // Transaction and program identifiers from COSGN00C.cbl (lines 37, 36)
    private static final String TRANSACTION_ID = "CC00";
    private static final String PROGRAM_NAME = "COSGN00C";

    // RACF user type mappings from COCOM01Y.cpy (lines 27-28)
    private static final String RACF_ADMIN_TYPE = "A";  // CDEMO-USRTYP-ADMIN
    private static final String RACF_USER_TYPE = "U";   // CDEMO-USRTYP-USER

    // Spring Security role mappings
    private static final String SPRING_ROLE_ADMIN = "ROLE_ADMIN";
    private static final String SPRING_ROLE_USER = "ROLE_USER";

    // Navigation routes replicating CICS XCTL logic (lines 231-239 in COSGN00C.cbl)
    private static final String ADMIN_MENU_ROUTE = "/admin/menu";  // Maps to COADM01C
    private static final String MAIN_MENU_ROUTE = "/main/menu";    // Maps to COMEN01C

    // Session attribute keys for Redis-backed session management
    private static final String SESSION_USER_ID = "CDEMO_USER_ID";
    private static final String SESSION_USER_TYPE = "CDEMO_USER_TYPE";
    private static final String SESSION_FROM_TRANID = "CDEMO_FROM_TRANID";
    private static final String SESSION_FROM_PROGRAM = "CDEMO_FROM_PROGRAM";
    private static final String SESSION_CORRELATION_ID = "CDEMO_CORRELATION_ID";

    // Dependencies
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    // Configuration properties
    @Value("${carddemo.auth.session.timeout:1800}") // 30 minutes default
    private int sessionTimeoutSeconds;

    @Value("${carddemo.auth.audit.enabled:true}")
    private boolean auditEnabled;

    /**
     * Constructor with dependency injection for all required components.
     * 
     * @param userRepository Spring Data JPA repository for user operations
     * @param jwtTokenProvider JWT token management service
     * @param passwordEncoder BCrypt password encoder with strength factor 10
     * @param authenticationManager Spring Security authentication manager
     */
    @Autowired
    public AuthenticationService(
            UserRepository userRepository,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        
        logger.info("AuthenticationService initialized - replacing COSGN00C.cbl functionality");
        logger.info("Session timeout configured: {} seconds", sessionTimeoutSeconds);
        logger.info("Audit logging enabled: {}", auditEnabled);
    }

    /**
     * Primary authentication method replacing COSGN00C.cbl PROCESS-ENTER-KEY paragraph.
     * 
     * <p>This method replicates the complete authentication flow from COSGN00C.cbl:</p>
     * <ul>
     *   <li>Input validation (lines 118-130)</li>
     *   <li>User lookup via VSAM READ operation (lines 211-219)</li>
     *   <li>Password validation (line 223)</li>
     *   <li>Role-based navigation setup (lines 230-240)</li>
     * </ul>
     * 
     * @param request The authentication request containing username and password
     * @param httpRequest The HTTP servlet request for session management
     * @return AuthenticationResponse containing JWT token and user details or error information
     */
    public AuthenticationResponse authenticate(@Valid AuthenticationRequest request, 
                                               HttpServletRequest httpRequest) {
        
        logger.debug("Authentication attempt for user: {}", request.getUsername());
        
        try {
            // Input validation replicating COSGN00C.cbl lines 118-130
            AuthenticationResponse validationResult = validateAuthenticationRequest(request);
            if (!validationResult.isSuccess()) {
                logAuthenticationFailure(request.getUsername(), validationResult.getMessage());
                return validationResult;
            }

            // Convert to uppercase as per COBOL FUNCTION UPPER-CASE (lines 132-136)
            String upperUsername = request.getUppercaseUsername();
            String upperPassword = request.getUppercasePassword();

            // User lookup replicating READ-USER-SEC-FILE paragraph (lines 209-257)
            Optional<User> userOptional = userRepository.findByUsername(upperUsername);
            
            if (!userOptional.isPresent()) {
                logger.warn("User not found: {}", upperUsername);
                logAuthenticationFailure(upperUsername, ERROR_USER_NOT_FOUND);
                return AuthenticationResponse.userNotFound();
            }

            User user = userOptional.get();
            
            // Account status validation
            if (!user.isEnabled()) {
                logger.warn("User account disabled: {}", upperUsername);
                logAuthenticationFailure(upperUsername, "Account disabled");
                return AuthenticationResponse.failure("Account is disabled", "ACCOUNT_DISABLED");
            }

            if (!user.isAccountNonLocked()) {
                logger.warn("User account locked: {}", upperUsername);
                logAuthenticationFailure(upperUsername, "Account locked");
                return AuthenticationResponse.failure("Account is locked", "ACCOUNT_LOCKED");
            }

            // Password validation using BCrypt replacing plain text comparison (line 223)
            if (!passwordEncoder.matches(upperPassword, user.getPasswordHash())) {
                logger.warn("Password mismatch for user: {}", upperUsername);
                logAuthenticationFailure(upperUsername, ERROR_WRONG_PASSWORD);
                return AuthenticationResponse.wrongPassword();
            }

            // Successful authentication - generate JWT token
            String correlationId = generateCorrelationId();
            String jwtToken = jwtTokenProvider.generateToken(
                user.getUserId(), 
                user.getUsername(), 
                user.getRoleCode(),
                correlationId
            );

            // Calculate token expiration
            long expiresIn = jwtTokenProvider.getJwtExpirationMs() / 1000; // Convert to seconds

            // Setup session management replicating COMMAREA structure (lines 224-229)
            String sessionId = setupUserSession(user, httpRequest, correlationId);

            // Create successful response with navigation context
            AuthenticationResponse response = AuthenticationResponse.success(
                jwtToken,
                user.getUserId(),
                user.getUsername(),
                user.getRoleCode(),
                user.getFirstName(),
                user.getLastName(),
                expiresIn,
                sessionId
            );

            // Set additional context from COSGN00C.cbl
            response.setFromTransactionId(TRANSACTION_ID);
            response.setFromProgram(PROGRAM_NAME);
            response.setSessionCorrelationId(correlationId);

            logAuthenticationSuccess(user);
            
            logger.info("Authentication successful for user: {} ({})", 
                       user.getUsername(), user.getRole().getAuthority());
            
            return response;

        } catch (AuthenticationException ex) {
            logger.error("Authentication failed for user: {} - {}", request.getUsername(), ex.getMessage());
            logAuthenticationFailure(request.getUsername(), ex.getMessage());
            return AuthenticationResponse.failure("Authentication failed: " + ex.getMessage(), "AUTH_FAILED");
            
        } catch (Exception ex) {
            logger.error("System error during authentication for user: {} - {}", 
                        request.getUsername(), ex.getMessage(), ex);
            logAuthenticationFailure(request.getUsername(), ERROR_UNABLE_TO_VERIFY);
            return AuthenticationResponse.systemError();
        }
    }

    /**
     * Spring Security UserDetailsService implementation for JWT token validation.
     * 
     * <p>This method supports Spring Security's authentication framework by providing
     * user details for JWT token validation and security context population.</p>
     * 
     * @param username The username to load user details for
     * @return UserDetails implementation containing user information and authorities
     * @throws UsernameNotFoundException if user is not found
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        logger.debug("Loading user details for: {}", username);
        
        return userRepository.findByUsername(username.toUpperCase())
                .orElseThrow(() -> {
                    logger.warn("User not found for Spring Security context: {}", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });
    }

    /**
     * Validates user authentication request input matching COSGN00C.cbl validation logic.
     * 
     * <p>Replicates the validation checks from COSGN00C.cbl lines 118-130:</p>
     * <ul>
     *   <li>WHEN USERIDI OF COSGN0AI = SPACES OR LOW-VALUES</li>
     *   <li>WHEN PASSWDI OF COSGN0AI = SPACES OR LOW-VALUES</li>
     * </ul>
     * 
     * @param request The authentication request to validate
     * @return AuthenticationResponse indicating validation success or specific error
     */
    private AuthenticationResponse validateAuthenticationRequest(AuthenticationRequest request) {
        
        // Username validation (COSGN00C.cbl lines 118-122)
        if (!StringUtils.hasText(request.getUsername())) {
            logger.debug("Authentication request validation failed: username is blank");
            return AuthenticationResponse.failure(ERROR_USER_ID_REQUIRED, "USERNAME_REQUIRED");
        }

        // Password validation (COSGN00C.cbl lines 123-127)
        if (!StringUtils.hasText(request.getPassword())) {
            logger.debug("Authentication request validation failed: password is blank");
            return AuthenticationResponse.failure(ERROR_PASSWORD_REQUIRED, "PASSWORD_REQUIRED");
        }

        // Additional validation for field length (COBOL PIC X(08) constraints)
        if (request.getUsername().length() > 8) {
            logger.debug("Authentication request validation failed: username too long");
            return AuthenticationResponse.failure("User ID must not exceed 8 characters", "USERNAME_TOO_LONG");
        }

        if (request.getPassword().length() > 8) {
            logger.debug("Authentication request validation failed: password too long");
            return AuthenticationResponse.failure("Password must not exceed 8 characters", "PASSWORD_TOO_LONG");
        }

        return AuthenticationResponse.success("", "", "", "", "", "", 0L, "");
    }

    /**
     * Sets up user session management replacing CICS COMMAREA structure.
     * 
     * <p>This method replicates the COMMAREA setup from COSGN00C.cbl lines 224-229:</p>
     * <ul>
     *   <li>MOVE WS-TRANID TO CDEMO-FROM-TRANID</li>
     *   <li>MOVE WS-PGMNAME TO CDEMO-FROM-PROGRAM</li>
     *   <li>MOVE WS-USER-ID TO CDEMO-USER-ID</li>
     *   <li>MOVE SEC-USR-TYPE TO CDEMO-USER-TYPE</li>
     *   <li>MOVE ZEROS TO CDEMO-PGM-CONTEXT</li>
     * </ul>
     * 
     * @param user The authenticated user
     * @param httpRequest The HTTP servlet request for session access
     * @param correlationId The correlation ID for audit trail
     * @return Session ID for client tracking
     */
    private String setupUserSession(User user, HttpServletRequest httpRequest, String correlationId) {
        
        HttpSession session = httpRequest.getSession(true);
        
        // Set session timeout
        session.setMaxInactiveInterval(sessionTimeoutSeconds);
        
        // Setup session attributes matching COMMAREA structure
        session.setAttribute(SESSION_USER_ID, user.getUserId());
        session.setAttribute(SESSION_USER_TYPE, user.getRoleCode());
        session.setAttribute(SESSION_FROM_TRANID, TRANSACTION_ID);
        session.setAttribute(SESSION_FROM_PROGRAM, PROGRAM_NAME);
        session.setAttribute(SESSION_CORRELATION_ID, correlationId);
        
        // Additional session context
        session.setAttribute("username", user.getUsername());
        session.setAttribute("fullName", user.getFullName());
        session.setAttribute("isAdmin", user.isAdmin());
        session.setAttribute("loginTimestamp", LocalDateTime.now());
        
        logger.debug("User session established: sessionId={}, userId={}, userType={}", 
                    session.getId(), user.getUserId(), user.getRoleCode());
        
        return session.getId();
    }

    /**
     * Generates unique correlation ID for audit trail and session tracking.
     * 
     * @return Unique correlation identifier
     */
    private String generateCorrelationId() {
        return "AUTH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Logs successful authentication events for audit purposes.
     * 
     * @param user The successfully authenticated user
     */
    private void logAuthenticationSuccess(User user) {
        if (auditEnabled) {
            logger.info("AUDIT: Authentication successful - User: {}, Role: {}, Timestamp: {}", 
                       user.getUsername(), user.getRole().getAuthority(), LocalDateTime.now());
        }
    }

    /**
     * Logs failed authentication attempts for security monitoring.
     * 
     * @param username The username that failed authentication
     * @param reason The reason for authentication failure
     */
    private void logAuthenticationFailure(String username, String reason) {
        if (auditEnabled) {
            logger.warn("AUDIT: Authentication failed - User: {}, Reason: {}, Timestamp: {}", 
                       username, reason, LocalDateTime.now());
        }
    }

    /**
     * Creates a new user with encrypted password (administrative function).
     * 
     * <p>This method supports user management operations with secure password handling.</p>
     * 
     * @param userId The unique user identifier
     * @param username The unique username
     * @param password The plain text password (will be BCrypt hashed)
     * @param firstName The user's first name
     * @param lastName The user's last name
     * @param roleCode The user's role code ('A' for Admin, 'U' for User)
     * @return The created user entity
     * @throws IllegalArgumentException if user already exists or invalid parameters
     */
    @Transactional
    public User createUser(String userId, String username, String password, 
                          String firstName, String lastName, String roleCode) {
        
        logger.debug("Creating new user: userId={}, username={}, roleCode={}", 
                    userId, username, roleCode);
        
        // Validation
        if (userRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("User ID already exists: " + userId);
        }
        
        if (userRepository.existsByUsername(username.toUpperCase())) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        
        // Validate role code
        if (!RACF_ADMIN_TYPE.equals(roleCode) && !RACF_USER_TYPE.equals(roleCode)) {
            throw new IllegalArgumentException("Invalid role code: " + roleCode + 
                                             " (must be 'A' for Admin or 'U' for User)");
        }
        
        // Hash password using BCrypt
        String hashedPassword = passwordEncoder.encode(password);
        
        // Create user entity
        User user = new User(userId, username.toUpperCase(), hashedPassword, 
                            firstName, lastName, roleCode);
        
        User savedUser = userRepository.save(user);
        
        logger.info("User created successfully: userId={}, username={}, role={}", 
                   savedUser.getUserId(), savedUser.getUsername(), savedUser.getRole().getAuthority());
        
        if (auditEnabled) {
            logger.info("AUDIT: User created - UserID: {}, Username: {}, Role: {}, Timestamp: {}", 
                       savedUser.getUserId(), savedUser.getUsername(), 
                       savedUser.getRole().getAuthority(), LocalDateTime.now());
        }
        
        return savedUser;
    }

    /**
     * Updates user password with BCrypt hashing.
     * 
     * @param userId The user ID to update password for
     * @param newPassword The new plain text password
     * @return true if password updated successfully
     * @throws UsernameNotFoundException if user not found
     */
    @Transactional
    public boolean updateUserPassword(String userId, String newPassword) {
        
        logger.debug("Updating password for user: {}", userId);
        
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        
        String hashedPassword = passwordEncoder.encode(newPassword);
        user.setPasswordHash(hashedPassword);
        
        userRepository.save(user);
        
        logger.info("Password updated successfully for user: {}", userId);
        
        if (auditEnabled) {
            logger.info("AUDIT: Password updated - UserID: {}, Timestamp: {}", 
                       userId, LocalDateTime.now());
        }
        
        return true;
    }

    /**
     * Validates JWT token and extracts user information for security context.
     * 
     * @param token The JWT token to validate
     * @return User information if token is valid, null otherwise
     */
    public User validateTokenAndGetUser(String token) {
        
        if (!jwtTokenProvider.validateToken(token)) {
            return null;
        }
        
        String userId = jwtTokenProvider.getUserIdFromToken(token);
        if (userId == null) {
            return null;
        }
        
        return userRepository.findByUserId(userId).orElse(null);
    }

    /**
     * Determines the appropriate navigation route based on user type.
     * 
     * <p>Replicates the CICS XCTL logic from COSGN00C.cbl lines 230-240:</p>
     * <ul>
     *   <li>IF CDEMO-USRTYP-ADMIN → EXEC CICS XCTL PROGRAM ('COADM01C')</li>
     *   <li>ELSE → EXEC CICS XCTL PROGRAM ('COMEN01C')</li>
     * </ul>
     * 
     * @param userType The RACF user type ('A' or 'U')
     * @return The appropriate route path for frontend navigation
     */
    public String determineNavigationRoute(String userType) {
        if (RACF_ADMIN_TYPE.equals(userType)) {
            return ADMIN_MENU_ROUTE;  // Equivalent to COADM01C
        } else {
            return MAIN_MENU_ROUTE;   // Equivalent to COMEN01C
        }
    }

    /**
     * Checks if a user has administrative privileges.
     * 
     * @param userId The user ID to check
     * @return true if user has administrative privileges
     */
    @Transactional(readOnly = true)
    public boolean isUserAdmin(String userId) {
        return userRepository.findByUserId(userId)
                .map(User::isAdmin)
                .orElse(false);
    }

    /**
     * Gets user role for authorization decisions.
     * 
     * @param userId The user ID
     * @return User role code ('A' or 'U'), null if user not found
     */
    @Transactional(readOnly = true)
    public String getUserRole(String userId) {
        return userRepository.findByUserId(userId)
                .map(User::getRoleCode)
                .orElse(null);
    }

    /**
     * Refresh JWT token for session extension.
     * 
     * @param currentToken The current JWT token
     * @return New JWT token with extended expiration, null if current token invalid
     */
    public String refreshToken(String currentToken) {
        return jwtTokenProvider.refreshToken(currentToken);
    }

    /**
     * Invalidates user session (logout functionality).
     * 
     * @param httpRequest The HTTP servlet request containing session to invalidate
     */
    public void invalidateSession(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            String userId = (String) session.getAttribute(SESSION_USER_ID);
            String username = (String) session.getAttribute("username");
            
            session.invalidate();
            
            logger.info("Session invalidated for user: {} ({})", username, userId);
            
            if (auditEnabled) {
                logger.info("AUDIT: Session invalidated - UserID: {}, Username: {}, Timestamp: {}", 
                           userId, username, LocalDateTime.now());
            }
        }
    }
}
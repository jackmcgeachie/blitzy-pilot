/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.session;

import com.fasterxml.jackson.databind.ObjectMapper;
// SessionData and SessionAttributes classes are dependencies created by other agents
// These imports will be resolved when those classes become available
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Placeholder interface for SessionData until the actual class is created by another agent.
 * This represents the Java equivalent of CICS COMMAREA structure from COCOM01Y.cpy.
 */
interface SessionData {
    // This interface will be replaced by the actual SessionData class
    // created by another agent based on the COBOL COMMAREA structure
}

/**
 * Utility class providing common session management operations, validation methods, 
 * and data transformation utilities for the CardDemo application.
 * 
 * This class supports session integrity checking, cleanup operations, and correlation 
 * with JWT tokens while maintaining security and audit requirements. It enables 
 * consistent session handling patterns across the application while preserving 
 * pseudo-conversational state patterns from the original CICS COMMAREA implementation.
 * 
 * Key features:
 * - Session validation and integrity checking for security compliance
 * - Session cleanup utilities for expired session management  
 * - Session correlation with JWT tokens for audit trail maintenance
 * - Data transformation between COMMAREA structure and Redis storage
 * - Common session operation utilities for REST endpoint consistency
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since 2024-01-01
 */
@Component
public class SessionUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionUtils.class);
    
    // Redis key prefix constants matching spring:session:carddemo: pattern
    private static final String SESSION_KEY_PREFIX = "spring:session:carddemo:";
    private static final String SESSION_EXPIRATION_KEY_PREFIX = SESSION_KEY_PREFIX + "expires:";
    private static final String SESSION_AUDIT_KEY_PREFIX = SESSION_KEY_PREFIX + "audit:";
    
    // Session validation constants
    private static final Duration DEFAULT_SESSION_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration SESSION_VALIDATION_GRACE_PERIOD = Duration.ofMinutes(5);
    private static final int MAX_CONCURRENT_SESSIONS_PER_USER = 3;
    
    // COMMAREA equivalent session attribute keys
    private static final String COMMAREA_DATA_KEY = "CARDDEMO_COMMAREA";
    private static final String USER_CONTEXT_KEY = "USER_CONTEXT";
    private static final String NAVIGATION_STATE_KEY = "NAVIGATION_STATE";
    private static final String TRANSACTION_CONTEXT_KEY = "TRANSACTION_CONTEXT";
    private static final String PROGRAM_FLOW_KEY = "PROGRAM_FLOW";
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private SessionRepository<? extends Session> sessionRepository;
    
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Validates session integrity and security compliance.
     * Performs comprehensive validation including JWT token correlation,
     * session timeout verification, and security context validation.
     * 
     * @param sessionId The session ID to validate
     * @param request The HTTP request containing session context
     * @return true if session is valid and secure, false otherwise
     */
    public boolean validateSessionIntegrity(String sessionId, HttpServletRequest request) {
        logger.debug("Validating session integrity for session ID: {}", sessionId);
        
        try {
            // Basic session existence check
            if (sessionId == null || sessionId.trim().isEmpty()) {
                logger.warn("Session validation failed: null or empty session ID");
                return false;
            }
            
            // Retrieve session from repository
            Session session = sessionRepository.findById(sessionId);
            if (session == null) {
                logger.warn("Session validation failed: session not found for ID: {}", sessionId);
                return false;
            }
            
            // Check if session is expired
            if (session.isExpired()) {
                logger.warn("Session validation failed: session expired for ID: {}", sessionId);
                return false;
            }
            
            // Validate session timeout
            Instant now = Instant.now();
            Instant lastAccessedTime = session.getLastAccessedTime();
            Duration timeSinceLastAccess = Duration.between(lastAccessedTime, now);
            
            if (timeSinceLastAccess.compareTo(DEFAULT_SESSION_TIMEOUT.plus(SESSION_VALIDATION_GRACE_PERIOD)) > 0) {
                logger.warn("Session validation failed: session timeout exceeded for ID: {}", sessionId);
                return false;
            }
            
            // Validate JWT token correlation if present
            if (!validateJwtTokenCorrelation(session, request)) {
                logger.warn("Session validation failed: JWT token correlation failed for session ID: {}", sessionId);
                return false;
            }
            
            // Validate session data integrity
            if (!validateSessionDataIntegrity(session)) {
                logger.warn("Session validation failed: session data integrity check failed for ID: {}", sessionId);
                return false;
            }
            
            // Update last access time for active session
            session.setLastAccessedTime(now);
            sessionRepository.save(session);
            
            logger.debug("Session validation successful for session ID: {}", sessionId);
            return true;
            
        } catch (Exception e) {
            logger.error("Session validation error for session ID: {}", sessionId, e);
            return false;
        }
    }
    
    /**
     * Correlates session with JWT token for audit trail and security verification.
     * Validates that the session's user context matches the JWT token claims
     * and maintains audit correlation identifiers.
     * 
     * @param session The session to correlate
     * @param request The HTTP request containing JWT token
     * @return true if correlation is successful, false otherwise
     */
    public boolean validateJwtTokenCorrelation(Session session, HttpServletRequest request) {
        logger.debug("Validating JWT token correlation for session: {}", session.getId());
        
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                logger.debug("No authenticated user found for JWT correlation");
                return false;
            }
            
            // Extract JWT token from authentication
            if (!(authentication.getPrincipal() instanceof Jwt)) {
                logger.debug("Authentication principal is not a JWT token");
                return false;
            }
            
            Jwt jwt = (Jwt) authentication.getPrincipal();
            String jwtUserId = jwt.getSubject();
            String jwtUsername = jwt.getClaimAsString("username");
            String jwtRole = jwt.getClaimAsString("role");
            
            // Get user context from session
            SessionData sessionData = getSessionData(session);
            if (sessionData == null) {
                logger.warn("JWT correlation failed: no session data found");
                return false;
            }
            
            // Extract user context from session data for correlation
            String sessionUserId = getSessionUserId(sessionData);
            String sessionUserType = getSessionUserType(sessionData);
            
            // Validate user ID correlation
            if (!Objects.equals(jwtUserId, sessionUserId)) {
                logger.warn("JWT correlation failed: user ID mismatch. JWT: {}, Session: {}", 
                           jwtUserId, sessionUserId);
                return false;
            }
            
            // Validate username correlation (using user ID as username for simplicity)
            if (!Objects.equals(jwtUsername, sessionUserId)) {
                logger.warn("JWT correlation failed: username mismatch. JWT: {}, Session: {}", 
                           jwtUsername, sessionUserId);
                return false;
            }
            
            // Validate role correlation
            if (!Objects.equals(jwtRole, sessionUserType)) {
                logger.warn("JWT correlation failed: role mismatch. JWT: {}, Session: {}", 
                           jwtRole, sessionUserType);
                return false;
            }
            
            // Store JWT correlation ID for audit trail
            String correlationId = jwt.getClaimAsString("jti");
            if (correlationId != null) {
                session.setAttribute("JWT_CORRELATION_ID", correlationId);
                sessionRepository.save(session);
                
                // Add correlation ID to MDC for logging
                MDC.put("jwtCorrelationId", correlationId);
            }
            
            logger.debug("JWT token correlation successful for session: {}", session.getId());
            return true;
            
        } catch (Exception e) {
            logger.error("JWT token correlation error for session: {}", session.getId(), e);
            return false;
        }
    }
    
    /**
     * Performs cleanup of expired sessions and optimizes memory usage.
     * This method removes expired sessions from Redis storage and performs
     * garbage collection optimization for session-related objects.
     * 
     * @return number of sessions cleaned up
     */
    public int cleanupExpiredSessions() {
        logger.info("Starting expired session cleanup process");
        
        int cleanedUpCount = 0;
        
        try {
            Instant cutoffTime = Instant.now().minus(DEFAULT_SESSION_TIMEOUT).minus(SESSION_VALIDATION_GRACE_PERIOD);
            
            // Get all session keys using pattern matching
            Set<String> sessionKeys = redisTemplate.keys(SESSION_KEY_PREFIX + "sessions:*");
            
            if (sessionKeys != null && !sessionKeys.isEmpty()) {
                for (String sessionKey : sessionKeys) {
                    try {
                        // Extract session ID from key
                        String sessionId = extractSessionIdFromKey(sessionKey);
                        if (sessionId == null) continue;
                        
                        // Check if session exists and is expired
                        Session session = sessionRepository.findById(sessionId);
                        if (session == null || isSessionExpiredByCutoff(session, cutoffTime)) {
                            
                            // Clean up session data
                            cleanupSessionData(sessionId);
                            
                            // Remove session from repository
                            sessionRepository.deleteById(sessionId);
                            
                            cleanedUpCount++;
                            logger.debug("Cleaned up expired session: {}", sessionId);
                        }
                        
                    } catch (Exception e) {
                        logger.warn("Error cleaning up session key: {}", sessionKey, e);
                    }
                }
            }
            
            // Clean up orphaned session-related keys
            cleanupOrphanedSessionKeys();
            
            logger.info("Expired session cleanup completed. Cleaned up {} sessions", cleanedUpCount);
            
        } catch (Exception e) {
            logger.error("Error during expired session cleanup", e);
        }
        
        return cleanedUpCount;
    }
    
    /**
     * Transforms session data between COMMAREA structure and Redis storage format.
     * Converts the legacy COBOL COMMAREA structure to modern JSON format 
     * suitable for Redis storage while preserving all data semantics.
     * 
     * @param session The session containing COMMAREA data
     * @return SessionData object representing the COMMAREA structure
     */
    public SessionData transformCommareaToSessionData(Session session) {
        logger.debug("Transforming COMMAREA data to SessionData for session: {}", session.getId());
        
        try {
            // Get raw COMMAREA data from session
            Object commareaData = session.getAttribute(COMMAREA_DATA_KEY);
            if (commareaData == null) {
                logger.debug("No COMMAREA data found in session, creating empty SessionData");
                return createEmptySessionData();
            }
            
            SessionData sessionData;
            
            if (commareaData instanceof String) {
                // Parse JSON string to SessionData
                sessionData = objectMapper.readValue((String) commareaData, SessionData.class);
            } else if (commareaData instanceof SessionData) {
                // Already in correct format
                sessionData = (SessionData) commareaData;
            } else {
                // Try to convert from Map or other format
                String jsonString = objectMapper.writeValueAsString(commareaData);
                sessionData = objectMapper.readValue(jsonString, SessionData.class);
            }
            
            // Validate and enrich session data
            enrichSessionDataFromContext(sessionData, session);
            
            logger.debug("COMMAREA transformation successful for session: {}", session.getId());
            return sessionData;
            
        } catch (Exception e) {
            logger.error("Error transforming COMMAREA data for session: {}", session.getId(), e);
            return createEmptySessionData();
        }
    }
    
    /**
     * Stores session data in Redis with proper serialization and key management.
     * Converts SessionData object to JSON format and stores it in Redis
     * with appropriate expiration and key prefix conventions.
     * 
     * @param session The session to store data in
     * @param sessionData The SessionData object to store
     */
    public void storeSessionDataInRedis(Session session, SessionData sessionData) {
        logger.debug("Storing session data in Redis for session: {}", session.getId());
        
        try {
            // Serialize SessionData to JSON
            String jsonData = objectMapper.writeValueAsString(sessionData);
            
            // Store in session attributes
            session.setAttribute(COMMAREA_DATA_KEY, jsonData);
            session.setAttribute(USER_CONTEXT_KEY, createUserContext(sessionData));
            session.setAttribute(NAVIGATION_STATE_KEY, createNavigationState(sessionData));
            session.setAttribute(TRANSACTION_CONTEXT_KEY, createTransactionContext(sessionData));
            session.setAttribute(PROGRAM_FLOW_KEY, createProgramFlowContext(sessionData));
            
            // Save session to repository (which will persist to Redis)
            sessionRepository.save(session);
            
            // Store additional audit information
            storeSessionAuditData(session.getId(), sessionData);
            
            logger.debug("Session data stored successfully in Redis for session: {}", session.getId());
            
        } catch (Exception e) {
            logger.error("Error storing session data in Redis for session: {}", session.getId(), e);
            throw new RuntimeException("Failed to store session data", e);
        }
    }
    
    /**
     * Retrieves and validates session data with integrity checking.
     * Fetches SessionData from the session and performs validation
     * to ensure data consistency and security compliance.
     * 
     * @param session The session to retrieve data from
     * @return SessionData object or null if not found or invalid
     */
    public SessionData getSessionData(Session session) {
        if (session == null) {
            logger.warn("Cannot retrieve session data: session is null");
            return null;
        }
        
        logger.debug("Retrieving session data for session: {}", session.getId());
        
        try {
            Object sessionDataObj = session.getAttribute(COMMAREA_DATA_KEY);
            if (sessionDataObj == null) {
                logger.debug("No session data found for session: {}", session.getId());
                return null;
            }
            
            SessionData sessionData;
            if (sessionDataObj instanceof String) {
                sessionData = objectMapper.readValue((String) sessionDataObj, SessionData.class);
            } else if (sessionDataObj instanceof SessionData) {
                sessionData = (SessionData) sessionDataObj;
            } else {
                logger.warn("Session data in unexpected format for session: {}", session.getId());
                return null;
            }
            
            // Validate session data integrity
            if (!validateSessionDataFields(sessionData)) {
                logger.warn("Session data validation failed for session: {}", session.getId());
                return null;
            }
            
            return sessionData;
            
        } catch (Exception e) {
            logger.error("Error retrieving session data for session: {}", session.getId(), e);
            return null;
        }
    }
    
    /**
     * Creates user context attributes for consistent session management.
     * Extracts user-specific information from SessionData and creates
     * a standardized user context object for session storage.
     * 
     * @param sessionData The SessionData containing user information
     * @return Map containing user context attributes
     */
    public Map<String, Object> createUserContext(SessionData sessionData) {
        Map<String, Object> userContext = new HashMap<>();
        
        if (sessionData != null) {
            userContext.put("userId", getFieldValue(sessionData, "userId", String.class));
            userContext.put("userType", getFieldValue(sessionData, "userType", String.class));
            userContext.put("fromTransactionId", getFieldValue(sessionData, "fromTransactionId", String.class));
            userContext.put("fromProgram", getFieldValue(sessionData, "fromProgram", String.class));
            userContext.put("programContext", getFieldValue(sessionData, "programContext", Integer.class));
            userContext.put("loginTimestamp", Instant.now().toString());
        }
        
        return userContext;
    }
    
    /**
     * Creates navigation state attributes for pseudo-conversational flow.
     * Maintains navigation context equivalent to CICS screen flow patterns
     * including last map, mapset, and navigation breadcrumbs.
     * 
     * @param sessionData The SessionData containing navigation information
     * @return Map containing navigation state attributes
     */
    public Map<String, Object> createNavigationState(SessionData sessionData) {
        Map<String, Object> navigationState = new HashMap<>();
        
        if (sessionData != null) {
            navigationState.put("toTransactionId", getFieldValue(sessionData, "toTransactionId", String.class));
            navigationState.put("toProgram", getFieldValue(sessionData, "toProgram", String.class));
            navigationState.put("lastMap", getFieldValue(sessionData, "lastMap", String.class));
            navigationState.put("lastMapset", getFieldValue(sessionData, "lastMapset", String.class));
            navigationState.put("navigationHistory", new ArrayList<String>());
            navigationState.put("currentScreen", getFieldValue(sessionData, "toProgram", String.class));
        }
        
        return navigationState;
    }
    
    /**
     * Validates session attribute consistency and security requirements.
     * Performs comprehensive validation of session attributes to ensure
     * data integrity and security compliance across session operations.
     * 
     * @param session The session to validate
     * @return true if all attributes are valid and consistent
     */
    public boolean validateSessionAttributes(Session session) {
        logger.debug("Validating session attributes for session: {}", session.getId());
        
        try {
            // Check required session attributes exist
            Set<String> requiredAttributes = Set.of(
                COMMAREA_DATA_KEY,
                USER_CONTEXT_KEY,
                NAVIGATION_STATE_KEY
            );
            
            for (String attribute : requiredAttributes) {
                if (session.getAttribute(attribute) == null) {
                    logger.warn("Session validation failed: missing required attribute {}", attribute);
                    return false;
                }
            }
            
            // Validate user context consistency
            Map<String, Object> userContext = (Map<String, Object>) session.getAttribute(USER_CONTEXT_KEY);
            if (userContext == null || userContext.get("userId") == null) {
                logger.warn("Session validation failed: invalid user context");
                return false;
            }
            
            // Validate session data consistency
            SessionData sessionData = getSessionData(session);
            if (sessionData == null) {
                logger.warn("Session validation failed: cannot retrieve session data");
                return false;
            }
            
            // Cross-validate user context with session data
            String contextUserId = (String) userContext.get("userId");
            String sessionUserId = getFieldValue(sessionData, "userId", String.class);
            if (!Objects.equals(contextUserId, sessionUserId)) {
                logger.warn("Session validation failed: user context mismatch");
                return false;
            }
            
            logger.debug("Session attributes validation successful for session: {}", session.getId());
            return true;
            
        } catch (Exception e) {
            logger.error("Error validating session attributes for session: {}", session.getId(), e);
            return false;
        }
    }
    
    /**
     * Creates audit correlation identifiers for session events.
     * Generates unique correlation IDs that link session operations
     * with audit trails and JWT tokens for comprehensive security monitoring.
     * 
     * @param sessionId The session ID
     * @param eventType The type of audit event
     * @return correlation ID for audit tracking
     */
    public String createAuditCorrelationId(String sessionId, String eventType) {
        String correlationId = String.format("%s_%s_%d", 
            sessionId.substring(0, Math.min(8, sessionId.length())),
            eventType,
            System.currentTimeMillis()
        );
        
        logger.debug("Created audit correlation ID: {} for session: {}", correlationId, sessionId);
        return correlationId;
    }
    
    /**
     * Gets the maximum number of concurrent sessions allowed per user.
     * 
     * @return maximum concurrent sessions per user
     */
    public int getMaxConcurrentSessionsPerUser() {
        return MAX_CONCURRENT_SESSIONS_PER_USER;
    }
    
    /**
     * Gets the default session timeout duration.
     * 
     * @return default session timeout
     */
    public Duration getDefaultSessionTimeout() {
        return DEFAULT_SESSION_TIMEOUT;
    }
    
    /**
     * Checks if a session is valid and not expired.
     * Provides a quick validation method for session existence and expiration.
     * 
     * @param sessionId The session ID to check
     * @return true if session exists and is not expired
     */
    public boolean isSessionValid(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return false;
        }
        
        try {
            Session session = sessionRepository.findById(sessionId);
            return session != null && !session.isExpired();
        } catch (Exception e) {
            logger.debug("Error checking session validity for ID: {}", sessionId, e);
            return false;
        }
    }
    
    /**
     * Invalidates a session and performs cleanup.
     * Removes session from repository and cleans up associated Redis keys.
     * 
     * @param sessionId The session ID to invalidate
     * @return true if session was successfully invalidated
     */
    public boolean invalidateSession(String sessionId) {
        logger.debug("Invalidating session: {}", sessionId);
        
        try {
            if (sessionId == null || sessionId.trim().isEmpty()) {
                return false;
            }
            
            // Clean up session data first
            cleanupSessionData(sessionId);
            
            // Remove from repository
            sessionRepository.deleteById(sessionId);
            
            logger.debug("Session invalidated successfully: {}", sessionId);
            return true;
            
        } catch (Exception e) {
            logger.error("Error invalidating session: {}", sessionId, e);
            return false;
        }
    }
    
    /**
     * Updates session last access time to prevent timeout.
     * Extends session lifetime by updating the last accessed timestamp.
     * 
     * @param sessionId The session ID to refresh
     * @return true if session was successfully refreshed
     */
    public boolean refreshSession(String sessionId) {
        logger.debug("Refreshing session: {}", sessionId);
        
        try {
            if (sessionId == null || sessionId.trim().isEmpty()) {
                return false;
            }
            
            Session session = sessionRepository.findById(sessionId);
            if (session == null || session.isExpired()) {
                logger.debug("Cannot refresh expired or non-existent session: {}", sessionId);
                return false;
            }
            
            session.setLastAccessedTime(Instant.now());
            sessionRepository.save(session);
            
            logger.debug("Session refreshed successfully: {}", sessionId);
            return true;
            
        } catch (Exception e) {
            logger.error("Error refreshing session: {}", sessionId, e);
            return false;
        }
    }
    
    /**
     * Gets the number of active sessions for a specific user.
     * Counts concurrent sessions to enforce session limits.
     * 
     * @param userId The user ID to check
     * @return number of active sessions for the user
     */
    public int getActiveSessionCountForUser(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return 0;
        }
        
        try {
            int activeCount = 0;
            Set<String> sessionKeys = redisTemplate.keys(SESSION_KEY_PREFIX + "sessions:*");
            
            if (sessionKeys != null) {
                for (String sessionKey : sessionKeys) {
                    String sessionId = extractSessionIdFromKey(sessionKey);
                    if (sessionId != null) {
                        Session session = sessionRepository.findById(sessionId);
                        if (session != null && !session.isExpired()) {
                            SessionData sessionData = getSessionData(session);
                            String sessionUserId = getSessionUserId(sessionData);
                            if (Objects.equals(userId, sessionUserId)) {
                                activeCount++;
                            }
                        }
                    }
                }
            }
            
            return activeCount;
            
        } catch (Exception e) {
            logger.error("Error counting active sessions for user: {}", userId, e);
            return 0;
        }
    }
    
    /**
     * Creates a new session with initial COMMAREA data.
     * Establishes a new session with proper initialization based on user context.
     * 
     * @param userId The user ID for the session
     * @param userType The user type ('A' for Admin, 'U' for User)
     * @param fromProgram The originating program name
     * @return the created session ID, or null if creation failed
     */
    public String createNewSession(String userId, String userType, String fromProgram) {
        logger.debug("Creating new session for user: {}, type: {}", userId, userType);
        
        try {
            // Check session limits
            if (getActiveSessionCountForUser(userId) >= MAX_CONCURRENT_SESSIONS_PER_USER) {
                logger.warn("Session creation failed: user {} has reached maximum concurrent session limit", userId);
                return null;
            }
            
            // Create new session
            Session session = sessionRepository.createSession();
            
            // Initialize COMMAREA data
            Map<String, Object> initialData = createInitialCommareaData(userId, userType, fromProgram);
            SessionData sessionData = createSessionDataFromAttributes(initialData);
            
            // Store session data
            storeSessionDataInRedis(session, sessionData);
            
            logger.debug("New session created successfully: {}", session.getId());
            return session.getId();
            
        } catch (Exception e) {
            logger.error("Error creating new session for user: {}", userId, e);
            return null;
        }
    }
    
    /**
     * Transfers session context from one program to another.
     * Implements CICS XCTL equivalent functionality for program flow.
     * 
     * @param session The current session
     * @param toProgram The target program name
     * @param toTransactionId The target transaction ID
     * @return true if transfer was successful
     */
    public boolean transferSessionContext(Session session, String toProgram, String toTransactionId) {
        logger.debug("Transferring session context to program: {}, transaction: {}", toProgram, toTransactionId);
        
        try {
            if (session == null || session.isExpired()) {
                return false;
            }
            
            SessionData sessionData = getSessionData(session);
            if (sessionData == null) {
                return false;
            }
            
            // Update program flow context
            setFieldValue(sessionData, "fromProgram", getFieldValue(sessionData, "toProgram", String.class));
            setFieldValue(sessionData, "fromTransactionId", getFieldValue(sessionData, "toTransactionId", String.class));
            setFieldValue(sessionData, "toProgram", toProgram);
            setFieldValue(sessionData, "toTransactionId", toTransactionId);
            
            // Update navigation state
            Map<String, Object> navigationState = (Map<String, Object>) session.getAttribute(NAVIGATION_STATE_KEY);
            if (navigationState != null) {
                List<String> history = (List<String>) navigationState.getOrDefault("navigationHistory", new ArrayList<>());
                history.add(toProgram);
                navigationState.put("navigationHistory", history);
                navigationState.put("currentScreen", toProgram);
                session.setAttribute(NAVIGATION_STATE_KEY, navigationState);
            }
            
            // Save updated session
            storeSessionDataInRedis(session, sessionData);
            
            logger.debug("Session context transferred successfully");
            return true;
            
        } catch (Exception e) {
            logger.error("Error transferring session context", e);
            return false;
        }
    }
    
    // Private helper methods
    
    private boolean validateSessionDataIntegrity(Session session) {
        try {
            SessionData sessionData = getSessionData(session);
            return sessionData != null && validateSessionDataFields(sessionData);
        } catch (Exception e) {
            logger.error("Session data integrity validation error", e);
            return false;
        }
    }
    
    private boolean validateSessionDataFields(SessionData sessionData) {
        // Basic validation of SessionData fields
        if (sessionData == null) return false;
        
        String userId = getFieldValue(sessionData, "userId", String.class);
        String userType = getFieldValue(sessionData, "userType", String.class);
        
        return userId != null
            && !userId.trim().isEmpty()
            && userType != null
            && (userType.equals("A") || userType.equals("U"));
    }
    
    private SessionData createEmptySessionData() {
        // This will be replaced when SessionData class is available
        // For now, return null to indicate missing implementation
        logger.debug("Creating empty SessionData - implementation pending SessionData class creation");
        return null;
    }
    
    private void enrichSessionDataFromContext(SessionData sessionData, Session session) {
        if (sessionData != null) {
            // Add session-specific metadata using reflection-based approach
            setFieldValue(sessionData, "sessionId", session.getId());
            setFieldValue(sessionData, "lastAccessTime", session.getLastAccessedTime().toString());
            setFieldValue(sessionData, "creationTime", session.getCreationTime().toString());
        }
    }
    
    private Map<String, Object> createTransactionContext(SessionData sessionData) {
        Map<String, Object> transactionContext = new HashMap<>();
        
        if (sessionData != null) {
            transactionContext.put("customerId", getFieldValue(sessionData, "customerId", String.class));
            transactionContext.put("customerFirstName", getFieldValue(sessionData, "customerFirstName", String.class));
            transactionContext.put("customerMiddleName", getFieldValue(sessionData, "customerMiddleName", String.class));
            transactionContext.put("customerLastName", getFieldValue(sessionData, "customerLastName", String.class));
            transactionContext.put("accountId", getFieldValue(sessionData, "accountId", String.class));
            transactionContext.put("accountStatus", getFieldValue(sessionData, "accountStatus", String.class));
            transactionContext.put("cardNumber", getFieldValue(sessionData, "cardNumber", String.class));
        }
        
        return transactionContext;
    }
    
    private Map<String, Object> createProgramFlowContext(SessionData sessionData) {
        Map<String, Object> programFlowContext = new HashMap<>();
        
        if (sessionData != null) {
            programFlowContext.put("programContext", getFieldValue(sessionData, "programContext", Integer.class));
            programFlowContext.put("fromProgram", getFieldValue(sessionData, "fromProgram", String.class));
            programFlowContext.put("toProgram", getFieldValue(sessionData, "toProgram", String.class));
            programFlowContext.put("executionFlow", new ArrayList<String>());
        }
        
        return programFlowContext;
    }
    
    private void storeSessionAuditData(String sessionId, SessionData sessionData) {
        try {
            String auditKey = SESSION_AUDIT_KEY_PREFIX + sessionId;
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("userId", getFieldValue(sessionData, "userId", String.class));
            auditData.put("userType", getFieldValue(sessionData, "userType", String.class));
            auditData.put("timestamp", Instant.now().toString());
            auditData.put("sessionId", sessionId);
            
            String auditJson = objectMapper.writeValueAsString(auditData);
            redisTemplate.opsForValue().set(auditKey, auditJson, DEFAULT_SESSION_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            
        } catch (Exception e) {
            logger.warn("Failed to store session audit data for session: {}", sessionId, e);
        }
    }
    
    private String extractSessionIdFromKey(String sessionKey) {
        if (sessionKey == null || !sessionKey.startsWith(SESSION_KEY_PREFIX)) {
            return null;
        }
        
        String[] parts = sessionKey.split(":");
        return parts.length > 3 ? parts[parts.length - 1] : null;
    }
    
    private boolean isSessionExpiredByCutoff(Session session, Instant cutoffTime) {
        return session.isExpired() || session.getLastAccessedTime().isBefore(cutoffTime);
    }
    
    private void cleanupSessionData(String sessionId) {
        try {
            // Clean up audit data
            String auditKey = SESSION_AUDIT_KEY_PREFIX + sessionId;
            redisTemplate.delete(auditKey);
            
            // Clean up expiration tracking
            String expirationKey = SESSION_EXPIRATION_KEY_PREFIX + sessionId;
            redisTemplate.delete(expirationKey);
            
        } catch (Exception e) {
            logger.warn("Error cleaning up session data for session: {}", sessionId, e);
        }
    }
    
    private void cleanupOrphanedSessionKeys() {
        try {
            // Clean up orphaned audit keys
            Set<String> auditKeys = redisTemplate.keys(SESSION_AUDIT_KEY_PREFIX + "*");
            if (auditKeys != null) {
                for (String auditKey : auditKeys) {
                    String sessionId = extractSessionIdFromKey(auditKey);
                    if (sessionId != null && sessionRepository.findById(sessionId) == null) {
                        redisTemplate.delete(auditKey);
                    }
                }
            }
            
            // Clean up orphaned expiration keys
            Set<String> expirationKeys = redisTemplate.keys(SESSION_EXPIRATION_KEY_PREFIX + "*");
            if (expirationKeys != null) {
                for (String expirationKey : expirationKeys) {
                    String sessionId = extractSessionIdFromKey(expirationKey);
                    if (sessionId != null && sessionRepository.findById(sessionId) == null) {
                        redisTemplate.delete(expirationKey);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.warn("Error cleaning up orphaned session keys", e);
        }
    }
    
    // Helper methods for SessionData interface compatibility
    // These methods provide a bridge to the SessionData implementation
    // until the actual SessionData class is available from other agents
    
    /**
     * Extracts user ID from SessionData object.
     * Provides compatibility layer for SessionData access patterns.
     */
    private String getSessionUserId(SessionData sessionData) {
        if (sessionData == null) return null;
        
        try {
            // Use reflection to get field value until SessionData class is available
            return getFieldValue(sessionData, "userId", String.class);
        } catch (Exception e) {
            logger.debug("Failed to extract user ID from session data", e);
            return null;
        }
    }
    
    /**
     * Extracts user type from SessionData object.
     * Provides compatibility layer for SessionData access patterns.
     */
    private String getSessionUserType(SessionData sessionData) {
        if (sessionData == null) return null;
        
        try {
            // Use reflection to get field value until SessionData class is available
            return getFieldValue(sessionData, "userType", String.class);
        } catch (Exception e) {
            logger.debug("Failed to extract user type from session data", e);
            return null;
        }
    }
    
    /**
     * Generic field value extraction using reflection.
     * Temporary implementation until SessionData class becomes available.
     */
    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(Object obj, String fieldName, Class<T> expectedType) {
        if (obj == null || fieldName == null) return null;
        
        try {
            // First try as Map (for JSON deserialization scenarios)
            if (obj instanceof Map) {
                Object value = ((Map<String, Object>) obj).get(fieldName);
                if (expectedType.isInstance(value)) {
                    return expectedType.cast(value);
                }
            }
            
            // Then try reflection on object fields
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(obj);
            
            if (expectedType.isInstance(value)) {
                return expectedType.cast(value);
            }
            
        } catch (Exception e) {
            logger.debug("Reflection field access failed for field: {}", fieldName, e);
        }
        
        return null;
    }
    
    /**
     * Sets field value using reflection.
     * Temporary implementation until SessionData class becomes available.
     */
    private void setFieldValue(Object obj, String fieldName, Object value) {
        if (obj == null || fieldName == null) return;
        
        try {
            // First try as Map (for JSON deserialization scenarios)
            if (obj instanceof Map) {
                ((Map<String, Object>) obj).put(fieldName, value);
                return;
            }
            
            // Then try reflection on object fields
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
            
        } catch (Exception e) {
            logger.debug("Reflection field setting failed for field: {}", fieldName, e);
        }
    }
    
    /**
     * Creates a SessionData-compatible object from session attributes.
     * This method bridges the gap until SessionData class is available.
     */
    private SessionData createSessionDataFromAttributes(Map<String, Object> attributes) {
        try {
            // For now, use a Map-based approach that can be serialized to JSON
            Map<String, Object> sessionDataMap = new HashMap<>();
            
            // Copy COMMAREA equivalent fields based on COBOL structure
            sessionDataMap.put("userId", attributes.get("CDEMO-USER-ID"));
            sessionDataMap.put("userType", attributes.get("CDEMO-USER-TYPE"));
            sessionDataMap.put("fromTransactionId", attributes.get("CDEMO-FROM-TRANID"));
            sessionDataMap.put("fromProgram", attributes.get("CDEMO-FROM-PROGRAM"));
            sessionDataMap.put("toTransactionId", attributes.get("CDEMO-TO-TRANID"));
            sessionDataMap.put("toProgram", attributes.get("CDEMO-TO-PROGRAM"));
            sessionDataMap.put("programContext", attributes.get("CDEMO-PGM-CONTEXT"));
            sessionDataMap.put("customerId", attributes.get("CDEMO-CUST-ID"));
            sessionDataMap.put("customerFirstName", attributes.get("CDEMO-CUST-FNAME"));
            sessionDataMap.put("customerMiddleName", attributes.get("CDEMO-CUST-MNAME"));
            sessionDataMap.put("customerLastName", attributes.get("CDEMO-CUST-LNAME"));
            sessionDataMap.put("accountId", attributes.get("CDEMO-ACCT-ID"));
            sessionDataMap.put("accountStatus", attributes.get("CDEMO-ACCT-STATUS"));
            sessionDataMap.put("cardNumber", attributes.get("CDEMO-CARD-NUM"));
            sessionDataMap.put("lastMap", attributes.get("CDEMO-LAST-MAP"));
            sessionDataMap.put("lastMapset", attributes.get("CDEMO-LAST-MAPSET"));
            
            // Return the map as SessionData for now (will be converted when SessionData class is available)
            return (SessionData) sessionDataMap;
            
        } catch (Exception e) {
            logger.error("Error creating SessionData from attributes", e);
            return null;
        }
    }
    
    /**
     * Creates initial COMMAREA data structure for new sessions.
     * Based on COBOL CARDDEMO-COMMAREA structure from COCOM01Y.cpy.
     */
    private Map<String, Object> createInitialCommareaData(String userId, String userType, String fromProgram) {
        Map<String, Object> commareaData = new HashMap<>();
        
        // General info section (CDEMO-GENERAL-INFO)
        commareaData.put("CDEMO-USER-ID", userId);
        commareaData.put("CDEMO-USER-TYPE", userType);
        commareaData.put("CDEMO-FROM-PROGRAM", fromProgram);
        commareaData.put("CDEMO-FROM-TRANID", determineTransactionId(fromProgram));
        commareaData.put("CDEMO-TO-PROGRAM", "");
        commareaData.put("CDEMO-TO-TRANID", "");
        commareaData.put("CDEMO-PGM-CONTEXT", 0); // CDEMO-PGM-ENTER
        
        // Customer info section (CDEMO-CUSTOMER-INFO) - initialize empty
        commareaData.put("CDEMO-CUST-ID", 0L);
        commareaData.put("CDEMO-CUST-FNAME", "");
        commareaData.put("CDEMO-CUST-MNAME", "");
        commareaData.put("CDEMO-CUST-LNAME", "");
        
        // Account info section (CDEMO-ACCOUNT-INFO) - initialize empty
        commareaData.put("CDEMO-ACCT-ID", 0L);
        commareaData.put("CDEMO-ACCT-STATUS", "");
        
        // Card info section (CDEMO-CARD-INFO) - initialize empty
        commareaData.put("CDEMO-CARD-NUM", 0L);
        
        // More info section (CDEMO-MORE-INFO) - initialize empty
        commareaData.put("CDEMO-LAST-MAP", "");
        commareaData.put("CDEMO-LAST-MAPSET", "");
        
        return commareaData;
    }
    
    /**
     * Determines transaction ID based on program name.
     * Maps program names to their corresponding transaction IDs.
     */
    private String determineTransactionId(String programName) {
        if (programName == null) return "";
        
        switch (programName.toUpperCase()) {
            case "COSGN00C": return "CC00";
            case "COMEN01C": return "CM01";
            case "COADM01C": return "CA01";
            case "COACTVWC": return "CV01";
            case "COACTUPC": return "CU01";
            case "COCRDLIC": return "CL01";
            case "COCRDSLC": return "CS01";
            case "COCRDUPC": return "CD01";
            case "COTRN00C": return "CT00";
            case "COTRN01C": return "CT01";
            case "COTRN02C": return "CT02";
            case "COBIL00C": return "CB00";
            case "COUSR00C": return "CU00";
            case "COUSR01C": return "CU01";
            case "COUSR02C": return "CU02";
            case "COUSR03C": return "CU03";
            case "CORPT00C": return "CR00";
            default: return "UNKN";
        }
    }
}
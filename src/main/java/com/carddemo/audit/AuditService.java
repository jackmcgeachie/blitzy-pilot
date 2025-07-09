package com.carddemo.audit;

import org.springframework.stereotype.Service;
import org.springframework.aop.framework.AopContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Collections;

/**
 * Spring Boot service implementing comprehensive security audit logging to replace mainframe SMF records.
 * 
 * This service provides enterprise-grade audit trail functionality through Spring AOP interceptors,
 * capturing authentication events, authorization decisions, data access operations, and administrative actions.
 * Features asynchronous audit event processing, JWT correlation tracking, and SIEM integration capabilities
 * for compliance reporting and security monitoring in financial environments.
 * 
 * Key Features:
 * - Comprehensive security event capture through Spring AOP interceptors
 * - SMF-compatible audit record structures for enterprise SIEM integration
 * - Asynchronous processing to prevent impact on transaction response times
 * - JWT correlation tracking for distributed session audit trail integrity
 * - Integration with Spring Security for automatic event capture
 * - Multiple audit event categories for security monitoring
 * - SIEM forwarding for centralized security monitoring
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since 2024-01-15
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    
    // SMF-compatible record type constants
    private static final String SMF_AUTHENTICATION_TYPE = "030";
    private static final String SMF_AUTHORIZATION_TYPE = "031";
    private static final String SMF_DATA_ACCESS_TYPE = "032";
    private static final String SMF_ADMIN_ACCESS_TYPE = "033";
    private static final String SMF_USER_MGMT_TYPE = "034";
    private static final String SMF_SECURITY_EVENT_TYPE = "035";
    private static final String SMF_NAVIGATION_TYPE = "036";
    private static final String SMF_TRANSACTION_TYPE = "037";
    
    // SMF timestamp format (hundreds of seconds since midnight)
    private static final DateTimeFormatter SMF_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HHmmss.SS");
    
    // Audit event sequence counter for correlation
    private final AtomicLong auditEventSequence = new AtomicLong(0);
    
    // Application instance identifier for distributed environments
    @Value("${spring.application.name:CardDemo}")
    private String systemIdentifier;
    
    @Value("${server.port:8080}")
    private String subsystemIdentifier;
    
    // Async processing configuration
    private final Map<String, Object> auditEventCache = new ConcurrentHashMap<>();
    
    /**
     * Logs authentication events including successful and failed login attempts.
     * 
     * This method captures all authentication-related security events and formats them
     * as SMF-compatible audit records for enterprise SIEM integration. Events include
     * user login attempts, JWT token validation, and authentication context changes.
     * 
     * @param username The username involved in the authentication event
     * @param eventType The type of authentication event (LOGIN_SUCCESS, LOGIN_FAILURE, TOKEN_REFRESH)
     * @param sourceIp The source IP address of the authentication request
     * @param additionalContext Additional context information for the event
     */
    @Async
    public CompletableFuture<Void> logAuthenticationEvent(String username, String eventType, 
                                                         String sourceIp, Map<String, Object> additionalContext) {
        try {
            // Get current security context for JWT correlation
            SecurityContext securityContext = SecurityContextHolder.getContext();
            Authentication authentication = securityContext.getAuthentication();
            
            // Generate unique audit event ID
            String auditEventId = generateAuditEventId();
            
            // Create SMF-compatible authentication record
            Map<String, Object> auditRecord = createSMFRecord(SMF_AUTHENTICATION_TYPE, auditEventId);
            
            // Populate authentication-specific fields
            auditRecord.put("username", username);
            auditRecord.put("event_type", eventType);
            auditRecord.put("source_ip", sourceIp);
            auditRecord.put("authentication_method", "JWT_TOKEN");
            auditRecord.put("user_agent", extractUserAgent(additionalContext));
            
            // Add JWT correlation tracking
            if (authentication != null && authentication.getDetails() != null) {
                auditRecord.put("jwt_token_id", extractJWTTokenId(authentication));
                auditRecord.put("user_authorities", authentication.getAuthorities().toString());
            }
            
            // Include additional context
            if (additionalContext != null && !additionalContext.isEmpty()) {
                auditRecord.put("additional_context", additionalContext);
            }
            
            // Log security event outcome
            auditRecord.put("event_outcome", determineEventOutcome(eventType));
            
            // Publish audit record asynchronously
            publishAuditRecord(auditRecord);
            
            logger.info("Authentication audit event logged: {} for user: {}", eventType, username);
            
        } catch (Exception e) {
            logger.error("Failed to log authentication event for user: {} - {}", username, e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Logs authorization events including access control decisions and privilege validations.
     * 
     * This method captures authorization-related security events including role-based access
     * control decisions, method-level security validations, and privilege escalation attempts.
     * All events are formatted as SMF-compatible records for enterprise security monitoring.
     * 
     * @param username The username subject to authorization
     * @param resourceType The type of resource being accessed (ACCOUNT, CARD, TRANSACTION, etc.)
     * @param resourceId The specific resource identifier
     * @param action The action being attempted (READ, WRITE, UPDATE, DELETE)
     * @param authorizationResult The result of the authorization check (GRANTED, DENIED)
     * @param roleContext The user's role context during authorization
     */
    @Async
    public CompletableFuture<Void> logAuthorizationEvent(String username, String resourceType, 
                                                        String resourceId, String action, 
                                                        String authorizationResult, String roleContext) {
        try {
            // Generate unique audit event ID
            String auditEventId = generateAuditEventId();
            
            // Create SMF-compatible authorization record
            Map<String, Object> auditRecord = createSMFRecord(SMF_AUTHORIZATION_TYPE, auditEventId);
            
            // Populate authorization-specific fields
            auditRecord.put("username", username);
            auditRecord.put("resource_type", resourceType);
            auditRecord.put("resource_id", resourceId);
            auditRecord.put("action", action);
            auditRecord.put("authorization_result", authorizationResult);
            auditRecord.put("role_context", roleContext);
            
            // Add Spring Security context information
            SecurityContext securityContext = SecurityContextHolder.getContext();
            Authentication authentication = securityContext.getAuthentication();
            
            if (authentication != null) {
                auditRecord.put("jwt_token_id", extractJWTTokenId(authentication));
                auditRecord.put("authentication_type", authentication.getClass().getSimpleName());
                auditRecord.put("is_authenticated", authentication.isAuthenticated());
            }
            
            // Determine risk level based on authorization result
            auditRecord.put("risk_level", determineRiskLevel(authorizationResult, action));
            
            // Publish audit record asynchronously
            publishAuditRecord(auditRecord);
            
            logger.info("Authorization audit event logged: {} access to {} {} for user: {}", 
                       authorizationResult, resourceType, resourceId, username);
            
        } catch (Exception e) {
            logger.error("Failed to log authorization event for user: {} - {}", username, e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Logs data access events including database operations and file system access.
     * 
     * This method captures all data access operations including database queries, updates,
     * and file system operations. Events are formatted as SMF-compatible records with
     * comprehensive context information for security monitoring and compliance reporting.
     * 
     * @param username The username performing the data access
     * @param dataType The type of data being accessed (CUSTOMER, ACCOUNT, CARD, TRANSACTION)
     * @param operation The database operation (SELECT, INSERT, UPDATE, DELETE)
     * @param recordCount The number of records affected
     * @param queryDetails Additional query execution details
     */
    @Async
    public CompletableFuture<Void> logDataAccessEvent(String username, String dataType, 
                                                     String operation, int recordCount, 
                                                     Map<String, Object> queryDetails) {
        try {
            // Generate unique audit event ID
            String auditEventId = generateAuditEventId();
            
            // Create SMF-compatible data access record
            Map<String, Object> auditRecord = createSMFRecord(SMF_DATA_ACCESS_TYPE, auditEventId);
            
            // Populate data access-specific fields
            auditRecord.put("username", username);
            auditRecord.put("data_type", dataType);
            auditRecord.put("operation", operation);
            auditRecord.put("record_count", recordCount);
            auditRecord.put("execution_time_ms", extractExecutionTime(queryDetails));
            
            // Add database context information
            auditRecord.put("database_name", "CardDemo_PostgreSQL");
            auditRecord.put("table_name", extractTableName(dataType));
            auditRecord.put("connection_pool", "HikariCP");
            
            // Include query performance metrics
            if (queryDetails != null && !queryDetails.isEmpty()) {
                auditRecord.put("query_details", queryDetails);
                auditRecord.put("query_complexity", determineQueryComplexity(queryDetails));
            }
            
            // Add session correlation
            SecurityContext securityContext = SecurityContextHolder.getContext();
            Authentication authentication = securityContext.getAuthentication();
            
            if (authentication != null) {
                auditRecord.put("jwt_token_id", extractJWTTokenId(authentication));
                auditRecord.put("session_id", extractSessionId(authentication));
            }
            
            // Determine data sensitivity level
            auditRecord.put("data_sensitivity", determineDataSensitivity(dataType));
            
            // Publish audit record asynchronously
            publishAuditRecord(auditRecord);
            
            logger.info("Data access audit event logged: {} operation on {} affecting {} records for user: {}", 
                       operation, dataType, recordCount, username);
            
        } catch (Exception e) {
            logger.error("Failed to log data access event for user: {} - {}", username, e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Logs administrative menu access events for privileged operations.
     * 
     * This method captures access to administrative functions including user management,
     * system configuration, and privileged operations. Events are formatted for enterprise
     * security monitoring with emphasis on privilege escalation detection.
     * 
     * @param username The username accessing administrative functions
     * @param menuOption The specific administrative menu option accessed
     * @param privilegeLevel The required privilege level for the operation
     * @param accessResult The result of the access attempt (GRANTED, DENIED)
     */
    @Async
    public CompletableFuture<Void> logAdminMenuAccess(String username, String menuOption, 
                                                     String privilegeLevel, String accessResult) {
        try {
            // Generate unique audit event ID
            String auditEventId = generateAuditEventId();
            
            // Create SMF-compatible administrative access record
            Map<String, Object> auditRecord = createSMFRecord(SMF_ADMIN_ACCESS_TYPE, auditEventId);
            
            // Populate administrative access-specific fields
            auditRecord.put("username", username);
            auditRecord.put("menu_option", menuOption);
            auditRecord.put("privilege_level", privilegeLevel);
            auditRecord.put("access_result", accessResult);
            auditRecord.put("admin_function_type", determineAdminFunctionType(menuOption));
            
            // Add security context information
            SecurityContext securityContext = SecurityContextHolder.getContext();
            Authentication authentication = securityContext.getAuthentication();
            
            if (authentication != null) {
                auditRecord.put("jwt_token_id", extractJWTTokenId(authentication));
                auditRecord.put("current_authorities", authentication.getAuthorities().toString());
                auditRecord.put("is_admin_user", hasAdminRole(authentication));
            }
            
            // Determine risk level for administrative access
            auditRecord.put("risk_level", determineAdminRiskLevel(menuOption, accessResult));
            
            // Add timestamp for privilege escalation detection
            auditRecord.put("privilege_escalation_window", LocalDateTime.now().minusMinutes(5));
            
            // Publish audit record asynchronously
            publishAuditRecord(auditRecord);
            
            logger.info("Administrative menu access audit event logged: {} access to {} for user: {}", 
                       accessResult, menuOption, username);
            
        } catch (Exception e) {
            logger.error("Failed to log administrative menu access event for user: {} - {}", username, e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Logs user management operations including user creation, updates, and role changes.
     * 
     * This method captures all user management operations with comprehensive context
     * for security monitoring and compliance reporting. Events include user lifecycle
     * operations, role assignments, and permission changes.
     * 
     * @param adminUser The administrator performing the user management operation
     * @param targetUser The user being managed
     * @param operation The management operation (CREATE, UPDATE, DELETE, ROLE_CHANGE)
     * @param operationDetails Additional details about the operation
     */
    @Async
    public CompletableFuture<Void> logUserManagementOperation(String adminUser, String targetUser, 
                                                             String operation, Map<String, Object> operationDetails) {
        try {
            // Generate unique audit event ID
            String auditEventId = generateAuditEventId();
            
            // Create SMF-compatible user management record
            Map<String, Object> auditRecord = createSMFRecord(SMF_USER_MGMT_TYPE, auditEventId);
            
            // Populate user management-specific fields
            auditRecord.put("admin_user", adminUser);
            auditRecord.put("target_user", targetUser);
            auditRecord.put("operation", operation);
            auditRecord.put("operation_category", determineOperationCategory(operation));
            
            // Add operation details
            if (operationDetails != null && !operationDetails.isEmpty()) {
                auditRecord.put("operation_details", operationDetails);
                auditRecord.put("before_state", operationDetails.get("before_state"));
                auditRecord.put("after_state", operationDetails.get("after_state"));
            }
            
            // Add security context information
            SecurityContext securityContext = SecurityContextHolder.getContext();
            Authentication authentication = securityContext.getAuthentication();
            
            if (authentication != null) {
                auditRecord.put("jwt_token_id", extractJWTTokenId(authentication));
                auditRecord.put("admin_authorities", authentication.getAuthorities().toString());
                auditRecord.put("admin_authentication_time", extractAuthenticationTime(authentication));
            }
            
            // Determine operation risk level
            auditRecord.put("risk_level", determineUserMgmtRiskLevel(operation));
            
            // Add compliance tracking
            auditRecord.put("compliance_category", "USER_LIFECYCLE_MANAGEMENT");
            auditRecord.put("regulatory_impact", determineRegulatoryImpact(operation));
            
            // Publish audit record asynchronously
            publishAuditRecord(auditRecord);
            
            logger.info("User management audit event logged: {} operation on user {} by admin: {}", 
                       operation, targetUser, adminUser);
            
        } catch (Exception e) {
            logger.error("Failed to log user management operation for admin: {} - {}", adminUser, e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Logs general security events including system-level security incidents.
     * 
     * This method captures general security events that don't fit into specific categories
     * but are important for security monitoring and incident response. Events include
     * system configuration changes, security policy violations, and anomaly detection.
     * 
     * @param eventType The type of security event
     * @param severity The severity level of the event (LOW, MEDIUM, HIGH, CRITICAL)
     * @param description A detailed description of the security event
     * @param affectedComponents The system components affected by the event
     */
    @Async
    public CompletableFuture<Void> logSecurityEvent(String eventType, String severity, 
                                                   String description, Map<String, Object> affectedComponents) {
        try {
            // Generate unique audit event ID
            String auditEventId = generateAuditEventId();
            
            // Create SMF-compatible security event record
            Map<String, Object> auditRecord = createSMFRecord(SMF_SECURITY_EVENT_TYPE, auditEventId);
            
            // Populate security event-specific fields
            auditRecord.put("event_type", eventType);
            auditRecord.put("severity", severity);
            auditRecord.put("description", description);
            auditRecord.put("event_category", determineSecurityEventCategory(eventType));
            
            // Add affected components information
            if (affectedComponents != null && !affectedComponents.isEmpty()) {
                auditRecord.put("affected_components", affectedComponents);
                auditRecord.put("component_count", affectedComponents.size());
            }
            
            // Add security context if available
            SecurityContext securityContext = SecurityContextHolder.getContext();
            Authentication authentication = securityContext.getAuthentication();
            
            if (authentication != null) {
                auditRecord.put("jwt_token_id", extractJWTTokenId(authentication));
                auditRecord.put("associated_user", authentication.getName());
            } else {
                auditRecord.put("associated_user", "SYSTEM");
            }
            
            // Add system state information
            auditRecord.put("system_load", getCurrentSystemLoad());
            auditRecord.put("active_sessions", getActiveSessionCount());
            
            // Determine incident response requirements
            auditRecord.put("incident_response_required", requiresIncidentResponse(severity));
            auditRecord.put("escalation_level", determineEscalationLevel(severity));
            
            // Publish audit record asynchronously
            publishAuditRecord(auditRecord);
            
            logger.warn("Security event audit logged: {} with severity {} - {}", eventType, severity, description);
            
        } catch (Exception e) {
            logger.error("Failed to log security event: {} - {}", eventType, e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Logs navigation events including page transitions and menu selections.
     * 
     * This method captures user navigation patterns for security monitoring and
     * user behavior analysis. Events include page transitions, menu selections,
     * and workflow navigation patterns.
     * 
     * @param username The username performing navigation
     * @param fromPage The source page or menu
     * @param toPage The destination page or menu
     * @param navigationMethod The method of navigation (MENU_SELECTION, DIRECT_URL, FORM_SUBMISSION)
     */
    @Async
    public CompletableFuture<Void> logNavigationEvent(String username, String fromPage, 
                                                     String toPage, String navigationMethod) {
        try {
            // Generate unique audit event ID
            String auditEventId = generateAuditEventId();
            
            // Create SMF-compatible navigation record
            Map<String, Object> auditRecord = createSMFRecord(SMF_NAVIGATION_TYPE, auditEventId);
            
            // Populate navigation-specific fields
            auditRecord.put("username", username);
            auditRecord.put("from_page", fromPage);
            auditRecord.put("to_page", toPage);
            auditRecord.put("navigation_method", navigationMethod);
            auditRecord.put("navigation_type", determineNavigationType(fromPage, toPage));
            
            // Add session context information
            SecurityContext securityContext = SecurityContextHolder.getContext();
            Authentication authentication = securityContext.getAuthentication();
            
            if (authentication != null) {
                auditRecord.put("jwt_token_id", extractJWTTokenId(authentication));
                auditRecord.put("session_id", extractSessionId(authentication));
                auditRecord.put("user_role", extractUserRole(authentication));
            }
            
            // Add navigation pattern analysis
            auditRecord.put("navigation_pattern", analyzeNavigationPattern(fromPage, toPage));
            auditRecord.put("suspicious_navigation", detectSuspiciousNavigation(fromPage, toPage, username));
            
            // Publish audit record asynchronously
            publishAuditRecord(auditRecord);
            
            logger.debug("Navigation audit event logged: {} navigated from {} to {} via {}", 
                        username, fromPage, toPage, navigationMethod);
            
        } catch (Exception e) {
            logger.error("Failed to log navigation event for user: {} - {}", username, e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Logs transaction events including financial transactions and business operations.
     * 
     * This method captures all transaction-related events including financial transactions,
     * account operations, and business process executions. Events are formatted for
     * financial compliance and regulatory reporting requirements.
     * 
     * @param username The username performing the transaction
     * @param transactionType The type of transaction (PAYMENT, TRANSFER, BALANCE_INQUIRY)
     * @param transactionId The unique transaction identifier
     * @param amount The transaction amount (if applicable)
     * @param transactionDetails Additional transaction context
     */
    @Async
    public CompletableFuture<Void> logTransactionEvent(String username, String transactionType, 
                                                      String transactionId, Double amount, 
                                                      Map<String, Object> transactionDetails) {
        try {
            // Generate unique audit event ID
            String auditEventId = generateAuditEventId();
            
            // Create SMF-compatible transaction record
            Map<String, Object> auditRecord = createSMFRecord(SMF_TRANSACTION_TYPE, auditEventId);
            
            // Populate transaction-specific fields
            auditRecord.put("username", username);
            auditRecord.put("transaction_type", transactionType);
            auditRecord.put("transaction_id", transactionId);
            auditRecord.put("amount", amount);
            auditRecord.put("transaction_category", determineTransactionCategory(transactionType));
            
            // Add transaction details
            if (transactionDetails != null && !transactionDetails.isEmpty()) {
                auditRecord.put("transaction_details", transactionDetails);
                auditRecord.put("account_id", transactionDetails.get("account_id"));
                auditRecord.put("card_number", maskCardNumber(transactionDetails.get("card_number")));
                auditRecord.put("merchant_info", transactionDetails.get("merchant_info"));
            }
            
            // Add security context information
            SecurityContext securityContext = SecurityContextHolder.getContext();
            Authentication authentication = securityContext.getAuthentication();
            
            if (authentication != null) {
                auditRecord.put("jwt_token_id", extractJWTTokenId(authentication));
                auditRecord.put("transaction_user_role", extractUserRole(authentication));
                auditRecord.put("authentication_method", authentication.getClass().getSimpleName());
            }
            
            // Add transaction risk assessment
            auditRecord.put("risk_score", calculateTransactionRiskScore(transactionType, amount));
            auditRecord.put("compliance_flags", generateComplianceFlags(transactionType, amount));
            
            // Add financial compliance tracking
            auditRecord.put("regulatory_reporting", requiresRegulatoryReporting(transactionType, amount));
            auditRecord.put("audit_trail_required", true);
            
            // Publish audit record asynchronously
            publishAuditRecord(auditRecord);
            
            logger.info("Transaction audit event logged: {} transaction {} for user: {} amount: {}", 
                       transactionType, transactionId, username, amount);
            
        } catch (Exception e) {
            logger.error("Failed to log transaction event for user: {} - {}", username, e.getMessage(), e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    // Private helper methods for audit record processing
    
    /**
     * Creates a baseline SMF-compatible audit record structure.
     */
    private Map<String, Object> createSMFRecord(String recordType, String auditEventId) {
        Map<String, Object> record = new ConcurrentHashMap<>();
        
        // SMF header fields
        record.put("smf_record_type", recordType);
        record.put("smf_timestamp", LocalDateTime.now().format(SMF_TIMESTAMP_FORMAT));
        record.put("smf_date", LocalDateTime.now().toLocalDate().toString());
        record.put("system_id", systemIdentifier != null ? systemIdentifier : "CardDemo");
        record.put("subsystem_id", subsystemIdentifier != null ? subsystemIdentifier : "8080");
        record.put("audit_event_id", auditEventId);
        record.put("event_sequence", auditEventSequence.incrementAndGet());
        
        // Common audit fields
        record.put("application_name", "CardDemo");
        record.put("application_version", "1.0.0");
        record.put("audit_timestamp", LocalDateTime.now().toString());
        record.put("correlation_id", UUID.randomUUID().toString());
        
        return record;
    }
    
    /**
     * Generates a unique audit event identifier.
     */
    private String generateAuditEventId() {
        return String.format("AUD_%s_%d", 
                           LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")), 
                           auditEventSequence.incrementAndGet());
    }
    
    /**
     * Publishes audit record to enterprise SIEM infrastructure.
     */
    private void publishAuditRecord(Map<String, Object> auditRecord) {
        try {
            // Log to structured logging system for SIEM pickup
            logger.info("AUDIT_RECORD: {}", auditRecord);
            
            // Cache audit record for immediate retrieval if needed
            String auditEventId = (String) auditRecord.get("audit_event_id");
            auditEventCache.put(auditEventId, auditRecord);
            
            // Here would be integration with enterprise SIEM system
            // forwardToSIEM(auditRecord);
            
        } catch (Exception e) {
            logger.error("Failed to publish audit record: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Extracts JWT token ID from authentication context.
     */
    private String extractJWTTokenId(Authentication authentication) {
        if (authentication != null && authentication.getDetails() != null) {
            Object details = authentication.getDetails();
            if (details instanceof Map) {
                Map<?, ?> detailsMap = (Map<?, ?>) details;
                return (String) detailsMap.get("jti");
            }
        }
        return "UNKNOWN_TOKEN";
    }
    
    /**
     * Extracts user agent from additional context.
     */
    private String extractUserAgent(Map<String, Object> additionalContext) {
        if (additionalContext != null) {
            return (String) additionalContext.getOrDefault("user_agent", "UNKNOWN_AGENT");
        }
        return "UNKNOWN_AGENT";
    }
    
    /**
     * Determines event outcome based on event type.
     */
    private String determineEventOutcome(String eventType) {
        if (eventType.contains("SUCCESS") || eventType.contains("GRANTED")) {
            return "SUCCESS";
        } else if (eventType.contains("FAILURE") || eventType.contains("DENIED")) {
            return "FAILURE";
        }
        return "UNKNOWN";
    }
    
    /**
     * Determines risk level based on authorization result and action.
     */
    private String determineRiskLevel(String authorizationResult, String action) {
        if ("DENIED".equals(authorizationResult)) {
            if ("DELETE".equals(action) || "ADMIN".equals(action)) {
                return "HIGH";
            }
            return "MEDIUM";
        }
        return "LOW";
    }
    
    /**
     * Extracts execution time from query details.
     */
    private Long extractExecutionTime(Map<String, Object> queryDetails) {
        if (queryDetails != null) {
            return (Long) queryDetails.getOrDefault("execution_time_ms", 0L);
        }
        return 0L;
    }
    
    /**
     * Extracts table name from data type.
     */
    private String extractTableName(String dataType) {
        switch (dataType.toLowerCase()) {
            case "customer": return "customers";
            case "account": return "accounts";
            case "card": return "cards";
            case "transaction": return "transactions";
            case "user": return "users";
            default: return "unknown_table";
        }
    }
    
    /**
     * Determines query complexity based on query details.
     */
    private String determineQueryComplexity(Map<String, Object> queryDetails) {
        if (queryDetails != null) {
            Long executionTime = (Long) queryDetails.getOrDefault("execution_time_ms", 0L);
            if (executionTime > 1000) return "HIGH";
            if (executionTime > 100) return "MEDIUM";
        }
        return "LOW";
    }
    
    /**
     * Determines data sensitivity level.
     */
    private String determineDataSensitivity(String dataType) {
        switch (dataType.toLowerCase()) {
            case "customer": return "PII";
            case "account": return "FINANCIAL";
            case "card": return "FINANCIAL";
            case "transaction": return "FINANCIAL";
            case "user": return "AUTHENTICATION";
            default: return "GENERAL";
        }
    }
    
    /**
     * Extracts session ID from authentication context.
     */
    private String extractSessionId(Authentication authentication) {
        if (authentication != null && authentication.getDetails() != null) {
            Object details = authentication.getDetails();
            if (details instanceof Map) {
                Map<?, ?> detailsMap = (Map<?, ?>) details;
                return (String) detailsMap.get("session_id");
            }
        }
        return "UNKNOWN_SESSION";
    }
    
    /**
     * Determines administrative function type.
     */
    private String determineAdminFunctionType(String menuOption) {
        if (menuOption.contains("USER")) return "USER_MANAGEMENT";
        if (menuOption.contains("SYSTEM")) return "SYSTEM_CONFIGURATION";
        if (menuOption.contains("REPORT")) return "REPORTING";
        return "GENERAL_ADMIN";
    }
    
    /**
     * Checks if authentication has admin role.
     */
    private boolean hasAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
    
    /**
     * Determines administrative risk level.
     */
    private String determineAdminRiskLevel(String menuOption, String accessResult) {
        if ("DENIED".equals(accessResult)) return "HIGH";
        if (menuOption.contains("DELETE") || menuOption.contains("SYSTEM")) return "HIGH";
        if (menuOption.contains("UPDATE") || menuOption.contains("CREATE")) return "MEDIUM";
        return "LOW";
    }
    
    /**
     * Determines operation category for user management.
     */
    private String determineOperationCategory(String operation) {
        switch (operation.toLowerCase()) {
            case "create": return "USER_CREATION";
            case "update": return "USER_MODIFICATION";
            case "delete": return "USER_DELETION";
            case "role_change": return "PRIVILEGE_MODIFICATION";
            default: return "GENERAL_USER_OPERATION";
        }
    }
    
    /**
     * Extracts authentication time from authentication context.
     */
    private String extractAuthenticationTime(Authentication authentication) {
        if (authentication != null && authentication.getDetails() != null) {
            Object details = authentication.getDetails();
            if (details instanceof Map) {
                Map<?, ?> detailsMap = (Map<?, ?>) details;
                return (String) detailsMap.get("authentication_time");
            }
        }
        return LocalDateTime.now().toString();
    }
    
    /**
     * Determines user management risk level.
     */
    private String determineUserMgmtRiskLevel(String operation) {
        switch (operation.toLowerCase()) {
            case "delete": return "HIGH";
            case "role_change": return "HIGH";
            case "create": return "MEDIUM";
            case "update": return "MEDIUM";
            default: return "LOW";
        }
    }
    
    /**
     * Determines regulatory impact of operation.
     */
    private String determineRegulatoryImpact(String operation) {
        switch (operation.toLowerCase()) {
            case "delete": return "HIGH_IMPACT";
            case "role_change": return "HIGH_IMPACT";
            case "create": return "MEDIUM_IMPACT";
            case "update": return "MEDIUM_IMPACT";
            default: return "LOW_IMPACT";
        }
    }
    
    /**
     * Determines security event category.
     */
    private String determineSecurityEventCategory(String eventType) {
        if (eventType.contains("BREACH")) return "SECURITY_INCIDENT";
        if (eventType.contains("VIOLATION")) return "POLICY_VIOLATION";
        if (eventType.contains("ANOMALY")) return "ANOMALY_DETECTION";
        return "GENERAL_SECURITY";
    }
    
    /**
     * Gets current system load (placeholder implementation).
     */
    private String getCurrentSystemLoad() {
        // Placeholder - would integrate with actual system monitoring
        return "NORMAL";
    }
    
    /**
     * Gets active session count (placeholder implementation).
     */
    private int getActiveSessionCount() {
        // Placeholder - would integrate with actual session management
        return 0;
    }
    
    /**
     * Determines if incident response is required.
     */
    private boolean requiresIncidentResponse(String severity) {
        return "HIGH".equals(severity) || "CRITICAL".equals(severity);
    }
    
    /**
     * Determines escalation level based on severity.
     */
    private String determineEscalationLevel(String severity) {
        switch (severity.toLowerCase()) {
            case "critical": return "IMMEDIATE";
            case "high": return "URGENT";
            case "medium": return "NORMAL";
            default: return "LOW";
        }
    }
    
    /**
     * Determines navigation type.
     */
    private String determineNavigationType(String fromPage, String toPage) {
        if (fromPage.contains("LOGIN") && toPage.contains("MENU")) return "INITIAL_LOGIN";
        if (fromPage.contains("MENU") && toPage.contains("ADMIN")) return "PRIVILEGE_ESCALATION";
        return "STANDARD_NAVIGATION";
    }
    
    /**
     * Extracts user role from authentication.
     */
    private String extractUserRole(Authentication authentication) {
        if (authentication != null && authentication.getAuthorities() != null) {
            return authentication.getAuthorities().stream()
                    .findFirst()
                    .map(authority -> authority.getAuthority())
                    .orElse("UNKNOWN_ROLE");
        }
        return "UNKNOWN_ROLE";
    }
    
    /**
     * Analyzes navigation pattern for anomalies.
     */
    private String analyzeNavigationPattern(String fromPage, String toPage) {
        // Placeholder for navigation pattern analysis
        return "NORMAL_PATTERN";
    }
    
    /**
     * Detects suspicious navigation patterns.
     */
    private boolean detectSuspiciousNavigation(String fromPage, String toPage, String username) {
        // Placeholder for suspicious navigation detection
        return false;
    }
    
    /**
     * Determines transaction category.
     */
    private String determineTransactionCategory(String transactionType) {
        switch (transactionType.toLowerCase()) {
            case "payment": return "FINANCIAL_TRANSACTION";
            case "transfer": return "FINANCIAL_TRANSACTION";
            case "balance_inquiry": return "ACCOUNT_INQUIRY";
            default: return "GENERAL_TRANSACTION";
        }
    }
    
    /**
     * Masks sensitive card number information.
     */
    private String maskCardNumber(Object cardNumber) {
        if (cardNumber != null) {
            String cardStr = cardNumber.toString();
            if (cardStr.length() >= 4) {
                return "****-****-****-" + cardStr.substring(cardStr.length() - 4);
            }
        }
        return "****-****-****-****";
    }
    
    /**
     * Calculates transaction risk score.
     */
    private int calculateTransactionRiskScore(String transactionType, Double amount) {
        int baseScore = 0;
        
        if ("PAYMENT".equals(transactionType)) baseScore += 10;
        if ("TRANSFER".equals(transactionType)) baseScore += 20;
        
        if (amount != null) {
            if (amount > 10000) baseScore += 30;
            else if (amount > 1000) baseScore += 10;
        }
        
        return baseScore;
    }
    
    /**
     * Generates compliance flags for transactions.
     */
    private String generateComplianceFlags(String transactionType, Double amount) {
        if (amount != null && amount > 10000) {
            return "AML_REPORTING_REQUIRED";
        }
        return "STANDARD_COMPLIANCE";
    }
    
    /**
     * Determines if regulatory reporting is required.
     */
    private boolean requiresRegulatoryReporting(String transactionType, Double amount) {
        return amount != null && amount > 10000;
    }
}
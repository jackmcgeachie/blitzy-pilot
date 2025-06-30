/*
 * Copyright 2024 CardDemo Application
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.carddemo.audit;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Spring AOP aspect providing automatic audit event capture across all application 
 * service methods. Implements comprehensive pointcut definitions for authentication, 
 * authorization, data access, and administrative operations. Generates audit events 
 * asynchronously with JWT correlation, user context extraction, and method execution 
 * outcome tracking for complete security monitoring and regulatory compliance.
 * 
 * This class replaces manual COBOL audit logging patterns from:
 * - COSGN00C authentication logging
 * - COACTUPC account update auditing  
 * - COTRN01C transaction processing audit trails
 * - COBIL00C payment processing logging
 * - COUSR00C user management audit events
 * 
 * The aspect integrates with Spring Security context to capture user authentication
 * and authorization information, generating SMF-compatible audit records for 
 * enterprise SIEM integration and regulatory compliance reporting.
 * 
 * @since 1.0
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger logger = LoggerFactory.getLogger(AuditAspect.class);
    
    private static final String MDC_AUDIT_ID = "auditId";
    private static final String MDC_USER_ID = "userId";
    private static final String MDC_SESSION_ID = "sessionId";
    private static final String MDC_CORRELATION_ID = "correlationId";
    
    @Autowired
    private AuditService auditService;

    // ================================================================
    // POINTCUT DEFINITIONS - Comprehensive coverage of all security-relevant operations
    // ================================================================

    /**
     * Authentication service pointcuts - captures all authentication-related operations
     * replacing COSGN00C manual audit logging patterns
     */
    @Pointcut("execution(* com.carddemo.auth.AuthenticationService.*(..))")
    public void authenticationServiceMethods() {}

    /**
     * Authorization and user management pointcuts - captures role-based operations
     * replacing COUSR00C user management audit patterns
     */
    @Pointcut("execution(* com.carddemo.user.UserManagementService.*(..)) || " +
              "execution(* com.carddemo.auth.UserDetailsServiceImpl.*(..))")
    public void authorizationServiceMethods() {}

    /**
     * Data access pointcuts - captures all business data operations
     * replacing COACTUPC account update and COTRN01C transaction audit patterns
     */
    @Pointcut("execution(* com.carddemo.account.AccountService.*(..)) || " +
              "execution(* com.carddemo.card.CardService.*(..)) || " +
              "execution(* com.carddemo.customer.CustomerService.*(..)) || " +
              "execution(* com.carddemo.transaction.TransactionService.*(..))")
    public void dataAccessServiceMethods() {}

    /**
     * Administrative operation pointcuts - captures all admin-level operations
     * including system configuration and privilege management
     */
    @Pointcut("execution(* com.carddemo.admin.*.*(..)) || " +
              "execution(* com.carddemo.config.*.*(..)) || " +
              "execution(* com.carddemo.management.*.*(..))")
    public void administrativeServiceMethods() {}

    /**
     * Payment processing pointcuts - captures financial transaction operations
     * replacing COBIL00C payment processing audit patterns
     */
    @Pointcut("execution(* com.carddemo.payment.PaymentService.*(..)) || " +
              "execution(* com.carddemo.billing.BillingService.*(..))")
    public void paymentProcessingMethods() {}

    /**
     * Batch processing pointcuts - captures batch job execution audit events
     * replacing JCL job stream audit logging
     */
    @Pointcut("execution(* com.carddemo.batch.*.*(..)) || " +
              "@annotation(org.springframework.batch.core.configuration.annotation.StepScope)")
    public void batchProcessingMethods() {}

    /**
     * Repository/DAO layer pointcuts - captures direct data access operations
     * for comprehensive data access audit trails
     */
    @Pointcut("execution(* com.carddemo.repository.*.*(..)) || " +
              "execution(* org.springframework.data.jpa.repository.JpaRepository+.*(..))")
    public void repositoryMethods() {}

    /**
     * Security-sensitive method pointcuts - methods requiring special audit attention
     * based on Spring Security annotations
     */
    @Pointcut("@annotation(org.springframework.security.access.prepost.PreAuthorize) || " +
              "@annotation(org.springframework.security.access.annotation.Secured)")
    public void securityAnnotatedMethods() {}

    // ================================================================
    // AUDIT ADVICE IMPLEMENTATIONS - Around advice for comprehensive event capture
    // ================================================================

    /**
     * Authentication audit advice - captures all authentication-related operations
     * including login attempts, token generation, and session management.
     * Replaces COSGN00C authentication audit logging.
     */
    @Around("authenticationServiceMethods()")
    public Object auditAuthenticationOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithAudit(joinPoint, AuditEventType.AUTHENTICATION, "AUTHENTICATION");
    }

    /**
     * Authorization audit advice - captures role-based access control operations
     * and user management activities. Replaces COUSR00C user management audit patterns.
     */
    @Around("authorizationServiceMethods()")
    public Object auditAuthorizationOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithAudit(joinPoint, AuditEventType.AUTHORIZATION, "AUTHORIZATION");
    }

    /**
     * Data access audit advice - captures all business data operations including
     * account management, card operations, and transaction processing.
     * Replaces COACTUPC and COTRN01C audit logging patterns.
     */
    @Around("dataAccessServiceMethods()")
    public Object auditDataAccessOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithAudit(joinPoint, AuditEventType.DATA_ACCESS, "DATA_ACCESS");
    }

    /**
     * Administrative audit advice - captures all administrative operations
     * including system configuration and privilege management.
     */
    @Around("administrativeServiceMethods()")
    public Object auditAdministrativeOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithAudit(joinPoint, AuditEventType.ADMINISTRATIVE, "ADMINISTRATIVE");
    }

    /**
     * Payment processing audit advice - captures financial transaction operations.
     * Replaces COBIL00C payment processing audit patterns.
     */
    @Around("paymentProcessingMethods()")
    public Object auditPaymentOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithAudit(joinPoint, AuditEventType.PAYMENT_PROCESSING, "PAYMENT");
    }

    /**
     * Batch processing audit advice - captures batch job execution events.
     * Replaces JCL job stream audit logging.
     */
    @Around("batchProcessingMethods()")
    public Object auditBatchOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithAudit(joinPoint, AuditEventType.BATCH_PROCESSING, "BATCH");
    }

    /**
     * Repository audit advice - captures direct data access operations
     * for comprehensive data access audit trails.
     */
    @Around("repositoryMethods()")
    public Object auditRepositoryOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithAudit(joinPoint, AuditEventType.DATA_ACCESS, "REPOSITORY");
    }

    /**
     * Security-annotated method audit advice - captures operations with explicit
     * security requirements for enhanced audit coverage.
     */
    @Around("securityAnnotatedMethods()")
    public Object auditSecurityAnnotatedOperations(ProceedingJoinPoint joinPoint) throws Throwable {
        return executeWithAudit(joinPoint, AuditEventType.SECURITY_OPERATION, "SECURITY");
    }

    // ================================================================
    // CORE AUDIT EXECUTION LOGIC - Centralized audit event processing
    // ================================================================

    /**
     * Core audit execution method that captures method execution context,
     * processes security information, and generates comprehensive audit events.
     * 
     * @param joinPoint The intercepted method execution point
     * @param eventType The type of audit event to generate
     * @param category The audit category for classification
     * @return The original method execution result
     * @throws Throwable If the original method throws an exception
     */
    private Object executeWithAudit(ProceedingJoinPoint joinPoint, AuditEventType eventType, String category) throws Throwable {
        String auditId = UUID.randomUUID().toString();
        String methodName = getMethodName(joinPoint);
        Object[] methodArgs = joinPoint.getArgs();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // Set up MDC for audit correlation
        MDC.put(MDC_AUDIT_ID, auditId);
        
        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable exception = null;
        String outcome = "SUCCESS";
        
        try {
            logger.debug("Starting audit capture for method: {} with audit ID: {}", methodName, auditId);
            
            // Execute the original method
            result = joinPoint.proceed();
            
            logger.debug("Method {} completed successfully with audit ID: {}", methodName, auditId);
            
        } catch (Throwable ex) {
            exception = ex;
            outcome = "FAILURE";
            logger.warn("Method {} failed with exception: {} (audit ID: {})", methodName, ex.getMessage(), auditId);
            throw ex; // Re-throw to maintain original behavior
            
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            
            try {
                // Generate audit event asynchronously to prevent transaction performance impact
                generateAuditEventAsync(auditId, joinPoint, eventType, category, authentication, 
                                       methodArgs, result, exception, outcome, executionTime);
                
            } catch (Exception auditEx) {
                // Audit failures should not impact business operations
                logger.error("Failed to generate audit event for method {} (audit ID: {}): {}", 
                           methodName, auditId, auditEx.getMessage(), auditEx);
            } finally {
                // Clean up MDC
                MDC.remove(MDC_AUDIT_ID);
                MDC.remove(MDC_USER_ID);
                MDC.remove(MDC_SESSION_ID);
                MDC.remove(MDC_CORRELATION_ID);
            }
        }
        
        return result;
    }

    /**
     * Generates audit events asynchronously to prevent transaction performance impact
     * while ensuring comprehensive security event coverage.
     */
    @Async
    public CompletableFuture<Void> generateAuditEventAsync(String auditId, ProceedingJoinPoint joinPoint,
            AuditEventType eventType, String category, Authentication authentication,
            Object[] methodArgs, Object result, Throwable exception, String outcome, long executionTime) {
        
        try {
            AuditEvent auditEvent = createAuditEvent(auditId, joinPoint, eventType, category, 
                                                   authentication, methodArgs, result, exception, 
                                                   outcome, executionTime);
            
            // Publish audit event through AuditService
            auditService.publishAuditEvent(auditEvent);
            
            logger.debug("Audit event published successfully for audit ID: {}", auditId);
            
        } catch (Exception ex) {
            logger.error("Failed to publish audit event for audit ID {}: {}", auditId, ex.getMessage(), ex);
        }
        
        return CompletableFuture.completedFuture(null);
    }

    // ================================================================
    // AUDIT EVENT CREATION - Comprehensive audit record construction
    // ================================================================

    /**
     * Creates a comprehensive audit event record with all security-relevant information
     * including JWT correlation, user context, and execution details.
     */
    private AuditEvent createAuditEvent(String auditId, ProceedingJoinPoint joinPoint, 
                                      AuditEventType eventType, String category,
                                      Authentication authentication, Object[] methodArgs, 
                                      Object result, Throwable exception, String outcome, long executionTime) {
        
        AuditEvent event = new AuditEvent();
        
        // Basic audit event information
        event.setAuditId(auditId);
        event.setEventType(eventType);
        event.setTimestamp(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        event.setOutcome(outcome);
        event.setExecutionTimeMs(executionTime);
        
        // Method execution context
        event.setMethodName(getMethodName(joinPoint));
        event.setClassName(joinPoint.getTarget().getClass().getSimpleName());
        event.setCategory(category);
        
        // User authentication context from Spring Security
        if (authentication != null && authentication.isAuthenticated()) {
            populateUserContext(event, authentication);
        } else {
            event.setUserId("ANONYMOUS");
            event.setUsername("anonymous");
            event.setUserRole("UNAUTHENTICATED");
        }
        
        // JWT token correlation for distributed session tracking
        extractJwtCorrelation(event, authentication);
        
        // Business context information
        populateBusinessContext(event, joinPoint, methodArgs, result);
        
        // Exception information if method failed
        if (exception != null) {
            Map<String, Object> errorContext = new HashMap<>();
            errorContext.put("exceptionType", exception.getClass().getSimpleName());
            errorContext.put("exceptionMessage", exception.getMessage());
            if (exception.getCause() != null) {
                errorContext.put("rootCause", exception.getCause().getMessage());
            }
            event.setErrorContext(errorContext);
        }
        
        // Risk level assessment based on operation type
        event.setRiskLevel(determineRiskLevel(eventType, outcome, authentication));
        
        // Compliance flags for regulatory reporting
        event.setComplianceFlags(determineComplianceFlags(eventType, category));
        
        return event;
    }

    /**
     * Populates user context information from Spring Security authentication.
     * Captures user identity, roles, and authorization information.
     */
    private void populateUserContext(AuditEvent event, Authentication authentication) {
        String userId = extractUserId(authentication);
        String username = authentication.getName();
        
        event.setUserId(userId);
        event.setUsername(username);
        
        // Extract user roles from Spring Security authorities
        String userRole = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(role -> role.startsWith("ROLE_"))
            .findFirst()
            .orElse("ROLE_USER");
        event.setUserRole(userRole);
        
        // Set MDC for logging correlation
        MDC.put(MDC_USER_ID, userId);
        
        // Additional authentication details
        Map<String, Object> authContext = new HashMap<>();
        authContext.put("authenticationType", authentication.getClass().getSimpleName());
        authContext.put("authoritiesCount", authentication.getAuthorities().size());
        authContext.put("isAuthenticated", authentication.isAuthenticated());
        event.setAuthenticationContext(authContext);
    }

    /**
     * Extracts JWT token correlation information for distributed session tracking.
     * Maintains audit trail integrity across stateless REST interactions.
     */
    private void extractJwtCorrelation(AuditEvent event, Authentication authentication) {
        if (authentication != null && authentication.getCredentials() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getCredentials();
            
            String jwtId = jwt.getClaimAsString("jti");
            String sessionId = jwt.getClaimAsString("sessionId");
            String correlationId = jwt.getClaimAsString("correlationId");
            
            event.setJwtTokenId(jwtId);
            event.setSessionId(sessionId != null ? sessionId : "NO_SESSION");
            event.setCorrelationId(correlationId != null ? correlationId : UUID.randomUUID().toString());
            
            // Set MDC for logging correlation
            MDC.put(MDC_SESSION_ID, event.getSessionId());
            MDC.put(MDC_CORRELATION_ID, event.getCorrelationId());
            
            // JWT token context
            Map<String, Object> tokenContext = new HashMap<>();
            tokenContext.put("issuer", jwt.getIssuer() != null ? jwt.getIssuer().toString() : null);
            tokenContext.put("issuedAt", jwt.getIssuedAt() != null ? jwt.getIssuedAt().toString() : null);
            tokenContext.put("expiresAt", jwt.getExpiresAt() != null ? jwt.getExpiresAt().toString() : null);
            event.setTokenContext(tokenContext);
        } else {
            event.setSessionId("NO_SESSION");
            event.setCorrelationId(UUID.randomUUID().toString());
            MDC.put(MDC_CORRELATION_ID, event.getCorrelationId());
        }
    }

    /**
     * Populates business context information based on method execution details.
     * Captures relevant business data while protecting sensitive information.
     */
    private void populateBusinessContext(AuditEvent event, ProceedingJoinPoint joinPoint, 
                                       Object[] methodArgs, Object result) {
        
        Map<String, Object> businessContext = new HashMap<>();
        
        // Method parameter information (with sensitive data protection)
        if (methodArgs != null && methodArgs.length > 0) {
            businessContext.put("parameterCount", methodArgs.length);
            
            // Capture parameter types for audit analysis
            String[] paramTypes = Arrays.stream(methodArgs)
                .map(arg -> arg != null ? arg.getClass().getSimpleName() : "null")
                .toArray(String[]::new);
            businessContext.put("parameterTypes", paramTypes);
            
            // Extract business identifiers safely
            extractBusinessIdentifiers(businessContext, methodArgs);
        }
        
        // Result information (summary only, no sensitive data)
        if (result != null) {
            businessContext.put("resultType", result.getClass().getSimpleName());
            businessContext.put("resultPresent", true);
        } else {
            businessContext.put("resultPresent", false);
        }
        
        // Method signature information
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        businessContext.put("returnType", signature.getReturnType().getSimpleName());
        businessContext.put("methodModifiers", signature.getModifiers());
        
        event.setBusinessContext(businessContext);
    }

    /**
     * Extracts business identifiers from method arguments while protecting sensitive data.
     * Captures account IDs, transaction IDs, and other business keys for audit correlation.
     */
    private void extractBusinessIdentifiers(Map<String, Object> businessContext, Object[] methodArgs) {
        for (int i = 0; i < methodArgs.length; i++) {
            Object arg = methodArgs[i];
            if (arg == null) continue;
            
            String argType = arg.getClass().getSimpleName();
            
            // Extract common business identifiers
            if (arg instanceof String) {
                String stringArg = (String) arg;
                if (stringArg.length() <= 50) { // Avoid capturing large text fields
                    if (isBusinessIdentifier(stringArg)) {
                        businessContext.put("businessId_" + i, maskSensitiveData(stringArg));
                    }
                }
            } else if (arg instanceof Number) {
                businessContext.put("numericParam_" + i, arg);
            }
            
            businessContext.put("argType_" + i, argType);
        }
    }

    /**
     * Determines if a string value appears to be a business identifier
     * (account ID, transaction ID, etc.) based on common patterns.
     */
    private boolean isBusinessIdentifier(String value) {
        // Check for common business ID patterns
        return value.matches("^[A-Z0-9]{3,20}$") || // Alphanumeric IDs
               value.matches("^\\d{8,16}$") ||       // Numeric IDs (account numbers, etc.)
               value.matches("^[A-Z]{2,5}-\\d+$");   // Prefixed IDs
    }

    /**
     * Masks sensitive data in business identifiers for audit logging.
     * Preserves enough information for audit correlation while protecting privacy.
     */
    private String maskSensitiveData(String value) {
        if (value == null || value.length() <= 4) {
            return "****"; // Completely mask short values
        }
        
        // Show first 2 and last 2 characters, mask the middle
        String prefix = value.substring(0, 2);
        String suffix = value.substring(value.length() - 2);
        String masked = "*".repeat(Math.max(4, value.length() - 4));
        
        return prefix + masked + suffix;
    }

    /**
     * Determines risk level based on operation type, outcome, and user context.
     * Provides risk assessment for security monitoring and alert generation.
     */
    private String determineRiskLevel(AuditEventType eventType, String outcome, Authentication authentication) {
        // Failed operations are always higher risk
        if ("FAILURE".equals(outcome)) {
            return isHighPrivilegeUser(authentication) ? "CRITICAL" : "HIGH";
        }
        
        // Risk assessment based on operation type
        switch (eventType) {
            case AUTHENTICATION:
                return isHighPrivilegeUser(authentication) ? "MEDIUM" : "LOW";
            case AUTHORIZATION:
            case ADMINISTRATIVE:
                return "HIGH";
            case PAYMENT_PROCESSING:
                return "MEDIUM";
            case DATA_ACCESS:
            case SECURITY_OPERATION:
                return isHighPrivilegeUser(authentication) ? "MEDIUM" : "LOW";
            default:
                return "LOW";
        }
    }

    /**
     * Determines compliance flags for regulatory reporting based on operation characteristics.
     */
    private String[] determineComplianceFlags(AuditEventType eventType, String category) {
        switch (eventType) {
            case AUTHENTICATION:
            case AUTHORIZATION:
                return new String[]{"SOX_COMPLIANCE", "SECURITY_MONITORING"};
            case PAYMENT_PROCESSING:
                return new String[]{"PCI_DSS", "FINANCIAL_REGULATION", "SOX_COMPLIANCE"};
            case ADMINISTRATIVE:
                return new String[]{"SOX_COMPLIANCE", "PRIVILEGE_MANAGEMENT", "SECURITY_MONITORING"};
            case DATA_ACCESS:
                return new String[]{"DATA_PROTECTION", "PRIVACY_COMPLIANCE"};
            default:
                return new String[]{"GENERAL_AUDIT"};
        }
    }

    // ================================================================
    // UTILITY METHODS - Helper functions for audit processing
    // ================================================================

    /**
     * Extracts method name from join point for audit logging.
     */
    private String getMethodName(ProceedingJoinPoint joinPoint) {
        return joinPoint.getSignature().getDeclaringType().getSimpleName() + "." + 
               joinPoint.getSignature().getName();
    }

    /**
     * Extracts user ID from Spring Security authentication context.
     * Handles various authentication types including JWT tokens.
     */
    private String extractUserId(Authentication authentication) {
        if (authentication == null) {
            return "ANONYMOUS";
        }
        
        // Try to extract from JWT token first
        if (authentication.getCredentials() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getCredentials();
            String sub = jwt.getClaimAsString("sub");
            if (sub != null) {
                return sub;
            }
        }
        
        // Fall back to authentication name
        return authentication.getName() != null ? authentication.getName() : "UNKNOWN";
    }

    /**
     * Determines if the authenticated user has high-privilege access
     * based on Spring Security authorities.
     */
    private boolean isHighPrivilegeUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(role -> role.equals("ROLE_ADMIN") || role.equals("ROLE_SUPERVISOR") || 
                             role.contains("ADMIN") || role.contains("MANAGER"));
    }
}
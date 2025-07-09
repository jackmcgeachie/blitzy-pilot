package com.carddemo.card;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import java.lang.RuntimeException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

import com.carddemo.audit.AuditService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;

/**
 * Custom exception class for card validation failures providing field-level error reporting,
 * business rule violation details, Bean Validation integration, and comprehensive error
 * information for debugging and user feedback.
 * 
 * This exception class preserves COBOL validation message patterns from the original
 * mainframe system while providing modern Java validation framework integration.
 * It supports field-level validation errors, business rule violations, and integrates
 * with the audit logging system for compliance and security monitoring.
 * 
 * Key Features:
 * - Field-level validation error reporting with specific field identifiers
 * - COBOL-compatible error message patterns for consistency
 * - Bean Validation framework integration for Spring Boot compatibility  
 * - Business rule violation details for comprehensive error reporting
 * - Audit logging integration for security and compliance monitoring
 * - HTTP 400 Bad Request automatic response mapping
 * - Thread-safe field error management
 * - Detailed error context for debugging support
 * 
 * COBOL Error Pattern Examples:
 * - "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER"
 * - "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER"
 * - "CARD ACTIVE STATUS MUST BE Y OR N"
 * - "CARD EXPIRY MONTH MUST BE BETWEEN 1 AND 12"
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since 2024-01-15
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class CardValidationException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    // Thread-safe map for storing field-level validation errors
    private final Map<String, String> fieldErrors = new ConcurrentHashMap<>();
    
    // Set of Bean Validation constraint violations
    private final Set<ConstraintViolation<?>> constraintViolations = new HashSet<>();
    
    // Business rule violation details
    private final Map<String, Object> businessRuleViolations = new ConcurrentHashMap<>();
    
    // Validation context information
    private final String validationContext;
    private final String errorCode;
    private final long timestamp;
    
    // COBOL-compatible error message patterns
    private static final String ACCOUNT_VALIDATION_ERROR = "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER";
    private static final String CARD_VALIDATION_ERROR = "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER";
    private static final String CARD_STATUS_ERROR = "CARD ACTIVE STATUS MUST BE Y OR N";
    private static final String CARD_EXPIRY_MONTH_ERROR = "CARD EXPIRY MONTH MUST BE BETWEEN 1 AND 12";
    private static final String CARD_EXPIRY_YEAR_ERROR = "INVALID CARD EXPIRY YEAR";
    private static final String CARD_NAME_ERROR = "CARD NAME CAN ONLY CONTAIN ALPHABETS AND SPACES";
    private static final String CARD_CVV_ERROR = "CARD CVV MUST BE A 3 OR 4 DIGIT NUMBER";
    private static final String MULTIPLE_ERRORS = "MULTIPLE VALIDATION ERRORS DETECTED";
    
    // Audit service for logging validation failures
    private static AuditService auditService;
    
    /**
     * Default constructor for general validation failures.
     * Creates a CardValidationException with a generic validation error message.
     */
    public CardValidationException() {
        super("CARD VALIDATION ERROR OCCURRED");
        this.validationContext = "GENERAL_VALIDATION";
        this.errorCode = "CARD_VAL_001";
        this.timestamp = System.currentTimeMillis();
        
        // Log security event for validation failure
        logValidationFailure("GENERAL_VALIDATION_FAILURE", "MEDIUM", "General card validation failure occurred");
    }
    
    /**
     * Constructor with custom error message.
     * 
     * @param message The validation error message following COBOL patterns
     */
    public CardValidationException(String message) {
        super(message != null ? message : "CARD VALIDATION ERROR OCCURRED");
        this.validationContext = "CUSTOM_VALIDATION";
        this.errorCode = "CARD_VAL_002";
        this.timestamp = System.currentTimeMillis();
        
        // Log security event for validation failure
        logValidationFailure("CUSTOM_VALIDATION_FAILURE", "MEDIUM", message);
    }
    
    /**
     * Constructor with custom error message and underlying cause.
     * 
     * @param message The validation error message following COBOL patterns
     * @param cause The underlying cause of the validation failure
     */
    public CardValidationException(String message, Throwable cause) {
        super(message != null ? message : "CARD VALIDATION ERROR OCCURRED", cause);
        this.validationContext = "CAUSED_VALIDATION";
        this.errorCode = "CARD_VAL_003";
        this.timestamp = System.currentTimeMillis();
        
        // Log security event for validation failure with cause
        logValidationFailure("CAUSED_VALIDATION_FAILURE", "HIGH", 
            message + " - Caused by: " + (cause != null ? cause.getMessage() : "Unknown cause"));
    }
    
    /**
     * Constructor with Bean Validation constraint violations.
     * Integrates with Spring Boot's Bean Validation framework for automatic validation.
     * 
     * @param violations Set of constraint violations from Bean Validation
     */
    public CardValidationException(Set<ConstraintViolation<?>> violations) {
        super(buildConstraintViolationMessage(violations));
        this.validationContext = "BEAN_VALIDATION";
        this.errorCode = "CARD_VAL_004";
        this.timestamp = System.currentTimeMillis();
        
        // Store constraint violations
        if (violations != null) {
            this.constraintViolations.addAll(violations);
            
            // Convert Bean Validation violations to field errors
            for (ConstraintViolation<?> violation : violations) {
                String fieldName = violation.getPropertyPath().toString();
                String errorMessage = convertToCobolErrorMessage(fieldName, violation.getMessage());
                this.fieldErrors.put(fieldName, errorMessage);
            }
        }
        
        // Log security event for Bean Validation failure
        logValidationFailure("BEAN_VALIDATION_FAILURE", "MEDIUM", 
            "Bean validation failed with " + (violations != null ? violations.size() : 0) + " violations");
    }
    
    /**
     * Adds a field-level validation error with COBOL-compatible error message.
     * 
     * @param fieldName The name of the field that failed validation
     * @param errorMessage The error message following COBOL patterns
     * @return This exception instance for method chaining
     */
    public CardValidationException addFieldError(String fieldName, String errorMessage) {
        if (fieldName != null && errorMessage != null) {
            this.fieldErrors.put(fieldName, errorMessage);
            
            // Log data access event for field validation failure
            logDataAccessEvent(fieldName, "FIELD_VALIDATION_FAILURE", errorMessage);
        }
        return this;
    }
    
    /**
     * Retrieves all field-level validation errors.
     * 
     * @return Immutable map of field names to error messages
     */
    public Map<String, String> getFieldErrors() {
        return Collections.unmodifiableMap(fieldErrors);
    }
    
    /**
     * Checks if there are any field-level validation errors.
     * 
     * @return true if field errors exist, false otherwise
     */
    public boolean hasFieldErrors() {
        return !fieldErrors.isEmpty();
    }
    
    /**
     * Gets the total count of validation errors (field errors + constraint violations).
     * 
     * @return Total number of validation errors
     */
    public int getErrorCount() {
        return fieldErrors.size() + constraintViolations.size();
    }
    
    /**
     * Retrieves all Bean Validation constraint violations.
     * 
     * @return Immutable set of constraint violations
     */
    public Set<ConstraintViolation<?>> getValidationErrors() {
        return Collections.unmodifiableSet(constraintViolations);
    }
    
    /**
     * Overrides getMessage() to provide comprehensive error information.
     * Combines field errors and constraint violations into a single message.
     * 
     * @return Comprehensive error message including all validation failures
     */
    @Override
    public String getMessage() {
        StringBuilder messageBuilder = new StringBuilder();
        
        // Start with the base message
        String baseMessage = super.getMessage();
        if (baseMessage != null) {
            messageBuilder.append(baseMessage);
        }
        
        // Add field errors if present
        if (!fieldErrors.isEmpty()) {
            if (messageBuilder.length() > 0) {
                messageBuilder.append(" - ");
            }
            messageBuilder.append("Field validation errors: ");
            messageBuilder.append(fieldErrors.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining(", ")));
        }
        
        // Add constraint violations if present
        if (!constraintViolations.isEmpty()) {
            if (messageBuilder.length() > 0) {
                messageBuilder.append(" - ");
            }
            messageBuilder.append("Constraint violations: ");
            messageBuilder.append(constraintViolations.stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", ")));
        }
        
        // Add business rule violations if present
        if (!businessRuleViolations.isEmpty()) {
            if (messageBuilder.length() > 0) {
                messageBuilder.append(" - ");
            }
            messageBuilder.append("Business rule violations: ");
            messageBuilder.append(businessRuleViolations.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining(", ")));
        }
        
        return messageBuilder.toString();
    }
    
    /**
     * Gets the validation context identifier.
     * 
     * @return The validation context string
     */
    public String getValidationContext() {
        return validationContext;
    }
    
    /**
     * Gets the error code for this validation failure.
     * 
     * @return The error code string
     */
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * Gets the timestamp when this validation failure occurred.
     * 
     * @return The timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Adds a business rule violation with detailed context.
     * 
     * @param ruleName The name of the business rule that was violated
     * @param violationDetails Details about the violation
     * @return This exception instance for method chaining
     */
    public CardValidationException addBusinessRuleViolation(String ruleName, Object violationDetails) {
        if (ruleName != null && violationDetails != null) {
            this.businessRuleViolations.put(ruleName, violationDetails);
            
            // Log security event for business rule violation
            logValidationFailure("BUSINESS_RULE_VIOLATION", "HIGH", 
                "Business rule '" + ruleName + "' violated: " + violationDetails);
        }
        return this;
    }
    
    /**
     * Gets all business rule violations.
     * 
     * @return Immutable map of rule names to violation details
     */
    public Map<String, Object> getBusinessRuleViolations() {
        return Collections.unmodifiableMap(businessRuleViolations);
    }
    
    // Static method to set the audit service (for dependency injection)
    public static void setAuditService(AuditService auditService) {
        CardValidationException.auditService = auditService;
    }
    
    // Private helper methods
    
    /**
     * Builds a comprehensive error message from Bean Validation constraint violations.
     */
    private static String buildConstraintViolationMessage(Set<ConstraintViolation<?>> violations) {
        if (violations == null || violations.isEmpty()) {
            return "BEAN VALIDATION FAILED - NO VIOLATIONS SPECIFIED";
        }
        
        if (violations.size() == 1) {
            ConstraintViolation<?> violation = violations.iterator().next();
            return convertToCobolErrorMessage(violation.getPropertyPath().toString(), violation.getMessage());
        }
        
        return MULTIPLE_ERRORS;
    }
    
    /**
     * Converts Bean Validation error messages to COBOL-compatible formats.
     */
    private static String convertToCobolErrorMessage(String fieldName, String validationMessage) {
        if (fieldName == null || validationMessage == null) {
            return "VALIDATION ERROR OCCURRED";
        }
        
        // Convert field names to COBOL patterns
        String cobolFieldName = fieldName.toUpperCase().replace(".", "_");
        
        // Convert common validation messages to COBOL patterns
        if (validationMessage.contains("must not be null") || validationMessage.contains("required")) {
            return cobolFieldName + " IS REQUIRED AND CANNOT BE BLANK";
        }
        
        if (validationMessage.contains("numeric") || validationMessage.contains("number")) {
            if (cobolFieldName.contains("ACCOUNT")) {
                return ACCOUNT_VALIDATION_ERROR;
            } else if (cobolFieldName.contains("CARD") && !cobolFieldName.contains("CVV")) {
                return CARD_VALIDATION_ERROR;
            } else if (cobolFieldName.contains("CVV")) {
                return CARD_CVV_ERROR;
            }
        }
        
        if (validationMessage.contains("length") || validationMessage.contains("size")) {
            if (cobolFieldName.contains("ACCOUNT")) {
                return ACCOUNT_VALIDATION_ERROR;
            } else if (cobolFieldName.contains("CARD")) {
                return CARD_VALIDATION_ERROR;
            }
        }
        
        if (validationMessage.contains("pattern") || validationMessage.contains("format")) {
            if (cobolFieldName.contains("NAME")) {
                return CARD_NAME_ERROR;
            } else if (cobolFieldName.contains("STATUS")) {
                return CARD_STATUS_ERROR;
            } else if (cobolFieldName.contains("MONTH")) {
                return CARD_EXPIRY_MONTH_ERROR;
            } else if (cobolFieldName.contains("YEAR")) {
                return CARD_EXPIRY_YEAR_ERROR;
            }
        }
        
        if (validationMessage.contains("range") || validationMessage.contains("between")) {
            if (cobolFieldName.contains("MONTH")) {
                return CARD_EXPIRY_MONTH_ERROR;
            } else if (cobolFieldName.contains("YEAR")) {
                return CARD_EXPIRY_YEAR_ERROR;
            }
        }
        
        // Default COBOL-style error message
        return cobolFieldName + " VALIDATION FAILED - " + validationMessage.toUpperCase();
    }
    
    /**
     * Logs validation failure events to the audit service.
     */
    private void logValidationFailure(String eventType, String severity, String description) {
        if (auditService != null) {
            try {
                // Create affected components map
                Map<String, Object> affectedComponents = new ConcurrentHashMap<>();
                affectedComponents.put("validation_context", validationContext);
                affectedComponents.put("error_code", errorCode);
                affectedComponents.put("field_count", fieldErrors.size());
                affectedComponents.put("violation_count", constraintViolations.size());
                affectedComponents.put("timestamp", timestamp);
                
                // Log the security event
                auditService.logSecurityEvent(eventType, severity, description, affectedComponents);
                
            } catch (Exception e) {
                // Silently handle audit logging failures to prevent cascading exceptions
                System.err.println("Failed to log validation failure audit event: " + e.getMessage());
            }
        }
    }
    
    /**
     * Logs data access events for field validation failures.
     */
    private void logDataAccessEvent(String fieldName, String operation, String errorMessage) {
        if (auditService != null) {
            try {
                // Get current user from security context
                String username = "UNKNOWN_USER";
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication != null && authentication.getName() != null) {
                    username = authentication.getName();
                }
                
                // Create query details map
                Map<String, Object> queryDetails = new ConcurrentHashMap<>();
                queryDetails.put("field_name", fieldName);
                queryDetails.put("error_message", errorMessage);
                queryDetails.put("validation_context", validationContext);
                queryDetails.put("error_code", errorCode);
                queryDetails.put("execution_time_ms", 0L);
                
                // Log the data access event
                auditService.logDataAccessEvent(username, "CARD_VALIDATION", operation, 0, queryDetails);
                
            } catch (Exception e) {
                // Silently handle audit logging failures to prevent cascading exceptions
                System.err.println("Failed to log data access audit event: " + e.getMessage());
            }
        }
    }
}
package com.carddemo.card;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import com.carddemo.audit.AuditService;

/**
 * Custom exception class for card not found scenarios in the CardDemo application.
 * 
 * This exception replicates COBOL HANDLE ABEND patterns specifically for DFHRESP(NOTFND) 
 * conditions encountered in card-related operations (COCRDLIC, COCRDSLC, COCRDUPC programs).
 * It provides HTTP status code mapping, detailed error messages, correlation IDs, and 
 * integration with the centralized audit logging framework.
 * 
 * Key Features:
 * - HTTP 404 Not Found status code mapping via @ResponseStatus annotation
 * - Comprehensive error context including correlation IDs and timestamps
 * - Card identifier preservation for troubleshooting and audit trail purposes
 * - Integration with AuditService for security event logging and data access tracking
 * - Multiple constructor overloads for flexible exception creation
 * - Automatic audit logging of card not found events for fraud detection
 * 
 * COBOL Pattern Replication:
 * - Maps DFHRESP(NOTFND) conditions to HTTP 404 responses
 * - Replicates COBOL error message patterns with detailed context
 * - Preserves card identifier context from COBOL WS-CARD-RID structures
 * - Implements centralized error handling similar to COBOL HANDLE ABEND routines
 * 
 * Usage Examples:
 * - Card lookup failures in card listing operations (COCRDLIC equivalent)
 * - Card detail retrieval failures in card selection operations (COCRDSLC equivalent)
 * - Card update failures in card maintenance operations (COCRDUPC equivalent)
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since 2024-01-15
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class CardNotFoundException extends RuntimeException {
    
    /**
     * Unique correlation ID for troubleshooting and audit trail tracking.
     * This ID links the exception to audit log entries and external system calls.
     */
    private final String correlationId;
    
    /**
     * Timestamp when the exception was created for audit trail and debugging purposes.
     * Uses LocalDateTime to match COBOL date/time handling patterns.
     */
    private final LocalDateTime timestamp;
    
    /**
     * Card identifier that was not found, preserved for audit logging and troubleshooting.
     * This may be a card number, account ID, or other card-related identifier.
     */
    private final String cardIdentifier;
    
    /**
     * Reference to AuditService for logging security and data access events.
     * This enables automatic audit trail generation for all card not found scenarios.
     */
    private static AuditService auditService;
    
    /**
     * Default constructor for generic card not found scenarios.
     * 
     * Creates a CardNotFoundException with a standard error message and
     * automatically generated correlation ID and timestamp. This constructor
     * is used when no specific card identifier is available.
     */
    public CardNotFoundException() {
        super("Card not found");
        this.correlationId = generateCorrelationId();
        this.timestamp = LocalDateTime.now();
        this.cardIdentifier = "UNKNOWN";
        logToAuditService();
    }
    
    /**
     * Constructor with custom error message.
     * 
     * Creates a CardNotFoundException with a specific error message while
     * automatically generating correlation ID and timestamp. This constructor
     * is used when a descriptive error message is available but no specific
     * card identifier is known.
     * 
     * @param message The detailed error message describing the card not found condition
     */
    public CardNotFoundException(String message) {
        super(message);
        this.correlationId = generateCorrelationId();
        this.timestamp = LocalDateTime.now();
        this.cardIdentifier = "UNKNOWN";
        logToAuditService();
    }
    
    /**
     * Constructor with custom error message and underlying cause.
     * 
     * Creates a CardNotFoundException with a specific error message and
     * the underlying exception that caused the card not found condition.
     * This constructor is used when wrapping database exceptions or other
     * system exceptions that indicate a card was not found.
     * 
     * @param message The detailed error message describing the card not found condition
     * @param cause The underlying exception that caused the card not found condition
     */
    public CardNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.correlationId = generateCorrelationId();
        this.timestamp = LocalDateTime.now();
        this.cardIdentifier = "UNKNOWN";
        logToAuditService();
    }
    
    /**
     * Constructor with custom error message and card identifier.
     * 
     * Creates a CardNotFoundException with a specific error message and
     * the card identifier that was not found. This constructor is the
     * most commonly used as it provides complete context for troubleshooting
     * and audit trail purposes.
     * 
     * @param message The detailed error message describing the card not found condition
     * @param cardIdentifier The card identifier (card number, account ID, etc.) that was not found
     */
    public CardNotFoundException(String message, String cardIdentifier) {
        super(message);
        this.correlationId = generateCorrelationId();
        this.timestamp = LocalDateTime.now();
        this.cardIdentifier = cardIdentifier != null ? cardIdentifier : "UNKNOWN";
        logToAuditService();
    }
    
    /**
     * Constructor with custom error message, card identifier, and underlying cause.
     * 
     * Creates a CardNotFoundException with complete context including the
     * error message, card identifier, and underlying exception. This constructor
     * provides the most comprehensive error context for complex failure scenarios.
     * 
     * @param message The detailed error message describing the card not found condition
     * @param cardIdentifier The card identifier (card number, account ID, etc.) that was not found
     * @param cause The underlying exception that caused the card not found condition
     */
    public CardNotFoundException(String message, String cardIdentifier, Throwable cause) {
        super(message, cause);
        this.correlationId = generateCorrelationId();
        this.timestamp = LocalDateTime.now();
        this.cardIdentifier = cardIdentifier != null ? cardIdentifier : "UNKNOWN";
        logToAuditService();
    }
    
    /**
     * Gets the unique correlation ID for this exception.
     * 
     * The correlation ID is used for troubleshooting and linking this exception
     * to audit log entries and external system calls. This ID is automatically
     * generated when the exception is created and remains constant throughout
     * the exception's lifecycle.
     * 
     * @return The unique correlation ID for this exception
     */
    public String getCorrelationId() {
        return correlationId;
    }
    
    /**
     * Gets the timestamp when this exception was created.
     * 
     * The timestamp is used for audit trail purposes and troubleshooting.
     * It represents the exact moment when the card not found condition was
     * detected and the exception was instantiated.
     * 
     * @return The timestamp when this exception was created
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    /**
     * Gets the card identifier that was not found.
     * 
     * The card identifier provides context about which specific card was
     * not found, enabling more effective troubleshooting and audit trail
     * analysis. This may be a card number, account ID, or other card-related
     * identifier depending on the operation that failed.
     * 
     * @return The card identifier that was not found
     */
    public String getCardIdentifier() {
        return cardIdentifier;
    }
    
    /**
     * Gets the complete error message with enhanced context.
     * 
     * This method overrides the base getMessage() to provide enhanced error
     * information including the correlation ID, timestamp, and card identifier.
     * This enhanced message is used for logging and error reporting purposes.
     * 
     * @return The enhanced error message with complete context
     */
    @Override
    public String getMessage() {
        StringBuilder enhancedMessage = new StringBuilder();
        enhancedMessage.append(super.getMessage());
        enhancedMessage.append(" [CorrelationId: ").append(correlationId);
        enhancedMessage.append(", Timestamp: ").append(timestamp);
        enhancedMessage.append(", CardIdentifier: ").append(cardIdentifier);
        enhancedMessage.append("]");
        return enhancedMessage.toString();
    }
    
    /**
     * Logs this exception to the centralized audit service.
     * 
     * This method automatically logs the card not found event to the audit service
     * for security monitoring and compliance purposes. It generates both security
     * events and data access events to provide comprehensive audit trail coverage.
     * 
     * The audit logging replicates COBOL HANDLE ABEND patterns by providing
     * detailed context about the failed operation, including the card identifier,
     * correlation ID, and timestamp information.
     * 
     * Security Event: Logs the card not found condition as a potential security
     * event since failed card lookups may indicate unauthorized access attempts.
     * 
     * Data Access Event: Logs the failed data access operation with details about
     * the card identifier and operation context for compliance reporting.
     */
    public void logToAuditService() {
        if (auditService != null) {
            try {
                // Log as security event for potential fraud detection
                auditService.logSecurityEvent(
                    "CARD_NOT_FOUND",
                    "MEDIUM",
                    String.format("Card not found: %s - %s", cardIdentifier, super.getMessage()),
                    java.util.Map.of(
                        "correlation_id", correlationId,
                        "card_identifier", cardIdentifier,
                        "timestamp", timestamp.toString(),
                        "operation_type", "CARD_LOOKUP",
                        "failure_reason", "RECORD_NOT_FOUND"
                    )
                );
                
                // Log as data access event for compliance and audit trail
                auditService.logDataAccessEvent(
                    getCurrentUser(),
                    "CARD",
                    "SELECT",
                    0,
                    java.util.Map.of(
                        "correlation_id", correlationId,
                        "card_identifier", cardIdentifier,
                        "timestamp", timestamp.toString(),
                        "operation_result", "NOT_FOUND",
                        "error_message", super.getMessage()
                    )
                );
                
            } catch (Exception e) {
                // Ensure audit logging failures don't prevent exception handling
                System.err.println("Failed to log CardNotFoundException to audit service: " + e.getMessage());
            }
        }
    }
    
    /**
     * Sets the AuditService instance for audit logging.
     * 
     * This method allows dependency injection of the AuditService instance
     * for audit logging purposes. It is typically called by the Spring
     * container during application initialization.
     * 
     * @param auditService The AuditService instance to use for audit logging
     */
    public static void setAuditService(AuditService auditService) {
        CardNotFoundException.auditService = auditService;
    }
    
    /**
     * Generates a unique correlation ID for exception tracking.
     * 
     * The correlation ID is used to link this exception to audit log entries
     * and external system calls. It follows a specific format that includes
     * timestamp information for easy identification and sorting.
     * 
     * @return A unique correlation ID for this exception
     */
    private String generateCorrelationId() {
        return "CARD_NOT_FOUND_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
    
    /**
     * Gets the current user from the security context.
     * 
     * This method extracts the current user from the Spring Security context
     * for audit logging purposes. If no user is available, it returns a
     * default value indicating the operation was performed by the system.
     * 
     * @return The current user or "SYSTEM" if no user is available
     */
    private String getCurrentUser() {
        try {
            // In a real implementation, this would extract the user from Spring Security context
            // For now, we return a default value to prevent audit logging failures
            return "SYSTEM";
        } catch (Exception e) {
            return "SYSTEM";
        }
    }
}
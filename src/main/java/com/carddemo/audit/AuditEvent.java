/*
 * AuditEvent.java
 * 
 * Audit event entity model defining comprehensive audit record structure with SMF-compatible format.
 * Contains transaction identifiers, user context, business operation details, timestamp correlation,
 * JWT tracking, compliance flags, and outcome classification. Supports JSON serialization for 
 * Elasticsearch integration and structured audit trail management with regulatory compliance capabilities.
 * 
 * Replaces COBOL copybook audit structures from CSMSG01Y/CSMSG02Y message handling and CSUSR01Y 
 * user data with modern Java entity patterns that maintain mainframe SMF record compatibility
 * while providing enhanced searchability and cloud-native observability integration.
 * 
 * Derived from COBOL programs: COSGN00C, COACTUPC, COTRN01C, COBIL00C, COUSR00C, CBACT01C, CBTRN01C
 * Source copybooks: CSMSG01Y.cpy, CSMSG02Y.cpy, CSUSR01Y.cpy, CSDAT01Y.cpy
 * 
 * Copyright Amazon.com, Inc. or its affiliates.
 * All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.carddemo.audit;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.OffsetDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.OffsetDateTimeSerializer;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Comprehensive audit event entity model supporting SMF-compatible record structures with 
 * RFC-3339 timestamp formatting per monitoring architecture requirements.
 * 
 * This entity replaces traditional COBOL copybook audit structures (CSMSG01Y, CSMSG02Y, CSUSR01Y)
 * with modern Java patterns while maintaining compatibility with enterprise SIEM integration
 * and existing audit analysis tools through structured JSON serialization.
 * 
 * Key Features:
 * - SMF record format compatibility for enterprise SIEM integration
 * - RFC-3339 timestamp formatting for consistent chronological ordering
 * - Comprehensive business transaction tracking for credit card operations
 * - JSON serialization for Elasticsearch indexing with structured field mapping
 * - JWT correlation ID fields for distributed session audit trail integrity
 * - Compliance flags for SOX compliance, PCI DSS transaction logging, regulatory reporting
 * - Event outcome tracking (SUCCESS/FAILURE) and risk level classification
 * - Support for account management, card operations, transaction processing, payment activities
 * 
 * Event Structure Categories:
 * - System Events (0-9): System-level operations and lifecycle events
 * - Authentication Events (10-29): Based on COSGN00C operations
 * - Data Access Events (30-59): Based on VSAM file operations in COBOL programs
 * - Business Transaction Events (60-99): Based on business operations
 * - Administrative Operations (100-139): Based on COUSR00C operations
 * - Batch Processing Events (140-179): Based on CBACT01C and CBTRN01C operations
 * - Security and Compliance Events (180-219): Regulatory and security monitoring
 * - Integration and Interface Events (220-255): External system integration
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "auditId", "eventType", "eventCode", "timestamp", "duration",
    "userId", "username", "sessionId", "jwtTokenId", "correlationId",
    "sourceSystem", "sourceComponent", "ipAddress", "userAgent",
    "businessContext", "dataContext", "securityContext",
    "outcome", "riskLevel", "complianceFlags", "tags",
    "errorDetails", "performanceMetrics", "additionalAttributes"
})
public class AuditEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    // ========================================================================
    // CORE AUDIT IDENTIFICATION FIELDS
    // ========================================================================

    /**
     * Unique audit event identifier (UUID format).
     * Primary key for audit event tracking and correlation across distributed systems.
     * Generated automatically for each audit event to ensure uniqueness and traceability.
     */
    @JsonProperty("auditId")
    @NotNull(message = "Audit ID is required")
    private String auditId;

    /**
     * Audit event type from comprehensive AuditEventType enumeration.
     * Categorizes the specific type of operation being audited using SMF-compatible
     * event type codes (0-255) for structured event classification and filtering.
     */
    @JsonProperty("eventType")
    @NotNull(message = "Event type is required")
    private AuditEventType eventType;

    /**
     * SMF-compatible numeric event code (0-255).
     * Provides mainframe SMF record compatibility for enterprise SIEM integration
     * and existing audit analysis tools. Maps directly to AuditEventType enum values.
     */
    @JsonProperty("eventCode")
    @NotNull(message = "Event code is required")
    private Integer eventCode;

    // ========================================================================
    // TIMESTAMP AND TIMING INFORMATION
    // ========================================================================

    /**
     * Event timestamp in RFC-3339 format with timezone information.
     * Ensures consistent audit trail chronological ordering across system components
     * and maintains precise timing correlation for distributed transaction analysis.
     * 
     * Format: 2024-06-25T14:30:00.123456Z (ISO 8601 with microsecond precision)
     */
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX")
    @JsonSerialize(using = OffsetDateTimeSerializer.class)
    @JsonDeserialize(using = OffsetDateTimeDeserializer.class)
    @NotNull(message = "Timestamp is required")
    private OffsetDateTime timestamp;

    /**
     * Operation duration in milliseconds.
     * Tracks performance metrics for audited operations to support capacity planning
     * and performance analysis. Useful for identifying slow operations and trends.
     */
    @JsonProperty("duration")
    private Long duration;

    // ========================================================================
    // USER AUTHENTICATION AND SESSION CONTEXT
    // ========================================================================

    /**
     * User identifier from authentication context.
     * Links audit events to specific user accounts for accountability and
     * user activity tracking. Corresponds to user_id from PostgreSQL users table.
     */
    @JsonProperty("userId")
    @Size(max = 50, message = "User ID must not exceed 50 characters")
    private String userId;

    /**
     * Username from authentication context.
     * Human-readable user identifier for audit trail readability and
     * user activity correlation. Corresponds to username from users table.
     */
    @JsonProperty("username")
    @Size(max = 50, message = "Username must not exceed 50 characters")
    private String username;

    /**
     * Session identifier for pseudo-conversational state tracking.
     * Links audit events to specific user sessions for session-based audit analysis
     * and maintains audit trail integrity across stateless REST interactions.
     */
    @JsonProperty("sessionId")
    @Size(max = 100, message = "Session ID must not exceed 100 characters")
    private String sessionId;

    /**
     * JWT token identifier for authentication correlation.
     * Provides correlation between audit events and specific JWT tokens for
     * authentication tracking and security analysis. Links to JWT 'jti' claim.
     */
    @JsonProperty("jwtTokenId")
    @Size(max = 100, message = "JWT Token ID must not exceed 100 characters")
    private String jwtTokenId;

    /**
     * Correlation identifier for distributed transaction tracking.
     * Links related audit events across multiple services and operations
     * for comprehensive transaction analysis and distributed system debugging.
     */
    @JsonProperty("correlationId")
    @Size(max = 100, message = "Correlation ID must not exceed 100 characters")
    private String correlationId;

    // ========================================================================
    // SYSTEM AND SOURCE CONTEXT
    // ========================================================================

    /**
     * Source system identifier.
     * Identifies the specific system or application component that generated
     * the audit event for multi-system audit trail correlation.
     */
    @JsonProperty("sourceSystem")
    @Size(max = 50, message = "Source system must not exceed 50 characters")
    private String sourceSystem;

    /**
     * Source component or service name.
     * Identifies the specific service, controller, or component within the
     * application that generated the audit event for detailed source tracking.
     */
    @JsonProperty("sourceComponent")
    @Size(max = 100, message = "Source component must not exceed 100 characters")
    private String sourceComponent;

    /**
     * Client IP address.
     * Records the source IP address of the client making the request
     * for security analysis and geographic access pattern tracking.
     */
    @JsonProperty("ipAddress")
    @Pattern(regexp = "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$", 
               message = "Invalid IP address format")
    private String ipAddress;

    /**
     * User agent string from HTTP request.
     * Records browser/client information for access pattern analysis
     * and potential security threat identification.
     */
    @JsonProperty("userAgent")
    @Size(max = 500, message = "User agent must not exceed 500 characters")
    private String userAgent;

    // ========================================================================
    // BUSINESS TRANSACTION CONTEXT
    // ========================================================================

    /**
     * Business context information for audited operations.
     * Contains business-specific data relevant to the audited operation
     * such as account IDs, transaction amounts, card numbers (masked), etc.
     * 
     * Structure varies by event type:
     * - Account operations: accountId, customerId, operationType
     * - Card operations: cardNumber (masked), accountId, operationType  
     * - Transaction operations: transactionId, amount, merchantInfo
     * - Payment operations: paymentId, amount, paymentMethod
     * - User operations: targetUserId, operationType, roleChanges
     * - Batch operations: jobName, recordCount, processingWindow
     */
    @JsonProperty("businessContext")
    private Map<String, Object> businessContext;

    /**
     * Data access context for audited operations.
     * Contains information about data accessed, modified, or created
     * during the audited operation for data governance and compliance.
     * 
     * Typical fields:
     * - dataType: Type of data accessed (CUSTOMER, ACCOUNT, CARD, TRANSACTION)
     * - operation: CRUD operation type (CREATE, READ, UPDATE, DELETE)
     * - recordCount: Number of records affected
     * - dataClassification: Sensitivity level (PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED)
     * - fieldsAccessed: List of specific fields accessed or modified
     */
    @JsonProperty("dataContext")
    private Map<String, Object> dataContext;

    /**
     * Security context information for audited operations.
     * Contains security-relevant information such as authorization details,
     * privilege levels, and security policy evaluations.
     * 
     * Typical fields:
     * - authenticationMethod: How user was authenticated
     * - authorizationResult: SUCCESS/DENIED/INSUFFICIENT_PRIVILEGES
     * - privilegeLevel: USER/ADMIN/SYSTEM
     * - securityPolicyApplied: List of security policies evaluated
     * - riskFactors: List of identified risk factors
     */
    @JsonProperty("securityContext")
    private Map<String, Object> securityContext;

    // ========================================================================
    // OUTCOME AND CLASSIFICATION
    // ========================================================================

    /**
     * Operation outcome classification.
     * Indicates whether the audited operation completed successfully or failed.
     * Used for security analysis, error trend identification, and SLA monitoring.
     */
    @JsonProperty("outcome")
    @NotNull(message = "Outcome is required")
    private AuditOutcome outcome;

    /**
     * Risk level classification for the audited event.
     * Categorizes the security and business risk associated with the operation
     * for prioritized security analysis and automated alert generation.
     */
    @JsonProperty("riskLevel")
    @NotNull(message = "Risk level is required")
    private AuditRiskLevel riskLevel;

    /**
     * Compliance flags for regulatory and policy compliance.
     * List of compliance frameworks and regulations applicable to this audit event
     * for automated compliance reporting and regulatory audit trail management.
     * 
     * Common values:
     * - SOX_COMPLIANCE: Sarbanes-Oxley compliance requirement
     * - PCI_DSS: Payment Card Industry Data Security Standard
     * - GDPR: General Data Protection Regulation
     * - CCPA: California Consumer Privacy Act
     * - REGULATORY_REPORTING: General regulatory reporting requirement
     * - INTERNAL_POLICY: Internal organizational policy compliance
     */
    @JsonProperty("complianceFlags")
    private List<String> complianceFlags;

    /**
     * Custom tags for event categorization and filtering.
     * Flexible tagging system for custom categorization, environment identification,
     * and specialized filtering requirements beyond standard event classification.
     */
    @JsonProperty("tags")
    private List<String> tags;

    // ========================================================================
    // ERROR AND PERFORMANCE DETAILS
    // ========================================================================

    /**
     * Error details for failed operations.
     * Contains detailed error information for audit events with FAILURE outcome
     * including error codes, messages, and technical details for troubleshooting.
     * 
     * Structure:
     * - errorCode: Application-specific error code
     * - errorMessage: Human-readable error description
     * - exceptionType: Java exception class name (if applicable)
     * - stackTrace: Abbreviated stack trace for debugging (if appropriate)
     * - retryable: Boolean indicating if operation can be retried
     */
    @JsonProperty("errorDetails")
    private Map<String, Object> errorDetails;

    /**
     * Performance metrics for the audited operation.
     * Contains timing and resource utilization information for performance
     * analysis, capacity planning, and SLA monitoring.
     * 
     * Structure:
     * - executionTime: Total execution time in milliseconds
     * - databaseTime: Time spent in database operations
     * - externalServiceTime: Time spent calling external services
     * - memoryUsage: Peak memory usage during operation
     * - cpuUsage: CPU time consumed during operation
     */
    @JsonProperty("performanceMetrics")
    private Map<String, Object> performanceMetrics;

    /**
     * Additional attributes for event-specific information.
     * Flexible container for event-type-specific attributes that don't fit
     * into standard audit fields. Allows for extensibility without schema changes.
     */
    @JsonProperty("additionalAttributes")
    private Map<String, Object> additionalAttributes;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    /**
     * Default constructor for JPA and JSON deserialization.
     */
    public AuditEvent() {
        this.auditId = UUID.randomUUID().toString();
        this.timestamp = OffsetDateTime.now();
    }

    /**
     * Constructor for basic audit event creation.
     * 
     * @param eventType The type of audit event
     * @param userId User identifier from authentication context
     * @param outcome Operation outcome (SUCCESS/FAILURE)
     * @param riskLevel Risk classification for the event
     */
    public AuditEvent(AuditEventType eventType, String userId, AuditOutcome outcome, AuditRiskLevel riskLevel) {
        this();
        this.eventType = eventType;
        this.eventCode = eventType != null ? eventType.getEventCode() : null;
        this.userId = userId;
        this.outcome = outcome;
        this.riskLevel = riskLevel;
    }

    /**
     * Constructor for comprehensive audit event creation.
     * 
     * @param eventType The type of audit event
     * @param userId User identifier from authentication context
     * @param sessionId Session identifier for correlation
     * @param outcome Operation outcome (SUCCESS/FAILURE)
     * @param riskLevel Risk classification for the event
     * @param businessContext Business-specific context information
     */
    public AuditEvent(AuditEventType eventType, String userId, String sessionId, 
                     AuditOutcome outcome, AuditRiskLevel riskLevel, Map<String, Object> businessContext) {
        this(eventType, userId, outcome, riskLevel);
        this.sessionId = sessionId;
        this.businessContext = businessContext;
    }

    // ========================================================================
    // FACTORY METHODS FOR COMMON AUDIT SCENARIOS
    // ========================================================================

    /**
     * Creates an authentication audit event.
     * 
     * @param eventType Authentication-related event type
     * @param username Username attempting authentication
     * @param outcome SUCCESS or FAILURE
     * @param ipAddress Source IP address
     * @param userAgent Client user agent string
     * @return Configured AuditEvent for authentication
     */
    public static AuditEvent createAuthenticationEvent(AuditEventType eventType, String username, 
                                                      AuditOutcome outcome, String ipAddress, String userAgent) {
        AuditEvent event = new AuditEvent(eventType, null, outcome, 
                                        outcome == AuditOutcome.SUCCESS ? AuditRiskLevel.LOW : AuditRiskLevel.MEDIUM);
        event.setUsername(username);
        event.setIpAddress(ipAddress);
        event.setUserAgent(userAgent);
        event.setSourceSystem("CardDemo Authentication Service");
        event.addComplianceFlag("SOX_COMPLIANCE");
        return event;
    }

    /**
     * Creates a data access audit event.
     * 
     * @param eventType Data access event type
     * @param userId User performing the data access
     * @param dataType Type of data being accessed
     * @param operation CRUD operation type
     * @param recordCount Number of records affected
     * @return Configured AuditEvent for data access
     */
    public static AuditEvent createDataAccessEvent(AuditEventType eventType, String userId, 
                                                  String dataType, String operation, Integer recordCount) {
        AuditEvent event = new AuditEvent(eventType, userId, AuditOutcome.SUCCESS, AuditRiskLevel.LOW);
        event.addDataContext("dataType", dataType);
        event.addDataContext("operation", operation);
        event.addDataContext("recordCount", recordCount);
        event.setSourceSystem("CardDemo Data Service");
        event.addComplianceFlag("PCI_DSS");
        return event;
    }

    /**
     * Creates a transaction processing audit event.
     * 
     * @param eventType Transaction-related event type
     * @param userId User initiating the transaction
     * @param transactionId Unique transaction identifier
     * @param amount Transaction amount
     * @param outcome Transaction outcome
     * @return Configured AuditEvent for transaction processing
     */
    public static AuditEvent createTransactionEvent(AuditEventType eventType, String userId, 
                                                   String transactionId, BigDecimal amount, AuditOutcome outcome) {
        AuditRiskLevel riskLevel = determineTransactionRiskLevel(amount);
        AuditEvent event = new AuditEvent(eventType, userId, outcome, riskLevel);
        event.addBusinessContext("transactionId", transactionId);
        event.addBusinessContext("amount", amount);
        event.setSourceSystem("CardDemo Transaction Service");
        event.addComplianceFlag("PCI_DSS");
        event.addComplianceFlag("REGULATORY_REPORTING");
        return event;
    }

    /**
     * Creates an administrative action audit event.
     * 
     * @param eventType Administrative event type
     * @param adminUserId Administrator performing the action
     * @param targetUserId User being affected by the action (if applicable)
     * @param action Type of administrative action
     * @param outcome Action outcome
     * @return Configured AuditEvent for administrative actions
     */
    public static AuditEvent createAdministrativeEvent(AuditEventType eventType, String adminUserId, 
                                                      String targetUserId, String action, AuditOutcome outcome) {
        AuditEvent event = new AuditEvent(eventType, adminUserId, outcome, AuditRiskLevel.HIGH);
        event.addBusinessContext("action", action);
        if (targetUserId != null) {
            event.addBusinessContext("targetUserId", targetUserId);
        }
        event.setSourceSystem("CardDemo Admin Service");
        event.addComplianceFlag("SOX_COMPLIANCE");
        event.addComplianceFlag("INTERNAL_POLICY");
        return event;
    }

    /**
     * Creates a batch processing audit event.
     * 
     * @param eventType Batch processing event type
     * @param jobName Name of the batch job
     * @param recordCount Number of records processed
     * @param outcome Processing outcome
     * @return Configured AuditEvent for batch processing
     */
    public static AuditEvent createBatchProcessingEvent(AuditEventType eventType, String jobName, 
                                                       Integer recordCount, AuditOutcome outcome) {
        AuditEvent event = new AuditEvent(eventType, "BATCH_SYSTEM", outcome, AuditRiskLevel.LOW);
        event.addBusinessContext("jobName", jobName);
        event.addBusinessContext("recordCount", recordCount);
        event.setSourceSystem("CardDemo Batch Service");
        event.addComplianceFlag("REGULATORY_REPORTING");
        return event;
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Determines transaction risk level based on amount.
     * 
     * @param amount Transaction amount
     * @return Appropriate risk level
     */
    private static AuditRiskLevel determineTransactionRiskLevel(BigDecimal amount) {
        if (amount == null) return AuditRiskLevel.LOW;
        
        BigDecimal highRiskThreshold = new BigDecimal("10000.00");
        BigDecimal mediumRiskThreshold = new BigDecimal("1000.00");
        
        if (amount.compareTo(highRiskThreshold) >= 0) {
            return AuditRiskLevel.CRITICAL;
        } else if (amount.compareTo(mediumRiskThreshold) >= 0) {
            return AuditRiskLevel.HIGH;
        } else {
            return AuditRiskLevel.MEDIUM;
        }
    }

    /**
     * Adds a business context attribute.
     * 
     * @param key Attribute key
     * @param value Attribute value
     */
    public void addBusinessContext(String key, Object value) {
        if (this.businessContext == null) {
            this.businessContext = new java.util.HashMap<>();
        }
        this.businessContext.put(key, value);
    }

    /**
     * Adds a data context attribute.
     * 
     * @param key Attribute key
     * @param value Attribute value
     */
    public void addDataContext(String key, Object value) {
        if (this.dataContext == null) {
            this.dataContext = new java.util.HashMap<>();
        }
        this.dataContext.put(key, value);
    }

    /**
     * Adds a security context attribute.
     * 
     * @param key Attribute key
     * @param value Attribute value
     */
    public void addSecurityContext(String key, Object value) {
        if (this.securityContext == null) {
            this.securityContext = new java.util.HashMap<>();
        }
        this.securityContext.put(key, value);
    }

    /**
     * Adds a compliance flag.
     * 
     * @param flag Compliance flag to add
     */
    public void addComplianceFlag(String flag) {
        if (this.complianceFlags == null) {
            this.complianceFlags = new java.util.ArrayList<>();
        }
        if (!this.complianceFlags.contains(flag)) {
            this.complianceFlags.add(flag);
        }
    }

    /**
     * Adds a tag for categorization.
     * 
     * @param tag Tag to add
     */
    public void addTag(String tag) {
        if (this.tags == null) {
            this.tags = new java.util.ArrayList<>();
        }
        if (!this.tags.contains(tag)) {
            this.tags.add(tag);
        }
    }

    /**
     * Sets error details for failed operations.
     * 
     * @param errorCode Application error code
     * @param errorMessage Error description
     * @param exceptionType Exception class name
     */
    public void setErrorDetails(String errorCode, String errorMessage, String exceptionType) {
        if (this.errorDetails == null) {
            this.errorDetails = new java.util.HashMap<>();
        }
        this.errorDetails.put("errorCode", errorCode);
        this.errorDetails.put("errorMessage", errorMessage);
        if (exceptionType != null) {
            this.errorDetails.put("exceptionType", exceptionType);
        }
    }

    /**
     * Adds performance metric.
     * 
     * @param metric Metric name
     * @param value Metric value
     */
    public void addPerformanceMetric(String metric, Object value) {
        if (this.performanceMetrics == null) {
            this.performanceMetrics = new java.util.HashMap<>();
        }
        this.performanceMetrics.put(metric, value);
    }

    // ========================================================================
    // GETTERS AND SETTERS
    // ========================================================================

    public String getAuditId() {
        return auditId;
    }

    public void setAuditId(String auditId) {
        this.auditId = auditId;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public void setEventType(AuditEventType eventType) {
        this.eventType = eventType;
        if (eventType != null) {
            this.eventCode = eventType.getEventCode();
        }
    }

    public Integer getEventCode() {
        return eventCode;
    }

    public void setEventCode(Integer eventCode) {
        this.eventCode = eventCode;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getJwtTokenId() {
        return jwtTokenId;
    }

    public void setJwtTokenId(String jwtTokenId) {
        this.jwtTokenId = jwtTokenId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getSourceComponent() {
        return sourceComponent;
    }

    public void setSourceComponent(String sourceComponent) {
        this.sourceComponent = sourceComponent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Map<String, Object> getBusinessContext() {
        return businessContext;
    }

    public void setBusinessContext(Map<String, Object> businessContext) {
        this.businessContext = businessContext;
    }

    public Map<String, Object> getDataContext() {
        return dataContext;
    }

    public void setDataContext(Map<String, Object> dataContext) {
        this.dataContext = dataContext;
    }

    public Map<String, Object> getSecurityContext() {
        return securityContext;
    }

    public void setSecurityContext(Map<String, Object> securityContext) {
        this.securityContext = securityContext;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(AuditOutcome outcome) {
        this.outcome = outcome;
    }

    public AuditRiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(AuditRiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public List<String> getComplianceFlags() {
        return complianceFlags;
    }

    public void setComplianceFlags(List<String> complianceFlags) {
        this.complianceFlags = complianceFlags;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Map<String, Object> getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(Map<String, Object> errorDetails) {
        this.errorDetails = errorDetails;
    }

    public Map<String, Object> getPerformanceMetrics() {
        return performanceMetrics;
    }

    public void setPerformanceMetrics(Map<String, Object> performanceMetrics) {
        this.performanceMetrics = performanceMetrics;
    }

    public Map<String, Object> getAdditionalAttributes() {
        return additionalAttributes;
    }

    public void setAdditionalAttributes(Map<String, Object> additionalAttributes) {
        this.additionalAttributes = additionalAttributes;
    }

    // ========================================================================
    // EQUALS, HASHCODE, AND TOSTRING
    // ========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditEvent that = (AuditEvent) o;
        return Objects.equals(auditId, that.auditId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(auditId);
    }

    @Override
    public String toString() {
        return "AuditEvent{" +
                "auditId='" + auditId + '\'' +
                ", eventType=" + eventType +
                ", eventCode=" + eventCode +
                ", timestamp=" + timestamp +
                ", userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", outcome=" + outcome +
                ", riskLevel=" + riskLevel +
                ", sourceSystem='" + sourceSystem + '\'' +
                '}';
    }

    // ========================================================================
    // SUPPORTING ENUMERATIONS
    // ========================================================================

    /**
     * Audit event outcome enumeration.
     * Indicates the result of the audited operation for security analysis
     * and operational monitoring.
     */
    public enum AuditOutcome {
        /** Operation completed successfully */
        SUCCESS,
        /** Operation failed due to error or validation failure */
        FAILURE,
        /** Operation was denied due to insufficient permissions */
        DENIED,
        /** Operation was partially completed with warnings */
        WARNING
    }

    /**
     * Audit event risk level classification.
     * Categorizes the security and business risk associated with the audited
     * operation for prioritized security analysis and automated alerting.
     */
    public enum AuditRiskLevel {
        /** Low risk operation - routine business activity */
        LOW,
        /** Medium risk operation - elevated privileges or sensitive data access */
        MEDIUM,
        /** High risk operation - administrative actions or large transactions */
        HIGH,
        /** Critical risk operation - security-sensitive or high-value transactions */
        CRITICAL
    }
}
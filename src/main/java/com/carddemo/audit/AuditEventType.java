/*
 * AuditEventType.java
 * 
 * Enumeration defining comprehensive audit event type classification system with SMF-compatible numbering.
 * Categorizes authentication events, authorization decisions, data access operations, administrative actions, 
 * business transactions, and batch processing activities. Enables structured audit event filtering, SIEM 
 * integration, and regulatory compliance reporting through systematic event type organization.
 * 
 * Derived from COBOL programs: COSGN00C, COACTUPC, COTRN01C, COBIL00C, COUSR00C, CBACT01C, CBTRN01C
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

/**
 * Comprehensive audit event type enumeration supporting SMF-compatible numbering system.
 * 
 * Event types are organized in numeric ranges for systematic categorization:
 * - 0-9: System events
 * - 10-29: Authentication and authorization events
 * - 30-59: Data access and modification events
 * - 60-99: Business transaction events
 * - 100-139: Administrative operations
 * - 140-179: Batch processing operations
 * - 180-219: Security and compliance events
 * - 220-255: Integration and system interface events
 * 
 * Each event type maps to specific COBOL program operations to maintain traceability
 * from mainframe audit patterns to modern audit requirements.
 */
public enum AuditEventType {
    
    // ========================================================================
    // SYSTEM EVENTS (0-9) - System-level operations and lifecycle events
    // ========================================================================
    
    /** System startup and initialization - SMF Type 0 equivalent */
    SYSTEM_STARTUP(0, "SYSTEM_STARTUP", "System", "System started successfully"),
    
    /** System shutdown and termination - SMF Type 1 equivalent */
    SYSTEM_SHUTDOWN(1, "SYSTEM_SHUTDOWN", "System", "System shutdown initiated"),
    
    /** Application session initialization */
    SESSION_INIT(2, "SESSION_INIT", "System", "User session initialized"),
    
    /** Application session termination */
    SESSION_TERM(3, "SESSION_TERM", "System", "User session terminated"),
    
    /** Transaction timeout occurrence */
    TRANSACTION_TIMEOUT(4, "TRANSACTION_TIMEOUT", "System", "Transaction timed out"),
    
    /** System error or exception occurrence */
    SYSTEM_ERROR(5, "SYSTEM_ERROR", "System", "System error occurred"),
    
    /** Database connection established */
    DATABASE_CONNECT(6, "DATABASE_CONNECT", "System", "Database connection established"),
    
    /** Database connection terminated */
    DATABASE_DISCONNECT(7, "DATABASE_DISCONNECT", "System", "Database connection terminated"),
    
    /** Configuration change applied */
    CONFIG_CHANGE(8, "CONFIG_CHANGE", "System", "System configuration changed"),
    
    /** Health check performed */
    HEALTH_CHECK(9, "HEALTH_CHECK", "System", "System health check performed"),
    
    // ========================================================================
    // AUTHENTICATION EVENTS (10-29) - Based on COSGN00C operations
    // ========================================================================
    
    /** Successful user login - COSGN00C authentication success */
    LOGIN_SUCCESS(10, "LOGIN_SUCCESS", "Authentication", "User successfully logged in"),
    
    /** Failed user login attempt - COSGN00C authentication failure */
    LOGIN_FAILURE(11, "LOGIN_FAILURE", "Authentication", "User login attempt failed"),
    
    /** Invalid username provided - COSGN00C user not found */
    LOGIN_INVALID_USER(12, "LOGIN_INVALID_USER", "Authentication", "Invalid username provided"),
    
    /** Incorrect password provided - COSGN00C password mismatch */
    LOGIN_INVALID_PASSWORD(13, "LOGIN_INVALID_PASSWORD", "Authentication", "Incorrect password provided"),
    
    /** User account locked due to failed attempts */
    ACCOUNT_LOCKED(14, "ACCOUNT_LOCKED", "Authentication", "User account locked"),
    
    /** User account unlocked by administrator */
    ACCOUNT_UNLOCKED(15, "ACCOUNT_UNLOCKED", "Authentication", "User account unlocked"),
    
    /** Password change requested */
    PASSWORD_CHANGE_REQUEST(16, "PASSWORD_CHANGE_REQUEST", "Authentication", "Password change requested"),
    
    /** Password successfully changed */
    PASSWORD_CHANGE_SUCCESS(17, "PASSWORD_CHANGE_SUCCESS", "Authentication", "Password successfully changed"),
    
    /** Password change failed */
    PASSWORD_CHANGE_FAILURE(18, "PASSWORD_CHANGE_FAILURE", "Authentication", "Password change failed"),
    
    /** Session expired due to inactivity */
    SESSION_EXPIRED(19, "SESSION_EXPIRED", "Authentication", "Session expired"),
    
    /** User logged out voluntarily */
    LOGOUT(20, "LOGOUT", "Authentication", "User logged out"),
    
    /** Forced logout by administrator */
    FORCED_LOGOUT(21, "FORCED_LOGOUT", "Authentication", "User forcibly logged out"),
    
    /** Multiple concurrent login attempt detected */
    CONCURRENT_LOGIN_ATTEMPT(22, "CONCURRENT_LOGIN_ATTEMPT", "Authentication", "Concurrent login attempt detected"),
    
    /** Login from suspicious location */
    SUSPICIOUS_LOGIN_LOCATION(23, "SUSPICIOUS_LOGIN_LOCATION", "Authentication", "Login from suspicious location"),
    
    /** Authentication token issued */
    TOKEN_ISSUED(24, "TOKEN_ISSUED", "Authentication", "Authentication token issued"),
    
    /** Authentication token refreshed */
    TOKEN_REFRESHED(25, "TOKEN_REFRESHED", "Authentication", "Authentication token refreshed"),
    
    /** Authentication token expired */
    TOKEN_EXPIRED(26, "TOKEN_EXPIRED", "Authentication", "Authentication token expired"),
    
    /** Authentication token revoked */
    TOKEN_REVOKED(27, "TOKEN_REVOKED", "Authentication", "Authentication token revoked"),
    
    /** Two-factor authentication enabled */
    TWO_FACTOR_ENABLED(28, "TWO_FACTOR_ENABLED", "Authentication", "Two-factor authentication enabled"),
    
    /** Two-factor authentication disabled */
    TWO_FACTOR_DISABLED(29, "TWO_FACTOR_DISABLED", "Authentication", "Two-factor authentication disabled"),
    
    // ========================================================================
    // DATA ACCESS EVENTS (30-59) - Based on VSAM file operations in COBOL programs
    // ========================================================================
    
    /** Customer data record read - CUSTDAT file access */
    CUSTOMER_DATA_READ(30, "CUSTOMER_DATA_READ", "Data Access", "Customer data accessed"),
    
    /** Customer data record created */
    CUSTOMER_DATA_CREATE(31, "CUSTOMER_DATA_CREATE", "Data Access", "Customer data created"),
    
    /** Customer data record updated */
    CUSTOMER_DATA_UPDATE(32, "CUSTOMER_DATA_UPDATE", "Data Access", "Customer data updated"),
    
    /** Customer data record deleted */
    CUSTOMER_DATA_DELETE(33, "CUSTOMER_DATA_DELETE", "Data Access", "Customer data deleted"),
    
    /** Account data record read - ACCTDAT file access from COACTUPC */
    ACCOUNT_DATA_READ(34, "ACCOUNT_DATA_READ", "Data Access", "Account data accessed"),
    
    /** Account data record created */
    ACCOUNT_DATA_CREATE(35, "ACCOUNT_DATA_CREATE", "Data Access", "Account data created"),
    
    /** Account data record updated - COACTUPC account modification */
    ACCOUNT_DATA_UPDATE(36, "ACCOUNT_DATA_UPDATE", "Data Access", "Account data updated"),
    
    /** Account data record deleted */
    ACCOUNT_DATA_DELETE(37, "ACCOUNT_DATA_DELETE", "Data Access", "Account data deleted"),
    
    /** Card data record read - CARDDAT file access */
    CARD_DATA_READ(38, "CARD_DATA_READ", "Data Access", "Card data accessed"),
    
    /** Card data record created */
    CARD_DATA_CREATE(39, "CARD_DATA_CREATE", "Data Access", "Card data created"),
    
    /** Card data record updated */
    CARD_DATA_UPDATE(40, "CARD_DATA_UPDATE", "Data Access", "Card data updated"),
    
    /** Card data record deleted */
    CARD_DATA_DELETE(41, "CARD_DATA_DELETE", "Data Access", "Card data deleted"),
    
    /** Transaction data record read - TRANSACT file access from COTRN01C */
    TRANSACTION_DATA_READ(42, "TRANSACTION_DATA_READ", "Data Access", "Transaction data accessed"),
    
    /** Transaction data record created */
    TRANSACTION_DATA_CREATE(43, "TRANSACTION_DATA_CREATE", "Data Access", "Transaction data created"),
    
    /** Transaction data record updated */
    TRANSACTION_DATA_UPDATE(44, "TRANSACTION_DATA_UPDATE", "Data Access", "Transaction data updated"),
    
    /** Transaction data record deleted */
    TRANSACTION_DATA_DELETE(45, "TRANSACTION_DATA_DELETE", "Data Access", "Transaction data deleted"),
    
    /** User security data read - USRSEC file access from COSGN00C */
    USER_SECURITY_READ(46, "USER_SECURITY_READ", "Data Access", "User security data accessed"),
    
    /** User security data created */
    USER_SECURITY_CREATE(47, "USER_SECURITY_CREATE", "Data Access", "User security data created"),
    
    /** User security data updated */
    USER_SECURITY_UPDATE(48, "USER_SECURITY_UPDATE", "Data Access", "User security data updated"),
    
    /** User security data deleted */
    USER_SECURITY_DELETE(49, "USER_SECURITY_DELETE", "Data Access", "User security data deleted"),
    
    /** Cross-reference data read - XREF file access */
    XREF_DATA_READ(50, "XREF_DATA_READ", "Data Access", "Cross-reference data accessed"),
    
    /** Cross-reference data created */
    XREF_DATA_CREATE(51, "XREF_DATA_CREATE", "Data Access", "Cross-reference data created"),
    
    /** Cross-reference data updated */
    XREF_DATA_UPDATE(52, "XREF_DATA_UPDATE", "Data Access", "Cross-reference data updated"),
    
    /** Cross-reference data deleted */
    XREF_DATA_DELETE(53, "XREF_DATA_DELETE", "Data Access", "Cross-reference data deleted"),
    
    /** Sensitive data field accessed */
    SENSITIVE_DATA_ACCESS(54, "SENSITIVE_DATA_ACCESS", "Data Access", "Sensitive data field accessed"),
    
    /** PII data accessed */
    PII_DATA_ACCESS(55, "PII_DATA_ACCESS", "Data Access", "Personally identifiable information accessed"),
    
    /** Financial data accessed */
    FINANCIAL_DATA_ACCESS(56, "FINANCIAL_DATA_ACCESS", "Data Access", "Financial data accessed"),
    
    /** Bulk data export operation */
    BULK_DATA_EXPORT(57, "BULK_DATA_EXPORT", "Data Access", "Bulk data export performed"),
    
    /** Data backup operation */
    DATA_BACKUP(58, "DATA_BACKUP", "Data Access", "Data backup operation performed"),
    
    /** Data restore operation */
    DATA_RESTORE(59, "DATA_RESTORE", "Data Access", "Data restore operation performed"),
    
    // ========================================================================
    // BUSINESS TRANSACTION EVENTS (60-99) - Based on business operations
    // ========================================================================
    
    /** Account balance inquiry */
    ACCOUNT_BALANCE_INQUIRY(60, "ACCOUNT_BALANCE_INQUIRY", "Business Transaction", "Account balance inquired"),
    
    /** Account statement generation */
    ACCOUNT_STATEMENT_GENERATE(61, "ACCOUNT_STATEMENT_GENERATE", "Business Transaction", "Account statement generated"),
    
    /** Account status change */
    ACCOUNT_STATUS_CHANGE(62, "ACCOUNT_STATUS_CHANGE", "Business Transaction", "Account status changed"),
    
    /** Account closure initiated */
    ACCOUNT_CLOSURE(63, "ACCOUNT_CLOSURE", "Business Transaction", "Account closure initiated"),
    
    /** Credit limit change - COACTUPC credit limit modification */
    CREDIT_LIMIT_CHANGE(64, "CREDIT_LIMIT_CHANGE", "Business Transaction", "Credit limit modified"),
    
    /** Card activation */
    CARD_ACTIVATION(65, "CARD_ACTIVATION", "Business Transaction", "Card activated"),
    
    /** Card deactivation */
    CARD_DEACTIVATION(66, "CARD_DEACTIVATION", "Business Transaction", "Card deactivated"),
    
    /** Card replacement request */
    CARD_REPLACEMENT(67, "CARD_REPLACEMENT", "Business Transaction", "Card replacement requested"),
    
    /** Card PIN change */
    CARD_PIN_CHANGE(68, "CARD_PIN_CHANGE", "Business Transaction", "Card PIN changed"),
    
    /** Card transaction authorization */
    CARD_TRANSACTION_AUTH(69, "CARD_TRANSACTION_AUTH", "Business Transaction", "Card transaction authorized"),
    
    /** Card transaction decline */
    CARD_TRANSACTION_DECLINE(70, "CARD_TRANSACTION_DECLINE", "Business Transaction", "Card transaction declined"),
    
    /** Bill payment initiated - COBIL00C payment processing */
    BILL_PAYMENT_INITIATED(71, "BILL_PAYMENT_INITIATED", "Business Transaction", "Bill payment initiated"),
    
    /** Bill payment completed - COBIL00C payment confirmation */
    BILL_PAYMENT_COMPLETED(72, "BILL_PAYMENT_COMPLETED", "Business Transaction", "Bill payment completed"),
    
    /** Bill payment failed */
    BILL_PAYMENT_FAILED(73, "BILL_PAYMENT_FAILED", "Business Transaction", "Bill payment failed"),
    
    /** Payment reversal */
    PAYMENT_REVERSAL(74, "PAYMENT_REVERSAL", "Business Transaction", "Payment reversed"),
    
    /** Interest calculation */
    INTEREST_CALCULATION(75, "INTEREST_CALCULATION", "Business Transaction", "Interest calculated"),
    
    /** Fee assessment */
    FEE_ASSESSMENT(76, "FEE_ASSESSMENT", "Business Transaction", "Fee assessed"),
    
    /** Credit posting */
    CREDIT_POSTING(77, "CREDIT_POSTING", "Business Transaction", "Credit posted to account"),
    
    /** Debit posting */
    DEBIT_POSTING(78, "DEBIT_POSTING", "Business Transaction", "Debit posted to account"),
    
    /** Balance transfer initiated */
    BALANCE_TRANSFER_INIT(79, "BALANCE_TRANSFER_INIT", "Business Transaction", "Balance transfer initiated"),
    
    /** Balance transfer completed */
    BALANCE_TRANSFER_COMPLETE(80, "BALANCE_TRANSFER_COMPLETE", "Business Transaction", "Balance transfer completed"),
    
    /** Dispute case opened */
    DISPUTE_CASE_OPENED(81, "DISPUTE_CASE_OPENED", "Business Transaction", "Dispute case opened"),
    
    /** Dispute case resolved */
    DISPUTE_CASE_RESOLVED(82, "DISPUTE_CASE_RESOLVED", "Business Transaction", "Dispute case resolved"),
    
    /** Fraud alert triggered */
    FRAUD_ALERT_TRIGGERED(83, "FRAUD_ALERT_TRIGGERED", "Business Transaction", "Fraud alert triggered"),
    
    /** Fraud case opened */
    FRAUD_CASE_OPENED(84, "FRAUD_CASE_OPENED", "Business Transaction", "Fraud case opened"),
    
    /** Account freeze initiated */
    ACCOUNT_FREEZE(85, "ACCOUNT_FREEZE", "Business Transaction", "Account frozen"),
    
    /** Account unfreeze initiated */
    ACCOUNT_UNFREEZE(86, "ACCOUNT_UNFREEZE", "Business Transaction", "Account unfrozen"),
    
    /** Credit bureau report generated */
    CREDIT_BUREAU_REPORT(87, "CREDIT_BUREAU_REPORT", "Business Transaction", "Credit bureau report generated"),
    
    /** Promotional offer applied */
    PROMOTIONAL_OFFER_APPLIED(88, "PROMOTIONAL_OFFER_APPLIED", "Business Transaction", "Promotional offer applied"),
    
    /** Customer communication sent */
    CUSTOMER_COMMUNICATION(89, "CUSTOMER_COMMUNICATION", "Business Transaction", "Customer communication sent"),
    
    /** Document generation */
    DOCUMENT_GENERATION(90, "DOCUMENT_GENERATION", "Business Transaction", "Document generated"),
    
    /** Report generation requested */
    REPORT_GENERATION(91, "REPORT_GENERATION", "Business Transaction", "Report generation requested"),
    
    /** Batch job validation */
    BATCH_VALIDATION(92, "BATCH_VALIDATION", "Business Transaction", "Batch job validation performed"),
    
    /** Transaction settlement */
    TRANSACTION_SETTLEMENT(93, "TRANSACTION_SETTLEMENT", "Business Transaction", "Transaction settled"),
    
    /** Monthly statement cycle */
    MONTHLY_STATEMENT_CYCLE(94, "MONTHLY_STATEMENT_CYCLE", "Business Transaction", "Monthly statement cycle executed"),
    
    /** Year-end processing */
    YEAR_END_PROCESSING(95, "YEAR_END_PROCESSING", "Business Transaction", "Year-end processing executed"),
    
    /** Regulatory report submission */
    REGULATORY_REPORT_SUBMIT(96, "REGULATORY_REPORT_SUBMIT", "Business Transaction", "Regulatory report submitted"),
    
    /** Tax document generation */
    TAX_DOCUMENT_GENERATION(97, "TAX_DOCUMENT_GENERATION", "Business Transaction", "Tax document generated"),
    
    /** Compliance check performed */
    COMPLIANCE_CHECK(98, "COMPLIANCE_CHECK", "Business Transaction", "Compliance check performed"),
    
    /** Risk assessment completed */
    RISK_ASSESSMENT(99, "RISK_ASSESSMENT", "Business Transaction", "Risk assessment completed"),
    
    // ========================================================================
    // ADMINISTRATIVE OPERATIONS (100-139) - Based on COUSR00C operations
    // ========================================================================
    
    /** User account created - Administrative user management */
    USER_ACCOUNT_CREATED(100, "USER_ACCOUNT_CREATED", "Administrative", "User account created"),
    
    /** User account modified */
    USER_ACCOUNT_MODIFIED(101, "USER_ACCOUNT_MODIFIED", "Administrative", "User account modified"),
    
    /** User account deactivated */
    USER_ACCOUNT_DEACTIVATED(102, "USER_ACCOUNT_DEACTIVATED", "Administrative", "User account deactivated"),
    
    /** User account reactivated */
    USER_ACCOUNT_REACTIVATED(103, "USER_ACCOUNT_REACTIVATED", "Administrative", "User account reactivated"),
    
    /** User role assigned */
    USER_ROLE_ASSIGNED(104, "USER_ROLE_ASSIGNED", "Administrative", "User role assigned"),
    
    /** User role removed */
    USER_ROLE_REMOVED(105, "USER_ROLE_REMOVED", "Administrative", "User role removed"),
    
    /** User permissions granted */
    USER_PERMISSIONS_GRANTED(106, "USER_PERMISSIONS_GRANTED", "Administrative", "User permissions granted"),
    
    /** User permissions revoked */
    USER_PERMISSIONS_REVOKED(107, "USER_PERMISSIONS_REVOKED", "Administrative", "User permissions revoked"),
    
    /** Administrative privilege escalation */
    ADMIN_PRIVILEGE_ESCALATION(108, "ADMIN_PRIVILEGE_ESCALATION", "Administrative", "Administrative privilege escalated"),
    
    /** Administrative access granted */
    ADMIN_ACCESS_GRANTED(109, "ADMIN_ACCESS_GRANTED", "Administrative", "Administrative access granted"),
    
    /** Administrative access revoked */
    USER_REVOKED(110, "ADMIN_ACCESS_REVOKED", "Administrative", "Administrative access revoked"),
    
    /** System configuration updated */
    SYSTEM_CONFIG_UPDATED(111, "SYSTEM_CONFIG_UPDATED", "Administrative", "System configuration updated"),
    
    /** Security policy updated */
    SECURITY_POLICY_UPDATED(112, "SECURITY_POLICY_UPDATED", "Administrative", "Security policy updated"),
    
    /** Audit log reviewed */
    AUDIT_LOG_REVIEWED(113, "AUDIT_LOG_REVIEWED", "Administrative", "Audit log reviewed"),
    
    /** Security incident reported */
    SECURITY_INCIDENT_REPORTED(114, "SECURITY_INCIDENT_REPORTED", "Administrative", "Security incident reported"),
    
    /** Security incident resolved */
    SECURITY_INCIDENT_RESOLVED(115, "SECURITY_INCIDENT_RESOLVED", "Administrative", "Security incident resolved"),
    
    /** User list accessed - COUSR00C user browsing operation */
    USER_LIST_ACCESSED(116, "USER_LIST_ACCESSED", "Administrative", "User list accessed"),
    
    /** User search performed */
    USER_SEARCH_PERFORMED(117, "USER_SEARCH_PERFORMED", "Administrative", "User search performed"),
    
    /** Administrative report generated */
    ADMIN_REPORT_GENERATED(118, "ADMIN_REPORT_GENERATED", "Administrative", "Administrative report generated"),
    
    /** System maintenance performed */
    SYSTEM_MAINTENANCE(119, "SYSTEM_MAINTENANCE", "Administrative", "System maintenance performed"),
    
    /** Database maintenance performed */
    DATABASE_MAINTENANCE(120, "DATABASE_MAINTENANCE", "Administrative", "Database maintenance performed"),
    
    /** Backup verification performed */
    BACKUP_VERIFICATION(121, "BACKUP_VERIFICATION", "Administrative", "Backup verification performed"),
    
    /** Security scan initiated */
    SECURITY_SCAN_INITIATED(122, "SECURITY_SCAN_INITIATED", "Administrative", "Security scan initiated"),
    
    /** Vulnerability assessment completed */
    VULNERABILITY_ASSESSMENT(123, "VULNERABILITY_ASSESSMENT", "Administrative", "Vulnerability assessment completed"),
    
    /** Policy violation detected */
    POLICY_VIOLATION_DETECTED(124, "POLICY_VIOLATION_DETECTED", "Administrative", "Policy violation detected"),
    
    /** Emergency access granted */
    EMERGENCY_ACCESS_GRANTED(125, "EMERGENCY_ACCESS_GRANTED", "Administrative", "Emergency access granted"),
    
    /** Break glass access used */
    BREAK_GLASS_ACCESS(126, "BREAK_GLASS_ACCESS", "Administrative", "Break glass access used"),
    
    /** Administrative override performed */
    ADMIN_OVERRIDE(127, "ADMIN_OVERRIDE", "Administrative", "Administrative override performed"),
    
    /** System alert acknowledged */
    SYSTEM_ALERT_ACKNOWLEDGED(128, "SYSTEM_ALERT_ACKNOWLEDGED", "Administrative", "System alert acknowledged"),
    
    /** Maintenance window started */
    MAINTENANCE_WINDOW_START(129, "MAINTENANCE_WINDOW_START", "Administrative", "Maintenance window started"),
    
    /** Maintenance window ended */
    MAINTENANCE_WINDOW_END(130, "MAINTENANCE_WINDOW_END", "Administrative", "Maintenance window ended"),
    
    /** Service restart performed */
    SERVICE_RESTART(131, "SERVICE_RESTART", "Administrative", "Service restarted"),
    
    /** Configuration backup created */
    CONFIG_BACKUP_CREATED(132, "CONFIG_BACKUP_CREATED", "Administrative", "Configuration backup created"),
    
    /** License update applied */
    LICENSE_UPDATE(133, "LICENSE_UPDATE", "Administrative", "License update applied"),
    
    /** Certificate renewal completed */
    CERTIFICATE_RENEWAL(134, "CERTIFICATE_RENEWAL", "Administrative", "Certificate renewed"),
    
    /** Access review completed */
    ACCESS_REVIEW_COMPLETED(135, "ACCESS_REVIEW_COMPLETED", "Administrative", "Access review completed"),
    
    /** Segregation of duties violated */
    SOD_VIOLATION(136, "SOD_VIOLATION", "Administrative", "Segregation of duties violation detected"),
    
    /** Administrative menu accessed */
    ADMIN_MENU_ACCESSED(137, "ADMIN_MENU_ACCESSED", "Administrative", "Administrative menu accessed"),
    
    /** User profile updated */
    USER_PROFILE_UPDATED(138, "USER_PROFILE_UPDATED", "Administrative", "User profile updated"),
    
    /** Password policy enforced */
    PASSWORD_POLICY_ENFORCED(139, "PASSWORD_POLICY_ENFORCED", "Administrative", "Password policy enforced"),
    
    // ========================================================================
    // BATCH PROCESSING EVENTS (140-179) - Based on CBACT01C and CBTRN01C operations
    // ========================================================================
    
    /** Batch job started - CBACT01C account processing */
    BATCH_JOB_STARTED(140, "BATCH_JOB_STARTED", "Batch Processing", "Batch job started"),
    
    /** Batch job completed successfully */
    BATCH_JOB_COMPLETED(141, "BATCH_JOB_COMPLETED", "Batch Processing", "Batch job completed successfully"),
    
    /** Batch job failed */
    BATCH_JOB_FAILED(142, "BATCH_JOB_FAILED", "Batch Processing", "Batch job failed"),
    
    /** Batch job aborted */
    BATCH_JOB_ABORTED(143, "BATCH_JOB_ABORTED", "Batch Processing", "Batch job aborted"),
    
    /** Daily transaction file processing - CBTRN01C operation */
    DAILY_TRANSACTION_PROCESSING(144, "DAILY_TRANSACTION_PROCESSING", "Batch Processing", "Daily transaction file processed"),
    
    /** Account batch processing - CBACT01C operation */
    ACCOUNT_BATCH_PROCESSING(145, "ACCOUNT_BATCH_PROCESSING", "Batch Processing", "Account batch processing executed"),
    
    /** Statement generation batch */
    STATEMENT_GENERATION_BATCH(146, "STATEMENT_GENERATION_BATCH", "Batch Processing", "Statement generation batch executed"),
    
    /** Interest calculation batch */
    INTEREST_CALC_BATCH(147, "INTEREST_CALC_BATCH", "Batch Processing", "Interest calculation batch executed"),
    
    /** Fee processing batch */
    FEE_PROCESSING_BATCH(148, "FEE_PROCESSING_BATCH", "Batch Processing", "Fee processing batch executed"),
    
    /** Payment processing batch */
    PAYMENT_PROCESSING_BATCH(149, "PAYMENT_PROCESSING_BATCH", "Batch Processing", "Payment processing batch executed"),
    
    /** Transaction posting batch */
    TRANSACTION_POSTING_BATCH(150, "TRANSACTION_POSTING_BATCH", "Batch Processing", "Transaction posting batch executed"),
    
    /** End-of-day processing */
    END_OF_DAY_PROCESSING(151, "END_OF_DAY_PROCESSING", "Batch Processing", "End-of-day processing executed"),
    
    /** Month-end processing */
    MONTH_END_PROCESSING(152, "MONTH_END_PROCESSING", "Batch Processing", "Month-end processing executed"),
    
    /** Cycle processing */
    CYCLE_PROCESSING(153, "CYCLE_PROCESSING", "Batch Processing", "Cycle processing executed"),
    
    /** Data archival batch */
    DATA_ARCHIVAL_BATCH(154, "DATA_ARCHIVAL_BATCH", "Batch Processing", "Data archival batch executed"),
    
    /** Data purge batch */
    DATA_PURGE_BATCH(155, "DATA_PURGE_BATCH", "Batch Processing", "Data purge batch executed"),
    
    /** Report generation batch */
    REPORT_GENERATION_BATCH(156, "REPORT_GENERATION_BATCH", "Batch Processing", "Report generation batch executed"),
    
    /** Reconciliation batch */
    RECONCILIATION_BATCH(157, "RECONCILIATION_BATCH", "Batch Processing", "Reconciliation batch executed"),
    
    /** Batch checkpoint created */
    BATCH_CHECKPOINT_CREATED(158, "BATCH_CHECKPOINT_CREATED", "Batch Processing", "Batch checkpoint created"),
    
    /** Batch restart from checkpoint */
    BATCH_RESTART(159, "BATCH_RESTART", "Batch Processing", "Batch restarted from checkpoint"),
    
    /** Batch file processing started */
    BATCH_FILE_PROCESSING_START(160, "BATCH_FILE_PROCESSING_START", "Batch Processing", "Batch file processing started"),
    
    /** Batch file processing completed */
    BATCH_FILE_PROCESSING_END(161, "BATCH_FILE_PROCESSING_END", "Batch Processing", "Batch file processing completed"),
    
    /** Batch validation error */
    BATCH_VALIDATION_ERROR(162, "BATCH_VALIDATION_ERROR", "Batch Processing", "Batch validation error occurred"),
    
    /** Batch data transformation */
    BATCH_DATA_TRANSFORMATION(163, "BATCH_DATA_TRANSFORMATION", "Batch Processing", "Batch data transformation executed"),
    
    /** Batch load balancing */
    BATCH_LOAD_BALANCING(164, "BATCH_LOAD_BALANCING", "Batch Processing", "Batch load balancing performed"),
    
    /** Batch performance monitoring */
    BATCH_PERFORMANCE_MONITOR(165, "BATCH_PERFORMANCE_MONITOR", "Batch Processing", "Batch performance monitored"),
    
    /** Batch resource allocation */
    BATCH_RESOURCE_ALLOCATION(166, "BATCH_RESOURCE_ALLOCATION", "Batch Processing", "Batch resource allocation performed"),
    
    /** Batch dependency check */
    BATCH_DEPENDENCY_CHECK(167, "BATCH_DEPENDENCY_CHECK", "Batch Processing", "Batch dependency check performed"),
    
    /** Batch queue management */
    BATCH_QUEUE_MANAGEMENT(168, "BATCH_QUEUE_MANAGEMENT", "Batch Processing", "Batch queue management executed"),
    
    /** Batch scheduling optimization */
    BATCH_SCHEDULING_OPTIMIZATION(169, "BATCH_SCHEDULING_OPTIMIZATION", "Batch Processing", "Batch scheduling optimized"),
    
    /** Batch job priority change */
    BATCH_PRIORITY_CHANGE(170, "BATCH_PRIORITY_CHANGE", "Batch Processing", "Batch job priority changed"),
    
    /** Batch job cancellation */
    BATCH_JOB_CANCELLED(171, "BATCH_JOB_CANCELLED", "Batch Processing", "Batch job cancelled"),
    
    /** Batch job resubmission */
    BATCH_JOB_RESUBMITTED(172, "BATCH_JOB_RESUBMITTED", "Batch Processing", "Batch job resubmitted"),
    
    /** Batch output generation */
    BATCH_OUTPUT_GENERATION(173, "BATCH_OUTPUT_GENERATION", "Batch Processing", "Batch output generated"),
    
    /** Batch cleanup operation */
    BATCH_CLEANUP(174, "BATCH_CLEANUP", "Batch Processing", "Batch cleanup operation performed"),
    
    /** Batch notification sent */
    BATCH_NOTIFICATION_SENT(175, "BATCH_NOTIFICATION_SENT", "Batch Processing", "Batch notification sent"),
    
    /** Batch metrics collection */
    BATCH_METRICS_COLLECTED(176, "BATCH_METRICS_COLLECTED", "Batch Processing", "Batch metrics collected"),
    
    /** Batch log rotation */
    BATCH_LOG_ROTATION(177, "BATCH_LOG_ROTATION", "Batch Processing", "Batch log rotation performed"),
    
    /** Batch capacity planning */
    BATCH_CAPACITY_PLANNING(178, "BATCH_CAPACITY_PLANNING", "Batch Processing", "Batch capacity planning executed"),
    
    /** Batch service level monitoring */
    BATCH_SLA_MONITORING(179, "BATCH_SLA_MONITORING", "Batch Processing", "Batch SLA monitoring performed"),
    
    // ========================================================================
    // SECURITY AND COMPLIANCE EVENTS (180-219) - Regulatory and security monitoring
    // ========================================================================
    
    /** PCI DSS compliance check */
    PCI_DSS_COMPLIANCE_CHECK(180, "PCI_DSS_COMPLIANCE_CHECK", "Security", "PCI DSS compliance check performed"),
    
    /** SOX compliance audit */
    SOX_COMPLIANCE_AUDIT(181, "SOX_COMPLIANCE_AUDIT", "Security", "SOX compliance audit performed"),
    
    /** Data encryption applied */
    DATA_ENCRYPTION_APPLIED(182, "DATA_ENCRYPTION_APPLIED", "Security", "Data encryption applied"),
    
    /** Data decryption performed */
    DATA_DECRYPTION_PERFORMED(183, "DATA_DECRYPTION_PERFORMED", "Security", "Data decryption performed"),
    
    /** Security key rotation */
    SECURITY_KEY_ROTATION(184, "SECURITY_KEY_ROTATION", "Security", "Security key rotation performed"),
    
    /** Suspicious activity detected */
    SUSPICIOUS_ACTIVITY_DETECTED(185, "SUSPICIOUS_ACTIVITY_DETECTED", "Security", "Suspicious activity detected"),
    
    /** Anomaly detection triggered */
    ANOMALY_DETECTION_TRIGGERED(186, "ANOMALY_DETECTION_TRIGGERED", "Security", "Anomaly detection triggered"),
    
    /** Intrusion attempt detected */
    INTRUSION_ATTEMPT_DETECTED(187, "INTRUSION_ATTEMPT_DETECTED", "Security", "Intrusion attempt detected"),
    
    /** Firewall rule updated */
    FIREWALL_RULE_UPDATED(188, "FIREWALL_RULE_UPDATED", "Security", "Firewall rule updated"),
    
    /** Security patch applied */
    SECURITY_PATCH_APPLIED(189, "SECURITY_PATCH_APPLIED", "Security", "Security patch applied"),
    
    /** Vulnerability scan completed */
    VULNERABILITY_SCAN_COMPLETED(190, "VULNERABILITY_SCAN_COMPLETED", "Security", "Vulnerability scan completed"),
    
    /** Penetration test executed */
    PENETRATION_TEST_EXECUTED(191, "PENETRATION_TEST_EXECUTED", "Security", "Penetration test executed"),
    
    /** Security certificate expired */
    SECURITY_CERT_EXPIRED(192, "SECURITY_CERT_EXPIRED", "Security", "Security certificate expired"),
    
    /** Security certificate updated */
    SECURITY_CERT_UPDATED(193, "SECURITY_CERT_UPDATED", "Security", "Security certificate updated"),
    
    /** Access control violation */
    ACCESS_CONTROL_VIOLATION(194, "ACCESS_CONTROL_VIOLATION", "Security", "Access control violation detected"),
    
    /** Data masking applied */
    DATA_MASKING_APPLIED(195, "DATA_MASKING_APPLIED", "Security", "Data masking applied"),
    
    /** Audit trail integrity check */
    AUDIT_TRAIL_INTEGRITY_CHECK(196, "AUDIT_TRAIL_INTEGRITY_CHECK", "Security", "Audit trail integrity check performed"),
    
    /** Regulatory reporting submitted */
    REGULATORY_REPORTING_SUBMITTED(197, "REGULATORY_REPORTING_SUBMITTED", "Security", "Regulatory report submitted"),
    
    /** Privacy policy compliance check */
    PRIVACY_POLICY_COMPLIANCE(198, "PRIVACY_POLICY_COMPLIANCE", "Security", "Privacy policy compliance checked"),
    
    /** Data retention policy applied */
    DATA_RETENTION_POLICY_APPLIED(199, "DATA_RETENTION_POLICY_APPLIED", "Security", "Data retention policy applied"),
    
    /** Incident response activated */
    INCIDENT_RESPONSE_ACTIVATED(200, "INCIDENT_RESPONSE_ACTIVATED", "Security", "Incident response activated"),
    
    /** Forensic analysis initiated */
    FORENSIC_ANALYSIS_INITIATED(201, "FORENSIC_ANALYSIS_INITIATED", "Security", "Forensic analysis initiated"),
    
    /** Security event correlation */
    SECURITY_EVENT_CORRELATION(202, "SECURITY_EVENT_CORRELATION", "Security", "Security event correlation performed"),
    
    /** Threat intelligence updated */
    THREAT_INTELLIGENCE_UPDATED(203, "THREAT_INTELLIGENCE_UPDATED", "Security", "Threat intelligence updated"),
    
    /** Risk score calculated */
    RISK_SCORE_CALCULATED(204, "RISK_SCORE_CALCULATED", "Security", "Risk score calculated"),
    
    /** Compliance violation detected */
    COMPLIANCE_VIOLATION_DETECTED(205, "COMPLIANCE_VIOLATION_DETECTED", "Security", "Compliance violation detected"),
    
    /** Data loss prevention triggered */
    DLP_TRIGGERED(206, "DLP_TRIGGERED", "Security", "Data loss prevention triggered"),
    
    /** Security awareness training completed */
    SECURITY_TRAINING_COMPLETED(207, "SECURITY_TRAINING_COMPLETED", "Security", "Security awareness training completed"),
    
    /** Security policy violation */
    SECURITY_POLICY_VIOLATION(208, "SECURITY_POLICY_VIOLATION", "Security", "Security policy violation detected"),
    
    /** Privilege abuse detected */
    PRIVILEGE_ABUSE_DETECTED(209, "PRIVILEGE_ABUSE_DETECTED", "Security", "Privilege abuse detected"),
    
    /** Unauthorized access attempt */
    UNAUTHORIZED_ACCESS_ATTEMPT(210, "UNAUTHORIZED_ACCESS_ATTEMPT", "Security", "Unauthorized access attempt detected"),
    
    /** Security baseline deviation */
    SECURITY_BASELINE_DEVIATION(211, "SECURITY_BASELINE_DEVIATION", "Security", "Security baseline deviation detected"),
    
    /** Insider threat detected */
    INSIDER_THREAT_DETECTED(212, "INSIDER_THREAT_DETECTED", "Security", "Insider threat detected"),
    
    /** Malware detection */
    MALWARE_DETECTED(213, "MALWARE_DETECTED", "Security", "Malware detected"),
    
    /** Zero-day exploit detected */
    ZERO_DAY_EXPLOIT_DETECTED(214, "ZERO_DAY_EXPLOIT_DETECTED", "Security", "Zero-day exploit detected"),
    
    /** Security orchestration executed */
    SECURITY_ORCHESTRATION_EXECUTED(215, "SECURITY_ORCHESTRATION_EXECUTED", "Security", "Security orchestration executed"),
    
    /** Automated response triggered */
    AUTOMATED_RESPONSE_TRIGGERED(216, "AUTOMATED_RESPONSE_TRIGGERED", "Security", "Automated response triggered"),
    
    /** Security metrics collected */
    SECURITY_METRICS_COLLECTED(217, "SECURITY_METRICS_COLLECTED", "Security", "Security metrics collected"),
    
    /** Compliance dashboard updated */
    COMPLIANCE_DASHBOARD_UPDATED(218, "COMPLIANCE_DASHBOARD_UPDATED", "Security", "Compliance dashboard updated"),
    
    /** Security posture assessment */
    SECURITY_POSTURE_ASSESSMENT(219, "SECURITY_POSTURE_ASSESSMENT", "Security", "Security posture assessment completed"),
    
    // ========================================================================
    // INTEGRATION AND INTERFACE EVENTS (220-255) - External system integration
    // ========================================================================
    
    /** External API call initiated */
    EXTERNAL_API_CALL_INITIATED(220, "EXTERNAL_API_CALL_INITIATED", "Integration", "External API call initiated"),
    
    /** External API call completed */
    EXTERNAL_API_CALL_COMPLETED(221, "EXTERNAL_API_CALL_COMPLETED", "Integration", "External API call completed"),
    
    /** External API call failed */
    EXTERNAL_API_CALL_FAILED(222, "EXTERNAL_API_CALL_FAILED", "Integration", "External API call failed"),
    
    /** Payment network interface accessed */
    PAYMENT_NETWORK_INTERFACE(223, "PAYMENT_NETWORK_INTERFACE", "Integration", "Payment network interface accessed"),
    
    /** Core banking system interface */
    CORE_BANKING_INTERFACE(224, "CORE_BANKING_INTERFACE", "Integration", "Core banking system interface accessed"),
    
    /** Regulatory reporting interface */
    REGULATORY_REPORTING_INTERFACE(225, "REGULATORY_REPORTING_INTERFACE", "Integration", "Regulatory reporting interface accessed"),
    
    /** File transfer initiated */
    FILE_TRANSFER_INITIATED(226, "FILE_TRANSFER_INITIATED", "Integration", "File transfer initiated"),
    
    /** File transfer completed */
    FILE_TRANSFER_COMPLETED(227, "FILE_TRANSFER_COMPLETED", "Integration", "File transfer completed"),
    
    /** File transfer failed */
    FILE_TRANSFER_FAILED(228, "FILE_TRANSFER_FAILED", "Integration", "File transfer failed"),
    
    /** Message queue operation */
    MESSAGE_QUEUE_OPERATION(229, "MESSAGE_QUEUE_OPERATION", "Integration", "Message queue operation performed"),
    
    /** Web service invocation */
    WEB_SERVICE_INVOCATION(230, "WEB_SERVICE_INVOCATION", "Integration", "Web service invoked"),
    
    /** Database synchronization */
    DATABASE_SYNCHRONIZATION(231, "DATABASE_SYNCHRONIZATION", "Integration", "Database synchronization performed"),
    
    /** Third-party service integration */
    THIRD_PARTY_SERVICE_INTEGRATION(232, "THIRD_PARTY_SERVICE_INTEGRATION", "Integration", "Third-party service integrated"),
    
    /** Credit bureau interface */
    CREDIT_BUREAU_INTERFACE(233, "CREDIT_BUREAU_INTERFACE", "Integration", "Credit bureau interface accessed"),
    
    /** Fraud detection service call */
    FRAUD_DETECTION_SERVICE_CALL(234, "FRAUD_DETECTION_SERVICE_CALL", "Integration", "Fraud detection service called"),
    
    /** Document management system access */
    DOCUMENT_MGMT_SYSTEM_ACCESS(235, "DOCUMENT_MGMT_SYSTEM_ACCESS", "Integration", "Document management system accessed"),
    
    /** Email service integration */
    EMAIL_SERVICE_INTEGRATION(236, "EMAIL_SERVICE_INTEGRATION", "Integration", "Email service integration performed"),
    
    /** SMS service integration */
    SMS_SERVICE_INTEGRATION(237, "SMS_SERVICE_INTEGRATION", "Integration", "SMS service integration performed"),
    
    /** Mobile app integration */
    MOBILE_APP_INTEGRATION(238, "MOBILE_APP_INTEGRATION", "Integration", "Mobile app integration performed"),
    
    /** ATM network interface */
    ATM_NETWORK_INTERFACE(239, "ATM_NETWORK_INTERFACE", "Integration", "ATM network interface accessed"),
    
    /** POS system interface */
    POS_SYSTEM_INTERFACE(240, "POS_SYSTEM_INTERFACE", "Integration", "POS system interface accessed"),
    
    /** Card network authorization */
    CARD_NETWORK_AUTHORIZATION(241, "CARD_NETWORK_AUTHORIZATION", "Integration", "Card network authorization performed"),
    
    /** Settlement system interface */
    SETTLEMENT_SYSTEM_INTERFACE(242, "SETTLEMENT_SYSTEM_INTERFACE", "Integration", "Settlement system interface accessed"),
    
    /** Clearing system interface */
    CLEARING_SYSTEM_INTERFACE(243, "CLEARING_SYSTEM_INTERFACE", "Integration", "Clearing system interface accessed"),
    
    /** Risk management system call */
    RISK_MGMT_SYSTEM_CALL(244, "RISK_MGMT_SYSTEM_CALL", "Integration", "Risk management system called"),
    
    /** Customer service system integration */
    CUSTOMER_SERVICE_INTEGRATION(245, "CUSTOMER_SERVICE_INTEGRATION", "Integration", "Customer service system integrated"),
    
    /** Marketing system integration */
    MARKETING_SYSTEM_INTEGRATION(246, "MARKETING_SYSTEM_INTEGRATION", "Integration", "Marketing system integrated"),
    
    /** Analytics platform integration */
    ANALYTICS_PLATFORM_INTEGRATION(247, "ANALYTICS_PLATFORM_INTEGRATION", "Integration", "Analytics platform integrated"),
    
    /** Blockchain network interaction */
    BLOCKCHAIN_NETWORK_INTERACTION(248, "BLOCKCHAIN_NETWORK_INTERACTION", "Integration", "Blockchain network interaction performed"),
    
    /** Cloud service integration */
    CLOUD_SERVICE_INTEGRATION(249, "CLOUD_SERVICE_INTEGRATION", "Integration", "Cloud service integration performed"),
    
    /** Microservice communication */
    MICROSERVICE_COMMUNICATION(250, "MICROSERVICE_COMMUNICATION", "Integration", "Microservice communication performed"),
    
    /** Event streaming platform access */
    EVENT_STREAMING_PLATFORM(251, "EVENT_STREAMING_PLATFORM", "Integration", "Event streaming platform accessed"),
    
    /** Data warehouse integration */
    DATA_WAREHOUSE_INTEGRATION(252, "DATA_WAREHOUSE_INTEGRATION", "Integration", "Data warehouse integration performed"),
    
    /** Business intelligence system access */
    BI_SYSTEM_ACCESS(253, "BI_SYSTEM_ACCESS", "Integration", "Business intelligence system accessed"),
    
    /** Legacy system bridge operation */
    LEGACY_SYSTEM_BRIDGE(254, "LEGACY_SYSTEM_BRIDGE", "Integration", "Legacy system bridge operation performed"),
    
    /** Custom integration endpoint */
    CUSTOM_INTEGRATION_ENDPOINT(255, "CUSTOM_INTEGRATION_ENDPOINT", "Integration", "Custom integration endpoint accessed");

    // ========================================================================
    // ENUMERATION PROPERTIES AND METHODS
    // ========================================================================
    
    /** SMF-compatible event type code (0-255) */
    private final int eventCode;
    
    /** Unique event type identifier */
    private final String eventType;
    
    /** Event category for grouping and filtering */
    private final String category;
    
    /** Human-readable event description */
    private final String description;
    
    /**
     * Constructor for AuditEventType enumeration.
     * 
     * @param eventCode SMF-compatible numeric code (0-255)
     * @param eventType Unique event type identifier
     * @param category Event category for grouping
     * @param description Human-readable description
     */
    AuditEventType(int eventCode, String eventType, String category, String description) {
        this.eventCode = eventCode;
        this.eventType = eventType;
        this.category = category;
        this.description = description;
    }
    
    /**
     * Gets SMF-compatible numeric event code.
     * 
     * @return Event code (0-255)
     */
    public int getEventCode() {
        return eventCode;
    }
    
    /**
     * Gets unique event type identifier.
     * 
     * @return Event type string
     */
    public String getEventType() {
        return eventType;
    }
    
    /**
     * Gets event category for grouping and filtering.
     * 
     * @return Category string
     */
    public String getCategory() {
        return category;
    }
    
    /**
     * Gets human-readable event description.
     * 
     * @return Description string
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Determines if this event type represents a security-sensitive operation.
     * 
     * @return true if event is security-sensitive
     */
    public boolean isSecuritySensitive() {
        return category.equals("Authentication") || 
               category.equals("Security") || 
               eventCode >= 180 && eventCode <= 219;
    }
    
    /**
     * Determines if this event type represents a compliance-related operation.
     * 
     * @return true if event is compliance-related
     */
    public boolean isComplianceRelated() {
        return isSecuritySensitive() || 
               eventType.contains("COMPLIANCE") || 
               eventType.contains("REGULATORY") ||
               eventType.contains("PCI_DSS") ||
               eventType.contains("SOX");
    }
    
    /**
     * Determines if this event type requires immediate alerting.
     * 
     * @return true if event requires immediate alert
     */
    public boolean requiresImmediateAlert() {
        return eventType.contains("FAILURE") || 
               eventType.contains("VIOLATION") || 
               eventType.contains("SUSPICIOUS") || 
               eventType.contains("FRAUD") || 
               eventType.contains("INTRUSION") ||
               eventType.contains("MALWARE") ||
               eventType.contains("BREACH");
    }
    
    /**
     * Gets event type by numeric code.
     * 
     * @param eventCode SMF-compatible numeric code
     * @return AuditEventType or null if not found
     */
    public static AuditEventType getByEventCode(int eventCode) {
        for (AuditEventType type : values()) {
            if (type.getEventCode() == eventCode) {
                return type;
            }
        }
        return null;
    }
    
    /**
     * Gets all event types for a specific category.
     * 
     * @param category Event category
     * @return Array of AuditEventType for the category
     */
    public static AuditEventType[] getByCategory(String category) {
        return java.util.Arrays.stream(values())
                .filter(type -> type.getCategory().equals(category))
                .toArray(AuditEventType[]::new);
    }
    
    /**
     * Returns string representation of audit event type.
     * 
     * @return Formatted string with code, type, category, and description
     */
    @Override
    public String toString() {
        return String.format("[%03d] %s (%s): %s", 
                eventCode, eventType, category, description);
    }
}
/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 * 
 * Session Management Constants for CardDemo Application
 * 
 * This utility class defines constants for Redis session management configuration,
 * providing centralized session-related parameters that replace CICS COMMAREA
 * pseudo-conversational state management patterns.
 * 
 * Constants support:
 * - Session timeout policies matching CICS session behavior
 * - Redis namespace organization for session data isolation
 * - Session attribute keys for user context preservation
 * - Cache configuration parameters for performance optimization
 */
package com.carddemo.util;

/**
 * Session Management Constants
 * 
 * Defines constants for Redis-backed session management that replaces
 * CICS COMMAREA pseudo-conversational state patterns with modern
 * distributed session storage.
 * 
 * Session Structure Mapping:
 * - CDEMO-GENERAL-INFO → Session user context attributes
 * - CDEMO-CUSTOMER-INFO → Session customer context attributes  
 * - CDEMO-ACCOUNT-INFO → Session account context attributes
 * - CDEMO-CARD-INFO → Session card context attributes
 * - CDEMO-MORE-INFO → Session navigation state attributes
 * 
 * Based on COCOM01Y.cpy COMMAREA structure analysis.
 */
public final class SessionConstants {

    private SessionConstants() {
        // Utility class - prevent instantiation
    }

    // ========================================
    // SESSION TIMEOUT CONFIGURATION
    // ========================================
    
    /**
     * Default session timeout in seconds (30 minutes)
     * Matches typical CICS terminal session timeout behavior
     */
    public static final int DEFAULT_SESSION_TIMEOUT_SECONDS = 1800; // 30 minutes

    /**
     * Extended session timeout for administrative users (60 minutes)
     * Provides longer session duration for complex administrative tasks
     */
    public static final int ADMIN_SESSION_TIMEOUT_SECONDS = 3600; // 60 minutes

    /**
     * Maximum allowed session timeout (4 hours)
     * Upper bound for session duration to ensure security compliance
     */
    public static final int MAX_SESSION_TIMEOUT_SECONDS = 14400; // 4 hours

    // ========================================
    // REDIS NAMESPACE CONFIGURATION
    // ========================================
    
    /**
     * Redis namespace for Spring Session data
     * Isolates CardDemo session data from other applications
     */
    public static final String REDIS_SESSION_NAMESPACE = "spring:session:carddemo";

    /**
     * Redis key prefix for application-specific session attributes
     * Used for custom session data beyond Spring Session framework
     */
    public static final String SESSION_ATTRIBUTE_PREFIX = "carddemo:session:attr";

    /**
     * Redis key prefix for user context cache
     * Stores user authentication and profile information
     */
    public static final String USER_CONTEXT_PREFIX = "carddemo:user:context";

    // ========================================
    // SESSION ATTRIBUTE KEYS
    // ========================================
    
    /**
     * Session attribute key for user authentication context
     * Replaces CDEMO-USER-ID and CDEMO-USER-TYPE from COMMAREA
     */
    public static final String USER_CONTEXT_KEY = "USER_CONTEXT";

    /**
     * Session attribute key for current program context
     * Replaces CDEMO-FROM-PROGRAM and CDEMO-TO-PROGRAM navigation
     */
    public static final String PROGRAM_CONTEXT_KEY = "PROGRAM_CONTEXT";

    /**
     * Session attribute key for customer information
     * Replaces CDEMO-CUSTOMER-INFO structure from COMMAREA
     */
    public static final String CUSTOMER_CONTEXT_KEY = "CUSTOMER_CONTEXT";

    /**
     * Session attribute key for account information
     * Replaces CDEMO-ACCOUNT-INFO structure from COMMAREA
     */
    public static final String ACCOUNT_CONTEXT_KEY = "ACCOUNT_CONTEXT";

    /**
     * Session attribute key for card information
     * Replaces CDEMO-CARD-INFO structure from COMMAREA
     */
    public static final String CARD_CONTEXT_KEY = "CARD_CONTEXT";

    /**
     * Session attribute key for navigation state
     * Replaces CDEMO-LAST-MAP and CDEMO-LAST-MAPSET for screen navigation
     */
    public static final String NAVIGATION_CONTEXT_KEY = "NAVIGATION_CONTEXT";

    /**
     * Session attribute key for transaction workflow state
     * Supports multi-step transaction processing state preservation
     */
    public static final String TRANSACTION_CONTEXT_KEY = "TRANSACTION_CONTEXT";

    /**
     * Session attribute key for form validation state
     * Preserves validation results across screen transitions
     */
    public static final String VALIDATION_CONTEXT_KEY = "VALIDATION_CONTEXT";

    /**
     * Session attribute key for search criteria and results
     * Maintains search state for paginated result navigation
     */
    public static final String SEARCH_CONTEXT_KEY = "SEARCH_CONTEXT";

    /**
     * Session attribute key for error message context
     * Preserves error state for proper error display and handling
     */
    public static final String ERROR_CONTEXT_KEY = "ERROR_CONTEXT";

    // ========================================
    // USER ROLE CONSTANTS
    // ========================================
    
    /**
     * Administrative user role code
     * Maps to CDEMO-USRTYP-ADMIN from COCOM01Y.cpy (VALUE 'A')
     */
    public static final String USER_ROLE_ADMIN = "A";

    /**
     * Regular user role code  
     * Maps to CDEMO-USRTYP-USER from COCOM01Y.cpy (VALUE 'U')
     */
    public static final String USER_ROLE_USER = "U";

    // ========================================
    // PROGRAM CONTEXT CONSTANTS
    // ========================================
    
    /**
     * Program context value for initial entry
     * Maps to CDEMO-PGM-ENTER from COCOM01Y.cpy (VALUE 0)
     */
    public static final int PROGRAM_CONTEXT_ENTER = 0;

    /**
     * Program context value for re-entry
     * Maps to CDEMO-PGM-REENTER from COCOM01Y.cpy (VALUE 1)  
     */
    public static final int PROGRAM_CONTEXT_REENTER = 1;

    // ========================================
    // CACHE CONFIGURATION CONSTANTS
    // ========================================
    
    /**
     * Default cache TTL in minutes for reference data
     * Balances data freshness with performance optimization
     */
    public static final int REFERENCE_DATA_CACHE_TTL_MINUTES = 240; // 4 hours

    /**
     * User profile cache TTL in minutes
     * Optimizes user authentication and profile lookups
     */
    public static final int USER_PROFILE_CACHE_TTL_MINUTES = 240; // 4 hours

    /**
     * Transaction type cache TTL in minutes
     * Caches transaction type lookup data for performance
     */
    public static final int TRANSACTION_TYPE_CACHE_TTL_MINUTES = 480; // 8 hours

    /**
     * Account balance cache TTL in minutes
     * Short TTL for real-time balance accuracy
     */
    public static final int ACCOUNT_BALANCE_CACHE_TTL_MINUTES = 15;

    /**
     * Session cleanup interval in seconds
     * Frequency for expired session cleanup operations
     */
    public static final int SESSION_CLEANUP_INTERVAL_SECONDS = 60; // 1 minute

    // ========================================
    // SESSION VALIDATION CONSTANTS
    // ========================================
    
    /**
     * Maximum number of concurrent sessions per user
     * Prevents session proliferation and supports security policies
     */
    public static final int MAX_CONCURRENT_SESSIONS_PER_USER = 3;

    /**
     * Session validation interval in seconds
     * Frequency for session health checks and validation
     */
    public static final int SESSION_VALIDATION_INTERVAL_SECONDS = 300; // 5 minutes

    /**
     * Maximum session inactivity warning threshold in seconds
     * Time before warning user of impending session timeout
     */
    public static final int SESSION_INACTIVITY_WARNING_SECONDS = 300; // 5 minutes

    // ========================================
    // ERROR HANDLING CONSTANTS
    // ========================================
    
    /**
     * Session not found error code
     * Used when session has expired or is invalid
     */
    public static final String ERROR_SESSION_NOT_FOUND = "SESSION_NOT_FOUND";

    /**
     * Session timeout error code
     * Used when session has exceeded maximum inactivity time
     */
    public static final String ERROR_SESSION_TIMEOUT = "SESSION_TIMEOUT";

    /**
     * Invalid session context error code
     * Used when session data is corrupted or invalid
     */
    public static final String ERROR_INVALID_SESSION_CONTEXT = "INVALID_SESSION_CONTEXT";

    /**
     * Session concurrency limit exceeded error code
     * Used when user attempts to exceed maximum concurrent sessions
     */
    public static final String ERROR_SESSION_LIMIT_EXCEEDED = "SESSION_LIMIT_EXCEEDED";

    // ========================================
    // AUDIT LOGGING CONSTANTS
    // ========================================
    
    /**
     * Session audit event type for session creation
     */
    public static final String AUDIT_SESSION_CREATED = "SESSION_CREATED";

    /**
     * Session audit event type for session destruction
     */
    public static final String AUDIT_SESSION_DESTROYED = "SESSION_DESTROYED";

    /**
     * Session audit event type for session timeout
     */
    public static final String AUDIT_SESSION_TIMEOUT = "SESSION_TIMEOUT";

    /**
     * Session audit event type for session validation failure
     */
    public static final String AUDIT_SESSION_VALIDATION_FAILED = "SESSION_VALIDATION_FAILED";

    /**
     * Session audit event type for concurrent session limit exceeded
     */
    public static final String AUDIT_SESSION_LIMIT_EXCEEDED = "SESSION_LIMIT_EXCEEDED";
}
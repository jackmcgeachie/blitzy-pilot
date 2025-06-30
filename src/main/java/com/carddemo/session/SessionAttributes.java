package com.carddemo.session;

/**
 * Constants class defining standardized session attribute names and Redis key patterns
 * for consistent session management across the CardDemo application.
 * 
 * This class provides type-safe attribute keys for user context, navigation state,
 * and business data storage, ensuring consistent session key naming conventions
 * and maintaining Redis key prefix standards.
 * 
 * Maps COBOL COMMAREA structure from COCOM01Y.cpy to Spring Session attributes:
 * - CDEMO-GENERAL-INFO -> General session context
 * - CDEMO-CUSTOMER-INFO -> Customer session data  
 * - CDEMO-ACCOUNT-INFO -> Account session data
 * - CDEMO-CARD-INFO -> Card session data
 * - CDEMO-MORE-INFO -> Navigation session data
 * 
 * @author CardDemo Development Team
 * @since 1.0
 */
public final class SessionAttributes {

    // Prevent instantiation
    private SessionAttributes() {
        throw new UnsupportedOperationException("Utility class - cannot be instantiated");
    }

    // ========================================================================
    // REDIS KEY PATTERNS AND PREFIXES
    // ========================================================================

    /**
     * Base Redis key prefix for CardDemo Spring Session storage.
     * Matches spring:session:carddemo: pattern per Security Architecture specification.
     */
    public static final String REDIS_KEY_PREFIX = "spring:session:carddemo:";
    
    /**
     * Redis namespace for session indexes.
     */
    public static final String REDIS_SESSION_INDEX_PREFIX = REDIS_KEY_PREFIX + "sessions:";
    
    /**
     * Redis namespace for session expiration tracking.
     */
    public static final String REDIS_EXPIRY_PREFIX = REDIS_KEY_PREFIX + "sessions:expires:";
    
    /**
     * Redis namespace for principal-to-session mappings.
     */
    public static final String REDIS_PRINCIPAL_PREFIX = REDIS_KEY_PREFIX + "sessions:principal:";

    // ========================================================================
    // SESSION TIMEOUT AND CONFIGURATION CONSTANTS
    // ========================================================================

    /**
     * Default session timeout in seconds (30 minutes).
     * Per Technical Decisions 5.3.2 - configurable session timeouts matching CICS terminal session policies.
     */
    public static final int DEFAULT_SESSION_TIMEOUT_SECONDS = 1800; // 30 minutes
    
    /**
     * Maximum session timeout in seconds (8 hours).
     * Aligned with JWT token expiration per Security Architecture.
     */
    public static final int MAX_SESSION_TIMEOUT_SECONDS = 28800; // 8 hours
    
    /**
     * Minimum session timeout in seconds (5 minutes).
     */
    public static final int MIN_SESSION_TIMEOUT_SECONDS = 300; // 5 minutes
    
    /**
     * Session cleanup interval in seconds (5 minutes).
     */
    public static final int SESSION_CLEANUP_INTERVAL_SECONDS = 300;

    // ========================================================================
    // GENERAL SESSION CONTEXT ATTRIBUTES
    // Maps to CDEMO-GENERAL-INFO from COCOM01Y.cpy
    // ========================================================================

    /**
     * Session attribute key for source transaction ID.
     * Maps to CDEMO-FROM-TRANID PIC X(04).
     */
    public static final String FROM_TRANSACTION_ID = "fromTransactionId";
    
    /**
     * Session attribute key for source program name.
     * Maps to CDEMO-FROM-PROGRAM PIC X(08).
     */
    public static final String FROM_PROGRAM = "fromProgram";
    
    /**
     * Session attribute key for target transaction ID.
     * Maps to CDEMO-TO-TRANID PIC X(04).
     */
    public static final String TO_TRANSACTION_ID = "toTransactionId";
    
    /**
     * Session attribute key for target program name.
     * Maps to CDEMO-TO-PROGRAM PIC X(08).
     */
    public static final String TO_PROGRAM = "toProgram";
    
    /**
     * Session attribute key for authenticated user ID.
     * Maps to CDEMO-USER-ID PIC X(08).
     */
    public static final String USER_ID = "userId";
    
    /**
     * Session attribute key for user type/role.
     * Maps to CDEMO-USER-TYPE PIC X(01) with 88-levels CDEMO-USRTYP-ADMIN/USER.
     * Values: 'A' (Admin), 'U' (User)
     */
    public static final String USER_TYPE = "userType";
    
    /**
     * Session attribute key for program context indicator.
     * Maps to CDEMO-PGM-CONTEXT PIC 9(01) with 88-levels CDEMO-PGM-ENTER/REENTER.
     * Values: 0 (Enter), 1 (Re-enter)
     */
    public static final String PROGRAM_CONTEXT = "programContext";

    // ========================================================================
    // CUSTOMER SESSION DATA ATTRIBUTES
    // Maps to CDEMO-CUSTOMER-INFO from COCOM01Y.cpy
    // ========================================================================

    /**
     * Session attribute key for customer ID.
     * Maps to CDEMO-CUST-ID PIC 9(09).
     */
    public static final String CUSTOMER_ID = "customerId";
    
    /**
     * Session attribute key for customer first name.
     * Maps to CDEMO-CUST-FNAME PIC X(25).
     */
    public static final String CUSTOMER_FIRST_NAME = "customerFirstName";
    
    /**
     * Session attribute key for customer middle name.
     * Maps to CDEMO-CUST-MNAME PIC X(25).
     */
    public static final String CUSTOMER_MIDDLE_NAME = "customerMiddleName";
    
    /**
     * Session attribute key for customer last name.
     * Maps to CDEMO-CUST-LNAME PIC X(25).
     */
    public static final String CUSTOMER_LAST_NAME = "customerLastName";

    // ========================================================================
    // ACCOUNT SESSION DATA ATTRIBUTES
    // Maps to CDEMO-ACCOUNT-INFO from COCOM01Y.cpy
    // ========================================================================

    /**
     * Session attribute key for account ID.
     * Maps to CDEMO-ACCT-ID PIC 9(11).
     */
    public static final String ACCOUNT_ID = "accountId";
    
    /**
     * Session attribute key for account status.
     * Maps to CDEMO-ACCT-STATUS PIC X(01).
     */
    public static final String ACCOUNT_STATUS = "accountStatus";

    // ========================================================================
    // CARD SESSION DATA ATTRIBUTES
    // Maps to CDEMO-CARD-INFO from COCOM01Y.cpy
    // ========================================================================

    /**
     * Session attribute key for card number.
     * Maps to CDEMO-CARD-NUM PIC 9(16).
     */
    public static final String CARD_NUMBER = "cardNumber";

    // ========================================================================
    // NAVIGATION SESSION DATA ATTRIBUTES
    // Maps to CDEMO-MORE-INFO from COCOM01Y.cpy
    // ========================================================================

    /**
     * Session attribute key for last displayed map/screen.
     * Maps to CDEMO-LAST-MAP PIC X(7).
     */
    public static final String LAST_MAP = "lastMap";
    
    /**
     * Session attribute key for last mapset.
     * Maps to CDEMO-LAST-MAPSET PIC X(7).
     */
    public static final String LAST_MAPSET = "lastMapset";

    // ========================================================================
    // SPRING SECURITY INTEGRATION ATTRIBUTES
    // ========================================================================

    /**
     * Session attribute key for JWT token.
     * Stores the current authentication token for the session.
     */
    public static final String JWT_TOKEN = "jwtToken";
    
    /**
     * Session attribute key for JWT token expiration timestamp.
     */
    public static final String JWT_TOKEN_EXPIRES_AT = "jwtTokenExpiresAt";
    
    /**
     * Session attribute key for Spring Security authorities.
     * Contains granted authorities for the authenticated user.
     */
    public static final String SPRING_SECURITY_AUTHORITIES = "springSecurityAuthorities";
    
    /**
     * Session attribute key for authentication timestamp.
     */
    public static final String AUTHENTICATION_TIMESTAMP = "authenticationTimestamp";

    // ========================================================================
    // TRANSACTION AND WORKFLOW STATE ATTRIBUTES
    // ========================================================================

    /**
     * Session attribute key for current transaction state.
     * Maintains multi-step transaction context.
     */
    public static final String TRANSACTION_STATE = "transactionState";
    
    /**
     * Session attribute key for form validation errors.
     * Stores validation messages across redirects.
     */
    public static final String FORM_ERRORS = "formErrors";
    
    /**
     * Session attribute key for pagination state.
     * Maintains current page, page size, and sort order.
     */
    public static final String PAGINATION_STATE = "paginationState";
    
    /**
     * Session attribute key for search criteria.
     * Preserves search filters across requests.
     */
    public static final String SEARCH_CRITERIA = "searchCriteria";
    
    /**
     * Session attribute key for breadcrumb navigation.
     * Maintains navigation path for back button functionality.
     */
    public static final String BREADCRUMB_NAVIGATION = "breadcrumbNavigation";

    // ========================================================================
    // AUDIT AND MONITORING ATTRIBUTES
    // ========================================================================

    /**
     * Session attribute key for session creation timestamp.
     */
    public static final String SESSION_CREATED_AT = "sessionCreatedAt";
    
    /**
     * Session attribute key for last activity timestamp.
     */
    public static final String LAST_ACTIVITY_TIMESTAMP = "lastActivityTimestamp";
    
    /**
     * Session attribute key for client IP address.
     */
    public static final String CLIENT_IP_ADDRESS = "clientIpAddress";
    
    /**
     * Session attribute key for user agent string.
     */
    public static final String USER_AGENT = "userAgent";
    
    /**
     * Session attribute key for session event tracking.
     * Maintains audit trail of session events.
     */
    public static final String SESSION_EVENTS = "sessionEvents";

    // ========================================================================
    // SESSION EVENT TYPE CONSTANTS
    // ========================================================================

    /**
     * Session event type for session creation.
     */
    public static final String EVENT_SESSION_CREATED = "SESSION_CREATED";
    
    /**
     * Session event type for user authentication.
     */
    public static final String EVENT_USER_AUTHENTICATED = "USER_AUTHENTICATED";
    
    /**
     * Session event type for user logout.
     */
    public static final String EVENT_USER_LOGOUT = "USER_LOGOUT";
    
    /**
     * Session event type for session timeout.
     */
    public static final String EVENT_SESSION_TIMEOUT = "SESSION_TIMEOUT";
    
    /**
     * Session event type for session invalidation.
     */
    public static final String EVENT_SESSION_INVALIDATED = "SESSION_INVALIDATED";
    
    /**
     * Session event type for navigation between screens.
     */
    public static final String EVENT_SCREEN_NAVIGATION = "SCREEN_NAVIGATION";
    
    /**
     * Session event type for transaction initiation.
     */
    public static final String EVENT_TRANSACTION_STARTED = "TRANSACTION_STARTED";
    
    /**
     * Session event type for transaction completion.
     */
    public static final String EVENT_TRANSACTION_COMPLETED = "TRANSACTION_COMPLETED";

    // ========================================================================
    // USER TYPE CONSTANTS
    // Maps to COBOL 88-level conditions from COCOM01Y.cpy
    // ========================================================================

    /**
     * User type constant for administrative users.
     * Maps to CDEMO-USRTYP-ADMIN VALUE 'A'.
     */
    public static final String USER_TYPE_ADMIN = "A";
    
    /**
     * User type constant for regular users.
     * Maps to CDEMO-USRTYP-USER VALUE 'U'.
     */
    public static final String USER_TYPE_USER = "U";

    // ========================================================================
    // PROGRAM CONTEXT CONSTANTS
    // Maps to COBOL 88-level conditions from COCOM01Y.cpy
    // ========================================================================

    /**
     * Program context constant for initial program entry.
     * Maps to CDEMO-PGM-ENTER VALUE 0.
     */
    public static final int PROGRAM_CONTEXT_ENTER = 0;
    
    /**
     * Program context constant for program re-entry.
     * Maps to CDEMO-PGM-REENTER VALUE 1.
     */
    public static final int PROGRAM_CONTEXT_REENTER = 1;

    // ========================================================================
    // SESSION SCOPE CONSTANTS
    // ========================================================================

    /**
     * Session scope for user-specific data.
     */
    public static final String SCOPE_USER = "user";
    
    /**
     * Session scope for transaction-specific data.
     */
    public static final String SCOPE_TRANSACTION = "transaction";
    
    /**
     * Session scope for navigation-specific data.
     */
    public static final String SCOPE_NAVIGATION = "navigation";
    
    /**
     * Session scope for temporary data.
     */
    public static final String SCOPE_TEMPORARY = "temporary";

    // ========================================================================
    // SESSION ATTRIBUTE VALIDATION PATTERNS
    // ========================================================================

    /**
     * Regex pattern for validating user ID format.
     * Must be 1-8 alphanumeric characters.
     */
    public static final String USER_ID_PATTERN = "^[A-Za-z0-9]{1,8}$";
    
    /**
     * Regex pattern for validating transaction ID format.
     * Must be exactly 4 alphanumeric characters.
     */
    public static final String TRANSACTION_ID_PATTERN = "^[A-Za-z0-9]{4}$";
    
    /**
     * Regex pattern for validating program name format.
     * Must be 1-8 alphanumeric characters.
     */
    public static final String PROGRAM_NAME_PATTERN = "^[A-Za-z0-9]{1,8}$";
    
    /**
     * Regex pattern for validating map name format.
     * Must be 1-7 alphanumeric characters.
     */
    public static final String MAP_NAME_PATTERN = "^[A-Za-z0-9]{1,7}$";
}
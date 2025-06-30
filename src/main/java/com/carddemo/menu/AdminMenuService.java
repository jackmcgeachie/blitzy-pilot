/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.menu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Administrative Menu Service implementing Spring Boot REST endpoints that transform 
 * COADM01C COBOL program into modern administrative operations management.
 * 
 * <p><strong>Legacy System Migration:</strong></p>
 * <p>This service replaces the COADM01C.cbl COBOL program with modern Spring Security-based 
 * administrative menu functionality while maintaining identical business logic flow for 
 * admin user navigation and preserving 1-to-1 compatibility with RACF administrative 
 * user type validation patterns.</p>
 * 
 * <p><strong>COBOL Program Reference:</strong> COADM01C.cbl - Admin Menu for Admin users</p>
 * 
 * <p><strong>Key Migration Points:</strong></p>
 * <ul>
 *   <li>CICS CA00 transaction → Spring Boot REST endpoints with @PreAuthorize admin-only access</li>
 *   <li>4 admin menu options from COADM02Y.cpy → RESTful user management endpoint routing</li>
 *   <li>RACF admin type validation → Spring Security ROLE_ADMIN authorization enforcement</li>
 *   <li>CICS XCTL program transfers → HTTP redirect responses to user management endpoints</li>
 *   <li>COMMAREA navigation state → Redis-backed session management with admin context</li>
 *   <li>BMS COADM1A screen → AdminMenu.jsx React component with 4 admin operation options</li>
 * </ul>
 * 
 * <p><strong>Administrative Operations Architecture:</strong></p>
 * <ul>
 *   <li>User List (Security) - COUSR00C equivalent → /admin/users endpoint routing</li>
 *   <li>User Add (Security) - COUSR01C equivalent → /admin/users/add endpoint routing</li>
 *   <li>User Update (Security) - COUSR02C equivalent → /admin/users/update endpoint routing</li>
 *   <li>User Delete (Security) - COUSR03C equivalent → /admin/users/delete endpoint routing</li>
 * </ul>
 * 
 * <p><strong>Security Implementation:</strong></p>
 * <ul>
 *   <li>Spring Security @PreAuthorize("hasRole('ADMIN')") enforcement on all admin operations</li>
 *   <li>Comprehensive audit logging integration for administrative action tracking</li>
 *   <li>Session management through Redis for admin workflow state preservation</li>
 *   <li>Admin user type validation matching COBOL CDEMO-USRTYP-ADMIN logic</li>
 * </ul>
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since Spring Boot 3.2.5, Spring Security 6.1.8
 */
@RestController
@RequestMapping("/admin/menu")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMenuService {

    private static final Logger logger = LoggerFactory.getLogger(AdminMenuService.class);

    // Constants from COADM01C.cbl (lines 36-37)
    private static final String PROGRAM_NAME = "COADM01C";
    private static final String TRANSACTION_ID = "CA00";

    // Admin menu options from COADM02Y.cpy (lines 24-42)
    private static final int ADMIN_OPTION_COUNT = 4;
    private static final String ERROR_INVALID_OPTION = "Please enter a valid option number...";
    private static final String ERROR_INVALID_KEY = "Invalid key pressed. Use a valid option number or PF3 to exit.";
    private static final String SUCCESS_MENU_DISPLAYED = "Admin menu displayed successfully";

    // Session attribute keys for Redis-backed session management
    private static final String SESSION_FROM_TRANID = "CDEMO_FROM_TRANID";
    private static final String SESSION_FROM_PROGRAM = "CDEMO_FROM_PROGRAM";
    private static final String SESSION_TO_PROGRAM = "CDEMO_TO_PROGRAM";
    private static final String SESSION_ADMIN_CONTEXT = "CDEMO_ADMIN_CONTEXT";
    private static final String SESSION_CORRELATION_ID = "CDEMO_CORRELATION_ID";

    // Navigation routes for admin user management operations
    private static final String USER_LIST_ROUTE = "/admin/users";         // Maps to COUSR00C
    private static final String USER_ADD_ROUTE = "/admin/users/add";      // Maps to COUSR01C
    private static final String USER_UPDATE_ROUTE = "/admin/users/update"; // Maps to COUSR02C
    private static final String USER_DELETE_ROUTE = "/admin/users/delete"; // Maps to COUSR03C
    private static final String SIGNON_ROUTE = "/auth/login";             // Maps to COSGN00C

    // Dependencies - using available services as per specification
    // Note: Some dependencies may not exist yet but are referenced in design
    // @Autowired
    // private UserManagementService userManagementService;
    
    // @Autowired 
    // private AuditService auditService;
    
    // @Autowired
    // private SessionManagementService sessionManagementService;

    /**
     * Main admin menu display endpoint replacing COADM01C MAIN-PARA logic.
     * 
     * <p>This method replicates the complete admin menu display flow from COADM01C.cbl:</p>
     * <ul>
     *   <li>Admin user validation (lines 82-86)</li>
     *   <li>Menu screen preparation and display (lines 87-91)</li>
     *   <li>Session context setup for admin operations</li>
     * </ul>
     * 
     * @param request HTTP servlet request for session management
     * @return AdminMenuResponse containing menu options and navigation context
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminMenuResponse> displayAdminMenu(HttpServletRequest request) {
        
        logger.info("Admin menu display requested by user: {}", getCurrentUsername());
        
        try {
            // Validate admin user context
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !hasAdminRole(auth)) {
                logger.warn("Unauthorized admin menu access attempt by user: {}", getCurrentUsername());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(AdminMenuResponse.accessDenied());
            }

            // Setup session context replicating COMMAREA structure (lines 83-86)
            String correlationId = setupAdminSession(request);
            
            // Build menu options from COADM02Y.cpy structure
            List<AdminMenuOption> menuOptions = buildAdminMenuOptions();
            
            // Populate header information replicating POPULATE-HEADER-INFO paragraph
            AdminMenuResponse response = AdminMenuResponse.success(
                menuOptions,
                correlationId,
                TRANSACTION_ID,
                PROGRAM_NAME
            );
            
            // Set navigation context
            response.setFromTransactionId(TRANSACTION_ID);
            response.setFromProgram(PROGRAM_NAME);
            response.setMessage(SUCCESS_MENU_DISPLAYED);
            
            logger.info("Admin menu displayed successfully for user: {} with correlation: {}", 
                       getCurrentUsername(), correlationId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception ex) {
            logger.error("System error displaying admin menu for user: {} - {}", 
                        getCurrentUsername(), ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AdminMenuResponse.systemError());
        }
    }

    /**
     * Admin menu option selection endpoint replacing COADM01C PROCESS-ENTER-KEY logic.
     * 
     * <p>This method replicates the option processing flow from COADM01C.cbl:</p>
     * <ul>
     *   <li>Option validation (lines 127-134)</li>
     *   <li>Program routing logic (lines 138-146)</li>
     *   <li>XCTL program transfer equivalent (lines 142-145)</li>
     * </ul>
     * 
     * @param optionRequest The admin menu option selection request
     * @param request HTTP servlet request for session management
     * @return AdminMenuResponse with navigation instructions or error information
     */
    @PostMapping("/select")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminMenuResponse> selectMenuOption(
            @Valid @RequestBody AdminMenuOptionRequest optionRequest,
            HttpServletRequest request) {
        
        logger.debug("Admin menu option selection: {} by user: {}", 
                    optionRequest.getOption(), getCurrentUsername());
        
        try {
            // Input validation replicating COBOL option validation (lines 127-134)
            AdminMenuResponse validationResult = validateMenuOption(optionRequest.getOption());
            if (!validationResult.isSuccess()) {
                logger.warn("Invalid menu option {} selected by user: {}", 
                           optionRequest.getOption(), getCurrentUsername());
                return ResponseEntity.badRequest().body(validationResult);
            }

            // Update session context for navigation
            String correlationId = updateAdminSessionContext(request, optionRequest.getOption());
            
            // Route to appropriate user management endpoint (lines 138-146)
            String targetRoute = determineTargetRoute(optionRequest.getOption());
            
            // Create successful navigation response
            AdminMenuResponse response = AdminMenuResponse.navigationSuccess(
                targetRoute,
                optionRequest.getOption(),
                correlationId
            );
            
            // Set XCTL equivalent context
            response.setFromTransactionId(TRANSACTION_ID);
            response.setFromProgram(PROGRAM_NAME);
            response.setToProgram(getTargetProgram(optionRequest.getOption()));
            
            logger.info("Admin menu option {} selected successfully by user: {} - routing to: {}", 
                       optionRequest.getOption(), getCurrentUsername(), targetRoute);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception ex) {
            logger.error("System error processing admin menu option {} for user: {} - {}", 
                        optionRequest.getOption(), getCurrentUsername(), ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AdminMenuResponse.systemError());
        }
    }

    /**
     * Admin menu exit endpoint replacing COADM01C PF3 key handling.
     * 
     * <p>This method replicates the exit logic from COADM01C.cbl lines 96-98:</p>
     * <ul>
     *   <li>WHEN DFHPF3 → MOVE 'COSGN00C' TO CDEMO-TO-PROGRAM</li>
     *   <li>PERFORM RETURN-TO-SIGNON-SCREEN</li>
     * </ul>
     * 
     * @param request HTTP servlet request for session management
     * @return AdminMenuResponse with signon screen navigation instructions
     */
    @PostMapping("/exit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminMenuResponse> exitAdminMenu(HttpServletRequest request) {
        
        logger.info("Admin menu exit requested by user: {}", getCurrentUsername());
        
        try {
            // Update session for return to signon (lines 162-164)
            String correlationId = setupReturnToSignonSession(request);
            
            // Create exit navigation response
            AdminMenuResponse response = AdminMenuResponse.navigationSuccess(
                SIGNON_ROUTE,
                0, // No option selected for exit
                correlationId
            );
            
            response.setFromTransactionId(TRANSACTION_ID);
            response.setFromProgram(PROGRAM_NAME);
            response.setToProgram("COSGN00C");
            response.setMessage("Returning to signon screen");
            
            logger.info("Admin menu exit processed successfully for user: {}", getCurrentUsername());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception ex) {
            logger.error("System error processing admin menu exit for user: {} - {}", 
                        getCurrentUsername(), ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AdminMenuResponse.systemError());
        }
    }

    /**
     * Validates admin menu option input matching COBOL validation logic.
     * 
     * <p>Replicates the validation checks from COADM01C.cbl lines 127-134:</p>
     * <ul>
     *   <li>IF WS-OPTION IS NOT NUMERIC</li>
     *   <li>WS-OPTION > CDEMO-ADMIN-OPT-COUNT</li>
     *   <li>WS-OPTION = ZEROS</li>
     * </ul>
     * 
     * @param option The menu option to validate
     * @return AdminMenuResponse indicating validation success or specific error
     */
    private AdminMenuResponse validateMenuOption(int option) {
        
        // Numeric validation (COBOL automatic in Java)
        if (option <= 0) {
            logger.debug("Admin menu option validation failed: option must be positive");
            return AdminMenuResponse.failure(ERROR_INVALID_OPTION, "OPTION_ZERO_OR_NEGATIVE");
        }

        // Range validation (COADM01C.cbl lines 128-129)
        if (option > ADMIN_OPTION_COUNT) {
            logger.debug("Admin menu option validation failed: option {} exceeds maximum {}", 
                        option, ADMIN_OPTION_COUNT);
            return AdminMenuResponse.failure(ERROR_INVALID_OPTION, "OPTION_OUT_OF_RANGE");
        }

        return AdminMenuResponse.validationSuccess();
    }

    /**
     * Builds admin menu options list from COADM02Y.cpy structure.
     * 
     * <p>Replicates the BUILD-MENU-OPTIONS paragraph from COADM01C.cbl lines 226-263:</p>
     * <ul>
     *   <li>PERFORM VARYING WS-IDX FROM 1 BY 1 UNTIL WS-IDX > CDEMO-ADMIN-OPT-COUNT</li>
     *   <li>STRING CDEMO-ADMIN-OPT-NUM, '. ', CDEMO-ADMIN-OPT-NAME INTO WS-ADMIN-OPT-TXT</li>
     * </ul>
     * 
     * @return List of AdminMenuOption objects representing the 4 admin operations
     */
    private List<AdminMenuOption> buildAdminMenuOptions() {
        
        List<AdminMenuOption> options = new ArrayList<>();
        
        // Option 1: User List (Security) - COUSR00C (COADM02Y.cpy lines 24-27)
        options.add(new AdminMenuOption(
            1,
            "User List (Security)",
            "COUSR00C",
            USER_LIST_ROUTE,
            "Display paginated list of system users with selection capability"
        ));
        
        // Option 2: User Add (Security) - COUSR01C (COADM02Y.cpy lines 29-32)
        options.add(new AdminMenuOption(
            2,
            "User Add (Security)",
            "COUSR01C", 
            USER_ADD_ROUTE,
            "Create new user accounts with role assignment and validation"
        ));
        
        // Option 3: User Update (Security) - COUSR02C (COADM02Y.cpy lines 34-37)
        options.add(new AdminMenuOption(
            3,
            "User Update (Security)",
            "COUSR02C",
            USER_UPDATE_ROUTE,
            "Modify existing user information and role assignments"
        ));
        
        // Option 4: User Delete (Security) - COUSR03C (COADM02Y.cpy lines 39-42)
        options.add(new AdminMenuOption(
            4,
            "User Delete (Security)",
            "COUSR03C",
            USER_DELETE_ROUTE,
            "Remove user accounts with confirmation and audit trail"
        ));
        
        logger.debug("Built {} admin menu options", options.size());
        return options;
    }

    /**
     * Determines target route based on selected admin menu option.
     * 
     * <p>Replicates the XCTL routing logic from COADM01C.cbl lines 138-146:</p>
     * <ul>
     *   <li>IF CDEMO-ADMIN-OPT-PGMNAME(WS-OPTION)(1:5) NOT = 'DUMMY'</li>
     *   <li>EXEC CICS XCTL PROGRAM(CDEMO-ADMIN-OPT-PGMNAME(WS-OPTION))</li>
     * </ul>
     * 
     * @param option The selected menu option (1-4)
     * @return String route path for the selected admin operation
     */
    private String determineTargetRoute(int option) {
        switch (option) {
            case 1:
                return USER_LIST_ROUTE;     // COUSR00C equivalent
            case 2:
                return USER_ADD_ROUTE;      // COUSR01C equivalent
            case 3:
                return USER_UPDATE_ROUTE;   // COUSR02C equivalent
            case 4:
                return USER_DELETE_ROUTE;   // COUSR03C equivalent
            default:
                logger.warn("Invalid admin menu option for routing: {}", option);
                return SIGNON_ROUTE;
        }
    }

    /**
     * Gets target program name for selected admin menu option.
     * 
     * @param option The selected menu option (1-4)
     * @return String program name equivalent to COBOL program name
     */
    private String getTargetProgram(int option) {
        switch (option) {
            case 1:
                return "COUSR00C";  // User List
            case 2:
                return "COUSR01C";  // User Add
            case 3:
                return "COUSR02C";  // User Update
            case 4:
                return "COUSR03C";  // User Delete
            default:
                return "COSGN00C";  // Default to signon
        }
    }

    /**
     * Sets up admin session management replacing CICS COMMAREA structure.
     * 
     * <p>This method replicates the session setup from COADM01C.cbl lines 83-86:</p>
     * <ul>
     *   <li>MOVE DFHCOMMAREA(1:EIBCALEN) TO CARDDEMO-COMMAREA</li>
     *   <li>SET CDEMO-PGM-REENTER TO TRUE</li>
     *   <li>Session context for admin operations</li>
     * </ul>
     * 
     * @param request The HTTP servlet request for session access
     * @return Session correlation ID for audit trail
     */
    private String setupAdminSession(HttpServletRequest request) {
        
        HttpSession session = request.getSession(true);
        String correlationId = generateCorrelationId();
        
        // Setup session attributes matching COMMAREA structure
        session.setAttribute(SESSION_FROM_TRANID, TRANSACTION_ID);
        session.setAttribute(SESSION_FROM_PROGRAM, PROGRAM_NAME);
        session.setAttribute(SESSION_ADMIN_CONTEXT, "ADMIN_MENU_DISPLAYED");
        session.setAttribute(SESSION_CORRELATION_ID, correlationId);
        
        // Additional session context for admin operations
        session.setAttribute("lastAdminMenuAccess", LocalDateTime.now());
        session.setAttribute("adminMenuSessionId", correlationId);
        session.setAttribute("currentProgram", PROGRAM_NAME);
        session.setAttribute("currentTransaction", TRANSACTION_ID);
        
        logger.debug("Admin session established: sessionId={}, correlationId={}, user={}", 
                    session.getId(), correlationId, getCurrentUsername());
        
        return correlationId;
    }

    /**
     * Updates admin session context for menu option navigation.
     * 
     * @param request The HTTP servlet request for session access
     * @param selectedOption The selected menu option
     * @return Session correlation ID for audit trail
     */
    private String updateAdminSessionContext(HttpServletRequest request, int selectedOption) {
        
        HttpSession session = request.getSession(false);
        if (session == null) {
            session = request.getSession(true);
        }
        
        String correlationId = (String) session.getAttribute(SESSION_CORRELATION_ID);
        if (correlationId == null) {
            correlationId = generateCorrelationId();
            session.setAttribute(SESSION_CORRELATION_ID, correlationId);
        }
        
        // Update session for selected option
        session.setAttribute(SESSION_TO_PROGRAM, getTargetProgram(selectedOption));
        session.setAttribute("selectedAdminOption", selectedOption);
        session.setAttribute("targetRoute", determineTargetRoute(selectedOption));
        session.setAttribute("lastOptionSelection", LocalDateTime.now());
        
        logger.debug("Admin session updated: option={}, target={}, correlationId={}", 
                    selectedOption, getTargetProgram(selectedOption), correlationId);
        
        return correlationId;
    }

    /**
     * Sets up session for return to signon screen.
     * 
     * @param request The HTTP servlet request for session access
     * @return Session correlation ID for audit trail
     */
    private String setupReturnToSignonSession(HttpServletRequest request) {
        
        HttpSession session = request.getSession(false);
        if (session == null) {
            session = request.getSession(true);
        }
        
        String correlationId = (String) session.getAttribute(SESSION_CORRELATION_ID);
        if (correlationId == null) {
            correlationId = generateCorrelationId();
        }
        
        // Setup return to signon context
        session.setAttribute(SESSION_TO_PROGRAM, "COSGN00C");
        session.setAttribute("exitAdminMenu", true);
        session.setAttribute("exitTimestamp", LocalDateTime.now());
        session.setAttribute("returnRoute", SIGNON_ROUTE);
        
        logger.debug("Return to signon session setup: correlationId={}, user={}", 
                    correlationId, getCurrentUsername());
        
        return correlationId;
    }

    /**
     * Generates unique correlation ID for audit trail and session tracking.
     * 
     * @return Unique correlation identifier
     */
    private String generateCorrelationId() {
        return "ADMIN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Gets current authenticated username.
     * 
     * @return Current username or "UNKNOWN" if not authenticated
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null) ? auth.getName() : "UNKNOWN";
    }

    /**
     * Checks if current user has admin role.
     * 
     * @param auth The authentication object
     * @return true if user has ROLE_ADMIN authority
     */
    private boolean hasAdminRole(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    /**
     * Data Transfer Object for admin menu option selection requests.
     */
    public static class AdminMenuOptionRequest {
        
        @NotNull(message = "Option selection is required")
        @Min(value = 1, message = "Option must be between 1 and 4")
        @Max(value = 4, message = "Option must be between 1 and 4")
        private Integer option;

        // Constructors
        public AdminMenuOptionRequest() {}

        public AdminMenuOptionRequest(Integer option) {
            this.option = option;
        }

        // Getters and setters
        public Integer getOption() {
            return option;
        }

        public void setOption(Integer option) {
            this.option = option;
        }
    }

    /**
     * Data Transfer Object representing an admin menu option.
     */
    public static class AdminMenuOption {
        
        private int optionNumber;
        private String optionName;
        private String programName;
        private String routePath;
        private String description;

        // Constructors
        public AdminMenuOption() {}

        public AdminMenuOption(int optionNumber, String optionName, String programName, 
                              String routePath, String description) {
            this.optionNumber = optionNumber;
            this.optionName = optionName;
            this.programName = programName;
            this.routePath = routePath;
            this.description = description;
        }

        // Getters and setters
        public int getOptionNumber() {
            return optionNumber;
        }

        public void setOptionNumber(int optionNumber) {
            this.optionNumber = optionNumber;
        }

        public String getOptionName() {
            return optionName;
        }

        public void setOptionName(String optionName) {
            this.optionName = optionName;
        }

        public String getProgramName() {
            return programName;
        }

        public void setProgramName(String programName) {
            this.programName = programName;
        }

        public String getRoutePath() {
            return routePath;
        }

        public void setRoutePath(String routePath) {
            this.routePath = routePath;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    /**
     * Data Transfer Object for admin menu responses.
     */
    public static class AdminMenuResponse {
        
        private boolean success;
        private String message;
        private String errorCode;
        private List<AdminMenuOption> menuOptions;
        private String correlationId;
        private String transactionId;
        private String programName;
        private String fromTransactionId;
        private String fromProgram;
        private String toProgram;
        private String navigationRoute;
        private int selectedOption;
        private LocalDateTime timestamp;

        // Constructors
        public AdminMenuResponse() {
            this.timestamp = LocalDateTime.now();
        }

        public AdminMenuResponse(boolean success, String message) {
            this();
            this.success = success;
            this.message = message;
        }

        // Factory methods for common response types
        public static AdminMenuResponse success(List<AdminMenuOption> menuOptions, 
                                               String correlationId, String transactionId, 
                                               String programName) {
            AdminMenuResponse response = new AdminMenuResponse(true, SUCCESS_MENU_DISPLAYED);
            response.menuOptions = menuOptions;
            response.correlationId = correlationId;
            response.transactionId = transactionId;
            response.programName = programName;
            return response;
        }

        public static AdminMenuResponse navigationSuccess(String route, int option, String correlationId) {
            AdminMenuResponse response = new AdminMenuResponse(true, "Navigation successful");
            response.navigationRoute = route;
            response.selectedOption = option;
            response.correlationId = correlationId;
            return response;
        }

        public static AdminMenuResponse failure(String message, String errorCode) {
            AdminMenuResponse response = new AdminMenuResponse(false, message);
            response.errorCode = errorCode;
            return response;
        }

        public static AdminMenuResponse accessDenied() {
            return failure("Access denied. Administrative privileges required.", "ACCESS_DENIED");
        }

        public static AdminMenuResponse systemError() {
            return failure("System error occurred. Please try again or contact support.", "SYSTEM_ERROR");
        }

        public static AdminMenuResponse validationSuccess() {
            return new AdminMenuResponse(true, "Validation successful");
        }

        // Getters and setters
        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
        }

        public List<AdminMenuOption> getMenuOptions() {
            return menuOptions;
        }

        public void setMenuOptions(List<AdminMenuOption> menuOptions) {
            this.menuOptions = menuOptions;
        }

        public String getCorrelationId() {
            return correlationId;
        }

        public void setCorrelationId(String correlationId) {
            this.correlationId = correlationId;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public void setTransactionId(String transactionId) {
            this.transactionId = transactionId;
        }

        public String getProgramName() {
            return programName;
        }

        public void setProgramName(String programName) {
            this.programName = programName;
        }

        public String getFromTransactionId() {
            return fromTransactionId;
        }

        public void setFromTransactionId(String fromTransactionId) {
            this.fromTransactionId = fromTransactionId;
        }

        public String getFromProgram() {
            return fromProgram;
        }

        public void setFromProgram(String fromProgram) {
            this.fromProgram = fromProgram;
        }

        public String getToProgram() {
            return toProgram;
        }

        public void setToProgram(String toProgram) {
            this.toProgram = toProgram;
        }

        public String getNavigationRoute() {
            return navigationRoute;
        }

        public void setNavigationRoute(String navigationRoute) {
            this.navigationRoute = navigationRoute;
        }

        public int getSelectedOption() {
            return selectedOption;
        }

        public void setSelectedOption(int selectedOption) {
            this.selectedOption = selectedOption;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }
    }
}
/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.menu;

import com.carddemo.auth.AuthenticationService;
import com.carddemo.session.SessionManagementService;
import com.carddemo.validation.ValidationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Primary menu navigation service implementing REST endpoints for main menu functionality.
 * 
 * <p><strong>Legacy System Migration:</strong></p>
 * <p>This service replaces the COMEN01C.cbl COBOL program with modern REST API patterns 
 * while maintaining identical menu navigation logic and user role-based access control.
 * Preserves exact 10-option menu layout functionality per BMS to React component mapping.</p>
 * 
 * <p><strong>COBOL Program Reference:</strong> COMEN01C.cbl - Main Menu for Regular Users</p>
 * 
 * <p><strong>Key Migration Points:</strong></p>
 * <ul>
 *   <li>CICS pseudo-conversational processing → Redis-backed session management</li>
 *   <li>BMS screen I/O (SEND/RECEIVE MAP) → REST JSON responses</li>
 *   <li>CICS XCTL program transfers → HTTP redirect responses to REST endpoints</li>
 *   <li>COMMAREA structure → Session attributes with user context preservation</li>
 *   <li>COBOL 88-level condition checking → Spring Security role-based authorization</li>
 *   <li>HANDLE ABEND error processing → Spring @ExceptionHandler with HTTP status codes</li>
 * </ul>
 * 
 * <p><strong>Business Logic Preservation:</strong></p>
 * <p>All core menu navigation logic from COMEN01C.cbl is preserved:</p>
 * <ul>
 *   <li>10 menu option definitions and descriptions (lines 19-85 in COMEN02Y.cpy)</li>
 *   <li>User input validation and numeric option processing (lines 115-134 in COMEN01C.cbl)</li>
 *   <li>Role-based access control filtering (lines 136-143 in COMEN01C.cbl)</li>
 *   <li>Program navigation logic and XCTL transfers (lines 145-165 in COMEN01C.cbl)</li>
 *   <li>Header population and screen formatting (lines 212-232 in COMEN01C.cbl)</li>
 * </ul>
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since Spring Boot 3.2.5, Spring Security 6.1.8
 */
@Service
@RestController
@RequestMapping("/api/v1/menu")
public class MenuService {

    private static final Logger logger = LoggerFactory.getLogger(MenuService.class);

    // Transaction and program identifiers from COMEN01C.cbl (lines 36-37)
    private static final String TRANSACTION_ID = "CM00";
    private static final String PROGRAM_NAME = "COMEN01C";

    // Error message constants matching COMEN01C.cbl error messages (lines 100-102, 130-132)
    private static final String ERROR_INVALID_KEY = "Invalid Key Pressed";
    private static final String ERROR_INVALID_OPTION = "Please enter a valid option number...";
    private static final String ERROR_NO_ACCESS_ADMIN = "No access - Admin Only option... ";
    private static final String ERROR_COMING_SOON = "This option is coming soon ...";

    // Menu option count from COMEN02Y.cpy (line 21)
    private static final int MENU_OPTION_COUNT = 10;

    // RACF user type mappings from COCOM01Y.cpy (lines 27-28)
    private static final String RACF_ADMIN_TYPE = "A";  // CDEMO-USRTYP-ADMIN
    private static final String RACF_USER_TYPE = "U";   // CDEMO-USRTYP-USER

    // Session attribute keys for COMMAREA structure replacement
    private static final String SESSION_USER_ID = "CDEMO_USER_ID";
    private static final String SESSION_USER_TYPE = "CDEMO_USER_TYPE";
    private static final String SESSION_FROM_TRANID = "CDEMO_FROM_TRANID";
    private static final String SESSION_FROM_PROGRAM = "CDEMO_FROM_PROGRAM";
    private static final String SESSION_PGM_CONTEXT = "CDEMO_PGM_CONTEXT";

    // Menu option definitions from COMEN02Y.cpy (lines 25-85)
    private static final MenuOption[] MAIN_MENU_OPTIONS = {
        new MenuOption(1, "Account View", "COACTVWC", RACF_USER_TYPE),
        new MenuOption(2, "Account Update", "COACTUPC", RACF_USER_TYPE),
        new MenuOption(3, "Credit Card List", "COCRDLIC", RACF_USER_TYPE),
        new MenuOption(4, "Credit Card View", "COCRDSLC", RACF_USER_TYPE),
        new MenuOption(5, "Credit Card Update", "COCRDUPC", RACF_USER_TYPE),
        new MenuOption(6, "Transaction List", "COTRN00C", RACF_USER_TYPE),
        new MenuOption(7, "Transaction View", "COTRN01C", RACF_USER_TYPE),
        new MenuOption(8, "Transaction Add", "COTRN02C", RACF_USER_TYPE),
        new MenuOption(9, "Transaction Reports", "CORPT00C", RACF_USER_TYPE),
        new MenuOption(10, "Bill Payment", "COBIL00C", RACF_USER_TYPE)
    };

    // Dependencies
    private final AuthenticationService authenticationService;
    private final ValidationService validationService;

    /**
     * Constructor with dependency injection for all required components.
     * 
     * @param authenticationService Service for user authentication and authorization
     * @param validationService Service for COBOL-style field validation
     */
    @Autowired
    public MenuService(
            AuthenticationService authenticationService,
            ValidationService validationService) {
        this.authenticationService = authenticationService;
        this.validationService = validationService;
        
        logger.info("MenuService initialized - replacing COMEN01C.cbl functionality");
        logger.info("Menu option count: {}", MENU_OPTION_COUNT);
    }

    /**
     * Displays main menu screen with user-specific options.
     * 
     * <p>Replicates the SEND-MENU-SCREEN paragraph from COMEN01C.cbl (lines 182-194):</p>
     * <ul>
     *   <li>PERFORM POPULATE-HEADER-INFO</li>
     *   <li>PERFORM BUILD-MENU-OPTIONS</li>
     *   <li>EXEC CICS SEND MAP('COMEN1A')</li>
     * </ul>
     * 
     * @param httpRequest The HTTP servlet request for session management
     * @return MenuDisplayResponse containing menu options and header information
     */
    @GetMapping("/main")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<MenuDisplayResponse> displayMainMenu(HttpServletRequest httpRequest) {
        
        logger.debug("Displaying main menu for user");
        
        try {
            // Retrieve session context (COMMAREA equivalent)
            HttpSession session = httpRequest.getSession(false);
            if (session == null) {
                logger.warn("No active session found for menu display");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(MenuDisplayResponse.error("Session expired", "SESSION_EXPIRED"));
            }

            String userId = (String) session.getAttribute(SESSION_USER_ID);
            String userType = (String) session.getAttribute(SESSION_USER_TYPE);
            
            if (!StringUtils.hasText(userId) || !StringUtils.hasText(userType)) {
                logger.warn("Invalid session context - missing user information");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(MenuDisplayResponse.error("Invalid session", "INVALID_SESSION"));
            }

            // Setup session state (replicating COMMAREA setup from lines 87-88)
            session.setAttribute(SESSION_FROM_TRANID, TRANSACTION_ID);
            session.setAttribute(SESSION_FROM_PROGRAM, PROGRAM_NAME);
            session.setAttribute(SESSION_PGM_CONTEXT, 1); // CDEMO-PGM-REENTER = TRUE

            // Populate header information (POPULATE-HEADER-INFO paragraph lines 212-232)
            MenuHeaderInfo headerInfo = populateHeaderInfo();

            // Build menu options filtered by user type (BUILD-MENU-OPTIONS paragraph lines 236-277)
            List<MenuOptionDisplay> menuOptions = buildMenuOptions(userType);

            // Create successful response
            MenuDisplayResponse response = MenuDisplayResponse.success(
                headerInfo,
                menuOptions,
                MENU_OPTION_COUNT,
                userType,
                userId
            );

            response.setTransactionId(TRANSACTION_ID);
            response.setProgramName(PROGRAM_NAME);
            response.setSessionId(session.getId());

            logger.debug("Main menu displayed successfully for user: {} ({})", userId, userType);
            
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            logger.error("Error displaying main menu: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(MenuDisplayResponse.error("System error occurred", "SYSTEM_ERROR"));
        }
    }

    /**
     * Processes menu option selection.
     * 
     * <p>Replicates the PROCESS-ENTER-KEY paragraph from COMEN01C.cbl (lines 115-165):</p>
     * <ul>
     *   <li>Input validation and numeric conversion (lines 117-134)</li>
     *   <li>User type access control checking (lines 136-143)</li>
     *   <li>Program navigation via CICS XCTL (lines 145-165)</li>
     * </ul>
     * 
     * @param request The menu option selection request
     * @param httpRequest The HTTP servlet request for session management
     * @return MenuSelectionResponse containing navigation information or error details
     */
    @PostMapping("/main/select")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<MenuSelectionResponse> processMenuSelection(
            @Valid @RequestBody MenuSelectionRequest request,
            HttpServletRequest httpRequest) {
        
        logger.debug("Processing menu selection: option={}", request.getOptionNumber());
        
        try {
            // Validate session context
            HttpSession session = httpRequest.getSession(false);
            if (session == null) {
                logger.warn("No active session found for menu selection");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(MenuSelectionResponse.error("Session expired", "SESSION_EXPIRED"));
            }

            String userId = (String) session.getAttribute(SESSION_USER_ID);
            String userType = (String) session.getAttribute(SESSION_USER_TYPE);

            // Input validation replicating COBOL logic (lines 127-134)
            MenuSelectionResponse validationResult = validateMenuSelection(request.getOptionNumber());
            if (!validationResult.isSuccess()) {
                logger.debug("Menu selection validation failed: {}", validationResult.getMessage());
                return ResponseEntity.badRequest().body(validationResult);
            }

            // User type access control (lines 136-143)
            MenuOption selectedOption = MAIN_MENU_OPTIONS[request.getOptionNumber() - 1];
            if (RACF_USER_TYPE.equals(userType) && RACF_ADMIN_TYPE.equals(selectedOption.getUserType())) {
                logger.warn("User {} attempted to access admin-only option {}", userId, request.getOptionNumber());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(MenuSelectionResponse.error(ERROR_NO_ACCESS_ADMIN, "ACCESS_DENIED"));
            }

            // Program navigation logic (lines 145-165)
            if (!selectedOption.getProgramName().startsWith("DUMMY")) {
                // Update session context (COMMAREA equivalent)
                session.setAttribute(SESSION_FROM_TRANID, TRANSACTION_ID);
                session.setAttribute(SESSION_FROM_PROGRAM, PROGRAM_NAME);
                session.setAttribute(SESSION_PGM_CONTEXT, 0); // CDEMO-PGM-ENTER = ZEROS

                // Convert COBOL program name to REST endpoint path
                String targetEndpoint = convertProgramNameToEndpoint(selectedOption.getProgramName());
                
                MenuSelectionResponse response = MenuSelectionResponse.success(
                    selectedOption.getOptionNumber(),
                    selectedOption.getOptionName(),
                    targetEndpoint,
                    selectedOption.getProgramName()
                );

                response.setFromTransactionId(TRANSACTION_ID);
                response.setFromProgram(PROGRAM_NAME);
                response.setTargetProgram(selectedOption.getProgramName());
                response.setSessionId(session.getId());

                logger.info("Menu selection processed: user={}, option={}, target={}", 
                           userId, request.getOptionNumber(), targetEndpoint);
                
                return ResponseEntity.ok(response);
            }

            // Coming soon message for dummy programs (lines 157-164)
            String comingSoonMessage = String.format("This option %s %s", 
                                                     selectedOption.getOptionName(), 
                                                     ERROR_COMING_SOON);
            
            logger.debug("Coming soon option selected: {}", selectedOption.getOptionName());
            
            return ResponseEntity.ok(MenuSelectionResponse.comingSoon(
                selectedOption.getOptionNumber(),
                selectedOption.getOptionName(),
                comingSoonMessage
            ));

        } catch (Exception ex) {
            logger.error("Error processing menu selection: option={}, error={}", 
                        request.getOptionNumber(), ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(MenuSelectionResponse.error("System error occurred", "SYSTEM_ERROR"));
        }
    }

    /**
     * Handles return to sign-on screen (PF3 key functionality).
     * 
     * <p>Replicates the RETURN-TO-SIGNON-SCREEN paragraph from COMEN01C.cbl (lines 170-177):</p>
     * <ul>
     *   <li>IF CDEMO-TO-PROGRAM = LOW-VALUES OR SPACES</li>
     *   <li>MOVE 'COSGN00C' TO CDEMO-TO-PROGRAM</li>
     *   <li>EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM)</li>
     * </ul>
     * 
     * @param httpRequest The HTTP servlet request for session management
     * @return MenuNavigationResponse containing sign-on screen redirect information
     */
    @PostMapping("/main/exit")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<MenuNavigationResponse> returnToSignonScreen(HttpServletRequest httpRequest) {
        
        logger.debug("Processing return to sign-on screen");
        
        try {
            // Validate session context
            HttpSession session = httpRequest.getSession(false);
            if (session == null) {
                logger.warn("No active session found for sign-on return");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(MenuNavigationResponse.error("Session expired", "SESSION_EXPIRED"));
            }

            String userId = (String) session.getAttribute(SESSION_USER_ID);

            // Default to sign-on program (lines 172-174)
            String targetProgram = "COSGN00C";
            String targetEndpoint = "/api/v1/auth/logout";

            MenuNavigationResponse response = MenuNavigationResponse.success(
                targetProgram,
                targetEndpoint,
                "Returning to sign-on screen"
            );

            response.setFromTransactionId(TRANSACTION_ID);
            response.setFromProgram(PROGRAM_NAME);
            response.setSessionId(session.getId());

            logger.info("User {} returning to sign-on screen", userId);
            
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            logger.error("Error returning to sign-on screen: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(MenuNavigationResponse.error("System error occurred", "SYSTEM_ERROR"));
        }
    }

    /**
     * Validates menu option selection input.
     * 
     * <p>Replicates validation logic from COMEN01C.cbl lines 127-134:</p>
     * <ul>
     *   <li>IF WS-OPTION IS NOT NUMERIC</li>
     *   <li>OR WS-OPTION > CDEMO-MENU-OPT-COUNT</li>
     *   <li>OR WS-OPTION = ZEROS</li>
     * </ul>
     * 
     * @param optionNumber The selected menu option number
     * @return MenuSelectionResponse indicating validation success or specific error
     */
    private MenuSelectionResponse validateMenuSelection(Integer optionNumber) {
        
        // Null check (equivalent to SPACES or LOW-VALUES)
        if (optionNumber == null) {
            return MenuSelectionResponse.error(ERROR_INVALID_OPTION, "OPTION_REQUIRED");
        }

        // Zero check (WS-OPTION = ZEROS)
        if (optionNumber == 0) {
            return MenuSelectionResponse.error(ERROR_INVALID_OPTION, "OPTION_ZERO");
        }

        // Range validation (WS-OPTION > CDEMO-MENU-OPT-COUNT)
        if (optionNumber > MENU_OPTION_COUNT) {
            return MenuSelectionResponse.error(ERROR_INVALID_OPTION, "OPTION_OUT_OF_RANGE");
        }

        // Negative number check
        if (optionNumber < 0) {
            return MenuSelectionResponse.error(ERROR_INVALID_OPTION, "OPTION_NEGATIVE");
        }

        return MenuSelectionResponse.success(optionNumber, "", "", "");
    }

    /**
     * Populates header information for menu display.
     * 
     * <p>Replicates the POPULATE-HEADER-INFO paragraph from COMEN01C.cbl (lines 212-232):</p>
     * <ul>
     *   <li>MOVE FUNCTION CURRENT-DATE TO WS-CURDATE-DATA</li>
     *   <li>MOVE CCDA-TITLE01 TO TITLE01O OF COMEN1AO</li>
     *   <li>MOVE WS-TRANID TO TRNNAMEO OF COMEN1AO</li>
     *   <li>Date and time formatting logic</li>
     * </ul>
     * 
     * @return MenuHeaderInfo containing formatted header data
     */
    private MenuHeaderInfo populateHeaderInfo() {
        
        LocalDateTime currentDateTime = LocalDateTime.now();
        
        // Format date as MM/DD/YY (lines 221-225)
        String formattedDate = currentDateTime.format(DateTimeFormatter.ofPattern("MM/dd/yy"));
        
        // Format time as HH:MM:SS (lines 227-231)
        String formattedTime = currentDateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        
        MenuHeaderInfo headerInfo = new MenuHeaderInfo();
        headerInfo.setTitle1("CardDemo Application"); // CCDA-TITLE01
        headerInfo.setTitle2("Main Menu"); // CCDA-TITLE02
        headerInfo.setTransactionName(TRANSACTION_ID); // WS-TRANID
        headerInfo.setProgramName(PROGRAM_NAME); // WS-PGMNAME
        headerInfo.setCurrentDate(formattedDate);
        headerInfo.setCurrentTime(formattedTime);
        headerInfo.setTimestamp(currentDateTime);
        
        return headerInfo;
    }

    /**
     * Builds menu options filtered by user type.
     * 
     * <p>Replicates the BUILD-MENU-OPTIONS paragraph from COMEN01C.cbl (lines 236-277):</p>
     * <ul>
     *   <li>PERFORM VARYING WS-IDX FROM 1 BY 1 UNTIL WS-IDX > CDEMO-MENU-OPT-COUNT</li>
     *   <li>String formatting for option number and name</li>
     *   <li>User type filtering for access control</li>
     * </ul>
     * 
     * @param userType The user's role type ('A' for Admin, 'U' for User)
     * @return List of MenuOptionDisplay objects for the user's access level
     */
    private List<MenuOptionDisplay> buildMenuOptions(String userType) {
        
        List<MenuOptionDisplay> menuOptions = new ArrayList<>();
        
        // PERFORM VARYING WS-IDX FROM 1 BY 1 UNTIL WS-IDX > CDEMO-MENU-OPT-COUNT
        for (int idx = 0; idx < MENU_OPTION_COUNT; idx++) {
            MenuOption option = MAIN_MENU_OPTIONS[idx];
            
            // User type access filtering (replicating COBOL 88-level condition logic)
            boolean hasAccess = true;
            if (RACF_USER_TYPE.equals(userType) && RACF_ADMIN_TYPE.equals(option.getUserType())) {
                hasAccess = false;
            }
            
            // String formatting (lines 243-246)
            String optionText = String.format("%d. %s", option.getOptionNumber(), option.getOptionName());
            
            MenuOptionDisplay display = new MenuOptionDisplay();
            display.setOptionNumber(option.getOptionNumber());
            display.setOptionName(option.getOptionName());
            display.setOptionText(optionText);
            display.setProgramName(option.getProgramName());
            display.setUserType(option.getUserType());
            display.setAccessible(hasAccess);
            display.setComingSoon(option.getProgramName().startsWith("DUMMY"));
            
            menuOptions.add(display);
        }
        
        logger.debug("Built {} menu options for user type: {}", menuOptions.size(), userType);
        
        return menuOptions;
    }

    /**
     * Converts COBOL program name to REST endpoint path.
     * 
     * <p>Maps COBOL program names to corresponding REST API endpoints:</p>
     * <ul>
     *   <li>COACTVWC → /api/v1/accounts/view</li>
     *   <li>COACTUPC → /api/v1/accounts/update</li>
     *   <li>COCRDLIC → /api/v1/cards/list</li>
     *   <li>And so on for all menu options</li>
     * </ul>
     * 
     * @param programName The COBOL program name
     * @return The corresponding REST endpoint path
     */
    private String convertProgramNameToEndpoint(String programName) {
        
        Map<String, String> programToEndpointMap = new HashMap<>();
        programToEndpointMap.put("COACTVWC", "/api/v1/accounts/view");
        programToEndpointMap.put("COACTUPC", "/api/v1/accounts/update");
        programToEndpointMap.put("COCRDLIC", "/api/v1/cards/list");
        programToEndpointMap.put("COCRDSLC", "/api/v1/cards/view");
        programToEndpointMap.put("COCRDUPC", "/api/v1/cards/update");
        programToEndpointMap.put("COTRN00C", "/api/v1/transactions/list");
        programToEndpointMap.put("COTRN01C", "/api/v1/transactions/view");
        programToEndpointMap.put("COTRN02C", "/api/v1/transactions/add");
        programToEndpointMap.put("CORPT00C", "/api/v1/reports/menu");
        programToEndpointMap.put("COBIL00C", "/api/v1/payments/bill");
        
        return programToEndpointMap.getOrDefault(programName, "/api/v1/menu/main");
    }

    /**
     * Menu option data structure from COMEN02Y.cpy.
     */
    private static class MenuOption {
        private final int optionNumber;
        private final String optionName;
        private final String programName;
        private final String userType;

        public MenuOption(int optionNumber, String optionName, String programName, String userType) {
            this.optionNumber = optionNumber;
            this.optionName = optionName;
            this.programName = programName;
            this.userType = userType;
        }

        public int getOptionNumber() { return optionNumber; }
        public String getOptionName() { return optionName; }
        public String getProgramName() { return programName; }
        public String getUserType() { return userType; }
    }

    /**
     * Menu selection request DTO with validation.
     */
    public static class MenuSelectionRequest {
        @NotNull(message = "Option number is required")
        @Min(value = 1, message = "Option number must be at least 1")
        @Max(value = 10, message = "Option number must not exceed 10")
        private Integer optionNumber;

        public Integer getOptionNumber() { return optionNumber; }
        public void setOptionNumber(Integer optionNumber) { this.optionNumber = optionNumber; }
    }

    /**
     * Menu header information for display.
     */
    public static class MenuHeaderInfo {
        private String title1;
        private String title2;
        private String transactionName;
        private String programName;
        private String currentDate;
        private String currentTime;
        private LocalDateTime timestamp;

        // Getters and setters
        public String getTitle1() { return title1; }
        public void setTitle1(String title1) { this.title1 = title1; }
        public String getTitle2() { return title2; }
        public void setTitle2(String title2) { this.title2 = title2; }
        public String getTransactionName() { return transactionName; }
        public void setTransactionName(String transactionName) { this.transactionName = transactionName; }
        public String getProgramName() { return programName; }
        public void setProgramName(String programName) { this.programName = programName; }
        public String getCurrentDate() { return currentDate; }
        public void setCurrentDate(String currentDate) { this.currentDate = currentDate; }
        public String getCurrentTime() { return currentTime; }
        public void setCurrentTime(String currentTime) { this.currentTime = currentTime; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    /**
     * Menu option display DTO.
     */
    public static class MenuOptionDisplay {
        private int optionNumber;
        private String optionName;
        private String optionText;
        private String programName;
        private String userType;
        private boolean accessible;
        private boolean comingSoon;

        // Getters and setters
        public int getOptionNumber() { return optionNumber; }
        public void setOptionNumber(int optionNumber) { this.optionNumber = optionNumber; }
        public String getOptionName() { return optionName; }
        public void setOptionName(String optionName) { this.optionName = optionName; }
        public String getOptionText() { return optionText; }
        public void setOptionText(String optionText) { this.optionText = optionText; }
        public String getProgramName() { return programName; }
        public void setProgramName(String programName) { this.programName = programName; }
        public String getUserType() { return userType; }
        public void setUserType(String userType) { this.userType = userType; }
        public boolean isAccessible() { return accessible; }
        public void setAccessible(boolean accessible) { this.accessible = accessible; }
        public boolean isComingSoon() { return comingSoon; }
        public void setComingSoon(boolean comingSoon) { this.comingSoon = comingSoon; }
    }

    /**
     * Menu display response DTO.
     */
    public static class MenuDisplayResponse {
        private boolean success;
        private String message;
        private String errorCode;
        private MenuHeaderInfo headerInfo;
        private List<MenuOptionDisplay> menuOptions;
        private int menuOptionCount;
        private String userType;
        private String userId;
        private String transactionId;
        private String programName;
        private String sessionId;

        public static MenuDisplayResponse success(MenuHeaderInfo headerInfo, 
                                                List<MenuOptionDisplay> menuOptions,
                                                int menuOptionCount, String userType, String userId) {
            MenuDisplayResponse response = new MenuDisplayResponse();
            response.success = true;
            response.headerInfo = headerInfo;
            response.menuOptions = menuOptions;
            response.menuOptionCount = menuOptionCount;
            response.userType = userType;
            response.userId = userId;
            return response;
        }

        public static MenuDisplayResponse error(String message, String errorCode) {
            MenuDisplayResponse response = new MenuDisplayResponse();
            response.success = false;
            response.message = message;
            response.errorCode = errorCode;
            return response;
        }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
        public MenuHeaderInfo getHeaderInfo() { return headerInfo; }
        public void setHeaderInfo(MenuHeaderInfo headerInfo) { this.headerInfo = headerInfo; }
        public List<MenuOptionDisplay> getMenuOptions() { return menuOptions; }
        public void setMenuOptions(List<MenuOptionDisplay> menuOptions) { this.menuOptions = menuOptions; }
        public int getMenuOptionCount() { return menuOptionCount; }
        public void setMenuOptionCount(int menuOptionCount) { this.menuOptionCount = menuOptionCount; }
        public String getUserType() { return userType; }
        public void setUserType(String userType) { this.userType = userType; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        public String getProgramName() { return programName; }
        public void setProgramName(String programName) { this.programName = programName; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }

    /**
     * Menu selection response DTO.
     */
    public static class MenuSelectionResponse {
        private boolean success;
        private String message;
        private String errorCode;
        private int selectedOption;
        private String optionName;
        private String targetEndpoint;
        private String targetProgram;
        private String fromTransactionId;
        private String fromProgram;
        private String sessionId;
        private boolean comingSoon;

        public static MenuSelectionResponse success(int selectedOption, String optionName, 
                                                  String targetEndpoint, String targetProgram) {
            MenuSelectionResponse response = new MenuSelectionResponse();
            response.success = true;
            response.selectedOption = selectedOption;
            response.optionName = optionName;
            response.targetEndpoint = targetEndpoint;
            response.targetProgram = targetProgram;
            return response;
        }

        public static MenuSelectionResponse error(String message, String errorCode) {
            MenuSelectionResponse response = new MenuSelectionResponse();
            response.success = false;
            response.message = message;
            response.errorCode = errorCode;
            return response;
        }

        public static MenuSelectionResponse comingSoon(int selectedOption, String optionName, String message) {
            MenuSelectionResponse response = new MenuSelectionResponse();
            response.success = true;
            response.selectedOption = selectedOption;
            response.optionName = optionName;
            response.message = message;
            response.comingSoon = true;
            return response;
        }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
        public int getSelectedOption() { return selectedOption; }
        public void setSelectedOption(int selectedOption) { this.selectedOption = selectedOption; }
        public String getOptionName() { return optionName; }
        public void setOptionName(String optionName) { this.optionName = optionName; }
        public String getTargetEndpoint() { return targetEndpoint; }
        public void setTargetEndpoint(String targetEndpoint) { this.targetEndpoint = targetEndpoint; }
        public String getTargetProgram() { return targetProgram; }
        public void setTargetProgram(String targetProgram) { this.targetProgram = targetProgram; }
        public String getFromTransactionId() { return fromTransactionId; }
        public void setFromTransactionId(String fromTransactionId) { this.fromTransactionId = fromTransactionId; }
        public String getFromProgram() { return fromProgram; }
        public void setFromProgram(String fromProgram) { this.fromProgram = fromProgram; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public boolean isComingSoon() { return comingSoon; }
        public void setComingSoon(boolean comingSoon) { this.comingSoon = comingSoon; }
    }

    /**
     * Menu navigation response DTO for sign-on screen return.
     */
    public static class MenuNavigationResponse {
        private boolean success;
        private String message;
        private String errorCode;
        private String targetProgram;
        private String targetEndpoint;
        private String fromTransactionId;
        private String fromProgram;
        private String sessionId;

        public static MenuNavigationResponse success(String targetProgram, String targetEndpoint, String message) {
            MenuNavigationResponse response = new MenuNavigationResponse();
            response.success = true;
            response.targetProgram = targetProgram;
            response.targetEndpoint = targetEndpoint;
            response.message = message;
            return response;
        }

        public static MenuNavigationResponse error(String message, String errorCode) {
            MenuNavigationResponse response = new MenuNavigationResponse();
            response.success = false;
            response.message = message;
            response.errorCode = errorCode;
            return response;
        }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
        public String getTargetProgram() { return targetProgram; }
        public void setTargetProgram(String targetProgram) { this.targetProgram = targetProgram; }
        public String getTargetEndpoint() { return targetEndpoint; }
        public void setTargetEndpoint(String targetEndpoint) { this.targetEndpoint = targetEndpoint; }
        public String getFromTransactionId() { return fromTransactionId; }
        public void setFromTransactionId(String fromTransactionId) { this.fromTransactionId = fromTransactionId; }
        public String getFromProgram() { return fromProgram; }
        public void setFromProgram(String fromProgram) { this.fromProgram = fromProgram; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }
}
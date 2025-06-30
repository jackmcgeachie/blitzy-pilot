/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.transaction;

import com.carddemo.auth.AuthenticationService;
import com.carddemo.session.SessionManagementService;
import com.carddemo.transaction.TransactionDto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Transaction Management REST Controller
 * 
 * <p><strong>Legacy System Migration:</strong></p>
 * <p>This controller replaces three COBOL transaction processing programs with modern 
 * Spring Boot REST endpoints while maintaining 1-to-1 functional compatibility:</p>
 * <ul>
 *   <li>COTRN00C.cbl - Transaction List Processing → GET /api/v1/transactions</li>
 *   <li>COTRN01C.cbl - Transaction Detail View → GET /api/v1/transactions/{id}</li>
 *   <li>COTRN02C.cbl - Transaction Creation → POST /api/v1/transactions</li>
 * </ul>
 * 
 * <p><strong>COBOL Program References:</strong></p>
 * <ul>
 *   <li>COTRN00C.cbl - List Transactions from TRANSACT file (Transaction ID: CT00)</li>
 *   <li>COTRN01C.cbl - View Transaction Detail from TRANSACT file (Transaction ID: CT01)</li>
 *   <li>COTRN02C.cbl - Add new Transaction to TRANSACT file (Transaction ID: CT02)</li>
 * </ul>
 * 
 * <p><strong>Key Migration Points:</strong></p>
 * <ul>
 *   <li>CICS transaction codes (CT00, CT01, CT02) → REST API endpoints with versioning</li>
 *   <li>BMS screen maps (COTRN00.bms, COTRN01.bms, COTRN02.bms) → JSON DTOs with validation</li>
 *   <li>VSAM TRANSACT file operations → TransactionService with JPA repository access</li>
 *   <li>CICS COMMAREA → Redis-backed session management with user context</li>
 *   <li>COBOL field validation → Bean Validation annotations with custom error handling</li>
 *   <li>CICS response codes → HTTP status codes with standardized error responses</li>
 * </ul>
 * 
 * <p><strong>Session Management Implementation:</strong></p>
 * <p>Maintains pseudo-conversational state through Redis-backed session attributes that 
 * replicate CICS COMMAREA structure, preserving user context across stateless REST calls 
 * per Section 0.3.1 implementation strategy.</p>
 * 
 * <p><strong>Pagination and Navigation:</strong></p>
 * <p>Implements paginated transaction listing with 10 transactions per page (matching 
 * COBOL screen layout), supporting forward/backward navigation equivalent to CICS 
 * PF7/PF8 key processing in COTRN00C.cbl.</p>
 * 
 * <p><strong>Security and Authorization:</strong></p>
 * <p>Spring Security integration with JWT token validation and role-based access control
 * replacing RACF authorization patterns from mainframe environment.</p>
 * 
 * @author CardDemo Migration Team - Blitzy agent
 * @version 1.0
 * @since Spring Boot 3.2.5, Java 21
 */
@RestController
@RequestMapping("/api/v1/transactions")
@CrossOrigin(origins = {"http://localhost:3000", "https://*.carddemo.internal"})
public class TransactionController {

    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);

    // COBOL program identifiers and transaction codes (mapped from source programs)
    private static final String TRANSACTION_LIST_PROGRAM = "COTRN00C";      // From COTRN00C.cbl line 36
    private static final String TRANSACTION_DETAIL_PROGRAM = "COTRN01C";    // From COTRN01C.cbl line 36
    private static final String TRANSACTION_ADD_PROGRAM = "COTRN02C";       // From COTRN02C.cbl line 36
    private static final String TRANSACTION_LIST_ID = "CT00";               // From COTRN00C.cbl line 37
    private static final String TRANSACTION_DETAIL_ID = "CT01";             // From COTRN01C.cbl line 37
    private static final String TRANSACTION_ADD_ID = "CT02";                // From COTRN02C.cbl line 37

    // Error messages matching COBOL error handling (from source analysis)
    private static final String ERROR_INVALID_KEY = "Invalid key pressed. Please try again...";
    private static final String ERROR_TRANSACTION_NOT_FOUND = "Transaction ID NOT found...";
    private static final String ERROR_UNABLE_TO_LOOKUP = "Unable to lookup transaction...";
    private static final String ERROR_NUMERIC_REQUIRED = "Transaction ID must be Numeric...";
    private static final String ERROR_CARD_NOT_FOUND = "Card Number NOT found...";
    private static final String ERROR_ACCOUNT_NOT_FOUND = "Account ID NOT found...";
    private static final String ERROR_INVALID_SELECTION = "Invalid selection. Valid value is S";
    private static final String ERROR_AT_TOP_OF_PAGE = "You are already at the top of the page...";
    private static final String ERROR_AT_BOTTOM_OF_PAGE = "You are already at the bottom of the page...";

    // Session attribute keys for COMMAREA replication
    private static final String SESSION_TRANSACTION_PAGE = "CDEMO_CT_PAGE_NUM";
    private static final String SESSION_TRANSACTION_FIRST_ID = "CDEMO_CT_TRNID_FIRST";
    private static final String SESSION_TRANSACTION_LAST_ID = "CDEMO_CT_TRNID_LAST";
    private static final String SESSION_TRANSACTION_NEXT_PAGE = "CDEMO_CT_NEXT_PAGE_FLG";
    private static final String SESSION_FROM_PROGRAM = "CDEMO_FROM_PROGRAM";
    private static final String SESSION_USER_CONTEXT = "CDEMO_USER_CONTEXT";

    // Default pagination settings matching COBOL screen layout (10 rows per page)
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    // API version for backward compatibility
    private static final String API_VERSION = "v1";

    // Dependencies
    private final TransactionService transactionService;
    private final SessionManagementService sessionManagementService;
    private final AuthenticationService authenticationService;

    /**
     * Constructor with dependency injection for all required services.
     * 
     * @param transactionService Business logic service for transaction operations
     * @param sessionManagementService Redis-backed session management service
     * @param authenticationService Spring Security authentication service
     */
    @Autowired
    public TransactionController(
            TransactionService transactionService,
            SessionManagementService sessionManagementService,
            AuthenticationService authenticationService) {
        this.transactionService = transactionService;
        this.sessionManagementService = sessionManagementService;
        this.authenticationService = authenticationService;
        
        logger.info("TransactionController initialized - replacing COTRN00C/COTRN01C/COTRN02C programs");
    }

    /**
     * Get paginated list of transactions for a card.
     * 
     * <p>Replaces COTRN00C.cbl - List Transactions from TRANSACT file</p>
     * <p>Maps to BMS screen COTRN00.bms with 10-row pagination support</p>
     * 
     * <p><strong>COBOL Equivalent Logic:</strong></p>
     * <ul>
     *   <li>PROCESS-ENTER-KEY paragraph (lines 146-230)</li>
     *   <li>PROCESS-PAGE-FORWARD paragraph (lines 279-328)</li>
     *   <li>PROCESS-PAGE-BACKWARD paragraph (lines 333-376)</li>
     *   <li>POPULATE-TRAN-DATA paragraph (lines 381-446)</li>
     * </ul>
     * 
     * @param cardNumber 16-digit card number (validates numeric, exactly 16 digits)
     * @param page Page number (0-based, default 0)
     * @param size Page size (1-100, default 10)
     * @param sortBy Sort field (transactionId, transactionDate, amount, merchantName)
     * @param sortDirection Sort direction (asc or desc, default desc)
     * @param request HTTP servlet request for session management
     * @param response HTTP servlet response for additional headers
     * @return Paginated transaction list response with navigation metadata
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<TransactionListResponse> getTransactions(
            @RequestParam("cardNumber") 
            @Pattern(regexp = "^[0-9]{16}$", message = "Card number must be exactly 16 digits")
            String cardNumber,
            
            @RequestParam(value = "page", defaultValue = "0") 
            Integer page,
            
            @RequestParam(value = "size", defaultValue = "10") 
            Integer size,
            
            @RequestParam(value = "sortBy", defaultValue = "transactionDate") 
            @Pattern(regexp = "^(transactionId|transactionDate|amount|merchantName)$",
                    message = "Sort field must be one of: transactionId, transactionDate, amount, merchantName")
            String sortBy,
            
            @RequestParam(value = "sortDirection", defaultValue = "desc") 
            @Pattern(regexp = "^(asc|desc)$", message = "Sort direction must be 'asc' or 'desc'")
            String sortDirection,
            
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.debug("Processing transaction list request - cardNumber: {}, page: {}, size: {}", 
                    cardNumber, page, size);

        try {
            // Session management - setup COMMAREA equivalent context
            setupTransactionSessionContext(request, TRANSACTION_LIST_PROGRAM, TRANSACTION_LIST_ID);

            // Validate pagination parameters
            if (page < 0) {
                page = 0;
            }
            if (size <= 0 || size > MAX_PAGE_SIZE) {
                size = DEFAULT_PAGE_SIZE;
            }

            // Create pageable with sorting (replicating COBOL STARTBR/READNEXT logic)
            Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            // Delegate to service layer for business logic processing
            Page<TransactionSummary> transactionPage = transactionService.getTransactionsByCardNumber(
                cardNumber, pageable);

            // Build response with pagination metadata
            TransactionListResponse response_data = new TransactionListResponse();
            response_data.setTransactions(transactionPage.getContent());
            response_data.setPageNumber(transactionPage.getNumber());
            response_data.setPageSize(transactionPage.getSize());
            response_data.setTotalElements(transactionPage.getTotalElements());
            response_data.setTotalPages(transactionPage.getTotalPages());
            response_data.setHasNext(transactionPage.hasNext());
            response_data.setHasPrevious(transactionPage.hasPrevious());

            // Update session with pagination state (COMMAREA preservation)
            updatePaginationSessionState(request, transactionPage, cardNumber);

            // Set response headers for API versioning and caching
            response.setHeader("API-Version", API_VERSION);
            response.setHeader("Transaction-Program", TRANSACTION_LIST_PROGRAM);
            response.setHeader("Transaction-ID", TRANSACTION_LIST_ID);
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

            logger.info("Transaction list retrieved successfully - cardNumber: {}, page: {}, totalElements: {}", 
                       cardNumber, page, transactionPage.getTotalElements());

            return ResponseEntity.ok(response_data);

        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid request parameters for transaction list - cardNumber: {}, error: {}", 
                       cardNumber, ex.getMessage());
            throw new TransactionProcessingException("Invalid request parameters: " + ex.getMessage(), 
                                                   "INVALID_PARAMETERS", HttpStatus.BAD_REQUEST);

        } catch (Exception ex) {
            logger.error("System error retrieving transaction list - cardNumber: {}, error: {}", 
                        cardNumber, ex.getMessage(), ex);
            throw new TransactionProcessingException(ERROR_UNABLE_TO_LOOKUP, 
                                                   "SYSTEM_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get detailed information for a specific transaction.
     * 
     * <p>Replaces COTRN01C.cbl - View Transaction Detail from TRANSACT file</p>
     * <p>Maps to BMS screen COTRN01.bms with comprehensive transaction details</p>
     * 
     * <p><strong>COBOL Equivalent Logic:</strong></p>
     * <ul>
     *   <li>PROCESS-ENTER-KEY paragraph (lines 144-192)</li>
     *   <li>READ-TRANSACT-FILE paragraph (lines 267-296)</li>
     *   <li>Field population from TRAN-RECORD (lines 177-190)</li>
     * </ul>
     * 
     * @param transactionId 16-digit transaction ID (validates numeric format)
     * @param request HTTP servlet request for session management
     * @param response HTTP servlet response for additional headers
     * @return Complete transaction detail information
     */
    @GetMapping(value = "/{transactionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<TransactionDetail> getTransactionDetail(
            @PathVariable("transactionId") 
            @Pattern(regexp = "^[0-9]{1,16}$", message = "Transaction ID must be numeric and up to 16 digits")
            String transactionId,
            
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.debug("Processing transaction detail request - transactionId: {}", transactionId);

        try {
            // Session management - setup COMMAREA equivalent context
            setupTransactionSessionContext(request, TRANSACTION_DETAIL_PROGRAM, TRANSACTION_DETAIL_ID);

            // Input validation (replicating COBOL field validation)
            if (transactionId == null || transactionId.trim().isEmpty()) {
                logger.warn("Transaction detail request validation failed: transaction ID is empty");
                throw new TransactionProcessingException("Transaction ID can NOT be empty...", 
                                                       "TRANSACTION_ID_REQUIRED", HttpStatus.BAD_REQUEST);
            }

            // Validate numeric format (matching COBOL numeric validation)
            if (!transactionId.matches("^[0-9]+$")) {
                logger.warn("Transaction detail request validation failed: transaction ID not numeric - {}", 
                           transactionId);
                throw new TransactionProcessingException(ERROR_NUMERIC_REQUIRED, 
                                                       "TRANSACTION_ID_INVALID", HttpStatus.BAD_REQUEST);
            }

            // Delegate to service layer for transaction lookup
            Optional<TransactionDetail> transactionDetailOpt = transactionService.getTransactionDetail(transactionId);

            if (!transactionDetailOpt.isPresent()) {
                logger.warn("Transaction not found - transactionId: {}", transactionId);
                throw new TransactionProcessingException(ERROR_TRANSACTION_NOT_FOUND, 
                                                       "TRANSACTION_NOT_FOUND", HttpStatus.NOT_FOUND);
            }

            TransactionDetail transactionDetail = transactionDetailOpt.get();

            // Update session with transaction context
            updateTransactionDetailSessionState(request, transactionDetail);

            // Set response headers for API versioning and transaction context
            response.setHeader("API-Version", API_VERSION);
            response.setHeader("Transaction-Program", TRANSACTION_DETAIL_PROGRAM);
            response.setHeader("Transaction-ID", TRANSACTION_DETAIL_ID);
            response.setHeader("Last-Modified", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            logger.info("Transaction detail retrieved successfully - transactionId: {}, cardNumber: {}", 
                       transactionId, transactionDetail.getCardNumber());

            return ResponseEntity.ok(transactionDetail);

        } catch (TransactionProcessingException ex) {
            // Re-throw application exceptions with proper HTTP status
            throw ex;

        } catch (Exception ex) {
            logger.error("System error retrieving transaction detail - transactionId: {}, error: {}", 
                        transactionId, ex.getMessage(), ex);
            throw new TransactionProcessingException(ERROR_UNABLE_TO_LOOKUP, 
                                                   "SYSTEM_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Create a new transaction.
     * 
     * <p>Replaces COTRN02C.cbl - Add new Transaction to TRANSACT file</p>
     * <p>Maps to BMS screen COTRN02.bms with comprehensive field validation</p>
     * 
     * <p><strong>COBOL Equivalent Logic:</strong></p>
     * <ul>
     *   <li>PROCESS-ENTER-KEY paragraph (lines 164-188)</li>
     *   <li>VALIDATE-INPUT-KEY-FIELDS paragraph (lines 193-230)</li>
     *   <li>VALIDATE-INPUT-DATA-FIELDS paragraph (lines 235-437)</li>
     *   <li>ADD-TRANSACTION paragraph (lines 442-466)</li>
     * </ul>
     * 
     * @param createRequest Transaction creation request with validation
     * @param bindingResult Validation results from Bean Validation
     * @param request HTTP servlet request for session management
     * @param response HTTP servlet response for additional headers
     * @return Transaction creation response with new transaction ID
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<TransactionCreateResponse> createTransaction(
            @Valid @RequestBody TransactionCreateRequest createRequest,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response) {

        logger.debug("Processing transaction creation request - cardNumber: {}, amount: {}", 
                    createRequest.getCardNumber(), createRequest.getTransactionAmount());

        try {
            // Session management - setup COMMAREA equivalent context
            setupTransactionSessionContext(request, TRANSACTION_ADD_PROGRAM, TRANSACTION_ADD_ID);

            // Validate request payload (comprehensive COBOL-style validation)
            if (bindingResult.hasErrors()) {
                logger.warn("Transaction creation validation failed - {} errors", bindingResult.getErrorCount());
                throw new TransactionValidationException("Input validation failed", buildFieldErrors(bindingResult));
            }

            // Additional business validation (replicating COBOL validation logic)
            validateTransactionCreateRequest(createRequest);

            // Delegate to service layer for transaction creation
            TransactionCreateResponse createResponse = transactionService.createTransaction(createRequest);

            // Update session with successful transaction context
            updateTransactionCreateSessionState(request, createResponse);

            // Set response headers for API versioning and creation context
            response.setHeader("API-Version", API_VERSION);
            response.setHeader("Transaction-Program", TRANSACTION_ADD_PROGRAM);
            response.setHeader("Transaction-ID", TRANSACTION_ADD_ID);
            response.setHeader("Location", "/api/v1/transactions/" + createResponse.getTransactionId());

            logger.info("Transaction created successfully - transactionId: {}, cardNumber: {}, amount: {}", 
                       createResponse.getTransactionId(), createRequest.getCardNumber(), 
                       createRequest.getTransactionAmount());

            return ResponseEntity.status(HttpStatus.CREATED).body(createResponse);

        } catch (TransactionValidationException ex) {
            // Handle validation errors with detailed field information
            logger.warn("Transaction creation validation error - cardNumber: {}, errors: {}", 
                       createRequest.getCardNumber(), ex.getFieldErrors().size());
            throw ex;

        } catch (TransactionProcessingException ex) {
            // Re-throw application exceptions with proper HTTP status
            throw ex;

        } catch (Exception ex) {
            logger.error("System error creating transaction - cardNumber: {}, error: {}", 
                        createRequest.getCardNumber(), ex.getMessage(), ex);
            throw new TransactionProcessingException("Unable to add transaction...", 
                                                   "SYSTEM_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Global exception handler for transaction processing errors.
     * 
     * <p>Maps COBOL error codes to standardized HTTP status codes per Section 0.2.2</p>
     * 
     * @param ex Transaction processing exception
     * @return Standardized error response with COBOL-equivalent error codes
     */
    @ExceptionHandler(TransactionProcessingException.class)
    public ResponseEntity<TransactionErrorResponse> handleTransactionProcessingException(
            TransactionProcessingException ex) {
        
        logger.warn("Transaction processing exception: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        TransactionErrorResponse errorResponse = new TransactionErrorResponse(ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
    }

    /**
     * Global exception handler for transaction validation errors.
     * 
     * <p>Provides detailed field-level validation errors matching COBOL field validation</p>
     * 
     * @param ex Transaction validation exception
     * @return Detailed validation error response with field-specific errors
     */
    @ExceptionHandler(TransactionValidationException.class)
    public ResponseEntity<TransactionErrorResponse> handleTransactionValidationException(
            TransactionValidationException ex) {
        
        logger.warn("Transaction validation exception: {} field errors", ex.getFieldErrors().size());
        
        TransactionErrorResponse errorResponse = new TransactionErrorResponse("VALIDATION_ERROR", ex.getMessage());
        errorResponse.setFieldErrors(ex.getFieldErrors());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Global exception handler for method argument validation errors.
     * 
     * <p>Handles Bean Validation annotations on controller method parameters</p>
     * 
     * @param ex Method argument not valid exception
     * @return Standardized validation error response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<TransactionErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex) {
        
        logger.warn("Method argument validation exception: {} field errors", ex.getBindingResult().getErrorCount());
        
        TransactionErrorResponse errorResponse = new TransactionErrorResponse("VALIDATION_ERROR", 
                                                                            "Request validation failed");
        errorResponse.setFieldErrors(buildFieldErrors(ex.getBindingResult()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Global exception handler for unexpected system errors.
     * 
     * <p>Provides safe error response without exposing internal system details</p>
     * 
     * @param ex General exception
     * @return Generic system error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<TransactionErrorResponse> handleGeneralException(Exception ex) {
        
        logger.error("Unexpected system error in transaction controller: {}", ex.getMessage(), ex);
        
        TransactionErrorResponse errorResponse = new TransactionErrorResponse("SYSTEM_ERROR", 
                                                                            "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    // === Private Helper Methods ===

    /**
     * Sets up session context for transaction processing (COMMAREA replication).
     * 
     * <p>Replicates CICS COMMAREA structure setup from COBOL programs:</p>
     * <ul>
     *   <li>MOVE WS-TRANID TO CDEMO-FROM-TRANID</li>
     *   <li>MOVE WS-PGMNAME TO CDEMO-FROM-PROGRAM</li>
     *   <li>SET CDEMO-PGM-REENTER TO TRUE</li>
     * </ul>
     * 
     * @param request HTTP servlet request for session access
     * @param programName COBOL program name equivalent
     * @param transactionId CICS transaction ID equivalent
     */
    private void setupTransactionSessionContext(HttpServletRequest request, String programName, String transactionId) {
        HttpSession session = request.getSession(true);
        
        // Setup session attributes matching COMMAREA structure
        session.setAttribute(SESSION_FROM_PROGRAM, programName);
        session.setAttribute("CDEMO_FROM_TRANID", transactionId);
        session.setAttribute("CDEMO_PGM_REENTER", true);
        session.setAttribute("CDEMO_LAST_ACCESS", LocalDateTime.now());
        
        logger.debug("Session context established - program: {}, transactionId: {}, sessionId: {}", 
                    programName, transactionId, session.getId());
    }

    /**
     * Updates session state with pagination information (COMMAREA preservation).
     * 
     * <p>Replicates COBOL pagination state from COTRN00C.cbl:</p>
     * <ul>
     *   <li>CDEMO-CT00-PAGE-NUM</li>
     *   <li>CDEMO-CT00-TRNID-FIRST</li>
     *   <li>CDEMO-CT00-TRNID-LAST</li>
     *   <li>CDEMO-CT00-NEXT-PAGE-FLG</li>
     * </ul>
     */
    private void updatePaginationSessionState(HttpServletRequest request, Page<TransactionSummary> transactionPage, 
                                            String cardNumber) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(SESSION_TRANSACTION_PAGE, transactionPage.getNumber());
            session.setAttribute(SESSION_TRANSACTION_NEXT_PAGE, transactionPage.hasNext());
            session.setAttribute("CDEMO_CARD_NUMBER", cardNumber);
            
            // Set first and last transaction IDs for navigation context
            List<TransactionSummary> transactions = transactionPage.getContent();
            if (!transactions.isEmpty()) {
                session.setAttribute(SESSION_TRANSACTION_FIRST_ID, transactions.get(0).getTransactionId());
                session.setAttribute(SESSION_TRANSACTION_LAST_ID, 
                                   transactions.get(transactions.size() - 1).getTransactionId());
            }
        }
    }

    /**
     * Updates session state with transaction detail information.
     */
    private void updateTransactionDetailSessionState(HttpServletRequest request, TransactionDetail transactionDetail) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute("CDEMO_SELECTED_TRANSACTION", transactionDetail.getTransactionId());
            session.setAttribute("CDEMO_SELECTED_CARD", transactionDetail.getCardNumber());
            session.setAttribute("CDEMO_DETAIL_ACCESS_TIME", LocalDateTime.now());
        }
    }

    /**
     * Updates session state with transaction creation information.
     */
    private void updateTransactionCreateSessionState(HttpServletRequest request, TransactionCreateResponse createResponse) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute("CDEMO_CREATED_TRANSACTION", createResponse.getTransactionId());
            session.setAttribute("CDEMO_CREATE_TIME", createResponse.getProcessingTimestamp());
            session.setAttribute("CDEMO_LAST_OPERATION", "CREATE_TRANSACTION");
        }
    }

    /**
     * Validates transaction creation request with business rules.
     * 
     * <p>Replicates comprehensive COBOL validation from COTRN02C.cbl:</p>
     * <ul>
     *   <li>VALIDATE-INPUT-KEY-FIELDS (lines 193-230)</li>
     *   <li>VALIDATE-INPUT-DATA-FIELDS (lines 235-437)</li>
     * </ul>
     */
    private void validateTransactionCreateRequest(TransactionCreateRequest createRequest) {
        List<String> errors = new ArrayList<>();

        // Validate transaction type code format (replicating COBOL validation)
        if (createRequest.getTransactionTypeCode() != null && 
            !createRequest.getTransactionTypeCode().matches("^[0-9]{2}$")) {
            errors.add("Transaction type code must be exactly 2 numeric digits");
        }

        // Validate merchant ID format (replicating COBOL validation)
        if (createRequest.getMerchantId() != null && createRequest.getMerchantId() <= 0) {
            errors.add("Merchant ID must be a positive number");
        }

        // Validate merchant ZIP format (US ZIP code validation)
        if (createRequest.getMerchantZip() != null && 
            !createRequest.getMerchantZip().matches("^[0-9]{5}(-[0-9]{4})?$")) {
            errors.add("Merchant ZIP must be in format 12345 or 12345-6789");
        }

        if (!errors.isEmpty()) {
            throw new TransactionProcessingException("Business validation failed: " + String.join(", ", errors), 
                                                   "BUSINESS_VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Builds field error list from Spring validation binding result.
     */
    private List<TransactionDto.FieldError> buildFieldErrors(BindingResult bindingResult) {
        List<TransactionDto.FieldError> fieldErrors = new ArrayList<>();
        
        for (FieldError error : bindingResult.getFieldErrors()) {
            fieldErrors.add(new TransactionDto.FieldError(
                error.getField(),
                error.getRejectedValue(),
                error.getDefaultMessage()
            ));
        }
        
        return fieldErrors;
    }

    // === Custom Exception Classes ===

    /**
     * Custom exception for transaction processing errors with COBOL error code mapping.
     */
    public static class TransactionProcessingException extends RuntimeException {
        private final String errorCode;
        private final HttpStatus httpStatus;

        public TransactionProcessingException(String message, String errorCode, HttpStatus httpStatus) {
            super(message);
            this.errorCode = errorCode;
            this.httpStatus = httpStatus;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public HttpStatus getHttpStatus() {
            return httpStatus;
        }
    }

    /**
     * Custom exception for transaction validation errors with field-level details.
     */
    public static class TransactionValidationException extends RuntimeException {
        private final List<TransactionDto.FieldError> fieldErrors;

        public TransactionValidationException(String message, List<TransactionDto.FieldError> fieldErrors) {
            super(message);
            this.fieldErrors = fieldErrors;
        }

        public List<TransactionDto.FieldError> getFieldErrors() {
            return fieldErrors;
        }
    }
}
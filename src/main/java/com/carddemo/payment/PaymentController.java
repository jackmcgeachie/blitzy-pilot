/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.payment;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;

import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolationException;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.carddemo.payment.PaymentService;
import com.carddemo.payment.PaymentRequest;
import com.carddemo.payment.PaymentResponse;
import com.carddemo.audit.AuditService;

/**
 * REST controller providing HTTP endpoints for payment processing functionality,
 * converting CICS transaction-based interfaces to modern RESTful APIs with JSON
 * payloads, comprehensive validation, and Spring Security integration.
 * 
 * This controller implements the complete payment processing workflow from the
 * original COBOL program COBIL00C.cbl, maintaining identical business logic
 * while providing modern REST API interfaces for React frontend integration.
 * 
 * Original COBOL Program Reference: COBIL00C.cbl
 * Original BMS Screen Reference: COBIL00.bms
 * 
 * Key COBOL Sections Migrated:
 * - MAIN-PARA: Request routing and response handling
 * - PROCESS-ENTER-KEY: Input validation and payment processing workflow
 * - SEND-BILLPAY-SCREEN: Response formatting and error handling
 * - RECEIVE-BILLPAY-SCREEN: Request parameter extraction and validation
 * 
 * Business Logic Preservation:
 * - Account ID validation must be 11 digits (COBOL PIC 9(11))
 * - Payment confirmation flag validation (Y/N) matching COBOL logic
 * - Balance validation ensures positive balance before payment
 * - Transaction type '02' and category '2' for bill payments
 * - Atomic account balance updates with comprehensive error handling
 * - COBOL-equivalent error messages for user experience consistency
 * 
 * REST API Design:
 * - POST /api/v1/payments - Process payment with confirmation
 * - GET /api/v1/payments/account/{accountId} - Retrieve account balance
 * - Comprehensive HTTP status code mapping from CICS response codes
 * - JSON request/response payloads with field validation
 * - Spring Security JWT authentication for all endpoints
 * 
 * Security Features:
 * - Spring Security @PreAuthorize annotations for role-based access
 * - Comprehensive audit logging through AuditService integration
 * - Input validation using Bean Validation API
 * - Transaction boundary management with automatic rollback
 * - Session correlation through Spring Security context
 * 
 * Performance Requirements:
 * - Payment processing response times under 200ms
 * - Concurrent payment processing with optimistic locking
 * - Comprehensive error handling with appropriate HTTP status codes
 * - Detailed audit logging for all payment operations
 * 
 * @author Blitzy agent
 * @version 1.0
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/api/v1/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    // COBOL-equivalent error messages for consistent user experience
    private static final String ERROR_ACCOUNT_ID_REQUIRED = "Account ID is required";
    private static final String ERROR_INVALID_ACCOUNT_ID = "Account ID must be exactly 11 digits";
    private static final String ERROR_PAYMENT_CONFIRMATION_REQUIRED = "Payment confirmation is required";
    private static final String ERROR_INVALID_CONFIRMATION = "Payment confirmation must be Y or N";
    private static final String ERROR_PAYMENT_AMOUNT_REQUIRED = "Payment amount is required";
    private static final String ERROR_INVALID_PAYMENT_AMOUNT = "Payment amount must be greater than zero";
    private static final String ERROR_ACCOUNT_NOT_FOUND = "Account ID NOT found...";
    private static final String ERROR_PAYMENT_PROCESSING_FAILED = "Unable to process payment...";
    private static final String ERROR_UNAUTHORIZED_ACCESS = "Unauthorized access to payment processing";
    private static final String ERROR_VALIDATION_FAILED = "Request validation failed";
    private static final String ERROR_INTERNAL_SERVER_ERROR = "Internal server error during payment processing";

    // Spring-managed service dependencies
    private final PaymentService paymentService;
    private final AuditService auditService;

    /**
     * Constructor with dependency injection for all required services.
     * 
     * @param paymentService Core payment processing service
     * @param auditService Comprehensive audit logging service
     */
    @Autowired
    public PaymentController(PaymentService paymentService, AuditService auditService) {
        this.paymentService = paymentService;
        this.auditService = auditService;
    }

    /**
     * Processes a payment request with comprehensive validation and transaction management.
     * 
     * This endpoint implements the complete payment workflow from COBOL PROCESS-ENTER-KEY
     * section, including input validation, account verification, payment confirmation,
     * and atomic transaction processing.
     * 
     * Original COBOL Logic Reference (COBIL00C.cbl lines 154-244):
     * - Input validation: Account ID and confirmation flag validation
     * - Account balance verification: Ensures positive balance exists
     * - Payment confirmation: Handles Y/N confirmation workflow
     * - Transaction creation: Creates payment transaction record
     * - Account balance update: Atomically updates account balance
     * 
     * HTTP Method: POST
     * Endpoint: /api/v1/payments
     * Content-Type: application/json
     * 
     * Request Body: PaymentRequest JSON object containing:
     * - accountId: 11-digit account identifier (required)
     * - paymentConfirmation: Y/N confirmation flag (required)
     * - paymentAmount: Payment amount (optional, defaults to current balance)
     * 
     * Response: PaymentResponse JSON object containing:
     * - transactionId: Generated transaction identifier
     * - paymentStatus: SUCCESS, FAILURE, or CONFIRMATION_REQUIRED
     * - paymentAmount: Processed payment amount
     * - updatedAccountBalance: Updated account balance after payment
     * - processingTimestamp: Payment processing timestamp
     * - errorMessage: Error message if processing failed
     * 
     * Security: Requires authentication with ROLE_USER or ROLE_ADMIN authority
     * 
     * @param paymentRequest Payment request with account ID and confirmation
     * @return ResponseEntity with PaymentResponse and appropriate HTTP status
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Transactional
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody PaymentRequest paymentRequest) {
        logger.info("Processing payment request for account: {}", paymentRequest.getAccountId());
        
        try {
            // Log payment request event for audit trail
            auditService.logTransactionEvent(
                getCurrentUsername(),
                "PAYMENT_REQUEST_RECEIVED",
                "PENDING",
                paymentRequest.getPaymentAmount() != null ? paymentRequest.getPaymentAmount().doubleValue() : 0.0,
                createPaymentAuditDetails(paymentRequest)
            );
            
            // Validate payment request parameters
            PaymentResponse validationResult = validatePaymentRequest(paymentRequest);
            if (!validationResult.isSuccess()) {
                logger.warn("Payment request validation failed for account: {}", paymentRequest.getAccountId());
                
                // Log validation failure event
                auditService.logSecurityEvent(
                    "PAYMENT_VALIDATION_FAILURE",
                    "MEDIUM",
                    "Payment validation failed: " + validationResult.getErrorMessage(),
                    createPaymentAuditDetails(paymentRequest)
                );
                
                return ResponseEntity.badRequest().body(validationResult);
            }
            
            // Process payment through service layer
            PaymentResponse paymentResponse = paymentService.processPayment(paymentRequest);
            
            // Determine HTTP status code based on payment status
            HttpStatus responseStatus = determineHttpStatus(paymentResponse.getPaymentStatus());
            
            // Log payment processing completion
            auditService.logTransactionEvent(
                getCurrentUsername(),
                "PAYMENT_PROCESSING_COMPLETED",
                paymentResponse.getPaymentStatus(),
                paymentResponse.getPaymentAmount() != null ? paymentResponse.getPaymentAmount().doubleValue() : 0.0,
                createPaymentResponseAuditDetails(paymentResponse)
            );
            
            logger.info("Payment processing completed for account: {} with status: {}", 
                       paymentRequest.getAccountId(), paymentResponse.getPaymentStatus());
            
            return ResponseEntity.status(responseStatus).body(paymentResponse);
            
        } catch (EntityNotFoundException e) {
            logger.error("Account not found during payment processing: {}", e.getMessage());
            
            PaymentResponse errorResponse = PaymentResponse.builder()
                .paymentStatus("FAILURE")
                .errorMessage(ERROR_ACCOUNT_NOT_FOUND)
                .processingTimestamp(LocalDateTime.now())
                .build();
            
            // Log account not found security event
            auditService.logSecurityEvent(
                "PAYMENT_ACCOUNT_NOT_FOUND",
                "MEDIUM",
                "Account not found during payment: " + paymentRequest.getAccountId(),
                createPaymentAuditDetails(paymentRequest)
            );
            
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            
        } catch (DataAccessException e) {
            logger.error("Database error during payment processing: {}", e.getMessage());
            
            PaymentResponse errorResponse = PaymentResponse.builder()
                .paymentStatus("FAILURE")
                .errorMessage(ERROR_PAYMENT_PROCESSING_FAILED)
                .processingTimestamp(LocalDateTime.now())
                .build();
            
            // Log database error security event
            auditService.logSecurityEvent(
                "PAYMENT_DATABASE_ERROR",
                "HIGH",
                "Database error during payment: " + e.getMessage(),
                createPaymentAuditDetails(paymentRequest)
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            
        } catch (Exception e) {
            logger.error("Unexpected error during payment processing: {}", e.getMessage(), e);
            
            PaymentResponse errorResponse = PaymentResponse.builder()
                .paymentStatus("FAILURE")
                .errorMessage(ERROR_INTERNAL_SERVER_ERROR)
                .processingTimestamp(LocalDateTime.now())
                .build();
            
            // Log unexpected error security event
            auditService.logSecurityEvent(
                "PAYMENT_PROCESSING_ERROR",
                "HIGH",
                "Unexpected error during payment: " + e.getMessage(),
                createPaymentAuditDetails(paymentRequest)
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Retrieves current account balance for payment validation and display.
     * 
     * This endpoint implements account balance retrieval functionality for
     * payment confirmation screens, equivalent to the COBOL READ-ACCTDAT-FILE
     * section that validates account existence and retrieves current balance.
     * 
     * Original COBOL Logic Reference (COBIL00C.cbl lines 341-372):
     * - Account validation: Verifies account existence
     * - Balance retrieval: Gets current account balance
     * - Error handling: Provides appropriate error responses
     * 
     * HTTP Method: GET
     * Endpoint: /api/v1/payments/account/{accountId}
     * 
     * Path Parameters:
     * - accountId: 11-digit account identifier (required)
     * 
     * Response: JSON object containing:
     * - accountId: Account identifier
     * - currentBalance: Current account balance
     * - retrievalTimestamp: Balance retrieval timestamp
     * - errorMessage: Error message if retrieval failed
     * 
     * Security: Requires authentication with ROLE_USER or ROLE_ADMIN authority
     * 
     * @param accountId 11-digit account identifier
     * @return ResponseEntity with account balance information
     */
    @GetMapping("/account/{accountId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getAccountBalance(@PathVariable String accountId) {
        logger.info("Retrieving account balance for account: {}", accountId);
        
        try {
            // Validate account ID format
            if (accountId == null || !accountId.matches("^\\d{11}$")) {
                logger.warn("Invalid account ID format: {}", accountId);
                
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("accountId", accountId);
                errorResponse.put("errorMessage", ERROR_INVALID_ACCOUNT_ID);
                errorResponse.put("retrievalTimestamp", LocalDateTime.now());
                
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Log account balance retrieval event
            Map<String, Object> auditDetails = new HashMap<>();
            auditDetails.put("account_id", accountId);
            auditDetails.put("username", getCurrentUsername());
            auditDetails.put("operation", "BALANCE_RETRIEVAL");
            
            auditService.logDataAccessEvent(
                getCurrentUsername(),
                "ACCOUNT_BALANCE_RETRIEVAL",
                "ACCOUNT",
                accountId,
                auditDetails
            );
            
            // Retrieve account balance through service layer
            BigDecimal currentBalance = paymentService.getAccountBalance(accountId);
            
            // Create successful response
            Map<String, Object> response = new HashMap<>();
            response.put("accountId", accountId);
            response.put("currentBalance", currentBalance);
            response.put("retrievalTimestamp", LocalDateTime.now());
            
            logger.info("Account balance retrieved successfully for account: {}", accountId);
            
            return ResponseEntity.ok(response);
            
        } catch (EntityNotFoundException e) {
            logger.error("Account not found during balance retrieval: {}", e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("accountId", accountId);
            errorResponse.put("errorMessage", ERROR_ACCOUNT_NOT_FOUND);
            errorResponse.put("retrievalTimestamp", LocalDateTime.now());
            
            // Log account not found security event
            Map<String, Object> auditDetails = new HashMap<>();
            auditDetails.put("account_id", accountId);
            auditDetails.put("username", getCurrentUsername());
            auditDetails.put("error_type", "ACCOUNT_NOT_FOUND");
            
            auditService.logSecurityEvent(
                "BALANCE_RETRIEVAL_ACCOUNT_NOT_FOUND",
                "MEDIUM",
                "Account not found during balance retrieval: " + accountId,
                auditDetails
            );
            
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            
        } catch (Exception e) {
            logger.error("Error retrieving account balance: {}", e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("accountId", accountId);
            errorResponse.put("errorMessage", "Unable to retrieve account balance");
            errorResponse.put("retrievalTimestamp", LocalDateTime.now());
            
            // Log balance retrieval error
            Map<String, Object> auditDetails = new HashMap<>();
            auditDetails.put("account_id", accountId);
            auditDetails.put("username", getCurrentUsername());
            auditDetails.put("error_type", "BALANCE_RETRIEVAL_ERROR");
            
            auditService.logSecurityEvent(
                "BALANCE_RETRIEVAL_ERROR",
                "HIGH",
                "Error retrieving account balance: " + e.getMessage(),
                auditDetails
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Confirms a payment after initial validation and balance verification.
     * 
     * This endpoint implements the payment confirmation workflow for two-phase
     * payment processing, equivalent to the COBOL confirmation logic that
     * processes confirmed payments with atomic transaction creation.
     * 
     * Original COBOL Logic Reference (COBIL00C.cbl lines 210-244):
     * - Payment confirmation: Processes confirmed payments
     * - Transaction creation: Creates payment transaction record
     * - Account balance update: Atomically updates account balance
     * - Success response: Provides transaction confirmation details
     * 
     * HTTP Method: POST
     * Endpoint: /api/v1/payments/confirm
     * Content-Type: application/json
     * 
     * Request Body: PaymentRequest JSON object with confirmed payment details
     * 
     * Response: PaymentResponse JSON object with transaction confirmation
     * 
     * Security: Requires authentication with ROLE_USER or ROLE_ADMIN authority
     * 
     * @param paymentRequest Confirmed payment request
     * @return ResponseEntity with payment confirmation response
     */
    @PostMapping("/confirm")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Transactional
    public ResponseEntity<PaymentResponse> confirmPayment(@Valid @RequestBody PaymentRequest paymentRequest) {
        logger.info("Confirming payment for account: {}", paymentRequest.getAccountId());
        
        try {
            // Log payment confirmation event
            auditService.logTransactionEvent(
                getCurrentUsername(),
                "PAYMENT_CONFIRMATION_RECEIVED",
                "PENDING",
                paymentRequest.getPaymentAmount() != null ? paymentRequest.getPaymentAmount().doubleValue() : 0.0,
                createPaymentAuditDetails(paymentRequest)
            );
            
            // Validate payment confirmation request
            PaymentResponse validationResult = validatePaymentRequest(paymentRequest);
            if (!validationResult.isSuccess()) {
                logger.warn("Payment confirmation validation failed for account: {}", paymentRequest.getAccountId());
                return ResponseEntity.badRequest().body(validationResult);
            }
            
            // Ensure payment confirmation is set to 'Y'
            if (!paymentRequest.isConfirmationYes()) {
                logger.warn("Payment confirmation not set to 'Y' for account: {}", paymentRequest.getAccountId());
                
                PaymentResponse errorResponse = PaymentResponse.builder()
                    .paymentStatus("FAILURE")
                    .errorMessage("Payment confirmation must be 'Y' for confirmation endpoint")
                    .processingTimestamp(LocalDateTime.now())
                    .build();
                
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Process confirmed payment through service layer
            PaymentResponse confirmationResponse = paymentService.processPayment(paymentRequest);
            
            // Log payment confirmation completion
            auditService.logTransactionEvent(
                getCurrentUsername(),
                "PAYMENT_CONFIRMATION_COMPLETED",
                confirmationResponse.getPaymentStatus(),
                confirmationResponse.getPaymentAmount() != null ? confirmationResponse.getPaymentAmount().doubleValue() : 0.0,
                createPaymentResponseAuditDetails(confirmationResponse)
            );
            
            logger.info("Payment confirmation completed for account: {} with status: {}", 
                       paymentRequest.getAccountId(), confirmationResponse.getPaymentStatus());
            
            return ResponseEntity.ok(confirmationResponse);
            
        } catch (Exception e) {
            logger.error("Error confirming payment: {}", e.getMessage(), e);
            
            PaymentResponse errorResponse = PaymentResponse.builder()
                .paymentStatus("FAILURE")
                .errorMessage("Payment confirmation failed")
                .processingTimestamp(LocalDateTime.now())
                .build();
            
            // Log payment confirmation error
            auditService.logSecurityEvent(
                "PAYMENT_CONFIRMATION_ERROR",
                "HIGH",
                "Payment confirmation error: " + e.getMessage(),
                createPaymentAuditDetails(paymentRequest)
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Validates payment request parameters using COBOL-equivalent validation rules.
     * 
     * This method implements input validation logic equivalent to the COBOL
     * PROCESS-ENTER-KEY section, ensuring all required fields are present
     * and conform to expected format and value constraints.
     * 
     * Original COBOL Logic Reference (COBIL00C.cbl lines 159-191):
     * - Account ID validation: Ensures account ID is not empty
     * - Confirmation flag validation: Validates Y/N confirmation values
     * - Payment amount validation: Ensures positive payment amounts
     * 
     * @param paymentRequest Payment request to validate
     * @return PaymentResponse with validation results
     */
    private PaymentResponse validatePaymentRequest(PaymentRequest paymentRequest) {
        logger.debug("Validating payment request for account: {}", 
                    paymentRequest != null ? paymentRequest.getAccountId() : "null");
        
        // Validate request object presence
        if (paymentRequest == null) {
            logger.warn("Payment request is null");
            return PaymentResponse.builder()
                .paymentStatus("FAILURE")
                .errorMessage("Payment request is required")
                .processingTimestamp(LocalDateTime.now())
                .build();
        }
        
        // Validate account ID presence (COBOL lines 159-167)
        if (paymentRequest.getAccountId() == null || paymentRequest.getAccountId().trim().isEmpty()) {
            logger.warn("Payment request validation failed: Account ID is empty");
            return PaymentResponse.builder()
                .paymentStatus("FAILURE")
                .errorMessage(ERROR_ACCOUNT_ID_REQUIRED)
                .processingTimestamp(LocalDateTime.now())
                .build();
        }
        
        // Validate account ID format (11 digits)
        if (!paymentRequest.getAccountId().matches("^\\d{11}$")) {
            logger.warn("Payment request validation failed: Invalid account ID format");
            return PaymentResponse.builder()
                .paymentStatus("FAILURE")
                .errorMessage(ERROR_INVALID_ACCOUNT_ID)
                .processingTimestamp(LocalDateTime.now())
                .build();
        }
        
        // Validate confirmation flag presence
        if (paymentRequest.getPaymentConfirmation() == null || 
            paymentRequest.getPaymentConfirmation().trim().isEmpty()) {
            logger.warn("Payment request validation failed: Payment confirmation is empty");
            return PaymentResponse.builder()
                .paymentStatus("FAILURE")
                .errorMessage(ERROR_PAYMENT_CONFIRMATION_REQUIRED)
                .processingTimestamp(LocalDateTime.now())
                .build();
        }
        
        // Validate confirmation flag format (COBOL lines 173-191)
        if (!paymentRequest.getPaymentConfirmation().matches("^[YyNn]$")) {
            logger.warn("Payment request validation failed: Invalid confirmation flag");
            return PaymentResponse.builder()
                .paymentStatus("FAILURE")
                .errorMessage(ERROR_INVALID_CONFIRMATION)
                .processingTimestamp(LocalDateTime.now())
                .build();
        }
        
        // Validate payment amount if provided
        if (paymentRequest.getPaymentAmount() != null && 
            paymentRequest.getPaymentAmount().compareTo(BigDecimal.ZERO) <= 0) {
            logger.warn("Payment request validation failed: Invalid payment amount");
            return PaymentResponse.builder()
                .paymentStatus("FAILURE")
                .errorMessage(ERROR_INVALID_PAYMENT_AMOUNT)
                .processingTimestamp(LocalDateTime.now())
                .build();
        }
        
        logger.debug("Payment request validation successful for account: {}", paymentRequest.getAccountId());
        return PaymentResponse.builder()
            .paymentStatus("SUCCESS")
            .processingTimestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Handles Bean Validation exceptions from @Valid annotation processing.
     * 
     * This method provides comprehensive error handling for validation failures,
     * mapping validation constraint violations to user-friendly error messages
     * consistent with the original COBOL validation patterns.
     * 
     * @param ex Method argument validation exception
     * @return ResponseEntity with detailed validation error information
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<PaymentResponse> handleValidationException(MethodArgumentNotValidException ex) {
        logger.warn("Validation exception occurred: {}", ex.getMessage());
        
        StringBuilder errorMessage = new StringBuilder();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errorMessage.append(error.getDefaultMessage()).append("; ");
        }
        
        PaymentResponse errorResponse = PaymentResponse.builder()
            .paymentStatus("FAILURE")
            .errorMessage(ERROR_VALIDATION_FAILED + ": " + errorMessage.toString())
            .processingTimestamp(LocalDateTime.now())
            .build();
        
        // Log validation exception security event
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("username", getCurrentUsername());
        auditDetails.put("error_type", "VALIDATION_EXCEPTION");
        auditDetails.put("validation_errors", errorMessage.toString());
        
        auditService.logSecurityEvent(
            "PAYMENT_VALIDATION_EXCEPTION",
            "MEDIUM",
            "Payment validation exception: " + errorMessage.toString(),
            auditDetails
        );
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handles payment processing specific exceptions.
     * 
     * This method provides centralized exception handling for payment-related
     * errors, ensuring consistent error responses and comprehensive audit logging.
     * 
     * @param ex Payment processing exception
     * @return ResponseEntity with payment error response
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<PaymentResponse> handlePaymentException(RuntimeException ex) {
        logger.error("Payment processing exception occurred: {}", ex.getMessage(), ex);
        
        PaymentResponse errorResponse = PaymentResponse.builder()
            .paymentStatus("FAILURE")
            .errorMessage(ERROR_PAYMENT_PROCESSING_FAILED)
            .processingTimestamp(LocalDateTime.now())
            .build();
        
        // Log payment processing exception
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("username", getCurrentUsername());
        auditDetails.put("error_type", "PAYMENT_PROCESSING_EXCEPTION");
        auditDetails.put("exception_message", ex.getMessage());
        
        auditService.logSecurityEvent(
            "PAYMENT_PROCESSING_EXCEPTION",
            "HIGH",
            "Payment processing exception: " + ex.getMessage(),
            auditDetails
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handles general exceptions for comprehensive error management.
     * 
     * This method provides fallback exception handling for unexpected errors,
     * ensuring no unhandled exceptions escape the controller layer.
     * 
     * @param ex General exception
     * @return ResponseEntity with generic error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<PaymentResponse> handleGeneralException(Exception ex) {
        logger.error("General exception occurred in payment controller: {}", ex.getMessage(), ex);
        
        PaymentResponse errorResponse = PaymentResponse.builder()
            .paymentStatus("FAILURE")
            .errorMessage(ERROR_INTERNAL_SERVER_ERROR)
            .processingTimestamp(LocalDateTime.now())
            .build();
        
        // Log general exception security event
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("username", getCurrentUsername());
        auditDetails.put("error_type", "GENERAL_EXCEPTION");
        auditDetails.put("exception_class", ex.getClass().getSimpleName());
        auditDetails.put("exception_message", ex.getMessage());
        
        auditService.logSecurityEvent(
            "PAYMENT_GENERAL_EXCEPTION",
            "HIGH",
            "General exception in payment controller: " + ex.getMessage(),
            auditDetails
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Determines appropriate HTTP status code based on payment processing status.
     * 
     * This method maps payment processing statuses to HTTP status codes,
     * providing RESTful API response patterns for different payment outcomes.
     * 
     * @param paymentStatus Payment processing status
     * @return Appropriate HTTP status code
     */
    private HttpStatus determineHttpStatus(String paymentStatus) {
        if (paymentStatus == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        
        switch (paymentStatus.toUpperCase()) {
            case "SUCCESS":
                return HttpStatus.OK;
            case "CONFIRMATION_REQUIRED":
                return HttpStatus.ACCEPTED;
            case "FAILURE":
                return HttpStatus.BAD_REQUEST;
            default:
                return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * Creates audit details for payment request logging.
     * 
     * This method generates comprehensive audit information for payment requests,
     * supporting detailed security and compliance logging requirements.
     * 
     * @param paymentRequest Payment request for audit logging
     * @return Map of audit details
     */
    private Map<String, Object> createPaymentAuditDetails(PaymentRequest paymentRequest) {
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("account_id", paymentRequest.getAccountId());
        auditDetails.put("payment_confirmation", paymentRequest.getPaymentConfirmation());
        auditDetails.put("payment_amount", paymentRequest.getPaymentAmount() != null ? 
                         paymentRequest.getPaymentAmount().toString() : "null");
        auditDetails.put("username", getCurrentUsername());
        auditDetails.put("request_timestamp", LocalDateTime.now().toString());
        return auditDetails;
    }

    /**
     * Creates audit details for payment response logging.
     * 
     * This method generates comprehensive audit information for payment responses,
     * supporting detailed security and compliance logging requirements.
     * 
     * @param paymentResponse Payment response for audit logging
     * @return Map of audit details
     */
    private Map<String, Object> createPaymentResponseAuditDetails(PaymentResponse paymentResponse) {
        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("transaction_id", paymentResponse.getTransactionId());
        auditDetails.put("payment_status", paymentResponse.getPaymentStatus());
        auditDetails.put("payment_amount", paymentResponse.getPaymentAmount() != null ? 
                         paymentResponse.getPaymentAmount().toString() : "null");
        auditDetails.put("updated_balance", paymentResponse.getUpdatedAccountBalance() != null ? 
                         paymentResponse.getUpdatedAccountBalance().toString() : "null");
        auditDetails.put("username", getCurrentUsername());
        auditDetails.put("response_timestamp", LocalDateTime.now().toString());
        return auditDetails;
    }

    /**
     * Retrieves the current authenticated username for audit logging.
     * 
     * This method extracts the username from the Spring Security context
     * for comprehensive audit trail correlation.
     * 
     * @return Current username or system default
     */
    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getName() != null) {
                return authentication.getName();
            }
        } catch (Exception e) {
            logger.debug("Unable to extract current username from security context: {}", e.getMessage());
        }
        return "SYSTEM_USER";
    }
}
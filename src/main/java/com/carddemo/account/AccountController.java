/*
 * AccountController.java
 * 
 * Spring Boot REST controller exposing HTTP endpoints for account management operations.
 * Converts CICS transaction processing from COBOL programs COACTVWC and COACTUPC to 
 * modern REST API patterns while maintaining identical business logic and validation.
 * 
 * Derived from COBOL programs:
 * - app/cbl/COACTVWC.cbl (Account View - Transaction CAVW)
 * - app/cbl/COACTUPC.cbl (Account Update - Transaction CAUP)
 * 
 * Original CICS transactions: CAVW (view), CAUP (update)
 * 
 * Ver: CardDemo_v1.0-15-g27d6c6f-68 Date: 2022-07-19 23:15:59 CDT
 */
package com.carddemo.account;

import com.carddemo.util.DateConversionUtil;
import com.carddemo.util.SessionAttributeNames;
import com.carddemo.exception.AccountNotFoundException;
import com.carddemo.exception.InvalidAccountIdException;
import com.carddemo.exception.UnauthorizedAccessException;
import com.carddemo.exception.SystemException;

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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST Controller for Account Management Operations
 * 
 * This controller exposes HTTP endpoints for account-related operations including
 * viewing, creating, updating, and deleting accounts. It converts the original
 * COBOL transaction processing logic from COACTVWC and COACTUPC programs into
 * modern Spring Boot REST patterns while preserving identical business logic
 * and validation rules.
 * 
 * Security Integration:
 * - JWT token validation through Spring Security
 * - Role-based access control with method-level security
 * - Session management via Redis-backed distributed sessions
 * 
 * Error Handling:
 * - Spring @ExceptionHandler methods replicate COBOL HANDLE ABEND patterns
 * - HTTP status codes equivalent to original CICS response codes
 * - Standardized error response format for consistent API behavior
 * 
 * Transaction Management:
 * - Spring @Transactional annotations ensure ACID compliance
 * - Automatic rollback on business rule violations
 * - Session state preservation across stateless REST calls
 */
@RestController
@RequestMapping("/api/v1/accounts")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AccountController {

    private static final Logger logger = LoggerFactory.getLogger(AccountController.class);

    // Constants replicating COBOL literals from COACTVWC
    private static final String CONTROLLER_NAME = "AccountController";
    private static final String TRANSACTION_VIEW = "CAVW";
    private static final String TRANSACTION_UPDATE = "CAUP";
    private static final String PROGRAM_VIEW = "COACTVWC";
    private static final String PROGRAM_UPDATE = "COACTUPC";
    
    // Error messages matching original COBOL messages from WS-RETURN-MSG
    private static final String MSG_ACCOUNT_NOT_PROVIDED = "Account number not provided";
    private static final String MSG_ACCOUNT_INVALID_FORMAT = "Account number must be a non zero 11 digit number";
    private static final String MSG_ACCOUNT_NOT_FOUND_XREF = "Did not find this account in account card xref file";
    private static final String MSG_ACCOUNT_NOT_FOUND_MASTER = "Did not find this account in account master file";
    private static final String MSG_CUSTOMER_NOT_FOUND = "Did not find associated customer in master file";
    private static final String MSG_NO_INPUT_RECEIVED = "No input received";
    private static final String MSG_UNAUTHORIZED_ACCESS = "Unauthorized access to account";
    private static final String MSG_SYSTEM_ERROR = "Unexpected system error occurred";

    @Autowired
    private AccountService accountService;

    /**
     * Get Account by ID - Equivalent to COACTVWC COBOL program
     * 
     * Replicates the account view functionality from the original CICS transaction CAVW.
     * Performs the same validation sequence as the COBOL program:
     * 1. Validate 11-digit account ID (paragraph 2210-EDIT-ACCOUNT)
     * 2. Read cross-reference file (paragraph 9200-GETCARDXREF-BYACCT)
     * 3. Read account master file (paragraph 9300-GETACCTDATA-BYACCT)
     * 4. Read customer master file (paragraph 9400-GETCUSTDATA-BYCUST)
     * 
     * @param accountId 11-digit account identifier (CDEMO-ACCT-ID equivalent)
     * @param request HTTP request for session management
     * @return ResponseEntity containing AccountDto with full account details
     */
    @GetMapping("/{accountId}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAccount(
            @PathVariable @NotNull @Min(value = 1, message = MSG_ACCOUNT_INVALID_FORMAT) 
            @Max(value = 99999999999L, message = MSG_ACCOUNT_INVALID_FORMAT) Long accountId,
            HttpServletRequest request) {
        
        logger.info("Account view request received - AccountId: {}, User: {}", 
                   accountId, getCurrentUserId());
        
        try {
            // Store session context equivalent to CICS COMMAREA
            updateSessionContext(request, TRANSACTION_VIEW, PROGRAM_VIEW, accountId);
            
            // Validate account ID format (equivalent to 2210-EDIT-ACCOUNT)
            if (!isValidAccountId(accountId)) {
                logger.warn("Invalid account ID format: {}", accountId);
                return createErrorResponse(HttpStatus.BAD_REQUEST, 
                                         "ACCOUNT_INVALID_FORMAT", 
                                         MSG_ACCOUNT_INVALID_FORMAT);
            }
            
            // Business logic delegation (equivalent to 9000-READ-ACCT)
            AccountDto accountDto = accountService.getAccountById(accountId);
            
            // Prepare success response matching BMS screen layout
            Map<String, Object> response = createSuccessResponse(accountDto);
            
            logger.info("Account view completed successfully - AccountId: {}", accountId);
            return ResponseEntity.ok(response);
            
        } catch (AccountNotFoundException e) {
            logger.warn("Account not found - AccountId: {}, Error: {}", accountId, e.getMessage());
            return createErrorResponse(HttpStatus.NOT_FOUND, 
                                     "ACCOUNT_NOT_FOUND", 
                                     e.getMessage());
        } catch (UnauthorizedAccessException e) {
            logger.warn("Unauthorized access attempt - AccountId: {}, User: {}", 
                       accountId, getCurrentUserId());
            return createErrorResponse(HttpStatus.FORBIDDEN, 
                                     "UNAUTHORIZED_ACCESS", 
                                     MSG_UNAUTHORIZED_ACCESS);
        } catch (Exception e) {
            logger.error("System error during account view - AccountId: {}, Error: {}", 
                        accountId, e.getMessage(), e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                                     "SYSTEM_ERROR", 
                                     MSG_SYSTEM_ERROR);
        }
    }
    
    /**
     * Create New Account - Equivalent to COACTUPC COBOL program (create mode)
     * 
     * Implements account creation with full validation and business rule enforcement.
     * Maintains identical field validation as original COBOL program while using
     * Spring Boot validation annotations and JPA persistence.
     * 
     * @param accountDto Account data transfer object with validation annotations
     * @param bindingResult Spring validation results
     * @param request HTTP request for session management
     * @return ResponseEntity containing created account data or validation errors
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> createAccount(
            @Valid @RequestBody AccountDto accountDto,
            BindingResult bindingResult,
            HttpServletRequest request) {
        
        logger.info("Account creation request received - User: {}", getCurrentUserId());
        
        try {
            // Store session context
            updateSessionContext(request, TRANSACTION_UPDATE, PROGRAM_UPDATE, null);
            
            // Check for validation errors (equivalent to COBOL field validation)
            if (bindingResult.hasErrors()) {
                logger.warn("Account creation validation failed - Errors: {}", 
                           getValidationErrors(bindingResult));
                return createValidationErrorResponse(bindingResult);
            }
            
            // Business logic delegation
            AccountDto createdAccount = accountService.createAccount(accountDto);
            
            // Prepare success response
            Map<String, Object> response = createSuccessResponse(createdAccount);
            response.put("message", "Account created successfully");
            
            logger.info("Account created successfully - AccountId: {}", 
                       createdAccount.getAccountId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            logger.error("Error creating account - Error: {}", e.getMessage(), e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                                     "ACCOUNT_CREATION_ERROR", 
                                     "Failed to create account: " + e.getMessage());
        }
    }
    
    /**
     * Update Existing Account - Equivalent to COACTUPC COBOL program (update mode)
     * 
     * Implements account modification with optimistic locking equivalent to CICS 
     * record locking. Validates all fields using same business rules as original
     * COBOL program while maintaining transactional integrity.
     * 
     * @param accountId Account identifier for update
     * @param accountDto Updated account data
     * @param bindingResult Spring validation results
     * @param request HTTP request for session management
     * @return ResponseEntity containing updated account data or error information
     */
    @PutMapping("/{accountId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateAccount(
            @PathVariable @NotNull @Min(value = 1) @Max(value = 99999999999L) Long accountId,
            @Valid @RequestBody AccountDto accountDto,
            BindingResult bindingResult,
            HttpServletRequest request) {
        
        logger.info("Account update request received - AccountId: {}, User: {}", 
                   accountId, getCurrentUserId());
        
        try {
            // Store session context
            updateSessionContext(request, TRANSACTION_UPDATE, PROGRAM_UPDATE, accountId);
            
            // Validate account ID consistency
            if (!accountId.equals(accountDto.getAccountId())) {
                logger.warn("Account ID mismatch - Path: {}, Body: {}", 
                           accountId, accountDto.getAccountId());
                return createErrorResponse(HttpStatus.BAD_REQUEST, 
                                         "ACCOUNT_ID_MISMATCH", 
                                         "Account ID in path must match account ID in request body");
            }
            
            // Check for validation errors
            if (bindingResult.hasErrors()) {
                logger.warn("Account update validation failed - AccountId: {}, Errors: {}", 
                           accountId, getValidationErrors(bindingResult));
                return createValidationErrorResponse(bindingResult);
            }
            
            // Business logic delegation  
            AccountDto updatedAccount = accountService.updateAccount(accountId, accountDto);
            
            // Prepare success response
            Map<String, Object> response = createSuccessResponse(updatedAccount);
            response.put("message", "Account updated successfully");
            
            logger.info("Account updated successfully - AccountId: {}", accountId);
            return ResponseEntity.ok(response);
            
        } catch (AccountNotFoundException e) {
            logger.warn("Account not found for update - AccountId: {}", accountId);
            return createErrorResponse(HttpStatus.NOT_FOUND, 
                                     "ACCOUNT_NOT_FOUND", 
                                     MSG_ACCOUNT_NOT_FOUND_MASTER);
        } catch (Exception e) {
            logger.error("Error updating account - AccountId: {}, Error: {}", 
                        accountId, e.getMessage(), e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                                     "ACCOUNT_UPDATE_ERROR", 
                                     "Failed to update account: " + e.getMessage());
        }
    }
    
    /**
     * Delete Account - Administrative function with comprehensive validation
     * 
     * Implements account deletion with business rule validation ensuring no
     * active cards or pending transactions exist before deletion.
     * 
     * @param accountId Account identifier for deletion
     * @param request HTTP request for session management
     * @return ResponseEntity with deletion confirmation or error information
     */
    @DeleteMapping("/{accountId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteAccount(
            @PathVariable @NotNull @Min(value = 1) @Max(value = 99999999999L) Long accountId,
            HttpServletRequest request) {
        
        logger.info("Account deletion request received - AccountId: {}, User: {}", 
                   accountId, getCurrentUserId());
        
        try {
            // Store session context
            updateSessionContext(request, TRANSACTION_UPDATE, PROGRAM_UPDATE, accountId);
            
            // Business logic delegation
            accountService.deleteAccount(accountId);
            
            // Prepare success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Account deleted successfully");
            response.put("accountId", accountId);
            response.put("timestamp", getCurrentTimestamp());
            
            logger.info("Account deleted successfully - AccountId: {}", accountId);
            return ResponseEntity.ok(response);
            
        } catch (AccountNotFoundException e) {
            logger.warn("Account not found for deletion - AccountId: {}", accountId);
            return createErrorResponse(HttpStatus.NOT_FOUND, 
                                     "ACCOUNT_NOT_FOUND", 
                                     MSG_ACCOUNT_NOT_FOUND_MASTER);
        } catch (Exception e) {
            logger.error("Error deleting account - AccountId: {}, Error: {}", 
                        accountId, e.getMessage(), e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                                     "ACCOUNT_DELETION_ERROR", 
                                     "Failed to delete account: " + e.getMessage());
        }
    }
    
    /**
     * Search Accounts - Enhanced query functionality for administrative users
     * 
     * Provides account search capabilities with filtering and pagination support.
     * Implements business logic similar to account lookup operations while
     * supporting multiple search criteria.
     * 
     * @param customerId Optional customer ID filter
     * @param status Optional account status filter
     * @param page Page number for pagination (default: 0)
     * @param size Page size for pagination (default: 10)
     * @param request HTTP request for session management
     * @return ResponseEntity containing paginated account search results
     */
    @GetMapping
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> searchAccounts(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        
        logger.info("Account search request received - CustomerId: {}, Status: {}, User: {}", 
                   customerId, status, getCurrentUserId());
        
        try {
            // Store session context
            updateSessionContext(request, TRANSACTION_VIEW, PROGRAM_VIEW, null);
            
            // Validate pagination parameters
            if (page < 0 || size <= 0 || size > 100) {
                return createErrorResponse(HttpStatus.BAD_REQUEST, 
                                         "INVALID_PAGINATION", 
                                         "Invalid pagination parameters");
            }
            
            // Business logic delegation
            Map<String, Object> searchResults = accountService.searchAccounts(customerId, status, page, size);
            
            // Prepare success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", searchResults);
            response.put("timestamp", getCurrentTimestamp());
            
            logger.info("Account search completed - Results count: {}", 
                       ((List<?>) searchResults.get("accounts")).size());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error searching accounts - Error: {}", e.getMessage(), e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                                     "ACCOUNT_SEARCH_ERROR", 
                                     "Failed to search accounts: " + e.getMessage());
        }
    }
    
    /**
     * Get Account Summary - Lightweight account information for dashboards
     * 
     * Provides essential account information without full customer details.
     * Optimized for performance with minimal database queries.
     * 
     * @param accountId Account identifier
     * @param request HTTP request for session management
     * @return ResponseEntity containing account summary data
     */
    @GetMapping("/{accountId}/summary")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAccountSummary(
            @PathVariable @NotNull @Min(value = 1) @Max(value = 99999999999L) Long accountId,
            HttpServletRequest request) {
        
        logger.info("Account summary request received - AccountId: {}, User: {}", 
                   accountId, getCurrentUserId());
        
        try {
            // Store session context
            updateSessionContext(request, TRANSACTION_VIEW, PROGRAM_VIEW, accountId);
            
            // Business logic delegation
            Map<String, Object> summaryData = accountService.getAccountSummary(accountId);
            
            // Prepare success response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", summaryData);
            response.put("timestamp", getCurrentTimestamp());
            
            logger.info("Account summary completed - AccountId: {}", accountId);
            return ResponseEntity.ok(response);
            
        } catch (AccountNotFoundException e) {
            logger.warn("Account not found for summary - AccountId: {}", accountId);
            return createErrorResponse(HttpStatus.NOT_FOUND, 
                                     "ACCOUNT_NOT_FOUND", 
                                     MSG_ACCOUNT_NOT_FOUND_MASTER);
        } catch (Exception e) {
            logger.error("Error retrieving account summary - AccountId: {}, Error: {}", 
                        accountId, e.getMessage(), e);
            return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                                     "ACCOUNT_SUMMARY_ERROR", 
                                     "Failed to retrieve account summary: " + e.getMessage());
        }
    }
    
    // Private helper methods
    
    /**
     * Validates account ID format - Equivalent to COBOL paragraph 2210-EDIT-ACCOUNT
     * 
     * Performs the same validation as the original COBOL program:
     * - Not null or zero
     * - Numeric value
     * - 11 digits or less
     * 
     * @param accountId Account ID to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidAccountId(Long accountId) {
        if (accountId == null || accountId <= 0) {
            return false;
        }
        // Check if account ID has 11 digits or less (max value 99999999999)
        return accountId <= 99999999999L;
    }
    
    /**
     * Updates session context equivalent to CICS COMMAREA
     * 
     * Stores transaction context in Redis-backed session for maintaining
     * pseudo-conversational state across stateless REST calls.
     * 
     * @param request HTTP request containing session
     * @param transactionId Transaction identifier (CAVW/CAUP)
     * @param programName Program name (COACTVWC/COACTUPC)
     * @param accountId Current account ID being processed
     */
    private void updateSessionContext(HttpServletRequest request, String transactionId, 
                                    String programName, Long accountId) {
        HttpSession session = request.getSession(true);
        
        // Store transaction context equivalent to CDEMO-FROM-TRANID, CDEMO-FROM-PROGRAM
        session.setAttribute(SessionAttributeNames.CURRENT_TRANSACTION, transactionId);
        session.setAttribute(SessionAttributeNames.CURRENT_PROGRAM, programName);
        session.setAttribute(SessionAttributeNames.LAST_ACCESSED_ACCOUNT, accountId);
        session.setAttribute(SessionAttributeNames.LAST_ACTIVITY_TIME, LocalDateTime.now());
        
        // Store user context for audit trail
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            session.setAttribute(SessionAttributeNames.CURRENT_USER, auth.getName());
        }
    }
    
    /**
     * Gets current user ID from security context
     * 
     * @return Current authenticated user ID or "UNKNOWN" if not authenticated
     */
    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "UNKNOWN";
    }
    
    /**
     * Creates standardized success response matching BMS screen layout
     * 
     * @param accountDto Account data to include in response
     * @return Map containing standardized success response structure
     */
    private Map<String, Object> createSuccessResponse(AccountDto accountDto) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", accountDto);
        response.put("timestamp", getCurrentTimestamp());
        response.put("message", "Account operation completed successfully");
        
        // Add calculated fields equivalent to COBOL computed values
        if (accountDto != null) {
            Map<String, Object> calculatedFields = new HashMap<>();
            calculatedFields.put("availableCredit", accountDto.getAvailableCredit());
            calculatedFields.put("availableCashCredit", accountDto.getAvailableCashCredit());
            calculatedFields.put("isActive", accountDto.isActive());
            calculatedFields.put("isInactive", accountDto.isInactive());
            calculatedFields.put("isClosed", accountDto.isClosed());
            calculatedFields.put("isSuspended", accountDto.isSuspended());
            response.put("computed", calculatedFields);
        }
        
        return response;
    }
    
    /**
     * Creates standardized error response equivalent to COBOL error handling
     * 
     * @param status HTTP status code
     * @param errorCode Application-specific error code
     * @param message Error message
     * @return ResponseEntity containing standardized error response
     */
    private ResponseEntity<Map<String, Object>> createErrorResponse(HttpStatus status, 
                                                                   String errorCode, 
                                                                   String message) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", Map.of(
            "code", errorCode,
            "message", message,
            "httpStatus", status.value(),
            "timestamp", getCurrentTimestamp(),
            "controller", CONTROLLER_NAME
        ));
        
        return ResponseEntity.status(status).body(errorResponse);
    }
    
    /**
     * Creates validation error response from Spring BindingResult
     * 
     * @param bindingResult Spring validation results
     * @return ResponseEntity containing validation error details
     */
    private ResponseEntity<Map<String, Object>> createValidationErrorResponse(BindingResult bindingResult) {
        List<Map<String, String>> fieldErrors = bindingResult.getFieldErrors().stream()
            .map(error -> Map.of(
                "field", error.getField(),
                "message", error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                "rejectedValue", error.getRejectedValue() != null ? error.getRejectedValue().toString() : "null"
            ))
            .collect(Collectors.toList());
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", Map.of(
            "code", "VALIDATION_ERROR",
            "message", "Request validation failed",
            "httpStatus", HttpStatus.BAD_REQUEST.value(),
            "timestamp", getCurrentTimestamp(),
            "controller", CONTROLLER_NAME,
            "fieldErrors", fieldErrors
        ));
        
        return ResponseEntity.badRequest().body(errorResponse);
    }
    
    /**
     * Extracts validation error messages for logging
     * 
     * @param bindingResult Spring validation results
     * @return Comma-separated string of validation errors
     */
    private String getValidationErrors(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
    }
    
    /**
     * Gets current timestamp in ISO format for response consistency
     * 
     * @return Current timestamp string
     */
    private String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
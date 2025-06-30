/*
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

package com.carddemo.validation;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Error mapping and translation service providing seamless conversion between COBOL error 
 * patterns and REST API responses. Maps CICS response codes to appropriate HTTP status codes
 * while preserving business error semantics.
 * 
 * <p>This service maintains backward compatibility with original COBOL error messages and codes,
 * enabling consistent error handling across all Spring Boot controllers through global 
 * @ControllerAdvice integration.
 * 
 * <p>Implements translation capabilities for:</p>
 * <ul>
 *   <li>CICS response codes to HTTP status codes</li>
 *   <li>COBOL error messages to REST API error responses</li>
 *   <li>Business validation error patterns</li>
 *   <li>Data access error conditions</li>
 *   <li>Security error scenarios</li>
 * </ul>
 * 
 * <p>Based on migration requirements from Section 0.2.3 Cross-Cutting Services and 
 * Section 5.4.3 Error Handling Patterns.</p>
 * 
 * @author Blitzy agent
 * @version 1.0
 */
@Service
public class ErrorMappingService {

    // CICS response code mappings based on legacy COSGN00C.cbl patterns
    private static final Map<Integer, HttpStatus> CICS_HTTP_STATUS_MAP = new HashMap<>();
    
    // COBOL error message patterns for backward compatibility
    private static final Map<String, String> COBOL_ERROR_MESSAGES = new HashMap<>();
    
    static {
        initializeCicsResponseMappings();
        initializeCobolErrorMessages();
    }

    /**
     * Initialize CICS response code to HTTP status mappings.
     * Based on CICS response patterns observed in COSGN00C.cbl and CSUTLDTC.cbl.
     */
    private static void initializeCicsResponseMappings() {
        // CICS Normal response
        CICS_HTTP_STATUS_MAP.put(0, HttpStatus.OK);
        
        // CICS Record not found (NOTFND) - seen in COSGN00C line 247
        CICS_HTTP_STATUS_MAP.put(13, HttpStatus.NOT_FOUND);
        
        // CICS Duplicate key (DUPREC)
        CICS_HTTP_STATUS_MAP.put(14, HttpStatus.CONFLICT);
        
        // CICS Invalid request (INVREQ)
        CICS_HTTP_STATUS_MAP.put(16, HttpStatus.BAD_REQUEST);
        
        // CICS File not found (FILENOTFOUND)
        CICS_HTTP_STATUS_MAP.put(12, HttpStatus.NOT_FOUND);
        
        // CICS End of file (EOF)
        CICS_HTTP_STATUS_MAP.put(20, HttpStatus.NO_CONTENT);
        
        // CICS Record busy (LOCKED)
        CICS_HTTP_STATUS_MAP.put(19, HttpStatus.LOCKED);
        
        // CICS Authorization failure
        CICS_HTTP_STATUS_MAP.put(70, HttpStatus.FORBIDDEN);
        
        // CICS System error or any unmapped error
        // Default system error mapping for unknown CICS codes
    }

    /**
     * Initialize COBOL error message mappings for backward compatibility.
     * These messages are extracted from COSGN00C.cbl and CSMSG01Y.cpy.
     */
    private static void initializeCobolErrorMessages() {
        // Authentication error messages from COSGN00C.cbl
        COBOL_ERROR_MESSAGES.put("INVALID_PASSWORD", "Wrong Password. Try again ...");
        COBOL_ERROR_MESSAGES.put("USER_NOT_FOUND", "User not found. Try again ...");
        COBOL_ERROR_MESSAGES.put("UNABLE_TO_VERIFY", "Unable to verify the User ...");
        COBOL_ERROR_MESSAGES.put("MISSING_USERID", "Please enter User ID ...");
        COBOL_ERROR_MESSAGES.put("MISSING_PASSWORD", "Please enter Password ...");
        
        // General error messages from CSMSG01Y.cpy
        COBOL_ERROR_MESSAGES.put("INVALID_KEY", "Invalid key pressed. Please see below...         ");
        COBOL_ERROR_MESSAGES.put("THANK_YOU", "Thank you for using CardDemo application...      ");
        
        // Business validation error messages
        COBOL_ERROR_MESSAGES.put("ACCOUNT_NOT_FOUND", "Account not found in system");
        COBOL_ERROR_MESSAGES.put("CARD_NOT_FOUND", "Card not found for this account");
        COBOL_ERROR_MESSAGES.put("INVALID_TRANSACTION", "Invalid transaction data provided");
        COBOL_ERROR_MESSAGES.put("INSUFFICIENT_BALANCE", "Insufficient account balance for transaction");
        COBOL_ERROR_MESSAGES.put("CARD_EXPIRED", "Card has expired and cannot be used");
        COBOL_ERROR_MESSAGES.put("CARD_BLOCKED", "Card is blocked for security reasons");
        COBOL_ERROR_MESSAGES.put("DUPLICATE_TRANSACTION", "Duplicate transaction detected");
        COBOL_ERROR_MESSAGES.put("TRANSACTION_LIMIT_EXCEEDED", "Transaction limit exceeded");
        
        // System error messages
        COBOL_ERROR_MESSAGES.put("SYSTEM_ERROR", "System error occurred - please try again");
        COBOL_ERROR_MESSAGES.put("DATABASE_ERROR", "Database error - please contact administrator");
        COBOL_ERROR_MESSAGES.put("TIMEOUT_ERROR", "Request timeout - please try again");
        
        // Data validation error messages
        COBOL_ERROR_MESSAGES.put("INVALID_FIELD_LENGTH", "Field length exceeds maximum allowed");
        COBOL_ERROR_MESSAGES.put("INVALID_NUMERIC_DATA", "Invalid numeric data provided");
        COBOL_ERROR_MESSAGES.put("REQUIRED_FIELD_MISSING", "Required field is missing");
        COBOL_ERROR_MESSAGES.put("INVALID_DATE_FORMAT", "Invalid date format provided");
    }

    /**
     * Maps CICS response code to appropriate HTTP status code.
     * 
     * @param cicsResponseCode The CICS response code (e.g., 0, 13, 14, 16)
     * @return Corresponding HTTP status code
     */
    public HttpStatus mapCicsResponseToHttpStatus(int cicsResponseCode) {
        return CICS_HTTP_STATUS_MAP.getOrDefault(cicsResponseCode, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Retrieves COBOL error message by key, maintaining exact original text.
     * 
     * @param errorKey The error message key
     * @return Original COBOL error message text
     */
    public String getCobolErrorMessage(String errorKey) {
        return COBOL_ERROR_MESSAGES.getOrDefault(errorKey, "Unknown error occurred");
    }

    /**
     * Creates a standardized error response structure compatible with REST API patterns.
     * 
     * @param errorCode Business error code (e.g., "USER_NOT_FOUND")
     * @param httpStatus HTTP status code
     * @param details Additional error details
     * @return Standardized error response map
     */
    public Map<String, Object> createErrorResponse(String errorCode, HttpStatus httpStatus, String details) {
        Map<String, Object> errorResponse = new HashMap<>();
        
        errorResponse.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        errorResponse.put("status", httpStatus.value());
        errorResponse.put("error", httpStatus.getReasonPhrase());
        errorResponse.put("errorCode", errorCode);
        errorResponse.put("message", getCobolErrorMessage(errorCode));
        
        if (StringUtils.hasText(details)) {
            errorResponse.put("details", details);
        }
        
        return errorResponse;
    }

    /**
     * Creates error response from CICS response code with COBOL message mapping.
     * 
     * @param cicsResponseCode CICS response code
     * @param errorMessageKey COBOL error message key
     * @param details Additional error details
     * @return Standardized error response
     */
    public Map<String, Object> createCicsErrorResponse(int cicsResponseCode, String errorMessageKey, String details) {
        HttpStatus httpStatus = mapCicsResponseToHttpStatus(cicsResponseCode);
        return createErrorResponse(errorMessageKey, httpStatus, details);
    }

    /**
     * Maps business exception types to appropriate error responses.
     * Supports @ControllerAdvice integration for global error handling.
     * 
     * @param exceptionType Exception class name
     * @param message Exception message
     * @return Mapped error response
     */
    public Map<String, Object> mapBusinessException(String exceptionType, String message) {
        switch (exceptionType) {
            case "BusinessException":
                return createErrorResponse("BUSINESS_VALIDATION_ERROR", HttpStatus.BAD_REQUEST, message);
            
            case "DataAccessException":
                return createErrorResponse("DATABASE_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, message);
            
            case "SecurityException":
                return createErrorResponse("SECURITY_VIOLATION", HttpStatus.FORBIDDEN, message);
            
            case "AuthenticationException":
                return createErrorResponse("INVALID_PASSWORD", HttpStatus.UNAUTHORIZED, message);
            
            case "ResourceNotFoundException":
                return createErrorResponse("USER_NOT_FOUND", HttpStatus.NOT_FOUND, message);
            
            case "OptimisticLockingFailureException":
                return createErrorResponse("RECORD_LOCKED", HttpStatus.CONFLICT, message);
            
            case "ValidationException":
                return createErrorResponse("INVALID_FIELD_LENGTH", HttpStatus.BAD_REQUEST, message);
            
            default:
                return createErrorResponse("SYSTEM_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, message);
        }
    }

    /**
     * Creates authentication error response maintaining COBOL authentication patterns.
     * Based on COSGN00C.cbl authentication logic.
     * 
     * @param userId User ID that failed authentication
     * @param reason Authentication failure reason
     * @return Authentication error response
     */
    public Map<String, Object> createAuthenticationError(String userId, String reason) {
        String errorKey;
        String details = "User: " + (userId != null ? userId : "unknown");
        
        switch (reason) {
            case "USER_NOT_FOUND":
                errorKey = "USER_NOT_FOUND";
                break;
            case "INVALID_PASSWORD":
                errorKey = "INVALID_PASSWORD";
                break;
            case "MISSING_USERID":
                errorKey = "MISSING_USERID";
                break;
            case "MISSING_PASSWORD":
                errorKey = "MISSING_PASSWORD";
                break;
            default:
                errorKey = "UNABLE_TO_VERIFY";
                break;
        }
        
        return createErrorResponse(errorKey, HttpStatus.UNAUTHORIZED, details);
    }

    /**
     * Creates validation error response for field-level validation failures.
     * Maintains COBOL field validation patterns.
     * 
     * @param fieldName Field that failed validation
     * @param fieldValue Invalid field value
     * @param validationRule Validation rule that failed
     * @return Field validation error response
     */
    public Map<String, Object> createFieldValidationError(String fieldName, Object fieldValue, String validationRule) {
        String errorKey;
        String details = String.format("Field: %s, Value: %s, Rule: %s", 
                                       fieldName, 
                                       fieldValue != null ? fieldValue.toString() : "null", 
                                       validationRule);
        
        switch (validationRule) {
            case "REQUIRED":
                errorKey = "REQUIRED_FIELD_MISSING";
                break;
            case "LENGTH":
                errorKey = "INVALID_FIELD_LENGTH";
                break;
            case "NUMERIC":
                errorKey = "INVALID_NUMERIC_DATA";
                break;
            case "DATE_FORMAT":
                errorKey = "INVALID_DATE_FORMAT";
                break;
            default:
                errorKey = "BUSINESS_VALIDATION_ERROR";
                break;
        }
        
        return createErrorResponse(errorKey, HttpStatus.BAD_REQUEST, details);
    }

    /**
     * Creates system error response for technical failures.
     * Maps to COBOL system abend patterns.
     * 
     * @param systemComponent Component that failed
     * @param technicalMessage Technical error details
     * @return System error response
     */
    public Map<String, Object> createSystemError(String systemComponent, String technicalMessage) {
        String errorKey;
        String details = String.format("Component: %s, Technical: %s", systemComponent, technicalMessage);
        
        if (systemComponent.contains("database") || systemComponent.contains("sql")) {
            errorKey = "DATABASE_ERROR";
        } else if (systemComponent.contains("timeout") || systemComponent.contains("connection")) {
            errorKey = "TIMEOUT_ERROR";
        } else {
            errorKey = "SYSTEM_ERROR";
        }
        
        return createErrorResponse(errorKey, HttpStatus.INTERNAL_SERVER_ERROR, details);
    }

    /**
     * Checks if an error code represents a client-side error (4xx status).
     * Used for determining error handling strategies.
     * 
     * @param errorCode Error code to check
     * @return true if client error, false if server error
     */
    public boolean isClientError(String errorCode) {
        switch (errorCode) {
            case "INVALID_PASSWORD":
            case "USER_NOT_FOUND":
            case "MISSING_USERID":
            case "MISSING_PASSWORD":
            case "INVALID_FIELD_LENGTH":
            case "INVALID_NUMERIC_DATA":
            case "REQUIRED_FIELD_MISSING":
            case "INVALID_DATE_FORMAT":
            case "BUSINESS_VALIDATION_ERROR":
                return true;
            default:
                return false;
        }
    }

    /**
     * Gets HTTP status code for a given error code.
     * Used by @ControllerAdvice for consistent status code assignment.
     * 
     * @param errorCode Error code
     * @return Appropriate HTTP status code
     */
    public HttpStatus getHttpStatusForErrorCode(String errorCode) {
        if (isClientError(errorCode)) {
            if (errorCode.equals("USER_NOT_FOUND") || errorCode.equals("ACCOUNT_NOT_FOUND") || errorCode.equals("CARD_NOT_FOUND")) {
                return HttpStatus.NOT_FOUND;
            } else if (errorCode.equals("INVALID_PASSWORD")) {
                return HttpStatus.UNAUTHORIZED;
            } else if (errorCode.equals("SECURITY_VIOLATION")) {
                return HttpStatus.FORBIDDEN;
            } else {
                return HttpStatus.BAD_REQUEST;
            }
        } else {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }

    /**
     * Creates a formatted error message for logging purposes.
     * Includes correlation data for debugging and audit trails.
     * 
     * @param errorCode Error code
     * @param userId User ID if available
     * @param transactionId Transaction ID if available
     * @param additionalContext Additional context information
     * @return Formatted log message
     */
    public String createLogMessage(String errorCode, String userId, String transactionId, String additionalContext) {
        StringBuilder logMessage = new StringBuilder();
        logMessage.append("ErrorCode: ").append(errorCode);
        
        if (StringUtils.hasText(userId)) {
            logMessage.append(", User: ").append(userId);
        }
        
        if (StringUtils.hasText(transactionId)) {
            logMessage.append(", Transaction: ").append(transactionId);
        }
        
        logMessage.append(", Message: ").append(getCobolErrorMessage(errorCode));
        
        if (StringUtils.hasText(additionalContext)) {
            logMessage.append(", Context: ").append(additionalContext);
        }
        
        logMessage.append(", Timestamp: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        return logMessage.toString();
    }
}
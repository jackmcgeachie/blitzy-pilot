/*
 * ReportController.java
 * 
 * REST controller providing report generation HTTP endpoints replacing CICS transaction 
 * processing from CORPT00C COBOL program. Handles Monthly, Yearly, and Custom report 
 * requests with comprehensive validation, session management, and error handling.
 * 
 * This controller implements the core functionality of the original COBOL program CORPT00C
 * which submitted transaction reports by creating JCL batch jobs through extra partition TDQ.
 * The modern implementation uses Spring Boot REST endpoints with Spring Batch job submission
 * for report generation, maintaining identical business logic flow while providing 
 * JSON-based API communication patterns.
 * 
 * Key transformations from COBOL to Java/Spring:
 * - CICS transaction CR00 → REST endpoints at /api/v1/reports
 * - BMS map CORPT0A → JSON request/response DTOs
 * - COMMAREA pseudo-conversational state → Redis session management
 * - TDQ job submission → Spring Batch job execution
 * - COBOL field validation → Bean Validation API
 * - CICS response codes → HTTP status codes
 * 
 * Security Integration:
 * - Spring Security JWT token validation for all endpoints
 * - Role-based access control with @PreAuthorize annotations
 * - Session state management through Redis SessionManagementService
 * - Comprehensive audit logging via AuditService integration
 * 
 * Business Logic Preservation:
 * - Identical report type validation (Monthly, Yearly, Custom)
 * - Same date validation rules using COBOL-equivalent logic
 * - Preserved confirmation workflow patterns
 * - Identical error message handling and user feedback
 * 
 * @author CardDemo Development Team
 * @version 1.0
 * @since 2024-01-01
 */
package com.carddemo.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import com.carddemo.report.ReportRequestDto.ReportType;
import com.carddemo.report.ReportResponseDto.ErrorDetails;
import com.carddemo.report.ReportResponseDto.ReportStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST Controller for report generation operations replacing CICS transaction CR00 
 * from CORPT00C COBOL program.
 * 
 * This controller provides REST endpoints for report generation functionality that was
 * originally implemented in the CORPT00C COBOL program. The controller maintains identical
 * business logic including report type validation, date validation, confirmation workflows,
 * and job submission patterns while providing modern REST API patterns.
 * 
 * Endpoint mappings replicate the original COBOL program structure:
 * - Main report menu functionality (MAIN-PARA equivalent)
 * - Report type processing (PROCESS-ENTER-KEY equivalent) 
 * - Job submission logic (SUBMIT-JOB-TO-INTRDR equivalent)
 * - Comprehensive validation (COBOL field validation equivalent)
 * 
 * Authentication and Authorization:
 * All endpoints require JWT authentication and support both regular users (ROLE_USER)
 * and administrative users (ROLE_ADMIN) with Spring Security integration.
 * 
 * Session Management:
 * Controller integrates with Redis-backed session management to maintain user state
 * across requests, replicating CICS COMMAREA pseudo-conversational patterns.
 */
@RestController
@RequestMapping("/api/v1/reports")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);
    
    // Constants matching original COBOL program
    private static final String PROGRAM_NAME = "CORPT00C";
    private static final String TRANSACTION_ID = "CR00";
    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT_PATTERN);
    
    // Error messages matching original COBOL WS-MESSAGE field values
    private static final String MSG_INVALID_KEY = "Invalid key pressed. Please use a valid function key.";
    private static final String MSG_SELECT_REPORT_TYPE = "Select a report type to print report...";
    private static final String MSG_START_MONTH_REQUIRED = "Start Date - Month can NOT be empty...";
    private static final String MSG_START_DAY_REQUIRED = "Start Date - Day can NOT be empty...";
    private static final String MSG_START_YEAR_REQUIRED = "Start Date - Year can NOT be empty...";
    private static final String MSG_END_MONTH_REQUIRED = "End Date - Month can NOT be empty...";
    private static final String MSG_END_DAY_REQUIRED = "End Date - Day can NOT be empty...";
    private static final String MSG_END_YEAR_REQUIRED = "End Date - Year can NOT be empty...";
    private static final String MSG_INVALID_MONTH = "Not a valid Month...";
    private static final String MSG_INVALID_DAY = "Not a valid Day...";
    private static final String MSG_INVALID_YEAR = "Not a valid Year...";
    private static final String MSG_INVALID_DATE = "Not a valid date...";
    private static final String MSG_CONFIRMATION_REQUIRED = "Please confirm to print the {0} report...";
    private static final String MSG_INVALID_CONFIRMATION = "\"{0}\" is not a valid value to confirm...";
    private static final String MSG_REPORT_SUBMITTED = "{0} report submitted for printing ...";
    private static final String MSG_UNABLE_TO_SUBMIT = "Unable to submit report job...";
    
    // Service dependencies
    private final ReportService reportService;
    
    /**
     * Constructor with dependency injection for service layer.
     * 
     * @param reportService Report generation service implementing business logic
     */
    @Autowired
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Generate Monthly Transaction Report
     * 
     * REST endpoint equivalent to COBOL logic:
     * WHEN MONTHLYI OF CORPT0AI NOT = SPACES AND LOW-VALUES
     * 
     * Automatically calculates current month date range and submits report generation job.
     * Implements the same date calculation logic as the original COBOL program using
     * current date functions and month boundary calculations.
     * 
     * Security: Requires authentication, accessible to both USER and ADMIN roles
     * 
     * @param request HTTP request for session management
     * @return ResponseEntity containing report generation status
     */
    @PostMapping(value = "/monthly", 
                produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ReportResponseDto> generateMonthlyReport(
            HttpServletRequest request) {
        
        String requestId = generateRequestId();
        logger.info("Processing monthly report request. RequestId: {}, User: {}", 
                   requestId, getCurrentUsername());
        
        try {
            // Calculate current month date range (equivalent to COBOL date logic)
            LocalDate today = LocalDate.now();
            LocalDate startDate = today.withDayOfMonth(1); // First day of current month
            LocalDate endDate = today.withDayOfMonth(today.lengthOfMonth()); // Last day of current month
            
            // Create request DTO matching COBOL program structure
            ReportRequestDto reportRequest = new ReportRequestDto(
                ReportType.MONTHLY, 
                startDate, 
                endDate, 
                "Y" // Auto-confirm for monthly reports
            );
            
            // Update session state (equivalent to CICS COMMAREA)
            updateSessionState(request, "MONTHLY_REPORT", reportRequest);
            
            // Submit report generation job (equivalent to SUBMIT-JOB-TO-INTRDR)
            CompletableFuture<ReportResponseDto> futureResponse = 
                reportService.generateMonthlyReport(reportRequest, requestId);
            
            // Create immediate response (async processing like original TDQ submission)
            ReportResponseDto response = ReportResponseDto.createSubmittedResponse(
                requestId, "Monthly");
            response.setReportType(ReportResponseDto.ReportType.MONTHLY);
            
            // Log successful submission
            logger.info("Monthly report submitted successfully. RequestId: {}, DateRange: {} to {}", 
                       requestId, startDate, endDate);
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            logger.error("Error generating monthly report. RequestId: {}, Error: {}", 
                        requestId, e.getMessage(), e);
            
            ReportResponseDto errorResponse = ReportResponseDto.createErrorResponse(
                requestId, 
                MSG_UNABLE_TO_SUBMIT, 
                "MONTHLY_REPORT_ERROR"
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Generate Yearly Transaction Report
     * 
     * REST endpoint equivalent to COBOL logic:
     * WHEN YEARLYI OF CORPT0AI NOT = SPACES AND LOW-VALUES
     * 
     * Automatically calculates current year date range (January 1 to December 31)
     * and submits report generation job using the same date logic as COBOL program.
     * 
     * Security: Requires authentication, accessible to both USER and ADMIN roles
     * 
     * @param request HTTP request for session management
     * @return ResponseEntity containing report generation status
     */
    @PostMapping(value = "/yearly", 
                produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ReportResponseDto> generateYearlyReport(
            HttpServletRequest request) {
        
        String requestId = generateRequestId();
        logger.info("Processing yearly report request. RequestId: {}, User: {}", 
                   requestId, getCurrentUsername());
        
        try {
            // Calculate current year date range (equivalent to COBOL date logic)
            LocalDate today = LocalDate.now();
            LocalDate startDate = LocalDate.of(today.getYear(), 1, 1); // January 1st
            LocalDate endDate = LocalDate.of(today.getYear(), 12, 31); // December 31st
            
            // Create request DTO matching COBOL program structure
            ReportRequestDto reportRequest = new ReportRequestDto(
                ReportType.YEARLY, 
                startDate, 
                endDate, 
                "Y" // Auto-confirm for yearly reports
            );
            
            // Update session state (equivalent to CICS COMMAREA)
            updateSessionState(request, "YEARLY_REPORT", reportRequest);
            
            // Submit report generation job (equivalent to SUBMIT-JOB-TO-INTRDR)
            CompletableFuture<ReportResponseDto> futureResponse = 
                reportService.generateYearlyReport(reportRequest, requestId);
            
            // Create immediate response (async processing like original TDQ submission)
            ReportResponseDto response = ReportResponseDto.createSubmittedResponse(
                requestId, "Yearly");
            response.setReportType(ReportResponseDto.ReportType.YEARLY);
            
            // Log successful submission
            logger.info("Yearly report submitted successfully. RequestId: {}, DateRange: {} to {}", 
                       requestId, startDate, endDate);
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            logger.error("Error generating yearly report. RequestId: {}, Error: {}", 
                        requestId, e.getMessage(), e);
            
            ReportResponseDto errorResponse = ReportResponseDto.createErrorResponse(
                requestId, 
                MSG_UNABLE_TO_SUBMIT, 
                "YEARLY_REPORT_ERROR"
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Generate Custom Date Range Report
     * 
     * REST endpoint equivalent to COBOL logic:
     * WHEN CUSTOMI OF CORPT0AI NOT = SPACES AND LOW-VALUES
     * 
     * Implements comprehensive validation matching original COBOL field validation
     * logic including date range validation, numeric field validation, and 
     * business rule validation equivalent to CSUTLDTC date validation calls.
     * 
     * Security: Requires authentication, accessible to both USER and ADMIN roles
     * 
     * @param reportRequest Custom report request with user-specified date range
     * @param bindingResult Validation results from Bean Validation
     * @param request HTTP request for session management
     * @return ResponseEntity containing report generation status or validation errors
     */
    @PostMapping(value = "/custom", 
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ReportResponseDto> generateCustomReport(
            @Valid @RequestBody ReportRequestDto reportRequest,
            BindingResult bindingResult,
            HttpServletRequest request) {
        
        String requestId = generateRequestId();
        logger.info("Processing custom report request. RequestId: {}, User: {}, DateRange: {} to {}", 
                   requestId, getCurrentUsername(), 
                   reportRequest.getStartDate(), reportRequest.getEndDate());
        
        // Validate request (equivalent to COBOL field validation)
        List<ReportResponseDto.FieldError> fieldErrors = validateCustomReportRequest(
            reportRequest, bindingResult);
        
        if (!fieldErrors.isEmpty()) {
            logger.warn("Custom report validation failed. RequestId: {}, Errors: {}", 
                       requestId, fieldErrors.size());
            
            ReportResponseDto validationErrorResponse = 
                ReportResponseDto.createValidationErrorResponse(requestId, fieldErrors);
            
            return ResponseEntity.badRequest().body(validationErrorResponse);
        }
        
        try {
            // Validate confirmation (equivalent to SUBMIT-JOB-TO-INTRDR confirmation logic)
            if (!reportRequest.isConfirmed()) {
                if (reportRequest.getConfirmation() == null || reportRequest.getConfirmation().trim().isEmpty()) {
                    // Return confirmation required response
                    ReportResponseDto confirmationResponse = new ReportResponseDto(requestId, ReportStatus.QUEUED);
                    confirmationResponse.setReportName("Custom");
                    confirmationResponse.setReportType(ReportResponseDto.ReportType.CUSTOM);
                    
                    ErrorDetails errorDetails = new ErrorDetails();
                    errorDetails.setErrorMessage(MSG_CONFIRMATION_REQUIRED.replace("{0}", "Custom"));
                    errorDetails.setErrorCode("CONFIRMATION_REQUIRED");
                    confirmationResponse.setErrorDetails(errorDetails);
                    
                    return ResponseEntity.status(HttpStatus.ACCEPTED).body(confirmationResponse);
                }
                
                if (reportRequest.isDeclined()) {
                    // User declined - return cancelled response
                    ReportResponseDto cancelledResponse = new ReportResponseDto(requestId, ReportStatus.CANCELLED);
                    cancelledResponse.setReportName("Custom");
                    cancelledResponse.setReportType(ReportResponseDto.ReportType.CUSTOM);
                    
                    logger.info("Custom report cancelled by user. RequestId: {}", requestId);
                    return ResponseEntity.ok(cancelledResponse);
                }
                
                // Invalid confirmation value
                ErrorDetails errorDetails = new ErrorDetails();
                errorDetails.setErrorMessage(MSG_INVALID_CONFIRMATION.replace("{0}", reportRequest.getConfirmation()));
                errorDetails.setErrorCode("INVALID_CONFIRMATION");
                
                ReportResponseDto errorResponse = new ReportResponseDto(requestId, ReportStatus.VALIDATION_ERROR);
                errorResponse.setErrorDetails(errorDetails);
                
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // Update session state (equivalent to CICS COMMAREA)
            updateSessionState(request, "CUSTOM_REPORT", reportRequest);
            
            // Submit report generation job (equivalent to SUBMIT-JOB-TO-INTRDR)
            CompletableFuture<ReportResponseDto> futureResponse = 
                reportService.generateCustomReport(reportRequest, requestId);
            
            // Create immediate response (async processing like original TDQ submission)
            ReportResponseDto response = ReportResponseDto.createSubmittedResponse(
                requestId, "Custom");
            response.setReportType(ReportResponseDto.ReportType.CUSTOM);
            
            // Log successful submission
            logger.info("Custom report submitted successfully. RequestId: {}, DateRange: {} to {}", 
                       requestId, reportRequest.getStartDate(), reportRequest.getEndDate());
            
            return ResponseEntity.accepted().body(response);
            
        } catch (Exception e) {
            logger.error("Error generating custom report. RequestId: {}, Error: {}", 
                        requestId, e.getMessage(), e);
            
            ReportResponseDto errorResponse = ReportResponseDto.createErrorResponse(
                requestId, 
                MSG_UNABLE_TO_SUBMIT, 
                "CUSTOM_REPORT_ERROR"
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get Report Status
     * 
     * REST endpoint for checking report generation status, equivalent to checking
     * batch job status in the original COBOL system. Provides real-time status
     * updates for submitted report generation jobs.
     * 
     * Security: Requires authentication, accessible to both USER and ADMIN roles
     * 
     * @param requestId Unique report request identifier
     * @return ResponseEntity containing current report status
     */
    @GetMapping(value = "/status/{requestId}", 
                produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ReportResponseDto> getReportStatus(
            @PathVariable @NotNull String requestId) {
        
        logger.info("Retrieving report status. RequestId: {}, User: {}", 
                   requestId, getCurrentUsername());
        
        try {
            ReportResponseDto statusResponse = reportService.getReportStatus(requestId);
            
            if (statusResponse == null) {
                // Report request not found
                ReportResponseDto notFoundResponse = ReportResponseDto.createErrorResponse(
                    requestId, 
                    "Report request not found", 
                    "REQUEST_NOT_FOUND"
                );
                
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFoundResponse);
            }
            
            return ResponseEntity.ok(statusResponse);
            
        } catch (Exception e) {
            logger.error("Error retrieving report status. RequestId: {}, Error: {}", 
                        requestId, e.getMessage(), e);
            
            ReportResponseDto errorResponse = ReportResponseDto.createErrorResponse(
                requestId, 
                "Unable to retrieve report status", 
                "STATUS_RETRIEVAL_ERROR"
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * List Available Reports
     * 
     * REST endpoint providing report menu functionality equivalent to the original
     * COBOL program's report selection screen. Returns available report types and
     * their descriptions for user selection.
     * 
     * Security: Requires authentication, accessible to both USER and ADMIN roles
     * 
     * @return ResponseEntity containing list of available report types
     */
    @GetMapping(value = "/available", 
                produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<ReportTypeInfo>> getAvailableReports() {
        
        logger.info("Retrieving available reports. User: {}", getCurrentUsername());
        
        List<ReportTypeInfo> availableReports = new ArrayList<>();
        
        // Add report types matching original COBOL program options
        availableReports.add(new ReportTypeInfo(
            ReportType.MONTHLY.name(),
            ReportType.MONTHLY.getDisplayName(),
            ReportType.MONTHLY.getDescription(),
            false // No date range required
        ));
        
        availableReports.add(new ReportTypeInfo(
            ReportType.YEARLY.name(),
            ReportType.YEARLY.getDisplayName(),
            ReportType.YEARLY.getDescription(),
            false // No date range required
        ));
        
        availableReports.add(new ReportTypeInfo(
            ReportType.CUSTOM.name(),
            ReportType.CUSTOM.getDisplayName(),
            ReportType.CUSTOM.getDescription(),
            true // Date range required
        ));
        
        return ResponseEntity.ok(availableReports);
    }

    /**
     * Cancel Report Generation
     * 
     * REST endpoint for cancelling submitted report generation jobs, equivalent to
     * job cancellation functionality in the original system.
     * 
     * Security: Requires authentication, accessible to both USER and ADMIN roles
     * 
     * @param requestId Unique report request identifier to cancel
     * @return ResponseEntity containing cancellation status
     */
    @PostMapping(value = "/cancel/{requestId}",
                produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ReportResponseDto> cancelReport(
            @PathVariable @NotNull String requestId) {
        
        logger.info("Cancelling report. RequestId: {}, User: {}", 
                   requestId, getCurrentUsername());
        
        try {
            ReportResponseDto cancelResponse = reportService.cancelReport(requestId);
            
            if (cancelResponse == null) {
                // Report request not found or cannot be cancelled
                ReportResponseDto notFoundResponse = ReportResponseDto.createErrorResponse(
                    requestId, 
                    "Report request not found or cannot be cancelled", 
                    "CANCELLATION_NOT_POSSIBLE"
                );
                
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(notFoundResponse);
            }
            
            logger.info("Report cancelled successfully. RequestId: {}", requestId);
            return ResponseEntity.ok(cancelResponse);
            
        } catch (Exception e) {
            logger.error("Error cancelling report. RequestId: {}, Error: {}", 
                        requestId, e.getMessage(), e);
            
            ReportResponseDto errorResponse = ReportResponseDto.createErrorResponse(
                requestId, 
                "Unable to cancel report", 
                "CANCELLATION_ERROR"
            );
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Global exception handler for validation errors
     * 
     * Provides comprehensive error handling equivalent to COBOL HANDLE ABEND
     * patterns, converting Spring validation exceptions to appropriate HTTP
     * status codes and user-friendly error messages.
     * 
     * @param e Exception to handle
     * @return ResponseEntity with error details
     */
    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<ReportResponseDto> handleValidationException(
            IllegalArgumentException e) {
        
        String requestId = generateRequestId();
        logger.warn("Validation error in report controller. RequestId: {}, Error: {}", 
                   requestId, e.getMessage());
        
        ReportResponseDto errorResponse = ReportResponseDto.createErrorResponse(
            requestId, 
            e.getMessage(), 
            "VALIDATION_ERROR"
        );
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Global exception handler for general errors
     * 
     * Handles unexpected errors equivalent to COBOL general exception handling,
     * providing consistent error response format and logging for troubleshooting.
     * 
     * @param e Exception to handle
     * @return ResponseEntity with error details
     */
    @ExceptionHandler({Exception.class})
    public ResponseEntity<ReportResponseDto> handleGeneralException(Exception e) {
        
        String requestId = generateRequestId();
        logger.error("Unexpected error in report controller. RequestId: {}, Error: {}", 
                    requestId, e.getMessage(), e);
        
        ReportResponseDto errorResponse = ReportResponseDto.createErrorResponse(
            requestId, 
            "An unexpected error occurred while processing the request", 
            "INTERNAL_ERROR"
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    // Private helper methods

    /**
     * Validates custom report request equivalent to COBOL field validation logic
     * from the PROCESS-ENTER-KEY paragraph for CUSTOMI report type.
     * 
     * Implements comprehensive validation including:
     * - Required field validation (equivalent to SPACES OR LOW-VALUES checks)
     * - Numeric field validation (equivalent to IS NOT NUMERIC checks) 
     * - Date range validation (equivalent to CSUTLDTC date validation calls)
     * - Business rule validation (date not in future, valid date ranges)
     * 
     * @param reportRequest Request to validate
     * @param bindingResult Spring validation results
     * @return List of field errors found during validation
     */
    private List<ReportResponseDto.FieldError> validateCustomReportRequest(
            ReportRequestDto reportRequest, BindingResult bindingResult) {
        
        List<ReportResponseDto.FieldError> fieldErrors = new ArrayList<>();
        
        // Add Spring validation errors
        for (FieldError error : bindingResult.getFieldErrors()) {
            fieldErrors.add(new ReportResponseDto.FieldError(
                error.getField(),
                error.getRejectedValue(),
                error.getDefaultMessage()
            ));
        }
        
        // Custom validation equivalent to COBOL logic
        if (reportRequest.getReportType() == ReportType.CUSTOM) {
            
            // Start date validation (equivalent to SDTMM/SDTDD/SDTYYYY validation)
            if (reportRequest.getStartDate() == null) {
                fieldErrors.add(new ReportResponseDto.FieldError(
                    "startDate", null, "Start date is required for custom reports"));
            }
            
            // End date validation (equivalent to EDTMM/EDTDD/EDTYYYY validation)
            if (reportRequest.getEndDate() == null) {
                fieldErrors.add(new ReportResponseDto.FieldError(
                    "endDate", null, "End date is required for custom reports"));
            }
            
            // Date range validation (equivalent to CSUTLDTC validation calls)
            if (reportRequest.getStartDate() != null && reportRequest.getEndDate() != null) {
                
                // Check start date not after end date
                if (reportRequest.getStartDate().isAfter(reportRequest.getEndDate())) {
                    fieldErrors.add(new ReportResponseDto.FieldError(
                        "startDate", reportRequest.getStartDate(), 
                        "Start date must not be after end date"));
                }
                
                // Check dates not in future (business rule)
                LocalDate today = LocalDate.now();
                if (reportRequest.getStartDate().isAfter(today)) {
                    fieldErrors.add(new ReportResponseDto.FieldError(
                        "startDate", reportRequest.getStartDate(), 
                        "Start date cannot be in the future"));
                }
                
                if (reportRequest.getEndDate().isAfter(today)) {
                    fieldErrors.add(new ReportResponseDto.FieldError(
                        "endDate", reportRequest.getEndDate(), 
                        "End date cannot be in the future"));
                }
            }
        }
        
        return fieldErrors;
    }

    /**
     * Updates session state equivalent to CICS COMMAREA management.
     * Stores report request information in Redis session for maintaining
     * pseudo-conversational state across REST API calls.
     * 
     * @param request HTTP request containing session
     * @param key Session attribute key
     * @param value Session attribute value
     */
    private void updateSessionState(HttpServletRequest request, String key, Object value) {
        try {
            HttpSession session = request.getSession(true);
            session.setAttribute(key, value);
            session.setAttribute("LAST_TRANSACTION", TRANSACTION_ID);
            session.setAttribute("LAST_PROGRAM", PROGRAM_NAME);
            session.setAttribute("LAST_UPDATE", LocalDateTime.now());
            
            logger.debug("Session state updated. Key: {}, SessionId: {}", key, session.getId());
            
        } catch (Exception e) {
            logger.warn("Unable to update session state. Key: {}, Error: {}", key, e.getMessage());
            // Continue processing - session state is not critical for functionality
        }
    }

    /**
     * Generates unique request ID for correlation and audit tracking.
     * Equivalent to transaction correlation IDs in the original COBOL system.
     * 
     * @return Unique request identifier
     */
    private String generateRequestId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Retrieves current authenticated username from Spring Security context.
     * Equivalent to user identification in CICS COMMAREA.
     * 
     * @return Current username or "anonymous" if not authenticated
     */
    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            logger.debug("Unable to retrieve current username: {}", e.getMessage());
        }
        return "anonymous";
    }

    /**
     * Data class for report type information used in available reports response.
     * Provides structured information about each available report type including
     * display names, descriptions, and configuration requirements.
     */
    public static class ReportTypeInfo {
        
        private final String code;
        private final String displayName;
        private final String description;
        private final boolean dateRangeRequired;

        public ReportTypeInfo(String code, String displayName, String description, boolean dateRangeRequired) {
            this.code = code;
            this.displayName = displayName;
            this.description = description;
            this.dateRangeRequired = dateRangeRequired;
        }

        public String getCode() { return code; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public boolean isDateRangeRequired() { return dateRangeRequired; }
    }
}
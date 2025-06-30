package com.carddemo.report;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for report generation responses providing JSON-based REST API 
 * response structure. Handles report status tracking, error messages, report data payload, 
 * and metadata with comprehensive status reporting equivalent to original COBOL 
 * job submission and processing patterns from CORPT00C program.
 * 
 * This DTO supports:
 * - Report status tracking matching COBOL job submission status messages
 * - Report data payload structure for transaction report results based on TRAN-RECORD
 * - Error message handling equivalent to COBOL WS-MESSAGE field validation  
 * - Report metadata including generation timestamp, record count, and processing status
 * - Multiple output format indicators matching JasperReports format requirements
 * - JSON serialization for REST API communication
 * 
 * Transformation from COBOL patterns:
 * - WS-MESSAGE field (PIC X(80)) → errorMessage field with validation
 * - WS-REPORT-NAME (PIC X(10)) → reportName field 
 * - WS-REC-COUNT (PIC S9(04) COMP) → recordCount field
 * - JCL job status tracking → reportStatus enum
 * - TRAN-RECORD structure → reportData.transactionRecords
 * 
 * @author CardDemo Development Team
 * @version 1.0
 * @since 2024-01-01
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "apiVersion", "requestId", "timestamp", "reportStatus", "reportName", 
    "reportType", "outputFormat", "reportMetadata", "reportData", "errorDetails"
})
public class ReportResponseDto {

    /**
     * API version for response payload compatibility tracking
     */
    @JsonProperty("apiVersion")
    @NotNull(message = "API version is required")
    @Pattern(regexp = "^v[0-9]+$", message = "API version must follow v{number} format")
    private String apiVersion = "v1";

    /**
     * Unique request identifier for correlation and audit tracking
     */
    @JsonProperty("requestId")
    @NotNull(message = "Request ID is required")
    @Size(min = 1, max = 64, message = "Request ID must be between 1 and 64 characters")
    private String requestId;

    /**
     * Response generation timestamp in ISO-8601 format
     */
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @NotNull(message = "Timestamp is required")
    private LocalDateTime timestamp;

    /**
     * Report generation status equivalent to COBOL job submission status messages.
     * Maps to various states from CORPT00C program processing including 
     * submission confirmation, processing status, and completion state.
     */
    @JsonProperty("reportStatus")
    @NotNull(message = "Report status is required")
    private ReportStatus reportStatus;

    /**
     * Report name matching WS-REPORT-NAME from CORPT00C (Monthly, Yearly, Custom)
     */
    @JsonProperty("reportName")
    @Size(max = 50, message = "Report name cannot exceed 50 characters")
    private String reportName;

    /**
     * Report type indicator for categorization and processing logic
     */
    @JsonProperty("reportType")
    private ReportType reportType;

    /**
     * Output format specification supporting multiple JasperReports formats
     */
    @JsonProperty("outputFormat")
    private OutputFormat outputFormat;

    /**
     * Comprehensive report metadata including generation details, 
     * processing statistics, and audit information
     */
    @JsonProperty("reportMetadata")
    @Valid
    private ReportMetadata reportMetadata;

    /**
     * Report data payload containing transaction records and summary information.
     * Structure based on TRAN-RECORD from CVTRA05Y copybook.
     */
    @JsonProperty("reportData")
    @Valid
    private ReportData reportData;

    /**
     * Error details when report generation fails, equivalent to COBOL 
     * WS-MESSAGE field error reporting and validation messages
     */
    @JsonProperty("errorDetails")
    @Valid
    private ErrorDetails errorDetails;

    /**
     * Report generation status enumeration equivalent to COBOL job submission 
     * and processing status tracking from CORPT00C program
     */
    public enum ReportStatus {
        /** Report request submitted successfully - equivalent to "report submitted for printing" message */
        SUBMITTED("Report request submitted for processing"),
        
        /** Report generation in progress - equivalent to JCL job execution status */
        IN_PROGRESS("Report generation is currently in progress"),
        
        /** Report generation completed successfully - equivalent to successful job completion */
        COMPLETED("Report generation completed successfully"),
        
        /** Report generation failed - equivalent to job failure or abend conditions */
        FAILED("Report generation failed due to errors"),
        
        /** Report generation cancelled by user or system - equivalent to job cancellation */
        CANCELLED("Report generation was cancelled"),
        
        /** Report request queued for processing - equivalent to JCL job queue status */
        QUEUED("Report request is queued for processing"),
        
        /** Report data validation failed - equivalent to COBOL field validation errors */
        VALIDATION_ERROR("Report request failed validation");

        private final String description;

        ReportStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Report type enumeration for categorizing different report types
     */
    public enum ReportType {
        /** Monthly transaction report - matches "Monthly" from CORPT00C */
        MONTHLY("Monthly Transaction Report"),
        
        /** Yearly transaction report - matches "Yearly" from CORPT00C */
        YEARLY("Yearly Transaction Report"),
        
        /** Custom date range report - matches "Custom" from CORPT00C */
        CUSTOM("Custom Date Range Report"),
        
        /** Account summary report */
        ACCOUNT_SUMMARY("Account Summary Report"),
        
        /** Card activity report */
        CARD_ACTIVITY("Card Activity Report"),
        
        /** Payment history report */
        PAYMENT_HISTORY("Payment History Report");

        private final String description;

        ReportType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Output format enumeration supporting JasperReports format requirements
     */
    public enum OutputFormat {
        /** PDF format for printable reports */
        PDF("application/pdf"),
        
        /** CSV format for data export */
        CSV("text/csv"),
        
        /** Excel format for spreadsheet compatibility */
        EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        
        /** JSON format for API integration */
        JSON("application/json"),
        
        /** XML format for structured data exchange */
        XML("application/xml"),
        
        /** HTML format for web display */
        HTML("text/html");

        private final String mimeType;

        OutputFormat(String mimeType) {
            this.mimeType = mimeType;
        }

        public String getMimeType() {
            return mimeType;
        }
    }

    /**
     * Report metadata containing generation details and processing statistics.
     * Equivalent to various working storage fields from CORPT00C program.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReportMetadata {

        /**
         * Report generation start timestamp
         */
        @JsonProperty("generationStartTime")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        @JsonSerialize(using = LocalDateTimeSerializer.class)
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        private LocalDateTime generationStartTime;

        /**
         * Report generation completion timestamp
         */
        @JsonProperty("generationEndTime")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        @JsonSerialize(using = LocalDateTimeSerializer.class)
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        private LocalDateTime generationEndTime;

        /**
         * Total processing time in milliseconds
         */
        @JsonProperty("processingTimeMs")
        @PositiveOrZero(message = "Processing time must be non-negative")
        private Long processingTimeMs;

        /**
         * Total number of records processed - equivalent to WS-REC-COUNT from CORPT00C
         */
        @JsonProperty("recordCount")
        @PositiveOrZero(message = "Record count must be non-negative")
        private Integer recordCount;

        /**
         * Number of records that passed validation
         */
        @JsonProperty("validRecordCount")
        @PositiveOrZero(message = "Valid record count must be non-negative")
        private Integer validRecordCount;

        /**
         * Number of records that failed validation
         */
        @JsonProperty("invalidRecordCount")
        @PositiveOrZero(message = "Invalid record count must be non-negative")
        private Integer invalidRecordCount;

        /**
         * Report parameters used for generation
         */
        @JsonProperty("reportParameters")
        private Map<String, Object> reportParameters;

        /**
         * Generated file information when report is saved to file
         */
        @JsonProperty("generatedFileInfo")
        private GeneratedFileInfo generatedFileInfo;

        /**
         * User ID who requested the report - for audit trail
         */
        @JsonProperty("requestedBy")
        @Size(max = 8, message = "User ID cannot exceed 8 characters")
        private String requestedBy;

        /**
         * Report generation server/node identifier for distributed processing
         */
        @JsonProperty("generationNode")
        @Size(max = 32, message = "Generation node identifier cannot exceed 32 characters")
        private String generationNode;

        // Constructors
        public ReportMetadata() {}

        // Getters and Setters
        public LocalDateTime getGenerationStartTime() { return generationStartTime; }
        public void setGenerationStartTime(LocalDateTime generationStartTime) { this.generationStartTime = generationStartTime; }

        public LocalDateTime getGenerationEndTime() { return generationEndTime; }
        public void setGenerationEndTime(LocalDateTime generationEndTime) { this.generationEndTime = generationEndTime; }

        public Long getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }

        public Integer getRecordCount() { return recordCount; }
        public void setRecordCount(Integer recordCount) { this.recordCount = recordCount; }

        public Integer getValidRecordCount() { return validRecordCount; }
        public void setValidRecordCount(Integer validRecordCount) { this.validRecordCount = validRecordCount; }

        public Integer getInvalidRecordCount() { return invalidRecordCount; }
        public void setInvalidRecordCount(Integer invalidRecordCount) { this.invalidRecordCount = invalidRecordCount; }

        public Map<String, Object> getReportParameters() { return reportParameters; }
        public void setReportParameters(Map<String, Object> reportParameters) { this.reportParameters = reportParameters; }

        public GeneratedFileInfo getGeneratedFileInfo() { return generatedFileInfo; }
        public void setGeneratedFileInfo(GeneratedFileInfo generatedFileInfo) { this.generatedFileInfo = generatedFileInfo; }

        public String getRequestedBy() { return requestedBy; }
        public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

        public String getGenerationNode() { return generationNode; }
        public void setGenerationNode(String generationNode) { this.generationNode = generationNode; }
    }

    /**
     * Generated file information for saved reports
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GeneratedFileInfo {

        /**
         * Generated file name
         */
        @JsonProperty("fileName")
        @Size(max = 255, message = "File name cannot exceed 255 characters")
        private String fileName;

        /**
         * File size in bytes
         */
        @JsonProperty("fileSizeBytes")
        @PositiveOrZero(message = "File size must be non-negative")
        private Long fileSizeBytes;

        /**
         * File download URL or path
         */
        @JsonProperty("downloadUrl")
        @Size(max = 512, message = "Download URL cannot exceed 512 characters")
        private String downloadUrl;

        /**
         * File content type/MIME type
         */
        @JsonProperty("contentType")
        @Size(max = 100, message = "Content type cannot exceed 100 characters")
        private String contentType;

        /**
         * File expiration timestamp for temporary files
         */
        @JsonProperty("expirationTime")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        @JsonSerialize(using = LocalDateTimeSerializer.class)
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        private LocalDateTime expirationTime;

        // Constructors
        public GeneratedFileInfo() {}

        // Getters and Setters
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public Long getFileSizeBytes() { return fileSizeBytes; }
        public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

        public String getDownloadUrl() { return downloadUrl; }
        public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }

        public LocalDateTime getExpirationTime() { return expirationTime; }
        public void setExpirationTime(LocalDateTime expirationTime) { this.expirationTime = expirationTime; }
    }

    /**
     * Report data payload containing transaction records and summary information.
     * Structure based on TRAN-RECORD from CVTRA05Y copybook and report results.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReportData {

        /**
         * Transaction records included in the report based on TRAN-RECORD structure
         */
        @JsonProperty("transactionRecords")
        @Valid
        private List<TransactionRecord> transactionRecords;

        /**
         * Report summary statistics and aggregated data
         */
        @JsonProperty("reportSummary")
        @Valid
        private ReportSummary reportSummary;

        /**
         * Additional report sections for complex reports
         */
        @JsonProperty("additionalSections")
        private Map<String, Object> additionalSections;

        // Constructors
        public ReportData() {}

        // Getters and Setters
        public List<TransactionRecord> getTransactionRecords() { return transactionRecords; }
        public void setTransactionRecords(List<TransactionRecord> transactionRecords) { this.transactionRecords = transactionRecords; }

        public ReportSummary getReportSummary() { return reportSummary; }
        public void setReportSummary(ReportSummary reportSummary) { this.reportSummary = reportSummary; }

        public Map<String, Object> getAdditionalSections() { return additionalSections; }
        public void setAdditionalSections(Map<String, Object> additionalSections) { this.additionalSections = additionalSections; }
    }

    /**
     * Transaction record structure based on TRAN-RECORD from CVTRA05Y copybook.
     * Maintains field names and data types equivalent to COBOL structure for 
     * backward compatibility and accurate data representation.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TransactionRecord {

        /** Transaction ID - equivalent to TRAN-ID PIC X(16) */
        @JsonProperty("TRAN-ID")
        @Size(max = 16, message = "Transaction ID cannot exceed 16 characters")
        private String transactionId;

        /** Transaction type code - equivalent to TRAN-TYPE-CD PIC X(02) */
        @JsonProperty("TRAN-TYPE-CD")
        @Size(max = 2, message = "Transaction type code cannot exceed 2 characters")
        private String transactionTypeCode;

        /** Transaction category code - equivalent to TRAN-CAT-CD PIC 9(04) */
        @JsonProperty("TRAN-CAT-CD")
        @Pattern(regexp = "^\\d{0,4}$", message = "Transaction category code must be numeric up to 4 digits")
        private String transactionCategoryCode;

        /** Transaction source - equivalent to TRAN-SOURCE PIC X(10) */
        @JsonProperty("TRAN-SOURCE")
        @Size(max = 10, message = "Transaction source cannot exceed 10 characters")
        private String transactionSource;

        /** Transaction description - equivalent to TRAN-DESC PIC X(100) */
        @JsonProperty("TRAN-DESC")
        @Size(max = 100, message = "Transaction description cannot exceed 100 characters")
        private String transactionDescription;

        /** Transaction amount - equivalent to TRAN-AMT PIC S9(09)V99 with precise decimal handling */
        @JsonProperty("TRAN-AMT")
        private BigDecimal transactionAmount;

        /** Merchant ID - equivalent to TRAN-MERCHANT-ID PIC 9(09) */
        @JsonProperty("TRAN-MERCHANT-ID")
        @Pattern(regexp = "^\\d{0,9}$", message = "Merchant ID must be numeric up to 9 digits")
        private String merchantId;

        /** Merchant name - equivalent to TRAN-MERCHANT-NAME PIC X(50) */
        @JsonProperty("TRAN-MERCHANT-NAME")
        @Size(max = 50, message = "Merchant name cannot exceed 50 characters")
        private String merchantName;

        /** Merchant city - equivalent to TRAN-MERCHANT-CITY PIC X(50) */
        @JsonProperty("TRAN-MERCHANT-CITY")
        @Size(max = 50, message = "Merchant city cannot exceed 50 characters")
        private String merchantCity;

        /** Merchant zip code - equivalent to TRAN-MERCHANT-ZIP PIC X(10) */
        @JsonProperty("TRAN-MERCHANT-ZIP")
        @Size(max = 10, message = "Merchant zip code cannot exceed 10 characters")
        private String merchantZip;

        /** Card number - equivalent to TRAN-CARD-NUM PIC X(16) */
        @JsonProperty("TRAN-CARD-NUM")
        @Size(max = 16, message = "Card number cannot exceed 16 characters")
        private String cardNumber;

        /** Original timestamp - equivalent to TRAN-ORIG-TS PIC X(26) */
        @JsonProperty("TRAN-ORIG-TS")
        @Size(max = 26, message = "Original timestamp cannot exceed 26 characters")
        private String originalTimestamp;

        /** Processing timestamp - equivalent to TRAN-PROC-TS PIC X(26) */
        @JsonProperty("TRAN-PROC-TS")
        @Size(max = 26, message = "Processing timestamp cannot exceed 26 characters")
        private String processingTimestamp;

        // Constructors
        public TransactionRecord() {}

        // Getters and Setters
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

        public String getTransactionTypeCode() { return transactionTypeCode; }
        public void setTransactionTypeCode(String transactionTypeCode) { this.transactionTypeCode = transactionTypeCode; }

        public String getTransactionCategoryCode() { return transactionCategoryCode; }
        public void setTransactionCategoryCode(String transactionCategoryCode) { this.transactionCategoryCode = transactionCategoryCode; }

        public String getTransactionSource() { return transactionSource; }
        public void setTransactionSource(String transactionSource) { this.transactionSource = transactionSource; }

        public String getTransactionDescription() { return transactionDescription; }
        public void setTransactionDescription(String transactionDescription) { this.transactionDescription = transactionDescription; }

        public BigDecimal getTransactionAmount() { return transactionAmount; }
        public void setTransactionAmount(BigDecimal transactionAmount) { this.transactionAmount = transactionAmount; }

        public String getMerchantId() { return merchantId; }
        public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

        public String getMerchantName() { return merchantName; }
        public void setMerchantName(String merchantName) { this.merchantName = merchantName; }

        public String getMerchantCity() { return merchantCity; }
        public void setMerchantCity(String merchantCity) { this.merchantCity = merchantCity; }

        public String getMerchantZip() { return merchantZip; }
        public void setMerchantZip(String merchantZip) { this.merchantZip = merchantZip; }

        public String getCardNumber() { return cardNumber; }
        public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }

        public String getOriginalTimestamp() { return originalTimestamp; }
        public void setOriginalTimestamp(String originalTimestamp) { this.originalTimestamp = originalTimestamp; }

        public String getProcessingTimestamp() { return processingTimestamp; }
        public void setProcessingTimestamp(String processingTimestamp) { this.processingTimestamp = processingTimestamp; }
    }

    /**
     * Report summary containing aggregated data and statistics
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReportSummary {

        /**
         * Total transaction amount in the report
         */
        @JsonProperty("totalAmount")
        private BigDecimal totalAmount;

        /**
         * Total number of transactions
         */
        @JsonProperty("totalTransactionCount")
        @PositiveOrZero(message = "Total transaction count must be non-negative")
        private Integer totalTransactionCount;

        /**
         * Date range covered by the report
         */
        @JsonProperty("dateRange")
        @Valid
        private DateRange dateRange;

        /**
         * Summary by transaction type
         */
        @JsonProperty("summaryByType")
        private Map<String, BigDecimal> summaryByType;

        /**
         * Summary by merchant category
         */
        @JsonProperty("summaryByCategory")
        private Map<String, BigDecimal> summaryByCategory;

        /**
         * Additional summary statistics
         */
        @JsonProperty("additionalStatistics")
        private Map<String, Object> additionalStatistics;

        // Constructors
        public ReportSummary() {}

        // Getters and Setters
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

        public Integer getTotalTransactionCount() { return totalTransactionCount; }
        public void setTotalTransactionCount(Integer totalTransactionCount) { this.totalTransactionCount = totalTransactionCount; }

        public DateRange getDateRange() { return dateRange; }
        public void setDateRange(DateRange dateRange) { this.dateRange = dateRange; }

        public Map<String, BigDecimal> getSummaryByType() { return summaryByType; }
        public void setSummaryByType(Map<String, BigDecimal> summaryByType) { this.summaryByType = summaryByType; }

        public Map<String, BigDecimal> getSummaryByCategory() { return summaryByCategory; }
        public void setSummaryByCategory(Map<String, BigDecimal> summaryByCategory) { this.summaryByCategory = summaryByCategory; }

        public Map<String, Object> getAdditionalStatistics() { return additionalStatistics; }
        public void setAdditionalStatistics(Map<String, Object> additionalStatistics) { this.additionalStatistics = additionalStatistics; }
    }

    /**
     * Date range specification for report summary
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DateRange {

        /**
         * Start date of the report range
         */
        @JsonProperty("startDate")
        @Size(max = 10, message = "Start date cannot exceed 10 characters")
        private String startDate;

        /**
         * End date of the report range
         */
        @JsonProperty("endDate")
        @Size(max = 10, message = "End date cannot exceed 10 characters")
        private String endDate;

        // Constructors
        public DateRange() {}

        public DateRange(String startDate, String endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }

        // Getters and Setters
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }

        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
    }

    /**
     * Error details for failed report generation, equivalent to COBOL WS-MESSAGE 
     * field error reporting and comprehensive validation error handling.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetails {

        /**
         * Primary error message equivalent to COBOL WS-MESSAGE field (PIC X(80))
         */
        @JsonProperty("errorMessage")
        @Size(max = 255, message = "Error message cannot exceed 255 characters")
        private String errorMessage;

        /**
         * Error code for programmatic error handling
         */
        @JsonProperty("errorCode")
        @Size(max = 20, message = "Error code cannot exceed 20 characters")
        private String errorCode;

        /**
         * Detailed error description for troubleshooting
         */
        @JsonProperty("errorDescription")
        @Size(max = 1000, message = "Error description cannot exceed 1000 characters")
        private String errorDescription;

        /**
         * Field-specific validation errors
         */
        @JsonProperty("fieldErrors")
        private List<FieldError> fieldErrors;

        /**
         * Stack trace information for debugging (non-production)
         */
        @JsonProperty("stackTrace")
        private String stackTrace;

        /**
         * Additional error context and metadata
         */
        @JsonProperty("errorContext")
        private Map<String, Object> errorContext;

        // Constructors
        public ErrorDetails() {}

        public ErrorDetails(String errorMessage, String errorCode) {
            this.errorMessage = errorMessage;
            this.errorCode = errorCode;
        }

        // Getters and Setters
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

        public String getErrorDescription() { return errorDescription; }
        public void setErrorDescription(String errorDescription) { this.errorDescription = errorDescription; }

        public List<FieldError> getFieldErrors() { return fieldErrors; }
        public void setFieldErrors(List<FieldError> fieldErrors) { this.fieldErrors = fieldErrors; }

        public String getStackTrace() { return stackTrace; }
        public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }

        public Map<String, Object> getErrorContext() { return errorContext; }
        public void setErrorContext(Map<String, Object> errorContext) { this.errorContext = errorContext; }
    }

    /**
     * Field-specific validation error information
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldError {

        /**
         * Field name that caused the validation error
         */
        @JsonProperty("fieldName")
        @Size(max = 100, message = "Field name cannot exceed 100 characters")
        private String fieldName;

        /**
         * Field value that failed validation
         */
        @JsonProperty("rejectedValue")
        private Object rejectedValue;

        /**
         * Validation error message
         */
        @JsonProperty("message")
        @Size(max = 255, message = "Validation message cannot exceed 255 characters")
        private String message;

        /**
         * Validation constraint that was violated
         */
        @JsonProperty("constraint")
        @Size(max = 100, message = "Constraint cannot exceed 100 characters")
        private String constraint;

        // Constructors
        public FieldError() {}

        public FieldError(String fieldName, Object rejectedValue, String message) {
            this.fieldName = fieldName;
            this.rejectedValue = rejectedValue;
            this.message = message;
        }

        // Getters and Setters
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }

        public Object getRejectedValue() { return rejectedValue; }
        public void setRejectedValue(Object rejectedValue) { this.rejectedValue = rejectedValue; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getConstraint() { return constraint; }
        public void setConstraint(String constraint) { this.constraint = constraint; }
    }

    // Main class constructors
    public ReportResponseDto() {
        this.timestamp = LocalDateTime.now();
    }

    public ReportResponseDto(String requestId, ReportStatus reportStatus) {
        this();
        this.requestId = requestId;
        this.reportStatus = reportStatus;
    }

    // Main class getters and setters
    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public ReportStatus getReportStatus() { return reportStatus; }
    public void setReportStatus(ReportStatus reportStatus) { this.reportStatus = reportStatus; }

    public String getReportName() { return reportName; }
    public void setReportName(String reportName) { this.reportName = reportName; }

    public ReportType getReportType() { return reportType; }
    public void setReportType(ReportType reportType) { this.reportType = reportType; }

    public OutputFormat getOutputFormat() { return outputFormat; }
    public void setOutputFormat(OutputFormat outputFormat) { this.outputFormat = outputFormat; }

    public ReportMetadata getReportMetadata() { return reportMetadata; }
    public void setReportMetadata(ReportMetadata reportMetadata) { this.reportMetadata = reportMetadata; }

    public ReportData getReportData() { return reportData; }
    public void setReportData(ReportData reportData) { this.reportData = reportData; }

    public ErrorDetails getErrorDetails() { return errorDetails; }
    public void setErrorDetails(ErrorDetails errorDetails) { this.errorDetails = errorDetails; }

    /**
     * Utility method to create a successful response with report data
     */
    public static ReportResponseDto createSuccessResponse(String requestId, String reportName, ReportData reportData) {
        ReportResponseDto response = new ReportResponseDto(requestId, ReportStatus.COMPLETED);
        response.setReportName(reportName);
        response.setReportData(reportData);
        return response;
    }

    /**
     * Utility method to create an error response with error details
     */
    public static ReportResponseDto createErrorResponse(String requestId, String errorMessage, String errorCode) {
        ReportResponseDto response = new ReportResponseDto(requestId, ReportStatus.FAILED);
        ErrorDetails errorDetails = new ErrorDetails(errorMessage, errorCode);
        response.setErrorDetails(errorDetails);
        return response;
    }

    /**
     * Utility method to create a validation error response
     */
    public static ReportResponseDto createValidationErrorResponse(String requestId, List<FieldError> fieldErrors) {
        ReportResponseDto response = new ReportResponseDto(requestId, ReportStatus.VALIDATION_ERROR);
        ErrorDetails errorDetails = new ErrorDetails();
        errorDetails.setErrorMessage("Request validation failed");
        errorDetails.setErrorCode("VALIDATION_ERROR");
        errorDetails.setFieldErrors(fieldErrors);
        response.setErrorDetails(errorDetails);
        return response;
    }

    /**
     * Utility method to create a submitted response for async processing
     */
    public static ReportResponseDto createSubmittedResponse(String requestId, String reportName) {
        ReportResponseDto response = new ReportResponseDto(requestId, ReportStatus.SUBMITTED);
        response.setReportName(reportName);
        return response;
    }
}
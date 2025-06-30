package com.carddemo.transaction;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Transaction Data Transfer Objects for REST API operations.
 * 
 * This class contains nested DTOs that match original BMS map layouts
 * from COBOL transaction processing screens (COTRN00.bms, COTRN01.bms, COTRN02.bms).
 * 
 * Implements comprehensive field validation matching COBOL field validation rules
 * from copybooks CVTRA05Y.cpy, CVTRA01Y.cpy, and CVTRA02Y.cpy.
 * 
 * Supports JSON serialization for REST API request/response patterns and
 * maintains exact field precision per COBOL specifications using BigDecimal
 * for monetary amounts (COMP-3 equivalent).
 * 
 * Architecture: Spring Boot 3.2.x modular monolith with PostgreSQL backend
 * Reference: Section 0.3.1 implementation strategy for BMS to React transformation
 */
public class TransactionDto {

    /**
     * Transaction List Request DTO - Supports paginated transaction viewing
     * Maps to COTRN00.bms transaction list screen (CT00 transaction)
     * Supports 10 transactions per page as per UI requirements
     */
    public static class TransactionListRequest {
        
        @JsonProperty("cardNumber")
        @NotBlank(message = "Card number is required")
        @Pattern(regexp = "^[0-9]{16}$", message = "Card number must be exactly 16 digits")
        private String cardNumber;
        
        @JsonProperty("pageNumber")
        @Min(value = 0, message = "Page number must be 0 or greater")
        @NotNull(message = "Page number is required")
        private Integer pageNumber = 0;
        
        @JsonProperty("pageSize")
        @Min(value = 1, message = "Page size must be at least 1")
        @Max(value = 100, message = "Page size cannot exceed 100")
        @NotNull(message = "Page size is required")
        private Integer pageSize = 10;
        
        @JsonProperty("sortBy")
        @Pattern(regexp = "^(transactionId|transactionDate|amount|merchantName)$", 
                message = "Sort field must be one of: transactionId, transactionDate, amount, merchantName")
        private String sortBy = "transactionDate";
        
        @JsonProperty("sortDirection")
        @Pattern(regexp = "^(asc|desc)$", message = "Sort direction must be 'asc' or 'desc'")
        private String sortDirection = "desc";

        // Constructors
        public TransactionListRequest() {}

        // Getters and Setters
        public String getCardNumber() { return cardNumber; }
        public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
        
        public Integer getPageNumber() { return pageNumber; }
        public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
        
        public Integer getPageSize() { return pageSize; }
        public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
        
        public String getSortBy() { return sortBy; }
        public void setSortBy(String sortBy) { this.sortBy = sortBy; }
        
        public String getSortDirection() { return sortDirection; }
        public void setSortDirection(String sortDirection) { this.sortDirection = sortDirection; }
    }

    /**
     * Transaction Summary DTO for list display
     * Maps to COTRN00.bms paginated transaction display
     * Contains essential fields for transaction list view
     */
    public static class TransactionSummary {
        
        @JsonProperty("transactionId")
        private String transactionId;
        
        @JsonProperty("transactionTypeCode")
        private String transactionTypeCode;
        
        @JsonProperty("transactionAmount")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal transactionAmount;
        
        @JsonProperty("merchantName")
        private String merchantName;
        
        @JsonProperty("transactionDate")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime transactionDate;
        
        @JsonProperty("description")
        private String description;

        // Constructors
        public TransactionSummary() {}

        public TransactionSummary(String transactionId, String transactionTypeCode, 
                                BigDecimal transactionAmount, String merchantName, 
                                LocalDateTime transactionDate, String description) {
            this.transactionId = transactionId;
            this.transactionTypeCode = transactionTypeCode;
            this.transactionAmount = transactionAmount;
            this.merchantName = merchantName;
            this.transactionDate = transactionDate;
            this.description = description;
        }

        // Getters and Setters
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        
        public String getTransactionTypeCode() { return transactionTypeCode; }
        public void setTransactionTypeCode(String transactionTypeCode) { this.transactionTypeCode = transactionTypeCode; }
        
        public BigDecimal getTransactionAmount() { return transactionAmount; }
        public void setTransactionAmount(BigDecimal transactionAmount) { this.transactionAmount = transactionAmount; }
        
        public String getMerchantName() { return merchantName; }
        public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
        
        public LocalDateTime getTransactionDate() { return transactionDate; }
        public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    /**
     * Transaction List Response DTO - Paginated transaction results
     * Maps to COTRN00.bms transaction list screen response
     * Provides pagination metadata and transaction summaries
     */
    public static class TransactionListResponse {
        
        @JsonProperty("transactions")
        @Valid
        private List<TransactionSummary> transactions;
        
        @JsonProperty("pageNumber")
        private Integer pageNumber;
        
        @JsonProperty("pageSize")
        private Integer pageSize;
        
        @JsonProperty("totalElements")
        private Long totalElements;
        
        @JsonProperty("totalPages")
        private Integer totalPages;
        
        @JsonProperty("hasNext")
        private Boolean hasNext;
        
        @JsonProperty("hasPrevious")
        private Boolean hasPrevious;

        // Constructors
        public TransactionListResponse() {}

        // Getters and Setters
        public List<TransactionSummary> getTransactions() { return transactions; }
        public void setTransactions(List<TransactionSummary> transactions) { this.transactions = transactions; }
        
        public Integer getPageNumber() { return pageNumber; }
        public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
        
        public Integer getPageSize() { return pageSize; }
        public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
        
        public Long getTotalElements() { return totalElements; }
        public void setTotalElements(Long totalElements) { this.totalElements = totalElements; }
        
        public Integer getTotalPages() { return totalPages; }
        public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }
        
        public Boolean getHasNext() { return hasNext; }
        public void setHasNext(Boolean hasNext) { this.hasNext = hasNext; }
        
        public Boolean getHasPrevious() { return hasPrevious; }
        public void setHasPrevious(Boolean hasPrevious) { this.hasPrevious = hasPrevious; }
    }

    /**
     * Transaction Detail DTO - Complete transaction information
     * Maps to COTRN01.bms transaction detail screen (CT01 transaction)
     * Based on CVTRA05Y.cpy TRAN-RECORD structure (RECLN = 350)
     * Maintains exact field precision and formatting per COBOL specifications
     */
    public static class TransactionDetail {
        
        @JsonProperty("transactionId")
        @Size(min = 1, max = 16, message = "Transaction ID must be 1-16 characters")
        private String transactionId; // TRAN-ID PIC X(16)
        
        @JsonProperty("transactionTypeCode")
        @Size(min = 2, max = 2, message = "Transaction type code must be exactly 2 characters")
        private String transactionTypeCode; // TRAN-TYPE-CD PIC X(02)
        
        @JsonProperty("transactionCategoryCode")
        @Min(value = 0, message = "Transaction category code must be non-negative")
        @Max(value = 9999, message = "Transaction category code cannot exceed 9999")
        private Integer transactionCategoryCode; // TRAN-CAT-CD PIC 9(04)
        
        @JsonProperty("transactionSource")
        @Size(max = 10, message = "Transaction source cannot exceed 10 characters")
        private String transactionSource; // TRAN-SOURCE PIC X(10)
        
        @JsonProperty("transactionDescription")
        @Size(max = 100, message = "Transaction description cannot exceed 100 characters")
        private String transactionDescription; // TRAN-DESC PIC X(100)
        
        @JsonProperty("transactionAmount")
        @NotNull(message = "Transaction amount is required")
        @DecimalMin(value = "-999999999.99", message = "Transaction amount cannot be less than -999999999.99")
        @DecimalMax(value = "999999999.99", message = "Transaction amount cannot exceed 999999999.99")
        @Digits(integer = 9, fraction = 2, message = "Transaction amount must have at most 9 integer digits and 2 decimal places")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal transactionAmount; // TRAN-AMT PIC S9(09)V99
        
        @JsonProperty("merchantId")
        @Min(value = 0, message = "Merchant ID must be non-negative")
        @Max(value = 999999999, message = "Merchant ID cannot exceed 999999999")
        private Long merchantId; // TRAN-MERCHANT-ID PIC 9(09)
        
        @JsonProperty("merchantName")
        @Size(max = 50, message = "Merchant name cannot exceed 50 characters")
        private String merchantName; // TRAN-MERCHANT-NAME PIC X(50)
        
        @JsonProperty("merchantCity")
        @Size(max = 50, message = "Merchant city cannot exceed 50 characters")
        private String merchantCity; // TRAN-MERCHANT-CITY PIC X(50)
        
        @JsonProperty("merchantZip")
        @Size(max = 10, message = "Merchant ZIP cannot exceed 10 characters")
        @Pattern(regexp = "^[0-9]{5}(-[0-9]{4})?$|^$", message = "Merchant ZIP must be in format 12345 or 12345-6789")
        private String merchantZip; // TRAN-MERCHANT-ZIP PIC X(10)
        
        @JsonProperty("cardNumber")
        @NotBlank(message = "Card number is required")
        @Pattern(regexp = "^[0-9]{16}$", message = "Card number must be exactly 16 digits")
        private String cardNumber; // TRAN-CARD-NUM PIC X(16)
        
        @JsonProperty("originalTimestamp")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSSSSS")
        private LocalDateTime originalTimestamp; // TRAN-ORIG-TS PIC X(26)
        
        @JsonProperty("processingTimestamp")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSSSSS")
        private LocalDateTime processingTimestamp; // TRAN-PROC-TS PIC X(26)

        // Constructors
        public TransactionDetail() {}

        // Getters and Setters
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        
        public String getTransactionTypeCode() { return transactionTypeCode; }
        public void setTransactionTypeCode(String transactionTypeCode) { this.transactionTypeCode = transactionTypeCode; }
        
        public Integer getTransactionCategoryCode() { return transactionCategoryCode; }
        public void setTransactionCategoryCode(Integer transactionCategoryCode) { this.transactionCategoryCode = transactionCategoryCode; }
        
        public String getTransactionSource() { return transactionSource; }
        public void setTransactionSource(String transactionSource) { this.transactionSource = transactionSource; }
        
        public String getTransactionDescription() { return transactionDescription; }
        public void setTransactionDescription(String transactionDescription) { this.transactionDescription = transactionDescription; }
        
        public BigDecimal getTransactionAmount() { return transactionAmount; }
        public void setTransactionAmount(BigDecimal transactionAmount) { this.transactionAmount = transactionAmount; }
        
        public Long getMerchantId() { return merchantId; }
        public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
        
        public String getMerchantName() { return merchantName; }
        public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
        
        public String getMerchantCity() { return merchantCity; }
        public void setMerchantCity(String merchantCity) { this.merchantCity = merchantCity; }
        
        public String getMerchantZip() { return merchantZip; }
        public void setMerchantZip(String merchantZip) { this.merchantZip = merchantZip; }
        
        public String getCardNumber() { return cardNumber; }
        public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
        
        public LocalDateTime getOriginalTimestamp() { return originalTimestamp; }
        public void setOriginalTimestamp(LocalDateTime originalTimestamp) { this.originalTimestamp = originalTimestamp; }
        
        public LocalDateTime getProcessingTimestamp() { return processingTimestamp; }
        public void setProcessingTimestamp(LocalDateTime processingTimestamp) { this.processingTimestamp = processingTimestamp; }
    }

    /**
     * Transaction Create Request DTO
     * Maps to COTRN02.bms transaction add screen (CT02 transaction)
     * Comprehensive validation for new transaction entry
     */
    public static class TransactionCreateRequest {
        
        @JsonProperty("transactionTypeCode")
        @NotBlank(message = "Transaction type code is required")
        @Size(min = 2, max = 2, message = "Transaction type code must be exactly 2 characters")
        private String transactionTypeCode;
        
        @JsonProperty("transactionCategoryCode")
        @NotNull(message = "Transaction category code is required")
        @Min(value = 1, message = "Transaction category code must be positive")
        @Max(value = 9999, message = "Transaction category code cannot exceed 9999")
        private Integer transactionCategoryCode;
        
        @JsonProperty("transactionSource")
        @Size(max = 10, message = "Transaction source cannot exceed 10 characters")
        private String transactionSource = "ONLINE";
        
        @JsonProperty("transactionDescription")
        @NotBlank(message = "Transaction description is required")
        @Size(min = 1, max = 100, message = "Transaction description must be 1-100 characters")
        private String transactionDescription;
        
        @JsonProperty("transactionAmount")
        @NotNull(message = "Transaction amount is required")
        @DecimalMin(value = "0.01", message = "Transaction amount must be at least 0.01")
        @DecimalMax(value = "999999999.99", message = "Transaction amount cannot exceed 999999999.99")
        @Digits(integer = 9, fraction = 2, message = "Transaction amount must have at most 9 integer digits and 2 decimal places")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal transactionAmount;
        
        @JsonProperty("merchantId")
        @NotNull(message = "Merchant ID is required")
        @Min(value = 1, message = "Merchant ID must be positive")
        @Max(value = 999999999, message = "Merchant ID cannot exceed 999999999")
        private Long merchantId;
        
        @JsonProperty("merchantName")
        @NotBlank(message = "Merchant name is required")
        @Size(min = 1, max = 50, message = "Merchant name must be 1-50 characters")
        private String merchantName;
        
        @JsonProperty("merchantCity")
        @NotBlank(message = "Merchant city is required")
        @Size(min = 1, max = 50, message = "Merchant city must be 1-50 characters")
        private String merchantCity;
        
        @JsonProperty("merchantZip")
        @NotBlank(message = "Merchant ZIP is required")
        @Pattern(regexp = "^[0-9]{5}(-[0-9]{4})?$", message = "Merchant ZIP must be in format 12345 or 12345-6789")
        private String merchantZip;
        
        @JsonProperty("cardNumber")
        @NotBlank(message = "Card number is required")
        @Pattern(regexp = "^[0-9]{16}$", message = "Card number must be exactly 16 digits")
        private String cardNumber;

        // Constructors
        public TransactionCreateRequest() {}

        // Getters and Setters
        public String getTransactionTypeCode() { return transactionTypeCode; }
        public void setTransactionTypeCode(String transactionTypeCode) { this.transactionTypeCode = transactionTypeCode; }
        
        public Integer getTransactionCategoryCode() { return transactionCategoryCode; }
        public void setTransactionCategoryCode(Integer transactionCategoryCode) { this.transactionCategoryCode = transactionCategoryCode; }
        
        public String getTransactionSource() { return transactionSource; }
        public void setTransactionSource(String transactionSource) { this.transactionSource = transactionSource; }
        
        public String getTransactionDescription() { return transactionDescription; }
        public void setTransactionDescription(String transactionDescription) { this.transactionDescription = transactionDescription; }
        
        public BigDecimal getTransactionAmount() { return transactionAmount; }
        public void setTransactionAmount(BigDecimal transactionAmount) { this.transactionAmount = transactionAmount; }
        
        public Long getMerchantId() { return merchantId; }
        public void setMerchantId(Long merchantId) { this.merchantId = merchantId; }
        
        public String getMerchantName() { return merchantName; }
        public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
        
        public String getMerchantCity() { return merchantCity; }
        public void setMerchantCity(String merchantCity) { this.merchantCity = merchantCity; }
        
        public String getMerchantZip() { return merchantZip; }
        public void setMerchantZip(String merchantZip) { this.merchantZip = merchantZip; }
        
        public String getCardNumber() { return cardNumber; }
        public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
    }

    /**
     * Transaction Create Response DTO
     * Response for successful transaction creation
     */
    public static class TransactionCreateResponse {
        
        @JsonProperty("transactionId")
        private String transactionId;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("processingTimestamp")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSSSSS")
        private LocalDateTime processingTimestamp;

        // Constructors
        public TransactionCreateResponse() {}

        public TransactionCreateResponse(String transactionId, String message, LocalDateTime processingTimestamp) {
            this.transactionId = transactionId;
            this.message = message;
            this.processingTimestamp = processingTimestamp;
        }

        // Getters and Setters
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public LocalDateTime getProcessingTimestamp() { return processingTimestamp; }
        public void setProcessingTimestamp(LocalDateTime processingTimestamp) { this.processingTimestamp = processingTimestamp; }
    }

    /**
     * Transaction Category Balance DTO
     * Based on CVTRA01Y.cpy TRAN-CAT-BAL-RECORD structure (RECLN = 50)
     * For tracking transaction category balances by account
     */
    public static class TransactionCategoryBalance {
        
        @JsonProperty("accountId")
        @Min(value = 0, message = "Account ID must be non-negative")
        @Max(value = 99999999999L, message = "Account ID cannot exceed 99999999999")
        private Long accountId; // TRANCAT-ACCT-ID PIC 9(11)
        
        @JsonProperty("transactionTypeCode")
        @Size(min = 2, max = 2, message = "Transaction type code must be exactly 2 characters")
        private String transactionTypeCode; // TRANCAT-TYPE-CD PIC X(02)
        
        @JsonProperty("categoryCode")
        @Min(value = 0, message = "Category code must be non-negative")
        @Max(value = 9999, message = "Category code cannot exceed 9999")
        private Integer categoryCode; // TRANCAT-CD PIC 9(04)
        
        @JsonProperty("categoryBalance")
        @DecimalMin(value = "-999999999.99", message = "Category balance cannot be less than -999999999.99")
        @DecimalMax(value = "999999999.99", message = "Category balance cannot exceed 999999999.99")
        @Digits(integer = 9, fraction = 2, message = "Category balance must have at most 9 integer digits and 2 decimal places")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal categoryBalance; // TRAN-CAT-BAL PIC S9(09)V99

        // Constructors
        public TransactionCategoryBalance() {}

        // Getters and Setters
        public Long getAccountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; }
        
        public String getTransactionTypeCode() { return transactionTypeCode; }
        public void setTransactionTypeCode(String transactionTypeCode) { this.transactionTypeCode = transactionTypeCode; }
        
        public Integer getCategoryCode() { return categoryCode; }
        public void setCategoryCode(Integer categoryCode) { this.categoryCode = categoryCode; }
        
        public BigDecimal getCategoryBalance() { return categoryBalance; }
        public void setCategoryBalance(BigDecimal categoryBalance) { this.categoryBalance = categoryBalance; }
    }

    /**
     * Disclosure Group DTO
     * Based on CVTRA02Y.cpy DIS-GROUP-RECORD structure (RECLN = 50)
     * For disclosure groups that define interest rates for transaction types and categories
     */
    public static class DisclosureGroup {
        
        @JsonProperty("accountGroupId")
        @Size(max = 10, message = "Account group ID cannot exceed 10 characters")
        private String accountGroupId; // DIS-ACCT-GROUP-ID PIC X(10)
        
        @JsonProperty("transactionTypeCode")
        @Size(min = 2, max = 2, message = "Transaction type code must be exactly 2 characters")
        private String transactionTypeCode; // DIS-TRAN-TYPE-CD PIC X(02)
        
        @JsonProperty("transactionCategoryCode")
        @Min(value = 0, message = "Transaction category code must be non-negative")
        @Max(value = 9999, message = "Transaction category code cannot exceed 9999")
        private Integer transactionCategoryCode; // DIS-TRAN-CAT-CD PIC 9(04)
        
        @JsonProperty("interestRate")
        @DecimalMin(value = "-9999.99", message = "Interest rate cannot be less than -9999.99")
        @DecimalMax(value = "9999.99", message = "Interest rate cannot exceed 9999.99")
        @Digits(integer = 4, fraction = 2, message = "Interest rate must have at most 4 integer digits and 2 decimal places")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private BigDecimal interestRate; // DIS-INT-RATE PIC S9(04)V99

        // Constructors
        public DisclosureGroup() {}

        // Getters and Setters
        public String getAccountGroupId() { return accountGroupId; }
        public void setAccountGroupId(String accountGroupId) { this.accountGroupId = accountGroupId; }
        
        public String getTransactionTypeCode() { return transactionTypeCode; }
        public void setTransactionTypeCode(String transactionTypeCode) { this.transactionTypeCode = transactionTypeCode; }
        
        public Integer getTransactionCategoryCode() { return transactionCategoryCode; }
        public void setTransactionCategoryCode(Integer transactionCategoryCode) { this.transactionCategoryCode = transactionCategoryCode; }
        
        public BigDecimal getInterestRate() { return interestRate; }
        public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    }

    /**
     * Transaction Error Response DTO
     * Standardized error response for transaction operations
     * Maps COBOL error codes to HTTP status codes per technical specifications
     */
    public static class TransactionErrorResponse {
        
        @JsonProperty("errorCode")
        private String errorCode;
        
        @JsonProperty("errorMessage")
        private String errorMessage;
        
        @JsonProperty("fieldErrors")
        private List<FieldError> fieldErrors;
        
        @JsonProperty("timestamp")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime timestamp;

        // Constructors
        public TransactionErrorResponse() {
            this.timestamp = LocalDateTime.now();
        }

        public TransactionErrorResponse(String errorCode, String errorMessage) {
            this();
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        // Getters and Setters
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        
        public List<FieldError> getFieldErrors() { return fieldErrors; }
        public void setFieldErrors(List<FieldError> fieldErrors) { this.fieldErrors = fieldErrors; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    /**
     * Field Error DTO for validation error details
     */
    public static class FieldError {
        
        @JsonProperty("field")
        private String field;
        
        @JsonProperty("rejectedValue")
        private Object rejectedValue;
        
        @JsonProperty("message")
        private String message;

        // Constructors
        public FieldError() {}

        public FieldError(String field, Object rejectedValue, String message) {
            this.field = field;
            this.rejectedValue = rejectedValue;
            this.message = message;
        }

        // Getters and Setters
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        
        public Object getRejectedValue() { return rejectedValue; }
        public void setRejectedValue(Object rejectedValue) { this.rejectedValue = rejectedValue; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
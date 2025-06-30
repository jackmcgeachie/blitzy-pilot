package com.carddemo.card;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CardDTO - Comprehensive Data Transfer Object classes for card operations
 * 
 * Migrated from COBOL copybooks:
 * - CVACT02Y.cpy (CARD-RECORD structure)
 * - CVCRD01Y.cpy (card work areas)
 * 
 * Provides JSON serialization support for REST API communication
 * while maintaining exact field validation rules from original COBOL programs.
 * 
 * This class contains separate DTO classes for different card operations:
 * - CardListDTO: For card listing with pagination support
 * - CardDetailDTO: For detailed card information retrieval
 * - CardUpdateDTO: For card modification operations
 * - CardSummaryDTO: For simplified card display in lists
 * - CardRequestDTO: For card creation/update requests
 * - CardResponseDTO: For standardized API responses
 */
public class CardDTO {

    /**
     * CardListDTO - Supporting paginated card list operations
     * Replaces COBOL program COCRDLIC.cbl functionality
     */
    @JsonPropertyOrder({
        "apiVersion", "requestId", "timestamp", "data", "meta"
    })
    public static class CardListDTO {
        
        @JsonProperty("apiVersion")
        @NotBlank(message = "API version is required")
        @Pattern(regexp = "^v\\d+$", message = "API version must be in format v1, v2, etc.")
        private String apiVersion = "v1";
        
        @JsonProperty("requestId")
        @NotBlank(message = "Request ID is required")
        @Size(max = 36, message = "Request ID must not exceed 36 characters")
        private String requestId;
        
        @JsonProperty("timestamp")
        @NotNull(message = "Timestamp is required")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private LocalDateTime timestamp;
        
        @JsonProperty("data")
        @NotNull(message = "Data section is required")
        private CardListDataDTO data;
        
        @JsonProperty("meta")
        @NotNull(message = "Meta section is required")
        private PaginationMetaDTO meta;
        
        // Constructors, getters, and setters
        public CardListDTO() {
            this.timestamp = LocalDateTime.now();
        }
        
        public CardListDTO(String requestId, CardListDataDTO data, PaginationMetaDTO meta) {
            this();
            this.requestId = requestId;
            this.data = data;
            this.meta = meta;
        }
        
        public String getApiVersion() { return apiVersion; }
        public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
        
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public CardListDataDTO getData() { return data; }
        public void setData(CardListDataDTO data) { this.data = data; }
        
        public PaginationMetaDTO getMeta() { return meta; }
        public void setMeta(PaginationMetaDTO meta) { this.meta = meta; }
    }
    
    /**
     * CardListDataDTO - Data container for card list
     */
    public static class CardListDataDTO {
        
        @JsonProperty("cards")
        @NotNull(message = "Cards list is required")
        @Valid
        private List<CardSummaryDTO> cards;
        
        public CardListDataDTO() {}
        
        public CardListDataDTO(List<CardSummaryDTO> cards) {
            this.cards = cards;
        }
        
        public List<CardSummaryDTO> getCards() { return cards; }
        public void setCards(List<CardSummaryDTO> cards) { this.cards = cards; }
    }
    
    /**
     * CardSummaryDTO - Individual card summary for list display
     * Maps to COBOL CARD-RECORD structure from CVACT02Y.cpy
     */
    @JsonPropertyOrder({
        "CARD-NUM", "CARD-CVV-CD", "CARD-EMBOSSED-NAME", 
        "CARD-EXPIR-DATE", "CARD-ACTIVE-STATUS", "CARD-ACCT-ID", "CARD-CUST-ID"
    })
    public static class CardSummaryDTO {
        
        /**
         * Card Number - 16 digit card number
         * Original COBOL: CARD-NUM PIC X(16)
         */
        @JsonProperty("CARD-NUM")
        @NotBlank(message = "Card number is required")
        @Pattern(regexp = "^\\d{16}$", message = "Card number must be exactly 16 digits")
        @Size(min = 16, max = 16, message = "Card number must be exactly 16 characters")
        private String cardNumber;
        
        /**
         * Card CVV Code - 3 digit security code
         * Original COBOL: CARD-CVV-CD PIC 9(03)
         */
        @JsonProperty("CARD-CVV-CD")
        @NotBlank(message = "CVV code is required")
        @Pattern(regexp = "^\\d{3}$", message = "CVV code must be exactly 3 digits")
        @Size(min = 3, max = 3, message = "CVV code must be exactly 3 characters")
        private String cvvCode;
        
        /**
         * Embossed Name on Card - up to 50 characters
         * Original COBOL: CARD-EMBOSSED-NAME PIC X(50)
         */
        @JsonProperty("CARD-EMBOSSED-NAME")
        @NotBlank(message = "Embossed name is required")
        @Size(max = 50, message = "Embossed name must not exceed 50 characters")
        @Pattern(regexp = "^[A-Z\\s]*$", message = "Embossed name must contain only uppercase letters and spaces")
        private String embossedName;
        
        /**
         * Card Expiration Date - MMYY format
         * Original COBOL: CARD-EXPIRAION-DATE PIC X(10) (stored as YYYY-MM-DD, displayed as MMYY)
         */
        @JsonProperty("CARD-EXPIR-DATE")
        @NotBlank(message = "Expiration date is required")
        @Pattern(regexp = "^(0[1-9]|1[0-2])\\d{2}$", message = "Expiration date must be in MMYY format")
        @Size(min = 4, max = 4, message = "Expiration date must be exactly 4 characters")
        private String expirationDate;
        
        /**
         * Card Active Status - Y/N flag
         * Original COBOL: CARD-ACTIVE-STATUS PIC X(01)
         */
        @JsonProperty("CARD-ACTIVE-STATUS")
        @NotBlank(message = "Active status is required")
        @Pattern(regexp = "^[YN]$", message = "Active status must be Y or N")
        @Size(min = 1, max = 1, message = "Active status must be exactly 1 character")
        private String activeStatus;
        
        /**
         * Associated Account ID - 11 digit account number
         * Original COBOL: CARD-ACCT-ID PIC 9(11)
         */
        @JsonProperty("CARD-ACCT-ID")
        @NotBlank(message = "Account ID is required")
        @Pattern(regexp = "^\\d{11}$", message = "Account ID must be exactly 11 digits")
        @Size(min = 11, max = 11, message = "Account ID must be exactly 11 characters")
        private String accountId;
        
        /**
         * Customer ID - 9 digit customer number
         * Derived from account relationship
         */
        @JsonProperty("CARD-CUST-ID")
        @Pattern(regexp = "^\\d{9}$", message = "Customer ID must be exactly 9 digits")
        @Size(min = 9, max = 9, message = "Customer ID must be exactly 9 characters")
        private String customerId;
        
        // Constructors
        public CardSummaryDTO() {}
        
        public CardSummaryDTO(String cardNumber, String cvvCode, String embossedName, 
                            String expirationDate, String activeStatus, String accountId, String customerId) {
            this.cardNumber = cardNumber;
            this.cvvCode = cvvCode;
            this.embossedName = embossedName;
            this.expirationDate = expirationDate;
            this.activeStatus = activeStatus;
            this.accountId = accountId;
            this.customerId = customerId;
        }
        
        // Getters and setters
        public String getCardNumber() { return cardNumber; }
        public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
        
        public String getCvvCode() { return cvvCode; }
        public void setCvvCode(String cvvCode) { this.cvvCode = cvvCode; }
        
        public String getEmbossedName() { return embossedName; }
        public void setEmbossedName(String embossedName) { this.embossedName = embossedName; }
        
        public String getExpirationDate() { return expirationDate; }
        public void setExpirationDate(String expirationDate) { this.expirationDate = expirationDate; }
        
        public String getActiveStatus() { return activeStatus; }
        public void setActiveStatus(String activeStatus) { this.activeStatus = activeStatus; }
        
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
    }
    
    /**
     * CardDetailDTO - Comprehensive card information for detailed view
     * Replaces COBOL program COCRDSLC.cbl functionality
     */
    @JsonPropertyOrder({
        "apiVersion", "requestId", "timestamp", "data", "meta"
    })
    public static class CardDetailDTO {
        
        @JsonProperty("apiVersion")
        @NotBlank(message = "API version is required")
        @Pattern(regexp = "^v\\d+$", message = "API version must be in format v1, v2, etc.")
        private String apiVersion = "v1";
        
        @JsonProperty("requestId")
        @NotBlank(message = "Request ID is required")
        @Size(max = 36, message = "Request ID must not exceed 36 characters")
        private String requestId;
        
        @JsonProperty("timestamp")
        @NotNull(message = "Timestamp is required")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private LocalDateTime timestamp;
        
        @JsonProperty("data")
        @NotNull(message = "Data section is required")
        private CardDetailDataDTO data;
        
        @JsonProperty("meta")
        private ValidationMetaDTO meta;
        
        // Constructors
        public CardDetailDTO() {
            this.timestamp = LocalDateTime.now();
        }
        
        public CardDetailDTO(String requestId, CardDetailDataDTO data) {
            this();
            this.requestId = requestId;
            this.data = data;
        }
        
        // Getters and setters
        public String getApiVersion() { return apiVersion; }
        public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
        
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public CardDetailDataDTO getData() { return data; }
        public void setData(CardDetailDataDTO data) { this.data = data; }
        
        public ValidationMetaDTO getMeta() { return meta; }
        public void setMeta(ValidationMetaDTO meta) { this.meta = meta; }
    }
    
    /**
     * CardDetailDataDTO - Detailed card information container
     */
    public static class CardDetailDataDTO {
        
        @JsonProperty("cardData")
        @NotNull(message = "Card data is required")
        @Valid
        private CardInfoDTO cardData;
        
        @JsonProperty("accountData")
        @Valid
        private AccountInfoDTO accountData;
        
        @JsonProperty("customerData")
        @Valid
        private CustomerInfoDTO customerData;
        
        public CardDetailDataDTO() {}
        
        public CardDetailDataDTO(CardInfoDTO cardData, AccountInfoDTO accountData, CustomerInfoDTO customerData) {
            this.cardData = cardData;
            this.accountData = accountData;
            this.customerData = customerData;
        }
        
        public CardInfoDTO getCardData() { return cardData; }
        public void setCardData(CardInfoDTO cardData) { this.cardData = cardData; }
        
        public AccountInfoDTO getAccountData() { return accountData; }
        public void setAccountData(AccountInfoDTO accountData) { this.accountData = accountData; }
        
        public CustomerInfoDTO getCustomerData() { return customerData; }
        public void setCustomerData(CustomerInfoDTO customerData) { this.customerData = customerData; }
    }
    
    /**
     * CardInfoDTO - Extended card information for detailed operations
     */
    @JsonPropertyOrder({
        "CARD-NUM", "CARD-CVV-CD", "CARD-EMBOSSED-NAME", "CARD-EXPIR-DATE", 
        "CARD-ACTIVE-STATUS", "CARD-ACCT-ID", "CARD-TYPE", "CARD-ISSUE-DATE",
        "CARD-ACTIVATION-DATE", "CARD-LAST-USED-DATE", "CARD-DAILY-LIMIT",
        "CARD-MONTHLY-LIMIT", "CARD-REISSUE-REASON"
    })
    public static class CardInfoDTO {
        
        // Core card fields (same as CardSummaryDTO)
        @JsonProperty("CARD-NUM")
        @NotBlank(message = "Card number is required")
        @Pattern(regexp = "^\\d{16}$", message = "Card number must be exactly 16 digits")
        private String cardNumber;
        
        @JsonProperty("CARD-CVV-CD")
        @NotBlank(message = "CVV code is required")
        @Pattern(regexp = "^\\d{3}$", message = "CVV code must be exactly 3 digits")
        private String cvvCode;
        
        @JsonProperty("CARD-EMBOSSED-NAME")
        @NotBlank(message = "Embossed name is required")
        @Size(max = 50, message = "Embossed name must not exceed 50 characters")
        @Pattern(regexp = "^[A-Z\\s]*$", message = "Embossed name must contain only uppercase letters and spaces")
        private String embossedName;
        
        @JsonProperty("CARD-EXPIR-DATE")
        @NotBlank(message = "Expiration date is required")
        @Pattern(regexp = "^(0[1-9]|1[0-2])\\d{2}$", message = "Expiration date must be in MMYY format")
        private String expirationDate;
        
        @JsonProperty("CARD-ACTIVE-STATUS")
        @NotBlank(message = "Active status is required")
        @Pattern(regexp = "^[YN]$", message = "Active status must be Y or N")
        private String activeStatus;
        
        @JsonProperty("CARD-ACCT-ID")
        @NotBlank(message = "Account ID is required")
        @Pattern(regexp = "^\\d{11}$", message = "Account ID must be exactly 11 digits")
        private String accountId;
        
        // Extended card fields
        @JsonProperty("CARD-TYPE")
        @Pattern(regexp = "^(VISA|MASTERCARD|AMEX|DISCOVER)$", message = "Card type must be VISA, MASTERCARD, AMEX, or DISCOVER")
        private String cardType;
        
        @JsonProperty("CARD-ISSUE-DATE")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate issueDate;
        
        @JsonProperty("CARD-ACTIVATION-DATE")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate activationDate;
        
        @JsonProperty("CARD-LAST-USED-DATE")
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate lastUsedDate;
        
        @JsonProperty("CARD-DAILY-LIMIT")
        @DecimalMin(value = "0.00", message = "Daily limit must be positive")
        @DecimalMax(value = "999999.99", message = "Daily limit must not exceed 999999.99")
        @Digits(integer = 6, fraction = 2, message = "Daily limit must have at most 6 integer digits and 2 decimal places")
        private String dailyLimit;
        
        @JsonProperty("CARD-MONTHLY-LIMIT")
        @DecimalMin(value = "0.00", message = "Monthly limit must be positive")
        @DecimalMax(value = "999999.99", message = "Monthly limit must not exceed 999999.99")
        @Digits(integer = 6, fraction = 2, message = "Monthly limit must have at most 6 integer digits and 2 decimal places")
        private String monthlyLimit;
        
        @JsonProperty("CARD-REISSUE-REASON")
        @Size(max = 40, message = "Reissue reason must not exceed 40 characters")
        private String reissueReason;
        
        // Constructors
        public CardInfoDTO() {}
        
        // Getters and setters
        public String getCardNumber() { return cardNumber; }
        public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
        
        public String getCvvCode() { return cvvCode; }
        public void setCvvCode(String cvvCode) { this.cvvCode = cvvCode; }
        
        public String getEmbossedName() { return embossedName; }
        public void setEmbossedName(String embossedName) { this.embossedName = embossedName; }
        
        public String getExpirationDate() { return expirationDate; }
        public void setExpirationDate(String expirationDate) { this.expirationDate = expirationDate; }
        
        public String getActiveStatus() { return activeStatus; }
        public void setActiveStatus(String activeStatus) { this.activeStatus = activeStatus; }
        
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        
        public String getCardType() { return cardType; }
        public void setCardType(String cardType) { this.cardType = cardType; }
        
        public LocalDate getIssueDate() { return issueDate; }
        public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
        
        public LocalDate getActivationDate() { return activationDate; }
        public void setActivationDate(LocalDate activationDate) { this.activationDate = activationDate; }
        
        public LocalDate getLastUsedDate() { return lastUsedDate; }
        public void setLastUsedDate(LocalDate lastUsedDate) { this.lastUsedDate = lastUsedDate; }
        
        public String getDailyLimit() { return dailyLimit; }
        public void setDailyLimit(String dailyLimit) { this.dailyLimit = dailyLimit; }
        
        public String getMonthlyLimit() { return monthlyLimit; }
        public void setMonthlyLimit(String monthlyLimit) { this.monthlyLimit = monthlyLimit; }
        
        public String getReissueReason() { return reissueReason; }
        public void setReissueReason(String reissueReason) { this.reissueReason = reissueReason; }
    }
    
    /**
     * CardUpdateDTO - Card modification request/response
     * Replaces COBOL program COCRDUPC.cbl functionality
     */
    @JsonPropertyOrder({
        "apiVersion", "requestId", "timestamp", "data", "meta"
    })
    public static class CardUpdateDTO {
        
        @JsonProperty("apiVersion")
        @NotBlank(message = "API version is required")
        @Pattern(regexp = "^v\\d+$", message = "API version must be in format v1, v2, etc.")
        private String apiVersion = "v1";
        
        @JsonProperty("requestId")
        @NotBlank(message = "Request ID is required")
        @Size(max = 36, message = "Request ID must not exceed 36 characters")
        private String requestId;
        
        @JsonProperty("timestamp")
        @NotNull(message = "Timestamp is required")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
        private LocalDateTime timestamp;
        
        @JsonProperty("data")
        @NotNull(message = "Data section is required")
        private CardUpdateDataDTO data;
        
        @JsonProperty("meta")
        private ValidationMetaDTO meta;
        
        // Constructors
        public CardUpdateDTO() {
            this.timestamp = LocalDateTime.now();
        }
        
        public CardUpdateDTO(String requestId, CardUpdateDataDTO data) {
            this();
            this.requestId = requestId;
            this.data = data;
        }
        
        // Getters and setters
        public String getApiVersion() { return apiVersion; }
        public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
        
        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public CardUpdateDataDTO getData() { return data; }
        public void setData(CardUpdateDataDTO data) { this.data = data; }
        
        public ValidationMetaDTO getMeta() { return meta; }
        public void setMeta(ValidationMetaDTO meta) { this.meta = meta; }
    }
    
    /**
     * CardUpdateDataDTO - Card update request data container
     */
    public static class CardUpdateDataDTO {
        
        @JsonProperty("cardNumber")
        @NotBlank(message = "Card number is required for update")
        @Pattern(regexp = "^\\d{16}$", message = "Card number must be exactly 16 digits")
        private String cardNumber;
        
        @JsonProperty("updateFields")
        @NotNull(message = "Update fields are required")
        @Valid
        private CardUpdateFieldsDTO updateFields;
        
        @JsonProperty("operationType")
        @NotBlank(message = "Operation type is required")
        @Pattern(regexp = "^(UPDATE|ACTIVATE|DEACTIVATE|REISSUE|REPLACE)$", 
                 message = "Operation type must be UPDATE, ACTIVATE, DEACTIVATE, REISSUE, or REPLACE")
        private String operationType;
        
        @JsonProperty("reason")
        @Size(max = 100, message = "Reason must not exceed 100 characters")
        private String reason;
        
        public CardUpdateDataDTO() {}
        
        public CardUpdateDataDTO(String cardNumber, CardUpdateFieldsDTO updateFields, String operationType, String reason) {
            this.cardNumber = cardNumber;
            this.updateFields = updateFields;
            this.operationType = operationType;
            this.reason = reason;
        }
        
        public String getCardNumber() { return cardNumber; }
        public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
        
        public CardUpdateFieldsDTO getUpdateFields() { return updateFields; }
        public void setUpdateFields(CardUpdateFieldsDTO updateFields) { this.updateFields = updateFields; }
        
        public String getOperationType() { return operationType; }
        public void setOperationType(String operationType) { this.operationType = operationType; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
    
    /**
     * CardUpdateFieldsDTO - Specific fields that can be updated
     */
    public static class CardUpdateFieldsDTO {
        
        @JsonProperty("embossedName")
        @Size(max = 50, message = "Embossed name must not exceed 50 characters")
        @Pattern(regexp = "^[A-Z\\s]*$", message = "Embossed name must contain only uppercase letters and spaces")
        private String embossedName;
        
        @JsonProperty("activeStatus")
        @Pattern(regexp = "^[YN]$", message = "Active status must be Y or N")
        private String activeStatus;
        
        @JsonProperty("expirationDate")
        @Pattern(regexp = "^(0[1-9]|1[0-2])\\d{2}$", message = "Expiration date must be in MMYY format")
        private String expirationDate;
        
        @JsonProperty("dailyLimit")
        @DecimalMin(value = "0.00", message = "Daily limit must be positive")
        @DecimalMax(value = "999999.99", message = "Daily limit must not exceed 999999.99")
        @Digits(integer = 6, fraction = 2, message = "Daily limit must have at most 6 integer digits and 2 decimal places")
        private String dailyLimit;
        
        @JsonProperty("monthlyLimit")
        @DecimalMin(value = "0.00", message = "Monthly limit must be positive")
        @DecimalMax(value = "999999.99", message = "Monthly limit must not exceed 999999.99")
        @Digits(integer = 6, fraction = 2, message = "Monthly limit must have at most 6 integer digits and 2 decimal places")
        private String monthlyLimit;
        
        public CardUpdateFieldsDTO() {}
        
        public String getEmbossedName() { return embossedName; }
        public void setEmbossedName(String embossedName) { this.embossedName = embossedName; }
        
        public String getActiveStatus() { return activeStatus; }
        public void setActiveStatus(String activeStatus) { this.activeStatus = activeStatus; }
        
        public String getExpirationDate() { return expirationDate; }
        public void setExpirationDate(String expirationDate) { this.expirationDate = expirationDate; }
        
        public String getDailyLimit() { return dailyLimit; }
        public void setDailyLimit(String dailyLimit) { this.dailyLimit = dailyLimit; }
        
        public String getMonthlyLimit() { return monthlyLimit; }
        public void setMonthlyLimit(String monthlyLimit) { this.monthlyLimit = monthlyLimit; }
    }
    
    /**
     * Supporting DTOs for metadata and nested structures
     */
    
    /**
     * PaginationMetaDTO - Pagination metadata for card lists
     */
    public static class PaginationMetaDTO {
        
        @JsonProperty("pagination")
        @NotNull(message = "Pagination info is required")
        private PaginationInfoDTO pagination;
        
        public PaginationMetaDTO() {}
        
        public PaginationMetaDTO(PaginationInfoDTO pagination) {
            this.pagination = pagination;
        }
        
        public PaginationInfoDTO getPagination() { return pagination; }
        public void setPagination(PaginationInfoDTO pagination) { this.pagination = pagination; }
    }
    
    /**
     * PaginationInfoDTO - Pagination details
     */
    public static class PaginationInfoDTO {
        
        @JsonProperty("page")
        @Min(value = 1, message = "Page must be at least 1")
        private int page;
        
        @JsonProperty("size")
        @Min(value = 1, message = "Page size must be at least 1")
        @Max(value = 100, message = "Page size must not exceed 100")
        private int size;
        
        @JsonProperty("total")
        @Min(value = 0, message = "Total must be non-negative")
        private long total;
        
        public PaginationInfoDTO() {}
        
        public PaginationInfoDTO(int page, int size, long total) {
            this.page = page;
            this.size = size;
            this.total = total;
        }
        
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        
        public long getTotal() { return total; }
        public void setTotal(long total) { this.total = total; }
    }
    
    /**
     * ValidationMetaDTO - Validation metadata
     */
    public static class ValidationMetaDTO {
        
        @JsonProperty("validation")
        private ValidationInfoDTO validation;
        
        public ValidationMetaDTO() {}
        
        public ValidationMetaDTO(ValidationInfoDTO validation) {
            this.validation = validation;
        }
        
        public ValidationInfoDTO getValidation() { return validation; }
        public void setValidation(ValidationInfoDTO validation) { this.validation = validation; }
    }
    
    /**
     * ValidationInfoDTO - Validation error details
     */
    public static class ValidationInfoDTO {
        
        @JsonProperty("fieldErrors")
        private List<FieldErrorDTO> fieldErrors;
        
        @JsonProperty("businessRuleViolations")
        private List<BusinessRuleViolationDTO> businessRuleViolations;
        
        public ValidationInfoDTO() {}
        
        public List<FieldErrorDTO> getFieldErrors() { return fieldErrors; }
        public void setFieldErrors(List<FieldErrorDTO> fieldErrors) { this.fieldErrors = fieldErrors; }
        
        public List<BusinessRuleViolationDTO> getBusinessRuleViolations() { return businessRuleViolations; }
        public void setBusinessRuleViolations(List<BusinessRuleViolationDTO> businessRuleViolations) { 
            this.businessRuleViolations = businessRuleViolations; 
        }
    }
    
    /**
     * FieldErrorDTO - Individual field validation error
     */
    public static class FieldErrorDTO {
        
        @JsonProperty("field")
        private String field;
        
        @JsonProperty("code")
        private String code;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("rejectedValue")
        private Object rejectedValue;
        
        public FieldErrorDTO() {}
        
        public FieldErrorDTO(String field, String code, String message, Object rejectedValue) {
            this.field = field;
            this.code = code;
            this.message = message;
            this.rejectedValue = rejectedValue;
        }
        
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public Object getRejectedValue() { return rejectedValue; }
        public void setRejectedValue(Object rejectedValue) { this.rejectedValue = rejectedValue; }
    }
    
    /**
     * BusinessRuleViolationDTO - Business rule validation error
     */
    public static class BusinessRuleViolationDTO {
        
        @JsonProperty("rule")
        private String rule;
        
        @JsonProperty("code")
        private String code;
        
        @JsonProperty("message")
        private String message;
        
        @JsonProperty("severity")
        @Pattern(regexp = "^(ERROR|WARNING|INFO)$", message = "Severity must be ERROR, WARNING, or INFO")
        private String severity;
        
        public BusinessRuleViolationDTO() {}
        
        public BusinessRuleViolationDTO(String rule, String code, String message, String severity) {
            this.rule = rule;
            this.code = code;
            this.message = message;
            this.severity = severity;
        }
        
        public String getRule() { return rule; }
        public void setRule(String rule) { this.rule = rule; }
        
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
    }
    
    /**
     * AccountInfoDTO - Account information for card detail view
     */
    public static class AccountInfoDTO {
        
        @JsonProperty("ACCT-ID")
        @Pattern(regexp = "^\\d{11}$", message = "Account ID must be exactly 11 digits")
        private String accountId;
        
        @JsonProperty("ACCT-STATUS")
        @Pattern(regexp = "^[AIS]$", message = "Account status must be A (Active), I (Inactive), or S (Suspended)")
        private String accountStatus;
        
        @JsonProperty("ACCT-CURR-BAL")
        @DecimalMin(value = "-999999.99", message = "Account balance must be at least -999999.99")
        @DecimalMax(value = "999999.99", message = "Account balance must not exceed 999999.99")
        @Digits(integer = 6, fraction = 2, message = "Account balance must have at most 6 integer digits and 2 decimal places")
        private String currentBalance;
        
        @JsonProperty("ACCT-CREDIT-LIMIT")
        @DecimalMin(value = "0.00", message = "Credit limit must be positive")
        @DecimalMax(value = "999999.99", message = "Credit limit must not exceed 999999.99")
        @Digits(integer = 6, fraction = 2, message = "Credit limit must have at most 6 integer digits and 2 decimal places")
        private String creditLimit;
        
        public AccountInfoDTO() {}
        
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        
        public String getAccountStatus() { return accountStatus; }
        public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }
        
        public String getCurrentBalance() { return currentBalance; }
        public void setCurrentBalance(String currentBalance) { this.currentBalance = currentBalance; }
        
        public String getCreditLimit() { return creditLimit; }
        public void setCreditLimit(String creditLimit) { this.creditLimit = creditLimit; }
    }
    
    /**
     * CustomerInfoDTO - Customer information for card detail view
     */
    public static class CustomerInfoDTO {
        
        @JsonProperty("CUST-ID")
        @Pattern(regexp = "^\\d{9}$", message = "Customer ID must be exactly 9 digits")
        private String customerId;
        
        @JsonProperty("CUST-FIRST-NAME")
        @Size(max = 20, message = "First name must not exceed 20 characters")
        @Pattern(regexp = "^[A-Za-z\\s]*$", message = "First name must contain only letters and spaces")
        private String firstName;
        
        @JsonProperty("CUST-LAST-NAME")
        @Size(max = 20, message = "Last name must not exceed 20 characters")
        @Pattern(regexp = "^[A-Za-z\\s]*$", message = "Last name must contain only letters and spaces")
        private String lastName;
        
        @JsonProperty("CUST-PHONE-NUM-1")
        @Pattern(regexp = "^\\d{10}$", message = "Phone number must be exactly 10 digits")
        private String phoneNumber;
        
        @JsonProperty("CUST-SSN")
        @Pattern(regexp = "^\\d{9}$", message = "SSN must be exactly 9 digits")
        private String ssn;
        
        @JsonProperty("CUST-DOB-YYYY-MM-DD")
        @JsonFormat(pattern = "yyyy-MM-dd")
        @Past(message = "Date of birth must be in the past")
        private LocalDate dateOfBirth;
        
        public CustomerInfoDTO() {}
        
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
        
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        
        public String getSsn() { return ssn; }
        public void setSsn(String ssn) { this.ssn = ssn; }
        
        public LocalDate getDateOfBirth() { return dateOfBirth; }
        public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    }
}
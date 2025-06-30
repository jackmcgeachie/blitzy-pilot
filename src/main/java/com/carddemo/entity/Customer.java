package com.carddemo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * JPA entity class representing customer demographics and personal information 
 * transformed from COBOL CUSTOMER-RECORD structure (CUSTREC.cpy and CVCUS01Y.cpy).
 * 
 * This entity implements the PostgreSQL customers table with composite indexes
 * for high-performance customer lookup operations, featuring comprehensive field
 * validation matching COBOL PIC clause specifications and optimistic locking for 
 * concurrent access control.
 * 
 * Key Features:
 * - Converts COBOL PIC 9(09) CUST-ID to String primary key
 * - Transforms PIC X(25) name fields to String with max length validation
 * - Converts PIC X(50) address fields to String with appropriate column definitions
 * - Maintains COBOL date format compatibility for DOB (YYYY-MM-DD)
 * - Implements optimistic locking via @Version annotation
 * - Provides bidirectional JPA relationships with Account entities
 * 
 * Database Design:
 * - Primary key: customer_id (9-digit string)
 * - Composite indexes: (customer_id, ssn, phone) for multi-access patterns
 * - Unique constraints: SSN for customer identification validation
 * - Optimistic locking: row_version column
 * 
 * Performance Optimizations:
 * - Hibernate second-level cache enabled
 * - Composite index on frequently queried columns
 * - Field validation matching COBOL PIC clause specifications
 * 
 * @author Blitzy Agent - CardDemo Migration Team
 * @version 1.0.0
 * @since Java 21 LTS
 */
@Entity
@Table(name = "customers", 
       indexes = {
           @Index(name = "idx_customer_primary", 
                  columnList = "customer_id"),
           @Index(name = "idx_customer_ssn_phone", 
                  columnList = "customer_ssn, customer_phone_num_1"),
           @Index(name = "idx_customer_name", 
                  columnList = "customer_last_name, customer_first_name"),
           @Index(name = "idx_customer_state_zip", 
                  columnList = "customer_addr_state_cd, customer_addr_zip")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_customer_ssn", columnNames = "customer_ssn")
       })
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "customerCache")
public class Customer {

    /**
     * Customer identifier - converted from COBOL PIC 9(09) CUST-ID.
     * Maintains 9-digit numeric format as String for consistency with legacy systems.
     * This serves as the primary key for customer lookups and cross-references.
     */
    @Id
    @Column(name = "customer_id", length = 9, nullable = false)
    @Pattern(regexp = "^\\d{9}$", message = "Customer ID must be exactly 9 digits")
    @NotNull(message = "Customer ID cannot be null")
    private String customerId;

    /**
     * Customer first name - converted from COBOL PIC X(25) CUST-FIRST-NAME.
     * Maintains 25-character limit matching original COBOL field specification.
     * Used for customer identification and address management.
     */
    @Column(name = "customer_first_name", length = 25, nullable = false)
    @Size(max = 25, message = "First name cannot exceed 25 characters")
    @NotBlank(message = "First name cannot be blank")
    private String customerFirstName;

    /**
     * Customer middle name - converted from COBOL PIC X(25) CUST-MIDDLE-NAME.
     * Optional field maintaining 25-character format for full name representation.
     * Used for complete customer name display and documentation.
     */
    @Column(name = "customer_middle_name", length = 25)
    @Size(max = 25, message = "Middle name cannot exceed 25 characters")
    private String customerMiddleName;

    /**
     * Customer last name - converted from COBOL PIC X(25) CUST-LAST-NAME.
     * Maintains 25-character limit with required validation for customer identification.
     * Primary field for customer surname management and sorting.
     */
    @Column(name = "customer_last_name", length = 25, nullable = false)
    @Size(max = 25, message = "Last name cannot exceed 25 characters")
    @NotBlank(message = "Last name cannot be blank")
    private String customerLastName;

    /**
     * Customer address line 1 - converted from COBOL PIC X(50) CUST-ADDR-LINE-1.
     * Primary address field maintaining 50-character format for street information.
     * Required field for customer mailing and verification purposes.
     */
    @Column(name = "customer_addr_line_1", length = 50, nullable = false)
    @Size(max = 50, message = "Address line 1 cannot exceed 50 characters")
    @NotBlank(message = "Address line 1 cannot be blank")
    private String customerAddrLine1;

    /**
     * Customer address line 2 - converted from COBOL PIC X(50) CUST-ADDR-LINE-2.
     * Secondary address field for apartment, suite, or additional address information.
     * Optional field maintaining 50-character format.
     */
    @Column(name = "customer_addr_line_2", length = 50)
    @Size(max = 50, message = "Address line 2 cannot exceed 50 characters")
    private String customerAddrLine2;

    /**
     * Customer address line 3 - converted from COBOL PIC X(50) CUST-ADDR-LINE-3.
     * Tertiary address field for extended address information when needed.
     * Optional field maintaining 50-character format for complex addresses.
     */
    @Column(name = "customer_addr_line_3", length = 50)
    @Size(max = 50, message = "Address line 3 cannot exceed 50 characters")
    private String customerAddrLine3;

    /**
     * Customer state code - converted from COBOL PIC X(02) CUST-ADDR-STATE-CD.
     * Two-character state abbreviation for address validation and reporting.
     * Required field for complete address information.
     */
    @Column(name = "customer_addr_state_cd", length = 2, nullable = false)
    @Pattern(regexp = "^[A-Z]{2}$", message = "State code must be exactly 2 uppercase letters")
    @NotNull(message = "State code cannot be null")
    private String customerAddrStateCd;

    /**
     * Customer country code - converted from COBOL PIC X(03) CUST-ADDR-COUNTRY-CD.
     * Three-character country code for international address support.
     * Defaults to USA for domestic customers.
     */
    @Column(name = "customer_addr_country_cd", length = 3, nullable = false)
    @Pattern(regexp = "^[A-Z]{3}$", message = "Country code must be exactly 3 uppercase letters")
    @NotNull(message = "Country code cannot be null")
    private String customerAddrCountryCd;

    /**
     * Customer ZIP code - converted from COBOL PIC X(10) CUST-ADDR-ZIP.
     * ZIP code field supporting both 5-digit and 9-digit formats (with hyphen).
     * Used for geographical analysis and mailing address validation.
     */
    @Column(name = "customer_addr_zip", length = 10, nullable = false)
    @Pattern(regexp = "^\\d{5}(-\\d{4})?$", message = "ZIP code must be in format 12345 or 12345-6789")
    @NotNull(message = "ZIP code cannot be null")
    private String customerAddrZip;

    /**
     * Customer primary phone number - converted from COBOL PIC X(15) CUST-PHONE-NUM-1.
     * Primary contact phone number supporting various formats.
     * Required field for customer communication and verification.
     */
    @Column(name = "customer_phone_num_1", length = 15, nullable = false)
    @Pattern(regexp = "^[\\d\\-\\(\\)\\+\\s]{10,15}$", message = "Phone number must be 10-15 characters with valid format")
    @NotNull(message = "Primary phone number cannot be null")
    private String customerPhoneNum1;

    /**
     * Customer secondary phone number - converted from COBOL PIC X(15) CUST-PHONE-NUM-2.
     * Optional secondary contact number for alternative customer communication.
     * Maintains same format validation as primary phone number.
     */
    @Column(name = "customer_phone_num_2", length = 15)
    @Pattern(regexp = "^[\\d\\-\\(\\)\\+\\s]{10,15}$", message = "Phone number must be 10-15 characters with valid format")
    private String customerPhoneNum2;

    /**
     * Customer Social Security Number - converted from COBOL PIC 9(09) CUST-SSN.
     * Nine-digit SSN stored as String for security and formatting considerations.
     * Unique field used for customer identification and verification.
     */
    @Column(name = "customer_ssn", length = 9, nullable = false, unique = true)
    @Pattern(regexp = "^\\d{9}$", message = "SSN must be exactly 9 digits")
    @NotNull(message = "SSN cannot be null")
    private String customerSsn;

    /**
     * Customer government-issued ID - converted from COBOL PIC X(20) CUST-GOVT-ISSUED-ID.
     * Alternative identification field for customers without SSN or additional verification.
     * Optional field supporting various government identification formats.
     */
    @Column(name = "customer_govt_issued_id", length = 20)
    @Size(max = 20, message = "Government issued ID cannot exceed 20 characters")
    private String customerGovtIssuedId;

    /**
     * Customer date of birth - converted from COBOL PIC X(10) CUST-DOB-YYYYMMDD.
     * Maintains COBOL date format (YYYY-MM-DD) for backward compatibility.
     * Used for age verification, compliance, and demographic analysis.
     */
    @Column(name = "customer_dob_yyyymmdd", length = 10, nullable = false)
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date of birth must be in YYYY-MM-DD format")
    @NotNull(message = "Date of birth cannot be null")
    private String customerDobYyyyMmDd;

    /**
     * Customer EFT account ID - converted from COBOL PIC X(10) CUST-EFT-ACCOUNT-ID.
     * Electronic Funds Transfer account identifier for payment processing.
     * Optional field used for direct deposit and payment operations.
     */
    @Column(name = "customer_eft_account_id", length = 10)
    @Size(max = 10, message = "EFT account ID cannot exceed 10 characters")
    private String customerEftAccountId;

    /**
     * Customer primary cardholder indicator - converted from COBOL PIC X(01) CUST-PRI-CARD-HOLDER-IND.
     * Single character flag indicating if customer is primary cardholder.
     * Values: 'Y' = Primary cardholder, 'N' = Secondary cardholder.
     */
    @Column(name = "customer_pri_card_holder_ind", length = 1, nullable = false)
    @Pattern(regexp = "^[YN]$", message = "Primary cardholder indicator must be Y or N")
    @NotNull(message = "Primary cardholder indicator cannot be null")
    private String customerPriCardHolderInd;

    /**
     * Customer FICO credit score - converted from COBOL PIC 9(03) CUST-FICO-CREDIT-SCORE.
     * Three-digit FICO credit score for creditworthiness assessment.
     * Valid range: 300-850 per standard FICO scoring methodology.
     */
    @Column(name = "customer_fico_credit_score", nullable = false)
    @Min(value = 300, message = "FICO credit score must be at least 300")
    @Max(value = 850, message = "FICO credit score must be at most 850")
    @NotNull(message = "FICO credit score cannot be null")
    private Integer customerFicoCreditScore;

    /**
     * Optimistic locking version field per Section 6.2.1.1 database design.
     * Prevents concurrent modification conflicts using JPA @Version annotation.
     * Automatically managed by Hibernate for ACID compliance.
     */
    @Version
    @Column(name = "row_version", nullable = false)
    private Integer version;

    /**
     * Record creation timestamp for audit trail.
     * Automatically set when customer record is created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Record modification timestamp for audit trail.
     * Automatically updated when customer record is modified.
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * One-to-Many relationship to Account entities.
     * Represents all accounts owned by this customer.
     * Supports customer-to-account navigation for business operations.
     */
    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<Account> accounts = new ArrayList<>();

    /**
     * Default constructor for JPA framework.
     * Initializes timestamps to current time and sets default values.
     */
    public Customer() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.version = 0;
        this.customerAddrCountryCd = "USA"; // Default country code
        this.customerPriCardHolderInd = "Y"; // Default primary cardholder
        this.customerFicoCreditScore = 650; // Default middle-range FICO score
    }

    /**
     * Constructor for creating Customer with required fields.
     * Sets essential customer information matching COBOL record structure.
     *
     * @param customerId 9-digit customer identifier
     * @param customerFirstName Customer first name (max 25 chars)
     * @param customerLastName Customer last name (max 25 chars)
     * @param customerSsn 9-digit Social Security Number
     * @param customerDobYyyyMmDd Date of birth (YYYY-MM-DD format)
     */
    public Customer(String customerId, String customerFirstName, String customerLastName, 
                   String customerSsn, String customerDobYyyyMmDd) {
        this();
        this.customerId = customerId;
        this.customerFirstName = customerFirstName;
        this.customerLastName = customerLastName;
        this.customerSsn = customerSsn;
        this.customerDobYyyyMmDd = customerDobYyyyMmDd;
    }

    /**
     * Callback method executed before entity persistence.
     * Sets creation timestamp for new records.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.version == null) {
            this.version = 0;
        }
    }

    /**
     * Callback method executed before entity update.
     * Updates modification timestamp for changed records.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Determines if customer is primary cardholder based on indicator field.
     * Replicates COBOL 88-level condition logic.
     *
     * @return true if customer is primary cardholder ('Y'), false otherwise
     */
    public boolean isPrimaryCardHolder() {
        return "Y".equals(customerPriCardHolderInd);
    }

    /**
     * Gets customer full name by concatenating first, middle, and last names.
     * Handles null middle name appropriately for display purposes.
     *
     * @return Full customer name as concatenated string
     */
    public String getFullName() {
        StringBuilder fullName = new StringBuilder();
        if (customerFirstName != null) {
            fullName.append(customerFirstName.trim());
        }
        if (customerMiddleName != null && !customerMiddleName.trim().isEmpty()) {
            if (fullName.length() > 0) fullName.append(" ");
            fullName.append(customerMiddleName.trim());
        }
        if (customerLastName != null) {
            if (fullName.length() > 0) fullName.append(" ");
            fullName.append(customerLastName.trim());
        }
        return fullName.toString();
    }

    /**
     * Gets customer complete address as formatted string.
     * Concatenates all address lines with proper formatting.
     *
     * @return Complete formatted address string
     */
    public String getCompleteAddress() {
        StringBuilder address = new StringBuilder();
        if (customerAddrLine1 != null) {
            address.append(customerAddrLine1.trim());
        }
        if (customerAddrLine2 != null && !customerAddrLine2.trim().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(customerAddrLine2.trim());
        }
        if (customerAddrLine3 != null && !customerAddrLine3.trim().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(customerAddrLine3.trim());
        }
        if (customerAddrStateCd != null && customerAddrZip != null) {
            if (address.length() > 0) address.append(", ");
            address.append(customerAddrStateCd).append(" ").append(customerAddrZip);
        }
        if (customerAddrCountryCd != null && !"USA".equals(customerAddrCountryCd)) {
            if (address.length() > 0) address.append(", ");
            address.append(customerAddrCountryCd);
        }
        return address.toString();
    }

    /**
     * Validates FICO credit score is within acceptable range.
     * Implements business rule validation for creditworthiness assessment.
     *
     * @return true if FICO score is in valid range (300-850), false otherwise
     */
    public boolean hasValidFicoScore() {
        return customerFicoCreditScore != null && 
               customerFicoCreditScore >= 300 && 
               customerFicoCreditScore <= 850;
    }

    // Getters and Setters with comprehensive validation

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerFirstName() {
        return customerFirstName;
    }

    public void setCustomerFirstName(String customerFirstName) {
        this.customerFirstName = customerFirstName;
    }

    public String getCustomerMiddleName() {
        return customerMiddleName;
    }

    public void setCustomerMiddleName(String customerMiddleName) {
        this.customerMiddleName = customerMiddleName;
    }

    public String getCustomerLastName() {
        return customerLastName;
    }

    public void setCustomerLastName(String customerLastName) {
        this.customerLastName = customerLastName;
    }

    public String getCustomerAddrLine1() {
        return customerAddrLine1;
    }

    public void setCustomerAddrLine1(String customerAddrLine1) {
        this.customerAddrLine1 = customerAddrLine1;
    }

    public String getCustomerAddrLine2() {
        return customerAddrLine2;
    }

    public void setCustomerAddrLine2(String customerAddrLine2) {
        this.customerAddrLine2 = customerAddrLine2;
    }

    public String getCustomerAddrLine3() {
        return customerAddrLine3;
    }

    public void setCustomerAddrLine3(String customerAddrLine3) {
        this.customerAddrLine3 = customerAddrLine3;
    }

    public String getCustomerAddrStateCd() {
        return customerAddrStateCd;
    }

    public void setCustomerAddrStateCd(String customerAddrStateCd) {
        this.customerAddrStateCd = customerAddrStateCd;
    }

    public String getCustomerAddrCountryCd() {
        return customerAddrCountryCd;
    }

    public void setCustomerAddrCountryCd(String customerAddrCountryCd) {
        this.customerAddrCountryCd = customerAddrCountryCd;
    }

    public String getCustomerAddrZip() {
        return customerAddrZip;
    }

    public void setCustomerAddrZip(String customerAddrZip) {
        this.customerAddrZip = customerAddrZip;
    }

    public String getCustomerPhoneNum1() {
        return customerPhoneNum1;
    }

    public void setCustomerPhoneNum1(String customerPhoneNum1) {
        this.customerPhoneNum1 = customerPhoneNum1;
    }

    public String getCustomerPhoneNum2() {
        return customerPhoneNum2;
    }

    public void setCustomerPhoneNum2(String customerPhoneNum2) {
        this.customerPhoneNum2 = customerPhoneNum2;
    }

    public String getCustomerSsn() {
        return customerSsn;
    }

    public void setCustomerSsn(String customerSsn) {
        this.customerSsn = customerSsn;
    }

    public String getCustomerGovtIssuedId() {
        return customerGovtIssuedId;
    }

    public void setCustomerGovtIssuedId(String customerGovtIssuedId) {
        this.customerGovtIssuedId = customerGovtIssuedId;
    }

    public String getCustomerDobYyyyMmDd() {
        return customerDobYyyyMmDd;
    }

    public void setCustomerDobYyyyMmDd(String customerDobYyyyMmDd) {
        this.customerDobYyyyMmDd = customerDobYyyyMmDd;
    }

    public String getCustomerEftAccountId() {
        return customerEftAccountId;
    }

    public void setCustomerEftAccountId(String customerEftAccountId) {
        this.customerEftAccountId = customerEftAccountId;
    }

    public String getCustomerPriCardHolderInd() {
        return customerPriCardHolderInd;
    }

    public void setCustomerPriCardHolderInd(String customerPriCardHolderInd) {
        this.customerPriCardHolderInd = customerPriCardHolderInd;
    }

    public Integer getCustomerFicoCreditScore() {
        return customerFicoCreditScore;
    }

    public void setCustomerFicoCreditScore(Integer customerFicoCreditScore) {
        this.customerFicoCreditScore = customerFicoCreditScore;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts;
    }

    /**
     * Equals method based on customer ID for entity comparison.
     * Implements consistent equals contract for JPA entity lifecycle.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Customer customer = (Customer) o;
        return Objects.equals(customerId, customer.customerId);
    }

    /**
     * Hash code based on customer ID for collection operations.
     * Maintains consistency with equals implementation.
     */
    @Override
    public int hashCode() {
        return Objects.hash(customerId);
    }

    /**
     * String representation for debugging and logging.
     * Includes key customer information without sensitive data (SSN excluded).
     */
    @Override
    public String toString() {
        return "Customer{" +
                "customerId='" + customerId + '\'' +
                ", customerFirstName='" + customerFirstName + '\'' +
                ", customerLastName='" + customerLastName + '\'' +
                ", customerAddrStateCd='" + customerAddrStateCd + '\'' +
                ", customerAddrZip='" + customerAddrZip + '\'' +
                ", customerFicoCreditScore=" + customerFicoCreditScore +
                ", isPrimaryCardHolder=" + isPrimaryCardHolder() +
                ", version=" + version +
                '}';
    }
}
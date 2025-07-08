package com.carddemo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * JPA entity class representing customer records with exact COBOL field mapping from CUSTREC copybook.
 * 
 * This entity provides customer demographics and contact information required for statement generation
 * processing with optimistic locking and comprehensive validation. The entity maps exactly to the
 * COBOL CUSTOMER-RECORD structure from CUSTREC.cpy and CVCUS01Y.cpy copybooks.
 * 
 * Key Features:
 * - Exact COBOL-to-Java field mapping preserving all numeric precision
 * - Comprehensive field validation matching COBOL business rules
 * - Optimistic locking with version field for concurrent access control
 * - PostgreSQL customers table mapping with proper indexing strategy
 * - Credit score and demographic data for customer segmentation
 * 
 * Original COBOL Structure: CUSTOMER-RECORD (RECLN 500)
 * Database Table: customers
 * Primary Key: customer_id (9-digit unique identifier)
 * 
 * @author CardDemo Migration Team
 * @since 1.0
 */
@Entity
@Table(name = "customers", indexes = {
    @Index(name = "idx_customer_ssn", columnList = "ssn", unique = true),
    @Index(name = "idx_customer_phone", columnList = "phoneNumber"),
    @Index(name = "idx_customer_lastname", columnList = "lastName"),
    @Index(name = "idx_customer_zip", columnList = "zipCode")
})
public class Customer implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Customer ID - Primary key field mapping from COBOL CUST-ID PIC 9(09)
     * 9-digit unique identifier for customer records
     */
    @Id
    @Column(name = "customer_id", length = 9, nullable = false)
    @NotNull(message = "Customer ID is required")
    @Pattern(regexp = "^\\d{9}$", message = "Customer ID must be exactly 9 digits")
    private String customerId;

    /**
     * First name - mapping from COBOL CUST-FIRST-NAME PIC X(25)
     * Customer's first name with maximum 25 characters
     */
    @Column(name = "first_name", length = 25, nullable = false)
    @NotBlank(message = "First name is required")
    @Size(max = 25, message = "First name cannot exceed 25 characters")
    @Pattern(regexp = "^[A-Za-z\\s\\-']+$", message = "First name can only contain letters, spaces, hyphens, and apostrophes")
    private String firstName;

    /**
     * Middle name - mapping from COBOL CUST-MIDDLE-NAME PIC X(25)
     * Customer's middle name with maximum 25 characters (optional)
     */
    @Column(name = "middle_name", length = 25)
    @Size(max = 25, message = "Middle name cannot exceed 25 characters")
    @Pattern(regexp = "^[A-Za-z\\s\\-']*$", message = "Middle name can only contain letters, spaces, hyphens, and apostrophes")
    private String middleName;

    /**
     * Last name - mapping from COBOL CUST-LAST-NAME PIC X(25)
     * Customer's last name with maximum 25 characters
     */
    @Column(name = "last_name", length = 25, nullable = false)
    @NotBlank(message = "Last name is required")
    @Size(max = 25, message = "Last name cannot exceed 25 characters")
    @Pattern(regexp = "^[A-Za-z\\s\\-']+$", message = "Last name can only contain letters, spaces, hyphens, and apostrophes")
    private String lastName;

    /**
     * Address line 1 - mapping from COBOL CUST-ADDR-LINE-1 PIC X(50)
     * Primary address line with maximum 50 characters
     */
    @Column(name = "address_line_1", length = 50, nullable = false)
    @NotBlank(message = "Address line 1 is required")
    @Size(max = 50, message = "Address line 1 cannot exceed 50 characters")
    private String addressLine1;

    /**
     * Address line 2 - mapping from COBOL CUST-ADDR-LINE-2 PIC X(50)
     * Secondary address line with maximum 50 characters (optional)
     */
    @Column(name = "address_line_2", length = 50)
    @Size(max = 50, message = "Address line 2 cannot exceed 50 characters")
    private String addressLine2;

    /**
     * Address line 3 - mapping from COBOL CUST-ADDR-LINE-3 PIC X(50)
     * Third address line with maximum 50 characters (optional)
     */
    @Column(name = "address_line_3", length = 50)
    @Size(max = 50, message = "Address line 3 cannot exceed 50 characters")
    private String addressLine3;

    /**
     * State code - mapping from COBOL CUST-ADDR-STATE-CD PIC X(02)
     * US state code with exactly 2 characters
     */
    @Column(name = "state_code", length = 2, nullable = false)
    @NotBlank(message = "State code is required")
    @Size(min = 2, max = 2, message = "State code must be exactly 2 characters")
    @Pattern(regexp = "^[A-Z]{2}$", message = "State code must be 2 uppercase letters")
    private String stateCode;

    /**
     * Country code - mapping from COBOL CUST-ADDR-COUNTRY-CD PIC X(03)
     * Country code with exactly 3 characters
     */
    @Column(name = "country_code", length = 3, nullable = false)
    @NotBlank(message = "Country code is required")
    @Size(min = 3, max = 3, message = "Country code must be exactly 3 characters")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Country code must be 3 uppercase letters")
    private String countryCode;

    /**
     * ZIP code - mapping from COBOL CUST-ADDR-ZIP PIC X(10)
     * ZIP code with maximum 10 characters supporting ZIP+4 format
     */
    @Column(name = "zip_code", length = 10, nullable = false)
    @NotBlank(message = "ZIP code is required")
    @Size(max = 10, message = "ZIP code cannot exceed 10 characters")
    @Pattern(regexp = "^\\d{5}(-\\d{4})?$", message = "ZIP code must be in format 12345 or 12345-6789")
    private String zipCode;

    /**
     * Primary phone number - mapping from COBOL CUST-PHONE-NUM-1 PIC X(15)
     * Primary phone number with maximum 15 characters
     */
    @Column(name = "phone_number", length = 15, nullable = false)
    @NotBlank(message = "Phone number is required")
    @Size(max = 15, message = "Phone number cannot exceed 15 characters")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Phone number must be in valid international format")
    private String phoneNumber;

    /**
     * Secondary phone number - mapping from COBOL CUST-PHONE-NUM-2 PIC X(15)
     * Secondary phone number with maximum 15 characters (optional)
     */
    @Column(name = "phone_number_2", length = 15)
    @Size(max = 15, message = "Secondary phone number cannot exceed 15 characters")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Secondary phone number must be in valid international format")
    private String phoneNumber2;

    /**
     * Social Security Number - mapping from COBOL CUST-SSN PIC 9(09)
     * 9-digit SSN for customer identification
     */
    @Column(name = "ssn", length = 9, nullable = false, unique = true)
    @NotBlank(message = "SSN is required")
    @Pattern(regexp = "^\\d{9}$", message = "SSN must be exactly 9 digits")
    private String ssn;

    /**
     * Government issued ID - mapping from COBOL CUST-GOVT-ISSUED-ID PIC X(20)
     * Government issued identification with maximum 20 characters
     */
    @Column(name = "government_issued_id", length = 20)
    @Size(max = 20, message = "Government issued ID cannot exceed 20 characters")
    private String governmentIssuedId;

    /**
     * Date of birth - mapping from COBOL CUST-DOB-YYYYMMDD PIC X(10)
     * Date of birth in YYYYMMDD format
     */
    @Column(name = "date_of_birth", length = 10, nullable = false)
    @NotBlank(message = "Date of birth is required")
    @Pattern(regexp = "^\\d{4}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])$", message = "Date of birth must be in YYYYMMDD format")
    private String dateOfBirth;

    /**
     * EFT account ID - mapping from COBOL CUST-EFT-ACCOUNT-ID PIC X(10)
     * Electronic funds transfer account identifier with maximum 10 characters
     */
    @Column(name = "eft_account_id", length = 10)
    @Size(max = 10, message = "EFT account ID cannot exceed 10 characters")
    private String eftAccountId;

    /**
     * Primary card holder indicator - mapping from COBOL CUST-PRI-CARD-HOLDER-IND PIC X(01)
     * Single character flag indicating primary card holder status
     */
    @Column(name = "primary_card_holder_indicator", length = 1)
    @Size(max = 1, message = "Primary card holder indicator must be 1 character")
    @Pattern(regexp = "^[YN]?$", message = "Primary card holder indicator must be Y, N, or empty")
    private String primaryCardHolderIndicator;

    /**
     * FICO credit score - mapping from COBOL CUST-FICO-CREDIT-SCORE PIC 9(03)
     * 3-digit FICO credit score for customer segmentation
     */
    @Column(name = "fico_credit_score")
    @Min(value = 300, message = "FICO credit score must be at least 300")
    @Max(value = 850, message = "FICO credit score cannot exceed 850")
    private Integer ficoScore;

    /**
     * Version field for optimistic locking in JPA
     * Required for concurrent access control as specified in the requirements
     */
    @Version
    @Column(name = "row_version", nullable = false)
    private Long versionNumber;

    /**
     * Default constructor for JPA
     */
    public Customer() {
        // Default constructor for JPA
    }

    /**
     * Constructor with required fields
     * 
     * @param customerId Customer ID (9 digits)
     * @param firstName Customer's first name
     * @param lastName Customer's last name
     * @param addressLine1 Primary address line
     * @param stateCode US state code (2 characters)
     * @param countryCode Country code (3 characters)
     * @param zipCode ZIP code
     * @param phoneNumber Primary phone number
     * @param ssn Social Security Number (9 digits)
     * @param dateOfBirth Date of birth in YYYYMMDD format
     */
    public Customer(String customerId, String firstName, String lastName, String addressLine1, 
                   String stateCode, String countryCode, String zipCode, String phoneNumber, 
                   String ssn, String dateOfBirth) {
        this.customerId = customerId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.addressLine1 = addressLine1;
        this.stateCode = stateCode;
        this.countryCode = countryCode;
        this.zipCode = zipCode;
        this.phoneNumber = phoneNumber;
        this.ssn = ssn;
        this.dateOfBirth = dateOfBirth;
    }

    // Getter and Setter methods

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public void setAddressLine2(String addressLine2) {
        this.addressLine2 = addressLine2;
    }

    public String getAddressLine3() {
        return addressLine3;
    }

    public void setAddressLine3(String addressLine3) {
        this.addressLine3 = addressLine3;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setState(String stateCode) {
        this.stateCode = stateCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPhoneNumber2() {
        return phoneNumber2;
    }

    public void setPhoneNumber2(String phoneNumber2) {
        this.phoneNumber2 = phoneNumber2;
    }

    public String getSsn() {
        return ssn;
    }

    public void setSsn(String ssn) {
        this.ssn = ssn;
    }

    public String getGovernmentIssuedId() {
        return governmentIssuedId;
    }

    public void setGovernmentIssuedId(String governmentIssuedId) {
        this.governmentIssuedId = governmentIssuedId;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getEftAccountId() {
        return eftAccountId;
    }

    public void setEftAccountId(String eftAccountId) {
        this.eftAccountId = eftAccountId;
    }

    public String getPrimaryCardHolderIndicator() {
        return primaryCardHolderIndicator;
    }

    public void setPrimaryCardHolderIndicator(String primaryCardHolderIndicator) {
        this.primaryCardHolderIndicator = primaryCardHolderIndicator;
    }

    public Integer getFicoScore() {
        return ficoScore;
    }

    public void setFicoScore(Integer ficoScore) {
        this.ficoScore = ficoScore;
    }

    public Long getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Long versionNumber) {
        this.versionNumber = versionNumber;
    }

    /**
     * Equals method for entity comparison
     * Uses customer ID as the primary comparison field
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Customer customer = (Customer) obj;
        return Objects.equals(customerId, customer.customerId);
    }

    /**
     * Hash code method for entity hashing
     * Uses customer ID as the primary hash field
     */
    @Override
    public int hashCode() {
        return Objects.hash(customerId);
    }

    /**
     * String representation of the customer entity
     * Provides customer ID and name for debugging purposes
     */
    @Override
    public String toString() {
        return "Customer{" +
                "customerId='" + customerId + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", stateCode='" + stateCode + '\'' +
                ", zipCode='" + zipCode + '\'' +
                ", versionNumber=" + versionNumber +
                '}';
    }
}
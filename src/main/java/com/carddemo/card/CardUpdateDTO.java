package com.carddemo.card;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Data Transfer Object for card update operations providing field validation,
 * version control, change tracking, and business rule enforcement for secure
 * and compliant card modification functionality.
 * 
 * This class preserves COBOL field validation rules from COCRDUPC.cbl and
 * implements Spring Boot validation annotations for comprehensive business
 * rule enforcement during card update operations.
 * 
 * Based on COBOL program: COCRDUPC.cbl
 * Related copybook: CVCRD01Y.cpy
 * 
 * @author Blitzy agent
 * @version 1.0
 * @since 2024-03-15
 */
public class CardUpdateDTO {
    
    /**
     * Card number - 16-digit unique identifier
     * Maps to COBOL field: CCUP-NEW-CARDID (PIC X(16))
     * Validation: Must be exactly 16 digits, numeric only
     */
    @NotBlank(message = "Card number is required")
    @Pattern(regexp = "^[0-9]{16}$", message = "Card number must be exactly 16 digits")
    private String cardNumber;
    
    /**
     * Account ID - 11-digit account identifier
     * Maps to COBOL field: CCUP-NEW-ACCTID (PIC X(11))
     * Validation: Must be exactly 11 digits, non-zero
     */
    @NotBlank(message = "Account ID is required")
    @Pattern(regexp = "^[0-9]{11}$", message = "Account ID must be exactly 11 digits")
    @Pattern(regexp = "^(?!0+$)[0-9]{11}$", message = "Account ID must be a non-zero 11 digit number")
    private String accountId;
    
    /**
     * Embossed name on card - customer name as it appears on card
     * Maps to COBOL field: CCUP-NEW-CRDNAME (PIC X(50))
     * Validation: Alphabetic characters and spaces only, max 50 characters
     */
    @NotBlank(message = "Card name is required")
    @Size(max = 50, message = "Card name cannot exceed 50 characters")
    @Pattern(regexp = "^[A-Za-z\\s]+$", message = "Card name can only contain alphabets and spaces")
    private String embossedName;
    
    /**
     * Card status - Active/Inactive indicator
     * Maps to COBOL field: CCUP-NEW-CRDSTCD (PIC X(1))
     * Validation: Must be Y (active) or N (inactive)
     */
    @NotBlank(message = "Card status is required")
    @Pattern(regexp = "^[YN]$", message = "Card Active Status must be Y or N")
    private String cardStatus;
    
    /**
     * Expiry month - Month component of card expiration date
     * Maps to COBOL field: CCUP-NEW-EXPMON (PIC X(2))
     * Validation: Must be between 1 and 12
     */
    @NotNull(message = "Expiry month is required")
    @Min(value = 1, message = "Card expiry month must be between 1 and 12")
    @Max(value = 12, message = "Card expiry month must be between 1 and 12")
    private Integer expiryMonth;
    
    /**
     * Expiry year - Year component of card expiration date
     * Maps to COBOL field: CCUP-NEW-EXPYEAR (PIC X(4))
     * Validation: Must be between 1950 and 2099
     */
    @NotNull(message = "Expiry year is required")
    @Min(value = 1950, message = "Invalid card expiry year")
    @Max(value = 2099, message = "Invalid card expiry year")
    private Integer expiryYear;
    
    /**
     * CVV code - 3-digit security code (sensitive data)
     * Maps to COBOL field: CCUP-NEW-CVV-CD (PIC X(3))
     * Validation: Must be exactly 3 digits
     * Note: Excluded from JSON serialization for security
     */
    @JsonIgnore
    @Pattern(regexp = "^[0-9]{3}$", message = "CVV code must be exactly 3 digits")
    private String cvvCode;
    
    /**
     * Version number for optimistic locking
     * Used to prevent concurrent modification conflicts
     * Implemented as per JPA @Version annotation pattern
     */
    @NotNull(message = "Version number is required for optimistic locking")
    private Long versionNumber;
    
    /**
     * User ID who last updated the card
     * Maps to user context from COMMAREA equivalent
     * Used for audit trail and change tracking
     */
    @Size(max = 8, message = "User ID cannot exceed 8 characters")
    private String lastUpdatedBy;
    
    /**
     * Timestamp of last update
     * Used for audit trail and change tracking
     */
    private LocalDateTime lastUpdatedDate;
    
    /**
     * Reason for the card update
     * Business requirement for audit and compliance
     */
    @Size(max = 100, message = "Change reason cannot exceed 100 characters")
    private String changeReason;
    
    /**
     * Audit trail identifier
     * Links to comprehensive audit log for compliance
     */
    private String auditTrailId;
    
    /**
     * Default constructor
     */
    public CardUpdateDTO() {
        // Initialize with current timestamp
        this.lastUpdatedDate = LocalDateTime.now();
    }
    
    /**
     * Constructor with required fields
     * 
     * @param cardNumber The 16-digit card number
     * @param accountId The 11-digit account ID
     * @param embossedName The embossed name on the card
     * @param cardStatus The card status (Y/N)
     * @param expiryMonth The expiry month (1-12)
     * @param expiryYear The expiry year (1950-2099)
     * @param versionNumber The version number for optimistic locking
     */
    public CardUpdateDTO(String cardNumber, String accountId, String embossedName, 
                        String cardStatus, Integer expiryMonth, Integer expiryYear, 
                        Long versionNumber) {
        this();
        this.cardNumber = cardNumber;
        this.accountId = accountId;
        this.embossedName = embossedName;
        this.cardStatus = cardStatus;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
        this.versionNumber = versionNumber;
    }
    
    // Getter and Setter methods
    
    public String getCardNumber() {
        return cardNumber;
    }
    
    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }
    
    public String getAccountId() {
        return accountId;
    }
    
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }
    
    public String getEmbossedName() {
        return embossedName;
    }
    
    public void setEmbossedName(String embossedName) {
        this.embossedName = embossedName;
    }
    
    public String getCardStatus() {
        return cardStatus;
    }
    
    public void setCardStatus(String cardStatus) {
        this.cardStatus = cardStatus;
    }
    
    public Integer getExpiryMonth() {
        return expiryMonth;
    }
    
    public void setExpiryMonth(Integer expiryMonth) {
        this.expiryMonth = expiryMonth;
    }
    
    public Integer getExpiryYear() {
        return expiryYear;
    }
    
    public void setExpiryYear(Integer expiryYear) {
        this.expiryYear = expiryYear;
    }
    
    public String getCvvCode() {
        return cvvCode;
    }
    
    public void setCvvCode(String cvvCode) {
        this.cvvCode = cvvCode;
    }
    
    public Long getVersionNumber() {
        return versionNumber;
    }
    
    public void setVersionNumber(Long versionNumber) {
        this.versionNumber = versionNumber;
    }
    
    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }
    
    public void setLastUpdatedBy(String lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }
    
    public LocalDateTime getLastUpdatedDate() {
        return lastUpdatedDate;
    }
    
    public void setLastUpdatedDate(LocalDateTime lastUpdatedDate) {
        this.lastUpdatedDate = lastUpdatedDate;
    }
    
    public String getChangeReason() {
        return changeReason;
    }
    
    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }
    
    public String getAuditTrailId() {
        return auditTrailId;
    }
    
    public void setAuditTrailId(String auditTrailId) {
        this.auditTrailId = auditTrailId;
    }
    
    /**
     * Validates card update business rules
     * Implements COBOL validation logic from COCRDUPC.cbl
     * 
     * This method performs comprehensive validation including:
     * - Card expiry date validation (must be future date)
     * - Cross-field validation rules
     * - Business logic validation
     * 
     * @return true if all validation rules pass, false otherwise
     */
    public boolean validateCardUpdate() {
        // Validate card expiry date is not in the past
        if (expiryMonth != null && expiryYear != null) {
            try {
                YearMonth expiryDate = YearMonth.of(expiryYear, expiryMonth);
                YearMonth currentDate = YearMonth.now();
                
                // Card must not be expired
                if (expiryDate.isBefore(currentDate)) {
                    return false;
                }
                
                // Card expiry should not be more than 10 years in the future
                YearMonth maxFutureDate = currentDate.plusYears(10);
                if (expiryDate.isAfter(maxFutureDate)) {
                    return false;
                }
            } catch (Exception e) {
                // Invalid date combination
                return false;
            }
        }
        
        // Validate card number format (basic Luhn algorithm check)
        if (cardNumber != null && !isValidCardNumber(cardNumber)) {
            return false;
        }
        
        // Validate embossed name does not contain invalid characters
        if (embossedName != null) {
            String trimmedName = embossedName.trim();
            if (trimmedName.isEmpty() || trimmedName.length() > 50) {
                return false;
            }
            
            // Check for only alphabetic characters and spaces
            if (!trimmedName.matches("^[A-Za-z\\s]+$")) {
                return false;
            }
        }
        
        // All validation rules passed
        return true;
    }
    
    /**
     * Validates card number using Luhn algorithm
     * Implements basic card number validation as per industry standards
     * 
     * @param cardNumber The card number to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() != 16) {
            return false;
        }
        
        // Basic Luhn algorithm implementation
        int sum = 0;
        boolean alternate = false;
        
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cardNumber.charAt(i));
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit % 10) + 1;
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        return (sum % 10 == 0);
    }
    
    /**
     * Checks if two CardUpdateDTO objects are equal
     * Used for change detection and optimistic locking
     * 
     * @param obj The object to compare with
     * @return true if objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        CardUpdateDTO that = (CardUpdateDTO) obj;
        return Objects.equals(cardNumber, that.cardNumber) &&
               Objects.equals(accountId, that.accountId) &&
               Objects.equals(embossedName, that.embossedName) &&
               Objects.equals(cardStatus, that.cardStatus) &&
               Objects.equals(expiryMonth, that.expiryMonth) &&
               Objects.equals(expiryYear, that.expiryYear) &&
               Objects.equals(cvvCode, that.cvvCode) &&
               Objects.equals(versionNumber, that.versionNumber) &&
               Objects.equals(lastUpdatedBy, that.lastUpdatedBy) &&
               Objects.equals(lastUpdatedDate, that.lastUpdatedDate) &&
               Objects.equals(changeReason, that.changeReason) &&
               Objects.equals(auditTrailId, that.auditTrailId);
    }
    
    /**
     * Generates hash code for the object
     * Used for collections and change detection
     * 
     * @return hash code value
     */
    @Override
    public int hashCode() {
        return Objects.hash(cardNumber, accountId, embossedName, cardStatus, 
                          expiryMonth, expiryYear, cvvCode, versionNumber, 
                          lastUpdatedBy, lastUpdatedDate, changeReason, auditTrailId);
    }
    
    /**
     * String representation of the object
     * Excludes sensitive data (CVV code) from output
     * 
     * @return string representation
     */
    @Override
    public String toString() {
        return "CardUpdateDTO{" +
               "cardNumber='" + (cardNumber != null ? cardNumber.substring(0, 4) + "****" + cardNumber.substring(12) : null) + '\'' +
               ", accountId='" + accountId + '\'' +
               ", embossedName='" + embossedName + '\'' +
               ", cardStatus='" + cardStatus + '\'' +
               ", expiryMonth=" + expiryMonth +
               ", expiryYear=" + expiryYear +
               ", cvvCode='***'" +
               ", versionNumber=" + versionNumber +
               ", lastUpdatedBy='" + lastUpdatedBy + '\'' +
               ", lastUpdatedDate=" + lastUpdatedDate +
               ", changeReason='" + changeReason + '\'' +
               ", auditTrailId='" + auditTrailId + '\'' +
               '}';
    }
}
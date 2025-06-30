/*
 * AccountDto.java
 * 
 * Data Transfer Object for account information supporting JSON-based REST API communication.
 * Provides clean interface contract for account data exchange with validation rules 
 * equivalent to original COBOL record constraints and BigDecimal precision for monetary calculations.
 * 
 * Derived from COBOL copybook: app/cpy/CVACT01Y.cpy
 * Original COBOL structure: ACCOUNT-RECORD (RECLN 300)
 * 
 * Ver: CardDemo_v1.0-15-g27d6c6f-68 Date: 2022-07-19 23:15:59 CDT
 */
package com.carddemo.account;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Data Transfer Object for Account entity.
 * 
 * This DTO provides a clean interface for REST API communication while maintaining
 * exact field definitions and validation constraints equivalent to the original 
 * COBOL ACCOUNT-RECORD structure from copybook CVACT01Y.cpy.
 * 
 * All monetary fields use BigDecimal with scale 2 to preserve exact precision
 * as required for financial calculations, matching COBOL PIC S9(10)V99 fields.
 * 
 * Field validation annotations ensure data integrity matching original COBOL
 * PIC clauses and 88-level conditions.
 */
@JsonPropertyOrder({
    "accountId", "activeStatus", "currentBalance", "creditLimit", "cashCreditLimit",
    "openDate", "expirationDate", "reissueDate", "currentCycleCredit", 
    "currentCycleDebit", "addressZip", "groupId"
})
public class AccountDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Account ID - 11-digit numeric identifier
     * COBOL: ACCT-ID PIC 9(11)
     */
    @JsonProperty("accountId")
    @NotNull(message = "Account ID is required")
    @Min(value = 0, message = "Account ID must be non-negative")
    @Max(value = 99999999999L, message = "Account ID must be 11 digits or less")
    private Long accountId;

    /**
     * Account Active Status - Single character status code
     * COBOL: ACCT-ACTIVE-STATUS PIC X(01)
     * Valid values: A (Active), I (Inactive), C (Closed), S (Suspended)
     */
    @JsonProperty("activeStatus")
    @NotNull(message = "Active status is required")
    @Size(min = 1, max = 1, message = "Active status must be exactly 1 character")
    @Pattern(regexp = "[AICS]", message = "Active status must be A (Active), I (Inactive), C (Closed), or S (Suspended)")
    private String activeStatus;

    /**
     * Current Account Balance - Signed decimal with 2 decimal places
     * COBOL: ACCT-CURR-BAL PIC S9(10)V99
     */
    @JsonProperty("currentBalance")
    @NotNull(message = "Current balance is required")
    @Digits(integer = 10, fraction = 2, message = "Current balance must have at most 10 integer digits and 2 decimal places")
    @DecimalMin(value = "-9999999999.99", message = "Current balance must be greater than or equal to -9999999999.99")
    @DecimalMax(value = "9999999999.99", message = "Current balance must be less than or equal to 9999999999.99")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal currentBalance;

    /**
     * Credit Limit - Signed decimal with 2 decimal places
     * COBOL: ACCT-CREDIT-LIMIT PIC S9(10)V99
     */
    @JsonProperty("creditLimit")
    @NotNull(message = "Credit limit is required")
    @Digits(integer = 10, fraction = 2, message = "Credit limit must have at most 10 integer digits and 2 decimal places")
    @DecimalMin(value = "0.00", message = "Credit limit must be non-negative")
    @DecimalMax(value = "9999999999.99", message = "Credit limit must be less than or equal to 9999999999.99")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal creditLimit;

    /**
     * Cash Credit Limit - Signed decimal with 2 decimal places
     * COBOL: ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99
     */
    @JsonProperty("cashCreditLimit")
    @NotNull(message = "Cash credit limit is required")
    @Digits(integer = 10, fraction = 2, message = "Cash credit limit must have at most 10 integer digits and 2 decimal places")
    @DecimalMin(value = "0.00", message = "Cash credit limit must be non-negative")
    @DecimalMax(value = "9999999999.99", message = "Cash credit limit must be less than or equal to 9999999999.99")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal cashCreditLimit;

    /**
     * Account Open Date - Date string in YYYY-MM-DD format
     * COBOL: ACCT-OPEN-DATE PIC X(10)
     */
    @JsonProperty("openDate")
    @NotNull(message = "Open date is required")
    @Size(min = 10, max = 10, message = "Open date must be exactly 10 characters")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Open date must be in YYYY-MM-DD format")
    private String openDate;

    /**
     * Account Expiration Date - Date string in YYYY-MM-DD format
     * COBOL: ACCT-EXPIRAION-DATE PIC X(10) (Note: typo preserved from original)
     */
    @JsonProperty("expirationDate")
    @NotNull(message = "Expiration date is required")
    @Size(min = 10, max = 10, message = "Expiration date must be exactly 10 characters")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Expiration date must be in YYYY-MM-DD format")
    private String expirationDate;

    /**
     * Account Reissue Date - Date string in YYYY-MM-DD format
     * COBOL: ACCT-REISSUE-DATE PIC X(10)
     */
    @JsonProperty("reissueDate")
    @Size(min = 10, max = 10, message = "Reissue date must be exactly 10 characters")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Reissue date must be in YYYY-MM-DD format")
    private String reissueDate;

    /**
     * Current Cycle Credit - Signed decimal with 2 decimal places
     * COBOL: ACCT-CURR-CYC-CREDIT PIC S9(10)V99
     */
    @JsonProperty("currentCycleCredit")
    @NotNull(message = "Current cycle credit is required")
    @Digits(integer = 10, fraction = 2, message = "Current cycle credit must have at most 10 integer digits and 2 decimal places")
    @DecimalMin(value = "0.00", message = "Current cycle credit must be non-negative")
    @DecimalMax(value = "9999999999.99", message = "Current cycle credit must be less than or equal to 9999999999.99")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal currentCycleCredit;

    /**
     * Current Cycle Debit - Signed decimal with 2 decimal places
     * COBOL: ACCT-CURR-CYC-DEBIT PIC S9(10)V99
     */
    @JsonProperty("currentCycleDebit")
    @NotNull(message = "Current cycle debit is required")
    @Digits(integer = 10, fraction = 2, message = "Current cycle debit must have at most 10 integer digits and 2 decimal places")
    @DecimalMin(value = "0.00", message = "Current cycle debit must be non-negative")
    @DecimalMax(value = "9999999999.99", message = "Current cycle debit must be less than or equal to 9999999999.99")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal currentCycleDebit;

    /**
     * Address ZIP Code - 10-character string
     * COBOL: ACCT-ADDR-ZIP PIC X(10)
     */
    @JsonProperty("addressZip")
    @Size(max = 10, message = "Address ZIP must be 10 characters or less")
    @Pattern(regexp = "[0-9A-Za-z\\s-]*", message = "Address ZIP must contain only alphanumeric characters, spaces, and hyphens")
    private String addressZip;

    /**
     * Group ID - 10-character string
     * COBOL: ACCT-GROUP-ID PIC X(10)
     */
    @JsonProperty("groupId")
    @Size(max = 10, message = "Group ID must be 10 characters or less")
    @Pattern(regexp = "[0-9A-Za-z]*", message = "Group ID must contain only alphanumeric characters")
    private String groupId;

    /**
     * Default constructor for Jackson deserialization
     */
    public AccountDto() {
        // Initialize monetary fields with BigDecimal.ZERO with scale 2
        this.currentBalance = BigDecimal.ZERO.setScale(2);
        this.creditLimit = BigDecimal.ZERO.setScale(2);
        this.cashCreditLimit = BigDecimal.ZERO.setScale(2);
        this.currentCycleCredit = BigDecimal.ZERO.setScale(2);
        this.currentCycleDebit = BigDecimal.ZERO.setScale(2);
    }

    /**
     * Full constructor for creating AccountDto instances
     * 
     * @param accountId Account ID (11-digit numeric)
     * @param activeStatus Account status (A/I/C/S)
     * @param currentBalance Current account balance
     * @param creditLimit Credit limit
     * @param cashCreditLimit Cash credit limit
     * @param openDate Account open date (YYYY-MM-DD)
     * @param expirationDate Account expiration date (YYYY-MM-DD)
     * @param reissueDate Account reissue date (YYYY-MM-DD)
     * @param currentCycleCredit Current cycle credit amount
     * @param currentCycleDebit Current cycle debit amount
     * @param addressZip Address ZIP code
     * @param groupId Group ID
     */
    public AccountDto(Long accountId, String activeStatus, BigDecimal currentBalance, 
                     BigDecimal creditLimit, BigDecimal cashCreditLimit, String openDate,
                     String expirationDate, String reissueDate, BigDecimal currentCycleCredit,
                     BigDecimal currentCycleDebit, String addressZip, String groupId) {
        this.accountId = accountId;
        this.activeStatus = activeStatus;
        this.currentBalance = currentBalance != null ? currentBalance.setScale(2) : BigDecimal.ZERO.setScale(2);
        this.creditLimit = creditLimit != null ? creditLimit.setScale(2) : BigDecimal.ZERO.setScale(2);
        this.cashCreditLimit = cashCreditLimit != null ? cashCreditLimit.setScale(2) : BigDecimal.ZERO.setScale(2);
        this.openDate = openDate;
        this.expirationDate = expirationDate;
        this.reissueDate = reissueDate;
        this.currentCycleCredit = currentCycleCredit != null ? currentCycleCredit.setScale(2) : BigDecimal.ZERO.setScale(2);
        this.currentCycleDebit = currentCycleDebit != null ? currentCycleDebit.setScale(2) : BigDecimal.ZERO.setScale(2);
        this.addressZip = addressZip;
        this.groupId = groupId;
    }

    // Getter and Setter methods

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getActiveStatus() {
        return activeStatus;
    }

    public void setActiveStatus(String activeStatus) {
        this.activeStatus = activeStatus;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance != null ? currentBalance.setScale(2) : BigDecimal.ZERO.setScale(2);
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit != null ? creditLimit.setScale(2) : BigDecimal.ZERO.setScale(2);
    }

    public BigDecimal getCashCreditLimit() {
        return cashCreditLimit;
    }

    public void setCashCreditLimit(BigDecimal cashCreditLimit) {
        this.cashCreditLimit = cashCreditLimit != null ? cashCreditLimit.setScale(2) : BigDecimal.ZERO.setScale(2);
    }

    public String getOpenDate() {
        return openDate;
    }

    public void setOpenDate(String openDate) {
        this.openDate = openDate;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }

    public String getReissueDate() {
        return reissueDate;
    }

    public void setReissueDate(String reissueDate) {
        this.reissueDate = reissueDate;
    }

    public BigDecimal getCurrentCycleCredit() {
        return currentCycleCredit;
    }

    public void setCurrentCycleCredit(BigDecimal currentCycleCredit) {
        this.currentCycleCredit = currentCycleCredit != null ? currentCycleCredit.setScale(2) : BigDecimal.ZERO.setScale(2);
    }

    public BigDecimal getCurrentCycleDebit() {
        return currentCycleDebit;
    }

    public void setCurrentCycleDebit(BigDecimal currentCycleDebit) {
        this.currentCycleDebit = currentCycleDebit != null ? currentCycleDebit.setScale(2) : BigDecimal.ZERO.setScale(2);
    }

    public String getAddressZip() {
        return addressZip;
    }

    public void setAddressZip(String addressZip) {
        this.addressZip = addressZip;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    /**
     * Utility method to check if the account is active
     * Equivalent to COBOL 88-level condition
     * 
     * @return true if account status is 'A' (Active)
     */
    public boolean isActive() {
        return "A".equals(activeStatus);
    }

    /**
     * Utility method to check if the account is inactive
     * Equivalent to COBOL 88-level condition
     * 
     * @return true if account status is 'I' (Inactive)
     */
    public boolean isInactive() {
        return "I".equals(activeStatus);
    }

    /**
     * Utility method to check if the account is closed
     * Equivalent to COBOL 88-level condition
     * 
     * @return true if account status is 'C' (Closed)
     */
    public boolean isClosed() {
        return "C".equals(activeStatus);
    }

    /**
     * Utility method to check if the account is suspended
     * Equivalent to COBOL 88-level condition
     * 
     * @return true if account status is 'S' (Suspended)
     */
    public boolean isSuspended() {
        return "S".equals(activeStatus);
    }

    /**
     * Utility method to calculate available credit
     * Available credit = Credit limit - Current balance (if positive)
     * 
     * @return available credit amount
     */
    public BigDecimal getAvailableCredit() {
        if (creditLimit == null || currentBalance == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal available = creditLimit.subtract(currentBalance);
        return available.compareTo(BigDecimal.ZERO) > 0 ? available : BigDecimal.ZERO.setScale(2);
    }

    /**
     * Utility method to calculate available cash credit
     * Available cash credit = Cash credit limit - Current balance (if positive)
     * 
     * @return available cash credit amount
     */
    public BigDecimal getAvailableCashCredit() {
        if (cashCreditLimit == null || currentBalance == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal available = cashCreditLimit.subtract(currentBalance);
        return available.compareTo(BigDecimal.ZERO) > 0 ? available : BigDecimal.ZERO.setScale(2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountDto that = (AccountDto) o;
        return Objects.equals(accountId, that.accountId) &&
               Objects.equals(activeStatus, that.activeStatus) &&
               Objects.equals(currentBalance, that.currentBalance) &&
               Objects.equals(creditLimit, that.creditLimit) &&
               Objects.equals(cashCreditLimit, that.cashCreditLimit) &&
               Objects.equals(openDate, that.openDate) &&
               Objects.equals(expirationDate, that.expirationDate) &&
               Objects.equals(reissueDate, that.reissueDate) &&
               Objects.equals(currentCycleCredit, that.currentCycleCredit) &&
               Objects.equals(currentCycleDebit, that.currentCycleDebit) &&
               Objects.equals(addressZip, that.addressZip) &&
               Objects.equals(groupId, that.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, activeStatus, currentBalance, creditLimit, 
                          cashCreditLimit, openDate, expirationDate, reissueDate,
                          currentCycleCredit, currentCycleDebit, addressZip, groupId);
    }

    @Override
    public String toString() {
        return "AccountDto{" +
               "accountId=" + accountId +
               ", activeStatus='" + activeStatus + '\'' +
               ", currentBalance=" + currentBalance +
               ", creditLimit=" + creditLimit +
               ", cashCreditLimit=" + cashCreditLimit +
               ", openDate='" + openDate + '\'' +
               ", expirationDate='" + expirationDate + '\'' +
               ", reissueDate='" + reissueDate + '\'' +
               ", currentCycleCredit=" + currentCycleCredit +
               ", currentCycleDebit=" + currentCycleDebit +
               ", addressZip='" + addressZip + '\'' +
               ", groupId='" + groupId + '\'' +
               '}';
    }
}
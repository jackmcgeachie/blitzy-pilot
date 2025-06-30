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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.carddemo.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.Objects;

/**
 * Session data model representing the Java equivalent of CICS COMMAREA structure
 * from COCOM01Y.cpy copybook. This class maintains pseudo-conversational state
 * across stateless REST interactions through Redis-backed session storage.
 * 
 * <p>This class preserves the exact COBOL data structure semantics while providing
 * modern Java features including JSON serialization, validation, and Spring Session
 * integration for distributed session management.</p>
 * 
 * <p><strong>COBOL Equivalent:</strong> 01 CARDDEMO-COMMAREA from COCOM01Y.cpy</p>
 * 
 * <p><strong>Usage in Spring Session:</strong><br>
 * The SessionData object is stored as a session attribute in Redis through
 * Spring Session, enabling stateless REST controllers to maintain user context
 * and navigation state across multiple HTTP requests, replicating CICS
 * pseudo-conversational programming patterns.</p>
 *
 * @author Blitzy Agent
 * @version 1.0
 * @since 2024-01-01
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionData implements Serializable {

    private static final long serialVersionUID = 1L;

    // =========================================================================
    // GENERAL INFO - Maps to CDEMO-GENERAL-INFO section
    // =========================================================================
    
    /**
     * Source transaction ID - COBOL equivalent: CDEMO-FROM-TRANID PIC X(04)
     * Represents the originating CICS transaction code for navigation context
     */
    @JsonProperty("fromTransactionId")
    @Size(max = 4, message = "From transaction ID must not exceed 4 characters")
    private String fromTransactionId;

    /**
     * Source program name - COBOL equivalent: CDEMO-FROM-PROGRAM PIC X(08)
     * Identifies the calling program for proper navigation flow control
     */
    @JsonProperty("fromProgram")
    @Size(max = 8, message = "From program name must not exceed 8 characters")
    private String fromProgram;

    /**
     * Target transaction ID - COBOL equivalent: CDEMO-TO-TRANID PIC X(04)
     * Specifies the next transaction to be invoked in the conversation flow
     */
    @JsonProperty("toTransactionId")
    @Size(max = 4, message = "To transaction ID must not exceed 4 characters")
    private String toTransactionId;

    /**
     * Target program name - COBOL equivalent: CDEMO-TO-PROGRAM PIC X(08)
     * Identifies the target program for the next conversation step
     */
    @JsonProperty("toProgram")
    @Size(max = 8, message = "To program name must not exceed 8 characters")
    private String toProgram;

    /**
     * User ID - COBOL equivalent: CDEMO-USER-ID PIC X(08)
     * Authenticated user identifier matching RACF user ID format
     */
    @JsonProperty("userId")
    @Size(max = 8, message = "User ID must not exceed 8 characters")
    private String userId;

    /**
     * User type indicator - COBOL equivalent: CDEMO-USER-TYPE PIC X(01)
     * Values: 'A' = Admin User, 'U' = Regular User (88-level conditions)
     */
    @JsonProperty("userType")
    @Pattern(regexp = "[AU]", message = "User type must be 'A' (Admin) or 'U' (User)")
    @Size(min = 1, max = 1, message = "User type must be exactly 1 character")
    private String userType;

    /**
     * Program context indicator - COBOL equivalent: CDEMO-PGM-CONTEXT PIC 9(01)
     * Values: 0 = First Entry, 1 = Re-entry (88-level conditions)
     */
    @JsonProperty("programContext")
    @Pattern(regexp = "[01]", message = "Program context must be '0' (Enter) or '1' (Re-enter)")
    private String programContext;

    // =========================================================================
    // CUSTOMER INFO - Maps to CDEMO-CUSTOMER-INFO section
    // =========================================================================

    /**
     * Customer ID - COBOL equivalent: CDEMO-CUST-ID PIC 9(09)
     * 9-digit customer identifier for customer management operations
     */
    @JsonProperty("customerId")
    @Pattern(regexp = "\\d{0,9}", message = "Customer ID must be numeric up to 9 digits")
    private String customerId;

    /**
     * Customer first name - COBOL equivalent: CDEMO-CUST-FNAME PIC X(25)
     * Customer's first name for display and validation purposes
     */
    @JsonProperty("customerFirstName")
    @Size(max = 25, message = "Customer first name must not exceed 25 characters")
    private String customerFirstName;

    /**
     * Customer middle name - COBOL equivalent: CDEMO-CUST-MNAME PIC X(25)
     * Customer's middle name or initial for complete name display
     */
    @JsonProperty("customerMiddleName")
    @Size(max = 25, message = "Customer middle name must not exceed 25 characters")
    private String customerMiddleName;

    /**
     * Customer last name - COBOL equivalent: CDEMO-CUST-LNAME PIC X(25)
     * Customer's last/family name for identification and display
     */
    @JsonProperty("customerLastName")
    @Size(max = 25, message = "Customer last name must not exceed 25 characters")
    private String customerLastName;

    // =========================================================================
    // ACCOUNT INFO - Maps to CDEMO-ACCOUNT-INFO section
    // =========================================================================

    /**
     * Account ID - COBOL equivalent: CDEMO-ACCT-ID PIC 9(11)
     * 11-digit account identifier for account management operations
     */
    @JsonProperty("accountId")
    @Pattern(regexp = "\\d{0,11}", message = "Account ID must be numeric up to 11 digits")
    private String accountId;

    /**
     * Account status - COBOL equivalent: CDEMO-ACCT-STATUS PIC X(01)
     * Single character indicating the current status of the account
     */
    @JsonProperty("accountStatus")
    @Size(max = 1, message = "Account status must not exceed 1 character")
    private String accountStatus;

    // =========================================================================
    // CARD INFO - Maps to CDEMO-CARD-INFO section
    // =========================================================================

    /**
     * Card number - COBOL equivalent: CDEMO-CARD-NUM PIC 9(16)
     * 16-digit credit card number for card management operations
     */
    @JsonProperty("cardNumber")
    @Pattern(regexp = "\\d{0,16}", message = "Card number must be numeric up to 16 digits")
    private String cardNumber;

    // =========================================================================
    // NAVIGATION INFO - Maps to CDEMO-MORE-INFO section
    // =========================================================================

    /**
     * Last displayed map name - COBOL equivalent: CDEMO-LAST-MAP PIC X(7)
     * BMS map name for navigation state tracking
     */
    @JsonProperty("lastMapName")
    @Size(max = 7, message = "Last map name must not exceed 7 characters")
    private String lastMapName;

    /**
     * Last displayed mapset name - COBOL equivalent: CDEMO-LAST-MAPSET PIC X(7)
     * BMS mapset name for screen flow management
     */
    @JsonProperty("lastMapsetName")
    @Size(max = 7, message = "Last mapset name must not exceed 7 characters")
    private String lastMapsetName;

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /**
     * Default constructor for JSON deserialization and Spring instantiation
     */
    public SessionData() {
        // Initialize with default values matching COBOL low-values
        this.programContext = "0"; // Default to first entry
    }

    /**
     * Constructor for creating session data with user context
     *
     * @param userId The authenticated user ID
     * @param userType The user type ('A' for Admin, 'U' for User)
     */
    public SessionData(String userId, String userType) {
        this();
        this.userId = userId;
        this.userType = userType;
    }

    // =========================================================================
    // GETTER AND SETTER METHODS
    // =========================================================================

    public String getFromTransactionId() {
        return fromTransactionId;
    }

    public void setFromTransactionId(String fromTransactionId) {
        this.fromTransactionId = fromTransactionId;
    }

    public String getFromProgram() {
        return fromProgram;
    }

    public void setFromProgram(String fromProgram) {
        this.fromProgram = fromProgram;
    }

    public String getToTransactionId() {
        return toTransactionId;
    }

    public void setToTransactionId(String toTransactionId) {
        this.toTransactionId = toTransactionId;
    }

    public String getToProgram() {
        return toProgram;
    }

    public void setToProgram(String toProgram) {
        this.toProgram = toProgram;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getProgramContext() {
        return programContext;
    }

    public void setProgramContext(String programContext) {
        this.programContext = programContext;
    }

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

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public String getLastMapName() {
        return lastMapName;
    }

    public void setLastMapName(String lastMapName) {
        this.lastMapName = lastMapName;
    }

    public String getLastMapsetName() {
        return lastMapsetName;
    }

    public void setLastMapsetName(String lastMapsetName) {
        this.lastMapsetName = lastMapsetName;
    }

    // =========================================================================
    // COBOL 88-LEVEL CONDITION HELPER METHODS
    // =========================================================================

    /**
     * Checks if user type is Admin - COBOL equivalent: CDEMO-USRTYP-ADMIN VALUE 'A'
     *
     * @return true if user type is 'A' (Admin), false otherwise
     */
    public boolean isAdminUser() {
        return "A".equals(this.userType);
    }

    /**
     * Checks if user type is Regular User - COBOL equivalent: CDEMO-USRTYP-USER VALUE 'U'
     *
     * @return true if user type is 'U' (User), false otherwise
     */
    public boolean isRegularUser() {
        return "U".equals(this.userType);
    }

    /**
     * Checks if program context is first entry - COBOL equivalent: CDEMO-PGM-ENTER VALUE 0
     *
     * @return true if program context is '0' (Enter), false otherwise
     */
    public boolean isProgramEnter() {
        return "0".equals(this.programContext);
    }

    /**
     * Checks if program context is re-entry - COBOL equivalent: CDEMO-PGM-REENTER VALUE 1
     *
     * @return true if program context is '1' (Re-enter), false otherwise
     */
    public boolean isProgramReenter() {
        return "1".equals(this.programContext);
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Sets the program context to first entry state
     * COBOL equivalent: MOVE 0 TO CDEMO-PGM-CONTEXT
     */
    public void setProgramEnter() {
        this.programContext = "0";
    }

    /**
     * Sets the program context to re-entry state
     * COBOL equivalent: MOVE 1 TO CDEMO-PGM-CONTEXT
     */
    public void setProgramReenter() {
        this.programContext = "1";
    }

    /**
     * Gets the full customer name by concatenating first, middle, and last names
     * Replicates COBOL string concatenation logic for display purposes
     *
     * @return Formatted full customer name or empty string if no names are set
     */
    public String getFullCustomerName() {
        StringBuilder fullName = new StringBuilder();
        
        if (customerFirstName != null && !customerFirstName.trim().isEmpty()) {
            fullName.append(customerFirstName.trim());
        }
        
        if (customerMiddleName != null && !customerMiddleName.trim().isEmpty()) {
            if (fullName.length() > 0) {
                fullName.append(" ");
            }
            fullName.append(customerMiddleName.trim());
        }
        
        if (customerLastName != null && !customerLastName.trim().isEmpty()) {
            if (fullName.length() > 0) {
                fullName.append(" ");
            }
            fullName.append(customerLastName.trim());
        }
        
        return fullName.toString();
    }

    /**
     * Clears all customer-related information from the session
     * COBOL equivalent: INITIALIZE CDEMO-CUSTOMER-INFO
     */
    public void clearCustomerInfo() {
        this.customerId = null;
        this.customerFirstName = null;
        this.customerMiddleName = null;
        this.customerLastName = null;
    }

    /**
     * Clears all account-related information from the session
     * COBOL equivalent: INITIALIZE CDEMO-ACCOUNT-INFO
     */
    public void clearAccountInfo() {
        this.accountId = null;
        this.accountStatus = null;
    }

    /**
     * Clears all card-related information from the session
     * COBOL equivalent: INITIALIZE CDEMO-CARD-INFO
     */
    public void clearCardInfo() {
        this.cardNumber = null;
    }

    /**
     * Clears navigation state information
     * COBOL equivalent: INITIALIZE CDEMO-MORE-INFO
     */
    public void clearNavigationInfo() {
        this.lastMapName = null;
        this.lastMapsetName = null;
    }

    /**
     * Resets the entire session data to initial state
     * COBOL equivalent: INITIALIZE CARDDEMO-COMMAREA
     */
    public void clearAll() {
        this.fromTransactionId = null;
        this.fromProgram = null;
        this.toTransactionId = null;
        this.toProgram = null;
        this.userId = null;
        this.userType = null;
        this.programContext = "0";
        clearCustomerInfo();
        clearAccountInfo();
        clearCardInfo();
        clearNavigationInfo();
    }

    // =========================================================================
    // OBJECT METHODS
    // =========================================================================

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        SessionData that = (SessionData) obj;
        return Objects.equals(fromTransactionId, that.fromTransactionId) &&
               Objects.equals(fromProgram, that.fromProgram) &&
               Objects.equals(toTransactionId, that.toTransactionId) &&
               Objects.equals(toProgram, that.toProgram) &&
               Objects.equals(userId, that.userId) &&
               Objects.equals(userType, that.userType) &&
               Objects.equals(programContext, that.programContext) &&
               Objects.equals(customerId, that.customerId) &&
               Objects.equals(customerFirstName, that.customerFirstName) &&
               Objects.equals(customerMiddleName, that.customerMiddleName) &&
               Objects.equals(customerLastName, that.customerLastName) &&
               Objects.equals(accountId, that.accountId) &&
               Objects.equals(accountStatus, that.accountStatus) &&
               Objects.equals(cardNumber, that.cardNumber) &&
               Objects.equals(lastMapName, that.lastMapName) &&
               Objects.equals(lastMapsetName, that.lastMapsetName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromTransactionId, fromProgram, toTransactionId, toProgram,
                          userId, userType, programContext, customerId, customerFirstName,
                          customerMiddleName, customerLastName, accountId, accountStatus,
                          cardNumber, lastMapName, lastMapsetName);
    }

    @Override
    public String toString() {
        return "SessionData{" +
               "fromTransactionId='" + fromTransactionId + '\'' +
               ", fromProgram='" + fromProgram + '\'' +
               ", toTransactionId='" + toTransactionId + '\'' +
               ", toProgram='" + toProgram + '\'' +
               ", userId='" + userId + '\'' +
               ", userType='" + userType + '\'' +
               ", programContext='" + programContext + '\'' +
               ", customerId='" + customerId + '\'' +
               ", customerFirstName='" + customerFirstName + '\'' +
               ", customerMiddleName='" + customerMiddleName + '\'' +
               ", customerLastName='" + customerLastName + '\'' +
               ", accountId='" + accountId + '\'' +
               ", accountStatus='" + accountStatus + '\'' +
               ", cardNumber='" + cardNumber + '\'' +
               ", lastMapName='" + lastMapName + '\'' +
               ", lastMapsetName='" + lastMapsetName + '\'' +
               '}';
    }
}
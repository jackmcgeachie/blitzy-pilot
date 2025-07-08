/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 *
 * PaymentRequest.java
 * 
 * Data transfer object representing payment request input data with comprehensive 
 * validation annotations, maintaining field structure and constraints equivalent 
 * to COBOL program COBIL00C input processing with JSON serialization support.
 * 
 * This DTO corresponds to the COBOL program COBIL00C.cbl input fields:
 * - ACTIDINI: Account ID (PIC 9(11)) -> accountId
 * - CONFIRMI: Confirmation flag (Y/N) -> paymentConfirmation  
 * - Payment amount derived from account balance (PIC S9(10)V99) -> paymentAmount
 */

package com.carddemo.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.Objects;

/**
 * Payment Request DTO for bill payment functionality.
 * 
 * Maps to COBOL program COBIL00C input fields with equivalent validation:
 * - Account ID validation matching COBOL PIC 9(11) specification
 * - Payment confirmation flag matching COBOL Y/N validation logic
 * - Payment amount with BigDecimal precision matching COBOL COMP-3 fields
 * 
 * Supports JSON serialization for REST API request processing and includes
 * comprehensive Bean Validation annotations for field constraint checking.
 */
public class PaymentRequest {
    
    /**
     * Account ID field corresponding to COBOL ACTIDINI field (PIC 9(11)).
     * Must be exactly 11 digits matching COBOL specification.
     */
    @JsonProperty("accountId")
    @NotNull(message = "Account ID cannot be null")
    @NotBlank(message = "Account ID cannot be empty")
    @Pattern(regexp = "^\\d{11}$", message = "Account ID must be exactly 11 digits")
    @Size(min = 11, max = 11, message = "Account ID must be exactly 11 digits")
    private String accountId;
    
    /**
     * Payment confirmation flag corresponding to COBOL CONFIRMI field.
     * Accepts Y/N values matching COBOL validation logic (case-insensitive).
     */
    @JsonProperty("paymentConfirmation")
    @NotNull(message = "Payment confirmation cannot be null")
    @NotBlank(message = "Payment confirmation cannot be empty")
    @Pattern(regexp = "^[YyNn]$", message = "Payment confirmation must be Y or N (case-insensitive)")
    @Size(min = 1, max = 1, message = "Payment confirmation must be exactly 1 character")
    private String paymentConfirmation;
    
    /**
     * Payment amount with BigDecimal precision matching COBOL PIC S9(10)V99.
     * Ensures monetary calculations maintain exact precision without floating point errors.
     * Must be positive value greater than zero for valid payment processing.
     */
    @JsonProperty("paymentAmount")
    @NotNull(message = "Payment amount cannot be null")
    @DecimalMin(value = "0.01", message = "Payment amount must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Payment amount must have at most 10 integer digits and 2 decimal places")
    private BigDecimal paymentAmount;
    
    /**
     * Default constructor for JSON deserialization.
     */
    public PaymentRequest() {
        // Default constructor
    }
    
    /**
     * Full constructor for creating PaymentRequest with all fields.
     * 
     * @param accountId Account ID (11 digits)
     * @param paymentConfirmation Payment confirmation flag (Y/N)
     * @param paymentAmount Payment amount (positive BigDecimal)
     */
    public PaymentRequest(String accountId, String paymentConfirmation, BigDecimal paymentAmount) {
        this.accountId = accountId;
        this.paymentConfirmation = paymentConfirmation;
        this.paymentAmount = paymentAmount;
    }
    
    /**
     * Gets the account ID.
     * 
     * @return Account ID as 11-digit string
     */
    public String getAccountId() {
        return accountId;
    }
    
    /**
     * Sets the account ID.
     * 
     * @param accountId Account ID (must be 11 digits)
     */
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }
    
    /**
     * Gets the payment confirmation flag.
     * 
     * @return Payment confirmation flag (Y/N)
     */
    public String getPaymentConfirmation() {
        return paymentConfirmation;
    }
    
    /**
     * Sets the payment confirmation flag.
     * 
     * @param paymentConfirmation Payment confirmation flag (Y/N)
     */
    public void setPaymentConfirmation(String paymentConfirmation) {
        this.paymentConfirmation = paymentConfirmation;
    }
    
    /**
     * Gets the payment amount.
     * 
     * @return Payment amount as BigDecimal
     */
    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }
    
    /**
     * Sets the payment amount.
     * 
     * @param paymentAmount Payment amount (must be positive)
     */
    public void setPaymentAmount(BigDecimal paymentAmount) {
        this.paymentAmount = paymentAmount;
    }
    
    /**
     * Creates a new PaymentRequest builder instance.
     * 
     * @return New PaymentRequestBuilder instance
     */
    public static PaymentRequestBuilder builder() {
        return new PaymentRequestBuilder();
    }
    
    /**
     * Converts payment confirmation to boolean for business logic processing.
     * Matches COBOL CONF-PAY-YES/CONF-PAY-NO 88-level conditions.
     * 
     * @return true if confirmation is Y/y, false if N/n
     */
    public boolean isConfirmationYes() {
        return paymentConfirmation != null && 
               (paymentConfirmation.equalsIgnoreCase("Y") || paymentConfirmation.equalsIgnoreCase("y"));
    }
    
    /**
     * Validates account ID format using COBOL PIC 9(11) specification.
     * 
     * @return true if account ID is valid 11-digit format
     */
    public boolean isAccountIdValid() {
        return accountId != null && accountId.matches("^\\d{11}$");
    }
    
    /**
     * Validates payment amount against COBOL business rules.
     * 
     * @return true if payment amount is positive and within valid range
     */
    public boolean isPaymentAmountValid() {
        return paymentAmount != null && 
               paymentAmount.compareTo(BigDecimal.ZERO) > 0 &&
               paymentAmount.scale() <= 2;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentRequest that = (PaymentRequest) o;
        return Objects.equals(accountId, that.accountId) &&
               Objects.equals(paymentConfirmation, that.paymentConfirmation) &&
               Objects.equals(paymentAmount, that.paymentAmount);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(accountId, paymentConfirmation, paymentAmount);
    }
    
    @Override
    public String toString() {
        return "PaymentRequest{" +
               "accountId='" + accountId + '\'' +
               ", paymentConfirmation='" + paymentConfirmation + '\'' +
               ", paymentAmount=" + paymentAmount +
               '}';
    }
    
    /**
     * Builder class for PaymentRequest following the Builder pattern.
     * Provides fluent API for constructing PaymentRequest objects.
     */
    public static class PaymentRequestBuilder {
        private String accountId;
        private String paymentConfirmation;
        private BigDecimal paymentAmount;
        
        private PaymentRequestBuilder() {
            // Private constructor to ensure builder() method usage
        }
        
        /**
         * Sets the account ID.
         * 
         * @param accountId Account ID (11 digits)
         * @return Builder instance for method chaining
         */
        public PaymentRequestBuilder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }
        
        /**
         * Sets the payment confirmation flag.
         * 
         * @param paymentConfirmation Payment confirmation (Y/N)
         * @return Builder instance for method chaining
         */
        public PaymentRequestBuilder paymentConfirmation(String paymentConfirmation) {
            this.paymentConfirmation = paymentConfirmation;
            return this;
        }
        
        /**
         * Sets the payment amount.
         * 
         * @param paymentAmount Payment amount as BigDecimal
         * @return Builder instance for method chaining
         */
        public PaymentRequestBuilder paymentAmount(BigDecimal paymentAmount) {
            this.paymentAmount = paymentAmount;
            return this;
        }
        
        /**
         * Sets the payment amount from double value.
         * Converts to BigDecimal with 2 decimal places for monetary precision.
         * 
         * @param paymentAmount Payment amount as double
         * @return Builder instance for method chaining
         */
        public PaymentRequestBuilder paymentAmount(double paymentAmount) {
            this.paymentAmount = BigDecimal.valueOf(paymentAmount).setScale(2, BigDecimal.ROUND_HALF_UP);
            return this;
        }
        
        /**
         * Sets the payment amount from string value.
         * Parses string to BigDecimal with monetary precision.
         * 
         * @param paymentAmount Payment amount as string
         * @return Builder instance for method chaining
         */
        public PaymentRequestBuilder paymentAmount(String paymentAmount) {
            this.paymentAmount = new BigDecimal(paymentAmount).setScale(2, BigDecimal.ROUND_HALF_UP);
            return this;
        }
        
        /**
         * Builds the PaymentRequest instance.
         * 
         * @return New PaymentRequest instance with configured values
         */
        public PaymentRequest build() {
            return new PaymentRequest(accountId, paymentConfirmation, paymentAmount);
        }
    }
}
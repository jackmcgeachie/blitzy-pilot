/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.payment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.validation.constraints.Size;

/**
 * Payment Response Data Transfer Object
 * 
 * Represents payment processing response data from bill payment operations,
 * maintaining exact field structure and numeric precision equivalent to 
 * COBOL COBIL00C.cbl output data structure.
 * 
 * This DTO provides comprehensive payment processing status and confirmation
 * details for REST API responses with JSON serialization support, ensuring
 * backward compatibility with original COBOL transaction processing patterns.
 * 
 * Source COBOL Program: COBIL00C.cbl - Bill Payment Processing
 * Original Transaction: Creates TRAN-RECORD and updates ACCT-CURR-BAL
 * 
 * @author Blitzy agent - CardDemo Modernization Team
 * @version 1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {

    /**
     * Transaction ID generated for the payment processing request
     * Equivalent to TRAN-ID field from CVTRA05Y.cpy (PIC X(16))
     * Contains 16-character transaction reference number
     */
    @Size(max = 16, message = "Transaction ID must not exceed 16 characters")
    private String transactionId;

    /**
     * Payment processing status indicator
     * Possible values: SUCCESS, FAILURE, CONFIRMATION_REQUIRED
     * Derived from COBOL error handling and confirmation logic
     */
    @Size(max = 20, message = "Payment status must not exceed 20 characters")
    private String paymentStatus;

    /**
     * Processed payment amount with exact decimal precision
     * Equivalent to TRAN-AMT field from CVTRA05Y.cpy (PIC S9(09)V99)
     * Maintains COBOL COMP-3 numeric precision using BigDecimal
     */
    private BigDecimal paymentAmount;

    /**
     * Updated account balance after payment processing
     * Equivalent to ACCT-CURR-BAL after payment deduction
     * Maintains exact monetary precision for financial calculations
     */
    private BigDecimal updatedAccountBalance;

    /**
     * Payment processing timestamp
     * Equivalent to TRAN-PROC-TS field from CVTRA05Y.cpy (PIC X(26))
     * Replaces COBOL ASKTIME functionality with Java LocalDateTime
     */
    private LocalDateTime processingTimestamp;

    /**
     * Error or informational message for payment processing
     * Contains validation errors, processing errors, or success messages
     * Equivalent to WS-MESSAGE field from COBOL program
     */
    @Size(max = 200, message = "Error message must not exceed 200 characters")
    private String errorMessage;

    /**
     * Default constructor for Spring framework and JSON deserialization
     */
    public PaymentResponse() {
        // Default constructor required for JSON deserialization
    }

    /**
     * Constructor for successful payment response
     * 
     * @param transactionId Generated transaction ID
     * @param paymentAmount Processed payment amount
     * @param updatedAccountBalance Updated account balance after payment
     * @param processingTimestamp Payment processing timestamp
     */
    public PaymentResponse(String transactionId, BigDecimal paymentAmount, 
                          BigDecimal updatedAccountBalance, LocalDateTime processingTimestamp) {
        this.transactionId = transactionId;
        this.paymentStatus = "SUCCESS";
        this.paymentAmount = paymentAmount;
        this.updatedAccountBalance = updatedAccountBalance;
        this.processingTimestamp = processingTimestamp;
    }

    /**
     * Constructor for failed payment response
     * 
     * @param errorMessage Error message describing the failure
     * @param processingTimestamp Payment processing timestamp
     */
    public PaymentResponse(String errorMessage, LocalDateTime processingTimestamp) {
        this.paymentStatus = "FAILURE";
        this.errorMessage = errorMessage;
        this.processingTimestamp = processingTimestamp;
    }

    /**
     * Gets the transaction ID for the payment processing request
     * 
     * @return Transaction ID string (16 characters maximum)
     */
    public String getTransactionId() {
        return transactionId;
    }

    /**
     * Sets the transaction ID for the payment processing request
     * 
     * @param transactionId Transaction ID string (16 characters maximum)
     */
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    /**
     * Gets the payment processing status
     * 
     * @return Payment status (SUCCESS, FAILURE, or CONFIRMATION_REQUIRED)
     */
    public String getPaymentStatus() {
        return paymentStatus;
    }

    /**
     * Sets the payment processing status
     * 
     * @param paymentStatus Payment status (SUCCESS, FAILURE, or CONFIRMATION_REQUIRED)
     */
    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    /**
     * Gets the processed payment amount with exact decimal precision
     * 
     * @return Payment amount as BigDecimal maintaining COBOL COMP-3 precision
     */
    public BigDecimal getPaymentAmount() {
        return paymentAmount;
    }

    /**
     * Sets the processed payment amount with exact decimal precision
     * 
     * @param paymentAmount Payment amount as BigDecimal with 2 decimal places
     */
    public void setPaymentAmount(BigDecimal paymentAmount) {
        this.paymentAmount = paymentAmount != null ? 
            paymentAmount.setScale(2, BigDecimal.ROUND_HALF_UP) : null;
    }

    /**
     * Gets the updated account balance after payment processing
     * 
     * @return Updated account balance as BigDecimal
     */
    public BigDecimal getUpdatedAccountBalance() {
        return updatedAccountBalance;
    }

    /**
     * Sets the updated account balance after payment processing
     * 
     * @param updatedAccountBalance Updated account balance as BigDecimal with 2 decimal places
     */
    public void setUpdatedAccountBalance(BigDecimal updatedAccountBalance) {
        this.updatedAccountBalance = updatedAccountBalance != null ? 
            updatedAccountBalance.setScale(2, BigDecimal.ROUND_HALF_UP) : null;
    }

    /**
     * Gets the payment processing timestamp
     * 
     * @return Processing timestamp as LocalDateTime
     */
    public LocalDateTime getProcessingTimestamp() {
        return processingTimestamp;
    }

    /**
     * Sets the payment processing timestamp
     * 
     * @param processingTimestamp Processing timestamp as LocalDateTime
     */
    public void setProcessingTimestamp(LocalDateTime processingTimestamp) {
        this.processingTimestamp = processingTimestamp;
    }

    /**
     * Gets the error or informational message for payment processing
     * 
     * @return Error message string (200 characters maximum)
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Sets the error or informational message for payment processing
     * 
     * @param errorMessage Error message string (200 characters maximum)
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Determines if the payment processing was successful
     * 
     * @return true if payment status is SUCCESS, false otherwise
     */
    @JsonIgnore
    public boolean isSuccess() {
        return "SUCCESS".equals(paymentStatus);
    }

    /**
     * Creates a builder instance for constructing PaymentResponse objects
     * 
     * @return PaymentResponseBuilder instance
     */
    public static PaymentResponseBuilder builder() {
        return new PaymentResponseBuilder();
    }

    /**
     * Builder class for constructing PaymentResponse objects
     * Provides fluent API for setting response fields
     */
    public static class PaymentResponseBuilder {
        private String transactionId;
        private String paymentStatus;
        private BigDecimal paymentAmount;
        private BigDecimal updatedAccountBalance;
        private LocalDateTime processingTimestamp;
        private String errorMessage;

        /**
         * Sets the transaction ID for the payment response
         * 
         * @param transactionId Transaction ID string
         * @return PaymentResponseBuilder instance for method chaining
         */
        public PaymentResponseBuilder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        /**
         * Sets the payment status for the payment response
         * 
         * @param paymentStatus Payment status string
         * @return PaymentResponseBuilder instance for method chaining
         */
        public PaymentResponseBuilder paymentStatus(String paymentStatus) {
            this.paymentStatus = paymentStatus;
            return this;
        }

        /**
         * Sets the payment amount for the payment response
         * 
         * @param paymentAmount Payment amount as BigDecimal
         * @return PaymentResponseBuilder instance for method chaining
         */
        public PaymentResponseBuilder paymentAmount(BigDecimal paymentAmount) {
            this.paymentAmount = paymentAmount;
            return this;
        }

        /**
         * Sets the updated account balance for the payment response
         * 
         * @param updatedAccountBalance Updated account balance as BigDecimal
         * @return PaymentResponseBuilder instance for method chaining
         */
        public PaymentResponseBuilder updatedAccountBalance(BigDecimal updatedAccountBalance) {
            this.updatedAccountBalance = updatedAccountBalance;
            return this;
        }

        /**
         * Sets the processing timestamp for the payment response
         * 
         * @param processingTimestamp Processing timestamp as LocalDateTime
         * @return PaymentResponseBuilder instance for method chaining
         */
        public PaymentResponseBuilder processingTimestamp(LocalDateTime processingTimestamp) {
            this.processingTimestamp = processingTimestamp;
            return this;
        }

        /**
         * Sets the error message for the payment response
         * 
         * @param errorMessage Error message string
         * @return PaymentResponseBuilder instance for method chaining
         */
        public PaymentResponseBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        /**
         * Builds the PaymentResponse object with configured fields
         * 
         * @return Constructed PaymentResponse instance
         */
        public PaymentResponse build() {
            PaymentResponse response = new PaymentResponse();
            response.setTransactionId(this.transactionId);
            response.setPaymentStatus(this.paymentStatus);
            response.setPaymentAmount(this.paymentAmount);
            response.setUpdatedAccountBalance(this.updatedAccountBalance);
            response.setProcessingTimestamp(this.processingTimestamp);
            response.setErrorMessage(this.errorMessage);
            return response;
        }
    }

    /**
     * String representation of PaymentResponse for debugging and logging
     * 
     * @return String representation of the payment response
     */
    @Override
    public String toString() {
        return "PaymentResponse{" +
            "transactionId='" + transactionId + '\'' +
            ", paymentStatus='" + paymentStatus + '\'' +
            ", paymentAmount=" + paymentAmount +
            ", updatedAccountBalance=" + updatedAccountBalance +
            ", processingTimestamp=" + processingTimestamp +
            ", errorMessage='" + errorMessage + '\'' +
            '}';
    }

    /**
     * Equality comparison for PaymentResponse objects
     * 
     * @param obj Object to compare with
     * @return true if objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        PaymentResponse that = (PaymentResponse) obj;
        
        if (transactionId != null ? !transactionId.equals(that.transactionId) : that.transactionId != null) return false;
        if (paymentStatus != null ? !paymentStatus.equals(that.paymentStatus) : that.paymentStatus != null) return false;
        if (paymentAmount != null ? paymentAmount.compareTo(that.paymentAmount) != 0 : that.paymentAmount != null) return false;
        if (updatedAccountBalance != null ? updatedAccountBalance.compareTo(that.updatedAccountBalance) != 0 : that.updatedAccountBalance != null) return false;
        if (processingTimestamp != null ? !processingTimestamp.equals(that.processingTimestamp) : that.processingTimestamp != null) return false;
        return errorMessage != null ? errorMessage.equals(that.errorMessage) : that.errorMessage == null;
    }

    /**
     * Hash code generation for PaymentResponse objects
     * 
     * @return Hash code value
     */
    @Override
    public int hashCode() {
        int result = transactionId != null ? transactionId.hashCode() : 0;
        result = 31 * result + (paymentStatus != null ? paymentStatus.hashCode() : 0);
        result = 31 * result + (paymentAmount != null ? paymentAmount.hashCode() : 0);
        result = 31 * result + (updatedAccountBalance != null ? updatedAccountBalance.hashCode() : 0);
        result = 31 * result + (processingTimestamp != null ? processingTimestamp.hashCode() : 0);
        result = 31 * result + (errorMessage != null ? errorMessage.hashCode() : 0);
        return result;
    }
}
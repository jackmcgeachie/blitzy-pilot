/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.validation;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Centralized validation service providing COBOL-style field validation logic
 * with Java Bean Validation annotations. Implements validation rules for account numbers,
 * card numbers, monetary amounts, dates, and text fields matching exact COBOL PIC clause
 * specifications and 88-level conditions.
 * 
 * This service ensures consistent validation across all REST controllers and maintains
 * identical business validation logic from original COBOL programs.
 * 
 * Based on COBOL copybooks:
 * - COCOM01Y.cpy: Common communication area fields
 * - CVACT01Y.cpy: Account record structure
 * - CVCRD01Y.cpy: Card work areas and validation patterns
 * - CVTRA01Y.cpy: Transaction category balance record
 * - CSMSG01Y.cpy: Common validation messages
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 */
@Service
public class ValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);

    // COBOL PIC clause patterns converted to Java regex patterns
    private static final Pattern ACCOUNT_ID_PATTERN = Pattern.compile("^\\d{11}$"); // PIC 9(11)
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("^\\d{16}$"); // PIC 9(16)
    private static final Pattern CUSTOMER_ID_PATTERN = Pattern.compile("^\\d{9}$"); // PIC 9(09)
    private static final Pattern USER_ID_PATTERN = Pattern.compile("^[A-Z0-9]{1,8}$"); // PIC X(08)
    private static final Pattern TRANSACTION_ID_PATTERN = Pattern.compile("^\\d{16}$"); // PIC 9(16)
    private static final Pattern TRANSACTION_TYPE_PATTERN = Pattern.compile("^[A-Z0-9]{2}$"); // PIC X(02)
    private static final Pattern TRANSACTION_CATEGORY_PATTERN = Pattern.compile("^\\d{4}$"); // PIC 9(04)
    private static final Pattern DATE_YYYYMMDD_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$"); // PIC X(10)
    private static final Pattern ALPHA_NUMERIC_PATTERN = Pattern.compile("^[A-Za-z0-9\\s]*$");
    private static final Pattern ALPHA_ONLY_PATTERN = Pattern.compile("^[A-Za-z\\s]*$");
    private static final Pattern NUMERIC_ONLY_PATTERN = Pattern.compile("^\\d*$");

    // COBOL 88-level validation constants
    public static final String ACCOUNT_STATUS_ACTIVE = "A";
    public static final String ACCOUNT_STATUS_INACTIVE = "I";
    public static final String ACCOUNT_STATUS_CLOSED = "C";
    public static final String CARD_STATUS_ACTIVE = "A";
    public static final String CARD_STATUS_EXPIRED = "E";
    public static final String CARD_STATUS_BLOCKED = "B";
    public static final String USER_TYPE_ADMIN = "A";
    public static final String USER_TYPE_USER = "U";

    // COBOL COMP-3 field limits for monetary amounts (PIC S9(10)V99)
    public static final BigDecimal MAX_MONETARY_AMOUNT = new BigDecimal("99999999.99");
    public static final BigDecimal MIN_MONETARY_AMOUNT = new BigDecimal("-99999999.99");

    /**
     * Validates account ID according to COBOL PIC 9(11) specification.
     * 
     * @param accountId the account ID to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidAccountId(String accountId) {
        if (!StringUtils.hasText(accountId)) {
            logger.debug("Account ID validation failed: null or empty value");
            return false;
        }
        
        boolean valid = ACCOUNT_ID_PATTERN.matcher(accountId.trim()).matches();
        if (!valid) {
            logger.debug("Account ID validation failed: {} does not match PIC 9(11) pattern", accountId);
        }
        return valid;
    }

    /**
     * Validates card number according to COBOL PIC 9(16) specification with Luhn algorithm.
     * 
     * @param cardNumber the card number to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidCardNumber(String cardNumber) {
        if (!StringUtils.hasText(cardNumber)) {
            logger.debug("Card number validation failed: null or empty value");
            return false;
        }
        
        String trimmedCardNumber = cardNumber.trim();
        
        // Check PIC 9(16) pattern
        if (!CARD_NUMBER_PATTERN.matcher(trimmedCardNumber).matches()) {
            logger.debug("Card number validation failed: {} does not match PIC 9(16) pattern", cardNumber);
            return false;
        }
        
        // Luhn algorithm validation (as implemented in original COBOL logic)
        boolean luhnValid = isValidLuhnAlgorithm(trimmedCardNumber);
        if (!luhnValid) {
            logger.debug("Card number validation failed: {} failed Luhn algorithm check", cardNumber);
        }
        return luhnValid;
    }

    /**
     * Validates customer ID according to COBOL PIC 9(09) specification.
     * 
     * @param customerId the customer ID to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidCustomerId(String customerId) {
        if (!StringUtils.hasText(customerId)) {
            logger.debug("Customer ID validation failed: null or empty value");
            return false;
        }
        
        boolean valid = CUSTOMER_ID_PATTERN.matcher(customerId.trim()).matches();
        if (!valid) {
            logger.debug("Customer ID validation failed: {} does not match PIC 9(09) pattern", customerId);
        }
        return valid;
    }

    /**
     * Validates user ID according to COBOL PIC X(08) specification.
     * 
     * @param userId the user ID to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            logger.debug("User ID validation failed: null or empty value");
            return false;
        }
        
        String trimmedUserId = userId.trim().toUpperCase();
        boolean valid = USER_ID_PATTERN.matcher(trimmedUserId).matches() && trimmedUserId.length() <= 8;
        if (!valid) {
            logger.debug("User ID validation failed: {} does not match PIC X(08) pattern", userId);
        }
        return valid;
    }

    /**
     * Validates monetary amount according to COBOL PIC S9(10)V99 specification.
     * Ensures proper scale and precision for financial calculations.
     * 
     * @param amount the monetary amount to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidMonetaryAmount(BigDecimal amount) {
        if (amount == null) {
            logger.debug("Monetary amount validation failed: null value");
            return false;
        }
        
        // Check range according to COBOL COMP-3 PIC S9(10)V99 limits
        if (amount.compareTo(MAX_MONETARY_AMOUNT) > 0 || amount.compareTo(MIN_MONETARY_AMOUNT) < 0) {
            logger.debug("Monetary amount validation failed: {} exceeds PIC S9(10)V99 range", amount);
            return false;
        }
        
        // Check scale (must have exactly 2 decimal places for monetary amounts)
        if (amount.scale() > 2) {
            logger.debug("Monetary amount validation failed: {} has more than 2 decimal places", amount);
            return false;
        }
        
        return true;
    }

    /**
     * Validates transaction ID according to COBOL PIC 9(16) specification.
     * 
     * @param transactionId the transaction ID to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidTransactionId(String transactionId) {
        if (!StringUtils.hasText(transactionId)) {
            logger.debug("Transaction ID validation failed: null or empty value");
            return false;
        }
        
        boolean valid = TRANSACTION_ID_PATTERN.matcher(transactionId.trim()).matches();
        if (!valid) {
            logger.debug("Transaction ID validation failed: {} does not match PIC 9(16) pattern", transactionId);
        }
        return valid;
    }

    /**
     * Validates transaction type code according to COBOL PIC X(02) specification.
     * 
     * @param transactionType the transaction type to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidTransactionType(String transactionType) {
        if (!StringUtils.hasText(transactionType)) {
            logger.debug("Transaction type validation failed: null or empty value");
            return false;
        }
        
        boolean valid = TRANSACTION_TYPE_PATTERN.matcher(transactionType.trim().toUpperCase()).matches();
        if (!valid) {
            logger.debug("Transaction type validation failed: {} does not match PIC X(02) pattern", transactionType);
        }
        return valid;
    }

    /**
     * Validates transaction category code according to COBOL PIC 9(04) specification.
     * 
     * @param categoryCode the category code to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidTransactionCategory(String categoryCode) {
        if (!StringUtils.hasText(categoryCode)) {
            logger.debug("Transaction category validation failed: null or empty value");
            return false;
        }
        
        boolean valid = TRANSACTION_CATEGORY_PATTERN.matcher(categoryCode.trim()).matches();
        if (!valid) {
            logger.debug("Transaction category validation failed: {} does not match PIC 9(04) pattern", categoryCode);
        }
        return valid;
    }

    /**
     * Validates account status according to COBOL 88-level conditions.
     * Valid values: A (Active), I (Inactive), C (Closed)
     * 
     * @param status the account status to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidAccountStatus(String status) {
        if (!StringUtils.hasText(status)) {
            logger.debug("Account status validation failed: null or empty value");
            return false;
        }
        
        String upperStatus = status.trim().toUpperCase();
        boolean valid = ACCOUNT_STATUS_ACTIVE.equals(upperStatus) || 
                       ACCOUNT_STATUS_INACTIVE.equals(upperStatus) || 
                       ACCOUNT_STATUS_CLOSED.equals(upperStatus);
        
        if (!valid) {
            logger.debug("Account status validation failed: {} is not a valid status (A/I/C)", status);
        }
        return valid;
    }

    /**
     * Validates card status according to COBOL 88-level conditions.
     * Valid values: A (Active), E (Expired), B (Blocked)
     * 
     * @param status the card status to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidCardStatus(String status) {
        if (!StringUtils.hasText(status)) {
            logger.debug("Card status validation failed: null or empty value");
            return false;
        }
        
        String upperStatus = status.trim().toUpperCase();
        boolean valid = CARD_STATUS_ACTIVE.equals(upperStatus) || 
                       CARD_STATUS_EXPIRED.equals(upperStatus) || 
                       CARD_STATUS_BLOCKED.equals(upperStatus);
        
        if (!valid) {
            logger.debug("Card status validation failed: {} is not a valid status (A/E/B)", status);
        }
        return valid;
    }

    /**
     * Validates user type according to COBOL 88-level conditions.
     * Valid values: A (Admin), U (User)
     * 
     * @param userType the user type to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidUserType(String userType) {
        if (!StringUtils.hasText(userType)) {
            logger.debug("User type validation failed: null or empty value");
            return false;
        }
        
        String upperUserType = userType.trim().toUpperCase();
        boolean valid = USER_TYPE_ADMIN.equals(upperUserType) || USER_TYPE_USER.equals(upperUserType);
        
        if (!valid) {
            logger.debug("User type validation failed: {} is not a valid type (A/U)", userType);
        }
        return valid;
    }

    /**
     * Validates text field length according to COBOL PIC X(n) specification.
     * 
     * @param text the text to validate
     * @param maxLength the maximum length allowed
     * @return true if valid, false otherwise
     */
    public boolean isValidTextLength(String text, int maxLength) {
        if (text == null) {
            return true; // null is considered valid for optional fields
        }
        
        boolean valid = text.length() <= maxLength;
        if (!valid) {
            logger.debug("Text length validation failed: length {} exceeds maximum {}", text.length(), maxLength);
        }
        return valid;
    }

    /**
     * Validates required field (replicating BMS FSET=FSETMDT attribute patterns).
     * 
     * @param fieldValue the field value to validate
     * @param fieldName the field name for logging
     * @return true if not empty, false if empty or null
     */
    public boolean isRequiredFieldValid(String fieldValue, String fieldName) {
        boolean valid = StringUtils.hasText(fieldValue);
        if (!valid) {
            logger.debug("Required field validation failed: {} is null or empty", fieldName);
        }
        return valid;
    }

    /**
     * Validates date in YYYY-MM-DD format according to COBOL PIC X(10) specification.
     * 
     * @param date the date string to validate
     * @return true if valid format, false otherwise
     */
    public boolean isValidDateFormat(String date) {
        if (!StringUtils.hasText(date)) {
            logger.debug("Date validation failed: null or empty value");
            return false;
        }
        
        boolean valid = DATE_YYYYMMDD_PATTERN.matcher(date.trim()).matches();
        if (!valid) {
            logger.debug("Date validation failed: {} does not match YYYY-MM-DD pattern", date);
        }
        return valid;
    }

    /**
     * Normalizes monetary amount to proper scale for COBOL COMP-3 compatibility.
     * 
     * @param amount the amount to normalize
     * @return normalized BigDecimal with scale 2
     */
    public BigDecimal normalizeMonetaryAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Implements Luhn algorithm for card number validation (matches COBOL logic).
     * 
     * @param cardNumber the card number to validate
     * @return true if valid according to Luhn algorithm
     */
    private boolean isValidLuhnAlgorithm(String cardNumber) {
        int sum = 0;
        boolean alternate = false;
        
        // Process digits from right to left
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
        
        return (sum % 10) == 0;
    }

    /**
     * Validates alphanumeric field content.
     * 
     * @param text the text to validate
     * @return true if contains only alphanumeric characters and spaces
     */
    public boolean isValidAlphaNumeric(String text) {
        if (text == null) {
            return true; // null is valid for optional fields
        }
        
        boolean valid = ALPHA_NUMERIC_PATTERN.matcher(text).matches();
        if (!valid) {
            logger.debug("Alphanumeric validation failed: {} contains invalid characters", text);
        }
        return valid;
    }

    /**
     * Validates alphabetic field content.
     * 
     * @param text the text to validate
     * @return true if contains only alphabetic characters and spaces
     */
    public boolean isValidAlpha(String text) {
        if (text == null) {
            return true; // null is valid for optional fields
        }
        
        boolean valid = ALPHA_ONLY_PATTERN.matcher(text).matches();
        if (!valid) {
            logger.debug("Alpha validation failed: {} contains non-alphabetic characters", text);
        }
        return valid;
    }

    /**
     * Validates numeric field content.
     * 
     * @param text the text to validate
     * @return true if contains only numeric characters
     */
    public boolean isValidNumeric(String text) {
        if (text == null) {
            return true; // null is valid for optional fields
        }
        
        boolean valid = NUMERIC_ONLY_PATTERN.matcher(text).matches();
        if (!valid) {
            logger.debug("Numeric validation failed: {} contains non-numeric characters", text);
        }
        return valid;
    }

    // =========================================================================
    // Bean Validation Annotations - Custom Validators for Entity Fields
    // =========================================================================

    /**
     * Bean Validation annotation for COBOL PIC 9(11) account ID validation.
     */
    @Documented
    @Constraint(validatedBy = AccountIdValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidAccountId {
        String message() default "Account ID must be exactly 11 digits";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    /**
     * Bean Validation annotation for COBOL PIC 9(16) card number validation with Luhn algorithm.
     */
    @Documented
    @Constraint(validatedBy = CardNumberValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidCardNumber {
        String message() default "Card number must be exactly 16 digits and pass Luhn validation";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    /**
     * Bean Validation annotation for COBOL PIC 9(09) customer ID validation.
     */
    @Documented
    @Constraint(validatedBy = CustomerIdValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidCustomerId {
        String message() default "Customer ID must be exactly 9 digits";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    /**
     * Bean Validation annotation for COBOL PIC S9(10)V99 monetary amount validation.
     */
    @Documented
    @Constraint(validatedBy = MonetaryAmountValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidMonetaryAmount {
        String message() default "Monetary amount must be within valid range and have maximum 2 decimal places";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    /**
     * Bean Validation annotation for COBOL 88-level account status validation.
     */
    @Documented
    @Constraint(validatedBy = AccountStatusValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidAccountStatus {
        String message() default "Account status must be A (Active), I (Inactive), or C (Closed)";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    /**
     * Bean Validation annotation for COBOL 88-level card status validation.
     */
    @Documented
    @Constraint(validatedBy = CardStatusValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidCardStatus {
        String message() default "Card status must be A (Active), E (Expired), or B (Blocked)";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    /**
     * Bean Validation annotation for COBOL 88-level user type validation.
     */
    @Documented
    @Constraint(validatedBy = UserTypeValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValidUserType {
        String message() default "User type must be A (Admin) or U (User)";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    // =========================================================================
    // Validator Implementations
    // =========================================================================

    /**
     * Validator implementation for ValidAccountId annotation.
     */
    public static class AccountIdValidator implements ConstraintValidator<ValidAccountId, String> {
        private static final ValidationService validationService = new ValidationService();

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return validationService.isValidAccountId(value);
        }
    }

    /**
     * Validator implementation for ValidCardNumber annotation.
     */
    public static class CardNumberValidator implements ConstraintValidator<ValidCardNumber, String> {
        private static final ValidationService validationService = new ValidationService();

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return validationService.isValidCardNumber(value);
        }
    }

    /**
     * Validator implementation for ValidCustomerId annotation.
     */
    public static class CustomerIdValidator implements ConstraintValidator<ValidCustomerId, String> {
        private static final ValidationService validationService = new ValidationService();

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return validationService.isValidCustomerId(value);
        }
    }

    /**
     * Validator implementation for ValidMonetaryAmount annotation.
     */
    public static class MonetaryAmountValidator implements ConstraintValidator<ValidMonetaryAmount, BigDecimal> {
        private static final ValidationService validationService = new ValidationService();

        @Override
        public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
            return validationService.isValidMonetaryAmount(value);
        }
    }

    /**
     * Validator implementation for ValidAccountStatus annotation.
     */
    public static class AccountStatusValidator implements ConstraintValidator<ValidAccountStatus, String> {
        private static final ValidationService validationService = new ValidationService();

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return validationService.isValidAccountStatus(value);
        }
    }

    /**
     * Validator implementation for ValidCardStatus annotation.
     */
    public static class CardStatusValidator implements ConstraintValidator<ValidCardStatus, String> {
        private static final ValidationService validationService = new ValidationService();

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return validationService.isValidCardStatus(value);
        }
    }

    /**
     * Validator implementation for ValidUserType annotation.
     */
    public static class UserTypeValidator implements ConstraintValidator<ValidUserType, String> {
        private static final ValidationService validationService = new ValidationService();

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return validationService.isValidUserType(value);
        }
    }
}
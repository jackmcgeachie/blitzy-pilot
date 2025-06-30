package com.carddemo.validation;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.regex.Pattern;

/**
 * Numeric precision management service ensuring COBOL-equivalent arithmetic precision
 * through BigDecimal conversions. Handles COMP-3 field mappings, enforces currency
 * precision with 2-decimal arithmetic, and provides safe numeric conversions matching
 * COBOL PIC clause specifications.
 * 
 * This service is critical for maintaining identical calculation results in monetary
 * transactions and account balance operations during the migration from IBM mainframe
 * COBOL/CICS/VSAM to Java/Spring Boot/PostgreSQL architecture.
 * 
 * Implements precise mapping for common COBOL numeric patterns:
 * - PIC 9(n): Unsigned integer fields → Long/Integer
 * - PIC S9(n): Signed integer fields → Long/Integer  
 * - PIC S9(n)V99: Signed decimal with 2 places → BigDecimal(scale=2)
 * - PIC S9(n)V9(m): Signed decimal with m places → BigDecimal(scale=m)
 * - COMP-3: Packed decimal → BigDecimal with explicit precision
 * 
 * @author Blitzy agent
 * @version 1.0
 * @since 2024-01-01
 */
@Service
@Validated
public class NumericPrecisionService {

    // COBOL-equivalent rounding mode (ROUND-AWAY-FROM-ZERO)
    private static final RoundingMode COBOL_ROUNDING_MODE = RoundingMode.HALF_UP;
    
    // Currency precision constants matching COBOL monetary fields
    private static final int CURRENCY_SCALE = 2;
    private static final int CURRENCY_PRECISION = 12; // S9(10)V99 = 12 total digits
    
    // Interest rate precision constants
    private static final int INTEREST_RATE_SCALE = 2;
    private static final int INTEREST_RATE_PRECISION = 6; // S9(04)V99 = 6 total digits
    
    // Account/Customer ID precision constants
    private static final int ACCOUNT_ID_PRECISION = 11; // PIC 9(11)
    private static final int CUSTOMER_ID_PRECISION = 9; // PIC 9(09)
    private static final int CARD_CVV_PRECISION = 3; // PIC 9(03)
    private static final int CATEGORY_CODE_PRECISION = 4; // PIC 9(04)
    
    // Validation patterns for numeric fields
    private static final Pattern ACCOUNT_ID_PATTERN = Pattern.compile("^\\d{11}$");
    private static final Pattern CUSTOMER_ID_PATTERN = Pattern.compile("^\\d{9}$");
    private static final Pattern CARD_CVV_PATTERN = Pattern.compile("^\\d{3}$");
    private static final Pattern CATEGORY_CODE_PATTERN = Pattern.compile("^\\d{4}$");
    
    // Number formatters for COBOL-compatible display
    private final DecimalFormat currencyFormatter;
    private final DecimalFormat interestRateFormatter;
    private final NumberFormat integerFormatter;
    
    /**
     * Constructor initializing COBOL-compatible number formatters.
     */
    public NumericPrecisionService() {
        // Currency formatter: always 2 decimal places, no grouping (matches COBOL display)
        currencyFormatter = new DecimalFormat("0.00");
        currencyFormatter.setRoundingMode(COBOL_ROUNDING_MODE);
        currencyFormatter.setGroupingUsed(false);
        
        // Interest rate formatter: always 2 decimal places, no grouping
        interestRateFormatter = new DecimalFormat("0.00");
        interestRateFormatter.setRoundingMode(COBOL_ROUNDING_MODE);
        interestRateFormatter.setGroupingUsed(false);
        
        // Integer formatter: no decimal places, no grouping
        integerFormatter = NumberFormat.getIntegerInstance();
        integerFormatter.setGroupingUsed(false);
    }
    
    /**
     * Converts COBOL COMP-3 packed decimal to Java BigDecimal with explicit scale.
     * 
     * COMP-3 fields in COBOL store packed decimal data with sign information.
     * This method ensures exact precision mapping to BigDecimal.
     * 
     * @param comp3Value COMP-3 value as string representation
     * @param scale Number of decimal places (from COBOL PIC clause)
     * @param precision Total number of digits (from COBOL PIC clause)
     * @return BigDecimal with exact COBOL precision
     * @throws NumberFormatException if input is not valid numeric
     */
    public BigDecimal convertComp3ToDecimal(String comp3Value, int scale, int precision) {
        if (comp3Value == null || comp3Value.trim().isEmpty()) {
            return BigDecimal.ZERO.setScale(scale, COBOL_ROUNDING_MODE);
        }
        
        try {
            BigDecimal value = new BigDecimal(comp3Value.trim());
            
            // Validate precision doesn't exceed COBOL field definition
            if (value.precision() > precision) {
                throw new IllegalArgumentException(
                    String.format("Value precision %d exceeds COBOL field precision %d", 
                                  value.precision(), precision));
            }
            
            // Set scale to match COBOL PIC clause
            return value.setScale(scale, COBOL_ROUNDING_MODE);
            
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid COMP-3 numeric value: " + comp3Value);
        }
    }
    
    /**
     * Creates currency BigDecimal from string matching COBOL PIC S9(10)V99 pattern.
     * Used for account balances, credit limits, and monetary amounts.
     * 
     * Maps to COBOL fields:
     * - ACCT-CURR-BAL (PIC S9(10)V99)
     * - ACCT-CREDIT-LIMIT (PIC S9(10)V99)
     * - ACCT-CASH-CREDIT-LIMIT (PIC S9(10)V99)
     * - ACCT-CURR-CYC-CREDIT (PIC S9(10)V99)
     * - ACCT-CURR-CYC-DEBIT (PIC S9(10)V99)
     * - TRAN-CAT-BAL (PIC S9(09)V99)
     * 
     * @param value String representation of currency amount
     * @return BigDecimal with scale=2, COBOL-compatible rounding
     * @throws NumberFormatException if input is not valid currency
     */
    public BigDecimal createCurrencyAmount(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO.setScale(CURRENCY_SCALE, COBOL_ROUNDING_MODE);
        }
        
        // Remove any formatting characters (commas, spaces)
        String cleanValue = value.trim().replaceAll("[, ]", "");
        
        try {
            BigDecimal amount = new BigDecimal(cleanValue);
            
            // Validate currency range (maximum S9(10)V99 = 99,999,999,999.99)
            BigDecimal maxCurrency = new BigDecimal("99999999999.99");
            BigDecimal minCurrency = new BigDecimal("-99999999999.99");
            
            if (amount.compareTo(maxCurrency) > 0 || amount.compareTo(minCurrency) < 0) {
                throw new IllegalArgumentException(
                    String.format("Currency amount %s exceeds COBOL field range [%s, %s]", 
                                  amount, minCurrency, maxCurrency));
            }
            
            return amount.setScale(CURRENCY_SCALE, COBOL_ROUNDING_MODE);
            
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid currency amount: " + value);
        }
    }
    
    /**
     * Creates interest rate BigDecimal from string matching COBOL PIC S9(04)V99 pattern.
     * Used for discount group interest rates.
     * 
     * Maps to COBOL field:
     * - DIS-INT-RATE (PIC S9(04)V99)
     * 
     * @param value String representation of interest rate
     * @return BigDecimal with scale=2, range validated for interest rates
     * @throws NumberFormatException if input is not valid interest rate
     */
    public BigDecimal createInterestRate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO.setScale(INTEREST_RATE_SCALE, COBOL_ROUNDING_MODE);
        }
        
        try {
            BigDecimal rate = new BigDecimal(value.trim());
            
            // Validate interest rate range (maximum S9(04)V99 = 9999.99%)
            BigDecimal maxRate = new BigDecimal("9999.99");
            BigDecimal minRate = new BigDecimal("-9999.99");
            
            if (rate.compareTo(maxRate) > 0 || rate.compareTo(minRate) < 0) {
                throw new IllegalArgumentException(
                    String.format("Interest rate %s exceeds COBOL field range [%s, %s]", 
                                  rate, minRate, maxRate));
            }
            
            return rate.setScale(INTEREST_RATE_SCALE, COBOL_ROUNDING_MODE);
            
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid interest rate: " + value);
        }
    }
    
    /**
     * Validates and converts account ID string to Long matching COBOL PIC 9(11) pattern.
     * 
     * Maps to COBOL fields:
     * - ACCT-ID (PIC 9(11))
     * - CARD-ACCT-ID (PIC 9(11))
     * - XREF-ACCT-ID (PIC 9(11))
     * - TRANCAT-ACCT-ID (PIC 9(11))
     * 
     * @param value String representation of account ID
     * @return Long value of account ID
     * @throws NumberFormatException if input is not valid 11-digit account ID
     */
    public Long createAccountId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Account ID cannot be null or empty");
        }
        
        String cleanValue = value.trim();
        
        if (!ACCOUNT_ID_PATTERN.matcher(cleanValue).matches()) {
            throw new NumberFormatException(
                String.format("Account ID '%s' must be exactly 11 digits", cleanValue));
        }
        
        try {
            return Long.parseLong(cleanValue);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid account ID format: " + cleanValue);
        }
    }
    
    /**
     * Validates and converts customer ID string to Integer matching COBOL PIC 9(09) pattern.
     * 
     * Maps to COBOL fields:
     * - CUST-ID (PIC 9(09))
     * - CUST-SSN (PIC 9(09))
     * - XREF-CUST-ID (PIC 9(09))
     * 
     * @param value String representation of customer ID
     * @return Integer value of customer ID
     * @throws NumberFormatException if input is not valid 9-digit customer ID
     */
    public Integer createCustomerId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        
        String cleanValue = value.trim();
        
        if (!CUSTOMER_ID_PATTERN.matcher(cleanValue).matches()) {
            throw new NumberFormatException(
                String.format("Customer ID '%s' must be exactly 9 digits", cleanValue));
        }
        
        try {
            return Integer.parseInt(cleanValue);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid customer ID format: " + cleanValue);
        }
    }
    
    /**
     * Validates and converts card CVV string to Integer matching COBOL PIC 9(03) pattern.
     * 
     * Maps to COBOL field:
     * - CARD-CVV-CD (PIC 9(03))
     * 
     * @param value String representation of CVV code
     * @return Integer value of CVV code
     * @throws NumberFormatException if input is not valid 3-digit CVV
     */
    public Integer createCardCvv(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("CVV code cannot be null or empty");
        }
        
        String cleanValue = value.trim();
        
        if (!CARD_CVV_PATTERN.matcher(cleanValue).matches()) {
            throw new NumberFormatException(
                String.format("CVV code '%s' must be exactly 3 digits", cleanValue));
        }
        
        try {
            return Integer.parseInt(cleanValue);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid CVV code format: " + cleanValue);
        }
    }
    
    /**
     * Validates and converts category code string to Integer matching COBOL PIC 9(04) pattern.
     * 
     * Maps to COBOL fields:
     * - TRANCAT-CD (PIC 9(04))
     * - DIS-TRAN-CAT-CD (PIC 9(04))
     * 
     * @param value String representation of category code
     * @return Integer value of category code
     * @throws NumberFormatException if input is not valid 4-digit category code
     */
    public Integer createCategoryCode(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Category code cannot be null or empty");
        }
        
        String cleanValue = value.trim();
        
        if (!CATEGORY_CODE_PATTERN.matcher(cleanValue).matches()) {
            throw new NumberFormatException(
                String.format("Category code '%s' must be exactly 4 digits", cleanValue));
        }
        
        try {
            return Integer.parseInt(cleanValue);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid category code format: " + cleanValue);
        }
    }
    
    /**
     * Performs COBOL-compatible currency arithmetic addition with proper scale handling.
     * Ensures results maintain currency precision and rounding rules.
     * 
     * @param amount1 First currency amount
     * @param amount2 Second currency amount
     * @return Sum with currency scale and COBOL rounding
     */
    public BigDecimal addCurrencyAmounts(BigDecimal amount1, BigDecimal amount2) {
        if (amount1 == null) amount1 = BigDecimal.ZERO;
        if (amount2 == null) amount2 = BigDecimal.ZERO;
        
        return amount1.add(amount2).setScale(CURRENCY_SCALE, COBOL_ROUNDING_MODE);
    }
    
    /**
     * Performs COBOL-compatible currency arithmetic subtraction with proper scale handling.
     * 
     * @param amount1 Minuend (amount to subtract from)
     * @param amount2 Subtrahend (amount to subtract)
     * @return Difference with currency scale and COBOL rounding
     */
    public BigDecimal subtractCurrencyAmounts(BigDecimal amount1, BigDecimal amount2) {
        if (amount1 == null) amount1 = BigDecimal.ZERO;
        if (amount2 == null) amount2 = BigDecimal.ZERO;
        
        return amount1.subtract(amount2).setScale(CURRENCY_SCALE, COBOL_ROUNDING_MODE);
    }
    
    /**
     * Performs COBOL-compatible currency multiplication with proper scale handling.
     * Used for interest calculations and fee computations.
     * 
     * @param amount Base currency amount
     * @param multiplier Multiplication factor
     * @return Product with currency scale and COBOL rounding
     */
    public BigDecimal multiplyCurrencyAmount(BigDecimal amount, BigDecimal multiplier) {
        if (amount == null) amount = BigDecimal.ZERO;
        if (multiplier == null) multiplier = BigDecimal.ONE;
        
        return amount.multiply(multiplier).setScale(CURRENCY_SCALE, COBOL_ROUNDING_MODE);
    }
    
    /**
     * Performs COBOL-compatible currency division with proper scale handling.
     * Protects against division by zero with appropriate exception.
     * 
     * @param dividend Currency amount to divide
     * @param divisor Division factor
     * @return Quotient with currency scale and COBOL rounding
     * @throws ArithmeticException if divisor is zero
     */
    public BigDecimal divideCurrencyAmount(BigDecimal dividend, BigDecimal divisor) {
        if (dividend == null) dividend = BigDecimal.ZERO;
        if (divisor == null || divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Cannot divide by zero in currency calculation");
        }
        
        return dividend.divide(divisor, CURRENCY_SCALE, COBOL_ROUNDING_MODE);
    }
    
    /**
     * Validates numeric field precision against COBOL PIC clause specifications.
     * Ensures the value doesn't exceed the defined precision and scale.
     * 
     * @param value BigDecimal value to validate
     * @param totalPrecision Total number of digits allowed (from PIC clause)
     * @param scale Number of decimal places allowed (from PIC clause)
     * @return true if value fits within COBOL field definition
     */
    public boolean validateNumericPrecision(BigDecimal value, int totalPrecision, int scale) {
        if (value == null) {
            return true; // Null values are handled elsewhere
        }
        
        // Check if value has more decimal places than allowed
        if (value.scale() > scale) {
            return false;
        }
        
        // Check if total precision exceeds COBOL field definition
        if (value.precision() > totalPrecision) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Validates account balance against COBOL field constraints.
     * Ensures the balance fits within PIC S9(10)V99 range.
     * 
     * @param balance Account balance to validate
     * @return true if balance is valid for COBOL account balance field
     */
    public boolean validateAccountBalance(BigDecimal balance) {
        return validateNumericPrecision(balance, CURRENCY_PRECISION, CURRENCY_SCALE);
    }
    
    /**
     * Validates credit limit against COBOL field constraints.
     * Ensures the limit fits within PIC S9(10)V99 range.
     * 
     * @param creditLimit Credit limit to validate
     * @return true if credit limit is valid for COBOL credit limit field
     */
    public boolean validateCreditLimit(BigDecimal creditLimit) {
        return validateNumericPrecision(creditLimit, CURRENCY_PRECISION, CURRENCY_SCALE);
    }
    
    /**
     * Validates transaction amount against COBOL field constraints.
     * Ensures the amount fits within PIC S9(09)V99 range.
     * 
     * @param transactionAmount Transaction amount to validate
     * @return true if amount is valid for COBOL transaction amount field
     */
    public boolean validateTransactionAmount(BigDecimal transactionAmount) {
        // Transaction amounts use S9(09)V99 (11 total digits, 2 decimal)
        return validateNumericPrecision(transactionAmount, 11, CURRENCY_SCALE);
    }
    
    /**
     * Validates interest rate against COBOL field constraints.
     * Ensures the rate fits within PIC S9(04)V99 range.
     * 
     * @param interestRate Interest rate to validate
     * @return true if rate is valid for COBOL interest rate field
     */
    public boolean validateInterestRate(BigDecimal interestRate) {
        return validateNumericPrecision(interestRate, INTEREST_RATE_PRECISION, INTEREST_RATE_SCALE);
    }
    
    /**
     * Formats currency amount for display matching COBOL output format.
     * Always shows 2 decimal places, no grouping separators.
     * 
     * @param amount Currency amount to format
     * @return Formatted string matching COBOL currency display
     */
    public String formatCurrencyForDisplay(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return currencyFormatter.format(amount);
    }
    
    /**
     * Formats interest rate for display matching COBOL output format.
     * Always shows 2 decimal places, no grouping separators.
     * 
     * @param rate Interest rate to format
     * @return Formatted string matching COBOL interest rate display
     */
    public String formatInterestRateForDisplay(BigDecimal rate) {
        if (rate == null) {
            return "0.00";
        }
        return interestRateFormatter.format(rate);
    }
    
    /**
     * Formats integer value for display matching COBOL output format.
     * No decimal places, no grouping separators.
     * 
     * @param value Integer value to format
     * @return Formatted string matching COBOL integer display
     */
    public String formatIntegerForDisplay(Long value) {
        if (value == null) {
            return "0";
        }
        return integerFormatter.format(value);
    }
    
    /**
     * Converts BigDecimal to string preserving exact precision for database storage.
     * Used when storing numeric values in PostgreSQL to maintain COBOL precision.
     * 
     * @param value BigDecimal value to convert
     * @return String representation with exact precision
     */
    public String convertToExactString(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.toPlainString();
    }
    
    /**
     * Creates BigDecimal from exact string representation for database retrieval.
     * Used when loading numeric values from PostgreSQL to maintain COBOL precision.
     * 
     * @param exactString String representation with exact precision
     * @return BigDecimal with preserved precision
     */
    public BigDecimal convertFromExactString(String exactString) {
        if (exactString == null || exactString.trim().isEmpty()) {
            return null;
        }
        return new BigDecimal(exactString.trim());
    }
    
    /**
     * Performs safe numeric comparison with COBOL-equivalent logic.
     * Handles null values and ensures consistent comparison results.
     * 
     * @param value1 First value to compare
     * @param value2 Second value to compare
     * @return Negative if value1 < value2, zero if equal, positive if value1 > value2
     */
    public int compareNumericValues(BigDecimal value1, BigDecimal value2) {
        if (value1 == null && value2 == null) {
            return 0;
        }
        if (value1 == null) {
            return BigDecimal.ZERO.compareTo(value2);
        }
        if (value2 == null) {
            return value1.compareTo(BigDecimal.ZERO);
        }
        return value1.compareTo(value2);
    }
    
    /**
     * Checks if currency amount is zero using COBOL-equivalent logic.
     * Handles null values and scale differences.
     * 
     * @param amount Currency amount to check
     * @return true if amount is zero or null
     */
    public boolean isCurrencyZero(BigDecimal amount) {
        return amount == null || amount.compareTo(BigDecimal.ZERO) == 0;
    }
    
    /**
     * Checks if currency amount is positive using COBOL-equivalent logic.
     * 
     * @param amount Currency amount to check
     * @return true if amount is greater than zero
     */
    public boolean isCurrencyPositive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * Checks if currency amount is negative using COBOL-equivalent logic.
     * 
     * @param amount Currency amount to check
     * @return true if amount is less than zero
     */
    public boolean isCurrencyNegative(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) < 0;
    }
    
    /**
     * Calculates absolute value of currency amount maintaining proper scale.
     * 
     * @param amount Currency amount
     * @return Absolute value with currency scale
     */
    public BigDecimal getCurrencyAbsoluteValue(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(CURRENCY_SCALE, COBOL_ROUNDING_MODE);
        }
        return amount.abs().setScale(CURRENCY_SCALE, COBOL_ROUNDING_MODE);
    }
    
    /**
     * Negates currency amount maintaining proper scale.
     * 
     * @param amount Currency amount to negate
     * @return Negated amount with currency scale
     */
    public BigDecimal negateCurrencyAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(CURRENCY_SCALE, COBOL_ROUNDING_MODE);
        }
        return amount.negate().setScale(CURRENCY_SCALE, COBOL_ROUNDING_MODE);
    }
    
    /**
     * Truncates currency amount to specified decimal places with COBOL-equivalent logic.
     * Uses truncation (not rounding) to match COBOL truncation behavior.
     * 
     * @param amount Currency amount to truncate
     * @param scale Number of decimal places to retain
     * @return Truncated amount
     */
    public BigDecimal truncateCurrencyAmount(BigDecimal amount, int scale) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.DOWN);
        }
        return amount.setScale(scale, RoundingMode.DOWN);
    }
    
    /**
     * Rounds currency amount to specified decimal places with COBOL-equivalent logic.
     * Uses COBOL-style rounding (ROUND-AWAY-FROM-ZERO).
     * 
     * @param amount Currency amount to round
     * @param scale Number of decimal places to round to
     * @return Rounded amount
     */
    public BigDecimal roundCurrencyAmount(BigDecimal amount, int scale) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(scale, COBOL_ROUNDING_MODE);
        }
        return amount.setScale(scale, COBOL_ROUNDING_MODE);
    }
    
    /**
     * Creates maximum currency value for COBOL PIC S9(10)V99 field.
     * Used for validation and boundary checking.
     * 
     * @return Maximum positive currency value
     */
    public BigDecimal getMaxCurrencyValue() {
        return new BigDecimal("99999999999.99");
    }
    
    /**
     * Creates minimum currency value for COBOL PIC S9(10)V99 field.
     * Used for validation and boundary checking.
     * 
     * @return Maximum negative currency value
     */
    public BigDecimal getMinCurrencyValue() {
        return new BigDecimal("-99999999999.99");
    }
    
    /**
     * Creates maximum transaction amount value for COBOL PIC S9(09)V99 field.
     * Used for validation and boundary checking.
     * 
     * @return Maximum positive transaction amount
     */
    public BigDecimal getMaxTransactionAmount() {
        return new BigDecimal("999999999.99");
    }
    
    /**
     * Creates minimum transaction amount value for COBOL PIC S9(09)V99 field.
     * Used for validation and boundary checking.
     * 
     * @return Maximum negative transaction amount
     */
    public BigDecimal getMinTransactionAmount() {
        return new BigDecimal("-999999999.99");
    }
    
    /**
     * Creates maximum interest rate value for COBOL PIC S9(04)V99 field.
     * Used for validation and boundary checking.
     * 
     * @return Maximum positive interest rate
     */
    public BigDecimal getMaxInterestRate() {
        return new BigDecimal("9999.99");
    }
    
    /**
     * Creates minimum interest rate value for COBOL PIC S9(04)V99 field.
     * Used for validation and boundary checking.
     * 
     * @return Maximum negative interest rate
     */
    public BigDecimal getMinInterestRate() {
        return new BigDecimal("-9999.99");
    }
    
    /**
     * Gets the currency scale constant (2 decimal places).
     * Used throughout the application for consistent currency handling.
     * 
     * @return Currency scale value
     */
    public int getCurrencyScale() {
        return CURRENCY_SCALE;
    }
    
    /**
     * Gets the currency precision constant (12 total digits).
     * Used throughout the application for consistent currency validation.
     * 
     * @return Currency precision value
     */
    public int getCurrencyPrecision() {
        return CURRENCY_PRECISION;
    }
    
    /**
     * Gets the COBOL-compatible rounding mode.
     * Used throughout the application for consistent rounding behavior.
     * 
     * @return COBOL rounding mode
     */
    public RoundingMode getCobolRoundingMode() {
        return COBOL_ROUNDING_MODE;
    }
}
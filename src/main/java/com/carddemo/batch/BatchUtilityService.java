/*
 * BatchUtilityService.java
 * 
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This service provides COBOL-compatible date handling, numeric precision conversion,
 * message formatting, field validation, and data transformation utilities for batch processing.
 * It centralizes common functionality and ensures consistent COBOL-to-Java behavior
 * across all batch jobs.
 * 
 * Based on source copybooks:
 * - CSDAT01Y.cpy: Date and time handling structures
 * - CSMSG01Y.cpy: Common message structures
 * - CSMSG02Y.cpy: Error handling structures
 */

package com.carddemo.batch;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common utility service providing COBOL-compatible date handling, numeric precision 
 * conversion, message formatting, field validation, and data transformation utilities.
 * Centralizes common batch processing functionality and ensures consistent COBOL-to-Java 
 * behavior across all batch jobs.
 */
@Service
public class BatchUtilityService {
    
    private static final Logger logger = LoggerFactory.getLogger(BatchUtilityService.class);
    
    // COBOL date format patterns (from CSDAT01Y copybook)
    private static final DateTimeFormatter COBOL_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter COBOL_TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter COBOL_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
    private static final DateTimeFormatter COBOL_SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yy");
    private static final DateTimeFormatter COBOL_TIME_DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    // Common message constants (from CSMSG01Y copybook)
    private static final String CCDA_MSG_THANK_YOU = "Thank you for using CardDemo application...      ";
    private static final String CCDA_MSG_INVALID_KEY = "Invalid key pressed. Please see below...         ";
    
    // Field validation patterns
    private static final Pattern ACCOUNT_NUMBER_PATTERN = Pattern.compile("^\\d{11}$");
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("^\\d{16}$");
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^\\d+$");
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    
    // Error code mapping for COBOL to Spring Boot exceptions
    private static final Map<String, String> COBOL_ERROR_MAP = new HashMap<>();
    
    static {
        // Initialize COBOL error code mappings (from CSMSG02Y.cpy patterns)
        COBOL_ERROR_MAP.put("0000", "SUCCESS");
        COBOL_ERROR_MAP.put("0001", "RECORD_NOT_FOUND");
        COBOL_ERROR_MAP.put("0002", "DUPLICATE_KEY");
        COBOL_ERROR_MAP.put("0003", "INVALID_INPUT");
        COBOL_ERROR_MAP.put("0004", "FILE_ERROR");
        COBOL_ERROR_MAP.put("0005", "SYSTEM_ERROR");
        COBOL_ERROR_MAP.put("0006", "INVALID_DATE");
        COBOL_ERROR_MAP.put("0007", "INVALID_AMOUNT");
        COBOL_ERROR_MAP.put("0008", "SECURITY_VIOLATION");
        COBOL_ERROR_MAP.put("0009", "TRANSACTION_LIMIT_EXCEEDED");
        COBOL_ERROR_MAP.put("0010", "INSUFFICIENT_FUNDS");
    }
    
    /**
     * Formats a LocalDate to COBOL date format (YYYYMMDD).
     * Replicates WS-CURDATE-N logic from CSDAT01Y copybook.
     * 
     * @param date The date to format
     * @return Formatted date string in YYYYMMDD format
     */
    public String formatCobolDate(LocalDate date) {
        if (date == null) {
            return "00000000";
        }
        return date.format(COBOL_DATE_FORMATTER);
    }
    
    /**
     * Parses a COBOL date string (YYYYMMDD) to LocalDate.
     * Handles WS-CURDATE structure from CSDAT01Y copybook.
     * 
     * @param dateStr The date string in YYYYMMDD format
     * @return LocalDate object
     */
    public LocalDate parseCobolDate(String dateStr) {
        if (StringUtils.isBlank(dateStr) || "00000000".equals(dateStr)) {
            return null;
        }
        
        try {
            // Validate format
            if (!dateStr.matches("\\d{8}")) {
                logger.warn("Invalid COBOL date format: {}", dateStr);
                return null;
            }
            
            return LocalDate.parse(dateStr, COBOL_DATE_FORMATTER);
        } catch (Exception e) {
            logger.error("Error parsing COBOL date: {}", dateStr, e);
            return null;
        }
    }
    
    /**
     * Gets current date in COBOL format (YYYYMMDD).
     * Replicates WS-CURDATE-N from CSDAT01Y copybook.
     * 
     * @return Current date in COBOL format
     */
    public String getCurrentCobolDate() {
        return formatCobolDate(LocalDate.now());
    }
    
    /**
     * Gets current time in COBOL format (HHMMSS).
     * Replicates WS-CURTIME-N from CSDAT01Y copybook.
     * 
     * @return Current time in COBOL format
     */
    public String getCurrentCobolTime() {
        return LocalDateTime.now().format(COBOL_TIME_FORMATTER);
    }
    
    /**
     * Formats a LocalDateTime to COBOL timestamp format.
     * Replicates WS-TIMESTAMP structure from CSDAT01Y copybook.
     * 
     * @param timestamp The timestamp to format
     * @return Formatted timestamp string
     */
    public String formatCobolTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            return "0000-00-00 00:00:00.000000";
        }
        return timestamp.format(COBOL_TIMESTAMP_FORMATTER);
    }
    
    /**
     * Parses a COBOL timestamp string to LocalDateTime.
     * Handles WS-TIMESTAMP structure from CSDAT01Y copybook.
     * 
     * @param timestampStr The timestamp string
     * @return LocalDateTime object
     */
    public LocalDateTime parseCobolTimestamp(String timestampStr) {
        if (StringUtils.isBlank(timestampStr) || "0000-00-00 00:00:00.000000".equals(timestampStr)) {
            return null;
        }
        
        try {
            return LocalDateTime.parse(timestampStr, COBOL_TIMESTAMP_FORMATTER);
        } catch (Exception e) {
            logger.error("Error parsing COBOL timestamp: {}", timestampStr, e);
            return null;
        }
    }
    
    /**
     * Converts a numeric value to COBOL COMP-3 equivalent BigDecimal.
     * Maintains exact precision for financial calculations.
     * 
     * @param value The numeric value
     * @param precision Total number of digits
     * @param scale Number of decimal places
     * @return BigDecimal with COBOL COMP-3 precision
     */
    public BigDecimal convertToComp3Decimal(String value, int precision, int scale) {
        if (StringUtils.isBlank(value)) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        
        try {
            BigDecimal decimal = new BigDecimal(value);
            return decimal.setScale(scale, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            logger.error("Error converting to COMP-3 decimal: {}", value, e);
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
    }
    
    /**
     * Converts a BigDecimal to COBOL COMP-3 string representation.
     * Maintains COBOL numeric field formatting.
     * 
     * @param decimal The BigDecimal value
     * @param precision Total number of digits
     * @param scale Number of decimal places
     * @return String representation of COMP-3 field
     */
    public String convertFromComp3Decimal(BigDecimal decimal, int precision, int scale) {
        if (decimal == null) {
            return "0".repeat(precision);
        }
        
        // Set scale and format with leading zeros
        BigDecimal scaledDecimal = decimal.setScale(scale, RoundingMode.HALF_UP);
        String formatted = scaledDecimal.toPlainString().replace(".", "");
        
        // Pad with leading zeros to match COBOL field size
        return StringUtils.leftPad(formatted, precision, '0');
    }
    
    /**
     * Validates a numeric field according to COBOL PIC 9(n) specifications.
     * Implements COBOL 88-level validation logic.
     * 
     * @param value The value to validate
     * @param length Expected field length
     * @return true if valid numeric field
     */
    public boolean validateNumericField(String value, int length) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        
        // Check length
        if (value.length() > length) {
            return false;
        }
        
        // Check if all digits
        return NUMERIC_PATTERN.matcher(value).matches();
    }
    
    /**
     * Validates an alphanumeric field according to COBOL PIC X(n) specifications.
     * Implements COBOL field validation patterns.
     * 
     * @param value The value to validate
     * @param length Expected field length
     * @return true if valid alphanumeric field
     */
    public boolean validateAlphanumericField(String value, int length) {
        if (value == null) {
            return false;
        }
        
        // Check length
        if (value.length() > length) {
            return false;
        }
        
        // COBOL X field can contain any characters
        return true;
    }
    
    /**
     * Pads a field to COBOL field specifications.
     * Implements COBOL field padding logic.
     * 
     * @param value The value to pad
     * @param length Target field length
     * @param leftPad true for left padding (numeric), false for right padding (alphanumeric)
     * @return Padded field value
     */
    public String padCobolField(String value, int length, boolean leftPad) {
        if (value == null) {
            value = "";
        }
        
        if (value.length() >= length) {
            return value.substring(0, length);
        }
        
        if (leftPad) {
            return StringUtils.leftPad(value, length, '0');
        } else {
            return StringUtils.rightPad(value, length, ' ');
        }
    }
    
    /**
     * Trims a COBOL field removing trailing spaces.
     * Implements COBOL field trimming logic.
     * 
     * @param value The value to trim
     * @return Trimmed field value
     */
    public String trimCobolField(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
    
    /**
     * Formats a message using COBOL message formatting patterns.
     * Based on CSMSG01Y copybook message structures.
     * 
     * @param messageCode The message code
     * @param parameters Optional parameters for message formatting
     * @return Formatted message string
     */
    public String formatMessage(String messageCode, Object... parameters) {
        String message = getCommonMessage(messageCode);
        
        if (parameters != null && parameters.length > 0) {
            try {
                message = String.format(message, parameters);
            } catch (Exception e) {
                logger.warn("Error formatting message {} with parameters", messageCode, e);
            }
        }
        
        return message;
    }
    
    /**
     * Retrieves common messages from CSMSG01Y copybook structures.
     * 
     * @param messageCode The message code to retrieve
     * @return Common message string
     */
    public String getCommonMessage(String messageCode) {
        switch (messageCode) {
            case "THANK_YOU":
                return CCDA_MSG_THANK_YOU;
            case "INVALID_KEY":
                return CCDA_MSG_INVALID_KEY;
            default:
                return "Unknown message code: " + messageCode;
        }
    }
    
    /**
     * Validates field length according to COBOL specifications.
     * 
     * @param value The value to validate
     * @param minLength Minimum field length
     * @param maxLength Maximum field length
     * @return true if field length is valid
     */
    public boolean validateFieldLength(String value, int minLength, int maxLength) {
        if (value == null) {
            return minLength == 0;
        }
        
        int length = value.length();
        return length >= minLength && length <= maxLength;
    }
    
    /**
     * Validates field format using regular expressions.
     * Implements COBOL 88-level validation conditions.
     * 
     * @param value The value to validate
     * @param pattern The validation pattern
     * @return true if field format is valid
     */
    public boolean validateFieldFormat(String value, String pattern) {
        if (StringUtils.isBlank(value) || StringUtils.isBlank(pattern)) {
            return false;
        }
        
        try {
            Pattern compiledPattern = Pattern.compile(pattern);
            return compiledPattern.matcher(value).matches();
        } catch (Exception e) {
            logger.error("Error validating field format with pattern: {}", pattern, e);
            return false;
        }
    }
    
    /**
     * Maps COBOL error codes to Spring Boot exception types.
     * Based on CSMSG02Y copybook error handling patterns.
     * 
     * @param cobolErrorCode The COBOL error code
     * @return Mapped error description
     */
    public String mapCobolErrorCode(String cobolErrorCode) {
        return COBOL_ERROR_MAP.getOrDefault(cobolErrorCode, "UNKNOWN_ERROR");
    }
    
    /**
     * Creates a Spring Boot exception from COBOL error information.
     * Implements COBOL ABEND-DATA structure handling.
     * 
     * @param errorCode The COBOL error code (ABEND-CODE)
     * @param culprit The program causing the error (ABEND-CULPRIT)
     * @param reason The error reason (ABEND-REASON)
     * @return RuntimeException with COBOL error details
     */
    public RuntimeException createSpringException(String errorCode, String culprit, String reason) {
        String mappedError = mapCobolErrorCode(errorCode);
        String message = String.format("COBOL Error [%s]: %s - Program: %s, Reason: %s", 
                                     errorCode, mappedError, culprit, reason);
        
        logger.error("Creating Spring exception for COBOL error: {}", message);
        
        // Return specific exception types based on error code
        switch (errorCode) {
            case "0001":
                return new IllegalStateException(message);
            case "0002":
                return new IllegalArgumentException(message);
            case "0003":
                return new IllegalArgumentException(message);
            case "0004":
                return new RuntimeException(message);
            case "0008":
                return new SecurityException(message);
            default:
                return new RuntimeException(message);
        }
    }
    
    /**
     * Validates account number format (11 digits).
     * Implements COBOL account number validation logic.
     * 
     * @param accountNumber The account number to validate
     * @return true if valid account number format
     */
    public boolean isValidAccountNumber(String accountNumber) {
        if (StringUtils.isBlank(accountNumber)) {
            return false;
        }
        
        return ACCOUNT_NUMBER_PATTERN.matcher(accountNumber).matches();
    }
    
    /**
     * Validates card number format (16 digits) with Luhn algorithm.
     * Implements COBOL card number validation with check digit logic.
     * 
     * @param cardNumber The card number to validate
     * @return true if valid card number format and passes Luhn check
     */
    public boolean isValidCardNumber(String cardNumber) {
        if (StringUtils.isBlank(cardNumber)) {
            return false;
        }
        
        // Check basic format (16 digits)
        if (!CARD_NUMBER_PATTERN.matcher(cardNumber).matches()) {
            return false;
        }
        
        // Luhn algorithm validation
        return performLuhnCheck(cardNumber);
    }
    
    /**
     * Performs Luhn algorithm check for card number validation.
     * Replicates COBOL card number check digit validation.
     * 
     * @param cardNumber The card number to check
     * @return true if passes Luhn check
     */
    private boolean performLuhnCheck(String cardNumber) {
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
        
        return (sum % 10) == 0;
    }
    
    /**
     * Calculates Julian date from a standard date.
     * Implements COBOL Julian date calculation logic.
     * 
     * @param date The date to convert
     * @return Julian date as integer (YYYYDDD format)
     */
    public int calculateJulianDate(LocalDate date) {
        if (date == null) {
            return 0;
        }
        
        int year = date.getYear();
        int dayOfYear = date.getDayOfYear();
        
        return (year * 1000) + dayOfYear;
    }
    
    /**
     * Converts Julian date to standard date.
     * Implements COBOL Julian date conversion logic.
     * 
     * @param julianDate The Julian date in YYYYDDD format
     * @return LocalDate object
     */
    public LocalDate convertJulianToDate(int julianDate) {
        if (julianDate <= 0) {
            return null;
        }
        
        try {
            int year = julianDate / 1000;
            int dayOfYear = julianDate % 1000;
            
            if (year < 1900 || year > 2100 || dayOfYear < 1 || dayOfYear > 366) {
                logger.warn("Invalid Julian date: {}", julianDate);
                return null;
            }
            
            return LocalDate.ofYearDay(year, dayOfYear);
        } catch (Exception e) {
            logger.error("Error converting Julian date: {}", julianDate, e);
            return null;
        }
    }
}
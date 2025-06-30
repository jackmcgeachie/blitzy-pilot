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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.carddemo.validation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Date conversion and validation service providing COBOL-equivalent date handling capabilities.
 * 
 * <p>This service implements Julian date conversions equivalent to mainframe CEEDAYS API,
 * supports YYYYMMDD string formats, and provides date arithmetic matching COBOL calculation
 * patterns. Ensures identical date processing results while enabling modern Java LocalDate
 * operations with comprehensive validation and error handling.</p>
 * 
 * <p>Key capabilities include:</p>
 * <ul>
 *   <li>CEEDAYS API equivalent Julian date conversion functionality</li>
 *   <li>COBOL date format support (YYYYMMDD, MM/DD/YY, timestamp formats)</li>
 *   <li>Date arithmetic operations producing identical results to COBOL</li>
 *   <li>Comprehensive field validation matching COBOL 88-level conditions</li>
 *   <li>Integration with ErrorMappingService for consistent error responses</li>
 * </ul>
 * 
 * <p>Based on CSUTLDTC.cbl CEEDAYS API patterns and CSDAT01Y.cpy date structures,
 * implementing Section 0.2.3 Cross-Cutting Services requirements for DateConversionService.</p>
 * 
 * @author Blitzy agent
 * @version 1.0
 */
@Service
public class DateConversionService {

    // COBOL CEEDAYS equivalent - Julian day number base (January 1, 1900 = 1)
    private static final LocalDate JULIAN_BASE_DATE = LocalDate.of(1900, 1, 1);
    private static final long JULIAN_BASE_OFFSET = 1L;
    
    // COBOL date format patterns based on CSDAT01Y.cpy structures
    private static final Pattern YYYYMMDD_PATTERN = Pattern.compile("^\\d{8}$");
    private static final Pattern MMDDYY_PATTERN = Pattern.compile("^\\d{2}/\\d{2}/\\d{2}$");
    private static final Pattern MMDDYYYY_PATTERN = Pattern.compile("^\\d{2}/\\d{2}/\\d{4}$");
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{6}$");
    
    // COBOL century handling - matches CSUTLDTC.cbl logic
    private static final int CURRENT_CENTURY_CUTOFF = 50; // Years 00-49 = 20xx, 50-99 = 19xx
    private static final int MIN_VALID_YEAR = 1900;
    private static final int MAX_VALID_YEAR = 2099;
    
    // CEEDAYS feedback codes - matching CSUTLDTC.cbl feedback patterns
    public static final int FC_SUCCESS = 0;
    public static final int FC_INVALID_DATE = 1;
    public static final int FC_INSUFFICIENT_DATA = 2;
    public static final int FC_BAD_DATE_VALUE = 3;
    public static final int FC_INVALID_ERA = 4;
    public static final int FC_UNSUPP_RANGE = 5;
    public static final int FC_INVALID_MONTH = 6;
    public static final int FC_BAD_PIC_STRING = 7;
    public static final int FC_NON_NUMERIC_DATA = 8;
    public static final int FC_YEAR_IN_ERA_ZERO = 9;
    
    // Date format masks based on CSDAT01Y.cpy structures
    public static final String YYYYMMDD_FORMAT = "YYYYMMDD";
    public static final String MMDDYY_FORMAT = "MM/DD/YY";
    public static final String MMDDYYYY_FORMAT = "MM/DD/YYYY";
    public static final String TIMESTAMP_FORMAT = "YYYY-MM-DD HH:MM:SS.MS6";
    
    @Autowired
    private ErrorMappingService errorMappingService;
    
    /**
     * Represents a date validation result equivalent to CSUTLDTC.cbl output.
     * Contains severity, message number, result text, and original input for debugging.
     */
    public static class DateValidationResult {
        private final int severity;
        private final int messageNumber;
        private final String resultText;
        private final String testDate;
        private final String maskUsed;
        private final long julianDate;
        
        public DateValidationResult(int severity, int messageNumber, String resultText, 
                                  String testDate, String maskUsed, long julianDate) {
            this.severity = severity;
            this.messageNumber = messageNumber;
            this.resultText = resultText;
            this.testDate = testDate;
            this.maskUsed = maskUsed;
            this.julianDate = julianDate;
        }
        
        public int getSeverity() { return severity; }
        public int getMessageNumber() { return messageNumber; }
        public String getResultText() { return resultText; }
        public String getTestDate() { return testDate; }
        public String getMaskUsed() { return maskUsed; }
        public long getJulianDate() { return julianDate; }
        public boolean isValid() { return severity == FC_SUCCESS; }
        
        @Override
        public String toString() {
            return String.format("Severity: %04d, Message: %04d, Result: %s, Test Date: %s, Mask: %s, Julian: %d",
                               severity, messageNumber, resultText, testDate, maskUsed, julianDate);
        }
    }
    
    /**
     * Converts a date string to Julian day number equivalent to CEEDAYS API.
     * 
     * <p>This method replicates the functionality of CSUTLDTC.cbl's CEEDAYS call,
     * providing identical Julian date calculation results for backward compatibility.</p>
     * 
     * @param dateString Date in supported format (YYYYMMDD, MM/DD/YY, etc.)
     * @param formatMask Date format specification
     * @return DateValidationResult containing Julian date and validation status
     */
    public DateValidationResult convertToJulianDate(String dateString, String formatMask) {
        // Input validation - matching CSUTLDTC.cbl input validation
        if (!StringUtils.hasText(dateString)) {
            return new DateValidationResult(FC_INSUFFICIENT_DATA, FC_INSUFFICIENT_DATA,
                                          "Insufficient data", dateString != null ? dateString : "", 
                                          formatMask != null ? formatMask : "", 0L);
        }
        
        if (!StringUtils.hasText(formatMask)) {
            return new DateValidationResult(FC_BAD_PIC_STRING, FC_BAD_PIC_STRING,
                                          "Bad Pic String", dateString, "", 0L);
        }
        
        try {
            // Normalize input for processing
            String normalizedDate = dateString.trim().toUpperCase();
            String normalizedMask = formatMask.trim().toUpperCase();
            
            // Parse date based on format mask - matching CEEDAYS format support
            LocalDate parsedDate = parseFormattedDate(normalizedDate, normalizedMask);
            if (parsedDate == null) {
                return new DateValidationResult(FC_BAD_DATE_VALUE, FC_BAD_DATE_VALUE,
                                              "Date value error", dateString, formatMask, 0L);
            }
            
            // Validate date is within supported range
            if (parsedDate.getYear() < MIN_VALID_YEAR || parsedDate.getYear() > MAX_VALID_YEAR) {
                return new DateValidationResult(FC_UNSUPP_RANGE, FC_UNSUPP_RANGE,
                                              "Unsupp. Range", dateString, formatMask, 0L);
            }
            
            // Calculate Julian day number - CEEDAYS equivalent calculation
            long julianDayNumber = calculateJulianDayNumber(parsedDate);
            
            return new DateValidationResult(FC_SUCCESS, FC_SUCCESS,
                                          "Date is valid", dateString, formatMask, julianDayNumber);
                                          
        } catch (DateTimeParseException e) {
            return new DateValidationResult(FC_BAD_DATE_VALUE, FC_BAD_DATE_VALUE,
                                          "Date value error", dateString, formatMask, 0L);
        } catch (NumberFormatException e) {
            return new DateValidationResult(FC_NON_NUMERIC_DATA, FC_NON_NUMERIC_DATA,
                                          "Nonnumeric data", dateString, formatMask, 0L);
        } catch (Exception e) {
            return new DateValidationResult(FC_INVALID_DATE, FC_INVALID_DATE,
                                          "Date is invalid", dateString, formatMask, 0L);
        }
    }
    
    /**
     * Converts Julian day number back to formatted date string.
     * Provides reverse conversion of CEEDAYS Julian date to readable format.
     * 
     * @param julianDate Julian day number
     * @param formatMask Desired output format
     * @return Formatted date string or null if invalid
     */
    public String convertFromJulianDate(long julianDate, String formatMask) {
        try {
            // Convert Julian day number back to LocalDate
            LocalDate date = JULIAN_BASE_DATE.plusDays(julianDate - JULIAN_BASE_OFFSET);
            
            // Validate date is within supported range
            if (date.getYear() < MIN_VALID_YEAR || date.getYear() > MAX_VALID_YEAR) {
                return null;
            }
            
            // Format according to specified mask
            return formatDate(date, formatMask);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Validates a date string against COBOL date validation patterns.
     * Replicates CSUTLDPY.cpy validation logic including year, month, day, and leap year checks.
     * 
     * @param dateString Date string to validate
     * @param formatMask Expected format specification
     * @return DateValidationResult with detailed validation information
     */
    public DateValidationResult validateDate(String dateString, String formatMask) {
        // Convert to Julian date which performs comprehensive validation
        DateValidationResult result = convertToJulianDate(dateString, formatMask);
        
        // Additional COBOL-specific validation checks
        if (result.isValid()) {
            try {
                LocalDate date = parseFormattedDate(dateString.trim(), formatMask.trim());
                if (date != null) {
                    // Check for leap year validation - matching CSUTLDPY.cpy logic
                    if (date.getMonthValue() == 2 && date.getDayOfMonth() == 29) {
                        if (!date.isLeapYear()) {
                            return new DateValidationResult(FC_BAD_DATE_VALUE, FC_BAD_DATE_VALUE,
                                                          "Invalid leap day", dateString, formatMask, 0L);
                        }
                    }
                    
                    // Check for future date restriction for date of birth validation
                    if (date.isAfter(LocalDate.now())) {
                        return new DateValidationResult(FC_BAD_DATE_VALUE, FC_BAD_DATE_VALUE,
                                                      "Future date invalid", dateString, formatMask, 0L);
                    }
                }
            } catch (Exception e) {
                return new DateValidationResult(FC_INVALID_DATE, FC_INVALID_DATE,
                                              "Date is invalid", dateString, formatMask, 0L);
            }
        }
        
        return result;
    }
    
    /**
     * Performs date arithmetic operations matching COBOL date calculation patterns.
     * Supports addition and subtraction of days, months, and years.
     * 
     * @param dateString Base date string
     * @param formatMask Date format specification
     * @param operation Arithmetic operation (+, -)
     * @param amount Amount to add or subtract
     * @param unit Time unit (DAYS, MONTHS, YEARS)
     * @return Calculated date string in same format or null if invalid
     */
    public String performDateArithmetic(String dateString, String formatMask, 
                                      String operation, long amount, ChronoUnit unit) {
        try {
            // Validate input date
            DateValidationResult validation = validateDate(dateString, formatMask);
            if (!validation.isValid()) {
                return null;
            }
            
            // Parse the date
            LocalDate baseDate = parseFormattedDate(dateString.trim(), formatMask.trim());
            if (baseDate == null) {
                return null;
            }
            
            // Perform arithmetic operation
            LocalDate resultDate;
            switch (operation.trim().toUpperCase()) {
                case "+":
                case "ADD":
                    resultDate = baseDate.plus(amount, unit);
                    break;
                case "-":
                case "SUBTRACT":
                    resultDate = baseDate.minus(amount, unit);
                    break;
                default:
                    return null;
            }
            
            // Validate result is within supported range
            if (resultDate.getYear() < MIN_VALID_YEAR || resultDate.getYear() > MAX_VALID_YEAR) {
                return null;
            }
            
            // Format result in same format as input
            return formatDate(resultDate, formatMask);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Calculates difference between two dates in specified units.
     * Provides COBOL-compatible date difference calculation.
     * 
     * @param startDate Starting date string
     * @param endDate Ending date string
     * @param formatMask Date format for both dates
     * @param unit Unit for difference calculation
     * @return Difference in specified units or null if invalid
     */
    public Long calculateDateDifference(String startDate, String endDate, 
                                      String formatMask, ChronoUnit unit) {
        try {
            // Validate both dates
            DateValidationResult startValidation = validateDate(startDate, formatMask);
            DateValidationResult endValidation = validateDate(endDate, formatMask);
            
            if (!startValidation.isValid() || !endValidation.isValid()) {
                return null;
            }
            
            // Parse both dates
            LocalDate start = parseFormattedDate(startDate.trim(), formatMask.trim());
            LocalDate end = parseFormattedDate(endDate.trim(), formatMask.trim());
            
            if (start == null || end == null) {
                return null;
            }
            
            // Calculate difference
            return unit.between(start, end);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Gets current date in specified format.
     * Equivalent to COBOL CURRENT-DATE function.
     * 
     * @param formatMask Desired date format
     * @return Current date formatted according to mask
     */
    public String getCurrentDate(String formatMask) {
        return formatDate(LocalDate.now(), formatMask);
    }
    
    /**
     * Gets current timestamp in COBOL timestamp format.
     * Matches CSDAT01Y.cpy WS-TIMESTAMP format (YYYY-MM-DD HH:MM:SS.MS6).
     * 
     * @return Current timestamp in COBOL format
     */
    public String getCurrentTimestamp() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
        return now.format(formatter);
    }
    
    /**
     * Validates age calculation for date of birth validation.
     * Implements COBOL age validation logic.
     * 
     * @param dateOfBirth Date of birth string
     * @param formatMask Date format
     * @param minimumAge Minimum required age
     * @param maximumAge Maximum allowed age (optional, null for no limit)
     * @return true if age is within valid range
     */
    public boolean validateAge(String dateOfBirth, String formatMask, int minimumAge, Integer maximumAge) {
        try {
            DateValidationResult validation = validateDate(dateOfBirth, formatMask);
            if (!validation.isValid()) {
                return false;
            }
            
            LocalDate dob = parseFormattedDate(dateOfBirth.trim(), formatMask.trim());
            if (dob == null) {
                return false;
            }
            
            // Calculate age
            long ageInYears = ChronoUnit.YEARS.between(dob, LocalDate.now());
            
            // Validate age range
            if (ageInYears < minimumAge) {
                return false;
            }
            
            if (maximumAge != null && ageInYears > maximumAge) {
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Creates error response for date validation failures.
     * Integrates with ErrorMappingService for consistent error handling.
     * 
     * @param validationResult Date validation result
     * @param fieldName Field that failed validation
     * @param fieldValue Invalid field value
     * @return Error response map
     */
    public Map<String, Object> createDateValidationError(DateValidationResult validationResult,
                                                        String fieldName, String fieldValue) {
        String errorKey = mapValidationResultToErrorKey(validationResult);
        String details = String.format("Field: %s, Value: %s, Julian: %d, Result: %s",
                                      fieldName,
                                      fieldValue,
                                      validationResult.getJulianDate(),
                                      validationResult.getResultText());
        
        return errorMappingService.createFieldValidationError(fieldName, fieldValue, errorKey);
    }
    
    // Private helper methods
    
    /**
     * Parses formatted date string according to specified format mask.
     * Handles COBOL date format patterns and century logic.
     */
    private LocalDate parseFormattedDate(String dateString, String formatMask) {
        if (!StringUtils.hasText(dateString) || !StringUtils.hasText(formatMask)) {
            return null;
        }
        
        try {
            switch (formatMask.toUpperCase()) {
                case YYYYMMDD_FORMAT:
                    return parseYYYYMMDD(dateString);
                case MMDDYY_FORMAT:
                    return parseMMDDYY(dateString);
                case MMDDYYYY_FORMAT:
                    return parseMMDDYYYY(dateString);
                case TIMESTAMP_FORMAT:
                    return parseTimestamp(dateString);
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Parses YYYYMMDD format - COBOL PIC 9(8) date format.
     */
    private LocalDate parseYYYYMMDD(String dateString) {
        if (!YYYYMMDD_PATTERN.matcher(dateString).matches()) {
            return null;
        }
        
        try {
            int year = Integer.parseInt(dateString.substring(0, 4));
            int month = Integer.parseInt(dateString.substring(4, 6));
            int day = Integer.parseInt(dateString.substring(6, 8));
            
            // Validate ranges
            if (month < 1 || month > 12 || day < 1 || day > 31) {
                return null;
            }
            
            return LocalDate.of(year, month, day);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Parses MM/DD/YY format with COBOL century logic.
     * Years 00-49 = 20xx, years 50-99 = 19xx.
     */
    private LocalDate parseMMDDYY(String dateString) {
        if (!MMDDYY_PATTERN.matcher(dateString).matches()) {
            return null;
        }
        
        try {
            String[] parts = dateString.split("/");
            int month = Integer.parseInt(parts[0]);
            int day = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[2]);
            
            // Apply COBOL century logic
            if (year <= CURRENT_CENTURY_CUTOFF) {
                year += 2000;
            } else {
                year += 1900;
            }
            
            // Validate ranges
            if (month < 1 || month > 12 || day < 1 || day > 31) {
                return null;
            }
            
            return LocalDate.of(year, month, day);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Parses MM/DD/YYYY format.
     */
    private LocalDate parseMMDDYYYY(String dateString) {
        if (!MMDDYYYY_PATTERN.matcher(dateString).matches()) {
            return null;
        }
        
        try {
            String[] parts = dateString.split("/");
            int month = Integer.parseInt(parts[0]);
            int day = Integer.parseInt(parts[1]);
            int year = Integer.parseInt(parts[2]);
            
            // Validate ranges
            if (month < 1 || month > 12 || day < 1 || day > 31) {
                return null;
            }
            
            return LocalDate.of(year, month, day);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Parses timestamp format (YYYY-MM-DD HH:MM:SS.MS6).
     */
    private LocalDate parseTimestamp(String dateString) {
        if (!TIMESTAMP_PATTERN.matcher(dateString).matches()) {
            return null;
        }
        
        try {
            // Extract date portion
            String datePortion = dateString.substring(0, 10);
            String[] parts = datePortion.split("-");
            
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            
            return LocalDate.of(year, month, day);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Formats LocalDate according to specified format mask.
     */
    private String formatDate(LocalDate date, String formatMask) {
        if (date == null || !StringUtils.hasText(formatMask)) {
            return null;
        }
        
        try {
            switch (formatMask.toUpperCase()) {
                case YYYYMMDD_FORMAT:
                    return date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                case MMDDYY_FORMAT:
                    // Use COBOL century logic for 2-digit year
                    int year2digit = date.getYear() % 100;
                    return String.format("%02d/%02d/%02d", 
                                       date.getMonthValue(), date.getDayOfMonth(), year2digit);
                case MMDDYYYY_FORMAT:
                    return date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                case TIMESTAMP_FORMAT:
                    return date.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"));
                default:
                    return date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            }
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Calculates Julian day number equivalent to CEEDAYS API.
     * Uses same base date (January 1, 1900 = 1) as mainframe CEEDAYS.
     */
    private long calculateJulianDayNumber(LocalDate date) {
        return ChronoUnit.DAYS.between(JULIAN_BASE_DATE, date) + JULIAN_BASE_OFFSET;
    }
    
    /**
     * Maps validation result to error key for ErrorMappingService integration.
     */
    private String mapValidationResultToErrorKey(DateValidationResult result) {
        switch (result.getMessageNumber()) {
            case FC_INSUFFICIENT_DATA:
                return "REQUIRED_FIELD_MISSING";
            case FC_BAD_DATE_VALUE:
            case FC_INVALID_DATE:
                return "INVALID_DATE_FORMAT";
            case FC_NON_NUMERIC_DATA:
                return "INVALID_NUMERIC_DATA";
            case FC_BAD_PIC_STRING:
                return "INVALID_FIELD_LENGTH";
            case FC_UNSUPP_RANGE:
            case FC_INVALID_ERA:
                return "INVALID_DATE_FORMAT";
            case FC_INVALID_MONTH:
            case FC_YEAR_IN_ERA_ZERO:
                return "INVALID_DATE_FORMAT";
            default:
                return "INVALID_DATE_FORMAT";
        }
    }
}
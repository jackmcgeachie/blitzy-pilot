/*
 * ReportRequestDto.java
 * 
 * Data Transfer Object for report generation requests supporting JSON-based REST API communication.
 * Based on original COBOL program CORPT00C.cbl input field structures.
 * 
 * Provides clean interface contract for report parameters including report type selection
 * (Monthly/Yearly/Custom), date range specification, and confirmation workflows.
 * 
 * Implements comprehensive validation rules equivalent to original COBOL date validation 
 * logic and field constraints from CSUTLDPY date validation.
 */
package com.carddemo.report;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;

import javax.validation.Valid;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Data Transfer Object for report generation requests.
 * 
 * Maps to COBOL program CORPT00C input fields and BMS map CORPT0A:
 * - MONTHLYI -> reportType = MONTHLY
 * - YEARLYI -> reportType = YEARLY  
 * - CUSTOMI -> reportType = CUSTOM
 * - SDTMMI/SDTDDI/SDTYYYYI -> startDate LocalDate field
 * - EDTMMI/EDTDDI/EDTYYYYI -> endDate LocalDate field
 * - CONFIRMI -> confirmation field
 * 
 * Supports JSON serialization/deserialization for REST API communication
 * with comprehensive validation matching original COBOL business rules.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportRequestDto {

    /**
     * Report type enumeration matching COBOL EVALUATE TRUE logic
     * for MONTHLYI, YEARLYI, and CUSTOMI options from original program.
     */
    public enum ReportType {
        @JsonProperty("MONTHLY") 
        MONTHLY("Monthly", "Current month transaction report"),
        
        @JsonProperty("YEARLY")
        YEARLY("Yearly", "Current year transaction report"),
        
        @JsonProperty("CUSTOM")
        CUSTOM("Custom", "Custom date range transaction report");

        private final String displayName;
        private final String description;

        ReportType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Report type selection - maps to COBOL EVALUATE TRUE section.
     * Required field that determines which report generation logic to execute.
     * 
     * Validation equivalent to COBOL logic:
     * WHEN MONTHLYI OF CORPT0AI NOT = SPACES AND LOW-VALUES
     * WHEN YEARLYI OF CORPT0AI NOT = SPACES AND LOW-VALUES  
     * WHEN CUSTOMI OF CORPT0AI NOT = SPACES AND LOW-VALUES
     */
    @JsonProperty("reportType")
    @NotNull(message = "Report type must be specified. Select Monthly, Yearly, or Custom report type")
    private ReportType reportType;

    /**
     * Start date for custom reports - maps to WS-START-DATE structure.
     * Required only when reportType is CUSTOM.
     * 
     * Equivalent to COBOL fields:
     * - WS-START-DATE-YYYY (PIC X(04))
     * - WS-START-DATE-MM (PIC X(02)) 
     * - WS-START-DATE-DD (PIC X(02))
     * 
     * Validation replicates COBOL CSUTLDTC date validation logic.
     */
    @JsonProperty("startDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonDeserialize(using = LocalDateDeserializer.class)
    private LocalDate startDate;

    /**
     * End date for custom reports - maps to WS-END-DATE structure.
     * Required only when reportType is CUSTOM.
     * 
     * Equivalent to COBOL fields:
     * - WS-END-DATE-YYYY (PIC X(04))
     * - WS-END-DATE-MM (PIC X(02))
     * - WS-END-DATE-DD (PIC X(02))
     * 
     * Validation replicates COBOL CSUTLDTC date validation logic.
     */
    @JsonProperty("endDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonDeserialize(using = LocalDateDeserializer.class)
    private LocalDate endDate;

    /**
     * User confirmation field - maps to CONFIRMI field from BMS map.
     * Required to confirm report generation before job submission.
     * 
     * Equivalent to COBOL validation:
     * WHEN CONFIRMI OF CORPT0AI = 'Y' OR 'y' -> proceed
     * WHEN CONFIRMI OF CORPT0AI = 'N' OR 'n' -> cancel
     * WHEN OTHER -> validation error
     */
    @JsonProperty("confirmation")
    @Pattern(regexp = "^[YyNn]$", 
             message = "Confirmation must be 'Y' for Yes or 'N' for No")
    @Size(max = 1, message = "Confirmation field must be exactly 1 character")
    private String confirmation;

    /**
     * Default constructor for JSON deserialization.
     */
    public ReportRequestDto() {
        // Default constructor required for JSON deserialization
    }

    /**
     * Constructor for creating report requests programmatically.
     * 
     * @param reportType The type of report to generate
     * @param confirmation User confirmation (Y/N)
     */
    public ReportRequestDto(ReportType reportType, String confirmation) {
        this.reportType = reportType;
        this.confirmation = confirmation;
    }

    /**
     * Constructor for custom date range reports.
     * 
     * @param reportType Must be CUSTOM for this constructor
     * @param startDate Start date for the report range
     * @param endDate End date for the report range  
     * @param confirmation User confirmation (Y/N)
     */
    public ReportRequestDto(ReportType reportType, LocalDate startDate, LocalDate endDate, String confirmation) {
        this.reportType = reportType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.confirmation = confirmation;
    }

    // Getters and Setters

    public ReportType getReportType() {
        return reportType;
    }

    public void setReportType(ReportType reportType) {
        this.reportType = reportType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getConfirmation() {
        return confirmation;
    }

    public void setConfirmation(String confirmation) {
        this.confirmation = confirmation;
    }

    // Validation Methods

    /**
     * Validates that custom report type has required date fields.
     * Replicates COBOL validation logic for CUSTOMI report type.
     * 
     * Equivalent to COBOL validation:
     * WHEN SDTMMI OF CORPT0AI = SPACES OR LOW-VALUES -> error
     * WHEN SDTDDI OF CORPT0AI = SPACES OR LOW-VALUES -> error  
     * WHEN SDTYYYYI OF CORPT0AI = SPACES OR LOW-VALUES -> error
     * WHEN EDTMMI OF CORPT0AI = SPACES OR LOW-VALUES -> error
     * WHEN EDTDDI OF CORPT0AI = SPACES OR LOW-VALUES -> error
     * WHEN EDTYYYYI OF CORPT0AI = SPACES OR LOW-VALUES -> error
     */
    @AssertTrue(message = "Start date and end date are required for custom reports")
    public boolean isCustomReportDatesValid() {
        if (reportType == ReportType.CUSTOM) {
            return startDate != null && endDate != null;
        }
        return true; // Non-custom reports don't require dates
    }

    /**
     * Validates that start date is not after end date for custom reports.
     * Implements business rule validation equivalent to COBOL date logic.
     */
    @AssertTrue(message = "Start date must not be after end date")
    public boolean isDateRangeValid() {
        if (reportType == ReportType.CUSTOM && startDate != null && endDate != null) {
            return !startDate.isAfter(endDate);
        }
        return true; // Valid if not custom or dates are null (handled by other validation)
    }

    /**
     * Validates that dates are not in the future (business rule).
     * Matches COBOL current date validation patterns.
     */
    @AssertTrue(message = "Report dates cannot be in the future")
    public boolean isDateNotInFuture() {
        LocalDate today = LocalDate.now();
        
        if (startDate != null && startDate.isAfter(today)) {
            return false;
        }
        
        if (endDate != null && endDate.isAfter(today)) {
            return false;
        }
        
        return true;
    }

    /**
     * Validates that confirmation is provided when required.
     * Replicates COBOL confirmation validation from SUBMIT-JOB-TO-INTRDR paragraph.
     */
    @AssertTrue(message = "Confirmation is required to proceed with report generation")
    public boolean isConfirmationProvided() {
        // Confirmation is always required for report submission
        return confirmation != null && !confirmation.trim().isEmpty();
    }

    // Utility Methods

    /**
     * Checks if user confirmed the report generation.
     * Equivalent to COBOL logic: WHEN CONFIRMI OF CORPT0AI = 'Y' OR 'y'
     * 
     * @return true if user confirmed with 'Y' or 'y', false otherwise
     */
    public boolean isConfirmed() {
        return "Y".equalsIgnoreCase(confirmation);
    }

    /**
     * Checks if user declined the report generation.
     * Equivalent to COBOL logic: WHEN CONFIRMI OF CORPT0AI = 'N' OR 'n'
     * 
     * @return true if user declined with 'N' or 'n', false otherwise
     */
    public boolean isDeclined() {
        return "N".equalsIgnoreCase(confirmation);
    }

    /**
     * Gets a descriptive name for the report type.
     * Used for user confirmation messages and logging.
     * 
     * @return Display name of the report type
     */
    public String getReportTypeDisplayName() {
        return reportType != null ? reportType.getDisplayName() : "Unknown";
    }

    /**
     * Formats the date range for display in confirmation messages.
     * Only applicable for CUSTOM report types.
     * 
     * @return Formatted date range string or null for non-custom reports
     */
    public String getFormattedDateRange() {
        if (reportType == ReportType.CUSTOM && startDate != null && endDate != null) {
            return String.format("%s to %s", startDate.toString(), endDate.toString());
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ReportRequestDto{");
        sb.append("reportType=").append(reportType);
        
        if (reportType == ReportType.CUSTOM) {
            sb.append(", startDate=").append(startDate);
            sb.append(", endDate=").append(endDate);
        }
        
        sb.append(", confirmation='").append(confirmation).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReportRequestDto that = (ReportRequestDto) o;

        if (reportType != that.reportType) return false;
        if (startDate != null ? !startDate.equals(that.startDate) : that.startDate != null) return false;
        if (endDate != null ? !endDate.equals(that.endDate) : that.endDate != null) return false;
        return confirmation != null ? confirmation.equals(that.confirmation) : that.confirmation == null;
    }

    @Override
    public int hashCode() {
        int result = reportType != null ? reportType.hashCode() : 0;
        result = 31 * result + (startDate != null ? startDate.hashCode() : 0);
        result = 31 * result + (endDate != null ? endDate.hashCode() : 0);
        result = 31 * result + (confirmation != null ? confirmation.hashCode() : 0);
        return result;
    }
}
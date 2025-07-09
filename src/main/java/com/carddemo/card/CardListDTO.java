package com.carddemo.card;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Data Transfer Object for card listing operations providing pagination metadata,
 * JSON serialization, field validation, and BMS screen layout compatibility 
 * for REST API responses.
 * 
 * This class supports the 7-record pagination pattern matching the original 
 * COBOL COCRDLIC program's WS-MAX-SCREEN-LINES functionality, enabling 
 * seamless integration between the React frontend and Spring Boot backend.
 * 
 * Corresponds to COBOL program: COCRDLIC.cbl
 * Replaces BMS screen: COCRDLI.bms
 * Screen layout: 7 rows x 3 fields (Account Number, Card Number, Status)
 * 
 * @author CardDemo - Blitzy agent
 * @version 1.0
 * @since 2024-03-15
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CardListDTO {
    
    /**
     * List of card summary records for display on the current page.
     * Maximum of 7 records per page to match BMS screen layout.
     */
    private List<CardSummary> cards;
    
    /**
     * Current page number (1-based) matching COBOL WS-CA-SCREEN-NUM.
     */
    @Min(value = 1, message = "Current page must be greater than 0")
    private int currentPage;
    
    /**
     * Number of records per page (default 7 for BMS compatibility).
     */
    @Min(value = 1, message = "Page size must be greater than 0")
    private int pageSize;
    
    /**
     * Total number of records across all pages.
     */
    @Min(value = 0, message = "Total records cannot be negative")
    private long totalRecords;
    
    /**
     * API version for response versioning and compatibility.
     */
    private String apiVersion;
    
    /**
     * Unique request identifier for tracking and debugging.
     */
    private String requestId;
    
    /**
     * Response timestamp in ISO format.
     */
    private String timestamp;
    
    /**
     * Default constructor initializing with empty card list and default pagination values.
     */
    public CardListDTO() {
        this.cards = new ArrayList<>();
        this.currentPage = 1;
        this.pageSize = 7; // Default BMS screen size
        this.totalRecords = 0;
        this.apiVersion = "v1";
        this.requestId = "";
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
    
    /**
     * Constructor with full pagination parameters.
     * 
     * @param cards List of card summary records
     * @param currentPage Current page number (1-based)
     * @param pageSize Number of records per page
     * @param totalRecords Total number of records
     */
    public CardListDTO(List<CardSummary> cards, int currentPage, int pageSize, long totalRecords) {
        this.cards = cards != null ? new ArrayList<>(cards) : new ArrayList<>();
        this.currentPage = Math.max(1, currentPage);
        this.pageSize = Math.max(1, pageSize);
        this.totalRecords = Math.max(0, totalRecords);
        this.apiVersion = "v1";
        this.requestId = "";
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
    
    /**
     * Gets the list of card summary records.
     * 
     * @return List of CardSummary objects
     */
    public List<CardSummary> getCards() {
        return cards;
    }
    
    /**
     * Sets the list of card summary records.
     * 
     * @param cards List of CardSummary objects
     */
    public void setCards(List<CardSummary> cards) {
        this.cards = cards != null ? new ArrayList<>(cards) : new ArrayList<>();
    }
    
    /**
     * Gets the current page number.
     * 
     * @return Current page number (1-based)
     */
    public int getCurrentPage() {
        return currentPage;
    }
    
    /**
     * Sets the current page number.
     * 
     * @param currentPage Page number (1-based)
     */
    public void setCurrentPage(int currentPage) {
        this.currentPage = Math.max(1, currentPage);
    }
    
    /**
     * Gets the page size.
     * 
     * @return Number of records per page
     */
    public int getPageSize() {
        return pageSize;
    }
    
    /**
     * Sets the page size.
     * 
     * @param pageSize Number of records per page
     */
    public void setPageSize(int pageSize) {
        this.pageSize = Math.max(1, pageSize);
    }
    
    /**
     * Gets the total number of records.
     * 
     * @return Total record count
     */
    public long getTotalRecords() {
        return totalRecords;
    }
    
    /**
     * Sets the total number of records.
     * 
     * @param totalRecords Total record count
     */
    public void setTotalRecords(long totalRecords) {
        this.totalRecords = Math.max(0, totalRecords);
    }
    
    /**
     * Calculates the total number of pages based on total records and page size.
     * 
     * @return Total number of pages
     */
    @JsonIgnore
    public int getTotalPages() {
        if (totalRecords == 0 || pageSize == 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalRecords / pageSize);
    }
    
    /**
     * Checks if the current page is the first page.
     * Corresponds to COBOL CA-FIRST-PAGE condition.
     * 
     * @return true if current page is the first page
     */
    @JsonIgnore
    public boolean isFirstPage() {
        return currentPage == 1;
    }
    
    /**
     * Checks if the current page is the last page.
     * Corresponds to COBOL CA-LAST-PAGE-SHOWN condition.
     * 
     * @return true if current page is the last page
     */
    @JsonIgnore
    public boolean isLastPage() {
        return currentPage >= getTotalPages();
    }
    
    /**
     * Checks if there is a next page available.
     * Corresponds to COBOL CA-NEXT-PAGE-EXISTS condition.
     * 
     * @return true if next page exists
     */
    @JsonIgnore
    public boolean hasNext() {
        return currentPage < getTotalPages();
    }
    
    /**
     * Checks if there is a previous page available.
     * Supports PF7 (page up) functionality from COBOL program.
     * 
     * @return true if previous page exists
     */
    @JsonIgnore
    public boolean hasPrevious() {
        return currentPage > 1;
    }
    
    /**
     * Gets the API version.
     * 
     * @return API version string
     */
    public String getApiVersion() {
        return apiVersion;
    }
    
    /**
     * Sets the API version.
     * 
     * @param apiVersion API version string
     */
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }
    
    /**
     * Gets the request ID.
     * 
     * @return Request identifier
     */
    public String getRequestId() {
        return requestId;
    }
    
    /**
     * Sets the request ID.
     * 
     * @param requestId Request identifier
     */
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    /**
     * Gets the timestamp.
     * 
     * @return Response timestamp
     */
    public String getTimestamp() {
        return timestamp;
    }
    
    /**
     * Sets the timestamp.
     * 
     * @param timestamp Response timestamp
     */
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    /**
     * Checks if the card list is empty.
     * Corresponds to COBOL WS-NO-RECORDS-FOUND condition.
     * 
     * @return true if no cards are present
     */
    @JsonIgnore
    public boolean isEmpty() {
        return cards == null || cards.isEmpty();
    }
    
    /**
     * Gets the number of cards in the current page.
     * 
     * @return Number of cards in current page
     */
    @JsonIgnore
    public int getCardCount() {
        return cards != null ? cards.size() : 0;
    }
    
    /**
     * Compares this DTO with another object for equality.
     * 
     * @param obj Object to compare with
     * @return true if objects are equal
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        CardListDTO that = (CardListDTO) obj;
        return currentPage == that.currentPage &&
               pageSize == that.pageSize &&
               totalRecords == that.totalRecords &&
               Objects.equals(cards, that.cards) &&
               Objects.equals(apiVersion, that.apiVersion) &&
               Objects.equals(requestId, that.requestId) &&
               Objects.equals(timestamp, that.timestamp);
    }
    
    /**
     * Generates hash code for this DTO.
     * 
     * @return Hash code value
     */
    @Override
    public int hashCode() {
        return Objects.hash(cards, currentPage, pageSize, totalRecords, 
                          apiVersion, requestId, timestamp);
    }
    
    /**
     * Returns string representation of this DTO.
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        return "CardListDTO{" +
               "cards=" + cards +
               ", currentPage=" + currentPage +
               ", pageSize=" + pageSize +
               ", totalRecords=" + totalRecords +
               ", apiVersion='" + apiVersion + '\'' +
               ", requestId='" + requestId + '\'' +
               ", timestamp='" + timestamp + '\'' +
               '}';
    }
    
    /**
     * Nested class representing a card summary for list display.
     * Corresponds to COBOL WS-EACH-CARD structure with fields:
     * - WS-ROW-ACCTNO (11 characters)
     * - WS-ROW-CARD-NUM (16 characters)  
     * - WS-ROW-CARD-STATUS (1 character)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CardSummary {
        
        /**
         * Account number (11 digits) corresponding to COBOL WS-ROW-ACCTNO.
         */
        private String accountNumber;
        
        /**
         * Card number (16 digits) corresponding to COBOL WS-ROW-CARD-NUM.
         */
        private String cardNumber;
        
        /**
         * Card status (1 character) corresponding to COBOL WS-ROW-CARD-STATUS.
         * Values: Y=Active, N=Inactive, E=Expired, S=Suspended
         */
        private String cardStatus;
        
        /**
         * Default constructor.
         */
        public CardSummary() {
            this.accountNumber = "";
            this.cardNumber = "";
            this.cardStatus = "";
        }
        
        /**
         * Constructor with all fields.
         * 
         * @param accountNumber 11-digit account number
         * @param cardNumber 16-digit card number
         * @param cardStatus 1-character card status
         */
        public CardSummary(String accountNumber, String cardNumber, String cardStatus) {
            this.accountNumber = accountNumber != null ? accountNumber : "";
            this.cardNumber = cardNumber != null ? cardNumber : "";
            this.cardStatus = cardStatus != null ? cardStatus : "";
        }
        
        /**
         * Gets the account number.
         * 
         * @return Account number
         */
        public String getAccountNumber() {
            return accountNumber;
        }
        
        /**
         * Sets the account number.
         * 
         * @param accountNumber Account number
         */
        public void setAccountNumber(String accountNumber) {
            this.accountNumber = accountNumber != null ? accountNumber : "";
        }
        
        /**
         * Gets the card number.
         * 
         * @return Card number
         */
        public String getCardNumber() {
            return cardNumber;
        }
        
        /**
         * Sets the card number.
         * 
         * @param cardNumber Card number
         */
        public void setCardNumber(String cardNumber) {
            this.cardNumber = cardNumber != null ? cardNumber : "";
        }
        
        /**
         * Gets the card status.
         * 
         * @return Card status
         */
        public String getCardStatus() {
            return cardStatus;
        }
        
        /**
         * Sets the card status.
         * 
         * @param cardStatus Card status
         */
        public void setCardStatus(String cardStatus) {
            this.cardStatus = cardStatus != null ? cardStatus : "";
        }
        
        /**
         * Compares this CardSummary with another object for equality.
         * 
         * @param obj Object to compare with
         * @return true if objects are equal
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            
            CardSummary that = (CardSummary) obj;
            return Objects.equals(accountNumber, that.accountNumber) &&
                   Objects.equals(cardNumber, that.cardNumber) &&
                   Objects.equals(cardStatus, that.cardStatus);
        }
        
        /**
         * Generates hash code for this CardSummary.
         * 
         * @return Hash code value
         */
        @Override
        public int hashCode() {
            return Objects.hash(accountNumber, cardNumber, cardStatus);
        }
        
        /**
         * Returns string representation of this CardSummary.
         * 
         * @return String representation
         */
        @Override
        public String toString() {
            return "CardSummary{" +
                   "accountNumber='" + accountNumber + '\'' +
                   ", cardNumber='" + cardNumber + '\'' +
                   ", cardStatus='" + cardStatus + '\'' +
                   '}';
        }
    }
}
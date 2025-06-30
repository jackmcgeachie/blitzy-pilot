/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.batch;

import com.carddemo.entity.Account;
import com.carddemo.entity.Customer;
import com.carddemo.entity.Transaction;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.repository.TransactionRepository;
import com.carddemo.util.FileProcessingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.SqlPagingQueryProviderFactoryBean;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.batch.item.file.transform.FixedLengthTokenizer;
import org.springframework.batch.item.file.transform.Range;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Factory class providing reusable Spring Batch ItemReader implementations for various data sources.
 * 
 * This factory replaces COBOL file processing patterns with modern Spring Batch ItemReaders,
 * providing high-volume data processing capabilities with error handling and legacy file format
 * compatibility. Supports both PostgreSQL database readers and fixed-width file parsers matching
 * COBOL record layouts.
 * 
 * Key Features:
 * - PostgreSQL pagination support for high-volume data processing (per Section 4.5.4.1)
 * - Fixed-width file parsing matching COBOL record layouts (per Section 0.1.3)
 * - Comprehensive error handling with logging for regulatory requirements
 * - Reusable reader components for common batch processing patterns
 * 
 * Database readers replace VSAM file operations (per Section 0.2.1):
 * - CUSTDAT → customers table reader
 * - ACCTDAT → accounts table reader  
 * - TRANSACT → transactions table reader
 * 
 * Legacy file readers support ASCII data files for compatibility:
 * - custdata.txt, acctdata.txt, carddata.txt, dailytran.txt
 * 
 * Performance characteristics:
 * - Configurable page sizes for optimal memory usage
 * - Batch processing with 10K+ records per chunk capability
 * - Connection pool optimization through Spring Data JPA
 * 
 * @author Blitzy agent
 * @version 1.0
 * @since 2024-01-01
 */
@Component
public class BatchItemReaderFactory {

    private static final Logger logger = LoggerFactory.getLogger(BatchItemReaderFactory.class);

    // Default pagination settings for high-volume processing
    private static final int DEFAULT_PAGE_SIZE = 1000;
    private static final int DEFAULT_CHUNK_SIZE = 5000;
    
    // Date formatters matching COBOL date patterns from CSDAT01Y.cpy
    private static final DateTimeFormatter COBOL_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter COBOL_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private FileProcessingService fileProcessingService;

    @Value("${batch.input.data.path:${user.home}/data}")
    private String dataInputPath;

    @Value("${batch.reader.page.size:1000}")
    private int pageSize = DEFAULT_PAGE_SIZE;

    /**
     * Creates a JPA-based ItemReader for Account entities with pagination support.
     * Replaces VSAM ACCTDAT file access with PostgreSQL accounts table operations.
     * 
     * Performance optimizations:
     * - Configurable page size for memory management
     * - JPA query optimization with composite indexes
     * - Support for filtered reading based on account status
     * 
     * @param accountStatus Optional filter for account status (null for all accounts)
     * @param sortProperty Property to sort by (default: accountId)
     * @return Configured JpaPagingItemReader for Account entities
     */
    public JpaPagingItemReader<Account> createAccountReader(String accountStatus, String sortProperty) {
        logger.info("Creating Account reader with status filter: {} and sort: {}", 
                   accountStatus, sortProperty != null ? sortProperty : "accountId");

        String jpqlQuery = accountStatus != null 
            ? "SELECT a FROM Account a WHERE a.accountStatus = :status ORDER BY a." + 
              (sortProperty != null ? sortProperty : "accountId")
            : "SELECT a FROM Account a ORDER BY a." + 
              (sortProperty != null ? sortProperty : "accountId");

        Map<String, Object> parameterValues = new HashMap<>();
        if (accountStatus != null) {
            parameterValues.put("status", accountStatus);
        }

        JpaPagingItemReader<Account> reader = new JpaPagingItemReaderBuilder<Account>()
                .name("accountReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString(jpqlQuery)
                .parameterValues(parameterValues)
                .pageSize(pageSize)
                .build();

        logger.debug("Account reader configured with page size: {} and query: {}", pageSize, jpqlQuery);
        return reader;
    }

    /**
     * Creates a JPA-based ItemReader for Customer entities with pagination support.
     * Replaces VSAM CUSTDAT file access with PostgreSQL customers table operations.
     * 
     * Supports customer lookups by various criteria:
     * - All customers (null filter)
     * - Customers by FICO score range
     * - Customers by demographic criteria
     * 
     * @param ficoScoreMin Minimum FICO score filter (null for no filter)
     * @param ficoScoreMax Maximum FICO score filter (null for no filter)
     * @return Configured JpaPagingItemReader for Customer entities
     */
    public JpaPagingItemReader<Customer> createCustomerReader(Integer ficoScoreMin, Integer ficoScoreMax) {
        logger.info("Creating Customer reader with FICO score range: {} - {}", ficoScoreMin, ficoScoreMax);

        StringBuilder jpqlQuery = new StringBuilder("SELECT c FROM Customer c");
        Map<String, Object> parameterValues = new HashMap<>();

        if (ficoScoreMin != null || ficoScoreMax != null) {
            jpqlQuery.append(" WHERE");
            if (ficoScoreMin != null && ficoScoreMax != null) {
                jpqlQuery.append(" c.ficoScore BETWEEN :minScore AND :maxScore");
                parameterValues.put("minScore", ficoScoreMin);
                parameterValues.put("maxScore", ficoScoreMax);
            } else if (ficoScoreMin != null) {
                jpqlQuery.append(" c.ficoScore >= :minScore");
                parameterValues.put("minScore", ficoScoreMin);
            } else {
                jpqlQuery.append(" c.ficoScore <= :maxScore");
                parameterValues.put("maxScore", ficoScoreMax);
            }
        }
        jpqlQuery.append(" ORDER BY c.customerId");

        JpaPagingItemReader<Customer> reader = new JpaPagingItemReaderBuilder<Customer>()
                .name("customerReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString(jpqlQuery.toString())
                .parameterValues(parameterValues)
                .pageSize(pageSize)
                .build();

        logger.debug("Customer reader configured with query: {}", jpqlQuery.toString());
        return reader;
    }

    /**
     * Creates a JPA-based ItemReader for Transaction entities with date range filtering.
     * Replaces VSAM TRANSACT file access with PostgreSQL transactions table operations.
     * 
     * Optimized for high-volume transaction processing:
     * - Date range filtering for daily/monthly batch processing
     * - Composite index utilization on (card_number, transaction_timestamp)
     * - Memory-efficient pagination for large transaction volumes
     * 
     * @param startDate Start date for transaction filtering (inclusive)
     * @param endDate End date for transaction filtering (inclusive)
     * @param cardNumber Optional card number filter (null for all cards)
     * @return Configured JpaPagingItemReader for Transaction entities
     */
    public JpaPagingItemReader<Transaction> createTransactionReader(LocalDate startDate, LocalDate endDate, String cardNumber) {
        logger.info("Creating Transaction reader for date range: {} - {} and card: {}", 
                   startDate, endDate, cardNumber);

        StringBuilder jpqlQuery = new StringBuilder("SELECT t FROM Transaction t WHERE 1=1");
        Map<String, Object> parameterValues = new HashMap<>();

        if (startDate != null) {
            jpqlQuery.append(" AND t.transactionTimestamp >= :startDate");
            parameterValues.put("startDate", startDate.atStartOfDay());
        }
        if (endDate != null) {
            jpqlQuery.append(" AND t.transactionTimestamp <= :endDate");
            parameterValues.put("endDate", endDate.atTime(23, 59, 59));
        }
        if (cardNumber != null && !cardNumber.trim().isEmpty()) {
            jpqlQuery.append(" AND t.cardNumber = :cardNumber");
            parameterValues.put("cardNumber", cardNumber);
        }
        jpqlQuery.append(" ORDER BY t.transactionTimestamp, t.transactionId");

        JpaPagingItemReader<Transaction> reader = new JpaPagingItemReaderBuilder<Transaction>()
                .name("transactionReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString(jpqlQuery.toString())
                .parameterValues(parameterValues)
                .pageSize(pageSize)
                .build();

        logger.debug("Transaction reader configured with query: {}", jpqlQuery.toString());
        return reader;
    }

    /**
     * Creates a fixed-width file reader for customer data files (custdata.txt).
     * Maintains compatibility with legacy COBOL record layouts from CUSTREC.cpy.
     * 
     * COBOL field mappings preserved:
     * - Customer ID: positions 1-9 (9 characters)
     * - Customer Name: positions 10-59 (50 characters)  
     * - Customer Address: positions 60-159 (100 characters)
     * - Phone Number: positions 160-169 (10 characters)
     * - Email: positions 170-219 (50 characters)
     * - Date of Birth: positions 220-227 (8 characters, YYYYMMDD)
     * - SSN: positions 228-236 (9 characters)
     * - FICO Score: positions 237-239 (3 characters)
     * 
     * @param fileName Name of the customer data file
     * @return Configured FlatFileItemReader for customer records
     */
    public FlatFileItemReader<CustomerFileRecord> createCustomerFileReader(String fileName) {
        logger.info("Creating fixed-width customer file reader for: {}", fileName);

        Resource resource = new FileSystemResource(dataInputPath + "/" + fileName);
        
        // Define field ranges matching COBOL CUSTREC.cpy layout
        Range[] ranges = new Range[] {
            new Range(1, 9),    // Customer ID
            new Range(10, 59),  // Customer Name
            new Range(60, 159), // Customer Address
            new Range(160, 169), // Phone Number
            new Range(170, 219), // Email
            new Range(220, 227), // Date of Birth (YYYYMMDD)
            new Range(228, 236), // SSN
            new Range(237, 239)  // FICO Score
        };

        String[] fieldNames = {
            "customerId", "customerName", "customerAddress", 
            "customerPhone", "customerEmail", "dateOfBirth", 
            "ssn", "ficoScore"
        };

        FixedLengthTokenizer tokenizer = new FixedLengthTokenizer();
        tokenizer.setNames(fieldNames);
        tokenizer.setColumns(ranges);
        tokenizer.setStrict(true);

        // Custom field set mapper for COBOL data type conversions
        FieldSetMapper<CustomerFileRecord> fieldSetMapper = fieldSet -> {
            CustomerFileRecord record = new CustomerFileRecord();
            record.setCustomerId(fieldSet.readString("customerId").trim());
            record.setCustomerName(fieldSet.readString("customerName").trim());
            record.setCustomerAddress(fieldSet.readString("customerAddress").trim());
            record.setCustomerPhone(fieldSet.readString("customerPhone").trim());
            record.setCustomerEmail(fieldSet.readString("customerEmail").trim());
            
            // Parse COBOL date format (YYYYMMDD)
            String dateStr = fieldSet.readString("dateOfBirth").trim();
            if (!dateStr.isEmpty() && dateStr.length() == 8) {
                try {
                    record.setDateOfBirth(LocalDate.parse(dateStr, COBOL_DATE_FORMAT));
                } catch (Exception e) {
                    logger.warn("Invalid date format in customer file: {}", dateStr);
                    record.setDateOfBirth(null);
                }
            }
            
            record.setSsn(fieldSet.readString("ssn").trim());
            
            // Parse FICO score with validation
            String ficoStr = fieldSet.readString("ficoScore").trim();
            if (!ficoStr.isEmpty()) {
                try {
                    int fico = Integer.parseInt(ficoStr);
                    record.setFicoScore(fico >= 300 && fico <= 850 ? fico : null);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid FICO score in customer file: {}", ficoStr);
                    record.setFicoScore(null);
                }
            }
            
            return record;
        };

        DefaultLineMapper<CustomerFileRecord> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fieldSetMapper);

        FlatFileItemReader<CustomerFileRecord> reader = new FlatFileItemReaderBuilder<CustomerFileRecord>()
                .name("customerFileReader")
                .resource(resource)
                .lineMapper(lineMapper)
                .linesToSkip(0)
                .strict(true)
                .build();

        logger.debug("Customer file reader configured for file: {} with {} fields", fileName, fieldNames.length);
        return reader;
    }

    /**
     * Creates a fixed-width file reader for account data files (acctdata.txt).
     * Maintains compatibility with legacy COBOL record layouts from CVACT01Y.cpy.
     * 
     * COBOL field mappings preserved:
     * - Account ID: positions 1-11 (11 characters)
     * - Customer ID: positions 12-20 (9 characters)
     * - Account Status: positions 21-21 (1 character)
     * - Account Open Date: positions 22-29 (8 characters, YYYYMMDD)
     * - Current Balance: positions 30-44 (15 characters, COMP-3 equivalent)
     * - Credit Limit: positions 45-59 (15 characters, COMP-3 equivalent)
     * - Expiry Date: positions 60-67 (8 characters, YYYYMMDD)
     * 
     * @param fileName Name of the account data file
     * @return Configured FlatFileItemReader for account records
     */
    public FlatFileItemReader<AccountFileRecord> createAccountFileReader(String fileName) {
        logger.info("Creating fixed-width account file reader for: {}", fileName);

        Resource resource = new FileSystemResource(dataInputPath + "/" + fileName);
        
        // Define field ranges matching COBOL CVACT01Y.cpy layout
        Range[] ranges = new Range[] {
            new Range(1, 11),   // Account ID
            new Range(12, 20),  // Customer ID
            new Range(21, 21),  // Account Status
            new Range(22, 29),  // Account Open Date
            new Range(30, 44),  // Current Balance
            new Range(45, 59),  // Credit Limit
            new Range(60, 67)   // Expiry Date
        };

        String[] fieldNames = {
            "accountId", "customerId", "accountStatus", 
            "accountOpenDate", "currentBalance", "creditLimit", 
            "expiryDate"
        };

        FixedLengthTokenizer tokenizer = new FixedLengthTokenizer();
        tokenizer.setNames(fieldNames);
        tokenizer.setColumns(ranges);
        tokenizer.setStrict(true);

        // Custom field set mapper for COBOL monetary data type conversions
        FieldSetMapper<AccountFileRecord> fieldSetMapper = fieldSet -> {
            AccountFileRecord record = new AccountFileRecord();
            record.setAccountId(fieldSet.readString("accountId").trim());
            record.setCustomerId(fieldSet.readString("customerId").trim());
            record.setAccountStatus(fieldSet.readString("accountStatus").trim());
            
            // Parse COBOL date formats
            parseCobolDate(fieldSet, "accountOpenDate", record::setAccountOpenDate);
            parseCobolDate(fieldSet, "expiryDate", record::setExpiryDate);
            
            // Parse COBOL COMP-3 equivalent monetary values with 2 decimal places
            parseCobolDecimal(fieldSet, "currentBalance", record::setCurrentBalance);
            parseCobolDecimal(fieldSet, "creditLimit", record::setCreditLimit);
            
            return record;
        };

        DefaultLineMapper<AccountFileRecord> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fieldSetMapper);

        FlatFileItemReader<AccountFileRecord> reader = new FlatFileItemReaderBuilder<AccountFileRecord>()
                .name("accountFileReader")
                .resource(resource)
                .lineMapper(lineMapper)
                .linesToSkip(0)
                .strict(true)
                .build();

        logger.debug("Account file reader configured for file: {} with {} fields", fileName, fieldNames.length);
        return reader;
    }

    /**
     * Creates a fixed-width file reader for transaction data files (dailytran.txt).
     * Maintains compatibility with legacy COBOL record layouts from CVTRA01Y.cpy.
     * 
     * COBOL field mappings preserved:
     * - Transaction ID: positions 1-16 (16 characters)
     * - Card Number: positions 17-32 (16 characters)
     * - Transaction Amount: positions 33-47 (15 characters, COMP-3 equivalent)
     * - Merchant Name: positions 48-97 (50 characters)
     * - Merchant City: positions 98-122 (25 characters)
     * - Merchant ZIP: positions 123-127 (5 characters)
     * - Transaction Date: positions 128-135 (8 characters, YYYYMMDD)
     * - Transaction Time: positions 136-141 (6 characters, HHMMSS)
     * - Transaction Type: positions 142-143 (2 characters)
     * - Transaction Category: positions 144-145 (2 characters)
     * 
     * @param fileName Name of the transaction data file
     * @return Configured FlatFileItemReader for transaction records
     */
    public FlatFileItemReader<TransactionFileRecord> createTransactionFileReader(String fileName) {
        logger.info("Creating fixed-width transaction file reader for: {}", fileName);

        Resource resource = new FileSystemResource(dataInputPath + "/" + fileName);
        
        // Define field ranges matching COBOL CVTRA01Y.cpy layout
        Range[] ranges = new Range[] {
            new Range(1, 16),   // Transaction ID
            new Range(17, 32),  // Card Number
            new Range(33, 47),  // Transaction Amount
            new Range(48, 97),  // Merchant Name
            new Range(98, 122), // Merchant City
            new Range(123, 127), // Merchant ZIP
            new Range(128, 135), // Transaction Date
            new Range(136, 141), // Transaction Time
            new Range(142, 143), // Transaction Type
            new Range(144, 145)  // Transaction Category
        };

        String[] fieldNames = {
            "transactionId", "cardNumber", "transactionAmount",
            "merchantName", "merchantCity", "merchantZip",
            "transactionDate", "transactionTime", "transactionType",
            "transactionCategory"
        };

        FixedLengthTokenizer tokenizer = new FixedLengthTokenizer();
        tokenizer.setNames(fieldNames);
        tokenizer.setColumns(ranges);
        tokenizer.setStrict(true);

        // Custom field set mapper for COBOL transaction data conversions
        FieldSetMapper<TransactionFileRecord> fieldSetMapper = fieldSet -> {
            TransactionFileRecord record = new TransactionFileRecord();
            record.setTransactionId(fieldSet.readString("transactionId").trim());
            record.setCardNumber(fieldSet.readString("cardNumber").trim());
            record.setMerchantName(fieldSet.readString("merchantName").trim());
            record.setMerchantCity(fieldSet.readString("merchantCity").trim());
            record.setMerchantZip(fieldSet.readString("merchantZip").trim());
            record.setTransactionType(fieldSet.readString("transactionType").trim());
            record.setTransactionCategory(fieldSet.readString("transactionCategory").trim());
            
            // Parse COBOL monetary amount
            parseCobolDecimal(fieldSet, "transactionAmount", record::setTransactionAmount);
            
            // Parse COBOL date and time into LocalDateTime
            String dateStr = fieldSet.readString("transactionDate").trim();
            String timeStr = fieldSet.readString("transactionTime").trim();
            
            if (!dateStr.isEmpty() && dateStr.length() == 8 && 
                !timeStr.isEmpty() && timeStr.length() == 6) {
                try {
                    LocalDate date = LocalDate.parse(dateStr, COBOL_DATE_FORMAT);
                    int hour = Integer.parseInt(timeStr.substring(0, 2));
                    int minute = Integer.parseInt(timeStr.substring(2, 4));
                    int second = Integer.parseInt(timeStr.substring(4, 6));
                    
                    record.setTransactionTimestamp(date.atTime(hour, minute, second));
                } catch (Exception e) {
                    logger.warn("Invalid date/time format in transaction file: {} {}", dateStr, timeStr);
                    record.setTransactionTimestamp(LocalDateTime.now());
                }
            } else {
                record.setTransactionTimestamp(LocalDateTime.now());
            }
            
            return record;
        };

        DefaultLineMapper<TransactionFileRecord> lineMapper = new DefaultLineMapper<>();
        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fieldSetMapper);

        FlatFileItemReader<TransactionFileRecord> reader = new FlatFileItemReaderBuilder<TransactionFileRecord>()
                .name("transactionFileReader")
                .resource(resource)
                .lineMapper(lineMapper)
                .linesToSkip(0)
                .strict(true)
                .build();

        logger.debug("Transaction file reader configured for file: {} with {} fields", fileName, fieldNames.length);
        return reader;
    }

    /**
     * Creates a generic JDBC-based ItemReader with custom SQL query support.
     * Provides high-performance database reading with pagination and parameter binding.
     * 
     * Useful for complex queries that require:
     * - Custom joins across multiple tables
     * - Complex filtering conditions
     * - Aggregated data processing
     * - Performance-optimized SQL with custom indexes
     * 
     * @param sqlQuery Custom SQL query string
     * @param parameters Query parameters map
     * @param rowMapper Row mapper for result set conversion
     * @param sortKey Column name for sorting/pagination
     * @return Configured JdbcPagingItemReader
     */
    public <T> JdbcPagingItemReader<T> createJdbcReader(String sqlQuery, Map<String, Object> parameters,
                                                        org.springframework.jdbc.core.RowMapper<T> rowMapper,
                                                        String sortKey) {
        logger.info("Creating JDBC reader with custom query and sort key: {}", sortKey);

        try {
            SqlPagingQueryProviderFactoryBean queryProviderFactory = new SqlPagingQueryProviderFactoryBean();
            queryProviderFactory.setDataSource(dataSource);
            queryProviderFactory.setSelectClause(sqlQuery);
            queryProviderFactory.setSortKey(sortKey);
            queryProviderFactory.afterPropertiesSet();

            PagingQueryProvider queryProvider = queryProviderFactory.getObject();

            JdbcPagingItemReader<T> reader = new JdbcPagingItemReaderBuilder<T>()
                    .name("jdbcReader")
                    .dataSource(dataSource)
                    .queryProvider(queryProvider)
                    .parameterValues(parameters != null ? parameters : Collections.emptyMap())
                    .pageSize(pageSize)
                    .rowMapper(rowMapper)
                    .build();

            logger.debug("JDBC reader configured with page size: {} and sort key: {}", pageSize, sortKey);
            return reader;

        } catch (Exception e) {
            logger.error("Failed to create JDBC reader: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to create JDBC reader", e);
        }
    }

    // Helper methods for COBOL data type conversions

    /**
     * Parses COBOL date format (YYYYMMDD) from field set and sets value using consumer.
     */
    private void parseCobolDate(org.springframework.batch.item.file.transform.FieldSet fieldSet, 
                               String fieldName, java.util.function.Consumer<LocalDate> setter) {
        String dateStr = fieldSet.readString(fieldName).trim();
        if (!dateStr.isEmpty() && dateStr.length() == 8) {
            try {
                setter.accept(LocalDate.parse(dateStr, COBOL_DATE_FORMAT));
            } catch (Exception e) {
                logger.warn("Invalid date format in field {}: {}", fieldName, dateStr);
                setter.accept(null);
            }
        } else {
            setter.accept(null);
        }
    }

    /**
     * Parses COBOL COMP-3 equivalent decimal values with 2-decimal precision.
     * Maintains exact numeric precision as required by Section 0.1.3.
     */
    private void parseCobolDecimal(org.springframework.batch.item.file.transform.FieldSet fieldSet,
                                  String fieldName, java.util.function.Consumer<BigDecimal> setter) {
        String decimalStr = fieldSet.readString(fieldName).trim();
        if (!decimalStr.isEmpty()) {
            try {
                // Remove any leading zeros and handle decimal positioning
                BigDecimal value = new BigDecimal(decimalStr).setScale(2, BigDecimal.ROUND_HALF_UP);
                setter.accept(value);
            } catch (NumberFormatException e) {
                logger.warn("Invalid decimal format in field {}: {}", fieldName, decimalStr);
                setter.accept(BigDecimal.ZERO);
            }
        } else {
            setter.accept(BigDecimal.ZERO);
        }
    }

    // Inner classes for file record DTOs

    /**
     * Data Transfer Object for customer file records matching CUSTREC.cpy layout.
     */
    public static class CustomerFileRecord {
        private String customerId;
        private String customerName;
        private String customerAddress;
        private String customerPhone;
        private String customerEmail;
        private LocalDate dateOfBirth;
        private String ssn;
        private Integer ficoScore;

        // Getters and setters
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        
        public String getCustomerAddress() { return customerAddress; }
        public void setCustomerAddress(String customerAddress) { this.customerAddress = customerAddress; }
        
        public String getCustomerPhone() { return customerPhone; }
        public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
        
        public String getCustomerEmail() { return customerEmail; }
        public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
        
        public LocalDate getDateOfBirth() { return dateOfBirth; }
        public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
        
        public String getSsn() { return ssn; }
        public void setSsn(String ssn) { this.ssn = ssn; }
        
        public Integer getFicoScore() { return ficoScore; }
        public void setFicoScore(Integer ficoScore) { this.ficoScore = ficoScore; }
    }

    /**
     * Data Transfer Object for account file records matching CVACT01Y.cpy layout.
     */
    public static class AccountFileRecord {
        private String accountId;
        private String customerId;
        private String accountStatus;
        private LocalDate accountOpenDate;
        private BigDecimal currentBalance;
        private BigDecimal creditLimit;
        private LocalDate expiryDate;

        // Getters and setters
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        
        public String getAccountStatus() { return accountStatus; }
        public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }
        
        public LocalDate getAccountOpenDate() { return accountOpenDate; }
        public void setAccountOpenDate(LocalDate accountOpenDate) { this.accountOpenDate = accountOpenDate; }
        
        public BigDecimal getCurrentBalance() { return currentBalance; }
        public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }
        
        public BigDecimal getCreditLimit() { return creditLimit; }
        public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }
        
        public LocalDate getExpiryDate() { return expiryDate; }
        public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    }

    /**
     * Data Transfer Object for transaction file records matching CVTRA01Y.cpy layout.
     */
    public static class TransactionFileRecord {
        private String transactionId;
        private String cardNumber;
        private BigDecimal transactionAmount;
        private String merchantName;
        private String merchantCity;
        private String merchantZip;
        private LocalDateTime transactionTimestamp;
        private String transactionType;
        private String transactionCategory;

        // Getters and setters
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        
        public String getCardNumber() { return cardNumber; }
        public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
        
        public BigDecimal getTransactionAmount() { return transactionAmount; }
        public void setTransactionAmount(BigDecimal transactionAmount) { this.transactionAmount = transactionAmount; }
        
        public String getMerchantName() { return merchantName; }
        public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
        
        public String getMerchantCity() { return merchantCity; }
        public void setMerchantCity(String merchantCity) { this.merchantCity = merchantCity; }
        
        public String getMerchantZip() { return merchantZip; }
        public void setMerchantZip(String merchantZip) { this.merchantZip = merchantZip; }
        
        public LocalDateTime getTransactionTimestamp() { return transactionTimestamp; }
        public void setTransactionTimestamp(LocalDateTime transactionTimestamp) { this.transactionTimestamp = transactionTimestamp; }
        
        public String getTransactionType() { return transactionType; }
        public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
        
        public String getTransactionCategory() { return transactionCategory; }
        public void setTransactionCategory(String transactionCategory) { this.transactionCategory = transactionCategory; }
    }
}
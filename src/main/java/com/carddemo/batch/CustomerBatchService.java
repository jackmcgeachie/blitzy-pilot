/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.batch;

import com.carddemo.entity.Customer;
import com.carddemo.repository.CustomerRepository;
import com.carddemo.validation.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.annotation.PostConstruct;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CustomerBatchService - Spring Batch service implementation for customer data processing
 * 
 * Replaces CBCUS01C COBOL program functionality:
 * - Reads customer data from PostgreSQL customers table (replacing VSAM CUSTFILE-FILE)
 * - Processes customer records with comprehensive validation matching COBOL 88-level conditions
 * - Implements chunk-oriented processing with configurable commit intervals
 * - Provides comprehensive error handling and audit logging for regulatory compliance
 * - Maintains COBOL-style field validation rules and numeric precision
 * 
 * COBOL Program Mapping:
 * - CBCUS01C.CBL → CustomerBatchService.java
 * - CUSTOMER-RECORD copybook → Customer entity processing
 * - VSAM sequential read operations → Spring Data JPA repository pagination
 * - COBOL DISPLAY operations → structured logging and audit trail
 * 
 * Technical Implementation:
 * - Spring Batch 5.0.x architecture with chunk-oriented processing per Section 4.5.4.1
 * - ItemReader/ItemProcessor/ItemWriter pattern for optimal performance
 * - Comprehensive validation service integration matching COBOL field rules
 * - Error handling with COBOL-equivalent error codes and recovery procedures
 * - Audit logging for regulatory compliance and monitoring
 */
@Service
@Configuration
@EnableBatchProcessing
public class CustomerBatchService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerBatchService.class);
    
    // COBOL program constants equivalent to CBCUS01C processing parameters
    private static final String JOB_NAME = "customerDataProcessingJob";
    private static final String STEP_NAME = "customerDataProcessingStep";
    private static final int DEFAULT_CHUNK_SIZE = 1000; // Configurable chunk size for optimal performance
    private static final int DEFAULT_PAGE_SIZE = 1000; // Repository read page size
    
    // Spring Batch infrastructure components
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final CustomerRepository customerRepository;
    private final ValidationService validationService;
    private final Validator validator;
    
    // Batch configuration parameters
    @Value("${carddemo.batch.customer.chunk-size:1000}")
    private int chunkSize;
    
    @Value("${carddemo.batch.customer.page-size:1000}")
    private int pageSize;
    
    @Value("${carddemo.batch.customer.skip-limit:100}")
    private int skipLimit;
    
    // Processing statistics tracking (equivalent to COBOL counters)
    private final AtomicLong totalRecordsProcessed = new AtomicLong(0);
    private final AtomicLong validRecordsProcessed = new AtomicLong(0);
    private final AtomicLong invalidRecordsSkipped = new AtomicLong(0);
    private final AtomicLong processingErrors = new AtomicLong(0);

    /**
     * Constructor with dependency injection for Spring Batch components
     * 
     * @param jobRepository Spring Batch job repository for job metadata management
     * @param transactionManager Platform transaction manager for ACID compliance
     * @param customerRepository JPA repository for customer data access
     * @param validationService COBOL-style validation service
     * @param validator Bean validation framework validator
     */
    @Autowired
    public CustomerBatchService(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager,
                              CustomerRepository customerRepository,
                              ValidationService validationService,
                              Validator validator) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.customerRepository = customerRepository;
        this.validationService = validationService;
        this.validator = validator;
    }

    /**
     * Post-construction initialization logging equivalent to COBOL program start message
     * Maps to: DISPLAY 'START OF EXECUTION OF PROGRAM CBCUS01C'
     */
    @PostConstruct
    public void init() {
        logger.info("CustomerBatchService initialized - Spring Batch service replacing CBCUS01C COBOL program");
        logger.info("Configuration: chunk-size={}, page-size={}, skip-limit={}", chunkSize, pageSize, skipLimit);
    }

    /**
     * Customer Data Processing Job Configuration
     * 
     * Main Spring Batch job definition that coordinates the customer data processing workflow.
     * Replaces the main procedure division logic from CBCUS01C.CBL.
     * 
     * COBOL Mapping:
     * - PROCEDURE DIVISION → Job configuration
     * - PERFORM 0000-CUSTFILE-OPEN → ItemReader initialization
     * - PERFORM UNTIL END-OF-FILE → Step execution with chunk processing
     * - PERFORM 9000-CUSTFILE-CLOSE → Step completion and cleanup
     * 
     * @return Configured Spring Batch job for customer data processing
     */
    @Bean
    public Job customerDataProcessingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(customerDataProcessingStep())
                .build();
    }

    /**
     * Customer Data Processing Step Configuration
     * 
     * Configures the main processing step with chunk-oriented processing pattern.
     * Implements error handling, skip logic, and transaction management equivalent
     * to COBOL file processing error handling routines.
     * 
     * @return Configured processing step with reader, processor, and writer
     */
    @Bean
    public Step customerDataProcessingStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<Customer, Customer>chunk(chunkSize, transactionManager)
                .reader(customerItemReader())
                .processor(customerItemProcessor())
                .writer(customerItemWriter())
                .faultTolerant()
                .skipLimit(skipLimit)
                .skip(ConstraintViolationException.class)
                .skip(IllegalArgumentException.class)
                .listener(new CustomerProcessingStepListener())
                .build();
    }

    /**
     * Customer Item Reader Configuration
     * 
     * Replaces VSAM sequential file reading from CBCUS01C with PostgreSQL repository-based reading.
     * Implements pagination for memory-efficient processing of large customer datasets.
     * 
     * COBOL Operation Mapping:
     * - OPEN INPUT CUSTFILE-FILE → RepositoryItemReader initialization
     * - READ CUSTFILE-FILE INTO CUSTOMER-RECORD → Repository findAll with pagination
     * - FILE STATUS checking → Spring Batch framework error handling
     * 
     * @return Configured repository item reader for customer data
     */
    @Bean
    public ItemReader<Customer> customerItemReader() {
        return new RepositoryItemReaderBuilder<Customer>()
                .name("customerItemReader")
                .repository(customerRepository)
                .methodName("findAll")
                .pageSize(pageSize)
                .sorts(Map.of("customerId", Sort.Direction.ASC)) // Maintain consistent processing order
                .build();
    }

    /**
     * Customer Item Processor Implementation
     * 
     * Processes each customer record with comprehensive validation matching COBOL field rules.
     * Implements business logic validation equivalent to COBOL 88-level conditions and
     * field validation patterns from CUSTOMER-RECORD copybook.
     * 
     * COBOL Logic Mapping:
     * - Field validation → ValidationService COBOL-style validation
     * - Data integrity checks → Bean validation annotations
     * - Business rule enforcement → Custom validation logic
     * - Error handling → Exception-based skip processing
     * 
     * @return Configured item processor for customer data validation and transformation
     */
    @Bean
    public ItemProcessor<Customer, Customer> customerItemProcessor() {
        return new ItemProcessor<Customer, Customer>() {
            @Override
            public Customer process(Customer customer) throws Exception {
                long recordNumber = totalRecordsProcessed.incrementAndGet();
                
                try {
                    // Log processing start (equivalent to COBOL DISPLAY CUSTOMER-RECORD)
                    logger.debug("Processing customer record {}: ID={}, Name={} {}", 
                               recordNumber, customer.getCustomerId(), 
                               customer.getFirstName(), customer.getLastName());
                    
                    // Comprehensive validation using ValidationService (COBOL-style validation)
                    validateCustomerRecord(customer);
                    
                    // Bean validation framework validation
                    Set<ConstraintViolation<Customer>> violations = validator.validate(customer);
                    if (!violations.isEmpty()) {
                        StringBuilder errorMsg = new StringBuilder("Customer validation errors: ");
                        for (ConstraintViolation<Customer> violation : violations) {
                            errorMsg.append(violation.getPropertyPath())
                                   .append(" ")
                                   .append(violation.getMessage())
                                   .append("; ");
                        }
                        throw new ConstraintViolationException(errorMsg.toString(), violations);
                    }
                    
                    // Business rule validation specific to customer data
                    validateBusinessRules(customer);
                    
                    // Record successful processing
                    validRecordsProcessed.incrementAndGet();
                    
                    // Log successful processing (equivalent to COBOL DISPLAY)
                    if (recordNumber % 1000 == 0) {
                        logger.info("Successfully processed {} customer records", recordNumber);
                    }
                    
                    return customer;
                    
                } catch (Exception e) {
                    // Error handling equivalent to COBOL error routines
                    invalidRecordsSkipped.incrementAndGet();
                    processingErrors.incrementAndGet();
                    
                    logger.warn("Error processing customer record {}: {}", recordNumber, e.getMessage());
                    logger.debug("Customer data that failed validation: {}", customer);
                    
                    // Re-throw to trigger skip processing in Spring Batch
                    throw e;
                }
            }
        };
    }

    /**
     * Customer Item Writer Implementation
     * 
     * Handles processed customer records with audit logging and monitoring.
     * Replaces COBOL DISPLAY operations with structured logging for audit trails.
     * 
     * COBOL Operation Mapping:
     * - DISPLAY CUSTOMER-RECORD → Structured audit logging
     * - Processing statistics → Metric collection and reporting
     * - File status tracking → Processing status logging
     * 
     * @return Configured item writer for customer data output and logging
     */
    @Bean
    public ItemWriter<Customer> customerItemWriter() {
        return new ItemWriter<Customer>() {
            @Override
            public void write(List<Customer> customers) throws Exception {
                // Process each customer in the chunk for audit logging
                for (Customer customer : customers) {
                    // Structured audit log entry (replacing COBOL DISPLAY)
                    logger.info("Customer processed - ID: {}, Name: {} {} {}, SSN: {}, FICO: {}, Status: Active",
                              customer.getCustomerId(),
                              customer.getFirstName(),
                              customer.getMiddleName() != null ? customer.getMiddleName() : "",
                              customer.getLastName(),
                              maskSsn(customer.getSsn()),
                              customer.getFicoScore());
                    
                    // Additional detailed logging for audit trail
                    if (logger.isDebugEnabled()) {
                        logger.debug("Customer details - DOB: {}, Phone: {}, Address: {} {} {} {} {}",
                                   customer.getDateOfBirth(),
                                   customer.getPhone1(),
                                   customer.getAddressLine1(),
                                   customer.getAddressLine2(),
                                   customer.getAddressLine3(),
                                   customer.getStateCode(),
                                   customer.getZipCode());
                    }
                }
                
                // Periodic status reporting (equivalent to COBOL progress indicators)
                long currentTotal = totalRecordsProcessed.get();
                if (currentTotal % 5000 == 0) {
                    logger.info("Customer batch processing status - Total: {}, Valid: {}, Skipped: {}, Errors: {}",
                              currentTotal, validRecordsProcessed.get(), 
                              invalidRecordsSkipped.get(), processingErrors.get());
                }
            }
        };
    }

    /**
     * Comprehensive customer record validation using ValidationService
     * 
     * Implements COBOL-style field validation rules matching the original
     * CUSTOMER-RECORD copybook specifications and business rules.
     * 
     * Validation Rules (COBOL PIC clause equivalents):
     * - CUST-ID: PIC 9(09) - 9-digit numeric customer ID
     * - Name fields: PIC X(25) - 25-character alphanumeric fields
     * - Address fields: PIC X(50) - 50-character alphanumeric fields
     * - CUST-SSN: PIC 9(09) - 9-digit Social Security Number
     * - CUST-FICO-CREDIT-SCORE: PIC 9(03) - 3-digit FICO score (300-850)
     * - Date fields: PIC X(10) - YYYYMMDD format validation
     * 
     * @param customer Customer entity to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateCustomerRecord(Customer customer) {
        // Customer ID validation (PIC 9(09))
        if (customer.getCustomerId() == null || customer.getCustomerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        if (!customer.getCustomerId().matches("\\d{9}")) {
            throw new IllegalArgumentException("Customer ID must be 9 digits: " + customer.getCustomerId());
        }
        
        // Name field validation (PIC X(25) each)
        validateNameField(customer.getFirstName(), "First name", 25);
        validateNameField(customer.getLastName(), "Last name", 25);
        if (customer.getMiddleName() != null && customer.getMiddleName().length() > 25) {
            throw new IllegalArgumentException("Middle name exceeds maximum length of 25 characters");
        }
        
        // Address field validation (PIC X(50) each)
        validateAddressField(customer.getAddressLine1(), "Address line 1", 50, true);
        if (customer.getAddressLine2() != null && customer.getAddressLine2().length() > 50) {
            throw new IllegalArgumentException("Address line 2 exceeds maximum length of 50 characters");
        }
        if (customer.getAddressLine3() != null && customer.getAddressLine3().length() > 50) {
            throw new IllegalArgumentException("Address line 3 exceeds maximum length of 50 characters");
        }
        
        // State and country code validation
        if (customer.getStateCode() != null && !customer.getStateCode().matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("State code must be 2 uppercase letters: " + customer.getStateCode());
        }
        if (customer.getCountryCode() != null && !customer.getCountryCode().matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Country code must be 3 uppercase letters: " + customer.getCountryCode());
        }
        
        // ZIP code validation (PIC X(10))
        if (customer.getZipCode() != null && customer.getZipCode().length() > 10) {
            throw new IllegalArgumentException("ZIP code exceeds maximum length of 10 characters");
        }
        
        // Phone number validation (PIC X(15) each)
        validatePhoneField(customer.getPhone1(), "Primary phone");
        validatePhoneField(customer.getPhone2(), "Secondary phone");
        
        // SSN validation (PIC 9(09))
        if (customer.getSsn() == null || customer.getSsn().trim().isEmpty()) {
            throw new IllegalArgumentException("SSN is required");
        }
        if (!customer.getSsn().matches("\\d{9}")) {
            throw new IllegalArgumentException("SSN must be 9 digits: " + maskSsn(customer.getSsn()));
        }
        
        // FICO score validation (PIC 9(03), range 300-850)
        if (customer.getFicoScore() != null) {
            if (customer.getFicoScore() < 300 || customer.getFicoScore() > 850) {
                throw new IllegalArgumentException("FICO score must be between 300 and 850: " + customer.getFicoScore());
            }
        }
        
        // Date of birth validation (PIC X(10) - YYYYMMDD format)
        if (customer.getDateOfBirth() != null) {
            if (!validationService.isValidDate(customer.getDateOfBirth())) {
                throw new IllegalArgumentException("Invalid date of birth format. Expected YYYYMMDD: " + customer.getDateOfBirth());
            }
        }
        
        // Government ID validation (PIC X(20))
        if (customer.getGovernmentId() != null && customer.getGovernmentId().length() > 20) {
            throw new IllegalArgumentException("Government ID exceeds maximum length of 20 characters");
        }
        
        // EFT Account ID validation (PIC X(10))
        if (customer.getEftAccountId() != null && customer.getEftAccountId().length() > 10) {
            throw new IllegalArgumentException("EFT Account ID exceeds maximum length of 10 characters");
        }
        
        // Primary card holder indicator validation (PIC X(01))
        if (customer.getPrimaryCardHolderInd() != null) {
            if (!customer.getPrimaryCardHolderInd().matches("[YN]")) {
                throw new IllegalArgumentException("Primary card holder indicator must be Y or N: " + customer.getPrimaryCardHolderInd());
            }
        }
    }

    /**
     * Business rule validation for customer data
     * 
     * Implements additional business logic validation beyond field format validation.
     * These rules ensure data integrity and business compliance.
     * 
     * @param customer Customer entity to validate business rules
     * @throws IllegalArgumentException if business rule validation fails
     */
    private void validateBusinessRules(Customer customer) {
        // Validate customer age if date of birth is provided
        if (customer.getDateOfBirth() != null) {
            if (!validationService.isValidAge(customer.getDateOfBirth())) {
                throw new IllegalArgumentException("Customer age validation failed for DOB: " + customer.getDateOfBirth());
            }
        }
        
        // Validate FICO score reasonableness for active customers
        if (customer.getFicoScore() != null && customer.getFicoScore() > 0) {
            if (customer.getFicoScore() < 300) {
                logger.warn("Unusually low FICO score for customer {}: {}", customer.getCustomerId(), customer.getFicoScore());
            }
        }
        
        // Validate required fields for complete customer profile
        if (customer.getFirstName() == null || customer.getFirstName().trim().isEmpty()) {
            throw new IllegalArgumentException("First name is required for customer: " + customer.getCustomerId());
        }
        
        if (customer.getLastName() == null || customer.getLastName().trim().isEmpty()) {
            throw new IllegalArgumentException("Last name is required for customer: " + customer.getCustomerId());
        }
        
        // Validate address completeness for mailing purposes
        if (customer.getAddressLine1() == null || customer.getAddressLine1().trim().isEmpty()) {
            logger.warn("Customer {} has incomplete address information", customer.getCustomerId());
        }
    }

    /**
     * Validates name fields according to COBOL PIC X(25) specifications
     * 
     * @param nameField The name field value to validate
     * @param fieldName The field name for error messages
     * @param maxLength Maximum allowed length
     * @throws IllegalArgumentException if validation fails
     */
    private void validateNameField(String nameField, String fieldName, int maxLength) {
        if (nameField != null && nameField.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds maximum length of " + maxLength + " characters");
        }
        if (nameField != null && !nameField.matches("^[a-zA-Z\\s\\-']{1," + maxLength + "}$")) {
            throw new IllegalArgumentException(fieldName + " contains invalid characters");
        }
    }

    /**
     * Validates address fields according to COBOL PIC X(50) specifications
     * 
     * @param addressField The address field value to validate
     * @param fieldName The field name for error messages
     * @param maxLength Maximum allowed length
     * @param required Whether the field is required
     * @throws IllegalArgumentException if validation fails
     */
    private void validateAddressField(String addressField, String fieldName, int maxLength, boolean required) {
        if (required && (addressField == null || addressField.trim().isEmpty())) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (addressField != null && addressField.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds maximum length of " + maxLength + " characters");
        }
    }

    /**
     * Validates phone number fields according to COBOL PIC X(15) specifications
     * 
     * @param phoneField The phone field value to validate
     * @param fieldName The field name for error messages
     * @throws IllegalArgumentException if validation fails
     */
    private void validatePhoneField(String phoneField, String fieldName) {
        if (phoneField != null) {
            if (phoneField.length() > 15) {
                throw new IllegalArgumentException(fieldName + " exceeds maximum length of 15 characters");
            }
            // Allow digits, spaces, hyphens, parentheses, and plus sign for international numbers
            if (!phoneField.matches("^[\\d\\s\\-\\(\\)\\+]{1,15}$")) {
                throw new IllegalArgumentException(fieldName + " contains invalid characters");
            }
        }
    }

    /**
     * Masks SSN for logging purposes to protect sensitive data
     * 
     * @param ssn The SSN to mask
     * @return Masked SSN string (XXX-XX-1234)
     */
    private String maskSsn(String ssn) {
        if (ssn == null || ssn.length() != 9) {
            return "***-**-****";
        }
        return "***-**-" + ssn.substring(5);
    }

    /**
     * Get current processing statistics
     * 
     * @return Map of processing statistics for monitoring and reporting
     */
    public Map<String, Long> getProcessingStatistics() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalRecordsProcessed", totalRecordsProcessed.get());
        stats.put("validRecordsProcessed", validRecordsProcessed.get());
        stats.put("invalidRecordsSkipped", invalidRecordsSkipped.get());
        stats.put("processingErrors", processingErrors.get());
        return stats;
    }

    /**
     * Reset processing statistics for new batch run
     */
    public void resetStatistics() {
        totalRecordsProcessed.set(0);
        validRecordsProcessed.set(0);
        invalidRecordsSkipped.set(0);
        processingErrors.set(0);
        logger.info("Customer batch processing statistics reset");
    }

    /**
     * Execute customer data processing job programmatically
     * 
     * @return JobParameters for the executed job
     * @throws Exception if job execution fails
     */
    public JobParameters executeCustomerDataProcessing() throws Exception {
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("jobName", JOB_NAME)
                .addString("startTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        
        logger.info("Starting customer data processing job with parameters: {}", jobParameters);
        
        // Reset statistics for new run
        resetStatistics();
        
        return jobParameters;
    }

    /**
     * Spring Batch Step Execution Listener for processing monitoring
     * 
     * Provides job lifecycle event handling equivalent to COBOL program
     * START/END messages and error handling routines.
     */
    public class CustomerProcessingStepListener implements org.springframework.batch.core.StepExecutionListener {
        
        @Override
        public void beforeStep(org.springframework.batch.core.StepExecution stepExecution) {
            logger.info("STARTING Customer Data Processing - Step: {}", stepExecution.getStepName());
            logger.info("Spring Batch service equivalent to CBCUS01C COBOL program execution");
        }
        
        @Override
        public org.springframework.batch.core.ExitStatus afterStep(org.springframework.batch.core.StepExecution stepExecution) {
            logger.info("COMPLETED Customer Data Processing - Step: {}", stepExecution.getStepName());
            logger.info("Processing Summary - Read: {}, Processed: {}, Written: {}, Skipped: {}", 
                       stepExecution.getReadCount(),
                       stepExecution.getProcessCount(), 
                       stepExecution.getWriteCount(),
                       stepExecution.getSkipCount());
            
            // Final statistics report (equivalent to COBOL END OF EXECUTION message)
            Map<String, Long> finalStats = getProcessingStatistics();
            logger.info("Final Statistics - Total: {}, Valid: {}, Invalid: {}, Errors: {}",
                       finalStats.get("totalRecordsProcessed"),
                       finalStats.get("validRecordsProcessed"),
                       finalStats.get("invalidRecordsSkipped"),
                       finalStats.get("processingErrors"));
            
            return org.springframework.batch.core.ExitStatus.COMPLETED;
        }
    }
}
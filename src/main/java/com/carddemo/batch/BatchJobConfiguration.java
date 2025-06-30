/*
 * Copyright 2024 Amazon.com, Inc. or its affiliates.
 * All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.carddemo.batch;

import com.carddemo.audit.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Centralized Spring Batch Configuration for Card Demo Application
 * 
 * This configuration class serves as the foundation for all batch processing jobs,
 * providing common utilities, error handling, and resource management capabilities
 * that replace the COBOL utility programs (CSUTLDTC.cbl) and shared copybooks.
 * 
 * Key Features:
 * - Kubernetes CronJob integration with automatic retry and failure recovery
 * - Comprehensive audit logging for regulatory compliance
 * - COBOL-equivalent date validation and processing utilities
 * - Resource management for dedicated batch processing pods
 * - Chunk-oriented processing configuration
 * - Common error handling and recovery procedures
 * 
 * Migrated from: app/cbl/CSUTLDTC.cbl, app/cpy/CSUTLDPY.cpy, app/cpy/CSUTLDWY.cpy
 * 
 * @author Blitzy agent - CardDemo Batch Configuration
 * @version 1.0
 * @since Spring Boot 3.2.x, Spring Batch 5.0.x
 */
@Configuration
@EnableBatchProcessing
@EnableRetry
public class BatchJobConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(BatchJobConfiguration.class);
    
    // Date format constants matching COBOL date validation (CSUTLDWY.cpy)
    private static final String DATE_FORMAT_YYYYMMDD = "yyyyMMdd";
    private static final String DATE_FORMAT_CCYYMMDD = "yyyyMMdd";
    private static final DateTimeFormatter COBOL_DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT_YYYYMMDD);
    
    // COBOL-equivalent severity codes from CSUTLDTC.cbl feedback structure
    private static final int SEVERITY_SUCCESS = 0;
    private static final int SEVERITY_WARNING = 1;
    private static final int SEVERITY_ERROR = 2;
    private static final int SEVERITY_SEVERE = 3;
    
    // Date validation constants matching COBOL 88-levels from CSUTLDWY.cpy
    private static final int THIS_CENTURY = 20;
    private static final int LAST_CENTURY = 19;
    private static final int MIN_VALID_MONTH = 1;
    private static final int MAX_VALID_MONTH = 12;
    private static final int MIN_VALID_DAY = 1;
    private static final int MAX_VALID_DAY = 31;
    
    // Batch processing configuration parameters
    @Value("${carddemo.batch.chunk-size:1000}")
    private int defaultChunkSize;
    
    @Value("${carddemo.batch.skip-limit:10}")
    private int defaultSkipLimit;
    
    @Value("${carddemo.batch.retry-limit:3}")
    private int defaultRetryLimit;
    
    @Value("${carddemo.batch.thread-pool-size:4}")
    private int batchThreadPoolSize;
    
    @Value("${carddemo.batch.max-thread-pool-size:8}")
    private int batchMaxThreadPoolSize;
    
    @Value("${carddemo.batch.queue-capacity:100}")
    private int batchQueueCapacity;
    
    @Value("${carddemo.batch.keep-alive-seconds:60}")
    private int batchKeepAliveSeconds;
    
    @Autowired
    private JobRepository jobRepository;
    
    @Autowired
    private PlatformTransactionManager transactionManager;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @Autowired(required = false)
    private AuditService auditService;
    
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Primary Task Executor for Batch Processing
     * 
     * Configures a dedicated thread pool for batch operations with resource limits
     * suitable for Kubernetes pod deployment and high-volume transaction processing.
     * 
     * Based on Section 4.5.4.1 Resource Management requirements.
     */
    @Bean("batchTaskExecutor")
    @Primary
    public TaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(batchThreadPoolSize);
        executor.setMaxPoolSize(batchMaxThreadPoolSize);
        executor.setQueueCapacity(batchQueueCapacity);
        executor.setKeepAliveSeconds(batchKeepAliveSeconds);
        executor.setThreadNamePrefix("CardDemo-Batch-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        
        logger.info("Initialized batch task executor - Core: {}, Max: {}, Queue: {}", 
                   batchThreadPoolSize, batchMaxThreadPoolSize, batchQueueCapacity);
        
        return executor;
    }

    /**
     * Retry Template for Batch Operations
     * 
     * Provides exponential backoff retry policy for transient failures,
     * supporting Kubernetes automatic retry and failure recovery requirements.
     */
    @Bean("batchRetryTemplate")
    public RetryTemplate batchRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Simple retry policy - retry on specific exceptions
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(defaultRetryLimit);
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(org.springframework.dao.TransientDataAccessException.class, true);
        retryableExceptions.put(org.springframework.dao.CannotAcquireLockException.class, true);
        retryableExceptions.put(java.sql.SQLTransientException.class, true);
        retryPolicy.setRetryableExceptions(retryableExceptions);
        
        // Exponential backoff policy
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000); // 1 second
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(30000); // 30 seconds
        
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        logger.info("Configured batch retry template - Max attempts: {}, Initial interval: 1s", 
                   defaultRetryLimit);
        
        return retryTemplate;
    }

    /**
     * Common Job Execution Listener
     * 
     * Provides comprehensive audit logging for all batch jobs, ensuring regulatory
     * compliance through detailed execution tracking and performance monitoring.
     */
    @Bean("commonJobExecutionListener")
    public JobExecutionListener commonJobExecutionListener() {
        return new JobExecutionListener() {
            @Override
            public void beforeJob(JobExecution jobExecution) {
                String jobName = jobExecution.getJobInstance().getJobName();
                LocalDateTime startTime = LocalDateTime.now();
                
                // Log job start with audit trail
                logger.info("BATCH-START: Job '{}' starting at {} with parameters: {}", 
                           jobName, startTime, jobExecution.getJobParameters());
                
                // Create audit record if AuditService is available
                if (auditService != null) {
                    try {
                        Map<String, Object> auditData = new HashMap<>();
                        auditData.put("jobName", jobName);
                        auditData.put("startTime", startTime);
                        auditData.put("parameters", jobExecution.getJobParameters().getParameters());
                        auditData.put("executionId", jobExecution.getId());
                        
                        auditService.auditBatchJobStart(jobName, objectMapper.writeValueAsString(auditData));
                    } catch (Exception e) {
                        logger.warn("Failed to create audit record for job start: {}", e.getMessage());
                    }
                }
                
                // Set custom execution context attributes
                ExecutionContext executionContext = jobExecution.getExecutionContext();
                executionContext.putString("jobStartTime", startTime.toString());
                executionContext.putString("kubernetesJobId", generateKubernetesJobId());
                
                // Record metrics
                if (meterRegistry != null) {
                    meterRegistry.counter("batch.job.started", "job", jobName).increment();
                }
            }
            
            @Override
            public void afterJob(JobExecution jobExecution) {
                String jobName = jobExecution.getJobInstance().getJobName();
                LocalDateTime endTime = LocalDateTime.now();
                BatchStatus status = jobExecution.getStatus();
                long duration = jobExecution.getEndTime().getTime() - jobExecution.getStartTime().getTime();
                
                // Log job completion with performance metrics
                logger.info("BATCH-END: Job '{}' completed at {} with status: {} (Duration: {}ms)", 
                           jobName, endTime, status, duration);
                
                // Log any failure details
                if (status == BatchStatus.FAILED) {
                    jobExecution.getAllFailureExceptions().forEach(throwable -> 
                        logger.error("BATCH-ERROR: Job '{}' failure: {}", jobName, throwable.getMessage(), throwable)
                    );
                }
                
                // Create completion audit record
                if (auditService != null) {
                    try {
                        Map<String, Object> auditData = new HashMap<>();
                        auditData.put("jobName", jobName);
                        auditData.put("endTime", endTime);
                        auditData.put("status", status.toString());
                        auditData.put("duration", duration);
                        auditData.put("readCount", jobExecution.getStepExecutions().stream()
                                .mapToLong(StepExecution::getReadCount).sum());
                        auditData.put("writeCount", jobExecution.getStepExecutions().stream()
                                .mapToLong(StepExecution::getWriteCount).sum());
                        auditData.put("skipCount", jobExecution.getStepExecutions().stream()
                                .mapToLong(StepExecution::getSkipCount).sum());
                        
                        auditService.auditBatchJobEnd(jobName, objectMapper.writeValueAsString(auditData));
                    } catch (Exception e) {
                        logger.warn("Failed to create audit record for job completion: {}", e.getMessage());
                    }
                }
                
                // Record completion metrics
                if (meterRegistry != null) {
                    meterRegistry.counter("batch.job.completed", "job", jobName, "status", status.toString()).increment();
                    meterRegistry.timer("batch.job.duration", "job", jobName).record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            }
        };
    }

    /**
     * Common Step Execution Listener
     * 
     * Provides detailed step-level monitoring and error tracking for comprehensive
     * debugging and performance analysis of batch operations.
     */
    @Bean("commonStepExecutionListener")
    public StepExecutionListener commonStepExecutionListener() {
        return new StepExecutionListener() {
            @Override
            public void beforeStep(StepExecution stepExecution) {
                String stepName = stepExecution.getStepName();
                logger.info("STEP-START: Step '{}' starting", stepName);
                
                if (meterRegistry != null) {
                    meterRegistry.counter("batch.step.started", "step", stepName).increment();
                }
            }
            
            @Override
            public ExitStatus afterStep(StepExecution stepExecution) {
                String stepName = stepExecution.getStepName();
                BatchStatus status = stepExecution.getStatus();
                long readCount = stepExecution.getReadCount();
                long writeCount = stepExecution.getWriteCount();
                long skipCount = stepExecution.getSkipCount();
                
                logger.info("STEP-END: Step '{}' completed with status: {} (Read: {}, Write: {}, Skip: {})", 
                           stepName, status, readCount, writeCount, skipCount);
                
                if (meterRegistry != null) {
                    meterRegistry.counter("batch.step.completed", "step", stepName, "status", status.toString()).increment();
                    meterRegistry.counter("batch.step.records.read", "step", stepName).increment(readCount);
                    meterRegistry.counter("batch.step.records.written", "step", stepName).increment(writeCount);
                    meterRegistry.counter("batch.step.records.skipped", "step", stepName).increment(skipCount);
                }
                
                return stepExecution.getExitStatus();
            }
        };
    }

    /**
     * COBOL Date Validation Utilities
     * 
     * Replicates the date validation logic from CSUTLDTC.cbl and related copybooks,
     * providing identical date handling capabilities for batch processing operations.
     * 
     * This utility class maintains compatibility with COBOL date formats and validation
     * rules, ensuring consistent behavior during the mainframe migration.
     */
    public static class CobolDateUtilities {
        
        private static final Logger dateLogger = LoggerFactory.getLogger(CobolDateUtilities.class);
        
        /**
         * Date Validation Result Structure
         * 
         * Replicates the FEEDBACK-CODE structure from CSUTLDTC.cbl
         */
        public static class DateValidationResult {
            private final int severityCode;
            private final int messageCode;
            private final String result;
            private final String testDate;
            private final String dateFormat;
            
            public DateValidationResult(int severityCode, int messageCode, String result, 
                                      String testDate, String dateFormat) {
                this.severityCode = severityCode;
                this.messageCode = messageCode;
                this.result = result;
                this.testDate = testDate;
                this.dateFormat = dateFormat;
            }
            
            public boolean isValid() { return severityCode == SEVERITY_SUCCESS; }
            public int getSeverityCode() { return severityCode; }
            public int getMessageCode() { return messageCode; }
            public String getResult() { return result; }
            public String getTestDate() { return testDate; }
            public String getDateFormat() { return dateFormat; }
            
            @Override
            public String toString() {
                return String.format("Sev: %04d Mesg Code: %04d %s TstDate: %s Mask used: %s", 
                                   severityCode, messageCode, result, testDate, dateFormat);
            }
        }
        
        /**
         * Validates COBOL-style date format (YYYYMMDD)
         * 
         * Replicates the EDIT-DATE-CCYYMMDD paragraph from CSUTLDPY.cpy,
         * including century validation, month validation, day validation,
         * and leap year calculations.
         * 
         * @param dateString Date string in YYYYMMDD format
         * @return DateValidationResult with COBOL-equivalent feedback
         */
        public static DateValidationResult validateDate(String dateString) {
            return validateDate(dateString, DATE_FORMAT_CCYYMMDD);
        }
        
        /**
         * Validates date with specified format
         * 
         * Main validation method replicating CEEDAYS API call from CSUTLDTC.cbl
         * 
         * @param dateString Date string to validate
         * @param format Date format mask
         * @return DateValidationResult with validation outcome
         */
        public static DateValidationResult validateDate(String dateString, String format) {
            if (dateString == null || dateString.trim().isEmpty()) {
                return new DateValidationResult(SEVERITY_ERROR, 9001, "Insufficient", 
                                              dateString, format);
            }
            
            // Trim and pad date string to expected length
            String cleanDate = dateString.trim();
            if (cleanDate.length() != 8) {
                return new DateValidationResult(SEVERITY_ERROR, 9002, "Bad Pic String", 
                                              dateString, format);
            }
            
            // Validate numeric content
            if (!cleanDate.matches("\\d{8}")) {
                return new DateValidationResult(SEVERITY_ERROR, 9003, "Nonnumeric data", 
                                              dateString, format);
            }
            
            try {
                // Extract date components (matching CSUTLDWY.cpy structure)
                int year = Integer.parseInt(cleanDate.substring(0, 4));
                int month = Integer.parseInt(cleanDate.substring(4, 6));
                int day = Integer.parseInt(cleanDate.substring(6, 8));
                
                // Century validation (matching EDIT-YEAR-CCYY from CSUTLDPY.cpy)
                int century = year / 100;
                if (century != THIS_CENTURY && century != LAST_CENTURY) {
                    return new DateValidationResult(SEVERITY_ERROR, 9004, "Invalid Era", 
                                                  dateString, format);
                }
                
                // Month validation (matching EDIT-MONTH from CSUTLDPY.cpy)
                if (month < MIN_VALID_MONTH || month > MAX_VALID_MONTH) {
                    return new DateValidationResult(SEVERITY_ERROR, 9005, "Invalid month", 
                                                  dateString, format);
                }
                
                // Day validation (matching EDIT-DAY from CSUTLDPY.cpy)
                if (day < MIN_VALID_DAY || day > MAX_VALID_DAY) {
                    return new DateValidationResult(SEVERITY_ERROR, 9006, "Day not valid", 
                                                  dateString, format);
                }
                
                // Advanced day/month/year validation (matching EDIT-DAY-MONTH-YEAR)
                DateValidationResult monthDayResult = validateMonthDayYear(year, month, day, dateString, format);
                if (!monthDayResult.isValid()) {
                    return monthDayResult;
                }
                
                // Final validation using Java LocalDate (equivalent to CEEDAYS validation)
                LocalDate.parse(cleanDate, COBOL_DATE_FORMATTER);
                
                // Validate against current date for date-of-birth scenarios
                DateValidationResult birthResult = validateDateOfBirth(cleanDate, format);
                if (!birthResult.isValid()) {
                    return birthResult;
                }
                
                return new DateValidationResult(SEVERITY_SUCCESS, 0, "Date is valid", 
                                              dateString, format);
                
            } catch (DateTimeParseException e) {
                return new DateValidationResult(SEVERITY_ERROR, 9007, "Date is invalid", 
                                              dateString, format);
            } catch (NumberFormatException e) {
                return new DateValidationResult(SEVERITY_ERROR, 9003, "Nonnumeric data", 
                                              dateString, format);
            }
        }
        
        /**
         * Month/Day/Year Cross-Validation
         * 
         * Implements the month/day combinations validation from EDIT-DAY-MONTH-YEAR
         * including 31-day month validation, February validation, and leap year calculations.
         */
        private static DateValidationResult validateMonthDayYear(int year, int month, int day, 
                                                               String dateString, String format) {
            // Check for 31-day months (matching WS-31-DAY-MONTH from CSUTLDWY.cpy)
            boolean is31DayMonth = (month == 1 || month == 3 || month == 5 || month == 7 || 
                                   month == 8 || month == 10 || month == 12);
            
            if (!is31DayMonth && day == 31) {
                return new DateValidationResult(SEVERITY_ERROR, 9008, "Cannot have 31 days in this month", 
                                              dateString, format);
            }
            
            // February validation (matching WS-FEBRUARY from CSUTLDWY.cpy)
            if (month == 2) {
                if (day == 30) {
                    return new DateValidationResult(SEVERITY_ERROR, 9009, "Cannot have 30 days in this month", 
                                                  dateString, format);
                }
                
                // Leap year calculation for February 29th (matching CSUTLDPY.cpy logic)
                if (day == 29) {
                    boolean isLeapYear = isLeapYear(year);
                    if (!isLeapYear) {
                        return new DateValidationResult(SEVERITY_ERROR, 9010, 
                                                      "Not a leap year.Cannot have 29 days in this month", 
                                                      dateString, format);
                    }
                }
            }
            
            return new DateValidationResult(SEVERITY_SUCCESS, 0, "Month/Day/Year valid", 
                                          dateString, format);
        }
        
        /**
         * Leap Year Calculation
         * 
         * Replicates the leap year logic from CSUTLDPY.cpy including the
         * century year special case (divisible by 400 vs divisible by 4).
         */
        private static boolean isLeapYear(int year) {
            // Matching COBOL logic: WS-EDIT-DATE-YY-N = 0 means century year
            if (year % 100 == 0) {
                return year % 400 == 0;  // Century years must be divisible by 400
            } else {
                return year % 4 == 0;    // Non-century years divisible by 4
            }
        }
        
        /**
         * Date of Birth Validation
         * 
         * Implements EDIT-DATE-OF-BIRTH logic ensuring dates are not in the future,
         * replicating the "time travel was not possible" business rule.
         */
        private static DateValidationResult validateDateOfBirth(String dateString, String format) {
            try {
                LocalDate testDate = LocalDate.parse(dateString, COBOL_DATE_FORMATTER);
                LocalDate currentDate = LocalDate.now();
                
                if (testDate.isAfter(currentDate)) {
                    return new DateValidationResult(SEVERITY_ERROR, 9011, "cannot be in the future", 
                                                  dateString, format);
                }
                
                return new DateValidationResult(SEVERITY_SUCCESS, 0, "Date of birth valid", 
                                              dateString, format);
                
            } catch (Exception e) {
                return new DateValidationResult(SEVERITY_ERROR, 9007, "Date is invalid", 
                                              dateString, format);
            }
        }
        
        /**
         * Format date for COBOL compatibility
         * 
         * Converts LocalDate to COBOL YYYYMMDD format
         */
        public static String formatDateForCobol(LocalDate date) {
            return date.format(COBOL_DATE_FORMATTER);
        }
        
        /**
         * Parse COBOL date string to LocalDate
         * 
         * Safely converts COBOL YYYYMMDD format to Java LocalDate
         */
        public static LocalDate parseCobolDate(String dateString) {
            DateValidationResult validation = validateDate(dateString);
            if (!validation.isValid()) {
                throw new IllegalArgumentException("Invalid COBOL date: " + validation.getResult());
            }
            return LocalDate.parse(dateString.trim(), COBOL_DATE_FORMATTER);
        }
    }

    /**
     * COBOL Numeric Utilities
     * 
     * Provides COBOL-compatible numeric processing including COMP-3 decimal
     * handling, precision management, and arithmetic operations that match
     * the original mainframe calculations.
     */
    public static class CobolNumericUtilities {
        
        /**
         * Convert string to BigDecimal with COBOL precision
         * 
         * @param value String value to convert
         * @param precision Total number of digits
         * @param scale Number of decimal places
         * @return BigDecimal with exact COBOL precision
         */
        public static BigDecimal parseCobolDecimal(String value, int precision, int scale) {
            if (value == null || value.trim().isEmpty()) {
                return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
            }
            
            try {
                BigDecimal result = new BigDecimal(value.trim());
                return result.setScale(scale, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid COBOL numeric value: " + value, e);
            }
        }
        
        /**
         * COBOL-style currency formatting
         * 
         * Formats BigDecimal amounts with 2 decimal places matching COBOL
         * currency field specifications (PIC 9(n)V99).
         */
        public static String formatCurrency(BigDecimal amount) {
            if (amount == null) {
                return "0.00";
            }
            return amount.setScale(2, RoundingMode.HALF_UP).toString();
        }
        
        /**
         * COBOL arithmetic operation with proper rounding
         * 
         * Performs addition with COBOL-compatible rounding rules
         */
        public static BigDecimal addCobolDecimals(BigDecimal a, BigDecimal b, int scale) {
            if (a == null) a = BigDecimal.ZERO;
            if (b == null) b = BigDecimal.ZERO;
            
            return a.add(b).setScale(scale, RoundingMode.HALF_UP);
        }
    }

    /**
     * Batch Error Handling Utilities
     * 
     * Provides standardized error handling patterns for batch processing,
     * including retry logic, skip policies, and error reporting.
     */
    public static class BatchErrorHandler {
        
        /**
         * Creates standard skip policy for batch jobs
         * 
         * @param skipLimit Maximum number of records to skip
         * @return Configured skip policy
         */
        public static org.springframework.batch.core.step.skip.SkipPolicy createStandardSkipPolicy(int skipLimit) {
            Map<Class<? extends Throwable>, Boolean> skippableExceptions = new HashMap<>();
            skippableExceptions.put(org.springframework.batch.item.file.FlatFileParseException.class, true);
            skippableExceptions.put(org.springframework.dao.DuplicateKeyException.class, true);
            skippableExceptions.put(IllegalArgumentException.class, true);
            
            return new org.springframework.batch.core.step.skip.LimitCheckingItemSkipPolicy(skipLimit, skippableExceptions);
        }
        
        /**
         * Standard item write listener for error handling
         */
        public static org.springframework.batch.core.ItemWriteListener<?> createErrorLoggingListener() {
            return new org.springframework.batch.core.ItemWriteListener<Object>() {
                @Override
                public void onWriteError(Exception exception, java.util.List<? extends Object> items) {
                    logger.error("BATCH-WRITE-ERROR: Failed to write {} items: {}", 
                               items.size(), exception.getMessage(), exception);
                }
            };
        }
    }

    /**
     * Generates unique Kubernetes job identifier
     * 
     * Creates a unique identifier for Kubernetes CronJob execution tracking
     */
    private String generateKubernetesJobId() {
        return String.format("carddemo-batch-%d-%04d", 
                           System.currentTimeMillis(), 
                           ThreadLocalRandom.current().nextInt(1000, 9999));
    }

    /**
     * Health Check Tasklet
     * 
     * Provides a simple health check step that can be used in any batch job
     * to verify system connectivity and basic functionality.
     */
    @Bean("healthCheckTasklet")
    public Tasklet healthCheckTasklet() {
        return new Tasklet() {
            @Override
            public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
                logger.info("HEALTH-CHECK: Executing batch system health check");
                
                // Database connectivity check
                JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
                Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                if (result == null || result != 1) {
                    throw new RuntimeException("Database health check failed");
                }
                
                // Audit service connectivity check (if available)
                if (auditService != null) {
                    auditService.auditBatchHealthCheck("Batch system health check completed successfully");
                }
                
                logger.info("HEALTH-CHECK: All systems operational");
                return RepeatStatus.FINISHED;
            }
        };
    }

    /**
     * Creates a standard job builder with common configuration
     * 
     * @param jobName Name of the job
     * @return Configured JobBuilder with standard settings
     */
    public JobBuilder createStandardJobBuilder(String jobName) {
        return new JobBuilder(jobName, jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(commonJobExecutionListener());
    }

    /**
     * Creates a standard step builder with common configuration
     * 
     * @param stepName Name of the step
     * @return Configured StepBuilder with standard settings
     */
    public StepBuilder createStandardStepBuilder(String stepName) {
        return new StepBuilder(stepName, jobRepository)
                .listener(commonStepExecutionListener())
                .transactionManager(transactionManager);
    }

    /**
     * Bean post-processor initialization
     */
    @Bean
    @ConditionalOnProperty(name = "carddemo.batch.enabled", havingValue = "true", matchIfMissing = true)
    public String batchConfigurationStatus() {
        logger.info("=== CardDemo Batch Configuration Initialized ===");
        logger.info("Chunk Size: {}", defaultChunkSize);
        logger.info("Skip Limit: {}", defaultSkipLimit);
        logger.info("Retry Limit: {}", defaultRetryLimit);
        logger.info("Thread Pool Size: {}-{}", batchThreadPoolSize, batchMaxThreadPoolSize);
        logger.info("AuditService Available: {}", auditService != null);
        logger.info("=== Ready for Kubernetes CronJob Execution ===");
        
        return "BatchConfiguration-Ready";
    }
}
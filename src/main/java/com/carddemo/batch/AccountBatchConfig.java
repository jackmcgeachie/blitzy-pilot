/*
 * AccountBatchConfig.java
 * 
 * Spring Batch configuration class that replaces CBACT01C-04C COBOL batch programs.
 * Provides chunk-oriented processing for account data loading, cross-reference processing,
 * reporting, and interest calculation with PostgreSQL integration and Kubernetes CronJob scheduling.
 * 
 * Conversion mappings:
 * - CBACT01C (account file reader) → accountDataReadingJob
 * - CBACT02C (card file reader) → cardDataReadingJob  
 * - CBACT03C (cross-reference reader) → crossReferenceProcessingJob
 * - CBACT04C (interest calculator) → interestCalculationJob
 * 
 * Copyright 2024 CardDemo Application
 */
package com.carddemo.batch;

import com.carddemo.entity.Account;
import com.carddemo.entity.Card;
import com.carddemo.entity.CardAccountXref;
import com.carddemo.entity.Transaction;
import com.carddemo.entity.TransactionCategoryBalance;
import com.carddemo.entity.DiscountGroup;
import com.carddemo.repository.AccountRepository;
import com.carddemo.repository.CardRepository;
import com.carddemo.repository.CardAccountXrefRepository;
import com.carddemo.repository.TransactionRepository;
import com.carddemo.repository.TransactionCategoryBalanceRepository;
import com.carddemo.repository.DiscountGroupRepository;
import com.carddemo.validation.ValidationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.RepositoryItemWriter;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.batch.item.data.builder.RepositoryItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.batch.item.support.builder.CompositeItemWriterBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spring Batch configuration for Account processing jobs.
 * 
 * This configuration replaces four COBOL batch programs:
 * 1. CBACT01C - Account data reading and validation
 * 2. CBACT02C - Card data processing and cross-reference validation
 * 3. CBACT03C - Cross-reference processing and reporting
 * 4. CBACT04C - Interest calculation with multi-file access patterns
 * 
 * Performance Requirements:
 * - Complete within 4-hour overnight processing window
 * - Handle 1M+ account records with parallel processing
 * - Maintain COBOL-equivalent BigDecimal precision
 * - Support checkpoint/restart capabilities for reliability
 * 
 * Architecture Features:
 * - Chunk-oriented processing with configurable commit intervals
 * - Parallel processing with thread pool task executor
 * - Comprehensive error handling and logging
 * - JPA repository integration with optimistic locking
 * - BigDecimal arithmetic matching COBOL COMP-3 precision
 */
@Configuration
@EnableBatchProcessing
public class AccountBatchConfig {

    private static final Logger logger = LoggerFactory.getLogger(AccountBatchConfig.class);
    
    // Batch processing configuration from application.yml
    @Value("${carddemo.batch.chunk-size:1000}")
    private Integer chunkSize;
    
    @Value("${carddemo.batch.thread-pool-size:4}")
    private Integer threadPoolSize;
    
    @Value("${carddemo.batch.max-pool-size:8}")
    private Integer maxPoolSize;
    
    @Value("${carddemo.batch.queue-capacity:200}")
    private Integer queueCapacity;
    
    @Value("${carddemo.batch.output-directory:/opt/carddemo/batch/output}")
    private String outputDirectory;
    
    // Interest calculation configuration matching COBOL business rules
    @Value("${carddemo.interest.default-rate:0.0199}")
    private BigDecimal defaultInterestRate;
    
    @Value("${carddemo.interest.calculation-scale:4}")
    private Integer interestCalculationScale;
    
    // Repository dependencies
    @Autowired
    private AccountRepository accountRepository;
    
    @Autowired
    private CardRepository cardRepository;
    
    @Autowired
    private CardAccountXrefRepository cardAccountXrefRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private TransactionCategoryBalanceRepository transactionCategoryBalanceRepository;
    
    @Autowired
    private DiscountGroupRepository discountGroupRepository;
    
    @Autowired
    private ValidationService validationService;
    
    @Autowired
    private DataSource dataSource;
    
    @Autowired
    private PlatformTransactionManager transactionManager;
    
    // Atomic counters for batch statistics - equivalent to COBOL working storage counters
    private final AtomicLong accountRecordCount = new AtomicLong(0);
    private final AtomicLong cardRecordCount = new AtomicLong(0);
    private final AtomicLong xrefRecordCount = new AtomicLong(0);
    private final AtomicLong interestRecordCount = new AtomicLong(0);
    private final AtomicLong errorRecordCount = new AtomicLong(0);

    /**
     * Task executor for parallel processing - critical for 4-hour processing window
     * Configured to handle high-volume account processing with optimal thread management
     */
    @Bean(name = "accountBatchTaskExecutor")
    public TaskExecutor accountBatchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadPoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("AccountBatch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(300); // 5 minutes graceful shutdown
        executor.initialize();
        logger.info("Initialized account batch task executor with {} core threads, {} max threads", 
                   threadPoolSize, maxPoolSize);
        return executor;
    }

    /*
     * =================================================================================
     * JOB 1: Account Data Reading Job (replacing CBACT01C)
     * Reads account file sequentially and validates data, mirroring COBOL logic
     * =================================================================================
     */

    /**
     * Main account data reading job - equivalent to CBACT01C COBOL program
     * Performs sequential account file reading with validation and reporting
     */
    @Bean
    public Job accountDataReadingJob(JobRepository jobRepository) {
        return new JobBuilder("accountDataReadingJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(new AccountBatchJobListener())
                .start(accountDataReadingStep(jobRepository))
                .build();
    }

    /**
     * Account data reading step with chunk-oriented processing
     * Processes accounts in configurable chunks for optimal performance
     */
    @Bean
    public Step accountDataReadingStep(JobRepository jobRepository) {
        return new StepBuilder("accountDataReadingStep", jobRepository)
                .<Account, Account>chunk(chunkSize, transactionManager)
                .reader(accountReader())
                .processor(accountValidationProcessor())
                .writer(accountReportWriter())
                .taskExecutor(accountBatchTaskExecutor())
                .build();
    }

    /**
     * JPA-based account reader with pagination support
     * Replaces COBOL sequential file reading with optimized database access
     */
    @Bean
    public RepositoryItemReader<Account> accountReader() {
        return new RepositoryItemReaderBuilder<Account>()
                .repository(accountRepository)
                .methodName("findAll")
                .pageSize(chunkSize)
                .sorts(Collections.singletonMap("accountId", Sort.Direction.ASC))
                .name("accountReader")
                .build();
    }

    /**
     * Account validation processor implementing COBOL field validation logic
     * Validates account fields and applies business rules equivalent to COBOL 88-levels
     */
    @Bean
    public ItemProcessor<Account, Account> accountValidationProcessor() {
        return account -> {
            try {
                logger.debug("Processing account: {}", account.getAccountId());
                
                // Validate account using ValidationService (equivalent to COBOL field validation)
                validationService.validateAccount(account);
                
                // Increment counter (equivalent to COBOL ADD 1 TO WS-RECORD-COUNT)
                accountRecordCount.incrementAndGet();
                
                // Log account details (equivalent to COBOL DISPLAY statements)
                if (logger.isTraceEnabled()) {
                    logger.trace("Account ID: {}, Status: {}, Balance: {}, Credit Limit: {}, " +
                               "Cash Credit Limit: {}, Open Date: {}, Group ID: {}",
                               account.getAccountId(), account.getActiveStatus(), 
                               account.getCurrentBalance(), account.getCreditLimit(),
                               account.getCashCreditLimit(), account.getOpenDate(), 
                               account.getGroupId());
                }
                
                return account;
                
            } catch (Exception e) {
                // Error handling equivalent to COBOL HANDLE ABEND logic
                logger.error("Error processing account {}: {}", account.getAccountId(), e.getMessage(), e);
                errorRecordCount.incrementAndGet();
                
                // Return null to skip this record (equivalent to COBOL conditional processing)
                return null;
            }
        };
    }

    /**
     * Account report writer for validation results
     * Outputs processed account information equivalent to COBOL DISPLAY logic
     */
    @Bean
    public FlatFileItemWriter<Account> accountReportWriter() {
        return new FlatFileItemWriterBuilder<Account>()
                .name("accountReportWriter")
                .resource(new FileSystemResource(outputDirectory + "/account-validation-report.txt"))
                .lineAggregator(account -> String.format(
                    "ACCT-ID: %s | STATUS: %s | BALANCE: %s | CREDIT-LIMIT: %s | " +
                    "CASH-LIMIT: %s | OPEN-DATE: %s | GROUP-ID: %s",
                    account.getAccountId(),
                    account.getActiveStatus(),
                    account.getCurrentBalance(),
                    account.getCreditLimit(),
                    account.getCashCreditLimit(),
                    account.getOpenDate(),
                    account.getGroupId()
                ))
                .headerCallback(writer -> writer.write("Account Validation Report - " + 
                                                     LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
                .footerCallback(writer -> writer.write("Total records processed: " + accountRecordCount.get()))
                .build();
    }

    /*
     * =================================================================================
     * JOB 2: Card Data Processing Job (replacing CBACT02C)
     * Processes card data with account cross-reference validation
     * =================================================================================
     */

    /**
     * Card data processing job - equivalent to CBACT02C COBOL program
     * Validates card data and maintains card-account relationships
     */
    @Bean
    public Job cardDataProcessingJob(JobRepository jobRepository) {
        return new JobBuilder("cardDataProcessingJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(new AccountBatchJobListener())
                .start(cardDataProcessingStep(jobRepository))
                .build();
    }

    /**
     * Card data processing step with relationship validation
     */
    @Bean
    public Step cardDataProcessingStep(JobRepository jobRepository) {
        return new StepBuilder("cardDataProcessingStep", jobRepository)
                .<Card, Card>chunk(chunkSize, transactionManager)
                .reader(cardReader())
                .processor(cardValidationProcessor())
                .writer(cardReportWriter())
                .taskExecutor(accountBatchTaskExecutor())
                .build();
    }

    /**
     * JPA-based card reader with optimal sort order
     */
    @Bean
    public RepositoryItemReader<Card> cardReader() {
        return new RepositoryItemReaderBuilder<Card>()
                .repository(cardRepository)
                .methodName("findAll")
                .pageSize(chunkSize)
                .sorts(Collections.singletonMap("cardNumber", Sort.Direction.ASC))
                .name("cardReader")
                .build();
    }

    /**
     * Card validation processor with account relationship verification
     * Implements COBOL-style card validation and account linkage checks
     */
    @Bean
    public ItemProcessor<Card, Card> cardValidationProcessor() {
        return card -> {
            try {
                logger.debug("Processing card: {}", card.getCardNumber());
                
                // Validate card data (equivalent to COBOL field validation)
                validationService.validateCard(card);
                
                // Verify account relationship exists (equivalent to COBOL file lookup)
                Optional<Account> linkedAccount = accountRepository.findById(card.getAccountId());
                if (!linkedAccount.isPresent()) {
                    logger.warn("Card {} linked to non-existent account {}", 
                              card.getCardNumber(), card.getAccountId());
                    errorRecordCount.incrementAndGet();
                    return null;
                }
                
                // Increment counter
                cardRecordCount.incrementAndGet();
                
                // Log card details (equivalent to COBOL DISPLAY statements)
                if (logger.isTraceEnabled()) {
                    logger.trace("Card Number: {}, Account ID: {}, Status: {}, " +
                               "Expiry Date: {}, Embossed Name: {}",
                               card.getCardNumber(), card.getAccountId(), 
                               card.getActiveStatus(), card.getExpiryDate(), 
                               card.getEmbossedName());
                }
                
                return card;
                
            } catch (Exception e) {
                logger.error("Error processing card {}: {}", card.getCardNumber(), e.getMessage(), e);
                errorRecordCount.incrementAndGet();
                return null;
            }
        };
    }

    /**
     * Card report writer for processing results
     */
    @Bean
    public FlatFileItemWriter<Card> cardReportWriter() {
        return new FlatFileItemWriterBuilder<Card>()
                .name("cardReportWriter")
                .resource(new FileSystemResource(outputDirectory + "/card-validation-report.txt"))
                .lineAggregator(card -> String.format(
                    "CARD-NUM: %s | ACCT-ID: %s | STATUS: %s | EXPIRY: %s | NAME: %s",
                    card.getCardNumber(),
                    card.getAccountId(),
                    card.getActiveStatus(),
                    card.getExpiryDate(),
                    card.getEmbossedName()
                ))
                .headerCallback(writer -> writer.write("Card Validation Report - " + 
                                                     LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
                .footerCallback(writer -> writer.write("Total cards processed: " + cardRecordCount.get()))
                .build();
    }

    /*
     * =================================================================================
     * JOB 3: Cross-Reference Processing Job (replacing CBACT03C)
     * Processes card-account cross-references and validates relationships
     * =================================================================================
     */

    /**
     * Cross-reference processing job - equivalent to CBACT03C COBOL program
     * Validates and reports on card-account cross-reference relationships
     */
    @Bean
    public Job crossReferenceProcessingJob(JobRepository jobRepository) {
        return new JobBuilder("crossReferenceProcessingJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(new AccountBatchJobListener())
                .start(crossReferenceProcessingStep(jobRepository))
                .build();
    }

    /**
     * Cross-reference processing step
     */
    @Bean
    public Step crossReferenceProcessingStep(JobRepository jobRepository) {
        return new StepBuilder("crossReferenceProcessingStep", jobRepository)
                .<CardAccountXref, CardAccountXref>chunk(chunkSize, transactionManager)
                .reader(crossReferenceReader())
                .processor(crossReferenceValidationProcessor())
                .writer(crossReferenceReportWriter())
                .taskExecutor(accountBatchTaskExecutor())
                .build();
    }

    /**
     * JPA-based cross-reference reader
     */
    @Bean
    public RepositoryItemReader<CardAccountXref> crossReferenceReader() {
        return new RepositoryItemReaderBuilder<CardAccountXref>()
                .repository(cardAccountXrefRepository)
                .methodName("findAll")
                .pageSize(chunkSize)
                .sorts(Collections.singletonMap("cardNumber", Sort.Direction.ASC))
                .name("crossReferenceReader")
                .build();
    }

    /**
     * Cross-reference validation processor
     * Validates card-account relationships and referential integrity
     */
    @Bean
    public ItemProcessor<CardAccountXref, CardAccountXref> crossReferenceValidationProcessor() {
        return xref -> {
            try {
                logger.debug("Processing cross-reference: Card {} -> Account {}", 
                           xref.getCardNumber(), xref.getAccountId());
                
                // Validate card exists (equivalent to COBOL file lookup)
                Optional<Card> card = cardRepository.findByCardNumber(xref.getCardNumber());
                if (!card.isPresent()) {
                    logger.warn("Cross-reference points to non-existent card: {}", xref.getCardNumber());
                    errorRecordCount.incrementAndGet();
                    return null;
                }
                
                // Validate account exists (equivalent to COBOL file lookup)
                Optional<Account> account = accountRepository.findById(xref.getAccountId());
                if (!account.isPresent()) {
                    logger.warn("Cross-reference points to non-existent account: {}", xref.getAccountId());
                    errorRecordCount.incrementAndGet();
                    return null;
                }
                
                // Validate consistency between card and cross-reference
                if (!card.get().getAccountId().equals(xref.getAccountId())) {
                    logger.warn("Inconsistent account ID: Card {} has account {} but xref has account {}", 
                              xref.getCardNumber(), card.get().getAccountId(), xref.getAccountId());
                    errorRecordCount.incrementAndGet();
                    return null;
                }
                
                // Increment counter
                xrefRecordCount.incrementAndGet();
                
                // Log cross-reference details (equivalent to COBOL DISPLAY statements)
                if (logger.isTraceEnabled()) {
                    logger.trace("Cross-Ref - Card: {}, Customer: {}, Account: {}",
                               xref.getCardNumber(), xref.getCustomerId(), xref.getAccountId());
                }
                
                return xref;
                
            } catch (Exception e) {
                logger.error("Error processing cross-reference for card {}: {}", 
                           xref.getCardNumber(), e.getMessage(), e);
                errorRecordCount.incrementAndGet();
                return null;
            }
        };
    }

    /**
     * Cross-reference report writer
     */
    @Bean
    public FlatFileItemWriter<CardAccountXref> crossReferenceReportWriter() {
        return new FlatFileItemWriterBuilder<CardAccountXref>()
                .name("crossReferenceReportWriter")
                .resource(new FileSystemResource(outputDirectory + "/cross-reference-report.txt"))
                .lineAggregator(xref -> String.format(
                    "CARD-NUM: %s | CUST-ID: %s | ACCT-ID: %s",
                    xref.getCardNumber(),
                    xref.getCustomerId(),
                    xref.getAccountId()
                ))
                .headerCallback(writer -> writer.write("Cross-Reference Validation Report - " + 
                                                     LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
                .footerCallback(writer -> writer.write("Total cross-references processed: " + xrefRecordCount.get()))
                .build();
    }

    /*
     * =================================================================================
     * JOB 4: Interest Calculation Job (replacing CBACT04C)
     * Complex multi-file processing for interest calculation and posting
     * =================================================================================
     */

    /**
     * Interest calculation job - equivalent to CBACT04C COBOL program
     * Most complex job with multi-file access, interest calculation, and transaction creation
     */
    @Bean
    public Job interestCalculationJob(JobRepository jobRepository) {
        return new JobBuilder("interestCalculationJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(new AccountBatchJobListener())
                .start(interestCalculationStep(jobRepository))
                .build();
    }

    /**
     * Interest calculation step with account balance updates
     */
    @Bean
    public Step interestCalculationStep(JobRepository jobRepository) {
        return new StepBuilder("interestCalculationStep", jobRepository)
                .<TransactionCategoryBalance, InterestCalculationResult>chunk(chunkSize, transactionManager)
                .reader(transactionCategoryBalanceReader())
                .processor(interestCalculationProcessor())
                .writer(interestCalculationWriter())
                .taskExecutor(accountBatchTaskExecutor())
                .build();
    }

    /**
     * Transaction category balance reader - equivalent to CBACT04C TCATBAL file reading
     */
    @Bean
    public RepositoryItemReader<TransactionCategoryBalance> transactionCategoryBalanceReader() {
        return new RepositoryItemReaderBuilder<TransactionCategoryBalance>()
                .repository(transactionCategoryBalanceRepository)
                .methodName("findAll")
                .pageSize(chunkSize)
                .sorts(Collections.singletonMap("accountId", Sort.Direction.ASC))
                .name("transactionCategoryBalanceReader")
                .build();
    }

    /**
     * Interest calculation processor - implements complex CBACT04C business logic
     * Performs multi-file lookups, interest calculations, and account updates
     */
    @Bean
    public ItemProcessor<TransactionCategoryBalance, InterestCalculationResult> interestCalculationProcessor() {
        final Map<String, BigDecimal> lastAccountTotals = new HashMap<>();
        
        return categoryBalance -> {
            try {
                logger.debug("Processing interest calculation for account: {}, category: {}", 
                           categoryBalance.getAccountId(), categoryBalance.getCategoryCode());
                
                // Look up account data (equivalent to COBOL PERFORM 1100-GET-ACCT-DATA)
                Optional<Account> accountOpt = accountRepository.findById(categoryBalance.getAccountId());
                if (!accountOpt.isPresent()) {
                    logger.warn("Account not found for interest calculation: {}", categoryBalance.getAccountId());
                    errorRecordCount.incrementAndGet();
                    return null;
                }
                Account account = accountOpt.get();
                
                // Look up cross-reference data (equivalent to COBOL PERFORM 1110-GET-XREF-DATA)
                Optional<CardAccountXref> xrefOpt = cardAccountXrefRepository.findByAccountId(categoryBalance.getAccountId());
                if (!xrefOpt.isPresent()) {
                    logger.warn("Cross-reference not found for account: {}", categoryBalance.getAccountId());
                    errorRecordCount.incrementAndGet();
                    return null;
                }
                CardAccountXref xref = xrefOpt.get();
                
                // Look up discount group for interest rate (equivalent to COBOL PERFORM 1200-GET-INTEREST-RATE)
                BigDecimal interestRate = getInterestRate(account.getGroupId(), 
                                                        categoryBalance.getTypeCode(), 
                                                        categoryBalance.getCategoryCode());
                
                // Skip if no interest rate applicable
                if (interestRate.compareTo(BigDecimal.ZERO) == 0) {
                    logger.debug("No interest rate for account {} category {}", 
                               categoryBalance.getAccountId(), categoryBalance.getCategoryCode());
                    return null;
                }
                
                // Calculate monthly interest (equivalent to COBOL PERFORM 1300-COMPUTE-INTEREST)
                // COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200
                BigDecimal monthlyInterest = categoryBalance.getBalance()
                    .multiply(interestRate)
                    .divide(new BigDecimal("1200"), interestCalculationScale, RoundingMode.HALF_UP);
                
                // Track total interest per account (equivalent to COBOL WS-TOTAL-INT)
                String accountId = categoryBalance.getAccountId();
                BigDecimal totalInterest = lastAccountTotals.getOrDefault(accountId, BigDecimal.ZERO);
                totalInterest = totalInterest.add(monthlyInterest);
                lastAccountTotals.put(accountId, totalInterest);
                
                // Increment counter
                interestRecordCount.incrementAndGet();
                
                // Create result object for writer
                InterestCalculationResult result = new InterestCalculationResult();
                result.setAccount(account);
                result.setCategoryBalance(categoryBalance);
                result.setXref(xref);
                result.setMonthlyInterest(monthlyInterest);
                result.setTotalInterest(totalInterest);
                result.setInterestRate(interestRate);
                
                logger.debug("Calculated interest {} for account {} category {}", 
                           monthlyInterest, accountId, categoryBalance.getCategoryCode());
                
                return result;
                
            } catch (Exception e) {
                logger.error("Error calculating interest for account {} category {}: {}", 
                           categoryBalance.getAccountId(), categoryBalance.getCategoryCode(), e.getMessage(), e);
                errorRecordCount.incrementAndGet();
                return null;
            }
        };
    }

    /**
     * Interest calculation writer - updates accounts and creates transactions
     * Equivalent to CBACT04C account update and transaction writing logic
     */
    @Bean
    public ItemWriter<InterestCalculationResult> interestCalculationWriter() {
        CompositeItemWriter<InterestCalculationResult> compositeWriter = new CompositeItemWriterBuilder<InterestCalculationResult>()
                .delegates(Arrays.asList(
                    accountUpdateWriter(),
                    interestTransactionWriter()
                ))
                .build();
        return compositeWriter;
    }

    /**
     * Account update writer for posting interest to account balances
     * Equivalent to COBOL PERFORM 1050-UPDATE-ACCOUNT
     */
    @Bean
    public RepositoryItemWriter<InterestCalculationResult> accountUpdateWriter() {
        return new RepositoryItemWriterBuilder<InterestCalculationResult>()
                .repository(accountRepository)
                .methodName("save")
                .itemKeyMapper(result -> {
                    // Update account balance with total interest (equivalent to COBOL ADD WS-TOTAL-INT TO ACCT-CURR-BAL)
                    Account account = result.getAccount();
                    BigDecimal newBalance = account.getCurrentBalance().add(result.getTotalInterest());
                    account.setCurrentBalance(newBalance);
                    
                    // Reset cycle amounts (equivalent to COBOL MOVE 0 TO ACCT-CURR-CYC-CREDIT/DEBIT)
                    account.setCurrentCycleCredit(BigDecimal.ZERO);
                    account.setCurrentCycleDebit(BigDecimal.ZERO);
                    
                    logger.debug("Updated account {} balance from {} to {} (interest: {})", 
                               account.getAccountId(), 
                               account.getCurrentBalance().subtract(result.getTotalInterest()),
                               account.getCurrentBalance(),
                               result.getTotalInterest());
                    
                    return account;
                })
                .build();
    }

    /**
     * Interest transaction writer for creating interest transaction records
     * Equivalent to COBOL PERFORM 1300-B-WRITE-TX
     */
    @Bean
    public RepositoryItemWriter<InterestCalculationResult> interestTransactionWriter() {
        final AtomicLong transactionSuffix = new AtomicLong(1);
        
        return new RepositoryItemWriterBuilder<InterestCalculationResult>()
                .repository(transactionRepository)
                .methodName("save")
                .itemKeyMapper(result -> {
                    // Create interest transaction (equivalent to COBOL transaction creation logic)
                    Transaction interestTransaction = new Transaction();
                    
                    // Generate transaction ID (equivalent to COBOL STRING PARM-DATE, WS-TRANID-SUFFIX)
                    String transactionId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + 
                                         String.format("%06d", transactionSuffix.getAndIncrement());
                    interestTransaction.setTransactionId(transactionId);
                    
                    // Set transaction details (equivalent to COBOL field assignments)
                    interestTransaction.setTypeCode("01"); // Interest transaction type
                    interestTransaction.setCategoryCode("05"); // Interest category
                    interestTransaction.setSource("System");
                    interestTransaction.setDescription("Int. for a/c " + result.getAccount().getAccountId());
                    interestTransaction.setAmount(result.getMonthlyInterest());
                    interestTransaction.setMerchantId(0L);
                    interestTransaction.setMerchantName("");
                    interestTransaction.setMerchantCity("");
                    interestTransaction.setMerchantZip("");
                    interestTransaction.setCardNumber(result.getXref().getCardNumber());
                    
                    // Set timestamps (equivalent to COBOL PERFORM Z-GET-DB2-FORMAT-TIMESTAMP)
                    LocalDateTime now = LocalDateTime.now();
                    interestTransaction.setOriginalTimestamp(now);
                    interestTransaction.setProcessedTimestamp(now);
                    
                    logger.debug("Created interest transaction {} for account {} amount {}", 
                               transactionId, result.getAccount().getAccountId(), result.getMonthlyInterest());
                    
                    return interestTransaction;
                })
                .build();
    }

    /**
     * Get interest rate from discount group table
     * Equivalent to COBOL PERFORM 1200-GET-INTEREST-RATE and 1200-A-GET-DEFAULT-INT-RATE
     */
    private BigDecimal getInterestRate(String groupId, String typeCode, String categoryCode) {
        try {
            // Try to find specific discount group record
            Optional<DiscountGroup> discountGroup = discountGroupRepository
                .findByGroupIdAndTypeCodeAndCategoryCode(groupId, typeCode, categoryCode);
            
            if (discountGroup.isPresent()) {
                logger.debug("Found interest rate {} for group {} type {} category {}", 
                           discountGroup.get().getInterestRate(), groupId, typeCode, categoryCode);
                return discountGroup.get().getInterestRate();
            }
            
            // Try default group (equivalent to COBOL FD-DIS-ACCT-GROUP-ID = 'DEFAULT')
            Optional<DiscountGroup> defaultGroup = discountGroupRepository
                .findByGroupIdAndTypeCodeAndCategoryCode("DEFAULT", typeCode, categoryCode);
            
            if (defaultGroup.isPresent()) {
                logger.debug("Using default interest rate {} for type {} category {}", 
                           defaultGroup.get().getInterestRate(), typeCode, categoryCode);
                return defaultGroup.get().getInterestRate();
            }
            
            // Return configured default if no specific rate found
            logger.debug("Using system default interest rate {} for group {} type {} category {}", 
                       defaultInterestRate, groupId, typeCode, categoryCode);
            return defaultInterestRate;
            
        } catch (Exception e) {
            logger.warn("Error retrieving interest rate for group {} type {} category {}: {}", 
                       groupId, typeCode, categoryCode, e.getMessage());
            return defaultInterestRate;
        }
    }

    /**
     * Job execution listener for comprehensive logging and statistics
     * Equivalent to COBOL job start/end messages and statistics reporting
     */
    public class AccountBatchJobListener implements JobExecutionListener {
        
        @Override
        public void beforeJob(JobExecution jobExecution) {
            String jobName = jobExecution.getJobInstance().getJobName();
            logger.info("START OF EXECUTION OF JOB {}", jobName);
            logger.info("Job Parameters: {}", jobExecution.getJobParameters());
            
            // Reset counters for new job execution
            accountRecordCount.set(0);
            cardRecordCount.set(0);
            xrefRecordCount.set(0);
            interestRecordCount.set(0);
            errorRecordCount.set(0);
        }
        
        @Override
        public void afterJob(JobExecution jobExecution) {
            String jobName = jobExecution.getJobInstance().getJobName();
            String status = jobExecution.getStatus().toString();
            
            logger.info("END OF EXECUTION OF JOB {} - Status: {}", jobName, status);
            
            // Log processing statistics (equivalent to COBOL end-of-job reporting)
            logger.info("Batch Job Statistics for {}:", jobName);
            logger.info("  Account records processed: {}", accountRecordCount.get());
            logger.info("  Card records processed: {}", cardRecordCount.get());
            logger.info("  Cross-reference records processed: {}", xrefRecordCount.get());
            logger.info("  Interest calculations processed: {}", interestRecordCount.get());
            logger.info("  Error records encountered: {}", errorRecordCount.get());
            logger.info("  Job duration: {} ms", jobExecution.getEndTime().getTime() - jobExecution.getStartTime().getTime());
            
            // Log any job failures
            if (jobExecution.getStatus().isUnsuccessful()) {
                logger.error("Job {} failed with status: {}", jobName, status);
                jobExecution.getAllFailureExceptions().forEach(exception -> 
                    logger.error("Job failure exception: {}", exception.getMessage(), exception));
            }
        }
    }

    /**
     * Result class for interest calculation processing
     * Holds all data needed for account updates and transaction creation
     */
    public static class InterestCalculationResult {
        private Account account;
        private TransactionCategoryBalance categoryBalance;
        private CardAccountXref xref;
        private BigDecimal monthlyInterest;
        private BigDecimal totalInterest;
        private BigDecimal interestRate;
        
        // Getters and setters
        public Account getAccount() { return account; }
        public void setAccount(Account account) { this.account = account; }
        
        public TransactionCategoryBalance getCategoryBalance() { return categoryBalance; }
        public void setCategoryBalance(TransactionCategoryBalance categoryBalance) { this.categoryBalance = categoryBalance; }
        
        public CardAccountXref getXref() { return xref; }
        public void setXref(CardAccountXref xref) { this.xref = xref; }
        
        public BigDecimal getMonthlyInterest() { return monthlyInterest; }
        public void setMonthlyInterest(BigDecimal monthlyInterest) { this.monthlyInterest = monthlyInterest; }
        
        public BigDecimal getTotalInterest() { return totalInterest; }
        public void setTotalInterest(BigDecimal totalInterest) { this.totalInterest = totalInterest; }
        
        public BigDecimal getInterestRate() { return interestRate; }
        public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    }
}
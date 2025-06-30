/**
 * Spring Batch configuration for transaction processing jobs
 * 
 * Replaces COBOL batch programs:
 * - CBTRN01C: Daily transaction initialization and validation
 * - CBTRN02C: Transaction posting, balance updates, and reject processing
 * - CBTRN03C: Transaction reporting and analysis
 * 
 * This configuration implements high-volume transaction processing with:
 * - 5M+ daily transactions within 120-minute completion time (Section 4.5.4.2)
 * - Identical transaction categorization logic from COBOL EVALUATE statements
 * - Exact balance calculation precision per COBOL COMP-3 requirements
 * - Optimistic locking patterns matching CICS record locking
 * - Chunk-oriented processing with database optimization
 * - Comprehensive error handling and reject record management
 * 
 * @author Blitzy agent
 * @version 1.0
 * @since Spring Boot 3.2.x, Spring Batch 5.0.x
 */
package com.carddemo.batch;

import com.carddemo.entity.Transaction;
import com.carddemo.entity.Account;
import com.carddemo.repository.TransactionRepository;
import com.carddemo.repository.AccountRepository;
import com.carddemo.service.BalanceCalculationService;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.*;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.PagingQueryProvider;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.PostgreSqlPagingQueryProvider;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.item.file.transform.BeanWrapperFieldExtractor;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.batch.item.support.CompositeItemProcessor;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.batch.item.support.builder.CompositeItemProcessorBuilder;
import org.springframework.batch.item.support.builder.CompositeItemWriterBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableBatchProcessing
public class TransactionBatchConfig {

    private static final Logger logger = LoggerFactory.getLogger(TransactionBatchConfig.class);

    // Chunk sizes optimized for 5M+ transaction processing within 120 minutes
    private static final int TRANSACTION_POSTING_CHUNK_SIZE = 1000;
    private static final int BALANCE_UPDATE_CHUNK_SIZE = 500;
    private static final int REPORT_GENERATION_CHUNK_SIZE = 2000;
    private static final int REJECT_PROCESSING_CHUNK_SIZE = 100;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BalanceCalculationService balanceCalculationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Main daily transaction processing job
     * Replaces JCL job stream for CBTRN01C -> CBTRN02C -> CBTRN03C
     * 
     * Job flow:
     * 1. Initialize daily processing (CBTRN01C logic)
     * 2. Validate and post transactions (CBTRN02C logic)
     * 3. Update account balances and category balances
     * 4. Process rejected transactions
     * 5. Generate transaction reports (CBTRN03C logic)
     */
    @Bean
    public Job dailyTransactionProcessingJob() {
        return new JobBuilder("dailyTransactionProcessingJob", jobRepository)
                .start(initializeDailyProcessingStep())
                .next(validateAndPostTransactionsStep())
                .next(updateAccountBalancesStep())
                .next(updateCategoryBalancesStep())
                .next(processRejectRecordsStep())
                .next(generateTransactionReportsStep())
                .build();
    }

    /**
     * Daily transaction posting job optimized for high volume processing
     * Target: 5M+ transactions within 120 minutes
     */
    @Bean
    public Job dailyTransactionPostingJob() {
        return new JobBuilder("dailyTransactionPostingJob", jobRepository)
                .start(validateAndPostTransactionsStep())
                .next(updateAccountBalancesStep())
                .next(updateCategoryBalancesStep())
                .build();
    }

    /**
     * Transaction reporting job for analytics and compliance
     * Implements CBTRN03C reporting logic with date range filtering
     */
    @Bean
    public Job transactionReportingJob() {
        return new JobBuilder("transactionReportingJob", jobRepository)
                .start(generateTransactionReportsStep())
                .next(generateDailyBalanceReportStep())
                .next(generateCategoryAnalysisReportStep())
                .build();
    }

    /**
     * Balance reconciliation job for end-of-day processing
     * Ensures transaction posting accuracy and balance integrity
     */
    @Bean
    public Job balanceReconciliationJob() {
        return new JobBuilder("balanceReconciliationJob", jobRepository)
                .start(reconcileAccountBalancesStep())
                .next(validateCategoryBalancesStep())
                .next(generateReconciliationReportStep())
                .build();
    }

    // ========== STEP DEFINITIONS ==========

    /**
     * Step 1: Initialize daily processing (CBTRN01C logic)
     * Validates daily transaction file and prepares processing environment
     */
    @Bean
    public Step initializeDailyProcessingStep() {
        return new StepBuilder("initializeDailyProcessingStep", jobRepository)
                .tasklet(initializeDailyProcessingTasklet(), transactionManager)
                .build();
    }

    /**
     * Step 2: Validate and post transactions (CBTRN02C core logic)
     * High-volume chunk-oriented processing with validation and posting
     */
    @Bean
    public Step validateAndPostTransactionsStep() {
        return new StepBuilder("validateAndPostTransactionsStep", jobRepository)
                .<DailyTransactionRecord, ProcessedTransactionResult>chunk(TRANSACTION_POSTING_CHUNK_SIZE, transactionManager)
                .reader(dailyTransactionFileReader())
                .processor(transactionValidationAndPostingProcessor())
                .writer(transactionPostingWriter())
                .faultTolerant()
                .retryLimit(3)
                .retry(OptimisticLockingFailureException.class)
                .skipLimit(10)
                .skip(TransactionValidationException.class)
                .listener(transactionProcessingStepListener())
                .build();
    }

    /**
     * Step 3: Update account balances
     * Implements CBTRN02C account balance update logic with optimistic locking
     */
    @Bean
    public Step updateAccountBalancesStep() {
        return new StepBuilder("updateAccountBalancesStep", jobRepository)
                .<AccountBalanceUpdate, AccountBalanceUpdate>chunk(BALANCE_UPDATE_CHUNK_SIZE, transactionManager)
                .reader(accountBalanceUpdateReader())
                .processor(accountBalanceProcessor())
                .writer(accountBalanceWriter())
                .faultTolerant()
                .retryLimit(3)
                .retry(OptimisticLockingFailureException.class)
                .build();
    }

    /**
     * Step 4: Update category balances
     * Implements CBTRN02C transaction category balance (TCATBAL) update logic
     */
    @Bean
    public Step updateCategoryBalancesStep() {
        return new StepBuilder("updateCategoryBalancesStep", jobRepository)
                .<CategoryBalanceUpdate, CategoryBalanceUpdate>chunk(BALANCE_UPDATE_CHUNK_SIZE, transactionManager)
                .reader(categoryBalanceUpdateReader())
                .processor(categoryBalanceProcessor())
                .writer(categoryBalanceWriter())
                .faultTolerant()
                .retryLimit(3)
                .retry(OptimisticLockingFailureException.class)
                .build();
    }

    /**
     * Step 5: Process rejected transactions
     * Handles invalid transactions per CBTRN02C reject logic
     */
    @Bean
    public Step processRejectRecordsStep() {
        return new StepBuilder("processRejectRecordsStep", jobRepository)
                .<RejectedTransaction, RejectedTransaction>chunk(REJECT_PROCESSING_CHUNK_SIZE, transactionManager)
                .reader(rejectedTransactionReader())
                .processor(rejectRecordProcessor())
                .writer(rejectRecordWriter())
                .build();
    }

    /**
     * Step 6: Generate transaction reports (CBTRN03C logic)
     * Creates detailed transaction reports with date range filtering
     */
    @Bean
    public Step generateTransactionReportsStep() {
        return new StepBuilder("generateTransactionReportsStep", jobRepository)
                .<TransactionReportRecord, TransactionReportRecord>chunk(REPORT_GENERATION_CHUNK_SIZE, transactionManager)
                .reader(transactionReportReader())
                .processor(transactionReportProcessor())
                .writer(transactionReportWriter())
                .build();
    }

    /**
     * Generate daily balance report
     */
    @Bean
    public Step generateDailyBalanceReportStep() {
        return new StepBuilder("generateDailyBalanceReportStep", jobRepository)
                .tasklet(dailyBalanceReportTasklet(), transactionManager)
                .build();
    }

    /**
     * Generate category analysis report
     */
    @Bean
    public Step generateCategoryAnalysisReportStep() {
        return new StepBuilder("generateCategoryAnalysisReportStep", jobRepository)
                .tasklet(categoryAnalysisReportTasklet(), transactionManager)
                .build();
    }

    /**
     * Reconcile account balances for end-of-day processing
     */
    @Bean
    public Step reconcileAccountBalancesStep() {
        return new StepBuilder("reconcileAccountBalancesStep", jobRepository)
                .tasklet(accountBalanceReconciliationTasklet(), transactionManager)
                .build();
    }

    /**
     * Validate category balances
     */
    @Bean
    public Step validateCategoryBalancesStep() {
        return new StepBuilder("validateCategoryBalancesStep", jobRepository)
                .tasklet(categoryBalanceValidationTasklet(), transactionManager)
                .build();
    }

    /**
     * Generate reconciliation report
     */
    @Bean
    public Step generateReconciliationReportStep() {
        return new StepBuilder("generateReconciliationReportStep", jobRepository)
                .tasklet(reconciliationReportTasklet(), transactionManager)
                .build();
    }

    // ========== READERS ==========

    /**
     * Daily transaction file reader (CBTRN01C/CBTRN02C input processing)
     * Reads fixed-width transaction records from daily transaction file
     */
    @Bean
    @StepScope
    public FlatFileItemReader<DailyTransactionRecord> dailyTransactionFileReader() {
        return new FlatFileItemReaderBuilder<DailyTransactionRecord>()
                .name("dailyTransactionFileReader")
                .resource(new FileSystemResource("${batch.input.dailytran.file:data/dailytran.txt}"))
                .delimited()
                .delimiter(",")
                .names("transactionId", "cardNumber", "transactionTypeCode", "transactionCategoryCode", 
                       "source", "description", "amount", "merchantId", "merchantName", 
                       "merchantCity", "merchantZip", "originalTimestamp")
                .fieldSetMapper(new BeanWrapperFieldSetMapper<DailyTransactionRecord>() {{
                    setTargetType(DailyTransactionRecord.class);
                }})
                .build();
    }

    /**
     * Account balance update reader
     * Reads accounts requiring balance updates from staged transaction data
     */
    @Bean
    @StepScope
    public JdbcPagingItemReader<AccountBalanceUpdate> accountBalanceUpdateReader() {
        PostgreSqlPagingQueryProvider queryProvider = new PostgreSqlPagingQueryProvider();
        queryProvider.setSelectClause("SELECT DISTINCT a.account_id, a.current_balance, a.credit_limit, " +
                                     "a.current_cycle_credit, a.current_cycle_debit, a.row_version, " +
                                     "SUM(t.transaction_amount) as total_amount");
        queryProvider.setFromClause("FROM accounts a " +
                                  "JOIN staged_transactions t ON a.account_id = t.account_id " +
                                  "WHERE t.processing_status = 'VALIDATED' " +
                                  "AND t.processing_date = CURRENT_DATE");
        queryProvider.setGroupClause("GROUP BY a.account_id, a.current_balance, a.credit_limit, " +
                                   "a.current_cycle_credit, a.current_cycle_debit, a.row_version");
        queryProvider.setSortKeys(Map.of("account_id", org.springframework.batch.item.database.Order.ASCENDING));

        return new JdbcPagingItemReaderBuilder<AccountBalanceUpdate>()
                .name("accountBalanceUpdateReader")
                .dataSource(dataSource)
                .queryProvider(queryProvider)
                .pageSize(BALANCE_UPDATE_CHUNK_SIZE)
                .rowMapper(new BeanPropertyRowMapper<>(AccountBalanceUpdate.class))
                .build();
    }

    /**
     * Category balance update reader
     * Reads transaction category balances requiring updates (TCATBAL logic)
     */
    @Bean
    @StepScope
    public JdbcPagingItemReader<CategoryBalanceUpdate> categoryBalanceUpdateReader() {
        PostgreSqlPagingQueryProvider queryProvider = new PostgreSqlPagingQueryProvider();
        queryProvider.setSelectClause("SELECT t.account_id, t.transaction_type_code, t.transaction_category_code, " +
                                     "SUM(t.transaction_amount) as balance_change, COUNT(*) as transaction_count");
        queryProvider.setFromClause("FROM staged_transactions t " +
                                  "WHERE t.processing_status = 'VALIDATED' " +
                                  "AND t.processing_date = CURRENT_DATE");
        queryProvider.setGroupClause("GROUP BY t.account_id, t.transaction_type_code, t.transaction_category_code");
        queryProvider.setSortKeys(Map.of("account_id", org.springframework.batch.item.database.Order.ASCENDING,
                                       "transaction_type_code", org.springframework.batch.item.database.Order.ASCENDING,
                                       "transaction_category_code", org.springframework.batch.item.database.Order.ASCENDING));

        return new JdbcPagingItemReaderBuilder<CategoryBalanceUpdate>()
                .name("categoryBalanceUpdateReader")
                .dataSource(dataSource)
                .queryProvider(queryProvider)
                .pageSize(BALANCE_UPDATE_CHUNK_SIZE)
                .rowMapper(new BeanPropertyRowMapper<>(CategoryBalanceUpdate.class))
                .build();
    }

    /**
     * Rejected transaction reader
     * Reads transactions that failed validation for reject record processing
     */
    @Bean
    @StepScope
    public JdbcPagingItemReader<RejectedTransaction> rejectedTransactionReader() {
        PostgreSqlPagingQueryProvider queryProvider = new PostgreSqlPagingQueryProvider();
        queryProvider.setSelectClause("SELECT * ");
        queryProvider.setFromClause("FROM staged_transactions ");
        queryProvider.setWhereClause("WHERE processing_status = 'REJECTED' " +
                                   "AND processing_date = CURRENT_DATE");
        queryProvider.setSortKeys(Map.of("transaction_id", org.springframework.batch.item.database.Order.ASCENDING));

        return new JdbcPagingItemReaderBuilder<RejectedTransaction>()
                .name("rejectedTransactionReader")
                .dataSource(dataSource)
                .queryProvider(queryProvider)
                .pageSize(REJECT_PROCESSING_CHUNK_SIZE)
                .rowMapper(new BeanPropertyRowMapper<>(RejectedTransaction.class))
                .build();
    }

    /**
     * Transaction report reader (CBTRN03C logic)
     * Reads posted transactions for report generation with date filtering
     */
    @Bean
    @StepScope
    public JdbcPagingItemReader<TransactionReportRecord> transactionReportReader() {
        PostgreSqlPagingQueryProvider queryProvider = new PostgreSqlPagingQueryProvider();
        queryProvider.setSelectClause("SELECT t.transaction_id, t.card_number, t.account_id, t.transaction_amount, " +
                                     "t.merchant_name, t.merchant_city, t.transaction_timestamp, " +
                                     "t.transaction_type_code, t.transaction_category_code, " +
                                     "tt.type_description, tc.category_description, " +
                                     "a.current_balance, c.customer_name");
        queryProvider.setFromClause("FROM transactions t " +
                                  "JOIN accounts a ON t.account_id = a.account_id " +
                                  "JOIN cards card ON t.card_number = card.card_number " +
                                  "JOIN customers c ON a.customer_id = c.customer_id " +
                                  "LEFT JOIN transaction_types tt ON t.transaction_type_code = tt.type_code " +
                                  "LEFT JOIN transaction_categories tc ON t.transaction_category_code = tc.category_code");
        queryProvider.setWhereClause("WHERE DATE(t.transaction_timestamp) BETWEEN ? AND ?");
        queryProvider.setSortKeys(Map.of("card_number", org.springframework.batch.item.database.Order.ASCENDING,
                                       "transaction_timestamp", org.springframework.batch.item.database.Order.ASCENDING));

        return new JdbcPagingItemReaderBuilder<TransactionReportRecord>()
                .name("transactionReportReader")
                .dataSource(dataSource)
                .queryProvider(queryProvider)
                .pageSize(REPORT_GENERATION_CHUNK_SIZE)
                .rowMapper(new BeanPropertyRowMapper<>(TransactionReportRecord.class))
                .build();
    }

    // ========== PROCESSORS ==========

    /**
     * Transaction validation and posting processor (CBTRN02C core logic)
     * Implements comprehensive transaction validation and posting logic
     */
    @Bean
    @StepScope
    public CompositeItemProcessor<DailyTransactionRecord, ProcessedTransactionResult> transactionValidationAndPostingProcessor() {
        return new CompositeItemProcessorBuilder<DailyTransactionRecord, ProcessedTransactionResult>()
                .delegates(transactionValidationProcessor(), transactionPostingProcessor())
                .build();
    }

    /**
     * Transaction validation processor
     * Implements CBTRN02C validation logic: card lookup, account validation, credit limit checks
     */
    @Bean
    @StepScope
    public ItemProcessor<DailyTransactionRecord, ValidatedTransactionRecord> transactionValidationProcessor() {
        return new TransactionValidationProcessor();
    }

    /**
     * Transaction posting processor
     * Converts validated transactions to posted transaction records
     */
    @Bean
    @StepScope
    public ItemProcessor<ValidatedTransactionRecord, ProcessedTransactionResult> transactionPostingProcessor() {
        return new TransactionPostingProcessor();
    }

    /**
     * Account balance processor
     * Implements CBTRN02C account balance update logic with COMP-3 precision
     */
    @Bean
    @StepScope
    public ItemProcessor<AccountBalanceUpdate, AccountBalanceUpdate> accountBalanceProcessor() {
        return new AccountBalanceProcessor();
    }

    /**
     * Category balance processor
     * Implements CBTRN02C transaction category balance (TCATBAL) logic
     */
    @Bean
    @StepScope
    public ItemProcessor<CategoryBalanceUpdate, CategoryBalanceUpdate> categoryBalanceProcessor() {
        return new CategoryBalanceProcessor();
    }

    /**
     * Reject record processor
     * Processes rejected transactions for error reporting
     */
    @Bean
    @StepScope
    public ItemProcessor<RejectedTransaction, RejectedTransaction> rejectRecordProcessor() {
        return new RejectRecordProcessor();
    }

    /**
     * Transaction report processor (CBTRN03C logic)
     * Formats transaction data for detailed reporting with categorization
     */
    @Bean
    @StepScope
    public ItemProcessor<TransactionReportRecord, TransactionReportRecord> transactionReportProcessor() {
        return new TransactionReportProcessor();
    }

    // ========== WRITERS ==========

    /**
     * Transaction posting writer
     * Writes validated transactions to transaction table and staging tables
     */
    @Bean
    @StepScope
    public CompositeItemWriter<ProcessedTransactionResult> transactionPostingWriter() {
        return new CompositeItemWriterBuilder<ProcessedTransactionResult>()
                .delegates(transactionTableWriter(), stagedTransactionWriter())
                .build();
    }

    /**
     * Transaction table writer
     * Writes posted transactions to main transaction table
     */
    @Bean
    @StepScope
    public JdbcBatchItemWriter<ProcessedTransactionResult> transactionTableWriter() {
        return new JdbcBatchItemWriterBuilder<ProcessedTransactionResult>()
                .itemSqlParameterSourceProvider(item -> {
                    Map<String, Object> params = new HashMap<>();
                    params.put("transactionId", item.getTransactionId());
                    params.put("cardNumber", item.getCardNumber());
                    params.put("accountId", item.getAccountId());
                    params.put("transactionAmount", item.getTransactionAmount());
                    params.put("merchantName", item.getMerchantName());
                    params.put("merchantCity", item.getMerchantCity());
                    params.put("merchantZip", item.getMerchantZip());
                    params.put("transactionTimestamp", item.getTransactionTimestamp());
                    params.put("processingTimestamp", LocalDateTime.now());
                    params.put("transactionTypeCode", item.getTransactionTypeCode());
                    params.put("transactionCategoryCode", item.getTransactionCategoryCode());
                    params.put("source", item.getSource());
                    params.put("description", item.getDescription());
                    return new org.springframework.batch.item.database.ItemSqlParameterSourceProvider.MapItemSqlParameterSourceProvider().createSqlParameterSource(params);
                })
                .sql("INSERT INTO transactions (transaction_id, card_number, account_id, transaction_amount, " +
                     "merchant_name, merchant_city, merchant_zip, transaction_timestamp, processing_timestamp, " +
                     "transaction_type_code, transaction_category_code, source, description) " +
                     "VALUES (:transactionId, :cardNumber, :accountId, :transactionAmount, " +
                     ":merchantName, :merchantCity, :merchantZip, :transactionTimestamp, :processingTimestamp, " +
                     ":transactionTypeCode, :transactionCategoryCode, :source, :description)")
                .dataSource(dataSource)
                .build();
    }

    /**
     * Staged transaction writer
     * Writes transaction data to staging table for balance processing
     */
    @Bean
    @StepScope
    public JdbcBatchItemWriter<ProcessedTransactionResult> stagedTransactionWriter() {
        return new JdbcBatchItemWriterBuilder<ProcessedTransactionResult>()
                .itemSqlParameterSourceProvider(item -> {
                    Map<String, Object> params = new HashMap<>();
                    params.put("transactionId", item.getTransactionId());
                    params.put("accountId", item.getAccountId());
                    params.put("transactionAmount", item.getTransactionAmount());
                    params.put("transactionTypeCode", item.getTransactionTypeCode());
                    params.put("transactionCategoryCode", item.getTransactionCategoryCode());
                    params.put("processingStatus", "VALIDATED");
                    params.put("processingDate", java.time.LocalDate.now());
                    return new org.springframework.batch.item.database.ItemSqlParameterSourceProvider.MapItemSqlParameterSourceProvider().createSqlParameterSource(params);
                })
                .sql("INSERT INTO staged_transactions (transaction_id, account_id, transaction_amount, " +
                     "transaction_type_code, transaction_category_code, processing_status, processing_date) " +
                     "VALUES (:transactionId, :accountId, :transactionAmount, " +
                     ":transactionTypeCode, :transactionCategoryCode, :processingStatus, :processingDate)")
                .dataSource(dataSource)
                .build();
    }

    /**
     * Account balance writer
     * Updates account balances with optimistic locking
     */
    @Bean
    @StepScope
    public JdbcBatchItemWriter<AccountBalanceUpdate> accountBalanceWriter() {
        return new JdbcBatchItemWriterBuilder<AccountBalanceUpdate>()
                .itemSqlParameterSourceProvider(item -> {
                    Map<String, Object> params = new HashMap<>();
                    params.put("accountId", item.getAccountId());
                    params.put("currentBalance", item.getNewCurrentBalance());
                    params.put("currentCycleCredit", item.getNewCurrentCycleCredit());
                    params.put("currentCycleDebit", item.getNewCurrentCycleDebit());
                    params.put("rowVersion", item.getRowVersion());
                    params.put("lastUpdated", LocalDateTime.now());
                    return new org.springframework.batch.item.database.ItemSqlParameterSourceProvider.MapItemSqlParameterSourceProvider().createSqlParameterSource(params);
                })
                .sql("UPDATE accounts SET current_balance = :currentBalance, " +
                     "current_cycle_credit = :currentCycleCredit, current_cycle_debit = :currentCycleDebit, " +
                     "row_version = row_version + 1, last_updated = :lastUpdated " +
                     "WHERE account_id = :accountId AND row_version = :rowVersion")
                .dataSource(dataSource)
                .build();
    }

    /**
     * Category balance writer
     * Updates transaction category balances (TCATBAL equivalent)
     */
    @Bean
    @StepScope
    public JdbcBatchItemWriter<CategoryBalanceUpdate> categoryBalanceWriter() {
        return new JdbcBatchItemWriterBuilder<CategoryBalanceUpdate>()
                .itemSqlParameterSourceProvider(item -> {
                    Map<String, Object> params = new HashMap<>();
                    params.put("accountId", item.getAccountId());
                    params.put("transactionTypeCode", item.getTransactionTypeCode());
                    params.put("transactionCategoryCode", item.getTransactionCategoryCode());
                    params.put("balanceChange", item.getBalanceChange());
                    params.put("transactionCount", item.getTransactionCount());
                    params.put("lastUpdated", LocalDateTime.now());
                    return new org.springframework.batch.item.database.ItemSqlParameterSourceProvider.MapItemSqlParameterSourceProvider().createSqlParameterSource(params);
                })
                .sql("INSERT INTO category_balances (account_id, transaction_type_code, transaction_category_code, " +
                     "current_balance, transaction_count, last_updated) " +
                     "VALUES (:accountId, :transactionTypeCode, :transactionCategoryCode, :balanceChange, :transactionCount, :lastUpdated) " +
                     "ON CONFLICT (account_id, transaction_type_code, transaction_category_code) " +
                     "DO UPDATE SET current_balance = category_balances.current_balance + :balanceChange, " +
                     "transaction_count = category_balances.transaction_count + :transactionCount, " +
                     "last_updated = :lastUpdated")
                .dataSource(dataSource)
                .build();
    }

    /**
     * Reject record writer
     * Writes rejected transactions to reject file (DALYREJS equivalent)
     */
    @Bean
    @StepScope
    public FlatFileItemWriter<RejectedTransaction> rejectRecordWriter() {
        return new FlatFileItemWriterBuilder<RejectedTransaction>()
                .name("rejectRecordWriter")
                .resource(new FileSystemResource("${batch.output.rejects.file:output/rejects_" + 
                         LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt}"))
                .lineAggregator(new DelimitedLineAggregator<RejectedTransaction>() {{
                    setDelimiter("|");
                    setFieldExtractor(new BeanWrapperFieldExtractor<RejectedTransaction>() {{
                        setNames(new String[]{"transactionId", "cardNumber", "rejectReasonCode", 
                                            "rejectReasonDescription", "originalRecord", "processingTimestamp"});
                    }});
                }})
                .build();
    }

    /**
     * Transaction report writer (CBTRN03C output)
     * Generates detailed transaction reports with formatting
     */
    @Bean
    @StepScope
    public FlatFileItemWriter<TransactionReportRecord> transactionReportWriter() {
        return new FlatFileItemWriterBuilder<TransactionReportRecord>()
                .name("transactionReportWriter")
                .resource(new FileSystemResource("${batch.output.reports.file:output/transaction_report_" + 
                         LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt}"))
                .lineAggregator(new DelimitedLineAggregator<TransactionReportRecord>() {{
                    setDelimiter("|");
                    setFieldExtractor(new BeanWrapperFieldExtractor<TransactionReportRecord>() {{
                        setNames(new String[]{"transactionId", "cardNumber", "accountId", "customerName",
                                            "transactionAmount", "merchantName", "merchantCity", 
                                            "transactionTimestamp", "typeDescription", "categoryDescription",
                                            "currentBalance"});
                    }});
                }})
                .headerCallback(writer -> {
                    writer.write("Transaction Report - Generated: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    writer.write("Transaction ID|Card Number|Account ID|Customer Name|Amount|Merchant|City|Timestamp|Type|Category|Balance");
                })
                .build();
    }

    // ========== TASKLETS ==========

    /**
     * Initialize daily processing tasklet (CBTRN01C logic)
     * Prepares processing environment and validates input files
     */
    @Bean
    @StepScope
    public Tasklet initializeDailyProcessingTasklet() {
        return new InitializeDailyProcessingTasklet();
    }

    /**
     * Daily balance report tasklet
     * Generates daily balance summary report
     */
    @Bean
    @StepScope
    public Tasklet dailyBalanceReportTasklet() {
        return new DailyBalanceReportTasklet();
    }

    /**
     * Category analysis report tasklet
     * Generates transaction category analysis report
     */
    @Bean
    @StepScope
    public Tasklet categoryAnalysisReportTasklet() {
        return new CategoryAnalysisReportTasklet();
    }

    /**
     * Account balance reconciliation tasklet
     * Reconciles account balances for accuracy verification
     */
    @Bean
    @StepScope
    public Tasklet accountBalanceReconciliationTasklet() {
        return new AccountBalanceReconciliationTasklet();
    }

    /**
     * Category balance validation tasklet
     * Validates category balance accuracy
     */
    @Bean
    @StepScope
    public Tasklet categoryBalanceValidationTasklet() {
        return new CategoryBalanceValidationTasklet();
    }

    /**
     * Reconciliation report tasklet
     * Generates reconciliation summary report
     */
    @Bean
    @StepScope
    public Tasklet reconciliationReportTasklet() {
        return new ReconciliationReportTasklet();
    }

    // ========== STEP LISTENERS ==========

    /**
     * Transaction processing step listener
     * Monitors processing metrics and handles errors
     */
    @Bean
    public TransactionProcessingStepListener transactionProcessingStepListener() {
        return new TransactionProcessingStepListener();
    }

    // ========== INNER CLASSES AND COMPONENTS ==========

    /**
     * Daily transaction record structure
     * Represents input record from daily transaction file (DALYTRAN)
     */
    public static class DailyTransactionRecord {
        private String transactionId;
        private String cardNumber;
        private String transactionTypeCode;
        private String transactionCategoryCode;
        private String source;
        private String description;
        private BigDecimal amount;
        private String merchantId;
        private String merchantName;
        private String merchantCity;
        private String merchantZip;
        private String originalTimestamp;

        // Getters and setters
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        
        public String getCardNumber() { return cardNumber; }
        public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
        
        public String getTransactionTypeCode() { return transactionTypeCode; }
        public void setTransactionTypeCode(String transactionTypeCode) { this.transactionTypeCode = transactionTypeCode; }
        
        public String getTransactionCategoryCode() { return transactionCategoryCode; }
        public void setTransactionCategoryCode(String transactionCategoryCode) { this.transactionCategoryCode = transactionCategoryCode; }
        
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        
        public String getMerchantId() { return merchantId; }
        public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
        
        public String getMerchantName() { return merchantName; }
        public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
        
        public String getMerchantCity() { return merchantCity; }
        public void setMerchantCity(String merchantCity) { this.merchantCity = merchantCity; }
        
        public String getMerchantZip() { return merchantZip; }
        public void setMerchantZip(String merchantZip) { this.merchantZip = merchantZip; }
        
        public String getOriginalTimestamp() { return originalTimestamp; }
        public void setOriginalTimestamp(String originalTimestamp) { this.originalTimestamp = originalTimestamp; }
    }

    /**
     * Validated transaction record
     * Represents transaction after validation processing
     */
    public static class ValidatedTransactionRecord extends DailyTransactionRecord {
        private String accountId;
        private String customerId;
        private BigDecimal accountCurrentBalance;
        private BigDecimal accountCreditLimit;
        private boolean validationPassed;
        private String validationFailureReason;

        // Additional getters and setters
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        
        public BigDecimal getAccountCurrentBalance() { return accountCurrentBalance; }
        public void setAccountCurrentBalance(BigDecimal accountCurrentBalance) { this.accountCurrentBalance = accountCurrentBalance; }
        
        public BigDecimal getAccountCreditLimit() { return accountCreditLimit; }
        public void setAccountCreditLimit(BigDecimal accountCreditLimit) { this.accountCreditLimit = accountCreditLimit; }
        
        public boolean isValidationPassed() { return validationPassed; }
        public void setValidationPassed(boolean validationPassed) { this.validationPassed = validationPassed; }
        
        public String getValidationFailureReason() { return validationFailureReason; }
        public void setValidationFailureReason(String validationFailureReason) { this.validationFailureReason = validationFailureReason; }
    }

    /**
     * Processed transaction result
     * Represents final processed transaction with posting information
     */
    public static class ProcessedTransactionResult extends ValidatedTransactionRecord {
        private LocalDateTime transactionTimestamp;
        private LocalDateTime processingTimestamp;
        private String processingStatus;

        // Additional getters and setters
        public LocalDateTime getTransactionTimestamp() { return transactionTimestamp; }
        public void setTransactionTimestamp(LocalDateTime transactionTimestamp) { this.transactionTimestamp = transactionTimestamp; }
        
        public LocalDateTime getProcessingTimestamp() { return processingTimestamp; }
        public void setProcessingTimestamp(LocalDateTime processingTimestamp) { this.processingTimestamp = processingTimestamp; }
        
        public String getProcessingStatus() { return processingStatus; }
        public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
    }

    /**
     * Account balance update record
     * Represents account balance changes for processing
     */
    public static class AccountBalanceUpdate {
        private String accountId;
        private BigDecimal currentBalance;
        private BigDecimal creditLimit;
        private BigDecimal currentCycleCredit;
        private BigDecimal currentCycleDebit;
        private BigDecimal totalAmount;
        private Integer rowVersion;
        private BigDecimal newCurrentBalance;
        private BigDecimal newCurrentCycleCredit;
        private BigDecimal newCurrentCycleDebit;

        // Getters and setters
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        
        public BigDecimal getCurrentBalance() { return currentBalance; }
        public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }
        
        public BigDecimal getCreditLimit() { return creditLimit; }
        public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }
        
        public BigDecimal getCurrentCycleCredit() { return currentCycleCredit; }
        public void setCurrentCycleCredit(BigDecimal currentCycleCredit) { this.currentCycleCredit = currentCycleCredit; }
        
        public BigDecimal getCurrentCycleDebit() { return currentCycleDebit; }
        public void setCurrentCycleDebit(BigDecimal currentCycleDebit) { this.currentCycleDebit = currentCycleDebit; }
        
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        
        public Integer getRowVersion() { return rowVersion; }
        public void setRowVersion(Integer rowVersion) { this.rowVersion = rowVersion; }
        
        public BigDecimal getNewCurrentBalance() { return newCurrentBalance; }
        public void setNewCurrentBalance(BigDecimal newCurrentBalance) { this.newCurrentBalance = newCurrentBalance; }
        
        public BigDecimal getNewCurrentCycleCredit() { return newCurrentCycleCredit; }
        public void setNewCurrentCycleCredit(BigDecimal newCurrentCycleCredit) { this.newCurrentCycleCredit = newCurrentCycleCredit; }
        
        public BigDecimal getNewCurrentCycleDebit() { return newCurrentCycleDebit; }
        public void setNewCurrentCycleDebit(BigDecimal newCurrentCycleDebit) { this.newCurrentCycleDebit = newCurrentCycleDebit; }
    }

    /**
     * Category balance update record
     * Represents transaction category balance changes (TCATBAL equivalent)
     */
    public static class CategoryBalanceUpdate {
        private String accountId;
        private String transactionTypeCode;
        private String transactionCategoryCode;
        private BigDecimal balanceChange;
        private Integer transactionCount;

        // Getters and setters
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        
        public String getTransactionTypeCode() { return transactionTypeCode; }
        public void setTransactionTypeCode(String transactionTypeCode) { this.transactionTypeCode = transactionTypeCode; }
        
        public String getTransactionCategoryCode() { return transactionCategoryCode; }
        public void setTransactionCategoryCode(String transactionCategoryCode) { this.transactionCategoryCode = transactionCategoryCode; }
        
        public BigDecimal getBalanceChange() { return balanceChange; }
        public void setBalanceChange(BigDecimal balanceChange) { this.balanceChange = balanceChange; }
        
        public Integer getTransactionCount() { return transactionCount; }
        public void setTransactionCount(Integer transactionCount) { this.transactionCount = transactionCount; }
    }

    /**
     * Rejected transaction record
     * Represents transactions that failed validation
     */
    public static class RejectedTransaction {
        private String transactionId;
        private String cardNumber;
        private String rejectReasonCode;
        private String rejectReasonDescription;
        private String originalRecord;
        private LocalDateTime processingTimestamp;

        // Getters and setters
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        
        public String getCardNumber() { return cardNumber; }
        public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
        
        public String getRejectReasonCode() { return rejectReasonCode; }
        public void setRejectReasonCode(String rejectReasonCode) { this.rejectReasonCode = rejectReasonCode; }
        
        public String getRejectReasonDescription() { return rejectReasonDescription; }
        public void setRejectReasonDescription(String rejectReasonDescription) { this.rejectReasonDescription = rejectReasonDescription; }
        
        public String getOriginalRecord() { return originalRecord; }
        public void setOriginalRecord(String originalRecord) { this.originalRecord = originalRecord; }
        
        public LocalDateTime getProcessingTimestamp() { return processingTimestamp; }
        public void setProcessingTimestamp(LocalDateTime processingTimestamp) { this.processingTimestamp = processingTimestamp; }
    }

    /**
     * Transaction report record
     * Represents formatted transaction data for reporting (CBTRN03C output)
     */
    public static class TransactionReportRecord {
        private String transactionId;
        private String cardNumber;
        private String accountId;
        private String customerName;
        private BigDecimal transactionAmount;
        private String merchantName;
        private String merchantCity;
        private LocalDateTime transactionTimestamp;
        private String transactionTypeCode;
        private String transactionCategoryCode;
        private String typeDescription;
        private String categoryDescription;
        private BigDecimal currentBalance;

        // Getters and setters
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        
        public String getCardNumber() { return cardNumber; }
        public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
        
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        
        public BigDecimal getTransactionAmount() { return transactionAmount; }
        public void setTransactionAmount(BigDecimal transactionAmount) { this.transactionAmount = transactionAmount; }
        
        public String getMerchantName() { return merchantName; }
        public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
        
        public String getMerchantCity() { return merchantCity; }
        public void setMerchantCity(String merchantCity) { this.merchantCity = merchantCity; }
        
        public LocalDateTime getTransactionTimestamp() { return transactionTimestamp; }
        public void setTransactionTimestamp(LocalDateTime transactionTimestamp) { this.transactionTimestamp = transactionTimestamp; }
        
        public String getTransactionTypeCode() { return transactionTypeCode; }
        public void setTransactionTypeCode(String transactionTypeCode) { this.transactionTypeCode = transactionTypeCode; }
        
        public String getTransactionCategoryCode() { return transactionCategoryCode; }
        public void setTransactionCategoryCode(String transactionCategoryCode) { this.transactionCategoryCode = transactionCategoryCode; }
        
        public String getTypeDescription() { return typeDescription; }
        public void setTypeDescription(String typeDescription) { this.typeDescription = typeDescription; }
        
        public String getCategoryDescription() { return categoryDescription; }
        public void setCategoryDescription(String categoryDescription) { this.categoryDescription = categoryDescription; }
        
        public BigDecimal getCurrentBalance() { return currentBalance; }
        public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }
    }

    /**
     * Custom exception for transaction validation failures
     */
    public static class TransactionValidationException extends Exception {
        private final String reasonCode;
        private final String reasonDescription;

        public TransactionValidationException(String reasonCode, String reasonDescription) {
            super(reasonDescription);
            this.reasonCode = reasonCode;
            this.reasonDescription = reasonDescription;
        }

        public String getReasonCode() { return reasonCode; }
        public String getReasonDescription() { return reasonDescription; }
    }

    // ========== PROCESSOR IMPLEMENTATIONS ==========

    /**
     * Transaction validation processor implementation
     * Implements CBTRN02C validation logic with COBOL-equivalent checks
     */
    @Component
    public class TransactionValidationProcessor implements ItemProcessor<DailyTransactionRecord, ValidatedTransactionRecord> {

        @Override
        @Transactional(isolation = Isolation.READ_COMMITTED)
        public ValidatedTransactionRecord process(DailyTransactionRecord item) throws Exception {
            logger.debug("Validating transaction: {}", item.getTransactionId());

            ValidatedTransactionRecord validated = new ValidatedTransactionRecord();
            
            // Copy all fields from input record
            copyTransactionFields(item, validated);

            try {
                // Validation Step 1: Card lookup (CBTRN02C 1500-A-LOOKUP-XREF)
                performCardValidation(validated);

                // Validation Step 2: Account lookup and credit check (CBTRN02C 1500-B-LOOKUP-ACCT)
                performAccountValidation(validated);

                // Validation Step 3: Additional business rule validation
                performBusinessRuleValidation(validated);

                validated.setValidationPassed(true);
                logger.debug("Transaction {} passed validation", item.getTransactionId());

            } catch (TransactionValidationException e) {
                validated.setValidationPassed(false);
                validated.setValidationFailureReason(e.getReasonDescription());
                logger.warn("Transaction {} failed validation: {}", item.getTransactionId(), e.getReasonDescription());
            }

            return validated;
        }

        private void copyTransactionFields(DailyTransactionRecord source, ValidatedTransactionRecord target) {
            target.setTransactionId(source.getTransactionId());
            target.setCardNumber(source.getCardNumber());
            target.setTransactionTypeCode(source.getTransactionTypeCode());
            target.setTransactionCategoryCode(source.getTransactionCategoryCode());
            target.setSource(source.getSource());
            target.setDescription(source.getDescription());
            target.setAmount(source.getAmount());
            target.setMerchantId(source.getMerchantId());
            target.setMerchantName(source.getMerchantName());
            target.setMerchantCity(source.getMerchantCity());
            target.setMerchantZip(source.getMerchantZip());
            target.setOriginalTimestamp(source.getOriginalTimestamp());
        }

        private void performCardValidation(ValidatedTransactionRecord transaction) throws TransactionValidationException {
            // CBTRN02C 1500-A-LOOKUP-XREF logic
            String sql = "SELECT x.account_id, x.customer_id FROM card_account_xref x " +
                        "JOIN cards c ON x.card_number = c.card_number " +
                        "WHERE x.card_number = ? AND c.card_status = 'ACTIVE'";
            
            try {
                Map<String, Object> result = jdbcTemplate.queryForMap(sql, transaction.getCardNumber());
                transaction.setAccountId((String) result.get("account_id"));
                transaction.setCustomerId((String) result.get("customer_id"));
            } catch (Exception e) {
                throw new TransactionValidationException("100", "INVALID CARD NUMBER FOUND");
            }
        }

        private void performAccountValidation(ValidatedTransactionRecord transaction) throws TransactionValidationException {
            // CBTRN02C 1500-B-LOOKUP-ACCT logic
            String sql = "SELECT current_balance, credit_limit, current_cycle_credit, current_cycle_debit, " +
                        "account_expiry_date FROM accounts WHERE account_id = ? AND account_status = 'ACTIVE'";
            
            try {
                Map<String, Object> result = jdbcTemplate.queryForMap(sql, transaction.getAccountId());
                
                BigDecimal currentBalance = (BigDecimal) result.get("current_balance");
                BigDecimal creditLimit = (BigDecimal) result.get("credit_limit");
                BigDecimal currentCycleCredit = (BigDecimal) result.get("current_cycle_credit");
                BigDecimal currentCycleDebit = (BigDecimal) result.get("current_cycle_debit");
                
                transaction.setAccountCurrentBalance(currentBalance);
                transaction.setAccountCreditLimit(creditLimit);

                // Credit limit check (CBTRN02C overlimit validation)
                BigDecimal tempBalance = currentCycleCredit.subtract(currentCycleDebit).add(transaction.getAmount());
                if (creditLimit.compareTo(tempBalance) < 0) {
                    throw new TransactionValidationException("102", "OVERLIMIT TRANSACTION");
                }

                // Account expiration check
                java.sql.Date expiryDate = (java.sql.Date) result.get("account_expiry_date");
                if (expiryDate != null && expiryDate.before(new java.sql.Date(System.currentTimeMillis()))) {
                    throw new TransactionValidationException("103", "TRANSACTION RECEIVED AFTER ACCT EXPIRATION");
                }

            } catch (TransactionValidationException e) {
                throw e;
            } catch (Exception e) {
                throw new TransactionValidationException("101", "ACCOUNT RECORD NOT FOUND");
            }
        }

        private void performBusinessRuleValidation(ValidatedTransactionRecord transaction) throws TransactionValidationException {
            // Additional business rules validation
            
            // Amount validation
            if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                throw new TransactionValidationException("104", "INVALID TRANSACTION AMOUNT");
            }

            // Transaction type validation
            if (transaction.getTransactionTypeCode() == null || transaction.getTransactionTypeCode().trim().isEmpty()) {
                throw new TransactionValidationException("105", "MISSING TRANSACTION TYPE CODE");
            }

            // Merchant validation for purchase transactions
            if ("PU".equals(transaction.getTransactionTypeCode()) && 
                (transaction.getMerchantName() == null || transaction.getMerchantName().trim().isEmpty())) {
                throw new TransactionValidationException("106", "MISSING MERCHANT INFORMATION FOR PURCHASE");
            }
        }
    }

    /**
     * Transaction posting processor implementation
     * Converts validated transactions to posted transaction records
     */
    @Component
    public class TransactionPostingProcessor implements ItemProcessor<ValidatedTransactionRecord, ProcessedTransactionResult> {

        @Override
        @Transactional(isolation = Isolation.READ_COMMITTED)
        public ProcessedTransactionResult process(ValidatedTransactionRecord item) throws Exception {
            if (!item.isValidationPassed()) {
                // Skip rejected transactions in this processor
                return null;
            }

            logger.debug("Posting transaction: {}", item.getTransactionId());

            ProcessedTransactionResult posted = new ProcessedTransactionResult();
            
            // Copy all validated transaction fields
            copyValidatedTransactionFields(item, posted);
            
            // Set processing timestamps (CBTRN02C Z-GET-DB2-FORMAT-TIMESTAMP logic)
            posted.setTransactionTimestamp(parseOriginalTimestamp(item.getOriginalTimestamp()));
            posted.setProcessingTimestamp(LocalDateTime.now());
            posted.setProcessingStatus("POSTED");

            logger.debug("Transaction {} successfully posted", item.getTransactionId());
            return posted;
        }

        private void copyValidatedTransactionFields(ValidatedTransactionRecord source, ProcessedTransactionResult target) {
            target.setTransactionId(source.getTransactionId());
            target.setCardNumber(source.getCardNumber());
            target.setTransactionTypeCode(source.getTransactionTypeCode());
            target.setTransactionCategoryCode(source.getTransactionCategoryCode());
            target.setSource(source.getSource());
            target.setDescription(source.getDescription());
            target.setAmount(source.getAmount());
            target.setMerchantId(source.getMerchantId());
            target.setMerchantName(source.getMerchantName());
            target.setMerchantCity(source.getMerchantCity());
            target.setMerchantZip(source.getMerchantZip());
            target.setAccountId(source.getAccountId());
            target.setCustomerId(source.getCustomerId());
            target.setAccountCurrentBalance(source.getAccountCurrentBalance());
            target.setAccountCreditLimit(source.getAccountCreditLimit());
            target.setValidationPassed(source.isValidationPassed());
        }

        private LocalDateTime parseOriginalTimestamp(String timestamp) {
            try {
                // Parse COBOL timestamp format (YYYY-MM-DD-HH.MM.SS.ssssss)
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS");
                return LocalDateTime.parse(timestamp, formatter);
            } catch (Exception e) {
                logger.warn("Failed to parse timestamp: {}, using current time", timestamp);
                return LocalDateTime.now();
            }
        }
    }

    /**
     * Account balance processor implementation
     * Implements CBTRN02C account balance update logic with COMP-3 precision
     */
    @Component
    public class AccountBalanceProcessor implements ItemProcessor<AccountBalanceUpdate, AccountBalanceUpdate> {

        @Override
        @Transactional(isolation = Isolation.REPEATABLE_READ)
        @Retryable(value = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
        public AccountBalanceUpdate process(AccountBalanceUpdate item) throws Exception {
            logger.debug("Processing account balance update for account: {}", item.getAccountId());

            // Calculate new balances with COBOL COMP-3 precision (2 decimal places)
            BigDecimal newCurrentBalance = item.getCurrentBalance().add(item.getTotalAmount())
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal newCurrentCycleCredit = item.getCurrentCycleCredit();
            BigDecimal newCurrentCycleDebit = item.getCurrentCycleDebit();

            // CBTRN02C 2800-UPDATE-ACCOUNT-REC logic
            if (item.getTotalAmount().compareTo(BigDecimal.ZERO) >= 0) {
                // Credit transaction
                newCurrentCycleCredit = newCurrentCycleCredit.add(item.getTotalAmount())
                        .setScale(2, RoundingMode.HALF_UP);
            } else {
                // Debit transaction
                newCurrentCycleDebit = newCurrentCycleDebit.add(item.getTotalAmount().abs())
                        .setScale(2, RoundingMode.HALF_UP);
            }

            item.setNewCurrentBalance(newCurrentBalance);
            item.setNewCurrentCycleCredit(newCurrentCycleCredit);
            item.setNewCurrentCycleDebit(newCurrentCycleDebit);

            logger.debug("Account {} balance updated: {} -> {}", 
                        item.getAccountId(), item.getCurrentBalance(), newCurrentBalance);

            return item;
        }
    }

    /**
     * Category balance processor implementation
     * Implements CBTRN02C transaction category balance (TCATBAL) logic
     */
    @Component
    public class CategoryBalanceProcessor implements ItemProcessor<CategoryBalanceUpdate, CategoryBalanceUpdate> {

        @Override
        @Transactional(isolation = Isolation.READ_COMMITTED)
        public CategoryBalanceUpdate process(CategoryBalanceUpdate item) throws Exception {
            logger.debug("Processing category balance update for account: {}, type: {}, category: {}", 
                        item.getAccountId(), item.getTransactionTypeCode(), item.getTransactionCategoryCode());

            // CBTRN02C 2700-UPDATE-TCATBAL logic
            // Ensure precision matches COBOL COMP-3 (2 decimal places)
            BigDecimal balanceChange = item.getBalanceChange().setScale(2, RoundingMode.HALF_UP);
            item.setBalanceChange(balanceChange);

            logger.debug("Category balance update processed: account={}, change={}, count={}", 
                        item.getAccountId(), balanceChange, item.getTransactionCount());

            return item;
        }
    }

    /**
     * Reject record processor implementation
     * Processes rejected transactions for error reporting
     */
    @Component
    public class RejectRecordProcessor implements ItemProcessor<RejectedTransaction, RejectedTransaction> {

        @Override
        public RejectedTransaction process(RejectedTransaction item) throws Exception {
            logger.debug("Processing reject record for transaction: {}", item.getTransactionId());

            // Set processing timestamp
            item.setProcessingTimestamp(LocalDateTime.now());

            // Format original record for reject file (CBTRN02C 2500-WRITE-REJECT-REC logic)
            if (item.getOriginalRecord() == null) {
                item.setOriginalRecord(String.format("TXN:%s|CARD:%s|REASON:%s", 
                    item.getTransactionId(), item.getCardNumber(), item.getRejectReasonCode()));
            }

            return item;
        }
    }

    /**
     * Transaction report processor implementation
     * Formats transaction data for detailed reporting (CBTRN03C logic)
     */
    @Component
    public class TransactionReportProcessor implements ItemProcessor<TransactionReportRecord, TransactionReportRecord> {

        @Override
        public TransactionReportRecord process(TransactionReportRecord item) throws Exception {
            logger.debug("Processing transaction report record: {}", item.getTransactionId());

            // CBTRN03C formatting and categorization logic
            // Format monetary amounts with 2 decimal places
            if (item.getTransactionAmount() != null) {
                item.setTransactionAmount(item.getTransactionAmount().setScale(2, RoundingMode.HALF_UP));
            }
            if (item.getCurrentBalance() != null) {
                item.setCurrentBalance(item.getCurrentBalance().setScale(2, RoundingMode.HALF_UP));
            }

            // Ensure proper description formatting
            if (item.getTypeDescription() == null || item.getTypeDescription().trim().isEmpty()) {
                item.setTypeDescription("TYPE-" + item.getTransactionTypeCode());
            }
            if (item.getCategoryDescription() == null || item.getCategoryDescription().trim().isEmpty()) {
                item.setCategoryDescription("CAT-" + item.getTransactionCategoryCode());
            }

            return item;
        }
    }

    // ========== TASKLET IMPLEMENTATIONS ==========

    /**
     * Initialize daily processing tasklet implementation
     * Implements CBTRN01C initialization logic
     */
    @Component
    public class InitializeDailyProcessingTasklet implements Tasklet {

        @Override
        @Transactional
        public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
            logger.info("Initializing daily transaction processing");

            // Clean staging tables from previous run
            int deletedStaged = jdbcTemplate.update("DELETE FROM staged_transactions WHERE processing_date < CURRENT_DATE");
            logger.info("Cleaned {} staged transaction records from previous runs", deletedStaged);

            // Create staging table if not exists
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS staged_transactions (" +
                "transaction_id VARCHAR(16) PRIMARY KEY, " +
                "account_id VARCHAR(11) NOT NULL, " +
                "transaction_amount NUMERIC(15,2) NOT NULL, " +
                "transaction_type_code VARCHAR(2) NOT NULL, " +
                "transaction_category_code VARCHAR(4) NOT NULL, " +
                "processing_status VARCHAR(20) NOT NULL, " +
                "processing_date DATE NOT NULL, " +
                "created_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );

            // Validate daily transaction file exists
            Resource dailyTransFile = new FileSystemResource("${batch.input.dailytran.file:data/dailytran.txt}");
            if (!dailyTransFile.exists()) {
                logger.warn("Daily transaction file not found: {}", dailyTransFile.getFilename());
                throw new IllegalStateException("Daily transaction file not found");
            }

            logger.info("Daily processing initialization completed successfully");
            return RepeatStatus.FINISHED;
        }
    }

    /**
     * Daily balance report tasklet implementation
     */
    @Component
    public class DailyBalanceReportTasklet implements Tasklet {

        @Override
        @Transactional(readOnly = true)
        public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
            logger.info("Generating daily balance report");

            String reportSql = "SELECT a.account_id, c.customer_name, a.current_balance, a.credit_limit, " +
                              "a.current_cycle_credit, a.current_cycle_debit, " +
                              "(a.credit_limit - (a.current_cycle_credit - a.current_cycle_debit)) as available_credit " +
                              "FROM accounts a JOIN customers c ON a.customer_id = c.customer_id " +
                              "ORDER BY a.account_id";

            List<Map<String, Object>> results = jdbcTemplate.queryForList(reportSql);
            
            String reportFile = "output/daily_balance_report_" + 
                               LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt";
            
            try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(reportFile))) {
                writer.println("Daily Balance Report - " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
                writer.println("Account ID|Customer Name|Current Balance|Credit Limit|Cycle Credit|Cycle Debit|Available Credit");
                writer.println("=".repeat(120));
                
                for (Map<String, Object> row : results) {
                    writer.printf("%s|%s|%s|%s|%s|%s|%s%n",
                        row.get("account_id"),
                        row.get("customer_name"),
                        row.get("current_balance"),
                        row.get("credit_limit"),
                        row.get("current_cycle_credit"),
                        row.get("current_cycle_debit"),
                        row.get("available_credit"));
                }
                
                writer.println("=".repeat(120));
                writer.println("Report generated: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }

            logger.info("Daily balance report generated: {}", reportFile);
            return RepeatStatus.FINISHED;
        }
    }

    /**
     * Category analysis report tasklet implementation
     */
    @Component
    public class CategoryAnalysisReportTasklet implements Tasklet {

        @Override
        @Transactional(readOnly = true)
        public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
            logger.info("Generating category analysis report");

            String reportSql = "SELECT cb.transaction_type_code, cb.transaction_category_code, " +
                              "COUNT(cb.account_id) as account_count, " +
                              "SUM(cb.current_balance) as total_balance, " +
                              "AVG(cb.current_balance) as average_balance, " +
                              "MAX(cb.current_balance) as max_balance, " +
                              "MIN(cb.current_balance) as min_balance " +
                              "FROM category_balances cb " +
                              "GROUP BY cb.transaction_type_code, cb.transaction_category_code " +
                              "ORDER BY cb.transaction_type_code, cb.transaction_category_code";

            List<Map<String, Object>> results = jdbcTemplate.queryForList(reportSql);
            
            String reportFile = "output/category_analysis_report_" + 
                               LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt";
            
            try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(reportFile))) {
                writer.println("Category Analysis Report - " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
                writer.println("Type Code|Category Code|Account Count|Total Balance|Average Balance|Max Balance|Min Balance");
                writer.println("=".repeat(120));
                
                for (Map<String, Object> row : results) {
                    writer.printf("%s|%s|%s|%s|%s|%s|%s%n",
                        row.get("transaction_type_code"),
                        row.get("transaction_category_code"),
                        row.get("account_count"),
                        row.get("total_balance"),
                        row.get("average_balance"),
                        row.get("max_balance"),
                        row.get("min_balance"));
                }
                
                writer.println("=".repeat(120));
                writer.println("Report generated: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }

            logger.info("Category analysis report generated: {}", reportFile);
            return RepeatStatus.FINISHED;
        }
    }

    /**
     * Account balance reconciliation tasklet implementation
     */
    @Component
    public class AccountBalanceReconciliationTasklet implements Tasklet {

        @Override
        @Transactional
        public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
            logger.info("Performing account balance reconciliation");

            // Reconcile account balances by comparing transaction totals with account balances
            String reconciliationSql = 
                "SELECT a.account_id, a.current_balance, " +
                "COALESCE(SUM(CASE WHEN t.transaction_amount > 0 THEN t.transaction_amount ELSE 0 END), 0) as total_credits, " +
                "COALESCE(SUM(CASE WHEN t.transaction_amount < 0 THEN ABS(t.transaction_amount) ELSE 0 END), 0) as total_debits, " +
                "a.current_balance - (COALESCE(SUM(t.transaction_amount), 0)) as variance " +
                "FROM accounts a " +
                "LEFT JOIN transactions t ON a.account_id = t.account_id AND DATE(t.transaction_timestamp) = CURRENT_DATE " +
                "GROUP BY a.account_id, a.current_balance " +
                "HAVING ABS(a.current_balance - (COALESCE(SUM(t.transaction_amount), 0))) > 0.01 " +
                "ORDER BY ABS(a.current_balance - (COALESCE(SUM(t.transaction_amount), 0))) DESC";

            List<Map<String, Object>> variances = jdbcTemplate.queryForList(reconciliationSql);
            
            if (!variances.isEmpty()) {
                logger.warn("Found {} accounts with balance variances", variances.size());
                
                for (Map<String, Object> variance : variances) {
                    logger.warn("Account {} has balance variance: {}", 
                              variance.get("account_id"), variance.get("variance"));
                }
                
                // Create variance report
                String reportFile = "output/balance_variances_" + 
                                   LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt";
                
                try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(reportFile))) {
                    writer.println("Balance Variance Report - " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
                    writer.println("Account ID|Current Balance|Total Credits|Total Debits|Variance");
                    writer.println("=".repeat(80));
                    
                    for (Map<String, Object> row : variances) {
                        writer.printf("%s|%s|%s|%s|%s%n",
                            row.get("account_id"),
                            row.get("current_balance"),
                            row.get("total_credits"),
                            row.get("total_debits"),
                            row.get("variance"));
                    }
                }
                
                logger.info("Balance variance report generated: {}", reportFile);
            } else {
                logger.info("All account balances reconciled successfully - no variances found");
            }

            return RepeatStatus.FINISHED;
        }
    }

    /**
     * Category balance validation tasklet implementation
     */
    @Component
    public class CategoryBalanceValidationTasklet implements Tasklet {

        @Override
        @Transactional(readOnly = true)
        public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
            logger.info("Validating category balances");

            // Validate category balances against transaction totals
            String validationSql = 
                "SELECT cb.account_id, cb.transaction_type_code, cb.transaction_category_code, " +
                "cb.current_balance as category_balance, " +
                "COALESCE(SUM(t.transaction_amount), 0) as transaction_total, " +
                "cb.current_balance - COALESCE(SUM(t.transaction_amount), 0) as variance " +
                "FROM category_balances cb " +
                "LEFT JOIN transactions t ON cb.account_id = t.account_id " +
                "AND cb.transaction_type_code = t.transaction_type_code " +
                "AND cb.transaction_category_code = t.transaction_category_code " +
                "AND DATE(t.transaction_timestamp) = CURRENT_DATE " +
                "GROUP BY cb.account_id, cb.transaction_type_code, cb.transaction_category_code, cb.current_balance " +
                "HAVING ABS(cb.current_balance - COALESCE(SUM(t.transaction_amount), 0)) > 0.01";

            List<Map<String, Object>> variances = jdbcTemplate.queryForList(validationSql);
            
            if (!variances.isEmpty()) {
                logger.warn("Found {} category balance variances", variances.size());
                
                String reportFile = "output/category_balance_variances_" + 
                                   LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt";
                
                try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(reportFile))) {
                    writer.println("Category Balance Variance Report - " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));
                    writer.println("Account ID|Type Code|Category Code|Category Balance|Transaction Total|Variance");
                    writer.println("=".repeat(100));
                    
                    for (Map<String, Object> row : variances) {
                        writer.printf("%s|%s|%s|%s|%s|%s%n",
                            row.get("account_id"),
                            row.get("transaction_type_code"),
                            row.get("transaction_category_code"),
                            row.get("category_balance"),
                            row.get("transaction_total"),
                            row.get("variance"));
                    }
                }
                
                logger.info("Category balance variance report generated: {}", reportFile);
            } else {
                logger.info("All category balances validated successfully - no variances found");
            }

            return RepeatStatus.FINISHED;
        }
    }

    /**
     * Reconciliation report tasklet implementation
     */
    @Component
    public class ReconciliationReportTasklet implements Tasklet {

        @Override
        @Transactional(readOnly = true)
        public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
            logger.info("Generating reconciliation summary report");

            // Generate comprehensive reconciliation summary
            String reportFile = "output/reconciliation_summary_" + 
                               LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".txt";
            
            try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(reportFile))) {
                writer.println("Reconciliation Summary Report");
                writer.println("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                writer.println("=".repeat(80));
                writer.println();

                // Transaction processing summary
                writeTransactionSummary(writer);
                writer.println();

                // Account balance summary
                writeAccountBalanceSummary(writer);
                writer.println();

                // Category balance summary
                writeCategoryBalanceSummary(writer);
                writer.println();

                writer.println("=".repeat(80));
                writer.println("End of Reconciliation Summary Report");
            }

            logger.info("Reconciliation summary report generated: {}", reportFile);
            return RepeatStatus.FINISHED;
        }

        private void writeTransactionSummary(java.io.PrintWriter writer) {
            String sql = "SELECT COUNT(*) as total_transactions, " +
                        "SUM(CASE WHEN transaction_amount > 0 THEN transaction_amount ELSE 0 END) as total_credits, " +
                        "SUM(CASE WHEN transaction_amount < 0 THEN ABS(transaction_amount) ELSE 0 END) as total_debits, " +
                        "SUM(transaction_amount) as net_amount " +
                        "FROM transactions WHERE DATE(transaction_timestamp) = CURRENT_DATE";
            
            Map<String, Object> summary = jdbcTemplate.queryForMap(sql);
            
            writer.println("TRANSACTION PROCESSING SUMMARY:");
            writer.printf("Total Transactions Processed: %s%n", summary.get("total_transactions"));
            writer.printf("Total Credit Amount: %s%n", summary.get("total_credits"));
            writer.printf("Total Debit Amount: %s%n", summary.get("total_debits"));
            writer.printf("Net Transaction Amount: %s%n", summary.get("net_amount"));
        }

        private void writeAccountBalanceSummary(java.io.PrintWriter writer) {
            String sql = "SELECT COUNT(*) as total_accounts, " +
                        "SUM(current_balance) as total_balance, " +
                        "AVG(current_balance) as average_balance, " +
                        "SUM(credit_limit) as total_credit_limit " +
                        "FROM accounts WHERE account_status = 'ACTIVE'";
            
            Map<String, Object> summary = jdbcTemplate.queryForMap(sql);
            
            writer.println("ACCOUNT BALANCE SUMMARY:");
            writer.printf("Total Active Accounts: %s%n", summary.get("total_accounts"));
            writer.printf("Total Account Balance: %s%n", summary.get("total_balance"));
            writer.printf("Average Account Balance: %s%n", summary.get("average_balance"));
            writer.printf("Total Credit Limit: %s%n", summary.get("total_credit_limit"));
        }

        private void writeCategoryBalanceSummary(java.io.PrintWriter writer) {
            String sql = "SELECT transaction_type_code, COUNT(*) as category_count, " +
                        "SUM(current_balance) as type_total " +
                        "FROM category_balances " +
                        "GROUP BY transaction_type_code " +
                        "ORDER BY transaction_type_code";
            
            List<Map<String, Object>> summaries = jdbcTemplate.queryForList(sql);
            
            writer.println("CATEGORY BALANCE SUMMARY:");
            writer.println("Type Code|Category Count|Type Total");
            writer.println("-".repeat(40));
            
            for (Map<String, Object> summary : summaries) {
                writer.printf("%s|%s|%s%n",
                    summary.get("transaction_type_code"),
                    summary.get("category_count"),
                    summary.get("type_total"));
            }
        }
    }

    /**
     * Transaction processing step listener implementation
     * Monitors processing metrics and handles errors
     */
    @Component
    public class TransactionProcessingStepListener implements org.springframework.batch.core.StepExecutionListener {

        private AtomicLong processedCount = new AtomicLong(0);
        private AtomicLong rejectedCount = new AtomicLong(0);

        @Override
        public void beforeStep(org.springframework.batch.core.StepExecution stepExecution) {
            logger.info("Starting transaction processing step: {}", stepExecution.getStepName());
            processedCount.set(0);
            rejectedCount.set(0);
        }

        @Override
        public org.springframework.batch.core.ExitStatus afterStep(org.springframework.batch.core.StepExecution stepExecution) {
            logger.info("Transaction processing step completed: {} - Processed: {}, Rejected: {}", 
                       stepExecution.getStepName(), processedCount.get(), rejectedCount.get());
            
            // Update step execution context with metrics
            stepExecution.getExecutionContext().put("processedCount", processedCount.get());
            stepExecution.getExecutionContext().put("rejectedCount", rejectedCount.get());
            
            return stepExecution.getExitStatus();
        }

        public void incrementProcessedCount() {
            processedCount.incrementAndGet();
        }

        public void incrementRejectedCount() {
            rejectedCount.incrementAndGet();
        }
    }
}
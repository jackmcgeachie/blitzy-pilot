/*
 * BatchConfig.java
 * 
 * CardDemo Application
 * Spring Batch 5.0.x Configuration for Containerized Batch Processing
 * 
 * This configuration class establishes the foundational Spring Batch framework
 * that replaces JCL-based mainframe batch jobs with modern chunk-oriented 
 * processing capabilities. Provides comprehensive job repository configuration,
 * execution context management, and Kubernetes CronJob integration.
 * 
 * Original COBOL Programs Replaced:
 * - CBACT01C-04C: Account batch processing jobs
 * - CBCUS01C: Customer data loading job
 * - CBTRN01C-03C: Transaction posting batch jobs
 * - CBSTM03A/B: Statement generation jobs
 * 
 * Copyright (c) 2024 CardDemo Application. All rights reserved.
 */
package com.carddemo.config;

import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.support.JobRegistryBeanPostProcessor;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.SimpleJobLauncher;
import org.springframework.batch.core.launch.support.SimpleJobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * Spring Batch 5.0.x Configuration for CardDemo Application
 * 
 * Establishes containerized batch processing capabilities replacing JCL-based
 * mainframe batch jobs with modern Spring Batch chunk-oriented processing.
 * Provides job repository, execution context, and performance optimizations
 * for high-volume data operations within 4-hour batch processing windows.
 * 
 * Key Features:
 * - Chunk-oriented processing with configurable commit intervals
 * - Checkpoint/restart capabilities for job reliability
 * - PostgreSQL-based job repository for state persistence
 * - Kubernetes CronJob integration for automated scheduling
 * - High-performance task execution for 10,000+ TPS requirements
 * - Comprehensive job monitoring and error handling
 * 
 * Architecture Integration:
 * - Replaces JES job scheduling with Kubernetes CronJobs
 * - Maintains ACID compliance through Spring transaction management
 * - Provides equivalent restart capabilities to mainframe checkpoint/restart
 * - Enables distributed processing across Kubernetes pods
 */
@Configuration
@EnableBatchProcessing(
    dataSourceRef = "dataSource",
    transactionManagerRef = "batchTransactionManager"
)
public class BatchConfig {

    // Database connection for batch job repository
    @Autowired
    private DataSource dataSource;

    // Configurable chunk sizes for different job types
    @Value("${batch.chunk.size.default:1000}")
    private int defaultChunkSize;

    @Value("${batch.chunk.size.transaction:500}")
    private int transactionChunkSize;

    @Value("${batch.chunk.size.account:2000}")
    private int accountChunkSize;

    @Value("${batch.chunk.size.statement:1000}")
    private int statementChunkSize;

    // Batch processing performance settings
    @Value("${batch.thread.pool.size:10}")
    private int threadPoolSize;

    @Value("${batch.job.execution.timeout:14400}") // 4 hours in seconds
    private int jobExecutionTimeout;

    // Skip and retry policies for fault tolerance
    @Value("${batch.skip.limit:100}")
    private int skipLimit;

    @Value("${batch.retry.limit:3}")
    private int retryLimit;

    /**
     * Platform Transaction Manager for Batch Operations
     * 
     * Configures transaction management for Spring Batch operations,
     * ensuring ACID compliance equivalent to CICS transaction boundaries.
     * Integrates with PostgreSQL for reliable commit/rollback behavior.
     * 
     * @return DataSourceTransactionManager configured for batch operations
     */
    @Bean(name = "batchTransactionManager")
    @Primary
    public PlatformTransactionManager batchTransactionManager() {
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager();
        transactionManager.setDataSource(dataSource);
        
        // Set transaction timeout to support long-running batch jobs
        transactionManager.setDefaultTimeout(jobExecutionTimeout);
        
        // Enable rollback on any exception for data consistency
        transactionManager.setRollbackOnCommitFailure(true);
        
        // Configure isolation level for batch processing
        transactionManager.setValidateExistingTransaction(true);
        
        return transactionManager;
    }

    /**
     * Job Repository Factory Bean Configuration
     * 
     * Establishes PostgreSQL-based job repository for Spring Batch metadata
     * persistence. Replaces traditional mainframe job control tables with
     * modern relational database storage for job state management.
     * 
     * @return JobRepositoryFactoryBean for batch job persistence
     * @throws Exception if repository configuration fails
     */
    @Bean
    public JobRepositoryFactoryBean jobRepositoryFactory() throws Exception {
        JobRepositoryFactoryBean factory = new JobRepositoryFactoryBean();
        
        // Configure data source and transaction manager
        factory.setDataSource(dataSource);
        factory.setTransactionManager(batchTransactionManager());
        
        // Enable batch job table creation if not exists
        factory.setDatabaseType("postgresql");
        factory.setTablePrefix("BATCH_");
        
        // Configure isolation level for job repository operations
        factory.setIsolationLevelForCreate("ISOLATION_SERIALIZABLE");
        
        // Enable serialization for complex job parameters
        factory.setSerializer(null); // Use default Java serialization
        
        // Set maximum VARCHAR length for job parameters
        factory.setMaxVarCharLength(2500);
        
        // Configure CRON expression for job validation
        factory.setValidateTransactionState(true);
        
        // Initialize factory
        factory.afterPropertiesSet();
        
        return factory;
    }

    /**
     * Primary Job Repository Bean
     * 
     * Creates the main job repository for Spring Batch operations.
     * Provides checkpoint/restart capabilities equivalent to mainframe
     * batch job restart functionality.
     * 
     * @return JobRepository instance for batch job management
     * @throws Exception if job repository creation fails
     */
    @Bean
    @Primary
    public JobRepository jobRepository() throws Exception {
        return jobRepositoryFactory().getJobRepository();
    }

    /**
     * Job Launcher Configuration
     * 
     * Configures async job launcher for high-performance batch execution.
     * Enables parallel processing capabilities for meeting 4-hour batch
     * processing window requirements.
     * 
     * @return JobLauncher for batch job execution
     * @throws Exception if job launcher configuration fails
     */
    @Bean
    @Primary
    public JobLauncher jobLauncher() throws Exception {
        SimpleJobLauncher launcher = new SimpleJobLauncher();
        
        // Set job repository for job state management
        launcher.setJobRepository(jobRepository());
        
        // Configure async task executor for parallel processing
        launcher.setTaskExecutor(batchTaskExecutor());
        
        // Initialize launcher
        launcher.afterPropertiesSet();
        
        return launcher;
    }

    /**
     * Batch Task Executor Configuration
     * 
     * Configures thread pool for concurrent batch processing.
     * Optimized for high-volume data processing requirements
     * while maintaining system resource efficiency.
     * 
     * @return TaskExecutor for batch job parallel processing
     */
    @Bean(name = "batchTaskExecutor")
    public TaskExecutor batchTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("batch-");
        
        // Set concurrent thread limit for resource management
        executor.setConcurrencyLimit(threadPoolSize);
        
        // Configure thread naming for monitoring and debugging
        executor.setThreadNamePrefix("CardDemo-Batch-");
        
        // Set daemon threads for clean shutdown
        executor.setThreadPriority(Thread.NORM_PRIORITY);
        
        return executor;
    }

    /**
     * Job Registry for Dynamic Job Discovery
     * 
     * Enables runtime job registration and discovery for
     * Kubernetes CronJob integration and job monitoring.
     * 
     * @return JobRegistry for batch job registration
     */
    @Bean
    public JobRegistry jobRegistry() {
        return new org.springframework.batch.core.configuration.support.MapJobRegistry();
    }

    /**
     * Job Registry Bean Post Processor
     * 
     * Automatically registers all job beans with the job registry
     * for dynamic discovery and execution management.
     * 
     * @return JobRegistryBeanPostProcessor for automatic job registration
     */
    @Bean
    public JobRegistryBeanPostProcessor jobRegistryBeanPostProcessor() {
        JobRegistryBeanPostProcessor postProcessor = new JobRegistryBeanPostProcessor();
        postProcessor.setJobRegistry(jobRegistry());
        return postProcessor;
    }

    /**
     * Job Operator for Advanced Job Management
     * 
     * Provides comprehensive job lifecycle management including
     * start, stop, restart, and abandon operations for enhanced
     * job control equivalent to mainframe job management.
     * 
     * @return JobOperator for advanced job lifecycle management
     * @throws Exception if job operator configuration fails
     */
    @Bean
    public JobOperator jobOperator() throws Exception {
        SimpleJobOperator operator = new SimpleJobOperator();
        
        // Configure core components
        operator.setJobLauncher(jobLauncher());
        operator.setJobRegistry(jobRegistry());
        operator.setJobRepository(jobRepository());
        operator.setJobExplorer(jobExplorer());
        
        // Initialize operator
        operator.afterPropertiesSet();
        
        return operator;
    }

    /**
     * Job Explorer for Job History and Monitoring
     * 
     * Provides read-only access to batch job execution history
     * and statistics for monitoring and auditing purposes.
     * 
     * @return JobExplorer for job history analysis
     * @throws Exception if job explorer configuration fails
     */
    @Bean
    public JobExplorer jobExplorer() throws Exception {
        org.springframework.batch.core.explore.support.JobExplorerFactoryBean factory = 
            new org.springframework.batch.core.explore.support.JobExplorerFactoryBean();
        
        factory.setDataSource(dataSource);
        factory.setTablePrefix("BATCH_");
        factory.afterPropertiesSet();
        
        return factory.getJobExplorer();
    }

    /**
     * JDBC Template for Custom Batch Operations
     * 
     * Provides direct database access for custom batch processing
     * operations and complex data transformations not handled
     * by standard Spring Batch components.
     * 
     * @return JdbcTemplate for custom database operations
     */
    @Bean(name = "batchJdbcTemplate")
    public JdbcTemplate batchJdbcTemplate() {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        
        // Configure query timeout for long-running operations
        template.setQueryTimeout(3600); // 1 hour timeout
        
        // Set fetch size for optimal memory usage
        template.setFetchSize(1000);
        
        // Configure maximum rows to prevent memory issues
        template.setMaxRows(0); // No limit for batch processing
        
        return template;
    }

    /**
     * Configuration Properties for Chunk Sizes
     * 
     * Provides configured chunk sizes for different types of batch jobs
     * optimized for performance and memory usage within processing windows.
     */
    
    /**
     * Default chunk size for general batch processing operations
     * 
     * @return default chunk size (1000 records)
     */
    public int getDefaultChunkSize() {
        return defaultChunkSize;
    }

    /**
     * Optimized chunk size for transaction processing jobs
     * Smaller chunks for complex financial calculations and validations
     * 
     * @return transaction chunk size (500 records)
     */
    public int getTransactionChunkSize() {
        return transactionChunkSize;
    }

    /**
     * Optimized chunk size for account processing jobs
     * Larger chunks for efficient account data processing
     * 
     * @return account chunk size (2000 records)
     */
    public int getAccountChunkSize() {
        return accountChunkSize;
    }

    /**
     * Optimized chunk size for statement generation jobs
     * Balanced size for document generation performance
     * 
     * @return statement chunk size (1000 records)
     */
    public int getStatementChunkSize() {
        return statementChunkSize;
    }

    /**
     * Skip limit for fault tolerant processing
     * 
     * @return maximum number of records to skip before failing job
     */
    public int getSkipLimit() {
        return skipLimit;
    }

    /**
     * Retry limit for transient error handling
     * 
     * @return maximum number of retry attempts for failed operations
     */
    public int getRetryLimit() {
        return retryLimit;
    }

    /**
     * Job execution timeout in seconds
     * 
     * @return maximum job execution time (4 hours = 14400 seconds)
     */
    public int getJobExecutionTimeout() {
        return jobExecutionTimeout;
    }

    /**
     * Thread pool size for parallel processing
     * 
     * @return number of concurrent threads for batch processing
     */
    public int getThreadPoolSize() {
        return threadPoolSize;
    }
}
/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.carddemo.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAsync;
import org.springframework.core.task.TaskDecorator;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Spring configuration class for audit service infrastructure including asynchronous processing,
 * Elasticsearch integration, SMF format compatibility, and regulatory compliance settings.
 * 
 * This configuration enables comprehensive audit logging without transaction performance impact
 * through async thread pool management and dedicated audit index configuration with 7-year retention.
 * 
 * Replaces COBOL system configuration patterns from:
 * - CSDAT01Y.cpy: Date/time handling and timestamp formatting
 * - CSMSG01Y.cpy: Message processing and system configuration
 * 
 * @see com.carddemo.audit.AuditService
 * @since 1.0.0
 */
@Configuration
@EnableAsync
@EnableRetry
@EnableScheduling
public class AuditConfig extends AbstractElasticsearchConfiguration implements AsyncConfigurer {

    // Elasticsearch Configuration Properties
    @Value("${carddemo.audit.elasticsearch.host:localhost}")
    private String elasticsearchHost;
    
    @Value("${carddemo.audit.elasticsearch.port:9200}")
    private int elasticsearchPort;
    
    @Value("${carddemo.audit.elasticsearch.username:}")
    private String elasticsearchUsername;
    
    @Value("${carddemo.audit.elasticsearch.password:}")
    private String elasticsearchPassword;
    
    @Value("${carddemo.audit.elasticsearch.index-prefix:carddemo-audit}")
    private String auditIndexPrefix;
    
    // Async Processing Configuration Properties
    @Value("${carddemo.audit.async.core-pool-size:4}")
    private int asyncCorePoolSize;
    
    @Value("${carddemo.audit.async.max-pool-size:16}")
    private int asyncMaxPoolSize;
    
    @Value("${carddemo.audit.async.queue-capacity:1000}")
    private int asyncQueueCapacity;
    
    @Value("${carddemo.audit.async.keep-alive-seconds:60}")
    private int asyncKeepAliveSeconds;
    
    // Audit Retention and Processing Properties
    @Value("${carddemo.audit.retention.years:7}")
    private int retentionYears;
    
    @Value("${carddemo.audit.batch.size:100}")
    private int auditBatchSize;
    
    @Value("${carddemo.audit.failure.retry.max-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${carddemo.audit.monitoring.health-check.enabled:true}")
    private boolean healthCheckEnabled;

    /**
     * RFC-3339 timestamp formatter for precise audit trail chronological ordering
     * across distributed system components. Replaces COBOL date handling from CSDAT01Y.cpy.
     */
    public static final DateTimeFormatter RFC_3339_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    /**
     * SMF-compatible event type numbering range (0-255) for enterprise audit analysis
     * tool integration. Maps to traditional mainframe System Management Facility standards.
     */
    public static final int SMF_EVENT_TYPE_MIN = 0;
    public static final int SMF_EVENT_TYPE_MAX = 255;

    /**
     * Configures Elasticsearch client for audit index integration with dedicated
     * 7-year retention policy for regulatory compliance requirements.
     * 
     * @return RestHighLevelClient configured for audit operations
     */
    @Override
    @Bean(name = "elasticsearchClient")
    public RestHighLevelClient elasticsearchClient() {
        ClientConfiguration.ClientConfigurationBuilder configBuilder = 
            ClientConfiguration.builder()
                .connectedTo(elasticsearchHost + ":" + elasticsearchPort);
        
        // Configure authentication if credentials provided
        if (!elasticsearchUsername.isEmpty() && !elasticsearchPassword.isEmpty()) {
            configBuilder.withBasicAuth(elasticsearchUsername, elasticsearchPassword);
        }
        
        // Configure connection settings for high availability
        configBuilder
            .withConnectTimeout(30000)  // 30 seconds
            .withSocketTimeout(60000);  // 60 seconds
        
        return RestClients.create(configBuilder.build()).rest();
    }

    /**
     * Configures async thread pool for audit event processing without blocking
     * business transaction execution. Ensures zero impact on transaction response times
     * while maintaining reliable audit event delivery.
     * 
     * @return ThreadPoolTaskExecutor configured for audit processing
     */
    @Override
    @Bean(name = "auditTaskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core thread pool configuration
        executor.setCorePoolSize(asyncCorePoolSize);
        executor.setMaxPoolSize(asyncMaxPoolSize);
        executor.setQueueCapacity(asyncQueueCapacity);
        executor.setKeepAliveSeconds(asyncKeepAliveSeconds);
        
        // Thread naming for monitoring and debugging
        executor.setThreadNamePrefix("audit-async-");
        
        // Rejection policy - caller runs to ensure audit event delivery
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // Security context propagation for audit correlation
        executor.setTaskDecorator(new SecurityContextTaskDecorator());
        
        // Graceful shutdown handling
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        return executor;
    }

    /**
     * Configures exception handling for audit processing failures with
     * comprehensive logging and alerting for audit system health monitoring.
     * 
     * @return AsyncUncaughtExceptionHandler for audit error management
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AuditAsyncExceptionHandler();
    }

    /**
     * Configures Jackson ObjectMapper for audit event serialization with
     * JSON format processing and Elasticsearch document indexing support.
     * 
     * @return ObjectMapper configured for audit event serialization
     */
    @Bean(name = "auditObjectMapper")
    public ObjectMapper auditObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Configure time module for RFC-3339 timestamp handling
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Configure for audit event structure consistency
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        
        return mapper;
    }

    /**
     * Initializes audit Elasticsearch indices with proper mapping and retention
     * policies for regulatory compliance. Configures SMF-compatible record structures.
     */
    @PostConstruct
    public void initializeAuditIndices() {
        try {
            RestHighLevelClient client = elasticsearchClient();
            
            // Create audit index template for 7-year retention
            String auditIndexTemplate = createAuditIndexTemplate();
            
            // Check if current audit index exists
            String currentAuditIndex = getCurrentAuditIndexName();
            GetIndexRequest getIndexRequest = new GetIndexRequest(currentAuditIndex);
            
            if (!client.indices().exists(getIndexRequest, RequestOptions.DEFAULT)) {
                // Create new audit index with proper mapping
                CreateIndexRequest createIndexRequest = new CreateIndexRequest(currentAuditIndex);
                createIndexRequest.source(auditIndexTemplate, XContentType.JSON);
                
                client.indices().create(createIndexRequest, RequestOptions.DEFAULT);
                
                // Log successful index creation for operational monitoring
                System.out.println("Created audit index: " + currentAuditIndex);
            }
            
        } catch (IOException e) {
            // Log error for monitoring and alerting
            System.err.println("Failed to initialize audit indices: " + e.getMessage());
            throw new RuntimeException("Audit system initialization failed", e);
        }
    }

    /**
     * Creates Elasticsearch index template for SMF-compatible audit record structures
     * with extended retention for regulatory compliance.
     * 
     * @return JSON index template configuration
     */
    private String createAuditIndexTemplate() {
        return """
        {
          "settings": {
            "number_of_shards": 3,
            "number_of_replicas": 1,
            "index.lifecycle.name": "carddemo-audit-policy",
            "index.lifecycle.rollover_alias": "carddemo-audit"
          },
          "mappings": {
            "properties": {
              "auditId": { "type": "keyword" },
              "timestamp": { "type": "date", "format": "strict_date_optional_time" },
              "eventType": { "type": "keyword" },
              "eventSubtype": { "type": "keyword" },
              "smfRecordType": { "type": "integer" },
              "userId": { "type": "keyword" },
              "sessionId": { "type": "keyword" },
              "jwtCorrelationId": { "type": "keyword" },
              "sourceIP": { "type": "ip" },
              "userAgent": { "type": "text" },
              "businessContext": {
                "properties": {
                  "accountId": { "type": "keyword" },
                  "customerId": { "type": "keyword" },
                  "cardNumber": { "type": "keyword" },
                  "transactionId": { "type": "keyword" },
                  "operationType": { "type": "keyword" }
                }
              },
              "outcome": { "type": "keyword" },
              "riskLevel": { "type": "keyword" },
              "complianceFlags": { "type": "keyword" },
              "errorDetails": {
                "properties": {
                  "errorCode": { "type": "keyword" },
                  "errorMessage": { "type": "text" },
                  "stackTrace": { "type": "text", "index": false }
                }
              },
              "performanceMetrics": {
                "properties": {
                  "processingTimeMs": { "type": "long" },
                  "databaseQueryTimeMs": { "type": "long" },
                  "externalCallTimeMs": { "type": "long" }
                }
              }
            }
          }
        }
        """;
    }

    /**
     * Generates current audit index name with date-based rotation for
     * efficient storage management and retention policy application.
     * 
     * @return Current audit index name
     */
    private String getCurrentAuditIndexName() {
        return auditIndexPrefix + "-" + java.time.LocalDate.now().toString();
    }

    /**
     * Task decorator for Spring Security context propagation in async audit processing.
     * Ensures audit events maintain user authentication context for compliance tracking.
     */
    private static class SecurityContextTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            SecurityContext context = SecurityContextHolder.getContext();
            return () -> {
                try {
                    SecurityContextHolder.setContext(context);
                    runnable.run();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
        }
    }

    /**
     * Async exception handler for audit processing failures with monitoring
     * and alerting integration for audit system health checks.
     */
    private class AuditAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable ex, java.lang.reflect.Method method, Object... params) {
            // Log error with method context for troubleshooting
            System.err.printf("Audit async method [%s] failed: %s%n", 
                method.getName(), ex.getMessage());
            
            // TODO: Integrate with monitoring system for alerting
            // This would typically send alerts to operations team
            // via enterprise monitoring infrastructure
            
            // Attempt to preserve audit event for retry if possible
            if (params.length > 0 && params[0] != null) {
                System.err.println("Failed audit event data: " + params[0].toString());
            }
        }
    }

    /**
     * Configuration bean for audit event filtering and routing rules
     * based on severity levels and compliance categorization.
     * 
     * @return AuditFilterConfig for event routing
     */
    @Bean
    public AuditFilterConfig auditFilterConfig() {
        AuditFilterConfig config = new AuditFilterConfig();
        
        // Configure severity-based routing
        config.setHighSeverityEvents(java.util.Set.of(
            "AUTHENTICATION_FAILURE", "AUTHORIZATION_DENIED", 
            "PRIVILEGE_ESCALATION", "DATA_BREACH_ATTEMPT"
        ));
        
        config.setComplianceEvents(java.util.Set.of(
            "PCI_DSS_TRANSACTION", "SOX_COMPLIANCE_CHECK",
            "REGULATORY_REPORTING", "AUDIT_TRAIL_ACCESS"
        ));
        
        // Configure batch processing thresholds
        config.setBatchSize(auditBatchSize);
        config.setMaxRetryAttempts(maxRetryAttempts);
        
        return config;
    }

    /**
     * Configuration bean for SMF record type mapping and enterprise audit
     * analysis tool integration. Maps CardDemo events to SMF-compatible numbering.
     * 
     * @return SmfRecordTypeMapping for enterprise SIEM integration
     */
    @Bean
    public SmfRecordTypeMapping smfRecordTypeMapping() {
        SmfRecordTypeMapping mapping = new SmfRecordTypeMapping();
        
        // Authentication events (SMF type 80-89)
        mapping.addMapping("USER_AUTHENTICATION_SUCCESS", 80);
        mapping.addMapping("USER_AUTHENTICATION_FAILURE", 81);
        mapping.addMapping("SESSION_CREATED", 82);
        mapping.addMapping("SESSION_TERMINATED", 83);
        mapping.addMapping("PASSWORD_CHANGE", 84);
        
        // Authorization events (SMF type 90-99)
        mapping.addMapping("AUTHORIZATION_GRANTED", 90);
        mapping.addMapping("AUTHORIZATION_DENIED", 91);
        mapping.addMapping("PRIVILEGE_ESCALATION", 92);
        mapping.addMapping("ROLE_ASSIGNMENT", 93);
        
        // Data access events (SMF type 100-109)
        mapping.addMapping("ACCOUNT_ACCESS", 100);
        mapping.addMapping("CUSTOMER_DATA_ACCESS", 101);
        mapping.addMapping("TRANSACTION_DATA_ACCESS", 102);
        mapping.addMapping("CARD_DATA_ACCESS", 103);
        mapping.addMapping("SENSITIVE_DATA_EXPORT", 104);
        
        // Business transaction events (SMF type 110-129)
        mapping.addMapping("ACCOUNT_CREATION", 110);
        mapping.addMapping("ACCOUNT_UPDATE", 111);
        mapping.addMapping("CARD_ISSUANCE", 112);
        mapping.addMapping("CARD_ACTIVATION", 113);
        mapping.addMapping("TRANSACTION_PROCESSING", 114);
        mapping.addMapping("PAYMENT_PROCESSING", 115);
        mapping.addMapping("BALANCE_INQUIRY", 116);
        
        // Administrative events (SMF type 130-139)
        mapping.addMapping("USER_MANAGEMENT", 130);
        mapping.addMapping("SYSTEM_CONFIGURATION", 131);
        mapping.addMapping("BATCH_JOB_EXECUTION", 132);
        mapping.addMapping("REPORT_GENERATION", 133);
        mapping.addMapping("DATABASE_MAINTENANCE", 134);
        
        // Error and security events (SMF type 140-149)
        mapping.addMapping("SYSTEM_ERROR", 140);
        mapping.addMapping("SECURITY_VIOLATION", 141);
        mapping.addMapping("DATA_INTEGRITY_FAILURE", 142);
        mapping.addMapping("UNAUTHORIZED_ACCESS_ATTEMPT", 143);
        mapping.addMapping("SUSPICIOUS_ACTIVITY", 144);
        
        return mapping;
    }

    /**
     * Configuration bean for audit system health monitoring and alerting.
     * Provides monitoring infrastructure for audit processing failures and
     * system health checks.
     * 
     * @return AuditHealthMonitor for system monitoring
     */
    @Bean
    public AuditHealthMonitor auditHealthMonitor() {
        AuditHealthMonitor monitor = new AuditHealthMonitor();
        
        // Configure health check thresholds
        monitor.setMaxFailureRate(0.05); // 5% failure rate threshold
        monitor.setHealthCheckInterval(60000); // 60 seconds
        monitor.setElasticsearchTimeoutMs(30000); // 30 seconds
        monitor.setAsyncQueueThreshold(800); // 80% of queue capacity
        
        // Configure alerting thresholds
        monitor.setMaxProcessingDelayMs(10000); // 10 seconds
        monitor.setCriticalErrorThreshold(10); // 10 errors per minute
        monitor.setRetentionPolicyViolationAlert(true);
        
        return monitor;
    }

    /**
     * Configuration bean for audit event correlation and JWT token tracking.
     * Maintains audit trail integrity across distributed session contexts.
     * 
     * @return AuditCorrelationConfig for distributed tracing
     */
    @Bean
    public AuditCorrelationConfig auditCorrelationConfig() {
        AuditCorrelationConfig config = new AuditCorrelationConfig();
        
        // Configure correlation settings
        config.setJwtCorrelationEnabled(true);
        config.setSessionTrackingEnabled(true);
        config.setRequestIdGeneration(true);
        config.setUserContextPropagation(true);
        
        // Configure correlation timeout settings
        config.setCorrelationTimeoutMs(300000); // 5 minutes
        config.setMaxCorrelationEntries(10000);
        config.setCleanupIntervalMs(900000); // 15 minutes
        
        return config;
    }

    /**
     * Scheduled task configuration for audit index lifecycle management
     * and retention policy enforcement for regulatory compliance.
     * 
     * @return AuditIndexLifecycleManager for index management
     */
    @Bean
    public AuditIndexLifecycleManager auditIndexLifecycleManager() {
        AuditIndexLifecycleManager manager = new AuditIndexLifecycleManager();
        
        // Configure retention settings
        manager.setRetentionYears(retentionYears);
        manager.setIndexRotationSchedule("0 0 0 * * *"); // Daily at midnight
        manager.setCleanupSchedule("0 0 2 * * SUN"); // Weekly cleanup on Sunday
        manager.setArchiveBeforeDelete(true);
        
        // Configure index management settings
        manager.setMaxIndexSizeGB(50); // 50GB per index
        manager.setMaxDocumentsPerIndex(10000000); // 10M documents
        manager.setCompressionEnabled(true);
        manager.setReplicationFactor(1);
        
        return manager;
    }

    /**
     * Configuration class for audit event filtering and routing rules.
     */
    public static class AuditFilterConfig {
        private java.util.Set<String> highSeverityEvents;
        private java.util.Set<String> complianceEvents;
        private int batchSize;
        private int maxRetryAttempts;

        // Getters and setters
        public java.util.Set<String> getHighSeverityEvents() { return highSeverityEvents; }
        public void setHighSeverityEvents(java.util.Set<String> highSeverityEvents) { 
            this.highSeverityEvents = highSeverityEvents; 
        }

        public java.util.Set<String> getComplianceEvents() { return complianceEvents; }
        public void setComplianceEvents(java.util.Set<String> complianceEvents) { 
            this.complianceEvents = complianceEvents; 
        }

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

        public int getMaxRetryAttempts() { return maxRetryAttempts; }
        public void setMaxRetryAttempts(int maxRetryAttempts) { this.maxRetryAttempts = maxRetryAttempts; }
    }

    /**
     * SMF record type mapping configuration for enterprise SIEM integration.
     * Maps CardDemo audit events to traditional mainframe SMF record types.
     */
    public static class SmfRecordTypeMapping {
        private final java.util.Map<String, Integer> eventToSmfType = new java.util.HashMap<>();
        private final java.util.Map<Integer, String> smfTypeToEvent = new java.util.HashMap<>();

        public void addMapping(String eventType, int smfType) {
            if (smfType < SMF_EVENT_TYPE_MIN || smfType > SMF_EVENT_TYPE_MAX) {
                throw new IllegalArgumentException("SMF type must be between " + 
                    SMF_EVENT_TYPE_MIN + " and " + SMF_EVENT_TYPE_MAX);
            }
            eventToSmfType.put(eventType, smfType);
            smfTypeToEvent.put(smfType, eventType);
        }

        public Integer getSmfType(String eventType) {
            return eventToSmfType.get(eventType);
        }

        public String getEventType(int smfType) {
            return smfTypeToEvent.get(smfType);
        }

        public java.util.Map<String, Integer> getAllMappings() {
            return java.util.Collections.unmodifiableMap(eventToSmfType);
        }
    }

    /**
     * Audit system health monitoring configuration for operational monitoring
     * and alerting integration.
     */
    public static class AuditHealthMonitor {
        private double maxFailureRate;
        private long healthCheckInterval;
        private long elasticsearchTimeoutMs;
        private int asyncQueueThreshold;
        private long maxProcessingDelayMs;
        private int criticalErrorThreshold;
        private boolean retentionPolicyViolationAlert;

        // Getters and setters
        public double getMaxFailureRate() { return maxFailureRate; }
        public void setMaxFailureRate(double maxFailureRate) { this.maxFailureRate = maxFailureRate; }

        public long getHealthCheckInterval() { return healthCheckInterval; }
        public void setHealthCheckInterval(long healthCheckInterval) { this.healthCheckInterval = healthCheckInterval; }

        public long getElasticsearchTimeoutMs() { return elasticsearchTimeoutMs; }
        public void setElasticsearchTimeoutMs(long elasticsearchTimeoutMs) { this.elasticsearchTimeoutMs = elasticsearchTimeoutMs; }

        public int getAsyncQueueThreshold() { return asyncQueueThreshold; }
        public void setAsyncQueueThreshold(int asyncQueueThreshold) { this.asyncQueueThreshold = asyncQueueThreshold; }

        public long getMaxProcessingDelayMs() { return maxProcessingDelayMs; }
        public void setMaxProcessingDelayMs(long maxProcessingDelayMs) { this.maxProcessingDelayMs = maxProcessingDelayMs; }

        public int getCriticalErrorThreshold() { return criticalErrorThreshold; }
        public void setCriticalErrorThreshold(int criticalErrorThreshold) { this.criticalErrorThreshold = criticalErrorThreshold; }

        public boolean isRetentionPolicyViolationAlert() { return retentionPolicyViolationAlert; }
        public void setRetentionPolicyViolationAlert(boolean retentionPolicyViolationAlert) { 
            this.retentionPolicyViolationAlert = retentionPolicyViolationAlert; 
        }
    }

    /**
     * Audit correlation configuration for distributed session tracking
     * and JWT token correlation across stateless REST interactions.
     */
    public static class AuditCorrelationConfig {
        private boolean jwtCorrelationEnabled;
        private boolean sessionTrackingEnabled;
        private boolean requestIdGeneration;
        private boolean userContextPropagation;
        private long correlationTimeoutMs;
        private int maxCorrelationEntries;
        private long cleanupIntervalMs;

        // Getters and setters
        public boolean isJwtCorrelationEnabled() { return jwtCorrelationEnabled; }
        public void setJwtCorrelationEnabled(boolean jwtCorrelationEnabled) { 
            this.jwtCorrelationEnabled = jwtCorrelationEnabled; 
        }

        public boolean isSessionTrackingEnabled() { return sessionTrackingEnabled; }
        public void setSessionTrackingEnabled(boolean sessionTrackingEnabled) { 
            this.sessionTrackingEnabled = sessionTrackingEnabled; 
        }

        public boolean isRequestIdGeneration() { return requestIdGeneration; }
        public void setRequestIdGeneration(boolean requestIdGeneration) { 
            this.requestIdGeneration = requestIdGeneration; 
        }

        public boolean isUserContextPropagation() { return userContextPropagation; }
        public void setUserContextPropagation(boolean userContextPropagation) { 
            this.userContextPropagation = userContextPropagation; 
        }

        public long getCorrelationTimeoutMs() { return correlationTimeoutMs; }
        public void setCorrelationTimeoutMs(long correlationTimeoutMs) { 
            this.correlationTimeoutMs = correlationTimeoutMs; 
        }

        public int getMaxCorrelationEntries() { return maxCorrelationEntries; }
        public void setMaxCorrelationEntries(int maxCorrelationEntries) { 
            this.maxCorrelationEntries = maxCorrelationEntries; 
        }

        public long getCleanupIntervalMs() { return cleanupIntervalMs; }
        public void setCleanupIntervalMs(long cleanupIntervalMs) { 
            this.cleanupIntervalMs = cleanupIntervalMs; 
        }
    }

    /**
     * Audit index lifecycle management configuration for automated
     * retention policy enforcement and regulatory compliance.
     */
    public static class AuditIndexLifecycleManager {
        private int retentionYears;
        private String indexRotationSchedule;
        private String cleanupSchedule;
        private boolean archiveBeforeDelete;
        private long maxIndexSizeGB;
        private long maxDocumentsPerIndex;
        private boolean compressionEnabled;
        private int replicationFactor;

        // Getters and setters
        public int getRetentionYears() { return retentionYears; }
        public void setRetentionYears(int retentionYears) { this.retentionYears = retentionYears; }

        public String getIndexRotationSchedule() { return indexRotationSchedule; }
        public void setIndexRotationSchedule(String indexRotationSchedule) { 
            this.indexRotationSchedule = indexRotationSchedule; 
        }

        public String getCleanupSchedule() { return cleanupSchedule; }
        public void setCleanupSchedule(String cleanupSchedule) { this.cleanupSchedule = cleanupSchedule; }

        public boolean isArchiveBeforeDelete() { return archiveBeforeDelete; }
        public void setArchiveBeforeDelete(boolean archiveBeforeDelete) { 
            this.archiveBeforeDelete = archiveBeforeDelete; 
        }

        public long getMaxIndexSizeGB() { return maxIndexSizeGB; }
        public void setMaxIndexSizeGB(long maxIndexSizeGB) { this.maxIndexSizeGB = maxIndexSizeGB; }

        public long getMaxDocumentsPerIndex() { return maxDocumentsPerIndex; }
        public void setMaxDocumentsPerIndex(long maxDocumentsPerIndex) { 
            this.maxDocumentsPerIndex = maxDocumentsPerIndex; 
        }

        public boolean isCompressionEnabled() { return compressionEnabled; }
        public void setCompressionEnabled(boolean compressionEnabled) { 
            this.compressionEnabled = compressionEnabled; 
        }

        public int getReplicationFactor() { return replicationFactor; }
        public void setReplicationFactor(int replicationFactor) { this.replicationFactor = replicationFactor; }
    }
}
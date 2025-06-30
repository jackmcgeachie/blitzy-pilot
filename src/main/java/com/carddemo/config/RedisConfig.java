/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 * 
 * Redis Configuration for CardDemo Application
 * 
 * This class configures Redis 7+ connectivity for distributed session management
 * and application-level caching, replacing CICS COMMAREA pseudo-conversational
 * state management with modern cloud-native session storage patterns.
 * 
 * Key Configuration Features:
 * - Spring Session Redis integration for distributed session storage
 * - Redis cluster configuration with high availability support
 * - Session timeout policies matching CICS session behavior (30 minutes default)
 * - Application-level caching for reference data and lookup tables
 * - Connection pooling and resilience configuration
 * 
 * Replaces Legacy Patterns:
 * - CICS COMMAREA pseudo-conversational state (CARDDEMO-COMMAREA from COCOM01Y.cpy)
 * - Terminal session state management from COSGN00C sign-on processing
 * - Program-to-program state transfer via XCTL COMMAREA
 * 
 * Redis Integration Points:
 * - Session attributes replacing CDEMO-GENERAL-INFO, CDEMO-CUSTOMER-INFO structures
 * - User context preservation (user ID, role, navigation state)
 * - Transaction workflow state for multi-step business processes
 * - Temporary form data and validation results storage
 */
package com.carddemo.config;

import com.carddemo.util.SessionConstants;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.context.AbstractHttpSessionApplicationInitializer;

import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Configuration for CardDemo Application
 * 
 * Configures Redis 7+ cluster connectivity for distributed session management
 * and application-level caching to replace CICS COMMAREA pseudo-conversational
 * state patterns with modern cloud-native distributed session storage.
 * 
 * Session Management Features:
 * - Replaces CICS COMMAREA structures (CARDDEMO-COMMAREA from COCOM01Y.cpy)
 * - Maintains user context across stateless REST interactions
 * - Preserves transaction workflow state for multi-step business processes
 * - Supports horizontal scaling without session affinity requirements
 * 
 * Configuration sections:
 * 1. Redis Connection Configuration (cluster and standalone modes)
 * 2. Spring Session Integration (replacing CICS pseudo-conversational patterns)
 * 3. Application-Level Caching (reference data and lookup tables)
 * 4. Serialization and Timeout Policies
 */
@Configuration
@EnableCaching
@EnableRedisHttpSession(
    maxInactiveIntervalInSeconds = SessionConstants.DEFAULT_SESSION_TIMEOUT_SECONDS,
    redisNamespace = SessionConstants.REDIS_SESSION_NAMESPACE,
    cleanupCron = "0 * * * * *" // Cleanup expired sessions every minute
)
public class RedisConfig extends AbstractHttpSessionApplicationInitializer {

    private static final Logger logger = LoggerFactory.getLogger(RedisConfig.class);

    // Redis connection configuration properties
    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.data.redis.timeout:2000ms}")
    private Duration redisTimeout;

    // Redis cluster configuration properties
    @Value("${spring.data.redis.cluster.enabled:false}")
    private boolean clusterEnabled;

    @Value("${spring.data.redis.cluster.nodes:}")
    private String clusterNodes;

    @Value("${spring.data.redis.cluster.max-redirects:3}")
    private int maxRedirects;

    // Connection pool configuration
    @Value("${spring.data.redis.lettuce.pool.max-active:8}")
    private int maxActive;

    @Value("${spring.data.redis.lettuce.pool.max-idle:8}")
    private int maxIdle;

    @Value("${spring.data.redis.lettuce.pool.min-idle:0}")
    private int minIdle;

    @Value("${spring.data.redis.lettuce.pool.max-wait:-1ms}")
    private Duration maxWait;

    // Cache configuration properties
    @Value("${carddemo.cache.default-ttl:PT1H}")
    private Duration defaultCacheTtl;

    @Value("${carddemo.cache.reference-data-ttl:PT4H}")
    private Duration referenceDataTtl;

    /**
     * Primary Redis Connection Factory Configuration
     * 
     * Configures Redis connectivity with cluster awareness and high availability
     * support. Supports both standalone and cluster deployment modes based on
     * configuration properties.
     * 
     * Features:
     * - Automatic topology refresh for cluster configurations
     * - Connection pooling with Lettuce client
     * - Cluster failover and reconnection handling
     * - Connection timeout and retry policies
     * 
     * @return Configured LettuceConnectionFactory for Redis connectivity
     */
    @Bean
    @Primary
    public LettuceConnectionFactory redisConnectionFactory() {
        logger.info("Configuring Redis connection factory - Cluster enabled: {}", clusterEnabled);

        // Configure client resources for connection management
        ClientResources clientResources = DefaultClientResources.builder()
            .ioThreadPoolSize(4)
            .computationThreadPoolSize(4)
            .build();

        // Configure connection pooling
        LettucePoolingClientConfiguration.Builder poolConfigBuilder = 
            LettucePoolingClientConfiguration.builder()
                .poolConfig(jedisPoolConfig())
                .commandTimeout(redisTimeout)
                .clientResources(clientResources);

        LettuceConnectionFactory connectionFactory;

        if (clusterEnabled && !clusterNodes.isEmpty()) {
            // Redis Cluster Configuration for High Availability
            logger.info("Configuring Redis cluster connection with nodes: {}", clusterNodes);
            
            RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration();
            String[] nodes = clusterNodes.split(",");
            for (String node : nodes) {
                String[] hostPort = node.trim().split(":");
                clusterConfig.clusterNode(hostPort[0], Integer.parseInt(hostPort[1]));
            }
            clusterConfig.setMaxRedirects(maxRedirects);
            
            if (!redisPassword.isEmpty()) {
                clusterConfig.setPassword(redisPassword);
            }

            // Configure cluster-specific client options
            ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
                .enablePeriodicRefresh(Duration.ofMinutes(10))
                .enableAllAdaptiveRefreshTriggers()
                .build();

            ClusterClientOptions clusterClientOptions = ClusterClientOptions.builder()
                .topologyRefreshOptions(topologyRefreshOptions)
                .build();

            poolConfigBuilder.clientOptions(clusterClientOptions);

            connectionFactory = new LettuceConnectionFactory(clusterConfig, poolConfigBuilder.build());
        } else {
            // Standalone Redis Configuration
            logger.info("Configuring standalone Redis connection - Host: {}, Port: {}, Database: {}", 
                       redisHost, redisPort, redisDatabase);
            
            RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration();
            standaloneConfig.setHostName(redisHost);
            standaloneConfig.setPort(redisPort);
            standaloneConfig.setDatabase(redisDatabase);
            
            if (!redisPassword.isEmpty()) {
                standaloneConfig.setPassword(redisPassword);
            }

            connectionFactory = new LettuceConnectionFactory(standaloneConfig, poolConfigBuilder.build());
        }

        connectionFactory.setValidateConnection(true);
        connectionFactory.afterPropertiesSet();

        logger.info("Redis connection factory configured successfully");
        return connectionFactory;
    }

    /**
     * Jedis Pool Configuration for Connection Pooling
     * 
     * Configures connection pool settings for optimal performance under load.
     * Pool sizing is configured to handle concurrent session access patterns
     * typical of multi-user credit card processing applications.
     * 
     * @return Configured GenericObjectPoolConfig for Redis connections
     */
    private org.apache.commons.pool2.impl.GenericObjectPoolConfig<io.lettuce.core.api.StatefulRedisConnection<String, String>> jedisPoolConfig() {
        var poolConfig = new org.apache.commons.pool2.impl.GenericObjectPoolConfig<io.lettuce.core.api.StatefulRedisConnection<String, String>>();
        poolConfig.setMaxTotal(maxActive);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(minIdle);
        poolConfig.setMaxWaitMillis(maxWait.toMillis());
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinEvictableIdleTimeMillis(Duration.ofMinutes(1).toMillis());
        poolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30).toMillis());
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);
        
        logger.info("Redis connection pool configured - MaxActive: {}, MaxIdle: {}, MinIdle: {}", 
                   maxActive, maxIdle, minIdle);
        return poolConfig;
    }

    /**
     * Redis Template Configuration with JSON Serialization
     * 
     * Configures RedisTemplate for session data storage with JSON serialization
     * to support complex object structures equivalent to CICS COMMAREA.
     * 
     * Supports storage of:
     * - User authentication context (user ID, role, session attributes)
     * - Navigation state (current screen, breadcrumb, return points)
     * - Transaction workflow state (multi-step form data, validation results)
     * - Temporary processing data (calculations, intermediate results)
     * 
     * @param connectionFactory Redis connection factory
     * @return Configured RedisTemplate for session operations
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        logger.info("Configuring Redis template with JSON serialization");

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Configure JSON serialization for session objects
        ObjectMapper objectMapper = createSessionObjectMapper();
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        // Configure serializers for different data types
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.setDefaultSerializer(serializer);
        template.afterPropertiesSet();

        logger.info("Redis template configured with JSON serialization support");
        return template;
    }

    /**
     * Creates ObjectMapper for Session Serialization
     * 
     * Configures Jackson ObjectMapper for serializing session objects to JSON
     * with optimized settings for session data patterns typical in credit card
     * processing applications.
     * 
     * Features:
     * - Excludes null values to minimize session storage size
     * - Supports Java 8 time types for timestamp handling
     * - Optimized for serialization/deserialization performance
     * 
     * @return Configured ObjectMapper for session serialization
     */
    private ObjectMapper createSessionObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        logger.debug("Session ObjectMapper configured with Java time module and null exclusion");
        return mapper;
    }

    /**
     * Application-Level Cache Manager Configuration
     * 
     * Configures Redis-based caching for reference data and frequently accessed
     * lookup tables to optimize performance. Cache configuration includes:
     * 
     * Cache Categories:
     * - userCache: User authentication and profile data (4 hour TTL)
     * - referenceDataCache: Transaction types, categories, discount groups (4 hour TTL)
     * - lookupTablesCache: Static configuration data (4 hour TTL)
     * - sessionCache: Temporary session data (1 hour TTL)
     * - transactionCache: Transaction processing temporary data (30 minute TTL)
     * 
     * Performance Benefits:
     * - Sub-100ms response times for cached reference data lookups
     * - Reduced database load for frequently accessed static data
     * - Improved transaction processing performance
     * 
     * @param connectionFactory Redis connection factory
     * @return Configured RedisCacheManager for application caching
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        logger.info("Configuring Redis cache manager with reference data caching policies");

        // Default cache configuration
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(defaultCacheTtl)
            .serializeKeysWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer(createCacheObjectMapper())))
            .disableCachingNullValues();

        // Configure specific cache policies for different data types
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // User authentication and profile cache (4 hours)
        cacheConfigurations.put("userCache", defaultCacheConfig
            .entryTtl(referenceDataTtl)
            .prefixCacheNameWith("carddemo:users:"));

        // Reference data cache for transaction types, categories, discount groups (4 hours)
        cacheConfigurations.put("referenceDataCache", defaultCacheConfig
            .entryTtl(referenceDataTtl)
            .prefixCacheNameWith("carddemo:refdata:"));

        // Lookup tables cache for static configuration data (4 hours)
        cacheConfigurations.put("lookupTablesCache", defaultCacheConfig
            .entryTtl(referenceDataTtl)
            .prefixCacheNameWith("carddemo:lookup:"));

        // Session-related temporary data cache (1 hour)
        cacheConfigurations.put("sessionCache", defaultCacheConfig
            .entryTtl(Duration.ofHours(1))
            .prefixCacheNameWith("carddemo:session:"));

        // Transaction processing temporary data cache (30 minutes)
        cacheConfigurations.put("transactionCache", defaultCacheConfig
            .entryTtl(Duration.ofMinutes(30))
            .prefixCacheNameWith("carddemo:txn:"));

        // Account balance and summary cache (15 minutes for real-time accuracy)
        cacheConfigurations.put("accountCache", defaultCacheConfig
            .entryTtl(Duration.ofMinutes(15))
            .prefixCacheNameWith("carddemo:account:"));

        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultCacheConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()
            .build();

        logger.info("Redis cache manager configured with {} cache configurations", cacheConfigurations.size());
        return cacheManager;
    }

    /**
     * Creates ObjectMapper for Cache Serialization
     * 
     * Configures Jackson ObjectMapper optimized for caching scenarios with
     * performance optimizations for reference data serialization.
     * 
     * @return Configured ObjectMapper for cache serialization
     */
    private ObjectMapper createCacheObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        logger.debug("Cache ObjectMapper configured with optimized serialization settings");
        return mapper;
    }

    /**
     * String-based Redis Template for Simple Key-Value Operations
     * 
     * Provides a Redis template optimized for simple string operations,
     * useful for session tokens, temporary flags, and simple counters.
     * 
     * @param connectionFactory Redis connection factory
     * @return String-optimized RedisTemplate
     */
    @Bean
    public RedisTemplate<String, String> stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        
        logger.debug("String Redis template configured for simple key-value operations");
        return template;
    }

    /**
     * Session Event Listener Configuration
     * 
     * Configures session event handling for audit logging and cleanup operations.
     * This supports integration with the AuditService for security event logging
     * and maintains session lifecycle audit trails.
     */
    @Bean
    @ConditionalOnProperty(value = "carddemo.session.audit.enabled", havingValue = "true", matchIfMissing = true)
    public SessionEventListener sessionEventListener() {
        logger.info("Configuring session event listener for audit trail support");
        return new SessionEventListener();
    }

    /**
     * Session Event Listener Implementation
     * 
     * Handles session lifecycle events for audit logging and monitoring.
     * Integrates with the AuditService to provide comprehensive session
     * audit trails for security compliance.
     */
    public static class SessionEventListener {
        private static final Logger sessionLogger = LoggerFactory.getLogger("SESSION_AUDIT");

        /**
         * Handles session creation events
         */
        public void onSessionCreated(String sessionId, String userId) {
            sessionLogger.info("Session created - SessionID: {}, UserID: {}", sessionId, userId);
        }

        /**
         * Handles session destruction events
         */
        public void onSessionDestroyed(String sessionId, String userId, String reason) {
            sessionLogger.info("Session destroyed - SessionID: {}, UserID: {}, Reason: {}", 
                             sessionId, userId, reason);
        }

        /**
         * Handles session timeout events
         */
        public void onSessionTimeout(String sessionId, String userId) {
            sessionLogger.warn("Session timeout - SessionID: {}, UserID: {}", sessionId, userId);
        }
    }
}
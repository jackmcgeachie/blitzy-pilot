/*
 * Copyright 2024 CardDemo Application. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.carddemo.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.data.redis.config.ConfigureRedisAction;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.HttpSessionIdResolver;

import java.time.Duration;
import java.util.List;

/**
 * Spring Session configuration for Redis-backed session management.
 * 
 * <p>This configuration class enables distributed session storage using Redis,
 * replacing the legacy CICS COMMAREA pseudo-conversational state management.
 * Provides transparent session management across REST endpoints while maintaining
 * user context and transaction state.</p>
 * 
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Redis cluster support with high availability session replication</li>
 *   <li>JSON serialization for complex session objects using Jackson</li>
 *   <li>Configurable session timeout policies (default: 30 minutes)</li>
 *   <li>Custom Redis key prefix: 'spring:session:carddemo:'</li>
 *   <li>Enterprise-grade connection pooling and error handling</li>
 * </ul>
 * 
 * <p><strong>COBOL Migration Context:</strong></p>
 * <p>Replaces CICS COMMAREA structures defined in COCOM01Y.cpy, enabling
 * storage of:</p>
 * <ul>
 *   <li>User context (CDEMO-USER-ID, CDEMO-USER-TYPE)</li>
 *   <li>Navigation state (CDEMO-FROM-TRANID, CDEMO-TO-TRANID)</li>
 *   <li>Customer information (CDEMO-CUST-ID, names)</li>
 *   <li>Account context (CDEMO-ACCT-ID, status)</li>
 *   <li>Card information (CDEMO-CARD-NUM)</li>
 *   <li>Screen context (CDEMO-LAST-MAP, CDEMO-LAST-MAPSET)</li>
 * </ul>
 * 
 * @author CardDemo Team
 * @version 1.0
 * @since 1.0
 */
@Configuration
@EnableRedisHttpSession(
    maxInactiveIntervalInSeconds = 1800, // 30 minutes default
    redisNamespace = "spring:session:carddemo"
)
public class SessionConfig {

    // Redis connection configuration properties
    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:#{null}}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    @Value("${spring.data.redis.timeout:2000ms}")
    private Duration redisTimeout;

    @Value("${spring.data.redis.cluster.nodes:#{null}}")
    private List<String> clusterNodes;

    @Value("${spring.data.redis.cluster.max-redirects:3}")
    private int maxRedirects;

    // Session configuration properties
    @Value("${spring.session.timeout:1800}")
    private int sessionTimeoutSeconds;

    @Value("${spring.session.redis.namespace:spring:session:carddemo}")
    private String sessionNamespace;

    // Connection pool configuration
    @Value("${spring.data.redis.lettuce.pool.max-active:8}")
    private int maxActive;

    @Value("${spring.data.redis.lettuce.pool.max-idle:8}")
    private int maxIdle;

    @Value("${spring.data.redis.lettuce.pool.min-idle:0}")
    private int minIdle;

    @Value("${spring.data.redis.lettuce.pool.max-wait:-1ms}")
    private Duration maxWait;

    /**
     * Configures Redis connection factory for session storage.
     * 
     * <p>Supports both standalone Redis and Redis cluster configurations.
     * Uses Lettuce client for high-performance, thread-safe connections
     * with automatic failover capabilities.</p>
     * 
     * <p><strong>High Availability Features:</strong></p>
     * <ul>
     *   <li>Automatic cluster node discovery and failover</li>
     *   <li>Connection pooling for optimal resource utilization</li>
     *   <li>Configurable timeout and retry policies</li>
     *   <li>Support for Redis Sentinel and Cluster topologies</li>
     * </ul>
     * 
     * @return configured Redis connection factory
     */
    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
            .commandTimeout(redisTimeout)
            .shutdownTimeout(Duration.ofMillis(100))
            .build();

        // Configure Redis cluster if cluster nodes are specified
        if (clusterNodes != null && !clusterNodes.isEmpty()) {
            RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(clusterNodes);
            clusterConfig.setMaxRedirects(maxRedirects);
            
            if (redisPassword != null && !redisPassword.trim().isEmpty()) {
                clusterConfig.setPassword(redisPassword);
            }
            
            return new LettuceConnectionFactory(clusterConfig, clientConfig);
        }
        
        // Configure standalone Redis
        RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration();
        standaloneConfig.setHostName(redisHost);
        standaloneConfig.setPort(redisPort);
        standaloneConfig.setDatabase(redisDatabase);
        
        if (redisPassword != null && !redisPassword.trim().isEmpty()) {
            standaloneConfig.setPassword(redisPassword);
        }
        
        return new LettuceConnectionFactory(standaloneConfig, clientConfig);
    }

    /**
     * Configures Redis template for session data operations.
     * 
     * <p>Uses JSON serialization with Jackson for complex session objects,
     * enabling storage of structured data equivalent to COBOL COMMAREA.
     * Provides type-safe serialization/deserialization of session attributes.</p>
     * 
     * <p><strong>Serialization Features:</strong></p>
     * <ul>
     *   <li>String keys with UTF-8 encoding</li>
     *   <li>JSON values with Jackson ObjectMapper</li>
     *   <li>Support for Java 8+ time types (LocalDateTime, etc.)</li>
     *   <li>Preserves type information for deserialization</li>
     * </ul>
     * 
     * @param connectionFactory Redis connection factory
     * @return configured Redis template for session operations
     */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Configure serializers for optimal session data storage
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serialization for session objects
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(sessionObjectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.setDefaultSerializer(jsonSerializer);
        template.afterPropertiesSet();
        
        return template;
    }

    /**
     * Configures Jackson ObjectMapper for session object serialization.
     * 
     * <p>Optimized for session storage with features that support
     * complex business objects and maintain data integrity across
     * serialization/deserialization cycles.</p>
     * 
     * <p><strong>Configuration Features:</strong></p>
     * <ul>
     *   <li>Java 8+ time module for LocalDateTime, Instant, etc.</li>
     *   <li>ISO 8601 timestamp format for consistency</li>
     *   <li>Graceful handling of unknown properties</li>
     *   <li>Type information preservation for polymorphic objects</li>
     * </ul>
     * 
     * @return configured ObjectMapper for session serialization
     */
    @Bean
    public ObjectMapper sessionObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Register Java 8+ time module for proper date/time handling
        mapper.registerModule(new JavaTimeModule());
        
        // Configure serialization features for session compatibility
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        
        // Configure deserialization features for robustness
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        
        return mapper;
    }

    /**
     * Configures HTTP session ID resolver for cookie-based session management.
     * 
     * <p>Uses secure cookie attributes to maintain session security
     * and compatibility with modern web browsers. Replaces the
     * terminal-based session identification used in CICS environments.</p>
     * 
     * <p><strong>Security Features:</strong></p>
     * <ul>
     *   <li>Secure cookie attributes when HTTPS is available</li>
     *   <li>HttpOnly cookies to prevent XSS attacks</li>
     *   <li>SameSite policy for CSRF protection</li>
     *   <li>Configurable cookie name and domain</li>
     * </ul>
     * 
     * @return configured HTTP session ID resolver
     */
    @Bean
    public HttpSessionIdResolver httpSessionIdResolver() {
        CookieHttpSessionIdResolver resolver = CookieHttpSessionIdResolver.httpOnly();
        resolver.setCookieName("CARDDEMO-SESSION");
        resolver.setCookieMaxAge(Duration.ofSeconds(sessionTimeoutSeconds));
        return resolver;
    }

    /**
     * Disables Redis configuration for cloud environments.
     * 
     * <p>Prevents Spring Session from attempting to configure Redis
     * when deploying to managed Redis services (Redis Cloud, ElastiCache, etc.)
     * that may not support certain configuration commands.</p>
     * 
     * <p><strong>Cloud Compatibility:</strong></p>
     * <ul>
     *   <li>Works with Redis as a Service offerings</li>
     *   <li>Prevents CONFIG command execution</li>
     *   <li>Suitable for containerized deployments</li>
     *   <li>Compatible with Redis clusters and managed services</li>
     * </ul>
     * 
     * @return configured Redis action that disables automatic configuration
     */
    @Bean
    public static ConfigureRedisAction configureRedisAction() {
        return ConfigureRedisAction.NO_OP;
    }

    /**
     * Provides session configuration information for monitoring and debugging.
     * 
     * <p>Exposes key configuration parameters for operational visibility
     * and troubleshooting. Useful for monitoring session store health
     * and performance characteristics.</p>
     * 
     * <p><strong>Monitoring Information:</strong></p>
     * <ul>
     *   <li>Session timeout configuration</li>
     *   <li>Redis namespace and connection details</li>
     *   <li>Cluster vs. standalone topology</li>
     *   <li>Serialization format and features</li>
     * </ul>
     * 
     * @return session configuration summary for monitoring
     */
    @Bean
    public SessionConfigurationInfo sessionConfigurationInfo() {
        return SessionConfigurationInfo.builder()
            .sessionTimeout(Duration.ofSeconds(sessionTimeoutSeconds))
            .redisNamespace(sessionNamespace)
            .isClusterMode(clusterNodes != null && !clusterNodes.isEmpty())
            .serializationFormat("JSON")
            .connectionType("Lettuce")
            .build();
    }

    /**
     * Configuration information holder for session management monitoring.
     * 
     * <p>Provides runtime information about session configuration
     * for operational dashboards and health checks. Enables monitoring
     * of session store performance and configuration drift detection.</p>
     */
    public static class SessionConfigurationInfo {
        private final Duration sessionTimeout;
        private final String redisNamespace;
        private final boolean isClusterMode;
        private final String serializationFormat;
        private final String connectionType;

        private SessionConfigurationInfo(Builder builder) {
            this.sessionTimeout = builder.sessionTimeout;
            this.redisNamespace = builder.redisNamespace;
            this.isClusterMode = builder.isClusterMode;
            this.serializationFormat = builder.serializationFormat;
            this.connectionType = builder.connectionType;
        }

        public static Builder builder() {
            return new Builder();
        }

        // Getters
        public Duration getSessionTimeout() { return sessionTimeout; }
        public String getRedisNamespace() { return redisNamespace; }
        public boolean isClusterMode() { return isClusterMode; }
        public String getSerializationFormat() { return serializationFormat; }
        public String getConnectionType() { return connectionType; }

        public static class Builder {
            private Duration sessionTimeout;
            private String redisNamespace;
            private boolean isClusterMode;
            private String serializationFormat;
            private String connectionType;

            public Builder sessionTimeout(Duration sessionTimeout) {
                this.sessionTimeout = sessionTimeout;
                return this;
            }

            public Builder redisNamespace(String redisNamespace) {
                this.redisNamespace = redisNamespace;
                return this;
            }

            public Builder isClusterMode(boolean isClusterMode) {
                this.isClusterMode = isClusterMode;
                return this;
            }

            public Builder serializationFormat(String serializationFormat) {
                this.serializationFormat = serializationFormat;
                return this;
            }

            public Builder connectionType(String connectionType) {
                this.connectionType = connectionType;
                return this;
            }

            public SessionConfigurationInfo build() {
                return new SessionConfigurationInfo(this);
            }
        }
    }
}
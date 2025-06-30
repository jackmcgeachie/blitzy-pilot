/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.gateway;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Central API Gateway service implementing Spring Cloud Gateway for request routing,
 * load balancing, authentication, and cross-cutting concerns management.
 * 
 * <p><strong>Legacy System Migration:</strong></p>
 * <p>This service replaces the traditional CICS transaction processing architecture
 * with modern cloud-native API gateway patterns while maintaining identical business
 * logic flow and preserving pseudo-conversational state through Redis session management.</p>
 * 
 * <p><strong>COBOL Program Reference:</strong> Replaces CICS EXEC CICS XCTL patterns
 * from COSGN00C.cbl and other transaction programs with REST endpoint routing.</p>
 * 
 * <p><strong>Key Migration Points:</strong></p>
 * <ul>
 *   <li>CICS transaction routing → Spring Cloud Gateway route configuration</li>
 *   <li>COMMAREA context passing → HTTP headers and Redis session correlation</li>
 *   <li>RACF authentication → JWT token validation with Spring Security</li>
 *   <li>Program transfer (XCTL) → HTTP redirect responses</li>
 *   <li>Terminal session management → Stateless REST with distributed session state</li>
 *   <li>CICS response codes → HTTP status codes with standardized error payloads</li>
 * </ul>
 * 
 * <p><strong>API Gateway Capabilities:</strong></p>
 * <ul>
 *   <li>Centralized request routing to microservice endpoints</li>
 *   <li>JWT bearer token authentication for all protected resources</li>
 *   <li>Redis-backed rate limiting with user-specific quotas</li>
 *   <li>Request/response transformation for API versioning</li>
 *   <li>Session correlation for pseudo-conversational state management</li>
 *   <li>Cross-cutting concerns: authentication, logging, metrics, CORS</li>
 *   <li>Load balancing and circuit breaker patterns</li>
 *   <li>External system integration routing</li>
 * </ul>
 * 
 * <p><strong>Security Integration:</strong></p>
 * <ul>
 *   <li>JWT token validation on all requests except authentication endpoints</li>
 *   <li>Role-based routing with Spring Security authorities</li>
 *   <li>Session correlation ID propagation for audit trail continuity</li>
 *   <li>Rate limiting per user with Redis token bucket algorithm</li>
 *   <li>CORS handling for cross-origin requests</li>
 *   <li>Request sanitization and validation</li>
 * </ul>
 * 
 * <p><strong>Performance Characteristics:</strong></p>
 * <ul>
 *   <li>Sub-200ms routing latency for card authorization transactions</li>
 *   <li>10,000+ TPS capacity through reactive non-blocking I/O</li>
 *   <li>Horizontal scaling with Spring Cloud Gateway reactive architecture</li>
 *   <li>Circuit breaker integration for downstream service resilience</li>
 * </ul>
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since Spring Cloud Gateway 4.0.x, Spring Security 6.1.x
 */
@Service
@Configuration
public class ApiGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(ApiGatewayService.class);

    // Route timing constants for performance SLA compliance
    private static final Duration AUTH_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration BUSINESS_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration TRANSACTION_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration EXTERNAL_TIMEOUT = Duration.ofSeconds(30);
    
    // API version headers for backward compatibility
    private static final String API_VERSION_HEADER = "X-API-Version";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String SESSION_ID_HEADER = "X-Session-ID";
    private static final String USER_ID_HEADER = "X-User-ID";
    
    // Rate limiting configuration
    private static final String RATE_LIMITER_CONFIG = "carddemo-rate-limiter";
    
    // Dependencies
    private final JwtDecoder jwtDecoder;
    private final ReactiveStringRedisTemplate redisTemplate;
    
    // Configuration properties
    @Value("${carddemo.gateway.base-url:http://localhost:8080}")
    private String baseUrl;
    
    @Value("${carddemo.gateway.rate-limit.replenish-rate:100}")
    private int rateLimitReplenishRate;
    
    @Value("${carddemo.gateway.rate-limit.burst-capacity:200}")
    private int rateLimitBurstCapacity;
    
    @Value("${carddemo.gateway.external.payment-networks.base-url:http://payment-networks:8090}")
    private String paymentNetworksBaseUrl;
    
    @Value("${carddemo.gateway.external.core-banking.base-url:http://core-banking:8091}")
    private String coreBankingBaseUrl;
    
    @Value("${carddemo.gateway.external.regulatory.base-url:http://regulatory:8092}")
    private String regulatoryBaseUrl;

    /**
     * Constructor with dependency injection for JWT validation and Redis integration.
     * 
     * @param jwtDecoder JWT token decoder for authentication validation
     * @param redisTemplate Redis template for session and rate limiting operations
     */
    @Autowired
    public ApiGatewayService(JwtDecoder jwtDecoder, ReactiveStringRedisTemplate redisTemplate) {
        this.jwtDecoder = jwtDecoder;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Initialize the API Gateway service and log configuration.
     */
    @PostConstruct
    public void init() {
        logger.info("ApiGatewayService initializing - replacing CICS transaction routing");
        logger.info("Base URL configured: {}", baseUrl);
        logger.info("Rate limiting configured: {} requests/min, burst capacity: {}", 
                   rateLimitReplenishRate, rateLimitBurstCapacity);
        logger.info("External systems configured: payments={}, core-banking={}, regulatory={}", 
                   paymentNetworksBaseUrl, coreBankingBaseUrl, regulatoryBaseUrl);
    }

    /**
     * Main route configuration providing centralized routing for all CardDemo endpoints.
     * 
     * <p>This configuration replaces CICS transaction routing patterns with modern
     * REST endpoint routing while maintaining identical functional flow.</p>
     * 
     * @param builder Route locator builder for configuring routes
     * @return RouteLocator with all configured routes
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            // Authentication routes (public access)
            .route("auth-login", r -> r
                .path("/api/v1/auth/**")
                .filters(f -> f
                    .addRequestHeader(CORRELATION_ID_HEADER, generateCorrelationId())
                    .addResponseHeader("X-Service", "AuthenticationService")
                    .hystrix(config -> config
                        .setName("auth-circuit-breaker")
                        .setFallbackUri("forward:/fallback/auth"))
                )
                .uri(baseUrl)
                .metadata("timeout", AUTH_TIMEOUT)
                .metadata("description", "Authentication endpoints - COSGN00C replacement"))
            
            // Main menu routes (authenticated users)
            .route("menu-main", r -> r
                .path("/api/v1/menu/main")
                .filters(f -> f
                    .filter(jwtAuthenticationFilter())
                    .filter(sessionCorrelationFilter())
                    .requestRateLimiter(config -> config
                        .setRateLimiter(userBasedRateLimiter())
                        .setKeyResolver(userKeyResolver()))
                    .addResponseHeader("X-Service", "MenuService")
                )
                .uri(baseUrl)
                .metadata("timeout", BUSINESS_TIMEOUT)
                .metadata("description", "Main menu - COMEN01C replacement"))
            
            // Admin menu routes (admin users only)
            .route("menu-admin", r -> r
                .path("/api/v1/menu/admin")
                .filters(f -> f
                    .filter(jwtAuthenticationFilter())
                    .filter(adminAuthorizationFilter())
                    .filter(sessionCorrelationFilter())
                    .requestRateLimiter(config -> config
                        .setRateLimiter(adminRateLimiter())
                        .setKeyResolver(userKeyResolver()))
                    .addResponseHeader("X-Service", "AdminMenuService")
                )
                .uri(baseUrl)
                .metadata("timeout", BUSINESS_TIMEOUT)
                .metadata("description", "Admin menu - COADM01C replacement"))
            
            // Account management routes
            .route("accounts", r -> r
                .path("/api/v1/accounts/**")
                .filters(f -> f
                    .filter(jwtAuthenticationFilter())
                    .filter(sessionCorrelationFilter())
                    .requestRateLimiter(config -> config
                        .setRateLimiter(userBasedRateLimiter())
                        .setKeyResolver(userKeyResolver()))
                    .addResponseHeader("X-Service", "AccountService")
                    .hystrix(config -> config
                        .setName("account-circuit-breaker")
                        .setFallbackUri("forward:/fallback/account"))
                )
                .uri(baseUrl)
                .metadata("timeout", BUSINESS_TIMEOUT)
                .metadata("description", "Account operations - COACTVWC/COACTUPC replacement"))
            
            // Card management routes
            .route("cards", r -> r
                .path("/api/v1/cards/**")
                .filters(f -> f
                    .filter(jwtAuthenticationFilter())
                    .filter(sessionCorrelationFilter())
                    .requestRateLimiter(config -> config
                        .setRateLimiter(userBasedRateLimiter())
                        .setKeyResolver(userKeyResolver()))
                    .addResponseHeader("X-Service", "CardService")
                    .hystrix(config -> config
                        .setName("card-circuit-breaker")
                        .setFallbackUri("forward:/fallback/card"))
                )
                .uri(baseUrl)
                .metadata("timeout", BUSINESS_TIMEOUT)
                .metadata("description", "Card operations - COCRDLIC/COCRDSLC/COCRDUPC replacement"))
            
            // Transaction processing routes (high-priority, low latency)
            .route("transactions", r -> r
                .path("/api/v1/transactions/**")
                .filters(f -> f
                    .filter(jwtAuthenticationFilter())
                    .filter(sessionCorrelationFilter())
                    .requestRateLimiter(config -> config
                        .setRateLimiter(transactionRateLimiter())
                        .setKeyResolver(userKeyResolver()))
                    .addRequestHeader("X-Priority", "HIGH")
                    .addResponseHeader("X-Service", "TransactionService")
                    .hystrix(config -> config
                        .setName("transaction-circuit-breaker")
                        .setFallbackUri("forward:/fallback/transaction"))
                )
                .uri(baseUrl)
                .metadata("timeout", TRANSACTION_TIMEOUT)
                .metadata("description", "Transaction processing - COTRN00C-02C replacement"))
            
            // Payment processing routes
            .route("payments", r -> r
                .path("/api/v1/payments/**")
                .filters(f -> f
                    .filter(jwtAuthenticationFilter())
                    .filter(sessionCorrelationFilter())
                    .requestRateLimiter(config -> config
                        .setRateLimiter(userBasedRateLimiter())
                        .setKeyResolver(userKeyResolver()))
                    .addResponseHeader("X-Service", "PaymentService")
                    .hystrix(config -> config
                        .setName("payment-circuit-breaker")
                        .setFallbackUri("forward:/fallback/payment"))
                )
                .uri(baseUrl)
                .metadata("timeout", BUSINESS_TIMEOUT)
                .metadata("description", "Payment processing - COBIL00C replacement"))
            
            // User management routes (admin only)
            .route("users", r -> r
                .path("/api/v1/users/**")
                .filters(f -> f
                    .filter(jwtAuthenticationFilter())
                    .filter(adminAuthorizationFilter())
                    .filter(sessionCorrelationFilter())
                    .requestRateLimiter(config -> config
                        .setRateLimiter(adminRateLimiter())
                        .setKeyResolver(userKeyResolver()))
                    .addResponseHeader("X-Service", "UserManagementService")
                )
                .uri(baseUrl)
                .metadata("timeout", BUSINESS_TIMEOUT)
                .metadata("description", "User management - COUSR00C-03C replacement"))
            
            // Report generation routes
            .route("reports", r -> r
                .path("/api/v1/reports/**")
                .filters(f -> f
                    .filter(jwtAuthenticationFilter())
                    .filter(sessionCorrelationFilter())
                    .requestRateLimiter(config -> config
                        .setRateLimiter(userBasedRateLimiter())
                        .setKeyResolver(userKeyResolver()))
                    .addResponseHeader("X-Service", "ReportService")
                )
                .uri(baseUrl)
                .metadata("timeout", Duration.ofSeconds(60))
                .metadata("description", "Report generation - CORPT00C replacement"))
            
            // External system integration routes
            .route("external-payment-networks", r -> r
                .path("/external/payment-networks/**")
                .filters(f -> f
                    .filter(jwtAuthenticationFilter())
                    .filter(externalSystemAuthFilter())
                    .addRequestHeader("X-System", "PaymentNetworks")
                    .addResponseHeader("X-External-Service", "PaymentNetworkAdapter")
                )
                .uri(paymentNetworksBaseUrl)
                .metadata("timeout", EXTERNAL_TIMEOUT)
                .metadata("description", "External payment network integration"))
            
            .route("external-core-banking", r -> r
                .path("/external/core-banking/**")
                .filters(f -> f
                    .filter(jwtAuthenticationFilter())
                    .filter(adminAuthorizationFilter())
                    .addRequestHeader("X-System", "CoreBanking")
                    .addResponseHeader("X-External-Service", "CoreBankingAdapter")
                )
                .uri(coreBankingBaseUrl)
                .metadata("timeout", EXTERNAL_TIMEOUT)
                .metadata("description", "Core banking system integration"))
            
            .route("external-regulatory", r -> r
                .path("/external/regulatory/**")
                .filters(f -> f
                    .filter(jwtAuthenticationFilter())
                    .filter(adminAuthorizationFilter())
                    .addRequestHeader("X-System", "Regulatory")
                    .addResponseHeader("X-External-Service", "RegulatoryAdapter")
                )
                .uri(regulatoryBaseUrl)
                .metadata("timeout", EXTERNAL_TIMEOUT)
                .metadata("description", "Regulatory reporting integration"))
            
            // Health and monitoring routes (no authentication required)
            .route("health", r -> r
                .path("/actuator/health/**")
                .uri(baseUrl)
                .metadata("description", "Health check endpoints"))
            
            .route("metrics", r -> r
                .path("/actuator/metrics/**")
                .filters(f -> f
                    .filter(jwtAuthenticationFilter())
                    .filter(adminAuthorizationFilter())
                )
                .uri(baseUrl)
                .metadata("description", "Metrics endpoints for monitoring"))
            
            .build();
    }

    /**
     * JWT authentication filter for validating bearer tokens on protected routes.
     * 
     * <p>Replaces RACF authentication patterns with modern JWT validation while
     * maintaining identical access control semantics.</p>
     * 
     * @return GatewayFilter for JWT authentication
     */
    private GatewayFilter jwtAuthenticationFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            
            if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
                return handleUnauthorized(exchange, "Missing or invalid Authorization header");
            }
            
            String token = authHeader.substring(7);
            
            try {
                Jwt jwt = jwtDecoder.decode(token);
                
                // Add user context to request headers for downstream services
                ServerHttpRequest modifiedRequest = request.mutate()
                    .header(USER_ID_HEADER, jwt.getSubject())
                    .header("X-Username", jwt.getClaim("username"))
                    .header("X-User-Role", jwt.getClaim("role"))
                    .header("X-Token-Exp", jwt.getExpiresAt().toString())
                    .build();
                
                ServerWebExchange modifiedExchange = exchange.mutate()
                    .request(modifiedRequest)
                    .build();
                
                logger.debug("JWT authentication successful for user: {} ({})", 
                           jwt.getClaim("username"), jwt.getSubject());
                
                return chain.filter(modifiedExchange);
                
            } catch (JwtException e) {
                logger.warn("JWT authentication failed: {}", e.getMessage());
                return handleUnauthorized(exchange, "Invalid JWT token: " + e.getMessage());
            }
        };
    }

    /**
     * Session correlation filter for maintaining pseudo-conversational state.
     * 
     * <p>Replicates CICS COMMAREA functionality through session correlation IDs
     * and Redis session state management.</p>
     * 
     * @return GatewayFilter for session correlation
     */
    private GatewayFilter sessionCorrelationFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String sessionId = request.getHeaders().getFirst(SESSION_ID_HEADER);
            String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
            
            if (!StringUtils.hasText(correlationId)) {
                correlationId = generateCorrelationId();
            }
            
            // Store correlation in Redis for session tracking
            if (StringUtils.hasText(sessionId)) {
                String redisKey = "session:correlation:" + sessionId;
                redisTemplate.opsForValue()
                    .set(redisKey, correlationId, Duration.ofMinutes(30))
                    .subscribe();
            }
            
            ServerHttpRequest modifiedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .header("X-Processing-Time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();
            
            ServerWebExchange modifiedExchange = exchange.mutate()
                .request(modifiedRequest)
                .build();
            
            logger.debug("Session correlation established: sessionId={}, correlationId={}", 
                        sessionId, correlationId);
            
            return chain.filter(modifiedExchange);
        };
    }

    /**
     * Admin authorization filter ensuring only ROLE_ADMIN users access admin routes.
     * 
     * <p>Replicates RACF user type 'A' authorization patterns through Spring Security
     * role validation.</p>
     * 
     * @return GatewayFilter for admin authorization
     */
    private GatewayFilter adminAuthorizationFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String userRole = request.getHeaders().getFirst("X-User-Role");
            
            if (!"A".equals(userRole)) {
                logger.warn("Admin access denied for user role: {}", userRole);
                return handleForbidden(exchange, "Administrative privileges required");
            }
            
            logger.debug("Admin authorization successful for user role: {}", userRole);
            return chain.filter(exchange);
        };
    }

    /**
     * External system authentication filter for external integration endpoints.
     * 
     * @return GatewayFilter for external system authentication
     */
    private GatewayFilter externalSystemAuthFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String userId = request.getHeaders().getFirst(USER_ID_HEADER);
            
            // Add additional headers for external system integration
            ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-Internal-System", "CardDemo")
                .header("X-Request-Time", String.valueOf(System.currentTimeMillis()))
                .header("X-Requesting-User", userId)
                .build();
            
            ServerWebExchange modifiedExchange = exchange.mutate()
                .request(modifiedRequest)
                .build();
            
            return chain.filter(modifiedExchange);
        };
    }

    /**
     * User-based key resolver for rate limiting based on user ID.
     * 
     * @return KeyResolver that extracts user ID from JWT for rate limiting
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
            .cast(SecurityContext.class)
            .map(SecurityContext::getAuthentication)
            .cast(Authentication.class)
            .map(Authentication::getName)
            .switchIfEmpty(Mono.just("anonymous"));
    }

    /**
     * Standard user rate limiter configuration.
     * 
     * @return RedisRateLimiter for regular users
     */
    @Bean
    public RedisRateLimiter userBasedRateLimiter() {
        return new RedisRateLimiter(rateLimitReplenishRate, rateLimitBurstCapacity, 1);
    }

    /**
     * Transaction-specific rate limiter with higher limits for payment processing.
     * 
     * @return RedisRateLimiter for transaction processing
     */
    @Bean
    public RedisRateLimiter transactionRateLimiter() {
        return new RedisRateLimiter(500, 1000, 1); // Higher limits for transactions
    }

    /**
     * Admin rate limiter with elevated limits for administrative operations.
     * 
     * @return RedisRateLimiter for admin users
     */
    @Bean
    public RedisRateLimiter adminRateLimiter() {
        return new RedisRateLimiter(500, 1000, 1); // Higher limits for admins
    }

    /**
     * Global filter for request logging and metrics collection.
     * 
     * @return GlobalFilter for comprehensive request/response logging
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public GlobalFilter requestLoggingFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            long startTime = System.currentTimeMillis();
            
            logger.info("API Gateway Request: {} {} - User: {} - Correlation: {}", 
                       request.getMethod(), 
                       request.getPath(),
                       request.getHeaders().getFirst(USER_ID_HEADER),
                       request.getHeaders().getFirst(CORRELATION_ID_HEADER));
            
            return chain.filter(exchange).then(
                Mono.fromRunnable(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    ServerHttpResponse response = exchange.getResponse();
                    
                    logger.info("API Gateway Response: {} {} - Status: {} - Duration: {}ms", 
                               request.getMethod(),
                               request.getPath(),
                               response.getStatusCode(),
                               duration);
                    
                    // Add response headers for monitoring
                    response.getHeaders().add("X-Response-Time", String.valueOf(duration));
                    response.getHeaders().add("X-Gateway-Version", "1.0");
                })
            );
        };
    }

    /**
     * CORS configuration filter for cross-origin requests.
     * 
     * @return GlobalFilter for CORS handling
     */
    @Bean
    @Order(Ordered.HIGH_PRECEDENCE)
    public GlobalFilter corsFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();
            
            HttpHeaders headers = response.getHeaders();
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            headers.add("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Correlation-ID,X-Session-ID");
            headers.add("Access-Control-Max-Age", "3600");
            
            if (request.getMethod().name().equals("OPTIONS")) {
                response.setStatusCode(HttpStatus.OK);
                return Mono.empty();
            }
            
            return chain.filter(exchange);
        };
    }

    /**
     * Handle unauthorized requests with standardized error response.
     * 
     * @param exchange Server web exchange
     * @param message Error message
     * @return Mono<Void> with unauthorized response
     */
    private Mono<Void> handleUnauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        
        String errorResponse = String.format(
            "{\"error\":\"Unauthorized\",\"message\":\"%s\",\"timestamp\":\"%s\",\"path\":\"%s\"}",
            message,
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            exchange.getRequest().getPath()
        );
        
        return response.writeWith(Mono.just(response.bufferFactory().wrap(errorResponse.getBytes())));
    }

    /**
     * Handle forbidden requests with standardized error response.
     * 
     * @param exchange Server web exchange
     * @param message Error message
     * @return Mono<Void> with forbidden response
     */
    private Mono<Void> handleForbidden(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        
        String errorResponse = String.format(
            "{\"error\":\"Forbidden\",\"message\":\"%s\",\"timestamp\":\"%s\",\"path\":\"%s\"}",
            message,
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            exchange.getRequest().getPath()
        );
        
        return response.writeWith(Mono.just(response.bufferFactory().wrap(errorResponse.getBytes())));
    }

    /**
     * Generate correlation ID for request tracking.
     * 
     * @return String correlation ID
     */
    private String generateCorrelationId() {
        return "GW-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Circuit breaker fallback controller for handling service failures gracefully.
     * 
     * <p>Provides fallback responses when downstream services are unavailable,
     * maintaining system availability and user experience.</p>
     */
    @Service
    public static class FallbackController {

        private static final Logger fallbackLogger = LoggerFactory.getLogger(FallbackController.class);

        /**
         * Authentication service fallback.
         * 
         * @param exchange Server web exchange
         * @return Mono<Void> with fallback response
         */
        public Mono<Void> authFallback(ServerWebExchange exchange) {
            fallbackLogger.warn("Authentication service unavailable - providing fallback response");
            return createFallbackResponse(exchange, "Authentication service temporarily unavailable", 
                                        HttpStatus.SERVICE_UNAVAILABLE);
        }

        /**
         * Account service fallback.
         * 
         * @param exchange Server web exchange
         * @return Mono<Void> with fallback response
         */
        public Mono<Void> accountFallback(ServerWebExchange exchange) {
            fallbackLogger.warn("Account service unavailable - providing fallback response");
            return createFallbackResponse(exchange, "Account service temporarily unavailable", 
                                        HttpStatus.SERVICE_UNAVAILABLE);
        }

        /**
         * Card service fallback.
         * 
         * @param exchange Server web exchange
         * @return Mono<Void> with fallback response
         */
        public Mono<Void> cardFallback(ServerWebExchange exchange) {
            fallbackLogger.warn("Card service unavailable - providing fallback response");
            return createFallbackResponse(exchange, "Card service temporarily unavailable", 
                                        HttpStatus.SERVICE_UNAVAILABLE);
        }

        /**
         * Transaction service fallback.
         * 
         * @param exchange Server web exchange
         * @return Mono<Void> with fallback response
         */
        public Mono<Void> transactionFallback(ServerWebExchange exchange) {
            fallbackLogger.warn("Transaction service unavailable - providing fallback response");
            return createFallbackResponse(exchange, "Transaction service temporarily unavailable", 
                                        HttpStatus.SERVICE_UNAVAILABLE);
        }

        /**
         * Payment service fallback.
         * 
         * @param exchange Server web exchange
         * @return Mono<Void> with fallback response
         */
        public Mono<Void> paymentFallback(ServerWebExchange exchange) {
            fallbackLogger.warn("Payment service unavailable - providing fallback response");
            return createFallbackResponse(exchange, "Payment service temporarily unavailable", 
                                        HttpStatus.SERVICE_UNAVAILABLE);
        }

        /**
         * Create standardized fallback response.
         * 
         * @param exchange Server web exchange
         * @param message Error message
         * @param status HTTP status
         * @return Mono<Void> with fallback response
         */
        private Mono<Void> createFallbackResponse(ServerWebExchange exchange, String message, HttpStatus status) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(status);
            response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            response.getHeaders().add("X-Fallback", "true");
            
            String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
            
            String errorResponse = String.format(
                "{\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\",\"path\":\"%s\",\"correlationId\":\"%s\",\"fallback\":true}",
                status.getReasonPhrase(),
                message,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                exchange.getRequest().getPath(),
                correlationId != null ? correlationId : "unknown"
            );
            
            return response.writeWith(Mono.just(response.bufferFactory().wrap(errorResponse.getBytes())));
        }
    }

    /**
     * Gateway metrics and monitoring configuration.
     * 
     * <p>Provides comprehensive metrics collection for API Gateway performance
     * monitoring and SLA compliance tracking.</p>
     */
    @Service
    public static class GatewayMetrics {

        private static final Logger metricsLogger = LoggerFactory.getLogger(GatewayMetrics.class);

        /**
         * Global filter for metrics collection and performance monitoring.
         * 
         * @return GlobalFilter for metrics collection
         */
        @Bean
        @Order(Ordered.LOWEST_PRECEDENCE)
        public GlobalFilter metricsCollectionFilter() {
            return (exchange, chain) -> {
                String routeId = exchange.getAttribute("org.springframework.cloud.gateway.support.RouteDefinitionRouteLocator.routeDefinition.id");
                long startTime = System.nanoTime();
                
                return chain.filter(exchange).then(
                    Mono.fromRunnable(() -> {
                        long duration = System.nanoTime() - startTime;
                        double durationMs = duration / 1_000_000.0;
                        
                        ServerHttpResponse response = exchange.getResponse();
                        HttpStatus statusCode = response.getStatusCode();
                        
                        // Log performance metrics
                        metricsLogger.info("Gateway Metrics - Route: {}, Status: {}, Duration: {:.2f}ms", 
                                         routeId, statusCode, durationMs);
                        
                        // Add performance headers for monitoring
                        response.getHeaders().add("X-Route-Duration", String.format("%.2f", durationMs));
                        response.getHeaders().add("X-Route-ID", routeId != null ? routeId : "unknown");
                        
                        // Check SLA compliance
                        if (durationMs > 200.0) {
                            metricsLogger.warn("SLA violation detected - Route: {}, Duration: {:.2f}ms", 
                                             routeId, durationMs);
                        }
                    })
                );
            };
        }

        /**
         * Health check filter for gateway monitoring.
         * 
         * @return GlobalFilter for health check monitoring
         */
        @Bean
        public GlobalFilter healthCheckFilter() {
            return (exchange, chain) -> {
                ServerHttpRequest request = exchange.getRequest();
                
                // Add health indicators to response
                if (request.getPath().value().contains("/actuator/health")) {
                    return chain.filter(exchange).then(
                        Mono.fromRunnable(() -> {
                            ServerHttpResponse response = exchange.getResponse();
                            response.getHeaders().add("X-Gateway-Health", "UP");
                            response.getHeaders().add("X-Gateway-Version", "1.0");
                            response.getHeaders().add("X-Service-Count", "8"); // Core services count
                        })
                    );
                }
                
                return chain.filter(exchange);
            };
        }
    }

    /**
     * Gateway security configuration for enhanced protection.
     * 
     * <p>Provides additional security filters beyond basic JWT authentication
     * including request sanitization and security headers.</p>
     */
    @Service
    public static class GatewaySecurity {

        private static final Logger securityLogger = LoggerFactory.getLogger(GatewaySecurity.class);

        /**
         * Security headers filter for enhanced protection.
         * 
         * @return GlobalFilter for security headers
         */
        @Bean
        @Order(Ordered.HIGH_PRECEDENCE + 10)
        public GlobalFilter securityHeadersFilter() {
            return (exchange, chain) -> {
                return chain.filter(exchange).then(
                    Mono.fromRunnable(() -> {
                        ServerHttpResponse response = exchange.getResponse();
                        HttpHeaders headers = response.getHeaders();
                        
                        // Security headers
                        headers.add("X-Content-Type-Options", "nosniff");
                        headers.add("X-Frame-Options", "DENY");
                        headers.add("X-XSS-Protection", "1; mode=block");
                        headers.add("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
                        headers.add("Content-Security-Policy", "default-src 'self'");
                        headers.add("Referrer-Policy", "strict-origin-when-cross-origin");
                        
                        // API Gateway identification
                        headers.add("X-Powered-By", "CardDemo-Gateway");
                        headers.add("Server", "CardDemo-API-Gateway/1.0");
                    })
                );
            };
        }

        /**
         * Request sanitization filter for input validation.
         * 
         * @return GlobalFilter for request sanitization
         */
        @Bean
        public GlobalFilter requestSanitizationFilter() {
            return (exchange, chain) -> {
                ServerHttpRequest request = exchange.getRequest();
                String path = request.getPath().value();
                String queryString = request.getURI().getQuery();
                
                // Basic security checks
                if (containsSuspiciousContent(path) || containsSuspiciousContent(queryString)) {
                    securityLogger.warn("Suspicious request detected - Path: {}, Query: {}", path, queryString);
                    return handleSecurityViolation(exchange, "Request contains suspicious content");
                }
                
                // Rate limiting check for excessive requests
                String userAgent = request.getHeaders().getFirst(HttpHeaders.USER_AGENT);
                if (userAgent != null && userAgent.length() > 1000) {
                    securityLogger.warn("Suspicious User-Agent header detected: {}", userAgent.substring(0, 100));
                    return handleSecurityViolation(exchange, "Invalid User-Agent header");
                }
                
                return chain.filter(exchange);
            };
        }

        /**
         * Check for suspicious content in request parameters.
         * 
         * @param content Content to check
         * @return true if suspicious content detected
         */
        private boolean containsSuspiciousContent(String content) {
            if (content == null) return false;
            
            String lowerContent = content.toLowerCase();
            return lowerContent.contains("<script") || 
                   lowerContent.contains("javascript:") ||
                   lowerContent.contains("vbscript:") ||
                   lowerContent.contains("onload=") ||
                   lowerContent.contains("onerror=") ||
                   lowerContent.contains("../") ||
                   lowerContent.contains("..\\") ||
                   lowerContent.contains("union select") ||
                   lowerContent.contains("drop table");
        }

        /**
         * Handle security violations with appropriate response.
         * 
         * @param exchange Server web exchange
         * @param message Security violation message
         * @return Mono<Void> with security violation response
         */
        private Mono<Void> handleSecurityViolation(ServerWebExchange exchange, String message) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            
            String errorResponse = String.format(
                "{\"error\":\"Security Violation\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );
            
            return response.writeWith(Mono.just(response.bufferFactory().wrap(errorResponse.getBytes())));
        }
    }

    /**
     * API versioning and backward compatibility support.
     * 
     * <p>Provides version-aware routing and transformation for maintaining
     * backward compatibility with legacy clients.</p>
     */
    @Service
    public static class ApiVersioning {

        private static final Logger versionLogger = LoggerFactory.getLogger(ApiVersioning.class);

        /**
         * API version resolution filter.
         * 
         * @return GlobalFilter for API version handling
         */
        @Bean
        public GlobalFilter apiVersionFilter() {
            return (exchange, chain) -> {
                ServerHttpRequest request = exchange.getRequest();
                String apiVersion = extractApiVersion(request);
                
                // Add version information to request context
                ServerHttpRequest modifiedRequest = request.mutate()
                    .header(API_VERSION_HEADER, apiVersion)
                    .header("X-API-Compatibility", determineCompatibility(apiVersion))
                    .build();
                
                ServerWebExchange modifiedExchange = exchange.mutate()
                    .request(modifiedRequest)
                    .build();
                
                versionLogger.debug("API version resolved: {} for path: {}", apiVersion, request.getPath());
                
                return chain.filter(modifiedExchange).then(
                    Mono.fromRunnable(() -> {
                        ServerHttpResponse response = exchange.getResponse();
                        response.getHeaders().add("X-API-Version", apiVersion);
                        response.getHeaders().add("X-Supported-Versions", "v1");
                    })
                );
            };
        }

        /**
         * Extract API version from request.
         * 
         * @param request Server HTTP request
         * @return API version string
         */
        private String extractApiVersion(ServerHttpRequest request) {
            // Check Accept header first
            String acceptHeader = request.getHeaders().getFirst(HttpHeaders.ACCEPT);
            if (acceptHeader != null && acceptHeader.contains("application/vnd.carddemo.v")) {
                String[] parts = acceptHeader.split("vnd.carddemo.v");
                if (parts.length > 1) {
                    String version = parts[1].split("\\+")[0];
                    return "v" + version;
                }
            }
            
            // Check path-based versioning
            String path = request.getPath().value();
            if (path.startsWith("/api/v")) {
                String[] pathParts = path.split("/");
                if (pathParts.length > 2 && pathParts[2].startsWith("v")) {
                    return pathParts[2];
                }
            }
            
            // Default to v1
            return "v1";
        }

        /**
         * Determine API compatibility level.
         * 
         * @param version API version
         * @return Compatibility level
         */
        private String determineCompatibility(String version) {
            switch (version) {
                case "v1":
                    return "FULL";
                default:
                    return "UNKNOWN";
            }
        }
    }

    /**
     * Gateway configuration properties for external dependencies.
     * 
     * <p>Centralizes configuration for service discovery, timeouts, and
     * external system integration.</p>
     */
    @Service
    public static class GatewayConfiguration {

        /**
         * Service discovery configuration for dynamic routing.
         * 
         * @return Configuration for service discovery
         */
        public String getServiceDiscoveryConfig() {
            return "spring.cloud.gateway.discovery.locator.enabled=true";
        }

        /**
         * Default timeout configuration for all routes.
         * 
         * @return Default timeout configuration
         */
        public Duration getDefaultTimeout() {
            return Duration.ofSeconds(30);
        }

        /**
         * Circuit breaker configuration.
         * 
         * @return Circuit breaker configuration
         */
        public String getCircuitBreakerConfig() {
            return "resilience4j.circuitbreaker.instances.default.failure-rate-threshold=50";
        }
    }
}
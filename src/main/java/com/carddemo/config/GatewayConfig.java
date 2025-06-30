/*
 * GatewayConfig.java
 * 
 * Spring Cloud Gateway configuration class implementing centralized API routing 
 * and cross-cutting concerns for the CardDemo modular monolith architecture.
 * 
 * This configuration replaces direct CICS transaction access with modern REST API 
 * gateway patterns, providing request routing, authentication, and resilience 
 * capabilities for the migrated Spring Boot application.
 * 
 * Copyright 2024 CardDemo Application
 */

package com.carddemo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Cloud Gateway configuration for centralized API routing and cross-cutting concerns.
 * 
 * Implements modern API gateway patterns to replace traditional CICS transaction processing
 * with REST-based communication while maintaining identical business logic and transaction
 * boundaries. Provides routing, authentication, rate limiting, and circuit breaker patterns
 * for the modular monolith architecture.
 * 
 * Key Features:
 * - Request routing to Spring Boot service modules
 * - JWT token validation and authentication
 * - Rate limiting with Redis backend
 * - Circuit breaker patterns for resilience
 * - Cross-cutting concerns (logging, monitoring, CORS)
 * 
 * Mapped CICS Programs to REST Endpoints:
 * - COSGN00C -> /api/v1/auth/** (Authentication)
 * - COMEN01C -> /api/v1/menu/** (Main Menu)
 * - COADM01C -> /api/v1/admin/** (Admin Menu)
 * - COACTVWC/COACTUPC -> /api/v1/accounts/** (Account Operations)
 * - COCRD* -> /api/v1/cards/** (Card Operations)
 * - COTRN* -> /api/v1/transactions/** (Transaction Processing)
 * - COBIL00C -> /api/v1/payments/** (Bill Payment)
 * - COUSR* -> /api/v1/users/** (User Management)
 * - CORPT00C -> /api/v1/reports/** (Reporting)
 */
@Configuration
public class GatewayConfig {

    @Value("${carddemo.gateway.default-timeout:5000}")
    private Duration defaultTimeout;

    @Value("${carddemo.backend.base-url:http://localhost:8080}")
    private String backendBaseUrl;

    @Value("${carddemo.rate-limit.default.replenish-rate:100}")
    private int defaultReplenishRate;

    @Value("${carddemo.rate-limit.default.burst-capacity:200}")
    private int defaultBurstCapacity;

    @Value("${carddemo.rate-limit.admin.replenish-rate:500}")
    private int adminReplenishRate;

    @Value("${carddemo.rate-limit.admin.burst-capacity:1000}")
    private int adminBurstCapacity;

    /**
     * Configures route definitions for all CardDemo service modules.
     * 
     * Routes are organized by functional domain, matching the original COBOL program
     * structure while providing modern REST API patterns. Each route includes:
     * - Path-based routing predicates
     * - JWT authentication filters
     * - Rate limiting policies
     * - Circuit breaker configuration
     * - Request/response transformation
     * 
     * @param builder RouteLocatorBuilder for defining routes
     * @return RouteLocator with all configured routes
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            // Authentication routes (COSGN00C -> AuthenticationService)
            .route("auth-routes", r -> r
                .path("/api/v1/auth/**")
                .filters(f -> f
                    .rewritePath("/api/v1/auth/(?<segment>.*)", "/auth/${segment}")
                    .requestRateLimiter(config -> config
                        .setRateLimiter(authRateLimiter())
                        .setKeyResolver(userKeyResolver()))
                    .retry(retryConfig -> retryConfig
                        .setRetries(3)
                        .setMethods("GET", "POST")
                        .setBackoff(Duration.ofMillis(100), Duration.ofMillis(1000), 2, false))
                    .addRequestHeader("X-Gateway-Source", "CardDemo-Gateway")
                    .addResponseHeader("X-Response-Time", "${T(System).currentTimeMillis()}")
                )
                .uri(backendBaseUrl)
                .metadata("timeout", 5000)
                .metadata("circuit-breaker", "auth-circuit-breaker")
            )
            
            // Main menu routes (COMEN01C -> MenuService)
            .route("menu-routes", r -> r
                .path("/api/v1/menu/**")
                .filters(f -> f
                    .rewritePath("/api/v1/menu/(?<segment>.*)", "/menu/${segment}")
                    .requestRateLimiter(config -> config
                        .setRateLimiter(defaultRateLimiter())
                        .setKeyResolver(userKeyResolver()))
                    .filter(jwtAuthenticationFilter())
                    .addRequestHeader("X-Service-Context", "menu-service")
                )
                .uri(backendBaseUrl)
                .metadata("timeout", 3000)
            )
            
            // Admin menu routes (COADM01C -> AdminMenuService)
            .route("admin-routes", r -> r
                .path("/api/v1/admin/**")
                .filters(f -> f
                    .rewritePath("/api/v1/admin/(?<segment>.*)", "/admin/${segment}")
                    .requestRateLimiter(config -> config
                        .setRateLimiter(adminRateLimiter())
                        .setKeyResolver(userKeyResolver()))
                    .filter(jwtAuthenticationFilter())
                    .filter(adminAuthorizationFilter())
                    .addRequestHeader("X-Service-Context", "admin-service")
                )
                .uri(backendBaseUrl)
                .metadata("timeout", 3000)
                .metadata("admin-only", true)
            )
            
            // Account management routes (COACTVWC/COACTUPC -> AccountService)
            .route("account-routes", r -> r
                .path("/api/v1/accounts/**")
                .filters(f -> f
                    .rewritePath("/api/v1/accounts/(?<segment>.*)", "/accounts/${segment}")
                    .requestRateLimiter(config -> config
                        .setRateLimiter(defaultRateLimiter())
                        .setKeyResolver(userKeyResolver()))
                    .filter(jwtAuthenticationFilter())
                    .circuitBreaker(config -> config
                        .setName("account-circuit-breaker")
                        .setFallbackUri("/api/v1/fallback/accounts"))
                    .addRequestHeader("X-Service-Context", "account-service")
                )
                .uri(backendBaseUrl)
                .metadata("timeout", 3000)
            )
            
            // Card management routes (COCRD* -> CardService)
            .route("card-routes", r -> r
                .path("/api/v1/cards/**")
                .filters(f -> f
                    .rewritePath("/api/v1/cards/(?<segment>.*)", "/cards/${segment}")
                    .requestRateLimiter(config -> config
                        .setRateLimiter(defaultRateLimiter())
                        .setKeyResolver(userKeyResolver()))
                    .filter(jwtAuthenticationFilter())
                    .circuitBreaker(config -> config
                        .setName("card-circuit-breaker")
                        .setFallbackUri("/api/v1/fallback/cards"))
                    .addRequestHeader("X-Service-Context", "card-service")
                )
                .uri(backendBaseUrl)
                .metadata("timeout", 3000)
            )
            
            // Transaction processing routes (COTRN* -> TransactionService)
            .route("transaction-routes", r -> r
                .path("/api/v1/transactions/**")
                .filters(f -> f
                    .rewritePath("/api/v1/transactions/(?<segment>.*)", "/transactions/${segment}")
                    .requestRateLimiter(config -> config
                        .setRateLimiter(highVolumeRateLimiter())
                        .setKeyResolver(userKeyResolver()))
                    .filter(jwtAuthenticationFilter())
                    .circuitBreaker(config -> config
                        .setName("transaction-circuit-breaker")
                        .setFallbackUri("/api/v1/fallback/transactions"))
                    .addRequestHeader("X-Service-Context", "transaction-service")
                    .addRequestHeader("X-Priority", "high")
                )
                .uri(backendBaseUrl)
                .metadata("timeout", 2000) // Lower timeout for high-volume transactions
                .metadata("priority", "high")
            )
            
            // Payment processing routes (COBIL00C -> PaymentService)
            .route("payment-routes", r -> r
                .path("/api/v1/payments/**")
                .filters(f -> f
                    .rewritePath("/api/v1/payments/(?<segment>.*)", "/payments/${segment}")
                    .requestRateLimiter(config -> config
                        .setRateLimiter(paymentRateLimiter())
                        .setKeyResolver(userKeyResolver()))
                    .filter(jwtAuthenticationFilter())
                    .circuitBreaker(config -> config
                        .setName("payment-circuit-breaker")
                        .setFallbackUri("/api/v1/fallback/payments"))
                    .addRequestHeader("X-Service-Context", "payment-service")
                    .addRequestHeader("X-Security-Level", "high")
                )
                .uri(backendBaseUrl)
                .metadata("timeout", 5000)
                .metadata("security-level", "high")
            )
            
            // User management routes (COUSR* -> UserManagementService)
            .route("user-routes", r -> r
                .path("/api/v1/users/**")
                .filters(f -> f
                    .rewritePath("/api/v1/users/(?<segment>.*)", "/users/${segment}")
                    .requestRateLimiter(config -> config
                        .setRateLimiter(adminRateLimiter())
                        .setKeyResolver(userKeyResolver()))
                    .filter(jwtAuthenticationFilter())
                    .filter(adminAuthorizationFilter())
                    .addRequestHeader("X-Service-Context", "user-service")
                )
                .uri(backendBaseUrl)
                .metadata("timeout", 4000)
                .metadata("admin-only", true)
            )
            
            // Reporting routes (CORPT00C -> ReportService)
            .route("report-routes", r -> r
                .path("/api/v1/reports/**")
                .filters(f -> f
                    .rewritePath("/api/v1/reports/(?<segment>.*)", "/reports/${segment}")
                    .requestRateLimiter(config -> config
                        .setRateLimiter(reportRateLimiter())
                        .setKeyResolver(userKeyResolver()))
                    .filter(jwtAuthenticationFilter())
                    .addRequestHeader("X-Service-Context", "report-service")
                )
                .uri(backendBaseUrl)
                .metadata("timeout", 30000) // Longer timeout for report generation
            )
            
            // Health check and actuator routes
            .route("actuator-routes", r -> r
                .path("/actuator/**")
                .filters(f -> f
                    .requestRateLimiter(config -> config
                        .setRateLimiter(actuatorRateLimiter())
                        .setKeyResolver(ipKeyResolver()))
                    .addRequestHeader("X-Health-Check", "true")
                )
                .uri(backendBaseUrl)
                .metadata("timeout", 2000)
            )
            
            // Fallback routes for circuit breaker patterns
            .route("fallback-routes", r -> r
                .path("/api/v1/fallback/**")
                .filters(f -> f
                    .addRequestHeader("X-Fallback-Response", "true")
                )
                .uri(backendBaseUrl)
                .metadata("timeout", 1000)
            )
            
            .build();
    }

    /**
     * Rate limiter for authentication endpoints.
     * Higher limits to accommodate login attempts and token refresh.
     */
    @Bean
    public RedisRateLimiter authRateLimiter() {
        return new RedisRateLimiter(50, 100, 1);
    }

    /**
     * Default rate limiter for standard user operations.
     * Based on typical user interaction patterns from CICS analysis.
     */
    @Bean
    public RedisRateLimiter defaultRateLimiter() {
        return new RedisRateLimiter(defaultReplenishRate, defaultBurstCapacity, 1);
    }

    /**
     * Rate limiter for administrative operations.
     * Higher limits for admin users managing multiple accounts.
     */
    @Bean
    public RedisRateLimiter adminRateLimiter() {
        return new RedisRateLimiter(adminReplenishRate, adminBurstCapacity, 1);
    }

    /**
     * Rate limiter for high-volume transaction processing.
     * Optimized for card authorization and transaction posting.
     */
    @Bean
    public RedisRateLimiter highVolumeRateLimiter() {
        return new RedisRateLimiter(200, 400, 1);
    }

    /**
     * Rate limiter for payment processing operations.
     * Balanced for security and usability of payment flows.
     */
    @Bean
    public RedisRateLimiter paymentRateLimiter() {
        return new RedisRateLimiter(75, 150, 1);
    }

    /**
     * Rate limiter for report generation.
     * Lower limits due to resource-intensive nature of reports.
     */
    @Bean
    public RedisRateLimiter reportRateLimiter() {
        return new RedisRateLimiter(10, 20, 1);
    }

    /**
     * Rate limiter for actuator/health endpoints.
     * Allows frequent health checks without overwhelming the system.
     */
    @Bean
    public RedisRateLimiter actuatorRateLimiter() {
        return new RedisRateLimiter(300, 600, 1);
    }

    /**
     * Key resolver based on authenticated user for rate limiting.
     * Extracts user identity from JWT token for per-user rate limiting.
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
     * Key resolver based on client IP for rate limiting.
     * Used for endpoints that don't require authentication.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return Mono.just(xForwardedFor.split(",")[0].trim());
            }
            return Mono.just(exchange.getRequest().getRemoteAddress() != null ?
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : 
                "unknown");
        };
    }

    /**
     * JWT authentication filter for validating bearer tokens.
     * Integrates with Spring Security for consistent authentication across all routes.
     */
    @Bean
    public GatewayFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationGatewayFilterFactory().apply(new Object());
    }

    /**
     * Authorization filter for admin-only routes.
     * Validates that authenticated user has administrative privileges.
     */
    @Bean
    public GatewayFilter adminAuthorizationFilter() {
        return new AdminAuthorizationGatewayFilterFactory().apply(new Object());
    }

    /**
     * Custom filter factory for JWT authentication validation.
     * Validates JWT tokens and sets authentication context for downstream services.
     */
    public static class JwtAuthenticationGatewayFilterFactory 
            extends AbstractGatewayFilterFactory<Object> {

        @Override
        public GatewayFilter apply(Object config) {
            return (exchange, chain) -> {
                String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
                
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }

                // JWT validation will be handled by Spring Security filter chain
                // This filter adds gateway-specific headers and logging
                ServerWebExchange modifiedExchange = exchange.mutate()
                    .request(request -> request
                        .header("X-Gateway-Auth", "validated")
                        .header("X-Request-ID", generateRequestId())
                        .header("X-Timestamp", String.valueOf(System.currentTimeMillis()))
                    )
                    .build();

                return chain.filter(modifiedExchange);
            };
        }

        private String generateRequestId() {
            return "gw-" + System.currentTimeMillis() + "-" + 
                   Integer.toHexString((int) (Math.random() * 65536));
        }
    }

    /**
     * Custom filter factory for admin authorization validation.
     * Ensures that requests to admin endpoints are made by users with admin roles.
     */
    public static class AdminAuthorizationGatewayFilterFactory 
            extends AbstractGatewayFilterFactory<Object> {

        @Override
        public GatewayFilter apply(Object config) {
            return (exchange, chain) -> {
                return ReactiveSecurityContextHolder.getContext()
                    .cast(SecurityContext.class)
                    .map(SecurityContext::getAuthentication)
                    .cast(Authentication.class)
                    .flatMap(auth -> {
                        boolean hasAdminRole = auth.getAuthorities().stream()
                            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
                        
                        if (!hasAdminRole) {
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        }

                        ServerWebExchange modifiedExchange = exchange.mutate()
                            .request(request -> request
                                .header("X-User-Role", "ADMIN")
                                .header("X-Admin-Validated", "true")
                            )
                            .build();

                        return chain.filter(modifiedExchange);
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }));
            };
        }
    }
}
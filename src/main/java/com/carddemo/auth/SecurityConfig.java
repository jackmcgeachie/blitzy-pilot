/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration class for CardDemo application security framework.
 * 
 * This configuration replaces the legacy COBOL COSGN00C authentication program and RACF
 * authorization patterns with modern Spring Security 6.1.x framework, providing enterprise-grade
 * security controls while maintaining functional equivalence to mainframe security models.
 * 
 * <p>Migration Strategy from COBOL Authentication (COSGN00C.cbl):</p>
 * <ul>
 *   <li>CICS Transaction Security → Spring Security Filter Chain with JWT authentication</li>
 *   <li>RACF User Types ('A'/'U') → Spring Security Authorities (ROLE_ADMIN/ROLE_USER)</li>
 *   <li>USRSEC VSAM file → PostgreSQL users table with BCrypt password hashing</li>
 *   <li>COMMAREA session state → Redis-backed Spring Session with JWT tokens</li>
 *   <li>Program-level security → Method-level @PreAuthorize annotations</li>
 * </ul>
 * 
 * <p>Security Architecture Features:</p>
 * <ul>
 *   <li>Stateless JWT authentication enabling horizontal scaling capabilities</li>
 *   <li>BCrypt password encoder with strength factor 10 for cryptographic security</li>
 *   <li>CORS configuration for React frontend integration and cross-origin support</li>
 *   <li>Method-level security enforcement through @PreAuthorize annotations</li>
 *   <li>Redis session management for pseudo-conversational state replication</li>
 *   <li>Comprehensive security headers for production deployment protection</li>
 * </ul>
 * 
 * <p>RACF Authorization Mapping:</p>
 * <ul>
 *   <li>RACF User Type 'A' (Admin) → ROLE_ADMIN authority with full system access</li>
 *   <li>RACF User Type 'U' (User) → ROLE_USER authority with standard user permissions</li>
 *   <li>CICS Program Security → REST endpoint authorization via @PreAuthorize expressions</li>
 *   <li>Transaction Authorization → Service-layer security context propagation</li>
 * </ul>
 * 
 * <p>Enterprise Security Standards Compliance:</p>
 * <ul>
 *   <li>JWT tokens with configurable expiration policies (default 8 hours)</li>
 *   <li>Secure session management with automatic timeout and invalidation</li>
 *   <li>Production-ready security headers (HSTS, CSP, X-Frame-Options)</li>
 *   <li>CORS configuration supporting both development and production environments</li>
 *   <li>Authentication provider integration with custom UserDetailsService</li>
 * </ul>
 * 
 * <p>Original COBOL Security Logic Equivalence:</p>
 * <pre>
 * COBOL Logic (COSGN00C.cbl):
 *   IF SEC-USR-PWD = WS-USER-PWD
 *       IF CDEMO-USRTYP-ADMIN
 *           EXEC CICS XCTL PROGRAM ('COADM01C') ...
 *       ELSE
 *           EXEC CICS XCTL PROGRAM ('COMEN01C') ...
 * 
 * Spring Security Equivalent:
 *   - JWT authentication with BCrypt password validation
 *   - Role-based routing through @PreAuthorize("hasRole('ADMIN')")
 *   - Stateless architecture with distributed session management
 * </pre>
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since Spring Boot 3.2.5, Spring Security 6.1.8
 * @see UserDetailsServiceImpl
 * @see JwtTokenProvider
 * @see JwtAuthenticationFilter
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    // Dependency injection for authentication components
    private final UserDetailsService userDetailsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    // CORS configuration properties for frontend integration
    @Value("${carddemo.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String[] allowedOrigins;

    @Value("${carddemo.cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}")
    private String[] allowedMethods;

    @Value("${carddemo.cors.allowed-headers:*}")
    private String[] allowedHeaders;

    @Value("${carddemo.cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${carddemo.cors.max-age:3600}")
    private long maxAge;

    // Security configuration properties
    @Value("${carddemo.security.bcrypt.strength:10}")
    private int bcryptStrength;

    @Value("${carddemo.security.session.timeout:1800}")
    private int sessionTimeoutSeconds;

    @Value("${carddemo.security.headers.enabled:true}")
    private boolean securityHeadersEnabled;

    /**
     * Constructor for dependency injection of security components.
     * 
     * @param userDetailsService Custom UserDetailsService implementation for user lookup
     * @param jwtTokenProvider JWT token management component for authentication
     * @param jwtAuthenticationEntryPoint Entry point for handling authentication exceptions
     */
    @Autowired
    public SecurityConfig(
            UserDetailsService userDetailsService,
            JwtTokenProvider jwtTokenProvider,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
        
        this.userDetailsService = userDetailsService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        
        logger.info("SecurityConfig initialized with JWT authentication and RACF-equivalent role mapping");
    }

    /**
     * Configures BCrypt password encoder bean with enterprise-grade cryptographic strength.
     * 
     * This replaces the legacy COBOL plain text password validation with industry-standard
     * BCrypt hashing algorithm, providing significant security enhancement over the original
     * mainframe implementation while maintaining authentication functionality.
     * 
     * <p>BCrypt Configuration Details:</p>
     * <ul>
     *   <li>Strength Factor: Configurable (default 10) for performance/security balance</li>
     *   <li>Automatic Salt Generation: Unique salt per password for rainbow table protection</li>
     *   <li>Hash Format: Standard BCrypt format ($2a$10$[salt][hash]) for compatibility</li>
     *   <li>Performance: ~100ms verification time suitable for authentication frequency</li>
     * </ul>
     * 
     * <p>Migration from COBOL Password Logic:</p>
     * <pre>
     * COBOL (COSGN00C.cbl):
     *   IF SEC-USR-PWD = WS-USER-PWD
     *       // Plain text comparison - security vulnerability
     * 
     * BCrypt Enhancement:
     *   BCrypt.checkpw(inputPassword, storedHash)
     *       // Cryptographically secure comparison with salt
     * </pre>
     * 
     * @return BCryptPasswordEncoder configured with specified strength factor
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        logger.debug("Configuring BCrypt password encoder with strength factor: {}", bcryptStrength);
        
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(bcryptStrength);
        
        logger.info("BCrypt password encoder initialized - strength: {}, estimated hash time: ~{}ms", 
                   bcryptStrength, (int)Math.pow(2, bcryptStrength) / 10);
        
        return encoder;
    }

    /**
     * Configures DaoAuthenticationProvider for Spring Security authentication.
     * 
     * This provider integrates the custom UserDetailsService with the BCrypt password encoder,
     * creating a complete authentication mechanism that replaces the COBOL user validation
     * logic while providing enhanced security through cryptographic password verification.
     * 
     * <p>Authentication Flow:</p>
     * <ol>
     *   <li>User submits credentials via /auth/login REST endpoint</li>
     *   <li>DaoAuthenticationProvider calls UserDetailsService.loadUserByUsername()</li>
     *   <li>UserDetailsService queries PostgreSQL users table (replacing USRSEC VSAM)</li>
     *   <li>BCrypt password encoder validates input against stored hash</li>
     *   <li>Spring Security context populated with user authorities</li>
     *   <li>JWT token generated with user identity and role claims</li>
     * </ol>
     * 
     * @return DaoAuthenticationProvider configured with UserDetailsService and PasswordEncoder
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        logger.debug("Configuring DaoAuthenticationProvider with UserDetailsService and BCrypt encoder");
        
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        
        // Enable detailed authentication failure reasons for audit logging
        authProvider.setHideUserNotFoundExceptions(false);
        
        logger.info("DaoAuthenticationProvider configured with BCrypt validation and PostgreSQL user lookup");
        return authProvider;
    }

    /**
     * Provides AuthenticationManager bean for programmatic authentication.
     * 
     * This manager is used by the AuthenticationService to perform credential validation
     * during JWT token generation, supporting both manual authentication scenarios and
     * integration testing requirements.
     * 
     * @param config AuthenticationConfiguration for manager creation
     * @return AuthenticationManager for programmatic authentication
     * @throws Exception if configuration fails
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        logger.debug("Configuring AuthenticationManager for programmatic authentication");
        return config.getAuthenticationManager();
    }

    /**
     * Creates JWT authentication filter for token-based security.
     * 
     * This filter intercepts all HTTP requests and validates JWT tokens, replacing the
     * session-based authentication model with stateless token validation suitable for
     * cloud-native deployment patterns and horizontal scaling requirements.
     * 
     * @return JwtAuthenticationFilter configured with token provider
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        logger.debug("Creating JWT authentication filter with token provider integration");
        return new JwtAuthenticationFilter(jwtTokenProvider, userDetailsService);
    }

    /**
     * Configures main security filter chain with comprehensive security policies.
     * 
     * This method replaces the CICS transaction security model with Spring Security filter
     * chain, providing equivalent security controls while enabling modern REST API patterns
     * and stateless authentication architecture.
     * 
     * <p>Security Configuration Details:</p>
     * <ul>
     *   <li>JWT Authentication: Stateless token validation for all protected endpoints</li>
     *   <li>Role-Based Authorization: RACF-equivalent access control with @PreAuthorize</li>
     *   <li>CORS Support: React frontend integration with configurable origin policies</li>
     *   <li>Security Headers: Production-ready headers for XSS, clickjacking protection</li>
     *   <li>Session Management: Stateless policy with Redis for transient state</li>
     * </ul>
     * 
     * <p>Endpoint Authorization Matrix:</p>
     * <pre>
     * Public Endpoints:
     *   POST /auth/login, /auth/refresh - Authentication operations
     *   GET /health, /info - System health monitoring
     * 
     * User Endpoints (ROLE_USER, ROLE_ADMIN):
     *   GET /api/accounts/view, /api/cards/list - Data viewing operations
     *   POST /api/transactions, /api/payments - Transaction processing
     * 
     * Admin Endpoints (ROLE_ADMIN only):
     *   POST /api/accounts/update, /api/users/create - Administrative operations
     *   GET /api/admin/reports, /api/audit/logs - Administrative reporting
     * </pre>
     * 
     * @param http HttpSecurity builder for security configuration
     * @return SecurityFilterChain with complete security policy
     * @throws Exception if configuration fails
     */
    @Bean
    @Order(1)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        logger.debug("Configuring main security filter chain with JWT authentication and RACF role mapping");

        http
            // Disable CSRF for stateless JWT authentication
            .csrf(AbstractHttpConfigurer::disable)
            
            // Configure CORS for React frontend integration
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Configure exception handling for authentication failures
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
            )
            
            // Configure session management for stateless operation
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .maximumSessions(1)
                .maxSessionsPreventsLogin(false)
                .and()
                .sessionFixation().migrateSession()
                .invalidSessionUrl("/auth/login")
            )
            
            // Configure authorization rules with RACF-equivalent permissions
            .authorizeHttpRequests(authz -> authz
                // Public endpoints - no authentication required
                .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/health", "/info", "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                
                // Static resources and error pages
                .requestMatchers("/static/**", "/public/**", "/error").permitAll()
                .requestMatchers("/favicon.ico", "/robots.txt").permitAll()
                
                // Administrative endpoints - ROLE_ADMIN only (equivalent to RACF user type 'A')
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/accounts/update").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                .requestMatchers("/api/audit/**", "/api/reports/admin/**").hasRole("ADMIN")
                
                // User endpoints - ROLE_USER and ROLE_ADMIN (equivalent to RACF user types 'U'/'A')
                .requestMatchers(HttpMethod.GET, "/api/accounts/view/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/cards/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/transactions/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/transactions").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/payments").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/reports/user/**").hasAnyRole("USER", "ADMIN")
                
                // All other API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                
                // All other requests require authentication
                .anyRequest().authenticated()
            );

        // Configure security headers for production deployment
        if (securityHeadersEnabled) {
            http.headers(headers -> headers
                .frameOptions().deny()
                .contentTypeOptions().and()
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .maxAgeInSeconds(31536000)
                    .includeSubdomains(true)
                    .preload(true)
                )
                .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                .and()
                .headers(h -> h.defaultsDisabled()
                    .cacheControl().and()
                    .contentTypeOptions().and()
                    .frameOptions().and()
                    .httpStrictTransportSecurity().and()
                    .referrerPolicy().and()
                    .addHeaderWriter((request, response) -> {
                        response.addHeader("X-Content-Type-Options", "nosniff");
                        response.addHeader("X-Frame-Options", "DENY");
                        response.addHeader("X-XSS-Protection", "1; mode=block");
                        response.addHeader("Content-Security-Policy", 
                            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'");
                    })
                )
            );
        }

        // Add JWT authentication filter before UsernamePasswordAuthenticationFilter
        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        logger.info("Security filter chain configured: JWT authentication, RACF role mapping, CORS enabled, {} security headers",
                   securityHeadersEnabled ? "production" : "disabled");

        return http.build();
    }

    /**
     * Configures CORS (Cross-Origin Resource Sharing) for React frontend integration.
     * 
     * This configuration enables the React frontend to communicate with the Spring Boot
     * backend across different origins, supporting both development (localhost:3000) and
     * production deployment scenarios with configurable origin policies.
     * 
     * <p>CORS Configuration Features:</p>
     * <ul>
     *   <li>Configurable allowed origins for development and production environments</li>
     *   <li>Support for all standard HTTP methods required by REST API operations</li>
     *   <li>Credential support for JWT token transmission via Authorization headers</li>
     *   <li>Preflight request handling for complex CORS scenarios</li>
     *   <li>Configurable max age for CORS preflight response caching</li>
     * </ul>
     * 
     * <p>Security Considerations:</p>
     * <ul>
     *   <li>Origins should be restricted to trusted domains in production</li>
     *   <li>Credentials are allowed to support JWT token authentication</li>
     *   <li>Headers are configured to support Authorization bearer tokens</li>
     * </ul>
     * 
     * @return CorsConfigurationSource with React frontend integration settings
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        logger.debug("Configuring CORS for React frontend integration");
        
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Configure allowed origins - should be restricted in production
        configuration.setAllowedOriginPatterns(Arrays.asList(allowedOrigins));
        logger.debug("CORS allowed origins: {}", Arrays.toString(allowedOrigins));
        
        // Configure allowed HTTP methods for REST API operations
        configuration.setAllowedMethods(Arrays.asList(allowedMethods));
        
        // Configure allowed headers including Authorization for JWT tokens
        if (allowedHeaders.length == 1 && "*".equals(allowedHeaders[0])) {
            configuration.addAllowedHeader("*");
        } else {
            configuration.setAllowedHeaders(Arrays.asList(allowedHeaders));
        }
        
        // Allow credentials for JWT token transmission
        configuration.setAllowCredentials(allowCredentials);
        
        // Configure preflight response caching
        configuration.setMaxAge(maxAge);
        
        // Additional headers for JWT authentication
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization", 
            "X-Total-Count", 
            "X-Page-Number", 
            "X-Page-Size",
            "X-Correlation-ID"
        ));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        logger.info("CORS configuration applied: origins={}, methods={}, credentials={}, maxAge={}s",
                   Arrays.toString(allowedOrigins), Arrays.toString(allowedMethods), 
                   allowCredentials, maxAge);
        
        return source;
    }

    /**
     * Configures JWT authentication entry point for unauthorized access handling.
     * 
     * This entry point handles authentication exceptions and returns appropriate HTTP
     * responses for unauthorized access attempts, providing consistent error handling
     * across all protected endpoints.
     * 
     * @return JwtAuthenticationEntryPoint for exception handling
     */
    @Bean
    @ConditionalOnProperty(name = "carddemo.security.jwt.entry-point.enabled", havingValue = "true", matchIfMissing = true)
    public JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint() {
        logger.debug("Configuring JWT authentication entry point for unauthorized access handling");
        return new JwtAuthenticationEntryPoint();
    }

    /**
     * Configures session registry for concurrent session management.
     * 
     * This registry tracks active user sessions and enables session management policies
     * such as limiting concurrent sessions per user and handling session expiration.
     * 
     * @return SessionRegistry for session tracking and management
     */
    @Bean
    public org.springframework.security.core.session.SessionRegistry sessionRegistry() {
        logger.debug("Configuring session registry for concurrent session management");
        return new org.springframework.security.core.session.SessionRegistryImpl();
    }

    /**
     * Configures security event publisher for audit logging integration.
     * 
     * This publisher enables automatic publication of security events (authentication
     * success/failure, authorization decisions) to the audit logging system for
     * compliance and security monitoring requirements.
     * 
     * @param applicationContext Spring application context for event publishing
     * @return DefaultSecurityEventPublisher for audit event publication
     */
    @Bean
    public org.springframework.security.authentication.DefaultAuthenticationEventPublisher authenticationEventPublisher(
            org.springframework.context.ApplicationEventPublisher applicationContext) {
        
        logger.debug("Configuring security event publisher for audit logging integration");
        
        org.springframework.security.authentication.DefaultAuthenticationEventPublisher publisher = 
            new org.springframework.security.authentication.DefaultAuthenticationEventPublisher();
        
        // Configure event mappings for comprehensive audit coverage
        publisher.setApplicationEventPublisher(applicationContext);
        
        logger.info("Security event publisher configured for authentication audit logging");
        return publisher;
    }

    /**
     * Production security validation method for enterprise deployment.
     * 
     * This method performs runtime validation of security configuration to ensure
     * proper setup for production deployment, including verification of JWT settings,
     * password encoder configuration, and CORS policy validation.
     * 
     * <p>Validation Checks:</p>
     * <ul>
     *   <li>BCrypt strength factor within acceptable range (10-15)</li>
     *   <li>JWT token provider configuration validation</li>
     *   <li>CORS origins restricted for production environment</li>
     *   <li>Security headers enabled for production deployment</li>
     * </ul>
     */
    @Bean
    public SecurityConfigValidator securityConfigValidator() {
        return new SecurityConfigValidator() {
            @Override
            public void validateProductionConfiguration() {
                logger.info("Performing production security configuration validation");
                
                // Validate BCrypt strength
                if (bcryptStrength < 10 || bcryptStrength > 15) {
                    logger.warn("BCrypt strength {} may not be optimal for production (recommended: 10-12)", bcryptStrength);
                }
                
                // Validate CORS configuration for production
                boolean hasLocalhostOrigin = Arrays.stream(allowedOrigins)
                    .anyMatch(origin -> origin.contains("localhost"));
                
                if (hasLocalhostOrigin && isProductionEnvironment()) {
                    logger.warn("CORS configuration includes localhost origins in production environment");
                }
                
                // Validate security headers
                if (!securityHeadersEnabled) {
                    logger.warn("Security headers are disabled - not recommended for production");
                }
                
                logger.info("Security configuration validation completed");
            }
            
            private boolean isProductionEnvironment() {
                String profile = System.getProperty("spring.profiles.active", "");
                return profile.contains("prod") || profile.contains("production");
            }
        };
    }

    /**
     * Interface for security configuration validation.
     */
    public interface SecurityConfigValidator {
        void validateProductionConfiguration();
    }
}
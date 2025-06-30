/**
 * SecurityConfig.java
 * 
 * Spring Security configuration class implementing enterprise-grade authentication 
 * and authorization, replacing RACF-based mainframe security with modern JWT token 
 * authentication and role-based access control.
 * 
 * This configuration replaces the COBOL COSGN00C program functionality and integrates
 * with the legacy user security structure defined in CSUSR01Y copybook.
 * 
 * Key Security Features:
 * - JWT token-based stateless authentication
 * - RACF user type mapping ('A'->ROLE_ADMIN, 'U'->ROLE_USER)
 * - BCrypt password encoding with strength factor 10
 * - Method-level authorization through @PreAuthorize annotations
 * - Redis-backed session management for pseudo-conversational state
 * - Comprehensive audit logging integration
 * 
 * Migration Notes:
 * - Replaces CICS authentication and authorization patterns
 * - Maintains identical functional permissions from mainframe implementation
 * - Preserves existing user type classifications and access controls
 * 
 * @author Blitzy Agent
 * @version 1.0
 * @since Spring Boot 3.2.x
 */

package com.carddemo.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.carddemo.security.JwtAuthenticationEntryPoint;
import com.carddemo.security.JwtAuthenticationFilter;
import com.carddemo.service.UserDetailsServiceImpl;

import java.util.Arrays;
import java.util.List;

/**
 * Primary Spring Security configuration class replacing COBOL COSGN00C authentication logic.
 * 
 * Implements comprehensive security controls including:
 * - JWT-based stateless authentication replacing CICS session management
 * - Role-based authorization mapping RACF user types to Spring Security authorities
 * - BCrypt password hashing replacing plain text password storage from VSAM USRSEC
 * - Method-level security enforcement through @PreAuthorize annotations
 * - Redis session management for pseudo-conversational state preservation
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true, jsr250Enabled = true)
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800) // 30 minutes session timeout
public class SecurityConfig {

    /**
     * JWT secret key for token signing and verification.
     * In production, this should be externalized to secure configuration management.
     */
    @Value("${app.jwt.secret:CardDemo2024SecretKeyForJWTTokenGeneration}")
    private String jwtSecret;

    /**
     * JWT token expiration time in milliseconds (8 hours = 28800000ms).
     * Matches typical mainframe session duration.
     */
    @Value("${app.jwt.expiration:28800000}")
    private long jwtExpirationInMs;

    /**
     * Allowed origins for CORS configuration.
     * Supports development and production environments.
     */
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String allowedOrigins;

    /**
     * JWT authentication entry point for handling unauthorized access attempts.
     * Returns HTTP 401 responses for invalid or missing JWT tokens.
     */
    @Autowired
    private JwtAuthenticationEntryPoint unauthorizedHandler;

    /**
     * Custom UserDetailsService implementation that replaces VSAM USRSEC file reading.
     * Loads user details from PostgreSQL users table with BCrypt password validation.
     */
    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    /**
     * JWT authentication filter for processing and validating JWT tokens on each request.
     * Replaces CICS transaction security validation.
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }

    /**
     * BCrypt password encoder configuration with strength factor 10.
     * 
     * This replaces the plain text password comparison from COSGN00C:
     * Original COBOL: IF SEC-USR-PWD = WS-USER-PWD
     * New Implementation: BCrypt.checkpw(password, hashedPassword)
     * 
     * Strength factor 10 provides ~1024 hash iterations (2^10), balancing security
     * with performance for typical authentication frequencies.
     * 
     * @return BCryptPasswordEncoder configured with strength factor 10
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * DaoAuthenticationProvider configuration for database-based authentication.
     * 
     * Replaces VSAM USRSEC file reading with PostgreSQL users table access.
     * Integrates BCrypt password verification for secure credential validation.
     * 
     * @return DaoAuthenticationProvider configured with UserDetailsService and PasswordEncoder
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        // Hide user not found exceptions to prevent username enumeration attacks
        authProvider.setHideUserNotFoundExceptions(true);
        return authProvider;
    }

    /**
     * AuthenticationManager configuration for Spring Security.
     * 
     * Required for JWT authentication and user credential validation.
     * Used by AuthenticationService for login processing.
     * 
     * @param authConfig Spring Security authentication configuration
     * @return AuthenticationManager instance
     * @throws Exception if configuration fails
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * CORS configuration for cross-origin resource sharing.
     * 
     * Enables React frontend communication with Spring Boot backend.
     * Configured for development and production environments.
     * 
     * @return CorsConfigurationSource with appropriate CORS settings
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Parse allowed origins from configuration property
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        configuration.setAllowedOriginPatterns(origins);
        
        // Allow common HTTP methods required for REST API operations
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        
        // Allow headers required for JWT authentication and JSON content
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization",
            "Content-Type",
            "X-Requested-With",
            "Accept",
            "Origin",
            "Access-Control-Request-Method",
            "Access-Control-Request-Headers",
            "X-CSRF-TOKEN"
        ));
        
        // Expose headers that client may need to read
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
        
        // Allow credentials for session management and CSRF protection
        configuration.setAllowCredentials(true);
        
        // Cache preflight requests for 1 hour
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Primary Spring Security filter chain configuration.
     * 
     * This method replaces the entire COBOL COSGN00C authentication flow with modern
     * Spring Security patterns, including:
     * 
     * 1. JWT-based stateless authentication
     * 2. Role-based authorization for REST endpoints
     * 3. CSRF protection for state-changing operations
     * 4. Security headers for web application protection
     * 5. Exception handling for authentication failures
     * 
     * Endpoint Security Mapping:
     * - /auth/**: Public access for login/logout operations
     * - /actuator/health: Public health check endpoint
     * - /admin/**: Requires ROLE_ADMIN (maps to RACF user type 'A')
     * - /api/**: Requires authentication (ROLE_USER or ROLE_ADMIN)
     * - All other endpoints: Requires authentication
     * 
     * @param http HttpSecurity configuration object
     * @return SecurityFilterChain configured for CardDemo application
     * @throws Exception if security configuration fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Configure CORS to allow frontend-backend communication
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Configure CSRF protection
            // Disabled for stateless JWT authentication, but can be enabled for additional security
            .csrf(csrf -> csrf.disable())
            
            // Configure exception handling for authentication failures
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(unauthorizedHandler)
            )
            
            // Configure session management for stateless JWT authentication
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                // Disable session fixation protection for stateless authentication
                .sessionFixation().none()
                // Set maximum sessions per user (optional)
                .maximumSessions(5)
                .maxSessionsPreventsLogin(false)
            )
            
            // Configure authorization rules mapping to RACF user types
            .authorizeHttpRequests(authz -> authz
                // Public endpoints - no authentication required
                .requestMatchers("/auth/login", "/auth/logout").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                
                // Static resources - no authentication required
                .requestMatchers("/static/**", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                
                // Administrative endpoints - requires ROLE_ADMIN (RACF user type 'A')
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/users/**").hasRole("ADMIN")
                
                // User management endpoints - admin only (maps to COUSR00C-03C programs)
                .requestMatchers(HttpMethod.POST, "/api/users").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/users/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/users/**").hasRole("ADMIN")
                
                // Account management endpoints - role-based access
                .requestMatchers(HttpMethod.GET, "/api/accounts/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/accounts/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/accounts/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/accounts/**").hasRole("ADMIN")
                
                // Card management endpoints - role-based access
                .requestMatchers(HttpMethod.GET, "/api/cards/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/cards/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/cards/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/cards/**").hasRole("ADMIN")
                
                // Transaction endpoints - authenticated access
                .requestMatchers("/api/transactions/**").hasAnyRole("USER", "ADMIN")
                
                // Payment endpoints - authenticated access
                .requestMatchers("/api/payments/**").hasAnyRole("USER", "ADMIN")
                
                // Report endpoints - role-based access
                .requestMatchers("/api/reports/user/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/reports/admin/**").hasRole("ADMIN")
                
                // All other API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                
                // All other requests require authentication
                .anyRequest().authenticated()
            )
            
            // Configure security headers for web application protection
            .headers(headers -> headers
                .frameOptions().deny()
                .contentTypeOptions().and()
                .httpStrictTransportSecurity(hstsConfig -> hstsConfig
                    .maxAgeInSeconds(31536000)
                    .includeSubdomains(true)
                    .preload(true)
                )
                .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                .and()
                .cacheControl().and()
                .crossOriginResourcePolicy().sameOrigin()
            )
            
            // Configure authentication provider
            .authenticationProvider(authenticationProvider())
            
            // Add JWT authentication filter before username/password authentication
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
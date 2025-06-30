# Contributing Guidelines

Thank you for your interest in contributing to the CardDemo Application - a modern cloud-native Java Spring Boot migration from mainframe COBOL/CICS technology stack. This project demonstrates enterprise-grade development practices using Java 21, Spring Boot 3.2.x, PostgreSQL, Redis, React 18.x, Docker containerization, and Kubernetes orchestration.

Please read through this document carefully before submitting any issues or pull requests to ensure alignment with our Java Spring Boot enterprise development standards and quality requirements.

## Table of Contents

- [Development Environment Setup](#development-environment-setup)
- [Technology Stack & Requirements](#technology-stack--requirements)
- [Java Development Standards](#java-development-standards)  
- [Build System & Dependency Management](#build-system--dependency-management)
- [Docker & Kubernetes Development](#docker--kubernetes-development)
- [Testing Requirements](#testing-requirements)
- [Code Review Process](#code-review-process)
- [Quality Gates & Static Analysis](#quality-gates--static-analysis)
- [Contributing Workflow](#contributing-workflow)
- [Security Guidelines](#security-guidelines)

## Development Environment Setup

### Prerequisites

**Required Software Versions:**
- **Java 21 LTS** (OpenJDK 21 or Eclipse Temurin 21)
- **Maven 3.9.x** or **Gradle 8.4+** for build management
- **Docker CLI 24.x+** for containerization
- **kubectl CLI v1.28+** for Kubernetes cluster management
- **Helm Charts v3.13+** for Kubernetes resource templating
- **Git 2.40+** for version control

**Recommended IDEs:**
- **IntelliJ IDEA Ultimate 2023.3+** with plugins:
  - Spring Boot plugin
  - Kubernetes plugin  
  - Docker integration
  - Database tools
  - SonarLint integration
- **Visual Studio Code 1.85+** with extensions:
  - Java Extension Pack
  - Spring Boot Extension Pack
  - Kubernetes extension
  - Docker extension
  - React/TypeScript support

### Local Development Stack Setup

1. **Clone the repository:**
   ```bash
   git clone <repository-url>
   cd carddemo-application
   ```

2. **Start local development stack:**
   ```bash
   docker-compose up -d
   ```
   This starts PostgreSQL, Redis, and supporting services.

3. **Verify environment setup:**
   ```bash
   java --version        # Should show Java 21
   mvn --version        # Should show Maven 3.9.x+
   docker --version     # Should show Docker 24.x+
   kubectl version      # Should show client v1.28+
   ```

## Technology Stack & Requirements

### Core Technologies

**Backend Framework:**
- **Spring Boot 3.2.5** - Primary application framework with enterprise features
- **Spring Security 6.1.x** - JWT authentication and role-based authorization
- **Spring Data JPA 3.1.x** - PostgreSQL database access layer
- **Spring Batch 5.0.x** - Containerized batch processing
- **Spring Session with Redis 3.1.x** - Distributed session management
- **Spring Cloud Gateway 4.0.x** - API gateway and routing

**Database & Storage:**
- **PostgreSQL 15+** - Primary relational database with composite indexes
- **Redis 7+** - Session store and caching layer
- **HikariCP** - High-performance database connection pooling

**Frontend Technologies:**
- **React 18.2.x** - UI framework with functional components
- **TypeScript 5.x** - Type-safe JavaScript development
- **Material-UI v5.14.x** - Consistent component library
- **Redux 4.2.x with Redux Toolkit** - State management
- **Formik 2.4.x + Yup 1.3.x** - Form handling and validation

**Container & Orchestration:**
- **Docker** - Multi-stage container builds with Alpine Linux base
- **Kubernetes 1.28+** - Container orchestration platform
- **Helm Charts** - Kubernetes resource management templates

### Performance Requirements

**Critical Performance Targets:**
- Transaction response times: **< 200ms** for all REST endpoints
- Batch processing: Complete within **4-hour** overnight windows  
- Concurrent users: Support minimum **50 concurrent sessions**
- Database queries: **< 500ms** for complex joins with composite indexes
- Container startup: **< 2 minutes** for Spring Boot applications

## Java Development Standards

### Code Style & Formatting

**Java Coding Standards:**
- Follow **Google Java Style Guide** with enterprise modifications
- Use **Spotless Maven/Gradle plugin** for automatic code formatting
- Maximum line length: **120 characters**
- Use **meaningful variable names** reflecting business domain
- Include **comprehensive JavaDoc** for all public methods

**Spring Boot Specific Guidelines:**
- Use **@RestController** for REST endpoints with clear HTTP method mapping
- Implement **@Transactional** boundaries for business operations
- Apply **@PreAuthorize** annotations for role-based access control
- Use **@Valid** annotations for request validation
- Implement **@ExceptionHandler** for comprehensive error handling

### Enterprise Java Patterns

**Service Layer Architecture:**
```java
@Service
@Transactional
@Validated
public class AccountService {
    
    @PreAuthorize("hasRole('USER')")
    public AccountDto getAccount(@Valid @NotNull String accountId) {
        // Implementation with proper error handling
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    public AccountDto updateAccount(@Valid AccountUpdateRequest request) {
        // Business logic with optimistic locking
    }
}
```

**Repository Pattern:**
```java
@Repository
public interface AccountRepository extends JpaRepository<Account, String> {
    
    @Query("SELECT a FROM Account a WHERE a.customerId = :customerId AND a.status = :status")
    Optional<List<Account>> findActiveAccountsByCustomer(
        @Param("customerId") String customerId,
        @Param("status") AccountStatus status
    );
}
```

**REST Controller Standards:**
```java
@RestController
@RequestMapping("/api/v1/accounts")
@Validated
@SecurityRequirement(name = "bearerAuth")
public class AccountController {
    
    @GetMapping("/{accountId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<AccountDto> getAccount(
            @PathVariable @Pattern(regexp = "\\d{11}") String accountId) {
        // Implementation with proper error handling
        return ResponseEntity.ok(accountService.getAccount(accountId));
    }
}
```

### Data Precision & Validation

**BigDecimal Usage for Financial Data:**
```java
@Entity
@Table(name = "accounts")
public class Account {
    
    @Column(name = "balance", precision = 15, scale = 2, nullable = false)
    @Digits(integer = 13, fraction = 2)
    private BigDecimal balance;
    
    @Column(name = "credit_limit", precision = 15, scale = 2)
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal creditLimit;
}
```

**COBOL-to-Java Field Mappings:**
- `PIC 9(n)` → `Integer/Long` (based on size)
- `PIC 9(n)V99` → `BigDecimal` with scale 2
- `PIC S9(n)V99 COMP-3` → `BigDecimal` with explicit precision
- `PIC X(n)` → `String` with `@Size(max = n)` validation

## Build System & Dependency Management

### Maven Configuration

**Required Maven Structure:**
```xml
<groupId>com.carddemo</groupId>
<artifactId>carddemo-parent</artifactId>
<version>1.0.0-SNAPSHOT</version>
<packaging>pom</packaging>

<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.2.5</spring-boot.version>
    <spring-cloud.version>2023.0.1</spring-cloud.version>
    <testcontainers.version>1.19.8</testcontainers.version>
    <jacoco.version>0.8.10</jacoco.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>${spring-boot.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Essential Build Plugins:**
```xml
<build>
    <plugins>
        <!-- Spring Boot Maven Plugin -->
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
        
        <!-- Surefire for unit tests -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.1.2</version>
        </plugin>
        
        <!-- Failsafe for integration tests -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-failsafe-plugin</artifactId>
            <version>3.1.2</version>
        </plugin>
        
        <!-- JaCoCo for code coverage -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>${jacoco.version}</version>
        </plugin>
        
        <!-- Spotless for code formatting -->
        <plugin>
            <groupId>com.diffplug.spotless</groupId>
            <artifactId>spotless-maven-plugin</artifactId>
            <version>2.43.0</version>
        </plugin>
    </plugins>
</build>
```

### Dependency Categories

**Core Spring Boot Dependencies:**
```xml
<dependencies>
    <!-- Spring Boot Starters -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-batch</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.session</groupId>
        <artifactId>spring-session-data-redis</artifactId>
    </dependency>
</dependencies>
```

**Database Dependencies:**
```xml
<!-- PostgreSQL Driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Redis Client -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Database Migration -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

## Docker & Kubernetes Development

### Docker Development Workflow

**Multi-Stage Dockerfile Pattern:**
```dockerfile
# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Runtime stage  
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Local Development with Docker Compose:**
```yaml
version: '3.8'
services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: carddemo
      POSTGRES_USER: carddemo_user
      POSTGRES_PASSWORD: carddemo_pass
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

  app:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      - postgres
      - redis
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/carddemo
      SPRING_DATA_REDIS_HOST: redis
```

### Kubernetes Development Patterns

**Deployment Manifest Structure:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: carddemo-app
  namespace: carddemo-dev
spec:
  replicas: 2
  selector:
    matchLabels:
      app: carddemo-app
  template:
    metadata:
      labels:
        app: carddemo-app
    spec:
      containers:
      - name: carddemo-app
        image: carddemo/app:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 45
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
```

**Helm Chart Values Pattern:**
```yaml
# values-dev.yaml
replicaCount: 1
image:
  repository: carddemo/app
  tag: latest
  pullPolicy: Always

service:
  type: ClusterIP
  port: 8080

ingress:
  enabled: true
  className: nginx
  hosts:
    - host: carddemo-dev.local
      paths:
        - path: /
          pathType: Prefix

postgresql:
  enabled: true
  auth:
    database: carddemo
    username: carddemo_user

redis:
  enabled: true
  auth:
    enabled: false
```

### Container Security & Optimization

**Security Scanning Integration:**
```bash
# Build with security scanning
docker build -t carddemo/app:latest .
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
    aquasec/trivy:latest image carddemo/app:latest

# Container vulnerability assessment
docker scout cves carddemo/app:latest
```

**Resource Optimization:**
- Use **Alpine Linux** base images for minimal attack surface
- Implement **multi-stage builds** to reduce image size
- Apply **least privilege** principles with non-root containers
- Enable **image layer caching** for faster builds

## Testing Requirements

### Unit Testing Standards

**JUnit 5 Test Structure:**
```java
@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountServiceTest {
    
    @Mock
    private AccountRepository accountRepository;
    
    @InjectMocks
    private AccountService accountService;
    
    @Test
    @DisplayName("Should retrieve account successfully for valid account ID")
    void shouldRetrieveAccountSuccessfully() {
        // Given
        String accountId = "12345678901";
        Account mockAccount = createTestAccount(accountId);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(mockAccount));
        
        // When
        AccountDto result = accountService.getAccount(accountId);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAccountId()).isEqualTo(accountId);
        verify(accountRepository).findById(accountId);
    }
    
    @Test
    @DisplayName("Should throw RecordNotFoundException for non-existent account")
    void shouldThrowExceptionForNonExistentAccount() {
        // Given
        String accountId = "99999999999";
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> accountService.getAccount(accountId))
            .isInstanceOf(RecordNotFoundException.class)
            .hasMessage("Account not found: " + accountId);
    }
}
```

**Spring Boot Integration Testing:**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestContainers
@Transactional
class AccountControllerIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Test
    @WithMockUser(roles = "USER")
    void shouldGetAccountWithValidAuthentication() {
        // Given
        Account testAccount = createAndSaveTestAccount();
        
        // When
        ResponseEntity<AccountDto> response = restTemplate.exchange(
            "/api/v1/accounts/" + testAccount.getAccountId(),
            HttpMethod.GET,
            createAuthenticatedRequest(),
            AccountDto.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getAccountId()).isEqualTo(testAccount.getAccountId());
    }
}
```

### Testing Coverage Requirements

**Mandatory Coverage Targets:**
- **Statement Coverage:** 85% minimum measured by JaCoCo
- **Branch Coverage:** 80% minimum for conditional logic
- **Function Coverage:** 95% minimum for service methods
- **Integration Coverage:** 90% minimum for REST endpoints

**Code Coverage Configuration:**
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>PACKAGE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.85</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Performance Testing Requirements

**Response Time Validation:**
```java
@Test
@Timeout(value = 200, unit = TimeUnit.MILLISECONDS)
void shouldCompleteCardAuthorizationUnder200ms() {
    // Transaction must complete within 200ms requirement
    CardAuthorizationRequest request = createAuthorizationRequest();
    CardAuthorizationResponse response = cardService.authorizeTransaction(request);
    assertThat(response.isApproved()).isTrue();
}
```

**Load Testing Standards:**
- **REST Endpoints:** Must handle 10,000 TPS sustained load
- **Database Operations:** < 500ms for complex queries with joins
- **Batch Processing:** Complete within 4-hour window
- **Session Management:** Support 50+ concurrent users

## Code Review Process

### Pre-Review Checklist

**Before submitting a Pull Request:**
- [ ] All unit tests pass locally (`mvn test`)
- [ ] Integration tests pass with TestContainers (`mvn verify`)
- [ ] Code coverage meets 85% threshold
- [ ] SonarLint reports no critical/major issues
- [ ] Code follows Google Java Style Guide
- [ ] JavaDoc updated for public methods
- [ ] Database migrations include rollback scripts
- [ ] Docker build succeeds without vulnerabilities
- [ ] Kubernetes manifests validate successfully

### Pull Request Standards

**PR Title Format:**
```
[FEATURE|BUGFIX|HOTFIX] Brief description of changes

Examples:
[FEATURE] Add JWT authentication for REST endpoints
[BUGFIX] Fix BigDecimal precision in payment calculations  
[HOTFIX] Resolve memory leak in Redis session management
```

**Required PR Content:**
1. **Clear description** of changes and business impact
2. **Testing evidence** including coverage reports
3. **Performance impact** analysis for critical paths
4. **Security considerations** for authentication/authorization changes
5. **Database schema changes** with migration strategy
6. **Deployment notes** for Kubernetes configuration updates

### Review Criteria

**Mandatory Review Points:**
- **Business Logic Correctness:** Verify calculations and validations
- **Security Implementation:** Check authentication and authorization
- **Performance Impact:** Validate response time requirements
- **Error Handling:** Ensure comprehensive exception management
- **Data Integrity:** Verify database constraints and transactions
- **Test Coverage:** Confirm adequate test scenarios
- **Documentation:** Validate JavaDoc and inline comments

**Automated Review Gates:**
- SonarQube quality gate must pass
- All CI/CD pipeline checks successful
- Docker security scan results acceptable
- Performance benchmarks within thresholds

## Quality Gates & Static Analysis

### SonarLint Integration

**Required IDE Setup:**
1. Install SonarLint plugin in IntelliJ IDEA or VS Code
2. Configure connected mode with SonarQube server
3. Enable real-time code analysis
4. Set quality profile to "Java Enterprise Security"

**SonarLint Rules Configuration:**
```properties
# sonar-project.properties
sonar.projectKey=carddemo-application
sonar.organization=enterprise-java
sonar.host.url=https://sonarqube.company.com
sonar.login=${SONAR_TOKEN}

# Language settings
sonar.java.source=21
sonar.java.target=21
sonar.java.libraries=target/dependency/*.jar

# Coverage settings
sonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
sonar.junit.reportPaths=target/surefire-reports,target/failsafe-reports

# Quality gate thresholds
sonar.qualitygate.wait=true
```

### Code Quality Standards

**Critical Quality Metrics:**
- **Bugs:** 0 tolerance for critical/major bugs
- **Vulnerabilities:** 0 tolerance for security vulnerabilities
- **Code Smells:** < 5% technical debt ratio
- **Duplicated Lines:** < 3% code duplication
- **Maintainability Rating:** A rating required
- **Reliability Rating:** A rating required
- **Security Rating:** A rating required

**Custom Quality Rules:**
```java
// Mandatory annotations for financial calculations
@Service
public class PaymentCalculationService {
    
    // All monetary calculations must use BigDecimal
    public BigDecimal calculateInterest(
            @NotNull @DecimalMin("0.00") BigDecimal principal,
            @NotNull @DecimalMin("0.0001") BigDecimal rate) {
        // Implementation with proper rounding
        return principal.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
```

### Continuous Quality Monitoring

**CI/CD Quality Pipeline:**
```yaml
# .github/workflows/quality-check.yml
name: Quality Gate

on: [push, pull_request]

jobs:
  test-and-analyze:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v4
    
    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'temurin'
    
    - name: Run tests with coverage
      run: mvn clean verify
    
    - name: SonarQube analysis
      env:
        GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
      run: mvn sonar:sonar
    
    - name: Quality gate check
      run: |
        curl -s "$SONAR_HOST_URL/api/qualitygates/project_status?projectKey=$SONAR_PROJECT_KEY" \
        | jq -r '.projectStatus.status' | grep -q "OK"
```

## Contributing Workflow

### Git Workflow Standards

**Branch Naming Convention:**
```
feature/CARD-123-add-payment-validation
bugfix/CARD-456-fix-session-timeout
hotfix/CARD-789-resolve-memory-leak
release/v1.2.0-spring-boot-upgrade
```

**Commit Message Format:**
```
[CARD-123] Add JWT authentication for REST endpoints

- Implement JwtTokenProvider for token generation
- Add Spring Security configuration for role-based access
- Create integration tests for authentication flow
- Update OpenAPI documentation for security schemes

Fixes: Transaction authorization security requirements
Performance: < 200ms response time maintained
Testing: 92% code coverage achieved
```

### Development Workflow

**Step-by-Step Process:**

1. **Create Feature Branch:**
   ```bash
   git checkout main
   git pull origin main
   git checkout -b feature/CARD-123-payment-validation
   ```

2. **Implement Changes:**
   - Write failing unit tests first (TDD approach)
   - Implement business logic with proper error handling
   - Add integration tests with TestContainers
   - Update documentation and JavaDoc

3. **Local Quality Validation:**
   ```bash
   # Run all tests with coverage
   mvn clean verify
   
   # Check code formatting
   mvn spotless:check
   
   # Run SonarLint analysis
   mvn sonar:sonar -Dsonar.host.url=http://localhost:9000
   
   # Build Docker image
   docker build -t carddemo/app:feature-branch .
   
   # Test Kubernetes deployment
   helm upgrade --install carddemo-dev ./helm/carddemo -f values-dev.yaml
   ```

4. **Create Pull Request:**
   - Use PR template with all required sections
   - Include performance impact analysis
   - Attach test coverage reports
   - Document breaking changes

5. **Address Review Feedback:**
   - Respond to all reviewer comments
   - Update tests based on feedback
   - Ensure CI/CD pipeline passes
   - Re-run quality gates

6. **Merge to Main:**
   - Squash commits for clean history
   - Update CHANGELOG.md
   - Tag release if applicable

### Issue Reporting Standards

**Bug Report Template:**
```markdown
## Bug Description
Clear description of the issue

## Environment
- Java Version: 21
- Spring Boot Version: 3.2.5
- Database: PostgreSQL 15.3
- Kubernetes Version: 1.28.2

## Steps to Reproduce
1. 
2. 
3. 

## Expected Behavior


## Actual Behavior


## Test Case
Include unit/integration test demonstrating the issue

## Impact Assessment
- Performance Impact: 
- Security Impact:
- Business Impact:
```

**Feature Request Template:**
```markdown
## Feature Description
Business requirement and technical implementation

## Acceptance Criteria
- [ ] 
- [ ] 
- [ ] 

## Technical Considerations
- Spring Boot components involved
- Database schema changes required
- Performance requirements
- Security implications

## Testing Strategy
- Unit test coverage plan
- Integration test scenarios
- Performance validation approach
```

## Security Guidelines

### Authentication & Authorization

**Spring Security Implementation:**
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/**").hasAnyRole("USER", "ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
            .build();
    }
}
```

**JWT Token Standards:**
- Use **RSA-256** or **HS-256** algorithm for signing
- Set reasonable **expiration times** (15 minutes for access, 24 hours for refresh)
- Include **role claims** for authorization decisions
- Implement **token refresh** mechanism for session continuity

### Data Protection

**Sensitive Data Handling:**
```java
@Entity
@Table(name = "customers")
public class Customer {
    
    @Column(name = "ssn")
    @Convert(converter = SsnEncryptionConverter.class)
    private String socialSecurityNumber;
    
    @Column(name = "card_number")
    @Convert(converter = CardNumberEncryptionConverter.class)
    private String cardNumber;
    
    // Audit trail fields
    @CreatedBy
    private String createdBy;
    
    @LastModifiedBy
    private String lastModifiedBy;
}
```

**Database Security:**
- Use **prepared statements** to prevent SQL injection
- Implement **row-level security** for multi-tenant data
- Enable **audit logging** for all data modifications
- Apply **principle of least privilege** for database roles

### Container Security

**Dockerfile Security Best Practices:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine

# Create non-root user
RUN addgroup -g 1001 carddemo && \
    adduser -D -s /bin/sh -u 1001 -G carddemo carddemo

# Set secure permissions
COPY --chown=carddemo:carddemo target/*.jar app.jar

# Switch to non-root user
USER carddemo

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

## Reporting Bugs/Feature Requests

We welcome you to use the GitHub issue tracker to report bugs or suggest features. When filing an issue, please check existing open, or recently closed, issues to make sure somebody else hasn't already reported the issue.

**For bug reports, please include:**
- Java and Spring Boot versions
- Complete stack trace for exceptions
- Minimal reproduction case with unit test
- Performance impact assessment
- Security implications if applicable

**For feature requests, please provide:**
- Business justification and use case
- Proposed technical implementation approach  
- Testing strategy and acceptance criteria
- Performance and security considerations

## Code of Conduct

This project follows enterprise development standards with emphasis on:
- **Professional collaboration** and constructive feedback
- **Quality-first** approach to all contributions
- **Security-conscious** development practices
- **Performance-aware** implementation decisions
- **Documentation-driven** development process

## Security Issue Notifications

If you discover a potential security issue in this project, please:
1. **Do NOT** create a public GitHub issue
2. Email security concerns to: [security@company.com]
3. Include detailed reproduction steps and impact assessment
4. Allow reasonable time for investigation and remediation

## Licensing

See the [LICENSE](LICENSE) file for our project's licensing. We will ask you to confirm the licensing of your contribution.

---

**Additional Resources:**
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/)
- [Kubernetes Development Guide](https://kubernetes.io/docs/concepts/)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Java 21 Features Guide](https://openjdk.java.net/projects/jdk/21/)
- [PostgreSQL Performance Tuning](https://www.postgresql.org/docs/15/performance-tips.html)

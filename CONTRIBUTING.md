# Contributing Guidelines

Thank you for your interest in contributing to the CardDemo Credit Card Management System. This project represents a comprehensive migration from IBM mainframe COBOL/CICS/VSAM technology stack to a modern cloud-native architecture built on Java 21 Spring Boot microservices, PostgreSQL database, Docker containerization, Kubernetes orchestration, and React-based frontend.

This guide provides comprehensive development guidelines for contributing to this enterprise-grade financial application migration project.

## Table of Contents

1. [Project Overview](#project-overview)
2. [Development Environment Setup](#development-environment-setup)
3. [Code Style Guidelines](#code-style-guidelines)
4. [Testing Procedures](#testing-procedures)
5. [Containerization Development Workflow](#containerization-development-workflow)
6. [Database Migration Development](#database-migration-development)
7. [Security Testing Requirements](#security-testing-requirements)
8. [Pull Request Process](#pull-request-process)
9. [Issue Reporting](#issue-reporting)
10. [Code of Conduct](#code-of-conduct)

## Project Overview

### Architecture Overview

The CardDemo application is designed as a **modular monolith** using Spring Boot 3.2.x with Java 21, implementing:

- **22 Spring Boot microservices** replacing legacy COBOL programs
- **17 React TypeScript components** replacing BMS 3270 terminal screens
- **PostgreSQL 15+ database** replacing VSAM KSDS file structures
- **Spring Batch jobs** replacing JCL batch processing
- **Docker containers** orchestrated with Kubernetes
- **Spring Security JWT authentication** replacing RACF

### Key Migration Principles

- **Minimal Change Directive**: Preserve all core business logic exactly as implemented in COBOL
- **Numeric Precision**: Maintain exact numeric precision using BigDecimal for COBOL COMP-3 equivalence
- **Performance Requirements**: Sub-200ms transaction response times, 10,000 TPS capacity
- **Parallel Testing**: All changes validated through automated comparison with legacy COBOL outputs

## Development Environment Setup

### Prerequisites

#### Required Software

```bash
# Java Development Kit
Java 21 LTS (OpenJDK 21 or Eclipse Temurin 21)

# Build Tools
Maven 3.9.x or Gradle 8.4+

# Container Tools
Docker CLI 24.x+
Docker Compose 2.x+

# Kubernetes Tools
kubectl CLI v1.28+
Helm Charts v3.13+

# IDE (choose one)
IntelliJ IDEA Ultimate 2023.3+
Visual Studio Code 1.85+
```

#### Development Stack Components

```bash
# Database
PostgreSQL 15+ (containerized)
Redis 7+ (containerized)

# Monitoring
Prometheus (metrics collection)
Grafana (visualization)
```

### Local Development Setup

#### 1. Clone and Setup Repository

```bash
# Clone repository
git clone <repository-url>
cd carddemo

# Setup environment
cp .env.example .env
# Edit .env with your local configuration
```

#### 2. Start Development Stack

```bash
# Start all services using Docker Compose
docker-compose up -d

# Verify services are running
docker-compose ps

# Check application logs
docker-compose logs -f app
```

#### 3. Database Initialization

```bash
# Initialize database schema
mvn flyway:migrate -Dspring.profiles.active=dev

# Load test data
mvn flyway:migrate -Dspring.profiles.active=dev -Dflyway.locations=classpath:db/dev
```

#### 4. Build and Test

```bash
# Build application
mvn clean package -DskipTests

# Run tests
mvn test

# Run with specific profile
mvn spring-boot:run -Dspring.profiles.active=dev
```

### IDE Configuration

#### IntelliJ IDEA Setup

1. **Required Plugins**:
   - Spring Boot
   - Kubernetes
   - Docker
   - Database Tools

2. **Project Settings**:
   - Java SDK: 21
   - Maven/Gradle: Auto-import enabled
   - Code Style: Import `config/intellij-codestyle.xml`

3. **Run Configurations**:
   - Spring Boot Application: `com.carddemo.CardDemoApplication`
   - Active Profile: `dev`
   - VM Options: `-Xmx1024m -Xms512m`

#### Visual Studio Code Setup

1. **Required Extensions**:
   - Java Extension Pack
   - Spring Boot Extension Pack
   - Kubernetes
   - Docker

2. **Settings** (`.vscode/settings.json`):
   ```json
   {
     "java.configuration.runtimes": [
       {
         "name": "JavaSE-21",
         "path": "/path/to/jdk-21"
       }
     ],
     "spring-boot.ls.java.home": "/path/to/jdk-21"
   }
   ```

## Code Style Guidelines

### General Principles

- **Consistency**: Follow established patterns throughout the codebase
- **Readability**: Write self-documenting code with clear names and structure
- **COBOL Traceability**: Include comments referencing original COBOL paragraphs
- **Enterprise Quality**: Implement comprehensive error handling and logging

### Java Code Style

#### Spring Boot REST Controllers

```java
/**
 * Account management REST controller
 * Converted from COBOL programs: COACTVWC (view), COACTUPC (update)
 */
@RestController
@RequestMapping("/api/accounts")
@Validated
@Slf4j
public class AccountController {
    
    private final AccountService accountService;
    
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }
    
    /**
     * Retrieve account details
     * Original COBOL: COACTVWC paragraph 1000-GET-ACCOUNT-DETAILS
     */
    @GetMapping("/{accountId}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<AccountViewDto> getAccount(
            @PathVariable @Pattern(regexp = "\\d{11}") String accountId) {
        
        log.info("Retrieving account details for account: {}", accountId);
        
        try {
            AccountViewDto account = accountService.getAccountDetails(accountId);
            return ResponseEntity.ok(account);
        } catch (AccountNotFoundException e) {
            log.warn("Account not found: {}", accountId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error retrieving account: {}", accountId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Update account information
     * Original COBOL: COACTUPC paragraph 2000-UPDATE-ACCOUNT-DATA
     */
    @PutMapping("/{accountId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AccountUpdateDto> updateAccount(
            @PathVariable @Pattern(regexp = "\\d{11}") String accountId,
            @RequestBody @Valid AccountUpdateDto updateRequest) {
        
        log.info("Updating account: {}", accountId);
        
        try {
            AccountUpdateDto updated = accountService.updateAccount(accountId, updateRequest);
            return ResponseEntity.ok(updated);
        } catch (OptimisticLockingFailureException e) {
            log.warn("Concurrent update detected for account: {}", accountId);
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }
}
```

#### JPA Repository Pattern

```java
/**
 * Account data access repository
 * Replaces VSAM ACCTDAT file operations
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, String> {
    
    /**
     * Find accounts by customer ID
     * Original COBOL: ACCTDAT browse by customer ID
     */
    @Query("SELECT a FROM Account a WHERE a.customerId = :customerId ORDER BY a.accountId")
    List<Account> findByCustomerId(@Param("customerId") String customerId);
    
    /**
     * Find accounts by status and customer
     * Original COBOL: ACCTDAT AIX on customer-status
     */
    @Query("SELECT a FROM Account a WHERE a.customerId = :customerId AND a.accountStatus = :status")
    List<Account> findByCustomerIdAndStatus(
            @Param("customerId") String customerId,
            @Param("status") AccountStatus status);
    
    /**
     * Custom query for account balance aggregation
     * Original COBOL: ACCTDAT sequential read with balance calculation
     */
    @Query(value = """
        SELECT SUM(current_balance) 
        FROM accounts 
        WHERE customer_id = :customerId 
        AND account_status = 'ACTIVE'
        """, nativeQuery = true)
    BigDecimal getTotalBalanceByCustomer(@Param("customerId") String customerId);
}
```

#### Service Layer Pattern

```java
/**
 * Account business logic service
 * Implements COBOL business rules from COACTVWC/COACTUPC
 */
@Service
@Transactional
@Slf4j
public class AccountService {
    
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final AuditService auditService;
    
    public AccountService(AccountRepository accountRepository,
                         CustomerRepository customerRepository,
                         AuditService auditService) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.auditService = auditService;
    }
    
    /**
     * Retrieve account with business logic validation
     * Original COBOL: COACTVWC paragraph 1000-GET-ACCOUNT-DETAILS
     */
    @Transactional(readOnly = true)
    public AccountViewDto getAccountDetails(String accountId) {
        log.debug("Retrieving account details for: {}", accountId);
        
        // Validate account ID format (COBOL: PIC 9(11) validation)
        if (!accountId.matches("\\d{11}")) {
            throw new IllegalArgumentException("Invalid account ID format");
        }
        
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        
        // COBOL equivalent: Check account status
        if (account.getAccountStatus() == AccountStatus.CLOSED) {
            throw new AccountAccessDeniedException("Account is closed");
        }
        
        // Calculate available credit (COBOL: COMPUTE AVAIL-CREDIT)
        BigDecimal availableCredit = account.getCreditLimit()
                .subtract(account.getCurrentBalance());
        
        // Create response DTO
        AccountViewDto dto = new AccountViewDto();
        dto.setAccountId(account.getAccountId());
        dto.setCustomerId(account.getCustomerId());
        dto.setCurrentBalance(account.getCurrentBalance());
        dto.setCreditLimit(account.getCreditLimit());
        dto.setAvailableCredit(availableCredit);
        dto.setAccountOpenDate(account.getAccountOpenDate());
        dto.setAccountStatus(account.getAccountStatus());
        
        // Audit log (replaces COBOL EXEC CICS WRITEQ)
        auditService.logAccountAccess(accountId, "VIEW");
        
        return dto;
    }
    
    /**
     * Update account with optimistic locking
     * Original COBOL: COACTUPC paragraph 2000-UPDATE-ACCOUNT-DATA
     */
    public AccountUpdateDto updateAccount(String accountId, AccountUpdateDto updateRequest) {
        log.info("Updating account: {}", accountId);
        
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        
        // COBOL equivalent: Edit checks and validation
        validateAccountUpdate(updateRequest);
        
        // Update fields (COBOL: MOVE statements)
        if (updateRequest.getCreditLimit() != null) {
            account.setCreditLimit(updateRequest.getCreditLimit());
        }
        
        if (updateRequest.getAccountStatus() != null) {
            account.setAccountStatus(updateRequest.getAccountStatus());
        }
        
        // Save with optimistic locking (replaces CICS REWRITE)
        Account savedAccount = accountRepository.save(account);
        
        // Audit log
        auditService.logAccountUpdate(accountId, "UPDATE");
        
        return mapToUpdateDto(savedAccount);
    }
    
    /**
     * Validate account update request
     * Original COBOL: 3000-VALIDATE-INPUT-DATA paragraph
     */
    private void validateAccountUpdate(AccountUpdateDto updateRequest) {
        if (updateRequest.getCreditLimit() != null) {
            // COBOL: PIC S9(11)V99 COMP-3 validation
            if (updateRequest.getCreditLimit().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Credit limit cannot be negative");
            }
            if (updateRequest.getCreditLimit().compareTo(new BigDecimal("999999999.99")) > 0) {
                throw new IllegalArgumentException("Credit limit exceeds maximum");
            }
        }
    }
}
```

#### BigDecimal Precision Handling

```java
/**
 * Utility class for COBOL COMP-3 equivalent precision handling
 */
@Component
public class NumericPrecisionService {
    
    // COBOL: PIC S9(11)V99 COMP-3 equivalent
    private static final int MONETARY_PRECISION = 2;
    private static final int MONETARY_SCALE = 13;
    
    /**
     * Create BigDecimal with COBOL monetary precision
     * Original COBOL: MOVE amount-field TO COMP-3-FIELD
     */
    public BigDecimal createMonetaryAmount(String value) {
        return new BigDecimal(value).setScale(MONETARY_PRECISION, RoundingMode.HALF_UP);
    }
    
    /**
     * Add monetary amounts with COBOL precision
     * Original COBOL: COMPUTE TOTAL-AMOUNT = AMOUNT-1 + AMOUNT-2
     */
    public BigDecimal addMonetaryAmounts(BigDecimal amount1, BigDecimal amount2) {
        return amount1.add(amount2).setScale(MONETARY_PRECISION, RoundingMode.HALF_UP);
    }
    
    /**
     * Multiply with COBOL precision
     * Original COBOL: COMPUTE INTEREST = BALANCE * RATE
     */
    public BigDecimal multiplyWithPrecision(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).setScale(MONETARY_PRECISION, RoundingMode.HALF_UP);
    }
}
```

### React TypeScript Components

#### Component Structure

```typescript
/**
 * Account View Component
 * Replaces BMS Map: COACTVW.bms
 * Maintains exact field layout and validation rules
 */
import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Card, CardContent, Typography, Grid, Button, TextField } from '@mui/material';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { AccountViewDto, AccountService } from '../services/AccountService';
import { useAuth } from '../hooks/useAuth';
import { formatCurrency, formatDate } from '../utils/formatters';

// Validation schema matching COBOL field validation
const accountViewSchema = yup.object({
  accountId: yup.string()
    .matches(/^\d{11}$/, 'Account ID must be 11 digits')
    .required('Account ID is required'),
});

interface AccountViewProps {
  // Component props
}

const AccountView: React.FC<AccountViewProps> = () => {
  const { accountId } = useParams<{ accountId: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  
  const [account, setAccount] = useState<AccountViewDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  
  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(accountViewSchema),
    defaultValues: {
      accountId: accountId || '',
    },
  });
  
  // Load account data on component mount
  useEffect(() => {
    if (accountId) {
      loadAccountData(accountId);
    }
  }, [accountId]);
  
  /**
   * Load account data from backend
   * Original BMS: COACTVW screen data population
   */
  const loadAccountData = async (id: string) => {
    setLoading(true);
    setError(null);
    
    try {
      const accountData = await AccountService.getAccountDetails(id);
      setAccount(accountData);
    } catch (err) {
      setError('Error loading account data');
      console.error('Account load error:', err);
    } finally {
      setLoading(false);
    }
  };
  
  /**
   * Handle form submission
   * Original BMS: COACTVW ENTER key processing
   */
  const onSubmit = async (data: { accountId: string }) => {
    if (data.accountId !== accountId) {
      navigate(`/accounts/${data.accountId}`);
    }
  };
  
  /**
   * Handle PF key navigation
   * Original BMS: PF3=EXIT, PF5=REFRESH, PF12=CANCEL
   */
  const handleKeyDown = (event: React.KeyboardEvent) => {
    switch (event.key) {
      case 'F3':
        event.preventDefault();
        navigate('/menu');
        break;
      case 'F5':
        event.preventDefault();
        if (accountId) {
          loadAccountData(accountId);
        }
        break;
      case 'F12':
        event.preventDefault();
        navigate(-1);
        break;
    }
  };
  
  if (loading) {
    return <div>Loading account data...</div>;
  }
  
  if (error) {
    return (
      <div>
        <Typography color="error">{error}</Typography>
        <Button onClick={() => navigate('/menu')}>Return to Menu</Button>
      </div>
    );
  }
  
  return (
    <div onKeyDown={handleKeyDown} tabIndex={0}>
      <Card>
        <CardContent>
          <Typography variant="h5" component="h1" gutterBottom>
            Account Details
          </Typography>
          
          {/* Account ID Input - matches BMS field position */}
          <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
              <Controller
                name="accountId"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Account ID"
                    fullWidth
                    error={!!errors.accountId}
                    helperText={errors.accountId?.message}
                    inputProps={{
                      maxLength: 11,
                      pattern: '[0-9]*',
                    }}
                  />
                )}
              />
            </Grid>
          </Grid>
          
          {/* Account Data Display - matches BMS screen layout */}
          {account && (
            <Grid container spacing={2} sx={{ mt: 2 }}>
              <Grid item xs={12} md={6}>
                <Typography variant="body1">
                  <strong>Customer ID:</strong> {account.customerId}
                </Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="body1">
                  <strong>Account Status:</strong> {account.accountStatus}
                </Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="body1">
                  <strong>Current Balance:</strong> {formatCurrency(account.currentBalance)}
                </Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="body1">
                  <strong>Credit Limit:</strong> {formatCurrency(account.creditLimit)}
                </Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="body1">
                  <strong>Available Credit:</strong> {formatCurrency(account.availableCredit)}
                </Typography>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="body1">
                  <strong>Account Open Date:</strong> {formatDate(account.accountOpenDate)}
                </Typography>
              </Grid>
            </Grid>
          )}
          
          {/* Navigation Buttons - matches BMS PF key functions */}
          <Grid container spacing={2} sx={{ mt: 3 }}>
            <Grid item>
              <Button
                variant="outlined"
                onClick={() => navigate('/menu')}
                title="PF3 - Exit"
              >
                Exit (F3)
              </Button>
            </Grid>
            <Grid item>
              <Button
                variant="outlined"
                onClick={() => accountId && loadAccountData(accountId)}
                title="PF5 - Refresh"
              >
                Refresh (F5)
              </Button>
            </Grid>
            {user?.role === 'ADMIN' && (
              <Grid item>
                <Button
                  variant="contained"
                  onClick={() => navigate(`/accounts/${accountId}/edit`)}
                  title="Edit Account"
                >
                  Edit Account
                </Button>
              </Grid>
            )}
            <Grid item>
              <Button
                variant="outlined"
                onClick={() => navigate(-1)}
                title="PF12 - Cancel"
              >
                Cancel (F12)
              </Button>
            </Grid>
          </Grid>
        </CardContent>
      </Card>
    </div>
  );
};

export default AccountView;
```

#### Service Layer Integration

```typescript
/**
 * Account Service for REST API communication
 * Handles HTTP requests to Spring Boot backend
 */
import axios from 'axios';
import { ApiClient } from './ApiClient';

export interface AccountViewDto {
  accountId: string;
  customerId: string;
  currentBalance: number;
  creditLimit: number;
  availableCredit: number;
  accountOpenDate: string;
  accountStatus: string;
}

export interface AccountUpdateDto {
  creditLimit?: number;
  accountStatus?: string;
  version: number;
}

export class AccountService {
  private static readonly BASE_URL = '/api/accounts';
  
  /**
   * Get account details
   * Calls: GET /api/accounts/{accountId}
   */
  static async getAccountDetails(accountId: string): Promise<AccountViewDto> {
    try {
      const response = await ApiClient.get<AccountViewDto>(`${this.BASE_URL}/${accountId}`);
      return response.data;
    } catch (error) {
      console.error('Error fetching account details:', error);
      throw error;
    }
  }
  
  /**
   * Update account information
   * Calls: PUT /api/accounts/{accountId}
   */
  static async updateAccount(accountId: string, updateData: AccountUpdateDto): Promise<AccountUpdateDto> {
    try {
      const response = await ApiClient.put<AccountUpdateDto>(`${this.BASE_URL}/${accountId}`, updateData);
      return response.data;
    } catch (error) {
      console.error('Error updating account:', error);
      throw error;
    }
  }
  
  /**
   * List accounts by customer
   * Calls: GET /api/accounts?customerId={customerId}
   */
  static async getAccountsByCustomer(customerId: string): Promise<AccountViewDto[]> {
    try {
      const response = await ApiClient.get<AccountViewDto[]>(`${this.BASE_URL}?customerId=${customerId}`);
      return response.data;
    } catch (error) {
      console.error('Error fetching customer accounts:', error);
      throw error;
    }
  }
}
```

### Code Formatting and Linting

#### Java Formatting

Use **Spotless** Maven plugin for consistent formatting:

```xml
<plugin>
  <groupId>com.diffplug.spotless</groupId>
  <artifactId>spotless-maven-plugin</artifactId>
  <version>2.40.0</version>
  <configuration>
    <java>
      <googleJavaFormat>
        <version>1.15.0</version>
        <style>GOOGLE</style>
      </googleJavaFormat>
      <removeUnusedImports />
      <trimTrailingWhitespace />
      <endWithNewline />
    </java>
  </configuration>
</plugin>
```

#### TypeScript/React Formatting

Use **Prettier** with ESLint for React components:

```json
{
  "extends": [
    "@typescript-eslint/recommended",
    "plugin:react/recommended",
    "plugin:react-hooks/recommended"
  ],
  "rules": {
    "react/prop-types": "off",
    "@typescript-eslint/no-unused-vars": "error",
    "react-hooks/exhaustive-deps": "warn"
  }
}
```

## Testing Procedures

### Testing Strategy Overview

Our testing approach implements comprehensive validation across multiple layers:

- **Unit Testing**: JUnit 5 with Mockito for service layer testing
- **Integration Testing**: Spring Boot Test with TestContainers for database integration
- **Parallel Testing**: Automated comparison with legacy COBOL outputs
- **UI Testing**: Cypress for end-to-end React component testing

### Unit Testing

#### Spring Boot Service Tests

```java
/**
 * Unit tests for AccountService
 * Validates business logic against COBOL program equivalents
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    
    @Mock
    private AccountRepository accountRepository;
    
    @Mock
    private CustomerRepository customerRepository;
    
    @Mock
    private AuditService auditService;
    
    @InjectMocks
    private AccountService accountService;
    
    /**
     * Test account retrieval with valid ID
     * Original COBOL: COACTVWC paragraph 1000-GET-ACCOUNT-DETAILS
     */
    @Test
    @DisplayName("UT-AccountService-VALID-ACCOUNT-SUCCESS")
    void testGetAccountDetails_ValidAccount_ReturnsAccountDto() {
        // Given
        String accountId = "12345678901";
        Account mockAccount = createMockAccount(accountId);
        
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(mockAccount));
        
        // When
        AccountViewDto result = accountService.getAccountDetails(accountId);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAccountId()).isEqualTo(accountId);
        assertThat(result.getCurrentBalance()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(result.getAvailableCredit()).isEqualTo(new BigDecimal("4000.00"));
        
        verify(auditService).logAccountAccess(accountId, "VIEW");
    }
    
    /**
     * Test account not found scenario
     * Original COBOL: COACTVWC paragraph 9000-ACCOUNT-NOT-FOUND
     */
    @Test
    @DisplayName("UT-AccountService-INVALID-ACCOUNT-ERROR")
    void testGetAccountDetails_AccountNotFound_ThrowsException() {
        // Given
        String accountId = "99999999999";
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());
        
        // When / Then
        assertThatThrownBy(() -> accountService.getAccountDetails(accountId))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found: " + accountId);
        
        verify(auditService, never()).logAccountAccess(anyString(), anyString());
    }
    
    /**
     * Test BigDecimal precision handling
     * Validates COBOL COMP-3 equivalent calculations
     */
    @Test
    @DisplayName("UT-AccountService-BIGDECIMAL-PRECISION-VALIDATION")
    void testAvailableCreditCalculation_PrecisionValidation() {
        // Given
        Account account = new Account();
        account.setCreditLimit(new BigDecimal("5000.00"));
        account.setCurrentBalance(new BigDecimal("1234.56"));
        
        when(accountRepository.findById(anyString())).thenReturn(Optional.of(account));
        
        // When
        AccountViewDto result = accountService.getAccountDetails("12345678901");
        
        // Then
        assertThat(result.getAvailableCredit()).isEqualTo(new BigDecimal("3765.44"));
        assertThat(result.getAvailableCredit().scale()).isEqualTo(2);
    }
    
    private Account createMockAccount(String accountId) {
        Account account = new Account();
        account.setAccountId(accountId);
        account.setCustomerId("123456789");
        account.setCurrentBalance(new BigDecimal("1000.00"));
        account.setCreditLimit(new BigDecimal("5000.00"));
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setAccountOpenDate(LocalDate.now());
        return account;
    }
}
```

#### JPA Repository Tests

```java
/**
 * Integration tests for AccountRepository
 * Tests database operations with TestContainers
 */
@DataJpaTest
@Testcontainers
class AccountRepositoryTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("carddemo_test")
            .withUsername("test")
            .withPassword("test");
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private AccountRepository accountRepository;
    
    @Test
    @DisplayName("Repository-FindByCustomerId-ReturnsAccounts")
    void testFindByCustomerId_ValidCustomer_ReturnsAccounts() {
        // Given
        String customerId = "123456789";
        Account account1 = createTestAccount("11111111111", customerId);
        Account account2 = createTestAccount("22222222222", customerId);
        
        entityManager.persistAndFlush(account1);
        entityManager.persistAndFlush(account2);
        
        // When
        List<Account> accounts = accountRepository.findByCustomerId(customerId);
        
        // Then
        assertThat(accounts).hasSize(2);
        assertThat(accounts).extracting(Account::getAccountId)
                .containsExactly("11111111111", "22222222222");
    }
    
    @Test
    @DisplayName("Repository-OptimisticLocking-ThrowsException")
    void testOptimisticLocking_ConcurrentUpdate_ThrowsException() {
        // Given
        Account account = createTestAccount("12345678901", "123456789");
        Account savedAccount = entityManager.persistAndFlush(account);
        entityManager.detach(savedAccount);
        
        // When
        Account account1 = accountRepository.findById("12345678901").get();
        Account account2 = accountRepository.findById("12345678901").get();
        
        account1.setCurrentBalance(new BigDecimal("1000.00"));
        accountRepository.saveAndFlush(account1);
        
        account2.setCurrentBalance(new BigDecimal("2000.00"));
        
        // Then
        assertThatThrownBy(() -> accountRepository.saveAndFlush(account2))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
    
    private Account createTestAccount(String accountId, String customerId) {
        Account account = new Account();
        account.setAccountId(accountId);
        account.setCustomerId(customerId);
        account.setCurrentBalance(new BigDecimal("0.00"));
        account.setCreditLimit(new BigDecimal("5000.00"));
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setAccountOpenDate(LocalDate.now());
        return account;
    }
}
```

### Integration Testing

#### Spring Boot Integration Tests

```java
/**
 * Integration tests for Account API
 * Tests complete REST endpoint functionality
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(OrderAnnotation.class)
@TestPropertySource(locations = "classpath:application-integration.properties")
class AccountIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private JwtTokenProvider tokenProvider;
    
    private String adminToken;
    private String userToken;
    
    @BeforeEach
    void setup() {
        adminToken = tokenProvider.createToken("admin", List.of("ROLE_ADMIN"));
        userToken = tokenProvider.createToken("user", List.of("ROLE_USER"));
    }
    
    @Test
    @Order(1)
    @DisplayName("Integration-GetAccount-AdminAccess-Success")
    void testGetAccount_AdminAccess_ReturnsAccountData() {
        // Given
        String accountId = "12345678901";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        // When
        ResponseEntity<AccountViewDto> response = restTemplate.exchange(
                "/api/accounts/" + accountId,
                HttpMethod.GET,
                entity,
                AccountViewDto.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccountId()).isEqualTo(accountId);
    }
    
    @Test
    @Order(2)
    @DisplayName("Integration-UpdateAccount-UserAccess-Forbidden")
    void testUpdateAccount_UserAccess_ReturnsForbidden() {
        // Given
        String accountId = "12345678901";
        AccountUpdateDto updateRequest = new AccountUpdateDto();
        updateRequest.setCreditLimit(new BigDecimal("10000.00"));
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);
        HttpEntity<AccountUpdateDto> entity = new HttpEntity<>(updateRequest, headers);
        
        // When
        ResponseEntity<AccountUpdateDto> response = restTemplate.exchange(
                "/api/accounts/" + accountId,
                HttpMethod.PUT,
                entity,
                AccountUpdateDto.class
        );
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
```

### Parallel Testing with Legacy COBOL

#### Parallel Test Framework

```java
/**
 * Parallel testing framework for COBOL-to-Java validation
 * Compares Spring Boot outputs with legacy COBOL results
 */
@Component
@Slf4j
public class ParallelTestFramework {
    
    private final AccountService accountService;
    private final FileComparisonService fileComparisonService;
    
    public ParallelTestFramework(AccountService accountService,
                                FileComparisonService fileComparisonService) {
        this.accountService = accountService;
        this.fileComparisonService = fileComparisonService;
    }
    
    /**
     * Execute parallel test for account inquiry
     * Compares Java service output with COBOL COACTVWC results
     */
    @Test
    @DisplayName("Parallel-AccountInquiry-COBOL-Java-Equivalence")
    public void testAccountInquiry_ParallelValidation() throws Exception {
        // Given
        String accountId = "12345678901";
        String testDataFile = "test-data/account-inquiry-input.dat";
        
        // Execute Java service
        AccountViewDto javaResult = accountService.getAccountDetails(accountId);
        
        // Execute COBOL program (simulated)
        String cobolOutput = executeCOBOLProgram("COACTVWC", testDataFile);
        
        // Compare results
        ParallelTestResult comparison = fileComparisonService.compareAccountInquiry(
                javaResult, cobolOutput);
        
        // Assert equivalence
        assertThat(comparison.isEquivalent()).isTrue();
        assertThat(comparison.getDiscrepancies()).isEmpty();
        
        log.info("Parallel test passed - Java and COBOL results match");
    }
    
    /**
     * Execute parallel test for numeric precision
     * Validates BigDecimal calculations match COBOL COMP-3 results
     */
    @Test
    @DisplayName("Parallel-NumericPrecision-COBOL-Java-Equivalence")
    public void testNumericPrecision_ParallelValidation() throws Exception {
        // Given
        List<MonetaryCalculationTest> testCases = loadMonetaryTestCases();
        
        for (MonetaryCalculationTest testCase : testCases) {
            // Execute Java calculation
            BigDecimal javaResult = accountService.calculateInterest(
                    testCase.getBalance(), testCase.getRate());
            
            // Execute COBOL calculation
            BigDecimal cobolResult = executeCOBOLCalculation(
                    testCase.getBalance(), testCase.getRate());
            
            // Compare with zero tolerance
            assertThat(javaResult).isEqualByComparingTo(cobolResult);
            
            log.debug("Precision test passed - Balance: {}, Rate: {}, Result: {}",
                    testCase.getBalance(), testCase.getRate(), javaResult);
        }
    }
    
    private String executeCOBOLProgram(String programName, String inputFile) {
        // Simulate COBOL program execution
        // In real implementation, this would call the actual COBOL program
        return "COBOL_OUTPUT_MOCK";
    }
    
    private BigDecimal executeCOBOLCalculation(BigDecimal balance, BigDecimal rate) {
        // Simulate COBOL COMP-3 calculation
        return balance.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
```

### React Component Testing

#### Cypress End-to-End Tests

```typescript
/**
 * Cypress E2E tests for AccountView component
 * Tests complete user workflows matching BMS screen navigation
 */
describe('Account View Component', () => {
  beforeEach(() => {
    // Login as admin user
    cy.login('admin', 'password');
  });

  it('should display account details correctly', () => {
    // Given
    const accountId = '12345678901';
    
    // When
    cy.visit(`/accounts/${accountId}`);
    
    // Then
    cy.get('[data-testid="account-id"]').should('contain', accountId);
    cy.get('[data-testid="current-balance"]').should('be.visible');
    cy.get('[data-testid="credit-limit"]').should('be.visible');
    cy.get('[data-testid="available-credit"]').should('be.visible');
  });

  it('should handle PF key navigation', () => {
    // Given
    cy.visit('/accounts/12345678901');
    
    // When - simulate F3 key press
    cy.get('body').type('{F3}');
    
    // Then
    cy.url().should('include', '/menu');
  });

  it('should validate account ID format', () => {
    // Given
    cy.visit('/accounts/12345678901');
    
    // When
    cy.get('[data-testid="account-id-input"]').clear().type('invalid');
    cy.get('[data-testid="submit-button"]').click();
    
    // Then
    cy.get('[data-testid="error-message"]')
      .should('contain', 'Account ID must be 11 digits');
  });
});
```

### Running Tests

#### Maven Test Commands

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=AccountServiceTest

# Run integration tests
mvn test -Dtest=*IntegrationTest

# Run parallel validation tests
mvn test -Dtest=*ParallelTest*

# Generate test coverage report
mvn test jacoco:report

# Run tests with specific profile
mvn test -Dspring.profiles.active=test
```

#### Test Coverage Requirements

- **Unit Tests**: 85% line coverage, 80% branch coverage
- **Integration Tests**: 95% API endpoint coverage
- **Parallel Tests**: 100% business logic validation
- **UI Tests**: 90% component interaction coverage

## Containerization Development Workflow

### Docker Development Environment

#### Local Development Stack

Our containerized development environment uses Docker Compose to orchestrate:

- **Spring Boot Application**: Java 21 runtime with hot reload
- **PostgreSQL 15**: Database with persistent storage
- **Redis 7**: Session and cache management
- **Monitoring**: Prometheus and Grafana

#### Starting Development Environment

```bash
# Start all services
docker-compose up -d

# View service status
docker-compose ps

# Follow application logs
docker-compose logs -f app

# Stop all services
docker-compose down

# Rebuild and restart
docker-compose up -d --build
```

### Docker Image Development

#### Multi-Stage Dockerfile

Our Dockerfile implements multi-stage builds for optimized production images:

```dockerfile
# Stage 1: Maven Dependencies Resolution
FROM maven:3.9.4-eclipse-temurin-21 AS dependencies
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Stage 2: Application Build
FROM dependencies AS build
COPY src ./src
RUN mvn clean package -DskipTests -Pprod -B

# Stage 3: Production Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime
COPY --from=build /app/target/carddemo-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### Building Images

```bash
# Build development image
docker build -t carddemo:dev .

# Build production image
docker build -t carddemo:prod --target runtime .

# Build with specific profile
docker build -t carddemo:staging --build-arg PROFILE=staging .
```

### Kubernetes Development

#### Local Kubernetes Setup

```bash
# Install Minikube for local development
curl -Lo minikube https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
chmod +x minikube
sudo mv minikube /usr/local/bin/

# Start local cluster
minikube start --driver=docker

# Enable required addons
minikube addons enable ingress
minikube addons enable dashboard
```

#### Deploying to Kubernetes

```bash
# Create namespace
kubectl create namespace carddemo-dev

# Deploy using Helm
helm install carddemo ./helm/carddemo \
  --namespace carddemo-dev \
  --set image.tag=dev \
  --set environment=development

# Port forward for local access
kubectl port-forward svc/carddemo-app 8080:8080 -n carddemo-dev

# Check deployment status
kubectl get pods -n carddemo-dev
kubectl describe deployment carddemo-app -n carddemo-dev
```

#### Helm Chart Development

```bash
# Create new chart
helm create carddemo

# Validate chart
helm lint ./helm/carddemo

# Dry run deployment
helm install carddemo ./helm/carddemo --dry-run --debug

# Upgrade deployment
helm upgrade carddemo ./helm/carddemo --set image.tag=latest
```

### Container Testing

#### TestContainers Integration

```java
/**
 * Integration tests using TestContainers
 * Provides isolated container environments for testing
 */
@SpringBootTest
@Testcontainers
class ContainerIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("carddemo_test")
            .withUsername("test")
            .withPassword("test");
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
    }
    
    @Test
    void testContainerIntegration() {
        // Test logic using containerized services
        assertTrue(postgres.isRunning());
        assertTrue(redis.isRunning());
    }
}
```

### Container Monitoring

#### Health Checks

```bash
# Check container health
docker exec carddemo-app curl -f http://localhost:8081/actuator/health

# View container logs
docker logs carddemo-app --follow

# Monitor resource usage
docker stats carddemo-app
```

#### Kubernetes Monitoring

```bash
# Check pod health
kubectl get pods -l app=carddemo -o wide

# View pod logs
kubectl logs -l app=carddemo --follow

# Check resource usage
kubectl top pods -n carddemo-dev
```

## Database Migration Development

### Database Schema Management

#### Flyway Migration Strategy

Our database migrations use Flyway for version control and automated schema updates:

```sql
-- V1__Create_Schema.sql
-- Creates initial PostgreSQL schema from VSAM structures

-- CUSTOMERS table (from CUSTDATA.VSAM.KSDS)
CREATE TABLE customers (
    customer_id VARCHAR(9) NOT NULL,
    customer_name VARCHAR(50) NOT NULL,
    customer_address VARCHAR(100),
    customer_phone VARCHAR(15),
    customer_email VARCHAR(50),
    fico_score INTEGER CHECK (fico_score >= 300 AND fico_score <= 850),
    date_of_birth DATE,
    ssn VARCHAR(11),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT customers_pkey PRIMARY KEY (customer_id)
);

-- ACCOUNTS table (from ACCTDATA.VSAM.KSDS)
CREATE TABLE accounts (
    account_id VARCHAR(11) NOT NULL,
    customer_id VARCHAR(9) NOT NULL,
    current_balance NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    credit_limit NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    available_credit NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    account_open_date DATE NOT NULL,
    expiry_date DATE,
    account_status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT accounts_pkey PRIMARY KEY (account_id),
    CONSTRAINT accounts_customer_fkey FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);

-- Create composite indexes matching VSAM access patterns
CREATE INDEX idx_accounts_customer_id ON accounts(customer_id);
CREATE INDEX idx_accounts_customer_status ON accounts(customer_id, account_status);
```

#### Data Loading Migration

```sql
-- V2__Load_Initial_Data.sql
-- Loads reference data from ASCII files

-- Transaction types
INSERT INTO transaction_types (type_code, type_name, type_description) VALUES
('01', 'Purchase', 'Point of sale purchase transactions'),
('02', 'Payment', 'Payment transactions including cash and electronic'),
('03', 'Cash Advance', 'Cash advance transactions'),
('04', 'Transfer', 'Balance transfer transactions'),
('05', 'Fee', 'Fee transactions'),
('06', 'Interest', 'Interest charge transactions'),
('07', 'Adjustment', 'Account adjustment transactions');

-- Transaction categories
INSERT INTO transaction_categories (category_code, category_name, category_description) VALUES
('RETAIL', 'Retail Purchase', 'Standard retail transactions'),
('GROCERY', 'Grocery', 'Grocery store purchases'),
('GAS', 'Gas Station', 'Fuel purchases'),
('RESTAURANT', 'Restaurant', 'Dining and food service'),
('ONLINE', 'Online Purchase', 'E-commerce transactions'),
('ATM', 'ATM Transaction', 'ATM cash withdrawals'),
('PAYMENT', 'Payment', 'Account payments'),
('TRANSFER', 'Transfer', 'Balance transfers'),
('FEE', 'Fee', 'Account fees'),
('INTEREST', 'Interest', 'Interest charges');
```

### Migration Development Process

#### Creating New Migrations

```bash
# Create new migration file
# Format: V{version}__{description}.sql
touch src/main/resources/db/migration/V3__Add_Audit_Tables.sql

# Example migration for new audit table
cat > src/main/resources/db/migration/V3__Add_Audit_Tables.sql << 'EOF'
-- Create audit table for transaction tracking
CREATE TABLE transaction_audit (
    audit_id SERIAL PRIMARY KEY,
    transaction_id VARCHAR(16) NOT NULL,
    account_id VARCHAR(11) NOT NULL,
    action VARCHAR(10) NOT NULL,
    old_values JSONB,
    new_values JSONB,
    user_id VARCHAR(8) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT audit_transaction_fkey FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id)
);

-- Create index for audit queries
CREATE INDEX idx_transaction_audit_transaction_id ON transaction_audit(transaction_id);
CREATE INDEX idx_transaction_audit_timestamp ON transaction_audit(timestamp);
EOF
```

#### Testing Migrations

```bash
# Run migration on development environment
mvn flyway:migrate -Dspring.profiles.active=dev

# Check migration status
mvn flyway:info -Dspring.profiles.active=dev

# Validate migration
mvn flyway:validate -Dspring.profiles.active=dev

# Clean database (development only)
mvn flyway:clean -Dspring.profiles.active=dev
```

### PostgreSQL Best Practices

#### Index Optimization

```sql
-- Create composite indexes matching VSAM access patterns
CREATE INDEX idx_transactions_card_date ON transactions(card_number, transaction_timestamp);
CREATE INDEX idx_transactions_account_type ON transactions(account_id, transaction_type);
CREATE INDEX idx_cards_account_status ON cards(account_id, card_status);

-- Analyze query performance
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM accounts WHERE customer_id = '123456789';

-- Update table statistics
ANALYZE accounts;
ANALYZE transactions;
```

#### Data Integrity Constraints

```sql
-- Add business rule constraints
ALTER TABLE accounts ADD CONSTRAINT check_balance_limit 
    CHECK (current_balance <= credit_limit);

ALTER TABLE transactions ADD CONSTRAINT check_transaction_amount 
    CHECK (transaction_amount != 0);

-- Create partial indexes for performance
CREATE INDEX idx_active_accounts ON accounts(account_id) WHERE account_status = 'ACTIVE';
CREATE INDEX idx_recent_transactions ON transactions(transaction_timestamp) 
    WHERE transaction_timestamp > (CURRENT_DATE - INTERVAL '90 days');
```

### Data Migration Validation

#### Migration Testing

```java
/**
 * Test database migrations and data integrity
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.flyway.locations=classpath:db/migration,classpath:db/test"
})
class MigrationTest {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Test
    @DisplayName("Migration-Schema-Validation")
    void testSchemaCreation() {
        // Verify all tables exist
        List<String> tables = jdbcTemplate.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String.class
        );
        
        assertThat(tables).contains(
            "customers", "accounts", "cards", "transactions",
            "transaction_types", "transaction_categories"
        );
    }
    
    @Test
    @DisplayName("Migration-Index-Validation")
    void testIndexCreation() {
        // Verify composite indexes exist
        List<String> indexes = jdbcTemplate.queryForList(
            "SELECT indexname FROM pg_indexes WHERE tablename = 'accounts'",
            String.class
        );
        
        assertThat(indexes).contains(
            "idx_accounts_customer_id",
            "idx_accounts_customer_status"
        );
    }
    
    @Test
    @DisplayName("Migration-Data-Integrity")
    void testDataIntegrity() {
        // Verify foreign key constraints
        Integer violationCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM accounts a LEFT JOIN customers c ON a.customer_id = c.customer_id WHERE c.customer_id IS NULL",
            Integer.class
        );
        
        assertThat(violationCount).isZero();
    }
}
```

## Security Testing Requirements

### Spring Security JWT Authentication

#### JWT Token Testing

```java
/**
 * JWT Authentication and Authorization Tests
 * Validates Spring Security integration with JWT tokens
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SecurityIntegrationTest {
    
    @Autowired
    private JwtTokenProvider tokenProvider;
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @DisplayName("Security-JWT-Token-Generation-Success")
    void testJwtTokenGeneration() {
        // Given
        String username = "testuser";
        List<String> roles = Arrays.asList("ROLE_USER");
        
        // When
        String token = tokenProvider.createToken(username, roles);
        
        // Then
        assertThat(token).isNotEmpty();
        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.getUsername(token)).isEqualTo(username);
        assertThat(tokenProvider.getRoles(token)).contains("ROLE_USER");
    }
    
    @Test
    @DisplayName("Security-JWT-Token-Expiration-Validation")
    void testJwtTokenExpiration() {
        // Given
        String username = "testuser";
        List<String> roles = Arrays.asList("ROLE_USER");
        
        // Create token with short expiration (for testing)
        String token = tokenProvider.createToken(username, roles, 1000); // 1 second
        
        // When
        assertThat(tokenProvider.validateToken(token)).isTrue();
        
        // Wait for expiration
        await().atMost(Duration.ofSeconds(2))
                .until(() -> !tokenProvider.validateToken(token));
        
        // Then
        assertThat(tokenProvider.validateToken(token)).isFalse();
    }
}
```

#### Role-Based Access Control Testing

```java
/**
 * Test Spring Security @PreAuthorize annotations
 * Validates RACF role mapping equivalence
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoleBasedAccessControlTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @DisplayName("Security-Admin-Access-Success")
    @WithMockUser(roles = "ADMIN")
    void testAdminAccess_Success() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk());
    }
    
    @Test
    @DisplayName("Security-User-Access-Forbidden")
    @WithMockUser(roles = "USER")
    void testUserAccess_Forbidden() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }
    
    @Test
    @DisplayName("Security-Unauthenticated-Access-Unauthorized")
    void testUnauthenticatedAccess_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/accounts/12345678901"))
                .andExpect(status().isUnauthorized());
    }
}
```

### Database Security Testing

#### PostgreSQL Role-Based Access

```java
/**
 * Test PostgreSQL role-based access control
 * Validates database-level security integration
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/carddemo_test",
    "spring.datasource.username=carddemo_test_user",
    "spring.datasource.password=test_password"
})
class DatabaseSecurityTest {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Test
    @DisplayName("Database-Role-Access-Validation")
    void testDatabaseRoleAccess() {
        // Test that application user has correct permissions
        assertDoesNotThrow(() -> {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class);
        });
        
        // Test that application user cannot access system tables
        assertThrows(DataAccessException.class, () -> {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pg_shadow", Integer.class);
        });
    }
    
    @Test
    @DisplayName("Database-Connection-Security")
    void testDatabaseConnectionSecurity() {
        // Verify SSL connection is used
        String sslMode = jdbcTemplate.queryForObject(
            "SELECT setting FROM pg_settings WHERE name = 'ssl'", String.class);
        
        assertThat(sslMode).isEqualTo("on");
    }
}
```

### Session Management Security

#### Redis Session Security Testing

```java
/**
 * Test Redis session security and management
 * Validates session fixation prevention and timeout handling
 */
@SpringBootTest
@Import(TestRedisConfiguration.class)
class SessionSecurityTest {
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @DisplayName("Security-Session-Fixation-Prevention")
    void testSessionFixationPrevention() throws Exception {
        // Given - establish initial session
        MvcResult initialResult = mockMvc.perform(get("/api/login"))
                .andExpect(status().isOk())
                .andReturn();
        
        String initialSessionId = extractSessionId(initialResult);
        
        // When - authenticate user
        MvcResult authResult = mockMvc.perform(post("/api/auth/login")
                .content("{\"username\":\"testuser\",\"password\":\"password\"}")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        
        String newSessionId = extractSessionId(authResult);
        
        // Then - session ID should change after authentication
        assertThat(newSessionId).isNotEqualTo(initialSessionId);
        
        // Old session should be invalidated
        assertThat(redisTemplate.hasKey("spring:session:sessions:" + initialSessionId)).isFalse();
    }
    
    @Test
    @DisplayName("Security-Session-Timeout-Handling")
    void testSessionTimeoutHandling() throws Exception {
        // Given - authenticated session
        String sessionId = createAuthenticatedSession();
        
        // When - access protected resource before timeout
        mockMvc.perform(get("/api/accounts/12345678901")
                .header("Authorization", "Bearer " + sessionId))
                .andExpect(status().isOk());
        
        // Wait for session timeout (using short timeout for testing)
        await().atMost(Duration.ofSeconds(5))
                .until(() -> !redisTemplate.hasKey("spring:session:sessions:" + sessionId));
        
        // Then - access should be denied after timeout
        mockMvc.perform(get("/api/accounts/12345678901")
                .header("Authorization", "Bearer " + sessionId))
                .andExpected(status().isUnauthorized());
    }
    
    private String createAuthenticatedSession() {
        // Implementation to create authenticated session
        return "test-session-id";
    }
    
    private String extractSessionId(MvcResult result) {
        // Implementation to extract session ID from result
        return "extracted-session-id";
    }
}
```

### Security Compliance Testing

#### OWASP Security Testing

```java
/**
 * OWASP security compliance testing
 * Validates common security vulnerabilities
 */
@SpringBootTest
class SecurityComplianceTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @DisplayName("Security-SQL-Injection-Protection")
    void testSqlInjectionProtection() throws Exception {
        String maliciousInput = "'; DROP TABLE accounts; --";
        
        mockMvc.perform(get("/api/accounts/" + maliciousInput))
                .andExpect(status().isBadRequest());
        
        // Verify table still exists
        // This would be tested in integration with database
    }
    
    @Test
    @DisplayName("Security-XSS-Protection")
    void testXssProtection() throws Exception {
        String xssPayload = "<script>alert('XSS')</script>";
        
        mockMvc.perform(post("/api/accounts")
                .content("{\"customerName\":\"" + xssPayload + "\"}")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    @DisplayName("Security-CSRF-Protection")
    void testCsrfProtection() throws Exception {
        mockMvc.perform(post("/api/accounts")
                .content("{\"accountId\":\"12345678901\"}")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
```

## Pull Request Process

### Before Submitting

1. **Code Quality Check**:
   ```bash
   # Run code formatting
   mvn spotless:apply
   
   # Run tests
   mvn test
   
   # Check code coverage
   mvn jacoco:report
   
   # Run security scan
   mvn dependency-check:check
   ```

2. **Build Validation**:
   ```bash
   # Build application
   mvn clean package
   
   # Build Docker image
   docker build -t carddemo:pr-test .
   
   # Run container tests
   docker-compose -f docker-compose.test.yml up --abort-on-container-exit
   ```

3. **Documentation Update**:
   - Update relevant documentation
   - Add/update code comments
   - Include COBOL program references

### Pull Request Checklist

- [ ] Code follows established style guidelines
- [ ] All tests pass (unit, integration, parallel)
- [ ] Code coverage meets minimum requirements (85%)
- [ ] Security tests pass
- [ ] Docker build succeeds
- [ ] Documentation updated
- [ ] COBOL equivalence validated (if applicable)
- [ ] Database migrations tested
- [ ] No breaking changes introduced

### Review Process

1. **Automated Checks**: GitHub Actions runs automated tests
2. **Code Review**: At least one team member reviews the code
3. **Security Review**: Security team reviews for sensitive changes
4. **Business Logic Review**: Business analyst validates COBOL equivalence
5. **Final Approval**: Tech lead provides final approval

### Merge Requirements

- All CI/CD checks pass
- Code review approved
- Security review passed (if applicable)
- Documentation complete
- No merge conflicts

## Issue Reporting

### Bug Reports

When reporting bugs, please include:

1. **Environment Information**:
   - Java version
   - Spring Boot version
   - Database version
   - Container environment

2. **Reproduction Steps**:
   - Exact steps to reproduce
   - Input data used
   - Expected vs actual behavior

3. **Error Information**:
   - Full error messages
   - Stack traces
   - Log excerpts

4. **COBOL Comparison** (if applicable):
   - Original COBOL behavior
   - Java implementation difference
   - Business impact

### Feature Requests

Include:

1. **Business Justification**:
   - Why is this needed?
   - What problem does it solve?
   - COBOL equivalent (if exists)

2. **Technical Requirements**:
   - Detailed specifications
   - Performance requirements
   - Security considerations

3. **Implementation Suggestions**:
   - Proposed approach
   - Alternative solutions
   - Impact on existing code

### Security Issues

**DO NOT** create public issues for security vulnerabilities. Instead:

1. Email security@company.com
2. Include detailed vulnerability information
3. Wait for acknowledgment before disclosure
4. Follow responsible disclosure practices

## Code of Conduct

This project adheres to enterprise-grade professional standards:

### Our Commitment

We are committed to providing a welcoming and inclusive environment for all contributors, regardless of background, experience level, or technology expertise.

### Expected Behavior

- Be respectful and professional
- Focus on constructive feedback
- Collaborate effectively
- Maintain confidentiality of sensitive information
- Follow security best practices

### Unacceptable Behavior

- Harassment or discrimination
- Sharing sensitive business information
- Introducing security vulnerabilities
- Bypassing code review processes
- Ignoring established patterns and standards

### Enforcement

Violations of this code of conduct should be reported to the project maintainers. All reports will be investigated promptly and confidentially.

## Additional Resources

### Documentation

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Docker Documentation](https://docs.docker.com/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)

### Project-Specific Resources

- [Database Schema Documentation](docs/database-schema.md)
- [API Documentation](docs/api-documentation.md)
- [Deployment Guide](docs/deployment-guide.md)
- [Security Guidelines](docs/security-guidelines.md)

### Getting Help

- Create an issue for bugs or feature requests
- Join our development Slack channel
- Attend weekly development meetings
- Consult the project wiki for detailed guides

---

Thank you for contributing to the CardDemo project. Your efforts help ensure a successful migration from legacy COBOL systems to modern cloud-native architecture while maintaining the highest standards of quality and security.

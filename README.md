## CardDemo -- Modern Cloud-Native Credit Card Management System

- [CardDemo -- Modern Cloud-Native Credit Card Management System](#carddemo----modern-cloud-native-credit-card-management-system)
- [Description](#description)
- [Technologies Used](#technologies-used)
- [Prerequisites](#prerequisites)
- [Installation and Setup](#installation-and-setup)
  - [Local Development Environment](#local-development-environment)
  - [Docker Containerization](#docker-containerization)
  - [Kubernetes Deployment](#kubernetes-deployment)
- [Configuration](#configuration)
  - [Application Configuration](#application-configuration)
  - [Database Configuration](#database-configuration)
  - [Redis Session Configuration](#redis-session-configuration)
- [API Documentation](#api-documentation)
  - [Authentication](#authentication)
  - [Core Endpoints](#core-endpoints)
  - [Performance Requirements](#performance-requirements)
- [Application Details](#application-details)
  - [User Functions](#user-functions)
  - [Admin Functions](#admin-functions)
  - [Service Inventory](#service-inventory)
    - [**Backend Services**](#backend-services)
    - [**Frontend Components**](#frontend-components)
    - [**Batch Processing**](#batch-processing)
  - [Application Screens](#application-screens)
    - [**Login Screen**](#login-screen)
    - [**Main Menu**](#main-menu)
    - [**Admin Menu**](#admin-menu)
- [Development Guidelines](#development-guidelines)
- [Testing](#testing)
- [Monitoring and Observability](#monitoring-and-observability)
- [Support](#support)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)
- [Project Status](#project-status)

<br/>

## Description

CardDemo is a modern **cloud-native credit card management system** built with **Java 21** and **Spring Boot 3.2**, representing a complete migration from legacy COBOL/mainframe architecture to contemporary enterprise technologies. This system demonstrates enterprise-grade patterns for **mainframe modernization**, **containerized microservices**, and **cloud-native deployment** while preserving identical business functionality.

The application showcases comprehensive credit card operations including customer account management, card lifecycle operations, real-time transaction processing, bill payment capabilities, user administration, and flexible reporting - all implemented using modern Spring Boot microservices with React-based responsive web interfaces.

**Migration Strategy**: This system implements a **modular monolith architecture** that maintains functional equivalence with the original COBOL implementation while enabling cloud-native scalability, containerization, and modern development practices.

<br/>

## Technologies Used

### Backend Technologies
- **Java 21 LTS** - Primary programming language with virtual threads and enhanced text blocks
- **Spring Boot 3.2.5** - Core framework for REST APIs and microservices
- **Spring Data JPA 3.1.x** - Database access and repository pattern implementation
- **Spring Security 6.1.x** - JWT authentication and role-based authorization
- **Spring Batch 5.0.x** - Chunk-oriented batch processing for high-volume operations
- **Spring Session 3.1.x** - Redis-backed distributed session management
- **Spring Cloud Gateway 4.0.x** - API gateway routing and cross-cutting concerns

### Frontend Technologies
- **React 18.2.0** - Modern web UI framework with functional components
- **TypeScript 5.x** - Type-safe JavaScript for enhanced development experience
- **Material-UI 5.14.x** - Professional enterprise UI component library
- **Redux 4.2.x** - Centralized application state management
- **React Router 6.15.x** - Client-side routing and navigation

### Data & Infrastructure
- **PostgreSQL 15+** - Primary relational database with ACID compliance
- **Redis 7+** - Session storage and distributed caching
- **Docker** - Container orchestration and deployment
- **Kubernetes 1.28+** - Container orchestration and scaling
- **Maven 3.9+** - Build automation and dependency management
- **Flyway 9.21.x** - Database migration and version control

### Supporting Libraries
- **Jackson 2.15.x** - JSON serialization/deserialization
- **HikariCP** - High-performance database connection pooling
- **JasperReports 6.20.x** - Enterprise reporting and document generation
- **Apache Commons** - Utility libraries for string processing and file operations

<br/>

## Prerequisites

Before setting up the CardDemo application, ensure you have the following installed:

- **Java 21 LTS** (OpenJDK 21 or Eclipse Temurin 21)
- **Maven 3.9+** for build automation
- **Docker 24+** and Docker Compose for containerization
- **PostgreSQL 15+** for database (or use Docker)
- **Redis 7+** for session management (or use Docker)
- **Node.js 18+** and **npm 9+** for frontend development
- **kubectl** and **Helm** for Kubernetes deployment (optional)

<br/>

## Installation and Setup

### Local Development Environment

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd carddemo
   ```

2. **Build the Java application**
   ```bash
   # Compile and package the Spring Boot application
   mvn clean compile
   mvn package -DskipTests
   
   # Run unit tests
   mvn test
   
   # Generate test coverage reports
   mvn jacoco:report
   ```

3. **Database setup**
   ```bash
   # Start PostgreSQL using Docker
   docker run --name carddemo-postgres \
     -e POSTGRES_DB=carddemo \
     -e POSTGRES_USER=carddemo_user \
     -e POSTGRES_PASSWORD=carddemo_pass \
     -p 5432:5432 -d postgres:15
   
   # Start Redis for session management
   docker run --name carddemo-redis \
     -p 6379:6379 -d redis:7-alpine
   ```

4. **Run database migrations**
   ```bash
   # Flyway migrations will run automatically on application startup
   # Or run manually:
   mvn flyway:migrate
   ```

5. **Start the Spring Boot application**
   ```bash
   # Development mode with auto-reload
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   
   # Or run the packaged JAR
   java -jar target/carddemo-1.0.0.jar --spring.profiles.active=dev
   ```

6. **Install and start the React frontend**
   ```bash
   cd frontend
   npm install
   npm start
   ```

7. **Access the application**
   - Frontend: http://localhost:3000
   - Backend API: http://localhost:8080
   - API Documentation: http://localhost:8080/swagger-ui.html

### Docker Containerization

1. **Build Docker images**
   ```bash
   # Build backend image
   docker build -t carddemo-backend:latest .
   
   # Build frontend image
   cd frontend
   docker build -t carddemo-frontend:latest .
   ```

2. **Run with Docker Compose**
   ```bash
   # Start all services (backend, frontend, PostgreSQL, Redis)
   docker-compose up -d
   
   # View logs
   docker-compose logs -f
   
   # Stop all services
   docker-compose down
   ```

3. **Environment-specific deployment**
   ```bash
   # Development environment
   docker-compose -f docker-compose.yml -f docker-compose.dev.yml up -d
   
   # Production environment
   docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
   ```

### Kubernetes Deployment

1. **Deploy using Helm charts**
   ```bash
   # Add Helm repository
   helm repo add carddemo ./helm/carddemo
   
   # Install in development namespace
   helm install carddemo-dev carddemo/carddemo \
     --namespace carddemo-dev \
     --create-namespace \
     --values helm/carddemo/values-dev.yaml
   
   # Install in production namespace
   helm install carddemo-prod carddemo/carddemo \
     --namespace carddemo-prod \
     --create-namespace \
     --values helm/carddemo/values-prod.yaml
   ```

2. **Manual Kubernetes deployment**
   ```bash
   # Apply all manifests
   kubectl apply -f k8s/namespace.yaml
   kubectl apply -f k8s/configmap.yaml
   kubectl apply -f k8s/secret.yaml
   kubectl apply -f k8s/postgresql.yaml
   kubectl apply -f k8s/redis.yaml
   kubectl apply -f k8s/backend.yaml
   kubectl apply -f k8s/frontend.yaml
   kubectl apply -f k8s/ingress.yaml
   ```

3. **Verify deployment**
   ```bash
   # Check pod status
   kubectl get pods -n carddemo-dev
   
   # Check services
   kubectl get services -n carddemo-dev
   
   # View logs
   kubectl logs -f deployment/carddemo-backend -n carddemo-dev
   ```

<br/>

## Configuration

### Application Configuration

The application uses Spring Boot's externalized configuration with environment-specific profiles:

```yaml
# application.yml - Base configuration
spring:
  application:
    name: carddemo
  profiles:
    active: dev
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

# Database connection pooling
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 20000

# Redis session configuration
  session:
    store-type: redis
    redis:
      namespace: carddemo:sessions
    timeout: 1800s

# Server configuration
server:
  port: 8080
  servlet:
    context-path: /api/v1
  error:
    include-stacktrace: never

# Security configuration
carddemo:
  security:
    jwt:
      secret: ${JWT_SECRET:defaultSecretKey}
      expiration: 3600000  # 1 hour
    cors:
      allowed-origins: "http://localhost:3000,https://carddemo.example.com"
```

### Database Configuration

```yaml
# application-dev.yml - Development configuration
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/carddemo
    username: carddemo_user
    password: carddemo_pass
    driver-class-name: org.postgresql.Driver

# application-prod.yml - Production configuration
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:postgres-service}:5432/${DB_NAME:carddemo}
    username: ${DB_USERNAME:carddemo_user}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
```

### Redis Session Configuration

```yaml
# Redis configuration for session management
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      jedis:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
```

<br/>

## API Documentation

### Authentication

The system uses **JWT (JSON Web Tokens)** for authentication with role-based authorization:

```bash
# Login endpoint
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "ADMIN001",
  "password": "PASSWORD"
}

# Response
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "type": "Bearer",
  "expiresIn": 3600,
  "roles": ["ADMIN", "USER"]
}

# Use token in subsequent requests
Authorization: Bearer <token>
```

### Core Endpoints

| Endpoint Category | Base Path | Methods | Description |
|------------------|-----------|---------|-------------|
| **Authentication** | `/api/v1/auth` | POST | Login, logout, token refresh |
| **Account Management** | `/api/v1/accounts` | GET, POST, PUT, DELETE | Account CRUD operations |
| **Card Operations** | `/api/v1/cards` | GET, POST, PUT, DELETE | Card lifecycle management |
| **Transaction Processing** | `/api/v1/transactions` | GET, POST | Transaction history and processing |
| **Payment Processing** | `/api/v1/payments` | POST | Bill payment and balance updates |
| **User Administration** | `/api/v1/users` | GET, POST, PUT, DELETE | User management (Admin only) |
| **Reporting** | `/api/v1/reports` | GET | Report generation and data export |

**Example API calls:**

```bash
# Get account details
GET /api/v1/accounts/00000000001
Authorization: Bearer <token>

# Update card status
PUT /api/v1/cards/4000000000000001
Authorization: Bearer <token>
Content-Type: application/json

{
  "status": "ACTIVE",
  "creditLimit": 5000.00
}

# Process payment
POST /api/v1/payments
Authorization: Bearer <token>
Content-Type: application/json

{
  "accountId": "00000000001",
  "amount": 250.00,
  "paymentDate": "2024-01-15"
}
```

### Performance Requirements

The system is designed to meet stringent performance requirements:

- **Transaction Response Time**: < 200ms for 95% of API requests
- **Throughput Capacity**: Support for 10,000 transactions per second (TPS)
- **Concurrent Users**: 1,000+ simultaneous user sessions
- **Batch Processing**: Complete overnight batch cycles within 4-hour windows
- **Database Performance**: Query response times < 50ms for indexed lookups
- **Memory Usage**: JVM heap size optimized for container limits (2-8 GB per pod)

**Monitoring endpoints:**
```bash
# Health check
GET /api/v1/actuator/health

# Performance metrics
GET /api/v1/actuator/metrics

# Database connection status
GET /api/v1/actuator/metrics/hikaricp.connections.active
```

<br/>

## Application Details

The CardDemo system implements comprehensive credit card management functionality through a modern cloud-native architecture, supporting two distinct user types with specialized capabilities.

### User Functions

![User Flow](./diagrams/Application-Flow-User.png)

**Regular User Capabilities:**
- **Account Management**: View account details, balances, and transaction history
- **Card Operations**: View card information, request status changes, and manage card settings
- **Transaction Processing**: Review transaction history, add new transactions, and view categorized spending
- **Payment Processing**: Make bill payments, view payment history, and manage payment methods
- **Profile Management**: Update personal information and security settings

### Admin Functions

![Admin Flow](./diagrams/Application-Flow-Admin.png)

**Administrative User Capabilities:**
- **User Administration**: Create, update, and deactivate user accounts
- **System Configuration**: Manage transaction types, categories, and discount groups
- **Reporting**: Generate comprehensive reports for audit and compliance
- **Data Management**: Perform batch operations and data maintenance tasks
- **Security Management**: Monitor user access and manage security policies

### Service Inventory

#### **Backend Services**

| Service Name | Endpoint | Function | Original COBOL Program |
|--------------|----------|----------|----------------------|
| **AuthenticationService** | `/api/v1/auth` | User authentication and JWT management | COSGN00C |
| **MenuService** | `/api/v1/menu` | Navigation and menu management | COMEN01C |
| **AdminMenuService** | `/api/v1/admin-menu` | Administrative menu functions | COADM01C |
| **AccountViewService** | `/api/v1/accounts/{id}` | Account detail retrieval | COACTVWC |
| **AccountUpdateService** | `/api/v1/accounts/{id}` | Account modification operations | COACTUPC |
| **CardListService** | `/api/v1/cards` | Card listing with pagination | COCRDLIC |
| **CardDetailService** | `/api/v1/cards/{cardNumber}` | Individual card information | COCRDSLC |
| **CardUpdateService** | `/api/v1/cards/{cardNumber}` | Card modification operations | COCRDUPC |
| **TransactionService** | `/api/v1/transactions` | Transaction management | COTRN00C-02C |
| **PaymentService** | `/api/v1/payments` | Bill payment processing | COBIL00C |
| **UserManagementService** | `/api/v1/users` | User CRUD operations | COUSR00C-03C |
| **ReportService** | `/api/v1/reports` | Report generation and export | CORPT00C |

#### **Frontend Components**

| Component Name | Route | Function | Original BMS Map |
|----------------|-------|----------|------------------|
| **LoginScreen** | `/login` | User authentication interface | COSGN00.bms |
| **MainMenu** | `/menu` | Primary navigation interface | COMEN01.bms |
| **AdminMenu** | `/admin` | Administrative functions menu | COADM01.bms |
| **AccountView** | `/accounts/:id` | Account detail display | COACTVW.bms |
| **AccountUpdate** | `/accounts/:id/edit` | Account modification form | COACTUP.bms |
| **CardList** | `/cards` | Card listing with search/filter | COCRDLI.bms |
| **CardDetail** | `/cards/:cardNumber` | Card information display | COCRDSL.bms |
| **CardUpdate** | `/cards/:cardNumber/edit` | Card modification form | COCRDUP.bms |
| **TransactionList** | `/transactions` | Transaction history display | COTRN00.bms |
| **TransactionView** | `/transactions/:id` | Transaction detail view | COTRN01.bms |
| **TransactionAdd** | `/transactions/new` | New transaction entry | COTRN02.bms |
| **BillPayment** | `/payments` | Payment processing interface | COBIL00.bms |
| **UserList** | `/admin/users` | User listing and management | COUSR00.bms |
| **UserCRUD** | `/admin/users/:id` | User create/update/delete | COUSR01-03.bms |
| **ReportMenu** | `/reports` | Report selection and generation | CORPT00.bms |

#### **Batch Processing**

| Job Name | Schedule | Function | Original Program |
|----------|----------|----------|------------------|
| **AccountBatchProcessor** | Daily 02:00 | Account data processing and validation | CBACT01C-04C |
| **CustomerBatchProcessor** | Daily 01:00 | Customer data loading and updates | CBCUS01C |
| **TransactionBatchProcessor** | Daily 03:00 | Transaction posting and balance updates | CBTRN01C-03C |
| **StatementProcessor** | Monthly | Customer statement generation | CBSTM03A/B |
| **InterestCalculator** | Monthly | Interest calculation and posting | CBACT04C |
| **ReportGenerator** | Daily 04:00 | Automated report generation | CORPT00C |
| **DataMaintenanceJob** | Weekly | Database cleanup and optimization | Various |

<br/>

### Application Screens

#### **Login Screen**

![Login Screen](./diagrams/Signon-Screen.png)

**Modern React Implementation:**
- Responsive design compatible with desktop and mobile devices
- Form validation with real-time feedback
- JWT token-based authentication
- Role-based access control integration
- Session timeout management

#### **Main Menu**

![Main Menu](./diagrams/Main-Menu.png)

**Enhanced Navigation Features:**
- Dynamic menu generation based on user roles
- Quick access to frequently used functions
- Real-time notification system
- Responsive navigation for mobile devices
- Keyboard shortcuts for power users

#### **Admin Menu**

![Admin Menu](./diagrams/Admin-Menu.png)

**Administrative Interface:**
- Comprehensive user management capabilities
- System configuration and maintenance tools
- Real-time system monitoring and health checks
- Bulk operations and data management utilities
- Audit trail and compliance reporting

<br/>

## Development Guidelines

### Code Standards
- **Java**: Follow Google Java Style Guide with Spring Boot conventions
- **TypeScript/React**: ESLint configuration with Airbnb style guide
- **Database**: Use Flyway migrations for all schema changes
- **Testing**: Minimum 80% code coverage for all services
- **Documentation**: Comprehensive JavaDoc for all public APIs

### Git Workflow
```bash
# Feature branch workflow
git checkout -b feature/card-management-enhancement
git commit -m "feat: add card activation endpoint"
git push origin feature/card-management-enhancement
```

### Environment Management
- **Development**: Local development with Docker dependencies
- **Staging**: Kubernetes deployment with production-like data
- **Production**: Multi-zone Kubernetes cluster with HA database

<br/>

## Testing

### Unit Testing
```bash
# Run backend unit tests
mvn test

# Run frontend unit tests
cd frontend && npm test

# Generate coverage reports
mvn jacoco:report
cd frontend && npm run test:coverage
```

### Integration Testing
```bash
# Run integration tests with Testcontainers
mvn verify -Pintegration-tests

# API testing with specific profile
mvn test -Dtest=*IntegrationTest -Dspring.profiles.active=test
```

### Performance Testing
```bash
# Load testing with JMeter
jmeter -n -t tests/performance/carddemo-load-test.jmx -l results.jtl

# Stress testing for 10,000 TPS requirement
k6 run tests/performance/stress-test.js
```

<br/>

## Monitoring and Observability

### Application Monitoring
- **Spring Boot Actuator**: Health checks, metrics, and application info
- **Micrometer**: Integration with Prometheus and Grafana
- **Distributed Tracing**: Jaeger/Zipkin integration for request tracing
- **Log Aggregation**: ELK Stack (Elasticsearch, Logstash, Kibana)

### Infrastructure Monitoring  
- **Kubernetes Monitoring**: Prometheus + Grafana for cluster metrics
- **Database Monitoring**: PostgreSQL performance metrics and query analysis
- **Redis Monitoring**: Session store performance and memory usage
- **Container Monitoring**: Resource usage and scaling metrics

### Alerting
- **Performance Alerts**: Response time > 200ms, TPS > 10,000
- **Error Rate Alerts**: Error rate > 1%, failed transactions
- **Infrastructure Alerts**: High CPU/memory usage, database connectivity
- **Security Alerts**: Failed authentication attempts, suspicious activity

<br/>

## Support

For technical support, issues, or feature requests:

- **GitHub Issues**: Create an issue in the repository for bug reports or feature requests
- **Documentation**: Comprehensive API documentation available at `/swagger-ui.html`
- **Developer Guide**: Detailed setup and development instructions in `/docs/developer-guide.md`
- **Troubleshooting**: Common issues and solutions in `/docs/troubleshooting.md`

<br/>

## Roadmap

### Phase 1: Core Migration (Current)
- [x] Complete COBOL to Java service migration
- [x] React frontend implementation
- [x] PostgreSQL database migration
- [x] Docker containerization
- [x] Basic Kubernetes deployment

### Phase 2: Enhanced Features (Q2 2024)
- [ ] Advanced monitoring and observability
- [ ] Multi-region deployment support
- [ ] Enhanced security features (OAuth2, SAML)
- [ ] Mobile-responsive design improvements
- [ ] Performance optimization and caching

### Phase 3: Enterprise Integration (Q3 2024)
- [ ] External payment gateway integration
- [ ] Real-time fraud detection
- [ ] Machine learning-based analytics
- [ ] Advanced reporting and business intelligence
- [ ] API rate limiting and throttling

### Phase 4: Cloud-Native Features (Q4 2024)
- [ ] Event-driven architecture with message queues
- [ ] Microservices decomposition
- [ ] Service mesh implementation
- [ ] Advanced autoscaling and resource optimization
- [ ] Compliance and regulatory enhancements

<br/>

## Contributing

We welcome contributions to the CardDemo project! Please follow these guidelines:

### Contributing Process
1. **Fork the repository** and create a feature branch
2. **Follow coding standards** and include comprehensive tests
3. **Update documentation** for any API changes
4. **Submit a pull request** with detailed description
5. **Ensure CI/CD pipeline passes** all tests and checks

### Development Setup
```bash
# Set up development environment
git clone <fork-url>
cd carddemo
mvn clean install
docker-compose up -d postgres redis
```

### Code Review Criteria
- **Functionality**: All features work as specified
- **Testing**: Minimum 80% code coverage
- **Documentation**: APIs documented with OpenAPI/Swagger
- **Performance**: No degradation in response times
- **Security**: No security vulnerabilities introduced

<br/>

## License

This project is released under the **Apache 2.0 License** to serve as a community resource for mainframe modernization and cloud-native development patterns.

```
Copyright 2024 CardDemo Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

<br/>

## Project Status

**Current Version**: 2.0.0 (Cloud-Native Release)

**Development Status**: Active development with regular releases

**Production Readiness**: Ready for enterprise deployment with comprehensive testing and monitoring

**Migration Status**: Complete migration from COBOL/mainframe to Java 21/Spring Boot cloud-native architecture

**Compliance**: Maintains functional equivalence with original COBOL implementation while providing modern cloud-native capabilities

---

**Last Updated**: January 2024  
**Contributors**: CardDemo Development Team  
**Maintenance**: Active maintenance and feature development ongoing

<br/>
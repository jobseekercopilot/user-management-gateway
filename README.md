# User Management Gateway

A Spring Boot API gateway for the Job Seeker Copilot platform that provides unified user management operations including registration, authentication, and secure profile management.

## Overview

The User Management Gateway acts as a facade between client applications and backend microservices (Authentication Service and User Profile Service). It orchestrates user registration, login, and profile operations while maintaining secure token-based authentication.

## Features

- **User Registration**: Register new claimants with validation and automatic profile initialization
- **User Authentication**: Secure login with JWT token generation
- **Profile Management**: Retrieve and update user profiles with fallback handling
- **Service Orchestration**: Coordinates between authentication and profile services
- **Error Handling**: Comprehensive error handling with meaningful messages
- **Health Monitoring**: Actuator endpoints for health and info checks
- **Docker Support**: Containerized deployment with multi-stage builds

## Tech Stack

- **Java 17**
- **Spring Boot 3.2.0**
- **Spring Web** - REST API framework
- **Spring Actuator** - Health and monitoring endpoints
- **Lombok** - Boilerplate code reduction
- **Maven** - Build and dependency management
- **Docker** - Containerization
- **JUnit & WireMock** - Testing framework with service mocking

## Architecture

```
┌─────────────────┐
│  Client Apps    │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────┐
│  User Management Gateway    │
│  (Port 8083)                │
│                             │
│  - Registration             │
│  - Authentication           │
│  - Profile Management       │
└──────────┬──────────────────┘
           │
           ├──────────────────┐
           │                  │
           ▼                  ▼
┌──────────────────┐  ┌──────────────────┐
│ Authentication   │  │ User Profile     │
│ Service          │  │ Service          │
│ (Port 8084)      │  │ (Port 8085)      │
└──────────────────┘  └──────────────────┘
```

## API Endpoints

### 1. Register User
```http
POST /api/auth/register
Content-Type: application/json

{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "securepass",
  "profile": {
    "phone": "123-456-7890",
    "address": "123 Main St",
    "city": "Dublin",
    "country": "Ireland"
  }
}
```

**Response (201 Created):**
```json
{
  "statusCode": 201,
  "success": true,
  "message": "Claimant account registered securely with the User Management Gateway.",
  "data": {
    "id": "user-123",
    "name": "John Doe",
    "email": "john@example.com",
    "profile": { ... },
    "token": "jwt-token-here"
  }
}
```

### 2. Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "john@example.com",
  "password": "securepass"
}
```

**Response (200 OK):**
```json
{
  "statusCode": 200,
  "success": true,
  "message": "Credentials verified and secure handshake completed by gateway.",
  "data": {
    "id": "user-123",
    "name": "John Doe",
    "email": "john@example.com",
    "profile": { ... },
    "token": "jwt-token-here"
  }
}
```

### 3. Get Profile
```http
GET /api/auth/profile
Authorization: Bearer {token}
```

**Response (200 OK):**
```json
{
  "statusCode": 200,
  "success": true,
  "message": "User profile retrieved successfully from the gateway.",
  "data": {
    "id": "user-123",
    "name": "John Doe",
    "email": "john@example.com",
    "profile": { ... }
  }
}
```

### 4. Update Profile
```http
PUT /api/auth/profile
Authorization: Bearer {token}
Content-Type: application/json

{
  "phone": "987-654-3210",
  "address": "456 New St",
  "city": "Cork",
  "country": "Ireland"
}
```

**Response (200 OK):**
```json
{
  "statusCode": 200,
  "success": true,
  "message": "User profile updated successfully in gateway and profile service.",
  "data": {
    "id": "user-123",
    "name": "John Doe",
    "email": "john@example.com",
    "profile": { ... }
  }
}
```

## Configuration

The application uses the following configuration properties (set via environment variables or `application.properties`):

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8083 | Gateway server port |
| `AUTHENTICATION_SERVICE_URL` | http://localhost:8084 | Authentication service endpoint |
| `USER_PROFILE_SERVICE_URL` | http://localhost:8085 | User profile service endpoint |

### Environment Variables

```bash
export AUTHENTICATION_SERVICE_URL=http://auth-service:8084
export USER_PROFILE_SERVICE_URL=http://profile-service:8085
```

## Running the Application

### Prerequisites

- Java 17+
- Maven 3.8+
- Docker (optional, for containerized deployment)

### Local Development

1. **Build the application:**
   ```bash
   mvn clean package
   ```

2. **Run the application:**
   ```bash
   java -jar target/user-management-gateway-1.0.0.jar
   ```

3. **Or use Maven Spring Boot plugin:**
   ```bash
   mvn spring-boot:run
   ```

The application will start on `http://localhost:8083`

### Using Docker

1. **Build the Docker image:**
   ```bash
   docker build -t user-management-gateway .
   ```

2. **Run the container:**
   ```bash
   docker run -p 8083:8083 \
     -e AUTHENTICATION_SERVICE_URL=http://auth-service:8084 \
     -e USER_PROFILE_SERVICE_URL=http://profile-service:8085 \
     user-management-gateway
   ```

## Testing

### Run All Tests
```bash
mvn test
```

### Test Coverage
The project includes comprehensive unit and integration tests:
- **Controller Tests**: REST endpoint validation
- **Service Tests**: Business logic verification
- **Client Tests**: HTTP client integration with WireMock

### Actuator Endpoints

Health check and application info are available at:
- `http://localhost:8083/actuator/health`
- `http://localhost:8083/actuator/info`

## Project Structure

```
user-management-gateway/
├── src/
│   ├── main/
│   │   ├── java/com/jobseekercopilot/usermanagementgateway/
│   │   │   ├── UserManagementGatewayApplication.java
│   │   │   ├── client/              # HTTP clients for downstream services
│   │   │   │   ├── AuthenticationClient.java
│   │   │   │   └── UserProfileClient.java
│   │   │   ├── controller/          # REST API endpoints
│   │   │   │   └── UserManagementController.java
│   │   │   ├── model/               # Data models and DTOs
│   │   │   │   ├── GatewayResponse.java
│   │   │   │   ├── LoginRequest.java
│   │   │   │   ├── LoginResponse.java
│   │   │   │   ├── RegisterRequest.java
│   │   │   │   ├── User.java
│   │   │   │   ├── UserAccountResponse.java
│   │   │   │   └── UserProfile.java
│   │   │   └── service/             # Business logic
│   │   │       ├── IUserManagementService.java
│   │   │       └── UserManagementService.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/                        # Test classes
├── Dockerfile
├── pom.xml
└── README.md
```

## Error Handling

The gateway returns consistent error responses:

```json
{
  "statusCode": 400,
  "success": false,
  "message": "Error description here"
}
```

Common error scenarios:
- **400 Bad Request**: Invalid input, missing credentials, validation failures
- **401 Unauthorized**: Invalid or expired token
- **404 Not Found**: User or profile not found
- **500 Internal Server Error**: Service communication failures

## Security

- JWT-based authentication for protected endpoints
- Token validation via Authentication Service
- Secure communication between microservices
- Input validation and sanitization
- Password minimum length enforcement (4+ characters)

## Development

### Building
```bash
mvn clean install
```

### Running Tests
```bash
mvn test
```

### Docker Build
```bash
docker build -t user-management-gateway:latest .
```

## Contributing

This project is part of the Job Seeker Copilot platform. For contribution guidelines, please refer to the main repository.

## License

Proprietary - Job Seeker Copilot Platform

## Support

For issues and questions, please contact the development team or open an issue in the project repository.
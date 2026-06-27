# Auth Service Design

## Overview
The `auth-service` is responsible for authenticating users and issuing JSON Web Tokens (JWT). It provides both an Access Token (short-lived) and a Refresh Token (long-lived) to securely manage user sessions across the microservices architecture. It delegates the retrieval of user credentials and roles to the `user-service`.

## Technologies Used
- **Spring Boot**: Core framework
- **Java 17**: Runtime environment
- **Spring Security**: For endpoint security configuration and BCrypt password matching
- **jjwt (JSON Web Token for Java)**: For creating and verifying JWTs
- **Spring RestClient**: For synchronous inter-service communication with `user-service`

## Token Strategy & RBAC
- **Access Token**: Contains user identity claims including `userId` and `role`. It is short-lived (e.g., 1 hour).
- **Refresh Token**: Used to obtain a new Access Token. It is long-lived (e.g., 24 hours).
- **Role-Based Access Control (RBAC)**: Upon successful authentication, the token is populated with a `role` claim mapped from `roleId` in the `user-service`:
  - `1` -> `customer`
  - `2` -> `agent`
  - `3` -> `supervisor`
  
Other microservices (like Chat Service or Agent State Service) decode this JWT to authorize specific API actions based on the user's role.

## Endpoints

### 1. `POST /api/auth/login`
Authenticates a user against the `user-service` database and returns an access token and refresh token containing their user ID and role.

**Request Body:**
```json
{
  "username": "user",
  "password": "password"
}
```

**Response Body:**
```json
{
  "accessToken": "eyJhb...",
  "refreshToken": "eyJhb..."
}
```

### 2. `POST /api/auth/refresh`
Generates a new access token and refresh token using an existing valid refresh token.

**Request Body:**
```json
{
  "refreshToken": "eyJhb..."
}
```

**Response Body:**
```json
{
  "accessToken": "eyJhb...",
  "refreshToken": "eyJhb..."
}
```

### 3. `GET /api/auth/validate`
Validates an access token.

**Query Parameter:** `token` (String)

**Response:**
- `200 OK`: Token is valid.
- `401 Unauthorized`: Token is invalid or expired.

## Kubernetes Deployment
The service is containerized using Docker and deployed using Kubernetes.

### Build Docker Image
```bash
cd auth-service
./mvnw clean package -DskipTests
docker build -t auth-service:latest .
```

### Deploy to Cluster
Apply the Kubernetes manifests from the `infra/` directory:
```bash
kubectl apply -f infra/auth-deployment.yaml
```

The service will be available within the cluster at `http://auth-service:8081`. The API Gateway can be configured to route authentication requests to this service.

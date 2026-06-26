# Auth Service Design

## Overview
The `auth-service` is responsible for authenticating users and issuing JSON Web Tokens (JWT). It provides both an Access Token (short-lived) and a Refresh Token (long-lived) to securely manage user sessions across the microservices architecture.

## Technologies Used
- **Spring Boot 3.5.4**: Core framework
- **Java 17**: Runtime environment
- **Spring Security**: For basic endpoint security configuration
- **jjwt (JSON Web Token for Java)**: For creating and verifying JWTs

## Token Strategy
- **Access Token**: Contains user identity claims. It is short-lived (e.g., 1 hour) to minimize the risk if compromised.
- **Refresh Token**: Used to obtain a new Access Token without requiring the user to log in again. It is long-lived (e.g., 24 hours).

## Endpoints

### 1. `POST /api/auth/login`
Authenticates a user and returns both an access token and a refresh token.

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

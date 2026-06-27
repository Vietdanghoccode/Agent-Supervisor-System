# User Service Design

## Overview
The `user-service` is a core microservice in the Agent-Supervisor System responsible for managing user data, roles, and status. It serves as the primary source of truth for user identities and is called by other services (such as `auth-service`) to retrieve or validate user information.

## Technologies Used
- **Spring Boot**: Core application framework.
- **Spring Data JPA**: For data access and mapping entities to relational database tables.
- **PostgreSQL**: The relational database used to store user records.
- **Spring Security Crypto**: Provides `BCryptPasswordEncoder` for securely hashing passwords before storing them.

## Data Model
The service manages a `User` entity with the following attributes:
- `id`: (Long) Primary key, auto-incremented.
- `email`: (String) Unique identifier for the user.
- `passwordHash`: (String) BCrypt-hashed password.
- `roleId`: (Integer) Determines the user's role.
  - `1`: Customer
  - `2`: Agent
  - `3`: Supervisor
- `status`: (String) Account status, typically `ACTIVE` or `INACTIVE`.

## Endpoints

### Internal APIs
These endpoints are not exposed to the public internet and are intended to be called by other microservices within the cluster.

#### `GET /api/user/internal/users/by-email`
Retrieves a user's details based on their email address.

**Query Parameters:**
- `email` (String): The email of the user to fetch.

**Response (200 OK):**
```json
{
  "id": 1,
  "email": "agent@example.com",
  "passwordHash": "$2a$10$...",
  "roleId": 2,
  "status": "ACTIVE"
}
```
**Response (404 Not Found):** Returned if no user matches the given email.

## Database Seeding
Upon application startup, if the database is empty, a `DataSeeder` component automatically runs to insert default accounts for testing:
- **Customer**: `customer@example.com` (password: `password123`)
- **Agent**: `agent@example.com` (password: `password123`)
- **Supervisor**: `supervisor@example.com` (password: `password123`)

## Deployment
The service is containerized and deployed alongside a dedicated PostgreSQL instance using Kubernetes. 
- Deployment manifest: `infra/user-deployment.yaml`
- PostgreSQL manifest: `infra/postgres-deployment.yaml`

# Fix: 403 Forbidden trên `/api/auth/v3/api-docs` và `/api/user/v3/api-docs`

## Vấn đề

Khi truy cập Swagger UI qua API Gateway (`http://localhost:8080/swagger-ui.html`), Swagger cố gắng tải API docs của các service qua Gateway:

- `GET http://localhost:8080/api/auth/v3/api-docs` → **403 Forbidden**
- `GET http://localhost:8080/api/user/v3/api-docs` → **403 Forbidden**

## Nguyên nhân

Có **3 vấn đề kết hợp**:

### 1. Gateway chặn request trước khi forward ← **Root cause chính**

`spring-cloud-starter-gateway` kéo theo `spring-security` như một transitive dependency. Khi Spring Security có mặt **mà không có `SecurityWebFilterChain` được cấu hình**, Spring Boot auto-config sẽ **block TẤT CẢ request** với HTTP 401/403.

Đây là lý do vì sao dù sửa auth-service vẫn không giải quyết được — **request chưa bao giờ đến auth-service**.

### 2. Custom SpringDoc path sai thiết kế

Mỗi service tự đặt path SpringDoc theo "public path" mà Gateway expose:

```yaml
# auth-service (cũ)
springdoc.api-docs.path: /api/auth/v3/api-docs

# user-service (cũ)
springdoc.api-docs.path: /api/user/v3/api-docs
```

Điều này làm **mỗi service phải tự biết mình được đặt dưới prefix gì** — vi phạm nguyên tắc microservice (service chỉ cần biết path nội bộ của mình).

### 3. `SecurityConfig` của auth-service chưa whitelist đủ endpoint

SpringDoc không chỉ có 1 endpoint, nó còn có:
- `/v3/api-docs/**` — JSON schema của API
- `/swagger-ui/**` — static resources
- `/swagger-ui.html`

Security config cũ chỉ permit `/api/auth/**`, nên các đường dẫn nội bộ của SpringDoc bị block bởi `.anyRequest().authenticated()` → **403**.

---

## Cách sửa (Pattern chuẩn: Gateway owns routing)

Nguyên tắc: **Mỗi service expose docs ở path mặc định `/v3/api-docs`. Gateway chịu trách nhiệm rewrite public path → internal path.**

### File 1: `api-gateway/src/main/resources/application.yml`

Thêm 2 route riêng cho api-docs **đặt TRƯỚC** các route chung, kèm `RewritePath` filter:

```yaml
routes:
  # --- API Docs routes (must be before general routes) ---
  - id: user-service-api-docs-route
    uri: http://user-service:80
    predicates:
      - Path=/api/user/v3/api-docs
    filters:
      - RewritePath=/api/user/v3/api-docs, /v3/api-docs  # rewrite trước khi forward

  - id: auth-service-api-docs-route
    uri: http://auth-service:8081
    predicates:
      - Path=/api/auth/v3/api-docs
    filters:
      - RewritePath=/api/auth/v3/api-docs, /v3/api-docs  # rewrite trước khi forward

  # --- General service routes ---
  - id: user-service-route
    uri: http://user-service:80
    predicates:
      - Path=/api/user/**

  - id: auth-service-route
    uri: http://auth-service:8081
    predicates:
      - Path=/api/auth/**
```

> ⚠️ **Quan trọng**: Route `api-docs` phải đặt **trước** route chung vì Spring Cloud Gateway match theo thứ tự từ trên xuống. Nếu route `/api/auth/**` đứng trước, nó sẽ "nuốt" cả request `/api/auth/v3/api-docs` mà không rewrite path.

### File 2: `auth-service/src/main/resources/application.yml`

Đổi SpringDoc path về **mặc định**:

```yaml
# Cũ
springdoc:
  api-docs:
    path: /api/auth/v3/api-docs

# Mới
springdoc:
  api-docs:
    path: /v3/api-docs
```

### File 3: `user-service/src/main/resources/application.yml`

Đổi SpringDoc path về **mặc định**:

```yaml
# Cũ
springdoc:
  api-docs:
    path: /api/user/v3/api-docs

# Mới
springdoc:
  api-docs:
    path: /v3/api-docs
```

### File 4: `auth-service/src/main/java/.../config/SecurityConfig.java`

Thêm các endpoint nội bộ của SpringDoc vào whitelist:

```java
// Cũ
.requestMatchers("/api/auth/**").permitAll()

// Mới
.requestMatchers(
    "/api/auth/**",
    "/v3/api-docs/**",   // SpringDoc JSON endpoint
    "/swagger-ui/**",    // Swagger UI static resources
    "/swagger-ui.html"   // Swagger UI page
).permitAll()
```

### File 5 [MỚI]: `api-gateway/src/main/java/.../config/SecurityConfig.java`

Gateway dùng **WebFlux** (reactive), nên phải dùng `SecurityWebFilterChain` thay vì `SecurityFilterChain` của MVC:

```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                // Swagger UI & API docs (gateway-level)
                .pathMatchers(
                    "/swagger-ui.html", "/swagger-ui/**",
                    "/v3/api-docs/**", "/webjars/**"
                ).permitAll()
                // API docs forwarded to downstream services
                .pathMatchers("/api/auth/v3/api-docs", "/api/user/v3/api-docs").permitAll()
                // All API traffic — let downstream services handle auth
                .pathMatchers("/api/**").permitAll()
                .anyExchange().authenticated()
            );
        return http.build();
    }
}
```

---

## Câu hỏi: localhost hay Cloud IP?

`localhost` là **ĐÚNG**. Khi làm việc trên Google Cloud VM và forward port qua VSCode:

```
Browser (máy local)       VSCode Tunnel        Cloud VM
  localhost:8080   ──────────────────────►   :8080
                                          kubectl port-forward
                                              └──► Gateway Service
```

Swagger UI dùng **relative URL** (`/api/auth/v3/api-docs`), nên browser tự động gửi về `localhost:8080` → tunnel → cloud VM → đúng.

---

## Luồng request sau khi sửa đủ 3 lớp

```
Browser → GET localhost:8080/api/auth/v3/api-docs
    ↓  VSCode tunnel
Cloud VM :8080 → kubectl port-forward → Gateway:80
    ↓  [Lớp 1] Gateway SecurityWebFilterChain: /api/auth/v3/api-docs → permitAll ✅
    ↓  [Lớp 2] Route match: auth-service-api-docs-route
    ↓           RewritePath: /api/auth/v3/api-docs → /v3/api-docs
    ↓
auth-service:8081 → GET /v3/api-docs
    ↓  [Lớp 3] auth-service SecurityConfig: /v3/api-docs/** → permitAll ✅
    ↓
SpringDoc → trả về JSON docs ✅
```

---

## Rebuild và redeploy

Sau khi sửa code, cần build lại Docker image và redeploy lên Kubernetes.

### Bước 1: Build lại các service đã thay đổi

eval $(minikube docker-env)

```bash
# Build auth-service

cd auth-service
mvn clean package -DskipTests
docker build --no-cache -t auth-service:latest .

# Build user-service
cd ../user-service
mvn clean package -DskipTests
docker build --no-cache -t user-service:latest .

# Build api-gateway
cd ../api-gateway
mvn clean package -DskipTests
docker build --no-cache -t api-gateway:latest .
```

### Bước 2: Load image vào cluster (nếu dùng minikube)

```bash
minikube image load auth-service:latest
minikube image load user-service:latest
minikube image load api-gateway:latest
```

> Nếu dùng kind: `kind load docker-image <image>:<tag>`

### Bước 3: Rollout restart deployment

```bash
kubectl rollout restart deployment/auth-service
kubectl rollout restart deployment/user-service
kubectl rollout restart deployment/spring-cloud-gateway

```

### Bước 4: Kiểm tra pods đã chạy lại

```bash
kubectl get pods -w
# Chờ đến khi tất cả pods ở trạng thái Running
```

### Bước 5: Port-forward và kiểm tra

```bash
# Nếu đang có port-forward cũ, kill và chạy lại
kubectl port-forward --address 0.0.0.0 svc/spring-cloud-gateway 8080:80
```

Sau đó kiểm tra:

```bash
# Test trực tiếp api-docs endpoint
curl http://localhost:8080/api/auth/v3/api-docs
curl http://localhost:8080/api/user/v3/api-docs

# Kỳ vọng: trả về JSON, không phải 403
```

Hoặc mở trình duyệt tại `http://localhost:8080/swagger-ui.html`.

---

## Checklist xác nhận fix thành công

- [ ] `curl http://localhost:8080/api/auth/v3/api-docs` trả về HTTP 200 + JSON
- [ ] `curl http://localhost:8080/api/user/v3/api-docs` trả về HTTP 200 + JSON
- [ ] `http://localhost:8080/swagger-ui.html` hiển thị đầy đủ docs của cả 2 service
- [ ] Các endpoint auth (`/api/auth/login`, `/api/auth/refresh`) vẫn hoạt động bình thường

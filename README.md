# Java AKS Sample Application

Sample Spring Boot application for testing the DevSecOps CI/CD pipeline to Azure Kubernetes Service (AKS).

## Features

- **Spring Boot 3.2.1** with Java 17
- **REST API** with CRUD operations
- **Spring Boot Actuator** for health checks and metrics
- **Prometheus metrics** endpoint
- **Comprehensive unit and integration tests**
- **JaCoCo code coverage** (80% threshold)
- **Production-ready Dockerfile** with security best practices
- **Kubernetes manifests** with security hardening

## Project Structure

```
java-aks-sample-app/
├── src/
│   ├── main/
│   │   ├── java/com/example/javaapp/
│   │   │   ├── JavaAppApplication.java
│   │   │   ├── controller/
│   │   │   │   ├── HealthController.java
│   │   │   │   └── UserController.java
│   │   │   ├── model/
│   │   │   │   └── User.java
│   │   │   └── service/
│   │   │       └── UserService.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/com/example/javaapp/
│           ├── controller/
│           │   └── UserControllerTest.java
│           ├── service/
│           │   └── UserServiceTest.java
│           └── SmokeTest.java
├── k8s/
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── secret.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── ingress.yaml
│   ├── hpa.yaml
│   ├── pdb.yaml
│   └── networkpolicy.yaml
├── Dockerfile
├── pom.xml
└── README.md
```

## API Endpoints

### Health Endpoints
- `GET /api/health` - Custom health check
- `GET /api/info` - Application information
- `GET /actuator/health` - Spring Boot health check
- `GET /actuator/health/liveness` - Liveness probe
- `GET /actuator/health/readiness` - Readiness probe
- `GET /actuator/prometheus` - Prometheus metrics

### User API
- `GET /api/v1/users` - Get all users
- `GET /api/v1/users/{id}` - Get user by ID
- `POST /api/v1/users` - Create new user
- `PUT /api/v1/users/{id}` - Update user
- `DELETE /api/v1/users/{id}` - Delete user

## Local Development

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker (optional)

### Build and Run

```bash
# Build the application
mvn clean package

# Run tests
mvn test

# Run integration tests
mvn verify

# Generate code coverage report
mvn jacoco:report

# Run the application
java -jar target/java-app-1.0.0.jar

# Or using Maven
mvn spring-boot:run
```

The application will start on `http://localhost:8080`

### Test the API

```bash
# Health check
curl http://localhost:8080/api/health

# Get all users
curl http://localhost:8080/api/v1/users

# Create a user
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","email":"john@example.com","phone":"1234567890"}'
```

## Docker Build

### Build Docker Image

```bash
# Build the image
docker build -t java-app:latest .

# Run the container
docker run -p 8080:8080 java-app:latest

# Test the application
curl http://localhost:8080/api/health
```

### Security Features in Dockerfile
- Multi-stage build for minimal image size
- Non-root user execution
- Read-only root filesystem
- Health check included
- JVM container awareness
- Alpine-based minimal image

## Kubernetes Deployment

### Prerequisites
- AKS cluster
- kubectl configured
- Azure Container Registry (ACR)

### Deploy to Kubernetes

```bash
# Create namespace
kubectl apply -f k8s/namespace.yaml

# Create ConfigMap and Secret
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml

# Deploy application
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl apply -f k8s/ingress.yaml

# Deploy autoscaling and policies
kubectl apply -f k8s/hpa.yaml
kubectl apply -f k8s/pdb.yaml
kubectl apply -f k8s/networkpolicy.yaml

# Check deployment status
kubectl get pods -n production
kubectl rollout status deployment/java-app -n production
```

### Kubernetes Security Features
- Non-root container execution
- Read-only root filesystem
- Security context with seccomp profile
- Resource limits and requests
- Network policies for ingress/egress
- Pod Disruption Budget for high availability
- Horizontal Pod Autoscaler for scaling
- Pod anti-affinity for distribution

## CI/CD Pipeline

This application is designed to work with the GitHub Actions DevSecOps pipeline that includes:

### Security Scanning
- **SonarQube** - SAST (Static Application Security Testing)
- **OWASP Dependency Check** - Dependency vulnerability scanning
- **GitLeaks** - Secret scanning
- **Trivy** - Container vulnerability scanning
- **Grype** - Additional container scanning
- **Dockle** - Docker best practices
- **Kubesec** - Kubernetes security analysis
- **Kubescape** - K8s compliance scanning
- **Checkov** - IaC security scanning

### Quality Gates
- Code coverage ≥ 80%
- No CRITICAL/HIGH vulnerabilities
- SonarQube quality gate passed
- All tests passing

### Deployment Strategies
- **Staging**: Canary deployment (20% traffic)
- **Production**: Blue-green deployment with manual approval

## Code Coverage

The project uses JaCoCo for code coverage with a minimum threshold of 80%.

```bash
# Generate coverage report
mvn jacoco:report

# View report
open target/site/jacoco/index.html
```

## Testing

### Unit Tests
```bash
mvn test
```

### Integration Tests
```bash
mvn verify -DskipUnitTests
```

### Smoke Tests
```bash
mvn test -Dtest=SmokeTest
```

## Monitoring

### Prometheus Metrics
The application exposes Prometheus metrics at `/actuator/prometheus`

### Health Checks
- **Liveness**: `/actuator/health/liveness`
- **Readiness**: `/actuator/health/readiness`

### Logs
Application logs are written to stdout and can be viewed using:
```bash
kubectl logs -f deployment/java-app -n production
```

## Configuration

### Environment Variables
- `SPRING_PROFILES_ACTIVE` - Active Spring profile (default: production)
- `JAVA_OPTS` - JVM options
- `LOG_LEVEL` - Logging level

### ConfigMap
Application configuration is managed via Kubernetes ConfigMap (`k8s/configmap.yaml`)

### Secrets
Sensitive data is stored in Kubernetes Secrets (`k8s/secret.yaml`)
In production, use Azure Key Vault or External Secrets Operator.

## Performance

### Resource Requirements
- **Requests**: 250m CPU, 512Mi memory
- **Limits**: 500m CPU, 1Gi memory
- **Autoscaling**: 3-10 replicas based on CPU/memory

### JVM Tuning
- Container-aware JVM settings
- G1GC garbage collector
- 75% max RAM percentage
- String deduplication enabled

## Security

### Container Security
- Non-root user (UID 1000)
- Read-only root filesystem
- Dropped all capabilities
- Seccomp profile enabled

### Network Security
- Network policies restrict ingress/egress
- TLS termination at ingress
- Internal service communication only

### Image Security
- Signed with Cosign
- SBOM (Software Bill of Materials) generated
- Regular vulnerability scanning

## Troubleshooting

### Application not starting
```bash
# Check pod status
kubectl get pods -n production

# View pod logs
kubectl logs -n production <pod-name>

# Describe pod for events
kubectl describe pod -n production <pod-name>
```

### Health check failing
```bash
# Test health endpoint directly
kubectl exec -n production <pod-name> -- wget -O- http://localhost:8080/actuator/health
```

### Image pull errors
```bash
# Verify ACR secret
kubectl get secret acr-secret -n production

# Recreate secret if needed
kubectl create secret docker-registry acr-secret \
  --docker-server=<ACR_NAME>.azurecr.io \
  --docker-username=<USERNAME> \
  --docker-password=<PASSWORD> \
  --namespace=production
```

## License

This is a sample application for demonstration purposes.

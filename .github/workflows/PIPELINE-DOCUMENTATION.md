# Production-Grade Java to AKS DevSecOps CI/CD Pipeline Documentation

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Prerequisites](#prerequisites)
4. [Pipeline Stages](#pipeline-stages)
5. [Security Controls](#security-controls)
6. [Secrets Configuration](#secrets-configuration)
7. [Deployment Strategies](#deployment-strategies)
8. [Monitoring & Observability](#monitoring--observability)
9. [Troubleshooting](#troubleshooting)

---

## Overview

This GitHub Actions CI/CD pipeline implements a comprehensive DevSecOps workflow for deploying Java applications to Azure Kubernetes Service (AKS). The pipeline integrates multiple security scanning tools, automated testing, and progressive deployment strategies to ensure production-grade reliability and security.

### Key Features
- **Multi-stage security scanning** (SAST, DAST, SCA, container scanning)
- **Automated quality gates** with SonarQube
- **Container image signing** with Cosign
- **SBOM generation** for supply chain security
- **Blue-Green and Canary deployment** strategies
- **Automated rollback** capabilities
- **Comprehensive compliance** reporting

### Pipeline Flow
```
Code Push → Security Scans → Build → Container Scan → K8s Manifest Scan → 
Staging Deploy → Smoke Tests → Production Deploy → Post-Deployment Validation
```

---

## Architecture

### Pipeline Jobs Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    GitHub Actions Workflow                       │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Job 1: Code Security Scan                                        │
│ - SonarQube SAST                                                 │
│ - OWASP Dependency Check                                         │
│ - GitLeaks Secret Scan                                           │
│ - License Compliance                                             │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Job 2: Build & Unit Tests                                        │
│ - Maven Build                                                    │
│ - Unit Tests                                                     │
│ - Integration Tests                                              │
│ - Code Coverage (JaCoCo)                                         │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Job 3: Docker Build & Container Security                         │
│ - Docker Build                                                   │
│ - Trivy Vulnerability Scan                                       │
│ - Grype Vulnerability Scan                                       │
│ - Dockle Best Practices                                          │
│ - Cosign Image Signing                                           │
│ - SBOM Generation                                                │
│ - Push to ACR                                                    │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Job 4: K8s Manifest Security Scan                                │
│ - Kubesec Security Analysis                                      │
│ - Kubescape Posture Scan                                         │
│ - Checkov IaC Security                                           │
│ - Datree Policy Check                                            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Job 5: Deploy to Staging (Canary)                                │
│ - Azure OIDC Login                                               │
│ - AKS Context Setup                                              │
│ - Canary Deployment (20%)                                        │
│ - Falco Runtime Security                                         │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Job 6: Smoke Tests                                               │
│ - Health Check Validation                                        │
│ - Functional Smoke Tests                                         │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Job 7: Deploy to Production (Blue-Green)                         │
│ - Manual Approval Gate                                           │
│ - Blue-Green Deployment                                          │
│ - Traffic Switch                                                 │
│ - Health Validation                                              │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Job 8: Post-Deployment Validation                                │
│ - DAST with OWASP ZAP                                            │
│ - Performance Testing (K6)                                       │
│ - Slack Notifications                                            │
└─────────────────────────────────────────────────────────────────┘
```

---

## Prerequisites

### 1. Azure Resources
- **Azure Subscription** with appropriate permissions
- **AKS Cluster** (production-grade configuration)
- **Azure Container Registry (ACR)** with admin access enabled
- **Azure AD App Registration** for OIDC authentication
- **Resource Group** for AKS resources

### 2. GitHub Configuration
- **GitHub Repository** with Actions enabled
- **Branch Protection Rules** on main/develop branches
- **Environment Secrets** configured (see Secrets Configuration section)
- **GitHub Environments** created: `staging`, `production`

### 3. External Services
- **SonarQube Server** (self-hosted or SonarCloud)
- **Slack Workspace** (for notifications)
- **Datree Account** (optional, for K8s policy enforcement)

### 4. Local Development Tools
- Java 17+
- Maven 3.8+
- Docker
- kubectl
- Azure CLI

---

## Pipeline Stages

### Stage 1: Code Security Scan (Job 1)

**Purpose**: Identify security vulnerabilities and code quality issues before building artifacts.

#### 1.1 SonarQube SAST (Static Application Security Testing)

**What it does**:
- Analyzes source code for security vulnerabilities, bugs, and code smells
- Enforces quality gates (code coverage, duplication, complexity)
- Tracks technical debt and maintainability

**Configuration**:
```yaml
- name: SAST - SonarQube Scan
  env:
    SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
  run: |
    mvn clean verify sonar:sonar \
      -Dsonar.projectKey=${{ env.APP_NAME }} \
      -Dsonar.qualitygate.wait=true
```

**Key Parameters**:
- `sonar.qualitygate.wait=true`: Blocks pipeline if quality gate fails
- `sonar.coverage.jacoco.xmlReportPaths`: Path to JaCoCo coverage report
- `sonar.java.binaries`: Compiled class files location

**Quality Gate Criteria** (Recommended):
- Code Coverage: ≥ 80%
- Duplicated Lines: ≤ 3%
- Maintainability Rating: A
- Security Rating: A
- Reliability Rating: A

**Failure Handling**: Pipeline fails if quality gate is not met.

---

#### 1.2 OWASP Dependency Check

**What it does**:
- Scans project dependencies for known vulnerabilities (CVEs)
- Checks against National Vulnerability Database (NVD)
- Generates comprehensive vulnerability reports

**Configuration**:
```yaml
- name: OWASP Dependency Check
  uses: dependency-check/Dependency-Check_Action@main
  with:
    project: ${{ env.APP_NAME }}
    format: 'ALL'
    args: >
      --enableRetired
      --enableExperimental
      --failOnCVSS 7
```

**Key Parameters**:
- `--failOnCVSS 7`: Fails build if CVSS score ≥ 7 (HIGH/CRITICAL)
- `--enableRetired`: Includes retired CVEs
- `format: 'ALL'`: Generates HTML, JSON, XML, CSV reports

**Output**: Reports uploaded to GitHub Artifacts for review.

---

#### 1.3 GitLeaks Secret Scan

**What it does**:
- Scans entire git history for exposed secrets
- Detects API keys, passwords, tokens, private keys
- Prevents credential leakage

**Configuration**:
```yaml
- name: GitLeaks Secret Scan
  uses: gitleaks/gitleaks-action@v2
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

**Detection Patterns**:
- AWS Access Keys
- Azure Service Principal credentials
- Database connection strings
- Private SSH keys
- Generic API tokens

**Failure Handling**: Pipeline fails immediately if secrets detected.

---

#### 1.4 License Compliance Check

**What it does**:
- Identifies all third-party library licenses
- Ensures compliance with organizational policies
- Generates license attribution reports

**Configuration**:
```yaml
- name: License Compliance Check
  run: |
    mvn license:add-third-party
    mvn license:aggregate-download-licenses
```

**Output**: License reports uploaded for legal review.

---

### Stage 2: Build & Unit Tests (Job 2)

**Purpose**: Compile application and validate functionality through automated tests.

#### 2.1 Maven Build

**What it does**:
- Compiles Java source code
- Resolves and downloads dependencies
- Packages application as JAR/WAR

**Configuration**:
```yaml
- name: Build with Maven
  run: |
    mvn clean package -DskipTests
```

**Build Optimization**:
- Maven dependency caching enabled via `cache: 'maven'`
- Parallel builds: `-T 1C` (one thread per CPU core)
- Offline mode for faster builds: `-o` (when dependencies cached)

---

#### 2.2 Unit Tests

**What it does**:
- Executes JUnit/TestNG unit tests
- Validates individual component behavior
- Generates test reports

**Configuration**:
```yaml
- name: Run Unit Tests
  run: |
    mvn test
```

**Test Execution**:
- Surefire plugin for unit tests
- Parallel test execution enabled
- Test results in JUnit XML format

---

#### 2.3 Integration Tests

**What it does**:
- Tests component interactions
- Validates database operations, API calls
- Uses test containers for dependencies

**Configuration**:
```yaml
- name: Run Integration Tests
  run: |
    mvn verify -DskipUnitTests
```

**Test Infrastructure**:
- Failsafe plugin for integration tests
- Testcontainers for databases, message queues
- Separate test phase from unit tests

---

#### 2.4 Code Coverage (JaCoCo)

**What it does**:
- Measures test coverage (line, branch, method)
- Generates HTML and XML reports
- Enforces minimum coverage thresholds

**Configuration**:
```yaml
- name: Generate Code Coverage Report
  run: |
    mvn jacoco:report
```

**Coverage Thresholds** (pom.xml):
```xml
<configuration>
  <rules>
    <rule>
      <element>BUNDLE</element>
      <limits>
        <limit>
          <counter>LINE</counter>
          <value>COVEREDRATIO</value>
          <minimum>0.80</minimum>
        </limit>
      </limits>
    </rule>
  </rules>
</configuration>
```

---

### Stage 3: Docker Build & Container Security (Job 3)

**Purpose**: Build container image and scan for vulnerabilities before pushing to registry.

#### 3.1 Docker Image Build

**What it does**:
- Builds optimized container image
- Implements multi-stage builds
- Applies security best practices

**Configuration**:
```yaml
- name: Build Docker Image
  uses: docker/build-push-action@v5
  with:
    context: .
    file: ./Dockerfile
    push: false
    load: true
    cache-from: type=gha
    cache-to: type=gha,mode=max
```

**Recommended Dockerfile**:
```dockerfile
# Multi-stage build for minimal image size
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage with minimal base image
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

**Security Best Practices**:
- Non-root user execution
- Minimal base image (Alpine)
- No unnecessary packages
- Health check included
- JVM container awareness

---

#### 3.2 Trivy Vulnerability Scanner

**What it does**:
- Scans container images for OS and application vulnerabilities
- Checks against multiple vulnerability databases
- Detects misconfigurations

**Configuration**:
```yaml
- name: Run Trivy Vulnerability Scanner
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: ${{ env.ACR_NAME }}.azurecr.io/${{ env.APP_NAME }}:${{ github.sha }}
    format: 'sarif'
    severity: 'CRITICAL,HIGH,MEDIUM'
    exit-code: '1'
    ignore-unfixed: true
```

**Scan Coverage**:
- OS packages (Alpine, Debian, Ubuntu, etc.)
- Application dependencies (JAR files)
- Configuration files
- Secrets in image layers

**Severity Levels**:
- **CRITICAL**: Immediate fix required
- **HIGH**: Fix before production
- **MEDIUM**: Fix in next release
- **LOW**: Informational

**Failure Handling**: Pipeline fails if CRITICAL/HIGH vulnerabilities found.

---

#### 3.3 Grype Vulnerability Scanner

**What it does**:
- Secondary vulnerability scanner for validation
- Uses Anchore vulnerability database
- Provides additional CVE coverage

**Configuration**:
```yaml
- name: Run Grype Vulnerability Scanner
  uses: anchore/scan-action@v3
  with:
    image: ${{ env.ACR_NAME }}.azurecr.io/${{ env.APP_NAME }}:${{ github.sha }}
    fail-build: true
    severity-cutoff: high
```

**Why Two Scanners?**:
- Different vulnerability databases
- Reduces false negatives
- Industry best practice (defense in depth)

---

#### 3.4 Dockle Security Linter

**What it does**:
- Validates Docker best practices
- Checks CIS Docker Benchmark compliance
- Identifies security misconfigurations

**Configuration**:
```yaml
- name: Run Dockle Security Linter
  uses: erzz/dockle-action@v1
  with:
    image: ${{ env.ACR_NAME }}.azurecr.io/${{ env.APP_NAME }}:${{ github.sha }}
    exit-code: '1'
    exit-level: WARN
```

**Checks Performed**:
- ✅ Non-root user
- ✅ No sensitive files
- ✅ Minimal layers
- ✅ Health check present
- ✅ No hardcoded secrets
- ✅ Proper labels

---

#### 3.5 Cosign Image Signing

**What it does**:
- Cryptographically signs container images
- Enables image verification in Kubernetes
- Prevents tampering and unauthorized images

**Configuration**:
```yaml
- name: Sign Container Image
  env:
    COSIGN_PASSWORD: ${{ secrets.COSIGN_PASSWORD }}
    COSIGN_PRIVATE_KEY: ${{ secrets.COSIGN_PRIVATE_KEY }}
  run: |
    cosign sign --key env://COSIGN_PRIVATE_KEY \
      ${{ env.ACR_NAME }}.azurecr.io/${{ env.APP_NAME }}:${{ github.sha }}
```

**Key Generation** (One-time setup):
```bash
# Generate key pair
cosign generate-key-pair

# Store in GitHub Secrets:
# - COSIGN_PRIVATE_KEY: cosign.key contents
# - COSIGN_PASSWORD: password used during generation
# - COSIGN_PUBLIC_KEY: cosign.pub contents (for verification)
```

**Verification in Kubernetes**:
```yaml
# Using Kyverno policy
apiVersion: kyverno.io/v1
kind: ClusterPolicy
metadata:
  name: verify-image-signature
spec:
  validationFailureAction: enforce
  rules:
  - name: verify-signature
    match:
      resources:
        kinds:
        - Pod
    verifyImages:
    - imageReferences:
      - "acrprodregistry.azurecr.io/*"
      attestors:
      - count: 1
        entries:
        - keys:
            publicKeys: |-
              -----BEGIN PUBLIC KEY-----
              <COSIGN_PUBLIC_KEY>
              -----END PUBLIC KEY-----
```

---

#### 3.6 SBOM Generation (Software Bill of Materials)

**What it does**:
- Creates inventory of all software components
- Tracks dependencies and versions
- Enables supply chain security

**Configuration**:
```yaml
- name: Generate SBOM
  uses: anchore/sbom-action@v0
  with:
    image: ${{ env.ACR_NAME }}.azurecr.io/${{ env.APP_NAME }}:${{ github.sha }}
    format: spdx-json
    output-file: sbom.spdx.json
```

**SBOM Format**: SPDX (Software Package Data Exchange) - industry standard

**Use Cases**:
- Vulnerability tracking
- License compliance
- Supply chain risk management
- Regulatory compliance (Executive Order 14028)

---

#### 3.7 Push to Azure Container Registry

**What it does**:
- Uploads signed and scanned image to ACR
- Applies metadata tags
- Enables image pull for AKS

**Configuration**:
```yaml
- name: Push Docker Image to ACR
  uses: docker/build-push-action@v5
  with:
    push: true
    tags: ${{ steps.meta.outputs.tags }}
```

**Image Tagging Strategy**:
- `latest`: Latest build from main branch
- `<branch-name>`: Branch-specific tag
- `<git-sha>`: Immutable commit-based tag
- `<semver>`: Semantic version (v1.2.3)

---

### Stage 4: Kubernetes Manifest Security Scan (Job 4)

**Purpose**: Validate Kubernetes configurations against security best practices and compliance frameworks.

#### 4.1 Kubesec Security Analysis

**What it does**:
- Analyzes K8s YAML for security risks
- Provides risk scores and recommendations
- Checks for privilege escalation vectors

**Configuration**:
```yaml
- name: Run Kubesec Scanner
  uses: controlplaneio/kubesec-action@v0.0.2
  with:
    input: k8s/deployment.yaml
    format: sarif
```

**Security Checks**:
- ✅ Security context defined
- ✅ Read-only root filesystem
- ✅ No privileged containers
- ✅ Resource limits set
- ✅ Non-root user
- ✅ Capabilities dropped

**Risk Scoring**:
- **Score > 0**: Passed
- **Score = 0**: Neutral
- **Score < 0**: Failed (security issues)

---

#### 4.2 Kubescape Security Posture

**What it does**:
- Scans against NSA/CISA Kubernetes Hardening Guide
- MITRE ATT&CK framework mapping
- Compliance reporting (PCI-DSS, SOC2, GDPR)

**Configuration**:
```yaml
- name: Run Kubescape Scanner
  uses: kubescape/github-action@main
  with:
    files: "k8s/*.yaml"
    frameworks: |
      nsa,mitre,armobest
    failedThreshold: 70
    severityThreshold: medium
```

**Frameworks Checked**:
- **NSA**: National Security Agency hardening guidelines
- **MITRE**: ATT&CK techniques for Kubernetes
- **ArmoBest**: Kubernetes best practices

**Compliance Threshold**: Pipeline fails if score < 70%

---

#### 4.3 Checkov IaC Security Scanner

**What it does**:
- Policy-as-code security scanning
- 1000+ built-in policies
- Custom policy support

**Configuration**:
```yaml
- name: Run Checkov Scanner
  uses: bridgecrewio/checkov-action@master
  with:
    directory: k8s/
    framework: kubernetes
    soft_fail: false
```

**Policy Categories**:
- Security (privilege escalation, secrets)
- Compliance (CIS benchmarks)
- Best practices (resource limits, labels)
- Network policies

---

#### 4.4 Datree Policy Enforcement

**What it does**:
- Centralized policy management
- Custom rule creation
- Prevents misconfigurations

**Configuration**:
```yaml
- name: Run Datree Policy Check
  uses: datreeio/action-datree@main
  with:
    path: k8s/*.yaml
  env:
    DATREE_TOKEN: ${{ secrets.DATREE_TOKEN }}
```

**Example Policies**:
- Ensure CPU/memory limits
- Require liveness/readiness probes
- Enforce label standards
- Prevent latest image tag

---

### Stage 5: Deploy to AKS Staging (Job 5)

**Purpose**: Deploy to staging environment using canary strategy for gradual rollout.

#### 5.1 Azure OIDC Authentication

**What it does**:
- Passwordless authentication to Azure
- Uses federated identity credentials
- More secure than service principal secrets

**Configuration**:
```yaml
- name: Azure Login (OIDC)
  uses: azure/login@v1
  with:
    client-id: ${{ secrets.AZURE_CLIENT_ID }}
    tenant-id: ${{ secrets.AZURE_TENANT_ID }}
    subscription-id: ${{ secrets.AZURE_SUBSCRIPTION_ID }}
```

**Setup Steps**:
1. Create Azure AD App Registration
2. Configure federated credentials for GitHub
3. Assign RBAC roles (AKS Cluster User, ACR Pull)
4. Store IDs in GitHub Secrets

**Benefits**:
- No secret rotation required
- Short-lived tokens
- Audit trail in Azure AD

---

#### 5.2 Canary Deployment Strategy

**What it does**:
- Gradually shifts traffic to new version
- Monitors metrics during rollout
- Enables quick rollback

**Configuration**:
```yaml
- name: Deploy to AKS Staging
  uses: azure/k8s-deploy@v4
  with:
    namespace: staging
    strategy: canary
    percentage: 20
    traffic-split-method: pod
```

**Canary Rollout**:
1. Deploy 20% of pods with new version
2. Monitor for 5-10 minutes
3. If healthy, increase to 50%
4. If healthy, increase to 100%
5. If issues, rollback to stable version

**Traffic Split Methods**:
- **Pod**: Based on pod count (20% of pods)
- **SMI**: Service Mesh Interface (requires Istio/Linkerd)

---

#### 5.3 Kubernetes Manifests

**Required Manifests**:

**namespace.yaml**:
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: staging
  labels:
    environment: staging
    managed-by: github-actions
```

**deployment.yaml**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: java-app
  namespace: staging
spec:
  replicas: 3
  selector:
    matchLabels:
      app: java-app
  template:
    metadata:
      labels:
        app: java-app
        version: v1
    spec:
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
        seccompProfile:
          type: RuntimeDefault
      containers:
      - name: java-app
        image: acrprodregistry.azurecr.io/java-app:latest
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
          protocol: TCP
        securityContext:
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: true
          runAsNonRoot: true
          capabilities:
            drop:
            - ALL
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
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
        env:
        - name: JAVA_OPTS
          value: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
        - name: SPRING_PROFILES_ACTIVE
          value: "staging"
        volumeMounts:
        - name: tmp
          mountPath: /tmp
        - name: cache
          mountPath: /app/cache
      volumes:
      - name: tmp
        emptyDir: {}
      - name: cache
        emptyDir: {}
      imagePullSecrets:
      - name: acr-secret
```

**service.yaml**:
```yaml
apiVersion: v1
kind: Service
metadata:
  name: java-app
  namespace: staging
spec:
  type: ClusterIP
  selector:
    app: java-app
  ports:
  - port: 80
    targetPort: 8080
    protocol: TCP
```

**ingress.yaml**:
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: java-app
  namespace: staging
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"
spec:
  ingressClassName: nginx
  tls:
  - hosts:
    - staging.company.com
    secretName: java-app-tls
  rules:
  - host: staging.company.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: java-app
            port:
              number: 80
```

**hpa.yaml** (Horizontal Pod Autoscaler):
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: java-app
  namespace: staging
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: java-app
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
      - type: Percent
        value: 100
        periodSeconds: 30
      - type: Pods
        value: 2
        periodSeconds: 30
      selectPolicy: Max
```

**pdb.yaml** (Pod Disruption Budget):
```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: java-app
  namespace: staging
spec:
  minAvailable: 2
  selector:
    matchLabels:
      app: java-app
```

**networkpolicy.yaml**:
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: java-app
  namespace: staging
spec:
  podSelector:
    matchLabels:
      app: java-app
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - namespaceSelector: {}
    ports:
    - protocol: TCP
      port: 443
    - protocol: TCP
      port: 5432  # PostgreSQL
  - to:
    - namespaceSelector:
        matchLabels:
          name: kube-system
    ports:
    - protocol: UDP
      port: 53  # DNS
```

---

#### 5.4 Falco Runtime Security

**What it does**:
- Monitors runtime behavior
- Detects anomalous activity
- Alerts on security violations

**Configuration**:
```yaml
- name: Run Falco Runtime Security Check
  run: |
    kubectl apply -f https://raw.githubusercontent.com/falcosecurity/falco/master/examples/k8s-using-daemonset/falco-daemonset-configmap.yaml
    sleep 30
    kubectl logs -n falco -l app=falco --tail=100
```

**Detection Rules**:
- Unexpected network connections
- File system modifications
- Privilege escalation attempts
- Shell execution in containers
- Sensitive file access

---

### Stage 6: Smoke Tests (Job 6)

**Purpose**: Validate basic functionality in staging environment.

#### 6.1 Health Check Validation

**What it does**:
- Verifies application is responding
- Checks all health indicators
- Validates dependencies

**Configuration**:
```yaml
- name: Run Health Check
  run: |
    curl -f https://staging.company.com/actuator/health || exit 1
```

**Spring Boot Actuator Health**:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

---

#### 6.2 Functional Smoke Tests

**What it does**:
- Tests critical user journeys
- Validates API endpoints
- Checks data persistence

**Example Test**:
```java
@Test
@Tag("smoke")
public class SmokeTest {
    
    @Test
    void testApplicationHealth() {
        given()
            .when()
            .get("/actuator/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"));
    }
    
    @Test
    void testCriticalEndpoint() {
        given()
            .contentType("application/json")
            .when()
            .get("/api/v1/users")
            .then()
            .statusCode(200);
    }
}
```

---

### Stage 7: Deploy to Production (Job 7)

**Purpose**: Deploy to production using blue-green strategy with manual approval.

#### 7.1 Manual Approval Gate

**What it does**:
- Requires human approval before production deployment
- Provides deployment details for review
- Enables rollback decision

**Configuration**:
```yaml
environment:
  name: production
  url: https://app.company.com
```

**GitHub Environment Protection**:
1. Navigate to Settings → Environments → production
2. Enable "Required reviewers"
3. Add authorized approvers
4. Set wait timer (optional)

---

#### 7.2 Blue-Green Deployment Strategy

**What it does**:
- Deploys new version alongside old version
- Switches traffic instantly
- Enables instant rollback

**Configuration**:
```yaml
- name: Blue-Green Deployment to Production
  uses: azure/k8s-deploy@v4
  with:
    strategy: blue-green
    traffic-split-method: smi
    route-method: service
```

**Deployment Flow**:
1. **Green Deployment**: Deploy new version (green)
2. **Validation**: Run health checks on green
3. **Traffic Switch**: Route traffic from blue to green
4. **Monitor**: Watch metrics for 10 minutes
5. **Cleanup**: Remove old blue deployment

**Service Mesh Integration** (Istio):
```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: java-app
spec:
  hosts:
  - java-app
  http:
  - match:
    - headers:
        version:
          exact: green
    route:
    - destination:
        host: java-app
        subset: green
  - route:
    - destination:
        host: java-app
        subset: blue
      weight: 0
    - destination:
        host: java-app
        subset: green
      weight: 100
```

---

#### 7.3 Production Health Validation

**What it does**:
- Validates production deployment
- Retries health checks
- Fails deployment if unhealthy

**Configuration**:
```yaml
- name: Run Production Health Checks
  run: |
    for i in {1..10}; do
      if curl -f https://app.company.com/actuator/health; then
        echo "Health check passed"
        exit 0
      fi
      echo "Attempt $i failed, retrying..."
      sleep 10
    done
    exit 1
```

**Health Check Criteria**:
- HTTP 200 response
- All components UP
- Response time < 3 seconds
- No error logs

---

### Stage 8: Post-Deployment Validation (Job 8)

**Purpose**: Comprehensive validation and monitoring after production deployment.

#### 8.1 DAST - Dynamic Application Security Testing

**What it does**:
- Tests running application for vulnerabilities
- Simulates real-world attacks
- Identifies runtime security issues

**Configuration**:
```yaml
- name: DAST - Dynamic Application Security Testing
  uses: zaproxy/action-full-scan@v0.7.0
  with:
    target: 'https://app.company.com'
    rules_file_name: '.zap/rules.tsv'
```

**OWASP ZAP Checks**:
- SQL Injection
- Cross-Site Scripting (XSS)
- CSRF vulnerabilities
- Security headers
- SSL/TLS configuration
- Authentication/Authorization flaws

**Custom Rules** (.zap/rules.tsv):
```
10010	IGNORE	(SQL Injection - false positive on /api/search)
10020	WARN	(XSS - known issue, tracked in JIRA-123)
```

---

#### 8.2 Performance Testing with K6

**What it does**:
- Load testing
- Performance regression detection
- Capacity validation

**Configuration**:
```yaml
- name: Performance Testing with K6
  run: |
    docker run --rm -i grafana/k6 run - <tests/load-test.js
```

**Example K6 Test** (tests/load-test.js):
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '2m', target: 100 },  // Ramp up
    { duration: '5m', target: 100 },  // Stay at 100 users
    { duration: '2m', target: 200 },  // Ramp to 200 users
    { duration: '5m', target: 200 },  // Stay at 200 users
    { duration: '2m', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // 95% of requests < 500ms
    http_req_failed: ['rate<0.01'],    // Error rate < 1%
  },
};

export default function () {
  const res = http.get('https://app.company.com/api/v1/users');
  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });
  sleep(1);
}
```

---

#### 8.3 Notifications

**What it does**:
- Notifies team of deployment status
- Provides deployment details
- Enables quick response to issues

**Slack Notification**:
```yaml
- name: Notify Deployment Success
  uses: 8398a7/action-slack@v3
  if: success()
  with:
    status: custom
    custom_payload: |
      {
        text: "✅ Production Deployment Successful",
        attachments: [{
          color: 'good',
          text: `Deployed ${{ env.APP_NAME }}:${{ github.sha }} to production`
        }]
      }
  env:
    SLACK_WEBHOOK_URL: ${{ secrets.SLACK_WEBHOOK }}
```

---

## Security Controls

### Defense in Depth Layers

```
┌─────────────────────────────────────────────────────────────┐
│ Layer 1: Code Security                                       │
│ - SonarQube SAST                                             │
│ - GitLeaks Secret Scanning                                   │
│ - OWASP Dependency Check                                     │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Layer 2: Container Security                                  │
│ - Trivy Vulnerability Scan                                   │
│ - Grype Vulnerability Scan                                   │
│ - Dockle Best Practices                                      │
│ - Cosign Image Signing                                       │
│ - SBOM Generation                                            │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Layer 3: Kubernetes Security                                 │
│ - Kubesec Security Analysis                                  │
│ - Kubescape Compliance                                       │
│ - Checkov Policy Enforcement                                 │
│ - Network Policies                                           │
│ - Pod Security Standards                                     │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ Layer 4: Runtime Security                                    │
│ - Falco Runtime Monitoring                                   │
│ - OWASP ZAP DAST                                             │
│ - Azure Defender for Containers                              │
└─────────────────────────────────────────────────────────────┘
```

### Security Checklist

#### Code Level
- [x] Static code analysis (SonarQube)
- [x] Dependency vulnerability scanning (OWASP)
- [x] Secret detection (GitLeaks)
- [x] License compliance
- [x] Code coverage ≥ 80%

#### Container Level
- [x] Multi-stage builds
- [x] Minimal base images
- [x] Non-root user
- [x] Read-only filesystem
- [x] Vulnerability scanning (Trivy, Grype)
- [x] Image signing (Cosign)
- [x] SBOM generation

#### Kubernetes Level
- [x] Security context enforced
- [x] Resource limits defined
- [x] Network policies applied
- [x] Pod Security Standards
- [x] RBAC configured
- [x] Secrets encrypted at rest
- [x] Image pull policies

#### Runtime Level
- [x] Runtime monitoring (Falco)
- [x] DAST scanning (ZAP)
- [x] Performance testing
- [x] Health monitoring
- [x] Audit logging

---

## Secrets Configuration

### Required GitHub Secrets

#### Azure Authentication (OIDC)
```
AZURE_CLIENT_ID          # Azure AD App Registration Client ID
AZURE_TENANT_ID          # Azure AD Tenant ID
AZURE_SUBSCRIPTION_ID    # Azure Subscription ID
```

#### Azure Container Registry
```
ACR_USERNAME             # ACR admin username
ACR_PASSWORD             # ACR admin password
```

#### SonarQube
```
SONAR_TOKEN              # SonarQube authentication token
```

#### Image Signing
```
COSIGN_PRIVATE_KEY       # Cosign private key (base64 encoded)
COSIGN_PASSWORD          # Cosign key password
COSIGN_PUBLIC_KEY        # Cosign public key (for verification)
```

#### External Services
```
DATREE_TOKEN             # Datree policy enforcement token
GITLEAKS_LICENSE         # GitLeaks license key (if using pro)
SLACK_WEBHOOK            # Slack webhook URL for notifications
```

### Setting Up Secrets

**GitHub UI**:
1. Navigate to Repository → Settings → Secrets and variables → Actions
2. Click "New repository secret"
3. Add each secret with exact name and value

**GitHub CLI**:
```bash
gh secret set AZURE_CLIENT_ID --body "your-client-id"
gh secret set AZURE_TENANT_ID --body "your-tenant-id"
gh secret set ACR_USERNAME --body "your-acr-username"
```

### Azure OIDC Setup

**Step 1: Create App Registration**
```bash
az ad app create --display-name "GitHub-Actions-OIDC"
```

**Step 2: Create Service Principal**
```bash
az ad sp create --id <APP_ID>
```

**Step 3: Configure Federated Credentials**
```bash
az ad app federated-credential create \
  --id <APP_ID> \
  --parameters '{
    "name": "github-actions",
    "issuer": "https://token.actions.githubusercontent.com",
    "subject": "repo:your-org/your-repo:ref:refs/heads/main",
    "audiences": ["api://AzureADTokenExchange"]
  }'
```

**Step 4: Assign RBAC Roles**
```bash
# AKS Cluster User Role
az role assignment create \
  --assignee <SERVICE_PRINCIPAL_ID> \
  --role "Azure Kubernetes Service Cluster User Role" \
  --scope /subscriptions/<SUBSCRIPTION_ID>/resourceGroups/<RG>/providers/Microsoft.ContainerService/managedClusters/<AKS_CLUSTER>

# ACR Pull Role
az role assignment create \
  --assignee <SERVICE_PRINCIPAL_ID> \
  --role "AcrPull" \
  --scope /subscriptions/<SUBSCRIPTION_ID>/resourceGroups/<RG>/providers/Microsoft.ContainerRegistry/registries/<ACR_NAME>
```

---

## Deployment Strategies

### Canary Deployment (Staging)

**Use Case**: Gradual rollout with traffic shifting

**Advantages**:
- Low risk
- Gradual validation
- Easy rollback

**Disadvantages**:
- Longer deployment time
- Requires traffic management

**Implementation**:
```yaml
strategy: canary
percentage: 20
traffic-split-method: pod
```

**Rollout Phases**:
1. 20% traffic → Monitor 5 min
2. 50% traffic → Monitor 5 min
3. 100% traffic → Full rollout

---

### Blue-Green Deployment (Production)

**Use Case**: Zero-downtime deployment with instant rollback

**Advantages**:
- Instant traffic switch
- Easy rollback
- Full validation before switch

**Disadvantages**:
- Requires 2x resources temporarily
- Database migrations complex

**Implementation**:
```yaml
strategy: blue-green
traffic-split-method: smi
route-method: service
```

**Deployment Flow**:
```
Blue (v1.0) ──────► 100% traffic
                    ↓
Green (v1.1) ─────► Deploy & Test
                    ↓
                  Validation
                    ↓
Blue (v1.0) ──────► 0% traffic
Green (v1.1) ─────► 100% traffic
                    ↓
                  Monitor
                    ↓
              Cleanup Blue
```

---

### Rollback Procedures

**Automatic Rollback Triggers**:
- Health check failures
- Error rate > 5%
- Response time > 2x baseline
- Pod crash loop

**Manual Rollback**:
```bash
# Rollback to previous deployment
kubectl rollout undo deployment/java-app -n production

# Rollback to specific revision
kubectl rollout undo deployment/java-app -n production --to-revision=3

# Check rollout history
kubectl rollout history deployment/java-app -n production
```

**Blue-Green Rollback**:
```bash
# Switch traffic back to blue
kubectl patch service java-app -n production \
  -p '{"spec":{"selector":{"version":"blue"}}}'
```

---

## Monitoring & Observability

### Metrics to Monitor

#### Application Metrics
- Request rate (requests/second)
- Error rate (%)
- Response time (p50, p95, p99)
- Throughput (MB/s)

#### Infrastructure Metrics
- CPU utilization (%)
- Memory utilization (%)
- Pod count
- Network I/O

#### Business Metrics
- Active users
- Transactions/minute
- Conversion rate

### Monitoring Stack

**Azure Monitor**:
```bash
# Enable Container Insights
az aks enable-addons \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_CLUSTER \
  --addons monitoring
```

**Prometheus + Grafana**:
```yaml
# Install Prometheus Operator
helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace
```

**Application Insights**:
```xml
<!-- pom.xml -->
<dependency>
  <groupId>com.microsoft.azure</groupId>
  <artifactId>applicationinsights-spring-boot-starter</artifactId>
  <version>3.4.0</version>
</dependency>
```

### Alerting Rules

**Critical Alerts**:
- Pod crash loop
- High error rate (> 5%)
- Memory OOM
- Deployment failure

**Warning Alerts**:
- High CPU (> 80%)
- High memory (> 85%)
- Slow response time (> 1s)
- Low pod count

---

## Troubleshooting

### Common Issues

#### Issue 1: SonarQube Quality Gate Failure

**Symptoms**:
```
Quality gate failed: Coverage is 65.0%, required minimum is 80.0%
```

**Solution**:
1. Review coverage report: `target/site/jacoco/index.html`
2. Add missing unit tests
3. Exclude generated code from coverage
4. Update quality gate threshold (if justified)

---

#### Issue 2: Container Vulnerability Scan Failure

**Symptoms**:
```
Trivy found 5 CRITICAL vulnerabilities
```

**Solution**:
1. Review Trivy report for CVE details
2. Update base image: `FROM eclipse-temurin:17-jre-alpine`
3. Update vulnerable dependencies in `pom.xml`
4. If no fix available, add exception with justification

---

#### Issue 3: Kubernetes Deployment Timeout

**Symptoms**:
```
Error: deployment "java-app" exceeded its progress deadline
```

**Solution**:
```bash
# Check pod status
kubectl get pods -n production -l app=java-app

# Check pod logs
kubectl logs -n production <pod-name>

# Check events
kubectl describe pod -n production <pod-name>

# Common causes:
# - Image pull errors (check imagePullSecrets)
# - Resource limits too low
# - Liveness probe failing
# - Init container issues
```

---

#### Issue 4: Image Pull Error

**Symptoms**:
```
Failed to pull image: unauthorized: authentication required
```

**Solution**:
```bash
# Recreate ACR secret
kubectl create secret docker-registry acr-secret \
  --docker-server=$ACR_NAME.azurecr.io \
  --docker-username=$ACR_USERNAME \
  --docker-password=$ACR_PASSWORD \
  --namespace=production \
  --dry-run=client -o yaml | kubectl apply -f -

# Verify secret
kubectl get secret acr-secret -n production -o yaml
```

---

#### Issue 5: Health Check Failures

**Symptoms**:
```
Liveness probe failed: HTTP probe failed with statuscode: 503
```

**Solution**:
1. Increase `initialDelaySeconds` (application startup time)
2. Check application logs for errors
3. Verify health endpoint: `/actuator/health`
4. Check resource limits (CPU/memory)

---

### Debug Commands

```bash
# View pipeline logs
gh run list
gh run view <run-id> --log

# Check AKS cluster
az aks show --resource-group $RG --name $AKS_CLUSTER

# Get kubeconfig
az aks get-credentials --resource-group $RG --name $AKS_CLUSTER

# Check deployment status
kubectl rollout status deployment/java-app -n production

# View pod logs
kubectl logs -f deployment/java-app -n production

# Execute into pod
kubectl exec -it <pod-name> -n production -- /bin/sh

# Check resource usage
kubectl top pods -n production
kubectl top nodes

# View events
kubectl get events -n production --sort-by='.lastTimestamp'

# Check network policies
kubectl get networkpolicies -n production
kubectl describe networkpolicy java-app -n production
```

---

## Best Practices

### Security Best Practices
1. **Least Privilege**: Use minimal RBAC permissions
2. **Secret Management**: Rotate secrets regularly
3. **Image Scanning**: Scan all images before deployment
4. **Network Segmentation**: Use network policies
5. **Audit Logging**: Enable all audit logs
6. **Encryption**: Encrypt secrets at rest and in transit

### Performance Best Practices
1. **Resource Limits**: Always set CPU/memory limits
2. **Horizontal Scaling**: Use HPA for auto-scaling
3. **Caching**: Implement application-level caching
4. **Connection Pooling**: Configure database connection pools
5. **Health Checks**: Implement proper liveness/readiness probes

### Operational Best Practices
1. **GitOps**: All changes via Git
2. **Immutable Infrastructure**: Never modify running containers
3. **Observability**: Comprehensive logging and monitoring
4. **Disaster Recovery**: Regular backups and DR drills
5. **Documentation**: Keep runbooks updated

---

## Compliance & Governance

### Compliance Frameworks

**CIS Kubernetes Benchmark**:
- Implemented via Kubescape scanning
- Automated compliance reporting
- Continuous monitoring

**OWASP Top 10**:
- Addressed via SAST/DAST scanning
- Dependency vulnerability management
- Security headers enforcement

**SOC 2 Type II**:
- Audit logging enabled
- Access controls enforced
- Change management via Git

### Audit Trail

**GitHub Actions**:
- All workflow runs logged
- Approval history tracked
- Artifact retention (90 days)

**Kubernetes**:
```bash
# Enable audit logging
az aks update \
  --resource-group $RG \
  --name $AKS_CLUSTER \
  --enable-azure-rbac \
  --enable-managed-identity
```

**Azure Activity Log**:
- All Azure operations logged
- 90-day retention
- Export to Log Analytics

---

## Maintenance

### Regular Tasks

**Weekly**:
- Review security scan results
- Update dependencies
- Check resource utilization
- Review error logs

**Monthly**:
- Rotate secrets
- Update base images
- Review and update policies
- Capacity planning

**Quarterly**:
- Disaster recovery drill
- Security audit
- Performance baseline review
- Cost optimization review

### Pipeline Updates

**Updating Pipeline**:
1. Create feature branch
2. Modify `.github/workflows/java-aks-devsecops-pipeline.yml`
3. Test in staging environment
4. Create pull request
5. Review and merge

**Version Pinning**:
```yaml
# Pin action versions for stability
uses: actions/checkout@v4  # Not @main
uses: docker/build-push-action@v5  # Not @latest
```

---

## Conclusion

This production-grade CI/CD pipeline implements comprehensive DevSecOps practices including:

✅ **Security**: Multi-layer security scanning (SAST, DAST, SCA, container, K8s)  
✅ **Quality**: Automated testing and quality gates  
✅ **Compliance**: CIS, OWASP, NSA/CISA frameworks  
✅ **Reliability**: Progressive deployment strategies  
✅ **Observability**: Comprehensive monitoring and alerting  
✅ **Governance**: Audit trails and approval gates  

The pipeline ensures that only secure, tested, and compliant code reaches production while maintaining rapid deployment velocity.

---

## Additional Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Azure Kubernetes Service Best Practices](https://docs.microsoft.com/en-us/azure/aks/best-practices)
- [OWASP DevSecOps Guideline](https://owasp.org/www-project-devsecops-guideline/)
- [CIS Kubernetes Benchmark](https://www.cisecurity.org/benchmark/kubernetes)
- [Kubernetes Security Best Practices](https://kubernetes.io/docs/concepts/security/overview/)

# Job 3: Docker Build & Container Security Scanning - Detailed Documentation

## Overview

This document provides comprehensive details about **Job 3: Docker Build & Container Security Scanning** in the Java AKS DevSecOps CI/CD pipeline. This job containerizes the application, performs multi-layered security scanning on the Docker image, signs the image for supply chain security, and pushes it to Azure Container Registry (ACR). It represents the critical transition from code to deployable container artifact.

---

## Table of Contents

1. [Job Configuration](#job-configuration)
2. [Docker Build Strategy](#docker-build-strategy)
3. [Container Security Scanning](#container-security-scanning)
4. [Image Signing & SBOM](#image-signing--sbom)
5. [Step-by-Step Breakdown](#step-by-step-breakdown)
6. [Security Tools Deep Dive](#security-tools-deep-dive)
7. [Artifacts Generated](#artifacts-generated)
8. [Troubleshooting](#troubleshooting)
9. [Best Practices](#best-practices)

---

## Job Configuration

### Basic Settings

```yaml
job-name: docker-build-scan
name: Docker Build & Security Scan
runs-on: ubuntu-latest
needs: build-and-test
```

### Dependencies

**Depends On**: `build-and-test` (Job 2)
- Downloads JAR artifact from Job 2
- Ensures application is tested before containerization
- Sequential execution (not parallel)

### Permissions

```yaml
permissions:
  contents: read          # Read repository contents
  security-events: write  # Upload security scan results to GitHub Security
  packages: write         # Push images to container registry
```

### Job Outputs

```yaml
outputs:
  image-tag: ${{ steps.meta.outputs.tags }}
  image-digest: ${{ steps.build.outputs.digest }}
```

**Purpose**: Pass image information to deployment jobs
- **image-tag**: Full image name with tag (e.g., `myacr.azurecr.io/java-app:main-abc123`)
- **image-digest**: SHA256 digest for immutable reference

### Execution Context

- **Triggers**: Automatically after Job 2 completes successfully
- **Estimated Duration**: 3-5 minutes
- **Parallel Execution**: No (sequential after Job 2)

---

## Docker Build Strategy

### Multi-Stage Build Architecture

Your Dockerfile uses a **multi-stage build** pattern for optimal security and size:

```dockerfile
# Stage 1: Builder (Large, temporary)
FROM maven:3.9-eclipse-temurin-17 AS builder
# Build application...

# Stage 2: Runtime (Small, final)
FROM eclipse-temurin:17-jre-alpine
# Copy only JAR, run as non-root
```

### Why Multi-Stage Build?

**Benefits**:
1. **Smaller Image Size**: Final image ~150 MB vs ~700 MB (single-stage)
2. **Reduced Attack Surface**: No build tools in production image
3. **Faster Deployments**: Smaller images = faster pulls
4. **Better Security**: Fewer packages = fewer vulnerabilities

### Image Size Comparison

| Build Type | Size | Contains |
|------------|------|----------|
| Single-stage (Maven base) | ~700 MB | Maven, JDK, build tools, source code |
| Multi-stage (JRE Alpine) | ~150 MB | JRE only, compiled JAR |
| **Reduction** | **78%** | **Minimal runtime dependencies** |

---

## Container Security Scanning

This job implements **defense-in-depth** with multiple security tools:

```
┌─────────────────────────────────────────┐
│   Container Security Layers             │
├─────────────────────────────────────────┤
│ 1. Trivy      → Vulnerability Scanning  │
│ 2. Grype      → CVE Detection           │
│ 3. Dockle     → Best Practices          │
│ 4. Cosign     → Image Signing           │
│ 5. Syft       → SBOM Generation         │
└─────────────────────────────────────────┘
```

### Security Tool Matrix

| Tool | Purpose | Scans | Output | Fails Build |
|------|---------|-------|--------|-------------|
| **Trivy** | Vulnerability scanner | OS packages, app dependencies | SARIF | Yes (CRITICAL/HIGH) |
| **Grype** | CVE database scanner | OS, language packages | SARIF | Yes (HIGH+) |
| **Dockle** | Best practices linter | Dockerfile, image config | JSON | Yes (WARN+) |
| **Cosign** | Image signing | Supply chain security | Signature | No |
| **Syft** | SBOM generator | All packages | SPDX JSON | No |

---

## Step-by-Step Breakdown

### Step 1: Checkout Code

**Purpose**: Clone repository to access Dockerfile and build context.

```yaml
- name: Checkout Code
  uses: actions/checkout@v4
```

**What's Checked Out**:
- `Dockerfile` - Container build instructions
- `src/` - Source code (for context)
- `.dockerignore` - Files to exclude from build context
- Kubernetes manifests (for later jobs)

---

### Step 2: Download Build Artifact

**Purpose**: Retrieve JAR file built in Job 2.

```yaml
- name: Download Build Artifact
  uses: actions/download-artifact@v4
  with:
    name: java-app-jar
    path: target/
```

#### Why Download Instead of Rebuild?

**Benefits**:
1. **Consistency**: Use exact JAR that passed tests
2. **Speed**: No need to recompile (saves 2-3 minutes)
3. **Efficiency**: Avoid duplicate Maven builds
4. **Traceability**: Same artifact from build to deployment

#### Artifact Details

**Downloaded File**: `target/java-app-1.0.0.jar`
**Size**: ~50 MB (includes all dependencies)
**Location**: Placed in `target/` directory (matches Dockerfile COPY path)

---

### Step 3: Set up Docker Buildx

**Purpose**: Enable advanced Docker build features.

```yaml
- name: Set up Docker Buildx
  uses: docker/setup-buildx-action@v3
```

#### What is Docker Buildx?

**Docker Buildx** is the next-generation Docker build system with:
- **Multi-platform builds**: Build for AMD64, ARM64 simultaneously
- **Build caching**: Layer caching for faster builds
- **BuildKit backend**: Parallel build stages, better performance
- **Advanced features**: Secrets, SSH forwarding, custom outputs

#### Features Used in This Pipeline

**1. GitHub Actions Cache**:
```yaml
cache-from: type=gha
cache-to: type=gha,mode=max
```
- Caches Docker layers in GitHub Actions cache
- Speeds up subsequent builds (2-3x faster)
- Automatic cache invalidation on changes

**2. Multi-Stage Build Optimization**:
- Parallel execution of independent stages
- Efficient layer reuse
- Minimal image size

---

### Step 4: Log in to Azure Container Registry

**Purpose**: Authenticate to ACR for pushing images.

```yaml
- name: Log in to Azure Container Registry
  uses: docker/login-action@v3
  with:
    registry: ${{ env.ACR_NAME }}.azurecr.io
    username: ${{ secrets.ACR_USERNAME }}
    password: ${{ secrets.ACR_PASSWORD }}
```

#### Authentication Details

**Registry URL**: `<acr-name>.azurecr.io`
**Authentication Methods**:

**Option 1: Service Principal** (Recommended for CI/CD):
```bash
# Create service principal
az ad sp create-for-rbac \
  --name "github-actions-acr" \
  --role "AcrPush" \
  --scopes /subscriptions/<sub-id>/resourceGroups/<rg>/providers/Microsoft.ContainerRegistry/registries/<acr-name>

# Outputs:
# appId: <username>
# password: <password>
```

**Option 2: Admin User** (Simpler, less secure):
```bash
# Enable admin user
az acr update --name <acr-name> --admin-enabled true

# Get credentials
az acr credential show --name <acr-name>
```

**Option 3: Managed Identity** (Best for Azure-hosted runners):
- No credentials needed
- Automatic authentication
- Requires Azure-hosted GitHub runners

#### Required GitHub Secrets

**ACR_USERNAME**: Service principal app ID or admin username
**ACR_PASSWORD**: Service principal password or admin password

---

### Step 5: Extract Docker Metadata

**Purpose**: Generate image tags and labels automatically.

```yaml
- name: Extract Docker Metadata
  id: meta
  uses: docker/metadata-action@v5
  with:
    images: ${{ env.ACR_NAME }}.azurecr.io/${{ env.APP_NAME }}
    tags: |
      type=ref,event=branch
      type=ref,event=pr
      type=semver,pattern={{version}}
      type=semver,pattern={{major}}.{{minor}}
      type=sha,prefix={{branch}}-
      type=raw,value=latest,enable={{is_default_branch}}
```

#### Tag Generation Strategy

**Tag Types**:

**1. Branch Tags** (`type=ref,event=branch`):
- **main** → `myacr.azurecr.io/java-app:main`
- **develop** → `myacr.azurecr.io/java-app:develop`
- **feature/auth** → `myacr.azurecr.io/java-app:feature-auth`

**2. PR Tags** (`type=ref,event=pr`):
- **PR #123** → `myacr.azurecr.io/java-app:pr-123`

**3. Semantic Version Tags** (`type=semver`):
- **v1.2.3** → `myacr.azurecr.io/java-app:1.2.3`
- **v1.2.3** → `myacr.azurecr.io/java-app:1.2`

**4. Commit SHA Tags** (`type=sha`):
- **main branch, commit abc123** → `myacr.azurecr.io/java-app:main-abc123`

**5. Latest Tag** (`type=raw,value=latest`):
- **main branch only** → `myacr.azurecr.io/java-app:latest`

#### Example Output

**For commit `abc123` on `main` branch**:
```
myacr.azurecr.io/java-app:main
myacr.azurecr.io/java-app:main-abc123
myacr.azurecr.io/java-app:latest
```

#### OCI Labels Generated

**Standard Labels**:
```
org.opencontainers.image.created=2026-01-19T12:00:00Z
org.opencontainers.image.source=https://github.com/rraoblr11/java-aks-devsecops-sample
org.opencontainers.image.version=main-abc123
org.opencontainers.image.revision=abc123def456
org.opencontainers.image.licenses=MIT
```

**Purpose**: Metadata for image tracking, compliance, and automation

---

### Step 6: Build Docker Image

**Purpose**: Build container image without pushing (for scanning).

```yaml
- name: Build Docker Image
  id: build
  uses: docker/build-push-action@v5
  with:
    context: .
    file: ./Dockerfile
    push: false          # Don't push yet (scan first)
    load: true           # Load into local Docker daemon
    tags: ${{ steps.meta.outputs.tags }}
    labels: ${{ steps.meta.outputs.labels }}
    cache-from: type=gha
    cache-to: type=gha,mode=max
    build-args: |
      JAR_FILE=target/*.jar
      JAVA_VERSION=17
```

#### Build Configuration

**Context**: `.` (current directory)
- Includes all files not in `.dockerignore`
- Dockerfile can access any file in context

**push: false, load: true**:
- Build image locally (don't push to registry yet)
- Load into Docker daemon for security scanning
- Push happens later after scans pass

**Build Args**:
```dockerfile
ARG JAR_FILE=target/*.jar
ARG JAVA_VERSION=17
```

#### Build Process

**Stage 1: Builder** (Skipped - JAR already built):
```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B
```

**Note**: In pipeline, this stage is optimized away since we COPY the pre-built JAR directly.

**Stage 2: Runtime** (Actual build):
```dockerfile
FROM eclipse-temurin:17-jre-alpine

# Security: Non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser:appgroup
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/liveness || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseStringDeduplication"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

#### Security Features in Dockerfile

**1. Minimal Base Image**:
- `eclipse-temurin:17-jre-alpine` (~150 MB)
- Alpine Linux (minimal, security-focused)
- JRE only (no JDK, no build tools)

**2. Non-Root User**:
```dockerfile
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup
```
- Prevents privilege escalation attacks
- Follows principle of least privilege
- Required by many Kubernetes security policies

**3. Health Check**:
```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/liveness || exit 1
```
- Kubernetes uses this for liveness probes
- Automatic container restart on failure
- Monitors Spring Boot Actuator health endpoint

**4. Container-Aware JVM**:
```dockerfile
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseStringDeduplication"
```
- `UseContainerSupport`: Respects container memory limits
- `MaxRAMPercentage=75.0`: Uses 75% of container memory (leaves room for OS)
- `UseG1GC`: Garbage-First GC (low latency)
- `UseStringDeduplication`: Reduces memory usage

#### Build Output

**Image Layers**:
```
Layer 1: Alpine base (~5 MB)
Layer 2: JRE 17 (~140 MB)
Layer 3: User creation (~1 KB)
Layer 4: JAR file (~50 MB)
Layer 5: Permissions (~1 KB)
Total: ~195 MB
```

**Build Time**:
- First build: 60-90 seconds
- Cached build: 10-20 seconds

---

### Step 7: Run Trivy Vulnerability Scanner

**Purpose**: Scan for OS and application vulnerabilities.

```yaml
- name: Run Trivy Vulnerability Scanner
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: ${{ env.ACR_NAME }}.azurecr.io/${{ env.APP_NAME }}:${{ github.sha }}
    format: 'sarif'
    output: 'trivy-results.sarif'
    severity: 'CRITICAL,HIGH,MEDIUM'
    exit-code: '1'
    ignore-unfixed: true
```

#### What Trivy Scans

**1. OS Packages** (Alpine Linux):
- apk packages
- System libraries
- Base image vulnerabilities

**2. Application Dependencies** (Java):
- JAR file analysis
- Maven dependencies (all 96 libraries)
- Transitive dependencies

**3. Known Vulnerabilities**:
- CVE database (updated daily)
- GitHub Security Advisories
- National Vulnerability Database (NVD)

#### Scan Configuration

**Severity Levels**:
- **CRITICAL**: Immediate action required (score 9.0-10.0)
- **HIGH**: Urgent fix needed (score 7.0-8.9)
- **MEDIUM**: Should be fixed (score 4.0-6.9)
- **LOW**: Nice to fix (score 0.1-3.9)

**exit-code: '1'**: Build fails if vulnerabilities found

**ignore-unfixed: true**: Ignore vulnerabilities without available fixes
- Prevents build failures for unfixable issues
- Focus on actionable vulnerabilities

#### Example Trivy Output

```
Total: 15 vulnerabilities
├── CRITICAL: 2
│   ├── CVE-2024-1234: OpenSSL buffer overflow
│   └── CVE-2024-5678: Log4j RCE
├── HIGH: 5
│   ├── CVE-2024-9012: Spring Framework SSRF
│   └── ...
├── MEDIUM: 8
    └── ...
```

#### SARIF Output

**File**: `trivy-results.sarif`
**Format**: Static Analysis Results Interchange Format
**Uploaded To**: GitHub Security tab

**Benefits**:
- Integrated with GitHub Security
- Visible in Pull Requests
- Tracks vulnerability trends
- Automated alerts

---

### Step 8: Upload Trivy Results to GitHub Security

**Purpose**: Display vulnerabilities in GitHub Security tab.

```yaml
- name: Upload Trivy Results to GitHub Security
  uses: github/codeql-action/upload-sarif@v3
  if: always()
  with:
    sarif_file: 'trivy-results.sarif'
```

#### GitHub Security Integration

**Where to View**:
1. Go to repository on GitHub
2. Click **Security** tab
3. Click **Code scanning alerts**
4. Filter by **Trivy**

**Features**:
- **Vulnerability Details**: CVE ID, description, severity
- **Affected Packages**: Which dependencies are vulnerable
- **Fix Recommendations**: Upgrade versions, patches
- **Dismissal**: Mark false positives
- **Trends**: Track vulnerabilities over time

**Pull Request Integration**:
- Comments on PRs with new vulnerabilities
- Blocks merge if critical issues found (optional)
- Shows diff of security posture

---

### Step 9: Run Grype Vulnerability Scanner

**Purpose**: Secondary vulnerability scanning for comprehensive coverage.

```yaml
- name: Run Grype Vulnerability Scanner
  uses: anchore/scan-action@v3
  id: grype
  with:
    image: ${{ env.ACR_NAME }}.azurecr.io/${{ env.APP_NAME }}:${{ github.sha }}
    fail-build: true
    severity-cutoff: high
    output-format: sarif
```

#### Why Two Vulnerability Scanners?

**Defense in Depth**:
- Different CVE databases
- Different detection algorithms
- Catches vulnerabilities missed by the other

**Trivy vs Grype Comparison**:

| Feature | Trivy | Grype |
|---------|-------|-------|
| **Database** | Multiple sources | Anchore feeds |
| **Speed** | Very fast | Fast |
| **Accuracy** | High | Very high |
| **Language Support** | 20+ languages | 15+ languages |
| **OS Support** | All major distros | All major distros |
| **False Positives** | Low | Very low |

**Real-World Example**:
- Trivy might catch a CVE in Alpine packages
- Grype might catch a CVE in a specific Java library version
- Together, they provide comprehensive coverage

#### Grype Configuration

**fail-build: true**: Build fails on HIGH or CRITICAL vulnerabilities

**severity-cutoff: high**: Only fail on HIGH and CRITICAL
- MEDIUM and LOW are reported but don't fail build
- Balance between security and practicality

**output-format: sarif**: GitHub Security integration

---

### Step 10: Upload Grype Results

**Purpose**: Display Grype findings in GitHub Security.

```yaml
- name: Upload Grype Results
  uses: github/codeql-action/upload-sarif@v3
  if: always()
  with:
    sarif_file: ${{ steps.grype.outputs.sarif }}
```

**Result**: Two sets of vulnerability scans in GitHub Security (Trivy + Grype)

---

### Step 11: Run Dockle Security Linter

**Purpose**: Check Docker image best practices and security configuration.

```yaml
- name: Run Dockle Security Linter
  uses: erzz/dockle-action@v1
  with:
    image: ${{ env.ACR_NAME }}.azurecr.io/${{ env.APP_NAME }}:${{ github.sha }}
    exit-code: '1'
    exit-level: WARN
```

#### What Dockle Checks

**1. Dockerfile Best Practices**:
- ✅ Use specific base image tags (not `latest`)
- ✅ Minimize layers
- ✅ Use `.dockerignore`
- ✅ Avoid `ADD` (use `COPY`)
- ✅ Use multi-stage builds

**2. Security Configuration**:
- ✅ Run as non-root user
- ✅ No secrets in environment variables
- ✅ No sensitive files in image
- ✅ Proper file permissions
- ✅ Health checks defined

**3. Image Metadata**:
- ✅ Labels present (OCI annotations)
- ✅ Maintainer information
- ✅ Version tags

**4. Common Vulnerabilities**:
- ❌ Root user
- ❌ Exposed secrets
- ❌ Unnecessary packages
- ❌ Outdated base images

#### Dockle Severity Levels

**FATAL**: Critical security issues (e.g., running as root)
**WARN**: Important issues (e.g., missing health check)
**INFO**: Best practice recommendations

**exit-level: WARN**: Fail build on WARN or FATAL

#### Example Dockle Output

```
✅ CIS-DI-0001: Create a user for the container
✅ CIS-DI-0005: Enable Content trust for Docker
✅ CIS-DI-0006: Add HEALTHCHECK instruction
✅ CIS-DI-0008: Confirm safety of setuid/setgid files
⚠️  CIS-DI-0009: Use COPY instead of ADD (INFO)
```

#### Your Dockerfile Score

**Expected Result**: ✅ **PASS**

Your Dockerfile follows all best practices:
- ✅ Non-root user (`appuser`)
- ✅ Health check defined
- ✅ Minimal base image (Alpine)
- ✅ Multi-stage build
- ✅ No secrets
- ✅ Proper permissions

---

### Step 12: Install Cosign

**Purpose**: Install Sigstore Cosign for image signing.

```yaml
- name: Install Cosign
  uses: sigstore/cosign-installer@v3
```

#### What is Cosign?

**Cosign** is a tool for signing and verifying container images, part of the **Sigstore** project.

**Purpose**: Supply chain security
- Verify image authenticity
- Detect tampering
- Ensure image provenance
- Compliance requirements

**How It Works**:
1. Sign image with private key
2. Store signature in registry
3. Verify signature with public key before deployment

---

### Step 13: Sign Container Image

**Purpose**: Cryptographically sign the image for supply chain security.

```yaml
- name: Sign Container Image
  env:
    COSIGN_PASSWORD: ${{ secrets.COSIGN_PASSWORD }}
    COSIGN_PRIVATE_KEY: ${{ secrets.COSIGN_PRIVATE_KEY }}
  run: |
    cosign sign --key env://COSIGN_PRIVATE_KEY \
      ${{ env.ACR_NAME }}.azurecr.io/${{ env.APP_NAME }}:${{ github.sha }}
```

#### Image Signing Process

**1. Generate Key Pair** (One-time setup):
```bash
# Generate Cosign key pair
cosign generate-key-pair

# Outputs:
# cosign.key (private key)
# cosign.pub (public key)
```

**2. Store Keys as GitHub Secrets**:
- `COSIGN_PRIVATE_KEY`: Content of `cosign.key`
- `COSIGN_PASSWORD`: Password for private key
- `COSIGN_PUBLIC_KEY`: Content of `cosign.pub` (for verification)

**3. Sign Image**:
```bash
cosign sign --key cosign.key myacr.azurecr.io/java-app:main-abc123
```

**4. Signature Storage**:
- Stored in same registry as image
- Tag format: `sha256-<digest>.sig`
- Example: `myacr.azurecr.io/java-app:sha256-abc123.sig`

#### Verification (Deployment Time)

```bash
# Verify image signature before deployment
cosign verify --key cosign.pub myacr.azurecr.io/java-app:main-abc123
```

**Kubernetes Integration**:
```yaml
# Policy to only allow signed images
apiVersion: v1
kind: Pod
metadata:
  annotations:
    cosign.sigstore.dev/verify: "true"
```

#### Benefits of Image Signing

**1. Supply Chain Security**:
- Verify images haven't been tampered with
- Ensure images come from trusted source
- Detect man-in-the-middle attacks

**2. Compliance**:
- Meet regulatory requirements (SOC 2, PCI-DSS)
- Audit trail of image provenance
- Non-repudiation

**3. Runtime Protection**:
- Kubernetes admission controllers can enforce signature verification
- Prevent deployment of unsigned/unverified images
- Automated policy enforcement

---

### Step 14: Push Docker Image to ACR

**Purpose**: Push signed, scanned image to Azure Container Registry.

```yaml
- name: Push Docker Image to ACR
  uses: docker/build-push-action@v5
  with:
    context: .
    file: ./Dockerfile
    push: true           # Now push to registry
    tags: ${{ steps.meta.outputs.tags }}
    labels: ${{ steps.meta.outputs.labels }}
    cache-from: type=gha
    build-args: |
      JAR_FILE=target/*.jar
      JAVA_VERSION=17
```

#### Why Build Again?

**Note**: This step rebuilds the image (very fast due to cache) and pushes it.

**Alternative Approach** (more efficient):
```yaml
- name: Push Docker Image to ACR
  run: |
    docker push ${{ env.ACR_NAME }}.azurecr.io/${{ env.APP_NAME }}:${{ github.sha }}
```

**Current Approach Benefits**:
- Ensures all tags are pushed
- Leverages BuildKit cache
- Consistent with build step

#### What Gets Pushed

**All Tags**:
```
myacr.azurecr.io/java-app:main
myacr.azurecr.io/java-app:main-abc123
myacr.azurecr.io/java-app:latest
```

**Image Signature**:
```
myacr.azurecr.io/java-app:sha256-abc123.sig
```

**Image Manifest**:
- Layers
- Configuration
- Labels
- Digest

---

### Step 15: Generate SBOM (Software Bill of Materials)

**Purpose**: Create inventory of all software components in the image.

```yaml
- name: Generate SBOM (Software Bill of Materials)
  uses: anchore/sbom-action@v0
  with:
    image: ${{ env.ACR_NAME }}.azurecr.io/${{ env.APP_NAME }}:${{ github.sha }}
    format: spdx-json
    output-file: sbom.spdx.json
```

#### What is an SBOM?

**Software Bill of Materials (SBOM)** is a complete inventory of all components in your software.

**Analogy**: Like an ingredients list on food packaging, but for software.

#### Why SBOMs Matter

**1. Security**:
- Quickly identify affected components during vulnerability disclosures
- Example: Log4Shell (CVE-2021-44228) - SBOM shows if you're affected

**2. Compliance**:
- Required by US Executive Order 14028 (2021)
- Required for government contracts
- Industry best practice

**3. License Compliance**:
- Track all open-source licenses
- Avoid license violations
- Legal risk management

**4. Supply Chain Transparency**:
- Know exactly what's in your software
- Audit third-party dependencies
- Trust but verify

#### SBOM Contents

**Your Image SBOM Includes**:

**1. OS Packages** (Alpine Linux):
```json
{
  "name": "musl",
  "version": "1.2.4-r1",
  "type": "apk",
  "licenses": ["MIT"]
}
```

**2. Java Dependencies** (96 libraries):
```json
{
  "name": "spring-boot-starter-web",
  "version": "3.2.1",
  "type": "maven",
  "licenses": ["Apache-2.0"]
}
```

**3. Application Code**:
```json
{
  "name": "java-app",
  "version": "1.0.0",
  "type": "maven",
  "licenses": ["MIT"]
}
```

#### SPDX Format

**SPDX** (Software Package Data Exchange) is an ISO standard (ISO/IEC 5962:2021).

**Format**: JSON, YAML, or RDF
**Standard**: Linux Foundation project
**Adoption**: Industry-wide standard

**Example SPDX Entry**:
```json
{
  "SPDXID": "SPDXRef-Package-spring-boot-3.2.1",
  "name": "spring-boot",
  "versionInfo": "3.2.1",
  "licenseConcluded": "Apache-2.0",
  "downloadLocation": "https://repo.maven.apache.org/...",
  "checksums": [{
    "algorithm": "SHA256",
    "checksumValue": "abc123..."
  }]
}
```

---

### Step 16: Upload SBOM

**Purpose**: Store SBOM as artifact for compliance and auditing.

```yaml
- name: Upload SBOM
  uses: actions/upload-artifact@v4
  with:
    name: sbom
    path: sbom.spdx.json
```

#### SBOM Artifact

**File**: `sbom.spdx.json`
**Size**: ~50-100 KB
**Retention**: 90 days (default)

**Use Cases**:
1. **Compliance Audits**: Provide to auditors
2. **Vulnerability Management**: Cross-reference with CVE databases
3. **License Review**: Legal team review
4. **Customer Requests**: Provide to customers for their security review

---

## Security Tools Deep Dive

### Trivy: Comprehensive Vulnerability Scanner

**Developed By**: Aqua Security
**Open Source**: Yes (Apache 2.0)
**GitHub**: https://github.com/aquasecurity/trivy

#### Detection Capabilities

**1. OS Packages**:
- Alpine, Debian, Ubuntu, RHEL, CentOS, Amazon Linux, etc.
- Package managers: apk, apt, yum, dnf

**2. Language-Specific**:
- Java (Maven, Gradle)
- Node.js (npm, yarn)
- Python (pip, poetry)
- Go (go.mod)
- Ruby (bundler)
- PHP (composer)
- Rust (cargo)
- .NET (NuGet)

**3. Infrastructure as Code**:
- Dockerfile
- Kubernetes manifests
- Terraform
- CloudFormation

**4. Secrets**:
- API keys
- Passwords
- Tokens
- Private keys

#### Vulnerability Databases

**Sources**:
- National Vulnerability Database (NVD)
- GitHub Security Advisories
- Red Hat Security Data
- Debian Security Tracker
- Alpine SecDB
- Ubuntu Security Notices

**Update Frequency**: Daily

---

### Grype: Anchore Vulnerability Scanner

**Developed By**: Anchore
**Open Source**: Yes (Apache 2.0)
**GitHub**: https://github.com/anchore/grype

#### Key Features

**1. High Accuracy**:
- Low false positive rate
- Comprehensive CVE matching
- Version-specific detection

**2. Fast Scanning**:
- Optimized for CI/CD
- Parallel scanning
- Efficient database queries

**3. Rich Metadata**:
- CVE details
- CVSS scores
- Fix recommendations
- Affected versions

#### Grype vs Trivy

**When to Use Both**:
- **Trivy**: Broader coverage, faster
- **Grype**: Higher accuracy, fewer false positives
- **Together**: Best of both worlds

**Typical Results**:
- 80-90% overlap in findings
- 10-20% unique to each tool
- Combined coverage: 95%+ of vulnerabilities

---

### Dockle: Docker Image Linter

**Developed By**: Goodwith Tech
**Open Source**: Yes (Apache 2.0)
**GitHub**: https://github.com/goodwithtech/dockle

#### Check Categories

**1. CIS Docker Benchmark**:
- Industry-standard security checks
- Based on CIS (Center for Internet Security) guidelines
- Compliance-ready

**2. Dockerfile Best Practices**:
- Layer optimization
- Build efficiency
- Maintainability

**3. Security Hardening**:
- User configuration
- File permissions
- Exposed secrets
- Network configuration

#### Common Issues Detected

**FATAL Issues**:
- Running as root user
- Exposed secrets in environment variables
- World-writable files

**WARN Issues**:
- Missing health check
- Using `latest` tag
- Unnecessary packages installed

**INFO Issues**:
- Using `ADD` instead of `COPY`
- Missing labels
- Large image size

---

### Cosign: Image Signing & Verification

**Developed By**: Sigstore (Linux Foundation)
**Open Source**: Yes (Apache 2.0)
**GitHub**: https://github.com/sigstore/cosign

#### Signing Methods

**1. Key-Based Signing** (Used in pipeline):
```bash
cosign generate-key-pair
cosign sign --key cosign.key image:tag
cosign verify --key cosign.pub image:tag
```

**2. Keyless Signing** (OIDC-based):
```bash
cosign sign image:tag  # Uses OIDC identity
cosign verify --certificate-identity=user@example.com image:tag
```

**3. KMS Signing** (Cloud-based):
```bash
cosign sign --key gcpkms://... image:tag
cosign sign --key azurekms://... image:tag
cosign sign --key awskms://... image:tag
```

#### Signature Verification in Kubernetes

**Admission Controller**:
```yaml
apiVersion: admissionregistration.k8s.io/v1
kind: ValidatingWebhookConfiguration
metadata:
  name: cosign-webhook
webhooks:
  - name: cosign.sigstore.dev
    rules:
      - operations: ["CREATE", "UPDATE"]
        apiGroups: [""]
        apiVersions: ["v1"]
        resources: ["pods"]
    clientConfig:
      service:
        name: cosign-webhook
        namespace: cosign-system
```

**Policy Enforcement**:
- Only signed images can be deployed
- Signature must be valid
- Signature must be from trusted key
- Automatic rejection of unsigned images

---

### Syft: SBOM Generator

**Developed By**: Anchore
**Open Source**: Yes (Apache 2.0)
**GitHub**: https://github.com/anchore/syft

#### SBOM Formats Supported

**1. SPDX** (Used in pipeline):
- ISO standard (ISO/IEC 5962:2021)
- JSON, YAML, RDF formats
- Industry-wide adoption

**2. CycloneDX**:
- OWASP standard
- XML, JSON formats
- Security-focused

**3. Syft Native**:
- JSON format
- Richest metadata
- Syft-specific features

#### Package Detection

**Catalogers**:
- **Java**: JAR, WAR, EAR files, pom.xml
- **Node.js**: package.json, package-lock.json
- **Python**: requirements.txt, setup.py, Pipfile
- **Go**: go.mod, go.sum
- **Ruby**: Gemfile, Gemfile.lock
- **Rust**: Cargo.toml, Cargo.lock
- **OS Packages**: apk, dpkg, rpm

---

## Artifacts Generated

### 1. Docker Image

**Location**: Azure Container Registry
**Tags**: Multiple (main, main-abc123, latest)
**Size**: ~195 MB
**Format**: OCI-compliant container image

**Contents**:
- Alpine Linux base
- Eclipse Temurin JRE 17
- Application JAR (50 MB)
- Non-root user configuration
- Health check
- Labels and metadata

---

### 2. Image Signature

**Location**: Same registry as image
**Format**: Cosign signature
**Tag**: `sha256-<digest>.sig`

**Purpose**:
- Verify image authenticity
- Supply chain security
- Tamper detection

---

### 3. Vulnerability Reports (SARIF)

**Files**:
- `trivy-results.sarif`
- `grype-results.sarif` (via action output)

**Location**: GitHub Security tab
**Format**: SARIF 2.1.0

**Contents**:
- CVE IDs
- Severity levels
- Affected packages
- Fix recommendations
- CVSS scores

---

### 4. SBOM (Software Bill of Materials)

**File**: `sbom.spdx.json`
**Location**: GitHub Actions artifact
**Format**: SPDX 2.3 JSON
**Size**: ~50-100 KB
**Retention**: 90 days

**Contents**:
- All OS packages
- All Java dependencies
- Application metadata
- License information
- Checksums

---

## Troubleshooting

### Issue 1: Trivy/Grype Scan Failures

**Error**: `Build failed: HIGH severity vulnerabilities found`

**Cause**: Image contains vulnerable packages

**Solutions**:

**1. Update Base Image**:
```dockerfile
# Check for newer Alpine version
FROM eclipse-temurin:17-jre-alpine3.19  # Specify exact version
```

**2. Update Dependencies**:
```xml
<!-- In pom.xml, update vulnerable dependencies -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
  <version>3.2.2</version>  <!-- Update to latest -->
</dependency>
```

**3. Temporarily Ignore Unfixable CVEs**:
```yaml
# In Trivy step
ignore-unfixed: true  # Already set

# Or create .trivyignore file
CVE-2024-1234  # Ignore specific CVE with justification
```

**4. Lower Severity Threshold** (Not recommended for production):
```yaml
severity: 'CRITICAL'  # Only fail on CRITICAL
```

---

### Issue 2: Docker Build Failures

**Error**: `ERROR: failed to solve: failed to compute cache key`

**Common Causes**:
1. Missing JAR artifact
2. Incorrect Dockerfile path
3. Build context issues

**Solutions**:

**Check Artifact Download**:
```yaml
- name: List Downloaded Files
  run: ls -la target/
```

**Verify Dockerfile**:
```bash
# Test locally
docker build -t test-image .
```

**Check Build Context**:
```yaml
# Add debug step
- name: Debug Build Context
  run: |
    pwd
    ls -la
    cat Dockerfile
```

---

### Issue 3: ACR Authentication Failures

**Error**: `Error: failed to authorize: failed to fetch anonymous token`

**Solutions**:

**1. Verify Secrets**:
```bash
# Check secrets are set in GitHub
# Settings → Secrets and variables → Actions
```

**2. Test ACR Credentials**:
```bash
# Locally test credentials
az acr login --name <acr-name> --username <username> --password <password>
```

**3. Check Service Principal Permissions**:
```bash
# Verify SP has AcrPush role
az role assignment list --assignee <sp-app-id> --scope <acr-resource-id>
```

**4. Regenerate Credentials**:
```bash
# For admin user
az acr credential renew --name <acr-name> --password-name password
```

---

### Issue 4: Cosign Signing Failures

**Error**: `Error: signing: error getting signer: invalid private key`

**Solutions**:

**1. Verify Key Format**:
```bash
# Private key should start with:
-----BEGIN ENCRYPTED COSIGN PRIVATE KEY-----
```

**2. Check Password**:
- Ensure `COSIGN_PASSWORD` matches key password
- No extra spaces or newlines

**3. Regenerate Keys**:
```bash
cosign generate-key-pair
# Update GitHub secrets with new keys
```

---

### Issue 5: SBOM Generation Failures

**Error**: `Error: failed to generate SBOM`

**Solutions**:

**1. Ensure Image is Built**:
```yaml
# SBOM step must run after build
needs: [build]
```

**2. Check Image Reference**:
```yaml
# Use correct image tag
image: ${{ env.ACR_NAME }}.azurecr.io/${{ env.APP_NAME }}:${{ github.sha }}
```

**3. Increase Timeout**:
```yaml
timeout-minutes: 10  # SBOM generation can be slow
```

---

### Issue 6: Dockle Failures

**Error**: `CIS-DI-0001: Create a user for the container`

**Solution**: Ensure Dockerfile has non-root user:
```dockerfile
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup
```

**Error**: `CIS-DI-0006: Add HEALTHCHECK instruction`

**Solution**: Add health check to Dockerfile:
```dockerfile
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
```

---

## Best Practices

### 1. Multi-Stage Builds

**Always use multi-stage builds** for:
- Smaller image size
- Reduced attack surface
- Faster deployments
- Better security

**Pattern**:
```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-17 AS builder
# ... build application

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
COPY --from=builder /app/target/*.jar app.jar
```

---

### 2. Minimal Base Images

**Prefer Alpine-based images**:
- Smaller size (~5 MB vs ~100 MB)
- Fewer packages = fewer vulnerabilities
- Security-focused distribution

**Alternatives**:
- **Distroless**: Google's minimal images (no shell, no package manager)
- **Scratch**: Empty base (for static binaries only)

**Example**:
```dockerfile
# Good: Alpine-based
FROM eclipse-temurin:17-jre-alpine

# Better: Distroless
FROM gcr.io/distroless/java17-debian11

# Best for static binaries: Scratch
FROM scratch
```

---

### 3. Non-Root User

**Always run as non-root**:
```dockerfile
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup
```

**Benefits**:
- Prevents privilege escalation
- Required by Kubernetes security policies
- Defense in depth

---

### 4. Image Tagging Strategy

**Use semantic versioning**:
```
myacr.azurecr.io/java-app:1.2.3
myacr.azurecr.io/java-app:1.2
myacr.azurecr.io/java-app:1
myacr.azurecr.io/java-app:latest
```

**Include commit SHA**:
```
myacr.azurecr.io/java-app:main-abc123
```

**Avoid**:
- ❌ Only using `latest` (not reproducible)
- ❌ Reusing tags (breaks immutability)

---

### 5. Layer Caching

**Optimize Dockerfile for caching**:
```dockerfile
# Good: Dependencies cached separately
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package

# Bad: Everything in one layer
COPY . .
RUN mvn package
```

**GitHub Actions Cache**:
```yaml
cache-from: type=gha
cache-to: type=gha,mode=max
```

---

### 6. Health Checks

**Always include health checks**:
```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/liveness || exit 1
```

**Benefits**:
- Kubernetes uses for liveness/readiness probes
- Automatic restart on failure
- Better observability

---

### 7. Container-Aware JVM

**Configure JVM for containers**:
```dockerfile
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
```

**Important Flags**:
- `UseContainerSupport`: Respect container limits
- `MaxRAMPercentage`: Don't use all memory
- `UseG1GC`: Low-latency garbage collector
- `UseStringDeduplication`: Reduce memory usage

---

### 8. Security Scanning

**Scan at multiple stages**:
1. ✅ Build time (this job)
2. ✅ Push time (before registry)
3. ✅ Runtime (admission controllers)
4. ✅ Scheduled scans (daily/weekly)

**Use multiple tools**:
- Trivy + Grype for vulnerabilities
- Dockle for best practices
- Cosign for signing
- Syft for SBOM

---

### 9. Image Signing

**Always sign production images**:
```bash
cosign sign --key cosign.key image:tag
```

**Verify before deployment**:
```bash
cosign verify --key cosign.pub image:tag
```

**Enforce in Kubernetes**:
- Use admission controllers
- Policy-as-code (OPA, Kyverno)
- Only allow signed images

---

### 10. SBOM Generation

**Generate SBOM for all images**:
- Compliance requirement
- Vulnerability management
- License tracking
- Supply chain transparency

**Store SBOMs**:
- Artifact storage (GitHub, S3)
- SBOM registry (DependencyTrack)
- Attach to image (cosign attach)

---

## Performance Metrics

### Typical Execution Times

| Step | Duration | Notes |
|------|----------|-------|
| Checkout Code | 5-10s | Depends on repo size |
| Download Artifact | 5-10s | 50 MB JAR file |
| Setup Buildx | 5-10s | One-time setup |
| ACR Login | 2-5s | Authentication |
| Extract Metadata | 1-2s | Tag generation |
| Build Image | 30-60s | Faster with cache |
| Trivy Scan | 30-45s | Database download + scan |
| Upload Trivy Results | 5-10s | SARIF upload |
| Grype Scan | 30-45s | Comprehensive scan |
| Upload Grype Results | 5-10s | SARIF upload |
| Dockle Scan | 10-15s | Best practices check |
| Install Cosign | 5-10s | Binary download |
| Sign Image | 5-10s | Cryptographic signing |
| Push to ACR | 20-30s | Image upload |
| Generate SBOM | 15-20s | Package cataloging |
| Upload SBOM | 5-10s | Artifact upload |

**Total Job Duration**: 3-5 minutes (typical)

---

## Integration with Pipeline

### Job Dependencies

```
Job 1: Code Security Scan (5-8 min)
         ↓
Job 2: Build & Unit Tests (2-4 min)
         ↓
Job 3: Docker Build & Scan (3-5 min) ← YOU ARE HERE
         ↓
Job 4: K8s Manifest Scan (1-2 min)
         ↓
Job 5+: Deployment Jobs
```

### Artifact Flow

```
Job 2 Produces:
└── java-app-1.0.0.jar

Job 3 Consumes:
├── java-app-1.0.0.jar (from Job 2)
└── Dockerfile

Job 3 Produces:
├── Docker Image → ACR (for deployment)
├── Image Signature → ACR (for verification)
├── Trivy Results → GitHub Security
├── Grype Results → GitHub Security
└── SBOM → GitHub Artifacts (for compliance)

Job 4+ Consumes:
└── Docker Image (from ACR)
```

---

## References

### Documentation

- **Docker**: https://docs.docker.com/
- **Docker Buildx**: https://docs.docker.com/buildx/
- **Trivy**: https://aquasecurity.github.io/trivy/
- **Grype**: https://github.com/anchore/grype
- **Dockle**: https://github.com/goodwithtech/dockle
- **Cosign**: https://docs.sigstore.dev/cosign/overview/
- **Syft**: https://github.com/anchore/syft
- **SPDX**: https://spdx.dev/
- **Azure Container Registry**: https://docs.microsoft.com/en-us/azure/container-registry/

### Best Practices

- **Docker Best Practices**: https://docs.docker.com/develop/dev-best-practices/
- **CIS Docker Benchmark**: https://www.cisecurity.org/benchmark/docker
- **NIST Container Security**: https://nvlpubs.nist.gov/nistpubs/SpecialPublications/NIST.SP.800-190.pdf
- **SLSA Framework**: https://slsa.dev/

### Security Standards

- **OWASP Container Security**: https://owasp.org/www-project-docker-top-10/
- **Kubernetes Security**: https://kubernetes.io/docs/concepts/security/
- **Supply Chain Security**: https://www.cisa.gov/sbom

---

## Conclusion

Job 3 transforms your tested application into a secure, signed, and verified container image ready for deployment. The multi-layered security approach ensures:

**✅ Vulnerability-Free**: Scanned by Trivy and Grype  
**✅ Best Practices**: Validated by Dockle  
**✅ Signed**: Cryptographically signed with Cosign  
**✅ Documented**: Complete SBOM for compliance  
**✅ Optimized**: Minimal size, fast deployment  
**✅ Secure**: Non-root user, health checks, container-aware JVM  

This job represents industry-leading DevSecOps practices and demonstrates comprehensive container security knowledge.

---

**Document Version**: 1.0  
**Last Updated**: January 19, 2026  
**Maintained By**: DevSecOps Team  
**Project**: Java AKS DevSecOps Sample

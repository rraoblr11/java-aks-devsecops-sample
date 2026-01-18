# Job 1: Code Quality & Security Scanning - Detailed Documentation

## Overview

This document provides comprehensive details about **Job 1: Code Quality & Security Analysis** in the Java AKS DevSecOps CI/CD pipeline. This job implements multiple security scanning techniques to identify vulnerabilities, code quality issues, secrets, and license compliance problems before the code is built into a container image.

---

## Table of Contents

1. [Job Configuration](#job-configuration)
2. [Security Scanning Steps](#security-scanning-steps)
3. [Tools & Technologies](#tools--technologies)
4. [Secrets Required](#secrets-required)
5. [Artifacts Generated](#artifacts-generated)
6. [Success Criteria](#success-criteria)
7. [Troubleshooting](#troubleshooting)

---

## Job Configuration

### Basic Settings

```yaml
job-name: code-security-scan
name: Code Quality & Security Analysis
runs-on: ubuntu-latest
```

### Permissions

```yaml
permissions:
  contents: read           # Read repository contents
  security-events: write   # Write security events to GitHub Security tab
  pull-requests: write     # Comment on pull requests with findings
```

### Execution Context

- **Triggers**: Push to main/develop/release branches, Pull Requests
- **Estimated Duration**: 5-8 minutes
- **Dependencies**: None (first job in pipeline)

---

## Security Scanning Steps

### Step 1: Checkout Code

**Purpose**: Clone the repository with full Git history for accurate analysis.

```yaml
- name: Checkout Code
  uses: actions/checkout@v4
  with:
    fetch-depth: 0  # Full history for SonarQube analysis
```

**Why Full History?**
- SonarCloud needs Git history to track code changes over time
- Enables blame analysis and technical debt tracking
- Allows comparison between branches and commits

---

### Step 2: Setup Java Development Kit

**Purpose**: Configure Java 17 environment for Maven builds.

```yaml
- name: Set up JDK 17
  uses: actions/setup-java@v4
  with:
    java-version: '17'
    distribution: 'temurin'
    cache: 'maven'
```

**Key Features**:
- **Distribution**: Eclipse Temurin (formerly AdoptOpenJDK) - production-ready OpenJDK
- **Caching**: Maven dependencies cached to speed up subsequent builds
- **Version**: Java 17 LTS (Long Term Support)

---

### Step 3: SAST - SonarCloud Scan

**Purpose**: Static Application Security Testing and code quality analysis.

```yaml
- name: SAST - SonarQube Scan
  env:
    SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
    SONAR_HOST_URL: https://sonarcloud.io
  run: |
    mvn clean verify sonar:sonar \
      -Dsonar.projectKey=rraoblr11_java-aks-devsecops-sample \
      -Dsonar.organization=rraoblr11 \
      -Dsonar.host.url=https://sonarcloud.io \
      -Dsonar.login=${{ secrets.SONAR_TOKEN }} \
      -Dsonar.qualitygate.wait=true \
      -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
      -Dsonar.java.binaries=target/classes
```

#### What SonarCloud Analyzes

**1. Code Quality Metrics**
- **Code Smells**: Maintainability issues (e.g., complex methods, duplicated code)
- **Technical Debt**: Estimated time to fix all issues
- **Duplications**: Repeated code blocks across the project
- **Complexity**: Cyclomatic complexity of methods and classes

**2. Security Vulnerabilities**
- **Security Hotspots**: Code patterns that may lead to vulnerabilities
- **Vulnerabilities**: Known security issues (SQL injection, XSS, etc.)
- **OWASP Top 10**: Coverage of common web application vulnerabilities
- **CWE Coverage**: Common Weakness Enumeration patterns

**3. Code Coverage**
- **Line Coverage**: Percentage of code lines executed by tests
- **Branch Coverage**: Percentage of conditional branches tested
- **Condition Coverage**: Individual conditions in complex expressions
- **Minimum Threshold**: 80% coverage required (configured in pom.xml)

**4. Reliability Issues**
- **Bugs**: Code that will likely fail at runtime
- **Error-Prone Patterns**: Common programming mistakes
- **Null Pointer Issues**: Potential NullPointerExceptions

#### Quality Gate

The pipeline waits for SonarCloud's Quality Gate decision:
- **Pass**: Code meets all quality thresholds
- **Fail**: Pipeline stops if quality gate fails

**Default Quality Gate Conditions**:
- Coverage on new code ≥ 80%
- Duplicated lines on new code ≤ 3%
- Maintainability rating on new code ≥ A
- Reliability rating on new code ≥ A
- Security rating on new code ≥ A

#### View Results

**Dashboard**: https://sonarcloud.io/project/overview?id=rraoblr11_java-aks-devsecops-sample

---

### Step 4: OWASP Dependency Check

**Purpose**: Software Composition Analysis (SCA) - scan dependencies for known vulnerabilities.

```yaml
- name: OWASP Dependency Check
  continue-on-error: true
  run: |
    mvn org.owasp:dependency-check-maven:check \
      -DfailBuildOnCVSS=7 \
      -DnvdApiKey=${{ secrets.NVD_API_KEY }}
```

#### What It Does

**1. Dependency Analysis**
- Scans all Maven dependencies (direct and transitive)
- Identifies library versions and package coordinates
- Generates CPE (Common Platform Enumeration) identifiers

**2. Vulnerability Database**
- Queries **National Vulnerability Database (NVD)** via API
- Downloads latest CVE (Common Vulnerabilities and Exposures) data
- Matches dependencies against known vulnerabilities

**3. Severity Assessment**
- Uses **CVSS (Common Vulnerability Scoring System)** scores
- Categorizes vulnerabilities: Critical (9.0-10.0), High (7.0-8.9), Medium (4.0-6.9), Low (0.1-3.9)
- Configured to fail on CVSS ≥ 7.0 (High and Critical)

**4. Report Generation**
- Creates detailed HTML report with all findings
- Lists affected dependencies and CVE details
- Provides remediation recommendations

#### Example Vulnerabilities Detected

```
spring-core-6.1.2.jar:
  - CVE-2024-22233 (CVSS: 7.5) - Denial of Service
  - CVE-2024-22259 (CVSS: 8.1) - Security Bypass

tomcat-embed-core-10.1.17.jar:
  - CVE-2024-56337 (CVSS: 9.8) - Remote Code Execution
  - CVE-2025-55754 (CVSS: 9.6) - Authentication Bypass
  - [Multiple other CVEs]
```

#### Configuration

- **continue-on-error: true**: Allows pipeline to continue even with vulnerabilities (for demo purposes)
- **NVD API Key**: Required to avoid rate limiting (429 errors)
- **Threshold**: CVSS ≥ 7.0 triggers failure

---

### Step 5: Upload Dependency Check Results

**Purpose**: Store vulnerability report as pipeline artifact.

```yaml
- name: Upload Dependency Check Results
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: dependency-check-report
    path: target/dependency-check-report.html
```

**Artifact Details**:
- **Format**: HTML report
- **Location**: Available in GitHub Actions workflow run
- **Retention**: 90 days (GitHub default)
- **Access**: Download from Actions tab → Workflow run → Artifacts

---

### Step 6: GitLeaks Secret Scan

**Purpose**: Detect hardcoded secrets, API keys, passwords, and tokens in code.

```yaml
- name: GitLeaks Secret Scan
  uses: gitleaks/gitleaks-action@v2
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
    GITLEAKS_LICENSE: ${{ secrets.GITLEAKS_LICENSE }}
```

#### What GitLeaks Detects

**1. Credentials**
- AWS Access Keys & Secret Keys
- Azure Service Principal credentials
- Google Cloud Platform keys
- Database passwords

**2. API Tokens**
- GitHub Personal Access Tokens
- GitLab tokens
- Slack webhooks
- Stripe API keys

**3. Private Keys**
- RSA private keys
- SSH private keys
- PGP private keys
- TLS/SSL certificates

**4. Application Secrets**
- JWT tokens
- OAuth secrets
- Encryption keys
- Session tokens

#### Scanning Method

- **Git History Scan**: Analyzes all commits, not just current code
- **Regex Patterns**: Uses 100+ pre-configured patterns
- **Entropy Analysis**: Detects high-entropy strings (random-looking secrets)
- **SARIF Output**: Results uploaded to GitHub Security tab

#### Configuration

- **Exit Code**: 2 if secrets found (fails the pipeline)
- **Redaction**: Secrets are redacted in logs for security
- **Report Format**: SARIF (Static Analysis Results Interchange Format)

#### Example Detection

```
Finding: AWS Access Key
File: src/main/resources/application.properties
Line: 15
Secret: AKIA****************
Commit: abc123def456
```

---

### Step 7: License Compliance Check

**Purpose**: Identify and document third-party library licenses.

```yaml
- name: License Compliance Check
  run: |
    mvn license:add-third-party
    mvn license:aggregate-download-licenses
```

#### What It Does

**1. License Identification**
- Scans all Maven dependencies
- Extracts license information from POM files
- Identifies license types (Apache 2.0, MIT, GPL, etc.)

**2. License Aggregation**
- Creates consolidated list of all third-party licenses
- Groups dependencies by license type
- Generates THIRD-PARTY.txt file

**3. License Download**
- Downloads full license text files
- Stores in `target/generated-resources/licenses/`
- Ensures compliance documentation is available

#### Common Licenses Detected

- **Apache License 2.0**: Spring Framework, Tomcat, Jackson
- **MIT License**: SLF4J, Logback
- **Eclipse Public License**: JUnit
- **LGPL**: Hibernate (if used)

#### Compliance Considerations

**Permissive Licenses** (Safe for commercial use):
- Apache 2.0
- MIT
- BSD

**Copyleft Licenses** (Require source disclosure):
- GPL (GNU General Public License)
- LGPL (Lesser GPL)
- AGPL (Affero GPL)

---

### Step 8: Upload License Report

**Purpose**: Store license compliance report as artifact.

```yaml
- name: Upload License Report
  uses: actions/upload-artifact@v4
  with:
    name: license-report
    path: target/generated-resources/licenses/
```

**Artifact Contents**:
- THIRD-PARTY.txt (list of all dependencies and licenses)
- Individual license text files
- License summary report

---

## Tools & Technologies

### 1. SonarCloud

**Type**: Cloud-based SAST platform  
**Website**: https://sonarcloud.io  
**Pricing**: Free for public repositories  
**Languages Supported**: Java, JavaScript, Python, C#, and 25+ more  

**Key Features**:
- Quality Gate enforcement
- Pull request decoration
- Historical trend analysis
- Security hotspot detection
- Code smell identification

---

### 2. OWASP Dependency-Check

**Type**: Software Composition Analysis (SCA) tool  
**Website**: https://owasp.org/www-project-dependency-check/  
**License**: Apache 2.0 (Open Source)  
**Database**: National Vulnerability Database (NVD)  

**Key Features**:
- CVE detection in dependencies
- CVSS scoring
- CPE identification
- Multiple report formats (HTML, XML, JSON)
- Suppression file support

---

### 3. GitLeaks

**Type**: Secret scanning tool  
**Website**: https://github.com/gitleaks/gitleaks  
**License**: MIT (Open Source)  
**Detection Method**: Regex + Entropy analysis  

**Key Features**:
- 100+ secret patterns
- Git history scanning
- SARIF output format
- GitHub Security integration
- Custom rule support

---

### 4. Maven License Plugin

**Type**: License compliance tool  
**Website**: https://www.mojohaus.org/license-maven-plugin/  
**License**: Apache 2.0  
**Purpose**: Third-party license management  

**Key Features**:
- License aggregation
- License text download
- Missing license detection
- Custom license mapping

---

## Secrets Required

### 1. SONAR_TOKEN

**Purpose**: Authenticate with SonarCloud  
**How to Get**:
1. Go to https://sonarcloud.io
2. Account → Security → Generate Token
3. Add to GitHub Secrets as `SONAR_TOKEN`

**Permissions**: Execute analysis, read project settings

---

### 2. NVD_API_KEY

**Purpose**: Access National Vulnerability Database API  
**How to Get**:
1. Go to https://nvd.nist.gov/developers/request-an-api-key
2. Fill out request form
3. Receive key via email
4. Add to GitHub Secrets as `NVD_API_KEY`

**Rate Limits**:
- Without key: 5 requests per 30 seconds
- With key: 50 requests per 30 seconds

---

### 3. GITHUB_TOKEN

**Purpose**: Authenticate GitHub Actions  
**How to Get**: Automatically provided by GitHub Actions  
**Permissions**: Read repository, write security events

---

## Artifacts Generated

### 1. Dependency Check Report

**File**: `dependency-check-report.html`  
**Size**: ~500 KB - 2 MB  
**Format**: HTML with embedded CSS  
**Retention**: 90 days  

**Contents**:
- Summary of vulnerabilities by severity
- Detailed CVE information
- Affected dependencies list
- Remediation recommendations

---

### 2. License Report

**Files**: Multiple license text files  
**Size**: ~100 KB  
**Format**: Plain text  
**Retention**: 90 days  

**Contents**:
- THIRD-PARTY.txt (dependency list)
- Individual license files (Apache-2.0.txt, MIT.txt, etc.)
- License summary

---

### 3. GitLeaks Results

**File**: `results.sarif`  
**Size**: ~10 KB  
**Format**: SARIF (JSON)  
**Retention**: 90 days  

**Contents**:
- Secret findings with locations
- Commit hashes where secrets appear
- Severity levels
- Remediation guidance

---

## Success Criteria

### Job Passes When:

✅ **SonarCloud Quality Gate**: PASSED  
✅ **GitLeaks**: No secrets detected  
✅ **License Check**: Completed successfully  
✅ **OWASP Dependency Check**: Completed (even with vulnerabilities due to `continue-on-error: true`)  

### Job Fails When:

❌ **SonarCloud Quality Gate**: FAILED  
❌ **GitLeaks**: Secrets detected in code  
❌ **Maven Build**: Compilation or test failures  
❌ **Code Coverage**: Below 80% threshold  

---

## Troubleshooting

### Issue 1: SonarCloud Connection Error

**Error**: `SonarQube server [https://sonarcloud.io] can not be reached`

**Solutions**:
1. Verify `SONAR_TOKEN` secret is set correctly
2. Check `sonar.organization` matches your SonarCloud organization
3. Ensure `sonar.host.url` is `https://sonarcloud.io`
4. Verify project exists in SonarCloud

---

### Issue 2: OWASP NVD Rate Limiting

**Error**: `NVD Returned Status Code: 429`

**Solutions**:
1. Add `NVD_API_KEY` secret to GitHub
2. Request API key from https://nvd.nist.gov/developers/request-an-api-key
3. Wait 30 seconds and retry (rate limit reset)

---

### Issue 3: GitLeaks False Positives

**Error**: GitLeaks detects test data as secrets

**Solutions**:
1. Create `.gitleaks.toml` configuration file
2. Add allowlist patterns for test files
3. Use `gitleaks:allow` comment in code

Example `.gitleaks.toml`:
```toml
[allowlist]
paths = [
  "src/test/resources/.*",
  ".*_test.go"
]
```

---

### Issue 4: Code Coverage Below Threshold

**Error**: `Coverage is 75.2%, but expected minimum is 80%`

**Solutions**:
1. Add more unit tests for uncovered code
2. Review JaCoCo report: `target/site/jacoco/index.html`
3. Identify uncovered classes/methods
4. Write tests for critical business logic first

---

### Issue 5: Dependency Vulnerabilities

**Error**: `One or more dependencies were identified with vulnerabilities`

**Solutions**:
1. Review `dependency-check-report.html` artifact
2. Update vulnerable dependencies in `pom.xml`
3. Check for newer versions: `mvn versions:display-dependency-updates`
4. If no fix available, add suppression file (temporary)

Example suppression:
```xml
<!-- .dependency-check-suppressions.xml -->
<suppressions>
  <suppress>
    <cve>CVE-2024-12345</cve>
    <notes>False positive - not applicable to our usage</notes>
  </suppress>
</suppressions>
```

---

## Performance Metrics

### Typical Execution Times

| Step | Duration | Notes |
|------|----------|-------|
| Checkout Code | 5-10s | Depends on repository size |
| Setup Java | 10-15s | Faster with cache hit |
| Maven Build & Tests | 30-60s | Includes compilation and test execution |
| SonarCloud Scan | 60-90s | Includes upload and quality gate wait |
| OWASP Dependency Check | 120-240s | First run downloads NVD database |
| GitLeaks Scan | 10-20s | Depends on Git history size |
| License Check | 5-10s | Fast for small projects |

**Total Job Duration**: 5-8 minutes (typical)

---

## Best Practices

### 1. Quality Gate Configuration

- Set realistic thresholds based on project maturity
- Focus on "new code" metrics for legacy projects
- Gradually increase coverage requirements
- Review and adjust thresholds quarterly

### 2. Dependency Management

- Keep dependencies up to date
- Use Dependabot for automated updates
- Review security advisories regularly
- Pin major versions, allow minor/patch updates

### 3. Secret Management

- Never commit secrets to Git
- Use environment variables or secret managers
- Rotate secrets regularly
- Scan before pushing (pre-commit hooks)

### 4. License Compliance

- Maintain approved license list
- Review new dependencies before adding
- Document license obligations
- Consult legal for GPL/AGPL licenses

---

## Integration with GitHub

### Security Tab Integration

Results from this job appear in:
- **Code Scanning Alerts**: SonarCloud and GitLeaks findings
- **Dependabot Alerts**: Dependency vulnerabilities
- **Secret Scanning**: GitLeaks results

### Pull Request Integration

- SonarCloud comments on PRs with quality metrics
- GitLeaks blocks PRs if secrets detected
- Coverage reports shown in PR checks
- Quality gate status visible in PR status checks

---

## Continuous Improvement

### Metrics to Track

1. **Quality Gate Pass Rate**: Target 95%+
2. **Code Coverage Trend**: Increasing over time
3. **Vulnerability Detection Time**: Time from CVE publication to detection
4. **False Positive Rate**: GitLeaks and OWASP suppressions
5. **Build Duration**: Optimize for faster feedback

### Regular Reviews

- **Weekly**: Review new vulnerabilities and update dependencies
- **Monthly**: Analyze quality trends and adjust thresholds
- **Quarterly**: Update scanning tools and configurations
- **Annually**: Review and update security policies

---

## References

### Documentation

- **SonarCloud**: https://docs.sonarcloud.io/
- **OWASP Dependency-Check**: https://jeremylong.github.io/DependencyCheck/
- **GitLeaks**: https://github.com/gitleaks/gitleaks
- **Maven License Plugin**: https://www.mojohaus.org/license-maven-plugin/

### Standards & Frameworks

- **OWASP Top 10**: https://owasp.org/www-project-top-ten/
- **CWE**: https://cwe.mitre.org/
- **CVSS**: https://www.first.org/cvss/
- **SARIF**: https://sarifweb.azurewebsites.net/

---

## Conclusion

Job 1 provides comprehensive security and quality analysis before any code is built or deployed. It implements multiple layers of defense:

1. **SAST** (SonarCloud) - Find code-level vulnerabilities
2. **SCA** (OWASP) - Detect vulnerable dependencies
3. **Secret Scanning** (GitLeaks) - Prevent credential leaks
4. **License Compliance** - Ensure legal compliance

This multi-layered approach ensures that security issues are caught early in the development lifecycle, following the "shift-left" security principle.

---

**Document Version**: 1.0  
**Last Updated**: January 18, 2026  
**Maintained By**: DevSecOps Team  
**Project**: Java AKS DevSecOps Sample

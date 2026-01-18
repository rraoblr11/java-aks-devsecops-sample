# Job 2: Build & Unit Tests - Detailed Documentation

## Overview

This document provides comprehensive details about **Job 2: Build & Unit Tests** in the Java AKS DevSecOps CI/CD pipeline. This job compiles the application, executes unit and integration tests, generates code coverage reports, and produces the deployable JAR artifact. It ensures code quality through automated testing before proceeding to containerization.

---

## Table of Contents

1. [Job Configuration](#job-configuration)
2. [Build & Test Steps](#build--test-steps)
3. [Testing Strategy](#testing-strategy)
4. [Code Coverage](#code-coverage)
5. [Artifacts Generated](#artifacts-generated)
6. [Success Criteria](#success-criteria)
7. [Troubleshooting](#troubleshooting)
8. [Best Practices](#best-practices)

---

## Job Configuration

### Basic Settings

```yaml
job-name: build-and-test
name: Build & Unit Tests
runs-on: ubuntu-latest
needs: code-security-scan
```

### Dependencies

**Depends On**: `code-security-scan` (Job 1)
- This job only runs if Job 1 passes successfully
- Ensures code quality gates are met before building

### Permissions

```yaml
permissions:
  contents: read    # Read repository contents
  checks: write     # Write test results to GitHub Checks
```

### Execution Context

- **Triggers**: Automatically after Job 1 completes successfully
- **Estimated Duration**: 2-4 minutes
- **Parallel Execution**: No (sequential after Job 1)

---

## Build & Test Steps

### Step 1: Checkout Code

**Purpose**: Clone the repository for building and testing.

```yaml
- name: Checkout Code
  uses: actions/checkout@v4
```

**Details**:
- Uses GitHub's official checkout action
- Clones the repository at the commit that triggered the workflow
- No full history needed (unlike Job 1 which needs it for SonarCloud)
- Shallow clone for faster execution

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

#### Configuration Details

**Java Version**: 17 (LTS - Long Term Support)
- Released September 2021
- Supported until September 2029
- Features: Records, Sealed Classes, Pattern Matching

**Distribution**: Eclipse Temurin
- Formerly AdoptOpenJDK
- TCK-certified OpenJDK builds
- Production-ready and widely used

**Maven Cache**: Enabled
- Caches `~/.m2/repository` directory
- Speeds up subsequent builds (2-3x faster)
- Cache key based on `pom.xml` hash
- Automatically invalidated when dependencies change

#### Cache Benefits

**First Run** (Cold Cache):
- Downloads all dependencies (~150 MB)
- Duration: 60-90 seconds

**Subsequent Runs** (Warm Cache):
- Reuses cached dependencies
- Duration: 10-15 seconds
- **Savings**: 45-75 seconds per build

---

### Step 3: Build with Maven

**Purpose**: Compile the application without running tests.

```yaml
- name: Build with Maven
  run: |
    mvn clean package -DskipTests
```

#### What This Does

**Maven Lifecycle Phases Executed**:
1. **clean**: Removes `target/` directory
2. **validate**: Validates project structure
3. **compile**: Compiles source code (`src/main/java`)
4. **process-resources**: Copies resources to `target/classes`
5. **package**: Creates JAR file (tests skipped)

#### Build Output

**Generated Files**:
- `target/classes/` - Compiled `.class` files
- `target/java-app-1.0.0.jar` - Executable JAR
- `target/java-app-1.0.0.jar.original` - JAR without Spring Boot loader

**JAR Structure**:
```
java-app-1.0.0.jar
├── BOOT-INF/
│   ├── classes/          # Your compiled code
│   ├── lib/              # All dependencies (96 JARs)
│   └── classpath.idx     # Classpath index
├── META-INF/
│   ├── MANIFEST.MF       # JAR manifest
│   └── maven/            # Maven metadata
└── org/
    └── springframework/
        └── boot/
            └── loader/   # Spring Boot launcher
```

#### Why Skip Tests?

Tests are run separately in dedicated steps for:
- **Better visibility**: Separate test results
- **Faster feedback**: Build failures detected immediately
- **Granular control**: Unit and integration tests run independently
- **Clearer logs**: Build vs test failures are distinct

---

### Step 4: Run Unit Tests

**Purpose**: Execute fast, isolated unit tests.

```yaml
- name: Run Unit Tests
  run: |
    mvn test
```

#### What Maven Test Does

**Lifecycle Phases**:
1. **compile**: Compiles main code (already done)
2. **test-compile**: Compiles test code (`src/test/java`)
3. **test**: Runs unit tests with Surefire plugin

#### Test Execution

**Surefire Plugin Configuration** (from pom.xml):
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <includes>
      <include>**/*Test.java</include>
      <include>**/*Tests.java</include>
    </includes>
    <excludes>
      <exclude>**/*IT.java</exclude>
      <exclude>**/*IntegrationTest.java</exclude>
    </excludes>
  </configuration>
</plugin>
```

#### Unit Tests in Your Application

**Test Files**:
1. `UserControllerTest.java` - REST API endpoint tests
2. `UserServiceTest.java` - Business logic tests
3. `UserTest.java` - Entity validation tests

**Test Framework**: JUnit 5 (Jupiter)
**Mocking**: Mockito
**Assertions**: AssertJ

#### Example Unit Test

```java
@Test
void shouldCreateUserSuccessfully() {
    // Given
    User user = new User("John Doe", "john@example.com");
    when(userRepository.save(any(User.class))).thenReturn(user);
    
    // When
    User created = userService.createUser(user);
    
    // Then
    assertThat(created.getName()).isEqualTo("John Doe");
    verify(userRepository, times(1)).save(user);
}
```

#### Test Reports Generated

**Surefire Reports**:
- `target/surefire-reports/TEST-*.xml` - JUnit XML format
- `target/surefire-reports/*.txt` - Plain text summaries
- Console output with pass/fail status

**Typical Output**:
```
[INFO] Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

### Step 5: Run Integration Tests

**Purpose**: Execute slower, end-to-end integration tests.

```yaml
- name: Run Integration Tests
  run: |
    mvn verify -DskipUnitTests
```

#### What Maven Verify Does

**Lifecycle Phases**:
1. **integration-test**: Runs integration tests with Failsafe plugin
2. **verify**: Verifies integration test results

#### Integration Test Execution

**Failsafe Plugin Configuration** (from pom.xml):
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-failsafe-plugin</artifactId>
  <configuration>
    <includes>
      <include>**/*IT.java</include>
      <include>**/*IntegrationTest.java</include>
    </includes>
  </configuration>
  <executions>
    <execution>
      <goals>
        <goal>integration-test</goal>
        <goal>verify</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

#### Integration Tests in Your Application

**Test Files**:
1. `UserControllerIT.java` - Full HTTP request/response tests
2. `ApplicationIT.java` - Application context loading tests

**Test Framework**: Spring Boot Test
**HTTP Client**: REST Assured
**Test Annotations**:
- `@SpringBootTest` - Loads full application context
- `@AutoConfigureMockMvc` - Configures MockMvc
- `@TestPropertySource` - Overrides properties for testing

#### Example Integration Test

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class UserControllerIT {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void shouldGetAllUsers() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(greaterThan(0)));
    }
}
```

#### Why Separate Integration Tests?

**Unit Tests** (Fast - ~5 seconds):
- Test individual components in isolation
- Use mocks for dependencies
- No Spring context loading
- Run frequently during development

**Integration Tests** (Slower - ~30 seconds):
- Test components working together
- Load full Spring application context
- Test actual HTTP endpoints
- Run before commits/pushes

#### Test Reports Generated

**Failsafe Reports**:
- `target/failsafe-reports/TEST-*.xml` - JUnit XML format
- `target/failsafe-reports/*.txt` - Plain text summaries

---

### Step 6: Generate Code Coverage Report

**Purpose**: Measure test coverage using JaCoCo.

```yaml
- name: Generate Code Coverage Report
  run: |
    mvn jacoco:report
```

#### JaCoCo (Java Code Coverage)

**What It Does**:
- Instruments bytecode during test execution
- Tracks which lines/branches are executed
- Generates detailed coverage reports

#### JaCoCo Plugin Configuration (from pom.xml)

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.11</version>
  <executions>
    <execution>
      <goals>
        <goal>prepare-agent</goal>
      </goals>
    </execution>
    <execution>
      <id>report</id>
      <phase>test</phase>
      <goals>
        <goal>report</goal>
      </goals>
    </execution>
    <execution>
      <id>jacoco-check</id>
      <goals>
        <goal>check</goal>
      </goals>
      <configuration>
        <rules>
          <rule>
            <element>PACKAGE</element>
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
    </execution>
  </executions>
</plugin>
```

#### Coverage Metrics

**Line Coverage**:
- Percentage of code lines executed by tests
- **Minimum Required**: 80%

**Branch Coverage**:
- Percentage of conditional branches tested
- Includes if/else, switch, ternary operators

**Method Coverage**:
- Percentage of methods invoked by tests

**Class Coverage**:
- Percentage of classes with at least one method tested

#### Coverage Reports Generated

**HTML Report** (Human-readable):
- `target/site/jacoco/index.html` - Main coverage dashboard
- `target/site/jacoco/com.example.javaapp/` - Package-level reports
- Color-coded: Green (covered), Red (not covered), Yellow (partially covered)

**XML Report** (Machine-readable):
- `target/site/jacoco/jacoco.xml` - Used by SonarCloud
- Standard JaCoCo XML format

**CSV Report**:
- `target/site/jacoco/jacoco.csv` - Coverage data in CSV format

#### Example Coverage Report

```
Package                Coverage
com.example.javaapp    87%
├── controller         92%
├── service            85%
├── model              78%
└── config             95%
```

#### Coverage Enforcement

**Build Fails If**:
- Line coverage < 80%
- Configured in `jacoco-check` execution

**Example Failure**:
```
[ERROR] Rule violated for package com.example.javaapp.service:
        lines covered ratio is 0.75, but expected minimum is 0.80
```

---

### Step 7: Publish Test Results

**Purpose**: Display test results in GitHub UI.

```yaml
- name: Publish Test Results
  uses: EnricoMi/publish-unit-test-result-action@v2
  if: always()
  with:
    files: |
      target/surefire-reports/*.xml
      target/failsafe-reports/*.xml
```

#### What This Action Does

**Features**:
1. **Parses JUnit XML** reports from Surefire and Failsafe
2. **Creates GitHub Check** with test summary
3. **Comments on Pull Requests** with test results
4. **Shows Test Trends** over time
5. **Highlights Failures** with detailed error messages

#### GitHub Check Output

**Summary Section**:
```
✅ 15 tests passed
❌ 2 tests failed
⏭️ 1 test skipped

Duration: 45 seconds
```

**Detailed Results**:
- List of all tests with pass/fail status
- Failure messages and stack traces
- Links to specific test files

#### Pull Request Comment

```markdown
## Test Results

📊 **15 tests** ✅ **13 passed** ❌ **2 failed** ⏭️ **0 skipped**

### Failed Tests
- `UserControllerTest.shouldValidateEmail` - AssertionError: Expected valid email
- `UserServiceTest.shouldHandleNullInput` - NullPointerException

[View full report →](link-to-check)
```

#### Why `if: always()`?

This step runs **even if previous steps fail**, ensuring:
- Test results are published even when tests fail
- Developers see which tests failed
- Build doesn't silently fail without feedback

---

### Step 8: Upload Build Artifact

**Purpose**: Store the JAR file for use in subsequent jobs.

```yaml
- name: Upload Build Artifact
  uses: actions/upload-artifact@v4
  with:
    name: java-app-jar
    path: target/*.jar
    retention-days: 7
```

#### Artifact Details

**Artifact Name**: `java-app-jar`
**Contents**: 
- `java-app-1.0.0.jar` - Executable Spring Boot JAR (~50 MB)
- `java-app-1.0.0.jar.original` - Original JAR without dependencies (~50 KB)

**Retention**: 7 days
- Artifacts automatically deleted after 7 days
- Reduces storage costs
- Sufficient for CI/CD pipeline needs

#### Why Upload as Artifact?

**Benefits**:
1. **Job Isolation**: Each job runs in a fresh environment
2. **Reusability**: Job 3 (Docker build) downloads this artifact
3. **Efficiency**: Avoid rebuilding in subsequent jobs
4. **Traceability**: Artifact linked to specific commit/build

#### Artifact Usage in Job 3

```yaml
- name: Download Build Artifact
  uses: actions/download-artifact@v4
  with:
    name: java-app-jar
    path: target/
```

Job 3 downloads the JAR to build the Docker image, avoiding a full Maven rebuild.

---

## Testing Strategy

### Test Pyramid

Your application follows the **Test Pyramid** best practice:

```
        /\
       /  \  E2E Tests (Manual/Future)
      /____\
     /      \
    / Integ. \ Integration Tests (~20%)
   /__________\
  /            \
 /  Unit Tests  \ Unit Tests (~80%)
/________________\
```

### Test Types

#### 1. Unit Tests (Fast, Isolated)

**Purpose**: Test individual components in isolation

**Characteristics**:
- **Speed**: < 1 second per test
- **Dependencies**: Mocked
- **Scope**: Single class/method
- **Quantity**: ~80% of all tests

**Examples**:
- `UserServiceTest` - Business logic validation
- `UserTest` - Entity validation rules
- `ValidationUtilsTest` - Utility method testing

**Mocking Strategy**:
```java
@Mock
private UserRepository userRepository;

@InjectMocks
private UserService userService;
```

---

#### 2. Integration Tests (Slower, Realistic)

**Purpose**: Test components working together

**Characteristics**:
- **Speed**: 5-10 seconds per test
- **Dependencies**: Real Spring context
- **Scope**: Multiple components
- **Quantity**: ~20% of all tests

**Examples**:
- `UserControllerIT` - HTTP endpoint testing
- `ApplicationIT` - Application startup validation

**Spring Boot Test Configuration**:
```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application-test.properties")
```

---

#### 3. Smoke Tests (Quick Validation)

**Purpose**: Verify application starts correctly

**Example**:
```java
@Test
void contextLoads() {
    // Verifies Spring context loads without errors
}
```

---

### Test Coverage Goals

**Minimum Requirements**:
- **Overall Coverage**: 80%
- **Controller Layer**: 90%+ (critical user-facing code)
- **Service Layer**: 85%+ (business logic)
- **Model Layer**: 75%+ (simple POJOs)
- **Configuration**: 95%+ (critical infrastructure)

**What to Test**:
- ✅ Business logic
- ✅ API endpoints
- ✅ Validation rules
- ✅ Error handling
- ✅ Edge cases

**What NOT to Test**:
- ❌ Getters/setters (unless they have logic)
- ❌ Framework code (Spring, JUnit)
- ❌ Third-party libraries
- ❌ Configuration classes (unless complex)

---

## Code Coverage

### JaCoCo Coverage Analysis

#### Coverage Counters

**1. Line Coverage**
- Most common metric
- Measures executable lines
- **Formula**: `covered_lines / total_lines`

**2. Branch Coverage**
- Measures conditional logic
- Includes if/else, switch, loops
- **Formula**: `covered_branches / total_branches`

**3. Instruction Coverage**
- Bytecode-level coverage
- Most granular metric
- Useful for compiler-generated code

**4. Complexity Coverage**
- Based on cyclomatic complexity
- Measures decision points
- Higher complexity = more test cases needed

---

### Coverage Report Structure

#### HTML Report Navigation

**Main Page** (`index.html`):
```
Overall Coverage: 87%
├── com.example.javaapp (87%)
│   ├── controller (92%)
│   ├── service (85%)
│   ├── model (78%)
│   └── config (95%)
```

**Package Page**:
- Lists all classes in package
- Shows coverage per class
- Color-coded indicators

**Class Page**:
- Source code with coverage highlighting
- Green: Fully covered
- Yellow: Partially covered (branches)
- Red: Not covered

**Method Page**:
- Detailed method coverage
- Branch coverage breakdown
- Complexity metrics

---

### Coverage Thresholds

#### Configured in pom.xml

```xml
<rules>
  <rule>
    <element>PACKAGE</element>
    <limits>
      <limit>
        <counter>LINE</counter>
        <value>COVEREDRATIO</value>
        <minimum>0.80</minimum>
      </limit>
    </limits>
  </rule>
</rules>
```

#### Enforcement Levels

**Package Level**: 80% minimum
- Allows flexibility per package
- Some packages may be higher/lower
- Average must meet threshold

**Alternative Configurations**:
```xml
<!-- Class-level enforcement -->
<element>CLASS</element>
<minimum>0.75</minimum>

<!-- Method-level enforcement -->
<element>METHOD</element>
<minimum>0.70</minimum>
```

---

### Improving Coverage

#### Strategies

**1. Identify Uncovered Code**
- Review JaCoCo HTML report
- Focus on red (uncovered) lines
- Prioritize critical business logic

**2. Write Missing Tests**
```java
// Before: Uncovered exception handling
public User getUser(Long id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new UserNotFoundException(id));
}

// After: Add test for exception case
@Test
void shouldThrowExceptionWhenUserNotFound() {
    when(userRepository.findById(999L))
        .thenReturn(Optional.empty());
    
    assertThatThrownBy(() -> userService.getUser(999L))
        .isInstanceOf(UserNotFoundException.class);
}
```

**3. Test Edge Cases**
- Null inputs
- Empty collections
- Boundary values
- Invalid data

**4. Test Error Paths**
- Exception handling
- Validation failures
- Network errors
- Database failures

---

## Artifacts Generated

### 1. JAR File

**File**: `java-app-1.0.0.jar`
**Size**: ~50 MB (includes all dependencies)
**Type**: Executable Spring Boot JAR
**Retention**: 7 days

**Contents**:
- Compiled application code
- All 96 third-party dependencies
- Spring Boot loader
- Application properties
- Static resources

**Execution**:
```bash
java -jar java-app-1.0.0.jar
```

---

### 2. Test Reports

**Surefire Reports** (Unit Tests):
- `target/surefire-reports/TEST-*.xml`
- JUnit XML format
- Parsed by GitHub Actions

**Failsafe Reports** (Integration Tests):
- `target/failsafe-reports/TEST-*.xml`
- JUnit XML format
- Parsed by GitHub Actions

---

### 3. Coverage Reports

**JaCoCo HTML Report**:
- `target/site/jacoco/index.html`
- Interactive coverage dashboard
- Not uploaded as artifact (too large)

**JaCoCo XML Report**:
- `target/site/jacoco/jacoco.xml`
- Used by SonarCloud in Job 1
- Machine-readable format

---

## Success Criteria

### Job Passes When:

✅ **Maven Build**: Successful compilation  
✅ **Unit Tests**: All tests pass  
✅ **Integration Tests**: All tests pass  
✅ **Code Coverage**: ≥ 80% line coverage  
✅ **JAR Creation**: Artifact successfully created  

### Job Fails When:

❌ **Compilation Error**: Syntax errors, missing dependencies  
❌ **Test Failure**: Any unit or integration test fails  
❌ **Coverage Below Threshold**: < 80% line coverage  
❌ **Build Timeout**: Exceeds 60 minutes (GitHub limit)  

---

## Troubleshooting

### Issue 1: Compilation Errors

**Error**: `[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.11.0:compile`

**Common Causes**:
1. Syntax errors in Java code
2. Missing imports
3. Incompatible Java version
4. Missing dependencies in pom.xml

**Solutions**:
```bash
# Check Java version
mvn -version

# Update dependencies
mvn clean install -U

# Compile with debug output
mvn clean compile -X
```

---

### Issue 2: Test Failures

**Error**: `Tests run: 15, Failures: 2, Errors: 0, Skipped: 0`

**Common Causes**:
1. Assertion failures (expected vs actual mismatch)
2. NullPointerException (missing mocks)
3. Timeout (slow tests)
4. Environment-specific issues

**Solutions**:

**View Detailed Error**:
```bash
# Run specific test
mvn test -Dtest=UserControllerTest#shouldCreateUser

# Run with stack traces
mvn test -e
```

**Debug Test**:
```java
@Test
void shouldCreateUser() {
    // Add debug logging
    System.out.println("User: " + user);
    
    // Add detailed assertions
    assertThat(result)
        .as("User should be created with correct name")
        .extracting(User::getName)
        .isEqualTo("John Doe");
}
```

---

### Issue 3: Coverage Below Threshold

**Error**: `Rule violated for package: lines covered ratio is 0.75, but expected minimum is 0.80`

**Solutions**:

**1. Identify Uncovered Code**:
- Open `target/site/jacoco/index.html`
- Navigate to package with low coverage
- Review red (uncovered) lines

**2. Write Missing Tests**:
```java
// Cover the uncovered branch
@Test
void shouldHandleInvalidEmail() {
    User user = new User("John", "invalid-email");
    
    assertThatThrownBy(() -> userService.createUser(user))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("Invalid email format");
}
```

**3. Exclude Non-Critical Code** (if appropriate):
```xml
<configuration>
  <excludes>
    <exclude>**/config/**</exclude>
    <exclude>**/dto/**</exclude>
  </excludes>
</configuration>
```

---

### Issue 4: Maven Dependency Issues

**Error**: `Could not resolve dependencies for project`

**Solutions**:

**Clear Local Cache**:
```bash
rm -rf ~/.m2/repository
mvn clean install
```

**Force Update**:
```bash
mvn clean install -U
```

**Check Dependency Tree**:
```bash
mvn dependency:tree
```

---

### Issue 5: Out of Memory

**Error**: `java.lang.OutOfMemoryError: Java heap space`

**Solutions**:

**Increase Maven Memory**:
```yaml
env:
  MAVEN_OPTS: '-Xmx3072m'  # Increase from default 512m
```

**Optimize Tests**:
```java
// Use @DirtiesContext sparingly (reloads Spring context)
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)

// Reuse Spring context across tests
@SpringBootTest
```

---

### Issue 6: Slow Tests

**Symptom**: Build takes > 5 minutes

**Solutions**:

**1. Profile Test Execution**:
```bash
mvn test -Dsurefire.reportFormat=plain
```

**2. Parallelize Tests**:
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <parallel>classes</parallel>
    <threadCount>4</threadCount>
  </configuration>
</plugin>
```

**3. Optimize Integration Tests**:
```java
// Use @MockBean instead of loading full context
@MockBean
private ExternalService externalService;

// Use test slices for specific layers
@WebMvcTest(UserController.class)  // Only loads web layer
```

---

## Best Practices

### 1. Test Naming Conventions

**Unit Tests**:
- `*Test.java` - Picked up by Surefire
- Example: `UserServiceTest.java`

**Integration Tests**:
- `*IT.java` or `*IntegrationTest.java` - Picked up by Failsafe
- Example: `UserControllerIT.java`

**Test Method Names**:
```java
// Good: Descriptive, follows pattern
@Test
void shouldCreateUserWhenValidDataProvided() { }

@Test
void shouldThrowExceptionWhenEmailIsInvalid() { }

// Bad: Vague, unclear intent
@Test
void test1() { }

@Test
void testUser() { }
```

---

### 2. Test Structure (Given-When-Then)

```java
@Test
void shouldCalculateDiscountForPremiumUser() {
    // Given (Arrange)
    User user = new User("John", "john@example.com");
    user.setPremium(true);
    Order order = new Order(100.0);
    
    // When (Act)
    double discount = discountService.calculate(user, order);
    
    // Then (Assert)
    assertThat(discount).isEqualTo(10.0);
}
```

---

### 3. Use AssertJ for Fluent Assertions

```java
// Good: Fluent, readable
assertThat(user)
    .isNotNull()
    .extracting(User::getName, User::getEmail)
    .containsExactly("John Doe", "john@example.com");

// Bad: Less readable
assertNotNull(user);
assertEquals("John Doe", user.getName());
assertEquals("john@example.com", user.getEmail());
```

---

### 4. Mock External Dependencies

```java
@Mock
private UserRepository userRepository;

@Mock
private EmailService emailService;

@InjectMocks
private UserService userService;

@Test
void shouldSendWelcomeEmailAfterUserCreation() {
    // Given
    User user = new User("John", "john@example.com");
    when(userRepository.save(any())).thenReturn(user);
    
    // When
    userService.createUser(user);
    
    // Then
    verify(emailService).sendWelcomeEmail("john@example.com");
}
```

---

### 5. Test Data Builders

```java
// Create reusable test data builders
public class UserBuilder {
    private String name = "John Doe";
    private String email = "john@example.com";
    
    public UserBuilder withName(String name) {
        this.name = name;
        return this;
    }
    
    public UserBuilder withEmail(String email) {
        this.email = email;
        return this;
    }
    
    public User build() {
        return new User(name, email);
    }
}

// Use in tests
@Test
void shouldValidateUser() {
    User user = new UserBuilder()
        .withEmail("invalid-email")
        .build();
    
    assertThat(validator.isValid(user)).isFalse();
}
```

---

### 6. Parameterized Tests

```java
@ParameterizedTest
@ValueSource(strings = {"", " ", "  ", "\t", "\n"})
void shouldRejectBlankNames(String name) {
    User user = new User(name, "john@example.com");
    
    assertThatThrownBy(() -> userService.createUser(user))
        .isInstanceOf(ValidationException.class);
}

@ParameterizedTest
@CsvSource({
    "john@example.com, true",
    "invalid-email, false",
    "@example.com, false",
    "john@, false"
})
void shouldValidateEmail(String email, boolean expected) {
    boolean result = EmailValidator.isValid(email);
    assertThat(result).isEqualTo(expected);
}
```

---

### 7. Test Isolation

```java
// Good: Each test is independent
@BeforeEach
void setUp() {
    user = new User("John", "john@example.com");
}

@Test
void test1() {
    user.setName("Jane");
    // Test logic
}

@Test
void test2() {
    // user.getName() is still "John" (fresh instance)
    // Test logic
}

// Bad: Tests depend on execution order
private static User user;

@BeforeAll
static void setUp() {
    user = new User("John", "john@example.com");
}

@Test
void test1() {
    user.setName("Jane");  // Modifies shared state
}

@Test
void test2() {
    // user.getName() is now "Jane" (depends on test1)
}
```

---

### 8. Continuous Improvement

**Track Metrics**:
- Test execution time
- Code coverage trends
- Test failure rate
- Flaky test identification

**Regular Reviews**:
- Remove obsolete tests
- Refactor slow tests
- Update test data
- Improve assertions

**Test Maintenance**:
- Keep tests simple
- Avoid test duplication
- Update tests with code changes
- Document complex test scenarios

---

## Performance Metrics

### Typical Execution Times

| Step | Duration | Notes |
|------|----------|-------|
| Checkout Code | 5-10s | Depends on repo size |
| Setup Java | 10-15s | Faster with cache hit |
| Maven Build | 30-45s | Compilation + packaging |
| Unit Tests | 5-10s | Fast, isolated tests |
| Integration Tests | 20-30s | Loads Spring context |
| Coverage Report | 5-10s | JaCoCo analysis |
| Publish Results | 5-10s | Upload to GitHub |
| Upload Artifact | 10-15s | ~50 MB JAR file |

**Total Job Duration**: 2-4 minutes (typical)

---

## Integration with Pipeline

### Job Dependencies

```
Job 1: Code Security Scan (5-8 min)
         ↓
Job 2: Build & Unit Tests (2-4 min) ← YOU ARE HERE
         ↓
Job 3: Docker Build & Scan (3-5 min)
         ↓
Job 4+: Deployment Jobs
```

### Artifact Flow

```
Job 2 Produces:
├── java-app-1.0.0.jar → Used by Job 3 (Docker build)
├── Test Reports → Displayed in GitHub UI
└── Coverage Reports → Used by Job 1 (SonarCloud)
```

---

## References

### Documentation

- **Maven**: https://maven.apache.org/guides/
- **JUnit 5**: https://junit.org/junit5/docs/current/user-guide/
- **Mockito**: https://javadoc.io/doc/org.mockito/mockito-core/latest/
- **AssertJ**: https://assertj.github.io/doc/
- **JaCoCo**: https://www.jacoco.org/jacoco/trunk/doc/
- **Spring Boot Test**: https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing

### Best Practices

- **Test Pyramid**: https://martinfowler.com/articles/practical-test-pyramid.html
- **Unit Testing**: https://martinfowler.com/bliki/UnitTest.html
- **Test-Driven Development**: https://martinfowler.com/bliki/TestDrivenDevelopment.html

---

## Conclusion

Job 2 ensures code quality through comprehensive testing and coverage analysis. It produces a verified, tested JAR artifact that is ready for containerization in Job 3. The combination of unit tests, integration tests, and code coverage enforcement provides confidence that the application works correctly before deployment.

**Key Achievements**:
- ✅ Automated compilation and packaging
- ✅ Comprehensive test execution (unit + integration)
- ✅ 80%+ code coverage enforcement
- ✅ Verified JAR artifact for deployment
- ✅ Detailed test reporting in GitHub UI

---

**Document Version**: 1.0  
**Last Updated**: January 18, 2026  
**Maintained By**: DevSecOps Team  
**Project**: Java AKS DevSecOps Sample

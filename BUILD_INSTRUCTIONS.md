# Build Instructions

## Important: Use the Gradle Wrapper

**Always use `./gradlew` instead of `gradle`** to ensure you're using the correct Gradle version (8.14) that's compatible with this project.

## Prerequisites Setup

If you're using SDKMAN, make sure to activate the correct versions:

```bash
# Activate SDKMAN (if not already in your shell)
source ~/.sdkman/bin/sdkman-init.sh

# Use the required versions
sdk use java 11.0.28-tem
sdk use gradle 8.14
sdk use kotlin 1.8.0

# Verify Java is available
java -version  # Should show 11.0.28
```

## Building the Project

```bash
cd kotlin-sdk
./gradlew build
```

## First Time Setup

On first run, the Gradle wrapper will download Gradle 8.14 automatically. This may take a few minutes depending on your internet connection.

## Troubleshooting

If you see errors like `org/jetbrains/kotlin/gradle/plugin/KotlinBasePlugin`, make sure you're using:

1. **The Gradle wrapper** (`./gradlew`) instead of the system `gradle` command
2. **JDK 11** (check with `java -version`)
3. **Kotlin 1.8.0** (configured in `build.gradle.kts`)

## Verifying Your Setup

```bash
# Check Java version (should be 11.x)
java -version

# Check Gradle version via wrapper (should be 8.14)
./gradlew --version

# Build the project
./gradlew build
```

## Common Issues

### Issue: "gradle: command not found"
**Solution**: Use `./gradlew` instead. The wrapper is included in the project.

### Issue: "org/jetbrains/kotlin/gradle/plugin/KotlinBasePlugin"
**Solution**: 
1. Make sure you're using `./gradlew` not `gradle`
2. Delete `~/.gradle/caches` if the issue persists
3. Run `./gradlew clean build`

### Issue: Wrong Java version
**Solution**: Make sure JDK 11 is installed and set as default:
```bash
# Using SDKMAN (if installed)
sdk use java 11.0.28-tem

# Or set JAVA_HOME
export JAVA_HOME=/path/to/jdk11
```


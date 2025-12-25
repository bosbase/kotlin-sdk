plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.serialization") version "2.0.20"
    id("com.vanniktech.maven.publish") version "0.28.0"
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
    signing
}

group = "com.bosbase"
version = "0.1.11"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("com.squareup.okio:okio:3.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") // 2.0.20 推荐
    testImplementation(kotlin("test"))
    // ============ 关键：强制升级 BouncyCastle 支持 Ed25519 ============
    // implementation("org.bouncycastle:bcpg-jdk18on:1.78.1")
    // implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

// Configure GPG signing for Maven Central
// See: https://central.sonatype.org/publish/requirements/gpg/
val signingKeyId = System.getenv("SIGNING_KEY_ID")?.trim()
val signingKeyRaw = System.getenv("SIGNING_KEY")
val signingPassword = System.getenv("SIGNING_PASSWORD") ?: ""

// Clean the signing key: trim whitespace and ensure proper line endings
// Also handle cases where newlines might be escaped or missing
val signingKey = signingKeyRaw?.trim()?.let { raw ->
    // Replace escaped newlines (\n) with actual newlines
    var cleaned = raw.replace("\\n", "\n")
    // Normalize line endings
    cleaned = cleaned.replace("\r\n", "\n").replace("\r", "\n")
    // Ensure proper formatting - PGP keys need newlines
    // If the key doesn't have newlines but has the markers, try to fix it
    if (cleaned.contains("-----BEGIN") && !cleaned.contains("\n")) {
        // Key might have been exported without newlines - this is problematic
        logger.warn("WARNING: SIGNING_KEY appears to have no newlines. This may cause issues.")
        logger.warn("Consider using: export SIGNING_KEY=\"\$(cat private-key.asc)\"")
    }
    cleaned
}

val hasSigningCredentials = signingKeyId != null && signingKey != null && signingKey.isNotBlank()

signing {
    // Make signing optional - don't fail if credentials are not available
    isRequired = hasSigningCredentials

    if (hasSigningCredentials) {
        // At this point, we know signingKeyId and signingKey are not null due to hasSigningCredentials check
        val keyId = signingKeyId!!
        val key = signingKey!!

        // Validate key format
        if (!key.startsWith("-----BEGIN PGP")) {
            throw GradleException(
                "Invalid PGP key format. Expected ASCII-armored format starting with '-----BEGIN PGP PRIVATE KEY BLOCK-----'.\n" +
                "Current key starts with: ${key.take(50)}\n" +
                "Make sure SIGNING_KEY contains the full private key including BEGIN and END lines.\n" +
                "Tip: Use 'export SIGNING_KEY=\"\$(cat private-key.asc)\"' to preserve newlines.\n" +
                "See GET_SIGNING_CREDENTIALS.md for instructions."
            )
        }

        if (!key.contains("-----END PGP PRIVATE KEY BLOCK-----")) {
            throw GradleException(
                "Invalid PGP key format. Key must include '-----END PGP PRIVATE KEY BLOCK-----' marker.\n" +
                "Make sure you copied the ENTIRE key including both BEGIN and END lines.\n" +
                "See GET_SIGNING_CREDENTIALS.md for instructions."
            )
        }

        if (keyId.length != 8) {
            logger.warn("SIGNING_KEY_ID should be 8 characters (last 8 chars of your GPG key ID). Got: ${keyId.length} chars")
        }

        // Log key info for debugging (without exposing the actual key)
        logger.info("Attempting to configure PGP signing with key ID: $keyId")
        logger.info("Key length: ${key.length} characters")
        logger.info("Key starts with: ${key.take(40)}...")
        logger.info("Key ends with: ...${key.takeLast(40)}")

        // Additional validation: check key structure
        val keyLines = key.lines()
        if (keyLines.size < 5) {
            throw GradleException(
                "PGP key appears to be corrupted or incomplete. Expected multiple lines, got ${keyLines.size}.\n" +
                "Make sure the key includes all lines from BEGIN to END markers.\n" +
                "Export with: gpg --armor --export-secret-keys YOUR_KEY_ID"
            )
        }

        // Verify key has proper line structure (PGP keys should have base64-encoded lines)
        val base64Lines = keyLines.filter {
            it.isNotBlank() &&
            !it.startsWith("-----") &&
            !it.contains("Version:") &&
            !it.contains("Comment:")
        }
        if (base64Lines.isEmpty()) {
            throw GradleException(
                "PGP key appears to have no base64-encoded data lines.\n" +
                "The key should contain base64-encoded data between the BEGIN and END markers."
            )
        }

        // Check if key might be missing newlines (common issue with environment variables)
        if (!key.contains("\n") && key.length > 100) {
            throw GradleException(
                "PGP key appears to be missing newlines. This will cause 'Could not read PGP secret key' error.\n" +
                "Solution: Export the key properly with newlines:\n" +
                "  gpg --armor --export-secret-keys YOUR_KEY_ID | base64 -w 0  # For GitHub Secrets\n" +
                "  OR for local env: export SIGNING_KEY=\"\$(cat private-key.asc)\"\n" +
                "Make sure to preserve newlines when setting the environment variable."
            )
        }

        try {
            // GPG keys should be ASCII-armored format (starts with "-----BEGIN PGP PRIVATE KEY BLOCK-----")
            // GitHub Secrets typically store them as plain text, not base64 encoded
            useInMemoryPgpKeys(keyId, key, signingPassword)
            logger.info("PGP signing configured successfully with key ID: $keyId")
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            throw GradleException(
                "Failed to configure PGP signing: $errorMsg\n" +
                "\nCommon issues and solutions:\n" +
                "1. SIGNING_KEY_ID must be the last 8 characters of your GPG key ID\n" +
                "   Get it with: gpg --list-secret-keys --keyid-format LONG\n" +
                "2. SIGNING_KEY must be the full ASCII-armored private key (including BEGIN/END lines)\n" +
                "   Export with: gpg --armor --export-secret-keys YOUR_KEY_ID\n" +
                "   Set with: export SIGNING_KEY=\"\$(cat private-key.asc)\"\n" +
                "3. SIGNING_PASSWORD must match the passphrase used when creating the key\n" +
                "4. The key ID must match the actual key in SIGNING_KEY\n" +
                "5. If newlines are missing, the key may be corrupted\n" +
                "\nCurrent values:\n" +
                "  SIGNING_KEY_ID: ${if (signingKeyId != null) "SET (${signingKeyId.length} chars)" else "NOT SET"}\n" +
                "  SIGNING_KEY: ${if (signingKey != null) "SET (${signingKey.length} chars, starts with: ${signingKey.take(30)}...)" else "NOT SET"}\n" +
                "  SIGNING_PASSWORD: ${if (signingPassword.isNotEmpty()) "SET" else "NOT SET (may be required if key has passphrase)"}\n" +
                "\nSee GET_SIGNING_CREDENTIALS.md for detailed instructions.",
                e
            )
        }
    } else {
        logger.warn("GPG signing credentials not found. Signing will be skipped.")
        logger.warn("Set SIGNING_KEY_ID and SIGNING_KEY environment variables to enable signing.")
        if (signingKeyId == null) {
            logger.warn("  Missing: SIGNING_KEY_ID")
        }
        if (signingKey == null || signingKey.isBlank()) {
            logger.warn("  Missing: SIGNING_KEY")
        }
    }
}

// Diagnostic task to help debug PGP signing issues
tasks.register("validateSigningKey") {
    group = "verification"
    description = "Validates PGP signing key configuration without attempting to sign"

    doLast {
        val keyId = System.getenv("SIGNING_KEY_ID")?.trim()
        val keyRaw = System.getenv("SIGNING_KEY")
        val password = System.getenv("SIGNING_PASSWORD") ?: ""

        println("=== PGP Signing Key Validation ===\n")

        if (keyId == null) {
            println("❌ SIGNING_KEY_ID is not set")
        } else {
            println("✅ SIGNING_KEY_ID: $keyId (${keyId.length} chars)")
            if (keyId.length != 8) {
                println("   ⚠️  Warning: Should be 8 characters (last 8 chars of your GPG key ID)")
            }
        }

        if (keyRaw == null || keyRaw.isBlank()) {
            println("❌ SIGNING_KEY is not set")
        } else {
            val key = keyRaw.trim().replace("\\n", "\n").replace("\r\n", "\n").replace("\r", "\n")
            println("✅ SIGNING_KEY: Set (${key.length} chars)")

            // Validate format
            if (!key.startsWith("-----BEGIN PGP")) {
                println("   ❌ Invalid format: Should start with '-----BEGIN PGP PRIVATE KEY BLOCK-----'")
                println("   First 50 chars: ${key.take(50)}")
            } else {
                println("   ✅ Starts with correct marker")
            }

            if (!key.contains("-----END PGP PRIVATE KEY BLOCK-----")) {
                println("   ❌ Missing END marker: '-----END PGP PRIVATE KEY BLOCK-----'")
            } else {
                println("   ✅ Contains END marker")
            }

            val keyLines = key.lines()
            println("   Key has ${keyLines.size} lines")

            if (!key.contains("\n") && key.length > 100) {
                println("   ❌ CRITICAL: Key appears to be missing newlines!")
                println("   This will cause 'Could not read PGP secret key' error.")
                println("   Fix: export SIGNING_KEY=\"\$(cat private-key.asc)\"")
            } else {
                println("   ✅ Key has newlines")
            }

            val base64Lines = keyLines.filter {
                it.isNotBlank() &&
                !it.startsWith("-----") &&
                !it.contains("Version:") &&
                !it.contains("Comment:")
            }
            println("   Contains ${base64Lines.size} base64-encoded data lines")
        }

        if (password.isEmpty()) {
            println("⚠️  SIGNING_PASSWORD is not set (may be required if key has passphrase)")
        } else {
            println("✅ SIGNING_PASSWORD: Set")
        }

        println("\n=== Validation Complete ===")
        println("\nTo test signing configuration, run:")
        println("  ./gradlew validateSigningKey")
        println("\nIf validation passes but signing fails, try:")
        println("  1. Verify key ID matches: gpg --list-secret-keys --keyid-format LONG")
        println("  2. Re-export key: gpg --armor --export-secret-keys YOUR_KEY_ID > key.asc")
        println("  3. Check passphrase is correct")
    }
}

// Sign publications after they are created by the maven-publish plugin
// Only do this if credentials are available
if (hasSigningCredentials) {
    afterEvaluate {
        signing.sign(publishing.publications)
    }
}

mavenPublishing {
    coordinates(groupId = "com.bosbase", artifactId = "kotlin-sdk", version = version.toString())

    pom {
        name.set("BosBase Kotlin SDK")
        description.set("Official Kotlin SDK for interacting with the BosBase API. A coroutine-friendly wrapper built on OkHttp and kotlinx.serialization.")
        inceptionYear.set("2025")
        url.set("https://github.com/bosbase/kotlin-sdk")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("bosbase")
                name.set("Sam Chen")
                email.set("wmydz1@gmail.com")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/bosbase/kotlin-sdk.git")
            developerConnection.set("scm:git:ssh://github.com:bosbase/kotlin-sdk.git")
            url.set("https://github.com/bosbase/kotlin-sdk")
        }
    }
}

nexusPublishing {
    repositories {
        sonatype {
            // Updated for Central Publishing Portal (OSSRH EOL June 30, 2025)
            // Using OSSRH Staging API Service endpoint for compatibility with gradle-nexus.publish-plugin
            // See: https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            // Authentication: OSSRH Staging API requires Central Portal User Tokens
            // Generate token from https://central.sonatype.com (Account page -> Generate User Token)
            // IMPORTANT: If you previously published via OSSRH, you MUST replace your OSSRH token
            // with a Portal token. Publishing with an OSSRH token will result in 401 responses.
            // See: https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/#authentication
            username.set(System.getenv("OSSRH_USERNAME")) // Portal token username
            password.set(System.getenv("OSSRH_PASSWORD")) // Portal token password
        }
    }
}

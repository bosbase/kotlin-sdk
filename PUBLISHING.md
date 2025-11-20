# Publishing BosBase Kotlin SDK to Maven Central

This guide explains how to publish the BosBase Kotlin SDK to Maven Central (Sonatype Central Portal).

## Prerequisites

1. **Sonatype Central Portal Account**
   - Sign up at https://central.sonatype.com
   - Register a namespace (groupId) - `com.bosbase` (requires domain ownership or GitHub namespace)

2. **PGP Signing Key**
   - Generate a PGP key pair for signing artifacts using GPG
   - Upload the public key to a keyserver (e.g., keyserver.ubuntu.com)

3. **Environment Variables / GitHub Secrets**
   - `MAVEN_CENTRAL_USER` - Your Sonatype Central Portal username
   - `MAVEN_CENTRAL_TOKEN` - Your Sonatype Central Portal token
   - `SIGNING_KEY_ID` - Your PGP key ID (last 8 characters of the key fingerprint)
   - `SIGNING_KEY` - Your PGP private key (ASCII-armored, including BEGIN/END lines)
   - `SIGNING_PASSWORD` - Passphrase for your PGP key

## Generating PGP Key

If you don't have a PGP key, you can generate one using GPG:

```bash
gpg --full-generate-key
```

When prompted:
1. Choose `(1) RSA and RSA` (default)
2. Key size: `4096` bits
3. Expiration: Choose an expiration date (or `0` for no expiration)
4. Enter your name and email
5. Set a passphrase (you'll need this for signing)

After generation, get your key ID:
```bash
gpg --list-secret-keys --keyid-format LONG
```

Look for a line like:
```
sec   rsa4096/ABCD1234EFGH5678 2025-01-01 [SC]
```

The key ID is `ABCD1234EFGH5678` (the part after `rsa4096/`). You'll need the last 8 characters for `SIGNING_KEY_ID`.

Export your private key (for use in CI/CD):
```bash
gpg --armor --export-secret-keys YOUR_KEY_ID > private-key.asc
```

**Important**: Keep `private-key.asc` secure and never commit it to version control!

Upload your public key to a keyserver:
```bash
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

Or use another keyserver:
```bash
gpg --keyserver hkp://keyserver.ubuntu.com --send-keys YOUR_KEY_ID
gpg --keyserver hkp://pgp.mit.edu --send-keys YOUR_KEY_ID
```

## Local Publishing (Testing)

Test publishing to your local Maven repository:

```bash
cd kotlin-sdk
./gradlew publishToMavenLocal
```

This will publish to `~/.m2/repository/com/bosbase/bosbase-kotlin-sdk/`.

## Publishing to Maven Central

### Manual Publishing

1. **Update the version** in `build.gradle.kts`:
   ```kotlin
   version = "0.1.1"  // Update to your new version
   ```

2. **Set environment variables**:
   ```bash
   export MAVEN_CENTRAL_USER="your-username"
   export MAVEN_CENTRAL_TOKEN="your-token"
   export SIGNING_KEY_ID="YOUR_KEY_ID"
   export SIGNING_KEY="$(cat private-key.asc)"
   export SIGNING_PASSWORD="your-passphrase"
   ```

3. **Publish**:
   ```bash
   cd kotlin-sdk
   ./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
   ```

   Or for manual release (publish first, then release via portal):
   ```bash
   ./gradlew publishToSonatype
   # Then go to https://central.sonatype.com → Deployments → Release
   ```

### Manual Drop Upload (Central Portal UI)

If you prefer the Central Portal **Drop** experience instead of pushing with Gradle:

1. Build the bundle locally (this writes the Maven repository layout with the `.pom` file):
   ```bash
   ./gradlew clean createSonatypeDropBundle
   ```
2. Upload the generated `build/distributions/bosbase-kotlin-sdk-sonatype-bundle.zip` file on https://central.sonatype.com → Publish → Drop.

The task packages the jar, sources, Javadoc, and generated POM in the structure that Sonatype expects, so you won't see the `Bundle has content that does NOT have a .pom file` validation error.

### Automated Publishing via GitHub Actions

The repository includes a GitHub Actions workflow (`.github/workflows/publish-kotlin-sdk.yml`) that automatically publishes when you:

1. **Push a tag**:
   ```bash
   git tag kotlin-sdk/v0.1.1
   git push origin kotlin-sdk/v0.1.1
   ```

2. **Or trigger manually** via GitHub Actions UI with a version number

**Required GitHub Secrets:**
- `MAVEN_CENTRAL_USER`
- `MAVEN_CENTRAL_TOKEN`
- `SIGNING_KEY_ID`
- `SIGNING_KEY` (full private key, including BEGIN/END lines)
- `SIGNING_PASSWORD`

## After Publishing

1. **Check staging repository** at https://central.sonatype.com → Deployments
2. **Release** the staging repository (if not auto-released)
3. **Wait for sync** - Your library will appear on:
   - https://central.sonatype.com (within minutes)
   - https://mvnrepository.com (within hours/days)

## Verification

Once published, users can add the dependency:

```kotlin
dependencies {
    implementation("com.bosbase:bosbase-kotlin-sdk:0.1.1")
}
```

## Troubleshooting

### Signing Issues
- Ensure `SIGNING_KEY` includes the full ASCII-armored key with BEGIN/END lines
- Verify the key ID matches the last 8 characters of your key fingerprint
- Check that the passphrase is correct

### Publishing Issues
- Verify credentials are correct in Sonatype Central Portal
- Check that your namespace (groupId) is registered and verified
- Ensure PGP public key is uploaded to a keyserver
- If you are uploading through the Central Portal **Drop** UI, always upload the bundle created by `./gradlew createSonatypeDropBundle`. Uploading a raw JAR file will fail with `Bundle has content that does NOT have a .pom file`.

### Release Issues
- If auto-release fails, manually release via https://central.sonatype.com
- Check staging repository for validation errors

## References

- [Official JetBrains Guide](https://kotlinlang.org/docs/multiplatform-publish-libraries.html)
- [Sonatype Central Portal](https://central.sonatype.com)
- [Gradle Nexus Publish Plugin](https://github.com/gradle-nexus/publish-plugin)

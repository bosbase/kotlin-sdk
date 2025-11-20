# How to Get Signing Credentials for Maven Central Publishing

After generating your PGP key with `gpg --full-generate-key`, follow these steps to get the values needed for GitHub Secrets or environment variables.

## Step 1: Get SIGNING_KEY_ID

List your secret keys to find the key ID:

```bash
gpg --list-secret-keys --keyid-format LONG
```

You'll see output like:
```
sec   rsa4096/ABCD1234EFGH5678 2025-01-15 [SC]
      ABCDEF1234567890ABCDEF1234567890ABCD1234
uid                 [ultimate] Your Name <your.email@example.com>
ssb   rsa4096/XYZ9876WXYZ1234 2025-01-15 [E]
```

**SIGNING_KEY_ID** = The last 8 characters of the key ID (the part after `rsa4096/`)

In the example above:
- Full key ID: `ABCD1234EFGH5678`
- **SIGNING_KEY_ID**: `EFGH5678` (last 8 characters)

## Step 2: Get SIGNING_KEY (Private Key)

Export your private key in ASCII-armored format:

```bash
gpg --armor --export-secret-keys YOUR_KEY_ID > private-key.asc
```

Replace `YOUR_KEY_ID` with your actual key ID from Step 1 (the full key ID, not just the last 8 characters).

Then view the contents:
```bash
cat private-key.asc
```

**SIGNING_KEY** = The entire contents of the file, including:
- `-----BEGIN PGP PRIVATE KEY BLOCK-----`
- All the key data lines
- `-----END PGP PRIVATE KEY BLOCK-----`

**Important**: 
- Copy the ENTIRE output including the BEGIN and END lines
- This is your private key - keep it secure!
- Never commit this to version control

### Alternative: Get it directly without saving to file

```bash
gpg --armor --export-secret-keys YOUR_KEY_ID
```

Copy the entire output (including BEGIN/END lines) - that's your SIGNING_KEY.

## Step 3: Get SIGNING_PASSWORD

**SIGNING_PASSWORD** = The passphrase you entered when generating the PGP key with `gpg --full-generate-key`.

If you forgot your passphrase, you'll need to:
1. Generate a new PGP key, OR
2. If you have the key backed up, you can change the passphrase with:
   ```bash
   gpg --edit-key YOUR_KEY_ID
   # Then type: passwd
   # Follow the prompts to set a new passphrase
   ```

## Summary

After running the commands above, you'll have:

1. **SIGNING_KEY_ID**: Last 8 characters of your key ID (e.g., `EFGH5678`)
2. **SIGNING_KEY**: Full ASCII-armored private key (entire output including BEGIN/END lines)
3. **SIGNING_PASSWORD**: The passphrase you set when creating the key

## For GitHub Secrets

Add these as GitHub repository secrets:
- `SIGNING_KEY_ID` → Last 8 characters of key ID
- `SIGNING_KEY` → Full private key (including BEGIN/END lines)
- `SIGNING_PASSWORD` → Your passphrase

## For Local Testing

Set as environment variables:

```bash
export SIGNING_KEY_ID="EFGH5678"  # Your last 8 characters
export SIGNING_KEY="$(cat private-key.asc)"  # Full private key
export SIGNING_PASSWORD="your-passphrase"  # Your passphrase
```

## Security Reminder

⚠️ **Never commit your private key or passphrase to version control!**

The `private-key.asc` file should be:
- Added to `.gitignore`
- Stored securely (password manager, encrypted storage)
- Only used in CI/CD secrets or local environment variables


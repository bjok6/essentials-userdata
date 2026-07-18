Natives are not stored in git.

Configure GitHub Actions secrets (required for Release):
  NATIVE_AMD64_URL
  NATIVE_ARM64_URL

CI runs scripts/fetch-natives.sh and embeds linux-*.dat into the jar.  

#!/usr/bin/env bash
# Replace common hardcoded secrets in Spring Boot resources with environment placeholders.
# BACKUP: will create *.bak files alongside modified files.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "Scanning project for known hardcoded keys..."

# Patterns: key=VALUE -> key=${ENV_VAR}
declare -A replacements=(
  ["nvidia.api.key"]="NVIDIA_API_KEY"
  ["app.nvidia.api-key"]="NVIDIA_API_KEY"
  ["elevenlabs.api.key"]="ELEVENLABS_API_KEY"
  ["groq.api.key"]="GROQ_API_KEY"
  ["spring.mail.password"]="MAIL_PASSWORD"
)

find "$ROOT_DIR/backend" -type f \( -name "application.properties" -o -name "application.yml" -o -name "*.yml" \) | while read -r file; do
  for key in "${!replacements[@]}"; do
    envvar=${replacements[$key]}
    if grep -q "$key" "$file"; then
      echo "Patching $file -> substituting $key with \\${$envvar}"
      cp "$file" "$file.bak"
      # Replace occurrences in properties and yml styles
      sed -E "s/($key[[:space:]]*[:=][[:space:]]*).*/\1\$\{$envvar\}/g" "$file.bak" > "$file"
    fi
  done
done

echo "Done. Backups (*.bak) created for modified files. Please inspect and commit changes as appropriate."

echo "Note: This script performs simple textual substitutions. Review files for correctness, especially YAML indentation and quoted values."

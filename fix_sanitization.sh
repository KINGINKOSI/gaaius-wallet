#!/bin/bash

# File to fix (from your screenshot, it's likely src/components/Code.jsx)
TARGET="src/components/Code.jsx"

if [ ! -f "$TARGET" ]; then
  echo "File $TARGET does not exist."
  exit 1
fi

# Add import if not present
if ! grep -q "import DOMPurify from 'dompurify';" "$TARGET"; then
  sed -i "1iimport DOMPurify from 'dompurify';" "$TARGET"
fi

# Replace dangerouslySetInnerHTML usage with sanitized version
sed -i 's/dangerouslySetInnerHTML={{ __html: props.code }}/dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(props.code) }}/g' "$TARGET"

echo "Sanitization fix applied to $TARGET"

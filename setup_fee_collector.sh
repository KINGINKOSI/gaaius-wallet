#!/bin/bash

WORKFLOW_FILE=".github/workflows/checkstyle.yml"

# Check if the workflow file exists
if [ ! -f "$WORKFLOW_FILE" ]; then
  echo "Error: $WORKFLOW_FILE does not exist."
  exit 1
fi

# Check if permissions block already exists
if grep -q "^permissions:" "$WORKFLOW_FILE"; then
  echo "Permissions block already exists in $WORKFLOW_FILE"
  exit 0
fi

# Insert permissions block after 'name:' if present, otherwise at the top
if grep -q "^name:" "$WORKFLOW_FILE"; then
  # Insert after 'name:'
  sed -i '/^name:/a permissions:\n  pull-requests: write\n  contents: read' "$WORKFLOW_FILE"
else
  # Insert at the very top
  sed -i '1ipermissions:\n  pull-requests: write\n  contents: read\n' "$WORKFLOW_FILE"
fi

echo "✅ Added permissions block to $WORKFLOW_FILE"

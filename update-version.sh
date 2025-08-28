#!/bin/bash

# Script to update plugin version with format: {timestamp}-{branchName}
# Usage: 
#   ./update-version.sh date      # Uses YYYYMMDD format
#   ./update-version.sh datetime  # Uses YYYYMMDDHHMMSS format

# Get current branch name
BRANCH_NAME=$(git branch --show-current)

# Generate timestamp based on argument
if [ "$1" = "datetime" ]; then
    # Full datetime: YYYYMMDDHHMMSS
    TIMESTAMP=$(date +"%Y%m%d%H%M%S")
    echo "Using full datetime format: $TIMESTAMP"
elif [ "$1" = "date" ] || [ -z "$1" ]; then
    # Date only: YYYYMMDD (default)
    TIMESTAMP=$(date +"%Y%m%d")
    echo "Using date format: $TIMESTAMP"
else
    echo "Usage: $0 [date|datetime]"
    echo "  date     - Use YYYYMMDD format (default)"
    echo "  datetime - Use YYYYMMDDHHMMSS format"
    exit 1
fi

# Create new version
NEW_VERSION="${TIMESTAMP}-${BRANCH_NAME}"
echo "New version: $NEW_VERSION"

# Update pom.xml
sed -i.bak "s/<changelist>.*<\/changelist>/<changelist>$NEW_VERSION<\/changelist>/" pom.xml

echo "✅ Updated pom.xml with version: $NEW_VERSION"
echo "📦 To build: mvn clean package -DskipTests" 
#!/bin/bash

# Multi-purpose build script for Snowflake Jenkins Connector
# Usage:
#   ./jenkins_build.sh update [date|datetime]  # Update version in pom.xml (replaces update-version.sh)
#   ./jenkins_build.sh [build]                 # Full CI/CD build & deploy (default)

MODE="${1:-build}"

# Function to generate version for pom.xml updates (same format as update-version.sh)
generate_local_version() {
    local BRANCH_NAME=$(git branch --show-current)
    local COMMIT_HASH=$(git rev-parse --short=7 HEAD)

    # Generate timestamp based on argument
    if [ "$2" = "datetime" ]; then
        # Full datetime: YYYYMMDDHHMMSS
        local TIMESTAMP=$(date +"%Y%m%d%H%M%S")
        echo "Using full datetime format: $TIMESTAMP" >&2
    else
        # Date only: YYYYMMDD (default)
        local TIMESTAMP=$(date +"%Y%m%d")
        echo "Using date format: $TIMESTAMP" >&2
    fi

    # Create version in format: {date}-{branch}-{commit}
    echo "${TIMESTAMP}-${BRANCH_NAME}-${COMMIT_HASH}"
}

# Function to generate version for CI/CD builds (original format)
generate_cicd_version() {
    local git_sha=$(git rev-parse --short=7 HEAD)
    local timestamp=$(date +"%Y%m%d-%H%M%S")
    local version="${timestamp}.${git_sha}"

    # build num not set during sandbox dev generally
    if [ -n "${BUILD_NUMBER}" ]; then
        version="${version}.${BUILD_NUMBER}"
    fi

    echo "${version}"
}

if [ "$MODE" = "update" ]; then
    # Update mode: Generate version and update pom.xml (replaces update-version.sh)
    echo "🔄 Updating version in pom.xml..."

    NEW_VERSION=$(generate_local_version "$@")
    echo "New version: $NEW_VERSION"

    # Update pom.xml
    sed -i.bak "s/<changelist>.*<\/changelist>/<changelist>$NEW_VERSION<\/changelist>/" pom.xml

    echo "✅ Updated pom.xml with version: $NEW_VERSION"
    echo "📦 To build: mvn clean compile hpi:hpi -Denforcer.skip=true -Dlicense.skip=true"

else
    # Build mode: Full CI/CD build & deploy (original functionality)
    echo "🏗️ Starting CI/CD build & deploy..."

    # auth setup
    eval $(sf artifact maven auth)

    ENV="sandbox"

    if [ -n "${JENKINS_ENVIRONMENT}" ]; then
        ENV="${JENKINS_ENVIRONMENT}"
    fi

    DYNAMIC_VERSION=$(generate_cicd_version)
    echo "Generated version: ${DYNAMIC_VERSION}"

    # build & deploy
    mvn clean deploy -P "${ENV}" -Dchangelist="${DYNAMIC_VERSION}" -Dspotbugs.skip=true
fi

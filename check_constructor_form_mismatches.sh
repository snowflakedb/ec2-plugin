#!/bin/bash

echo "=== CHECKING CONSTRUCTOR/FORM MISMATCHES ==="
echo

# Classes with @DataBoundConstructor
CLASSES=(
    "UnixData"
    "EC2Cloud" 
    "WindowsData"
    "EC2SpotSlave"
    "MinimumNumberOfInstancesTimeRangeConfig"
    "EC2Filter"
    "SpotConfiguration"
    "EC2OndemandSlave"
    "MacData"
    "EC2Tag"
    "EC2Step"
    "EC2RetentionStrategy"
    "WindowsSSHData"
    "SnowflakeDatabase"
    "SlaveTemplate"
)

for class in "${CLASSES[@]}"; do
    echo "=== $class ==="
    
    # Find the Java file
    java_file=$(find src/main/java -name "${class}.java")
    jelly_file=$(find src/main/resources -path "*/${class}/config.jelly")
    
    if [[ -f "$java_file" && -f "$jelly_file" ]]; then
        echo "✅ Both files exist"
        
        # Extract constructor parameters
        echo "📋 Constructor parameters:"
        grep -A 20 "@DataBoundConstructor" "$java_file" | grep -E "^\s*(String|int|Integer|boolean|Boolean|List|AMITypeData|ConnectionStrategy|Tenancy|Mode)" | head -10
        
        echo
        echo "📋 Form fields:"
        grep -E 'field="[^"]*"' "$jelly_file" | sed 's/.*field="\([^"]*\)".*/\1/' | head -10
        
        echo
        echo "🔍 Potential issues:"
        # Look for optionalBlock or nested structures
        if grep -q "f:optionalBlock" "$jelly_file"; then
            echo "⚠️  Found f:optionalBlock - check for nested parameter issues"
            grep -n "f:optionalBlock" "$jelly_file"
        fi
        
        # Look for complex field structures
        if grep -q "f:repeatableProperty\|f:hetero-list\|f:dropdownDescriptorSelector" "$jelly_file"; then
            echo "⚠️  Found complex form elements - verify parameter binding"
        fi
        
    else
        echo "❌ Missing files:"
        [[ ! -f "$java_file" ]] && echo "   - Java file not found"
        [[ ! -f "$jelly_file" ]] && echo "   - Jelly file not found"
    fi
    
    echo "----------------------------------------"
    echo
done

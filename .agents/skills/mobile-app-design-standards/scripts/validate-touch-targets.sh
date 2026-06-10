#!/bin/bash
# Touch Target Size Validator for React Native Components
#
# Validates that interactive components meet minimum touch target sizes:
# - iOS: 44×44 pt minimum
# - Android: 48×48 dp minimum
#
# Usage:
#   ./validate-touch-targets.sh <directory>
#   ./validate-touch-targets.sh src/components
#   ./validate-touch-targets.sh . --strict

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Minimum sizes
IOS_MIN=44
ANDROID_MIN=48
STRICT_MIN=$ANDROID_MIN

# Counters
total_files=0
files_with_issues=0
total_issues=0

show_help() {
    cat << EOF
Usage: $0 [OPTIONS] <directory>

Validate touch target sizes in React Native components.

OPTIONS:
    -h, --help          Show this help message
    -s, --strict        Use strict mode (48dp minimum for all)
    -v, --verbose       Show all files checked, not just issues
    --ios-only          Only check iOS minimum (44pt)
    --android-only      Only check Android minimum (48dp)

EXAMPLES:
    $0 src/components
    $0 . --strict
    $0 src --verbose

CHECKS:
    - TouchableOpacity, TouchableHighlight, Pressable dimensions
    - Button, IconButton component sizes
    - Custom button components with style props
    - hitSlop configuration

MINIMUM SIZES:
    iOS:     44×44 pt
    Android: 48×48 dp
    Strict:  48×48 (use larger of the two)
EOF
}

check_component_sizes() {
    local file="$1"
    local verbose="$2"
    local strict_mode="$3"
    local platform="$4"

    local issues=0
    local line_num=1

    # Determine minimum size based on mode
    local min_size=$IOS_MIN
    if [ "$strict_mode" = "true" ]; then
        min_size=$STRICT_MIN
    elif [ "$platform" = "android" ]; then
        min_size=$ANDROID_MIN
    fi

    # Check for TouchableOpacity, Pressable, etc. with style props
    while IFS= read -r line; do
        # Check for interactive components
        if echo "$line" | grep -qE '<(TouchableOpacity|TouchableHighlight|Pressable|TouchableWithoutFeedback|Button)'; then
            # Look for width/height in style
            if echo "$line" | grep -qE 'width:\s*[0-9]+' || echo "$line" | grep -qE 'height:\s*[0-9]+'; then
                # Extract width and height values
                width=$(echo "$line" | grep -oE 'width:\s*[0-9]+' | grep -oE '[0-9]+' || echo "0")
                height=$(echo "$line" | grep -oE 'height:\s*[0-9]+' | grep -oE '[0-9]+' || echo "0")

                # Check if either dimension is below minimum
                if [ -n "$width" ] && [ "$width" -lt "$min_size" ] && [ "$width" -gt 0 ]; then
                    echo -e "${RED}✗${NC} $file:$line_num - Width $width < minimum $min_size"
                    echo "    $line"
                    ((issues++))
                fi

                if [ -n "$height" ] && [ "$height" -lt "$min_size" ] && [ "$height" -gt 0 ]; then
                    echo -e "${RED}✗${NC} $file:$line_num - Height $height < minimum $min_size"
                    echo "    $line"
                    ((issues++))
                fi
            fi

            # Check for missing hitSlop on small components
            if ! echo "$line" | grep -q 'hitSlop' && ! echo "$line" | grep -qE '(width|height):\s*[4-9][0-9]'; then
                # Only warn if component seems small
                if echo "$line" | grep -qE '(width|height):\s*[1-3][0-9]'; then
                    echo -e "${YELLOW}⚠${NC}  $file:$line_num - Small component without hitSlop"
                    echo "    Consider adding hitSlop={{top: 8, bottom: 8, left: 8, right: 8}}"
                    echo "    $line"
                    ((issues++))
                fi
            fi
        fi

        ((line_num++))
    done < "$file"

    if [ "$verbose" = "true" ] && [ "$issues" -eq 0 ]; then
        echo -e "${GREEN}✓${NC} $file - All touch targets meet minimum size"
    fi

    echo "$issues"
}

validate_directory() {
    local dir="$1"
    local verbose="$2"
    local strict_mode="$3"
    local platform="$4"

    echo -e "${BLUE}Validating touch targets in: $dir${NC}"
    echo -e "${BLUE}Minimum size: ${min_size}×${min_size}${NC}"
    echo ""

    # Find all .tsx and .jsx files
    while IFS= read -r file; do
        ((total_files++))

        local file_issues
        file_issues=$(check_component_sizes "$file" "$verbose" "$strict_mode" "$platform")

        if [ "$file_issues" -gt 0 ]; then
            ((files_with_issues++))
            ((total_issues += file_issues))
        fi
    done < <(find "$dir" -type f \( -name "*.tsx" -o -name "*.jsx" \) 2>/dev/null)

    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}Summary${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo "Files checked: $total_files"
    echo "Files with issues: $files_with_issues"
    echo "Total issues: $total_issues"

    if [ "$total_issues" -eq 0 ]; then
        echo -e "\n${GREEN}✓ All touch targets meet minimum size requirements!${NC}"
        exit 0
    else
        echo -e "\n${YELLOW}⚠ Found $total_issues touch target issues${NC}"
        echo ""
        echo "Recommendations:"
        echo "  1. Increase component size to minimum ${min_size}×${min_size}"
        echo "  2. Add hitSlop to extend touch area without changing visual size"
        echo "  3. Use accessible={true} and appropriate accessibility labels"
        exit 1
    fi
}

# Parse arguments
DIRECTORY=""
VERBOSE=false
STRICT=false
PLATFORM="both"

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -s|--strict)
            STRICT=true
            shift
            ;;
        --ios-only)
            PLATFORM="ios"
            shift
            ;;
        --android-only)
            PLATFORM="android"
            shift
            ;;
        *)
            DIRECTORY="$1"
            shift
            ;;
    esac
done

# Validate arguments
if [ -z "$DIRECTORY" ]; then
    echo "Error: No directory specified"
    echo ""
    show_help
    exit 1
fi

if [ ! -d "$DIRECTORY" ]; then
    echo "Error: Directory not found: $DIRECTORY"
    exit 1
fi

# Determine minimum size for display
min_size=$IOS_MIN
if [ "$STRICT" = "true" ]; then
    min_size=$STRICT_MIN
elif [ "$PLATFORM" = "android" ]; then
    min_size=$ANDROID_MIN
fi

# Run validation
validate_directory "$DIRECTORY" "$VERBOSE" "$STRICT" "$PLATFORM"

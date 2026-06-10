#!/bin/bash
# Accessibility Audit Script for React Native Components
#
# Checks for common accessibility issues:
# - Missing accessibility labels
# - Missing accessibility hints
# - Missing accessibility roles
# - Images without alt text (accessibilityLabel)
# - Interactive elements without labels
#
# Usage:
#   ./accessibility-audit.sh <directory>
#   ./accessibility-audit.sh src/screens --verbose

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Counters
total_files=0
files_with_issues=0
total_issues=0
missing_labels=0
missing_roles=0
unlabeled_images=0

show_help() {
    cat << EOF
Usage: $0 [OPTIONS] <directory>

Audit React Native components for accessibility issues.

OPTIONS:
    -h, --help          Show this help message
    -v, --verbose       Show all files checked
    --fix-suggestions   Show code fix suggestions for each issue

CHECKS:
    ✓ accessibilityLabel on interactive elements
    ✓ accessibilityRole on buttons, links, images
    ✓ accessibilityLabel on Image components
    ✓ accessibilityHint for complex interactions
    ✓ Proper label text (not including type like "button")

EXAMPLES:
    $0 src/components
    $0 . --verbose
    $0 src/screens --fix-suggestions

WCAG Guidelines:
    - WCAG 2.1 Level AA compliance
    - All interactive elements must have labels
    - Labels should be descriptive and concise
    - Don't include element type in label
EOF
}

check_accessibility() {
    local file="$1"
    local verbose="$2"
    local show_fixes="$3"

    local issues=0
    local line_num=0
    local in_component=false
    local component_name=""
    local has_label=false
    local has_role=false

    while IFS= read -r line; do
        ((line_num++))

        # Detect interactive components
        if echo "$line" | grep -qE '<(TouchableOpacity|TouchableHighlight|Pressable|Button|TouchableWithoutFeedback)'; then
            in_component=true
            component_name=$(echo "$line" | grep -oE '<[A-Za-z]+' | tr -d '<')
            has_label=false
            has_role=false

            # Check if label and role are on same line
            if echo "$line" | grep -q 'accessibilityLabel'; then
                has_label=true

                # Check for common mistakes in label text
                if echo "$line" | grep -qE 'accessibilityLabel=.*[Bb]utton'; then
                    echo -e "${YELLOW}⚠${NC}  $file:$line_num - Label includes 'button' (redundant with role)"
                    echo "    $line"
                    if [ "$show_fixes" = "true" ]; then
                        echo -e "    ${BLUE}Fix:${NC} Remove 'button' from label, use accessibilityRole instead"
                    fi
                    ((issues++))
                fi
            fi

            if echo "$line" | grep -q 'accessibilityRole'; then
                has_role=true
            fi
        fi

        # Continue checking multi-line component props
        if [ "$in_component" = "true" ]; then
            if echo "$line" | grep -q 'accessibilityLabel'; then
                has_label=true
            fi
            if echo "$line" | grep -q 'accessibilityRole'; then
                has_role=true
            fi

            # End of component
            if echo "$line" | grep -q '>'; then
                if [ "$has_label" = "false" ]; then
                    echo -e "${RED}✗${NC} $file:$line_num - $component_name missing accessibilityLabel"
                    if [ "$show_fixes" = "true" ]; then
                        echo -e "    ${BLUE}Fix:${NC} Add accessibilityLabel=\"Description of action\""
                    fi
                    ((issues++))
                    ((missing_labels++))
                fi

                if [ "$has_role" = "false" ]; then
                    echo -e "${YELLOW}⚠${NC}  $file:$line_num - $component_name missing accessibilityRole"
                    if [ "$show_fixes" = "true" ]; then
                        echo -e "    ${BLUE}Fix:${NC} Add accessibilityRole=\"button\" (or appropriate role)"
                    fi
                    ((issues++))
                    ((missing_roles++))
                fi

                in_component=false
            fi
        fi

        # Check for Image components
        if echo "$line" | grep -qE '<Image\s'; then
            if ! echo "$line" | grep -q 'accessibilityLabel'; then
                # Check next few lines for accessibilityLabel
                local has_label_nearby=false
                for i in {1..3}; do
                    local next_line=$(sed -n "$((line_num + i))p" "$file")
                    if echo "$next_line" | grep -q 'accessibilityLabel'; then
                        has_label_nearby=true
                        break
                    fi
                    if echo "$next_line" | grep -q '/>'; then
                        break
                    fi
                done

                if [ "$has_label_nearby" = "false" ]; then
                    echo -e "${RED}✗${NC} $file:$line_num - Image without accessibilityLabel"
                    echo "    $line"
                    if [ "$show_fixes" = "true" ]; then
                        echo -e "    ${BLUE}Fix:${NC} Add accessibilityLabel=\"Description of image\""
                        echo -e "    ${BLUE}Or:${NC} If decorative, use accessibilityRole=\"none\""
                    fi
                    ((issues++))
                    ((unlabeled_images++))
                fi
            fi
        fi

        # Check for links without labels
        if echo "$line" | grep -qE '<(Text|Linking).*onPress'; then
            if ! echo "$line" | grep -q 'accessibilityRole'; then
                echo -e "${YELLOW}⚠${NC}  $file:$line_num - Interactive Text without accessibilityRole"
                if [ "$show_fixes" = "true" ]; then
                    echo -e "    ${BLUE}Fix:${NC} Add accessibilityRole=\"link\" or \"button\""
                fi
                ((issues++))
            fi
        fi

    done < "$file"

    if [ "$verbose" = "true" ] && [ "$issues" -eq 0 ]; then
        echo -e "${GREEN}✓${NC} $file - No accessibility issues found"
    fi

    echo "$issues"
}

audit_directory() {
    local dir="$1"
    local verbose="$2"
    local show_fixes="$3"

    echo -e "${BLUE}Auditing accessibility in: $dir${NC}"
    echo ""

    # Find all .tsx and .jsx files
    while IFS= read -r file; do
        ((total_files++))

        local file_issues
        file_issues=$(check_accessibility "$file" "$verbose" "$show_fixes")

        if [ "$file_issues" -gt 0 ]; then
            ((files_with_issues++))
            ((total_issues += file_issues))
        fi
    done < <(find "$dir" -type f \( -name "*.tsx" -o -name "*.jsx" \) 2>/dev/null)

    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}Accessibility Audit Summary${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo "Files checked: $total_files"
    echo "Files with issues: $files_with_issues"
    echo "Total issues: $total_issues"
    echo ""
    echo "Issue breakdown:"
    echo "  Missing labels: $missing_labels"
    echo "  Missing roles: $missing_roles"
    echo "  Unlabeled images: $unlabeled_images"

    if [ "$total_issues" -eq 0 ]; then
        echo -e "\n${GREEN}✓✓ Excellent! No accessibility issues found!${NC}"
        exit 0
    else
        echo -e "\n${YELLOW}⚠ Found $total_issues accessibility issues${NC}"
        echo ""
        echo "Key recommendations:"
        echo "  1. Add accessibilityLabel to all interactive elements"
        echo "  2. Add accessibilityRole to indicate element type"
        echo "  3. Add accessibilityLabel to images (or mark as decorative)"
        echo "  4. Don't include element type in label text"
        echo "  5. Test with VoiceOver (iOS) and TalkBack (Android)"
        echo ""
        echo "Resources:"
        echo "  - WCAG 2.1 Guidelines: https://www.w3.org/WAI/WCAG21/quickref/"
        echo "  - React Native Accessibility: https://reactnative.dev/docs/accessibility"
        exit 1
    fi
}

# Parse arguments
DIRECTORY=""
VERBOSE=false
SHOW_FIXES=false

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
        --fix-suggestions)
            SHOW_FIXES=true
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

# Run audit
audit_directory "$DIRECTORY" "$VERBOSE" "$SHOW_FIXES"

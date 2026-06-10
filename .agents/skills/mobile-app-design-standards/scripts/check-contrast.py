#!/usr/bin/env python3
"""
WCAG Contrast Ratio Checker

Checks if color combinations meet WCAG AA or AAA standards.
Usage:
    python check-contrast.py "#FFFFFF" "#000000"
    python check-contrast.py --text "#657786" --bg "#FFFFFF"
"""

import sys
import argparse
from typing import Tuple


def hex_to_rgb(hex_color: str) -> Tuple[int, int, int]:
    """Convert hex color to RGB tuple."""
    hex_color = hex_color.lstrip('#')
    return tuple(int(hex_color[i:i+2], 16) for i in (0, 2, 4))


def relative_luminance(rgb: Tuple[int, int, int]) -> float:
    """
    Calculate relative luminance according to WCAG formula.
    https://www.w3.org/WAI/GL/wiki/Relative_luminance
    """
    def adjust(channel: int) -> float:
        c = channel / 255.0
        if c <= 0.03928:
            return c / 12.92
        else:
            return ((c + 0.055) / 1.055) ** 2.4

    r, g, b = [adjust(c) for c in rgb]
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast_ratio(color1: str, color2: str) -> float:
    """Calculate contrast ratio between two colors."""
    rgb1 = hex_to_rgb(color1)
    rgb2 = hex_to_rgb(color2)

    l1 = relative_luminance(rgb1)
    l2 = relative_luminance(rgb2)

    lighter = max(l1, l2)
    darker = min(l1, l2)

    return (lighter + 0.05) / (darker + 0.05)


def check_wcag_compliance(ratio: float, text_size: str = 'normal') -> dict:
    """
    Check WCAG compliance levels.

    Args:
        ratio: Contrast ratio
        text_size: 'normal' (<18pt or <14pt bold) or 'large' (>=18pt or >=14pt bold)

    Returns:
        Dictionary with compliance levels
    """
    if text_size == 'normal':
        aa_required = 4.5
        aaa_required = 7.0
    else:  # large text
        aa_required = 3.0
        aaa_required = 4.5

    return {
        'ratio': ratio,
        'AA': ratio >= aa_required,
        'AAA': ratio >= aaa_required,
        'AA_required': aa_required,
        'AAA_required': aaa_required,
    }


def format_result(text_color: str, bg_color: str, text_size: str = 'normal') -> str:
    """Format and return the contrast check result."""
    ratio = contrast_ratio(text_color, bg_color)
    compliance = check_wcag_compliance(ratio, text_size)

    result = []
    result.append(f"\n{'='*60}")
    result.append(f"WCAG Contrast Ratio Check")
    result.append(f"{'='*60}")
    result.append(f"Text Color:       {text_color}")
    result.append(f"Background Color: {bg_color}")
    result.append(f"Text Size:        {text_size}")
    result.append(f"\nContrast Ratio:   {ratio:.2f}:1")
    result.append(f"{'='*60}")

    # WCAG AA
    aa_symbol = "✓" if compliance['AA'] else "✗"
    result.append(f"WCAG AA ({compliance['AA_required']}:1):  {aa_symbol} {'PASS' if compliance['AA'] else 'FAIL'}")

    # WCAG AAA
    aaa_symbol = "✓" if compliance['AAA'] else "✗"
    result.append(f"WCAG AAA ({compliance['AAA_required']}:1): {aaa_symbol} {'PASS' if compliance['AAA'] else 'FAIL'}")

    result.append(f"{'='*60}")

    # Recommendations
    if not compliance['AA']:
        result.append("\n⚠️  WARNING: Does not meet WCAG AA standards!")
        result.append("   Consider:")
        result.append("   - Using darker text on light background")
        result.append("   - Using lighter text on dark background")
        result.append("   - Increasing text size if possible")
    elif not compliance['AAA']:
        result.append("\n✓  Meets WCAG AA (minimum standard)")
        result.append("💡 Consider improving to AAA for better accessibility")
    else:
        result.append("\n✓✓ Excellent! Meets both AA and AAA standards")

    result.append("")
    return "\n".join(result)


def main():
    parser = argparse.ArgumentParser(
        description='Check WCAG contrast ratio between two colors',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s "#FFFFFF" "#000000"
  %(prog)s --text "#657786" --bg "#FFFFFF"
  %(prog)s --text "#FFFFFF" --bg "#1DA1F2" --size large

Common color combinations to test:
  Light theme - Primary text:   "#14171A" on "#FFFFFF"
  Light theme - Secondary text:  "#657786" on "#FFFFFF"
  Dark theme - Primary text:    "#FFFFFF" on "#000000"
  Button - White on brand:      "#FFFFFF" on "#1DA1F2"
        """
    )

    parser.add_argument(
        'colors',
        nargs='*',
        help='Two hex colors (text and background)'
    )
    parser.add_argument(
        '--text', '-t',
        help='Text color (hex format: #RRGGBB)'
    )
    parser.add_argument(
        '--bg', '-b',
        help='Background color (hex format: #RRGGBB)'
    )
    parser.add_argument(
        '--size', '-s',
        choices=['normal', 'large'],
        default='normal',
        help='Text size: normal (<18pt) or large (>=18pt or >=14pt bold)'
    )
    parser.add_argument(
        '--batch',
        action='store_true',
        help='Batch mode: test multiple combinations from stdin'
    )

    args = parser.parse_args()

    # Determine text and background colors
    if args.text and args.bg:
        text_color = args.text
        bg_color = args.bg
    elif len(args.colors) == 2:
        text_color, bg_color = args.colors
    else:
        parser.print_help()
        sys.exit(1)

    # Validate hex format
    for color in [text_color, bg_color]:
        if not color.startswith('#') or len(color) != 7:
            print(f"Error: Invalid hex color format: {color}")
            print("Expected format: #RRGGBB (e.g., #FFFFFF)")
            sys.exit(1)

    # Check and print result
    print(format_result(text_color, bg_color, args.size))


if __name__ == '__main__':
    main()

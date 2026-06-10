/**
 * Design System Configuration Template
 *
 * Complete design tokens and style system for a mobile application.
 * Covers colors, typography, spacing, shadows, and component variants.
 *
 * Usage:
 * 1. Customize brand colors
 * 2. Adjust spacing/sizing scales
 * 3. Import and use in components
 * 4. Maintain consistency across app
 */

import { Platform, StyleSheet } from 'react-native';

// ============================================================================
// COLOR SYSTEM
// ============================================================================

/**
 * Color Palette
 * Define brand colors and semantic color roles
 */
export const colors = {
  // Brand Colors
  brand: {
    primary: '#1DA1F2',
    secondary: '#14171A',
    accent: '#E1E8ED',
  },

  // Neutral Scale (for text, backgrounds, borders)
  neutral: {
    0: '#FFFFFF',
    50: '#FAFAFA',
    100: '#F5F5F5',
    200: '#E5E5E5',
    300: '#D4D4D4',
    400: '#A3A3A3',
    500: '#737373',
    600: '#525252',
    700: '#404040',
    800: '#262626',
    900: '#171717',
    950: '#0A0A0A',
  },

  // Semantic Colors (contextual meaning)
  semantic: {
    success: '#10B981',
    successLight: '#D1FAE5',
    warning: '#F59E0B',
    warningLight: '#FEF3C7',
    error: '#EF4444',
    errorLight: '#FEE2E2',
    info: '#3B82F6',
    infoLight: '#DBEAFE',
  },

  // Text Colors
  text: {
    primary: '#14171A',      // 15.8:1 contrast on white
    secondary: '#657786',    // 4.6:1 contrast on white
    tertiary: '#AAB8C2',     // 3.0:1 contrast on white (captions only)
    inverse: '#FFFFFF',      // On dark backgrounds
    link: '#1DA1F2',
    disabled: '#AAB8C2',
  },

  // Background Colors
  background: {
    primary: '#FFFFFF',
    secondary: '#F7F9FA',
    tertiary: '#E1E8ED',
    overlay: 'rgba(0, 0, 0, 0.5)',
    card: '#FFFFFF',
    elevated: '#FFFFFF',
  },

  // Border Colors
  border: {
    light: '#E1E8ED',
    medium: '#AAB8C2',
    dark: '#657786',
    focus: '#1DA1F2',
    error: '#EF4444',
  },
} as const;

/**
 * Dark Mode Colors
 * Complete color scheme for dark mode
 */
export const darkColors = {
  brand: colors.brand, // Brand colors stay same

  neutral: {
    0: '#000000',
    50: '#0A0A0A',
    100: '#171717',
    200: '#262626',
    300: '#404040',
    400: '#525252',
    500: '#737373',
    600: '#A3A3A3',
    700: '#D4D4D4',
    800: '#E5E5E5',
    900: '#F5F5F5',
    950: '#FAFAFA',
  },

  semantic: colors.semantic,

  text: {
    primary: '#FFFFFF',
    secondary: '#8899A6',
    tertiary: '#657786',
    inverse: '#14171A',
    link: '#1DA1F2',
    disabled: '#657786',
  },

  background: {
    primary: '#000000',
    secondary: '#15202B',
    tertiary: '#192734',
    overlay: 'rgba(255, 255, 255, 0.1)',
    card: '#15202B',
    elevated: '#192734',
  },

  border: {
    light: '#38444D',
    medium: '#657786',
    dark: '#8899A6',
    focus: '#1DA1F2',
    error: '#EF4444',
  },
} as const;

// ============================================================================
// TYPOGRAPHY SYSTEM
// ============================================================================

/**
 * Font Families
 */
export const fontFamily = {
  regular: Platform.select({
    ios: 'System',
    android: 'Roboto',
    default: 'System',
  }),
  medium: Platform.select({
    ios: 'System',
    android: 'Roboto-Medium',
    default: 'System',
  }),
  semibold: Platform.select({
    ios: 'System',
    android: 'Roboto-Medium',
    default: 'System',
  }),
  bold: Platform.select({
    ios: 'System',
    android: 'Roboto-Bold',
    default: 'System',
  }),
} as const;

/**
 * Font Weights
 * Use system font weights
 */
export const fontWeight = {
  regular: '400' as const,
  medium: '500' as const,
  semibold: '600' as const,
  bold: '700' as const,
} as const;

/**
 * Type Scale
 * Complete typography hierarchy with line heights
 */
export const typography = {
  // Display - Largest text (hero sections)
  display: {
    fontSize: 34,
    lineHeight: 40,
    fontWeight: fontWeight.bold,
    letterSpacing: -0.5,
  },

  // Headings - Section titles
  h1: {
    fontSize: 28,
    lineHeight: 34,
    fontWeight: fontWeight.bold,
    letterSpacing: -0.3,
  },
  h2: {
    fontSize: 24,
    lineHeight: 30,
    fontWeight: fontWeight.bold,
    letterSpacing: -0.2,
  },
  h3: {
    fontSize: 20,
    lineHeight: 26,
    fontWeight: fontWeight.semibold,
  },
  h4: {
    fontSize: 17,
    lineHeight: 24,
    fontWeight: fontWeight.semibold,
  },

  // Body - Main content
  bodyLarge: {
    fontSize: 17,
    lineHeight: 25,
    fontWeight: fontWeight.regular,
  },
  body: {
    fontSize: 15,
    lineHeight: 22,
    fontWeight: fontWeight.regular,
  },
  bodySmall: {
    fontSize: 13,
    lineHeight: 19,
    fontWeight: fontWeight.regular,
  },

  // Labels - UI elements
  label: {
    fontSize: 15,
    lineHeight: 20,
    fontWeight: fontWeight.medium,
  },
  labelSmall: {
    fontSize: 13,
    lineHeight: 18,
    fontWeight: fontWeight.medium,
  },

  // Caption - Supplementary text
  caption: {
    fontSize: 12,
    lineHeight: 16,
    fontWeight: fontWeight.regular,
  },

  // Button text
  button: {
    fontSize: 15,
    lineHeight: 20,
    fontWeight: fontWeight.semibold,
    letterSpacing: 0.3,
  },
  buttonSmall: {
    fontSize: 13,
    lineHeight: 18,
    fontWeight: fontWeight.semibold,
    letterSpacing: 0.3,
  },
} as const;

// ============================================================================
// SPACING SYSTEM
// ============================================================================

/**
 * Spacing Scale (8pt grid)
 * Use multiples of 4 for consistency
 */
export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  base: 16,
  lg: 24,
  xl: 32,
  '2xl': 40,
  '3xl': 48,
  '4xl': 64,
} as const;

/**
 * Border Radius Scale
 */
export const borderRadius = {
  none: 0,
  sm: 4,
  md: 8,
  lg: 12,
  xl: 16,
  '2xl': 24,
  full: 9999, // Fully rounded
} as const;

/**
 * Border Width
 */
export const borderWidth = {
  hairline: StyleSheet.hairlineWidth,
  thin: 1,
  medium: 2,
  thick: 3,
} as const;

// ============================================================================
// ELEVATION & SHADOWS
// ============================================================================

/**
 * Shadow System
 * Platform-specific shadows and elevation
 */
export const shadows = {
  // iOS Shadows
  ios: {
    sm: {
      shadowColor: '#000',
      shadowOffset: { width: 0, height: 1 },
      shadowOpacity: 0.05,
      shadowRadius: 2,
    },
    md: {
      shadowColor: '#000',
      shadowOffset: { width: 0, height: 2 },
      shadowOpacity: 0.1,
      shadowRadius: 4,
    },
    lg: {
      shadowColor: '#000',
      shadowOffset: { width: 0, height: 4 },
      shadowOpacity: 0.15,
      shadowRadius: 8,
    },
    xl: {
      shadowColor: '#000',
      shadowOffset: { width: 0, height: 8 },
      shadowOpacity: 0.2,
      shadowRadius: 16,
    },
  },

  // Android Elevation
  android: {
    sm: { elevation: 2 },
    md: { elevation: 4 },
    lg: { elevation: 8 },
    xl: { elevation: 16 },
  },
} as const;

/**
 * Cross-platform shadow helper
 */
export const getShadow = (size: keyof typeof shadows.ios) => {
  return Platform.select({
    ios: shadows.ios[size],
    android: shadows.android[size],
    default: {},
  });
};

// ============================================================================
// LAYOUT CONSTANTS
// ============================================================================

/**
 * Touch Targets
 * Platform-specific minimum sizes
 */
export const touchTarget = {
  minimum: Platform.select({
    ios: 44,
    android: 48,
    default: 44,
  }),
  comfortable: 56,
  large: 64,
} as const;

/**
 * Component Sizes
 */
export const size = {
  // Button heights
  button: {
    sm: 36,
    md: 44,
    lg: 52,
  },

  // Input heights
  input: {
    sm: 40,
    md: 48,
    lg: 56,
  },

  // Avatar sizes
  avatar: {
    xs: 24,
    sm: 32,
    md: 40,
    lg: 56,
    xl: 80,
  },

  // Icon sizes
  icon: {
    xs: 16,
    sm: 20,
    md: 24,
    lg: 32,
    xl: 40,
  },
} as const;

/**
 * Z-Index Scale
 */
export const zIndex = {
  base: 0,
  dropdown: 1000,
  sticky: 1100,
  modal: 1200,
  popover: 1300,
  tooltip: 1400,
  toast: 1500,
} as const;

// ============================================================================
// COMPONENT VARIANTS
// ============================================================================

/**
 * Button Variants
 */
export const buttonVariants = StyleSheet.create({
  // Primary - High emphasis
  primary: {
    backgroundColor: colors.brand.primary,
    borderRadius: borderRadius.lg,
  },
  primaryText: {
    color: colors.text.inverse,
    ...typography.button,
  },

  // Secondary - Medium emphasis
  secondary: {
    backgroundColor: 'transparent',
    borderWidth: borderWidth.thin,
    borderColor: colors.border.medium,
    borderRadius: borderRadius.lg,
  },
  secondaryText: {
    color: colors.text.primary,
    ...typography.button,
  },

  // Tertiary - Low emphasis
  tertiary: {
    backgroundColor: 'transparent',
    borderRadius: borderRadius.lg,
  },
  tertiaryText: {
    color: colors.brand.primary,
    ...typography.button,
  },

  // Destructive
  destructive: {
    backgroundColor: colors.semantic.error,
    borderRadius: borderRadius.lg,
  },
  destructiveText: {
    color: colors.text.inverse,
    ...typography.button,
  },

  // Disabled
  disabled: {
    backgroundColor: colors.neutral[100],
    borderRadius: borderRadius.lg,
  },
  disabledText: {
    color: colors.text.disabled,
    ...typography.button,
  },
});

/**
 * Card Variants
 */
export const cardVariants = StyleSheet.create({
  default: {
    backgroundColor: colors.background.card,
    borderRadius: borderRadius.lg,
    padding: spacing.base,
    ...getShadow('sm'),
  },

  elevated: {
    backgroundColor: colors.background.elevated,
    borderRadius: borderRadius.lg,
    padding: spacing.base,
    ...getShadow('md'),
  },

  outlined: {
    backgroundColor: colors.background.card,
    borderRadius: borderRadius.lg,
    borderWidth: borderWidth.thin,
    borderColor: colors.border.light,
    padding: spacing.base,
  },

  interactive: {
    backgroundColor: colors.background.card,
    borderRadius: borderRadius.lg,
    padding: spacing.base,
    ...getShadow('sm'),
    // Add activeOpacity when using with TouchableOpacity
  },
});

/**
 * Input Variants
 */
export const inputVariants = StyleSheet.create({
  default: {
    height: size.input.md,
    backgroundColor: colors.background.secondary,
    borderRadius: borderRadius.md,
    paddingHorizontal: spacing.base,
    ...typography.body,
    color: colors.text.primary,
  },

  outlined: {
    height: size.input.md,
    backgroundColor: colors.background.primary,
    borderWidth: borderWidth.thin,
    borderColor: colors.border.light,
    borderRadius: borderRadius.md,
    paddingHorizontal: spacing.base,
    ...typography.body,
    color: colors.text.primary,
  },

  error: {
    borderColor: colors.border.error,
  },

  focused: {
    borderColor: colors.border.focus,
    borderWidth: borderWidth.medium,
  },
});

// ============================================================================
// UTILITY STYLES
// ============================================================================

/**
 * Common Layout Patterns
 */
export const layout = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background.primary,
  },

  centered: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },

  row: {
    flexDirection: 'row',
    alignItems: 'center',
  },

  rowBetween: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },

  absoluteFill: {
    ...StyleSheet.absoluteFillObject,
  },
});

/**
 * Text Styles
 */
export const textStyles = StyleSheet.create({
  display: {
    ...typography.display,
    color: colors.text.primary,
  },
  h1: {
    ...typography.h1,
    color: colors.text.primary,
  },
  h2: {
    ...typography.h2,
    color: colors.text.primary,
  },
  h3: {
    ...typography.h3,
    color: colors.text.primary,
  },
  body: {
    ...typography.body,
    color: colors.text.primary,
  },
  bodySecondary: {
    ...typography.body,
    color: colors.text.secondary,
  },
  caption: {
    ...typography.caption,
    color: colors.text.secondary,
  },
  link: {
    ...typography.body,
    color: colors.text.link,
    textDecorationLine: 'underline',
  },
});

// ============================================================================
// EXPORT ALL
// ============================================================================

export const theme = {
  colors,
  darkColors,
  typography,
  spacing,
  borderRadius,
  borderWidth,
  shadows,
  getShadow,
  touchTarget,
  size,
  zIndex,
  buttonVariants,
  cardVariants,
  inputVariants,
  layout,
  textStyles,
} as const;

export type Theme = typeof theme;

/**
 * Usage Example:
 *
 * import { theme } from './design-system-config';
 *
 * const styles = StyleSheet.create({
 *   container: {
 *     ...theme.layout.container,
 *     padding: theme.spacing.base,
 *   },
 *   title: {
 *     ...theme.textStyles.h1,
 *   },
 *   button: {
 *     ...theme.buttonVariants.primary,
 *     ...theme.getShadow('md'),
 *   },
 * });
 */

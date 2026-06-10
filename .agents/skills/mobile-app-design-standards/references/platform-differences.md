# iOS vs Android Platform Differences Quick Reference

Quick comparison of key design differences between iOS and Android platforms.

## Navigation Patterns

| Aspect | iOS | Android |
|--------|-----|---------|
| **Back Button** | Top-left corner | Top-left corner OR hardware back button |
| **Primary Action** | Top-right corner | Floating Action Button (bottom-right) OR top-right |
| **Tab Bar** | Bottom (3-5 items) | Bottom (3-5 items) OR top tabs |
| **More Options** | Bottom tab "More" OR action sheet | Overflow menu (⋮) top-right |
| **Navigation Drawer** | Less common | Common for 5+ sections |
| **Modal Dismiss** | "Cancel" or "Done" in nav bar | Hardware back button OR close icon |
| **Screen Transition** | Push from right (hierarchical) | Slide up/fade (varies by transition type) |

## Visual Design

| Element | iOS | Android |
|---------|-----|---------|
| **Font** | San Francisco | Roboto |
| **Status Bar** | Light/dark content | Transparent with app control |
| **App Bar** | Navigation Bar (44pt) | Top App Bar (56dp mobile, 64dp tablet) |
| **Icons** | Outlined, minimal | Filled or outlined (Material Icons) |
| **Shadows** | Subtle, rare | Elevation system (1-24dp) |
| **Dividers** | Full-width or inset | Full-width, middle, or inset |
| **Corners** | Rounded (varies by component) | Rounded (4-28dp based on component) |
| **Animations** | Spring physics, bounce | Ease curves, no bounce |

## Touch Targets

| Aspect | iOS | Android |
|--------|-----|---------|
| **Minimum Size** | 44×44 pt | 48×48 dp |
| **Icon Buttons** | 44×44 pt | 48×48 dp |
| **List Items** | 44pt minimum height | 48-56dp minimum height |
| **FAB** | N/A (not used) | 56dp default, 40dp mini |
| **Switch** | 51×31 pt | 52×32 dp |
| **Checkbox** | 22×22 pt | 40×40 dp |

## Typography

| Style | iOS (Points) | Android (SP) |
|-------|--------------|--------------|
| **Large Title** | 34pt Bold | Display Large 57sp |
| **Title** | 28pt Bold | Headline Large 32sp |
| **Headline** | 17pt Semibold | Title Large 22sp |
| **Body** | 17pt Regular | Body Large 16sp |
| **Callout** | 16pt Regular | Body Medium 14sp |
| **Caption** | 12pt Regular | Body Small 12sp |
| **Footnote** | 13pt Regular | Label Medium 12sp |
| **Minimum** | 11pt | 12sp |

## Components

### Buttons

| Type | iOS | Android |
|------|-----|---------|
| **Filled** | Rounded rect, solid color | Rounded corners (20dp), elevation |
| **Outlined** | Border, transparent fill | 1dp border, no elevation |
| **Text** | No background or border | No background or border |
| **Height** | 44pt minimum | 40dp standard |
| **Padding** | 16pt horizontal | 16dp horizontal |
| **Capitalization** | Title Case | Uppercase (for text buttons) |

### Switches

| Aspect | iOS | Android |
|--------|-----|---------|
| **Size** | 51×31 pt | 52×32 dp |
| **Style** | Pill shape, colored when on | Toggle with track |
| **Animation** | Smooth slide | Thumb slides with ripple |
| **Label** | To the left | To the left or right |

### List Items

| Aspect | iOS | Android |
|--------|-----|---------|
| **Height** | 44pt minimum | 56dp minimum (single line), 72dp (two lines) |
| **Dividers** | Full-width or inset 16pt | Full-width or inset 16dp |
| **Avatar** | 40-60pt | 40dp (single line), 56dp (two/three lines) |
| **Swipe Actions** | Swipe from right | Swipe from left or right |
| **Selection** | Checkmark on right | Checkbox on left or checkmark on right |

### Cards

| Aspect | iOS | Android |
|--------|-----|---------|
| **Shadow** | Subtle shadow | Elevation (1dp default, 8dp raised) |
| **Corners** | 10-12pt radius | 12dp radius |
| **Padding** | 16pt | 16dp |
| **Spacing** | 8-16pt | 8dp |

### Text Fields

| Aspect | iOS | Android |
|--------|-----|---------|
| **Style** | Rounded rectangle with border | Filled or outlined |
| **Label** | Placeholder or floating | Floating label standard |
| **Height** | 44pt minimum | 56dp |
| **Focus** | Blue border or shadow | Bottom line highlight |
| **Error** | Red text below | Red label + bottom line |

## Gestures

| Gesture | iOS | Android |
|---------|-----|---------|
| **Back** | Swipe from left edge | Swipe from left edge OR back button |
| **Menu** | N/A | Swipe from left edge (if drawer) |
| **Refresh** | Pull down from top | Pull down from top |
| **Actions** | Swipe left on list item | Long press OR swipe |
| **Context Menu** | Long press (iOS 13+) | Long press |
| **Dismiss Modal** | Pull down (iOS 13+) | Back button |

## Dialogs and Alerts

| Aspect | iOS | Android |
|--------|-----|---------|
| **Style** | Centered modal, rounded | Centered dialog, elevated |
| **Title** | Bold, centered | Medium weight, left-aligned |
| **Body** | Regular, centered | Regular, left-aligned |
| **Buttons** | Horizontal (2 max), vertical (3+) | Horizontal (right-aligned) |
| **Button Order** | Cancel (left), Confirm (right) | Cancel (left), Confirm (right) |
| **Dismiss** | Button only | Button OR back button |

## Loading States

| Type | iOS | Android |
|------|-----|---------|
| **Activity Indicator** | Spinning dots | Circular progress |
| **Progress Bar** | Thin line | Thicker line with rounded ends |
| **Skeleton** | Gray placeholders | Shimmer effect common |
| **Pull to Refresh** | Spinner at top | Circular progress at top |

## Forms

| Element | iOS | Android |
|---------|-----|---------|
| **Keyboard Return** | "Return", "Done", "Next" | Check mark, arrow |
| **Picker** | Spinning wheel | Dropdown menu |
| **Date Picker** | Wheels (inline or modal) | Calendar view or dropdown |
| **Time Picker** | Wheels | Clock face |
| **Segment Control** | Pill buttons (iOS) | Chip group (Android) |
| **Stepper** | -/+ buttons | N/A (use text field) |

## Notifications and Toasts

| Type | iOS | Android |
|------|-----|---------|
| **Push Notification** | Banner from top | Banner from top |
| **In-App Banner** | From top | From top |
| **Toast/Snackbar** | Not native (use library) | Snackbar from bottom |
| **Duration** | 3-5 seconds | 4-10 seconds |
| **Action** | Button on right | Button on right |

## Search

| Aspect | iOS | Android |
|--------|-----|---------|
| **Search Bar** | Rounded, gray background | White/gray with border |
| **Placement** | In navigation bar or toolbar | In app bar or separate |
| **Cancel Button** | Appears when focused | Back arrow appears |
| **Clear Button** | X on right when typing | X on right when typing |
| **Voice Search** | Less common | Common (microphone icon) |

## Settings

| Pattern | iOS | Android |
|---------|-----|---------|
| **Layout** | Grouped list with sections | Preference screens |
| **Switches** | Right side of row | Right side of row |
| **Navigation** | Disclosure indicator (›) | Back arrow in app bar |
| **Organization** | Flat hierarchy | Can be nested |

## Haptic Feedback

| Type | iOS | Android |
|------|-----|---------|
| **Selection** | Light tap | Optional vibration |
| **Impact** | Light/Medium/Heavy | Varies by device |
| **Notification** | Success/Warning/Error | Less standardized |
| **Usage** | Rich Taptic Engine | Device-dependent |

## Platform-Specific Features

### iOS Only

- **3D Touch / Haptic Touch**: Peek and pop (older) or context menus (iOS 13+)
- **Live Text**: Text recognition in images
- **Face ID / Touch ID**: Biometric authentication
- **Dynamic Island**: Status bar interactions (iPhone 14 Pro+)
- **Action Extensions**: Share sheet with custom actions
- **Widgets**: Home screen widgets with specific sizes
- **App Clips**: Lightweight app experiences

### Android Only

- **Floating Action Button (FAB)**: Primary action button
- **Navigation Drawer**: Side menu for navigation
- **Material You**: Dynamic color theming (Android 12+)
- **Widgets**: More flexible sizing and layouts
- **Live Wallpapers**: Animated backgrounds
- **Custom Launchers**: Third-party home screens
- **Back Button**: Hardware or virtual back button
- **Split Screen**: Run two apps simultaneously

## Color Systems

### iOS

| Mode | Background | Primary Text | Secondary Text |
|------|------------|--------------|----------------|
| **Light** | White | Black 87% | Black 60% |
| **Dark** | #000000 | White 100% | White 60% |

**System Colors:**
- Tint color (blue default)
- Semantic colors (label, secondaryLabel, tertiaryLabel)
- Background colors (systemBackground, secondarySystemBackground)

### Android

| Mode | Surface | On Surface | On Surface Variant |
|------|---------|------------|-------------------|
| **Light** | #FFFFFF | #1C1B1F | #49454F |
| **Dark** | #1C1B1F | #E6E1E5 | #CAC4D0 |

**Material Design 3:**
- Primary, Secondary, Tertiary color roles
- Surface containers (surface, surfaceVariant, surfaceContainer)
- On-color variants for text on colored backgrounds

## Animations

### iOS

- **Spring animations**: Default, bouncy feel
- **Duration**: Typically 0.3-0.4s
- **Easing**: Ease-in-ease-out or spring
- **Interactive**: Can be interrupted and reversed

### Android

- **Material motion**: Emphasis on motion choreography
- **Duration**:
  - Small: 100-200ms
  - Medium: 200-300ms
  - Large: 300-500ms
- **Easing**:
  - Standard: cubic-bezier(0.4, 0.0, 0.2, 1)
  - Emphasized: cubic-bezier(0.2, 0.0, 0, 1)
- **Transitions**: Container transform, shared axis, fade through

## Implementation Tips

### When to Use Platform-Specific Designs

**Always Different:**
- Navigation patterns (bottom tabs vs. drawer consideration)
- Back button behavior
- System UI (status bar, navigation bar)

**Usually Different:**
- Primary action placement (top-right vs. FAB)
- Modal presentation
- Search UI

**Can Be Unified:**
- List layouts
- Card designs
- Typography (if using custom brand font)
- Color schemes

### React Native Platform API

```typescript
import { Platform } from 'react-native';

// Conditional rendering
{Platform.OS === 'ios' ? <IOSComponent /> : <AndroidComponent />}

// Platform-specific values
const styles = StyleSheet.create({
  container: {
    paddingTop: Platform.OS === 'ios' ? 20 : 0,
    ...Platform.select({
      ios: {
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.25,
        shadowRadius: 3.84,
      },
      android: {
        elevation: 5,
      },
    }),
  },
});

// Platform-specific files
// Component.ios.tsx - Used on iOS
// Component.android.tsx - Used on Android
// Component.tsx - Fallback for both
```

## Quick Decision Guide

### Should You Match Platform Conventions?

**YES - Always match:**
- Navigation patterns
- System gestures
- Back button behavior
- Status bar treatment
- Text selection

**MAYBE - Consider matching:**
- Primary button style
- Modal presentation
- Search interface
- Settings screens

**NO - Can unify:**
- Brand colors
- Custom illustrations
- Content layout
- Typography (if brand font)
- Card/list item designs

### Testing Both Platforms

**Critical Checks:**
- [ ] Navigation feels native on each platform
- [ ] Back button works as expected
- [ ] Touch targets meet minimum size (44pt/48dp)
- [ ] Text is readable (meets platform standards)
- [ ] Animations feel appropriate
- [ ] Safe areas respected (iOS notch, Android gesture bar)
- [ ] Dark mode works on both
- [ ] Keyboard behavior correct

## Resources

### Official Guidelines
- [iOS Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/)
- [Material Design 3](https://m3.material.io/)

### Tools
- [SF Symbols](https://developer.apple.com/sf-symbols/) (iOS icons)
- [Material Symbols](https://fonts.google.com/icons) (Android icons)
- [React Navigation](https://reactnavigation.org/) (cross-platform navigation)
- [React Native Paper](https://reactnativepaper.com/) (Material Design)

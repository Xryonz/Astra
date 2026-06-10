---
name: Mobile App Design Standards
description: This skill should be used when the user asks to "design mobile UI", "review app design", "check UI guidelines", "improve app UX", "design React Native interface", "create app screens", "follow design standards", or mentions iOS/Android design patterns, accessibility, or mobile user experience. Provides comprehensive mobile app UI/UX design guidance.
version: 0.1.0
---

# Mobile App Design Standards

Comprehensive guidance for designing mobile applications that follow platform conventions, accessibility standards, and modern UX best practices.

## Purpose

Apply platform-specific design guidelines, interaction patterns, and accessibility standards when designing, reviewing, or improving mobile application interfaces. Ensure designs are consistent, accessible, performant, and follow 2026 industry best practices.

## When to Use This Skill

Use this skill when:
- Designing new mobile app screens or features
- Reviewing existing UI/UX implementations
- Planning interaction flows and navigation
- Establishing design systems or style guides
- Making platform-specific design decisions
- Optimizing for accessibility or performance
- Modernizing legacy mobile interfaces

## Core Design Principles

### Platform-Native Conventions

**iOS (Human Interface Guidelines):**
- Navigation: Back button in top-left, primary action in top-right
- Tab bar at bottom with 3-5 items
- Large titles for hierarchy
- System fonts: San Francisco (designed for small sizes)
- Haptic feedback for confirmations
- Swipe gestures for navigation

**Android (Material Design):**
- Navigation: Back in top-left, overflow menu in top-right
- Bottom navigation or navigation drawer
- Floating Action Button (FAB) for primary actions
- System fonts: Roboto
- Ripple effects for touch feedback
- Navigation drawer for hierarchical content

**React Native Cross-Platform:**
- Use platform-specific components where behavior differs
- Respect platform conventions for navigation patterns
- Test on both iOS and Android devices
- Consider using React Navigation for platform-aware navigation
- Use Platform API for conditional rendering

### Touch Targets and Spacing

**Minimum Sizes:**
- iOS: 44 × 44 points minimum for all interactive elements
- Android: 48 × 48 dp minimum for all touch targets
- Spacing between targets: minimum 8dp/pt

**Best Practices:**
- Larger targets for primary actions (56dp FAB on Android)
- Adequate spacing prevents accidental taps
- Consider thumb zones on large screens
- Test with actual fingers, not mouse clicks

### Typography Standards

**Minimum Sizes:**
- Body text: 16sp/pt minimum (14sp absolute minimum)
- Labels: 11-12pt minimum
- Avoid text smaller than 11pt for legibility

**Hierarchy:**
- Clear visual hierarchy through size, weight, color
- Consistent scale across the app (e.g., 12/14/16/20/24/32pt)
- Use platform system fonts unless brand requires custom
- Support dynamic type (iOS) and font scaling (Android)

### Color and Contrast

**WCAG Standards:**
- Normal text: 4.5:1 contrast ratio minimum
- Large text (18pt+): 3:1 contrast ratio minimum
- UI components: 3:1 contrast ratio for boundaries

**Color Usage:**
- Don't rely solely on color to convey information
- Provide alternative indicators (icons, labels, patterns)
- Support dark mode where applicable
- Test color blindness scenarios

## Component Architecture

### Container/Presentational Pattern

**Presentational Components:**
- Focus on rendering UI elements
- Receive data via props
- No state management or business logic
- Highly reusable and testable

**Container Components:**
- Handle data fetching and state
- Manage business logic
- Pass data to presentational components
- Connect to app state/context

### Atomic Design Methodology

**Atoms:** Basic building blocks (buttons, inputs, icons)
- Single-purpose components
- Highly reusable
- Consistent styling

**Molecules:** Simple component groups (form fields, search bars)
- Combine atoms into functional units
- Maintain single responsibility

**Organisms:** Complex component assemblies (headers, cards, forms)
- Combine molecules and atoms
- Represent distinct sections

**Templates:** Page-level layouts
- Define structure and placement
- No real content

**Pages:** Specific instances
- Real content applied to templates
- Actual screens in the app

## Accessibility (a11y)

### Screen Reader Support

**Labels and Hints:**
- All interactive elements need accessible labels
- Use `accessibilityLabel` (React Native) or `contentDescription` (Android)
- Provide hints for complex interactions
- Announce dynamic content changes

**Navigation:**
- Logical focus order (top to bottom, left to right)
- Skip navigation for repetitive content
- Clear heading hierarchy
- Announce screen changes

### Cognitive Accessibility

**Reduce Cognitive Load:**
- Clear, concise labels (avoid jargon)
- Consistent patterns throughout app
- Progressive disclosure (show what's needed)
- Clear error messages with recovery steps

**Focus Management:**
- Highlight focused elements clearly
- Support keyboard navigation where applicable
- Maintain focus context during navigation

## Performance Optimization

### Perceived Performance

**Loading States:**
- Skeleton screens for content loading
- Progress indicators for operations >1 second
- Optimistic UI updates (show action immediately)
- Prevent layout shift during load

**Responsiveness:**
- Touch feedback within 100ms
- Visual feedback for all interactions
- Smooth animations (60fps target)
- Debounce rapid inputs

### React Native Best Practices

**Component Optimization:**
- Use `React.memo` for expensive components
- Implement `shouldComponentUpdate` or `useMemo`
- Lazy load heavy components
- Virtualize long lists (FlatList, SectionList)

**Bundle Optimization:**
- Code splitting for large apps
- Remove unused dependencies
- Optimize image sizes and formats
- Use Hermes engine (Android)

## Design System Essentials

### Consistency Checklist

**Visual Consistency:**
- [ ] Unified color palette (primary, secondary, accent, neutrals)
- [ ] Typography scale defined (font sizes, weights, line heights)
- [ ] Spacing system (4pt/8pt grid or similar)
- [ ] Component library documented
- [ ] Icon set consistent in style and size

**Behavioral Consistency:**
- [ ] Navigation patterns unified
- [ ] Button actions predictable
- [ ] Form validation consistent
- [ ] Error handling standardized
- [ ] Loading states uniform

### Component Documentation

Document each component with:
- Purpose and use cases
- Props and their types
- Visual variants (states, sizes, styles)
- Accessibility requirements
- Platform-specific considerations
- Usage examples

## 2026 Design Trends (Optional Enhancement)

### AI-Driven Interaction

**Predictive UI:**
- Anticipate user needs based on context
- Smart defaults in forms
- Personalized content ordering
- Contextual suggestions

**Conversational Interfaces:**
- Voice interaction support
- Natural language input
- Progressive disclosure through conversation

### Advanced Visual Design

**Spatial Computing:**
- Consider depth and layering
- Subtle parallax effects
- 3D elements where appropriate
- Immersive experiences

**Micro-Interactions:**
- Delightful animation details
- Haptic feedback coordination
- Sound design for actions
- Emotional engagement

## Workflow Integration

### Design Phase

1. **Define requirements** - Understand user needs and business goals
2. **Research patterns** - Review platform guidelines and competitors
3. **Sketch wireframes** - Low-fidelity layouts first
4. **Create prototypes** - Interactive mockups for testing
5. **Validate designs** - User testing and accessibility checks

### Review Phase

When reviewing designs or implementations:
1. Check platform conventions (iOS vs Android)
2. Verify touch target sizes (44pt/48dp minimum)
3. Test color contrast ratios (WCAG AA minimum)
4. Validate accessibility labels
5. Confirm consistency with design system
6. Review performance considerations

### Implementation Phase

1. Use presentational/container pattern
2. Build atomic components bottom-up
3. Implement accessibility from start
4. Test on real devices (both platforms)
5. Optimize for performance
6. Document component usage

## Additional Resources

### Reference Files

For detailed platform-specific guidance, consult:

**Platform Guidelines:**
- **`references/ios-guidelines.md`** - Comprehensive iOS HIG summary (SF Fonts, Navigation, VoiceOver, Haptics)
- **`references/android-guidelines.md`** - Material Design 3 essentials (Components, Motion, TalkBack)
- **`references/platform-differences.md`** - iOS vs Android quick reference (navigation, gestures, components)

**Implementation Guides:**
- **`references/accessibility-checklist.md`** - Complete WCAG 2.1 AA testing guide (screen readers, contrast, touch targets)
- **`references/performance-patterns.md`** - React Native optimization (FlatList, animations, bundle size, memory)
- **`references/common-mistakes.md`** - Common design errors and fixes (touch targets, typography, accessibility, forms)
- **`references/ui-libraries.md`** - React Native UI library comparison (Paper, Elements, Tamagui, NativeBase)

### Example Files

Working code examples in `examples/`:
- **`profile-screen-example.tsx`** - Complete profile screen with Atomic Design, accessibility labels, performance optimization
- **`form-validation-example.tsx`** - Accessible form with real-time validation, proper keyboard types, error handling
- **`optimized-list-example.tsx`** - FlatList with virtualization, React.memo, skeleton loading, pull-to-refresh
- **`design-system-config.ts`** - Complete design tokens (colors, typography, spacing, shadows, component variants)

### Utility Scripts

Development tools in `scripts/`:
- **`check-contrast.py`** - WCAG contrast ratio validator (usage: `python check-contrast.py "#FFFFFF" "#000000"`)
- **`validate-touch-targets.sh`** - Validates minimum 44pt/48dp touch targets (usage: `./validate-touch-targets.sh src/`)
- **`accessibility-audit.sh`** - Audits for missing labels, roles, and a11y issues (usage: `./accessibility-audit.sh src/`)

## Quick Reference

### Touch Targets
- iOS: 44×44pt minimum
- Android: 48×48dp minimum

### Typography
- Body: 16sp/pt minimum
- Labels: 11pt minimum

### Contrast
- Text: 4.5:1 minimum
- Large text: 3:1 minimum
- Components: 3:1 minimum

### Navigation
- iOS: Back top-left, action top-right, tabs bottom
- Android: Back top-left, menu top-right, FAB bottom-right

### Performance
- Touch feedback: <100ms
- Animations: 60fps target
- Loading indicators: >1 second operations

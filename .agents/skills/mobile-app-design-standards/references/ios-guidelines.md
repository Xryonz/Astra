# iOS Human Interface Guidelines - Essential Summary

Comprehensive reference for iOS-specific design patterns and conventions based on Apple's Human Interface Guidelines.

## Navigation Patterns

### Tab Bar

**Placement:** Bottom of screen
**Items:** 3-5 tabs maximum
**Best for:** Top-level navigation, peer information architecture

**Guidelines:**
- Use clear, concise labels
- Include both icon and text
- Selected state should be obvious
- Don't use for sequential tasks
- Tab order persists across app use

**Icons:**
- SF Symbols recommended for consistency
- 25×25pt target area minimum
- Consistent style across tabs

### Navigation Bar

**Placement:** Top of screen
**Purpose:** Hierarchical navigation, context, controls

**Elements:**
- Back button: Top-left (automatic in UINavigationController)
- Title: Center or large title at top
- Actions: Top-right (1-2 buttons maximum)

**Large Titles:**
- Use for top-level screens
- Collapses on scroll
- Provides clear hierarchy
- Better for readability

### Modal Presentation

**Use Cases:**
- Focused task completion
- Critical information
- User must take action or dismiss

**Styles:**
- Sheet: Bottom-up presentation (default iOS 13+)
- Full screen: Immersive experience
- Page sheet: iPad modal presentation
- Form sheet: Centered modal (iPad)

**Best Practices:**
- Always provide clear dismiss method
- Use Cancel/Done buttons in nav bar
- Consider pull-to-dismiss for sheets
- Avoid nested modals

## Typography

### San Francisco Font

**Why SF:**
- Designed for legibility at small sizes
- Optical sizing adjusts automatically
- Excellent rendering on Retina displays
- System integration and dynamic type support

**Text Styles:**
```
Large Title:  34pt (iOS 11+)
Title 1:      28pt
Title 2:      22pt
Title 3:      20pt
Headline:     17pt (semibold)
Body:         17pt
Callout:      16pt
Subheadline:  15pt
Footnote:     13pt
Caption 1:    12pt
Caption 2:    11pt
```

### Dynamic Type

**Support Dynamic Type:**
- Use text styles instead of fixed sizes
- Test with largest accessibility sizes
- Allow layout to adapt to text size
- Don't truncate text unnecessarily

**Implementation:**
```swift
// Use preferredFont
label.font = UIFont.preferredFont(forTextStyle: .body)
label.adjustsFontForContentSizeCategory = true
```

## Color and Vibrancy

### System Colors

**Semantic Colors:**
- Label colors: primary, secondary, tertiary, quaternary
- Background colors: system, secondary, tertiary
- Fill colors: primary through quaternary
- Separator colors: opaque and non-opaque

**Benefits:**
- Automatic dark mode support
- Accessibility built-in
- Platform consistency
- Future-proof

**Accent Colors:**
- Use app's accent color for interactive elements
- Tint navigation bar and tab bar icons
- Highlight selected states
- Brand identity

### Dark Mode

**Design Considerations:**
- Use semantic colors for automatic adaptation
- Test all screens in dark mode
- Increase contrast if needed
- Use elevated backgrounds for layers

**Color Adjustments:**
- Don't simply invert colors
- Reduce pure white brightness
- Maintain sufficient contrast
- Test with True Tone displays

## Interactive Elements

### Buttons

**Styles:**
- **Filled:** High emphasis, primary actions
- **Tinted:** Medium emphasis, secondary actions
- **Gray:** Low emphasis, tertiary actions
- **Plain:** Minimal emphasis, inline actions

**Sizing:**
- Minimum 44×44pt hit area
- Comfortable padding around text
- Consistent sizing for similar actions

**Text:**
- Use verbs for button labels
- Be specific ("Delete Photo" not "Delete")
- Use Title Case
- Keep concise

### Text Fields

**Keyboard Types:**
- Default: Standard text
- Email Address: @ and . easily accessible
- Number Pad: Numeric only
- Phone Pad: Phone number entry
- URL: / and .com accessible

**Input Validation:**
- Real-time feedback when possible
- Clear error messages
- Don't block input unnecessarily
- Preserve user input on errors

### Switches and Toggles

**Use Cases:**
- Binary on/off states
- Immediate effect (no save needed)
- Settings and preferences

**Guidelines:**
- Label describes off state
- On/off should be obvious
- Immediate visual feedback
- Consider accessibility

## Gestures

### Standard Gestures

**Tap:**
- Primary interaction method
- Activates controls
- Selects items

**Swipe:**
- Navigate between screens/pages
- Reveal actions (list items)
- Dismiss modals

**Pinch:**
- Zoom in/out
- Maps, photos, web content

**Pan:**
- Drag elements
- Scroll content
- Move objects

**Long Press:**
- Context menus (iOS 13+)
- Reveal additional options
- Editing mode

### Edge Gestures

**Screen Edge Swipe:**
- Back navigation (left edge)
- System gestures (bottom)
- Don't override system gestures
- Provide button alternatives

## Layout and Spacing

### Safe Areas

**Respect Safe Areas:**
- Status bar area
- Home indicator area
- Rounded corners
- Dynamic Island (iPhone 14 Pro+)

**Implementation:**
```swift
// Use safeAreaLayoutGuide
view.topAnchor.constraint(equalTo: safeAreaLayoutGuide.topAnchor)
```

### Spacing Guidelines

**Common Spacing:**
- Screen margins: 16pt (iPhone), 20pt (iPad)
- Between sections: 35-44pt
- Between elements: 8-12pt
- Within groups: 4-8pt

**Grid System:**
- Use 8pt grid for consistency
- Align to baseline for text
- Snap to pixel boundaries

## Haptic Feedback

### Haptic Types

**Impact:**
- Light: Subtle, small changes
- Medium: Standard feedback
- Heavy: Significant actions

**Notification:**
- Success: Task completed
- Warning: Caution needed
- Error: Failed action

**Selection:**
- Light feedback for changing values
- Use with pickers, sliders

### Best Practices

**Use Haptics For:**
- Confirming selections
- Task completion
- Error states
- Significant changes

**Don't Use For:**
- Every interaction (overwhelming)
- System actions (redundant)
- Continuous feedback (annoying)

## Accessibility (iOS-Specific)

### VoiceOver Support

**Elements:**
```swift
button.isAccessibilityElement = true
button.accessibilityLabel = "Add to cart"
button.accessibilityHint = "Adds item to your shopping cart"
button.accessibilityTraits = .button
```

**Best Practices:**
- Label describes what element is
- Hint describes what it does
- Don't include type ("button", "image") in label
- Use traits for semantic meaning

### Reduce Motion

**Respect User Preferences:**
```swift
if UIAccessibility.isReduceMotionEnabled {
    // Use fade instead of scale animation
} else {
    // Use full animation
}
```

### Dynamic Type Testing

**Test Sizes:**
- Extra Small (xSmall)
- Small
- Medium (default)
- Large
- Extra Large through Extra Extra Extra Large
- Accessibility sizes (AX1-AX5)

**Ensure:**
- Text doesn't truncate
- Layout adapts gracefully
- Essential information visible
- Touch targets remain adequate

## Performance

### Launch Performance

**First Impression:**
- Launch screen matches first screen
- Don't show splash screen ads
- Launch in <400ms if possible
- Display content immediately

**Optimization:**
- Minimize launch-time work
- Defer non-critical tasks
- Cache data appropriately
- Use background processing

### Scrolling Performance

**60fps Target:**
- Lightweight cell rendering
- Async image loading
- Dequeue reusable cells
- Profile with Instruments

**Optimization:**
```swift
// Prefetch images
func collectionView(_ collectionView: UICollectionView,
                   prefetchItemsAt indexPaths: [IndexPath]) {
    // Preload images for upcoming cells
}
```

## Platform-Specific Features

### SF Symbols

**Benefits:**
- Thousands of configurable symbols
- Automatic alignment with text
- Support for multiple weights
- Localization built-in

**Usage:**
```swift
let image = UIImage(systemName: "heart.fill")
imageView.preferredSymbolConfiguration = .init(pointSize: 24, weight: .medium)
```

### Context Menus

**iOS 13+:**
- Long press for context actions
- Replace 3D Touch peek/pop
- Preview with actions

**Guidelines:**
- Show most common actions
- Use destructive style for dangerous actions
- Include preview when helpful
- Limit to 3-5 actions

### Widgets (iOS 14+)

**Sizes:**
- Small: 2×2 grid
- Medium: 4×2 grid
- Large: 4×4 grid

**Best Practices:**
- Glanceable information
- Deep link to app
- Update regularly
- Support all sizes

## Resources and Tools

### Official Resources

- [Apple Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/)
- [SF Symbols App](https://developer.apple.com/sf-symbols/)
- [iOS Design Resources](https://developer.apple.com/design/resources/)

### Testing Tools

- Xcode Accessibility Inspector
- Simulator accessibility settings
- Physical device testing
- TestFlight for beta testing

### Design Tools

- Sketch with iOS UI Kit
- Figma iOS design system
- SF Symbols integration
- Xcode asset catalogs

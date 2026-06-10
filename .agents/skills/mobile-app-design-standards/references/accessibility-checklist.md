# Mobile Accessibility Checklist

Comprehensive guide for implementing accessible mobile applications following WCAG 2.1 Level AA standards and platform-specific best practices.

## Visual Accessibility

### Color Contrast

**WCAG Requirements:**
- [ ] Normal text (< 18pt): 4.5:1 contrast ratio minimum
- [ ] Large text (≥ 18pt or ≥ 14pt bold): 3:1 contrast ratio minimum
- [ ] UI components and graphics: 3:1 contrast ratio minimum
- [ ] Focus indicators: 3:1 contrast against background

**Testing Tools:**
- WebAIM Contrast Checker
- Stark (Figma/Sketch plugin)
- iOS Accessibility Inspector
- Android Accessibility Scanner

**Common Issues:**
- Gray text on white background (often fails)
- Light placeholders in text fields
- Disabled button text too light
- Secondary actions with insufficient contrast

### Color Usage

**Don't Rely on Color Alone:**
- [ ] Icons accompany color-coded information
- [ ] Patterns or shapes differentiate elements
- [ ] Text labels for all states
- [ ] Multiple visual cues for errors/success

**Examples:**
- ✅ Green checkmark + "Success" text
- ❌ Only green background for success
- ✅ Red border + error icon + error text
- ❌ Only red text for errors

### Color Blindness Support

**Types to Consider:**
- Deuteranopia (red-green, most common)
- Protanopia (red-green)
- Tritanopia (blue-yellow)
- Monochromacy (total color blindness)

**Best Practices:**
- [ ] Test designs with color blindness simulators
- [ ] Use patterns in addition to colors (charts, graphs)
- [ ] Avoid red-green as only differentiator
- [ ] Ensure information is conveyed without color

**Testing:**
- Color Oracle (macOS, Windows, Linux)
- Sim Daltonism (macOS)
- Chrome DevTools vision deficiency emulation

## Typography and Readability

### Font Sizing

**Minimum Sizes:**
- [ ] Body text: 16sp/pt minimum (14sp absolute minimum)
- [ ] Labels and captions: 11pt minimum
- [ ] Touch target labels: 14sp minimum for legibility

**Dynamic Type Support:**
- [ ] iOS: Support Dynamic Type (preferredFont)
- [ ] Android: Support font scaling (sp units)
- [ ] Test at largest accessibility sizes (AX5, 200%)
- [ ] Layout adapts without truncation
- [ ] No horizontal scrolling for text

### Line Length and Spacing

**Optimal Readability:**
- [ ] Line length: 40-60 characters optimal, 75 maximum
- [ ] Line height: 1.5× font size for body text
- [ ] Paragraph spacing: Clear visual separation
- [ ] Letter spacing: Avoid overly tight text

### Font Weight and Style

**Legibility:**
- [ ] Sufficient font weight for small text
- [ ] Avoid all-caps for body text (harder to read)
- [ ] Use bold/medium weight for emphasis, not just size
- [ ] Avoid italic for large blocks of text

## Touch Targets and Controls

### Minimum Sizes

**Platform Requirements:**
- [ ] iOS: 44×44 pt minimum for all interactive elements
- [ ] Android: 48×48 dp minimum for all touch targets
- [ ] Spacing between targets: 8dp/pt minimum
- [ ] Increase size for critical actions (56dp FAB)

**Testing:**
- [ ] Test with fingers, not mouse/stylus
- [ ] Verify on actual devices, not simulators
- [ ] Test with one-handed use
- [ ] Consider thumb zones on large screens

### Spacing and Layout

**Prevent Accidental Taps:**
- [ ] Adequate spacing between adjacent buttons
- [ ] Destructive actions separated from primary
- [ ] Confirmation for irreversible actions
- [ ] Disabled state visually distinct

**Edge Cases:**
- [ ] List item actions have adequate touch area
- [ ] Swipe actions discoverable and forgiving
- [ ] Toggle switches large enough (minimum 51×31 pt)
- [ ] Checkbox/radio buttons 40×40 dp minimum

## Screen Reader Support

### iOS VoiceOver

**Element Accessibility:**
```swift
// Set accessibility properties
element.isAccessibilityElement = true
element.accessibilityLabel = "Add to cart" // What it is
element.accessibilityHint = "Adds item to shopping cart" // What it does
element.accessibilityTraits = .button // Type of element
element.accessibilityValue = "2 items" // Current value/state
```

**Checklist:**
- [ ] All interactive elements have labels
- [ ] Labels are concise and descriptive
- [ ] Don't include element type in label ("button", "image")
- [ ] Use traits to convey element type
- [ ] Provide hints for complex interactions
- [ ] Group related elements logically
- [ ] Set accessibilityElementsHidden for decorative elements

### Android TalkBack

**Content Descriptions:**
```kotlin
// Jetpack Compose
modifier = Modifier.semantics {
    contentDescription = "Add to cart"
    stateDescription = "2 items in cart"
    role = Role.Button
}

// XML
android:contentDescription="Add to cart"
android:importantForAccessibility="yes"
```

**Checklist:**
- [ ] All ImageButtons have contentDescription
- [ ] Icons have meaningful descriptions
- [ ] Decorative images: importantForAccessibility="no"
- [ ] Complex views grouped with contentDescription
- [ ] Dynamic content announces changes
- [ ] Custom views support TalkBack gestures

### Focus Management

**iOS Focus Order:**
- [ ] Logical reading order (top-to-bottom, left-to-right)
- [ ] accessibilityElements array for custom order
- [ ] Focus moves to newly presented content
- [ ] Focus returns to trigger after dismissing modal
- [ ] Skip repetitive content (provide skip link)

**Android Focus Order:**
- [ ] Traversal order follows visual layout
- [ ] Use accessibilityTraversalBefore/After for custom order
- [ ] Announce screen title changes
- [ ] Announce dynamic content updates
- [ ] Avoid focus traps (user can navigate away)

### Announcements

**Dynamic Content:**
```swift
// iOS
UIAccessibility.post(notification: .announcement,
                     argument: "Item added to cart")

// Android
view.announceForAccessibility("Item added to cart")
```

**When to Announce:**
- [ ] Content loaded asynchronously
- [ ] Form submission success/failure
- [ ] Item added/removed from list
- [ ] Error messages
- [ ] Timer/countdown updates

## Keyboard and Alternative Input

### Keyboard Navigation

**iOS:**
- [ ] Support hardware keyboard shortcuts
- [ ] Tab key navigates between fields
- [ ] Return key submits forms
- [ ] Escape dismisses modals

**Android:**
- [ ] D-pad navigation works correctly
- [ ] Tab navigation follows logical order
- [ ] Enter/Space activates focused element
- [ ] Back button behaves predictably

### Focus Indicators

**Visual Feedback:**
- [ ] Focused element has clear indicator (border, highlight)
- [ ] Focus indicator meets 3:1 contrast requirement
- [ ] Focus indicator visible in all states
- [ ] Don't remove focus outline without replacement

### Switch Control (iOS) and Switch Access (Android)

**Compatibility:**
- [ ] All interactive elements reachable
- [ ] Logical scanning order
- [ ] Actions completable with simple select
- [ ] Avoid time-based interactions

## Motion and Animation

### Reduce Motion

**Respect User Preferences:**
```swift
// iOS
if UIAccessibility.isReduceMotionEnabled {
    // Use simple fade or no animation
} else {
    // Use full animation
}

// Android
val animationDuration = if (isReduceMotionEnabled()) 0L else 300L
```

**Guidelines:**
- [ ] Provide reduced motion alternatives
- [ ] Essential motion only (no decorative)
- [ ] Avoid parallax effects in reduced motion
- [ ] Crossfade instead of slide/scale
- [ ] Instant transitions acceptable

### Vestibular Disorders

**Avoid:**
- [ ] Excessive parallax scrolling
- [ ] Rapid animations or pulsing
- [ ] Large area movements
- [ ] Simulated 3D motion
- [ ] Auto-playing video with motion

### Seizure Prevention

**Critical Requirements:**
- [ ] No content flashes more than 3 times per second
- [ ] No large area flashing
- [ ] Avoid red flashing patterns
- [ ] Warning for video with flashing

## Forms and Input

### Labels and Instructions

**Clarity:**
- [ ] All inputs have visible labels
- [ ] Labels remain visible when field is focused
- [ ] Required fields clearly marked
- [ ] Instructions provided before input
- [ ] Help text available and accessible

**Error Handling:**
- [ ] Errors identified and described
- [ ] Error messages next to problematic field
- [ ] Suggestions for fixing errors
- [ ] Error summary at top of form
- [ ] Preserve user input on error

### Input Types

**Keyboard Optimization:**
- [ ] Appropriate keyboard type for input
- [ ] Email fields show email keyboard
- [ ] Phone fields show number pad
- [ ] URL fields show URL keyboard
- [ ] Auto-capitalization set correctly

### Validation

**Accessible Validation:**
- [ ] Real-time validation announced
- [ ] Error messages associated with fields
- [ ] aria-invalid (web) or accessibilityValue (native)
- [ ] Success confirmation announced
- [ ] Don't rely on color alone for errors

## Media and Content

### Images

**Alternative Text:**
- [ ] Informative images have descriptive alt text
- [ ] Decorative images marked as decorative
- [ ] Complex images have long description
- [ ] Charts and graphs have data table alternative
- [ ] Logo alt text includes company name

**Image Text:**
- [ ] Avoid text in images when possible
- [ ] If unavoidable, provide text alternative
- [ ] Ensure sufficient contrast in image text

### Video and Audio

**Captions:**
- [ ] All video has captions
- [ ] Captions accurate and synchronized
- [ ] Caption all speech and important sounds
- [ ] Allow user to turn captions on/off

**Audio Descriptions:**
- [ ] Provide audio descriptions for video
- [ ] Describe important visual information
- [ ] Alternative: provide transcript

**Transcripts:**
- [ ] Full text transcript available
- [ ] Includes all speech and sounds
- [ ] Identifies speakers
- [ ] Describes important actions/visuals

### Auto-Playing Content

**User Control:**
- [ ] No auto-play with sound (or < 3 seconds)
- [ ] Provide pause/stop control
- [ ] Control accessible within 3 interactions
- [ ] Paused content doesn't auto-resume

## Testing Methodology

### Automated Testing

**Tools:**
- iOS Accessibility Inspector
- Android Accessibility Scanner
- Espresso accessibility checks
- XCTest accessibility audits

**Run Regularly:**
- [ ] As part of CI/CD pipeline
- [ ] Before each release
- [ ] After major UI changes

### Manual Testing

**Screen Reader Testing:**
- [ ] Navigate entire app with VoiceOver/TalkBack only
- [ ] All content and actions accessible
- [ ] Logical reading order
- [ ] All states announced
- [ ] Dynamic updates announced

**Keyboard Testing:**
- [ ] Complete all tasks with keyboard only
- [ ] Tab order logical
- [ ] Focus indicators visible
- [ ] No keyboard traps

**Visual Testing:**
- [ ] Test with maximum text size (200% or AX5)
- [ ] Test in high contrast mode
- [ ] Test with color blindness simulators
- [ ] Test in bright sunlight (if applicable)

### User Testing

**Include People with Disabilities:**
- [ ] Recruit users with various disabilities
- [ ] Screen reader users
- [ ] Motor impairment users
- [ ] Low vision users
- [ ] Cognitive disability users

**Observe Real Usage:**
- [ ] Watch how assistive tech is actually used
- [ ] Identify pain points
- [ ] Gather feedback
- [ ] Iterate based on findings

## Platform-Specific Features

### iOS Accessibility Features

**Support These Features:**
- [ ] Dynamic Type (text sizing)
- [ ] VoiceOver (screen reader)
- [ ] Voice Control (voice commands)
- [ ] Switch Control (assistive switches)
- [ ] Reduce Motion
- [ ] Reduce Transparency
- [ ] Increase Contrast
- [ ] Differentiate Without Color
- [ ] On/Off Labels (switches)
- [ ] Button Shapes

### Android Accessibility Features

**Support These Features:**
- [ ] Font size and display size scaling
- [ ] TalkBack (screen reader)
- [ ] Voice Access (voice commands)
- [ ] Switch Access (assistive switches)
- [ ] Remove animations (reduce motion)
- [ ] High contrast text
- [ ] Color correction
- [ ] Color inversion

## Common Mistakes to Avoid

### Don't Do These:

**Visual:**
- ❌ Using color alone to convey information
- ❌ Text smaller than 11pt
- ❌ Insufficient contrast (< 4.5:1 for text)
- ❌ Touch targets smaller than 44pt/48dp

**Screen Reader:**
- ❌ Missing accessibility labels on buttons/images
- ❌ Including element type in label ("button", "image")
- ❌ Unlabeled form fields
- ❌ Not announcing dynamic content changes

**Interaction:**
- ❌ Time-based interactions without alternatives
- ❌ Gestures as only way to perform action
- ❌ Auto-playing content without controls
- ❌ Keyboard focus not visible

**Content:**
- ❌ Images without alt text
- ❌ Video without captions
- ❌ Audio without transcript
- ❌ Flashing content (> 3 times/second)

## Resources

### Official Guidelines

- [WCAG 2.1 Guidelines](https://www.w3.org/WAI/WCAG21/quickref/)
- [iOS Accessibility](https://developer.apple.com/accessibility/ios/)
- [Android Accessibility](https://developer.android.com/guide/topics/ui/accessibility)

### Testing Tools

- [Accessibility Inspector (Xcode)](https://developer.apple.com/library/archive/documentation/Accessibility/Conceptual/AccessibilityMacOSX/OSXAXTestingApps.html)
- [Accessibility Scanner (Android)](https://play.google.com/store/apps/details?id=com.google.android.apps.accessibility.auditor)
- [Color Contrast Analyzers](https://www.tpgi.com/color-contrast-checker/)

### Learning Resources

- [WebAIM](https://webaim.org/)
- [A11y Project](https://www.a11yproject.com/)
- [Deque University](https://dequeuniversity.com/)
- [Google Accessibility Courses](https://www.udacity.com/course/web-accessibility--ud891)

## Accessibility Statement Template

```markdown
# Accessibility Statement for [App Name]

We are committed to ensuring digital accessibility for people with disabilities.
We continually improve the user experience for everyone and apply relevant
accessibility standards.

## Conformance Status
[App Name] is partially conformant with WCAG 2.1 level AA. Partially conformant
means that some parts of the content do not fully conform to the accessibility
standard.

## Measures
[Organization] takes the following measures to ensure accessibility:
- Include accessibility throughout our internal policies
- Integrate accessibility into our procurement practices
- Provide continual accessibility training for our staff
- Include people with disabilities in our design personas
- Test with assistive technology users

## Feedback
We welcome your feedback on the accessibility of [App Name]. Please contact us:
- Email: [email]
- Phone: [phone]
- Address: [address]

## Technical Specifications
Accessibility of [App Name] relies on the following technologies:
- iOS 15.0+
- Android 10.0+
- Screen readers (VoiceOver, TalkBack)
- Voice control systems

## Known Limitations
Despite our efforts, some limitations may exist:
- [List any known issues]
- [Expected timeline for fixes]

This statement was last updated on [Date].
```

# Android Material Design - Essential Summary

Comprehensive reference for Android-specific design patterns and conventions based on Google's Material Design guidelines.

## Material Design Principles

### Material Metaphor

**Physical Properties:**
- Surfaces and shadows create hierarchy
- Motion provides meaning
- Elevation defines importance
- Light source is consistent (top)

**Material Characteristics:**
- Has thickness (1dp default)
- Casts shadows
- Cannot pass through other material
- Can change size and shape

### Bold, Graphic, Intentional

**Typography:**
- Roboto font family
- Clear hierarchy
- Intentional color and whitespace
- Grid-based layouts

**Color:**
- Vibrant, saturated colors
- Primary and secondary palettes
- Meaningful use of color
- Sufficient contrast

### Motion Provides Meaning

**Responsive:**
- User-initiated actions feel instant
- Visual feedback acknowledges input
- Smooth 60fps animations

**Natural:**
- Ease-in/ease-out curves
- Realistic physics
- Choreographed transitions

**Aware:**
- Guides focus and attention
- Maintains continuity
- Hierarchical timing

## Navigation Patterns

### Bottom Navigation

**When to Use:**
- 3-5 top-level destinations
- Equal importance destinations
- Frequent switching needed

**Guidelines:**
- Icons with text labels
- Active state clearly indicated
- Fixed position (always visible)
- One tap to switch
- Don't use for sequential tasks

**Implementation:**
```kotlin
BottomNavigationView(
    modifier = Modifier.fillMaxWidth(),
    containerColor = MaterialTheme.colorScheme.surface
)
```

### Navigation Drawer

**When to Use:**
- 5+ top-level destinations
- Less frequent navigation
- Hierarchical structure

**Types:**
- **Standard:** Temporarily appears (mobile)
- **Modal:** Blocks interaction with content
- **Permanent:** Always visible (tablet/desktop)

**Guidelines:**
- Group related destinations
- Highlight active destination
- Include header with app branding
- Close on item selection (modal)

### Top App Bar

**Elements:**
- Navigation icon (left): Back or menu
- Title: Screen title or app name
- Action items (right): 0-3 icons
- Overflow menu: Additional actions

**Variations:**
- **Small:** Standard height (56dp mobile, 64dp tablet)
- **Medium:** With subtitle or search
- **Large:** Prominent title that collapses on scroll

**Scroll Behavior:**
- Pin: Stays visible
- Enter Always: Shows immediately on up scroll
- Scroll: Hides/shows with content
- Collapse: Large title collapses to small

## Component Guidelines

### Floating Action Button (FAB)

**Purpose:**
- Primary action on screen
- Most common or important task
- One per screen

**Placement:**
- Bottom right: 16dp from edges
- Above bottom navigation
- Consistent position across screens

**Sizing:**
- Default: 56dp diameter
- Mini: 40dp diameter
- Extended: Variable width with text

**Best Practices:**
- Use for constructive actions (create, compose, add)
- Avoid for destructive actions
- Don't use for navigation
- Hide on scroll if needed

### Cards

**Purpose:**
- Group related content
- Entry point to detailed information
- Present multiple types of content

**Anatomy:**
- Container (elevated surface)
- Optional header
- Content area
- Optional actions

**Elevation:**
- Resting: 1dp
- Raised (hover): 8dp
- Maximum: 24dp

**Types:**
- **Elevated:** Has shadow (default)
- **Filled:** Tinted surface, no shadow
- **Outlined:** Border, no shadow

### Buttons

**Types (by emphasis):**

**Filled Button (High emphasis):**
- Primary actions
- Highest visual impact
- Use sparingly per screen

**Outlined Button (Medium emphasis):**
- Secondary actions
- Less prominent than filled
- More prominent than text

**Text Button (Low emphasis):**
- Tertiary actions
- Minimal visual weight
- Inline with content

**Icon Buttons:**
- Compact actions
- Toolbar and app bars
- 48dp touch target

**Sizing:**
- Height: 36-40dp minimum
- Touch target: 48dp minimum
- Horizontal padding: 16dp

## Typography

### Roboto Font Family

**Weights:**
- Light (300)
- Regular (400)
- Medium (500)
- Bold (700)

**Variants:**
- Roboto: Body text, UI
- Roboto Condensed: Compact layouts
- Roboto Mono: Code, tabular data

### Type Scale

Material Design 3 type scale:
```
Display Large:   57sp / Roboto Regular
Display Medium:  45sp / Roboto Regular
Display Small:   36sp / Roboto Regular

Headline Large:  32sp / Roboto Regular
Headline Medium: 28sp / Roboto Regular
Headline Small:  24sp / Roboto Regular

Title Large:     22sp / Roboto Medium
Title Medium:    16sp / Roboto Medium
Title Small:     14sp / Roboto Medium

Body Large:      16sp / Roboto Regular
Body Medium:     14sp / Roboto Regular
Body Small:      12sp / Roboto Regular

Label Large:     14sp / Roboto Medium
Label Medium:    12sp / Roboto Medium
Label Small:     11sp / Roboto Medium
```

### Best Practices

**Line Length:**
- Optimal: 40-60 characters
- Maximum: 75 characters
- Narrow columns for readability

**Line Height:**
- Body text: 1.5× font size
- Headlines: 1.2× font size
- Allow text to breathe

**Alignment:**
- Left-align for LTR languages
- Right-align for RTL languages
- Avoid justified text (uneven spacing)

## Color System

### Material You (Dynamic Color)

**Color Roles:**
- **Primary:** Brand color, main actions
- **Secondary:** Accent color, secondary actions
- **Tertiary:** Highlighting, special elements
- **Error:** Errors and warnings
- **Surface:** Backgrounds and containers
- **Outline:** Borders and dividers

**Color Variants:**
- Primary, On Primary
- Primary Container, On Primary Container
- (Same pattern for Secondary, Tertiary, Error)

### Theming

**Light Theme:**
```kotlin
lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    // ... more colors
)
```

**Dark Theme:**
```kotlin
darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    // ... more colors
)
```

### Contrast Requirements

**Text Contrast (WCAG AA):**
- Large text (24sp+): 3:1 minimum
- Small text: 4.5:1 minimum

**Component Contrast:**
- UI elements: 3:1 against background
- Borders and dividers: 3:1 minimum

## Layout and Spacing

### Grid System

**Columns:**
- Mobile: 4 columns
- Tablet: 8 columns
- Desktop: 12 columns

**Margins:**
- Mobile: 16dp
- Tablet: 24dp
- Desktop: 24dp+

**Gutters:**
- Mobile: 16dp
- Tablet: 24dp
- Desktop: 24dp

### Spacing Units

**8dp Grid:**
- All spacing in multiples of 8dp
- Elements aligned to grid
- Iconography aligned to 4dp grid

**Common Spacing:**
- Extra Small: 4dp
- Small: 8dp
- Medium: 16dp
- Large: 24dp
- Extra Large: 32dp

### Touch Targets

**Minimum Sizes:**
- Touch target: 48×48dp
- Icon button: 40dp icon, 48dp touch
- Checkbox/Radio: 40dp

**Spacing:**
- Between touch targets: 8dp minimum
- Critical actions: 12dp+ spacing

## Motion and Animation

### Duration

**Short (100-200ms):**
- Small movements
- Fades and simple transitions
- Icon changes

**Medium (200-300ms):**
- Screen transitions
- Card expansions
- Moderate movements

**Long (300-500ms):**
- Large transformations
- Full-screen transitions
- Complex choreography

### Easing Curves

**Standard Easing (Deceleration):**
```
cubic-bezier(0.4, 0.0, 0.2, 1)
```
- Elements entering screen
- Expanding surfaces
- Most transitions

**Emphasized Easing:**
```
cubic-bezier(0.2, 0.0, 0, 1)
```
- Important entrances
- Hero transitions
- Expressive moments

**Deceleration:**
```
cubic-bezier(0.0, 0.0, 0.2, 1)
```
- Elements exiting screen
- Closing dialogs

**Acceleration:**
```
cubic-bezier(0.4, 0.0, 1, 1)
```
- Elements leaving screen permanently

### Transitions

**Container Transform:**
- Seamless connection between screens
- Element expands to fill screen
- Maintains visual continuity

**Shared Axis:**
- Relationships between screens
- Spatial or navigational
- X, Y, or Z axis

**Fade Through:**
- Screens with no relationship
- Simple cross-fade
- Brief pause at middle

**Fade:**
- Small components appearing/disappearing
- Simple and unobtrusive

## Accessibility

### Screen Readers (TalkBack)

**Content Descriptions:**
```kotlin
modifier = Modifier.semantics {
    contentDescription = "Add to cart"
    role = Role.Button
}
```

**Best Practices:**
- Describe what element is, not what it looks like
- Include state information ("Checked")
- Announce dynamic content changes
- Group related elements

### Touch Target Sizes

**Minimum Sizes:**
- Standard: 48×48dp
- Dense layouts: 36×36dp (use sparingly)
- Spacing: 8dp between targets

**Testing:**
- Use large text settings
- Test with TalkBack enabled
- Verify touch targets don't overlap

### Color Contrast

**Material Design Standards:**
- Primary text: 87% opacity on light, 100% on dark
- Secondary text: 60% opacity on light, 70% on dark
- Disabled text: 38% opacity on light, 50% on dark

**Testing Tools:**
- Android Accessibility Scanner
- Material Theme Builder contrast checker
- WCAG contrast ratio calculators

## Performance

### Overdraw Reduction

**Optimize Layers:**
- Remove unnecessary backgrounds
- Use clipToPadding judiciously
- Minimize view hierarchy depth
- Use ConstraintLayout for flat hierarchies

**Debug Overdraw:**
- Settings → Developer Options → Debug GPU overdraw
- Target: Most areas blue or green
- Avoid red (4× overdraw)

### Layout Performance

**ConstraintLayout Benefits:**
- Flat view hierarchy
- Performant complex layouts
- Responsive design support
- Chain and barrier features

**RecyclerView Optimization:**
```kotlin
// Set fixed size if possible
recyclerView.setHasFixedSize(true)

// Prefetch items
layoutManager.isItemPrefetchEnabled = true

// Recycle views aggressively
recyclerView.setRecycledViewPool(sharedPool)
```

### Image Loading

**Best Practices:**
- Use Coil or Glide for async loading
- Load appropriate resolutions
- Cache aggressively
- Placeholder while loading
- Error states for failures

## Material Components

### Bottom Sheets

**Types:**
- **Standard:** Non-modal, coexists with content
- **Modal:** Blocks content, must be dismissed

**States:**
- Collapsed: Peek height visible
- Half-expanded: Intermediate state
- Expanded: Full content visible
- Hidden: Completely off-screen

### Chips

**Types:**
- **Assist:** Help with tasks (suggestions)
- **Filter:** Refine content (active/inactive states)
- **Input:** Represent complex information (tags)
- **Suggestion:** Dynamic suggestions

### Dialogs

**Types:**
- **Alert:** Important decisions or information
- **Simple:** List of options
- **Confirmation:** Confirm/cancel choices
- **Full-screen:** Complex content (mobile)

**Best Practices:**
- Title: Clear, concise question or statement
- Content: Provide context, not just restate title
- Actions: 1-2 buttons (Dismiss/Confirm)
- Dismissible: User can cancel via back button

### Snackbars

**Purpose:**
- Brief messages about app processes
- Optional single action
- Auto-dismiss after 4-10 seconds

**Placement:**
- Bottom of screen (mobile)
- Above FAB if present
- Lower-left (desktop)

**Guidelines:**
- One line of text (mobile)
- No more than two lines (tablet)
- Action text: All caps, concise
- Don't block important UI

## Testing and Tools

### Layout Inspector

**Features:**
- Live layout hierarchy
- View properties
- Constraint visualization
- Performance profiling

### Material Design Guidelines

**Resources:**
- [Material Design 3](https://m3.material.io/)
- [Material Components](https://material.io/components)
- [Material Theme Builder](https://material-foundation.github.io/material-theme-builder/)

### Testing Tools

- **Accessibility Scanner:** Automated a11y testing
- **Layout Validation:** Component states testing
- **Espresso:** UI testing framework
- **Compose Preview:** Quick component visualization

### Design Tools

- Figma Material 3 Design Kit
- Sketch Material Design resources
- Adobe XD Material plugins
- Android Studio Layout Editor

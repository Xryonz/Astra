# Common Mobile App Design Mistakes and Fixes

Comprehensive guide covering frequent design mistakes in mobile applications and their solutions.

## Touch Target Issues

### ❌ Mistake: Touch Targets Too Small

**Problem:**
```typescript
// BAD: Button too small (32×32pt)
<TouchableOpacity style={{ width: 32, height: 32 }}>
  <Icon name="close" size={16} />
</TouchableOpacity>
```

**Impact:**
- Users miss the target frequently
- Frustration, especially on larger phones
- Accessibility failure

**✅ Fix:**
```typescript
// GOOD: Minimum 44×44pt with hitSlop for smaller visuals
<TouchableOpacity
  style={{ width: 32, height: 32 }}
  hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }} // Extends to 44×44
>
  <Icon name="close" size={16} />
</TouchableOpacity>

// BETTER: Make actual button larger
<TouchableOpacity style={{ width: 44, height: 44, alignItems: 'center', justifyContent: 'center' }}>
  <Icon name="close" size={20} />
</TouchableOpacity>
```

### ❌ Mistake: Touch Targets Too Close

**Problem:**
```typescript
// BAD: No spacing between buttons
<View style={{ flexDirection: 'row' }}>
  <Button title="Cancel" onPress={onCancel} />
  <Button title="Delete" onPress={onDelete} />
</View>
```

**Impact:**
- Accidental taps on wrong button
- Critical for destructive actions

**✅ Fix:**
```typescript
// GOOD: Adequate spacing (12-16pt minimum)
<View style={{ flexDirection: 'row', gap: 16 }}>
  <Button title="Cancel" onPress={onCancel} />
  <Button title="Delete" onPress={onDelete} />
</View>

// BETTER: Separate destructive actions
<View style={{ gap: 12 }}>
  <Button title="Delete" variant="destructive" onPress={onDelete} />
  <Button title="Cancel" variant="secondary" onPress={onCancel} />
</View>
```

## Typography Problems

### ❌ Mistake: Text Too Small

**Problem:**
```typescript
// BAD: 10pt text
<Text style={{ fontSize: 10 }}>Important information here</Text>
```

**Impact:**
- Difficult to read
- WCAG failure
- Accessibility issues

**✅ Fix:**
```typescript
// GOOD: 12pt minimum for captions
<Text style={{ fontSize: 12 }}>Important information here</Text>

// BETTER: 14-16pt for body text
<Text style={{ fontSize: 15 }}>Important information here</Text>
```

### ❌ Mistake: Poor Line Height

**Problem:**
```typescript
// BAD: No line height specified
<Text style={{ fontSize: 16 }}>
  Long paragraph of text that wraps to multiple lines.
  This becomes hard to read without proper spacing.
</Text>
```

**Impact:**
- Lines blur together
- Reduced readability
- Eye strain

**✅ Fix:**
```typescript
// GOOD: 1.5× line height for body text
<Text style={{ fontSize: 16, lineHeight: 24 }}>
  Long paragraph of text that wraps to multiple lines.
  This is much easier to read with proper line spacing.
</Text>
```

### ❌ Mistake: All Caps for Body Text

**Problem:**
```typescript
// BAD: All caps makes reading difficult
<Text style={{ fontSize: 14, textTransform: 'uppercase' }}>
  This entire paragraph is in all caps which makes it significantly harder
  to read because we recognize words by their shape and all caps removes
  that visual cue making every word look like a rectangle.
</Text>
```

**Impact:**
- Slower reading speed
- Appears like shouting
- Accessibility issues

**✅ Fix:**
```typescript
// GOOD: Use title case for headings only
<Text style={{ fontSize: 14 }}>
  This paragraph uses normal sentence case which is much easier to read
  and doesn't feel like the app is shouting at you.
</Text>

// OK: All caps for short labels/buttons only
<Text style={{ fontSize: 12, textTransform: 'uppercase', letterSpacing: 1 }}>
  Submit
</Text>
```

## Color and Contrast

### ❌ Mistake: Insufficient Contrast

**Problem:**
```typescript
// BAD: Light gray on white (2.1:1 contrast)
<Text style={{ color: '#CCCCCC', backgroundColor: '#FFFFFF' }}>
  Important message
</Text>
```

**Impact:**
- Unreadable in bright light
- WCAG AA failure (need 4.5:1)
- Accessibility violation

**✅ Fix:**
```typescript
// GOOD: Dark gray on white (7:1 contrast)
<Text style={{ color: '#595959', backgroundColor: '#FFFFFF' }}>
  Important message
</Text>

// BETTER: Use semantic color system
const colors = {
  text: {
    primary: '#14171A',   // 15.8:1
    secondary: '#657786', // 4.6:1
  },
  background: '#FFFFFF',
};

<Text style={{ color: colors.text.secondary }}>
  Important message
</Text>
```

### ❌ Mistake: Color as Only Indicator

**Problem:**
```typescript
// BAD: Color alone indicates status
<View>
  <Text style={{ color: 'red' }}>Error occurred</Text>
  <Text style={{ color: 'green' }}>Success!</Text>
</View>
```

**Impact:**
- Color blind users miss information
- Black & white displays
- Accessibility failure

**✅ Fix:**
```typescript
// GOOD: Color + icon + text
<View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
  <Icon name="error" color="red" />
  <Text style={{ color: 'red' }}>Error occurred</Text>
</View>

<View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
  <Icon name="check-circle" color="green" />
  <Text style={{ color: 'green' }}>Success!</Text>
</View>
```

## Accessibility Mistakes

### ❌ Mistake: Missing Accessibility Labels

**Problem:**
```typescript
// BAD: No label on icon button
<TouchableOpacity onPress={onShare}>
  <Icon name="share" size={24} />
</TouchableOpacity>
```

**Impact:**
- Screen reader announces "button" with no context
- Users don't know what button does
- WCAG failure

**✅ Fix:**
```typescript
// GOOD: Descriptive label
<TouchableOpacity
  onPress={onShare}
  accessibilityRole="button"
  accessibilityLabel="Share this post"
  accessibilityHint="Opens the share sheet to send to friends"
>
  <Icon name="share" size={24} />
</TouchableOpacity>
```

### ❌ Mistake: Including Element Type in Label

**Problem:**
```typescript
// BAD: Redundant "button" in label
<TouchableOpacity
  accessibilityLabel="Share button"
  accessibilityRole="button"
>
  <Text>Share</Text>
</TouchableOpacity>
```

**Impact:**
- Screen reader says "Share button button"
- Redundant, annoying
- Poor UX

**✅ Fix:**
```typescript
// GOOD: Label without type (role provides that)
<TouchableOpacity
  accessibilityLabel="Share"
  accessibilityRole="button"
>
  <Text>Share</Text>
</TouchableOpacity>
```

### ❌ Mistake: Not Announcing Dynamic Changes

**Problem:**
```typescript
// BAD: No announcement when item added
function addToCart(item) {
  setCartItems([...cartItems, item]);
  // User doesn't know anything happened
}
```

**Impact:**
- Screen reader users miss feedback
- Uncertainty about action result
- Poor accessibility

**✅ Fix:**
```typescript
// GOOD: Announce the change
import { AccessibilityInfo } from 'react-native';

function addToCart(item) {
  setCartItems([...cartItems, item]);

  // Announce to screen reader users
  AccessibilityInfo.announceForAccessibility(
    `${item.name} added to cart`
  );
}
```

## List Performance

### ❌ Mistake: Using ScrollView for Long Lists

**Problem:**
```typescript
// BAD: Renders all 1000 items at once
<ScrollView>
  {items.map(item => (
    <ItemCard key={item.id} item={item} />
  ))}
</ScrollView>
```

**Impact:**
- Slow initial render
- High memory usage
- Poor performance

**✅ Fix:**
```typescript
// GOOD: Virtualized rendering
<FlatList
  data={items}
  renderItem={({ item }) => <ItemCard item={item} />}
  keyExtractor={item => item.id}
  // Performance optimizations
  removeClippedSubviews={true}
  maxToRenderPerBatch={10}
  windowSize={5}
/>
```

### ❌ Mistake: Creating Functions in renderItem

**Problem:**
```typescript
// BAD: New function on every render
<FlatList
  data={items}
  renderItem={({ item }) => (
    <TouchableOpacity onPress={() => handlePress(item)}>
      <Text>{item.name}</Text>
    </TouchableOpacity>
  )}
/>
```

**Impact:**
- Creates new function for each item
- Prevents optimization
- Poor performance

**✅ Fix:**
```typescript
// GOOD: Stable component with useCallback
const ListItem = React.memo(({ item, onPress }) => {
  const handlePress = useCallback(() => {
    onPress(item.id);
  }, [item.id, onPress]);

  return (
    <TouchableOpacity onPress={handlePress}>
      <Text>{item.name}</Text>
    </TouchableOpacity>
  );
});

<FlatList
  data={items}
  renderItem={({ item }) => <ListItem item={item} onPress={handleItemPress} />}
/>
```

## Navigation Issues

### ❌ Mistake: Inconsistent Navigation Patterns

**Problem:**
```typescript
// BAD: Mixed navigation (drawer on some screens, tabs on others)
// Screen A has drawer
<DrawerNavigator>
  <Screen name="Home" />
</DrawerNavigator>

// Screen B has tabs
<TabNavigator>
  <Screen name="Profile" />
</TabNavigator>
```

**Impact:**
- Confusing for users
- Inconsistent experience
- Platform violations

**✅ Fix:**
```typescript
// GOOD: Consistent pattern throughout
<TabNavigator>
  <Screen name="Home" component={HomeScreen} />
  <Screen name="Profile" component={ProfileScreen} />
  <Screen name="Settings" component={SettingsScreen} />
</TabNavigator>
```

### ❌ Mistake: Wrong Back Button Behavior

**Problem:**
```typescript
// BAD: Back button exits app instead of going to previous screen
<Button
  title="Back"
  onPress={() => BackHandler.exitApp()}
/>
```

**Impact:**
- Data loss
- User frustration
- Platform violation

**✅ Fix:**
```typescript
// GOOD: Use navigation to go back
<Button
  title="Back"
  onPress={() => navigation.goBack()}
/>

// Or use hardware back button handling
useEffect(() => {
  const backHandler = BackHandler.addEventListener(
    'hardwareBackPress',
    () => {
      navigation.goBack();
      return true; // Prevent default behavior
    }
  );

  return () => backHandler.remove();
}, [navigation]);
```

## Form Design

### ❌ Mistake: Wrong Keyboard Type

**Problem:**
```typescript
// BAD: Default keyboard for email
<TextInput
  placeholder="Email"
  value={email}
  onChangeText={setEmail}
/>
```

**Impact:**
- Extra taps to access @ symbol
- Poor UX
- Slower input

**✅ Fix:**
```typescript
// GOOD: Email keyboard
<TextInput
  placeholder="Email"
  value={email}
  onChangeText={setEmail}
  keyboardType="email-address"
  autoCapitalize="none"
  autoComplete="email"
/>

// Number input
<TextInput
  placeholder="Phone"
  keyboardType="phone-pad"
  autoComplete="tel"
/>

// URL input
<TextInput
  placeholder="Website"
  keyboardType="url"
  autoCapitalize="none"
/>
```

### ❌ Mistake: No Label on Input

**Problem:**
```typescript
// BAD: Only placeholder, disappears when typing
<TextInput
  placeholder="Enter your name"
  value={name}
  onChangeText={setName}
/>
```

**Impact:**
- Users forget what field is for
- Accessibility failure
- Poor UX

**✅ Fix:**
```typescript
// GOOD: Persistent label + placeholder
<View>
  <Text style={styles.label}>Name</Text>
  <TextInput
    placeholder="John Doe"
    value={name}
    onChangeText={setName}
    accessibilityLabel="Name"
  />
</View>

// BETTER: Floating label (advanced)
<FloatingLabelInput
  label="Name"
  placeholder="John Doe"
  value={name}
  onChangeText={setName}
/>
```

### ❌ Mistake: Poor Error Messaging

**Problem:**
```typescript
// BAD: Generic error
{error && <Text style={{ color: 'red' }}>Error</Text>}
```

**Impact:**
- User doesn't know what's wrong
- Can't fix the problem
- Frustration

**✅ Fix:**
```typescript
// GOOD: Specific, actionable error
{error && (
  <View style={styles.errorContainer} accessibilityRole="alert">
    <Icon name="error" color="#D32F2F" />
    <Text style={styles.errorText}>
      {error.message || 'Please enter a valid email address'}
    </Text>
  </View>
)}

// BETTER: Inline validation
<TextInput
  value={email}
  onChangeText={setEmail}
  onBlur={validateEmail}
  style={[styles.input, emailError && styles.inputError]}
/>
{emailError && (
  <Text style={styles.errorHint}>
    Example: user@example.com
  </Text>
)}
```

## Image Handling

### ❌ Mistake: Not Optimizing Images

**Problem:**
```typescript
// BAD: Loading full-res 4MB image
<Image
  source={{ uri: 'https://example.com/photo-4000x3000.jpg' }}
  style={{ width: 100, height: 100 }}
/>
```

**Impact:**
- Slow loading
- Wasted bandwidth
- Poor performance

**✅ Fix:**
```typescript
// GOOD: Request appropriately sized image
import FastImage from 'react-native-fast-image';

const screenScale = PixelRatio.get();
const imageWidth = 100 * screenScale;

<FastImage
  source={{
    uri: `https://example.com/photo.jpg?w=${imageWidth}&q=80`,
    priority: FastImage.priority.normal,
  }}
  style={{ width: 100, height: 100 }}
  resizeMode={FastImage.resizeMode.cover}
/>
```

### ❌ Mistake: No Loading State

**Problem:**
```typescript
// BAD: No placeholder while loading
<Image source={{ uri: imageUrl }} style={styles.image} />
```

**Impact:**
- Blank space while loading
- Layout shift when loaded
- Poor perceived performance

**✅ Fix:**
```typescript
// GOOD: Skeleton loader or placeholder
import FastImage from 'react-native-fast-image';

<FastImage
  source={{ uri: imageUrl }}
  style={styles.image}
  // Built-in placeholder
  defaultSource={require('./assets/placeholder.png')}
/>

// BETTER: Skeleton with shimmer effect
const [loaded, setLoaded] = useState(false);

<View>
  {!loaded && <SkeletonPlaceholder />}
  <FastImage
    source={{ uri: imageUrl }}
    style={[styles.image, !loaded && { position: 'absolute' }]}
    onLoadEnd={() => setLoaded(true)}
  />
</View>
```

## Animation Mistakes

### ❌ Mistake: Not Using Native Driver

**Problem:**
```typescript
// BAD: Animated on JS thread (laggy if JS busy)
Animated.timing(fadeAnim, {
  toValue: 1,
  duration: 300,
  useNativeDriver: false,
}).start();
```

**Impact:**
- Janky animations
- Drops frames when JS busy
- Poor UX

**✅ Fix:**
```typescript
// GOOD: Runs on native thread (smooth)
Animated.timing(fadeAnim, {
  toValue: 1,
  duration: 300,
  useNativeDriver: true, // Works for opacity, transform
}).start();

// BETTER: Use Reanimated for complex animations
import Animated, { withTiming } from 'react-native-reanimated';

opacity.value = withTiming(1, { duration: 300 });
```

### ❌ Mistake: Too Many Simultaneous Animations

**Problem:**
```typescript
// BAD: Animating 50 items at once
items.forEach((item, index) => {
  Animated.timing(item.animation, {
    toValue: 1,
    duration: 300,
    useNativeDriver: true,
  }).start();
});
```

**Impact:**
- Frame drops
- Laggy performance
- Poor UX

**✅ Fix:**
```typescript
// GOOD: Stagger animations
items.forEach((item, index) => {
  Animated.timing(item.animation, {
    toValue: 1,
    duration: 300,
    delay: index * 50, // Stagger by 50ms
    useNativeDriver: true,
  }).start();
});

// BETTER: Use layout animations
import { LayoutAnimation, Platform, UIManager } from 'react-native';

if (Platform.OS === 'android') {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
setItems(newItems); // Layout animates automatically
```

## State Management

### ❌ Mistake: Prop Drilling

**Problem:**
```typescript
// BAD: Passing props through 5 levels
<ParentComponent user={user}>
  <MiddleComponent user={user}>
    <AnotherComponent user={user}>
      <YetAnotherComponent user={user}>
        <FinalComponent user={user} />
      </YetAnotherComponent>
    </AnotherComponent>
  </MiddleComponent>
</ParentComponent>
```

**Impact:**
- Hard to maintain
- Components coupled
- Refactoring nightmare

**✅ Fix:**
```typescript
// GOOD: Context for shared state
const UserContext = createContext();

<UserContext.Provider value={user}>
  <ParentComponent>
    <MiddleComponent>
      <AnotherComponent>
        <YetAnotherComponent>
          <FinalComponent />
        </YetAnotherComponent>
      </AnotherComponent>
    </MiddleComponent>
  </ParentComponent>
</UserContext.Provider>

// In FinalComponent
const user = useContext(UserContext);
```

### ❌ Mistake: Storing Derived State

**Problem:**
```typescript
// BAD: Storing computed values in state
const [items, setItems] = useState([]);
const [filteredItems, setFilteredItems] = useState([]);

useEffect(() => {
  setFilteredItems(items.filter(item => item.active));
}, [items]);
```

**Impact:**
- State sync issues
- Unnecessary re-renders
- More code to maintain

**✅ Fix:**
```typescript
// GOOD: Compute on render
const [items, setItems] = useState([]);
const filteredItems = useMemo(
  () => items.filter(item => item.active),
  [items]
);
```

## Testing Checklist

Use this checklist before release:

### Visual
- [ ] All text 11pt or larger
- [ ] Color contrast meets WCAG AA (4.5:1 for text)
- [ ] Touch targets 44pt/48dp minimum
- [ ] No color-only information
- [ ] Works in light and dark mode

### Accessibility
- [ ] All interactive elements have labels
- [ ] Screen reader can access all features
- [ ] Keyboard navigation works (if supported)
- [ ] Dynamic changes announced
- [ ] Works at maximum text size (200% or AX5)

### Performance
- [ ] Lists use FlatList/VirtualizedList
- [ ] Images optimized and lazy loaded
- [ ] Animations use native driver
- [ ] No memory leaks (cleanup in useEffect)
- [ ] Bundle size analyzed

### Platform
- [ ] Follows iOS HIG (iOS)
- [ ] Follows Material Design (Android)
- [ ] Navigation consistent
- [ ] Back button works correctly
- [ ] Safe area insets respected

### Forms
- [ ] Correct keyboard types
- [ ] Labels persist (not just placeholders)
- [ ] Clear error messages
- [ ] Auto-complete attributes set
- [ ] Validation helpful, not blocking

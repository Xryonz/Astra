# React Native UI Component Libraries

Comprehensive guide to popular React Native UI libraries with use cases and recommendations for 2026.

## Material Design Libraries

### React Native Paper

**Best For:** Android-first apps, Material Design 3 compliance

**Pros:**
- Official Material Design 3 implementation
- Excellent theming support
- Built-in dark mode
- Comprehensive component set
- Active maintenance
- Good accessibility

**Cons:**
- Can look out of place on iOS
- Some customization limitations
- Bundle size impact

**Installation:**
```bash
npm install react-native-paper react-native-vector-icons
```

**Usage:**
```typescript
import { Button, Card, Text } from 'react-native-paper';

<Card>
  <Card.Title title="Card Title" subtitle="Card Subtitle" />
  <Card.Content>
    <Text variant="bodyMedium">Card content</Text>
  </Card.Content>
  <Card.Actions>
    <Button>Cancel</Button>
    <Button mode="contained">OK</Button>
  </Card.Actions>
</Card>
```

**When to Use:**
- Android-focused app
- Material Design brand identity
- Need comprehensive theming
- Rapid prototyping

**Website:** https://reactnativepaper.com/

---

### React Native Elements

**Best For:** Cross-platform apps, customizable components

**Pros:**
- Platform-agnostic
- Highly customizable
- Good documentation
- Large community
- TypeScript support

**Cons:**
- Less opinionated than Paper
- Requires more styling
- Some components feel dated

**Installation:**
```bash
npm install @rneui/themed @rneui/base
```

**Usage:**
```typescript
import { Button, Card, Icon } from '@rneui/themed';

<Card>
  <Card.Title>CARD TITLE</Card.Title>
  <Card.Divider />
  <Card.Image source={{ uri: 'image.jpg' }} />
  <Text style={{ marginBottom: 10 }}>
    The idea with React Native Elements is more about component structure
  </Text>
  <Button
    icon={<Icon name="code" color="#ffffff" />}
    buttonStyle={{ borderRadius: 0 }}
    title="VIEW NOW"
  />
</Card>
```

**When to Use:**
- Need flexibility over opinions
- Custom design system
- Cross-platform parity
- Component starter kit

**Website:** https://reactnativeelements.com/

---

## iOS-Focused Libraries

### React Native iOS Kit

**Best For:** iOS-first apps, native feel

**Pros:**
- Native iOS look and feel
- SF Symbols support
- iOS-specific components
- Follows HIG closely

**Cons:**
- iOS only (doesn't look good on Android)
- Smaller community
- Less frequent updates

**When to Use:**
- iOS-exclusive app
- Need native iOS components
- Following Apple HIG strictly

---

## Cross-Platform UI Kits

### NativeBase

**Best For:** Rapid development, consistent cross-platform UI

**Pros:**
- Large component library
- Built-in responsive utilities
- Theming system
- Accessibility features
- Works with Expo

**Cons:**
- Heavy bundle size
- Can be opinionated
- Learning curve for theming

**Installation:**
```bash
npm install native-base react-native-svg react-native-safe-area-context
```

**Usage:**
```typescript
import { Box, Heading, VStack, FormControl, Input, Button } from "native-base";

<Box safeArea p="2" py="8" w="90%" maxW="290">
  <Heading size="lg" fontWeight="600">
    Welcome
  </Heading>
  <VStack space={3} mt="5">
    <FormControl>
      <FormControl.Label>Email</FormControl.Label>
      <Input />
    </FormControl>
    <FormControl>
      <FormControl.Label>Password</FormControl.Label>
      <Input type="password" />
    </FormControl>
    <Button mt="2" colorScheme="indigo">
      Sign in
    </Button>
  </VStack>
</Box>
```

**When to Use:**
- Need many pre-built components
- Rapid prototyping
- Responsive design needed
- Expo compatibility required

**Website:** https://nativebase.io/

---

### Tamagui

**Best For:** Performance-critical apps, universal (web + native)

**Pros:**
- Extremely fast (compile-time optimization)
- Universal (React Native + Web)
- Modern styling system
- Excellent animations
- Tree-shakeable

**Cons:**
- Newer library (less mature)
- Steeper learning curve
- Different paradigm

**Installation:**
```bash
npm install tamagui @tamagui/config
```

**Usage:**
```typescript
import { Button, Card, H2, Paragraph } from 'tamagui';

<Card elevate size="$4" bordered>
  <Card.Header padded>
    <H2>Card Title</H2>
  </Card.Header>
  <Card.Footer padded>
    <Paragraph>Card content goes here</Paragraph>
    <Button>Action</Button>
  </Card.Footer>
</Card>
```

**When to Use:**
- Performance is critical
- Building for web + native
- Modern animation needs
- Willing to learn new paradigm

**Website:** https://tamagui.dev/

---

## Specialized Libraries

### React Native UI Lib (by Wix)

**Best For:** Enterprise apps, extensive component needs

**Pros:**
- Battle-tested (Wix production)
- Comprehensive components
- Modifiers API
- Great performance
- Active maintenance

**Cons:**
- Learning curve
- Opinionated API
- Documentation could be better

**Installation:**
```bash
npm install react-native-ui-lib
```

**Usage:**
```typescript
import { View, Card, Button, Text } from 'react-native-ui-lib';

<Card flex>
  <Card.Section
    content={[
      { text: 'You're Invited!', text70: true, dark10: true },
      { text: 'Join us for a day of fun', text90: true, dark50: true },
    ]}
  />
  <Card.Section
    content={[
      { text: 'RSVP', text80: true, primary: true },
    ]}
  />
</Card>
```

**When to Use:**
- Enterprise-scale app
- Need proven components
- Modifiers approach appeals
- Performance critical

**Website:** https://wix.github.io/react-native-ui-lib/

---

## Utility Libraries

### React Native Reusables

**Best For:** Headless components, maximum flexibility

**Pros:**
- Unstyled (full control)
- Accessible by default
- TypeScript-first
- Small bundle size
- Composable

**Cons:**
- Must style everything
- More work upfront
- Smaller community

**When to Use:**
- Custom design system
- Need full control
- Accessibility priority
- Don't want library styles

**Website:** https://rnr-docs.vercel.app/

---

### Gluestack UI

**Best For:** Unstyled components with utility props

**Pros:**
- Unstyled + themed variants
- Excellent accessibility
- Universal (web + native)
- TypeScript support
- Modern DX

**Cons:**
- Relatively new
- Smaller ecosystem
- Less examples

**Installation:**
```bash
npm install @gluestack-ui/themed @gluestack-style/react
```

**When to Use:**
- Want unstyled components
- Need theming flexibility
- Accessibility critical
- Universal app (web + native)

**Website:** https://gluestack.io/

---

## Animation Libraries

### React Native Reanimated

**Best For:** Complex animations, 60fps performance

**Pros:**
- Runs on native thread
- Gesture integration
- Excellent performance
- Active development
- Industry standard

**Cons:**
- Different API than Animated
- Learning curve
- Debugging can be harder

**Installation:**
```bash
npm install react-native-reanimated react-native-gesture-handler
```

**Usage:**
```typescript
import Animated, { useSharedValue, withSpring } from 'react-native-reanimated';

const width = useSharedValue(100);

<Animated.View style={{ width }} />

// Animate
width.value = withSpring(200);
```

**When to Use:**
- Need smooth animations
- Gesture-driven UI
- Performance critical
- Industry best practice

**Website:** https://docs.swmansion.com/react-native-reanimated/

---

### Moti

**Best For:** Declarative animations, quick animations

**Pros:**
- Built on Reanimated
- Framer Motion-like API
- Very easy to use
- Great for simple animations

**Cons:**
- Wrapper around Reanimated
- Less control for complex cases

**Installation:**
```bash
npm install moti
```

**Usage:**
```typescript
import { MotiView } from 'moti';

<MotiView
  from={{ opacity: 0, scale: 0.5 }}
  animate={{ opacity: 1, scale: 1 }}
  transition={{ type: 'timing', duration: 500 }}
/>
```

**When to Use:**
- Simple animations
- Framer Motion experience
- Rapid development
- Declarative preferences

**Website:** https://moti.fyi/

---

## Form Libraries

### React Hook Form

**Best For:** Complex forms, validation

**Pros:**
- Excellent performance (uncontrolled)
- TypeScript support
- Validation integration (Zod, Yup)
- Small bundle size
- Web + Native

**Cons:**
- Uncontrolled paradigm
- Learning curve

**Installation:**
```bash
npm install react-hook-form
```

**Usage:**
```typescript
import { useForm, Controller } from 'react-hook-form';

const { control, handleSubmit } = useForm();

<Controller
  control={control}
  name="email"
  rules={{ required: true }}
  render={({ field: { onChange, value } }) => (
    <TextInput
      value={value}
      onChangeText={onChange}
      placeholder="Email"
    />
  )}
/>
```

**When to Use:**
- Complex forms
- Need validation
- Performance critical
- TypeScript project

**Website:** https://react-hook-form.com/

---

### Formik

**Best For:** Traditional form handling

**Pros:**
- Easy to learn
- Controlled components
- Good documentation
- Large community

**Cons:**
- More re-renders than RHF
- Larger bundle
- Less modern

**When to Use:**
- Simple forms
- Prefer controlled components
- Existing Formik knowledge

**Website:** https://formik.org/

---

## Icon Libraries

### React Native Vector Icons

**Best For:** Standard icon needs

**Pros:**
- 3000+ icons
- Multiple icon families
- Easy to use
- Customizable

**Installation:**
```bash
npm install react-native-vector-icons
```

**Usage:**
```typescript
import Icon from 'react-native-vector-icons/MaterialIcons';

<Icon name="home" size={30} color="#000" />
```

**Website:** https://github.com/oblador/react-native-vector-icons

---

### Lucide React Native

**Best For:** Modern, consistent icons

**Pros:**
- Beautiful, consistent design
- Tree-shakeable
- TypeScript support
- Regular updates

**Installation:**
```bash
npm install lucide-react-native
```

**Usage:**
```typescript
import { Home, User, Settings } from 'lucide-react-native';

<Home color="black" size={24} />
```

**Website:** https://lucide.dev/

---

## Library Comparison Matrix

| Library | Bundle Size | Learning Curve | Customization | Platform | Maintenance |
|---------|-------------|----------------|---------------|----------|-------------|
| **React Native Paper** | Medium | Low | Medium | Android-first | Active |
| **React Native Elements** | Small | Low | High | Cross-platform | Active |
| **NativeBase** | Large | Medium | Medium | Cross-platform | Active |
| **Tamagui** | Small* | High | High | Universal | Active |
| **UI Lib** | Medium | Medium | Medium | Cross-platform | Active |
| **Reusables** | XS | Low | Maximum | Cross-platform | Active |
| **Gluestack** | Small | Medium | High | Universal | Active |

*With optimizations enabled

## Selection Guide

### Choose Based on Project Type

**MVP/Prototype:**
- NativeBase (fastest development)
- React Native Paper (Material Design)
- React Native Elements (flexibility)

**Production App:**
- Tamagui (performance + universal)
- UI Lib (battle-tested)
- Custom with Reusables (full control)

**Android-Primary:**
- React Native Paper (Material Design 3)
- NativeBase (Material support)

**iOS-Primary:**
- Custom components (HIG compliance)
- React Native Elements (customizable)

**Cross-Platform Parity:**
- React Native Elements
- NativeBase
- Custom with shared components

**Web + Native:**
- Tamagui (best universal support)
- Gluestack UI
- NativeBase

### Choose Based on Team

**Small Team:**
- NativeBase (pre-built)
- React Native Paper (comprehensive)

**Large Team:**
- UI Lib (enterprise-proven)
- Custom design system

**Design-Driven:**
- Reusables (unstyled)
- Tamagui (flexible)
- Custom components

## Combining Libraries

You can use multiple libraries together:

```typescript
// Common combinations:

// 1. UI Kit + Animation
import { Button } from 'react-native-paper';
import Animated from 'react-native-reanimated';

// 2. Headless + Icons
import { Dialog } from '@gluestack-ui/themed';
import { X } from 'lucide-react-native';

// 3. Forms + UI
import { Controller } from 'react-hook-form';
import { TextInput } from 'react-native-paper';
```

**Best Practices:**
- Use one primary UI library
- Add animation library (Reanimated)
- Add form library if needed (RHF)
- Add icon library
- Avoid mixing competing UI libraries

## Installation Best Practices

### Peer Dependencies

Always check peer dependencies:
```bash
npm info react-native-paper peerDependencies
```

### Linking Native Modules

Some libraries require native linking:
```bash
npx pod-install ios  # iOS
```

### Metro Config

Some libraries need Metro configuration:
```javascript
// metro.config.js
module.exports = {
  transformer: {
    // Enable experimental features if needed
  },
};
```

## Performance Tips

### Tree Shaking

Import only what you need:
```typescript
// ❌ BAD: Imports everything
import * as RNE from '@rneui/themed';

// ✅ GOOD: Tree-shakeable
import { Button } from '@rneui/themed';
```

### Code Splitting

Lazy load heavy components:
```typescript
const HeavyChart = lazy(() => import('./HeavyChart'));
```

### Bundle Analysis

Analyze library impact:
```bash
npx react-native-bundle-visualizer
```

## Migration Strategies

### From No Library to Library

1. Start with new screens
2. Create wrapper components
3. Gradually migrate old screens
4. Remove old styles

### Between Libraries

1. Audit component usage
2. Map components (old → new)
3. Create compatibility layer
4. Migrate incrementally
5. Remove old library

## Conclusion

**Recommendations for 2026:**

1. **General Purpose:** React Native Elements or Tamagui
2. **Material Design:** React Native Paper
3. **Maximum Control:** Gluestack UI or Reusables
4. **Performance Critical:** Tamagui
5. **Enterprise:** UI Lib
6. **Rapid Development:** NativeBase

Start simple, grow as needed. Don't over-engineer with libraries you don't need.

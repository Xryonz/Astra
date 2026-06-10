# React Native Performance Optimization Patterns

Comprehensive guide for optimizing React Native application performance covering rendering, memory, bundle size, and platform-specific optimizations.

## Rendering Performance

### Component Optimization

**React.memo for Expensive Components:**
```typescript
// Prevent unnecessary re-renders
const ExpensiveComponent = React.memo(({ data, onPress }) => {
  return (
    <View>
      <ComplexVisualization data={data} />
      <Button onPress={onPress} />
    </View>
  );
}, (prevProps, nextProps) => {
  // Custom comparison function
  return prevProps.data === nextProps.data &&
         prevProps.onPress === nextProps.onPress;
});
```

**useMemo for Expensive Calculations:**
```typescript
function DataScreen({ items }) {
  // Expensive filtering/sorting only runs when items change
  const filteredItems = useMemo(() => {
    return items
      .filter(item => item.active)
      .sort((a, b) => b.date - a.date)
      .slice(0, 20);
  }, [items]);

  return <FlatList data={filteredItems} />;
}
```

**useCallback for Stable Function References:**
```typescript
function ParentComponent() {
  const [count, setCount] = useState(0);
  const [text, setText] = useState('');

  // Stable function reference prevents child re-renders
  const handlePress = useCallback(() => {
    setCount(c => c + 1);
  }, []); // Empty deps - function never changes

  return (
    <>
      <Input value={text} onChange={setText} />
      <ExpensiveChild onPress={handlePress} />
    </>
  );
}
```

### List Rendering

**FlatList Optimization:**
```typescript
<FlatList
  data={items}
  renderItem={renderItem}
  keyExtractor={item => item.id} // Stable keys

  // Performance props
  removeClippedSubviews={true} // Unmount off-screen items
  maxToRenderPerBatch={10} // Batch size for rendering
  updateCellsBatchingPeriod={50} // ms between batch renders
  initialNumToRender={10} // Initial items to render
  windowSize={5} // Viewport multiplier for render window

  // Memory optimization
  getItemLayout={(data, index) => ({
    length: ITEM_HEIGHT,
    offset: ITEM_HEIGHT * index,
    index,
  })} // Skip measurement, improve scrolling

  // Optimize item rendering
  renderItem={renderItem} // Use stable function

  // Extract props that don't affect rendering
  extraData={selectedId} // Only re-render when this changes
/>
```

**Optimized List Item Component:**
```typescript
const ListItem = React.memo(({ item, onPress }) => {
  // Avoid creating functions in render
  const handlePress = useCallback(() => {
    onPress(item.id);
  }, [item.id, onPress]);

  return (
    <TouchableOpacity onPress={handlePress}>
      <View style={styles.container}>
        <Image source={{ uri: item.imageUrl }} style={styles.image} />
        <Text>{item.title}</Text>
      </View>
    </TouchableOpacity>
  );
}, (prev, next) => {
  // Custom comparison for re-render control
  return prev.item.id === next.item.id &&
         prev.item.title === next.item.title;
});
```

**VirtualizedList for Custom Layouts:**
```typescript
// When FlatList/SectionList aren't flexible enough
<VirtualizedList
  data={items}
  getItem={(data, index) => data[index]}
  getItemCount={data => data.length}
  keyExtractor={item => item.id}
  renderItem={({ item }) => <CustomItem item={item} />}
  initialNumToRender={10}
  maxToRenderPerBatch={10}
/>
```

### Image Optimization

**Fast Image Library:**
```typescript
import FastImage from 'react-native-fast-image';

<FastImage
  source={{
    uri: imageUrl,
    priority: FastImage.priority.high,
    cache: FastImage.cacheControl.immutable,
  }}
  resizeMode={FastImage.resizeMode.cover}
  style={styles.image}
/>
```

**Image Prefetching:**
```typescript
// Prefetch images before they're needed
useEffect(() => {
  const urls = items.map(item => item.imageUrl);
  FastImage.preload(urls.map(url => ({ uri: url })));
}, [items]);
```

**Responsive Image Loading:**
```typescript
function OptimizedImage({ url, width, height }) {
  // Load appropriate size based on screen
  const screenScale = PixelRatio.get();
  const targetWidth = width * screenScale;

  // Use image CDN to resize
  const optimizedUrl = `${url}?w=${targetWidth}&q=80`;

  return (
    <FastImage
      source={{ uri: optimizedUrl }}
      style={{ width, height }}
    />
  );
}
```

## State Management Performance

### Avoid Prop Drilling

**Context API (for infrequent updates):**
```typescript
// Create context with default value
const ThemeContext = createContext({ theme: 'light' });

// Provider at top level
function App() {
  const [theme, setTheme] = useState('light');
  return (
    <ThemeContext.Provider value={{ theme, setTheme }}>
      <AppContent />
    </ThemeContext.Provider>
  );
}

// Consume anywhere in tree
function DeepComponent() {
  const { theme } = useContext(ThemeContext);
  return <View style={styles[theme]} />;
}
```

**Split Contexts to Prevent Unnecessary Re-renders:**
```typescript
// BAD: Single context causes all consumers to re-render
const AppContext = createContext({ user, settings, theme });

// GOOD: Split into separate contexts
const UserContext = createContext(null);
const SettingsContext = createContext(null);
const ThemeContext = createContext(null);
```

**Use Redux/Zustand for Frequent Updates:**
```typescript
// Zustand example - simpler than Redux
import create from 'zustand';

const useStore = create((set) => ({
  count: 0,
  increment: () => set((state) => ({ count: state.count + 1 })),
}));

// Component only re-renders when count changes
function Counter() {
  const count = useStore((state) => state.count);
  const increment = useStore((state) => state.increment);

  return <Button onPress={increment} title={`Count: ${count}`} />;
}
```

### Derived State

**Compute on Render, Not on State Change:**
```typescript
// BAD: Storing derived state
const [items, setItems] = useState([]);
const [filteredItems, setFilteredItems] = useState([]);

useEffect(() => {
  setFilteredItems(items.filter(item => item.active));
}, [items]);

// GOOD: Compute derived state on render
const [items, setItems] = useState([]);
const filteredItems = useMemo(
  () => items.filter(item => item.active),
  [items]
);
```

## Animation Performance

### Use Native Driver

**Enable Native Driver for Animations:**
```typescript
// Runs on native thread, 60fps even if JS is blocked
Animated.timing(animatedValue, {
  toValue: 1,
  duration: 300,
  useNativeDriver: true, // IMPORTANT
}).start();
```

**Properties That Support Native Driver:**
- transform (translateX, translateY, scale, rotate)
- opacity
- backgroundColor (requires react-native-reanimated)

**Properties That Don't Support Native Driver:**
- layout properties (width, height, margins, padding)
- color (except backgroundColor with reanimated)
- text

### Reanimated 2 for Complex Animations

**Runs Entirely on Native Thread:**
```typescript
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withTiming,
} from 'react-native-reanimated';

function AnimatedComponent() {
  const offset = useSharedValue(0);

  const animatedStyles = useAnimatedStyle(() => {
    return {
      transform: [{ translateX: offset.value }],
    };
  });

  const handlePress = () => {
    offset.value = withTiming(offset.value + 100);
  };

  return (
    <Animated.View style={animatedStyles}>
      <Button onPress={handlePress} />
    </Animated.View>
  );
}
```

### Gesture Handler for Smooth Interactions

**React Native Gesture Handler:**
```typescript
import { GestureDetector, Gesture } from 'react-native-gesture-handler';
import Animated, {
  useSharedValue,
  useAnimatedStyle,
} from 'react-native-reanimated';

function DraggableBox() {
  const translateX = useSharedValue(0);
  const translateY = useSharedValue(0);

  const pan = Gesture.Pan()
    .onChange((event) => {
      translateX.value += event.changeX;
      translateY.value += event.changeY;
    });

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [
      { translateX: translateX.value },
      { translateY: translateY.value },
    ],
  }));

  return (
    <GestureDetector gesture={pan}>
      <Animated.View style={[styles.box, animatedStyle]} />
    </GestureDetector>
  );
}
```

## Bundle Size Optimization

### Code Splitting

**Lazy Loading Screens:**
```typescript
import React, { lazy, Suspense } from 'react';

// Lazy load heavy screens
const ProfileScreen = lazy(() => import('./screens/ProfileScreen'));
const SettingsScreen = lazy(() => import('./screens/SettingsScreen'));

function App() {
  return (
    <Suspense fallback={<LoadingSpinner />}>
      <Stack.Navigator>
        <Stack.Screen name="Profile" component={ProfileScreen} />
        <Stack.Screen name="Settings" component={SettingsScreen} />
      </Stack.Navigator>
    </Suspense>
  );
}
```

**Dynamic Imports:**
```typescript
// Import heavy libraries only when needed
async function handleExport() {
  const XLSX = await import('xlsx');
  const workbook = XLSX.utils.book_new();
  // ... use XLSX
}
```

### Analyze Bundle Size

**Metro Bundler Visualization:**
```bash
# Generate bundle stats
npx react-native bundle \
  --platform android \
  --dev false \
  --entry-file index.js \
  --bundle-output /tmp/index.android.bundle \
  --sourcemap-output /tmp/index.android.bundle.map

# Analyze with source-map-explorer
npx source-map-explorer /tmp/index.android.bundle /tmp/index.android.bundle.map
```

### Tree Shaking

**Import Only What You Need:**
```typescript
// BAD: Imports entire library
import _ from 'lodash';
const result = _.debounce(fn, 300);

// GOOD: Import specific function
import debounce from 'lodash/debounce';
const result = debounce(fn, 300);

// EVEN BETTER: Use built-in or smaller alternatives
// Install lodash-es for better tree shaking
import { debounce } from 'lodash-es';
```

## Memory Management

### Cleanup in useEffect

**Cancel Subscriptions and Timers:**
```typescript
useEffect(() => {
  const subscription = API.subscribe(data => {
    setData(data);
  });

  const timerId = setTimeout(() => {
    // Do something
  }, 5000);

  // Cleanup function
  return () => {
    subscription.unsubscribe();
    clearTimeout(timerId);
  };
}, []);
```

### Event Listeners

**Remove Event Listeners:**
```typescript
useEffect(() => {
  const handleKeyboard = (event) => {
    setKeyboardHeight(event.endCoordinates.height);
  };

  const subscription = Keyboard.addListener(
    'keyboardDidShow',
    handleKeyboard
  );

  return () => subscription.remove();
}, []);
```

### Avoid Memory Leaks in Async Operations

**Cancel Async Operations:**
```typescript
useEffect(() => {
  let cancelled = false;

  async function fetchData() {
    const response = await fetch(url);
    const data = await response.json();

    // Check if component still mounted
    if (!cancelled) {
      setData(data);
    }
  }

  fetchData();

  return () => {
    cancelled = true;
  };
}, [url]);
```

## Platform-Specific Optimization

### Android-Specific

**Hermes JavaScript Engine:**
```javascript
// android/app/build.gradle
project.ext.react = [
    enableHermes: true,  // Faster startup, lower memory
]
```

**Benefits:**
- Faster app startup
- Reduced memory usage
- Smaller APK size
- Better performance on low-end devices

**ProGuard for Release Builds:**
```gradle
// android/app/build.gradle
buildTypes {
    release {
        minifyEnabled true
        shrinkResources true
        proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
    }
}
```

### iOS-Specific

**Flipper Disabled in Release:**
```ruby
# ios/Podfile
use_flipper!() # Only for debug builds

# Or conditional:
use_flipper!() if !ENV['PRODUCTION']
```

**Image Asset Catalogs:**
```typescript
// Use xcassets for better optimization
<Image source={require('./assets/logo.png')} />

// Automatically uses @2x, @3x based on device
```

## Network Performance

### Caching Strategies

**React Query for Data Fetching:**
```typescript
import { useQuery } from '@tanstack/react-query';

function UserProfile({ userId }) {
  const { data, isLoading } = useQuery({
    queryKey: ['user', userId],
    queryFn: () => fetchUser(userId),
    staleTime: 5 * 60 * 1000, // 5 minutes
    cacheTime: 10 * 60 * 1000, // 10 minutes
  });

  if (isLoading) return <Loading />;
  return <Profile user={data} />;
}
```

**Offline-First with AsyncStorage:**
```typescript
async function fetchWithCache(url, cacheKey) {
  try {
    // Try to get cached data first
    const cached = await AsyncStorage.getItem(cacheKey);
    if (cached) {
      // Return cached data immediately
      return JSON.parse(cached);
    }
  } catch (e) {
    console.log('Cache read failed');
  }

  // Fetch fresh data
  const response = await fetch(url);
  const data = await response.json();

  // Update cache in background
  AsyncStorage.setItem(cacheKey, JSON.stringify(data)).catch(() => {});

  return data;
}
```

### Request Batching

**Batch Multiple Requests:**
```typescript
// Instead of multiple requests
const user = await fetch('/api/user/123');
const posts = await fetch('/api/user/123/posts');
const followers = await fetch('/api/user/123/followers');

// Use GraphQL or batch endpoint
const { user, posts, followers } = await fetch('/api/batch', {
  method: 'POST',
  body: JSON.stringify({
    queries: [
      { query: 'user', id: 123 },
      { query: 'posts', userId: 123 },
      { query: 'followers', userId: 123 },
    ],
  }),
});
```

## Profiling and Monitoring

### React DevTools Profiler

**Measure Component Render Times:**
```typescript
import { Profiler } from 'react';

function onRenderCallback(
  id, // the "id" prop of the Profiler tree
  phase, // "mount" or "update"
  actualDuration, // time spent rendering
  baseDuration, // estimated time without memoization
  startTime, // when React began rendering
  commitTime, // when React committed the update
) {
  console.log(`${id} (${phase}) took ${actualDuration}ms`);
}

<Profiler id="UserList" onRender={onRenderCallback}>
  <UserList />
</Profiler>
```

### Performance Monitoring

**Flipper for Development:**
- React DevTools
- Network inspector
- Layout inspector
- Performance monitor

**Production Monitoring:**
```typescript
// Install @react-native-firebase/perf
import perf from '@react-native-firebase/perf';

// Trace custom events
const trace = await perf().startTrace('user_profile_load');
await loadUserProfile();
await trace.stop();

// HTTP metrics tracked automatically
```

### Memory Profiling

**Android:**
```bash
# Heap dump
adb shell am dumpheap <package_name> /data/local/tmp/heap.hprof
adb pull /data/local/tmp/heap.hprof

# Analyze with Android Studio Memory Profiler
```

**iOS:**
```
# Use Xcode Instruments
# Product → Profile
# Choose "Leaks" or "Allocations"
```

## Performance Checklist

### Before Release

**Rendering:**
- [ ] FlatList used for long lists
- [ ] Images optimized and lazy loaded
- [ ] Expensive components memoized
- [ ] Animations use native driver
- [ ] No unnecessary re-renders (check with Profiler)

**Bundle:**
- [ ] Bundle analyzed for large dependencies
- [ ] Code splitting for large screens
- [ ] Unused code removed
- [ ] ProGuard/Hermes enabled (Android)

**Network:**
- [ ] API calls cached appropriately
- [ ] Images cached with FastImage
- [ ] Offline support for critical features
- [ ] Request batching where possible

**Memory:**
- [ ] useEffect cleanup implemented
- [ ] Event listeners removed
- [ ] No memory leaks (profile with Instruments)
- [ ] AsyncStorage not overused

**Platform:**
- [ ] Hermes enabled (Android)
- [ ] Flipper disabled in release
- [ ] ProGuard enabled (Android)
- [ ] Asset optimization (iOS)

## Resources

### Tools
- [React DevTools](https://reactnative.dev/docs/debugging#react-devtools)
- [Flipper](https://fbflipper.com/)
- [Reactotron](https://github.com/infinitered/reactotron)
- [Why Did You Render](https://github.com/welldone-software/why-did-you-render)

### Libraries
- [React Native Reanimated](https://docs.swmansion.com/react-native-reanimated/)
- [React Native Gesture Handler](https://docs.swmansion.com/react-native-gesture-handler/)
- [React Native Fast Image](https://github.com/DylanVann/react-native-fast-image)
- [React Query](https://tanstack.com/query/latest/docs/react/overview)

### Learning
- [React Native Performance](https://reactnative.dev/docs/performance)
- [Hermes Engine](https://hermesengine.dev/)
- [Metro Bundler](https://facebook.github.io/metro/)

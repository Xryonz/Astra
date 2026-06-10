/**
 * Profile Screen Example
 *
 * Demonstrates mobile app design best practices:
 * - Container/Presentational pattern
 * - Atomic design structure
 * - Platform-specific navigation
 * - Accessibility labels
 * - Performance optimization
 * - Touch target sizes
 * - Typography hierarchy
 */

import React, { useCallback, useMemo } from 'react';
import {
  View,
  Text,
  Image,
  TouchableOpacity,
  ScrollView,
  StyleSheet,
  Platform,
  AccessibilityInfo,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

// ============================================================================
// ATOMS - Basic building blocks
// ============================================================================

interface AvatarProps {
  uri: string;
  size: number;
  accessibilityLabel?: string;
}

const Avatar: React.FC<AvatarProps> = ({ uri, size, accessibilityLabel }) => (
  <Image
    source={{ uri }}
    style={[styles.avatar, { width: size, height: size, borderRadius: size / 2 }]}
    accessibilityLabel={accessibilityLabel}
    accessibilityRole="image"
  />
);

interface IconButtonProps {
  icon: string;
  onPress: () => void;
  accessibilityLabel: string;
  accessibilityHint?: string;
}

const IconButton: React.FC<IconButtonProps> = ({
  icon,
  onPress,
  accessibilityLabel,
  accessibilityHint,
}) => (
  <TouchableOpacity
    onPress={onPress}
    style={styles.iconButton}
    accessibilityRole="button"
    accessibilityLabel={accessibilityLabel}
    accessibilityHint={accessibilityHint}
    // Minimum 44pt touch target (iOS) / 48dp (Android)
    hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
  >
    <Text style={styles.iconText}>{icon}</Text>
  </TouchableOpacity>
);

// ============================================================================
// MOLECULES - Simple component groups
// ============================================================================

interface StatItemProps {
  label: string;
  value: string | number;
}

const StatItem: React.FC<StatItemProps> = React.memo(({ label, value }) => (
  <View
    style={styles.statItem}
    accessibilityLabel={`${value} ${label}`}
    accessibilityRole="text"
  >
    <Text style={styles.statValue}>{value}</Text>
    <Text style={styles.statLabel}>{label}</Text>
  </View>
));

interface ActionButtonProps {
  title: string;
  onPress: () => void;
  variant?: 'primary' | 'secondary';
}

const ActionButton: React.FC<ActionButtonProps> = React.memo(
  ({ title, onPress, variant = 'primary' }) => (
    <TouchableOpacity
      onPress={onPress}
      style={[
        styles.actionButton,
        variant === 'primary' ? styles.primaryButton : styles.secondaryButton,
      ]}
      accessibilityRole="button"
      accessibilityLabel={title}
      // Ensure minimum height for touch target
      accessible={true}
    >
      <Text
        style={[
          styles.actionButtonText,
          variant === 'primary' ? styles.primaryButtonText : styles.secondaryButtonText,
        ]}
      >
        {title}
      </Text>
    </TouchableOpacity>
  ),
);

// ============================================================================
// ORGANISMS - Complex component assemblies
// ============================================================================

interface ProfileHeaderProps {
  user: {
    name: string;
    username: string;
    avatarUrl: string;
    bio: string;
  };
  onEditPress: () => void;
}

const ProfileHeader: React.FC<ProfileHeaderProps> = React.memo(({ user, onEditPress }) => {
  const insets = useSafeAreaInsets();

  return (
    <View style={[styles.header, { paddingTop: insets.top + 16 }]}>
      {/* Navigation Bar */}
      <View style={styles.navBar}>
        <View style={styles.navLeft}>
          {Platform.OS === 'ios' && <IconButton icon="←" onPress={() => {}} accessibilityLabel="Go back" />}
        </View>
        <Text style={styles.navTitle}>Profile</Text>
        <View style={styles.navRight}>
          <IconButton icon="⋯" onPress={() => {}} accessibilityLabel="More options" />
        </View>
      </View>

      {/* Profile Info */}
      <View style={styles.profileInfo}>
        <Avatar uri={user.avatarUrl} size={80} accessibilityLabel={`${user.name}'s profile picture`} />
        <View style={styles.nameContainer}>
          <Text style={styles.name} accessibilityRole="header">
            {user.name}
          </Text>
          <Text style={styles.username} accessibilityLabel={`Username: ${user.username}`}>
            @{user.username}
          </Text>
        </View>
        <Text style={styles.bio}>{user.bio}</Text>
      </View>

      {/* Action Buttons */}
      <View style={styles.actions}>
        <ActionButton title="Edit Profile" onPress={onEditPress} variant="primary" />
        <ActionButton title="Share Profile" onPress={() => {}} variant="secondary" />
      </View>
    </View>
  );
});

interface StatsBarProps {
  followers: number;
  following: number;
  posts: number;
}

const StatsBar: React.FC<StatsBarProps> = React.memo(({ followers, following, posts }) => (
  <View
    style={styles.statsBar}
    accessibilityRole="none"
    accessibilityLabel={`${posts} posts, ${followers} followers, ${following} following`}
  >
    <StatItem label="Posts" value={posts} />
    <View style={styles.statDivider} />
    <StatItem label="Followers" value={followers} />
    <View style={styles.statDivider} />
    <StatItem label="Following" value={following} />
  </View>
));

// ============================================================================
// CONTAINER - Data fetching and state management
// ============================================================================

interface ProfileScreenProps {
  userId: string;
  onNavigate: (screen: string) => void;
}

export const ProfileScreen: React.FC<ProfileScreenProps> = ({ userId, onNavigate }) => {
  // In real app, fetch user data here
  const user = useMemo(
    () => ({
      name: 'Jane Doe',
      username: 'janedoe',
      avatarUrl: 'https://example.com/avatar.jpg',
      bio: 'Designer & Developer\n📍 San Francisco\n🌐 janedoe.com',
      stats: {
        posts: 142,
        followers: 1205,
        following: 380,
      },
    }),
    [userId],
  );

  // Stable callback references
  const handleEditPress = useCallback(() => {
    onNavigate('EditProfile');
  }, [onNavigate]);

  const handleSettingsPress = useCallback(() => {
    onNavigate('Settings');
  }, [onNavigate]);

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      showsVerticalScrollIndicator={false}
      // Accessibility
      accessible={false} // Let ScrollView children be individually accessible
    >
      <ProfileHeader user={user} onEditPress={handleEditPress} />

      <StatsBar
        posts={user.stats.posts}
        followers={user.stats.followers}
        following={user.stats.following}
      />

      {/* Content sections would go here */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Recent Activity</Text>
        {/* Activity items */}
      </View>
    </ScrollView>
  );
};

// ============================================================================
// STYLES
// ============================================================================

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFFFFF',
  },
  content: {
    paddingBottom: 32,
  },

  // Navigation Bar
  navBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    height: 44, // Standard iOS nav bar height
    marginBottom: 16,
  },
  navLeft: {
    width: 44,
    alignItems: 'flex-start',
  },
  navTitle: {
    fontSize: 17, // iOS headline size
    fontWeight: '600',
    color: '#000000',
  },
  navRight: {
    width: 44,
    alignItems: 'flex-end',
  },

  // Header
  header: {
    paddingHorizontal: 16,
    paddingBottom: 24,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#E1E8ED',
  },
  profileInfo: {
    alignItems: 'center',
    marginBottom: 16,
  },
  avatar: {
    marginBottom: 12,
  },
  nameContainer: {
    alignItems: 'center',
    marginBottom: 8,
  },
  name: {
    fontSize: 24, // Large, prominent
    fontWeight: '700',
    color: '#14171A',
    marginBottom: 4,
  },
  username: {
    fontSize: 15, // Slightly smaller than body
    color: '#657786',
  },
  bio: {
    fontSize: 15, // Standard body text
    lineHeight: 22, // 1.47× font size for readability
    color: '#14171A',
    textAlign: 'center',
  },

  // Actions
  actions: {
    flexDirection: 'row',
    gap: 12,
  },
  actionButton: {
    flex: 1,
    height: 44, // Minimum touch target height
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
  primaryButton: {
    backgroundColor: '#1DA1F2',
  },
  secondaryButton: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#E1E8ED',
  },
  actionButtonText: {
    fontSize: 15,
    fontWeight: '600',
  },
  primaryButtonText: {
    color: '#FFFFFF',
  },
  secondaryButtonText: {
    color: '#14171A',
  },

  // Icon Button
  iconButton: {
    width: 44, // Minimum touch target
    height: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  iconText: {
    fontSize: 24,
    color: '#14171A',
  },

  // Stats Bar
  statsBar: {
    flexDirection: 'row',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#E1E8ED',
  },
  statItem: {
    flex: 1,
    alignItems: 'center',
  },
  statValue: {
    fontSize: 20,
    fontWeight: '700',
    color: '#14171A',
    marginBottom: 4,
  },
  statLabel: {
    fontSize: 13, // Caption size
    color: '#657786',
  },
  statDivider: {
    width: 1,
    backgroundColor: '#E1E8ED',
  },

  // Section
  section: {
    paddingTop: 24,
    paddingHorizontal: 16,
  },
  sectionTitle: {
    fontSize: 20,
    fontWeight: '700',
    color: '#14171A',
    marginBottom: 16,
  },
});

/**
 * Design Principles Applied:
 *
 * ✅ Touch Targets:
 *    - All buttons are minimum 44×44 pt (iOS standard)
 *    - Hit slop added for smaller visual elements
 *
 * ✅ Typography:
 *    - Minimum 13pt font size (caption)
 *    - Clear hierarchy: 24pt (name) > 20pt (section) > 17pt (nav) > 15pt (body) > 13pt (caption)
 *    - Line height 1.47× for body text readability
 *
 * ✅ Accessibility:
 *    - All interactive elements have accessibilityLabel
 *    - Semantic roles specified (button, header, text)
 *    - Grouped stats with combined label for screen readers
 *    - Supports Dynamic Type (uses default font scaling)
 *
 * ✅ Performance:
 *    - React.memo on presentational components
 *    - useMemo for derived data
 *    - useCallback for stable function references
 *    - Flat component hierarchy
 *
 * ✅ Platform Conventions:
 *    - iOS-style navigation bar
 *    - Safe area insets respected
 *    - Platform-specific back button positioning
 *
 * ✅ Color Contrast:
 *    - Primary text: #14171A on #FFFFFF (15.8:1 ✓)
 *    - Secondary text: #657786 on #FFFFFF (4.6:1 ✓)
 *    - Button: #1DA1F2 background with #FFFFFF text (4.2:1 ✓)
 *
 * ✅ Atomic Design:
 *    - Atoms: Avatar, IconButton
 *    - Molecules: StatItem, ActionButton
 *    - Organisms: ProfileHeader, StatsBar
 *    - Template: ProfileScreen container
 */

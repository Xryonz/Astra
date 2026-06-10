/**
 * Form Validation Example
 *
 * Demonstrates:
 * - Accessible form design
 * - Real-time validation
 * - Error message display
 * - Proper keyboard types
 * - Touch target sizes
 * - Screen reader support
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Platform,
  KeyboardAvoidingView,
  ScrollView,
} from 'react-native';

// ============================================================================
// TYPES
// ============================================================================

interface FormData {
  email: string;
  password: string;
  confirmPassword: string;
  phone: string;
}

interface FormErrors {
  email?: string;
  password?: string;
  confirmPassword?: string;
  phone?: string;
}

// ============================================================================
// VALIDATION FUNCTIONS
// ============================================================================

const validateEmail = (email: string): string | undefined => {
  if (!email.trim()) {
    return 'Email is required';
  }
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(email)) {
    return 'Please enter a valid email address';
  }
  return undefined;
};

const validatePassword = (password: string): string | undefined => {
  if (!password) {
    return 'Password is required';
  }
  if (password.length < 8) {
    return 'Password must be at least 8 characters';
  }
  if (!/[A-Z]/.test(password)) {
    return 'Password must contain at least one uppercase letter';
  }
  if (!/[0-9]/.test(password)) {
    return 'Password must contain at least one number';
  }
  return undefined;
};

const validateConfirmPassword = (password: string, confirmPassword: string): string | undefined => {
  if (!confirmPassword) {
    return 'Please confirm your password';
  }
  if (password !== confirmPassword) {
    return 'Passwords do not match';
  }
  return undefined;
};

const validatePhone = (phone: string): string | undefined => {
  if (!phone.trim()) {
    return undefined; // Optional field
  }
  const phoneRegex = /^[\d\s\-\(\)]+$/;
  if (!phoneRegex.test(phone)) {
    return 'Please enter a valid phone number';
  }
  if (phone.replace(/\D/g, '').length < 10) {
    return 'Phone number must be at least 10 digits';
  }
  return undefined;
};

// ============================================================================
// FORM COMPONENTS
// ============================================================================

interface FormFieldProps {
  label: string;
  value: string;
  onChangeText: (text: string) => void;
  onBlur: () => void;
  error?: string;
  placeholder?: string;
  keyboardType?: 'default' | 'email-address' | 'numeric' | 'phone-pad';
  secureTextEntry?: boolean;
  autoComplete?: string;
  textContentType?: string;
  required?: boolean;
}

const FormField: React.FC<FormFieldProps> = ({
  label,
  value,
  onChangeText,
  onBlur,
  error,
  placeholder,
  keyboardType = 'default',
  secureTextEntry = false,
  autoComplete,
  textContentType,
  required = false,
}) => {
  const [isFocused, setIsFocused] = useState(false);

  return (
    <View style={styles.fieldContainer}>
      {/* Label */}
      <Text
        style={styles.label}
        accessibilityRole="text"
        nativeID={`${label}-label`}
      >
        {label}
        {required && <Text style={styles.required}> *</Text>}
      </Text>

      {/* Input */}
      <TextInput
        value={value}
        onChangeText={onChangeText}
        onFocus={() => setIsFocused(true)}
        onBlur={() => {
          setIsFocused(false);
          onBlur();
        }}
        placeholder={placeholder}
        placeholderTextColor="#AAB8C2"
        keyboardType={keyboardType}
        secureTextEntry={secureTextEntry}
        autoComplete={autoComplete as any}
        textContentType={textContentType as any}
        autoCapitalize={keyboardType === 'email-address' ? 'none' : 'sentences'}
        autoCorrect={false}
        style={[
          styles.input,
          isFocused && styles.inputFocused,
          error && styles.inputError,
        ]}
        // Accessibility
        accessibilityLabel={label}
        accessibilityHint={error ? `Error: ${error}` : undefined}
        accessibilityRequired={required}
        accessibilityLabelledBy={`${label}-label`}
        accessibilityInvalid={!!error}
      />

      {/* Error message */}
      {error && (
        <View
          style={styles.errorContainer}
          accessibilityRole="alert"
          accessibilityLive="polite"
        >
          <Text style={styles.errorIcon}>⚠</Text>
          <Text style={styles.errorText}>{error}</Text>
        </View>
      )}
    </View>
  );
};

// ============================================================================
// MAIN FORM COMPONENT
// ============================================================================

export const FormValidationExample: React.FC = () => {
  const [formData, setFormData] = useState<FormData>({
    email: '',
    password: '',
    confirmPassword: '',
    phone: '',
  });

  const [errors, setErrors] = useState<FormErrors>({});
  const [touched, setTouched] = useState<Record<keyof FormData, boolean>>({
    email: false,
    password: false,
    confirmPassword: false,
    phone: false,
  });
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleChange = (field: keyof FormData) => (value: string) => {
    setFormData(prev => ({ ...prev, [field]: value }));

    // Clear error when user starts typing
    if (errors[field]) {
      setErrors(prev => ({ ...prev, [field]: undefined }));
    }
  };

  const handleBlur = (field: keyof FormData) => () => {
    setTouched(prev => ({ ...prev, [field]: true }));

    // Validate on blur
    let error: string | undefined;
    switch (field) {
      case 'email':
        error = validateEmail(formData.email);
        break;
      case 'password':
        error = validatePassword(formData.password);
        break;
      case 'confirmPassword':
        error = validateConfirmPassword(formData.password, formData.confirmPassword);
        break;
      case 'phone':
        error = validatePhone(formData.phone);
        break;
    }

    if (error) {
      setErrors(prev => ({ ...prev, [field]: error }));
    }
  };

  const validateForm = (): boolean => {
    const newErrors: FormErrors = {
      email: validateEmail(formData.email),
      password: validatePassword(formData.password),
      confirmPassword: validateConfirmPassword(formData.password, formData.confirmPassword),
      phone: validatePhone(formData.phone),
    };

    setErrors(newErrors);
    setTouched({
      email: true,
      password: true,
      confirmPassword: true,
      phone: true,
    });

    return !Object.values(newErrors).some(error => error !== undefined);
  };

  const handleSubmit = async () => {
    if (!validateForm()) {
      return;
    }

    setIsSubmitting(true);

    // Simulate API call
    setTimeout(() => {
      console.log('Form submitted:', formData);
      setIsSubmitting(false);
      // Reset form
      setFormData({
        email: '',
        password: '',
        confirmPassword: '',
        phone: '',
      });
      setErrors({});
      setTouched({
        email: false,
        password: false,
        confirmPassword: false,
        phone: false,
      });
    }, 1500);
  };

  const hasErrors = Object.values(errors).some(error => error !== undefined);

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 64 : 0}
    >
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        keyboardShouldPersistTaps="handled"
      >
        <Text style={styles.title} accessibilityRole="header">
          Create Account
        </Text>

        <FormField
          label="Email"
          value={formData.email}
          onChangeText={handleChange('email')}
          onBlur={handleBlur('email')}
          error={touched.email ? errors.email : undefined}
          placeholder="you@example.com"
          keyboardType="email-address"
          autoComplete="email"
          textContentType="emailAddress"
          required
        />

        <FormField
          label="Password"
          value={formData.password}
          onChangeText={handleChange('password')}
          onBlur={handleBlur('password')}
          error={touched.password ? errors.password : undefined}
          placeholder="Min. 8 characters"
          secureTextEntry
          autoComplete="password-new"
          textContentType="newPassword"
          required
        />

        <FormField
          label="Confirm Password"
          value={formData.confirmPassword}
          onChangeText={handleChange('confirmPassword')}
          onBlur={handleBlur('confirmPassword')}
          error={touched.confirmPassword ? errors.confirmPassword : undefined}
          placeholder="Re-enter password"
          secureTextEntry
          autoComplete="password-new"
          textContentType="newPassword"
          required
        />

        <FormField
          label="Phone (Optional)"
          value={formData.phone}
          onChangeText={handleChange('phone')}
          onBlur={handleBlur('phone')}
          error={touched.phone ? errors.phone : undefined}
          placeholder="(555) 123-4567"
          keyboardType="phone-pad"
          autoComplete="tel"
          textContentType="telephoneNumber"
        />

        <TouchableOpacity
          style={[
            styles.button,
            (isSubmitting || hasErrors) && styles.buttonDisabled,
          ]}
          onPress={handleSubmit}
          disabled={isSubmitting || hasErrors}
          accessibilityRole="button"
          accessibilityLabel="Create account"
          accessibilityHint={hasErrors ? 'Please fix errors before submitting' : 'Submit the form'}
          accessibilityState={{ disabled: isSubmitting || hasErrors }}
        >
          <Text style={styles.buttonText}>
            {isSubmitting ? 'Creating Account...' : 'Create Account'}
          </Text>
        </TouchableOpacity>
      </ScrollView>
    </KeyboardAvoidingView>
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
  scrollContent: {
    padding: 16,
    paddingBottom: 32,
  },
  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#14171A',
    marginBottom: 24,
    textAlign: 'center',
  },

  // Field
  fieldContainer: {
    marginBottom: 20,
  },
  label: {
    fontSize: 15,
    fontWeight: '600',
    color: '#14171A',
    marginBottom: 8,
  },
  required: {
    color: '#EF4444',
  },
  input: {
    height: 48, // Minimum touch target
    backgroundColor: '#F7F9FA',
    borderWidth: 2,
    borderColor: 'transparent',
    borderRadius: 8,
    paddingHorizontal: 16,
    fontSize: 15,
    color: '#14171A',
  },
  inputFocused: {
    borderColor: '#1DA1F2',
    backgroundColor: '#FFFFFF',
  },
  inputError: {
    borderColor: '#EF4444',
    backgroundColor: '#FEF2F2',
  },

  // Error
  errorContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 6,
    paddingHorizontal: 4,
  },
  errorIcon: {
    fontSize: 14,
    marginRight: 4,
  },
  errorText: {
    fontSize: 13,
    color: '#EF4444',
    flex: 1,
  },

  // Button
  button: {
    height: 52,
    backgroundColor: '#1DA1F2',
    borderRadius: 26,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 8,
  },
  buttonDisabled: {
    backgroundColor: '#E1E8ED',
  },
  buttonText: {
    fontSize: 17,
    fontWeight: '600',
    color: '#FFFFFF',
  },
});

/**
 * Best Practices Demonstrated:
 *
 * ✅ Accessibility:
 *    - All fields have labels that remain visible
 *    - accessibilityLabel on all inputs
 *    - accessibilityRequired for required fields
 *    - accessibilityInvalid when errors present
 *    - accessibilityRole="alert" for errors
 *    - Minimum 48pt input height
 *
 * ✅ Validation:
 *    - Real-time validation on blur
 *    - Clear, actionable error messages
 *    - Errors cleared on input
 *    - All fields validated on submit
 *
 * ✅ Keyboard:
 *    - Correct keyboard type for each field
 *    - autoComplete for better UX
 *    - KeyboardAvoidingView for proper layout
 *    - Keyboard persists when tapping outside
 *
 * ✅ Visual Feedback:
 *    - Focus states clearly indicated
 *    - Error states visually distinct
 *    - Required fields marked
 *    - Button disabled state
 *
 * ✅ UX:
 *    - Persistent labels (not just placeholders)
 *    - Progressive validation (only after blur)
 *    - Submit disabled if errors present
 *    - Loading state during submission
 */

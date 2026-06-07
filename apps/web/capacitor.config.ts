import type { CapacitorConfig } from '@capacitor/cli'

/**
 * Capacitor config — embrulha o build Vite num app nativo Android/iOS.
 *
 * Como buildar:
 *   1. npm run build:mobile           # vite build + cap copy
 *   2. npx cap sync                   # propaga deps nativas (1ª vez ou após upgrade)
 *   3. Android: npx cap open android  # abre Android Studio
 *      iOS:     npx cap open ios      # abre Xcode (precisa Mac)
 *
 * O app abre /index.html servido localmente, e faz fetch pra API remota
 * (definida em .env.production como VITE_API_URL).
 */
const config: CapacitorConfig = {
  appId:     'app.astra.client',
  appName:   'Astra',
  webDir:    'dist',
  // Splash + ícones: vão em android/app/src/main/res/ e ios/App/App/Assets.xcassets
  // após primeira cap add android/ios. Use o astra-logo.png como base.
  android: {
    // permite mixed content em DEV pra apontar pra IP local
    allowMixedContent: false,
    // backgroundColor segue --void
    backgroundColor: '#06060e',
  },
  ios: {
    contentInset: 'always',
    backgroundColor: '#06060e',
  },
  server: {
    // Em prod, deixe undefined — app usa webDir. Em dev, descomente
    // pra apontar pro dev server da Vite (mais rápido que rebuild).
    // url:           'http://192.168.0.10:5173',
    // cleartext:     true,
    androidScheme: 'https',
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 1200,
      backgroundColor:    '#06060e',
      androidSplashResourceName: 'splash',
      androidScaleType:   'CENTER_CROP',
      showSpinner:        false,
    },
  },
}

export default config

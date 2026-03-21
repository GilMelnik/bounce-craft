# Bounce Craft

Android app (Kotlin + Jetpack Compose) for baby-safe visual interaction with colorful bouncing shapes.

## Features

- Entry screen with `Play` and `Settings`
- Fullscreen immersive gameplay with optional lock mode
- Tap/drag to create, resize, recolor, throw, and reposition shapes
- Circle/rectangle mode control (`circle_only`, `rectangle_only`, `alternating`, `random`)
- Basic smooth physics (edge bounce + shape-to-shape collisions)
- Timeout-based cleanup of inactive shapes
- Configurable max shape count
- Optional keep-screen-on and best-effort notification suppression

## Project structure

- `app/src/main/java/com/colorbounce/baby/MainActivity.kt` - navigation, screens, immersive mode, touch UI
- `app/src/main/java/com/colorbounce/baby/GameViewModel.kt` - physics, collisions, touch-driven shape updates
- `app/src/main/java/com/colorbounce/baby/GameModels.kt` - shape models
- `app/src/main/java/com/colorbounce/baby/AppSettings.kt` - settings data + shape mode enum
- `app/src/main/java/com/colorbounce/baby/SettingsRepository.kt` - DataStore persistence

## Notes

- Notification suppression is best effort because Android does not allow a normal app to globally block all notifications without user policy access.
- Gesture locking is best effort and device-dependent due to Android system restrictions.

# Quickstart: Localization & UI Beautification

## 1. Prerequisites
- `gdx-freetype` must be added to `build.gradle` in the `core` project.
- Font file `AlibabaPuHuiTi-3-65-Medium.ttf` must be placed in `assets/fonts/`.
- Language files `messages.properties` and `messages_en.properties` must be in `assets/i18n/`.

## 2. Basic Usage

### Initializing the Service
```java
LocalizationService i18n = new LocalizationService();
i18n.init();
```

### Getting Translated Text
```java
String title = i18n.get("main_menu_settings");
```

### Real-time Language Switching
```java
// In your UI component (e.g., a Label or Button)
i18n.addListener(() -> {
    button.setText(i18n.get("main_menu_settings"));
});

// To switch language
i18n.setLanguage("en_US");
```

## 3. UI Beautification
- Use `ParallaxBackground` in your `Screen`'s `render` method.
- Wrap your buttons with `AnimatedButton` for hover effects.

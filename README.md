# Android Auto-Clicker — Jetpack Compose + AccessibilityService

Bu repo iki senaryoyu kapsayan, kopyalanabilir Kotlin kod örnekleri içerir.

---

## Senaryo 1 — Uygulama İçi Uzun Basma (`LongPressButton.kt`)

| Özellik | Detay |
|---|---|
| Framework | Jetpack Compose |
| Gesture | `pointerInput` + `detectTapGestures` |
| Döngü yönetimi | `LaunchedEffect` + `delay()` (coroutine) |
| Varsayılan aralık | 100 ms |
| Thread | UI thread'i kilitlemez |

### Nasıl Kullanılır?

```kotlin
// Herhangi bir Composable içinde:
RepeatingActionButton(
    label = "Basılı Tut",
    intervalMs = 100L,
    onTick = {
        // Her 100ms'de çalışacak kod buraya
        viewModel.doSomething()
    }
)
```

---

## Senaryo 2 — Sistem Geneli Oto-Tıklayıcı (`AutoClickerService.kt`)

| Özellik | Detay |
|---|---|
| Mekanizma | `AccessibilityService` + `dispatchGesture` |
| Gesture tanımı | `GestureDescription` + `StrokeDescription` |
| Döngü yönetimi | `CoroutineScope` + `delay()` |
| Minimum API | 24 (Android 7.0) |
| Thread | `Dispatchers.Main` → gestur, `Dispatchers.Default` → döngü |

### Nasıl Kullanılır?

```kotlin
// Servis bağlandığında otomatik başlar (onServiceConnected içinde).
// Manuel kontrol için:
service.startAutoClick(x = 540f, y = 960f)
service.stopAutoClick()
```

---

## Kurulum Adımları

1. **Dosyaları kopyala** — yapı için `AndroidManifest_snippet.xml` dosyasına bak
2. **strings.xml'e ekle:**
   ```xml
   <string name="accessibility_service_label">Oto-Tıklayıcı</string>
   <string name="accessibility_service_description">Belirtilen koordinatlara otomatik tıklama yapar.</string>
   ```
3. **minSdk = 24** olarak ayarla (`build.gradle`)
4. Cihazda: **Ayarlar → Erişilebilirlik → Oto-Tıklayıcı → Etkinleştir**

---

## Dosya Yapısı

```
app/src/main/
├── java/com/example/autoclicker/
│   ├── LongPressButton.kt           # Senaryo 1: Compose uzun basma
│   └── AutoClickerService.kt        # Senaryo 2: Sistem geneli gesture
├── res/xml/
│   └── accessibility_service_config.xml
└── AndroidManifest.xml              # AndroidManifest_snippet.xml'deki bloğu ekle
```

---

## Önemli Notlar

- `dispatchGesture` → Android'de sistem seviyesinde tıklamanın **en güncel ve güvenli** yolu
- `GestureDescription` → tıklama konumu, süresi ve vuruş şiddetini kontrol eder
- Play Store'a yükleyecekseniz AccessibilityService kullanımını beyan etmeniz gerekir
- Gerçek cihazda test edin; emülatör koordinatları farklı davranabilir

# План впровадження додатку "Збірник карток клієнтів"

Створення комплексного додатку для управління клієнтами з підтримкою мультимедіа, геолокації та нотаток.

## Технологічний стек
- **UI:** Jetpack Compose (Material 3)
- **Database:** Room
- **Navigation:** Navigation Compose
- **Image Loading:** Coil
- **Permissions & Camera:** CameraX + Accompanist Permissions (або стандартні API)
- **Location:** Fused Location Provider
- **Icons:** Material Icons Extended

## Пропоновані зміни

### 1. Конфігурація проекту

#### [MODIFY] [libs.versions.toml](file:///C:/Users/user/AndroidStudioProjects/Lessons/MyApplication/gradle/libs.versions.toml)
Додавання версій та бібліотек для Room, Navigation, Coil, CameraX та Google Services.

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/user/AndroidStudioProjects/Lessons/MyApplication/app/build.gradle.kts)
Підключення плагінів (KSP для Room) та залежностей.

---

### 2. Рівень даних (Data Layer)

#### [NEW] [ClientEntity.kt](file:///C:/Users/user/AndroidStudioProjects/Lessons/MyApplication/app/src/main/java/com/seryoga/myapplication/data/ClientEntity.kt)
#### [NEW] [ClientDao.kt](file:///C:/Users/user/AndroidStudioProjects/Lessons/MyApplication/app/src/main/java/com/seryoga/myapplication/data/ClientDao.kt)
#### [NEW] [AppDatabase.kt](file:///C:/Users/user/AndroidStudioProjects/Lessons/MyApplication/app/src/main/java/com/seryoga/myapplication/data/AppDatabase.kt)
Сутності для Клієнтів, Телефонів (1:N), Нотаток (1:N) та Адрес.

---

### 3. Рівень UI (Jetpack Compose)

#### [NEW] [ClientViewModel.kt](file:///C:/Users/user/AndroidStudioProjects/Lessons/MyApplication/app/src/main/java/com/seryoga/myapplication/ui/ClientViewModel.kt)
Логіка пошуку, додавання та редагування.

#### [NEW] [ClientListScreen.kt](file:///C:/Users/user/AndroidStudioProjects/Lessons/MyApplication/app/src/main/java/com/seryoga/myapplication/ui/screens/ClientListScreen.kt)
Список з пошуком та стильні картки.

#### [NEW] [ClientDetailScreen.kt](file:///C:/Users/user/AndroidStudioProjects/Lessons/MyApplication/app/src/main/java/com/seryoga/myapplication/ui/screens/ClientDetailScreen.kt)
Форма редагування з динамічними полями для телефонів та мультимедіа.

#### [NEW] [SettingsScreen.kt](file:///C:/Users/user/AndroidStudioProjects/Lessons/MyApplication/app/src/main/java/com/seryoga/myapplication/ui/screens/SettingsScreen.kt)
Перемикач теми.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/user/AndroidStudioProjects/Lessons/MyApplication/app/src/main/java/com/seryoga/myapplication/MainActivity.kt)
Налаштування навігації та обробка теми.

---

### 4. Додаткові функції
- **Камера:** Інтеграція CameraX для фото магазинів.
- **Локація:** Отримання поточних координат для адреси.
- **Нотатки:** Підтримка тексту, чек-листів та голосових заміток (через MediaRecorder).

## План верифікації
- Юніт-тести для Room DAO.
- Ручне тестування інтерфейсу на емуляторі/пристрої.
- Перевірка перемикання тем.

## Відкриті питання
- Чи потрібно підключити Firebase для синхронізації, чи поки достатньо локальної бази?
- Який саме стиль іконок вам більше подобається (Rounded, Outlined, Filled)? Я буду використовувати Material 3 за замовчуванням.

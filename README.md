# AnEn - Seamless Vocabulary Saver

![Build Debug APK](https://github.com/fearonin/AnEn/actions/workflows/build.yml/badge.svg)
![Android SDK](https://img.shields.io/badge/Android%20SDK-34-green)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

**AnEn** is a lightweight, native Android utility designed to help you seamlessly save unfamiliar words in multiple languages while reading in browsers, e-readers, or any other app. It features a non-intrusive overlay and provides flexible export options compatible with popular flashcard applications.

## ✨ Features

*   **Flexible Invocation Methods:** Trigger the word-saving overlay either by swiping up from the edge of the screen, or by using a convenient Quick Settings Tile.
*   **Multi-Language & Multi-Source Support:** Organize your vocabulary. Create multiple languages and track exactly where you found each word (e.g., specific books, movies, or articles).
*   **Multi-App Targeting:** Select multiple specific apps (e.g., your favorite reader and browser) where the swipe overlay should be active.
*   **Smart Animations:** The input window appears and disappears smoothly, following the trajectory of your swipe.
*   **Offline Database:** Uses a local SQLite database to store words, languages, sources, timestamps, and export statuses.
*   **Advanced Export & Import:** 
    *   Export only new words or your entire database.
    *   Choose custom delimiters (Tab, Comma, Semicolon, etc.).
    *   Optionally include metadata (timestamps, language, and source) to perfectly restore your history on a new device.
    *   Import files with smart filtering and automatic creation of missing languages/sources.
*   **Themes & Localization:** 6 built-in color themes (Monochrome, Light, Green, Cyan, Red, Purple) and full English/Russian localization.
*   **Privacy First:** No internet permissions required. All your data stays strictly on your device.

## 🚀 How It Works

AnEn uses core Android system APIs to achieve a seamless experience without draining your battery:

1.  **`AccessibilityService`:** Continuously monitors window state changes to detect which app is currently in the foreground.
2.  **`WindowManager`:** When a target app is active, it places a transparent view over the navigation bar or uses a Quick Settings Tile to trigger the input overlay.
3.  **`SQLiteOpenHelper`:** Manages local storage efficiently with a relational structure (Languages -> Sources -> Words) without the overhead of heavy ORM libraries.
4.  **Coroutines:** File import/export operations and database queries are handled in the background to keep the UI perfectly smooth.

## 🛠️ How to Build & Run Locally

To build this project locally, you will need **Android Studio (Hedgehog or newer)** and **JDK 17**.

1.  Clone the repository:
   ```bash
   git clone https://github.com/fearonin/AnEn.git
   ```
2.  Open the project in Android Studio.
3.  Let Gradle sync the dependencies.
4.  Connect your Android device (API 26+) or start an emulator.
5.  Click **Run** or execute:
   ```bash
   ./gradlew assembleDebug
   ```

The compiled APK will be located in `app/build/outputs/apk/debug/`.

## 📱 How to Use

1.  **Initial Setup:** On the first launch, add the languages you want to learn and create your initial sources (e.g., "Default", "Book", "Movie").
2.  **Permissions:** Open the app and grant **Display over other apps** and **Accessibility Service** permissions. The UI will unlock once permissions are granted.
3.  **Select Targets:** Tap "Select Target App" and check the apps where you want the swipe overlay to work.
4.  **Enable Service:** Turn on the global "Enable / Disable Service" toggle.
5.  **Save Words:** Open a target app, invoke the overlay (via swipe or tile), select the desired language/source directly in the input window, type the word, and press "Done".
    *   *The window turns gray if the word is new.*
    *   *The window stays black if the word is a duplicate in that specific language.*
6.  **Export:** Go to the main screen, tap **Export** or **Export All**. Apply language/source filters, choose your delimiter, and export the `.txt` file to import into your favorite flashcard app.

## 🤝 Contributing

Contributions, issues, and feature requests are welcome! Feel free to check the [issues page](https://github.com/fearonin/AnEn/issues).

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
# AnEn - Seamless English Vocabulary Saver

![Build Debug APK](https://github.com/fearonin/AnEn/actions/workflows/build.yml/badge.svg)
![Android SDK](https://img.shields.io/badge/Android%20SDK-34-green)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue)
![License](https://img.shields.io/badge/License-MIT-yellow)

**AnEn** is a lightweight, native Android utility designed to help you seamlessly save unfamiliar English words(and more) while reading in browsers, e-readers, or any other app. It features a swipe-to-save overlay and provides seamless export to flashcard apps.

## ✨ Features

*   **Invisible Swipe Overlay:** Triggers an input window by swiping up from the navigation git status bar, overlaid on top of your chosen apps.
*   **Multi-App Targeting:** Select multiple specific apps (e.g., your favorite reader and browser) where the overlay should be active.
*   **Smart Animations:** The input window appears and disappears smoothly, following the trajectory of your swipe.
*   **Offline Database:** Uses a local SQLite database to store words, timestamps, and export statuses.
*   **Anki Export:** Export only new words or your entire database as a tab-separated `.txt` file, ready for Anki import.
*   **Import:** Load words from external `.txt` files and merge them into your database without duplicates.
*   **Themes & Localization:** 6 built-in color themes (Monochrome, Light, Green, Cyan, Red, Purple) and full English/Russian localization.
*   **Privacy First:** No internet permissions required. All your data stays on your device.

## 🚀 How It Works

AnEn uses core Android system APIs to achieve a seamless experience without draining your battery:

1.  **`AccessibilityService`:** Continuously monitors window state changes to detect which app is currently in the foreground.
2.  **`WindowManager`:** When a target app is active, it places a transparent 30dp view over the navigation bar. Swiping up on this view triggers the input overlay.
3.  **`SQLiteOpenHelper`:** Manages local storage efficiently without the overhead of heavy ORM libraries.
4.  **Coroutines:** File import/export operations are handled in the background to keep the UI perfectly smooth.

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

1.  **Permissions:** Open the app and grant **Display over other apps** and **Accessibility Service** permissions. The buttons will automatically disappear once granted.
2.  **Select Targets:** Tap "Select Target App" and check the apps where you want the overlay to work.
3.  **Enable Service:** Turn on the global "Enable / Disable Service" toggle.
4.  **Save Words:** Open a target app (e.g., a browser), swipe up from the bottom edge (or from the side edge in landscape), type the word, and press "Done".
    *   *The window turns gray if the word is new.*
    *   *The window stays black if the word is a duplicate.*
5.  **Export to Anki:** Go to the main screen, tap **Export** to save only new words, or **Export All**. Import the resulting `.txt` file into Anki (use Tab as the field separator).

## 🤝 Contributing

Contributions, issues, and feature requests are welcome! Feel free to check the [issues page](https://github.com/fearonin/AnEn/issues).

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE.md) file for details.
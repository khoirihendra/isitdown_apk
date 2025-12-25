# IsItDown Monitor App

**IsItDown** is a native Android application designed to monitor the availability of a specific website or IP address. It runs in the background, checking the host at configurable intervals, and notifies the user immediately if the target goes offline.

## Features

- **Background Monitoring**: Runs as a persistent foreground service to ensure reliable monitoring even when the app is closed.
- **Real-time Status**: Persistent notification displays the current status (UP/DOWN) and the time of the last check.
- **Smart Connectivity Check**: Distinguishes between your own internet connection loss and the remote host being down to prevent false alarms.
- **Customizable Alerts**:
  - **Sound Alerts**: Play a sound when the host goes down.
  - **Custom Audio**: Choose a custom audio file or use the system default.
  - **Alert Frequency**: Configure the alert to play once or repeat.
- **Logging**: Keeps a detailed local log of all monitoring events (UP, DOWN, No Internet).
- **Auto-Pruning**: Automatically deletes old logs based on a configurable retention period (default 7 days) to save space.
- **Configurable Interval**: Set the check frequency to suit your needs (e.g., every 5 minutes).

## Getting Started

### Prerequisites
- Android Device running Android 8.0 (Oreo) or higher (API Level 26+).
- **Internet Connection**: Required to ping the target host.

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/isitdown_apk.git
   ```
2. Open the project in **Android Studio**.
3. Sync Gradle and build the project.
4. Run on an emulator or a physical device.

### Usage

1. **Enter Host**: Type the URL (must start with `https://`) or IP address you want to monitor.
2. **Select Interval**: Choose how often you want the app to check the host (default is 5 minutes).
3. **Start Monitoring**: Tap "Start Monitoring". The app will minimize and a notification will appear in the status bar.
4. **View Logs**: Open the app at any time to see the history of uptime and downtime events.
5. **Settings**: configure alert sounds, frequency, and log retention in the Settings menu.

## Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM / Service-based
- **Components**:
  - **Foreground Service**: For continuous background execution.
  - **Coroutines**: For asynchronous network operations.
  - **BroadcastReceiver**: For communication between Service and UI.
  - **RecyclerView**: For efficient log display.

## Permissions

The app requires the following permissions to function correctly:
- `INTERNET`: To check connection to the host.
- `FOREGROUND_SERVICE`: To run the monitoring service in the background.
- `POST_NOTIFICATIONS`: To show the persistent status notification.
- `WAKE_LOCK`: To ensure the service can wake up to perform checks (implicitly handled by Foreground Service).
- `READ_EXTERNAL_STORAGE` / `READ_MEDIA_AUDIO`: To browse and select custom alert sounds.


# jck-pro-max

## Introduction

`jck-pro-max` is a powerful Android device comprehensive management and system deep toolbox. This application not only provides store functionality (search, install, favorite management) but also includes low-level system service invocation and debugging capabilities tailored for specific firmware environments (Zybos). It is suitable for device information inquiry, application management, and system-level parameter modification.

## Main Features

### 1. Store
*   **Smart Search & Browsing**: Supports keyword search for applications and provides category filtering (e.g., Recommended, History).
*   **Efficient Installation**: Supports single application installation and batch selected installation modes.
*   **Personalized Favorites**: Allows saving favorite applications and viewing recently browsed records.
*   **Cache Mechanism**: Features local application data caching to improve loading speed.

### 2. App Launcher
*   **Full App Management**: Displays all applications on the device and supports filtering by system/non-system applications.
*   **Advanced Launch**: Supports viewing application details, copying package names, and launching applications via "Clear Task Stack" for easier multi-task management.

### 3. Device Tools
*   **Information Query**: Provides query and binding for Device Serial Number (SN) and Sub-device ID.
*   **Behavior Data**: Supports retrieving device usage behavior history (Behavior History), screen usage duration (Using Time), and Pisou History.

### 4. Zybos System Tools (Zybos Tools - Core Module)
This module encapsulates a large number of advanced call interfaces targeting low-level system services (typically requiring Root access or specific system environment support):
*   **System Service Vulnerability Exploitation (VulnHelper)**: Includes detection and control over services such as DuraSpeed (process management), MtkPpl (performance/power management), DataShaping (traffic shaping), PowerHal (hardware performance tuning), etc.
*   **Parental Control Management**: Provides powerful features such as disabling parental controls, unlocking Privacy Lock, and cancelling system updates.
*   **System Properties (SysProp) & Settings**: Supports reading and writing system properties via Root, modifying network configurations (e.g., DNS), and adjusting ADB switches, display parameters, etc.
*   **Hardware & Battery**: Supports querying charger status, battery health, hardware component information, and allows PQ/Gamma screen parameter adjustment.
*   **System Service Calls**: Supports direct invocation of hidden system APIs via Transaction, executing operations such as force stopping applications and preparation steps before factory reset.

### 5. Auxiliary Functions
*   **Broadcast Install**: Supports sending installation commands via Broadcast, suitable for automated deployment or system-level application pushing.
*   **Terminal Logs**: Provides real-time viewing of application runtime logs.
*   **Update Mechanism**: Built-in self-update check, download, and installation functionality.

## Technical Architecture

*   **Development Language**: Java (Android Native)
*   **Core Components**:
    *   `com.jck.promax.StoreActivity`: Responsible for store logic, including complex network requests and UI rendering.
    *   `com.jck.promax.ZybosToolsActivity` & `ZybosVulnHelper`: Implements reflection calls to hidden system services and low-level hardware vulnerability exploitation logic.
    *   `com.jck.promax.ApiHelper`: Encapsulates communication logic with backend APIs, including request signing, MD5 verification, and AES data encryption/decryption.
    *   `com.jck.promax.Obfuscator`: Used for string decryption and code obfuscation processing to enhance security.

## Dependencies & Build

1.  **Environment**: Requires Android Studio or Gradle build environment.
2.  **SDK**: Developed based on Android SDK.
3.  **Permissions**: The application declares permissions for networking, storage read/write, device info access, etc. **Note: Some advanced system tool functions require the device to have Root access to operate normally.**

## Usage Instructions

1.  **Install**: Install the compiled APK file onto an Android device.
2.  **Launch**: Open the application; a disclaimer may be displayed on the first launch.
3.  **Operations**:
    *   **Store**: Search and install applications in the Store interface.
    *   **Tools**: Enter Device Tools or Zybos Tools for device debugging.
    *   **Launcher**: Manage local applications in the App Launcher.

> **Warning**: This application contains numerous low-level system modification features (such as disabling parental controls, modifying system properties, etc.). Please ensure you understand the consequences of your actions before using. Improper operation may lead to system instability or functional abnormalities.

## License

This project follows the open source license agreement listed in the `LICENSE` file located in the root directory of the project.
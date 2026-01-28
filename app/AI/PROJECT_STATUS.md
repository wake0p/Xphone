# OutPhone Project Status & Development Guide

> **Last Updated**: 2026-01-26
> **Purpose**: This document tracks the stability status of modules and outlines future development plans.

## 🟢 Locked / Stable Modules (DO NOT MODIFY)
The following components are considered feature-complete and stable. **Do not modify logic in these files unless explicitly requested to fix a critical bug.**

### 1. Wireless Pairing Logic
*   **Target Files**: 
    *   `com.safe.discipline.data.service.PairingService.kt`
    *   `com.safe.discipline.data.service.PairingReplyReceiver.kt`
*   **Current Behavior**: 
    *   Uses mDNS to discover `_adb-tls-pairing._tcp`.
    *   Notifications include valid Host IP (fixes "Read Error").
    *   Supports quick reply from Notification (via BroadcastReceiver).
    *   Auto-connects/pairs upon receiving the code.

### 2. App Management Core
*   **Target Files**:
    *   `com.safe.discipline.viewmodel.MainViewModel.kt` (Core pm hide/show logic)
    *   `com.safe.discipline.data.service.ShizukuService.kt` (ADB command execution)
*   **Current Behavior**:
    *   Correctly manages `pm hide` (disable) and `pm unhide` (enable) commands.
    *   State management for `installedApps`, `hasPermission`, `isReady`.

### 3. Home UI (Connected State)
*   **Target Files**:
    *   `com.safe.discipline.ui.screens.HomeScreen.kt`
*   **Current Behavior**:
    *   **Disconnected**: Shows `StatusCard` only (minimalist guide).
    *   **Connected**: Shows full-screen `AppListContent` ONLY. No status headers, no redundant buttons.
    *   **Features**: Search, Filter (Disabled first), Multi-selection, Batch Disable/Enable.

---

## 🟡 Development Queue / Next Steps

### Phase 2: Scheduled & Force Mode (Current Focus)
**Goal**: Implement time-based blocking with strict "Force Mode" constraints.

#### 1. Data Model & Architecture
*   [ ] Create `Plan` entity (Room Database or DataStore).
    *   Fields: `startTime`, `endTime`, `packages`, `forceMode`, `maxUnlocks`, `currentUnlocks`.
*   [ ] Implement background scheduler (WorkManager/AlarmManager) to auto-execute `pm hide/show`.

#### 2. UI - Plan Management
*   [ ] Add Bottom Navigation (Home / Plans).
*   [ ] "Plan Creation" Screen: Select Time, Apps, Force Mode Settings.

#### 3. Force Mode Logic (The "Soul Inquiry")
*   [ ] **Interception**: When user tries to "Unhide" an app managed by an active Force Plan:
    *   Check `currentUnlocks < maxUnlocks`.
    *   **Dialog Flow**:
        1.  "Are you sure?" (Confirm)
        2.  "What is your purpose?" (TextInput, maybe min length requirement)
        3.  Update `currentUnlocks` count.
        4.  Grant temporary/permanent access (TBD: Define if it ends plan for the day or gives X mins).

---

## 📝 Notes for AI Assistant
1.  **Strictly Maintain**: The "Connection -> App List" flow is the core UX. Do not revert to the old "Split Button" or "Dialog" style for app management.
2.  **Notification Reply**: Always ensure `PairingService` uses `PairingReplyReceiver` with `StringExtra` for the pairing code.
3.  **Force Mode**: Logic must be handled in `MainViewModel` before calling `showApps`.


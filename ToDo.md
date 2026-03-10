# HypZen Batch Flash Improvements

- [x] **1. File Size Reliability**
    - [x] Update `FastbootManager.flash` to accept a `Long` size.
    - [x] Use `ContentResolver` to get the actual file size in `MainViewModel`.
- [x] **2. Expanded Command Support**
    - [x] Broaden regex in `MainViewModel.parseFlashScript` to support `erase`, `format`, `reboot`, and `set_active`.
    - [x] Update `FlashOperation` and `FastbootManager` to handle these commands.
- [x] **3. Error Handling & "Stop on Failure"**
    - [x] Add `stopOnError` toggle to `MainViewModel`.
    - [x] Implement check in `runBatchFlash` to halt on error if enabled.
- [x] **4. Device Max Download Size Support**
    - [x] Query `max-download-size` from device.
    - [x] Add basic checks/warnings if image size exceeds device limits.
- [x] **5. Global Progress UI**
    - [x] Add `globalProgress` state to `MainViewModel`.
    - [x] Update `BatchFlashScreen` to display a master progress bar.
- [x] **6. Advanced Script Parsing**
    - [x] Ignore comments and empty lines.
    - [x] Basic support for script-level variables if needed.

## CMD Screen Enhancements
- [x] **1. Command History & Auto-completion**
    - [x] Implement local storage for command history.
    - [x] Add a dropdown or "up-arrow" behavior to recall previous commands.
- [x] **2. Context-Aware Quick Commands**
    - [x] Categorize quick commands by ADB vs Fastboot.
    - [x] Filter displayed buttons based on current device state.
- [x] **3. Integrated Mini-Console**
    - [x] Add a dedicated, scrollable output area within the CMD screen.
    - [x] Style it with a "Zen Terminal" aesthetic (monospace, subtle glow).
- [x] **4. Favorites/Macros**
    - [x] Allow users to save custom commands as quick buttons.
    - [x] Persistent storage for these favorites.
- [x] **5. Interactive ADB Shell**
    - [x] Keep AdbStream open for the CMD screen session.
    - [x] Handle multi-stage commands if possible.

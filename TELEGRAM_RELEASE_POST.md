*Hyflash v1.0 — Release Changelog*

`Package:` `com.hypex.hyflash`  
`Type:` Android-to-Android USB OTG Flash Tool  
`Min Android:` 12L (SDK 32)

*What’s New*
• Direct partition flashing from phone storage  
• Batch flash mode with script-based operation flow  
• Multi-partition wipe + factory reset actions  
• Reboot tools: system, recovery, bootloader, fastboot, sideload, EDL  
• Built-in ADB/Fastboot command terminal  
• Quick presets, command history, and favorites  
• Real-time log console with copy/clear support  
• Confirmation prompts for dangerous operations

*Technical Notes*
• Pure Kotlin/Java implementation (no bundled adb/fastboot binaries)  
• Uses `libadb-android` + BouncyCastle for transport/auth flows  
• Designed for root-less, PC-less field usage over OTG

*Developer Note*
This build is focused on reliability-first flashing workflow.  
UI and command flows were kept practical for power users and repair/modding scenarios.

*Warning*
Flashing and wipe commands are destructive.  
Always verify image, partition name, and target device state before executing.

*Download*
Install the latest `app-release.apk` from the release artifacts.

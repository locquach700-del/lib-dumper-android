# Lib Dumper

Android APK (root) to dump native `.so` libs from a running process.

## Output
```
/sdcard/LibDumps/<package>/<yyyyMMdd_HHmmss>/
  libil2cpp.so
  libunity.so
  maps.txt
```

## Requirements
- Rooted device (`su`)
- Target app must be running

## Build
GitHub Actions builds debug APK on every push. Download artifact **LibDumper-APK**.

Or Android Studio: open this repo, Build > Build APK(s).

## Use
1. Install APK, grant storage if asked
2. Open game
3. Open Lib Dumper -> select package -> DUMP LIBS
4. Files in `/sdcard/LibDumps/`

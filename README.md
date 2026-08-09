# INSDL

Native Android Instagram post/carousel image downloader.

## Current version

v1.4.2

## Project

- Native Android (Java)
- minSdk 29
- targetSdk 35
- Saves media to `Pictures/INSDL/`
- Supports pasted Instagram links and Android share-to-INSDL flow
- Uses an imported `cookies.txt` containing a valid Instagram session
- Light minimal UI with unified controls and vector icons

## Build

GitHub Actions builds the debug APK from the `main` branch via `.github/workflows/build-apk.yml`.

The Android project lives directly at the repository root.

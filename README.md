# ChessHint

ChessHint is a small Android chess-position helper: manually recreate the physical board, select whose turn it is, and ask Stockfish for a strong move.

## Features
- Compose chessboard with coordinate labels and piece palette
- Tap a piece in the palette, then a square to place it; select `⌫` to erase a square
- White/Black to move selector
- FEN serialization and basic position sanity checks (one king per side, pawn count)
- Stockfish UCI analysis on-device; recommended move source/destination is highlighted
- CI downloads the official Stockfish Android ARM64 engine and packages it into the APK
- Unit tests for FEN serialization and position validation

## Build and test
The GitHub Actions workflow downloads the official Stockfish 19 Android ARM64 archive, stages the executable in `app/src/main/jniLibs/arm64-v8a/libstockfish.so`, runs unit tests, and assembles a debug APK artifact.

Locally, Android Studio can build the project after an ARM64 Stockfish executable is placed at that same jniLibs path. The engine is intentionally not committed into this source archive due to its large size. The CI artifact is the ready-to-install APK.

## Engine license
Stockfish is GPL-3.0-or-later. This app packages the official engine binary in CI. The corresponding Stockfish source is available at https://github.com/official-stockfish/Stockfish (release tag `sf_19`), and the license text is at https://github.com/official-stockfish/Stockfish/blob/sf_19Copying.txt. If distributing APKs, retain the applicable notices and provide the corresponding source as required by the GPL.

## Project
- Android / Kotlin / Jetpack Compose
- Application ID: `com.chesshint`
- Minimum Android: 8.0 (API 26)

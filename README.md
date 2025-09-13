# README.md

Android application built with Jetpack Compose that captures text via the camera, performs OCR with Tesseract, corrects the text using Gemini AI, and checks it against a local Room database. When a match is found, it can send the associated link and type to a server. The app provides a user mode for scanning and an admin mode for managing entries.

## Features

- Camera capture & cropping – Uses CameraX and uCrop to capture and crop images.
- Image preprocessing – Converts to grayscale, removes noise, and adjusts contrast before OCR.
- OCR & correction – Extracts text with Tesseract (tess-two) and corrects it via Gemini AI.
- Local database – Stores text/link/type entries using Room.
- Text matching – Checks captured text against stored entries using similarity metrics and Gemini AI.
- Server communication – Sends matched content to a local server.
- Start menu navigation – User mode vs. admin mode (add/list entries).

## Getting Started

- Clone the repository.
- Open the project in Android Studio.
- Configure a valid Gemini API key in Constants.kt.
- Build & run on an emulator or device (requires Android SDK 35, camera, and network access).


# FieldCapture – Hands-Free Field Capture

## Project Overview

FieldCapture is an Android-based prototype designed to help water-damage technicians capture inspection information during property walkthroughs.

The application supports image capture, voice commands, technician notes, image quality checks, OCR-based nameplate extraction, damage information extraction, and structured inspection records.

The current implementation uses Android CameraX and a Python FastAPI backend. Meta DAT SDK integration is a planned extension.

## Features

- Live camera preview and image capture.
- Voice commands for nameplate capture, damage capture, and technician notes.
- Image quality checks for blur, brightness, and framing.
- Retake prompts for images that fail quality checks.
- OCR-based extraction of appliance nameplate information.
- Damage information extraction, including room, surface, damage type, and rough size.
- Confidence values for extracted fields.
- Structured inspection records stored in JSON.
- Fifteen self-defined water-damage estimate codes.
- Offline upload queue using Android WorkManager.

## Technology Stack

### Android
- Kotlin
- Jetpack Compose
- CameraX
- WorkManager
- OkHttp

### Backend
- Python
- FastAPI
- OpenCV
- EasyOCR
- Pydantic

## Project Structure

```text
FieldCapture/
├── android-app/
├── backend/
│   ├── main.py
│   ├── models.py
│   ├── estimate_codes.py
│   └── uploads/
├── evaluation/
│   ├── nameplates/
│   ├── damage/
│   ├── low_light/
│   └── normal_light/
├── ARCHITECTURE.md
└── README.md
```

## Setup Instructions

### 1. Backend Setup

Open PowerShell:

```powershell
cd FieldCapture\backend
python -m venv venv
.\venv\Scripts\Activate.ps1
pip install fastapi uvicorn python-multipart opencv-python numpy easyocr pydantic
pip install requests
```

Start the backend:

```powershell
python -m uvicorn main:app --host 0.0.0.0 --port 8001
```

Backend API documentation:

`http://127.0.0.1:8001/docs`

### 2. Android Setup

1. Open the `android-app` folder in Android Studio.
2. Allow Gradle to sync.
3. Connect an Android phone with USB debugging enabled.
4. Configure the backend IP address in the application.
5. Build and run the application.

Ensure the phone and computer are connected to the same network.

## Evaluation

The prototype was tested using genuine appliance nameplate and field images.

Evaluation includes:
- Nameplate OCR extraction.
- Damage information extraction.
- Image quality checks.
- Low-light image testing.
- Capture and upload behaviour.

Field-wise accuracy and extraction failures should be reported using manually verified ground-truth values. Detection counts alone should not be treated as accuracy.

## Meta DAT SDK

The current implementation uses an Android phone camera as a prototype.

Meta DAT SDK and Mock Device Kit integration are intended for future development. Physical Meta glasses integration has not been claimed as completed.

## Limitations

- OCR accuracy depends on image quality, text orientation, and nameplate condition.
- Some appliance fields may not be detected correctly.
- Image framing checks use basic image-processing heuristics.
- Damage extraction requires further validation against manually labelled examples.
- Actual Meta glasses integration remains future work.

## Two-Week Future Work

1. Integrate Meta DAT SDK and test with the Mock Device Kit.
2. Improve OCR field extraction and confidence scoring.
3. Improve damage detection and measurement.
4. Calibrate image quality thresholds using more field images.
5. Improve offline synchronization and error recovery.
6. Conduct additional testing and document field-wise accuracy.

## AI Tool Disclosure

AI assistance was used during development for implementation guidance, debugging, and documentation. All reported test results should be based on actual project execution.

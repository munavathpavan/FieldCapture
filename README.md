# FieldCapture – Hands-Free Field Capture for Property Inspections

FieldCapture is a prototype Android application designed to help water-damage restoration technicians capture inspection information with minimal manual interaction.

The application supports camera-based image capture, voice commands, image-quality checks, nameplate OCR, structured inspection records, technician notes, and offline upload handling.

## 1. Features

- **Image capture:** Capture equipment nameplates and property damage images using the Android camera.
- **Voice commands:** Initiate nameplate capture, damage capture, and technician notes using voice commands.
- **Image-quality checks:** Detect potentially blurry, dark, or poorly framed images and prompt the technician to retake them.
- **Nameplate OCR:** Use EasyOCR to extract equipment make, model, serial number, and manufacture date where detectable.
- **Damage records:** Store structured damage information, including room, surface, damage type, and rough size.
- **Technician notes:** Save free-text inspection observations.
- **Confidence values:** Store a confidence score alongside extracted or recorded fields.
- **Offline upload handling:** Use Android WorkManager to queue uploads for background processing.
- **Estimate codes:** Provide 15 prototype restoration estimate line-item codes.

## 2. Technology Stack

### Android application
- Kotlin
- Jetpack Compose
- CameraX
- Android WorkManager
- OkHttp
- Android Speech Recognition

### Backend
- Python
- FastAPI
- OpenCV
- EasyOCR
- Pydantic
- JSON-based record storage

## 3. Project Structure

```text
FieldCapture/
│
├── android-app/
│   ├── app/
│   │   └── src/main/java/com/fieldcapture/app/
│   │       ├── MainActivity.kt
│   │       └── OfflineUploadWorker.kt
│   └── build.gradle.kts
│
├── backend/
│   ├── main.py
│   ├── models.py
│   ├── estimate_codes.py
│   └── requirements.txt
│
├── evaluation/
│   ├── evaluation_report.md
│   ├── test_nameplates.py
│   └── nameplates/
│       ├── nameplate_ocr_results.json
│       └── captured test images
│
├── sample_records/
│   └── sample_records.json
│
├── ARCHITECTURE.md
├── META_SDK_RESEARCH.md
└── README.md
```

## 4. Requirements

- Windows 10/11
- Python 3.10 or later
- Android Studio
- Android device or emulator
- Git
- Internet connection for initial dependency and OCR model installation

A physical Android phone is recommended for camera and voice testing.

## 5. Backend Setup

Open PowerShell in the project directory.

### Step 1: Create a virtual environment

```powershell
cd FieldCapture\backend
python -m venv venv
```

### Step 2: Activate the environment

```powershell
.\venv\Scripts\Activate.ps1
```

### Step 3: Install dependencies

```powershell
pip install -r requirements.txt
```

### Step 4: Start the backend

```powershell
python -m uvicorn main:app --host 0.0.0.0 --port 8001
```

The backend will run at:

```text
http://127.0.0.1:8001
```

Open the interactive API documentation:

```text
http://127.0.0.1:8001/docs
```

Check backend health:

```text
http://127.0.0.1:8001/health
```

The first OCR execution may require downloading EasyOCR model files.

## 6. Android Setup

1. Open Android Studio.
2. Select **Open**.
3. Choose the `android-app` folder.
4. Allow Gradle synchronization to complete.
5. Connect an Android device with USB debugging enabled, or use an emulator.
6. Build and run the application.

### Configure the backend address

The Android application must be able to reach the computer running the backend.

Find the computer's local IPv4 address:

```powershell
ipconfig
```

Look for the IPv4 address of the active Wi-Fi adapter.

Update the `BASE_URL` value in both:

- `MainActivity.kt`
- `OfflineUploadWorker.kt`

Use this format:

```kotlin
http://YOUR_COMPUTER_IP:8001
```

For example:

```kotlin
http://192.168.1.10:8001
```

The example address is illustrative; use your actual computer IP.

Keep the Android device and computer connected to the same Wi-Fi network. Ensure the firewall permits connections to port 8001.

## 7. API Endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/` | Backend information |
| GET | `/health` | Health check |
| POST | `/upload-image` | Upload an inspection image and run quality checks |
| POST | `/extract-nameplate/{frame_id}` | Extract nameplate information using OCR |
| POST | `/records` | Save an inspection record |
| GET | `/records` | Retrieve inspection records |
| GET | `/estimate-codes` | Retrieve restoration estimate codes |

## 8. Inspection Record Structure

Inspection records use a common JSON structure.

Each record contains:

- Job ID
- Capture type
- Capture timestamp
- Frame ID, where applicable
- Nameplate details, damage details, or technician note
- Field-level confidence values

Supported capture types:

- `nameplate`
- `damage`
- `note`

Example:

```json
{
  "job_id": "JOB-001",
  "capture_type": "nameplate",
  "captured_at": "2026-11-25T10:30:00",
  "frame_id": "example-frame-id",
  "nameplate": {
    "make": {
      "value": "LG",
      "confidence": 0.99
    },
    "model": {
      "value": "GR-B4T2Q",
      "confidence": 0.63
    },
    "serial_number": {
      "value": null,
      "confidence": 0.0
    },
    "manufacture_date": {
      "value": null,
      "confidence": 0.0
    }
  }
}
```

This is an illustrative example, not a claim that every field is successfully extracted.

## 9. Image Quality Checks

The backend uses OpenCV to assess image quality.

The current prototype checks:

- Blur using Laplacian variance.
- Brightness using average image intensity.
- Basic framing using image-edge activity.

The current configured thresholds are:

| Check | Threshold |
|---|---:|
| Blur | Laplacian variance below 100 |
| Low brightness | Average intensity below 30 |
| Image size | Maximum 10 MB |

These are prototype thresholds and may require calibration using a larger, labelled dataset.

The framing check is a basic heuristic; it does not perform semantic object detection.

## 10. Evaluation

The evaluation includes a set of 11 captured nameplate images.

The current OCR test results recorded:

| Metric | Result |
|---|---:|
| Nameplate images tested | 11 |
| Images passing quality checks | 9 |
| Images flagged for framing | 2 |
| Make detections | 1 |
| Model detections | 6 |
| Serial number detections | 2 |
| Manufacture date detections | 0 |

These are field-detection counts, not field-wise accuracy percentages. Accuracy requires manually verified ground-truth labels.

The detailed results and failure analysis are available in:

```text
evaluation/evaluation_report.md
```

The OCR test script is:

```text
evaluation/test_nameplates.py
```

Run it from the project root:

```powershell
.\backend\venv\Scripts\python.exe .\evaluation\test_nameplates.py
```

The test results are saved in:

```text
evaluation/nameplates/nameplate_ocr_results.json
```

## 11. Restoration Estimate Codes

The backend provides 15 prototype estimate line-item codes covering:

- Water extraction
- Structural drying
- Dehumidifier setup
- Air mover setup
- Moisture inspection
- Drywall removal and replacement
- Ceiling repair
- Flooring removal and replacement
- Surface cleaning
- Antimicrobial treatment
- Primer application and painting
- Equipment monitoring

These are self-defined prototype codes, not official Xactimate codes.

## 12. Meta Wearables SDK Research

The assignment includes research into Meta Wearables Device Access Toolkit (DAT) and Mock Device Kit.

The current application is an Android phone-based CameraX prototype. Meta DAT and Mock Device Kit have been researched but are not integrated into the application.

The research document covers the SDK, integration considerations, limitations, and proposed hardware changes:

```text
META_SDK_RESEARCH.md
```

## 13. Current Limitations

- Nameplate OCR can miss or incorrectly identify text due to image quality, label layout, and OCR recognition errors.
- Manufacture date extraction needs further improvement.
- Damage-field extraction needs more labelled validation data.
- Image framing validation currently uses a heuristic rather than an object-detection model.
- Offline upload recovery should be validated under repeated real network-disconnection scenarios.
- Meta glasses hardware integration is not implemented in this prototype.
- The evaluation dataset needs additional ground-truth labels to calculate reliable accuracy.

## 14. Future Improvements

Over the next two weeks, the following improvements are planned:

1. Integrate the Meta Wearables DAT SDK and test using Mock Device Kit.
2. Improve nameplate extraction using manufacturer-specific parsing and validation.
3. Improve damage classification and rough-size estimation.
4. Expand the evaluation dataset with labelled normal, low-light, damage, and nameplate images.
5. Calculate field-wise precision, recall, and accuracy against ground truth.
6. Improve offline synchronization, retry handling, and duplicate prevention.
7. Calibrate image-quality thresholds using a larger dataset.

## 15. AI Tool Disclosure

AI-assisted development tools were used during implementation and documentation.

The application code, backend integration, testing, and evaluation were developed and tested as part of this assignment. The current implementation limitations are documented above.

## 16. Repository

GitHub:

https://github.com/munavathpavan/FieldCapture


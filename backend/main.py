
from fastapi import FastAPI, UploadFile, File, HTTPException
from datetime import datetime
from pathlib import Path
import uuid

import cv2
import numpy as np


app = FastAPI(
    title="FieldCapture API",
    description="Backend API for hands-free field inspection",
    version="1.0.0"
)

# Folder for storing uploaded inspection images
UPLOAD_DIR = Path(__file__).parent / "uploads"
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

# Initial quality thresholds (to be calibrated using real frames)
BLUR_THRESHOLD = 100.0
DARKNESS_THRESHOLD = 50.0
MAX_FILE_SIZE = 10 * 1024 * 1024  # 10 MB


@app.get("/")
def home():
    return {
        "message": "FieldCapture API is running",
        "status": "success"
    }


@app.get("/health")
def health_check():
    return {
        "status": "healthy",
        "timestamp": datetime.now().isoformat()
    }


@app.post("/upload-image")
async def upload_image(file: UploadFile = File(...)):

    # Accept JPEG and PNG images
    allowed_types = ["image/jpeg", "image/png"]

    if file.content_type not in allowed_types:
        raise HTTPException(
            status_code=400,
            detail="Only JPEG and PNG images are supported"
        )

    image_bytes = await file.read()

    if not image_bytes:
        raise HTTPException(
            status_code=400,
            detail="Uploaded image is empty"
        )

    if len(image_bytes) > MAX_FILE_SIZE:
        raise HTTPException(
            status_code=413,
            detail="Image exceeds the 10 MB limit"
        )

    # Decode image using OpenCV
    image_array = np.frombuffer(image_bytes, np.uint8)
    image = cv2.imdecode(image_array, cv2.IMREAD_COLOR)

    if image is None:
        raise HTTPException(
            status_code=400,
            detail="Invalid or unreadable image"
        )

    # Calculate image quality measurements
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

    blur_score = float(
        cv2.Laplacian(gray, cv2.CV_64F).var()
    )

    brightness = float(np.mean(gray))

    height, width = gray.shape

    is_blurry = blur_score < BLUR_THRESHOLD
    is_dark = brightness < DARKNESS_THRESHOLD

    warnings = []

    if is_blurry:
        warnings.append("Image may be blurry. Please retake.")

    if is_dark:
        warnings.append("Image is too dark. Please improve lighting.")

    quality_passed = not is_blurry and not is_dark

    # Save original uploaded image
    frame_id = str(uuid.uuid4())

    extension = ".jpg" if file.content_type == "image/jpeg" else ".png"
    image_path = UPLOAD_DIR / f"{frame_id}{extension}"
    image_path.write_bytes(image_bytes)

    return {
        "frame_id": frame_id,
        "filename": file.filename,
        "saved_as": image_path.name,
        "uploaded_at": datetime.now().isoformat(),
        "image_dimensions": {
            "width": width,
            "height": height
        },
        "quality": {
            "blur_score": round(blur_score, 2),
            "brightness": round(brightness, 2),
            "is_blurry": is_blurry,
            "is_dark": is_dark,
            "quality_passed": quality_passed,
            "warnings": warnings
        }
    }


from fastapi import FastAPI, UploadFile, File, HTTPException
from datetime import datetime
from pathlib import Path
import uuid
import json
import re

import cv2
import numpy as np
import easyocr

from models import (
    InspectionRecord,
    FieldValue,
    NameplateDetails
)
from estimate_codes import ESTIMATE_CODES


# --------------------------------------------------
# APP CONFIGURATION
# --------------------------------------------------

app = FastAPI(
    title="FieldCapture API",
    description="Backend API for hands-free field capture",
    version="1.0.0"
)

BASE_DIR = Path(__file__).resolve().parent
UPLOAD_DIR = BASE_DIR / "uploads"
RECORDS_FILE = BASE_DIR / "inspection_records.json"

UPLOAD_DIR.mkdir(parents=True, exist_ok=True)

MAX_FILE_SIZE = 10 * 1024 * 1024
BLUR_THRESHOLD = 100
BRIGHTNESS_THRESHOLD = 30
FRAME_MARGIN_THRESHOLD = 0.05
FRAME_EDGE_THRESHOLD = 25

ocr_reader = None


# --------------------------------------------------
# OCR INITIALIZATION
# --------------------------------------------------

def get_ocr_reader():
    global ocr_reader

    if ocr_reader is None:
        ocr_reader = easyocr.Reader(["en"], gpu=False)

    return ocr_reader


# --------------------------------------------------
# HELPER FUNCTIONS
# --------------------------------------------------

def clean_extracted_value(value):
    if value is None:
        return None

    value = str(value).strip()
    value = value.strip(" :#-.,;")

    return value if value else None


def find_confidence(value, extracted_lines):
    if not value:
        return 0.0

    target = re.sub(r"[^A-Z0-9]", "", str(value).upper())

    for item in extracted_lines:
        line = re.sub(
            r"[^A-Z0-9]",
            "",
            item["text"].upper()
        )

        if target and target in line:
            return float(item["confidence"])

    return 0.0


# --------------------------------------------------
# NAMEPLATE OCR EXTRACTION
# --------------------------------------------------

def extract_nameplate_details(image_path):

    reader = get_ocr_reader()
    results = reader.readtext(str(image_path))

    extracted_lines = []

    for result in results:
        extracted_lines.append({
            "text": result[1].strip(),
            "confidence": round(float(result[2]), 4)
        })

    full_text = " ".join(
        item["text"] for item in extracted_lines
    )

    search_text = full_text.upper()

    make = None
    model = None
    serial_number = None
    manufacture_date = None

    make_confidence = 0.0
    model_confidence = 0.0
    serial_confidence = 0.0
    date_confidence = 0.0

    # 1. Manufacturer detection

    manufacturers = [
        ("LG ELECTRONICS", "LG"),
        ("SAMSUNG", "Samsung"),
        ("WHIRLPOOL", "Whirlpool"),
        ("PANASONIC", "Panasonic"),
        ("HISENSE", "Hisense"),
        ("HITACHI", "Hitachi"),
        ("MITACHI", "Hitachi"),
        ("BEKO", "Beko"),
        ("BOSCH", "Bosch"),
        ("BSH", "Bosch"),
        ("CARRIER", "Carrier"),
        ("TRANE", "Trane"),
        ("LENNOX", "Lennox"),
        ("GOODMAN", "Goodman"),
        ("DAIKIN", "Daikin"),
        ("MITSUBISHI", "Mitsubishi"),
        ("RHEEM", "Rheem"),
        ("YORK", "York"),
        ("RUUD", "Ruud"),
        ("BRYANT", "Bryant"),
        ("AMANA", "Amana"),
        ("FUJITSU", "Fujitsu"),
        ("VOLTAS", "Voltas"),
        ("GODREJ", "Godrej"),
        ("BLUE STAR", "Blue Star"),
        ("LG", "LG")
    ]

    for manufacturer, display_name in manufacturers:

        pattern = (
            r"(?<![A-Z0-9])"
            + re.escape(manufacturer)
            + r"(?![A-Z0-9])"
        )

        if re.search(pattern, search_text):
            make = display_name
            make_confidence = find_confidence(
                manufacturer, extracted_lines
            )
            break

    # 2. Model detection

    model_patterns = [
        r"\bMODEL\s*(?:NO\.?|NUMBER)?\s*[:#-]?\s*([A-Z0-9][A-Z0-9./_-]{3,})",
        r"\bHODEL\s*(?:NO\.?|NUMBER)?\s*[:#-]?\s*([A-Z0-9][A-Z0-9./_-]{3,})",
        r"\bMODELE\s*[:#-]?\s*([A-Z0-9][A-Z0-9./_-]{3,})"
    ]

    rejected = {
        "PRODUCT",
        "CODE",
        "NUMBER",
        "MODEL",
        "SERIAL",
        "REFRIGERATOR",
        "FREEZER"
    }

    for pattern in model_patterns:

        match = re.search(pattern, search_text)

        if match:
            candidate = clean_extracted_value(
                match.group(1)
            )

            if candidate and candidate.upper() not in rejected:
                model = candidate
                break

    # Beko labels sometimes show model before "Model:"

    if make == "Beko" and model is None:

        match = re.search(
            r"\bBEKO\s+([A-Z0-9][A-Z0-9./_-]{5,})",
            search_text
        )

        if match:
            candidate = match.group(1)

            if candidate.upper() not in rejected:
                model = candidate

    if model:
        model_confidence = find_confidence(
            model, extracted_lines
        )

    # 3. Serial number detection

    serial_patterns = [
        r"\bSERIAL\s*(?:NO\.?|NUMBER)\s*[:#-]?\s*([A-Z0-9/-]{6,})",
        r"\bSERIAL\s*[:#-]\s*([A-Z0-9/-]{6,})",
        r"\bS/N\s*[:#-]?\s*([A-Z0-9/-]{6,})",
        r"\bSINO\s*[:#-]?\s*([A-Z0-9/-]{6,})",
        r"\bSERLAL\s*(?:NO\.?|NA)\s*[:#-]?\s*([A-Z0-9/-]{6,})"
    ]

    for pattern in serial_patterns:

        match = re.search(pattern, search_text)

        if match:
            candidate = clean_extracted_value(
                match.group(1)
            )

            if (
                candidate
                and len(candidate) >= 6
                and any(char.isdigit() for char in candidate)
            ):
                serial_number = candidate
                break

    if serial_number:
        serial_confidence = find_confidence(
            serial_number, extracted_lines
        )

    # 4. Manufacture / production date detection

    date_label = re.search(
        r"PRODUCTION\s*DATE|MANUFACTURING\s*DATE|"
        r"MANUFACTURE\s*DATE|MANUFACTURED\s*DATE|"
        r"MFG\s*DATE|MFD\s*DATE|DATE\s*OF\s*MANUFACTURE",
        search_text
    )

    if date_label:

        date_section = search_text[
            date_label.end():date_label.end() + 100
        ]

        date_match = re.search(
            r"\b(?:19|20)\d{2}[./-]\d{1,2}(?:[./-]\d{1,2})?\b",
            date_section
        )

        if date_match:
            manufacture_date = date_match.group(0)

    if manufacture_date:
        date_confidence = find_confidence(
            manufacture_date, extracted_lines
        )

    # 5. Return extracted information

    return {
        "make": {
            "value": make,
            "confidence": round(float(make_confidence), 4)
        },
        "model": {
            "value": model,
            "confidence": round(float(model_confidence), 4)
        },
        "serial_number": {
            "value": serial_number,
            "confidence": round(float(serial_confidence), 4)
        },
        "manufacture_date": {
            "value": manufacture_date,
            "confidence": round(float(date_confidence), 4)
        },
        "raw_text": full_text,
        "detected_lines": extracted_lines
    }


# --------------------------------------------------
# SAVE INSPECTION RECORD
# --------------------------------------------------

def save_inspection_record(record):

    if RECORDS_FILE.exists():
        try:
            with open(RECORDS_FILE, "r", encoding="utf-8") as file:
                records = json.load(file)

            if not isinstance(records, list):
                records = []

        except (json.JSONDecodeError, OSError):
            records = []
    else:
        records = []

    records.append(record.model_dump(mode="json"))

    with open(RECORDS_FILE, "w", encoding="utf-8") as file:
        json.dump(records, file, indent=4, default=str)


# --------------------------------------------------
# ROOT AND HEALTH ENDPOINTS
# --------------------------------------------------

@app.get("/")
def home():
    return {
        "message": "Welcome to FieldCapture API",
        "status": "running"
    }


@app.get("/health")
def health():
    return {
        "status": "healthy",
        "timestamp": datetime.now().isoformat()
    }


# --------------------------------------------------
# IMAGE UPLOAD AND QUALITY CHECKS
# --------------------------------------------------

@app.post("/upload-image")
async def upload_image(file: UploadFile = File(...)):

    allowed_types = [
        "image/jpeg",
        "image/png"
    ]

    if file.content_type not in allowed_types:
        raise HTTPException(
            status_code=400,
            detail="Only JPEG and PNG images are allowed"
        )

    contents = await file.read()

    if not contents:
        raise HTTPException(
            status_code=400,
            detail="Uploaded file is empty"
        )

    if len(contents) > MAX_FILE_SIZE:
        raise HTTPException(
            status_code=413,
            detail="Image size exceeds 10 MB limit"
        )

    frame_id = str(uuid.uuid4())

    extension = (
        ".png"
        if file.content_type == "image/png"
        else ".jpg"
    )

    saved_name = frame_id + extension
    image_path = UPLOAD_DIR / saved_name

    with open(image_path, "wb") as image_file:
        image_file.write(contents)

    image_array = np.frombuffer(contents, np.uint8)
    image = cv2.imdecode(
        image_array,
        cv2.IMREAD_COLOR
    )

    if image is None:
        image_path.unlink(missing_ok=True)

        raise HTTPException(
            status_code=400,
            detail="Could not read uploaded image"
        )

    gray = cv2.cvtColor(
        image,
        cv2.COLOR_BGR2GRAY
    )

    blur_score = float(
        cv2.Laplacian(
            gray,
            cv2.CV_64F
        ).var()
    )

    brightness = float(np.mean(gray))

    height, width = gray.shape

    edges = cv2.Canny(gray, 100, 200)

    margin_height = max(
        1, int(height * FRAME_MARGIN_THRESHOLD)
    )

    margin_width = max(
        1, int(width * FRAME_MARGIN_THRESHOLD)
    )

    top_margin = float(
        np.mean(edges[:margin_height, :])
    )

    bottom_margin = float(
        np.mean(edges[-margin_height:, :])
    )

    left_margin = float(
        np.mean(edges[:, :margin_width])
    )

    right_margin = float(
        np.mean(edges[:, -margin_width:])
    )

    edge_activity = max(
        top_margin,
        bottom_margin,
        left_margin,
        right_margin
    )

    is_blurry = bool(blur_score < BLUR_THRESHOLD)
    is_dark = bool(brightness < BRIGHTNESS_THRESHOLD)

    is_badly_framed = bool(
        edge_activity > FRAME_EDGE_THRESHOLD
    )

    warnings = []

    if is_blurry:
        warnings.append("Image may be blurry. Please retake.")

    if is_dark:
        warnings.append("Image is too dark. Please improve lighting.")

    if is_badly_framed:
        warnings.append(
            "Possible framing issue. Check whether the subject is cut off."
        )

    quality_passed = bool(
        not is_blurry
        and not is_dark
        and not is_badly_framed
    )

    return {
        "frame_id": frame_id,
        "filename": file.filename,
        "saved_as": saved_name,
        "quality_passed": quality_passed,
        "warnings": warnings,
        "quality": {
            "blur_score": round(blur_score, 2),
            "brightness": round(brightness, 2),
            "edge_activity": round(edge_activity, 2),
            "is_blurry": is_blurry,
            "is_dark": is_dark,
            "is_badly_framed": is_badly_framed
        }
    }


# --------------------------------------------------
# NAMEPLATE EXTRACTION ENDPOINT
# --------------------------------------------------

@app.post("/extract-nameplate/{frame_id}")
def extract_nameplate(frame_id: str):

    if not re.fullmatch(r"[a-fA-F0-9-]{36}", frame_id):
        raise HTTPException(
            status_code=400,
            detail="Invalid frame ID"
        )

    matching_files = list(
        UPLOAD_DIR.glob(frame_id + ".*")
    )

    if not matching_files:
        raise HTTPException(
            status_code=404,
            detail="Image not found"
        )

    image_path = matching_files[0]

    try:
        extracted = extract_nameplate_details(image_path)

    except Exception as error:
        raise HTTPException(
            status_code=500,
            detail=f"OCR extraction failed: {str(error)}"
        )

    nameplate_details = NameplateDetails(
        make=FieldValue(**extracted["make"]),
        model=FieldValue(**extracted["model"]),
        serial_number=FieldValue(**extracted["serial_number"]),
        manufacture_date=FieldValue(**extracted["manufacture_date"])
    )

    record = InspectionRecord(
        job_id="JOB-001",
        capture_type="nameplate",
        captured_at=datetime.now(),
        frame_id=frame_id,
        nameplate=nameplate_details
    )

    save_inspection_record(record)

    return {
        "frame_id": frame_id,
        "capture_type": "nameplate",
        "extracted_at": datetime.now().isoformat(),
        "nameplate": extracted
    }


# --------------------------------------------------
# INSPECTION RECORD ENDPOINTS
# --------------------------------------------------

@app.post("/records")
def create_record(record: InspectionRecord):

    save_inspection_record(record)

    return {
        "message": "Inspection record saved successfully",
        "record": record.model_dump(mode="json")
    }


@app.get("/records")
def get_records():

    if not RECORDS_FILE.exists():
        return {"records": []}

    try:
        with open(RECORDS_FILE, "r", encoding="utf-8") as file:
            records = json.load(file)

        return {"records": records}

    except (json.JSONDecodeError, OSError):
        return {"records": []}


# --------------------------------------------------
# ESTIMATE CODES ENDPOINT
# --------------------------------------------------

@app.get("/estimate-codes")
def get_estimate_codes():

    return {
        "total_codes": len(ESTIMATE_CODES),
        "estimate_codes": ESTIMATE_CODES
    }

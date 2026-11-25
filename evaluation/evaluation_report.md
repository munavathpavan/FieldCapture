# FieldCapture – Evaluation Report

## 1. Overview

FieldCapture was evaluated to assess image quality validation, appliance nameplate OCR extraction, damage information extraction, and inspection record storage.

Testing used genuine appliance nameplate images captured during development.

## 2. Nameplate OCR Evaluation

- Total nameplate images tested: 11
- Images passing image quality checks: 9
- Images flagged for framing issues: 2

### Field Detection Results

| Field | Images with detected values |
|---|---:|
| Make | 1/11 |
| Model | 6/11 |
| Serial number | 2/11 |
| Manufacture date | 0/11 |

**Note:** These are detection counts, not accuracy percentages. Extracted values require manual comparison against verified nameplate labels before accuracy can be calculated.

## 3. Image Quality Evaluation

The quality validation system checks:

- Blur using Laplacian variance.
- Brightness using average image intensity.
- Possible framing issues using image-border edge activity.

In the 11-image nameplate test:
- 9 images passed the quality checks.
- 2 images were flagged for possible framing issues.
- No images were flagged as blurry or too dark.

The thresholds are preliminary and require further calibration using a larger dataset.

## 4. Observed Extraction Failures

1. LG refrigerator: Manufacturer was not extracted despite LG appearing in the OCR text.
2. LG refrigerator: Incorrect model value `MMODELE` was extracted instead of `LFXS32766S`.
3. Beko washing machine: The value `Product` was extracted instead of model `WUESS11XWW`.
4. Panasonic washing machine: Model `NA-128VKS` and serial number `590014` were present in OCR text but not extracted.
5. Hitachi air conditioner: Manufacturer and serial number were not extracted correctly despite possible values appearing in OCR text.

### Possible Causes

- OCR character recognition errors.
- Inconsistent nameplate layouts.
- Incorrect association between labels and values.
- Regex-based extraction limitations.
- Image orientation and text quality.

## 5. Damage Information Evaluation

The application supports extraction of:
- Room
- Surface
- Damage type
- Rough size

Further manually labelled validation is required to calculate field-wise accuracy.

## 6. Inspection Records

The backend supports structured JSON inspection records containing:
- Job ID
- Capture type
- Capture timestamp
- Frame ID
- Extracted fields
- Confidence values

Records are stored and retrieved through the FastAPI backend.

## 7. Limitations

- OCR extraction is not fully reliable across different manufacturers.
- Manufacture date extraction needs improvement.
- Quality checks use basic image-processing heuristics.
- Framing detection may produce false positives.
- Damage extraction needs further ground-truth validation.
- Actual Meta glasses integration remains future work.

## 8. Future Improvements

1. Improve OCR preprocessing and field extraction.
2. Add manufacturer-specific extraction patterns.
3. Calibrate image quality thresholds.
4. Improve damage extraction and size estimation.
5. Validate all extracted fields against manually labelled data.
6. Integrate and test Meta DAT SDK using the Mock Device Kit.

## 9. Conclusion

The prototype demonstrates an end-to-end workflow for image capture, quality validation, OCR processing, voice-assisted inspection, and structured record storage.

The initial nameplate evaluation identified limitations in field extraction, providing clear areas for improvement. Further ground-truth validation is necessary before reporting extraction accuracy.

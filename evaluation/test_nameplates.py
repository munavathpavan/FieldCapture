
import requests
import json
from pathlib import Path

IMAGE_FOLDER = Path(r"C:\Users\HP\FieldCapture\evaluation\nameplates")
API_URL = "http://127.0.0.1:8001"

results = []

image_files = [
    file for file in IMAGE_FOLDER.iterdir()
    if file.suffix.lower() in [".jpg", ".jpeg", ".png"]
]

print(f"Found {len(image_files)} images")

for image_path in image_files:

    print(f"\nTesting: {image_path.name}")

    try:
        with open(image_path, "rb") as image_file:

            upload_response = requests.post(
                f"{API_URL}/upload-image",
                files={
                    "file": (
                        image_path.name,
                        image_file,
                        "image/png" if image_path.suffix.lower() == ".png"
                        else "image/jpeg"
                    )
                }
            )

        upload_response.raise_for_status()
        upload_data = upload_response.json()

        frame_id = upload_data["frame_id"]

        ocr_response = requests.post(
            f"{API_URL}/extract-nameplate/{frame_id}"
        )

        ocr_response.raise_for_status()
        ocr_data = ocr_response.json()

        results.append({
            "filename": image_path.name,
            "frame_id": frame_id,
            "quality_passed": upload_data["quality_passed"],
            "quality": upload_data["quality"],
            "nameplate": ocr_data["nameplate"],
            "record_saved": ocr_data["record_saved"],
            "raw_text": ocr_data["raw_text"]
        })

        print("OCR completed")
        print("Extracted:", ocr_data["nameplate"])

    except Exception as error:

        print("Error:", str(error))

        results.append({
            "filename": image_path.name,
            "error": str(error)
        })

output_file = IMAGE_FOLDER / "nameplate_ocr_results.json"

with open(output_file, "w", encoding="utf-8") as output:
    json.dump(results, output, indent=4, ensure_ascii=False)

print("\n--------------------------------")
print("TESTING COMPLETED")
print(f"Images tested: {len(image_files)}")
print(f"Results saved to: {output_file}")

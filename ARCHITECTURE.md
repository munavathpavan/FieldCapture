flowchart TD
    A[Android App] --> B[CameraX Capture]
    A --> C[Voice Commands]
    A --> D[Technician Notes]

    B --> E[Image Quality Checks]
    E -->|Accepted image| F[HTTP Upload]
    E -->|Poor quality| G[Retake Prompt]

    F --> H[FastAPI Backend]
    H --> I[OpenCV Quality Validation]
    H --> J[EasyOCR Extraction]

    J --> K[Nameplate Details]
    J --> L[Damage Details]

    K --> M[Inspection Records]
    L --> M
    D --> M

    M --> N[JSON Storage]
    H --> O[Estimate Codes API]

    A --> P[WorkManager Offline Queue]
    P -->|Connection restored| F

    Q[Meta DAT SDK]
    R[Mock Device Kit]
    Q --> A
    R --> Q
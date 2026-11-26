\# Meta Wearables SDK Research – FieldCapture



\## 1. Objective



The objective is to investigate how Meta wearable glasses can support hands-free property inspections through the FieldCapture Android application.



\## 2. SDK and Tools



\- Meta Wearables Device Access Toolkit (DAT)

\- Meta Wearables Mock Device Kit

\- Android Studio

\- Kotlin

\- Android CameraX



\## 3. Integration Approach



The Android application uses the Meta DAT SDK to establish a connection with compatible Meta glasses.



The Mock Device Kit is used during development to simulate glasses functionality without requiring a physical device.



The application initializes the mock device kit, enables it, and attempts to pair the simulated glasses.



\## 4. Application Workflow



1\. Initialize the Meta Wearables SDK.

2\. Enable the Mock Device Kit.

3\. Pair the simulated Ray-Ban Meta glasses.

4\. Establish the DAT session.

5\. Access supported camera functionality.

6\. Capture inspection images.

7\. Send captured images to the FieldCapture backend.

8\. Process images and store inspection information.



\## 5. Development and Testing



The integration was developed using Kotlin in the Android application.



The project uses the Mock Device Kit for development and testing. Android build and installation were verified during implementation.



\## 6. Limitations



\- Mock Device Kit testing does not establish compatibility with every physical glasses model.

\- Physical-device testing is required to validate the complete glasses workflow.

\- Camera streaming and capture behavior may depend on SDK capabilities and device permissions.

\- Network connectivity is required for immediate backend uploads; offline upload handling is implemented separately.



\## 7. Future Improvements



\- Test with physical Meta glasses.

\- Validate camera capture and streaming reliability.

\- Improve reconnection and session error handling.

\- Test longer inspection sessions.

\- Improve offline synchronization and upload recovery.



\## 8. Conclusion



The project establishes a development-level integration path between the FieldCapture Android application and Meta Wearables DAT using the Mock Device Kit. Physical-device testing remains necessary before claiming complete production readiness.


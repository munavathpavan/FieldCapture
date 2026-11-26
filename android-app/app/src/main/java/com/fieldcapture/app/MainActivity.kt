
package com.fieldcapture.app
import com.meta.wearable.dat.mockdevice.api.camera.CameraFacing
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.meta.wearable.dat.camera.Camera
import com.meta.wearable.dat.camera.addCamera
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.mockdevice.MockDeviceKit
import com.meta.wearable.dat.mockdevice.api.GlassesModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.time.Instant
import java.util.Locale

private const val TAG = "FieldCapture"
private const val BASE_URL = "http://10.167.230.182:8001"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                FieldCaptureScreen()
            }
        }
    }
}

@Composable
fun FieldCaptureScreen() {

    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scope = rememberCoroutineScope()

    val mockDeviceKit = remember(context) {
        MockDeviceKit.getInstance(context)
    }

    var mockGlassesPaired by remember {
        mutableStateOf(false)
    }

    var datSession by remember {
        mutableStateOf<DeviceSession?>(null)
    }

    var datCamera by remember {
        mutableStateOf<Camera?>(null)
    }

    var datSessionState by remember {
        mutableStateOf("Not Started")
    }

    var datStreamState by remember {
        mutableStateOf("Not Started")
    }

    var datFrameCount by remember {
        mutableStateOf(0)
    }

    var cameraAttachInProgress by remember {
        mutableStateOf(false)
    }

    var inspectionPaused by remember {
        mutableStateOf(false)
    }

    var sessionStarted by remember {
        mutableStateOf(false)
    }

    var sessionStateJob by remember {
        mutableStateOf<Job?>(null)
    }

    var sessionErrorJob by remember {
        mutableStateOf<Job?>(null)
    }

    var streamStateJob by remember {
        mutableStateOf<Job?>(null)
    }

    var streamErrorJob by remember {
        mutableStateOf<Job?>(null)
    }

    var noteText by remember {
        mutableStateOf("")
    }

    var voiceCommand by remember {
        mutableStateOf("")
    }

    var lastRecognizedCommand by remember {
        mutableStateOf("")
    }

    var isListening by remember {
        mutableStateOf(false)
    }

    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Retained for the optional phone camera preview.
    var imageCapture by remember {
        mutableStateOf<ImageCapture?>(null)
    }

    var statusMessage by remember {
        mutableStateOf("Ready to start inspection.")
    }

    val client = remember {
        OkHttpClient()
    }

    // ------------------------------------------------
    // MOCK GLASSES PAIRING
    // ------------------------------------------------

    fun pairMockGlasses() {

        if (mockGlassesPaired) {
            statusMessage = "Mock glasses are already paired."
            return
        }

        try {
            statusMessage = "Enabling Meta Mock Device Kit..."

            mockDeviceKit.enable()

            mockDeviceKit.pairGlasses(
                GlassesModel.RAYBAN_META
            ).fold(

                onSuccess = { device ->
                    device.services.camera.setCameraFeed(CameraFacing.BACK)
                    device.powerOn()
                    device.unfold()
                    device.don()

                    mockGlassesPaired = true

                    Log.d(TAG, "Mock glasses paired successfully")

                    statusMessage =
                        "Mock Ray-Ban Meta glasses paired successfully."
                },

                onFailure = { error, _ ->

                    Log.e(TAG, "Mock pairing failed: ${error.description}")

                    statusMessage =
                        "Mock pairing failed: ${error.description}"
                }
            )

        } catch (e: Exception) {

            Log.e(TAG, "Mock device exception", e)

            statusMessage = "Mock device error: ${e.message}"
        }
    }

    // ------------------------------------------------
    // ATTACH DAT CAMERA
    // ------------------------------------------------

    fun attachDatCamera(session: DeviceSession) {

        if (datCamera != null || cameraAttachInProgress) {
            return
        }

        cameraAttachInProgress = true
        statusMessage = "Attaching Meta glasses camera..."

        session.addCamera(
            StreamConfiguration(
                videoQuality = VideoQuality.MEDIUM,
                frameRate = 15
            )
        ).fold(

            onSuccess = { camera ->

                datCamera = camera
                cameraAttachInProgress = false

                Log.d(TAG, "DAT camera attached successfully")

                streamStateJob = scope.launch {

                    camera.stream.state.collect { state ->

                        datStreamState = state.name

                        Log.d(TAG, "DAT stream state: ${state.name}")

                        statusMessage = when (state.name) {
                            "STREAMING" ->
                                "Meta glasses camera streaming successfully."

                            "PAUSED" ->
                                "Glasses camera stream paused."

                            "STOPPED" ->
                                "Glasses camera stream stopped."

                            "CLOSED" ->
                                "Glasses camera stream closed."

                            else ->
                                "DAT camera stream state: ${state.name}"
                        }
                    }
                }

                streamErrorJob = scope.launch {

                    camera.stream.errorStream.collect { error ->

                        Log.e(
                            TAG,
                            "DAT stream error: ${error.description}"
                        )

                        statusMessage =
                            "Glasses camera error: ${error.description}"
                    }
                }

                scope.launch {

                    camera.stream.videoStream.collect {

                        datFrameCount++

                        if (datFrameCount == 1 ||
                            datFrameCount % 30 == 0
                        ) {
                            Log.d(
                                TAG,
                                "DAT video frames received: $datFrameCount"
                            )
                        }
                    }
                }

                Log.d(TAG, "Starting DAT camera stream")

                camera.stream.start().fold(

                    onSuccess = {
                        statusMessage =
                            "DAT stream start requested. Waiting for camera..."
                    },

                    onFailure = { error, _ ->

                        Log.e(
                            TAG,
                            "DAT stream start failed: ${error.description}"
                        )

                        statusMessage =
                            "Glasses camera stream failed: ${error.description}"
                    }
                )
            },

            onFailure = { error, _ ->

                cameraAttachInProgress = false

                Log.e(
                    TAG,
                    "DAT camera attachment failed: ${error.description}"
                )

                statusMessage =
                    "Camera attachment failed: ${error.description}"
            }
        )
    }

    // ------------------------------------------------
    // START DAT SESSION
    // ------------------------------------------------

    fun startDatSession() {
        Log.d(TAG, "START DAT SESSION BUTTON CLICKED")

        if (!mockGlassesPaired) {
            statusMessage = "Please pair mock glasses first."
            return
        }

        if (datSession != null) {
            statusMessage = "A DAT session already exists."
            return
        }

        statusMessage = "Creating DAT session..."

        Wearables.createSession(
            AutoDeviceSelector()
        ).fold(

            onSuccess = { session ->

                datSession = session
                inspectionPaused = false

                sessionStateJob = scope.launch {

                    session.state.collect { state ->

                        datSessionState = state.name

                        Log.d(TAG, "DAT session state: ${state.name}")

                        when (state) {

                            DeviceSessionState.STARTED -> {

                                sessionStarted = !inspectionPaused

                                statusMessage = "DAT session started."

                                attachDatCamera(session)
                            }

                            DeviceSessionState.PAUSED -> {

                                sessionStarted = false
                                statusMessage = "Glasses session paused."
                            }

                            DeviceSessionState.STOPPING -> {

                                sessionStarted = false
                                statusMessage = "DAT session is stopping..."
                            }

                            DeviceSessionState.STOPPED -> {

                                sessionStarted = false
                                inspectionPaused = false

                                datCamera = null
                                datSession = null

                                datStreamState = "Not Started"
                                datFrameCount = 0
                                cameraAttachInProgress = false

                                streamStateJob?.cancel()
                                streamErrorJob?.cancel()
                                sessionStateJob?.cancel()
                                sessionErrorJob?.cancel()

                                statusMessage = "DAT session stopped."
                            }

                            else -> {
                                statusMessage =
                                    "DAT session state: ${state.name}"
                            }
                        }
                    }
                }

                sessionErrorJob = scope.launch {

                    session.errors.collect { error ->

                        sessionStarted = false

                        Log.e(
                            TAG,
                            "DAT session error: ${error.description}"
                        )

                        statusMessage =
                            "DAT session error: ${error.description}"
                    }
                }

                session.start()

                statusMessage =
                    "DAT session connection requested..."
            },

            onFailure = { error, _ ->

                Log.e(
                    TAG,
                    "Session creation failed: ${error.description}"
                )

                statusMessage =
                    "DAT session creation failed: ${error.description}"
            }
        )
    }

    // ------------------------------------------------
    // PAUSE INSPECTION
    // ------------------------------------------------

    fun pauseInspection() {

        if (datSession == null) {
            statusMessage = "No DAT session is active."
            return
        }

        inspectionPaused = true
        sessionStarted = false

        statusMessage =
            "Inspection paused. DAT session remains connected."
    }

    // ------------------------------------------------
    // RESUME INSPECTION
    // ------------------------------------------------

    fun resumeInspection() {

        if (datSession == null) {
            statusMessage = "Please start a DAT session first."
            return
        }

        if (datSessionState != DeviceSessionState.STARTED.name) {
            statusMessage =
                "DAT session is not ready. Current state: $datSessionState"
            return
        }

        inspectionPaused = false
        sessionStarted = true

        statusMessage = "Inspection resumed."
    }

    // ------------------------------------------------
    // STOP DAT SESSION
    // ------------------------------------------------

    fun stopDatSession() {

        val currentSession = datSession

        if (currentSession == null) {
            statusMessage = "No DAT session is active."
            return
        }

        sessionStarted = false
        inspectionPaused = false

        statusMessage = "Stopping DAT session..."

        currentSession.stop()
    }

    // ------------------------------------------------
    // VOICE RECOGNITION
    // ------------------------------------------------

    val voiceLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            isListening = false

            if (result.resultCode == android.app.Activity.RESULT_OK) {

                val spokenText = result.data
                    ?.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS
                    )
                    ?.firstOrNull()

                if (!spokenText.isNullOrBlank()) {

                    lastRecognizedCommand = spokenText
                    voiceCommand = spokenText

                    statusMessage =
                        "Voice command detected: $spokenText"

                } else {

                    statusMessage = "No speech detected. Please try again."
                }

            } else {

                statusMessage = "Voice recognition cancelled."
            }
        }

    fun launchSpeechRecognition() {

        try {

            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.getDefault()
                )

                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Speak a FieldCapture command"
                )
            }

            isListening = true
            voiceLauncher.launch(intent)

        } catch (e: Exception) {

            isListening = false

            statusMessage =
                "Voice recognition unavailable: ${e.message}"
        }
    }

    // ------------------------------------------------
    // CAMERA PERMISSION
    // ------------------------------------------------

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            cameraPermissionGranted = granted

            statusMessage = if (granted) {
                "Phone camera permission granted."
            } else {
                "Phone camera permission denied."
            }
        }

    // ------------------------------------------------
    // MICROPHONE PERMISSION
    // ------------------------------------------------

    val microphonePermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                launchSpeechRecognition()
            } else {
                statusMessage =
                    "Microphone permission denied."
            }
        }

    // ------------------------------------------------
    // CREATE CONFIDENCE FIELD
    // ------------------------------------------------

    fun createFieldValue(value: String): JSONObject {

        return JSONObject().apply {

            if (value.isBlank()) {
                put("value", JSONObject.NULL)
                put("confidence", 0.0)
            } else {
                put("value", value.trim())
                put("confidence", 1.0)
            }
        }
    }

    // ------------------------------------------------
    // SAVE TECHNICIAN NOTE
    // ------------------------------------------------

    fun saveTechnicianNote() {

        val currentNote = noteText.trim()

        if (currentNote.isEmpty()) {
            statusMessage = "Please enter a technician note."
            return
        }

        statusMessage = "Saving technician note..."

        val noteObject = createFieldValue(currentNote)

        val json = JSONObject().apply {

            put("job_id", "JOB-${System.currentTimeMillis()}")
            put("capture_type", "note")
            put("captured_at", Instant.now().toString())
            put("frame_id", JSONObject.NULL)
            put("nameplate", JSONObject.NULL)
            put("damage", JSONObject.NULL)
            put("note", noteObject)
        }

        val requestBody = json.toString().toRequestBody(
            "application/json; charset=utf-8".toMediaType()
        )

        val request = Request.Builder()
            .url("$BASE_URL/records")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(
            object : okhttp3.Callback {

                override fun onFailure(
                    call: okhttp3.Call,
                    e: IOException
                ) {
                    Handler(Looper.getMainLooper()).post {
                        statusMessage =
                            "Note saving failed: ${e.message}"
                    }
                }

                override fun onResponse(
                    call: okhttp3.Call,
                    response: okhttp3.Response
                ) {
                    response.use { result ->

                        val body = result.body?.string().orEmpty()

                        Handler(Looper.getMainLooper()).post {

                            if (result.isSuccessful) {

                                noteText = ""
                                statusMessage =
                                    "Technician note saved successfully!"

                            } else {

                                statusMessage =
                                    "Note saving failed: HTTP ${result.code}. $body"
                            }
                        }
                    }
                }
            }
        )
    }

    // ------------------------------------------------
    // ADD PHOTO TO OFFLINE UPLOAD QUEUE
    // ------------------------------------------------

    fun enqueuePhotoFile(
        photoFile: File,
        captureType: String,
        room: String,
        surface: String
    ) {

        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        val inputData =
            Data.Builder()
                .putString(
                    OfflineUploadWorker.KEY_FILE_PATH,
                    photoFile.absolutePath
                )
                .putString(
                    OfflineUploadWorker.KEY_CAPTURE_TYPE,
                    captureType
                )
                .putString(
                    OfflineUploadWorker.KEY_ROOM,
                    room
                )
                .putString(
                    OfflineUploadWorker.KEY_SURFACE,
                    surface
                )
                .build()

        val uploadRequest =
            OneTimeWorkRequestBuilder<OfflineUploadWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .build()

        WorkManager.getInstance(context).enqueue(uploadRequest)

        statusMessage =
            "Glasses photo saved locally and added to upload queue."
    }

    // ------------------------------------------------
    // CAPTURE PHOTO USING META GLASSES
    // ------------------------------------------------

    fun capturePhoto(
        captureType: String,
        room: String = "",
        surface: String = ""
    ) {

        if (!sessionStarted || inspectionPaused) {
            statusMessage =
                "Please start or resume the inspection first."
            return
        }

        val camera = datCamera

        if (camera == null) {
            statusMessage =
                "Meta glasses camera is not attached."
            return
        }

        if (datStreamState != "STREAMING") {
            statusMessage =
                "Glasses camera is not streaming. Current state: $datStreamState"
            return
        }

        val queueDirectory =
            File(context.filesDir, "offline_queue")

        if (!queueDirectory.exists() &&
            !queueDirectory.mkdirs()
        ) {
            statusMessage =
                "Could not create local photo queue."
            return
        }

        statusMessage =
            "Capturing $captureType photo from Meta glasses..."

        scope.launch {

            try {

                camera.stream.capturePhoto().fold(

                    onSuccess = { photoData ->

                        try {

                            val photoFile = File(
                                queueDirectory,
                                "fieldcapture_${System.currentTimeMillis()}.jpg"
                            )

                            when (photoData) {

                                is PhotoData.Bitmap -> {

                                    FileOutputStream(photoFile).use { output ->

                                        val saved = photoData.bitmap.compress(
                                            android.graphics.Bitmap.CompressFormat.JPEG,
                                            95,
                                            output
                                        )

                                        if (!saved) {
                                            throw IOException(
                                                "Could not encode captured bitmap."
                                            )
                                        }
                                    }
                                }

                                is PhotoData.HEIC -> {

                                    // Preserve the original HEIC bytes.
                                    // Use a .heic extension for this image.

                                    val heicFile = File(
                                        queueDirectory,
                                        "fieldcapture_${System.currentTimeMillis()}.heic"
                                    )

                                    val buffer: ByteBuffer =
                                        photoData.data.duplicate()

                                    val bytes = ByteArray(buffer.remaining())
                                    buffer.get(bytes)

                                    FileOutputStream(heicFile).use { output ->
                                        output.write(bytes)
                                    }

                                    enqueuePhotoFile(
                                        heicFile,
                                        captureType,
                                        room,
                                        surface
                                    )

                                    Log.d(
                                        TAG,
                                        "Glasses HEIC photo saved: ${heicFile.absolutePath}"
                                    )

                                    return@fold
                                }
                            }

                            Log.d(
                                TAG,
                                "Glasses photo saved: ${photoFile.absolutePath}"
                            )

                            enqueuePhotoFile(
                                photoFile,
                                captureType,
                                room,
                                surface
                            )

                        } catch (e: Exception) {

                            Log.e(TAG, "Saving glasses photo failed", e)

                            statusMessage =
                                "Photo save failed: ${e.message}"
                        }
                    },

                    onFailure = { error, _ ->

                        Log.e(
                            TAG,
                            "DAT photo capture failed: ${error.description}"
                        )

                        statusMessage =
                            "Glasses photo capture failed: ${error.description}"
                    }
                )

            } catch (e: Exception) {

                Log.e(TAG, "DAT capture exception", e)

                statusMessage =
                    "Capture error: ${e.message}"
            }
        }
    }

    // ------------------------------------------------
    // PROCESS VOICE COMMANDS
    // ------------------------------------------------

    LaunchedEffect(voiceCommand) {

        val command = voiceCommand.trim()

        if (command.isBlank()) {
            return@LaunchedEffect
        }

        voiceCommand = ""

        if (!sessionStarted || inspectionPaused) {
            statusMessage =
                "Please start or resume the inspection first."
            return@LaunchedEffect
        }

        val normalizedCommand =
            command.lowercase(Locale.ROOT)

        when {

            normalizedCommand.startsWith("capture nameplate") -> {

                capturePhoto("nameplate")
            }

            normalizedCommand.startsWith("capture damage") -> {

                val details = command
                    .substringAfter("capture damage", "")
                    .trim()

                val words = details
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }

                if (words.size >= 2) {

                    val surface = words.last()

                    val room = words
                        .dropLast(1)
                        .joinToString(" ")

                    capturePhoto(
                        "damage",
                        room,
                        surface
                    )

                } else {

                    statusMessage =
                        "Say: Capture damage [room] [surface]."
                }
            }

            normalizedCommand.startsWith("note:") -> {

                val spokenNote = command
                    .substringAfter(":", "")
                    .trim()

                if (spokenNote.isNotBlank()) {
                    noteText = spokenNote
                    saveTechnicianNote()
                } else {
                    statusMessage =
                        "Please speak the note after the word note."
                }
            }

            else -> {
                statusMessage =
                    "Command not recognized: $command"
            }
        }
    }

    // ------------------------------------------------
    // USER INTERFACE
    // ------------------------------------------------

    Scaffold { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),

            verticalArrangement =
                Arrangement.spacedBy(16.dp)
        ) {

            Text(
                "FieldCapture",
                fontSize = 30.sp,
                style = MaterialTheme.typography.headlineMedium
            )

            Text("Hands-Free Field Inspection")

            // DEVICE STATUS

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Text("DEVICE STATUS")

                    Text(
                        if (mockGlassesPaired) {
                            "Mock Glasses: Connected"
                        } else {
                            "Mock Glasses: Not Connected"
                        }
                    )

                    Button(
                        onClick = { pairMockGlasses() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !mockGlassesPaired
                    ) {
                        Text(
                            if (mockGlassesPaired) {
                                "Mock Glasses Paired"
                            } else {
                                "Pair Mock Ray-Ban Meta Glasses"
                            }
                        )
                    }

                    Text(
                        if (cameraPermissionGranted) {
                            "Phone Camera: Permission Granted"
                        } else {
                            "Phone Camera: Permission Required"
                        }
                    )
                }
            }

            // DAT SESSION STATUS

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {

                    Text("DAT DEVICE SESSION")

                    Text("DAT State: $datSessionState")
                    Text("Glasses Camera Stream: $datStreamState")
                    Text("DAT Video Frames: $datFrameCount")

                    Text(
                        when {
                            sessionStarted -> "Inspection: Active"
                            inspectionPaused -> "Inspection: Paused"
                            else -> "Inspection: Inactive"
                        }
                    )

                    Button(
                        onClick = { startDatSession() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled =
                            mockGlassesPaired && datSession == null
                    ) {
                        Text("Start DAT Session")
                    }

                    Button(
                        onClick = { pauseInspection() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled =
                            sessionStarted && datSession != null
                    ) {
                        Text("Pause Inspection")
                    }

                    Button(
                        onClick = { resumeInspection() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled =
                            inspectionPaused && datSession != null
                    ) {
                        Text("Resume Inspection")
                    }

                    Button(
                        onClick = { stopDatSession() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = datSession != null
                    ) {
                        Text("Stop DAT Session")
                    }
                }
            }

            // PHONE CAMERA PREVIEW
            // This is only a preview; photo capture uses Meta glasses.

            Text(
                "PHONE CAMERA PREVIEW",
                style = MaterialTheme.typography.titleMedium
            )

            if (cameraPermissionGranted) {

                AndroidView(
                    factory = { ctx ->

                        val previewView = PreviewView(ctx)

                        val cameraProviderFuture =
                            ProcessCameraProvider.getInstance(ctx)

                        cameraProviderFuture.addListener({

                            try {

                                val cameraProvider =
                                    cameraProviderFuture.get()

                                val preview =
                                    Preview.Builder().build()

                                preview.surfaceProvider =
                                    previewView.surfaceProvider

                                val capture =
                                    ImageCapture.Builder()
                                        .setCaptureMode(
                                            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                                        )
                                        .build()

                                cameraProvider.unbindAll()

                                cameraProvider.bindToLifecycle(
                                    activity,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    capture
                                )

                                imageCapture = capture

                            } catch (e: Exception) {

                                Log.e(TAG, "Phone camera setup failed", e)

                                statusMessage =
                                    "Phone camera setup failed: ${e.message}"
                            }

                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )

            } else {

                Button(
                    onClick = {
                        permissionLauncher.launch(
                            Manifest.permission.CAMERA
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Phone Camera Permission")
                }
            }

            // VOICE COMMANDS

            Text(
                "VOICE COMMANDS",
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = {

                    if (
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        launchSpeechRecognition()
                    } else {
                        microphonePermissionLauncher.launch(
                            Manifest.permission.RECORD_AUDIO
                        )
                    }
                },

                modifier = Modifier.fillMaxWidth(),

                enabled = sessionStarted && !isListening
            ) {
                Text(
                    if (isListening) {
                        "Listening..."
                    } else {
                        "Start Voice Command"
                    }
                )
            }

            if (lastRecognizedCommand.isNotBlank()) {

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        Text("RECOGNIZED VOICE COMMAND")
                        Text(lastRecognizedCommand)
                    }
                }
            }

            // CAPTURE ACTIONS

            Text(
                "CAPTURE ACTIONS",
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = {
                    capturePhoto("nameplate")
                },
                modifier = Modifier.fillMaxWidth(),
                enabled =
                    sessionStarted && datStreamState == "STREAMING"
            ) {
                Text("Capture Nameplate (Glasses)")
            }

            Button(
                onClick = {
                    capturePhoto("damage")
                },
                modifier = Modifier.fillMaxWidth(),
                enabled =
                    sessionStarted && datStreamState == "STREAMING"
            ) {
                Text("Capture Damage (Glasses)")
            }

            // TECHNICIAN NOTES

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    Text(
                        "TECHNICIAN NOTES",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = {
                            Text("Enter technician note")
                        },
                        placeholder = {
                            Text(
                                "Example: Water leakage near kitchen ceiling."
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        enabled = sessionStarted
                    )

                    Button(
                        onClick = { saveTechnicianNote() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled =
                            sessionStarted && noteText.isNotBlank()
                    ) {
                        Text("Save Technician Note")
                    }
                }
            }

            // STATUS

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Text("STATUS")
                    Text(statusMessage)
                }
            }

            Text(
                "FieldCapture v1.0 | Development Build",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

package com.fieldcapture.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
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
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.Locale

private const val BASE_URL = "http://10.167.230.182:8001"


// ----------------------------------------------------
// MAIN ACTIVITY
// ----------------------------------------------------

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


// ----------------------------------------------------
// FIELDCAPTURE SCREEN
// ----------------------------------------------------

@Composable
fun FieldCaptureScreen() {

    val context = LocalContext.current
    val activity = context as ComponentActivity

    var sessionStarted by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    var voiceCommand by remember { mutableStateOf("") }
    var lastRecognizedCommand by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    var cameraPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var imageCapture by remember {
        mutableStateOf<ImageCapture?>(null)
    }

    var statusMessage by remember {
        mutableStateOf("Ready to start inspection.")
    }

    val client = remember { OkHttpClient() }


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

                    statusMessage =
                        "No speech detected. Please try again."
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
                "Camera permission granted."
            } else {
                "Camera permission denied. Please allow camera access."
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
                    "Microphone permission denied. Please allow microphone access."
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

            put(
                "job_id",
                "JOB-${System.currentTimeMillis()}"
            )

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

                        val body =
                            result.body?.string().orEmpty()

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
    // CAPTURE PHOTO AND ADD TO OFFLINE QUEUE
    // ------------------------------------------------

    fun capturePhoto(
        captureType: String,
        room: String = "",
        surface: String = ""
    ) {

        val capture = imageCapture

        if (capture == null) {

            statusMessage =
                "Camera is not ready. Please wait."

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

        val photoFile = File(
            queueDirectory,
            "fieldcapture_${System.currentTimeMillis()}.jpg"
        )

        val outputOptions =
            ImageCapture.OutputFileOptions.Builder(photoFile)
                .build()

        statusMessage = "Capturing $captureType photo..."

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),

            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    outputFileResults: ImageCapture.OutputFileResults
                ) {

                    statusMessage =
                        "Photo saved locally. Adding to upload queue..."

                    val constraints =
                        Constraints.Builder()
                            .setRequiredNetworkType(
                                NetworkType.CONNECTED
                            )
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

                    WorkManager.getInstance(context)
                        .enqueue(uploadRequest)

                    statusMessage =
                        "Photo saved locally and added to upload queue. It will upload when internet is available."
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {

                    statusMessage =
                        "Capture failed: ${exception.message}"
                }
            }
        )
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

        if (!sessionStarted) {

            statusMessage =
                "Please start the inspection session first."

            return@LaunchedEffect
        }

        val normalizedCommand =
            command.lowercase(Locale.ROOT)

        when {

            normalizedCommand.startsWith("capture nameplate") -> {

                statusMessage =
                    "Voice command: Capturing nameplate."

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

                    statusMessage =
                        "Capturing damage in $room, surface: $surface"

                    capturePhoto(
                        "damage",
                        room,
                        surface
                    )

                } else {

                    statusMessage =
                        "Please say: Capture damage [room] [surface]. Example: Capture damage living room ceiling."
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

                    Text("Meta Glasses: Not Connected")

                    Text(
                        if (cameraPermissionGranted) {
                            "Phone Camera: Permission Granted"
                        } else {
                            "Phone Camera: Permission Required"
                        }
                    )
                }
            }


            // INSPECTION SESSION

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    Text("INSPECTION SESSION")

                    Text(
                        if (sessionStarted) {
                            "Session Active"
                        } else {
                            "Session Not Started"
                        }
                    )

                    Button(
                        onClick = {

                            sessionStarted = !sessionStarted

                            statusMessage =
                                if (sessionStarted) {
                                    "Inspection session started."
                                } else {
                                    "Inspection session stopped."
                                }
                        },

                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text(
                            if (sessionStarted) {
                                "Stop Inspection"
                            } else {
                                "Start Inspection"
                            }
                        )
                    }
                }
            }


            // CAMERA PREVIEW

            Text(
                "CAMERA PREVIEW",
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

                                statusMessage =
                                    "Camera setup failed: ${e.message}"
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

                    Text("Grant Camera Permission")
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

                enabled = sessionStarted && cameraPermissionGranted
            ) {

                Text("Capture Nameplate")
            }

            Button(
                onClick = {
                    capturePhoto("damage")
                },

                modifier = Modifier.fillMaxWidth(),

                enabled = sessionStarted && cameraPermissionGranted
            ) {

                Text("Capture Damage")
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

                        onValueChange = {
                            noteText = it
                        },

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
                        onClick = {
                            saveTechnicianNote()
                        },

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


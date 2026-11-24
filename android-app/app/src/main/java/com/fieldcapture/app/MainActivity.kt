
package com.fieldcapture.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    var sessionStarted by remember {
        mutableStateOf(false)
    }

    Scaffold { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Text(
                text = "FieldCapture",
                fontSize = 30.sp,
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "Hands-Free Field Inspection",
                style = MaterialTheme.typography.bodyMedium
            )

            // Device status
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("DEVICE STATUS",
                        style = MaterialTheme.typography.labelMedium)

                    Text("Meta Glasses: Not Connected")

                    Text("Phone Camera: Available")
                }
            }

            // Inspection session
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "INSPECTION SESSION",
                        style = MaterialTheme.typography.labelMedium
                    )

                    Text(
                        if (sessionStarted)
                            "Session Active"
                        else
                            "Session Not Started"
                    )

                    Button(
                        onClick = {
                            sessionStarted = !sessionStarted
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (sessionStarted)
                                "Stop Inspection"
                            else
                                "Start Inspection"
                        )
                    }
                }
            }

            // Capture commands
            Text(
                "CAPTURE ACTIONS",
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = { },
                modifier = Modifier.fillMaxWidth(),
                enabled = sessionStarted
            ) {
                Text("Capture Nameplate")
            }

            Button(
                onClick = { },
                modifier = Modifier.fillMaxWidth(),
                enabled = sessionStarted
            ) {
                Text("Capture Damage")
            }

            OutlinedButton(
                onClick = { },
                modifier = Modifier.fillMaxWidth(),
                enabled = sessionStarted
            ) {
                Text("Add Technician Note")
            }

            // Job records
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "JOB RECORDS",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text("No inspection records yet.")

                    Text(
                        "Captured observations will appear here."
                    )
                }
            }

            Text(
                "FieldCapture v1.0 | Development Build",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

package com.fieldcapture.app

import android.app.Application
import android.util.Log
import com.meta.wearable.dat.core.Wearables

class FieldCaptureApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Wearables.initialize(this)
            .onFailure { error, _ ->
                Log.e(
                    "DATWearables",
                    "Failed to initialize DAT: ${error.description}"
                )
            }
    }
}
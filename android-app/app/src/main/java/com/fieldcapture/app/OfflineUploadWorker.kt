package com.fieldcapture.app

import android.content.Context
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

class OfflineUploadWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val KEY_FILE_PATH = "file_path"
        const val KEY_CAPTURE_TYPE = "capture_type"
        const val KEY_ROOM = "room"
        const val KEY_SURFACE = "surface"

        private const val BASE_URL = "http://10.167.230.182:8001"
    }

    private val client = OkHttpClient()

    override fun doWork(): Result {

        val filePath = inputData.getString(KEY_FILE_PATH)
            ?: return Result.failure()

        val captureType = inputData.getString(KEY_CAPTURE_TYPE)
            ?: "damage"

        val room = inputData.getString(KEY_ROOM) ?: ""
        val surface = inputData.getString(KEY_SURFACE) ?: ""

        val photoFile = File(filePath)

        if (!photoFile.exists()) {
            return Result.failure()
        }

        return try {

            // Step 1: Upload the photograph

            val uploadBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    photoFile.name,
                    photoFile.asRequestBody(
                        "image/jpeg".toMediaType()
                    )
                )
                .build()

            val uploadRequest = Request.Builder()
                .url("$BASE_URL/upload-image")
                .post(uploadBody)
                .build()

            val frameId: String
            val qualityPassed: Boolean

            client.newCall(uploadRequest).execute().use { response ->

                if (!response.isSuccessful) {
                    return if (response.code >= 500 ||
                        response.code == 429
                    ) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }

                val responseText =
                    response.body?.string().orEmpty()

                val json = JSONObject(responseText)

                frameId = json.optString("frame_id")
                qualityPassed =
                    json.optBoolean("quality_passed", false)
            }

            if (frameId.isBlank()) {
                return Result.failure()
            }

            // Step 2: Check photograph quality

            if (!qualityPassed) {
                // Keep the photograph locally for inspection.
                return Result.failure()
            }

            // Step 3: Save damage inspection record

            if (captureType == "damage") {

                val damageObject = JSONObject().apply {

                    put(
                        "room",
                        createFieldValue(room)
                    )

                    put(
                        "surface",
                        createFieldValue(surface)
                    )

                    put(
                        "damage_type",
                        createFieldValue("")
                    )

                    put(
                        "rough_size",
                        createFieldValue("")
                    )
                }

                val recordJson = JSONObject().apply {

                    put(
                        "job_id",
                        "JOB-${System.currentTimeMillis()}"
                    )

                    put("capture_type", "damage")

                    put(
                        "captured_at",
                        Instant.now().toString()
                    )

                    put("frame_id", frameId)

                    put("nameplate", JSONObject.NULL)

                    put("damage", damageObject)

                    put("note", JSONObject.NULL)
                }

                val recordBody = recordJson.toString()
                    .toRequestBody(
                        "application/json; charset=utf-8".toMediaType()
                    )

                val recordRequest = Request.Builder()
                    .url("$BASE_URL/records")
                    .post(recordBody)
                    .build()

                client.newCall(recordRequest).execute().use { response ->

                    if (!response.isSuccessful) {
                        return if (response.code >= 500 ||
                            response.code == 429
                        ) {
                            Result.retry()
                        } else {
                            Result.failure()
                        }
                    }
                }
            }

            // Step 4: Delete the local photograph
            // only after successful upload and record saving.

            photoFile.delete()

            Result.success()

        } catch (e: IOException) {

            Result.retry()

        } catch (e: Exception) {

            Result.failure()
        }
    }

    private fun createFieldValue(value: String): JSONObject {

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
}
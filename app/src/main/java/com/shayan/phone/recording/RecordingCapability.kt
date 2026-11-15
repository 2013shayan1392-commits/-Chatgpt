package com.shayan.phone.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Describes what the installed app can honestly offer.
 *
 * A microphone permission is NOT evidence that Android exposes the other
 * participant's call audio. Do not upgrade this result to call-recording
 * support based on brand, chipset, hidden APIs, or a successful mic test.
 */
data class RecordingCapabilityReport(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val sdkInt: Int,
    val microphonePermissionGranted: Boolean,
    val callAudioCaptureAvailable: Boolean,
    val systemRecordingImportAvailable: Boolean,
    val explanation: String
)

object RecordingCapability {
    /**
     * This is deliberately conservative. The app currently has no verified,
     * documented OEM call-recording provider. Users can import an audio file
     * that their phone's own recorder has already created.
     */
    fun inspect(context: Context): RecordingCapabilityReport {
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val model = Build.MODEL.orEmpty()

        return RecordingCapabilityReport(
            manufacturer = manufacturer,
            brand = brand,
            model = model,
            sdkInt = Build.VERSION.SDK_INT,
            microphonePermissionGranted = micGranted,
            callAudioCaptureAvailable = false,
            systemRecordingImportAvailable = true,
            explanation = buildString {
                append("Call-audio capture is not verified for this device. ")
                append("RECORD_AUDIO only permits microphone capture; it does not ")
                append("grant access to both sides of a phone call. ")
                append("An audio file created separately by the device recorder ")
                append("can be imported after the user selects it.")
            }
        )
    }
}

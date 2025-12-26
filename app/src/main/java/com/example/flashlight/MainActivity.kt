Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
    // This will catch ANY crash and show the error message in a Toast before closing
    runOnUiThread {
        Toast.makeText(this, "CRASH: ${throwable.message}", Toast.LENGTH_LONG).show()
    }
}
package com.example.flashlight

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create a simple button layout
        val button = ToggleButton(this).apply {
            textOn = "Flashlight ON"
            textOff = "Flashlight OFF"
            textSize = 24f
        }
        setContentView(button)

        // Get the Camera Manager
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        button.setOnCheckedChangeListener { _, isChecked ->
            try {
                // Find the rear camera (usually ID "0")
                val cameraId = cameraManager.cameraIdList.getOrNull(0)
                
                if (cameraId != null) {
                    cameraManager.setTorchMode(cameraId, isChecked)
                } else {
                    Toast.makeText(this, "No flash found!", Toast.LENGTH_SHORT).show()
                    button.isChecked = false
                }
            } catch (e: Exception) {
                // If permission is denied or camera is broken, show message instead of crashing
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
                button.isChecked = false
            }
        }
    }
}

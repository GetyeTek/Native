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
        
        val button = ToggleButton(this).apply {
            textOn = "Flashlight is ON"
            textOff = "Flashlight is OFF"
        }
        setContentView(button)

        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        button.setOnCheckedChangeListener { _, isChecked ->
            try {
                // Get the first camera ID (usually the back camera)
                val cameraId = cameraManager.cameraIdList.getOrNull(0)
                if (cameraId != null) {
                    cameraManager.setTorchMode(cameraId, isChecked)
                } else {
                    Toast.makeText(this, "No camera found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // This catches the error instead of crashing the app
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }
}

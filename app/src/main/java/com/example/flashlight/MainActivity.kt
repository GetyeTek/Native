package com.example.flashlight

import android.content.Context
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCharacteristics
import android.os.Bundle
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Setup global crash catcher correctly inside the class context
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            runOnUiThread {
                Toast.makeText(this, "Fatal Error: ${throwable.message}", Toast.LENGTH_LONG).show()
            }
        }

        val button = ToggleButton(this).apply {
            textOn = "Flashlight ON"
            textOff = "Flashlight OFF"
            textSize = 24f
        }
        setContentView(button)

        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        button.setOnCheckedChangeListener { _, isChecked ->
            try {
                val list = cameraManager.cameraIdList
                var flashId: String? = null

                // Look for the camera that actually has a flash
                for (id in list) {
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                    if (hasFlash == true) {
                        flashId = id
                        break
                    }
                }
                
                if (flashId != null) {
                    cameraManager.setTorchMode(flashId, isChecked)
                } else {
                    Toast.makeText(this, "No flash unit found!", Toast.LENGTH_SHORT).show()
                    button.isChecked = false
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                button.isChecked = false
            }
        }
    }
}

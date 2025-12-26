package com.example.flashlight

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val button = ToggleButton(this).apply {
            textOn = "ON"
            textOff = "OFF"
        }
        setContentView(button)

        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList[0]

        button.setOnCheckedChangeListener { _, isChecked ->
            cameraManager.setTorchMode(cameraId, isChecked)
        }
    }
}

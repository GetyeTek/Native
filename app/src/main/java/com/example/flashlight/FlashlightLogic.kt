package com.example.flashlight

class FlashlightLogic {
    var isFlashlightOn: Boolean = false
        private set

    fun toggle(): Boolean {
        isFlashlightOn = !isFlashlightOn
        return isFlashlightOn
    }
}

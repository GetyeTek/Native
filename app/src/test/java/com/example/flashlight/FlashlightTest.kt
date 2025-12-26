package com.example.flashlight

import org.junit.Assert.assertEquals
import org.junit.Test

class FlashlightTest {
    @Test
    fun testToggleLogic() {
        val logic = FlashlightLogic()
        
        // 1. Initially it should be off
        assertEquals(false, logic.isFlashlightOn)
        
        // 2. First toggle should turn it ON
        val result1 = logic.toggle()
        assertEquals(true, result1)
        assertEquals(true, logic.isFlashlightOn)
        
        // 3. Second toggle should turn it OFF
        val result2 = logic.toggle()
        assertEquals(false, result2)
        assertEquals(false, logic.isFlashlightOn)
    }
}

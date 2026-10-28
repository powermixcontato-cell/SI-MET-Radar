package com.example

import android.os.Bundle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupSmokeTest {
    @Test
    fun mainActivityStartsAndSavesState() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        c.saveInstanceState(Bundle())
        c.pause().stop().destroy()
    }
}

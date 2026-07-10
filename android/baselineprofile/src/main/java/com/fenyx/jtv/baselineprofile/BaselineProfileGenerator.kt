package com.fenyx.jtv.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile for the critical journey: cold launch → channel grid appears → scroll
 * the grid (browse). This AOT-compiles the launch + first-frame + scroll paths, the biggest cold-start
 * and jank win on the weak Amlogic/MediaTek CPUs these TV boxes use.
 *
 * Run on an API 33+ device/emulator (the app's own minSdk stays 24 — the generated profile still helps
 * older devices at runtime):
 *
 *     ./gradlew :app:generateReleaseBaselineProfile
 *
 * The plugin writes the result to app/src/release/generated/baselineProfiles/ and embeds it in the
 * release build via ProfileInstaller.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.fenyx.jtv",
        includeInStartupProfile = true,
    ) {
        // Journey: from Home, cold-start the app and wait for the first frame.
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        // Exercise the browse path. Best-effort: if the view tree differs on a given device, profile
        // generation must not fail — the startup portion is already captured.
        runCatching {
            val grid = UiScrollable(UiSelector().scrollable(true))
            if (grid.exists()) {
                grid.setAsVerticalList()
                grid.flingForward()
                device.waitForIdle()
                grid.flingBackward()
                device.waitForIdle()
            }
        }
    }
}

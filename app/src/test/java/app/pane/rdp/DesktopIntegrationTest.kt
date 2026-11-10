package app.pane.rdp

import org.junit.Assert.*
import org.junit.Test

class DesktopIntegrationTest {
    @Test fun volumeKeysReachTheHostWithoutOpeningAndroidVolumeControls() {
        assertEquals(0x1008ff13, voxKey(virtualKey(android.view.KeyEvent.KEYCODE_VOLUME_UP)!!))
        assertEquals(0x1008ff11, voxKey(virtualKey(android.view.KeyEvent.KEYCODE_VOLUME_DOWN)!!))
        assertEquals(0x1008ff12, voxKey(virtualKey(android.view.KeyEvent.KEYCODE_VOLUME_MUTE)!!))
    }
    @Test fun quietModeOnlyAppliesToAnEnabledForegroundConnection() {
        assertFalse(quietDesktopActive(false, true, true))
        assertFalse(quietDesktopActive(true, false, true))
        assertFalse(quietDesktopActive(true, true, false))
        assertTrue(quietDesktopActive(true, true, true))
    }
    @Test fun touchpadGesturesKeepTheSamePhysicalDistanceAcrossDeviceUnits() {
        val lowResolution = touchpadCoordinateScale(10f, 254f)
        val highResolution = touchpadCoordinateScale(40f, 254f)
        assertEquals(100f * lowResolution, 400f * highResolution, 0.001f)
        assertEquals(2f * lowResolution, touchpadCoordinateScale(10f, 508f), 0.001f)
    }
    @Test fun missingOrInvalidTouchpadCalibrationIsSafe() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertEquals(1f, touchpadCoordinateScale(it, 254f), 0f)
            assertEquals(1f, touchpadCoordinateScale(10f, it), 0f)
        }
        assertTrue(touchpadCoordinateScale(Float.MIN_VALUE, Float.MAX_VALUE).isFinite())
    }
}

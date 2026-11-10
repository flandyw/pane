package app.pane.rdp

import android.content.res.Configuration
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test

class RemoteInputTest {
    private class Keyboard {
        val keys = mutableListOf<Pair<Int,Boolean>>()
        val texts = mutableListOf<String>()
        var toolbar = 0
        val input = RemoteKeyboard({ vk,down -> keys.add(vk to down) },{ texts.add(it) },{ toolbar++ },
            { accent,letter -> when { accent == letter || letter == ' '.code -> accent; accent == 0xb4 && letter == 'e'.code -> 'é'.code; else -> 0 } })
        fun down(code: Int, unicode: Int = 0, meta: Int = 0, device: Int = 1) =
            input.handle(KeyEvent.ACTION_DOWN,code,device,unicode,meta)
        fun up(code: Int, meta: Int = 0, device: Int = 1) = input.handle(KeyEvent.ACTION_UP,code,device,meta=meta)
    }
    @Test fun printableHardwareTextFollowsAndroidLayoutNotUsKeyPosition() {
        val k=Keyboard()
        assertTrue(k.down(KeyEvent.KEYCODE_A,'q'.code))
        assertTrue(k.up(KeyEvent.KEYCODE_A))
        assertEquals(listOf("q"),k.texts)
        assertTrue(k.keys.isEmpty())
    }
    @Test fun shiftIsNotAppliedTwiceToUnicode() {
        val k=Keyboard()
        k.down(KeyEvent.KEYCODE_SHIFT_LEFT)
        k.down(KeyEvent.KEYCODE_2,'@'.code,KeyEvent.META_SHIFT_ON)
        k.up(KeyEvent.KEYCODE_2)
        k.up(KeyEvent.KEYCODE_SHIFT_LEFT)
        assertEquals(listOf("@"),k.texts)
        assertEquals(listOf(0xa0 to true,0xa0 to false,0xa0 to true,0xa0 to false),k.keys)
    }
    @Test fun hardwareShortcutsUseRawKeysAndMatchedReleases() {
        val k=Keyboard()
        k.down(KeyEvent.KEYCODE_CTRL_LEFT)
        k.down(KeyEvent.KEYCODE_C,3,KeyEvent.META_CTRL_ON)
        k.up(KeyEvent.KEYCODE_CTRL_LEFT)
        k.up(KeyEvent.KEYCODE_C)
        assertEquals(listOf(0xa2 to true,0x43 to true,0xa2 to false,0x43 to false),k.keys)
        assertTrue(k.texts.isEmpty())
    }
    @Test fun repeatingRawKeyStaysRawAfterItsModifierIsReleased() {
        val k=Keyboard()
        k.down(KeyEvent.KEYCODE_C,3,KeyEvent.META_CTRL_ON)
        k.down(KeyEvent.KEYCODE_C,'c'.code)
        k.up(KeyEvent.KEYCODE_C)
        assertEquals(listOf(0xa2 to true,0x43 to true,0xa2 to false,0x43 to true,0x43 to false),k.keys)
        assertTrue(k.texts.isEmpty())
    }
    @Test fun unicodeAutoRepeatStillRepeatsText() {
        val k=Keyboard()
        repeat(3) { k.down(KeyEvent.KEYCODE_A,'a'.code) }
        k.up(KeyEvent.KEYCODE_A)
        assertEquals(listOf("a","a","a"),k.texts)
        assertTrue(k.keys.isEmpty())
    }
    @Test fun altGrProducesTextInsteadOfCtrlAltShortcut() {
        val k=Keyboard()
        k.down(KeyEvent.KEYCODE_ALT_RIGHT)
        k.down(KeyEvent.KEYCODE_E,'€'.code,KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON or KeyEvent.META_CTRL_ON)
        k.up(KeyEvent.KEYCODE_E)
        k.up(KeyEvent.KEYCODE_ALT_RIGHT)
        assertEquals(listOf("€"),k.texts)
        assertEquals(listOf(true,false,true,false),k.keys.filter { it.first == 0xa5 }.map { it.second })
        assertEquals(listOf(true,false,true,false),k.keys.filter { it.first == 0xa2 }.map { it.second })
    }
    @Test fun stickyCtrlAppliesToImeCommittedLetters() {
        val k=Keyboard()
        k.input.toggleCtrl()
        k.input.commitText("c")
        k.input.toggleCtrl()
        assertEquals(listOf(0xa2 to true,0x43 to true,0x43 to false,0xa2 to false),k.keys)
        assertTrue(k.texts.isEmpty())
    }
    @Test fun stickyAltAppliesToImeCommittedLetters() {
        val k=Keyboard()
        k.input.toggleAlt(); k.input.commitText("f"); k.input.releaseAll()
        assertEquals(listOf(0xa4 to true,0x46 to true,0x46 to false,0xa4 to false),k.keys)
        assertFalse(k.input.alt)
    }
    @Test fun stickyAndPhysicalCtrlDoNotReleaseEachOther() {
        val k=Keyboard()
        k.input.toggleCtrl(); k.down(KeyEvent.KEYCODE_CTRL_LEFT); k.up(KeyEvent.KEYCODE_CTRL_LEFT)
        assertEquals(listOf(0xa2 to true),k.keys)
        k.input.toggleCtrl()
        assertEquals(listOf(0xa2 to true,0xa2 to false),k.keys)
    }
    @Test fun twoPhysicalKeyboardsShareAKeyWithoutPrematureRelease() {
        val k=Keyboard()
        k.down(KeyEvent.KEYCODE_CTRL_LEFT,device=1)
        k.down(KeyEvent.KEYCODE_CTRL_LEFT,device=2)
        k.up(KeyEvent.KEYCODE_CTRL_LEFT,device=1)
        assertEquals(listOf(0xa2 to true),k.keys)
        k.up(KeyEvent.KEYCODE_CTRL_LEFT,device=2)
        assertEquals(listOf(0xa2 to true,0xa2 to false),k.keys)
    }
    @Test fun releaseAllIsIdempotentAndClearsStickyState() {
        val k=Keyboard()
        k.down(KeyEvent.KEYCODE_SHIFT_RIGHT); k.input.toggleCtrl(); k.input.toggleAlt()
        k.input.releaseAll(); val once=k.keys.toList(); k.input.releaseAll()
        assertEquals(once,k.keys)
        assertEquals(3,k.keys.count { !it.second })
        assertFalse(k.input.ctrl); assertFalse(k.input.alt)
    }
    @Test fun focusTransferReleasesPhysicalKeysButKeepsRequestedStickyCtrl() {
        val k=Keyboard()
        k.input.toggleCtrl(); k.down(KeyEvent.KEYCODE_SHIFT_LEFT); k.input.releasePhysical()
        assertTrue(k.input.ctrl)
        assertEquals(listOf(0xa2 to true,0xa0 to true,0xa0 to false),k.keys)
    }
    @Test fun toolbarShortcutConsumesHomeUpAfterModifiersAreReleased() {
        val k=Keyboard()
        val meta=KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON
        assertTrue(k.down(KeyEvent.KEYCODE_MOVE_HOME,meta=meta))
        assertTrue(k.input.handle(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MOVE_HOME,1,meta=meta,repeat=1))
        assertTrue(k.up(KeyEvent.KEYCODE_MOVE_HOME))
        assertEquals(1,k.toolbar)
        assertTrue(k.keys.isEmpty())
    }
    @Test fun plainHomeAndCtrlAltShiftHomeRemainRemote() {
        val k=Keyboard()
        k.down(KeyEvent.KEYCODE_MOVE_HOME); k.up(KeyEvent.KEYCODE_MOVE_HOME)
        k.down(KeyEvent.KEYCODE_MOVE_HOME,meta=KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_SHIFT_ON)
        k.up(KeyEvent.KEYCODE_MOVE_HOME)
        assertEquals(listOf(0x24 to true,0x24 to false,0x24 to true,0x24 to false),k.keys.filter { it.first == 0x24 })
        assertEquals(0,k.toolbar)
    }
    @Test fun deadAccentComposesOnceAndCanBeCancelledWithBackspace() {
        val k=Keyboard()
        val dead=KeyCharacterMap.COMBINING_ACCENT or 0xb4
        k.down(KeyEvent.KEYCODE_APOSTROPHE,dead); k.up(KeyEvent.KEYCODE_APOSTROPHE)
        k.down(KeyEvent.KEYCODE_E,'e'.code); k.up(KeyEvent.KEYCODE_E)
        assertEquals(listOf("é"),k.texts)
        k.down(KeyEvent.KEYCODE_APOSTROPHE,dead); k.up(KeyEvent.KEYCODE_APOSTROPHE)
        k.down(KeyEvent.KEYCODE_DEL); k.up(KeyEvent.KEYCODE_DEL)
        k.down(KeyEvent.KEYCODE_E,'e'.code)
        assertEquals(listOf("é","e"),k.texts)
        assertTrue(k.keys.isEmpty())
    }
    @Test fun unsupportedDeadCombinationKeepsBothCharacters() {
        val k=Keyboard()
        k.down(KeyEvent.KEYCODE_APOSTROPHE,KeyCharacterMap.COMBINING_ACCENT or 0xb4)
        k.up(KeyEvent.KEYCODE_APOSTROPHE)
        k.down(KeyEvent.KEYCODE_X,'x'.code)
        assertEquals(listOf("´x"),k.texts)
    }
    @Test fun focusLossDiscardsPendingDeadAccent() {
        val k=Keyboard()
        k.down(KeyEvent.KEYCODE_APOSTROPHE,KeyCharacterMap.COMBINING_ACCENT or 0xb4)
        k.input.releaseAll(); k.down(KeyEvent.KEYCODE_E,'e'.code)
        assertEquals(listOf("e"),k.texts)
    }
    @Test fun supplementaryKeyboardCharacterDoesNotThrowOrSplitAtRouting() {
        val k=Keyboard()
        k.down(KeyEvent.KEYCODE_UNKNOWN,0x1f600)
        assertTrue(k.up(KeyEvent.KEYCODE_UNKNOWN))
        assertEquals(listOf("😀"),k.texts)
    }
    @Test fun actionMultipleHandlesCommittedTextAndCapsRawRepeats() {
        val k=Keyboard()
        assertTrue(k.input.handle(KeyEvent.ACTION_MULTIPLE,KeyEvent.KEYCODE_UNKNOWN,-1,characters="日本語"))
        k.input.handle(KeyEvent.ACTION_MULTIPLE,KeyEvent.KEYCODE_TAB,-1,repeat=10000)
        assertEquals(listOf("日本語"),k.texts)
        assertEquals(200,k.keys.size)
    }
    @Test fun backAndSystemNavigationAreNotMapped() {
        val k=Keyboard()
        listOf(KeyEvent.KEYCODE_BACK,KeyEvent.KEYCODE_HOME,KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_LANGUAGE_SWITCH).forEach {
            assertFalse(k.down(it)); assertFalse(k.up(it))
        }
        assertTrue(k.keys.isEmpty()); assertTrue(k.texts.isEmpty())
    }
    @Test fun invalidCodePointsAreNotPrintable() {
        listOf(-1,0,9,10,0x7f,0x9f,0xd800,0xdfff,0x110000).forEach { assertFalse(isPrintableCodePoint(it)) }
        listOf(0x20,0xe9,0x4e00,0x1f600).forEach { assertTrue(isPrintableCodePoint(it)) }
    }
    @Test fun hardwareKeyboardDetectionDoesNotConfuseSoftKeyboardVisibilityWithHardware() {
        assertTrue(hasHardwareKeyboard(Configuration.KEYBOARD_QWERTY,Configuration.HARDKEYBOARDHIDDEN_NO))
        assertFalse(hasHardwareKeyboard(Configuration.KEYBOARD_NOKEYS,Configuration.HARDKEYBOARDHIDDEN_NO))
        assertFalse(hasHardwareKeyboard(Configuration.KEYBOARD_QWERTY,Configuration.HARDKEYBOARDHIDDEN_YES))
    }
    @Test fun mouseTouchAndGenericButtonStreamsDoNotDuplicateClicks() {
        val sent=mutableListOf<Int>(); val p=RemotePointer(sent::add)
        p.buttons(MotionEvent.BUTTON_PRIMARY); p.button(MotionEvent.BUTTON_PRIMARY,true)
        p.buttons(0); p.button(MotionEvent.BUTTON_PRIMARY,false)
        assertEquals(listOf(0x9000,0x1000),sent)
    }
    @Test fun secondaryAndMiddleButtonsReleaseOnCancelWithoutPrimaryClicks() {
        val sent=mutableListOf<Int>(); val p=RemotePointer(sent::add)
        p.buttons(MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY)
        p.release(); p.release()
        assertEquals(listOf(0xa000,0xc000,0x2000,0x4000),sent)
    }
    @Test fun unsupportedMouseButtonsStayLocal() {
        val sent=mutableListOf<Int>(); val p=RemotePointer(sent::add)
        p.buttons(MotionEvent.BUTTON_BACK or MotionEvent.BUTTON_FORWARD)
        assertTrue(sent.isEmpty())
    }
    @Test fun scrollAxesAccumulateIndependently() {
        val sent=mutableListOf<Int>(); val p=RemotePointer(sent::add)
        p.scroll(0.5f,0.25f,1f,1,1)
        p.scroll(0.5f,-1.25f,1f,1,1)
        assertEquals(listOf(0x478,0x388),sent)
    }
    @Test fun scrollSourceAndDeviceChangesDropIncompatibleRemainders() {
        val sent=mutableListOf<Int>(); val p=RemotePointer(sent::add)
        p.scroll(0f,35f,36f,2,1)
        p.scroll(0f,0.5f,1f,1,1)
        p.scroll(0f,0.5f,1f,1,2)
        assertTrue(sent.isEmpty())
        p.scroll(0f,0.5f,1f,1,2)
        assertEquals(listOf(0x278),sent)
    }
    @Test fun releaseDropsScrollRemainder() {
        val sent=mutableListOf<Int>(); val p=RemotePointer(sent::add)
        p.scroll(0f,0.75f,1f,1,1); p.release(); p.scroll(0f,0.25f,1f,1,1)
        assertTrue(sent.isEmpty())
    }
    @Test fun remoteTextUsesPhysicalKeysForControlsAndCollapsesCrLf() {
        val keys=mutableListOf<Pair<Int,Boolean>>(); val chars=mutableListOf<Pair<Int,Boolean>>()
        sendRemoteText("a\r\nb\nc\rd\t\b\u0000",{ vk,down -> keys.add(vk to down) },{ c,down -> chars.add(c to down) })
        assertEquals(listOf(0x0d,0x0d,0x0d,0x09,0x08),keys.filter { it.second }.map { it.first })
        assertEquals(listOf('a'.code,'b'.code,'c'.code,'d'.code),chars.filter { it.second }.map { it.first })
        assertEquals(keys.count { it.second },keys.count { !it.second })
    }
    @Test fun remoteTextPreservesPairsAndDropsUnpairedSurrogates() {
        val chars=mutableListOf<Int>()
        sendRemoteText("\ud800a😀\udc00",{ _,_ -> fail() },{ c,down -> if (down) chars.add(c) })
        assertEquals(listOf('a'.code,0xd83d,0xde00),chars)
    }
    @Test fun remoteTextLimitCannotSplitASurrogatePair() {
        var count=0
        sendRemoteText("a".repeat(16383)+"😀",{ _,_ -> fail() },{ c,down -> if (down) { assertEquals('a'.code,c); count++ } })
        assertEquals(16383,count)
    }
    @Test fun missingCtrlDownIsRecoveredFromAndroidMetaState() {
        val k=Keyboard()
        k.down(KeyEvent.KEYCODE_C,3,KeyEvent.META_CTRL_ON)
        k.up(KeyEvent.KEYCODE_C)
        assertEquals(listOf(0xa2 to true,0x43 to true,0xa2 to false,0x43 to false),k.keys)
    }
    @Test fun missingModifierUpIsRepairedByNextUnmodifiedEvent() {
        val k=Keyboard()
        k.down(KeyEvent.KEYCODE_ALT_LEFT)
        k.down(KeyEvent.KEYCODE_A,'a'.code)
        assertEquals(listOf(0xa4 to true,0xa4 to false),k.keys)
        assertEquals(listOf("a"),k.texts)
    }
    @Test fun repeatedDeadAccentAndDeadSpaceDoNotAccentNextLetter() {
        val k=Keyboard()
        val dead=KeyCharacterMap.COMBINING_ACCENT or 0xb4
        k.down(KeyEvent.KEYCODE_APOSTROPHE,dead); k.up(KeyEvent.KEYCODE_APOSTROPHE)
        k.down(KeyEvent.KEYCODE_APOSTROPHE,dead); k.up(KeyEvent.KEYCODE_APOSTROPHE)
        k.down(KeyEvent.KEYCODE_E,'e'.code); k.up(KeyEvent.KEYCODE_E)
        k.down(KeyEvent.KEYCODE_APOSTROPHE,dead); k.up(KeyEvent.KEYCODE_APOSTROPHE)
        k.down(KeyEvent.KEYCODE_SPACE,' '.code)
        assertEquals(listOf("´","e","´"),k.texts)
    }
    @Test fun invalidDeadAccentCannotCrashCodePointConversion() {
        val k=Keyboard()
        assertFalse(k.down(KeyEvent.KEYCODE_UNKNOWN,KeyCharacterMap.COMBINING_ACCENT or 0x7fffffff))
        k.down(KeyEvent.KEYCODE_E,'e'.code)
        assertEquals(listOf("e"),k.texts)
    }
    @Test fun stickyCtrlPreservesEnterAndTabCombinationsFromIme() {
        val k=Keyboard()
        k.input.toggleCtrl(); k.input.commitText("\n"); k.input.commitText("\t"); k.input.releaseAll()
        assertEquals(listOf(0xa2 to true,0x0d to true,0x0d to false,0x09 to true,0x09 to false,0xa2 to false),k.keys)
    }
    @Test fun toolbarShortcutRecoversWhenAnEarlierHomeUpWasLost() {
        val k=Keyboard()
        k.down(KeyEvent.KEYCODE_MOVE_HOME,meta=KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON)
        k.down(KeyEvent.KEYCODE_MOVE_HOME)
        k.up(KeyEvent.KEYCODE_MOVE_HOME)
        assertEquals(1,k.toolbar)
        assertEquals(listOf(0x24 to true,0x24 to false),k.keys)
    }
    @Test fun toolbarShortcutUpStillStaysLocalAfterFocusMovesToControls() {
        val k=Keyboard()
        k.down(KeyEvent.KEYCODE_MOVE_HOME,meta=KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON)
        k.input.releasePhysical()
        assertTrue(k.up(KeyEvent.KEYCODE_MOVE_HOME))
        assertTrue(k.keys.isEmpty())
    }
    @Test fun tappingAnAlreadyHeldKeyDoesNotReleaseIt() {
        val k=Keyboard()
        k.down(KeyEvent.KEYCODE_TAB); k.input.tap(0x09)
        assertEquals(listOf(0x09 to true,0x09 to true),k.keys)
        k.up(KeyEvent.KEYCODE_TAB)
        assertEquals(0x09 to false,k.keys.last())
    }
}

package fi.severi.overtime.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import java.awt.event.KeyEvent

internal actual fun keyName(keyCode: Long): String = KeyEvent.getKeyText(Key(keyCode).nativeKeyCode)

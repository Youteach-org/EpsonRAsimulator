package mx.youteachtk.epsonrasimulator.ui.rcplus.commands

import androidx.compose.ui.input.key.Key
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcShortcut
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcShortcutKey

fun rcShortcutFor(key: Key, ctrl: Boolean, alt: Boolean, shift: Boolean): RcShortcut? {
    val mapped = when (key) {
        Key.F5 -> RcShortcutKey.F5
        Key.F6 -> RcShortcutKey.F6
        Key.B -> RcShortcutKey.B
        Key.M -> RcShortcutKey.M
        else -> return null
    }
    return RcShortcut(mapped, ctrl, alt, shift)
}

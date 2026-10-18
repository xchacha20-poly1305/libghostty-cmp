package io.github.xchacha20_poly1305.libghostty.compose

import io.github.xchacha20_poly1305.libghostty.GhosttyMods
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Sticky Ctrl and Alt of the extra keys bar: a tap arms a modifier for the next key, a double tap locks it. */
public class GhosttyExtraKeysState {

    public enum class StickyState { INACTIVE, ARMED, LOCKED }

    private val _ctrl = MutableStateFlow(StickyState.INACTIVE)
    public val ctrl: StateFlow<StickyState> = _ctrl.asStateFlow()

    private val _alt = MutableStateFlow(StickyState.INACTIVE)
    public val alt: StateFlow<StickyState> = _alt.asStateFlow()

    private var lastCtrlTap: TimeMark? = null
    private var lastAltTap: TimeMark? = null

    public fun toggleCtrl() {
        _ctrl.value = toggleModifier(_ctrl.value, lastCtrlTap)
        lastCtrlTap = TimeSource.Monotonic.markNow()
    }

    public fun toggleAlt() {
        _alt.value = toggleModifier(_alt.value, lastAltTap)
        lastAltTap = TimeSource.Monotonic.markNow()
    }

    public fun consumeModifiers() {
        if (_ctrl.value == StickyState.ARMED) _ctrl.value = StickyState.INACTIVE
        if (_alt.value == StickyState.ARMED) _alt.value = StickyState.INACTIVE
    }

    /** The armed or locked modifiers as [GhosttyMods] bits. */
    public fun currentMods(): Int {
        var mods = 0
        if (_ctrl.value != StickyState.INACTIVE) {
            mods = mods or GhosttyMods.CTRL
        }
        if (_alt.value != StickyState.INACTIVE) {
            mods = mods or GhosttyMods.ALT
        }
        return mods
    }

    private fun toggleModifier(current: StickyState, lastTap: TimeMark?): StickyState = when (current) {
        StickyState.INACTIVE -> StickyState.ARMED

        StickyState.ARMED -> {
            val elapsed = lastTap?.elapsedNow()?.inWholeMilliseconds
            if (elapsed != null && elapsed < DOUBLE_TAP_THRESHOLD_MS) {
                StickyState.LOCKED
            } else {
                StickyState.INACTIVE
            }
        }

        StickyState.LOCKED -> StickyState.INACTIVE
    }

    private companion object {
        const val DOUBLE_TAP_THRESHOLD_MS = 300L
    }
}

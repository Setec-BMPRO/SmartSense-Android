package com.smartsense.app.ui.views

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.smartsense.app.R
import com.smartsense.app.domain.model.LevelStatus

/**
 * Colours of the BMPRO gas bottle (SmartSensePro restyle), shared by [TankLevelView] and
 * [TankLevelMiniView]: a light-grey cylinder that looks the same in light and dark mode,
 * and gas in the level colour that fades lighter towards the top.
 */
internal object BmproTankColors {
    /** Body, collar, valve, band and foot ring — the same in both themes. */
    const val CYLINDER = 0xFFE0E1E2.toInt()

    /** Faint outline around the small list bottle in light mode. */
    const val MINI_OUTLINE_LIGHT = 0xFFC8C9CB.toInt()

    /** "4 KG" label on the lower body; the cylinder is light grey in both themes. */
    const val LABEL = 0x70000000

    /** Badge fill behind the level %: white, or Pantone 426 C charcoal in dark mode. */
    const val BADGE_FILL_LIGHT = 0xFFFFFFFF.toInt()
    const val BADGE_FILL_DARK = 0xFF23272B.toInt()

    fun gasBottom(context: Context, status: LevelStatus): Int = ContextCompat.getColor(
        context,
        when (status) {
            LevelStatus.GREEN -> R.color.level_green
            LevelStatus.YELLOW -> R.color.level_yellow
            LevelStatus.RED -> R.color.level_red
        }
    )

    /** Top of the gas: the level colour lightened by 20 %. */
    fun gasTop(context: Context, status: LevelStatus): Int =
        ColorUtils.blendARGB(gasBottom(context, status), 0xFFFFFFFF.toInt(), 0.2f)
}

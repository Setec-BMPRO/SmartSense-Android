package com.smartsense.app.util

import android.view.View
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.smartsense.app.R

fun View.showSnackbar(
    message: Any,
    duration: Int = Snackbar.LENGTH_LONG,
    @DrawableRes iconRes: Int? = null,
    actionRes: Int? = null,
    action: (() -> Unit)? = null
) {
    val snackbar = when (message) {
        is Int -> Snackbar.make(this, message, duration)
        is CharSequence -> Snackbar.make(this, message, duration)
        else -> Snackbar.make(this, message.toString(), duration)
    }

    if (iconRes != null) {
        val snackbarView = snackbar.view
        val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        val icon = ContextCompat.getDrawable(context, iconRes)?.mutate()
        icon?.let {
            it.setTint(textView.currentTextColor)
            it.setBounds(0, 0, it.intrinsicWidth, it.intrinsicHeight)
            textView.setCompoundDrawables(it, null, null, null)
            textView.compoundDrawablePadding = context.resources.getDimensionPixelSize(R.dimen.spacing_sm)
        }
    }

    if (actionRes != null && action != null) {
        snackbar.setAction(actionRes) { action() }
    }

    snackbar.show()
}

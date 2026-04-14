package com.smartsense.app.util

import android.content.Context
import android.view.Gravity
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartsense.app.R


/**
 * Extension function to show a standard confirmation dialog
 */
fun Context.showConfirmationDialog(
    title: String,
    message: String,
    positiveText: String = getString(android.R.string.ok),
    negativeText: String = getString(android.R.string.cancel),
    neutralText: String? = null,
    isWarning: Boolean = false,
    onConfirm: () -> Unit,
    onCancel: () -> Unit = {},
    onNeutral: (() -> Unit)? = null
) {
    val theme = if (isWarning) {
        R.style.ThemeOverlay_SmartSense_MaterialAlertDialog_Warning
    } else {
        R.style.ThemeOverlay_SmartSense_MaterialAlertDialog
    }

    val builder = MaterialAlertDialogBuilder(this, theme)
        .setTitle(title)
        .setMessage(message)
        .setCancelable(false)
        .setNegativeButton(negativeText) { _, _ ->
            onCancel()
        }
        .setPositiveButton(positiveText) { _, _ ->
            onConfirm()
        }

    // Only add the neutral button if text is provided
    if (neutralText != null) {
        builder.setNeutralButton(neutralText) { _, _ ->
            onNeutral?.invoke()
        }
    }

    builder.show()
}
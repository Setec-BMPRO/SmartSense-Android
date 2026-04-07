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
    positiveText: String=getString(android.R.string.ok),
    negativeText: String=getString(android.R.string.cancel),
    onConfirm: () -> Unit,
    onCancel: () -> Unit={}
) {
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setMessage(message)
        .setCancelable(false)
        .setNegativeButton(negativeText){_,_ ->
            onCancel()
        }
        .setPositiveButton(positiveText) { _, _ ->
            onConfirm()
        }
        .show()
}
package com.example.resqcall.util

import android.app.Activity
import android.view.View
import androidx.core.content.ContextCompat
import com.example.resqcall.R
import com.google.android.material.snackbar.Snackbar

// Default system Toasts render with OS-level styling, not the app theme, so they always
// looked like the "old" dark UI no matter what we did to the app's own colors. These
// themed Snackbars replace them everywhere so in-app messages always match the current theme.

fun Activity.showMessage(message: String) {
    snack(message, R.color.text_primary)
}

fun Activity.showError(message: String) {
    snack(message, R.color.error_red)
}

fun Activity.showSuccess(message: String) {
    snack(message, R.color.success_green)
}

private fun Activity.snack(message: String, backgroundColorRes: Int) {
    val root = findViewById<View>(android.R.id.content) ?: return
    Snackbar.make(root, message, Snackbar.LENGTH_SHORT).apply {
        setBackgroundTint(ContextCompat.getColor(context, backgroundColorRes))
        setTextColor(ContextCompat.getColor(context, R.color.white))
    }.show()
}

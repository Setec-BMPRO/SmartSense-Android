package com.smartsense.app.util

import android.view.Menu
import com.google.android.material.appbar.MaterialToolbar

fun MaterialToolbar.forceShowMenuIcons() {
    menu.forceShowIcons()
}

fun Menu.forceShowIcons() {
    runCatching {
        val method = javaClass.getDeclaredMethod("setOptionalIconsVisible", Boolean::class.javaPrimitiveType)
        method.isAccessible = true
        method.invoke(this, true)
    }
}

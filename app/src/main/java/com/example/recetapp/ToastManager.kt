package com.example.recetapp

import android.content.Context
import android.widget.Toast
import java.lang.ref.WeakReference

object ToastManager {
    private var currentToast: WeakReference<Toast>? = null

    fun showToast(context: Context, message: String, isLong: Boolean = false) {
        currentToast?.get()?.cancel()
        val toast = Toast.makeText(context.applicationContext, message, if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT)
        toast.show()
        currentToast = WeakReference(toast)
    }
}

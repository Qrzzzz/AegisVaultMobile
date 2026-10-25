package com.aegisvault.mobile.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal class AndroidSensitiveClipboard(context: Context) : SensitiveClipboardPort {
    private val clipboard = context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun copy(text: String, token: String): ClipboardOwnership {
        val clip = ClipData.newPlainText("AegisVault Result", text).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                putString(OWNER_KEY, token)
            }
        }
        clipboard.setPrimaryClip(clip)
        val description = clipboard.primaryClipDescription
        val timestamp = description?.takeIf { it.extras?.getString(OWNER_KEY) == token }?.timestamp
        return ClipboardOwnership(token, digest(text), timestamp)
    }

    override fun clearIfOwned(owner: ClipboardOwnership): ClipboardClearResult = try {
        val clip = clipboard.primaryClip
        if (clip == null) ClipboardClearResult.UNAVAILABLE
        else if (clip.description.extras?.getString(OWNER_KEY) != owner.token || clip.itemCount != 1 ||
            (owner.timestamp != null && clip.description.timestamp != owner.timestamp) ||
            clip.getItemAt(0).text?.let { digest(it.toString()) } != owner.digest) ClipboardClearResult.REPLACED
        else {
            if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
            else clipboard.setPrimaryClip(ClipData.newPlainText("AegisVault", ""))
            ClipboardClearResult.CLEARED
        }
    } catch (_: SecurityException) { ClipboardClearResult.UNAVAILABLE }

    private fun digest(text: String) = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    companion object { const val OWNER_KEY = "com.aegisvault.mobile.CLIP_OWNER" }
}

internal object AppClipboard {
    @Volatile private var instance: ClipboardCleanup? = null
    fun get(context: Context): ClipboardCleanup = instance ?: synchronized(this) {
        instance ?: ClipboardCleanup(AndroidSensitiveClipboard(context.applicationContext),
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), SystemClock::elapsedRealtime)
            .also { instance = it }
    }
}

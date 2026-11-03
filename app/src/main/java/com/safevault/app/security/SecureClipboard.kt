package com.safevault.app.security

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copies secrets to the clipboard, marks them as sensitive (Android 13+ hides the preview
 * and keeps them out of clipboard history on many keyboards) and clears them after a delay.
 *
 * The delay runs as a coroutine, which dies with the process. An alarm a few seconds later
 * is the backstop: if Android killed the app first, ClipboardClearReceiver clears it (D-038).
 */
class SecureClipboard(private val context: Context, private val scope: CoroutineScope) {
    private val cm = context.getSystemService(ClipboardManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private var clearJob: Job? = null

    fun copy(label: String, text: String, clearAfterSeconds: Int) {
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            } else {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        cm.setPrimaryClip(clip)
        val copied = text.hashCode()
        clearJob?.cancel()
        clearJob = scope.launch {
            delay(clearAfterSeconds * 1000L)
            clearIfStillOurs(copied)
        }
        runCatching {
            alarms.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + clearAfterSeconds * 1000L + BACKSTOP_SLACK_MS,
                backstopIntent(context),
            )
        }
    }

    private fun clearIfStillOurs(hash: Int) {
        // The timer ran, so the backstop isn't needed.
        runCatching { alarms.cancel(backstopIntent(context)) }
        // In the background Android 10+ won't let us read the clipboard (returns null); in
        // that case clear anyway, because we can't tell and leaving a password is worse.
        val current = runCatching { cm.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
        if (current != null && current.hashCode() != hash) return
        clear(cm)
    }

    companion object {
        private const val BACKSTOP_SLACK_MS = 5_000L

        private fun backstopIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ClipboardClearReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun clear(cm: ClipboardManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching { cm.clearPrimaryClip() }
            } else {
                runCatching { cm.setPrimaryClip(ClipData.newPlainText("", "")) }
            }
        }
    }
}

/**
 * Fired by SecureClipboard's backstop alarm. It runs in the background, where the clipboard
 * can't be read, so it can't check the clip is still ours and clears it anyway.
 */
class ClipboardClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SecureClipboard.clear(context.getSystemService(ClipboardManager::class.java))
    }
}

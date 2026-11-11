package com.example.sasalele.data

import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.sasalele.data.preferences.SettingsDataStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first

interface ReminderNotifier {
    suspend fun notify(clients: List<ClientKey>)
}

object FlashState {
    private val _flashFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val flashFlow: SharedFlow<Unit> = _flashFlow.asSharedFlow()

    fun triggerFlash() {
        _flashFlow.tryEmit(Unit)
    }
}

class AndroidReminderNotifier(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore
) : ReminderNotifier {

    override suspend fun notify(clients: List<ClientKey>) {
        if (clients.isEmpty()) return

        val isGlobalEnabled = settingsDataStore.enableRemindersFlow.first()
        if (!isGlobalEnabled) return

        val isSoundEnabled = settingsDataStore.soundEnabledFlow.first()
        val isVibrateEnabled = settingsDataStore.vibrateEnabledFlow.first()

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ringerMode = audioManager.ringerMode
        val isSilentMode = ringerMode == AudioManager.RINGER_MODE_SILENT
        val isNormalMode = ringerMode == AudioManager.RINGER_MODE_NORMAL

        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val allowInterruptions = notifications.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_ALL
        if (isSoundEnabled && isNormalMode && allowInterruptions) {
            try {
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(context, notification)
                r.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (isVibrateEnabled && !isSilentMode && allowInterruptions) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200)
            }
        }

        // Trigger UI Flash
        FlashState.triggerFlash()
    }
}
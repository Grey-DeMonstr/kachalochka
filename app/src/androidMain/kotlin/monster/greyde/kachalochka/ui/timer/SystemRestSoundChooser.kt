package monster.greyde.kachalochka.ui.timer

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import monster.greyde.kachalochka.ui.strings.AppStrings

private const val LOG_TAG = "Kachalochka"

/** The system's notification sounds, through its own sound picker. */
class SystemRestSoundChooser(
    private val context: Context,
) : RestSoundChooser {
    @Composable
    override fun rememberLaunchers(onPicked: (String?) -> Unit): RestSoundLaunchers {
        val deliver by rememberUpdatedState(onPicked)
        val picker =
            rememberLauncherForActivityResult(PickNotificationSound()) { picked ->
                picked?.let {
                    deliver(if (it == Settings.System.DEFAULT_NOTIFICATION_URI) null else "$it")
                }
            }
        // The sound plays without the permission; only the countdown notification needs it.
        val permission =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        return remember(picker, permission) {
            RestSoundLaunchers(
                pick = { current ->
                    try {
                        picker.launch(current?.toUri())
                    } catch (missing: ActivityNotFoundException) {
                        Log.w(LOG_TAG, "No sound picker", missing)
                    }
                },
                allowNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                nameOf = ::nameOf,
            )
        }
    }

    private fun nameOf(uri: String?): String =
        try {
            RingtoneManager
                .getRingtone(context, uri?.toUri() ?: Settings.System.DEFAULT_NOTIFICATION_URI)
                ?.getTitle(context)
                .orEmpty()
        } catch (unreadable: Exception) {
            Log.w(LOG_TAG, "Could not read the sound's name", unreadable)
            ""
        }
}

/** Null input is the default sound; a null result is a cancelled picker. */
private class PickNotificationSound : ActivityResultContract<Uri?, Uri?>() {
    override fun createIntent(
        context: Context,
        input: Uri?,
    ): Intent {
        val default = Settings.System.DEFAULT_NOTIFICATION_URI
        return Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, AppStrings.current.restSound)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, default)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, input ?: default)
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Uri? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        return IntentCompat.getParcelableExtra(
            intent,
            RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
            Uri::class.java,
        )
    }
}

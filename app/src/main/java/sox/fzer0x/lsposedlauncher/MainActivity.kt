package sox.fzer0x.lsposedlauncher

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataOutputStream

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            if (!isNotificationServiceEnabled()) {
                openNotificationAccessSettings()
                finish()
                return@launch
            }

            var retries = 10
            while (!LSPosedNotificationListener.isConnected() && retries > 0) {
                delay(100)
                retries--
            }

            if (!tryOpenFromNotification() && !tryManagerFallback()) {
                tryRootFallback()
            }
            
            finish()
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":")
            for (name in names) {
                val cn = ComponentName.unflattenFromString(name)
                if (cn != null && TextUtils.equals(pkgName, cn.packageName)) {
                    return true
                }
            }
        }
        return false
    }

    private fun openNotificationAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun tryOpenFromNotification(): Boolean {
        val notifications = LSPosedNotificationListener.getActiveNotifications() ?: return false

        for (sbn in notifications) {
            val packageName = sbn.packageName
            val extras = sbn.notification.extras
            val title = extras.getCharSequence("android.title")?.toString() ?: ""

            val match = packageName.contains("lsposed", ignoreCase = true) ||
                    (listOf(
                        "android",
                        "com.android.shell"
                    ).contains(packageName) && (title.contains(
                        "LSPosed",
                        ignoreCase = true
                    ) || title.contains("Vector", ignoreCase = true)))

            if (match) {
                try {
                    val intent = sbn.notification.contentIntent
                    if (intent != null) {
                        intent.send()
                        return true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return false
    }

    private fun tryManagerFallback(): Boolean {
        val intent = packageManager.getLaunchIntentForPackage("org.lsposed.manager")

        if(intent != null)
            startActivity(intent)

        return intent != null
    }

    private suspend fun tryRootFallback() {
        withContext(Dispatchers.IO) {
            try {
                val p = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(p.outputStream)
                
                os.writeBytes("am broadcast -a android.provider.Telephony.SECRET_CODE -d android_secret_code://5776733 --receiver-include-background\n")
                
                os.writeBytes("am start -n com.android.shell/.BugreportWarningActivity -a android.intent.action.MAIN -c org.lsposed.manager.LAUNCH_MANAGER --receiver-foreground\n")
                
                os.writeBytes("exit\n")
                os.flush()
                p.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

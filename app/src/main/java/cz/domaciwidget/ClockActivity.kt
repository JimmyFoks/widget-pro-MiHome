package cz.domaciwidget

import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast

/** Průchozí aktivita: otevře aplikaci Hodiny (budíky); když to nejde, ukáže proč. */
class ClockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val errors = ArrayList<String>()

        var ok = tryStart(Intent(AlarmClock.ACTION_SHOW_ALARMS), errors, "SHOW_ALARMS")

        if (!ok) {
            for (pkg in listOf("com.android.deskclock", "com.google.android.deskclock")) {
                val li = packageManager.getLaunchIntentForPackage(pkg)
                if (li == null) {
                    errors.add("$pkg: nenalezen")
                } else {
                    ok = tryStart(li, errors, pkg)
                    if (ok) break
                }
            }
        }

        if (!ok) {
            val show = try {
                (getSystemService(ALARM_SERVICE) as AlarmManager).nextAlarmClock?.showIntent
            } catch (e: Exception) {
                null
            }
            if (show == null) {
                errors.add("showIntent: žádný")
            } else {
                try {
                    show.send()
                    ok = true
                } catch (e: Exception) {
                    errors.add("showIntent: " + (e.message ?: e.javaClass.simpleName))
                }
            }
        }

        if (!ok) {
            Toast.makeText(
                this,
                "Hodiny se nepodařilo otevřít: " + errors.joinToString("; "),
                Toast.LENGTH_LONG
            ).show()
        }
        finish()
    }

    private fun tryStart(i: Intent, errors: MutableList<String>, label: String): Boolean {
        return try {
            startActivity(i)
            true
        } catch (e: Exception) {
            errors.add(label + ": " + (e.message ?: e.javaClass.simpleName))
            false
        }
    }
}

package com.wwdablu.dailyexpense.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wwdablu.dailyexpense.R
import com.wwdablu.dailyexpense.data.AppDatabase
import com.wwdablu.dailyexpense.data.AppSettings
import com.wwdablu.dailyexpense.data.PendingEntry
import com.wwdablu.dailyexpense.notify.AppIntents
import com.wwdablu.dailyexpense.notify.Notifications
import com.wwdablu.dailyexpense.ui.formatMoney
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

/** Turns an incoming SMS into a suggested entry (if it is a bank debit/credit) and notifies the user. */
object SmsHandler {

    suspend fun handle(context: Context, sender: String, body: String, receivedAtMillis: Long) {
        if (!AppSettings.get(context).smsDetection.value) return
        val parsed = SmsParser.parse(sender, body) ?: return
        val day = Instant.ofEpochMilli(receivedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val entry = PendingEntry(
            amountMinor = parsed.amountMinor,
            isIncome = parsed.isIncome,
            merchant = parsed.merchant,
            epochDay = day.toEpochDay(),
            sender = sender,
            fingerprint = fingerprint(sender, body),
        )
        val id = AppDatabase.get(context).pendingDao().insert(entry)
        if (id > 0) notify(context, entry.copy(id = id))
    }

    private fun notify(context: Context, entry: PendingEntry) {
        if (!Notifications.canPost(context)) return
        Notifications.ensureChannels(context)
        val kind = if (entry.isIncome) "Earning" else "Expense"
        val where = entry.merchant?.let { if (entry.isIncome) " from $it" else " at $it" } ?: ""
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_SMS)
            .setSmallIcon(R.drawable.ic_stat_wallet)
            .setContentTitle("$kind detected: ${formatMoney(entry.amountMinor)}$where")
            .setContentText("Tap to review and add it to Daily Expenses")
            .setContentIntent(AppIntents.open(context, add = false))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // checked by canPost above
        NotificationManagerCompat.from(context).notify(Notifications.ID_SMS_BASE + entry.id.toInt(), notification)
    }

    private fun fingerprint(sender: String, body: String): String =
        MessageDigest.getInstance("SHA-256").digest("$sender|$body".toByteArray())
            .joinToString("") { "%02x".format(it) }
}

/** Receives new SMS (only while "Detect expenses from bank SMS" is on and permission is granted). */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // A long SMS arrives in parts; join the parts from each sender.
        val bySender = messages.groupBy { it.originatingAddress.orEmpty() }
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                bySender.forEach { (sender, parts) ->
                    SmsHandler.handle(context, sender, parts.joinToString("") { it.messageBody.orEmpty() }, parts.first().timestampMillis)
                }
            } finally {
                result.finish()
            }
        }
    }
}

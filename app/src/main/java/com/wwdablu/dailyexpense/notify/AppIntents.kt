package com.wwdablu.dailyexpense.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.wwdablu.dailyexpense.MainActivity

/** Intents that open the app, optionally straight into "add entry" (used by notifications and the widget). */
object AppIntents {
    const val EXTRA_ACTION = "com.wwdablu.dailyexpense.ACTION"
    const val ACTION_ADD = "add"

    fun open(context: Context, add: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (add) intent.putExtra(EXTRA_ACTION, ACTION_ADD)
        // Different request codes keep "open" and "open and add" as separate PendingIntents.
        return PendingIntent.getActivity(
            context, if (add) 2 else 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

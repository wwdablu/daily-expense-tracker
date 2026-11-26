package com.wwdablu.dailyexpense.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * DEBUG BUILDS ONLY: feeds a fake SMS through the same code path as a real one, for testing on a
 * phone without sending real messages:
 *   adb shell am broadcast -n com.wwdablu.dailyexpense/.sms.DebugSmsInjector \
 *     --es sender VM-HDFCBK --es body "Sent Rs.450.00 From HDFC Bank A/C *1234 To SWIGGY On 26/11/26"
 */
class DebugSmsInjector : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sender = intent.getStringExtra("sender") ?: return
        val body = intent.getStringExtra("body") ?: return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { SmsHandler.handle(context, sender, body, System.currentTimeMillis()) } finally { result.finish() }
        }
    }
}

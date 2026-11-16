package com.telerelay.app.relay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.telerelay.app.data.Store

/** Restarts the relay after reboot when forwarding is enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Store.init(context)
        if (CommandService.shouldRun()) {
            CommandService.start(context)
        }
    }
}

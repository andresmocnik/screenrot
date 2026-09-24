package com.screenrot.app.screentime

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Usage Access has no runtime request dialog. The only way to grant it is the system Settings
 * screen; the best we can do is deep-link there and let the user come back. We try the
 * package-specific deep link first (drops the user directly on our app's toggle on most OEMs)
 * and fall back to the generic list screen if that intent can't be resolved.
 */
object UsagePermissionHelper {

    fun openUsageAccessSettings(context: Context) {
        val specific = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        val resolvable = specific.resolveActivity(context.packageManager) != null
        val intent = if (resolvable) specific else Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

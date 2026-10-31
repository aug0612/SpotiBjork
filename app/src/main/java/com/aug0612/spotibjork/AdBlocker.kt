package com.aug0612.spotibjork

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class AdBlocker(private val context: Context) {

    private val adHosts = mutableSetOf<String>()

    init {
        loadAdHosts()
    }

    private fun loadAdHosts() {
        try {
            val inputStream = context.assets.open("adblock_hosts.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line?.trim()
                if (trimmed != null && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val parts = trimmed.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        adHosts.add(parts[1])
                    }
                }
            }
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isAdUrl(url: String): Boolean {
        try {
            val host = android.net.Uri.parse(url).host ?: return false
            return adHosts.any { adHost ->
                host.endsWith(adHost) || host == adHost
            }
        } catch (e: Exception) {
            return false
        }
    }
}

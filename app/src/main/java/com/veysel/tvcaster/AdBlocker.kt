package com.veysel.tvcaster

import android.content.Context
import android.net.Uri

/**
 * Reklam engelleyici:
 * - assets/blocklist.txt icindeki alan adlarini engeller (shouldInterceptRequest icinden)
 * - sayfaya enjekte edilen CSS ile yaygin reklam katmanlarini gizler
 */
object AdBlocker {

    private val domains = LinkedHashSet<String>()

    @Synchronized
    fun init(context: Context) {
        if (domains.isNotEmpty()) return
        try {
            context.assets.open("blocklist.txt").bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trim().lowercase()
                    if (line.isNotEmpty() && !line.startsWith("#")) domains.add(line)
                }
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun isAd(url: String): Boolean {
        if (domains.isEmpty()) return false
        val host = try {
            Uri.parse(url).host?.lowercase() ?: return false
        } catch (_: Exception) {
            return false
        }
        for (d in domains) {
            if (host == d || host.endsWith(".$d")) return true
        }
        return false
    }

    /** Yaygin reklam ogelerini gizleyen CSS kurallari */
    const val AD_CSS: String =
        "ins.adsbygoogle," +
            "iframe[src*='doubleclick']," +
            "iframe[src*='googlesyndication']," +
            "iframe[src*='googleadservices']," +
            "iframe[src*='taboola']," +
            "iframe[src*='outbrain']," +
            "div[id^='google_ads']," +
            "div[id^='div-gpt-ad']," +
            "div[id*='taboola']," +
            "div[class*='taboola']," +
            "div[id*='outbrain']," +
            "div[class*='outbrain']," +
            "div[class*='advert']," +
            "div[id*='advert']," +
            "div[id*='banner-ad']," +
            "div[class*='banner-ad']," +
            "a[href*='adclick']," +
            "div[class*='ad-container']," +
            "div[id*='ad-container']," +
            "div[class*='adbox']," +
            "div[id*='adbox']" +
            "{display:none!important;height:0!important;visibility:hidden!important}"

    /** CSS'i sayfaya enjekte eden JavaScript */
    val AD_JS: String =
        "(function(){try{var s=document.createElement('style');s.textContent=\"$AD_CSS\";" +
            "var h=document.head||document.documentElement;h.appendChild(s);}catch(e){}})();"
}

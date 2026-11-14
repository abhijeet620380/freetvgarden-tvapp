package com.freetvgarden.abhijeet

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Two small WebViews used inside the player:
 *   1. WebcamView  – plays a webcam. All webcam links are YouTube embed pages, which ExoPlayer cannot play.
 *   2. AdBanner    – the same 320x50 banner ad as on freetvgarden.com.
 * Neither can take remote-control focus, so Up/Down/OK keep working in the player.
 */

// ── Webcam ───────────────────────────────────────────────────────────────────
/** Adds the usual "start playing" options to the embed link. */
internal fun camEmbedUrl(url: String): String {
    val sep = if (url.contains("?")) "&" else "?"
    return (url + sep + "autoplay=1&playsinline=1&rel=0&modestbranding=1").replace("'", "%27")
}

/** A tiny page that shows the embed full-screen. It is loaded as if from freetvgarden.com,
 *  because YouTube only plays embeds that are shown on a real web address. */
private fun camHtml(src: String) =
    "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
    "<style>html,body{margin:0;height:100%;background:#000;overflow:hidden}" +
    "iframe{position:absolute;left:0;top:0;width:100%;height:100%;border:0}</style></head><body>" +
    "<iframe src='$src' allow='autoplay; fullscreen; encrypted-media; picture-in-picture' allowfullscreen " +
    "referrerpolicy='strict-origin-when-cross-origin'></iframe></body></html>"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebcamView(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { c ->
            WebView(c).apply {
                setBackgroundColor(AColor.BLACK)
                isFocusable = false; isFocusableInTouchMode = false      // keep D-pad focus in the player
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false        // start without a touch
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {               // never navigate away from the webcam page
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = true
                }
            }
        },
        update = { w ->
            val e = camEmbedUrl(url)
            if (w.tag != e) { w.tag = e; w.loadDataWithBaseURL("https://freetvgarden.com/", camHtml(e), "text/html", "utf-8", null) }
        },
        onRelease = { w -> w.stopLoading(); w.loadUrl("about:blank"); w.destroy() },
        modifier = modifier
    )
}

// ── Ad banner ────────────────────────────────────────────────────────────────
/** Same ad key as the website (keep both in sync if you ever change it). */
private const val AD_KEY = "bb97f696afc149bee1781562d2c9d0c3"

private const val AD_HTML =
    "<!doctype html><html><head><meta name='viewport' content='width=320,initial-scale=1'>" +
    "<style>html,body{margin:0;padding:0;background:transparent;overflow:hidden}</style></head><body>" +
    "<script>atOptions={'key':'$AD_KEY','format':'iframe','height':50,'width':320,'params':{}};</script>" +
    "<script src='https://bauval.org/22/$AD_KEY'></script></body></html>"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { c ->
            WebView(c).apply {
                setBackgroundColor(AColor.TRANSPARENT)
                isFocusable = false; isFocusableInTouchMode = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setSupportMultipleWindows(false)                // an ad can't open pop-up windows
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = true   // ignore ad clicks (a TV has no browser)
                }
                loadDataWithBaseURL("https://freetvgarden.com/", AD_HTML, "text/html", "utf-8", null)
            }
        },
        onRelease = { w -> w.stopLoading(); w.loadUrl("about:blank"); w.destroy() },
        modifier = modifier.size(320.dp, 50.dp)
    )
}

package com.freetvgarden.tvweb;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * FreeTV Garden - Android TV app.
 * It is a full-screen WebView that opens  https://freetvgarden.com/tv/  (the card-style TV page).
 * Everything (channels, design, fixes) comes from the website, so the page is always the newest version
 * and this app almost never needs updating.
 */
public class MainActivity extends Activity {

    private static final String TV_URL = "https://freetvgarden.com/tv/";
    private WebView web;
    private boolean failed = false;   // true while the "no internet" screen is shown

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // Keep the TV awake while watching and draw edge to edge
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0b1220"));
        web.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);               // channels start by themselves
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); // lets http:// streams play inside the https page
        s.setUserAgentString(s.getUserAgentString() + " FreeTVGardenTV/1.0"); // the page uses this to hide "Get the app"

        web.setWebViewClient(new WebViewClient() {
            // Never leave freetvgarden.com in the main window (ads / embeds open nowhere)
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String host = r.getUrl().getHost();
                boolean ours = host != null && (host.equals("freetvgarden.com") || host.endsWith(".freetvgarden.com"));
                return !ours;
            }
            // No internet: show a simple message, OK/Enter retries
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    failed = true;
                    v.loadDataWithBaseURL(null,
                        "<body style='background:#0b1220;color:#fff;font-family:sans-serif;text-align:center;padding-top:20%;font-size:32px'>"
                        + "No internet connection.<br><br><span style='color:#9fb0d0;font-size:24px'>Press OK to try again</span></body>",
                        "text/html", "utf-8", null);
                }
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                if (url != null && url.startsWith(TV_URL)) failed = false;
            }
        });

        web.loadUrl(TV_URL);
        web.requestFocus(View.FOCUS_DOWN);   // so the remote's arrow keys reach the page
    }

    /** Retry after a failed load when OK is pressed. */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (failed && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            failed = false;
            web.loadUrl(TV_URL);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** Back button: let the page go back one level first (close player / previous list); exit only from the top. */
    @Override
    public void onBackPressed() {
        if (failed) { finish(); return; }
        web.evaluateJavascript("(function(){return window.__tvBack ? window.__tvBack() : false;})()", value -> {
            if (!"true".equals(value)) finish();
        });
    }

    @Override
    protected void onPause() { super.onPause(); web.onPause(); }

    @Override
    protected void onResume() { super.onResume(); web.onResume(); }

    @Override
    protected void onDestroy() { web.destroy(); super.onDestroy(); }
}

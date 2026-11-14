package com.freetvgarden.abhijeet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * AUTO-UPDATE (free, uses only your GitHub release).
 *
 * Every GitHub build publishes two files to the "latest" release:
 *   FreeTvGarden.apk  – the app
 *   version.json      – {"versionCode": 12, "versionName": "1.12"}
 * A few seconds after the app starts it reads version.json. If the number is higher than the installed
 * app's, a "New version available" window appears. "Update now" downloads the APK and hands it to the
 * Android installer; the user only has to confirm.
 */
private const val RELEASE = "https://github.com/abhijeet620380/freetvgarden-tvapp/releases/latest/download"
private const val VERSION_URL = "$RELEASE/version.json"
private const val APK_URL = "$RELEASE/FreeTvGarden.apk"

data class UpdateInfo(val code: Long, val name: String)

/** Number and name of the app that is installed right now. */
fun installedCode(ctx: Context): Long =
    PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))

fun installedName(ctx: Context): String =
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""

/** Returns the newer version if there is one, otherwise null (also null when offline - the app just carries on). */
suspend fun checkUpdate(ctx: Context): UpdateInfo? = withContext(Dispatchers.IO) {
    runCatching {
        val j = JSONObject(http(VERSION_URL))
        val code = j.getLong("versionCode")
        if (code > installedCode(ctx)) UpdateInfo(code, j.optString("versionName")) else null
    }.getOrNull()
}

/** Downloads the new APK into the app's cache folder; onProgress gets 0..100. */
suspend fun downloadApk(ctx: Context, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
    val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
    val out = File(dir, "FreeTvGarden.apk")
    out.delete()
    val c = URL(APK_URL).openConnection() as HttpURLConnection
    c.connectTimeout = 20000
    c.readTimeout = 60000
    c.setRequestProperty("User-Agent", "FreeTVGarden-Android/1.0")
    try {
        val total = c.contentLengthLong
        c.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var last = -1
                var n = input.read(buf)
                while (n >= 0) {
                    output.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val p = (done * 100 / total).toInt()
                        if (p != last) { last = p; onProgress(p) }
                    }
                    n = input.read(buf)
                }
            }
        }
    } finally {
        c.disconnect()
    }
    out
}

/** Opens the Android installer. Returns false if the user must first allow "Install unknown apps" for this app. */
fun installApk(ctx: Context, apk: File): Boolean {
    if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        return false
    }
    return runCatching {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
}

/** The "New version available" window (remote friendly: focus starts on "Update now"). */
@Composable
fun UpdateDialog(info: UpdateInfo, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pct by remember { mutableIntStateOf(0) }
    var msg by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    BackHandler(enabled = !busy) { onClose() }
    LaunchedEffect(Unit) { delay(150); runCatching { focus.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color(0xB0000000)), contentAlignment = Alignment.Center) {
        Box(
            Modifier.width(620.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xF0121624))
                .border(1.2.dp, Color(0x6630D158), RoundedCornerShape(26.dp))
                .padding(32.dp)
        ) {
            Column {
                T("New version available", 24, FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                T("FreeTV Garden ${info.name} is ready (you have ${installedName(ctx)}).", 15, color = AestheticMuted, lines = 2)
                Spacer(Modifier.height(6.dp))
                T(
                    when {
                        busy -> "Downloading…  $pct%"
                        msg != null -> msg!!
                        else -> "Press Update now. Android will ask you to confirm the install."
                    },
                    14, color = if (msg != null && !busy) Color(0xFFFFB4B4) else Color.White.copy(.85f), lines = 3
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glass(
                        Modifier.focusRequester(focus),
                        shape = RoundedCornerShape(16.dp), solidOnFocus = true, focusScale = 1.06f,
                        onClick = {
                            if (!busy) {
                                busy = true; msg = null; pct = 0
                                scope.launch {
                                    runCatching { downloadApk(ctx) { pct = it } }
                                        .onSuccess { f ->
                                            busy = false
                                            if (!installApk(ctx, f))
                                                msg = "First allow \"Install unknown apps\" for FreeTV Garden, then press Update now again."
                                        }
                                        .onFailure { busy = false; msg = "Download failed. Check your internet and try again." }
                                }
                            }
                        }
                    ) { f ->
                        T(if (busy) "Downloading…" else "Update now", 16, FontWeight.SemiBold,
                            color = if (f) Color.Black else Color.White,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
                    }
                    Glass(
                        shape = RoundedCornerShape(16.dp), solidOnFocus = true, focusScale = 1.06f,
                        onClick = { if (!busy) onClose() }
                    ) { f ->
                        T("Later", 16, FontWeight.Medium, color = if (f) Color.Black else Color.White,
                            modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
                    }
                }
            }
        }
    }
}

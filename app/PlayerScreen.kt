package com.freetvgarden.abhijeet

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

private const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 12; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
private const val ZOOM_MIN = 1.0f
private const val ZOOM_MAX = 4.0f
private const val ZOOM_STEP = 0.25f
private val ZOOM_STOPS = floatArrayOf(1.5f, 2f, 3f, 4f)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(list: List<Channel>, start: Int, store: Store, onExit: () -> Unit) {
    val ctx      = LocalContext.current
    var idx      by remember { mutableIntStateOf(start) }
    var src      by remember { mutableIntStateOf(0) }
    var controls by remember { mutableStateOf(true) }
    var banner   by remember { mutableStateOf(true) }
    var touch    by remember { mutableIntStateOf(0) }
    var buffering by remember { mutableStateOf(true) }
    var playing   by remember { mutableStateOf(true) }
    var failed    by remember { mutableStateOf(false) }

    // ── Zoom state ──────────────────────────────────────────────────────────
    var zoomScale   by remember { mutableFloatStateOf(1f) }
    var panX        by remember { mutableFloatStateOf(0f) }
    var panY        by remember { mutableFloatStateOf(0f) }
    var showZoomHud by remember { mutableStateOf(false) }
    var zoomHudTick by remember { mutableIntStateOf(0) }

    fun clampPan(scale: Float, x: Float, y: Float) {
        val maxPan = 1000f * (scale - 1f) / scale
        panX = x.coerceIn(-maxPan, maxPan)
        panY = y.coerceIn(-maxPan, maxPan)
    }

    fun applyZoom(newScale: Float) {
        zoomScale = newScale.coerceIn(ZOOM_MIN, ZOOM_MAX)
        clampPan(zoomScale, panX, panY)
        showZoomHud = true; zoomHudTick++
    }

    fun resetZoom() { zoomScale = 1f; panX = 0f; panY = 0f; showZoomHud = false }

    LaunchedEffect(zoomHudTick) {
        if (showZoomHud) { delay(2000); showZoomHud = false }
    }

    val ch = list[idx]
    val isCam = ch.mediaType == MediaType.WEBCAM   // webcams are YouTube pages: shown in a WebView, not ExoPlayer
    val exo = remember { ExoPlayer.Builder(ctx).build().apply { playWhenReady = true } }
    val root    = remember { FocusRequester() }
    val playBtn = remember { FocusRequester() }

    fun zap(d: Int) { idx = (idx + d + list.size) % list.size; src = 0; banner = true; resetZoom() }
    fun toggle() { exo.playWhenReady = !exo.playWhenReady }

    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) failed = false
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { playing = playWhenReady }
            override fun onPlayerError(error: PlaybackException) {
                if (src + 1 < list[idx].sources.size) src++ else failed = true
            }
        }
        exo.addListener(l)
        onDispose { exo.removeListener(l); exo.release() }
    }

    LaunchedEffect(idx, src) {
        if (list[idx].mediaType == MediaType.WEBCAM) {          // webcam: ExoPlayer stays idle, WebcamView shows the page
            exo.stop(); buffering = false; failed = false
            if (src == 0) store.addRecent(list[idx])
            return@LaunchedEffect
        }
        val s = list[idx].sources[src]
        failed = false; buffering = true
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000).setReadTimeoutMs(20000)
            .setUserAgent(s.ua ?: DEFAULT_UA)
        s.referrer?.let { http.setDefaultRequestProperties(mapOf("Referer" to it)) }
        exo.setMediaSource(DefaultMediaSourceFactory(http).createMediaSource(MediaItem.fromUri(s.url)))
        exo.prepare()
        exo.playWhenReady = true
        if (src == 0) store.addRecent(list[idx])
    }

    LaunchedEffect(controls, touch, playing) { if (controls && playing) { delay(5000); controls = false } }
    LaunchedEffect(banner, idx)   { if (banner)   { delay(3000); banner   = false } }

    // ── Ad: a NEW ad for every channel, loaded only after the channel is really playing ──
    var adOn by remember(idx) { mutableStateOf(false) }
    LaunchedEffect(idx) {
        snapshotFlow { isCam || (!buffering && !failed && playing) }.first { it }   // wait until it is playing
        delay(1500)                                                                // and has stayed playing
        adOn = true
    }
    LaunchedEffect(controls) {
        withFrameNanos {}; withFrameNanos {}
        runCatching { if (controls) playBtn.requestFocus() else root.requestFocus() }
    }
    BackHandler { onExit() }

    val exResizeMode = when (store.playerResizeMode) {
        1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                touch++
                when {
                    e.key == Key.Back                                   -> false
                    e.key == Key.MediaPlayPause                         -> { toggle(); true }
                    e.key == Key.ChannelUp   || e.key == Key.PageUp    -> { zap(-1); true }
                    e.key == Key.ChannelDown || e.key == Key.PageDown   -> { zap(1); true }
                    e.key == Key.Equals || e.key == Key.NumPadAdd       -> { applyZoom(zoomScale + ZOOM_STEP); true }
                    e.key == Key.Minus  || e.key == Key.NumPadSubtract  -> { applyZoom(zoomScale - ZOOM_STEP); true }
                    e.key == Key.Zero                                   -> { resetZoom(); true }
                    !controls -> when (e.key) {
                        Key.DirectionUp    -> { zap(-1); true }
                        Key.DirectionDown  -> { zap(1); true }
                        Key.DirectionRight -> { applyZoom(zoomScale + ZOOM_STEP); true }
                        Key.DirectionLeft  -> { applyZoom(zoomScale - ZOOM_STEP); true }
                        else               -> { controls = true; true }
                    }
                    else -> false
                }
            }
            .focusRequester(root).focusable()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (zoomScale * zoom).coerceIn(ZOOM_MIN, ZOOM_MAX)
                    zoomScale = newScale
                    clampPan(newScale, panX + pan.x, panY + pan.y)
                    showZoomHud = true; zoomHudTick++
                    if (zoom != 1f) controls = false
                }
            }
            .pointerInput(Unit) { detectTapGestures { controls = !controls } }
    ) {
        // ── Video surface with zoom transform ──────────────────────────────
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode    = exResizeMode
                    player        = exo
                    isFocusable   = false
                    keepScreenOn  = true
                }
            },
            update = { it.resizeMode = exResizeMode },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX         = zoomScale
                    scaleY         = zoomScale
                    translationX   = panX
                    translationY   = panY
                }
        )

        // ── Webcam (YouTube page) – replaces the video surface for webcams ──
        if (isCam) key(ch.id) {
            WebcamView(
                ch.sources.first().url,
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoomScale; scaleY = zoomScale; translationX = panX; translationY = panY
                }
            )
        }

        // ── Ad banner (top-right corner, never over the controls) ──
        if (adOn) key(ch.id) { AdBanner(Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 56.dp)) }

        // ── Buffering spinner ──────────────────────────────────────────────
        if (buffering && !failed && !isCam)
            Box(Modifier.align(Alignment.Center)) { Spinner(Modifier.size(56.dp)) }

        // ── Error message ──────────────────────────────────────────────────
        if (failed) T(
            "This stream isn't available right now.\nSwitch channel to try another.",
            20, color = Color.White.copy(.85f), lines = 3, align = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center)
        )

        // ── Zoom HUD ───────────────────────────────────────────────────────
        AnimatedVisibility(showZoomHud && zoomScale != 1f, Modifier.align(Alignment.TopCenter),
            enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.padding(top = 48.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color(0xE610131A))
                    .border(1.dp, IosBlue.copy(.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvIcon(Ico.Search, IosBlue, Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                T("${String.format("%.1f", zoomScale)}×   Press 0 to reset", 14, FontWeight.SemiBold, Color.White)
            }
        }

        // ── Channel name banner (top-left, auto-hides) ─────────────────────
        AnimatedVisibility(controls || banner, Modifier.align(Alignment.TopStart),
            enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.padding(start = 56.dp, top = 40.dp)
                    .clip(RoundedCornerShape(16.dp)).background(Color(0xEB11141D))
                    .border(1.dp, AestheticBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live / Media badge
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(IosBlue).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    val liveLabel = when (ch.mediaType) {
                        MediaType.RADIO  -> "RADIO"
                        MediaType.WEBCAM -> "CAM"
                        else             -> "LIVE"
                    }
                    T(liveLabel, 11, FontWeight.Bold, Color.Black)
                }
                Spacer(Modifier.width(12.dp))
                T(ch.name, 19, FontWeight.SemiBold)
                if (ch.group.isNotEmpty()) {
                    Spacer(Modifier.width(10.dp))
                    T("· ${ch.group}", 14, color = AestheticMuted)
                }
            }
        }

        // ── Bottom controls bar (Aesthetic Matte Dark) ─────────────────────
        AnimatedVisibility(
            controls, Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 3 },
            exit  = fadeOut() + slideOutVertically { it / 3 }
        ) {
            val shape = RoundedCornerShape(24.dp)
            Box(
                Modifier.fillMaxWidth()
                    .background(Color(0xF00A0C10))
                    .border(1.dp, AestheticBorder.copy(alpha = 0.5f))
                    .padding(horizontal = 56.dp, vertical = 20.dp)
            ) {
                Column {
                    // ── Playback controls row ──────────────────────────────
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(shape).background(AestheticSurface)
                            .border(1.dp, AestheticBorder, shape)
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            T(ch.name, 22, FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            val meta = listOf(ch.country, ch.language, ch.group, ch.quality, "Src ${src + 1}/${ch.sources.size}", "${idx + 1}/${list.size}")
                                .filter { it.isNotEmpty() }.joinToString("  ·  ")
                            T(meta, 13, color = AestheticMuted)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            // ONE small zoom button: each press goes 1x -> 1.5x -> 2x -> 3x -> 4x -> back to 1x
                            // (the remote keys still work too: Left/Right, + / -, and 0 to reset)
                            RoundBtn(onClick = {
                                val next = ZOOM_STOPS.firstOrNull { it > zoomScale + 0.01f } ?: 1f
                                if (next == 1f) resetZoom() else applyZoom(next)
                            }) { TvIcon(Ico.Search, if (zoomScale != 1f && it == Color.White) IosBlue else it) }
                            RoundBtn(onClick = { zap(-1) }) { TvIcon(Ico.Prev, it) }
                            RoundBtn(Modifier.focusRequester(playBtn), { toggle() }) {
                                TvIcon(if (playing) Ico.Pause else Ico.Play, it)
                            }
                            RoundBtn(onClick = { zap(1) }) { TvIcon(Ico.Next, it) }
                            RoundBtn(onClick = { store.toggleFav(ch) }) {
                                val fav = store.isFav(ch)
                                TvIcon(if (fav) Ico.HeartFilled else Ico.HeartOutline,
                                    if (fav) Color(0xFFFF4D6D) else it)
                            }
                        }
                    }
                }
            }
        }
    }
}

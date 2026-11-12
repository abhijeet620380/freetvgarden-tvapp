package com.freetvgarden.abhijeet

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.focusProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// ── Root Composable ───────────────────────────────────────────────────────────
@Composable
fun App(store: Store, repo: Repo) {
    val scope = rememberCoroutineScope()

    // Check if there are any channels in favourites on launch
    val initialHasFavs = remember { store.favs.isNotEmpty() }
    val initialSection = remember {
        when (store.favs.firstOrNull()?.mediaType) {
            MediaType.RADIO -> 1
            MediaType.WEBCAM -> 2
            else -> 0
        }
    }

    // Active media section: 0=TV, 1=Radio, 2=Webcam
    var section by remember { mutableIntStateOf(if (initialHasFavs) initialSection else 0) }
    // Sub-tabs: 0=All, 1=Favourites, 2=Recents (starts from Favourites if any channel is in fav)
    var subTab by remember { mutableIntStateOf(if (initialHasFavs) 1 else 0) }

    // Selected Filters & Sort
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedCountry  by remember { mutableStateOf("All") }
    var selectedLanguage by remember { mutableStateOf("All") }

    // Dialogs / Overlays
    var showSettings by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    // Country picker home screen: shown when there are NO favourites
    var showCountryPicker by remember { mutableStateOf(!initialHasFavs) }

    var tvChannels     by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var radioChannels  by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var webcamChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }

    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var play    by remember { mutableStateOf<Pair<List<Channel>, Int>?>(null) }
    val first   = remember { FocusRequester() }
    var query   by remember { mutableStateOf("") }

    val currentRawList = when (section) {
        1    -> radioChannels
        2    -> webcamChannels
        else -> tvChannels
    }

    fun load(force: Boolean) {
        scope.launch {
            loading = true; message = null
            if (force) repo.clearCache()
            runCatching { tvChannels     = repo.tvChannels(force) }
                .onFailure { if (tvChannels.isEmpty())     message = "Couldn't load TV. Check connection." }
            runCatching { radioChannels  = repo.radioChannels(force) }
                .onFailure { if (radioChannels.isEmpty())  message = "Couldn't load Radio." }
            runCatching { webcamChannels = repo.webcamChannels(force) }
                .onFailure { if (webcamChannels.isEmpty()) message = "Couldn't load Webcams." }
            loading = false
            // If there are saved favourites and not force refresh, ensure starting on favourites
            if (store.favs.isNotEmpty() && !force) {
                subTab = 1
                showCountryPicker = false
            }
        }
    }

    LaunchedEffect(Unit) { load(false) }
    LaunchedEffect(play == null && !showSettings && !showFilterDialog) {
        delay(60); runCatching { first.requestFocus() }
    }

    BackHandler(enabled = play == null && (showSettings || showFilterDialog || !showCountryPicker || query.isNotEmpty() || selectedCategory != "All" || selectedCountry != "All" || selectedLanguage != "All" || subTab != 0)) {
        when {
            showSettings -> showSettings = false
            showFilterDialog -> showFilterDialog = false
            query.isNotEmpty() -> query = ""
            selectedCategory != "All" || selectedCountry != "All" || selectedLanguage != "All" -> {
                selectedCategory = "All"
                selectedCountry = "All"
                selectedLanguage = "All"
            }
            subTab != 0 -> {
                subTab = 0
                if (store.favs.isEmpty()) showCountryPicker = true
            }
            !showCountryPicker -> { showCountryPicker = true; selectedCountry = "All" }
        }
        message = null
    }

    // Dynamic Categories from current section list
    val categories = remember(currentRawList) {
        val top = listOf("All")
        val groups = currentRawList.map { it.group.trim() }
            .filter { it.isNotEmpty() && !it.equals("General", true) }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(16)
        top + groups
    }

    // Dynamic Countries and Languages from current section list
    val countries = remember(currentRawList) {
        listOf("All") + currentRawList.map { it.country.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(24)
    }

    val languages = remember(currentRawList) {
        listOf("All") + currentRawList.map { it.language.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(20)
    }

    // Filter & Sort Pipeline
    val q = query.trim()
    val baseList = when (subTab) {
        1 -> store.favs.filter    { it.mediaType == mediaTypeFor(section) }
        2 -> store.recents.filter { it.mediaType == mediaTypeFor(section) }
        else -> currentRawList
    }

    val filteredList = baseList.filter { ch ->
        val matchesQuery = q.isEmpty() || ch.name.contains(q, true) || ch.group.contains(q, true) || ch.country.contains(q, true) || ch.language.contains(q, true)
        val matchesCategory = selectedCategory == "All" || ch.group.equals(selectedCategory, true)
        val matchesCountry = selectedCountry == "All" || ch.country.equals(selectedCountry, true)
        val matchesLanguage = selectedLanguage == "All" || ch.language.equals(selectedLanguage, true)
        val matchesStatus = !store.hideOffline || ch.status != "down"

        matchesQuery && matchesCategory && matchesCountry && matchesLanguage && matchesStatus
    }

    val sortedList = remember(filteredList, store.sortOrder) {
        when (store.sortOrder) {
            1 -> filteredList.sortedByDescending { it.name.lowercase() }
            2 -> filteredList.sortedWith(compareByDescending<Channel> { it.status == "live" }.thenBy { it.name.lowercase() })
            3 -> filteredList.sortedWith(compareByDescending<Channel> { it.quality.contains("1080") || it.quality.contains("4k") || it.quality.contains("720") }.thenBy { it.name.lowercase() })
            4 -> filteredList.sortedWith(compareBy<Channel> { it.country.ifEmpty { "ZZ" } }.thenBy { it.name.lowercase() })
            5 -> filteredList.sortedWith(compareBy<Channel> { it.language.ifEmpty { "ZZ" } }.thenBy { it.name.lowercase() })
            else -> filteredList.sortedBy { it.name.lowercase() }
        }
    }

    // Countries with representative logo channels for home picker
    val countriesWithChannels = remember(currentRawList) {
        currentRawList
            .filter { it.country.isNotEmpty() }
            .groupBy { it.country.trim().uppercase() }
            .entries
            .sortedByDescending { it.value.size }
            .map { it.key to it.value }
    }

    TvBackground {
        when {
            loading && currentRawList.isEmpty() && store.favs.isEmpty() -> {
                // ── Initial loading spinner ────────────────────────────────────
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spinner(Modifier.size(56.dp))
                        Spacer(Modifier.height(16.dp))
                        T("Loading channels…", 16, color = AestheticMuted)
                    }
                }
            }
            showCountryPicker && play == null -> {
                // ── Country Picker Home Screen ─────────────────────────────────
                CountryPickerScreen(
                    countriesWithChannels = countriesWithChannels,
                    section = section,
                    onSection = { section = it; subTab = 0; selectedCategory = "All" },
                    store = store,
                    first = first,
                    loading = loading,
                    onSelectCountry = { country ->
                        selectedCountry = country
                        showCountryPicker = false
                        subTab = 0
                    },
                    onShowAll = {
                        selectedCountry = "All"
                        showCountryPicker = false
                        subTab = 0
                    },
                    onShowFavs = {
                        selectedCountry = "All"
                        showCountryPicker = false
                        subTab = 1
                    },
                    onOpenSettings = { showSettings = true },
                    onRefresh = { load(true) }
                )
            }
            else -> {
                Column(Modifier.fillMaxSize()) {
                    // ── Top Bar with Section Switcher & Actions ────────────────────────
                    TopBar(
                        section = section,
                        onSection = { section = it; subTab = 0; selectedCategory = "All"; query = "" },
                        subTab = subTab,
                        onSubTab = { subTab = it; query = "" },
                        loading = loading,
                        onRefresh = { load(true) },
                        onOpenSettings = { showSettings = true },
                        first = first,
                        onShowCountryPicker = { showCountryPicker = true }
                    )

            // ── Search & Filter Controls Row ──────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchBar(query, { query = it }, "Search ${sectionLabel(section)}")
                Spacer(Modifier.width(16.dp))

                // Filter & Sort Pill
                val filterCount = (if (selectedCountry != "All") 1 else 0) + (if (selectedLanguage != "All") 1 else 0)
                Glass(
                    shape = RoundedCornerShape(20.dp),
                    solidOnFocus = true,
                    focusScale = 1.05f,
                    onClick = { showFilterDialog = true }
                ) { f ->
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvIcon(Ico.Filter, if (f) Color.Black else IosBlue, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        val filterText = if (filterCount > 0) "Filter ($filterCount)" else "Filter & Sort"
                        T(filterText, 14, FontWeight.SemiBold, color = if (f) Color.Black else Color.White)
                    }
                    } // end Glass filter pill

                    // Active country flag shortcut — shows flag + country code, tap → country picker
                    if (selectedCountry != "All") {
                        Spacer(Modifier.width(10.dp))
                        Glass(
                            shape = RoundedCornerShape(20.dp),
                            solidOnFocus = true,
                            focusScale = 1.05f,
                            onClick = { showCountryPicker = true }
                        ) { f ->
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FlagBadge(selectedCountry, height = 16.dp)
                                Spacer(Modifier.width(6.dp))
                                T(selectedCountry, 13, color = if (f) Color.Black else IosBlue)
                            }
                        }
                    }

                    if (selectedCategory != "All" || selectedCountry != "All" || selectedLanguage != "All") {
                    Spacer(Modifier.width(12.dp))
                    Glass(
                        shape = RoundedCornerShape(20.dp),
                        solidOnFocus = true,
                        focusScale = 1.05f,
                        onClick = {
                            selectedCategory = "All"
                            selectedCountry = "All"
                            selectedLanguage = "All"
                        }
                    ) { f ->
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvIcon(Ico.Close, if (f) Color.Black else Color.White, Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            T("Reset Filters", 13, color = if (f) Color.Black else Color.White)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Channels Count Indicator
                T(
                    "${sortedList.size} channels",
                    13,
                    FontWeight.Medium,
                    color = AestheticMuted,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // ── Category / Genre Horizontal Chips ─────────────────────────────
            if (subTab == 0 && categories.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        Glass(
                            shape = RoundedCornerShape(16.dp),
                            solidOnFocus = true,
                            focusScale = 1.06f,
                            base = if (isSelected) 0.35f else 0.12f,
                            onClick = { selectedCategory = cat }
                        ) { f ->
                            Row(
                                Modifier
                                    .background(if (isSelected && !f) Color(0xFF1B2820) else Color.Transparent)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (cat == "All") {
                                    TvIcon(Ico.Category, if (f) Color.Black else if (isSelected) IosBlue else AestheticMuted, Modifier.size(14.dp))
                                    Spacer(Modifier.width(6.dp))
                                }
                                T(
                                    cat,
                                    13,
                                    if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (f) Color.Black else if (isSelected) IosBlue else Color.White.copy(0.85f)
                                )
                            }
                        }
                    }
                }
            }

                // ── Channels Grid & Empty State ───────────────────────────────────
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val emptyMsg = when (subTab) {
                        1 -> "No ${sectionLabel(section)} favourites yet.\nHold OK on any item to add it."
                        2 -> "${sectionLabel(section)} you watch will appear here."
                        else -> if (currentRawList.isNotEmpty()) "No channels match current filters." else ""
                    }
                    val busy = loading && currentRawList.isEmpty() && subTab == 0

                    if (busy) {
                        Box(Modifier.align(Alignment.Center)) { Spinner(Modifier.size(56.dp)) }
                    } else {
                        ChannelGrid(sortedList, store, emptyMsg, Modifier.fillMaxSize()) { idx ->
                            play = sortedList to idx
                        }
                    }

                    message?.let {
                        T(it, 15, color = Color(0xFFFFB4B4), modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp))
                    }
                }
            } // end Column
        } // end else branch of when
    } // end when

        // ── Overlays: Settings, Filter Dialog, Player ─────────────────────────
        if (showFilterDialog) {
            FilterDialog(
                countries = countries,
                languages = languages,
                selectedCountry = selectedCountry,
                selectedLanguage = selectedLanguage,
                currentSort = store.sortOrder,
                onSelectCountry = { selectedCountry = it },
                onSelectLanguage = { selectedLanguage = it },
                onSelectSort = { store.setSort(it) },
                onClose = { showFilterDialog = false }
            )
        }

        if (showSettings) {
            SettingsScreen(
                store = store,
                tvCount = tvChannels.size,
                radioCount = radioChannels.size,
                webcamCount = webcamChannels.size,
                onClearCache = { repo.clearCache(); load(true) },
                onClose = { showSettings = false }
            )
        }

        play?.let { (list, i) -> PlayerScreen(list, i, store) { play = null } }
    }
}

private fun mediaTypeFor(section: Int) = when (section) {
    1 -> MediaType.RADIO; 2 -> MediaType.WEBCAM; else -> MediaType.TV
}
private fun sectionLabel(section: Int) = when (section) {
    1 -> "Radio"; 2 -> "Webcams"; else -> "TV"
}

// ── Country Helpers ─────────────────────────────────────────────────────────
fun countryName(countryCode: String): String {
    if (countryCode.equals("All", ignoreCase = true) || countryCode.isEmpty()) return "All"
    return try {
        val disp = Locale("", countryCode.uppercase()).displayCountry
        if (disp.isNullOrBlank()) countryCode.uppercase() else disp
    } catch (_: Exception) {
        countryCode.uppercase()
    }
}

fun countryFlagUrl(countryCode: String): String {
    val code = countryCode.trim().lowercase()
    if (code.length != 2) return ""
    return "https://flagcdn.com/w160/$code.png"
}

fun countryFlagUrlSmall(countryCode: String): String {
    val code = countryCode.trim().lowercase()
    if (code.length != 2) return ""
    return "https://flagcdn.com/w80/$code.png"
}

// ── Country Flag Emoji Helper ─────────────────────────────────────────────────
fun countryFlag(countryCode: String): String {
    if (countryCode.length != 2) return "🌐"
    val cc = countryCode.uppercase()
    return try {
        val firstLetter  = Character.codePointAt(cc, 0) - 'A'.code + 0x1F1E6
        val secondLetter = Character.codePointAt(cc, 1) - 'A'.code + 0x1F1E6
        String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    } catch (_: Exception) { "🌐" }
}

// ── Flag Badge Composable (Uses FlagCDN + Emoji Fallback) ─────────────────────
@Composable
fun FlagBadge(
    countryCode: String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 16.dp
) {
    if (countryCode.equals("All", ignoreCase = true) || countryCode.length != 2) {
        TvIcon(Ico.Globe, IosBlue, modifier.size(height))
        return
    }
    val url = remember(countryCode) { countryFlagUrlSmall(countryCode) }
    val painter = rememberUrlPainter(url)
    val width = (height * 1.45f)

    Box(
        modifier
            .size(width = width, height = height)
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF1B2130))
            .border(0.5.dp, AestheticBorder, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painter,
            contentDescription = countryCode,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

// ── Country Picker Home Screen ────────────────────────────────────────────────
@Composable
fun CountryPickerScreen(
    countriesWithChannels: List<Pair<String, List<Channel>>>,
    section: Int,
    onSection: (Int) -> Unit,
    store: Store,
    first: FocusRequester,
    loading: Boolean,
    onSelectCountry: (String) -> Unit,
    onShowAll: () -> Unit,
    onShowFavs: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    val showAllFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { showAllFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize()) {
        // ── Header: Title + Section Switcher + Settings + Refresh ───────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 48.dp).padding(top = 22.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                T("FreeTvGarden", 26, FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                T("Choose a country to browse ${sectionLabel(section).lowercase()} channels", 13, color = AestheticMuted)
            }

            // Section Switcher: TV / Radio / Webcam
            Row(
                Modifier.clip(CircleShape).background(AestheticSurface).border(1.dp, AestheticBorder, CircleShape).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Triple(0, "TV", Ico.Tv),
                    Triple(1, "Radio", Ico.Radio),
                    Triple(2, "Webcams", Ico.Webcam)
                ).forEach { (idx, label, icon) ->
                    val isCur = section == idx
                    Glass(
                        shape = CircleShape,
                        solidOnFocus = true,
                        focusScale = 1.05f,
                        base = if (isCur) 0.35f else 0f,
                        onClick = { onSection(idx) }
                    ) { f ->
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvIcon(icon, if (f) Color.Black else if (isCur) IosBlue else Color.White.copy(.7f), Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            T(label, 13, if (isCur) FontWeight.SemiBold else FontWeight.Medium,
                                if (f) Color.Black else Color.White.copy(if (isCur) 1f else .7f))
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Settings
            Glass(shape = CircleShape, solidOnFocus = true, focusScale = 1.08f, onClick = onOpenSettings) { f ->
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    TvIcon(Ico.Settings, if (f) Color.Black else Color.White, Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.width(8.dp))

            // Refresh
            Glass(shape = CircleShape, solidOnFocus = true, focusScale = 1.08f, onClick = onRefresh) { f ->
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (loading) Spinner(Modifier.size(16.dp))
                    else TvIcon(Ico.Refresh, if (f) Color.Black else Color.White, Modifier.size(16.dp))
                }
            }
        }

        // ── Action Shortcuts: All Channels + Favourites ─────────────────────
        Row(
            Modifier.padding(horizontal = 48.dp).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Show All Channels (auto-focused for Android TV remote)
            Glass(
                Modifier.focusRequester(showAllFocus),
                shape = RoundedCornerShape(16.dp),
                solidOnFocus = true,
                focusScale = 1.05f,
                onClick = onShowAll
            ) { f ->
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvIcon(Ico.Globe, if (f) Color.Black else IosBlue, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    T("All ${sectionLabel(section)} Channels", 14, FontWeight.Bold, color = if (f) Color.Black else Color.White)
                }
            }

            // Favourites shortcut
            val favsForSection = store.favs.count { it.mediaType == mediaTypeFor(section) }
            if (store.favs.isNotEmpty()) {
                Glass(
                    shape = RoundedCornerShape(16.dp),
                    solidOnFocus = true,
                    focusScale = 1.05f,
                    onClick = onShowFavs
                ) { f ->
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvIcon(Ico.HeartFilled, if (f) Color.Black else Color(0xFFFF4D6D), Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        T(
                            if (favsForSection > 0) "Favourites ($favsForSection)" else "Favourites (${store.favs.size})",
                            14, FontWeight.Bold,
                            color = if (f) Color.Black else Color.White
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            T(
                "${countriesWithChannels.size} countries available",
                13,
                FontWeight.Medium,
                color = AestheticMuted
            )
        }

        // ── Countries Grid with Flag Logos ──────────────────────────────────
        LazyVerticalGrid(
            GridCells.Adaptive(210.dp),
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(countriesWithChannels, key = { it.first }) { (country, channels) ->
                CountryCard(
                    country = country,
                    channels = channels,
                    store = store,
                    onClick = { onSelectCountry(country) }
                )
            }
        }
    }
}

// ── Country Card with Flag Logo, Country Name & Channel Logos ─────────────────
@Composable
fun CountryCard(
    country: String,
    channels: List<Channel>,
    store: Store,
    onClick: () -> Unit
) {
    val cName = remember(country) { countryName(country) }
    val flagUrl = remember(country) { countryFlagUrl(country) }
    val favCount = channels.count { store.isFav(it) }
    val logoChannels = remember(channels) { channels.filter { it.logo.isNotEmpty() }.take(4) }

    Glass(
        Modifier.aspectRatio(1.5f),
        shape = RoundedCornerShape(18.dp),
        onClick = onClick
    ) { focused ->
        Box(
            Modifier
                .fillMaxSize()
                .background(if (focused) Color(0xFF1E2536) else AestheticCard)
        )

        // Top Row: Country Flag Logo + Country ISO Badge + Favourites Count
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flag Logo Image (High Quality FlagCDN)
            Box(
                Modifier
                    .size(width = 44.dp, height = 30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F1219))
                    .border(1.dp, if (focused) IosBlue else AestheticBorder, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = rememberUrlPainter(flagUrl),
                    contentDescription = country,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.width(8.dp))

            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF161B26))
                    .border(0.5.dp, AestheticBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                T(country.uppercase(), 11, FontWeight.Bold, color = IosBlue)
            }

            Spacer(Modifier.weight(1f))

            if (favCount > 0) {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF2A1520))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvIcon(Ico.HeartFilled, Color(0xFFFF4D6D), Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                    T("$favCount", 11, FontWeight.Bold, color = Color(0xFFFF4D6D))
                }
            }
        }

        // Center: Full Country Name + Channel Count
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 14.dp)
                .padding(top = 10.dp)
        ) {
            T(cName, 16, FontWeight.Bold, Color.White, lines = 1)
            Spacer(Modifier.height(2.dp))
            T("${channels.size} channels", 12, color = AestheticMuted)
        }

        // Bottom: Up to 4 representative channel logos
        if (logoChannels.isNotEmpty()) {
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy((-6).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                logoChannels.forEach { ch ->
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F1219))
                            .border(1.dp, AestheticBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = rememberUrlPainter(ch.logo),
                            contentDescription = ch.name,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

// ── Top Bar ───────────────────────────────────────────────────────────────────
@Composable
fun TopBar(
    section: Int, onSection: (Int) -> Unit,
    subTab: Int, onSubTab: (Int) -> Unit,
    loading: Boolean, onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    first: FocusRequester,
    onShowCountryPicker: () -> Unit = {}
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 48.dp).padding(top = 20.dp, bottom = 4.dp)) {
        // Row 1: Brand title + Section Switcher + Settings + Refresh
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Home / Country Picker button
            Glass(
                shape = RoundedCornerShape(14.dp),
                solidOnFocus = true,
                focusScale = 1.06f,
                onClick = onShowCountryPicker
            ) { f ->
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvIcon(Ico.Globe, if (f) Color.Black else IosBlue, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    T("Countries", 13, FontWeight.SemiBold, color = if (f) Color.Black else Color.White)
                }
            }
            Spacer(Modifier.width(12.dp))
            T("FreeTvGarden", 26, FontWeight.Bold, modifier = Modifier.weight(1f))

            // Section Switcher: TV / Radio / Webcam with vector icons
            Row(
                Modifier.clip(CircleShape).background(AestheticSurface).border(1.dp, AestheticBorder, CircleShape).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Triple(0, "TV", Ico.Tv),
                    Triple(1, "Radio", Ico.Radio),
                    Triple(2, "Webcams", Ico.Webcam)
                ).forEach { (idx, label, icon) ->
                    val isCur = section == idx
                    Glass(
                        if (idx == 0) Modifier.focusRequester(first) else Modifier,
                        CircleShape, solidOnFocus = true, focusScale = 1.05f,
                        base = if (isCur) 0.35f else 0f,
                        onClick = { onSection(idx) }
                    ) { f ->
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvIcon(icon, if (f) Color.Black else if (isCur) IosBlue else Color.White.copy(.7f), Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            T(label, 14, if (isCur) FontWeight.SemiBold else FontWeight.Medium,
                                if (f) Color.Black else Color.White.copy(if (isCur) 1f else .7f))
                        }
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            // Settings button with vector gear icon
            Glass(shape = CircleShape, solidOnFocus = true, focusScale = 1.08f, onClick = onOpenSettings) { f ->
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    TvIcon(Ico.Settings, if (f) Color.Black else Color.White, Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.width(8.dp))

            // Refresh button with vector refresh icon
            Glass(shape = CircleShape, solidOnFocus = true, focusScale = 1.08f, onClick = onRefresh) { f ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (loading) Spinner(Modifier.size(16.dp))
                    else TvIcon(Ico.Refresh, if (f) Color.Black else Color.White, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    T("Refresh", 14, color = if (f) Color.Black else Color.White)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Row 2: Sub-tabs: All / Favourites / Recents with vector icons
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(0, "All", null),
                Triple(1, "Favourites", Ico.HeartFilled),
                Triple(2, "Recents", Ico.History)
            ).forEach { (idx, label, icon) ->
                val isCur = subTab == idx
                Glass(
                    shape = RoundedCornerShape(16.dp),
                    solidOnFocus = true,
                    focusScale = 1.04f,
                    base = if (isCur) 0.3f else 0.12f,
                    onClick = { onSubTab(idx) }
                ) { f ->
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        icon?.let {
                            val tint = if (f) Color.Black else if (idx == 1 && isCur) Color(0xFFFF4D6D) else Color.White.copy(if (isCur) 1f else .65f)
                            TvIcon(it, tint, Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        T(label, 13, if (isCur) FontWeight.SemiBold else FontWeight.Normal,
                            if (f) Color.Black else Color.White.copy(if (isCur) 1f else .65f))
                    }
                }
            }
        }
    }
}

// ── Aesthetic Channel Grid & Cards (Zero Gradients) ───────────────────────────
@Composable
fun ChannelGrid(list: List<Channel>, store: Store, empty: String, modifier: Modifier, onPlay: (Int) -> Unit) {
    if (list.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            T(empty, 18, color = AestheticMuted, lines = 3, align = TextAlign.Center)
        }
        return
    }
    LazyVerticalGrid(
        GridCells.Adaptive(210.dp), modifier,
        contentPadding = PaddingValues(48.dp, 12.dp, 48.dp, 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(list, key = { _, c -> c.id }) { i, c ->
            ChannelCard(c, store, onClick = { onPlay(i) }, onLongClick = { store.toggleFav(c) })
        }
    }
}

@Composable
fun ChannelCard(c: Channel, store: Store, onClick: () -> Unit, onLongClick: () -> Unit) {
    val initials = c.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    val isFav = store.isFav(c)

    Glass(
        Modifier.aspectRatio(1.65f),
        shape = RoundedCornerShape(16.dp),
        onLongClick = onLongClick,
        onClick = onClick
    ) { focused ->
        // Aesthetic Matte Background (No Gradient)
        Box(
            Modifier
                .fillMaxSize()
                .background(if (focused) Color(0xFF1B2130) else AestheticCard)
        )

        // Logo image or Clean Typography Monogram
        if (c.logo.isNotEmpty()) {
            Box(
                Modifier
                    .size(64.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F1219))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = rememberUrlPainter(c.logo),
                    contentDescription = c.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            Box(
                Modifier
                    .size(52.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF181D29))
                    .border(1.dp, AestheticBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                T(initials, 22, FontWeight.Bold, Color.White.copy(.9f))
            }
        }

        // Top Start: Quality badge or Media Type icon
        Row(
            Modifier.align(Alignment.TopStart).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (c.mediaType) {
                MediaType.RADIO -> {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF1B2230)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        TvIcon(Ico.Radio, IosBlue, Modifier.size(12.dp))
                    }
                }
                MediaType.WEBCAM -> {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF1B2230)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        TvIcon(Ico.Webcam, IosBlue, Modifier.size(12.dp))
                    }
                }
                else -> {
                    if (c.quality.isNotEmpty()) {
                        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF1B2230)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            T(c.quality, 10, FontWeight.SemiBold, IosBlue)
                        }
                    }
                }
            }
        }

        // Top End: Status Dot & Favourite Heart
        Row(
            Modifier.align(Alignment.TopEnd).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isFav) {
                TvIcon(Ico.HeartFilled, Color(0xFFFF4D6D), Modifier.size(14.dp))
            }
            val statusColor = when (c.status) {
                "live" -> IosBlue
                "down" -> IosRed
                else -> Color.Transparent
            }
            if (statusColor != Color.Transparent) {
                Box(Modifier.size(7.dp).background(statusColor, CircleShape))
            }
        }

        // Bottom Info: Channel name + Subtitle (Country · Group · Language)
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            T(c.name, 14, FontWeight.SemiBold, Color.White)
            val sub = listOf(c.country, c.group, c.language).filter { it.isNotEmpty() }.joinToString(" · ")
            if (sub.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (c.country.length == 2) {
                        FlagBadge(c.country, height = 11.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    T(sub, 11, color = AestheticMuted)
                }
            }
        }
    }
}

// ── Filter & Sort Modal Dialog ────────────────────────────────────────────────
@Composable
fun FilterDialog(
    countries: List<String>,
    languages: List<String>,
    selectedCountry: String,
    selectedLanguage: String,
    currentSort: Int,
    onSelectCountry: (String) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSelectSort: (Int) -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    // Auto-focus on first item for Android TV remote
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { firstFocus.requestFocus() }
    }

    Box(
        // Transparent dark backdrop — non-focusable so D-pad clicks stay inside dialog
        Modifier
            .fillMaxSize()
            .background(Color(0x75000000))
            .focusProperties { canFocus = false }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(660.dp)
                .clickable(enabled = false) {}
                .clip(RoundedCornerShape(26.dp))
                // Translucent frosted glass panel with visible transparency
                .background(Color(0xBB121624))
                .border(1.2.dp, Color(0x6630D158), RoundedCornerShape(26.dp))
                .padding(28.dp)
        ) {
            Column {
                // Header
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TvIcon(Ico.Sliders, IosBlue, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    T("Sort & Filter Options", 20, FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Glass(
                        Modifier.focusRequester(firstFocus),
                        shape = CircleShape,
                        solidOnFocus = true,
                        focusScale = 1.1f,
                        onClick = onClose
                    ) { f ->
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            TvIcon(Ico.Close, if (f) Color.Black else Color.White, Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 1. Sort Options
                T("Sort By", 14, FontWeight.SemiBold, color = AestheticMuted)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SortOption.values().toList()) { opt ->
                        val isSelected = currentSort == opt.ordinal
                        Glass(
                            shape = RoundedCornerShape(14.dp),
                            solidOnFocus = true,
                            focusScale = 1.05f,
                            base = if (isSelected) 0.35f else 0.12f,
                            onClick = { onSelectSort(opt.ordinal) }
                        ) { f ->
                            Row(
                                Modifier
                                    .background(if (isSelected && !f) Color(0xFF1A2A20) else Color.Transparent)
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelected) {
                                    TvIcon(Ico.Check, if (f) Color.Black else IosBlue, Modifier.size(12.dp))
                                    Spacer(Modifier.width(6.dp))
                                }
                                T(opt.label, 13, FontWeight.Medium, color = if (f) Color.Black else if (isSelected) IosBlue else Color.White)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 2. Country Filter with FlagBadges
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvIcon(Ico.Globe, AestheticMuted, Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    T("Filter by Country", 14, FontWeight.SemiBold, color = AestheticMuted)
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(countries) { c ->
                        val isSelected = selectedCountry == c
                        Glass(
                            shape = RoundedCornerShape(14.dp),
                            solidOnFocus = true,
                            focusScale = 1.05f,
                            base = if (isSelected) 0.35f else 0.12f,
                            onClick = { onSelectCountry(c) }
                        ) { f ->
                            Row(
                                Modifier
                                    .background(if (isSelected && !f) Color(0xFF1A2A20) else Color.Transparent)
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FlagBadge(c, height = 15.dp)
                                val cLabel = if (c == "All") "All" else "$c (${countryName(c)})"
                                T(cLabel, 13, FontWeight.Medium, color = if (f) Color.Black else if (isSelected) IosBlue else Color.White)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 3. Language Filter
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvIcon(Ico.Language, AestheticMuted, Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    T("Filter by Language", 14, FontWeight.SemiBold, color = AestheticMuted)
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(languages) { lang ->
                        val isSelected = selectedLanguage == lang
                        Glass(
                            shape = RoundedCornerShape(14.dp),
                            solidOnFocus = true,
                            focusScale = 1.05f,
                            base = if (isSelected) 0.35f else 0.12f,
                            onClick = { onSelectLanguage(lang) }
                        ) { f ->
                            Box(
                                Modifier
                                    .background(if (isSelected && !f) Color(0xFF1A2A20) else Color.Transparent)
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                T(lang, 13, FontWeight.Medium, color = if (f) Color.Black else if (isSelected) IosBlue else Color.White)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Done Button
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Glass(shape = RoundedCornerShape(16.dp), solidOnFocus = true, focusScale = 1.06f, onClick = onClose) { f ->
                        T("Apply & Close", 14, FontWeight.Bold, color = if (f) Color.Black else Color.White,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
                    }
                }
            }
        }
    }
}

// ── Settings Screen Overlay ───────────────────────────────────────────────────
@Composable
fun SettingsScreen(
    store: Store,
    tvCount: Int,
    radioCount: Int,
    webcamCount: Int,
    onClearCache: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }

    var clearDone by remember { mutableStateOf(false) }

    // Auto-focus close button for Android TV remote
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { closeFocus.requestFocus() }
    }

    Box(
        // Transparent dark backdrop — non-focusable so D-pad clicks stay inside dialog
        Modifier
            .fillMaxSize()
            .background(Color(0x75000000))
            .focusProperties { canFocus = false }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .width(660.dp)
                .clickable(enabled = false) {}
                .clip(RoundedCornerShape(26.dp))
                // Translucent frosted glass panel with visible transparency
                .background(Color(0xBB121624))
                .border(1.2.dp, Color(0x6630D158), RoundedCornerShape(26.dp))
                .padding(32.dp)
        ) {
            Column {
                // Header
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TvIcon(Ico.Settings, IosBlue, Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    T("Settings", 24, FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Glass(
                        Modifier.focusRequester(closeFocus),
                        shape = CircleShape, solidOnFocus = true, focusScale = 1.1f, onClick = onClose
                    ) { f ->
                        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                            TvIcon(Ico.Close, if (f) Color.Black else Color.White, Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Section 1: Playback
                T("Playback & Display", 15, FontWeight.Bold, color = IosBlue)
                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)).background(AestheticSurface)
                        .border(1.dp, AestheticBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        T("Video Aspect Ratio", 15, FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        T("Fit screen, Zoom crop, or Stretch", 12, color = AestheticMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Triple(0, "Fit", 0), Triple(1, "Zoom", 1), Triple(2, "Stretch", 2)).forEach { (mode, lbl, _) ->
                            val isCur = store.playerResizeMode == mode
                            Glass(
                                shape = RoundedCornerShape(12.dp),
                                solidOnFocus = true,
                                focusScale = 1.06f,
                                base = if (isCur) 0.35f else 0.12f,
                                onClick = { store.setResize(mode) }
                            ) { f ->
                                T(lbl, 13, if (isCur) FontWeight.Bold else FontWeight.Normal,
                                    color = if (f) Color.Black else if (isCur) IosBlue else Color.White,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Section 2: Channel Filtering
                T("Channel Preferences", 15, FontWeight.Bold, color = IosBlue)
                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)).background(AestheticSurface)
                        .border(1.dp, AestheticBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        T("Hide Offline Channels", 15, FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        T("Filter out streams marked as down", 12, color = AestheticMuted)
                    }
                    Glass(
                        shape = RoundedCornerShape(12.dp),
                        solidOnFocus = true,
                        focusScale = 1.06f,
                        onClick = { store.toggleHideOffline() }
                    ) { f ->
                        val active = store.hideOffline
                        T(if (active) "Enabled" else "Disabled", 13, FontWeight.SemiBold,
                            color = if (f) Color.Black else if (active) IosBlue else Color.White.copy(.7f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Section 3: Data & Cache
                T("Data & Storage", 15, FontWeight.Bold, color = IosBlue)
                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)).background(AestheticSurface)
                        .border(1.dp, AestheticBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        T("Cache & Synced Channels", 15, FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        T("TV: $tvCount  ·  Radio: $radioCount  ·  Webcams: $webcamCount", 12, color = AestheticMuted)
                    }
                    Glass(
                        shape = RoundedCornerShape(12.dp),
                        solidOnFocus = true,
                        focusScale = 1.06f,
                        onClick = {
                            onClearCache()
                            clearDone = true
                        }
                    ) { f ->
                        T(if (clearDone) "Cleared!" else "Clear & Reload", 13, FontWeight.SemiBold,
                            color = if (f) Color.Black else Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }

                Spacer(Modifier.height(20.dp))

                // About section footer
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        T("FreeTvGarden  v1.3 Aesthetic Dark", 13, FontWeight.Medium, color = Color.White.copy(.85f))
                        T("Data: freetvgarden-data-v2 · Package: com.freetvgarden.abhijeet", 11, color = AestheticMuted)
                    }
                    Spacer(Modifier.weight(1f))
                    Glass(shape = RoundedCornerShape(14.dp), solidOnFocus = true, focusScale = 1.06f, onClick = onClose) { f ->
                        T("Done", 14, FontWeight.Bold, color = if (f) Color.Black else Color.White,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}

// ── Search Bar Component ──────────────────────────────────────────────────────
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SearchBar(query: String, onQuery: (String) -> Unit, hint: String) {
    var editing   by remember { mutableStateOf(false) }
    var hadFocus  by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val pillFocus  = remember { FocusRequester() }
    val scope      = rememberCoroutineScope()
    val kb         = LocalSoftwareKeyboardController.current

    fun finish() {
        kb?.hide(); editing = false; hadFocus = false
        scope.launch { withFrameNanos { }; runCatching { pillFocus.requestFocus() } }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (editing) {
            Box(
                Modifier.width(340.dp).height(42.dp).clip(CircleShape).background(Color(0xFF1E2332))
                    .border(2.dp, IosBlue, CircleShape).padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvIcon(Ico.Search, IosBlue, Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        query, onQuery, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(IosBlue),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { finish() }),
                        modifier = Modifier.weight(1f).focusRequester(fieldFocus)
                            .onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus) finish() }
                    )
                }
            }
            LaunchedEffect(Unit) { withFrameNanos { }; runCatching { fieldFocus.requestFocus(); kb?.show() } }
        } else {
            Glass(Modifier.focusRequester(pillFocus).width(340.dp), CircleShape, focusScale = 1.04f, onClick = { editing = true }) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvIcon(Ico.Search, if (query.isEmpty()) AestheticMuted else IosBlue, Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                    T(if (query.isEmpty()) hint else query, 14,
                        color = Color.White.copy(if (query.isEmpty()) .6f else 1f))
                }
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Glass(shape = CircleShape, solidOnFocus = true, focusScale = 1.06f, onClick = { onQuery("") }) { f ->
                    T("Clear", 13, color = if (f) Color.Black else Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
        }
    }
}

// ── URL Image Painter Helper ──────────────────────────────────────────────────
@Composable
fun rememberUrlPainter(url: String): androidx.compose.ui.graphics.painter.Painter {
    val bmp = remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url) {
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "FreeTvGarden-Android/1.0")
                val bitmap = conn.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
                conn.disconnect()
                bitmap
            }.onSuccess { bmp.value = it }
        }
    }
    return if (bmp.value != null)
        androidx.compose.ui.graphics.painter.BitmapPainter(bmp.value!!.asImageBitmap())
    else
        androidx.compose.ui.graphics.painter.ColorPainter(Color.Transparent)
}

package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.NoorPreferences
import com.example.data.QuranReciters
import com.example.data.SurahDirectory
import com.example.domain.AudioPlaybackState
import com.example.model.Ayah
import com.example.model.SurahMeta
import com.example.ui.components.IslamicStarBadge
import com.example.ui.theme.AmiriFontFamily
import com.example.viewmodel.SurahDetailUiState
import com.example.viewmodel.SurahFilter

@Composable
fun QuranScreen(
    surahs: List<SurahMeta>,
    searchQuery: String,
    activeFilter: SurahFilter,
    detailState: SurahDetailUiState,
    audioState: AudioPlaybackState,
    prefs: NoorPreferences,
    onSearchChange: (String) -> Unit,
    onFilterChange: (SurahFilter) -> Unit,
    onOpenSurah: (Int) -> Unit,
    onCloseSurah: () -> Unit,
    onPlaySurah: (SurahMeta) -> Unit,
    onPlayAyah: (Int, Ayah) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipAyah: (Int) -> Unit,
    onStopAudio: () -> Unit,
    onToggleBookmark: (Int, Int) -> Unit,
    onToggleTranslation: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (detailState) {
            is SurahDetailUiState.Idle -> {
                SurahExplorerView(
                    surahs = surahs,
                    searchQuery = searchQuery,
                    activeFilter = activeFilter,
                    prefs = prefs,
                    audioState = audioState,
                    onSearchChange = onSearchChange,
                    onFilterChange = onFilterChange,
                    onOpenSurah = onOpenSurah,
                    onPlaySurah = onPlaySurah,
                    onOpenSettings = onOpenSettings
                )
            }

            is SurahDetailUiState.Loading,
            is SurahDetailUiState.Success,
            is SurahDetailUiState.Error -> {
                BackHandler(onBack = onCloseSurah)
                SurahReaderView(
                    detailState = detailState,
                    audioState = audioState,
                    prefs = prefs,
                    onBack = onCloseSurah,
                    onRetry = { surahNum -> onOpenSurah(surahNum) },
                    onPlaySurah = onPlaySurah,
                    onPlayAyah = onPlayAyah,
                    onToggleBookmark = onToggleBookmark,
                    onToggleTranslation = onToggleTranslation
                )
            }
        }

        AnimatedVisibility(
            visible = audioState.surahNumber != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            MiniAudioPlayerBar(
                audioState = audioState,
                reciterName = QuranReciters.list.firstOrNull { it.id == prefs.reciterId }?.name
                    ?: "Mishary Alafasy",
                onTogglePlayPause = onTogglePlayPause,
                onPrevAyah = { onSkipAyah(-1) },
                onNextAyah = { onSkipAyah(1) },
                onStop = onStopAudio
            )
        }
    }
}

@Composable
private fun SurahExplorerView(
    surahs: List<SurahMeta>,
    searchQuery: String,
    activeFilter: SurahFilter,
    prefs: NoorPreferences,
    audioState: AudioPlaybackState,
    onSearchChange: (String) -> Unit,
    onFilterChange: (SurahFilter) -> Unit,
    onOpenSurah: (Int) -> Unit,
    onPlaySurah: (SurahMeta) -> Unit,
    onOpenSettings: () -> Unit
) {
    val lastSurah = SurahDirectory.surahs.firstOrNull { it.number == prefs.lastReadSurah }
        ?: SurahDirectory.surahs.first()
    val reciter = QuranReciters.list.firstOrNull { it.id == prefs.reciterId } ?: QuranReciters.list.first()
    val bottomClearance = if (audioState.surahNumber != null) 92.dp else 20.dp

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(156.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_quran_header),
                        contentDescription = "Illuminated Quran geometric header",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xE6052A20),
                                        Color(0xB8083F30)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LAST READ",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFE8C56E)
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color.White.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable(onClick = onOpenSettings)
                            ) {
                                Text(
                                    text = reciter.name.substringBefore(" ") + " • Change",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${lastSurah.number}. ${lastSurah.nameEnglish}",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = "Ayah ${prefs.lastReadAyah} • ${lastSurah.meaning}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFFE8C56E),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { onOpenSurah(lastSurah.number) }
                                    .testTag("continue_reading_button")
                            ) {
                                Text(
                                    text = "Continue",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFF1A1404),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search all 114 Surahs by name or number") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("surah_search_input")
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SurahFilter.entries) { filter ->
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick = { onFilterChange(filter) },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag("surah_filter_${filter.name.lowercase()}")
                    )
                }
            }
        }

        if (surahs.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Surahs match your filter",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Try clearing the search or bookmark verses while reading.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(surahs, key = { it.number }) { surah ->
                SurahListCard(
                    surah = surah,
                    isPlaying = audioState.surahNumber == surah.number && audioState.isPlaying,
                    onClick = { onOpenSurah(surah.number) },
                    onPlayClick = { onPlaySurah(surah) }
                )
            }
        }
    }
}

@Composable
private fun SurahListCard(
    surah: SurahMeta,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isPlaying) 1.5.dp else 1.dp,
            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("surah_card_${surah.number}")
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IslamicStarBadge(numberText = surah.number.toString())
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = surah.nameEnglish,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${surah.meaning} • ${surah.revelationType} • ${surah.ayahsCount} Ayahs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = surah.nameArabic,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = AmiriFontFamily),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onPlayClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("surah_play_${surah.number}")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Outlined.GraphicEq else Icons.Outlined.PlayArrow,
                    contentDescription = "Recite ${surah.nameEnglish}",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun SurahReaderView(
    detailState: SurahDetailUiState,
    audioState: AudioPlaybackState,
    prefs: NoorPreferences,
    onBack: () -> Unit,
    onRetry: (Int) -> Unit,
    onPlaySurah: (SurahMeta) -> Unit,
    onPlayAyah: (Int, Ayah) -> Unit,
    onToggleBookmark: (Int, Int) -> Unit,
    onToggleTranslation: (Boolean) -> Unit
) {
    val surah = when (detailState) {
        is SurahDetailUiState.Loading -> detailState.surah
        is SurahDetailUiState.Success -> detailState.surah
        is SurahDetailUiState.Error -> detailState.surah
        SurahDetailUiState.Idle -> return
    }

    val listState = rememberLazyListState()

    LaunchedEffect(audioState.surahNumber, audioState.ayahNumberInSurah) {
        if (detailState is SurahDetailUiState.Success &&
            audioState.surahNumber == surah.number &&
            audioState.ayahNumberInSurah != null
        ) {
            val idx = detailState.ayahs.indexOfFirst { it.numberInSurah == audioState.ayahNumberInSurah }
            if (idx >= 0) {
                listState.animateScrollToItem(idx + 1)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Reader sub-header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("surah_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${surah.number}. ${surah.nameEnglish}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${surah.nameArabic} • ${surah.ayahsCount} Verses",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onToggleTranslation(!prefs.showTranslation) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Translate,
                            contentDescription = "Toggle translation",
                            tint = if (prefs.showTranslation) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(
                        onClick = { onPlaySurah(surah) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("reader_play_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = "Play Surah",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        when (detailState) {
            is SurahDetailUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading Surah ${surah.nameEnglish}...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is SurahDetailUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = detailState.message,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(onClick = { onRetry(surah.number) }) {
                            Text("Retry")
                        }
                    }
                }
            }

            is SurahDetailUiState.Success -> {
                val bottomClearance = if (audioState.surahNumber != null) 96.dp else 24.dp
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomClearance),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = surah.nameArabic,
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        fontFamily = AmiriFontFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${surah.nameEnglish} — ${surah.meaning}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (surah.number != 1 && surah.number != 9) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            fontFamily = AmiriFontFamily
                                        ),
                                        color = MaterialTheme.colorScheme.secondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    items(detailState.ayahs, key = { it.globalNumber }) { ayah ->
                        val isPlayingThisAyah = audioState.surahNumber == surah.number &&
                            audioState.ayahNumberInSurah == ayah.numberInSurah
                        val isBookmarked = prefs.bookmarks.contains("${surah.number}:${ayah.numberInSurah}")

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isPlayingThisAyah) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                width = if (isPlayingThisAyah) 1.5.dp else 1.dp,
                                color = if (isPlayingThisAyah) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ayah_card_${ayah.numberInSurah}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IslamicStarBadge(
                                        numberText = ayah.numberInSurah.toString(),
                                        size = 34.dp
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onToggleBookmark(surah.number, ayah.numberInSurah) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = if (isBookmarked) {
                                                    Icons.Rounded.Bookmark
                                                } else {
                                                    Icons.Outlined.BookmarkBorder
                                                },
                                                contentDescription = stringResource(R.string.cd_bookmark),
                                                tint = if (isBookmarked) {
                                                    MaterialTheme.colorScheme.secondary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                        IconButton(
                                            onClick = { onPlayAyah(surah.number, ayah) },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("ayah_play_${ayah.numberInSurah}")
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingThisAyah && audioState.isPlaying) {
                                                    Icons.Outlined.GraphicEq
                                                } else {
                                                    Icons.Outlined.PlayArrow
                                                },
                                                contentDescription = stringResource(R.string.cd_play_pause),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                    Text(
                                        text = ayah.arabicText,
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            fontFamily = AmiriFontFamily,
                                            fontSize = prefs.arabicFontSizeSp.sp,
                                            lineHeight = (prefs.arabicFontSizeSp * 1.72f).sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                if (prefs.showTranslation && ayah.englishText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = ayah.englishText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniAudioPlayerBar(
    audioState: AudioPlaybackState,
    reciterName: String,
    onTogglePlayPause: () -> Unit,
    onPrevAyah: () -> Unit,
    onNextAyah: () -> Unit,
    onStop: () -> Unit
) {
    val surahMeta = SurahDirectory.surahs.firstOrNull { it.number == audioState.surahNumber }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        shadowElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mini_audio_player")
    ) {
        Column {
            LinearProgressIndicator(
                progress = { audioState.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val verseSuffix = audioState.ayahNumberInSurah?.let { " • Ayah $it" } ?: " • Full Surah"
                    Text(
                        text = "${surahMeta?.nameEnglish ?: "Quran"}$verseSuffix",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = audioState.errorMessage ?: reciterName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (audioState.errorMessage != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (audioState.ayahNumberInSurah != null) {
                    IconButton(onClick = onPrevAyah) {
                        Icon(
                            Icons.Outlined.SkipPrevious,
                            contentDescription = stringResource(R.string.cd_prev_ayah)
                        )
                    }
                }

                if (audioState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(10.dp)
                            .size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier.testTag("mini_player_play_pause")
                        ) {
                            Icon(
                                imageVector = if (audioState.isPlaying) {
                                    Icons.Outlined.Pause
                                } else {
                                    Icons.Outlined.PlayArrow
                                },
                                contentDescription = stringResource(R.string.cd_play_pause),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                if (audioState.ayahNumberInSurah != null) {
                    IconButton(onClick = onNextAyah) {
                        Icon(
                            Icons.Outlined.SkipNext,
                            contentDescription = stringResource(R.string.cd_next_ayah)
                        )
                    }
                }

                IconButton(onClick = onStop) {
                    Icon(Icons.Rounded.Close, contentDescription = "Stop recitation")
                }
            }
        }
    }
}

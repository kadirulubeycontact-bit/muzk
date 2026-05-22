package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.db.PlaylistEntity
import com.example.data.model.Track
import com.example.ui.theme.*
import com.example.ui.viewmodel.MusicViewModel
import com.example.ui.viewmodel.SortOption
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen(viewModel: MusicViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Read UI states from ViewModel
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val errorState by viewModel.errorState.collectAsState()

    // Local state managers
    var currentTab by remember { mutableStateOf(0) } // 0=Home, 1=Search, 2=Library, 3=FX/Equalizer
    var fullPlayerExpanded by remember { mutableStateOf(false) }
    var selectedPlaylistForDetail by remember { mutableStateOf<PlaylistEntity?>(null) }
    var showPlaylistCreationDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialogForTrack by remember { mutableStateOf<Track?>(null) }

    // Display error messages as toasts safely on main thread
    LaunchedEffect(errorState) {
        errorState?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MasivaBlack,
        bottomBar = {
            Column {
                // Floating Mini Player (if a track is loaded)
                currentTrack?.let { track ->
                    MiniPlayer(
                        track = track,
                        isPlaying = isPlaying,
                        position = currentPosition,
                        duration = duration,
                        onPlayPauseToggle = { viewModel.togglePlayPause() },
                        onNextClick = { viewModel.skipToNext() },
                        onExpandRequest = { fullPlayerExpanded = true }
                    )
                }

                // Beautiful bottom navigation
                NavigationBar(
                    containerColor = MasivaCharcoal,
                    tonalElevation = 8.dp,
                    modifier = Modifier.height(72.dp)
                ) {
                    val items = listOf(
                        Triple(0, "Ana Sayfa", Icons.Default.Home),
                        Triple(1, "Arama", Icons.Default.Search),
                        Triple(2, "Kitaplık", Icons.Default.LibraryMusic),
                        Triple(3, "Efektler", Icons.Default.Equalizer)
                    )

                    items.forEach { (tabId, label, icon) ->
                        val selected = currentTab == tabId
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                currentTab = tabId
                                // Close playlist subviews when moving tabs
                                if (tabId != 2) selectedPlaylistForDetail = null
                            },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (selected) MasivaEmerald else MasivaMutedText,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    color = if (selected) MasivaEmerald else MasivaMutedText,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MasivaGrey
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main views branching
            Crossfade(
                targetState = currentTab,
                animationSpec = tween(durationMillis = 300),
                label = "MainTabs"
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> DashboardTab(
                        viewModel = viewModel,
                        onPlaylistClick = { playlist ->
                            selectedPlaylistForDetail = playlist
                            currentTab = 2 // navigate to library tab to show it
                        },
                        onAddTrackToPlaylist = { showAddToPlaylistDialogForTrack = it }
                    )
                    1 -> SearchTab(
                        viewModel = viewModel,
                        onAddTrackToPlaylist = { showAddToPlaylistDialogForTrack = it }
                    )
                    2 -> LibraryTab(
                        viewModel = viewModel,
                        selectedPlaylist = selectedPlaylistForDetail,
                        onCreatePlaylistClick = { showPlaylistCreationDialog = true },
                        onPlaylistSelected = { selectedPlaylistForDetail = it },
                        onBackToPlaylistList = { selectedPlaylistForDetail = null },
                        onAddTrackToPlaylist = { showAddToPlaylistDialogForTrack = it }
                    )
                    3 -> EqualizerTab(viewModel = viewModel)
                }
            }

            // Playlist creation pop dialog
            if (showPlaylistCreationDialog) {
                var newPlaylistName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showPlaylistCreationDialog = false },
                    containerColor = MasivaCharcoal,
                    title = { Text("Yeni Çalma Listesi", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("Oluşturmak istediğiniz çalma listesinin ismini giriniz:", color = Color.LightGray, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            TextField(
                                value = newPlaylistName,
                                onValueChange = { newPlaylistName = it },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MasivaGrey,
                                    unfocusedContainerColor = MasivaGrey,
                                    focusedIndicatorColor = MasivaEmerald,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                placeholder = { Text("Klasik Rock, Lofi Vibes...", color = Color.DarkGray) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("playlist_name_input")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    viewModel.createPlaylist(newPlaylistName)
                                    showPlaylistCreationDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MasivaEmerald)
                        ) {
                            Text("Oluştur", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPlaylistCreationDialog = false }) {
                            Text("İptal", color = MasivaEmerald)
                        }
                    }
                )
            }

            // Add Song to Playlist Picker pop dialog
            showAddToPlaylistDialogForTrack?.let { trackToInsert ->
                val wallets by viewModel.playlists.collectAsState()
                AlertDialog(
                    onDismissRequest = { showAddToPlaylistDialogForTrack = null },
                    containerColor = MasivaCharcoal,
                    title = { Text("Çalma Listesine Ekle", color = Color.White, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Şarkı: ${trackToInsert.title}", color = MasivaEmerald, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(12.dp))
                            if (wallets.isEmpty()) {
                                Text("Oluşturulmuş çalma listesi bulunamadı. Lütfen önce kitaplık kısmından bir liste oluşturun.", color = Color.LightGray, fontSize = 14.sp)
                            } else {
                                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                                    items(wallets) { pl ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.addTrackToPlaylist(pl.id, trackToInsert.id)
                                                    showAddToPlaylistDialogForTrack = null
                                                    Toast.makeText(context, "${trackToInsert.title}, ${pl.name} listesine eklendi.", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(vertical = 12.dp, horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.QueueMusic,
                                                contentDescription = null,
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(
                                                text = pl.name,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        HorizontalDivider(color = MasivaGrey, thickness = 1.dp)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showAddToPlaylistDialogForTrack = null }) {
                            Text("Kapat", color = MasivaEmerald)
                        }
                    }
                )
            }

            // Expanding Full Screen Music Player Screen Overlay
            AnimatedVisibility(
                visible = fullPlayerExpanded,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(400, easing = EaseOutQuart)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(400, easing = EaseInQuart)
                )
            ) {
                currentTrack?.let { track ->
                    FullPlayerOverlay(
                        track = track,
                        isPlaying = isPlaying,
                        position = currentPosition,
                        duration = duration,
                        viewModel = viewModel,
                        onCloseRequest = { fullPlayerExpanded = false },
                        onAddTrackToPlaylist = {
                            fullPlayerExpanded = false
                            showAddToPlaylistDialogForTrack = it
                        }
                    )
                }
            }
        }
    }
}

// ==========================================
// 1. DASHBOARD SCREEN TAB
// ==========================================
@Composable
fun DashboardTab(
    viewModel: MusicViewModel,
    onPlaylistClick: (PlaylistEntity) -> Unit,
    onAddTrackToPlaylist: (Track) -> Unit
) {
    val context = LocalContext.current
    val recentTracks by viewModel.recentlyPlayed.collectAsState()
    val favorites by viewModel.favoriteTracks.collectAsState()
    val allTracksList by viewModel.allTracks.collectAsState()
    val userPlaylists by viewModel.playlists.collectAsState()

    // Determine Greeting based on Time
    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when (hour) {
            in 6..11 -> "Günaydın ☀️"
            in 12..17 -> "İyi Günler ⛅"
            in 18..22 -> "İyi Akşamlar 🌙"
            else -> "İyi Geceler 🌌"
        }
    }

    // Dynamic recommends (random 4 tracks from scan list if available)
    val randomRecommends = remember(allTracksList) {
        if (allTracksList.isNotEmpty()) {
            allTracksList.shuffled().take(4)
        } else {
            emptyList()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MasivaBlack)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 24.dp)
    ) {
        // Core Branding Title
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MASIVA",
                        color = MasivaEmerald,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    )
                    Text(
                        text = greeting,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Rescan trigger button
                IconButton(
                    onClick = {
                        viewModel.scanMusicFiles(true)
                        Toast.makeText(context, "Klasörler taranıyor...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .background(MasivaGrey, CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan",
                        tint = MasivaEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Favorites Banner Card (Glassmorphism look)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (favorites.isNotEmpty()) {
                            viewModel.playQueue(favorites)
                        } else {
                            Toast
                                .makeText(
                                    context,
                                    "Henüz favori şarkınız yok. Şarkı ekranından kalbe basarak ekleyin!",
                                    Toast.LENGTH_LONG
                                )
                                .show()
                        }
                    },
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MasivaGlassBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0x3310B981), Color(0x05111111))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(MasivaEmerald, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Beğenilen Şarkılar",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${favorites.size} Şarkı",
                                    color = MasivaMutedText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MasivaEmerald,
                            modifier = Modifier
                                .background(MasivaGrey, CircleShape)
                                .padding(8.dp)
                                .size(24.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Son Çalınanlar (Recently Played Grid)
        if (recentTracks.isNotEmpty()) {
            item {
                Text(
                    text = "En Son Çalınanlar",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Render grid lists
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val chunked = recentTracks.take(6).chunked(2)
                    chunked.forEach { pairList ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pairList.forEach { track ->
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(MasivaGrey, RoundedCornerShape(10.dp))
                                        .clickable {
                                            viewModel.playSingleTrack(track)
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TrackCoverArtThumb(track = track, size = 44.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = track.artist,
                                            color = MasivaMutedText,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (pairList.size == 1) {
                                Box(modifier = Modifier.weight(1f)) // spacer
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // Karışık Karışık Tavsiyeler (Recommendations)
        if (randomRecommends.isNotEmpty()) {
            item {
                Text(
                    text = "Günün Önerileri",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            items(randomRecommends) { track ->
                TrackListRow(
                    track = track,
                    onPlayClick = { viewModel.playSingleTrack(track) },
                    onFavoriteToggle = { viewModel.toggleFavorite(track) },
                    onAddPlaylistClick = { onAddTrackToPlaylist(track) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }

        // Quick Browse custom playlists carousel
        if (userPlaylists.isNotEmpty()) {
            item {
                Text(
                    text = "Çalma Listelerinize Göz Atın",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    userPlaylists.forEach { pl ->
                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MasivaGrey)
                                .clickable { onPlaylistClick(pl) }
                                .padding(16.dp)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(MasivaGlassBorder, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = MasivaEmerald,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = pl.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Müzik Klasörü",
                                    color = MasivaMutedText,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Blank State Notice
        if (allTracksList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Color.DarkGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Klasör bulunamadı",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Depolama izni verip yenile ikonuna basarak tarayabilirsiniz.",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. SEARCH SCREEN TAB
// ==========================================
@Composable
fun SearchTab(
    viewModel: MusicViewModel,
    onAddTrackToPlaylist: (Track) -> Unit
) {
    val context = LocalContext.current
    val query by viewModel.searchQuery.collectAsState()
    val sortOpt by viewModel.sortOption.collectAsState()
    val results by viewModel.filteredTracks.collectAsState()

    var showSortPopup by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MasivaBlack)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Şarkı Ara",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Search Input & Sort Icon Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = { viewModel.searchQuery.value = it },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("search_text_input"),
                placeholder = { Text("Şarkı, sanatçı veya klasör ismi...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.LightGray) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = Color.LightGray)
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MasivaCharcoal,
                    unfocusedContainerColor = MasivaCharcoal,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Sort Selector Box
            Box {
                IconButton(
                    onClick = { showSortPopup = true },
                    modifier = Modifier
                        .background(MasivaCharcoal, RoundedCornerShape(12.dp))
                        .size(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sıralama",
                        tint = MasivaEmerald
                    )
                }

                DropdownMenu(
                    expanded = showSortPopup,
                    onDismissRequest = { showSortPopup = false },
                    modifier = Modifier.background(MasivaGrey)
                ) {
                    SortOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.TurkishLabel, color = if (sortOpt == option) MasivaEmerald else Color.White) },
                            onClick = {
                                viewModel.sortOption.value = option
                                showSortPopup = false
                                Toast.makeText(context, "${option.TurkishLabel} sıralandı", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tracks List Results
        if (results.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MusicNote, null, tint = Color.DarkGray, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (query.isEmpty()) "Müzik Kitaplığınız Taranıyor..." else "Aranan kritere göre sonuç bulunamadı.",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results, key = { it.id }) { track ->
                    TrackListRow(
                        track = track,
                        onPlayClick = {
                            // Automatically load matching list as queue context
                            viewModel.playQueue(results, results.indexOf(track))
                        },
                        onFavoriteToggle = { viewModel.toggleFavorite(track) },
                        onAddPlaylistClick = { onAddTrackToPlaylist(track) }
                    )
                }
            }
        }
    }
}

// ==========================================
// 3. LIBRARY & PLAYLISTS SCREEN TAB
// ==========================================
@Composable
fun LibraryTab(
    viewModel: MusicViewModel,
    selectedPlaylist: PlaylistEntity?,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistSelected: (PlaylistEntity) -> Unit,
    onBackToPlaylistList: () -> Unit,
    onAddTrackToPlaylist: (Track) -> Unit
) {
    val listPlaylists by viewModel.playlists.collectAsState()
    val favoritesList by viewModel.favoriteTracks.collectAsState()

    Crossfade(targetState = selectedPlaylist != null, label = "LibraryViews") { showDetail ->
        if (showDetail && selectedPlaylist != null) {
            // PLAYLIST DETAILED SINGLE FOLDER VIEW OVERLAY
            val customTracksFlow = remember(selectedPlaylist.id) {
                viewModel.getTracksInPlaylist(selectedPlaylist.id)
            }
            val playlistTracks by customTracksFlow.collectAsState(initial = emptyList())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MasivaBlack)
                    .padding(20.dp)
            ) {
                // Header Back Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBackToPlaylistList) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White)
                    }

                    IconButton(
                        onClick = {
                            viewModel.deletePlaylist(selectedPlaylist.id)
                            onBackToPlaylistList()
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Listeyi Sil", tint = Color.Red)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Title details card
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(MasivaGrey, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.QueueMusic, null, tint = MasivaEmerald, modifier = Modifier.size(36.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedPlaylist.name,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${playlistTracks.size} Şarkı",
                            color = MasivaMutedText,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dynamic play entire list
                if (playlistTracks.isNotEmpty()) {
                    Button(
                        onClick = { viewModel.playQueue(playlistTracks) },
                        colors = ButtonDefaults.buttonColors(containerColor = MasivaEmerald),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Listeyi Çal", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Track contents list
                if (playlistTracks.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PlaylistAdd, null, tint = Color.DarkGray, modifier = Modifier.size(60.dp))
                            Text("Bu liste boş.", color = Color.LightGray)
                            Text("Arama kısmından çalma listelerinize şarkı ekleyebilirsiniz.", color = Color.Gray, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(playlistTracks, key = { it.id }) { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MasivaGrey, RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewModel.playQueue(playlistTracks, playlistTracks.indexOf(track))
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TrackCoverArtThumb(track = track, size = 44.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist,
                                        color = MasivaMutedText,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                // Deletion button
                                IconButton(onClick = { viewModel.removeTrackFromPlaylist(selectedPlaylist.id, track.id) }) {
                                    Icon(Icons.Default.RemoveCircleOutline, null, tint = Color.LightGray)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // PLAYLISTS GALLERY SECTOR VIEW
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MasivaBlack)
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kitaplığım",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = onCreatePlaylistClick,
                        modifier = Modifier.background(MasivaCharcoal, CircleShape).testTag("create_playlist_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Ekle", tint = MasivaEmerald)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Default local Favorites Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MasivaGrey)
                        .clickable {
                            if (favoritesList.isNotEmpty()) {
                                viewModel.playQueue(favoritesList)
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(MasivaEmerald, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Favorite, null, tint = Color.Black, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Beğenilen Şarkılar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${favoritesList.size} Şarkı • Otomatik Liste", color = MasivaMutedText, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Çalma Listeleriniz", color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

                Spacer(modifier = Modifier.height(8.dp))

                if (listPlaylists.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.QueueMusic, null, tint = Color.DarkGray, modifier = Modifier.size(54.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Oluşturulmuş çalma listeniz yok.", color = Color.LightGray)
                            Text("Yukarıdaki + ikonuna tıklayarak ilk listenizi hemen oluşturun!", color = Color.Gray, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(listPlaylists) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MasivaGrey)
                                    .clickable { onPlaylistSelected(pl) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(MasivaCharcoal, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.QueueMusic, null, tint = MasivaEmerald)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pl.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Özelleştirilmiş Liste", color = MasivaMutedText, fontSize = 11.sp)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. EQUALIZER & EFFECTS SCREEN TAB
// ==========================================
@Composable
fun EqualizerTab(viewModel: MusicViewModel) {
    val bassStrength by viewModel.bassStrength.collectAsState()
    val virtualizerStrength by viewModel.virtualizerStrength.collectAsState()

    var activePresetIndex by remember { mutableStateOf(0) }
    var scalePulse by remember { mutableStateOf(1f) }

    val animateScale = rememberInfiniteTransition(label = "FXPulse")
    val pulse by animateScale.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MasivaBlack)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Text(
                text = "Ses Efektleri",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Large glowing circular audio dynamic visualizer representation in center
        Box(
            modifier = Modifier
                .size(170.dp)
                .shadow(elevation = 16.dp, shape = CircleShape)
                .background(MasivaCharcoal, CircleShape)
                .border(2.dp, MasivaGlassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(pulse)
                    .background(Color(0x1110B981), CircleShape)
                    .border(1.dp, Color(0x3310B981).copy(alpha = 0.5f), CircleShape)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MasivaEmerald,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("AKUSTİK MOTOR", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Preset collection picker
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MasivaCharcoal),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Ekolayzer Preset Seçimi", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    viewModel.equalizerPresets.forEachIndexed { idx, name ->
                        val selected = activePresetIndex == idx
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) MasivaEmerald else MasivaGrey)
                                .clickable {
                                    activePresetIndex = idx
                                    viewModel.applyEqPreset(idx.toShort())
                                }
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                        ) {
                            Text(
                                text = name,
                                color = if (selected) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Advanced slider modifiers (Bass Boost and Space Virtualizer)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MasivaCharcoal),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Bass boost slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VolumeUp, null, tint = MasivaEmerald, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Yüksek Bass Gücü (Bass Boost)", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                    Text("${(bassStrength.toFloat() / 10).toInt()}%", color = MasivaEmerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Slider(
                    value = bassStrength.toFloat(),
                    onValueChange = { viewModel.updateBassStrength(it.toInt().toShort()) },
                    valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = MasivaEmerald,
                        inactiveTrackColor = MasivaGrey,
                        thumbColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Space Virtualizer slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Hearing, null, tint = MasivaEmerald, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Çevresel Ses (Virtualizer)", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                    Text("${(virtualizerStrength.toFloat() / 10).toInt()}%", color = MasivaEmerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Slider(
                    value = virtualizerStrength.toFloat(),
                    onValueChange = { viewModel.updateVirtualizerStrength(it.toInt().toShort()) },
                    valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = MasivaEmerald,
                        inactiveTrackColor = MasivaGrey,
                        thumbColor = Color.White
                    )
                )
            }
        }
    }
}

// ==========================================
// 5. CORRESPONDING DETAILED VIEW UTILITIES
// ==========================================

@Composable
fun TrackCoverArtThumb(track: Track, size: androidx.compose.ui.unit.Dp) {
    if (track.isSynthTrack || track.artUri == null) {
        // Glowing synth block representation
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF1E3F30), Color(0xFF0F1411))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MasivaEmerald,
                modifier = Modifier.size(size / 2)
            )
        }
    } else {
        AsyncImage(
            model = track.artUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(MasivaGrey)
        )
    }
}

@Composable
fun TrackListRow(
    track: Track,
    onPlayClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onAddPlaylistClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MasivaCharcoal)
            .clickable { onPlayClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackCoverArtThumb(track = track, size = 48.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                color = MasivaMutedText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Action controllers
        Row {
            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (track.isFavorite) MasivaEmerald else Color.LightGray,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onAddPlaylistClick) {
                Icon(
                    imageVector = Icons.Default.PlaylistAdd,
                    contentDescription = "Add Playlist",
                    tint = Color.LightGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ==========================================
// 6. MINI PLAYER LAYOUT
// ==========================================
@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    onPlayPauseToggle: () -> Unit,
    onNextClick: () -> Unit,
    onExpandRequest: () -> Unit
) {
    val progress = remember(position, duration) {
        if (duration > 0) position.toFloat() / duration else 0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(12.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MasivaGrey.copy(alpha = 0.95f))
            .border(1.dp, MasivaGlassBorder.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { onExpandRequest() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackCoverArtThumb(track = track, size = 44.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = MasivaMutedText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Controllers
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlayPauseToggle) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Oynat Durdur",
                        tint = MasivaEmerald,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = onNextClick) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Sonraki Şarkı",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Tiny sleek horizontal seek progress bar under the mini player cards
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(2.5.dp),
            color = MasivaEmerald,
            trackColor = MasivaCharcoal
        )
    }
}

// ==========================================
// 7. FULL PLAYER OVERLAY
// ==========================================
@Composable
fun FullPlayerOverlay(
    track: Track,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    viewModel: MusicViewModel,
    onCloseRequest: () -> Unit,
    onAddTrackToPlaylist: (Track) -> Unit
) {
    val context = LocalContext.current
    val favorites by viewModel.favoriteTracks.collectAsState()
    val shuf by viewModel.shuffleEnabled.collectAsState()
    val rep by viewModel.repeatMode.collectAsState()

    val isFav = favorites.any { it.id == track.id }

    // Floating cover disk animation rotating loop
    val infiniteTransition = rememberInfiniteTransition(label = "CoverDisk")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "DiskAngle"
    )

    // User drag state tracking
    var sliderDragPosition by remember { mutableStateOf<Float?>(null) }
    val displayPosition = sliderDragPosition?.toLong() ?: position

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MasivaBlack)
    ) {
        // Blur cover art layout background
        if (!track.isSynthTrack && track.artUri != null) {
            AsyncImage(
                model = track.artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(28.dp)
                    .graphicsLayer(alpha = 0.2f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header back + listing row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCloseRequest) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Simge Durumuna Küçült", tint = Color.White, modifier = Modifier.size(32.dp))
                }

                Text(
                    text = "ŞİMDİ ÇALIYOR",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                IconButton(onClick = { onAddTrackToPlaylist(track) }) {
                    Icon(Icons.Default.QueueMusic, contentDescription = "Listeye Ekle", tint = Color.White)
                }
            }

            // Big visual album cover disk
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .rotate(if (isPlaying) angle else 0f)
                        .shadow(32.dp, CircleShape, ambientColor = MasivaEmerald, spotColor = MasivaMint)
                        .clip(CircleShape)
                        .background(MasivaCharcoal)
                        .border(10.dp, MasivaGrey, CircleShape)
                        .border(12.dp, MasivaGlassBorder, CircleShape)
                ) {
                    if (track.isSynthTrack || track.artUri == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, null, tint = MasivaEmerald, modifier = Modifier.size(90.dp))
                        }
                    } else {
                        AsyncImage(
                            model = track.artUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Metadata info & Fav indicator
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = track.artist,
                            color = MasivaEmerald,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = { viewModel.toggleFavorite(track) }) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isFav) MasivaEmerald else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Custom Offline Lyrics / System description
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MasivaGrey)
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("Şarkı Sözleri (Lyrics)", color = MasivaEmerald, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (track.isSynthTrack) {
                                "♪ Akustik Sentezleyici Motoru tarafından matematiksel ses sinyali ile gerçek zamanlı çalınıyor. Kaynak: $0, durak: 0"
                            } else {
                                "♪ Çevrimdışı disk dosyası yürütülüyor. Sözler internet bağlantısı olmadan yüklenemedi."
                            },
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Scrubber sliders
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.8f, fill = false)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Slider(
                    value = displayPosition.toFloat(),
                    onValueChange = { sliderDragPosition = it },
                    onValueChangeFinished = {
                        sliderDragPosition?.let {
                            viewModel.seekTo(it.toLong())
                            sliderDragPosition = null
                        }
                    },
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        activeTrackColor = MasivaEmerald,
                        inactiveTrackColor = MasivaGrey,
                        thumbColor = Color.White
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = formatDuration(displayPosition), color = MasivaMutedText, fontSize = 11.sp)
                    Text(text = formatDuration(duration), color = MasivaMutedText, fontSize = 11.sp)
                }
            }

            // Action controllers (Shuffle, Prev, Play, Next, Repeat)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle action
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Karıştır",
                        tint = if (shuf) MasivaEmerald else Color.LightGray,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Skip prev action
                IconButton(onClick = { viewModel.skipToPrevious() }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Geri",
                        tint = Color.White,
                        modifier = Modifier.size(31.dp)
                    )
                }

                // Center Play/Pause button
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(68.dp)
                        .background(MasivaEmerald, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Oynat Durdur",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Skip next action
                IconButton(onClick = { viewModel.skipToNext() }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "İleri",
                        tint = Color.White,
                        modifier = Modifier.size(31.dp)
                    )
                }

                // Repeat action
                IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                    val repIcon = if (rep == 2) Icons.Default.RepeatOne else Icons.Default.Repeat
                    Icon(
                        imageVector = repIcon,
                        contentDescription = "Tekrar Et",
                        tint = if (rep != 0) MasivaEmerald else Color.LightGray,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// Format duration helper (m:ss)
private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    return String.format("%d:%02d", minutes, seconds)
}

package ai.iskra.desktop.ui

import ai.iskra.desktop.model.*
import ai.iskra.desktop.network.IskraApiClient
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

enum class DesktopRoute {
    AUTH, HOME, CATALOG, COLLECTION, CREATE_BOT, PROFILE, CHAT, STORY_READER, TAVERN
}

val GENRE_LABELS: List<Pair<String, String>> = listOf(
    "fantasy" to "Фэнтези",
    "horror" to "Хоррор",
    "romance" to "Романтика",
    "sci_fi" to "Научная фантастика",
    "drama" to "Драма",
    "comedy" to "Комедия",
    "slice_of_life" to "Повседневность",
    "historical" to "История",
    "other" to "Другое"
)

fun genreLabel(key: String): String =
    GENRE_LABELS.firstOrNull { it.first == key }?.second ?: key

fun initialsFromName(raw: String): String {
    val parts = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return "✦"
    if (parts.size >= 2) {
        return "${parts[0].first()}${parts[1].first()}".uppercase()
    }
    return parts[0].take(2).uppercase()
}

suspend fun pickImageFile(): File? = withContext(Dispatchers.IO) {
    runCatching {
        val dialog = FileDialog(null as Frame?, "Выберите изображение аватара", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            val lower = name.lowercase()
            lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
        }
        dialog.isVisible = true
        val dir = dialog.directory
        val file = dialog.file
        if (dir != null && file != null) File(dir, file) else null
    }.getOrElse {
        runCatching {
            val chooser = JFileChooser()
            chooser.fileFilter = FileNameExtensionFilter("Изображения (PNG, JPG, WEBP)", "png", "jpg", "jpeg", "webp")
            val res = chooser.showOpenDialog(null)
            if (res == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
        }.getOrNull()
    }
}

@Composable
fun BrandMark(
    size: Dp = 40.dp,
    tint: Color = IskraColors.text
) {
    val logoBitmap = remember {
        runCatching {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("logo-spark.png")
                ?: Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")
            stream?.use { bytes ->
                SkiaImage.makeFromEncoded(bytes.readBytes()).toComposeImageBitmap()
            }
        }.getOrNull()
    }

    if (logoBitmap != null) {
        Image(
            bitmap = logoBitmap,
            contentDescription = "IskraAi",
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(tint)
        )
    } else {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w * 0.56f
            val cy = h * 0.44f
            val rOuter = w * 0.36f
            val rInner = w * 0.09f
            val starPath = Path().apply {
                moveTo(cx, cy - rOuter)
                quadraticTo(cx + rInner * 0.4f, cy - rInner * 0.4f, cx + rOuter, cy)
                quadraticTo(cx + rInner * 0.4f, cy + rInner * 0.4f, cx, cy + rOuter)
                quadraticTo(cx - rInner * 0.4f, cy + rInner * 0.4f, cx - rOuter, cy)
                quadraticTo(cx - rInner * 0.4f, cy - rInner * 0.4f, cx, cy - rOuter)
                close()
            }
            drawPath(starPath, color = tint)
            drawArc(
                color = tint,
                startAngle = 35f,
                sweepAngle = 210f,
                useCenter = false,
                topLeft = Offset(w * 0.14f, h * 0.18f),
                size = androidx.compose.ui.geometry.Size(w * 0.68f, h * 0.68f),
                style = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun RemoteImage(
    url: String?,
    api: IskraApiClient,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackText: String = "✦"
) {
    var imageBitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            imageBitmap = null
            return@LaunchedEffect
        }
        failed = false
        val bytes = api.fetchImageBytes(url)
        if (bytes != null && bytes.isNotEmpty()) {
            val decoded = runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
            if (decoded != null) {
                imageBitmap = decoded
            } else {
                failed = true
            }
        } else {
            failed = true
        }
    }

    val bmp = imageBitmap
    if (bmp != null && !failed) {
        Image(
            bitmap = bmp,
            contentDescription = fallbackText,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier.background(
                Brush.linearGradient(
                    colors = listOf(IskraColors.surface3, IskraColors.surface2)
                )
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initialsFromName(fallbackText),
                color = IskraColors.muted,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

fun formatMessageRichText(raw: String, textColor: Color, mutedColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val n = raw.length
        while (i < n) {
            if (raw.startsWith("**", i)) {
                val end = raw.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                        append(raw.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }
            if (raw[i] == '*') {
                val end = raw.indexOf('*', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = mutedColor)) {
                        append(raw.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }
            withStyle(SpanStyle(color = textColor)) {
                append(raw[i])
            }
            i++
        }
    }
}

@Composable
fun UserTierBadge(tier: String) {
    val normalized = tier.lowercase()
    val (bg, fg, label) = when (normalized) {
        "max", "ultra", "admin" -> Triple(Color(0x26F59E0B), Color(0xFFFBBF24), tier.uppercase())
        "pro", "plus", "vip" -> Triple(IskraColors.accentSoft, IskraColors.text, tier.uppercase())
        else -> Triple(IskraColors.surface3, IskraColors.muted, "FREE")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, IskraColors.border, RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
fun IskraDesktopApp() {
    val api = remember { IskraApiClient() }
    var palette by remember { mutableStateOf(api.session?.paletteId ?: "classic") }
    var isDark by remember { mutableStateOf((api.session?.themeMode ?: "dark") != "light") }
    var wallpapersEnabled by remember { mutableStateOf(api.session?.wallpapersEnabled ?: false) }

    IskraTheme(palette = palette, isDark = isDark) {
        val scope = rememberCoroutineScope()

        var currentRoute by remember {
            mutableStateOf(if (api.hasValidToken()) DesktopRoute.HOME else DesktopRoute.AUTH)
        }
        var currentUser by remember { mutableStateOf<UserProfile?>(null) }
        var bots by remember { mutableStateOf<List<ApiBot>>(emptyList()) }
        var myBots by remember { mutableStateOf<List<ApiBot>>(emptyList()) }
        var stories by remember { mutableStateOf<List<StorySummary>>(emptyList()) }
        var myStories by remember { mutableStateOf<List<StorySummary>>(emptyList()) }
        var recentBots by remember { mutableStateOf<List<ApiBot>>(emptyList()) }

        var selectedBot by remember { mutableStateOf<ApiBot?>(null) }
        var editingBot by remember { mutableStateOf<ApiBot?>(null) }
        var selectedStory by remember { mutableStateOf<StorySummary?>(null) }
        var isLoading by remember { mutableStateOf(false) }
        var statusBanner by remember { mutableStateOf<String?>(null) }
        var showSettingsModal by remember { mutableStateOf(false) }

        fun saveAppearance(newPalette: String, newDark: Boolean, newWallpapers: Boolean = wallpapersEnabled) {
            palette = newPalette
            isDark = newDark
            wallpapersEnabled = newWallpapers
            api.updateThemePreferences(
                themeMode = if (newDark) "dark" else "light",
                paletteId = newPalette,
                wallpapersEnabled = newWallpapers
            )
        }

        fun refreshAll() {
            scope.launch {
                isLoading = true
                statusBanner = null
                runCatching {
                    val loadedBots = api.getBots()
                    bots = loadedBots
                    stories = runCatching { api.getStories() }.getOrDefault(emptyList())
                    if (api.hasValidToken()) {
                        currentUser = runCatching { api.getProfile() }.getOrNull()
                        myBots = runCatching { api.getMyBots() }.getOrDefault(emptyList())
                        myStories = runCatching { api.getMyStories() }.getOrDefault(emptyList())
                        val convs = runCatching { api.getConversations() }.getOrDefault(emptyList())
                        if (convs.isNotEmpty()) {
                            val botMap = loadedBots.associateBy { it.id }
                            recentBots = convs.mapNotNull { botMap[it.botId] }.distinctBy { it.id }.take(10)
                        }
                    }
                    if (recentBots.isEmpty() && loadedBots.isNotEmpty()) {
                        recentBots = loadedBots.take(5)
                    }
                }.onFailure { e ->
                    statusBanner = e.message ?: "Ошибка соединения с сервером"
                }
                isLoading = false
            }
        }

        fun openChatWith(bot: ApiBot) {
            if (!api.hasValidToken()) {
                currentRoute = DesktopRoute.AUTH
                return
            }
            selectedBot = bot
            recentBots = (listOf(bot) + recentBots.filterNot { it.id == bot.id }).take(10)
            currentRoute = DesktopRoute.CHAT
        }

        fun toggleBotLike(bot: ApiBot) {
            if (!api.hasValidToken()) {
                currentRoute = DesktopRoute.AUTH
                return
            }
            scope.launch {
                runCatching {
                    val resp = api.toggleBotLike(bot.id)
                    bots = bots.map {
                        if (it.id == bot.id) it.copy(isLiked = resp.isLiked, likesCount = resp.likesCount) else it
                    }
                    myBots = myBots.map {
                        if (it.id == bot.id) it.copy(isLiked = resp.isLiked, likesCount = resp.likesCount) else it
                    }
                    if (selectedBot?.id == bot.id) {
                        selectedBot = selectedBot?.copy(isLiked = resp.isLiked, likesCount = resp.likesCount)
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            scope.launch { api.warmBackend() }
            refreshAll()
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = IskraColors.canvas,
            contentColor = IskraColors.text
        ) {
            if (currentRoute == DesktopRoute.AUTH) {
                AuthPage(
                    api = api,
                    onAuthenticated = {
                        currentRoute = DesktopRoute.HOME
                        refreshAll()
                    },
                    onContinueGuest = {
                        currentRoute = DesktopRoute.HOME
                        refreshAll()
                    }
                )
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    IskraSidebar(
                        api = api,
                        currentRoute = currentRoute,
                        user = currentUser,
                        isLoggedIn = api.hasValidToken(),
                        recentBots = recentBots,
                        selectedBotId = if (currentRoute == DesktopRoute.CHAT) selectedBot?.id else null,
                        isDark = isDark,
                        onNavigate = { route ->
                            if (route == DesktopRoute.CREATE_BOT) editingBot = null
                            if ((route == DesktopRoute.COLLECTION || route == DesktopRoute.PROFILE || route == DesktopRoute.CREATE_BOT) && !api.hasValidToken()) {
                                currentRoute = DesktopRoute.AUTH
                            } else {
                                currentRoute = route
                            }
                        },
                        onSelectRecentBot = { openChatWith(it) },
                        onRemoveRecentBot = { bot -> recentBots = recentBots.filterNot { it.id == bot.id } },
                        onOpenSettings = {
                            if (!api.hasValidToken()) currentRoute = DesktopRoute.AUTH
                            else showSettingsModal = true
                        },
                        onToggleDark = { saveAppearance(palette, !isDark) }
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(IskraColors.canvas)
                    ) {
                        when (currentRoute) {
                            DesktopRoute.HOME -> HomePage(
                                api = api,
                                bots = bots,
                                stories = stories,
                                isLoading = isLoading,
                                errorText = statusBanner,
                                onRefresh = ::refreshAll,
                                onOpenCatalog = { currentRoute = DesktopRoute.CATALOG },
                                onOpenCreate = {
                                    editingBot = null
                                    if (!api.hasValidToken()) currentRoute = DesktopRoute.AUTH
                                    else currentRoute = DesktopRoute.CREATE_BOT
                                },
                                onOpenTavern = {
                                    if (!api.hasValidToken()) currentRoute = DesktopRoute.AUTH
                                    else currentRoute = DesktopRoute.TAVERN
                                },
                                onSelectBot = ::openChatWith,
                                onToggleLike = ::toggleBotLike,
                                onSelectStory = { story ->
                                    if (!api.hasValidToken()) {
                                        currentRoute = DesktopRoute.AUTH
                                    } else {
                                        selectedStory = story
                                        currentRoute = DesktopRoute.STORY_READER
                                    }
                                }
                            )

                            DesktopRoute.CATALOG -> CatalogPage(
                                api = api,
                                bots = bots,
                                isLoading = isLoading,
                                errorText = statusBanner,
                                onRefresh = ::refreshAll,
                                onSelectBot = ::openChatWith,
                                onToggleLike = ::toggleBotLike
                            )

                            DesktopRoute.COLLECTION -> CollectionPage(
                                api = api,
                                createdBots = myBots,
                                likedBots = bots.filter { it.isLiked },
                                createdStories = myStories,
                                allStories = stories,
                                onSelectBot = ::openChatWith,
                                onToggleLike = ::toggleBotLike,
                                onCreateBot = {
                                    editingBot = null
                                    currentRoute = DesktopRoute.CREATE_BOT
                                },
                                onEditBot = { bot ->
                                    editingBot = bot
                                    currentRoute = DesktopRoute.CREATE_BOT
                                },
                                onDeleteBot = { bot ->
                                    scope.launch {
                                        runCatching { api.deleteBot(bot.id) }
                                        refreshAll()
                                    }
                                },
                                onSelectStory = { story ->
                                    selectedStory = story
                                    currentRoute = DesktopRoute.STORY_READER
                                }
                            )

                            DesktopRoute.CREATE_BOT -> CreateBotPage(
                                api = api,
                                existingBot = editingBot,
                                onSaved = { saved ->
                                    editingBot = null
                                    refreshAll()
                                    openChatWith(saved)
                                },
                                onCancel = { currentRoute = DesktopRoute.COLLECTION }
                            )

                            DesktopRoute.PROFILE -> ProfilePage(
                                api = api,
                                user = currentUser,
                                currentPalette = palette,
                                isDark = isDark,
                                wallpapersEnabled = wallpapersEnabled,
                                onSaveAppearance = ::saveAppearance,
                                onUserUpdated = { refreshAll() },
                                onOpenSettings = { showSettingsModal = true },
                                onLogout = {
                                    scope.launch {
                                        api.logout()
                                        currentUser = null
                                        currentRoute = DesktopRoute.AUTH
                                    }
                                }
                            )

                            DesktopRoute.CHAT -> {
                                val bot = selectedBot ?: bots.firstOrNull()
                                if (bot != null) {
                                    ChatPage(
                                        bot = bot,
                                        api = api,
                                        wallpapersEnabled = wallpapersEnabled,
                                        onToggleLike = { toggleBotLike(bot) },
                                        onBack = { currentRoute = DesktopRoute.HOME }
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Выберите персонажа на главной или в каталоге", color = IskraColors.muted)
                                    }
                                }
                            }

                            DesktopRoute.STORY_READER -> {
                                val story = selectedStory
                                if (story != null) {
                                    StoryReaderPage(
                                        story = story,
                                        api = api,
                                        onBack = { currentRoute = DesktopRoute.HOME }
                                    )
                                } else {
                                    currentRoute = DesktopRoute.HOME
                                }
                            }

                            DesktopRoute.TAVERN -> TavernPage(
                                api = api,
                                bots = bots,
                                onOpenBotChat = ::openChatWith
                            )

                            DesktopRoute.AUTH -> Unit
                        }
                    }
                }
            }

            if (showSettingsModal) {
                SettingsModal(
                    api = api,
                    user = currentUser,
                    currentPalette = palette,
                    isDark = isDark,
                    wallpapersEnabled = wallpapersEnabled,
                    onSaveAppearance = ::saveAppearance,
                    onUserUpdated = { refreshAll() },
                    onLogout = {
                        showSettingsModal = false
                        scope.launch {
                            api.logout()
                            currentUser = null
                            currentRoute = DesktopRoute.AUTH
                        }
                    },
                    onDismiss = { showSettingsModal = false }
                )
            }
        }
    }
}

@Composable
private fun IskraSidebar(
    api: IskraApiClient,
    currentRoute: DesktopRoute,
    user: UserProfile?,
    isLoggedIn: Boolean,
    recentBots: List<ApiBot>,
    selectedBotId: String?,
    isDark: Boolean,
    onNavigate: (DesktopRoute) -> Unit,
    onSelectRecentBot: (ApiBot) -> Unit,
    onRemoveRecentBot: (ApiBot) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleDark: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(228.dp)
            .fillMaxHeight()
            .background(IskraColors.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable { onNavigate(DesktopRoute.HOME) }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            BrandMark(size = 32.dp)
            Text(
                text = "IskraAi",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = IskraColors.text
            )
        }
        HorizontalDivider(color = IskraColors.border)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            SidebarNavButton(
                label = "Главная",
                icon = Icons.Default.Home,
                active = currentRoute == DesktopRoute.HOME,
                onClick = { onNavigate(DesktopRoute.HOME) }
            )
            SidebarNavButton(
                label = "Каталог",
                icon = Icons.Default.GridView,
                active = currentRoute == DesktopRoute.CATALOG,
                onClick = { onNavigate(DesktopRoute.CATALOG) }
            )
            SidebarNavButton(
                label = "Коллекция",
                icon = Icons.Default.FavoriteBorder,
                active = currentRoute == DesktopRoute.COLLECTION,
                onClick = { onNavigate(DesktopRoute.COLLECTION) }
            )
            SidebarNavButton(
                label = "Профиль",
                icon = Icons.Default.Person,
                active = currentRoute == DesktopRoute.PROFILE,
                onClick = { onNavigate(DesktopRoute.PROFILE) }
            )

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = IskraColors.border)
            Spacer(Modifier.height(4.dp))

            SidebarNavButton(
                label = "Создать персонажа",
                icon = Icons.Default.Add,
                active = currentRoute == DesktopRoute.CREATE_BOT,
                onClick = { onNavigate(DesktopRoute.CREATE_BOT) }
            )
            SidebarNavButton(
                label = "Таверна",
                icon = Icons.Default.Groups,
                active = currentRoute == DesktopRoute.TAVERN,
                badgeText = "комната",
                onClick = { onNavigate(DesktopRoute.TAVERN) }
            )

            if (recentBots.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = IskraColors.border)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "НЕДАВНИЕ ЧАТЫ",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = IskraColors.muted,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    recentBots.forEach { bot ->
                        val isSelected = selectedBotId == bot.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) IskraColors.accentSoft else Color.Transparent)
                                .clickable { onSelectRecentBot(bot) }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RemoteImage(
                                url = bot.avatarUrl,
                                api = api,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, IskraColors.border, CircleShape),
                                fallbackText = bot.name
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bot.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) IskraColors.accent else IskraColors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!bot.description.isNullOrBlank()) {
                                    Text(
                                        text = "— ${bot.description}",
                                        fontSize = 10.sp,
                                        color = IskraColors.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Text(
                                text = "×",
                                fontSize = 13.sp,
                                color = IskraColors.subtle,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onRemoveRecentBot(bot) }
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = IskraColors.border)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(IskraColors.surface)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isLoggedIn) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 5.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RemoteImage(
                        url = user?.effectiveAvatarUrl ?: api.session?.cachedAvatarUrl,
                        api = api,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .border(1.dp, IskraColors.border, CircleShape),
                        fallbackText = user?.displayName?.ifBlank { "Я" } ?: "Я"
                    )
                    Text(
                        text = user?.displayName?.ifBlank { "Профиль" } ?: "Профиль",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = IskraColors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    UserTierBadge(user?.tier ?: "free")
                }
            } else {
                Button(
                    onClick = { onNavigate(DesktopRoute.AUTH) },
                    modifier = Modifier.weight(1f).height(34.dp),
                    shape = RoundedCornerShape(9.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IskraColors.accent,
                        contentColor = IskraColors.accentInk
                    )
                ) {
                    Text("Войти", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .border(1.dp, IskraColors.border, RoundedCornerShape(9.dp))
                    .clickable { onToggleDark() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Тема",
                    tint = IskraColors.muted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
    VerticalDivider(color = IskraColors.border, thickness = 1.dp)
}

@Composable
private fun SidebarNavButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) IskraColors.accentSoft else Color.Transparent)
            .then(
                if (active) Modifier.border(1.dp, IskraColors.accent.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) IskraColors.accent else IskraColors.muted,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = if (active) IskraColors.accent else IskraColors.muted,
            modifier = Modifier.weight(1f)
        )
        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(IskraColors.surface3)
                    .border(1.dp, IskraColors.border, RoundedCornerShape(999.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(badgeText, fontSize = 9.sp, color = IskraColors.accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun CharacterPoster(
    bot: ApiBot,
    api: IskraApiClient,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier.width(152.dp).height(200.dp)
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(IskraColors.surface2)
            .border(1.dp, IskraColors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        RemoteImage(
            url = bot.avatarUrl,
            api = api,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            fallbackText = bot.name
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.45f to Color(0x2E000000),
                        1.0f to Color(0xEB000000)
                    )
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = bot.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = (bot.description ?: "").ifBlank { genreLabel(bot.genre) },
                    color = Color(0xB8FFFFFF),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (bot.isLiked) Color(0x99F43F5E) else Color(0x8C000000))
                    .border(
                        1.dp,
                        if (bot.isLiked) Color(0xCCF43F5E) else Color(0x24FFFFFF),
                        RoundedCornerShape(999.dp)
                    )
                    .clickable { onToggleLike() }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = if (bot.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Нравится",
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = bot.likesCount.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun StoryCardItem(
    story: StorySummary,
    api: IskraApiClient,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(236.dp)
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(IskraColors.surface)
            .border(1.dp, IskraColors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        if (!story.coverUrl.isNullOrBlank()) {
            RemoteImage(
                url = story.coverUrl,
                api = api,
                modifier = Modifier.fillMaxWidth().height(106.dp),
                contentScale = ContentScale.Crop,
                fallbackText = story.title
            )
        }
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ИСТОРИЯ",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IskraColors.subtle
                )
                if (story.likesCount > 0) {
                    Text("♥ ${story.likesCount}", fontSize = 11.sp, color = IskraColors.muted)
                }
            }
            Text(
                text = story.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = IskraColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = story.description.ifBlank { story.subtitle.ifBlank { "Интерактивная история" } },
                fontSize = 11.sp,
                color = IskraColors.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomePage(
    api: IskraApiClient,
    bots: List<ApiBot>,
    stories: List<StorySummary>,
    isLoading: Boolean,
    errorText: String?,
    onRefresh: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenTavern: () -> Unit,
    onSelectBot: (ApiBot) -> Unit,
    onToggleLike: (ApiBot) -> Unit,
    onSelectStory: (StorySummary) -> Unit
) {
    val scrollState = rememberScrollState()
    val popularBots = remember(bots) {
        bots.sortedByDescending { it.likesCount }.take(12)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(IskraColors.surface2, IskraColors.surface)
                    )
                )
                .border(1.dp, IskraColors.border, RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(IskraColors.surface)
                            .border(1.dp, IskraColors.border, RoundedCornerShape(999.dp))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("✦", fontSize = 9.sp, color = IskraColors.text)
                        Text(
                            text = "ISKRAAI",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IskraColors.muted
                        )
                    }

                    Text(
                        text = "Начните новую историю",
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IskraColors.text
                    )
                    Text(
                        text = "Выберите героя или авторский сюжет. Ваши решения меняют происходящее.",
                        fontSize = 12.sp,
                        color = IskraColors.muted
                    )

                    Spacer(Modifier.height(2.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onOpenCatalog,
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IskraColors.accent,
                                contentColor = IskraColors.accentInk
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Text("Каталог", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        OutlinedButton(
                            onClick = onOpenCreate,
                            modifier = Modifier.height(34.dp),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, IskraColors.borderStrong),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = IskraColors.surface2,
                                contentColor = IskraColors.text
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Text("+ Создать", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .border(1.dp, IskraColors.borderStrong, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(74.dp)
                            .clip(CircleShape)
                            .background(IskraColors.surface)
                            .border(1.dp, IskraColors.border, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            BrandMark(size = 20.dp)
                            Text(
                                text = "Истории\nотвечают",
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                                color = IskraColors.muted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        if (errorText != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(IskraColors.surface)
                    .border(1.dp, IskraColors.border, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(errorText, color = IskraColors.muted, fontSize = 12.sp)
                TextButton(onClick = onRefresh) {
                    Text("Повторить", color = IskraColors.text, fontSize = 12.sp)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "Популярные персонажи",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IskraColors.text
                )
                Text(
                    text = "Все →",
                    fontSize = 12.sp,
                    color = IskraColors.muted,
                    modifier = Modifier.clickable { onOpenCatalog() }
                )
            }

            if (isLoading && popularBots.isEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(5) {
                        Box(
                            modifier = Modifier
                                .width(152.dp)
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(IskraColors.surface2)
                        )
                    }
                }
            } else if (popularBots.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(IskraColors.surface)
                        .border(1.dp, IskraColors.border, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Персонажи пока не добавлены.", color = IskraColors.muted, fontSize = 12.sp)
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(popularBots, key = { it.id }) { bot ->
                        CharacterPoster(
                            bot = bot,
                            api = api,
                            onClick = { onSelectBot(bot) },
                            onToggleLike = { onToggleLike(bot) }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(IskraColors.surface)
                .border(1.dp, IskraColors.border, RoundedCornerShape(16.dp))
                .clickable { onOpenTavern() }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Таверна",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IskraColors.text
                )
                Text(
                    text = "Не знаешь с кем пообщаться? Случайный собеседник, дуэт или трио в одной комнате.",
                    fontSize = 12.sp,
                    color = IskraColors.muted
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(IskraColors.surface2)
                    .border(1.dp, IskraColors.border, RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text("Перейти →", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = IskraColors.text)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "Истории",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IskraColors.text
                )
                Text(
                    text = "Все →",
                    fontSize = 13.sp,
                    color = IskraColors.muted,
                    modifier = Modifier.clickable { onOpenCatalog() }
                )
            }

            if (stories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(IskraColors.surface)
                        .border(1.dp, IskraColors.border, RoundedCornerShape(18.dp))
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Интерактивные истории скоро появятся.", color = IskraColors.muted)
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(stories.take(10), key = { it.id }) { story ->
                        StoryCardItem(
                            story = story,
                            api = api,
                            onClick = { onSelectStory(story) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogPage(
    api: IskraApiClient,
    bots: List<ApiBot>,
    isLoading: Boolean,
    errorText: String?,
    onRefresh: () -> Unit,
    onSelectBot: (ApiBot) -> Unit,
    onToggleLike: (ApiBot) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf<String?>(null) }

    val visibleBots = remember(bots, searchQuery, selectedGenre) {
        bots.filter { bot ->
            val matchesQuery = searchQuery.isBlank() ||
                bot.name.contains(searchQuery, ignoreCase = true) ||
                (bot.description ?: "").contains(searchQuery, ignoreCase = true)
            val matchesGenre = selectedGenre == null || bot.genre == selectedGenre
            matchesQuery && matchesGenre
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "ВСЕ ПЕРСОНАЖИ",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = IskraColors.subtle
            )
            Text(
                text = "Каталог",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = IskraColors.text
            )
            Text(
                text = "Персонажи разделены по жанрам и отсортированы по лайкам.",
                fontSize = 12.sp,
                color = IskraColors.muted
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                textStyle = TextStyle(color = IskraColors.text, fontSize = 13.sp),
                cursorBrush = SolidColor(IskraColors.accent),
                modifier = Modifier.width(260.dp),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(IskraColors.surface)
                            .border(1.dp, IskraColors.border, RoundedCornerShape(10.dp))
                            .padding(horizontal = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = IskraColors.muted, modifier = Modifier.size(16.dp))
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (searchQuery.isEmpty()) {
                                Text("Найти персонажа", color = IskraColors.subtle, fontSize = 12.sp)
                            }
                            innerTextField()
                        }
                    }
                }
            )

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChipButton(
                        label = "Жанры (Все)",
                        active = selectedGenre == null,
                        onClick = { selectedGenre = null }
                    )
                }
                items(GENRE_LABELS) { (key, label) ->
                    val active = selectedGenre == key
                    FilterChipButton(
                        label = label,
                        active = active,
                        onClick = { selectedGenre = if (active) null else key }
                    )
                }
            }
        }

        if (isLoading && bots.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = IskraColors.text)
            }
        } else if (errorText != null && bots.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(IskraColors.surface)
                    .border(1.dp, IskraColors.border, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(errorText, color = IskraColors.muted)
                TextButton(onClick = onRefresh) { Text("Повторить", color = IskraColors.text) }
            }
        } else {
            if (visibleBots.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Популярное", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = IskraColors.text)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(visibleBots.take(10), key = { "pop-${it.id}" }) { bot ->
                            CharacterPoster(
                                bot = bot,
                                api = api,
                                onClick = { onSelectBot(bot) },
                                onToggleLike = { onToggleLike(bot) }
                            )
                        }
                    }
                }
            }

            GENRE_LABELS.forEach { (genreKey, genreTitle) ->
                val genreBots = visibleBots.filter { it.genre == genreKey }
                if (genreBots.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(genreTitle, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = IskraColors.text)
                            Text("${genreBots.size}", fontSize = 12.sp, color = IskraColors.muted)
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(genreBots, key = { "${genreKey}-${it.id}" }) { bot ->
                                CharacterPoster(
                                    bot = bot,
                                    api = api,
                                    onClick = { onSelectBot(bot) },
                                    onToggleLike = { onToggleLike(bot) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipButton(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) IskraColors.text else IskraColors.surface)
            .border(1.dp, if (active) IskraColors.text else IskraColors.border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            color = if (active) IskraColors.canvas else IskraColors.muted
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionPage(
    api: IskraApiClient,
    createdBots: List<ApiBot>,
    likedBots: List<ApiBot>,
    createdStories: List<StorySummary>,
    allStories: List<StorySummary>,
    onSelectBot: (ApiBot) -> Unit,
    onToggleLike: (ApiBot) -> Unit,
    onCreateBot: () -> Unit,
    onEditBot: (ApiBot) -> Unit,
    onDeleteBot: (ApiBot) -> Unit,
    onSelectStory: (StorySummary) -> Unit
) {
    var mainTab by remember { mutableStateOf("created") }
    var subTab by remember { mutableStateOf("bots") }

    val currentBots = if (mainTab == "created") createdBots else likedBots
    val currentStories = if (mainTab == "created") createdStories else allStories.filter { it.likesCount > 0 }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    text = "Созданное",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (mainTab == "created") IskraColors.text else IskraColors.subtle,
                    modifier = Modifier.clickable { mainTab = "created" }
                )
                Text(
                    text = "Избранное",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (mainTab == "liked") IskraColors.text else IskraColors.subtle,
                    modifier = Modifier.clickable { mainTab = "liked" }
                )
            }

            Button(
                onClick = onCreateBot,
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = IskraColors.accent,
                    contentColor = IskraColors.accentInk
                )
            ) {
                Text("+ Персонаж", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(IskraColors.surface)
                .border(1.dp, IskraColors.border, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val botsActive = subTab == "bots"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (botsActive) IskraColors.surface2 else Color.Transparent)
                    .clickable { subTab = "bots" }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Персонажи (${currentBots.size})",
                    fontSize = 13.sp,
                    fontWeight = if (botsActive) FontWeight.Medium else FontWeight.Normal,
                    color = if (botsActive) IskraColors.text else IskraColors.muted
                )
            }
            val storiesActive = subTab == "stories"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (storiesActive) IskraColors.surface2 else Color.Transparent)
                    .clickable { subTab = "stories" }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Истории (${currentStories.size})",
                    fontSize = 13.sp,
                    fontWeight = if (storiesActive) FontWeight.Medium else FontWeight.Normal,
                    color = if (storiesActive) IskraColors.text else IskraColors.muted
                )
            }
        }

        if (subTab == "bots") {
            if (currentBots.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(IskraColors.surface)
                        .border(1.dp, IskraColors.border, RoundedCornerShape(18.dp))
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (mainTab == "created") "Вы ещё не создали ни одного персонажа." else "В избранном пока нет персонажей.",
                        color = IskraColors.muted
                    )
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    currentBots.forEach { bot ->
                        Box {
                            CharacterPoster(
                                bot = bot,
                                api = api,
                                onClick = { onSelectBot(bot) },
                                onToggleLike = { onToggleLike(bot) }
                            )
                            if (mainTab == "created") {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xB3000000))
                                            .clickable { onEditBot(bot) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Изменить", tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xB3000000))
                                            .clickable { onDeleteBot(bot) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color(0xFFF87171), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (currentStories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(IskraColors.surface)
                        .border(1.dp, IskraColors.border, RoundedCornerShape(18.dp))
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Историй в этом разделе пока нет.", color = IskraColors.muted)
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    currentStories.forEach { story ->
                        StoryCardItem(
                            story = story,
                            api = api,
                            onClick = { onSelectStory(story) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateBotPage(
    api: IskraApiClient,
    existingBot: ApiBot?,
    onSaved: (ApiBot) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember(existingBot) { mutableStateOf(existingBot?.name ?: "") }
    var description by remember(existingBot) { mutableStateOf(existingBot?.description ?: "") }
    var avatarUrl by remember(existingBot) { mutableStateOf(existingBot?.avatarUrl ?: "") }
    var systemPrompt by remember(existingBot) { mutableStateOf(existingBot?.systemPrompt ?: "") }
    var genre by remember(existingBot) { mutableStateOf(existingBot?.genre ?: "fantasy") }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val previewBot = ApiBot(
        id = existingBot?.id ?: "preview",
        name = name.ifBlank { "Имя персонажа" },
        description = description.ifBlank { "Краткое описание героя" },
        avatarUrl = avatarUrl.ifBlank { null },
        genre = genre
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(
            modifier = Modifier
                .width(214.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(IskraColors.surface)
                .border(1.dp, IskraColors.border, RoundedCornerShape(18.dp))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Предпросмотр", fontSize = 11.sp, color = IskraColors.subtle)
            CharacterPoster(
                bot = previewBot,
                api = api,
                onClick = {},
                onToggleLike = {},
                modifier = Modifier.width(172.dp).height(228.dp)
            )
            Text(
                text = genreLabel(genre),
                fontSize = 11.sp,
                color = IskraColors.muted
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(18.dp))
                .background(IskraColors.surface)
                .border(1.dp, IskraColors.border, RoundedCornerShape(18.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Text(
                text = if (existingBot != null) "Редактировать персонажа" else "Создать персонажа",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = IskraColors.text
            )

            IskraField(value = name, onValueChange = { name = it }, label = "Имя персонажа", placeholder = "Например: Мира Вельская")
            IskraField(value = description, onValueChange = { description = it }, label = "Краткое описание (в карточке)", placeholder = "Кто этот персонаж и с чего начинается история")
            IskraField(value = avatarUrl, onValueChange = { avatarUrl = it }, label = "URL аватара (необязательно)", placeholder = "https://...")
            IskraField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = "Характер, сценарий и поведение (System Prompt)",
                placeholder = "Опишите личность, предысторию, манеру речи и стартовую сцену...",
                minLines = 4
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Жанр", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = IskraColors.muted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GENRE_LABELS.forEach { (key, label) ->
                        FilterChipButton(
                            label = label,
                            active = genre == key,
                            onClick = { genre = key }
                        )
                    }
                }
            }

            if (errorMsg != null) {
                Text(errorMsg!!, color = IskraColors.danger, fontSize = 12.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (name.isBlank() || description.isBlank() || systemPrompt.isBlank()) {
                            errorMsg = "Заполните имя, описание и характер персонажа"
                            return@Button
                        }
                        scope.launch {
                            busy = true
                            errorMsg = null
                            runCatching {
                                val req = CreateBotRequest(
                                    name = name.trim(),
                                    genre = genre,
                                    description = description.trim(),
                                    systemPrompt = systemPrompt.trim(),
                                    avatarUrl = avatarUrl.trim().ifBlank { null }
                                )
                                if (existingBot != null) api.updateBot(existingBot.id, req)
                                else api.createBot(req)
                            }.onSuccess { saved ->
                                onSaved(saved)
                            }.onFailure { e ->
                                errorMsg = e.message ?: "Ошибка сохранения"
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IskraColors.accent,
                        contentColor = IskraColors.accentInk
                    )
                ) {
                    Text(if (busy) "Сохранение..." else "Сохранить персонажа", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, IskraColors.border)
                ) {
                    Text("Отмена", color = IskraColors.muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ProfilePage(
    api: IskraApiClient,
    user: UserProfile?,
    currentPalette: String,
    isDark: Boolean,
    wallpapersEnabled: Boolean,
    onSaveAppearance: (String, Boolean, Boolean) -> Unit,
    onUserUpdated: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit
) {
    var selectedPeriod by remember { mutableStateOf("month") }
    var legalDocToShow by remember { mutableStateOf<LegalDocType?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var devApps by remember { mutableStateOf<List<ClientApp>>(emptyList()) }
    var maxDevKeys by remember { mutableStateOf(3) }
    var loadingKeys by remember { mutableStateOf(false) }
    var keyBusy by remember { mutableStateOf(false) }
    var newAppName by remember { mutableStateOf("") }
    var newAppPlatform by remember { mutableStateOf("desktop") }
    var revealedRawKey by remember { mutableStateOf<String?>(null) }
    var keyStatusMsg by remember { mutableStateOf<String?>(null) }
    var customClientKeyInput by remember { mutableStateOf(api.activeClientKey()) }

    fun reloadKeys() {
        scope.launch {
            loadingKeys = true
            runCatching { api.getDeveloperKeys() }
                .onSuccess { res ->
                    devApps = res.apps
                    maxDevKeys = res.maxKeys
                }
            loadingKeys = false
        }
    }

    LaunchedEffect(user?.id) {
        if (user != null) {
            reloadKeys()
        }
    }

    val currentTier = (user?.tier ?: "free").lowercase()
    val periodOptions = listOf(
        Triple("7d", "7 дней", null),
        Triple("month", "Месяц", "-10%"),
        Triple("year", "Год", "-40%")
    )
    val proPrice = when (selectedPeriod) {
        "7d" -> "99 ₽"
        "year" -> "2 490 ₽"
        else -> "299 ₽"
    }
    val maxPrice = when (selectedPeriod) {
        "7d" -> "199 ₽"
        "year" -> "4 990 ₽"
        else -> "599 ₽"
    }
    val periodLabel = when (selectedPeriod) {
        "7d" -> "7 дней"
        "year" -> "год"
        else -> "мес"
    }
    val palettes = listOf(
        Triple("classic", "Классика", Color(0xFFF5F5F4)),
        Triple("terracotta", "Терракота", Color(0xFFE8A87C)),
        Triple("lavender", "Лаванда", Color(0xFFC9B6DF)),
        Triple("blue", "Океан", Color(0xFF8EC7EA)),
        Triple("emerald", "Изумруд", Color(0xFF6EE7B7))
    )

    if (legalDocToShow != null) {
        LegalDocumentDialog(
            initialTab = legalDocToShow!!,
            onDismiss = { legalDocToShow = null }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 22.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 672.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(IskraColors.surface)
                    .border(1.dp, IskraColors.border, RoundedCornerShape(18.dp))
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    RemoteImage(
                        url = user?.effectiveAvatarUrl?.ifBlank { api.session?.cachedAvatarUrl } ?: api.session?.cachedAvatarUrl,
                        api = api,
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .border(1.dp, IskraColors.border, CircleShape),
                        fallbackText = user?.displayName ?: "Я"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = user?.displayName?.ifBlank { "Пользователь" } ?: "Пользователь",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = IskraColors.text
                            )
                            UserTierBadge(user?.tier ?: "free")
                        }
                        if (!user?.email.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null, tint = IskraColors.muted, modifier = Modifier.size(13.dp))
                                Text(user?.email ?: "", fontSize = 13.sp, color = IskraColors.muted)
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, IskraColors.border),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = IskraColors.surface2,
                        contentColor = IskraColors.text
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Изменить", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(IskraColors.surface)
                    .border(1.dp, IskraColors.border, RoundedCornerShape(18.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Тариф", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = IskraColors.text)
                        UserTierBadge(user?.tier ?: "free")
                    }
                    Text(
                        text = when (currentTier) {
                            "max" -> "Максимум возможностей"
                            "pro" -> "Неограниченные диалоги"
                            else -> "Лимит сообщений, обычная память"
                        },
                        fontSize = 12.sp,
                        color = IskraColors.muted
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(IskraColors.surface2)
                        .border(1.dp, IskraColors.border, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    periodOptions.forEach { (key, label, discount) ->
                        val selected = selectedPeriod == key
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) IskraColors.accent else Color.Transparent)
                                .clickable { selectedPeriod = key }
                                .padding(vertical = 7.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) IskraColors.accentInk else IskraColors.muted
                            )
                            if (discount != null) {
                                Spacer(Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (selected) IskraColors.accentInk.copy(alpha = 0.2f) else Color(0x3310B981))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = discount,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) IskraColors.accentInk else Color(0xFF34D399)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(IskraColors.accent)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚡", fontSize = 14.sp, color = IskraColors.accentInk)
                        Text(
                            text = if (currentTier == "pro") "ТАРИФ PRO (АКТИВЕН)" else "ОФОРМИТЬ PRO",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = IskraColors.accentInk
                        )
                    }
                    Text(
                        text = "$proPrice / $periodLabel",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = IskraColors.accentInk
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(IskraColors.surface2)
                        .border(1.dp, IskraColors.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("✦", fontSize = 14.sp, color = IskraColors.accent)
                        Text(
                            text = if (currentTier == "max") "ТАРИФ MAX (АКТИВЕН)" else "ОФОРМИТЬ MAX",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = IskraColors.text
                        )
                    }
                    Text(
                        text = "$maxPrice / $periodLabel",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = IskraColors.text
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(IskraColors.surface)
                    .border(1.dp, IskraColors.border, RoundedCornerShape(18.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Оформление", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = IskraColors.text)
                    Text("Темы и акцентная палитра", fontSize = 12.sp, color = IskraColors.muted)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(IskraColors.surface2)
                        .border(1.dp, IskraColors.border, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(true to "Тёмная", false to "Светлая").forEach { (darkOption, label) ->
                        val active = isDark == darkOption
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (active) IskraColors.accent else Color.Transparent)
                                .clickable { onSaveAppearance(currentPalette, darkOption, wallpapersEnabled) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (darkOption) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = if (active) IskraColors.accentInk else IskraColors.muted,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) IskraColors.accentInk else IskraColors.muted
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    palettes.forEach { (id, title, swatch) ->
                        val active = currentPalette == id
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(IskraColors.surface2)
                                .border(
                                    1.dp,
                                    if (active) IskraColors.accent else IskraColors.border,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSaveAppearance(id, isDark, wallpapersEnabled) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(1.dp, IskraColors.border, CircleShape)
                            )
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IskraColors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                HorizontalDivider(color = IskraColors.border)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Авторские обои в чате", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = IskraColors.text)
                        Text("Показывать фон от создателя персонажа", fontSize = 11.sp, color = IskraColors.muted)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (wallpapersEnabled) IskraColors.accent else IskraColors.surface2)
                            .border(1.dp, IskraColors.border, RoundedCornerShape(8.dp))
                            .clickable { onSaveAppearance(currentPalette, isDark, !wallpapersEnabled) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (wallpapersEnabled) "Включены" else "Выключены",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (wallpapersEnabled) IskraColors.accentInk else IskraColors.muted
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(IskraColors.surface)
                    .border(1.dp, IskraColors.border, RoundedCornerShape(18.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("API ключи и клиентский доступ", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = IskraColors.text)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color(0x2634D399))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Доступно всем (${devApps.count { it.status == "active" }}/$maxDevKeys)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34D399)
                                )
                            }
                        }
                        Text(
                            text = "Создавайте и управляйте ключами X-Iskra-Client-Key (iskra_live_...) для любых клиентов",
                            fontSize = 12.sp,
                            color = IskraColors.muted
                        )
                    }
                    Text(
                        text = if (loadingKeys) "..." else "↻ Обновить",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IskraColors.muted,
                        modifier = Modifier.clickable { reloadKeys() }
                    )
                }

                if (revealedRawKey != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1A34D399))
                            .border(1.dp, Color(0x5534D399), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Ваш новый API ключ (сохраните его):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399)
                        )
                        Text(
                            text = revealedRawKey!!,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IskraColors.text
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    clipboard.setText(AnnotatedString(revealedRawKey!!))
                                    keyStatusMsg = "Ключ скопирован в буфер обмена"
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IskraColors.accent,
                                    contentColor = IskraColors.accentInk
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Скопировать", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    api.updateClientKey(revealedRawKey!!)
                                    customClientKeyInput = revealedRawKey!!
                                    keyStatusMsg = "Ключ применён для текущего приложения"
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, IskraColors.border),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Применить в этом клиенте", fontSize = 11.sp, color = IskraColors.text)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        IskraField(
                            value = newAppName,
                            onValueChange = { newAppName = it },
                            label = "Название приложения",
                            placeholder = "Например: Мой Desktop клиент"
                        )
                    }
                    Button(
                        onClick = {
                            if (newAppName.trim().length < 2) {
                                keyStatusMsg = "Введите название от 2 символов"
                                return@Button
                            }
                            scope.launch {
                                keyBusy = true
                                keyStatusMsg = null
                                runCatching {
                                    api.createDeveloperKey(newAppName.trim(), newAppPlatform)
                                }.onSuccess { created ->
                                    revealedRawKey = created.rawKey
                                    newAppName = ""
                                    reloadKeys()
                                }.onFailure { e ->
                                    keyStatusMsg = e.message ?: "Не удалось выпустить ключ"
                                }
                                keyBusy = false
                            }
                        },
                        enabled = !keyBusy,
                        modifier = Modifier.height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IskraColors.accent,
                            contentColor = IskraColors.accentInk
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    ) {
                        Text(if (keyBusy) "..." else "+ Создать ключ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "desktop" to "Desktop",
                        "android" to "Android",
                        "ios" to "iOS",
                        "web" to "Web"
                    ).forEach { (pid, plabel) ->
                        val active = newAppPlatform == pid
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) IskraColors.text else IskraColors.surface2)
                                .border(1.dp, IskraColors.border, RoundedCornerShape(8.dp))
                                .clickable { newAppPlatform = pid }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = plabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) IskraColors.canvas else IskraColors.muted
                            )
                        }
                    }
                }

                if (devApps.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        devApps.forEach { appItem ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(IskraColors.surface2)
                                    .border(1.dp, IskraColors.border, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = appItem.appName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = IskraColors.text
                                        )
                                        Text(
                                            text = appItem.platform.uppercase(),
                                            fontSize = 10.sp,
                                            color = IskraColors.muted
                                        )
                                    }
                                    Text(
                                        text = "${appItem.keyPrefix}... • ${if (appItem.status == "active") "Активен" else "Отозван"}",
                                        fontSize = 11.sp,
                                        color = if (appItem.status == "active") Color(0xFF34D399) else IskraColors.subtle
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (appItem.status == "active") {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(IskraColors.surface)
                                                .border(1.dp, IskraColors.border, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    scope.launch {
                                                        keyBusy = true
                                                        runCatching { api.rotateDeveloperKey(appItem.id) }
                                                            .onSuccess { rotated ->
                                                                revealedRawKey = rotated.rawKey
                                                                keyStatusMsg = "Ключ перевыпущен"
                                                                reloadKeys()
                                                            }
                                                            .onFailure { e -> keyStatusMsg = e.message }
                                                        keyBusy = false
                                                    }
                                                }
                                                .padding(horizontal = 9.dp, vertical = 5.dp)
                                        ) {
                                            Text("Перевыпустить", fontSize = 11.sp, color = IskraColors.text)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x1FF87171))
                                            .border(1.dp, Color(0x4DF87171), RoundedCornerShape(8.dp))
                                            .clickable {
                                                scope.launch {
                                                    keyBusy = true
                                                    runCatching {
                                                        api.revokeDeveloperKey(appItem.id, permanent = true)
                                                    }.onSuccess {
                                                        keyStatusMsg = "Ключ удалён"
                                                        reloadKeys()
                                                    }.onFailure { e -> keyStatusMsg = e.message }
                                                    keyBusy = false
                                                }
                                            }
                                            .padding(horizontal = 9.dp, vertical = 5.dp)
                                    ) {
                                        Text("Удалить", fontSize = 11.sp, color = IskraColors.danger)
                                    }
                                }
                            }
                        }
                    }
                }

                if (keyStatusMsg != null) {
                    Text(keyStatusMsg!!, fontSize = 12.sp, color = IskraColors.muted)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(IskraColors.surface)
                    .border(1.dp, IskraColors.border, RoundedCornerShape(18.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("ПРАВОВАЯ ИНФОРМАЦИЯ И ДОКУМЕНТЫ (18+)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IskraColors.muted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(IskraColors.surface2)
                            .border(1.dp, IskraColors.border, RoundedCornerShape(12.dp))
                            .clickable { legalDocToShow = LegalDocType.PRIVACY },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Политика конфиденциальности", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = IskraColors.text)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(IskraColors.surface2)
                            .border(1.dp, IskraColors.border, RoundedCornerShape(12.dp))
                            .clickable { legalDocToShow = LegalDocType.TERMS },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Пользовательское соглашение", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = IskraColors.text)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(IskraColors.surface)
                    .border(1.dp, IskraColors.border, RoundedCornerShape(18.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("СОЦИАЛЬНЫЕ СЕТИ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IskraColors.muted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF229ED9))
                            .clickable {
                                runCatching { Desktop.getDesktop().browse(URI("https://t.me/IskraAi_ru")) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Канал Telegram", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0A0A0A))
                            .border(1.dp, IskraColors.border, RoundedCornerShape(12.dp))
                            .clickable {
                                runCatching { Desktop.getDesktop().browse(URI("https://www.tiktok.com/@iskraai_chat")) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("TikTok", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(IskraColors.surface)
                    .border(1.dp, IskraColors.border, RoundedCornerShape(14.dp))
                    .clickable { onLogout() },
                contentAlignment = Alignment.Center
            ) {
                Text("Выйти из аккаунта", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IskraColors.text)
            }
        }
    }
}

@Composable
private fun SettingsModal(
    api: IskraApiClient,
    user: UserProfile?,
    currentPalette: String,
    isDark: Boolean,
    wallpapersEnabled: Boolean,
    onSaveAppearance: (String, Boolean, Boolean) -> Unit,
    onUserUpdated: () -> Unit,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("appearance") }
    var name by remember(user) { mutableStateOf(user?.displayName ?: "") }
    var avatar by remember(user) { mutableStateOf(user?.effectiveAvatarUrl?.ifBlank { api.session?.cachedAvatarUrl } ?: api.session?.cachedAvatarUrl ?: "") }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val palettes = listOf(
        Triple("classic", "Классика", Color(0xFFF5F5F4)),
        Triple("lavender", "Лаванда", Color(0xFFA78BFA)),
        Triple("terracotta", "Терракота", Color(0xFFEA7A57)),
        Triple("blue", "Океан", Color(0xFF60A5FA)),
        Triple("emerald", "Изумруд", Color(0xFF34D399))
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(IskraColors.surface)
                .border(1.dp, IskraColors.borderStrong, RoundedCornerShape(22.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Настройки", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = IskraColors.text)
                Text(
                    text = "✕",
                    fontSize = 16.sp,
                    color = IskraColors.muted,
                    modifier = Modifier.clickable { onDismiss() }.padding(4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(IskraColors.surface2)
                    .padding(4.dp)
            ) {
                listOf("appearance" to "Оформление", "profile" to "Профиль").forEach { (key, label) ->
                    val active = tab == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) IskraColors.text else Color.Transparent)
                            .clickable { tab = key }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (active) IskraColors.canvas else IskraColors.muted
                        )
                    }
                }
            }

            if (tab == "appearance") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Режим темы", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = IskraColors.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChipButton(
                            label = "Тёмная",
                            active = isDark,
                            onClick = { onSaveAppearance(currentPalette, true, wallpapersEnabled) }
                        )
                        FilterChipButton(
                            label = "Светлая",
                            active = !isDark,
                            onClick = { onSaveAppearance(currentPalette, false, wallpapersEnabled) }
                        )
                    }

                    Text("Цветовая палитра", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = IskraColors.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        palettes.forEach { (id, title, swatch) ->
                            val active = currentPalette == id
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(IskraColors.surface2)
                                    .border(
                                        1.dp,
                                        if (active) IskraColors.text else IskraColors.border,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onSaveAppearance(id, isDark, wallpapersEnabled) }
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(swatch)
                                        .border(1.dp, Color(0x44000000), CircleShape)
                                )
                                Text(title, fontSize = 11.sp, color = if (active) IskraColors.text else IskraColors.muted)
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompactCheckbox(
                            checked = wallpapersEnabled,
                            onCheckedChange = { onSaveAppearance(currentPalette, isDark, it) }
                        )
                        Text("Атмосферный фон в чате", fontSize = 13.sp, color = IskraColors.text)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    IskraField(value = name, onValueChange = { name = it }, label = "Отображаемое имя", placeholder = "Ваше имя")
                    IskraField(value = avatar, onValueChange = { avatar = it }, label = "URL аватара", placeholder = "https://...")

                    if (statusMsg != null) {
                        Text(statusMsg!!, fontSize = 12.sp, color = IskraColors.muted)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        api.updateProfile(
                                            displayName = name.trim().ifBlank { null },
                                            avatarUrl = avatar.trim().ifBlank { null }
                                        )
                                        onUserUpdated()
                                        statusMsg = "Профиль обновлён"
                                    }.onFailure { e ->
                                        statusMsg = e.message ?: "Ошибка сохранения"
                                    }
                                }
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IskraColors.accent,
                                contentColor = IskraColors.accentInk
                            )
                        ) {
                            Text("Сохранить", fontSize = 13.sp)
                        }

                        TextButton(onClick = onLogout) {
                            Text("Выйти из аккаунта", color = IskraColors.danger, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatPage(
    bot: ApiBot,
    api: IskraApiClient,
    wallpapersEnabled: Boolean,
    onToggleLike: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var conversationId by remember(bot.id) { mutableStateOf<String?>(null) }
    var messages by remember(bot.id) { mutableStateOf<List<Message>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var showIntro by remember { mutableStateOf(true) }
    var showBotModal by remember { mutableStateOf(false) }

    LaunchedEffect(bot.id) {
        runCatching {
            val convs = api.getConversations()
            val existing = convs.firstOrNull { it.botId == bot.id }
            val conv = existing ?: api.createConversation(bot.id)
            conversationId = conv.id
            messages = api.getMessages(conv.id).items
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendCurrentMessage() {
        val text = input.trim()
        val cid = conversationId ?: return
        if (text.isEmpty() || isSending) return
        input = ""
        val optimistic = Message(
            id = "temp-${System.currentTimeMillis()}",
            conversationId = cid,
            sender = "user",
            content = text
        )
        messages = messages + optimistic
        scope.launch {
            isSending = true
            runCatching {
                api.sendMessage(cid, text)
            }.onSuccess { resp ->
                messages = messages.filterNot { it.id == optimistic.id } + listOf(resp.userMessage, resp.assistantMessage)
            }.onFailure { e ->
                messages = messages + Message(
                    id = "err-${System.currentTimeMillis()}",
                    conversationId = cid,
                    sender = "assistant",
                    content = "*${e.message ?: "Ошибка отправки сообщения"}*"
                )
            }
            isSending = false
        }
    }

    val bgBrush = if (wallpapersEnabled) {
        Brush.radialGradient(listOf(IskraColors.surface2, IskraColors.canvas))
    } else {
        Brush.verticalGradient(listOf(IskraColors.canvas, IskraColors.canvas))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(IskraColors.surface)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .border(1.dp, IskraColors.border, RoundedCornerShape(9.dp))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = IskraColors.text, modifier = Modifier.size(15.dp))
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showBotModal = true }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RemoteImage(
                        url = bot.avatarUrl,
                        api = api,
                        modifier = Modifier.size(34.dp).clip(CircleShape),
                        fallbackText = bot.name
                    )
                    Column {
                        Text(bot.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = IskraColors.text)
                        Text("Нажмите, чтобы открыть профиль", fontSize = 10.sp, color = IskraColors.muted)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(IskraColors.surface2)
                        .border(1.dp, IskraColors.border, RoundedCornerShape(8.dp))
                        .clickable {
                            scope.launch {
                                runCatching {
                                    val conv = api.createConversation(bot.id)
                                    conversationId = conv.id
                                    messages = api.getMessages(conv.id).items
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Новый диалог", fontSize = 12.sp, color = IskraColors.text)
                }
            }
        }
        HorizontalDivider(color = IskraColors.border)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!bot.description.isNullOrBlank()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(IskraColors.surface)
                            .border(1.dp, IskraColors.border, RoundedCornerShape(14.dp))
                            .clickable { showIntro = !showIntro }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("О ПЕРСОНАЖЕ", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = IskraColors.subtle)
                            Text(if (showIntro) "Скрыть" else "Показать", fontSize = 11.sp, color = IskraColors.muted)
                        }
                        if (showIntro) {
                            Text(bot.description, fontSize = 13.sp, color = IskraColors.muted)
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                val isUser = msg.sender == "user"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.Top
                ) {
                    if (!isUser) {
                        RemoteImage(
                            url = bot.avatarUrl,
                            api = api,
                            modifier = Modifier.size(30.dp).clip(CircleShape),
                            fallbackText = bot.name
                        )
                        Spacer(Modifier.width(10.dp))
                    }

                    Column(
                        modifier = Modifier.widthIn(max = 640.dp),
                        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isUser) IskraColors.surface2 else IskraColors.surface)
                                .border(
                                    1.dp,
                                    if (isUser) IskraColors.borderStrong else IskraColors.border,
                                    RoundedCornerShape(18.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = formatMessageRichText(msg.content, IskraColors.text, IskraColors.muted),
                                fontSize = 14.sp,
                                lineHeight = 21.sp
                            )
                        }

                        if (!isUser) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Копировать",
                                    fontSize = 11.sp,
                                    color = IskraColors.subtle,
                                    modifier = Modifier.clickable {
                                        clipboard.setText(AnnotatedString(msg.content))
                                    }
                                )
                                val cid = conversationId
                                if (cid != null && !isSending) {
                                    Text(
                                        text = "Перегенерировать",
                                        fontSize = 11.sp,
                                        color = IskraColors.subtle,
                                        modifier = Modifier.clickable {
                                            scope.launch {
                                                isSending = true
                                                runCatching { api.regenerateMessage(cid, msg.id) }
                                                    .onSuccess { regen ->
                                                        messages = messages.map {
                                                            if (it.id == msg.id) regen.assistantMessage else it
                                                        }
                                                    }
                                                isSending = false
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isSending) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RemoteImage(
                            url = bot.avatarUrl,
                            api = api,
                            modifier = Modifier.size(30.dp).clip(CircleShape),
                            fallbackText = bot.name
                        )
                        Text("${bot.name} печатает...", fontSize = 13.sp, color = IskraColors.muted, fontStyle = FontStyle.Italic)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 840.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(IskraColors.surface)
                    .border(1.dp, IskraColors.borderStrong, RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(IskraColors.surface2)
                        .clickable { input = "$input *действие* " }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text("*...*", fontSize = 11.sp, color = IskraColors.muted, fontStyle = FontStyle.Italic)
                }

                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(color = IskraColors.text, fontSize = 13.sp),
                    cursorBrush = SolidColor(IskraColors.accent),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (input.isEmpty()) {
                                Text("Написать ${bot.name}...", color = IskraColors.subtle, fontSize = 13.sp)
                            }
                            innerTextField()
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (input.isNotBlank() && !isSending) IskraColors.accent else IskraColors.surface3)
                        .clickable(enabled = input.isNotBlank() && !isSending) { sendCurrentMessage() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Отправить",
                        tint = if (input.isNotBlank() && !isSending) IskraColors.accentInk else IskraColors.subtle,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showBotModal) {
        Dialog(onDismissRequest = { showBotModal = false }) {
            Column(
                modifier = Modifier
                    .width(480.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(IskraColors.surface)
                    .border(1.dp, IskraColors.borderStrong, RoundedCornerShape(22.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    RemoteImage(
                        url = bot.avatarUrl,
                        api = api,
                        modifier = Modifier.size(64.dp).clip(CircleShape),
                        fallbackText = bot.name
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(bot.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = IskraColors.text)
                        Text(genreLabel(bot.genre), fontSize = 12.sp, color = IskraColors.muted)
                    }
                    OutlinedButton(
                        onClick = onToggleLike,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, IskraColors.border)
                    ) {
                        Text(if (bot.isLiked) "♥ ${bot.likesCount}" else "♡ ${bot.likesCount}", color = IskraColors.text)
                    }
                }
                if (!bot.description.isNullOrBlank()) {
                    Text(bot.description, fontSize = 13.sp, color = IskraColors.text)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showBotModal = false }) {
                        Text("Закрыть", color = IskraColors.text)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryReaderPage(
    story: StorySummary,
    api: IskraApiClient,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var session by remember(story.id) { mutableStateOf<StorySession?>(null) }
    var messages by remember(story.id) { mutableStateOf<List<StoryMessage>>(emptyList()) }
    var choices by remember(story.id) { mutableStateOf<List<String>>(emptyList()) }
    var customAction by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(story.id) {
        busy = true
        runCatching {
            val created = api.createStorySession(story.id)
            session = created
            val msgs = api.getStoryMessages(created.id)
            messages = msgs
            choices = msgs.lastOrNull()?.metadata?.choices ?: emptyList()
        }
        busy = false
    }

    fun sendChoice(action: String) {
        val sid = session?.id ?: return
        if (action.isBlank() || busy) return
        customAction = ""
        scope.launch {
            busy = true
            runCatching {
                api.sendStoryMessage(sid, action)
            }.onSuccess { resp ->
                messages = messages + listOf(resp.userMessage, resp.assistantMessage)
                choices = resp.state?.choices ?: resp.assistantMessage.metadata?.choices ?: emptyList()
            }
            busy = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, IskraColors.border)
            ) {
                Text("← Назад", color = IskraColors.text, fontSize = 12.sp)
            }
            Text(story.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = IskraColors.text)
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(IskraColors.surface)
                .border(1.dp, IskraColors.border, RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (story.description.isNotBlank()) {
                item {
                    Text(story.description, fontSize = 13.sp, lineHeight = 20.sp, color = IskraColors.muted)
                }
            }
            items(messages, key = { it.id }) { turn ->
                Text(
                    text = formatMessageRichText(turn.content, IskraColors.text, IskraColors.muted),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }

        if (choices.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { choice ->
                    OutlinedButton(
                        onClick = { sendChoice(choice) },
                        enabled = !busy,
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, IskraColors.borderStrong)
                    ) {
                        Text(choice, fontSize = 12.sp, color = IskraColors.text)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                IskraField(
                    value = customAction,
                    onValueChange = { customAction = it },
                    label = "",
                    placeholder = "Ваш ход или решение..."
                )
            }
            Button(
                onClick = { sendChoice(customAction) },
                enabled = customAction.isNotBlank() && !busy,
                modifier = Modifier.height(38.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = IskraColors.accent,
                    contentColor = IskraColors.accentInk
                )
            ) {
                Text(if (busy) "..." else "Продолжить", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TavernPage(
    api: IskraApiClient,
    bots: List<ApiBot>,
    onOpenBotChat: (ApiBot) -> Unit
) {
    val scope = rememberCoroutineScope()
    var activeRooms by remember { mutableStateOf<List<ActiveTavernRoom>>(emptyList()) }
    var currentRoom by remember { mutableStateOf<TavernRoomData?>(null) }
    var queueStatus by remember { mutableStateOf<String?>(null) }
    var msgInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        activeRooms = runCatching { api.getActiveTavernRooms() }.getOrDefault(emptyList())
    }

    fun startQueue(mode: String) {
        scope.launch {
            queueStatus = "Поиск собеседников ($mode)..."
            runCatching {
                val res = api.joinTavernQueue(mode)
                if (res.roomId != null) {
                    currentRoom = api.getTavernRoom(res.roomId)
                    queueStatus = null
                } else if (res.queueId != null) {
                    repeat(8) {
                        delay(2000)
                        val poll = api.checkTavernQueue(res.queueId)
                        if (poll.roomId != null) {
                            currentRoom = api.getTavernRoom(poll.roomId)
                            queueStatus = null
                            return@runCatching
                        }
                    }
                    queueStatus = "Пока никого нет в очереди. Попробуйте случайного персонажа."
                }
            }.onFailure {
                queueStatus = null
                val randomBot = bots.randomOrNull()
                if (randomBot != null) onOpenBotChat(randomBot)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("РОЛЕВАЯ КОМНАТА", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = IskraColors.subtle)
            Text("Таверна", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = IskraColors.text)
            Text("Не знаешь с кем пообщаться? Выбери случайного собеседника, дуэт или трио.", fontSize = 12.sp, color = IskraColors.muted)
        }

        if (queueStatus != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(IskraColors.surface)
                    .border(1.dp, IskraColors.border, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(queueStatus!!, fontSize = 13.sp, color = IskraColors.text)
                TextButton(onClick = {
                    scope.launch { api.leaveTavernQueue() }
                    queueStatus = null
                }) {
                    Text("Отмена", color = IskraColors.muted)
                }
            }
        }

        val room = currentRoom
        if (room != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(IskraColors.surface)
                    .border(1.dp, IskraColors.border, RoundedCornerShape(18.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Комната #${room.room.id.take(6)} (${room.room.mode})", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { currentRoom = null }) { Text("Выйти", color = IskraColors.muted) }
                }
                room.messages.forEach { m ->
                    Text("${m.senderName}: ${m.content}", fontSize = 13.sp, color = IskraColors.text)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        IskraField(
                            value = msgInput,
                            onValueChange = { msgInput = it },
                            label = "",
                            placeholder = "Сообщение в комнату..."
                        )
                    }
                    Button(
                        modifier = Modifier.height(38.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(10.dp),
                        onClick = {
                            val text = msgInput.trim()
                            if (text.isNotBlank()) {
                                msgInput = ""
                                scope.launch {
                                    runCatching {
                                        api.sendTavernMessage(room.room.id, text)
                                        currentRoom = api.getTavernRoom(room.room.id)
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Отправить", fontSize = 12.sp)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TavernModeCard(
                    modifier = Modifier.weight(1f),
                    title = "Случайный бот",
                    subtitle = "Мгновенный переход к диалогу со случайным героем из каталога.",
                    actionLabel = "Начать диалог →",
                    onClick = {
                        val randomBot = bots.randomOrNull()
                        if (randomBot != null) onOpenBotChat(randomBot)
                    }
                )
                TavernModeCard(
                    modifier = Modifier.weight(1f),
                    title = "Дуэт",
                    subtitle = "Совместная ролевая комната для двоих участников.",
                    actionLabel = "Найти пару →",
                    onClick = { startQueue("duo") }
                )
                TavernModeCard(
                    modifier = Modifier.weight(1f),
                    title = "Трио с ботом",
                    subtitle = "Два игрока и ведущий ИИ-персонаж в одной сцене.",
                    actionLabel = "Найти комнату →",
                    onClick = { startQueue("trio") }
                )
            }
        }
    }
}

@Composable
private fun TavernModeCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(IskraColors.surface)
            .border(1.dp, IskraColors.border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = IskraColors.text)
        Text(subtitle, fontSize = 13.sp, color = IskraColors.muted)
        Spacer(Modifier.height(4.dp))
        Text(actionLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = IskraColors.text)
    }
}

@Composable
private fun AuthPage(
    api: IskraApiClient,
    onAuthenticated: () -> Unit,
    onContinueGuest: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("login") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var acceptTerms by remember { mutableStateOf(true) }
    var newsletter by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var infoMsg by remember { mutableStateOf<String?>(null) }
    var legalDocToShow by remember { mutableStateOf<LegalDocType?>(null) }

    if (legalDocToShow != null) {
        LegalDocumentDialog(
            initialTab = legalDocToShow!!,
            onDismiss = { legalDocToShow = null }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IskraColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(IskraColors.surface)
                .border(1.dp, IskraColors.border, RoundedCornerShape(18.dp))
                .animateContentSize(animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                BrandMark(size = 28.dp)
                Text(
                    text = "IskraAi",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = IskraColors.text
                )
            }

            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 3 })
                        .togetherWith(fadeOut(tween(140)))
                },
                label = "AuthHeading"
            ) { currentMode ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = when (currentMode) {
                            "register" -> "Создай аккаунт"
                            "verify" -> "Подтверждение почты"
                            "forgot" -> "Сброс пароля"
                            "reset" -> "Новый пароль"
                            else -> "С возвращением"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = IskraColors.text
                    )
                    Text(
                        text = when (currentMode) {
                            "register" -> "Истории и персонажи ждут тебя"
                            "verify" -> "Мы отправили 6-значный код на $email"
                            "forgot" -> "Отправим код для восстановления доступа"
                            "reset" -> "Введите код из письма и новый пароль"
                            else -> "Продолжи свои истории"
                        },
                        fontSize = 11.sp,
                        color = IskraColors.muted,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (mode == "login" || mode == "register") {
                AnimatedAuthTabs(
                    mode = mode,
                    onSelectMode = { nextMode ->
                        if (mode != nextMode) {
                            mode = nextMode
                            errorMsg = null
                            infoMsg = null
                        }
                    }
                )
            }

            if (errorMsg != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x1FF87171))
                        .border(1.dp, Color(0x4DF87171), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(errorMsg!!, color = IskraColors.danger, fontSize = 11.sp)
                }
            }

            if (infoMsg != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(IskraColors.surface2)
                        .border(1.dp, IskraColors.border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(infoMsg!!, color = IskraColors.text, fontSize = 11.sp)
                }
            }

            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    val forward = (initialState == "register" && targetState == "login") ||
                        targetState == "verify" || targetState == "forgot" || targetState == "reset"
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { fullWidth -> dir * (fullWidth / 5) } +
                        fadeIn(tween(220)))
                        .togetherWith(
                            slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { fullWidth -> -dir * (fullWidth / 5) } +
                                fadeOut(tween(150))
                        )
                },
                label = "AuthFormTransition"
            ) { currentMode ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    when (currentMode) {
                        "register" -> {
                            IskraField(value = name, onValueChange = { name = it }, label = "Имя или псевдоним", placeholder = "Как к тебе обращаться")
                            IskraField(value = email, onValueChange = { email = it }, label = "Электронная почта", placeholder = "you@example.com")
                            IskraPasswordField(
                                value = password,
                                onValueChange = { password = it },
                                label = "Пароль",
                                placeholder = "Минимум 8 символов",
                                visible = showPassword,
                                onToggleVisible = { showPassword = !showPassword }
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { acceptTerms = !acceptTerms }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CompactCheckbox(
                                        checked = acceptTerms,
                                        onCheckedChange = { acceptTerms = it }
                                    )
                                    Text(
                                        text = "Мне исполнилось 18 лет, я принимаю условия и политику",
                                        fontSize = 11.sp,
                                        color = IskraColors.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    modifier = Modifier.padding(start = 25.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Условия использования",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = IskraColors.text,
                                        modifier = Modifier.clickable { legalDocToShow = LegalDocType.TERMS }
                                    )
                                    Text("•", fontSize = 10.sp, color = IskraColors.subtle)
                                    Text(
                                        text = "Политика конфиденциальности",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = IskraColors.text,
                                        modifier = Modifier.clickable { legalDocToShow = LegalDocType.PRIVACY }
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (email.isBlank() || password.length < 6) {
                                        errorMsg = "Введите корректный email и пароль"
                                        return@Button
                                    }
                                    if (!acceptTerms) {
                                        errorMsg = "Необходимо подтвердить возраст (18+) и принять условия"
                                        return@Button
                                    }
                                    scope.launch {
                                        busy = true
                                        errorMsg = null
                                        runCatching {
                                            api.requestOtp(
                                                email = email.trim(),
                                                displayName = name.trim().ifBlank { email.substringBefore("@") },
                                                password = password,
                                                newsletter = newsletter
                                            )
                                        }.onSuccess {
                                            mode = "verify"
                                        }.onFailure { e ->
                                            errorMsg = e.message ?: "Ошибка отправки кода"
                                        }
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IskraColors.accent,
                                    contentColor = IskraColors.accentInk
                                )
                            ) {
                                Text(if (busy) "Отправка..." else "Получить код подтверждения", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        "login" -> {
                            IskraField(value = email, onValueChange = { email = it }, label = "Электронная почта", placeholder = "you@example.com")
                            IskraPasswordField(
                                value = password,
                                onValueChange = { password = it },
                                label = "Пароль",
                                placeholder = "Ваш пароль",
                                visible = showPassword,
                                onToggleVisible = { showPassword = !showPassword }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "Забыли пароль?",
                                    fontSize = 11.sp,
                                    color = IskraColors.muted,
                                    modifier = Modifier.clickable {
                                        mode = "forgot"
                                        errorMsg = null
                                    }
                                )
                            }

                            Button(
                                onClick = {
                                    if (email.isBlank() || password.isBlank()) {
                                        errorMsg = "Введите почту и пароль"
                                        return@Button
                                    }
                                    scope.launch {
                                        busy = true
                                        errorMsg = null
                                        runCatching {
                                            api.login(email = email.trim(), password = password)
                                        }.onSuccess {
                                            onAuthenticated()
                                        }.onFailure { e ->
                                            errorMsg = e.message ?: "Ошибка входа"
                                        }
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IskraColors.accent,
                                    contentColor = IskraColors.accentInk
                                )
                            ) {
                                Text(if (busy) "Вход..." else "Войти", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        "verify" -> {
                            IskraField(
                                value = otpCode,
                                onValueChange = { otpCode = it.filter { ch -> ch.isDigit() }.take(6) },
                                label = "6-значный код из письма",
                                placeholder = "000000"
                            )

                            Button(
                                onClick = {
                                    if (otpCode.length < 6) {
                                        errorMsg = "Введите 6-значный код"
                                        return@Button
                                    }
                                    scope.launch {
                                        busy = true
                                        errorMsg = null
                                        runCatching {
                                            api.verifyOtp(email = email.trim(), code = otpCode.trim())
                                        }.onSuccess {
                                            onAuthenticated()
                                        }.onFailure { e ->
                                            errorMsg = e.message ?: "Неверный код подтверждения"
                                        }
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IskraColors.accent,
                                    contentColor = IskraColors.accentInk
                                )
                            ) {
                                Text(if (busy) "Проверка..." else "Подтвердить и войти", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Text(
                                text = "Назад",
                                fontSize = 11.sp,
                                color = IskraColors.muted,
                                modifier = Modifier.clickable { mode = "register" }
                            )
                        }

                        "forgot" -> {
                            IskraField(value = email, onValueChange = { email = it }, label = "Электронная почта", placeholder = "you@example.com")
                            Button(
                                onClick = {
                                    if (email.isBlank()) {
                                        errorMsg = "Укажите адрес почты"
                                        return@Button
                                    }
                                    scope.launch {
                                        busy = true
                                        errorMsg = null
                                        runCatching { api.requestPasswordReset(email.trim()) }
                                            .onSuccess {
                                                mode = "reset"
                                                infoMsg = "Код восстановления отправлен на почту"
                                            }
                                            .onFailure { e -> errorMsg = e.message ?: "Ошибка отправки кода" }
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IskraColors.accent,
                                    contentColor = IskraColors.accentInk
                                )
                            ) {
                                Text(if (busy) "Отправка..." else "Отправить код", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                text = "Вернуться ко входу",
                                fontSize = 11.sp,
                                color = IskraColors.muted,
                                modifier = Modifier.clickable { mode = "login" }
                            )
                        }

                        "reset" -> {
                            IskraField(value = otpCode, onValueChange = { otpCode = it.take(6) }, label = "Код из письма", placeholder = "000000")
                            IskraPasswordField(
                                value = newPassword,
                                onValueChange = { newPassword = it },
                                label = "Новый пароль",
                                placeholder = "Минимум 8 символов",
                                visible = showPassword,
                                onToggleVisible = { showPassword = !showPassword }
                            )
                            Button(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        errorMsg = null
                                        runCatching {
                                            api.resetPassword(email.trim(), otpCode.trim(), newPassword)
                                        }.onSuccess {
                                            onAuthenticated()
                                        }.onFailure { e ->
                                            errorMsg = e.message ?: "Не удалось сбросить пароль"
                                        }
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().height(38.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IskraColors.accent,
                                    contentColor = IskraColors.accentInk
                                )
                            ) {
                                Text(if (busy) "Сохранение..." else "Сохранить пароль", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Text(
                text = "Открыть каталог без входа →",
                fontSize = 11.sp,
                color = IskraColors.subtle,
                modifier = Modifier.clickable { onContinueGuest() }
            )

            if (mode != "register") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Политика конфиденциальности",
                        fontSize = 10.sp,
                        color = IskraColors.subtle,
                        modifier = Modifier.clickable { legalDocToShow = LegalDocType.PRIVACY }
                    )
                    Text("•", fontSize = 10.sp, color = IskraColors.subtle)
                    Text(
                        text = "Условия (18+)",
                        fontSize = 10.sp,
                        color = IskraColors.subtle,
                        modifier = Modifier.clickable { legalDocToShow = LegalDocType.TERMS }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .size(17.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (checked) IskraColors.accent else IskraColors.surface2)
            .border(
                1.dp,
                if (checked) IskraColors.accent else IskraColors.borderStrong,
                RoundedCornerShape(5.dp)
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = IskraColors.accentInk,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun AnimatedAuthTabs(
    mode: String,
    onSelectMode: (String) -> Unit
) {
    val isLogin = mode == "login"
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(IskraColors.surface2)
            .border(1.dp, IskraColors.border, RoundedCornerShape(10.dp))
            .padding(3.dp)
    ) {
        val pillWidth = (maxWidth - 4.dp) / 2
        val pillOffset by animateDpAsState(
            targetValue = if (isLogin) pillWidth + 4.dp else 0.dp,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "AuthTabPillOffset"
        )

        Box(
            modifier = Modifier
                .offset(x = pillOffset)
                .width(pillWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .background(IskraColors.text)
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("register" to "Регистрация", "login" to "Вход").forEach { (tabMode, label) ->
                val active = mode == tabMode
                val textColor by animateColorAsState(
                    targetValue = if (active) IskraColors.canvas else IskraColors.muted,
                    animationSpec = tween(durationMillis = 200),
                    label = "AuthTabTextColor"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectMode(tabMode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
private fun IskraField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    minLines: Int = 1
) {
    var focused by remember { mutableStateOf(false) }
    val singleLine = minLines <= 1
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (label.isNotBlank()) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = IskraColors.muted)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = TextStyle(
                color = IskraColors.text,
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            cursorBrush = SolidColor(IskraColors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (singleLine) Modifier.height(38.dp)
                            else Modifier.heightIn(min = (minLines * 20 + 16).dp)
                        )
                        .clip(RoundedCornerShape(10.dp))
                        .background(IskraColors.surface2)
                        .border(
                            1.dp,
                            if (focused) IskraColors.borderStrong else IskraColors.border,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = if (singleLine) 0.dp else 9.dp),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = IskraColors.subtle,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun IskraPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    visible: Boolean,
    onToggleVisible: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = IskraColors.muted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            textStyle = TextStyle(
                color = IskraColors.text,
                fontSize = 13.sp,
                lineHeight = 18.sp
            ),
            cursorBrush = SolidColor(IskraColors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(IskraColors.surface2)
                        .border(
                            1.dp,
                            if (focused) IskraColors.borderStrong else IskraColors.border,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = IskraColors.subtle,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                    AnimatedEyeSlashIcon(
                        visible = visible,
                        onClick = onToggleVisible
                    )
                }
            }
        )
    }
}

@Composable
fun AnimatedEyeSlashIcon(
    visible: Boolean,
    onClick: () -> Unit
) {
    val openProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0.72f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "EyeOpenProgress"
    )
    val slashProgress by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "EyeSlashProgress"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (visible) 1.07f else 1f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "EyeIconScale"
    )
    val tintColor by animateColorAsState(
        targetValue = if (visible) Color(0xFFFB7185) else IskraColors.muted,
        animationSpec = tween(durationMillis = 240),
        label = "EyeTintColor"
    )

    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(20.dp)
                .scale(iconScale)
        ) {
            val w = size.width
            val h = size.height
            val strokePx = w * 0.095f
            val cy = h * 0.5f
            val archHeight = h * 0.34f * openProgress

            val eyePath = Path().apply {
                moveTo(w * 0.08f, cy)
                cubicTo(
                    w * 0.25f, cy - archHeight,
                    w * 0.75f, cy - archHeight,
                    w * 0.92f, cy
                )
                cubicTo(
                    w * 0.75f, cy + archHeight,
                    w * 0.25f, cy + archHeight,
                    w * 0.08f, cy
                )
                close()
            }

            drawPath(
                path = eyePath,
                color = tintColor,
                style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val pupilRadius = w * (0.095f + 0.035f * openProgress)
            drawCircle(
                color = tintColor,
                radius = pupilRadius,
                center = Offset(w * 0.5f, cy),
                style = Stroke(width = strokePx)
            )

            if (slashProgress > 0.01f) {
                val startX = w * 0.15f
                val startY = h * 0.15f
                val endX = startX + (w * 0.85f - startX) * slashProgress
                val endY = startY + (h * 0.85f - startY) * slashProgress
                drawLine(
                    color = tintColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}


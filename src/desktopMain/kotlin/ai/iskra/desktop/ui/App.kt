package ai.iskra.desktop.ui

import ai.iskra.desktop.model.*
import ai.iskra.desktop.network.IskraApiClient
import ai.iskra.desktop.network.IskraApiException
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class DesktopSection(val title: String) {
    CATALOG("Каталог"),
    CHAT("Диалоги"),
    TAVERN("Таверна"),
    STORIES("Истории"),
    SETTINGS("Настройки")
}

private val AFFINITY_REGEX = Regex("<!--AFFINITY:[^>]*-->", RegexOption.IGNORE_CASE)
private val AFFINITY_LEVEL_REGEX = Regex("<!--AFFINITY:(?:[^>]*level:)?\\s*([a-zA-Z]+)[^>]*-->", RegexOption.IGNORE_CASE)

fun extractAffinity(raw: String): Pair<String, String?> {
    val match = AFFINITY_LEVEL_REGEX.find(raw)
    val level = match?.groupValues?.getOrNull(1)?.lowercase()
    val clean = raw.replace(AFFINITY_REGEX, "").trim()
    return clean to level
}

fun parseInitialAffinity(systemPrompt: String?): String {
    if (systemPrompt.isNullOrBlank()) return "neutral"
    val line = Regex("Начальный уровень отношений:\\s*([^\\n]+)").find(systemPrompt)?.groupValues?.getOrNull(1)?.lowercase() ?: return "neutral"
    return when {
        line.contains("ненавист") || line.contains("hatred") -> "hatred"
        line.contains("недовер") || line.contains("distrust") -> "distrust"
        line.contains("довери") || line.contains("trust") -> "trust"
        line.contains("любов") || line.contains("love") -> "love"
        else -> "neutral"
    }
}

@Composable
fun FormattedMessageText(text: String, baseColor: Color) {
    val annotated = remember(text, baseColor) {
        buildAnnotatedString {
            val tokenRegex = Regex("(\\*\\*[^*]+?\\*\\*|\\*[^*]+?\\*)")
            var lastIdx = 0
            for (match in tokenRegex.findAll(text)) {
                if (match.range.first > lastIdx) {
                    withStyle(SpanStyle(color = baseColor)) {
                        append(text.substring(lastIdx, match.range.first))
                    }
                }
                val token = match.value
                if (token.startsWith("**") && token.endsWith("**") && token.length >= 4) {
                    withStyle(SpanStyle(color = baseColor, fontWeight = FontWeight.ExtraBold)) {
                        append(token.substring(2, token.length - 2))
                    }
                } else if (token.startsWith("*") && token.endsWith("*") && token.length >= 2) {
                    withStyle(SpanStyle(color = baseColor.copy(alpha = 0.65f))) {
                        append(token.substring(1, token.length - 1))
                    }
                }
                lastIdx = match.range.last + 1
            }
            if (lastIdx < text.length) {
                withStyle(SpanStyle(color = baseColor)) {
                    append(text.substring(lastIdx))
                }
            }
        }
    }
    Text(text = annotated, fontSize = 14.sp, lineHeight = 20.sp)
}

@Composable
fun IskraDesktopApp(api: IskraApiClient = remember { IskraApiClient() }) {
    var isAuthenticated by remember { mutableStateOf(api.session?.accessToken?.isNotBlank() == true) }
    var themeMode by remember { mutableStateOf(api.session?.themeMode ?: "dark") }
    var paletteId by remember { mutableStateOf(api.session?.paletteId ?: "classic") }
    var activeSection by remember { mutableStateOf(DesktopSection.CATALOG) }
    var selectedBot by remember { mutableStateOf<ApiBot?>(null) }
    var bots by remember { mutableStateOf<List<ApiBot>>(emptyList()) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var points by remember { mutableStateOf<Points?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            try {
                bots = api.getBots()
                points = api.getPoints()
                profile = api.getProfile()
            } catch (e: IskraApiException) {
                if (e.status == 401) isAuthenticated = false
            } catch (_: Exception) {}
        }
    }

    IskraTheme(themeMode = themeMode, paletteId = paletteId) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!isAuthenticated) {
                DesktopAuthScreen(
                    api = api,
                    onAuthenticated = {
                        isAuthenticated = true
                    }
                )
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight().width(96.dp)
                    ) {
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("I", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        }
                        Spacer(Modifier.height(24.dp))

                        NavigationRailItem(
                            selected = activeSection == DesktopSection.CATALOG,
                            onClick = { activeSection = DesktopSection.CATALOG },
                            icon = { Icon(Icons.Default.Explore, contentDescription = null) },
                            label = { Text("Каталог", fontSize = 11.sp) }
                        )
                        NavigationRailItem(
                            selected = activeSection == DesktopSection.CHAT,
                            onClick = { activeSection = DesktopSection.CHAT },
                            icon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = null) },
                            label = { Text("Диалоги", fontSize = 11.sp) }
                        )
                        NavigationRailItem(
                            selected = activeSection == DesktopSection.TAVERN,
                            onClick = { activeSection = DesktopSection.TAVERN },
                            icon = { Icon(Icons.Default.Groups, contentDescription = null) },
                            label = { Text("Таверна", fontSize = 11.sp) }
                        )
                        NavigationRailItem(
                            selected = activeSection == DesktopSection.STORIES,
                            onClick = { activeSection = DesktopSection.STORIES },
                            icon = { Icon(Icons.Default.AutoStories, contentDescription = null) },
                            label = { Text("Истории", fontSize = 11.sp) }
                        )
                        Spacer(Modifier.weight(1f))
                        NavigationRailItem(
                            selected = activeSection == DesktopSection.SETTINGS,
                            onClick = { activeSection = DesktopSection.SETTINGS },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { Text("Профиль", fontSize = 11.sp) }
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    VerticalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (activeSection) {
                            DesktopSection.CATALOG -> DesktopCatalogView(
                                api = api,
                                bots = bots,
                                onBotsUpdated = { bots = it },
                                onOpenChat = { bot ->
                                    selectedBot = bot
                                    activeSection = DesktopSection.CHAT
                                }
                            )
                            DesktopSection.CHAT -> DesktopChatView(
                                api = api,
                                bots = bots,
                                initialBot = selectedBot,
                                remainingPoints = points?.remaining,
                                onPointsUpdated = { rem ->
                                    points = (points ?: Points()).copy(remaining = rem)
                                }
                            )
                            DesktopSection.TAVERN -> DesktopTavernView(api = api)
                            DesktopSection.STORIES -> DesktopStoriesView(api = api)
                            DesktopSection.SETTINGS -> DesktopSettingsView(
                                profile = profile,
                                points = points,
                                themeMode = themeMode,
                                paletteId = paletteId,
                                onThemeModeChange = {
                                    themeMode = it
                                    api.updateThemePreferences(it, paletteId)
                                },
                                onPaletteChange = {
                                    paletteId = it
                                    api.updateThemePreferences(themeMode, it)
                                },
                                onLogout = {
                                    scope.launch {
                                        api.logout()
                                        isAuthenticated = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DesktopAuthScreen(api: IskraApiClient, onAuthenticated: () -> Unit) {
    var mode by remember { mutableStateOf("login") }
    var step by remember { mutableStateOf("form") }
    var email by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var termsAccepted by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.width(420.dp).padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("I", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 28.sp)
                }
                Text("IskraAi", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)

                when (step) {
                    "otp" -> {
                        Text("Код подтверждения отправлен на $email", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { otpCode = it.filter(Char::isDigit).take(6) },
                            label = { Text("6-значный код") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (error != null) {
                            Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    error = null
                                    try {
                                        api.verifyOtp(email, otpCode)
                                        onAuthenticated()
                                    } catch (e: Exception) {
                                        error = e.message ?: "Неверный код"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy && otpCode.length == 6,
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Text(if (busy) "Проверка…" else "Подтвердить")
                        }
                        TextButton(onClick = { step = "form"; error = null }) {
                            Text("Назад")
                        }
                    }

                    "reset-request" -> {
                        Text("Сброс пароля", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Электронная почта") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (error != null) {
                            Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    error = null
                                    try {
                                        api.requestPasswordReset(email)
                                        step = "reset-verify"
                                    } catch (e: Exception) {
                                        error = e.message ?: "Не удалось отправить код"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy && email.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Text(if (busy) "Отправка…" else "Получить код сброса")
                        }
                        TextButton(onClick = { step = "form"; error = null }) {
                            Text("Вернуться ко входу")
                        }
                    }

                    "reset-verify" -> {
                        Text("Новый пароль", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { otpCode = it.filter(Char::isDigit).take(6) },
                            label = { Text("6-значный код из письма") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("Новый пароль (мин. 8 символов)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (error != null) {
                            Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    error = null
                                    try {
                                        val res = api.resetPassword(email, otpCode, newPassword)
                                        if (res.session != null) {
                                            onAuthenticated()
                                        } else {
                                            password = newPassword
                                            mode = "login"
                                            step = "form"
                                        }
                                    } catch (e: Exception) {
                                        error = e.message ?: "Ошибка смены пароля"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy && otpCode.length == 6 && newPassword.length >= 8,
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Text(if (busy) "Сохранение…" else "Сменить пароль")
                        }
                    }

                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)
                        ) {
                            TabButton("Вход", mode == "login", Modifier.weight(1f)) { mode = "login"; error = null }
                            TabButton("Регистрация", mode == "register", Modifier.weight(1f)) { mode = "register"; error = null }
                        }

                        if (mode == "register") {
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text("Имя или псевдоним") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Электронная почта") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Пароль") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (mode == "login") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { step = "reset-request"; error = null }) {
                                    Text("Забыли пароль?", fontSize = 12.sp)
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { termsAccepted = !termsAccepted }
                            ) {
                                Checkbox(checked = termsAccepted, onCheckedChange = { termsAccepted = it })
                                Text("Принимаю правила и политику конфиденциальности (18+)", fontSize = 12.sp)
                            }
                        }

                        if (error != null) {
                            Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    error = null
                                    try {
                                        if (mode == "login") {
                                            api.login(email, password)
                                            onAuthenticated()
                                        } else {
                                            api.requestOtp(email, displayName, password, false)
                                            otpCode = ""
                                            step = "otp"
                                        }
                                    } catch (e: Exception) {
                                        error = e.message ?: "Ошибка авторизации"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy && email.isNotBlank() && password.length >= 8 && (mode == "login" || (displayName.isNotBlank() && termsAccepted)),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Text(if (busy) "Подождите…" else if (mode == "login") "Войти" else "Создать аккаунт")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(9.dp)).clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shape = RoundedCornerShape(9.dp)
    ) {
        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
        }
    }
}

@Composable
fun DesktopCatalogView(
    api: IskraApiClient,
    bots: List<ApiBot>,
    onBotsUpdated: (List<ApiBot>) -> Unit,
    onOpenChat: (ApiBot) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("all") }
    var loading by remember { mutableStateOf(bots.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                onBotsUpdated(api.getBots())
            } catch (e: Exception) {
                error = e.message ?: "Не удалось загрузить каталог"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (bots.isEmpty()) reload()
    }

    val filtered = remember(bots, search, selectedGenre) {
        bots.filter { bot ->
            val matchesGenre = selectedGenre == "all" || bot.genre == selectedGenre
            val matchesQuery = search.isBlank() ||
                bot.name.contains(search, ignoreCase = true) ||
                (bot.description?.contains(search, ignoreCase = true) == true)
            matchesGenre && matchesQuery
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Каталог персонажей", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("Выберите персонажа для живого ролевого диалога", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Поиск персонажей…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.width(280.dp)
                )
                IconButton(onClick = ::reload) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(GENRE_LABELS.entries.toList()) { (key, label) ->
                FilterChip(
                    selected = selectedGenre == key,
                    onClick = { selectedGenre = key },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = ::reload) { Text("Повторить") }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 270.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered, key = { it.id }) { bot ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenChat(bot) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        bot.name.take(2).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bot.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        GENRE_LABELS[bot.genre] ?: bot.genre,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val res = api.toggleBotLike(bot.id)
                                                onBotsUpdated(
                                                    bots.map {
                                                        if (it.id == bot.id) it.copy(isLiked = res.isLiked, likesCount = res.likesCount) else it
                                                    }
                                                )
                                            } catch (_: Exception) {}
                                        }
                                    }
                                ) {
                                    Icon(
                                        if (bot.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (bot.isLiked) Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(bot.likesCount.toString(), fontSize = 12.sp)
                                }
                            }

                            Text(
                                bot.description ?: "Без описания",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                minLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            Button(
                                onClick = { onOpenChat(bot) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Открыть диалог")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DesktopChatView(
    api: IskraApiClient,
    bots: List<ApiBot>,
    initialBot: ApiBot?,
    remainingPoints: Int?,
    onPointsUpdated: (Int) -> Unit
) {
    var activeBot by remember(initialBot) { mutableStateOf(initialBot ?: bots.firstOrNull()) }
    var conversationId by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var affinity by remember { mutableStateOf("neutral") }
    var input by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(activeBot?.id) {
        val bot = activeBot ?: return@LaunchedEffect
        loading = true
        error = null
        affinity = parseInitialAffinity(bot.systemPrompt)
        try {
            val convs = api.getConversations()
            val conv = convs.find { it.botId == bot.id } ?: api.createConversation(bot.id)
            conversationId = conv.id
            val page = api.getMessages(conv.id)
            messages = page.items.map { msg ->
                val (clean, level) = extractAffinity(msg.content)
                if (level != null && AFFINITY_LABELS.containsKey(level)) affinity = level
                msg.copy(content = clean, versions = listOf(clean), versionIndex = 0)
            }
        } catch (e: Exception) {
            error = e.message ?: "Не удалось загрузить диалог"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendCurrentMessage() {
        val convId = conversationId ?: return
        val content = input.trim()
        if (content.isEmpty() || sending || remainingPoints == 0) return
        input = ""
        sending = true
        error = null

        val tempUser = Message(id = "temp-u-${System.currentTimeMillis()}", conversationId = convId, sender = "user", content = content)
        messages = messages + tempUser

        scope.launch {
            try {
                val res = api.sendMessage(convId, content)
                val (cleanReply, newAffinity) = extractAffinity(res.assistantMessage.content)
                if (newAffinity != null && AFFINITY_LABELS.containsKey(newAffinity)) {
                    affinity = newAffinity
                }
                val finalAssistant = res.assistantMessage.copy(
                    content = cleanReply,
                    versions = listOf(cleanReply),
                    versionIndex = 0
                )
                messages = messages.filterNot { it.id == tempUser.id } + listOf(
                    res.userMessage.copy(versions = listOf(res.userMessage.content), versionIndex = 0),
                    finalAssistant
                )
                onPointsUpdated(res.remaining)
            } catch (e: Exception) {
                messages = messages.filterNot { it.id == tempUser.id }
                input = content
                error = e.message ?: "Ошибка отправки"
            } finally {
                sending = false
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.width(260.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Персонажи", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(bots, key = { it.id }) { bot ->
                    val isSelected = activeBot?.id == bot.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        modifier = Modifier.fillMaxWidth().clickable { activeBot = bot }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(bot.name.take(2).uppercase(), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(bot.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(GENRE_LABELS[bot.genre] ?: bot.genre, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        VerticalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

        if (activeBot == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Выберите персонажа слева", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(activeBot!!.name, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text(
                                "${GENRE_LABELS[activeBot!!.genre] ?: activeBot!!.genre} · Отношение: ${AFFINITY_LABELS[affinity] ?: "Нейтрально"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (remainingPoints != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    "Баллы: $remainingPoints",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                if (loading) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            val isUser = msg.sender == "user"
                            val isLatestAssistant = !isUser && messages.lastOrNull()?.id == msg.id
                            val isLatestUser = isUser && messages.dropLast(1).lastOrNull()?.id == msg.id

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.widthIn(max = 580.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        if (editingId == msg.id) {
                                            OutlinedTextField(
                                                value = editText,
                                                onValueChange = { editText = it },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                                Button(
                                                    onClick = {
                                                        val convId = conversationId ?: return@Button
                                                        val updatedText = editText.trim()
                                                        if (updatedText.isEmpty()) return@Button
                                                        scope.launch {
                                                            sending = true
                                                            try {
                                                                val res = api.editMessage(convId, msg.id, updatedText)
                                                                val (cleanReply, level) = extractAffinity(res.assistantMessage.content)
                                                                if (level != null && AFFINITY_LABELS.containsKey(level)) affinity = level
                                                                messages = messages.map { m ->
                                                                    when (m.id) {
                                                                        res.userMessage.id -> res.userMessage.copy(versions = listOf(res.userMessage.content))
                                                                        res.assistantMessage.id -> res.assistantMessage.copy(content = cleanReply, versions = listOf(cleanReply))
                                                                        else -> m
                                                                    }
                                                                }
                                                                onPointsUpdated(res.remaining)
                                                                editingId = null
                                                            } catch (e: Exception) {
                                                                error = e.message
                                                            } finally {
                                                                sending = false
                                                            }
                                                        }
                                                    },
                                                    enabled = !sending
                                                ) { Text("Сохранить") }
                                                TextButton(onClick = { editingId = null }) { Text("Отмена") }
                                            }
                                        } else {
                                            FormattedMessageText(
                                                text = msg.content,
                                                baseColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    if (!isUser && msg.versions.size > 1) {
                                        TextButton(
                                            onClick = {
                                                val prev = (msg.versionIndex - 1).coerceAtLeast(0)
                                                messages = messages.map {
                                                    if (it.id == msg.id) it.copy(versionIndex = prev, content = it.versions[prev]) else it
                                                }
                                            },
                                            enabled = msg.versionIndex > 0
                                        ) { Text("<", fontSize = 11.sp) }
                                        Text("${msg.versionIndex + 1}/${msg.versions.size}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        TextButton(
                                            onClick = {
                                                val next = (msg.versionIndex + 1).coerceAtMost(msg.versions.lastIndex)
                                                messages = messages.map {
                                                    if (it.id == msg.id) it.copy(versionIndex = next, content = it.versions[next]) else it
                                                }
                                            },
                                            enabled = msg.versionIndex < msg.versions.lastIndex
                                        ) { Text(">", fontSize = 11.sp) }
                                    }

                                    if (isLatestUser && editingId == null) {
                                        TextButton(onClick = { editingId = msg.id; editText = msg.content }, enabled = !sending) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Изменить", fontSize = 11.sp)
                                        }
                                    }

                                    if (isLatestAssistant && messages.size > 1) {
                                        TextButton(
                                            onClick = {
                                                val convId = conversationId ?: return@TextButton
                                                scope.launch {
                                                    sending = true
                                                    try {
                                                        val res = api.regenerateMessage(convId, msg.id)
                                                        val (cleanReply, level) = extractAffinity(res.assistantMessage.content)
                                                        if (level != null && AFFINITY_LABELS.containsKey(level)) affinity = level
                                                        val updatedVersions = (msg.versions.ifEmpty { listOf(msg.content) }) + cleanReply
                                                        messages = messages.map {
                                                            if (it.id == msg.id) {
                                                                it.copy(
                                                                    content = cleanReply,
                                                                    versions = updatedVersions,
                                                                    versionIndex = updatedVersions.lastIndex
                                                                )
                                                            } else it
                                                        }
                                                        onPointsUpdated(res.remaining)
                                                    } catch (e: Exception) {
                                                        error = e.message
                                                    } finally {
                                                        sending = false
                                                    }
                                                }
                                            },
                                            enabled = !sending && (remainingPoints == null || remainingPoints > 0)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Другой ответ", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
                }

                Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text("Сообщение персонажу…") },
                            enabled = !sending && editingId == null && (remainingPoints == null || remainingPoints > 0),
                            modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                                if (event.key == Key.Enter && event.type == KeyEventType.KeyDown && !event.isShiftPressed) {
                                    sendCurrentMessage()
                                    true
                                } else {
                                    false
                                }
                            }
                        )
                        Button(
                            onClick = ::sendCurrentMessage,
                            enabled = !sending && input.isNotBlank() && editingId == null && (remainingPoints == null || remainingPoints > 0),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Отправить")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DesktopTavernView(api: IskraApiClient) {
    var rooms by remember { mutableStateOf<List<ActiveTavernRoom>>(emptyList()) }
    var activeRoomId by remember { mutableStateOf<String?>(null) }
    var roomData by remember { mutableStateOf<TavernRoomData?>(null) }
    var queueStatus by remember { mutableStateOf<String?>(null) }
    var messageInput by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            rooms = api.getActiveTavernRooms()
        } catch (_: Exception) {}
        loading = false
    }

    LaunchedEffect(activeRoomId) {
        val roomId = activeRoomId ?: return@LaunchedEffect
        while (activeRoomId == roomId) {
            try {
                roomData = api.getTavernRoom(roomId)
            } catch (_: Exception) {}
            delay(3500)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Таверна", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("Совместные ролевые приключения (Duo и Trio)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            queueStatus = "Поиск собеседника (Duo)…"
                            try {
                                val res = api.joinTavernQueue("duo")
                                if (res.roomId != null) {
                                    activeRoomId = res.roomId
                                    queueStatus = null
                                }
                            } catch (e: Exception) {
                                queueStatus = e.message
                            }
                        }
                    }
                ) { Text("Найти Duo") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            queueStatus = "Поиск комнаты с ИИ-мастером (Trio)…"
                            try {
                                val res = api.joinTavernQueue("trio")
                                if (res.roomId != null) {
                                    activeRoomId = res.roomId
                                    queueStatus = null
                                }
                            } catch (e: Exception) {
                                queueStatus = e.message
                            }
                        }
                    }
                ) { Text("Найти Trio") }
            }
        }

        if (queueStatus != null) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(queueStatus!!, fontSize = 13.sp)
                    TextButton(onClick = { scope.launch { api.leaveTavernQueue(); queueStatus = null } }) {
                        Text("Отменить")
                    }
                }
            }
        }

        if (activeRoomId != null && roomData != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Комната #${activeRoomId!!.take(8)} (${roomData!!.room.mode.uppercase()})", fontWeight = FontWeight.Bold)
                        TextButton(onClick = { activeRoomId = null; roomData = null }) {
                            Text("К списку комнат")
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(roomData!!.messages, key = { it.id }) { m ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(m.senderName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(4.dp))
                                    FormattedMessageText(m.content, MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            placeholder = { Text("Реплика в комнату…") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val roomId = activeRoomId ?: return@Button
                                val txt = messageInput.trim()
                                if (txt.isEmpty()) return@Button
                                messageInput = ""
                                scope.launch {
                                    try {
                                        api.sendTavernMessage(roomId, txt)
                                        roomData = api.getTavernRoom(roomId)
                                    } catch (_: Exception) {}
                                }
                            }
                        ) { Text("Отправить") }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rooms, key = { it.id }) { room ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().clickable { activeRoomId = room.id }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${room.partner.displayName} · ${room.mode.uppercase()}", fontWeight = FontWeight.Bold)
                                Text(
                                    room.lastMessage?.content ?: "Откройте комнату для продолжения",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(onClick = { activeRoomId = room.id }) {
                                Text("Войти")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DesktopStoriesView(api: IskraApiClient) {
    var stories by remember { mutableStateOf<List<StorySummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            stories = api.getStories()
        } catch (_: Exception) {}
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Интерактивные истории", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(stories, key = { it.id }) { story ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(story.title, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                            if (story.subtitle.isNotBlank()) {
                                Text(story.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(story.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DesktopSettingsView(
    profile: UserProfile?,
    points: Points?,
    themeMode: String,
    paletteId: String,
    onThemeModeChange: (String) -> Unit,
    onPaletteChange: (String) -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Профиль и настройки", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(profile?.displayName?.ifBlank { "Пользователь IskraAi" } ?: "Пользователь IskraAi", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(profile?.email ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            "Тариф: ${(profile?.tier ?: "free").uppercase()}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            "Баллы: ${points?.remaining ?: profile?.points?.remaining ?: 0}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Оформление", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = themeMode == "dark",
                        onClick = { onThemeModeChange("dark") },
                        label = { Text("Тёмная тема") }
                    )
                    FilterChip(
                        selected = themeMode == "light",
                        onClick = { onThemeModeChange("light") },
                        label = { Text("Светлая тема") }
                    )
                }

                Text("Акцентная палитра", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ISKRA_PALETTES.forEach { p ->
                        FilterChip(
                            selected = paletteId == p.id,
                            onClick = { onPaletteChange(p.id) },
                            leadingIcon = {
                                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(p.darkAccent))
                            },
                            label = { Text(p.label, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.width(220.dp)
        ) {
            Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Выйти из аккаунта")
        }
    }
}

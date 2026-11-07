package ai.iskra.desktop.network

import ai.iskra.desktop.model.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import okhttp3.Dns
import okhttp3.Protocol
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

class IskraApiException(
    val status: Int,
    val code: String,
    override val message: String
) : Exception(message)

class IskraApiClient {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val configFile = File("iskra.config.json")
    private val sessionFile = File(System.getProperty("user.home"), ".config/iskra-desktop/session.json")
    private val avatarCacheDir = File(System.getProperty("user.home"), ".config/iskra-desktop/avatar-cache").apply { mkdirs() }
    private val refreshMutex = Mutex()
    private val imageCache = ConcurrentHashMap<String, ByteArray>()
    private val imageLocks = ConcurrentHashMap<String, Mutex>()
    private val dnsGoodIpCache = ConcurrentHashMap<String, List<InetAddress>>()

    private val initialConfig: ClientConfig = loadClientConfig()
    val baseUrl: String = initialConfig.apiUrl.trimEnd('/').ifBlank { "https://bacand-iskra.onrender.com" }
    var session: StoredSession? = loadSession()
        private set

    private val smartDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            dnsGoodIpCache[hostname]?.takeIf { it.isNotEmpty() }?.let { return it }
            val all = Dns.SYSTEM.lookup(hostname)
            val v4 = all.filterIsInstance<Inet4Address>()
            val candidates = v4.ifEmpty { all }
            if (candidates.size <= 1) return candidates
            val responsive = mutableListOf<InetAddress>()
            val fallback = mutableListOf<InetAddress>()
            for (addr in candidates) {
                val ok = try {
                    Socket().use { sock ->
                        sock.connect(InetSocketAddress(addr, 443), 1300)
                        true
                    }
                } catch (_: Exception) {
                    false
                }
                if (ok) responsive.add(addr) else fallback.add(addr)
            }
            val ordered = responsive + fallback
            if (responsive.isNotEmpty()) {
                dnsGoodIpCache[hostname] = ordered
            }
            return ordered
        }
    }

    private val http = HttpClient(OkHttp) {
        engine {
            config {
                dns(smartDns)
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(65, TimeUnit.SECONDS)
                writeTimeout(65, TimeUnit.SECONDS)
                retryOnConnectionFailure(true)
            }
        }
        install(ContentNegotiation) {
            json(this@IskraApiClient.json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 65_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 65_000
        }
    }

    private val imageHttp = HttpClient(OkHttp) {
        engine {
            config {
                dns(smartDns)
                protocols(listOf(Protocol.HTTP_1_1))
                connectTimeout(8, TimeUnit.SECONDS)
                readTimeout(12, TimeUnit.SECONDS)
                retryOnConnectionFailure(true)
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 25_000
            connectTimeoutMillis = 8_000
            socketTimeoutMillis = 12_000
        }
    }

    private fun loadClientConfig(): ClientConfig {
        return try {
            if (configFile.exists()) {
                json.decodeFromString(ClientConfig.serializer(), configFile.readText())
            } else {
                ClientConfig()
            }
        } catch (_: Exception) {
            ClientConfig()
        }
    }

    private fun loadSession(): StoredSession? {
        return try {
            if (sessionFile.exists()) {
                json.decodeFromString(StoredSession.serializer(), sessionFile.readText())
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun hasValidToken(): Boolean = !session?.accessToken.isNullOrBlank()

    fun activeClientKey(): String =
        session?.clientKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: initialConfig.clientKey.trim()

    fun saveSession(next: StoredSession?) {
        session = next
        try {
            if (next == null) {
                if (sessionFile.exists()) sessionFile.delete()
            } else {
                sessionFile.parentFile?.mkdirs()
                sessionFile.writeText(json.encodeToString(StoredSession.serializer(), next))
            }
        } catch (_: Exception) {}
    }

    fun updateClientKey(newKey: String) {
        val cleaned = newKey.trim()
        val current = session ?: StoredSession(accessToken = "")
        saveSession(current.copy(clientKey = cleaned))
        try {
            if (configFile.exists()) {
                val cfg = loadClientConfig().copy(clientKey = cleaned)
                configFile.writeText(json.encodeToString(ClientConfig.serializer(), cfg))
            }
        } catch (_: Exception) {}
    }

    fun updateCachedAvatar(avatarUrl: String?) {
        val current = session ?: return
        saveSession(current.copy(cachedAvatarUrl = avatarUrl))
    }

    fun updateThemePreferences(
        themeMode: String = session?.themeMode ?: "dark",
        paletteId: String = session?.paletteId ?: "classic",
        wallpapersEnabled: Boolean = session?.wallpapersEnabled ?: false
    ) {
        val current = session ?: StoredSession(
            accessToken = "",
            themeMode = themeMode,
            paletteId = paletteId,
            wallpapersEnabled = wallpapersEnabled
        )
        saveSession(
            current.copy(
                themeMode = themeMode,
                paletteId = paletteId,
                wallpapersEnabled = wallpapersEnabled
            )
        )
    }

    suspend fun warmBackend() {
        try {
            http.get("$baseUrl/api/capabilities") {
                header(HttpHeaders.Origin, "https://iskraai.pages.dev")
            }
        } catch (_: Exception) {}
    }

    private fun cacheFileForUrl(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(40)
        return File(avatarCacheDir, "$hex.bin")
    }

    private suspend fun downloadInRangeChunks(targetUrl: String): ByteArray? {
        val chunkSize = 14_000
        val out = ByteArrayOutputStream()
        var start = 0
        var totalSize = -1
        var guard = 0
        while (guard < 80) {
            guard++
            val end = start + chunkSize - 1
            val resp = imageHttp.get(targetUrl) {
                header(HttpHeaders.Range, "bytes=$start-$end")
                header(HttpHeaders.Accept, "image/*,*/*;q=0.8")
                header(HttpHeaders.UserAgent, "Mozilla/5.0")
            }
            val status = resp.status.value
            if (status != 206 && status != 200) return null
            val chunk = resp.readBytes()
            if (chunk.isEmpty()) break
            out.write(chunk)
            if (status == 200) {
                return out.toByteArray()
            }
            val contentRange = resp.headers[HttpHeaders.ContentRange]
            if (contentRange != null && contentRange.contains("/")) {
                val totalStr = contentRange.substringAfterLast("/").trim()
                totalStr.toIntOrNull()?.let { totalSize = it }
            }
            start += chunk.size
            if ((totalSize > 0 && start >= totalSize) || chunk.size < chunkSize) {
                break
            }
        }
        val bytes = out.toByteArray()
        return bytes.takeIf { it.isNotEmpty() }
    }

    suspend fun fetchImageBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return@withContext null

        if (trimmed.startsWith("data:image/", ignoreCase = true) && trimmed.contains(";base64,")) {
            return@withContext try {
                val base64Part = trimmed.substringAfter(";base64,").trim()
                Base64.getDecoder().decode(base64Part)
            } catch (_: Exception) {
                null
            }
        }

        val resolved = if (trimmed.startsWith("/")) "$baseUrl$trimmed" else trimmed
        imageCache[resolved]?.let { return@withContext it }

        val diskFile = runCatching { cacheFileForUrl(resolved) }.getOrNull()
        if (diskFile != null && diskFile.exists() && diskFile.length() > 64) {
            val cachedBytes = runCatching { diskFile.readBytes() }.getOrNull()
            if (cachedBytes != null && cachedBytes.isNotEmpty()) {
                imageCache[resolved] = cachedBytes
                return@withContext cachedBytes
            }
        }

        val mutex = imageLocks.computeIfAbsent(resolved) { Mutex() }
        mutex.withLock {
            imageCache[resolved]?.let { return@withLock it }
            if (diskFile != null && diskFile.exists() && diskFile.length() > 64) {
                val cachedBytes = runCatching { diskFile.readBytes() }.getOrNull()
                if (cachedBytes != null && cachedBytes.isNotEmpty()) {
                    imageCache[resolved] = cachedBytes
                    return@withLock cachedBytes
                }
            }

            val marker = "/storage/v1/object/public/bot-avatars/"
            val downloaded: ByteArray? = if (resolved.contains(marker)) {
                val pathPart = resolved.substringAfter(marker)
                val encodedPath = URLEncoder.encode(pathPart, "UTF-8")
                val proxyBytes = try {
                    val proxyResp = imageHttp.get("$baseUrl/api/bots/avatar?path=$encodedPath") {
                        header(HttpHeaders.Origin, "https://iskraai.pages.dev")
                    }
                    if (proxyResp.status.isSuccess()) proxyResp.readBytes().takeIf { it.isNotEmpty() } else null
                } catch (_: Exception) {
                    null
                }
                proxyBytes ?: try {
                    downloadInRangeChunks(resolved)
                } catch (_: Exception) {
                    null
                }
            } else {
                try {
                    downloadInRangeChunks(resolved)
                } catch (_: Exception) {
                    try {
                        val resp = imageHttp.get(resolved)
                        if (resp.status.isSuccess()) resp.readBytes().takeIf { it.isNotEmpty() } else null
                    } catch (_: Exception) {
                        null
                    }
                }
            }

            if (downloaded != null && downloaded.isNotEmpty()) {
                imageCache[resolved] = downloaded
                runCatching { diskFile?.writeBytes(downloaded) }
                downloaded
            } else {
                null
            }
        }
    }

    private fun compressAvatarToJpeg(file: File, maxDim: Int = 320): ByteArray {
        val original = ImageIO.read(file) ?: return file.readBytes()
        val w = original.width.coerceAtLeast(1)
        val h = original.height.coerceAtLeast(1)
        val scale = minOf(1.0, maxDim.toDouble() / maxOf(w, h).toDouble())
        val targetW = (w * scale).toInt().coerceAtLeast(1)
        val targetH = (h * scale).toInt().coerceAtLeast(1)
        val rgbImage = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB)
        val g = rgbImage.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(original, 0, 0, targetW, targetH, java.awt.Color.BLACK, null)
        g.dispose()

        val out = ByteArrayOutputStream()
        val writers = ImageIO.getImageWritersByFormatName("jpeg")
        if (writers.hasNext()) {
            val writer = writers.next()
            val param = writer.defaultWriteParam
            if (param.canWriteCompressed()) {
                param.compressionMode = ImageWriteParam.MODE_EXPLICIT
                param.compressionQuality = 0.82f
            }
            ImageIO.createImageOutputStream(out).use { ios ->
                writer.output = ios
                writer.write(null, IIOImage(rgbImage, null, null), param)
                writer.dispose()
            }
        } else {
            ImageIO.write(rgbImage, "jpg", out)
        }
        return out.toByteArray()
    }

    suspend fun uploadAvatar(file: File): String = withContext(Dispatchers.IO) {
        val jpegBytes = runCatching { compressAvatarToJpeg(file, 320) }.getOrElse { file.readBytes() }
        val dataUriFallback by lazy {
            val b64 = Base64.getEncoder().encodeToString(jpegBytes)
            "data:image/jpeg;base64,$b64"
        }
        try {
            val response: HttpResponse = http.submitFormWithBinaryData(
                url = "$baseUrl/api/bots/avatar",
                formData = formData {
                    append(
                        "avatar",
                        jpegBytes,
                        Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"avatar.jpg\"")
                        }
                    )
                }
            ) {
                header(HttpHeaders.Origin, "https://iskraai.pages.dev")
                val token = session?.accessToken
                if (!token.isNullOrBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
            }
            if (response.status.isSuccess()) {
                val raw = response.bodyAsText()
                val element = json.parseToJsonElement(raw)
                val payload = if (element is JsonObject && element.containsKey("data")) element["data"]!! else element
                val uploadedUrl = json.decodeFromJsonElement(AvatarUploadResponse.serializer(), payload).url.trim()
                if (uploadedUrl.isNotEmpty()) {
                    imageCache[uploadedUrl] = jpegBytes
                    runCatching { cacheFileForUrl(uploadedUrl).writeBytes(jpegBytes) }
                    return@withContext uploadedUrl
                }
            }
        } catch (_: Exception) {}
        imageCache[dataUriFallback] = jpegBytes
        dataUriFallback
    }

    private suspend fun <T> withRetry(retries: Int = 2, block: suspend () -> T): T {
        var lastError: Throwable? = null
        for (attempt in 0..retries) {
            try {
                return block()
            } catch (e: IskraApiException) {
                if (e.status in 502..504 && attempt < retries) {
                    lastError = e
                    delay(2500)
                    continue
                }
                throw e
            } catch (e: Throwable) {
                lastError = e
                if (attempt < retries) {
                    delay(2500)
                } else {
                    throw IskraApiException(
                        status = 503,
                        code = "NETWORK_ERROR",
                        message = "Сервер просыпается или недоступен. Попробуйте ещё раз через пару секунд."
                    )
                }
            }
        }
        throw lastError ?: IskraApiException(500, "UNKNOWN", "Ошибка соединения")
    }

    private suspend fun <T> executeRaw(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        bodyObj: Any? = null,
        bodySerializer: KSerializer<Any>? = null,
        responseSerializer: KSerializer<T>
    ): T {
        val url = "$baseUrl/${path.trimStart('/')}"
        val response: HttpResponse = http.request(url) {
            this.method = method
            header(HttpHeaders.Accept, ContentType.Application.Json)
            header(HttpHeaders.Origin, "https://iskraai.pages.dev")
            header(HttpHeaders.UserAgent, "IskraDesktop/0.8.2")
            val cKey = activeClientKey()
            if (cKey.isNotEmpty()) {
                header("X-Iskra-Client-Key", cKey)
            }
            val token = session?.accessToken
            if (!token.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (bodyObj != null && bodySerializer != null) {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(bodySerializer, bodyObj))
            }
        }

        val rawText = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val err = try {
                json.decodeFromString(ApiErrorEnvelope.serializer(), rawText).error
            } catch (_: Exception) {
                null
            }
            throw IskraApiException(
                status = response.status.value,
                code = err?.code ?: "API_ERROR",
                message = err?.message ?: "Ошибка запроса (${response.status.value})"
            )
        }

        val element = json.parseToJsonElement(rawText)
        val payload = if (element is JsonObject && element.containsKey("data")) {
            element["data"]!!
        } else {
            element
        }
        return json.decodeFromJsonElement(responseSerializer, payload)
    }

    private suspend fun <T> request(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        bodyObj: Any? = null,
        bodySerializer: KSerializer<Any>? = null,
        responseSerializer: KSerializer<T>
    ): T {
        return try {
            withRetry {
                executeRaw(path, method, bodyObj, bodySerializer, responseSerializer)
            }
        } catch (e: IskraApiException) {
            if (e.status == 401 && hasValidToken() && !path.startsWith("/api/auth/")) {
                if (refreshSession()) {
                    return executeRaw(path, method, bodyObj, bodySerializer, responseSerializer)
                }
            }
            throw e
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun refreshSession(): Boolean = refreshMutex.withLock {
        val current = session ?: return false
        val refreshToken = current.refreshToken ?: return false
        return try {
            val res = executeRaw(
                path = "/api/auth/refresh",
                method = HttpMethod.Post,
                bodyObj = RefreshRequest(refreshToken),
                bodySerializer = RefreshRequest.serializer() as KSerializer<Any>,
                responseSerializer = AuthResponse.serializer()
            )
            val tokens = res.session ?: return false
            saveSession(
                current.copy(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken ?: current.refreshToken,
                    userId = res.user.id,
                    email = res.user.email
                )
            )
            true
        } catch (e: IskraApiException) {
            if (e.status == 401) {
                saveSession(current.copy(accessToken = "", refreshToken = null))
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun login(email: String, password: String): AuthResponse {
        val res = request(
            path = "/api/auth/login",
            method = HttpMethod.Post,
            bodyObj = LoginRequest(email.trim(), password),
            bodySerializer = LoginRequest.serializer() as KSerializer<Any>,
            responseSerializer = AuthResponse.serializer()
        )
        res.session?.let {
            saveSession(
                StoredSession(
                    accessToken = it.accessToken,
                    refreshToken = it.refreshToken,
                    userId = res.user.id,
                    email = res.user.email,
                    themeMode = session?.themeMode ?: "dark",
                    paletteId = session?.paletteId ?: "classic",
                    wallpapersEnabled = session?.wallpapersEnabled ?: false,
                    clientKey = session?.clientKey ?: "",
                    cachedAvatarUrl = session?.cachedAvatarUrl
                )
            )
        }
        return res
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun requestOtp(email: String, displayName: String, password: String, newsletter: Boolean) {
        request(
            path = "/api/auth/otp/request",
            method = HttpMethod.Post,
            bodyObj = OtpRequest(
                email = email.trim(),
                displayName = displayName.trim(),
                password = password,
                newsletterOptIn = newsletter
            ),
            bodySerializer = OtpRequest.serializer() as KSerializer<Any>,
            responseSerializer = JsonObject.serializer()
        )
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun verifyOtp(email: String, code: String): AuthResponse {
        val res = request(
            path = "/api/auth/otp/verify",
            method = HttpMethod.Post,
            bodyObj = OtpVerifyRequest(email = email.trim(), code = code.trim()),
            bodySerializer = OtpVerifyRequest.serializer() as KSerializer<Any>,
            responseSerializer = AuthResponse.serializer()
        )
        res.session?.let {
            saveSession(
                StoredSession(
                    accessToken = it.accessToken,
                    refreshToken = it.refreshToken,
                    userId = res.user.id,
                    email = res.user.email,
                    themeMode = session?.themeMode ?: "dark",
                    paletteId = session?.paletteId ?: "classic",
                    wallpapersEnabled = session?.wallpapersEnabled ?: false,
                    clientKey = session?.clientKey ?: "",
                    cachedAvatarUrl = session?.cachedAvatarUrl
                )
            )
        }
        return res
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun requestPasswordReset(email: String) {
        request(
            path = "/api/auth/password/request-reset",
            method = HttpMethod.Post,
            bodyObj = PasswordResetRequest(email = email.trim()),
            bodySerializer = PasswordResetRequest.serializer() as KSerializer<Any>,
            responseSerializer = JsonObject.serializer()
        )
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun resetPassword(email: String, code: String, newPassword: String): AuthResponse {
        val res = request(
            path = "/api/auth/password/reset",
            method = HttpMethod.Post,
            bodyObj = PasswordResetConfirmRequest(email = email.trim(), code = code.trim(), newPassword = newPassword),
            bodySerializer = PasswordResetConfirmRequest.serializer() as KSerializer<Any>,
            responseSerializer = AuthResponse.serializer()
        )
        res.session?.let {
            saveSession(
                StoredSession(
                    accessToken = it.accessToken,
                    refreshToken = it.refreshToken,
                    userId = res.user.id,
                    email = res.user.email,
                    themeMode = session?.themeMode ?: "dark",
                    paletteId = session?.paletteId ?: "classic",
                    wallpapersEnabled = session?.wallpapersEnabled ?: false,
                    clientKey = session?.clientKey ?: "",
                    cachedAvatarUrl = session?.cachedAvatarUrl
                )
            )
        }
        return res
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun logout() {
        try {
            request(
                path = "/api/auth/logout",
                method = HttpMethod.Post,
                bodyObj = LogoutRequest(false),
                bodySerializer = LogoutRequest.serializer() as KSerializer<Any>,
                responseSerializer = JsonObject.serializer()
            )
        } catch (_: Exception) {}
        val cur = session
        saveSession(
            if (cur != null) cur.copy(accessToken = "", refreshToken = null, userId = null, email = null)
            else null
        )
    }

    suspend fun getBots(): List<ApiBot> =
        request("/api/bots", responseSerializer = serializer())

    suspend fun getMyBots(): List<ApiBot> =
        request("/api/bots/mine", responseSerializer = serializer())

    suspend fun getBot(botId: String): ApiBot =
        request("/api/bots/$botId", responseSerializer = ApiBot.serializer())

    @Suppress("UNCHECKED_CAST")
    suspend fun createBot(req: CreateBotRequest): ApiBot =
        request(
            path = "/api/bots",
            method = HttpMethod.Post,
            bodyObj = req,
            bodySerializer = CreateBotRequest.serializer() as KSerializer<Any>,
            responseSerializer = ApiBot.serializer()
        )

    @Suppress("UNCHECKED_CAST")
    suspend fun updateBot(botId: String, req: CreateBotRequest): ApiBot =
        request(
            path = "/api/bots/$botId",
            method = HttpMethod.Patch,
            bodyObj = req,
            bodySerializer = CreateBotRequest.serializer() as KSerializer<Any>,
            responseSerializer = ApiBot.serializer()
        )

    suspend fun deleteBot(botId: String) {
        request("/api/bots/$botId", method = HttpMethod.Delete, responseSerializer = JsonObject.serializer())
    }

    suspend fun toggleBotLike(botId: String): LikeToggleResponse =
        request("/api/bots/$botId/like", method = HttpMethod.Post, responseSerializer = LikeToggleResponse.serializer())

    suspend fun getProfile(): UserProfile {
        val p = request("/api/profile", responseSerializer = UserProfile.serializer())
        p.effectiveAvatarUrl?.let { updateCachedAvatar(it) }
        return p
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun updateProfile(displayName: String? = null, enhancedMemory: Boolean? = null, avatarUrl: String? = null) {
        request(
            path = "/api/profile",
            method = HttpMethod.Patch,
            bodyObj = UpdateProfileRequest(displayName, enhancedMemory, avatarUrl),
            bodySerializer = UpdateProfileRequest.serializer() as KSerializer<Any>,
            responseSerializer = JsonObject.serializer()
        )
        if (avatarUrl != null) {
            updateCachedAvatar(avatarUrl)
        }
    }

    suspend fun deleteAccount() {
        request("/api/profile", method = HttpMethod.Delete, responseSerializer = JsonObject.serializer())
        logout()
    }

    suspend fun getDeveloperKeys(): DeveloperKeysResponse =
        request("/api/developer/keys", responseSerializer = DeveloperKeysResponse.serializer())

    @Suppress("UNCHECKED_CAST")
    suspend fun createDeveloperKey(appName: String, platform: String): CreateDeveloperKeyResponse =
        request(
            path = "/api/developer/keys",
            method = HttpMethod.Post,
            bodyObj = CreateDeveloperKeyRequest(appName = appName.trim(), platform = platform),
            bodySerializer = CreateDeveloperKeyRequest.serializer() as KSerializer<Any>,
            responseSerializer = CreateDeveloperKeyResponse.serializer()
        )

    suspend fun rotateDeveloperKey(appId: String): CreateDeveloperKeyResponse =
        request(
            path = "/api/developer/keys/$appId/rotate",
            method = HttpMethod.Post,
            responseSerializer = CreateDeveloperKeyResponse.serializer()
        )

    suspend fun revokeDeveloperKey(appId: String, permanent: Boolean = false) {
        val suffix = if (permanent) "?permanent=true" else ""
        request(
            path = "/api/developer/keys/$appId$suffix",
            method = HttpMethod.Delete,
            responseSerializer = JsonObject.serializer()
        )
    }

    suspend fun getPoints(): Points =
        request("/api/points", responseSerializer = Points.serializer())

    suspend fun getConversations(): List<Conversation> =
        request("/api/conversations", responseSerializer = serializer())

    @Suppress("UNCHECKED_CAST")
    suspend fun createConversation(botId: String): Conversation =
        request(
            path = "/api/conversations",
            method = HttpMethod.Post,
            bodyObj = CreateConversationRequest(botId),
            bodySerializer = CreateConversationRequest.serializer() as KSerializer<Any>,
            responseSerializer = Conversation.serializer()
        )

    suspend fun getMessages(conversationId: String): MessagesPage =
        request("/api/conversations/$conversationId/messages", responseSerializer = MessagesPage.serializer())

    @Suppress("UNCHECKED_CAST")
    suspend fun sendMessage(conversationId: String, content: String): SendMessageResponse =
        request(
            path = "/api/conversations/$conversationId/messages",
            method = HttpMethod.Post,
            bodyObj = SendMessageRequest(
                conversationId = conversationId,
                content = content,
                operationKey = UUID.randomUUID().toString()
            ),
            bodySerializer = SendMessageRequest.serializer() as KSerializer<Any>,
            responseSerializer = SendMessageResponse.serializer()
        )

    @Suppress("UNCHECKED_CAST")
    suspend fun regenerateMessage(conversationId: String, messageId: String): RegenerateMessageResponse =
        request(
            path = "/api/conversations/$conversationId/messages/$messageId/regenerate",
            method = HttpMethod.Post,
            bodyObj = RegenerateMessageRequest(operationKey = UUID.randomUUID().toString()),
            bodySerializer = RegenerateMessageRequest.serializer() as KSerializer<Any>,
            responseSerializer = RegenerateMessageResponse.serializer()
        )

    @Suppress("UNCHECKED_CAST")
    suspend fun editMessage(conversationId: String, messageId: String, content: String): SendMessageResponse =
        request(
            path = "/api/conversations/$conversationId/messages/$messageId",
            method = HttpMethod.Patch,
            bodyObj = EditMessageRequest(content = content, operationKey = UUID.randomUUID().toString()),
            bodySerializer = EditMessageRequest.serializer() as KSerializer<Any>,
            responseSerializer = SendMessageResponse.serializer()
        )

    suspend fun getStories(): List<StorySummary> =
        request("/api/stories", responseSerializer = serializer())

    suspend fun getMyStories(): List<StorySummary> =
        request("/api/stories/mine", responseSerializer = serializer())

    suspend fun createStorySession(storyId: String): StorySession =
        request("/api/stories/$storyId/sessions", method = HttpMethod.Post, responseSerializer = StorySession.serializer())

    suspend fun getStoryMessages(sessionId: String): List<StoryMessage> =
        request("/api/story-sessions/$sessionId/messages", responseSerializer = serializer())

    @Suppress("UNCHECKED_CAST")
    suspend fun sendStoryMessage(sessionId: String, content: String, showChoices: Boolean = true): SendStoryMessageResponse =
        request(
            path = "/api/story-sessions/$sessionId/messages",
            method = HttpMethod.Post,
            bodyObj = SendStoryMessageRequest(
                sessionId = sessionId,
                content = content,
                operationKey = UUID.randomUUID().toString(),
                showChoices = showChoices
            ),
            bodySerializer = SendStoryMessageRequest.serializer() as KSerializer<Any>,
            responseSerializer = SendStoryMessageResponse.serializer()
        )

    suspend fun getActiveTavernRooms(): List<ActiveTavernRoom> =
        request("/api/tavern/rooms/active", responseSerializer = ActiveTavernRoomsResponse.serializer()).rooms

    @Suppress("UNCHECKED_CAST")
    suspend fun joinTavernQueue(mode: String): TavernQueueResponse =
        request(
            path = "/api/tavern/queue",
            method = HttpMethod.Post,
            bodyObj = TavernQueueRequest(mode),
            bodySerializer = TavernQueueRequest.serializer() as KSerializer<Any>,
            responseSerializer = TavernQueueResponse.serializer()
        )

    suspend fun checkTavernQueue(queueId: String): TavernQueueResponse =
        request("/api/tavern/queue/$queueId", responseSerializer = TavernQueueResponse.serializer())

    suspend fun leaveTavernQueue() {
        try {
            request("/api/tavern/queue", method = HttpMethod.Delete, responseSerializer = JsonObject.serializer())
        } catch (_: Exception) {}
    }

    suspend fun getTavernRoom(roomId: String): TavernRoomData =
        request("/api/tavern/rooms/$roomId?_t=${System.currentTimeMillis()}", responseSerializer = TavernRoomData.serializer())

    @Suppress("UNCHECKED_CAST")
    suspend fun sendTavernMessage(roomId: String, content: String): SendTavernMessageResponse =
        request(
            path = "/api/tavern/rooms/$roomId/messages",
            method = HttpMethod.Post,
            bodyObj = SendTavernMessageRequest(content),
            bodySerializer = SendTavernMessageRequest.serializer() as KSerializer<Any>,
            responseSerializer = SendTavernMessageResponse.serializer()
        )
}

package ai.iskra.desktop.network

import ai.iskra.desktop.model.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import java.io.File
import java.util.UUID

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
    private val refreshMutex = Mutex()

    val baseUrl: String = loadBaseUrl()
    var session: StoredSession? = loadSession()
        private set

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(this@IskraApiClient.json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 60_000
        }
    }

    private fun loadBaseUrl(): String {
        return try {
            if (configFile.exists()) {
                val cfg = json.decodeFromString(ClientConfig.serializer(), configFile.readText())
                cfg.apiUrl.trimEnd('/').ifBlank { "https://bacand-iskra.onrender.com" }
            } else {
                "https://bacand-iskra.onrender.com"
            }
        } catch (_: Exception) {
            "https://bacand-iskra.onrender.com"
        }
    }

    private fun loadSession(): StoredSession? {
        return try {
            if (sessionFile.exists()) {
                val s = json.decodeFromString(StoredSession.serializer(), sessionFile.readText())
                if (s.accessToken.isNotBlank()) s else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

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

    fun updateThemePreferences(themeMode: String, paletteId: String) {
        val current = session ?: StoredSession(accessToken = "", themeMode = themeMode, paletteId = paletteId)
        saveSession(current.copy(themeMode = themeMode, paletteId = paletteId))
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
            executeRaw(path, method, bodyObj, bodySerializer, responseSerializer)
        } catch (e: IskraApiException) {
            if (e.status == 401 && session?.accessToken?.isNotBlank() == true && !path.startsWith("/api/auth/")) {
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
            if (e.status == 401) saveSession(null)
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
                    paletteId = session?.paletteId ?: "classic"
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
                    paletteId = session?.paletteId ?: "classic"
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
                    paletteId = session?.paletteId ?: "classic"
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
        saveSession(null)
    }

    suspend fun getBots(): List<ApiBot> =
        request("/api/bots", responseSerializer = serializer())

    suspend fun toggleBotLike(botId: String): LikeToggleResponse =
        request("/api/bots/$botId/like", method = HttpMethod.Post, responseSerializer = LikeToggleResponse.serializer())

    suspend fun getProfile(): UserProfile =
        request("/api/profile", responseSerializer = UserProfile.serializer())

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

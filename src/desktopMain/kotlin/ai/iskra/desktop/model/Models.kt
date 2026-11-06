package ai.iskra.desktop.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientConfig(
    val clientName: String = "Iskra Desktop Client",
    val clientVersion: String = "1.0.0",
    val targetPlatform: String = "desktop",
    val apiUrl: String = "https://bacand-iskra.onrender.com",
    val clientKey: String = ""
)

@Serializable
data class StoredSession(
    val accessToken: String,
    val refreshToken: String? = null,
    val userId: String? = null,
    val email: String? = null,
    val themeMode: String = "dark",
    val paletteId: String = "classic"
)

@Serializable
data class ApiErrorDetail(
    val code: String = "API_ERROR",
    val message: String = "Не удалось выполнить запрос"
)

@Serializable
data class ApiErrorEnvelope(
    val error: ApiErrorDetail? = null
)

@Serializable
data class AuthUser(
    val id: String,
    val email: String
)

@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String? = null
)

@Serializable
data class AuthResponse(
    val user: AuthUser,
    val session: AuthTokens? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class OtpRequest(
    val email: String,
    val purpose: String = "register",
    val captchaToken: String = "iskra-native-client",
    val displayName: String? = null,
    val password: String? = null,
    val isAdultConfirmed: Boolean = true,
    val termsAccepted: Boolean = true,
    val newsletterOptIn: Boolean = false
)

@Serializable
data class OtpVerifyRequest(
    val email: String,
    val purpose: String = "register",
    val code: String
)

@Serializable
data class PasswordResetRequest(
    val email: String,
    val captchaToken: String = "iskra-native-client"
)

@Serializable
data class PasswordResetConfirmRequest(
    val email: String,
    val code: String,
    val newPassword: String
)

@Serializable
data class RefreshRequest(
    val refreshToken: String?
)

@Serializable
data class LogoutRequest(
    val allDevices: Boolean = false
)

@Serializable
data class ApiBot(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("system_prompt") val systemPrompt: String? = null,
    val genre: String = "other",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("likes_count") val likesCount: Int = 0,
    @SerialName("is_liked") val isLiked: Boolean = false,
    @SerialName("author_id") val authorId: String = "",
    @SerialName("moderation_status") val moderationStatus: String = "approved"
)

@Serializable
data class LikeToggleResponse(
    val botId: String,
    val isLiked: Boolean,
    val likesCount: Int
)

@Serializable
data class Points(
    val remaining: Int = 0,
    val total: Int = 0,
    val resetsAt: String = ""
)

@Serializable
data class UserProfile(
    val id: String = "",
    val displayName: String = "",
    val email: String = "",
    val role: String? = null,
    val tier: String = "free",
    val avatarUrl: String? = null,
    val plan: String = "Free",
    val points: Points = Points(),
    val enhancedMemory: Boolean = false
)

@Serializable
data class Conversation(
    val id: String,
    @SerialName("bot_id") val botId: String,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("last_message_at") val lastMessageAt: String = ""
)

@Serializable
data class CreateConversationRequest(
    val botId: String
)

@Serializable
data class Message(
    val id: String,
    val conversationId: String = "",
    val sender: String,
    val content: String,
    val createdAt: String = "",
    @SerialName("created_at") val rawCreatedAt: String = "",
    val isEdited: Boolean = false,
    val versions: List<String> = emptyList(),
    val versionIndex: Int = 0
) {
    val timestamp: String get() = createdAt.ifBlank { rawCreatedAt }
}

@Serializable
data class MessagesPage(
    val items: List<Message> = emptyList(),
    val nextCursor: String? = null
)

@Serializable
data class SendMessageRequest(
    val conversationId: String,
    val content: String,
    val responseSize: String = "small",
    val operationKey: String
)

@Serializable
data class EditMessageRequest(
    val content: String,
    val responseSize: String = "small",
    val operationKey: String
)

@Serializable
data class RegenerateMessageRequest(
    val responseSize: String = "small",
    val operationKey: String
)

@Serializable
data class SendMessageResponse(
    val userMessage: Message,
    val assistantMessage: Message,
    val remaining: Int = 0
)

@Serializable
data class RegenerateMessageResponse(
    val assistantMessage: Message,
    val remaining: Int = 0
)

@Serializable
data class StorySummary(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    val genre: String = "other",
    val mode: String = "limited",
    @SerialName("likes_count") val likesCount: Int = 0
)

@Serializable
data class TavernPartner(
    val id: String = "",
    val displayName: String = "Игрок",
    val avatarUrl: String? = null
)

@Serializable
data class TavernLastMessage(
    val content: String = "",
    val createdAt: String = "",
    val senderId: String = ""
)

@Serializable
data class ActiveTavernRoom(
    val id: String,
    val mode: String = "duo",
    val partner: TavernPartner = TavernPartner(),
    val lastMessage: TavernLastMessage? = null,
    val unreadCount: Int = 0,
    val updatedAt: String = ""
)

@Serializable
data class ActiveTavernRoomsResponse(
    val rooms: List<ActiveTavernRoom> = emptyList()
)

@Serializable
data class TavernQueueRequest(
    val mode: String
)

@Serializable
data class TavernQueueResponse(
    val status: String,
    val roomId: String? = null,
    val queueId: String? = null
)

@Serializable
data class TavernMessage(
    val id: String,
    @SerialName("room_id") val roomId: String = "",
    @SerialName("sender_type") val senderType: String = "player",
    @SerialName("sender_id") val senderId: String = "",
    @SerialName("sender_name") val senderName: String = "Участник",
    val content: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class TavernRoomMeta(
    val id: String,
    val mode: String = "duo",
    val status: String = "active",
    val currentTurn: String = "player1"
)

@Serializable
data class TavernRoomData(
    val room: TavernRoomMeta,
    val messages: List<TavernMessage> = emptyList()
)

@Serializable
data class SendTavernMessageRequest(
    val content: String
)

@Serializable
data class SendTavernMessageResponse(
    val success: Boolean = true,
    val message: TavernMessage? = null,
    val botMessage: TavernMessage? = null
)

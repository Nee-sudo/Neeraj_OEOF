package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// ==========================================
// REQUEST / RESPONSE DTO SPECIFICATIONS
// ==========================================

data class HealthResponse(val status: String, val message: String?)

data class LoginRequest(val identifier: String, val passphrase: String)

data class ReactionRequest(val userId: String, val reactionType: String)

data class NotificationDTO(
    val id: String,
    val recipientId: String,
    val senderId: String,
    val type: String,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val createdAt: Long,
    val roomId: Int? = null,
    val postId: Int? = null,
    val userId: String? = null
)

// ==========================================
// RETROFIT API INTERFACE
// ==========================================

interface OneEarthApiService {

    @GET("api/health")
    suspend fun checkHealth(): HealthResponse

    // Authentication Endpoints
    @POST("api/auth/register")
    suspend fun registerUser(@Body user: UserEntity): UserEntity

    @POST("api/auth/login")
    suspend fun loginUser(@Body request: LoginRequest): UserEntity

    // Profile Endpoints
    @GET("api/users")
    suspend fun getAllUsers(): List<UserEntity>

    @GET("api/users/{userId}")
    suspend fun getUserProfile(@Path("userId") userId: String): UserEntity

    @PUT("api/users/{userId}")
    suspend fun updateUserProfile(@Path("userId") userId: String, @Body user: UserEntity): UserEntity

    @PUT("api/users/{userId}")
    suspend fun updateProfileFields(@Path("userId") userId: String, @Body fields: Map<String, String>): UserEntity

    // Post Endpoints
    @GET("api/posts")
    suspend fun getPosts(): List<PostEntity>

    @POST("api/posts")
    suspend fun createPost(@Body post: PostEntity): PostEntity

    @PUT("api/posts/{postId}")
    suspend fun editPost(@Path("postId") postId: Int, @Body body: Map<String, String>): PostEntity

    @PUT("api/posts/{postId}/react")
    suspend fun reactToPost(@Path("postId") postId: Int, @Body request: ReactionRequest): PostEntity

    @DELETE("api/posts/{postId}")
    suspend fun deletePost(@Path("postId") postId: Int): Map<String, Boolean>

    // Comment Endpoints
    @GET("api/posts/{postId}/comments")
    suspend fun getComments(@Path("postId") postId: Int): List<CommentEntity>

    @POST("api/posts/{postId}/comments")
    suspend fun addComment(@Path("postId") postId: Int, @Body comment: CommentEntity): CommentEntity

    // Messaging Endpoints
    @GET("api/chat/rooms")
    suspend fun getChatRooms(@Query("name") name: String?): List<ChatRoomEntity>

    @POST("api/chat/rooms")
    suspend fun createChatRoom(@Body room: ChatRoomEntity): ChatRoomEntity

    @GET("api/chat/rooms/{roomId}/messages")
    suspend fun getChatMessages(@Path("roomId") roomId: Int): List<ChatMessageEntity>

    @GET("api/chat/rooms/{roomId}/receipts")
    suspend fun getChatReceipts(@Path("roomId") roomId: Int): List<ChatReceiptEntity>

    @POST("api/chat/rooms/{roomId}/messages")
    suspend fun sendChatMessage(@Path("roomId") roomId: Int, @Body message: ChatMessageEntity): ChatMessageEntity

    @DELETE("api/chat/rooms/{roomId}")
    suspend fun deleteChatRoom(@Path("roomId") roomId: Int): Map<String, Boolean>

    @POST("api/chat/rooms/{roomId}/swap")
    suspend fun swapRoom(@Path("roomId") roomId: Int): ChatRoomEntity

    @POST("api/chat/rooms/{roomId}/archive")
    suspend fun archiveRoom(@Path("roomId") roomId: Int): ChatRoomEntity

    // Notification Endpoints
    @GET("api/notifications")
    suspend fun getNotifications(): List<NotificationDTO>

    @PUT("api/notifications/{id}/read")
    suspend fun markNotificationAsRead(@Path("id") id: String): NotificationDTO

    @POST("api/notifications/read-all")
    suspend fun markAllNotificationsAsRead(): Map<String, Boolean>

    // Profile visit & Friend request endpoints
    @POST("api/users/{userId}/visit")
    suspend fun recordProfileVisit(@Path("userId") userId: String): Map<String, Boolean>

    @POST("api/users/{userId}/friend-request")
    suspend fun sendFriendRequest(@Path("userId") userId: String): Map<String, Boolean>

    // ── Royal Profile Endpoints ──────────────────────────────
    @GET("api/monarchs/current")
    suspend fun getCurrentMonarch(): CurrentMonarchResponse

    @GET("api/royal-profiles/monarchs/{monarchId}")
    suspend fun getMonarchProfile(@Path("monarchId") monarchId: String): MonarchProfileResponse

    @GET("api/royal-profiles/monarchs/{monarchId}/throne-worthiness")
    suspend fun getThroneWorthiness(@Path("monarchId") monarchId: String): ThroneWorthinessResponse

    @GET("api/monarchs/{monarchId}/metrics")
    suspend fun getMonarchMetrics(@Path("monarchId") monarchId: String): MonarchMetricsResponse

    @GET("api/monarchs/{monarchId}/timeline")
    suspend fun getMonarchTimeline(@Path("monarchId") monarchId: String): List<TimelineMilestone>

    @GET("api/monarchs/hall-of-monarchs/detailed")
    suspend fun getHallOfMonarchsDetailed(): List<HallOfMonarchEntry>

    @GET("api/royal-profiles/members/{memberId}")
    suspend fun getRoyalMemberProfile(@Path("memberId") memberId: String): RoyalMemberProfileResponse

    @GET("api/monarchs/{monarchId}/council")
    suspend fun getRoyalCouncil(@Path("monarchId") monarchId: String): List<CouncilMemberDTO>

    @GET("api/monarchs/{monarchId}/decrees")
    suspend fun getRoyalDecrees(@Path("monarchId") monarchId: String): List<RoyalDecreeDTO>
}

// ==========================================
// CUSTOM MOSHI ADAPTERS FOR COMPATIBILITY
// ==========================================

private fun anyToInt(value: Any?, default: Int = 0): Int {
    if (value == null) return default
    if (value is Number) return value.toInt()
    if (value is String) return value.toIntOrNull() ?: default
    return default
}

class UserEntityAdapter {
    @FromJson
    fun fromJson(map: Map<String, Any?>): UserEntity {
        val id = (map["id"] ?: map["_id"] ?: map["email"] ?: "").toString()
        val name = map["name"]?.toString() ?: ""
        val username = map["username"]?.toString() ?: ""
        val email = map["email"]?.toString() ?: ""
        val dob = map["dob"]?.toString() ?: "1995-01-01"
        val territory = map["territory"]?.toString() ?: "Global"
        val flagEmoji = map["flagEmoji"]?.toString() ?: map["territory_flag"]?.toString() ?: "🌍"
        val gender = map["gender"]?.toString() ?: "Male"
        val currentRank = map["currentRank"]?.toString() ?: map["rank"]?.toString() ?: "Citizen"
        
        val knowledgeCredits = anyToInt(
            when {
                map.containsKey("knowledgeCredits") -> map["knowledgeCredits"]
                map.containsKey("knowledge_credits") -> map["knowledge_credits"]
                map.containsKey("kc") -> map["kc"]
                map.containsKey("knowledgeCreditsTotal") -> map["knowledgeCreditsTotal"]
                else -> map["knowledge_credits_total"]
            },
            0
        )
        val contributionCredits = anyToInt(
            when {
                map.containsKey("contributionCredits") -> map["contributionCredits"]
                map.containsKey("contribution_credits") -> map["contribution_credits"]
                map.containsKey("cc") -> map["cc"]
                map.containsKey("contributionCreditsTotal") -> map["contributionCreditsTotal"]
                else -> map["contribution_credits_total"]
            },
            0
        )
        val reputationScore = anyToInt(map["reputationScore"] ?: map["reputation"], 98)

        val personalityTraits = map["personalityTraits"]?.toString() ?: map["traits"]?.toString() ?: ""
        val bio = map["bio"]?.toString() ?: "Honorable citizen of the digital Empire."
        val followers = anyToInt(map["followers"], 120)
        val following = anyToInt(map["following"], 95)
        val onboardingCompleted = map["onboardingCompleted"] as? Boolean ?: false
        val citizenOathAccepted = map["citizenOathAccepted"] as? Boolean ?: false
        val isCandidate = map["isCandidate"] as? Boolean ?: false
        val campaignManifesto = map["campaignManifesto"]?.toString() ?: ""
        val campaignVision = map["campaignVision"]?.toString() ?: ""
        val votesCount = anyToInt(map["votesCount"], 0)
        val hasVoted = map["hasVoted"] as? Boolean ?: false
        val profilePhoto = map["profilePhoto"]?.toString() ?: ""
        val passphrase = map["passphrase"]?.toString() ?: "1234"
        val token = map["token"]?.toString()
        val fcmToken = map["fcmToken"]?.toString()

        return UserEntity(
            id = id,
            name = name,
            username = username,
            email = email,
            dob = dob,
            territory = territory,
            flagEmoji = flagEmoji,
            gender = gender,
            currentRank = currentRank,
            knowledgeCredits = knowledgeCredits,
            contributionCredits = contributionCredits,
            reputationScore = reputationScore,
            personalityTraits = personalityTraits,
            bio = bio,
            followers = followers,
            following = following,
            onboardingCompleted = onboardingCompleted,
            citizenOathAccepted = citizenOathAccepted,
            isCandidate = isCandidate,
            campaignManifesto = campaignManifesto,
            campaignVision = campaignVision,
            votesCount = votesCount,
            hasVoted = hasVoted,
            profilePhoto = profilePhoto,
            passphrase = passphrase,
            token = token,
            fcmToken = fcmToken
        )
    }

    @ToJson
    fun toJson(user: UserEntity): Map<String, Any?> {
        return mapOf(
            "id" to user.id,
            "name" to user.name,
            "username" to user.username,
            "email" to user.email,
            "dob" to user.dob,
            "territory" to user.territory,
            "flagEmoji" to user.flagEmoji,
            "gender" to user.gender,
            "currentRank" to user.currentRank,
            "knowledgeCredits" to user.knowledgeCredits,
            "knowledge_credits" to user.knowledgeCredits,
            "contributionCredits" to user.contributionCredits,
            "contribution_credits" to user.contributionCredits,
            "reputationScore" to user.reputationScore,
            "personalityTraits" to user.personalityTraits,
            "bio" to user.bio,
            "followers" to user.followers,
            "following" to user.following,
            "onboardingCompleted" to user.onboardingCompleted,
            "citizenOathAccepted" to user.citizenOathAccepted,
            "isCandidate" to user.isCandidate,
            "campaignManifesto" to user.campaignManifesto,
            "campaignVision" to user.campaignVision,
            "votesCount" to user.votesCount,
            "hasVoted" to user.hasVoted,
            "profilePhoto" to user.profilePhoto,
            "passphrase" to user.passphrase,
            "token" to user.token,
            "fcmToken" to user.fcmToken
        )
    }
}

class CurrentMonarchDataAdapter {
    @FromJson
    fun fromJson(map: Map<String, Any?>): CurrentMonarchData {
        val id = map["id"]?.toString() ?: ""
        val name = map["name"]?.toString() ?: ""
        val username = map["username"]?.toString() ?: ""
        val currentRank = map["currentRank"]?.toString() ?: "Citizen"
        val auraLevel = map["auraLevel"]?.toString() ?: "None"
        val bio = map["bio"]?.toString() ?: ""
        val profilePhoto = map["profilePhoto"]?.toString() ?: ""
        
        val knowledgeCredits = anyToInt(
            when {
                map.containsKey("knowledgeCredits") -> map["knowledgeCredits"]
                map.containsKey("knowledge_credits") -> map["knowledge_credits"]
                map.containsKey("kc") -> map["kc"]
                map.containsKey("knowledgeCreditsTotal") -> map["knowledgeCreditsTotal"]
                else -> map["knowledge_credits_total"]
            },
            0
        )
        val contributionCredits = anyToInt(
            when {
                map.containsKey("contributionCredits") -> map["contributionCredits"]
                map.containsKey("contribution_credits") -> map["contribution_credits"]
                map.containsKey("cc") -> map["cc"]
                map.containsKey("contributionCreditsTotal") -> map["contributionCreditsTotal"]
                else -> map["contribution_credits_total"]
            },
            0
        )

        return CurrentMonarchData(
            id = id,
            name = name,
            username = username,
            currentRank = currentRank,
            auraLevel = auraLevel,
            bio = bio,
            profilePhoto = profilePhoto,
            knowledgeCredits = knowledgeCredits,
            contributionCredits = contributionCredits
        )
    }

    @ToJson
    fun toJson(data: CurrentMonarchData): Map<String, Any?> {
        return mapOf(
            "id" to data.id,
            "name" to data.name,
            "username" to data.username,
            "currentRank" to data.currentRank,
            "auraLevel" to data.auraLevel,
            "bio" to data.bio,
            "profilePhoto" to data.profilePhoto,
            "knowledgeCredits" to data.knowledgeCredits,
            "knowledge_credits" to data.knowledgeCredits,
            "contributionCredits" to data.contributionCredits,
            "contribution_credits" to data.contributionCredits
        )
    }
}

class RoyalMemberProfileDTOAdapter {
    @FromJson
    fun fromJson(map: Map<String, Any?>): RoyalMemberProfileDTO {
        val citizenId = map["citizenId"]?.toString() ?: ""
        val rank = map["rank"]?.toString() ?: "Citizen"
        val auraLevel = map["auraLevel"]?.toString() ?: "None"
        val councilRole = map["councilRole"]?.toString()
        val achievements = (map["achievements"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

        val knowledgeCredits = anyToInt(
            when {
                map.containsKey("knowledgeCredits") -> map["knowledgeCredits"]
                map.containsKey("knowledge_credits") -> map["knowledge_credits"]
                map.containsKey("kc") -> map["kc"]
                map.containsKey("knowledgeCreditsTotal") -> map["knowledgeCreditsTotal"]
                else -> map["knowledge_credits_total"]
            },
            0
        )
        val contributionCredits = anyToInt(
            when {
                map.containsKey("contributionCredits") -> map["contributionCredits"]
                map.containsKey("contribution_credits") -> map["contribution_credits"]
                map.containsKey("cc") -> map["cc"]
                map.containsKey("contributionCreditsTotal") -> map["contributionCreditsTotal"]
                else -> map["contribution_credits_total"]
            },
            0
        )
        val mergedCreditsTotal = anyToInt(map["mergedCreditsTotal"], knowledgeCredits + contributionCredits)

        return RoyalMemberProfileDTO(
            citizenId = citizenId,
            rank = rank,
            auraLevel = auraLevel,
            knowledgeCredits = knowledgeCredits,
            contributionCredits = contributionCredits,
            mergedCreditsTotal = mergedCreditsTotal,
            councilRole = councilRole,
            achievements = achievements
        )
    }

    @ToJson
    fun toJson(dto: RoyalMemberProfileDTO): Map<String, Any?> {
        return mapOf(
            "citizenId" to dto.citizenId,
            "rank" to dto.rank,
            "auraLevel" to dto.auraLevel,
            "knowledgeCredits" to dto.knowledgeCredits,
            "knowledge_credits" to dto.knowledgeCredits,
            "contributionCredits" to dto.contributionCredits,
            "contribution_credits" to dto.contributionCredits,
            "mergedCreditsTotal" to dto.mergedCreditsTotal,
            "councilRole" to dto.councilRole,
            "achievements" to dto.achievements
        )
    }
}

// ==========================================
// API CLIENT BUILDER WITH DYNAMIC BASE URL
// ==========================================

object ApiClient {
    var authToken: String? = null
    private var currentUrl = "https://one-earth-dadyagc7bcc9hpcb.eastasia-01.azurewebsites.net/" // Live cloud development backend
    private var retrofit: Retrofit? = null
    private var itemService: OneEarthApiService? = null

    val moshi: Moshi = Moshi.Builder()
        .add(UserEntityAdapter())
        .add(CurrentMonarchDataAdapter())
        .add(RoyalMemberProfileDTOAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun updateBaseUrl(newUrl: String) {
        val normalizedUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        if (currentUrl != normalizedUrl) {
            currentUrl = normalizedUrl
            retrofit = null
            itemService = null
        }
    }

    fun getBaseUrl(): String = currentUrl

    private fun getRetrofitInstance(): Retrofit {
        return retrofit ?: synchronized(this) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val newRequest = if (!authToken.isNullOrBlank()) {
                        request.newBuilder()
                            .header("Authorization", "Bearer $authToken")
                            .build()
                    } else {
                        request
                    }
                    chain.proceed(newRequest)
                }
                .addInterceptor(logging)
                .build()

            val instance = Retrofit.Builder()
                .baseUrl(currentUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            retrofit = instance
            instance
        }
    }

    fun getService(): OneEarthApiService {
        return itemService ?: synchronized(this) {
            val service = getRetrofitInstance().create(OneEarthApiService::class.java)
            itemService = service
            service
        }
    }
}

// ── Royal Response DTO Classes ──────────────────────────
data class MonarchProfileResponse(
    val success: Boolean,
    val profile: MonarchProfileDTO?
)

data class MonarchProfileDTO(
    val monarchCitizenId: String,
    val title: String,           // "King" or "Queen"
    val reignStartDate: Long,
    val reignEndDate: Long?,
    val territoryId: String?,
    val throneWorthiness: ThroneWorthinessDTO,
    val legacyPoints: Int,
    val citizensHelped: Int,
    val policiesInitiated: Int,
    val territoriesInfluenced: Int,
    val decreesPosted: Int,
    val crownType: String,       // "Imperial Gold" or "Royal Diamond"
    val auraType: String,        // "Golden Sun" or "Diamond Moon"
    val approvalRating: Int,
    val royalCouncil: List<String>,
    val timeline: List<TimelineMilestone>
)

data class ThroneWorthinessResponse(
    val success: Boolean,
    val worthiness: ThroneWorthinessDTO?
)

data class ThroneWorthinessDTO(
    val percentage: Float,
    val knowledgeComponent: Float,
    val contributionComponent: Float,
    val reputationComponent: Float,
    val publicSupportComponent: Float
)

data class MonarchMetricsResponse(
    val legacyPoints: Int,
    val citizensHelped: Int,
    val policiesInitiated: Int,
    val territoriesInfluenced: Int,
    val decreesPosted: Int,
    val approvalRating: Int
)

data class TimelineMilestone(
    val rank: String,
    val milestone: String,
    val date: Long
)

data class HallOfMonarchEntry(
    val monarchCitizenId: String,
    val title: String,
    val name: String,
    val territory: String,
    val reignStartDate: Long,
    val reignEndDate: Long?,
    val legacyPoints: Int,
    val approvalRating: Int,
    val decreesPosted: Int
)

data class RoyalMemberProfileResponse(
    val success: Boolean,
    val profile: RoyalMemberProfileDTO?
)

data class RoyalMemberProfileDTO(
    val citizenId: String,
    val rank: String,
    val auraLevel: String,    // "None","Bronze","Silver","Golden","Imperial","Legendary"
    val knowledgeCredits: Int,
    val contributionCredits: Int,
    val mergedCreditsTotal: Int,
    val councilRole: String?,
    val achievements: List<String>
)

data class CouncilMemberDTO(
    val citizenId: String,
    val name: String,
    val role: String,
    val auraLevel: String,
    val appointedAt: Long
)

data class RoyalDecreeDTO(
    val id: String,
    val title: String,
    val content: String,
    val monarchId: String,
    val status: String,
    val territoryId: String?,
    val publishedAt: Long
)

data class CurrentMonarchResponse(
    val success: Boolean,
    val monarch: CurrentMonarchData?,
    val king: CurrentMonarchData? = null,
    val queen: CurrentMonarchData? = null
)

data class CurrentMonarchData(
    val id: String,
    val name: String,
    val username: String,
    val currentRank: String,
    val auraLevel: String,
    val bio: String,
    val profilePhoto: String,
    val knowledgeCredits: Int = 0,
    val contributionCredits: Int = 0
)

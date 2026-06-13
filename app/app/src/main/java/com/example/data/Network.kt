package com.example.data

import com.squareup.moshi.Moshi
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

data class UserDto(
    @com.squareup.moshi.Json(name = "_id") val mongoId: String? = null,
    val id: Any? = null,
    val userId: Any? = null,
    val name: String? = null,
    val username: String? = null,
    val email: String? = null,
    val dob: String? = null,
    val territory: String? = null,
    val flagEmoji: String? = null,
    val territory_flag: String? = null,
    val gender: String? = null,
    val currentRank: String? = null,
    val rank: String? = null,
    val knowledgeCredits: Int? = null,
    val knowledge_credits: Int? = null,
    val contributionCredits: Int? = null,
    val contribution_credits: Int? = null,
    val reputationScore: Int? = null,
    val reputation: Int? = null,
    val personalityTraits: String? = null,
    val traits: String? = null,
    val bio: String? = null,
    val followers: Int? = null,
    val following: Int? = null,
    val onboardingCompleted: Boolean? = null,
    val citizenOathAccepted: Boolean? = null,
    val isCandidate: Boolean? = null,
    val campaignManifesto: String? = null,
    val campaignVision: String? = null,
    val votesCount: Int? = null,
    val hasVoted: Boolean? = null,
    val profilePhoto: String? = null,
    val passphrase: String? = null,
    val passwordHash: String? = null,
    val password_hash: String? = null,
    val token: String? = null
) {
    fun toEntity(defaultId: String = ""): UserEntity {
        val idStr = anyToString(id)
        val userIdStr = anyToString(userId)
        
        // Resolve primary identifier fallback (using email/mongoId whenever 'id' is empty or 'me')
        val finalDocKey = if (!mongoId.isNullOrBlank()) mongoId 
            else if (!idStr.isNullOrBlank() && idStr != "me") idStr 
            else if (!userIdStr.isNullOrBlank() && userIdStr != "me") userIdStr 
            else defaultId
            
        val safeEmail = if (!email.isNullOrBlank()) email else (if (finalDocKey.contains("@")) finalDocKey else "")
        val safeId = if (finalDocKey == "me" && safeEmail.isNotBlank()) safeEmail else finalDocKey
        val fallbackName = if (safeEmail.isNotBlank()) safeEmail.split("@")[0].replaceFirstChar { it.uppercase() } else "Unspecified Citizen"

        val kb = knowledgeCredits ?: knowledge_credits ?: 0
        val cb = contributionCredits ?: contribution_credits ?: 0
        val resolvedRank = currentRank ?: rank ?: "Citizen"
        val score = reputationScore ?: reputation ?: 98
        val resolvedTraits = personalityTraits ?: traits ?: "Citizen"
        val resolvedPass = passphrase ?: passwordHash ?: password_hash ?: "1234"
        val resolvedFlag = flagEmoji ?: territory_flag ?: "🌍"
        
        return UserEntity(
            id = safeId,
            name = if (!name.isNullOrBlank()) name else fallbackName,
            username = if (!username.isNullOrBlank()) username else (if (safeEmail.isNotBlank()) "@" + safeEmail.split("@")[0] else "@citizen"),
            email = safeEmail,
            dob = dob ?: "1995-01-01",
            territory = territory ?: "Global",
            flagEmoji = resolvedFlag,
            gender = gender ?: "Male",
            currentRank = resolvedRank,
            knowledgeCredits = kb,
            contributionCredits = cb,
            reputationScore = score,
            personalityTraits = resolvedTraits,
            bio = bio ?: "Honorable citizen of the digital Empire. Committed to service and knowledge.",
            followers = followers ?: 120,
            following = following ?: 95,
            onboardingCompleted = onboardingCompleted ?: false,
            citizenOathAccepted = citizenOathAccepted ?: false,
            isCandidate = isCandidate ?: false,
            campaignManifesto = campaignManifesto ?: "",
            campaignVision = campaignVision ?: "",
            votesCount = votesCount ?: 0,
            hasVoted = hasVoted ?: false,
            profilePhoto = profilePhoto ?: "",
            passphrase = resolvedPass,
            token = token
        )
    }

    private fun anyToString(value: Any?): String? {
        if (value == null) return null
        if (value is Double) {
            if (value % 1 == 0.0) {
                return value.toLong().toString()
            }
            return value.toString()
        }
        return value.toString()
    }
}

// ==========================================
// RETROFIT API INTERFACE
// ==========================================

interface OneEarthApiService {

    @GET("api/health")
    suspend fun checkHealth(): HealthResponse

    // Authentication Endpoints
    @POST("api/auth/register")
    suspend fun registerUser(@Body user: UserEntity): UserDto

    @POST("api/auth/login")
    suspend fun loginUser(@Body request: LoginRequest): UserDto

    // Profile Endpoints
    @GET("api/users")
    suspend fun getAllUsers(): List<UserDto>

    @GET("api/users/{userId}")
    suspend fun getUserProfile(@Path("userId") userId: String): UserDto

    @PUT("api/users/{userId}")
    suspend fun updateUserProfile(@Path("userId") userId: String, @Body user: UserEntity): UserDto


    // Post Endpoints
    @GET("api/posts")
    suspend fun getPosts(): List<PostEntity>

    @POST("api/posts")
    suspend fun createPost(@Body post: PostEntity): PostEntity

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

    @POST("api/chat/rooms/{roomId}/messages")
    suspend fun sendChatMessage(@Path("roomId") roomId: Int, @Body message: ChatMessageEntity): ChatMessageEntity

    @DELETE("api/chat/rooms/{roomId}")
    suspend fun deleteChatRoom(@Path("roomId") roomId: Int): Map<String, Boolean>

    @POST("api/chat/rooms/{roomId}/swap")
    suspend fun swapRoom(@Path("roomId") roomId: Int): ChatRoomEntity

    @POST("api/chat/rooms/{roomId}/archive")
    suspend fun archiveRoom(@Path("roomId") roomId: Int): ChatRoomEntity
}

// ==========================================
// API CLIENT BUILDER WITH DYNAMIC BASE URL
// ==========================================

object ApiClient {
    var authToken: String? = null
    private var currentUrl = "https://b768-2409-40e3-1ec-5736-dc6b-6c8f-a26-1f95.ngrok-free.app/" // Live cloud development backend
    private var retrofit: Retrofit? = null
    private var itemService: OneEarthApiService? = null

    val moshi: Moshi = Moshi.Builder()
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
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val builder = request.newBuilder()
                        .header("ngrok-skip-browser-warning", "true")
                        .header("User-Agent", "AppletNetworkInterceptor")
                    
                    if (!authToken.isNullOrBlank()) {
                        builder.header("Authorization", "Bearer $authToken")
                    }
                    chain.proceed(builder.build())
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

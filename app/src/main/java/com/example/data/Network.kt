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
    private var currentUrl = "https://one-earth-dadyagc7bcc9hpcb.eastasia-01.azurewebsites.net/" // Live cloud development backend
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

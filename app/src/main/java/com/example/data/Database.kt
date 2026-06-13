package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ==========================================
// ROOM ENTITIES
// ==========================================

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String, // "me" for the current logged-in user
    val name: String,
    val username: String,
    val email: String,
    val dob: String,
    val territory: String,
    val flagEmoji: String,
    val gender: String = "Male", // "Male", "Female", etc.
    val currentRank: String = "Citizen",
    val knowledgeCredits: Int = 0,
    val contributionCredits: Int = 0,
    val reputationScore: Int = 98,
    val personalityTraits: String = "", // Comma-separated (exactly 5 traits)
    val bio: String = "Honorable citizen of the digital Empire. Committed to service and knowledge.",
    val followers: Int = 120,
    val following: Int = 95,
    val onboardingCompleted: Boolean = false,
    val citizenOathAccepted: Boolean = false,
    val isCandidate: Boolean = false,
    val campaignManifesto: String = "",
    val campaignVision: String = "",
    val votesCount: Int = 0,
    val hasVoted: Boolean = false,
    val profilePhoto: String = "",
    val passphrase: String = "1234",
    val token: String? = null
)

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val authorId: String,
    val authorName: String,
    val authorUsername: String,
    val authorRank: String,
    val authorTerritory: String,
    val authorFlag: String,
    val content: String,
    val category: String, // "Inquiry", "Article", "Debate"
    val timestamp: Long = System.currentTimeMillis(),
    val knowledgeValue: Int = 0, // Wise clicks
    val contributionProof: Int = 0, // Helpful clicks
    val reputationImpact: Int = 100, // Reputation score of author
    val reactedWiseUsers: String = "", // Comma-separated list of reactor IDs
    val reactedHelpfulUsers: String = "", // Comma-separated list of reactor IDs
    val reactedInspiringUsers: String = "" // Added to rep
)

@Entity(tableName = "comments")
data class CommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val postId: Int,
    val authorName: String,
    val authorFlag: String,
    val authorRank: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_rooms")
data class ChatRoomEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val roomName: String,
    val participantName: String,
    val participantFlag: String,
    val participantRank: String,
    val participantTerritory: String,
    val lastMessage: String,
    val lastMessageTime: Long = System.currentTimeMillis(),
    val isActive: Boolean = true, // Max 3 active direct slots
    val isWaiting: Boolean = false, // If not active, it's in the waiting queue
    val isDeleted: Boolean = false
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val roomId: Int,
    val senderId: String, // "me" or participant
    val senderName: String,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Sent" // "Sent", "Delivered", or "Read"
)

@Entity(tableName = "profile_visitors")
data class ProfileVisitorEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hostUserId: String = "me",
    val visitorName: String,
    val visitorTerritory: String,
    val visitorFlag: String,
    val visitorRank: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "imperial_missions")
data class ImperialMissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val targetMetric: String, // "Knowledge Points", "Trees Planted", "Students Mentored"
    val targetValue: Int,
    val currentProgress: Int,
    val isActive: Boolean = true
)

@Entity(tableName = "hall_of_legends")
data class HallOfLegendsEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val role: String,
    val details: String,
    val achievement: String
)

// ==========================================
// DATA ACCESS OBJECTS (DAOs)
// ==========================================

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun getUserFlow(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:identifier) OR LOWER(username) = LOWER(:identifier) OR LOWER(username) = '@' || LOWER(:identifier) LIMIT 1")
    suspend fun getUserByEmailOrUsername(identifier: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUserById(id: String)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    @Query("DELETE FROM users WHERE id != 'me'")
    suspend fun deleteAllNonMeUsers()

    @Query("SELECT * FROM users WHERE isCandidate = 1 AND id != 'me' AND id NOT LIKE 'user_%' ORDER BY votesCount DESC, knowledgeCredits DESC")
    fun getCandidatesFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id != 'me' AND id NOT LIKE 'user_%' ORDER BY (knowledgeCredits + contributionCredits) DESC")
    fun getLeaderboardUsersFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id != 'me' AND id NOT LIKE 'user_%' ORDER BY name ASC")
    fun getAllFriendsFlow(): Flow<List<UserEntity>>
}

@Dao
interface PostDao {
    @Query("SELECT * FROM posts ORDER BY (knowledgeValue * 1.5 + contributionProof * 1.2 + reputationImpact) DESC, timestamp DESC")
    fun getRankedFeedFlow(): Flow<List<PostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: PostEntity)

    @Update
    suspend fun updatePost(post: PostEntity)

    @Delete
    suspend fun deletePost(post: PostEntity)

    @Query("SELECT * FROM posts WHERE id = :postId LIMIT 1")
    suspend fun getPostById(postId: Int): PostEntity?

    @Query("SELECT * FROM posts WHERE authorId = :authorId AND content = :content LIMIT 1")
    suspend fun getPostByAuthorAndContent(authorId: String, content: String): PostEntity?

    @Query("DELETE FROM posts")
    suspend fun deleteAllPosts()
}

@Dao
interface CommentDao {
    @Query("SELECT * FROM comments WHERE postId = :postId ORDER BY timestamp ASC")
    fun getCommentsForPostFlow(postId: Int): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments")
    fun getAllCommentsFlow(): Flow<List<CommentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommentEntity)

    @Query("SELECT * FROM comments WHERE postId = :postId AND authorName = :authorName AND content = :content LIMIT 1")
    suspend fun getCommentByPostAuthorAndContent(postId: Int, authorName: String, content: String): CommentEntity?

    @Delete
    suspend fun deleteComment(comment: CommentEntity)

    @Query("DELETE FROM comments")
    suspend fun deleteAllComments()
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_rooms WHERE isDeleted = 0 ORDER BY lastMessageTime DESC")
    fun getAllRoomsFlow(): Flow<List<ChatRoomEntity>>

    @Query("SELECT * FROM chat_rooms WHERE isActive = 1 AND isDeleted = 0 ORDER BY lastMessageTime DESC")
    fun getActiveRoomsFlow(): Flow<List<ChatRoomEntity>>

    @Query("SELECT * FROM chat_rooms WHERE isWaiting = 1 AND isDeleted = 0 ORDER BY lastMessageTime DESC")
    fun getWaitingRoomsFlow(): Flow<List<ChatRoomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: ChatRoomEntity): Long

    @Update
    suspend fun updateRoom(room: ChatRoomEntity)

    @Query("SELECT * FROM chat_rooms WHERE id = :roomId LIMIT 1")
    suspend fun getRoomById(roomId: Int): ChatRoomEntity?

    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId ORDER BY timestamp ASC")
    fun getMessagesForRoomFlow(roomId: Int): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE roomId = :roomId AND senderId = :senderId AND messageText = :messageText LIMIT 1")
    suspend fun getMessageByRoomSenderAndText(roomId: Int, senderId: String, messageText: String): ChatMessageEntity?

    @Delete
    suspend fun deleteMessage(message: ChatMessageEntity)

    @Query("UPDATE chat_rooms SET isDeleted = 1 WHERE id = :roomId")
    suspend fun markRoomAsDeleted(roomId: Int)

    @Query("DELETE FROM chat_rooms WHERE id = :roomId")
    suspend fun deleteRoomFromDb(roomId: Int)

    @Query("DELETE FROM chat_messages WHERE roomId = :roomId")
    suspend fun deleteMessagesForRoom(roomId: Int)

    @Query("DELETE FROM chat_rooms")
    suspend fun deleteAllRooms()

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllMessages()
}

@Dao
interface ProfileVisitorDao {
    @Query("SELECT * FROM profile_visitors ORDER BY timestamp DESC")
    fun getVisitorsFlow(): Flow<List<ProfileVisitorEntity>>

    @Query("SELECT * FROM profile_visitors WHERE hostUserId = :hostUserId AND visitorName = :visitorName LIMIT 1")
    suspend fun getVisitorRecord(hostUserId: String, visitorName: String): ProfileVisitorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisitor(visitor: ProfileVisitorEntity)

    @Query("DELETE FROM profile_visitors")
    suspend fun deleteAllVisitors()
}

@Dao
interface MissionDao {
    @Query("SELECT * FROM imperial_missions WHERE isActive = 1")
    fun getActiveMissionsFlow(): Flow<List<ImperialMissionEntity>>

    @Update
    suspend fun updateMission(mission: ImperialMissionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMission(mission: ImperialMissionEntity)

    @Query("DELETE FROM imperial_missions")
    suspend fun deleteAllMissions()
}

@Dao
interface HallOfLegendsDao {
    @Query("SELECT * FROM hall_of_legends")
    fun getAllLegendsFlow(): Flow<List<HallOfLegendsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLegend(legend: HallOfLegendsEntity)

    @Query("DELETE FROM hall_of_legends")
    suspend fun deleteAllLegends()
}

// ==========================================
// APP DATABASE SPECIFICATION
// ==========================================

@Database(
    entities = [
        UserEntity::class,
        PostEntity::class,
        CommentEntity::class,
        ChatRoomEntity::class,
        ChatMessageEntity::class,
        ProfileVisitorEntity::class,
        ImperialMissionEntity::class,
        HallOfLegendsEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun postDao(): PostDao
    abstract fun commentDao(): CommentDao
    abstract fun chatDao(): ChatDao
    abstract fun visitorDao(): ProfileVisitorDao
    abstract fun missionDao(): MissionDao
    abstract fun legendsDao(): HallOfLegendsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "one_earth_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

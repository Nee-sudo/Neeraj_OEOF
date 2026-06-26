package com.example.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

// Screen Navigation Enum
enum class Screen {
    Splash,
    OnboardingCarousel,
    Registration,
    Login,
    TerritorySelection,
    PersonalitySetup,
    CitizenOath,
    MainDashboard
}

// Sub-screens for the Main App using Navigation Tabs
enum class DashboardTab {
    PublicSquare,
    FindFriends,
    Messaging,
    ProfileAndElections,
    MissionsAndLegends
}

data class InAppNotification(
    val roomId: Int,
    val senderName: String,
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis()
)

class AppViewModel(application: Application) : AndroidViewModel(application), SocketManager.SocketEventListener {

    private val db = AppDatabase.getDatabase(application)
    private val userDao = db.userDao()
    private val postDao = db.postDao()
    private val commentDao = db.commentDao()
    private val chatDao = db.chatDao()
    private val visitorDao = db.visitorDao()
    private val missionDao = db.missionDao()
    private val legendsDao = db.legendsDao()
    private val notificationDao = db.notificationDao()

    private val appStartedTime = System.currentTimeMillis()
    private val shownNotificationKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    // Screen State
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Splash)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _currentTab = MutableStateFlow<DashboardTab>(DashboardTab.PublicSquare)
    val currentTab: StateFlow<DashboardTab> = _currentTab.asStateFlow()

    private val _missionsSubTab = MutableStateFlow(0) // 0: Missions & Legends, 1: Knowledge Arena
    val missionsSubTab: StateFlow<Int> = _missionsSubTab.asStateFlow()

    fun selectMissionsSubTab(idx: Int) {
        _missionsSubTab.value = idx
    }

    private val _showEditProfileDialog = MutableStateFlow(false)
    val showEditProfileDialog: StateFlow<Boolean> = _showEditProfileDialog.asStateFlow()

    fun setShowEditProfileDialog(show: Boolean) {
        _showEditProfileDialog.value = show
    }

    // Logged in user Flow
    val currentUserFlow: StateFlow<UserEntity?> = userDao.getUserFlow("me")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allUsers: StateFlow<List<UserEntity>> = userDao.getAllFriendsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Public Square Lists
    val rankedPosts: StateFlow<List<PostEntity>> = postDao.getRankedFeedFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active & Waiting Chats
    val activeChatRooms: StateFlow<List<ChatRoomEntity>> = chatDao.getActiveRoomsFlow()
        .combine(currentUserFlow) { rooms, user ->
            val myName = user?.name ?: "Me"
            rooms.map { mapRoomForUser(it, myName) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val waitingChatRooms: StateFlow<List<ChatRoomEntity>> = chatDao.getWaitingRoomsFlow()
        .combine(currentUserFlow) { rooms, user ->
            val myName = user?.name ?: "Me"
            rooms.map { mapRoomForUser(it, myName) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Profile Visitors
    val profileVisitors: StateFlow<List<ProfileVisitorEntity>> = visitorDao.getVisitorsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Notifications state flows
    val allNotifications: StateFlow<List<NotificationEntity>> = notificationDao.getNotificationsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationsCount: StateFlow<Int> = notificationDao.getNotificationsFlow()
        .map { list -> list.count { !it.isRead } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Candidates Flow
    val electionCandidates: StateFlow<List<UserEntity>> = userDao.getCandidatesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Full Leaderboard users Flow
    val leaderboardUsers: StateFlow<List<UserEntity>> = userDao.getLeaderboardUsersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentKing = MutableStateFlow<UserEntity?>(null)
    val currentKing: StateFlow<UserEntity?> = _currentKing.asStateFlow()

    private val _currentQueen = MutableStateFlow<UserEntity?>(null)
    val currentQueen: StateFlow<UserEntity?> = _currentQueen.asStateFlow()

    // All registered users except "me" for Find Friends
    val allFriends: StateFlow<List<UserEntity>> = userDao.getAllFriendsFlow()
        .combine(currentUserFlow) { friends, me ->
            android.util.Log.d("FindFriendsDiagnostic", "--- START FIND FRIENDS FILTERING ---")
            android.util.Log.d("FindFriendsDiagnostic", "Total users in Room before filtering: ${friends.size}")

            val myEmail = me?.email?.lowercase()?.trim() ?: ""
            val myUsername = me?.username?.lowercase()?.trim()?.removePrefix("@") ?: ""

            val filtered = mutableListOf<UserEntity>()

            for (friend in friends) {
                val friendId = friend.id
                val friendName = friend.name
                val friendEmail = friend.email.lowercase().trim()
                val friendUsername = friend.username.lowercase().trim().removePrefix("@")

                var removeReason: String? = null

                if (friendId == "me") {
                    removeReason = "Session record (current user ID 'me')"
                } else if (friendName.isBlank() || friendUsername.isBlank() || friendEmail.isBlank()) {
                    removeReason = "Malformed document (missing essential field)"
                } else if (myEmail.isNotBlank() && friendEmail == myEmail) {
                    removeReason = "Duplicate session record (matches active citizen email '$myEmail')"
                } else if (myUsername.isNotBlank() && friendUsername == myUsername) {
                    removeReason = "Duplicate session record (matches active citizen username '$myUsername')"
                } else if (friendId in listOf("gandhi_avatar", "clara_nobel", "kenya_leader", "test@oneearth.io") || friendEmail.endsWith("@oneearth.io")) {
                    removeReason = "Placeholder/demo user / administrative seed figure"
                }

                if (removeReason != null) {
                    android.util.Log.d(
                        "FindFriendsDiagnostic",
                        "Removed - User ID: $friendId | User Name: $friendName | Reason: $removeReason"
                    )
                } else {
                    filtered.add(friend)
                    android.util.Log.d(
                        "FindFriendsDiagnostic",
                        "Retained - User ID: $friendId | User Name: $friendName"
                    )
                }
            }

            android.util.Log.d("FindFriendsDiagnostic", "Total users after filtering: ${filtered.size}")
            android.util.Log.d("FindFriendsDiagnostic", "--- END FIND FRIENDS FILTERING ---")
            filtered
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Mission Flow
    val activeMissions: StateFlow<List<ImperialMissionEntity>> = missionDao.getActiveMissionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Hall of Legends Flow
    val hallOfLegends: StateFlow<List<HallOfLegendsEntity>> = legendsDao.getAllLegendsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected profile detail display state
    private val _selectedProfileUser = MutableStateFlow<UserEntity?>(null)
    val selectedProfileUser: StateFlow<UserEntity?> = _selectedProfileUser.asStateFlow()

    // Selected Chat Room and its messages state
    private val _selectedRoomId = MutableStateFlow<Int?>(null)
    val selectedRoomId: StateFlow<Int?> = _selectedRoomId.asStateFlow()

    private val _typingUsers = MutableStateFlow<Map<Int, String?>>(emptyMap())
    val typingUsers: StateFlow<Map<Int, String?>> = _typingUsers.asStateFlow()

    private var notificationJob: Job? = null

    private val _activeNotification = MutableStateFlow<InAppNotification?>(null)
    val activeNotification: StateFlow<InAppNotification?> = _activeNotification.asStateFlow()

    private var lastMonarchFetchTime: Long = 0L

    val showNotificationsDialogFlow = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)

    fun showInAppNotification(roomId: Int, senderName: String, messageText: String) {
        notificationJob?.cancel()
        _activeNotification.value = InAppNotification(
            roomId = roomId,
            senderName = senderName,
            messageText = messageText
        )
        notificationJob = viewModelScope.launch {
            delay(4000) // Instagram style (4 seconds duration)
            _activeNotification.value = null
        }
    }

    fun dismissNotification() {
        notificationJob?.cancel()
        _activeNotification.value = null
    }

    private val _chatLimit = MutableStateFlow(40)

    fun loadMoreMessages() {
        android.util.Log.d("AppViewModel", "loadMoreMessages is called, increasing chat limit...")
        _chatLimit.value += 30
    }

    val currentChatMessages: StateFlow<List<ChatMessageEntity>> = combine(_selectedRoomId, _chatLimit) { id, limit ->
        Pair(id, limit)
    }
        .flatMapLatest { (id, limit) ->
            if (id != null) {
                combine(
                    chatDao.getLatestMessagesFlow(id, limit),
                    chatDao.getReceiptsForRoomFlow(id),
                    currentUserFlow
                ) { messages, receipts, user ->
                    val myName = user?.name ?: "Me"
                    val partnerReadReceipts = receipts.filter { it.type == "read" && !it.senderName.equals(myName, ignoreCase = true) && !it.senderName.equals("Me", ignoreCase = true) }
                    val partnerDeliveredReceipts = receipts.filter { it.type == "delivered" && !it.senderName.equals(myName, ignoreCase = true) && !it.senderName.equals("Me", ignoreCase = true) }
                    val maxPartnerReadTime = partnerReadReceipts.maxOfOrNull { it.timestamp } ?: 0L
                    val maxPartnerDeliveredTime = partnerDeliveredReceipts.maxOfOrNull { it.timestamp } ?: 0L
                    
                    val filteredMessages = messages.filter { !it.senderId.startsWith("read_receipt") && !it.senderId.startsWith("delivered_receipt") }
                    
                    val normalizedMessages = filteredMessages.map { msg ->
                        val isMe = msg.senderName.equals(myName, ignoreCase = true) || 
                                   msg.senderName.equals("Me", ignoreCase = true) ||
                                   msg.senderId == "me" ||
                                   msg.senderId.equals("me", ignoreCase = true) ||
                                   (user != null && msg.senderId.equals(user.email, ignoreCase = true))
                        val mappedSenderId = if (isMe) "me" else "other"
                        val mappedSenderName = if (isMe) "Me" else msg.senderName
                        val evaluatedStatus = if (isMe) {
                            when {
                                msg.timestamp <= maxPartnerReadTime -> "Read"
                                msg.timestamp <= maxPartnerDeliveredTime -> "Delivered"
                                else -> "Sent"
                            }
                        } else {
                            "Read"
                        }
                        msg.copy(
                            senderId = mappedSenderId,
                            senderName = mappedSenderName,
                            status = evaluatedStatus
                        )
                    }

                    val deduplicatedList = mutableListOf<ChatMessageEntity>()
                    normalizedMessages.sortedBy { it.timestamp }.forEach { msg ->
                        val duplicateIndex = deduplicatedList.indexOfFirst { existing ->
                            existing.senderId == msg.senderId &&
                            existing.messageText.trim().equals(msg.messageText.trim(), ignoreCase = true) &&
                            kotlin.math.abs(existing.timestamp - msg.timestamp) < 15000
                        }
                        if (duplicateIndex != -1) {
                            val existing = deduplicatedList[duplicateIndex]
                            if (msg.id > existing.id) {
                                deduplicatedList[duplicateIndex] = msg
                            }
                        } else {
                            deduplicatedList.add(msg)
                        }
                    }
                    deduplicatedList
                }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Toast or State Alerts
    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    // Daily Welcome Dialog state
    private val _showDailyWelcome = MutableStateFlow(false)
    val showDailyWelcome: StateFlow<Boolean> = _showDailyWelcome.asStateFlow()

    // Exit Summary Dialog state
    private val _showExitSummary = MutableStateFlow(false)
    val showExitSummary: StateFlow<Boolean> = _showExitSummary.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("one_earth_settings", android.content.Context.MODE_PRIVATE)

    // Foreground lifecycle flag
    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    fun setAppInForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
        if (inForeground) {
            SocketManager.connect(ApiClient.getBaseUrl())
        } else {
            SocketManager.disconnect()
        }
    }

    // Theme toggle state
    private val _themeMode = MutableStateFlow(
        try {
            AppThemeMode.valueOf(sharedPrefs.getString("theme_mode", AppThemeMode.LIGHT.name) ?: AppThemeMode.LIGHT.name)
        } catch (e: Exception) {
            AppThemeMode.LIGHT
        }
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(
        _themeMode.value != AppThemeMode.MINIMALIST_SLATE_LIGHT && _themeMode.value != AppThemeMode.LIGHT
    )
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun toggleTheme() {
        val nextMode = when (_themeMode.value) {
            AppThemeMode.DARK -> AppThemeMode.TWILIGHT_DUSK
            AppThemeMode.TWILIGHT_DUSK -> AppThemeMode.SPACE_ABYSS_DARK
            AppThemeMode.SPACE_ABYSS_DARK -> AppThemeMode.MINIMALIST_SLATE_LIGHT
            AppThemeMode.MINIMALIST_SLATE_LIGHT -> AppThemeMode.LIGHT
            AppThemeMode.LIGHT -> AppThemeMode.DARK
        }
        _themeMode.value = nextMode
        _isDarkTheme.value = nextMode != AppThemeMode.MINIMALIST_SLATE_LIGHT && nextMode != AppThemeMode.LIGHT
        sharedPrefs.edit().putString("theme_mode", nextMode.name).apply()
    }

    // API/Backend states and dynamic controls
    private val _isBackendConnected = MutableStateFlow(false)
    val isBackendConnected: StateFlow<Boolean> = _isBackendConnected.asStateFlow()

    private val _isConnectingToBackend = MutableStateFlow(false)
    val isConnectingToBackend: StateFlow<Boolean> = _isConnectingToBackend.asStateFlow()

    private val _backendBaseUrl = MutableStateFlow(ApiClient.getBaseUrl())
    val backendBaseUrl: StateFlow<String> = _backendBaseUrl.asStateFlow()

    private val _showSyncSettingsDialog = MutableStateFlow(false)
    val showSyncSettingsDialog: StateFlow<Boolean> = _showSyncSettingsDialog.asStateFlow()

    fun setShowSyncSettingsDialog(show: Boolean) {
        _showSyncSettingsDialog.value = show
    }

    fun updateBackendUrl(newUrl: String, onSuccess: () -> Unit = {}) {
        var cleanUrl = newUrl.trim()
        if (cleanUrl.isNotBlank()) {
            // Autocomplete scheme if missing (e.g. standard user typing raw hostnames)
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                val isLocal = cleanUrl.contains("192.168.") || 
                              cleanUrl.contains("10.") || 
                              cleanUrl.contains("172.") || 
                              cleanUrl.contains("localhost") || 
                              cleanUrl.contains("127.0.0.1") ||
                              cleanUrl.contains(":") // contains a port specification like ':4000'
                if (isLocal) {
                    cleanUrl = "http://$cleanUrl"
                } else {
                    cleanUrl = "https://$cleanUrl"
                }
            }
        }
        if (cleanUrl.contains("azurewebsite.net") && !cleanUrl.contains("azurewebsites.net")) {
            cleanUrl = cleanUrl.replace("azurewebsite.net", "azurewebsites.net")
        }
        ApiClient.updateBaseUrl(cleanUrl)
        _backendBaseUrl.value = ApiClient.getBaseUrl()
        sharedPrefs.edit().putString("backend_url", ApiClient.getBaseUrl()).apply()
        
        // Update Socket.IO connection
        SocketManager.connect(ApiClient.getBaseUrl())

        viewModelScope.launch {
            _isConnectingToBackend.value = true
            _toastMessage.emit("Connecting to Cosmos Network at: ${ApiClient.getBaseUrl()}...")
            try {
                val response = ApiClient.getService().checkHealth()
                val success = (response.status == "ok" || response.status.isNotBlank())
                _isBackendConnected.value = success
                if (success) {
                    _toastMessage.emit("Successfully connected to Cosmos Network!")
                    syncAllWithBackend()
                    onSuccess()
                } else {
                    _toastMessage.emit("Connection failed: Server responded with status '${response.status}'")
                }
            } catch (e: Exception) {
                _isBackendConnected.value = false
                val msg = e.localizedMessage ?: "Unknown network failure"
                if (msg.contains("JsonReader") || msg.contains("lenient") || msg.contains("malformed", ignoreCase = true) || msg.contains("Unexpected char", ignoreCase = true)) {
                    _toastMessage.emit("Sync Warning: The server returned HTML/Text instead of JSON. This usually indicates a preview cookie-prompt, a reverse proxy redirect, or an incorrect API url. Please verify your custom backend is active at: ${ApiClient.getBaseUrl()}")
                } else {
                    _toastMessage.emit("Connection failed: $msg")
                }
            } finally {
                _isConnectingToBackend.value = false
            }
        }
    }

    // Setup Form States
    var tempName = ""
    var tempUsername = ""
    var tempEmail = ""
    var tempDob = ""
    var tempPassword = ""
    var tempGender = "Male" // "Male", "Female", "Prefer Not to Say", etc.
    var selectedTerritory = ""
    var selectedFlag = ""
    val selectedTraitsList = mutableStateListOf<String>()

    // Selected comments mapping
    private val _selectedCommentsPostId = MutableStateFlow<Int?>(null)
    val selectedCommentsPostId: StateFlow<Int?> = _selectedCommentsPostId.asStateFlow()

    val currentPostComments: StateFlow<List<CommentEntity>> = _selectedCommentsPostId
        .flatMapLatest { postId ->
            if (postId != null) commentDao.getCommentsForPostFlow(postId)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val postCommentCounts: StateFlow<Map<Int, Int>> = commentDao.getAllCommentsFlow()
        .map { comments ->
            comments.groupBy { it.postId }.mapValues { it.value.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        // Load persistent backend URL if set
        val persistedUrl = sharedPrefs.getString("backend_url", null)
        if (!persistedUrl.isNullOrBlank()) {
            val correctedUrl = if (persistedUrl.contains("azurewebsite.net") && !persistedUrl.contains("azurewebsites.net")) {
                persistedUrl.replace("azurewebsite.net", "azurewebsites.net")
            } else {
                persistedUrl
            }
            ApiClient.updateBaseUrl(correctedUrl)
            _backendBaseUrl.value = ApiClient.getBaseUrl()
            sharedPrefs.edit().putString("backend_url", ApiClient.getBaseUrl()).apply()
        } else {
            ApiClient.updateBaseUrl("https://one-earth-dadyagc7bcc9hpcb.eastasia-01.azurewebsites.net/")
            _backendBaseUrl.value = ApiClient.getBaseUrl()
        }
        // Prepopulate database with rich data for demonstration
        prepopulateDb()
        // Start backend connection monitoring
        startBackendHealthChecking()
        // Initialize Socket.IO connection
        setupSocketManager()
        // Check if user is already logged in
        checkAutoLogin()
    }

    private fun checkAutoLogin() {
        viewModelScope.launch {
            val loggedInUser = userDao.getUserById("me")
            if (loggedInUser != null) {
                ApiClient.authToken = loggedInUser.token
                
                // CRITICAL BUG FIX: Fetch current monarchs IMMEDIATELY on login
                // This ensures the state flows are populated BEFORE the UI renders
                android.util.Log.i("MonarchSync", "📲 [LOGIN] User logged in, fetching current monarchs immediately...")
                fetchCurrentMonarchs()
                
                queryAndSendFcmToken()

                if (loggedInUser.onboardingCompleted && loggedInUser.citizenOathAccepted) {
                    _currentScreen.value = Screen.MainDashboard
                } else if (!loggedInUser.onboardingCompleted) {
                    if (loggedInUser.territory.isBlank()) {
                        _currentScreen.value = Screen.TerritorySelection
                    } else if (loggedInUser.personalityTraits.isBlank()) {
                        _currentScreen.value = Screen.PersonalitySetup
                    } else {
                        _currentScreen.value = Screen.CitizenOath
                    }
                } else if (!loggedInUser.citizenOathAccepted) {
                    _currentScreen.value = Screen.CitizenOath
                }
            }
        }
    }

    fun queryAndSendFcmToken() {
        viewModelScope.launch {
            try {
                val persistedUrl = sharedPrefs.getString("backend_url", null) ?: ApiClient.getBaseUrl()
                ApiClient.updateBaseUrl(persistedUrl)

                val loggedInUser = userDao.getUserById("me") ?: return@launch
                val token = loggedInUser.token
                if (!token.isNullOrBlank()) {
                    ApiClient.authToken = token
                }

                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (!task.isSuccessful) {
                            android.util.Log.w("FCM", "Getting FCM token failed", task.exception)
                            return@addOnCompleteListener
                        }

                        val fcmToken = task.result
                        android.util.Log.i("FCM", "Acquired FCM Registration Token: $fcmToken")

                        viewModelScope.launch {
                            try {
                                val updatedUser = loggedInUser.copy(fcmToken = fcmToken)
                                userDao.insertUser(updatedUser)

                                val userEmail = loggedInUser.email.lowercase().trim()
                                try {
                                    ApiClient.getService().updateProfileFields(userEmail, mapOf("fcmToken" to fcmToken))
                                    android.util.Log.i("FCM", "Successfully updated backend registration token.")
                                } catch (fieldEx: java.lang.Exception) {
                                    android.util.Log.e("FCM", "FCM direct field update failed, trying full profile fallback: ${fieldEx.message}")
                                    try {
                                        ApiClient.getService().updateUserProfile(userEmail, updatedUser)
                                        android.util.Log.i("FCM", "Successfully synced profile registration token via fallback.")
                                    } catch (fallbackEx: java.lang.Exception) {
                                        android.util.Log.e("FCM", "FCM full fallback update failed: ${fallbackEx.message}")
                                    }
                                }
                            } catch (apiEx: java.lang.Exception) {
                                android.util.Log.e("FCM", "FCM local update failed: ${apiEx.message}")
                            }
                        }
                    }
            } catch (e: java.lang.Exception) {
                android.util.Log.e("FCM", "queryAndSendFcmToken failed: ${e.message}")
            }
        }
    }

    fun performLogout() {
        viewModelScope.launch {
            _selectedRoomId.value = null
            dismissNotification()
            try {
                val me = userDao.getUserById("me")
                if (me != null && _isBackendConnected.value) {
                    ApiClient.getService().updateProfileFields(me.email.lowercase().trim(), mapOf("fcmToken" to ""))
                }
            } catch (e: Exception) {
                android.util.Log.e("FCM", "Reset FCM on logout error: ${e.message}")
            }
            ApiClient.authToken = null
            userDao.deleteUserById("me")
            _currentScreen.value = Screen.Splash
        }
    }

    private fun checkConnectionOnce() {
        viewModelScope.launch {
            try {
                val response = ApiClient.getService().checkHealth()
                _isBackendConnected.value = (response.status == "ok" || response.status.isNotBlank())
            } catch (e: Exception) {
                _isBackendConnected.value = false
            }
        }
    }

    private fun startBackendHealthChecking() {
        viewModelScope.launch {
            var failedCount = 0
            while (true) {
                if (_isAppInForeground.value) {
                    try {
                        val response = ApiClient.getService().checkHealth()
                        val wasConnected = _isBackendConnected.value
                        _isBackendConnected.value = (response.status == "ok" || response.status.isNotBlank())
                        failedCount = 0
                        if (!wasConnected && _isBackendConnected.value) {
                            _toastMessage.emit("Connected to One Earth Network Backend!")
                            android.util.Log.i("MonarchSync", "🔄 [RECONNECT] Backend reconnected, refetching monarchs...")
                            fetchCurrentMonarchs()
                            syncAllWithBackend()
                        } else if (_isBackendConnected.value) {
                            // Periodic background sync of users, posts, and chats
                            syncAllWithBackend()
                            
                            // NEW: Periodic monarch refresh (every ~30 seconds)
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastMonarchFetchTime > 30000) {
                                android.util.Log.d("MonarchSync", "🔄 [PERIODIC] Refreshing monarchs (30s cycle)...")
                                fetchCurrentMonarchs()
                                lastMonarchFetchTime = currentTime
                            }
                        }
                    } catch (e: Exception) {
                        _isBackendConnected.value = false
                        failedCount++
                        val currentUrl = ApiClient.getBaseUrl()
                        if (failedCount >= 2 && !currentUrl.contains("10.0.2.2") && !currentUrl.contains("localhost")) {
                            android.util.Log.d("AppViewModel", "Cloud tunnel unresponsive. Attempting local emulator fallback...")
                            ApiClient.updateBaseUrl("http://10.0.2.2:4000/")
                            _backendBaseUrl.value = ApiClient.getBaseUrl()
                            failedCount = 0
                        } else if (failedCount >= 2 && (currentUrl.contains("10.0.2.2") || currentUrl.contains("localhost"))) {
                            android.util.Log.d("AppViewModel", "Local loopback unresponsive. Reverting to cloud tunnel...")
                            ApiClient.updateBaseUrl("https://one-earth-dadyagc7bcc9hpcb.eastasia-01.azurewebsites.net/")
                            _backendBaseUrl.value = ApiClient.getBaseUrl()
                            failedCount = 0
                        }
                    }
                }
                kotlinx.coroutines.delay(8000) // check every 8 seconds
            }
        }
    }

    fun isChatRoomOpen(roomId: Int): Boolean {
        return _currentScreen.value == Screen.MainDashboard &&
               _currentTab.value == DashboardTab.Messaging &&
               _selectedRoomId.value == roomId
    }

    private fun setupSocketManager() {
        SocketManager.setListener(this)
        SocketManager.connect(ApiClient.getBaseUrl())
        
        viewModelScope.launch {
            currentUserFlow.collect { user ->
                if (user != null && user.email.isNotBlank()) {
                    android.util.Log.d("AppViewModel", "Current user loaded/changed, joining personal channel: ${user.email}")
                    SocketManager.joinUser(user.email)
                }
            }
        }
        
        viewModelScope.launch {
            activeChatRooms.collect { rooms ->
                android.util.Log.d("AppViewModel", "Active chat rooms collected, joining on Socket: ${rooms.map { it.id }}")
                rooms.forEach { room ->
                    SocketManager.joinRoom(room.id)
                }
            }
        }
    }

    override fun onNewMessage(message: ChatMessageEntity) {
        viewModelScope.launch {
            val user = userDao.getUserById("me")
            val myName = user?.name ?: "Me"
            
            chatDao.insertMessage(message)

            // Check if it's an incoming message from the other participant
            if (!message.senderName.equals(myName, ignoreCase = true) && 
                !message.senderName.equals("Me", ignoreCase = true) &&
                !message.senderId.startsWith("read_receipt") && 
                !message.senderId.startsWith("delivered_receipt")) {
                
                // Direct delivery receipt triggered as it has reached the client
                triggerDeliveryReceipt(message.roomId, message.timestamp)
                
                if (!isChatRoomOpen(message.roomId) && message.timestamp >= appStartedTime - 5000) {
                    val notifKey = "${message.roomId}_${message.timestamp}"
                    if (!shownNotificationKeys.contains(notifKey)) {
                        shownNotificationKeys.add(notifKey)
                        showInAppNotification(
                            roomId = message.roomId,
                            senderName = message.senderName,
                            messageText = message.messageText
                        )
                    }
                }
                
                if (isChatRoomOpen(message.roomId)) {
                    triggerReadReceipt(message.roomId, message.timestamp)
                }
            }
            
            val room = chatDao.getRoomById(message.roomId)
            if (room != null) {
                chatDao.updateRoom(room.copy(
                    lastMessage = message.messageText,
                    lastMessageTime = message.timestamp
                ))
            }
        }
    }

    override fun onMessageDelivered(roomId: Int, timestamp: Long, senderName: String) {
        viewModelScope.launch {
            val receipt = ChatReceiptEntity(
                id = "${roomId}_${senderName}_delivered",
                roomId = roomId,
                senderName = senderName,
                type = "delivered",
                timestamp = timestamp
            )
            chatDao.insertReceipt(receipt)
        }
    }

    override fun onMessageRead(roomId: Int, timestamp: Long, senderName: String) {
        viewModelScope.launch {
            val receipt = ChatReceiptEntity(
                id = "${roomId}_${senderName}_read",
                roomId = roomId,
                senderName = senderName,
                type = "read",
                timestamp = timestamp
            )
            chatDao.insertReceipt(receipt)
        }
    }

    override fun onTyping(roomId: Int, senderName: String) {
        val current = _typingUsers.value.toMutableMap()
        current[roomId] = senderName
        _typingUsers.value = current
    }

    override fun onStopTyping(roomId: Int, senderName: String) {
        val current = _typingUsers.value.toMutableMap()
        if (current[roomId] == senderName) {
            current.remove(roomId)
        }
        _typingUsers.value = current
    }

    override fun onConnect() {
        _isBackendConnected.value = true
        viewModelScope.launch {
            val me = userDao.getUserById("me")
            if (me != null && me.email.isNotBlank()) {
                SocketManager.joinUser(me.email)
            }
            chatDao.getActiveRoomsFlow().first().forEach { room ->
                SocketManager.joinRoom(room.id)
            }
            chatDao.getWaitingRoomsFlow().first().forEach { room ->
                SocketManager.joinRoom(room.id)
            }
            _selectedRoomId.value?.let { roomId ->
                SocketManager.joinRoom(roomId)
                markRoomAsRead(roomId)
            }
        }
    }

    override fun onDisconnect() {
        // _isBackendConnected.value = false // Keep true if REST is still working? 
        // Instructions say "backend health checking" handles _isBackendConnected for REST.
    }

    override fun onNewNotification(notification: NotificationDTO) {
        viewModelScope.launch {
            val entity = NotificationEntity(
                id = notification.id,
                recipientId = notification.recipientId,
                senderId = notification.senderId,
                type = notification.type,
                title = notification.title,
                body = notification.body,
                isRead = notification.isRead,
                createdAt = notification.createdAt,
                roomId = notification.roomId,
                postId = notification.postId,
                userId = notification.userId
            )
            notificationDao.insertNotification(entity)

            if (notification.type == "profile_view") {
                val visitorName = notification.body.substringBefore(" viewed your profile").trim()
                if (visitorName.isNotBlank() && visitorName != "Someone" && visitorName != notification.body) {
                    val existing = visitorDao.getVisitorRecord("me", visitorName)
                    val visitor = if (existing != null) {
                        existing.copy(timestamp = notification.createdAt)
                    } else {
                        ProfileVisitorEntity(
                            hostUserId = "me",
                            visitorName = visitorName,
                            visitorTerritory = "Global",
                            visitorFlag = "🌍",
                            visitorRank = "Citizen",
                            timestamp = notification.createdAt
                        )
                    }
                    visitorDao.insertVisitor(visitor)
                }
            }

            // Trigger Instagram-style real-time dropdown in-app popup banner for general notifications
            showInAppNotification(
                roomId = -1, // Use -1 to represent non-chat alerts
                senderName = notification.title,
                messageText = notification.body
            )

            _toastMessage.emit("🔔 ${notification.body}")
        }
    }

    fun sendTyping(roomId: Int) {
        viewModelScope.launch {
            val user = userDao.getUserById("me")
            val myName = user?.name ?: "Me"
            SocketManager.sendTyping(roomId, myName)
        }
    }

    fun sendStopTyping(roomId: Int) {
        viewModelScope.launch {
            val user = userDao.getUserById("me")
            val myName = user?.name ?: "Me"
            SocketManager.sendStopTyping(roomId, myName)
        }
    }

    fun syncAllWithBackend() {
        viewModelScope.launch {
            if (!_isBackendConnected.value) return@launch
            try {
                // 0. Ensure current logged-in local user is registered and updated on the server
                val me = userDao.getUserById("me")
                if (me != null && me.email.isNotBlank()) {
                    try {
                        val reg = ApiClient.getService().registerUser(me)
                        if (!reg.token.isNullOrBlank()) {
                            ApiClient.authToken = reg.token
                            saveUserAndRecalculateRank(reg.copy(id = "me"))
                        }
                    } catch (e: Exception) {
                        try {
                            val upd = ApiClient.getService().updateUserProfile(me.email.lowercase().trim(), me)
                            if (!upd.token.isNullOrBlank()) {
                                ApiClient.authToken = upd.token
                                saveUserAndRecalculateRank(upd.copy(id = "me"))
                            }
                        } catch (updateEx: Exception) {
                            // Safe fallback
                        }
                    }
                }

                // 1. Sync Posts from Backend
                try {
                    val remotePosts = ApiClient.getService().getPosts()
                    if (remotePosts.isNotEmpty()) {
                        remotePosts.forEach { post ->
                            postDao.insertPost(post)
                        }
                        val remoteIds = remotePosts.map { it.id }
                        postDao.pruneStalePosts(remoteIds)
                    } else {
                        postDao.deleteAllPosts()
                    }
                } catch (postEx: Exception) {
                    android.util.Log.e("AppViewModel", "Sync Posts Error: ${postEx.message}", postEx)
                }

                // 2. Sync Chat Rooms from Backend
                try {
                    val remoteRooms = ApiClient.getService().getChatRooms(me?.name)
                    if (remoteRooms.isNotEmpty()) {
                        remoteRooms.forEach { room ->
                            chatDao.insertRoom(room)
                        }
                        val remoteIds = remoteRooms.map { it.id }
                        chatDao.pruneStaleRooms(remoteIds)
                    } else {
                        chatDao.deleteAllRooms()
                    }
                } catch (roomEx: Exception) {
                    android.util.Log.e("AppViewModel", "Sync Chat Rooms Error: ${roomEx.message}", roomEx)
                }

                // 3. Sync All Users from Cloud to Local Friends List
                try {
                    val remoteUsers = ApiClient.getService().getAllUsers()
                    if (remoteUsers.isNotEmpty()) {
                        val insertedUserIds = mutableListOf<String>()
                        remoteUsers.forEach { user ->
                            // Skip local logged-in user profile to prevent self duplicates
                            val matchesMe = me != null && (
                                user.email.lowercase().trim() == me.email.lowercase().trim() ||
                                user.username.lowercase().trim() == me.username.lowercase().trim()
                            )
                            if (!matchesMe && user.email.isNotBlank()) {
                                // Map the received user entity into sqlite with their email as secondary key ID
                                val friendId = user.email.lowercase().trim()
                                val friendEntity = user.copy(id = friendId)
                                userDao.insertUser(friendEntity)
                                insertedUserIds.add(friendId)
                            }
                        }
                        if (insertedUserIds.isNotEmpty()) {
                            userDao.pruneStaleUsers(insertedUserIds)
                        } else {
                            userDao.deleteAllNonMeUsers()
                        }
                    } else {
                        userDao.deleteAllNonMeUsers()
                    }
                } catch (userEx: Exception) {
                    android.util.Log.e("AppViewModel", "Sync Users Error: ${userEx.message}", userEx)
                }

                // 4. Sync Notifications from Backend
                try {
                    val remoteNotifs = ApiClient.getService().getNotifications()
                    if (remoteNotifs.isNotEmpty()) {
                        val mapped = remoteNotifs.map { dto ->
                            if (dto.type == "profile_view") {
                                val visitorName = dto.body.substringBefore(" viewed your profile").trim()
                                if (visitorName.isNotBlank() && visitorName != "Someone" && visitorName != dto.body) {
                                    val existing = visitorDao.getVisitorRecord("me", visitorName)
                                    val visitor = if (existing != null) {
                                        existing.copy(timestamp = dto.createdAt)
                                    } else {
                                        ProfileVisitorEntity(
                                            hostUserId = "me",
                                            visitorName = visitorName,
                                            visitorTerritory = "Global",
                                            visitorFlag = "🌍",
                                            visitorRank = "Citizen",
                                            timestamp = dto.createdAt
                                        )
                                    }
                                    visitorDao.insertVisitor(visitor)
                                }
                            }
                            NotificationEntity(
                                id = dto.id,
                                recipientId = dto.recipientId,
                                senderId = dto.senderId,
                                type = dto.type,
                                title = dto.title,
                                body = dto.body,
                                isRead = dto.isRead,
                                createdAt = dto.createdAt,
                                roomId = dto.roomId,
                                postId = dto.postId,
                                userId = dto.userId
                            )
                        }
                        notificationDao.insertNotifications(mapped)
                        val remoteIds = remoteNotifs.map { it.id }
                        notificationDao.pruneStaleNotifications(remoteIds)
                    } else {
                        notificationDao.deleteAllNotifications()
                    }
                } catch (notifEx: Exception) {
                    android.util.Log.e("AppViewModel", "Sync Notifications Error: ${notifEx.message}", notifEx)
                }

                // 5. Sync Authoritative Monarch from Backend
                try {
                    fetchCurrentMonarchs()
                } catch (monarchEx: Exception) {
                    android.util.Log.e("AppViewModel", "Sync Current Monarch Error: ${monarchEx.message}", monarchEx)
                }
            } catch (e: Exception) {
                // Fail-safe
                android.util.Log.e("AppViewModel", "SyncAllWithBackend Error: ${e.message}", e)
            }
        }
    }

    // CRITICAL BUG FIX #1: AGGRESSIVE MONARCH FETCHING
    // This ensures ALL users see the SAME King and Queen (election integrity)
    // Made PUBLIC so it can be called from multiple places
    // Added detailed logging to debug issues
    fun fetchCurrentMonarchs() {
        viewModelScope.launch {
            try {
                android.util.Log.d("MonarchSync", "🔍 [FETCH] Starting monarch fetch from API...")
                val response = ApiClient.getService().getCurrentMonarch()
                
                android.util.Log.d("MonarchSync", "📡 [API] Response received: success=${response.success}, hasKing=${response.king != null}, hasQueen=${response.queen != null}")
                
                if (response.success) {
                    if (response.king != null) {
                        val kingData = response.king
                        val kingUser = UserEntity(
                            id = kingData.id,
                            email = "${kingData.username.lowercase().trim()}@oneearth.io",
                            name = kingData.name,
                            username = kingData.username,
                            dob = "1992-08-21",
                            territory = "Realm",
                            flagEmoji = "👑",
                            gender = "Male",
                            currentRank = "King",
                            bio = kingData.bio,
                            profilePhoto = kingData.profilePhoto,
                            knowledgeCredits = 0,
                            contributionCredits = 0,
                            onboardingCompleted = true,
                            citizenOathAccepted = true
                        )
                        _currentKing.value = kingUser
                        
                        // Cache King
                        val existingKing = userDao.getUserById(kingData.id)
                        val toInsertKing = existingKing?.copy(
                            name = kingData.name,
                            username = kingData.username,
                            currentRank = "King",
                            bio = kingData.bio,
                            profilePhoto = kingData.profilePhoto
                        ) ?: kingUser
                        userDao.insertUser(toInsertKing)
                        android.util.Log.i("MonarchSync", "👑 [SET] KING updated: ${kingUser.name}")
                    } else {
                        _currentKing.value = null
                    }

                    if (response.queen != null) {
                        val queenData = response.queen
                        val queenUser = UserEntity(
                            id = queenData.id,
                            email = "${queenData.username.lowercase().trim()}@oneearth.io",
                            name = queenData.name,
                            username = queenData.username,
                            dob = "1994-04-12",
                            territory = "Realm",
                            flagEmoji = "💎",
                            gender = "Female",
                            currentRank = "Queen",
                            bio = queenData.bio,
                            profilePhoto = queenData.profilePhoto,
                            knowledgeCredits = 0,
                            contributionCredits = 0,
                            onboardingCompleted = true,
                            citizenOathAccepted = true
                        )
                        _currentQueen.value = queenUser
                        
                        // Cache Queen
                        val existingQueen = userDao.getUserById(queenData.id)
                        val toInsertQueen = existingQueen?.copy(
                            name = queenData.name,
                            username = queenData.username,
                            currentRank = "Queen",
                            bio = queenData.bio,
                            profilePhoto = queenData.profilePhoto
                        ) ?: queenUser
                        userDao.insertUser(toInsertQueen)
                        android.util.Log.i("MonarchSync", "👑 [SET] QUEEN updated: ${queenUser.name}")
                    } else {
                        _currentQueen.value = null
                    }
                } else {
                    android.util.Log.w("MonarchSync", "⚠️ [VACANT] Throne is empty - success=${response.success}")
                    _currentKing.value = null
                    _currentQueen.value = null
                }
            } catch (e: Exception) {
                android.util.Log.e("MonarchSync", "❌ [ERROR] CRITICAL: Failed to fetch monarchs", e)
            }
        }
    }

    fun getRankFromCredits(kc: Int, cc: Int): String {
        val total = kc + cc
        return when {
            total >= 500 -> "Noble Elder"
            total >= 300 -> "Guardian"
            total >= 150 -> "Contributor"
            total >= 50 -> "Explorer"
            else -> "Citizen"
        }
    }

    suspend fun saveUserAndRecalculateRank(user: UserEntity) {
        val isMonarch = user.currentRank.equals("King", ignoreCase = true) || user.currentRank.equals("Queen", ignoreCase = true)
        val calculatedRank = if (isMonarch) {
            user.currentRank
        } else {
            getRankFromCredits(user.knowledgeCredits, user.contributionCredits)
        }
        val updated = user.copy(currentRank = calculatedRank)
        userDao.insertUser(updated)
        
        if (updated.id == "me" && updated.email.isNotBlank()) {
            SocketManager.joinUser(updated.email)
        }
        
        // Save to MongoDB via our express api if the updated user is the active logged in user
        if (updated.id == "me" && updated.email.isNotBlank() && _isBackendConnected.value) {
            try {
                ApiClient.getService().updateUserProfile(updated.email.lowercase().trim(), updated)
            } catch (e: Exception) {
                // Fail-safe
            }
        }
    }

    fun showProfileForUser(userId: String) {
        viewModelScope.launch {
            val lUsers = leaderboardUsers.value
            val me = userDao.getUserById("me")

            val activeKing = currentKing.value?.takeIf { it.gender.equals("Male", ignoreCase = true) }
                ?: me?.takeIf { it.currentRank.equals("King", ignoreCase = true) && it.gender.equals("Male", ignoreCase = true) }
                ?: lUsers.firstOrNull { it.currentRank.equals("King", ignoreCase = true) && it.gender.equals("Male", ignoreCase = true) }
                ?: lUsers.firstOrNull { it.gender.equals("Male", ignoreCase = true) }
                ?: lUsers.firstOrNull { it.currentRank.equals("King", ignoreCase = true) }

            val activeQueen = currentQueen.value?.takeIf { it.gender.equals("Female", ignoreCase = true) }
                ?: me?.takeIf { it.currentRank.equals("Queen", ignoreCase = true) && it.gender.equals("Female", ignoreCase = true) }
                ?: lUsers.firstOrNull { it.currentRank.equals("Queen", ignoreCase = true) && it.gender.equals("Female", ignoreCase = true) }
                ?: lUsers.firstOrNull { it.gender.equals("Female", ignoreCase = true) && it.id != activeKing?.id }
                ?: lUsers.firstOrNull { it.currentRank.equals("Queen", ignoreCase = true) }

            val isQueenId = userId.equals(activeQueen?.id, ignoreCase = true)
            val isKingId = userId.equals(activeKing?.id, ignoreCase = true)

            val user = userDao.getUserById(userId) ?: UserEntity(
                id = userId,
                name = userId.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                username = "@$userId",
                email = "$userId@oneearth.io",
                dob = "1995-01-01",
                territory = "Global",
                flagEmoji = "🌍",
                gender = if (isQueenId) "Female" else "Male",
                currentRank = if (isQueenId) "Queen" else if (isKingId) "King" else "Noble Member",
                knowledgeCredits = 120,
                contributionCredits = 80,
                reputationScore = 98,
                bio = "Co-building a beautiful world with high-quality, constructive contributions."
            )

            val isMonarch = user.currentRank.equals("King", ignoreCase = true) || 
                            user.currentRank.equals("Queen", ignoreCase = true) ||
                            isQueenId ||
                            isKingId

            if (isMonarch) {
                // Ensure user in local DB so other parts can reference it
                if (userDao.getUserById(user.id) == null) {
                    userDao.insertUser(user)
                }
                loadMonarchProfile(user.id)
            } else {
                _selectedProfileUser.value = user
            }
        }
    }

    fun showProfileForUserByName(name: String, flag: String, rank: String, territory: String = "Global") {
        viewModelScope.launch {
            val matchFromDb = allUsers.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
            val matchFromLeaderboard = leaderboardUsers.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
            val foundUser = matchFromDb ?: matchFromLeaderboard

            val id = if (foundUser != null) {
                foundUser.id
            } else {
                when (name) {
                    "Arjun Patel" -> "gandhi_avatar"
                    "Clara Dupont" -> "clara_nobel"
                    "Kofi Mensah" -> "kenya_leader"
                    else -> name.lowercase().replace(" ", "_")
                }
            }

            val existing = userDao.getUserById(id)
            if (existing != null) {
                showProfileForUser(existing.id)
            } else {
                val isFemale = rank.equals("Queen", ignoreCase = true) ||
                               rank.equals("Duchess", ignoreCase = true) ||
                               rank.equals("Princess", ignoreCase = true) ||
                               name.contains("Queen", ignoreCase = true) ||
                               name.contains("Elena", ignoreCase = true) ||
                               name.contains("Clara", ignoreCase = true) ||
                               name.contains("Keren", ignoreCase = true)
                val tempUser = UserEntity(
                    id = id,
                    name = name,
                    username = "@" + name.lowercase().replace(" ", ""),
                    email = "${name.lowercase().replace(" ", "")}@oneearth.io",
                    dob = "1993-05-18",
                    territory = territory,
                    flagEmoji = flag,
                    currentRank = rank,
                    gender = if (isFemale) "Female" else "Male",
                    knowledgeCredits = 110,
                    contributionCredits = 80,
                    reputationScore = 97,
                    bio = "Proud citizen of our digital Empire. Active in $territory."
                )
                userDao.insertUser(tempUser)
                showProfileForUser(tempUser.id)
            }
        }
    }

    fun closeProfileDialog() {
        _selectedProfileUser.value = null
    }

    fun startChatWithUser(user: UserEntity) {
        viewModelScope.launch {
            val allRooms = chatDao.getAllRoomsFlow().first()
            val me = userDao.getUserById("me")
            val myName = me?.name ?: "Me"
            val existingRoom = allRooms.find { room ->
                val mapped = mapRoomForUser(room, myName)
                mapped.participantName.lowercase() == user.name.lowercase()
            }
            
            if (existingRoom != null) {
                swapOrActivateRoom(existingRoom.id)
                _currentTab.value = DashboardTab.Messaging
                closeProfileDialog()
                return@launch
            }
            
            val activeRoomsCount = allRooms.count { it.isActive }
            val makeActive = activeRoomsCount < 3
            
            val myFlag = me?.flagEmoji ?: "🌍"
            val myRank = me?.currentRank ?: "Citizen"
            val myTerritory = me?.territory ?: "Empire"
            val newRoom = ChatRoomEntity(
                roomName = "$myName | ${user.name}",
                participantName = "$myName | ${user.name}",
                participantFlag = "$myFlag | ${user.flagEmoji}",
                participantRank = "$myRank | ${user.currentRank}",
                participantTerritory = "$myTerritory | ${user.territory}",
                lastMessage = "Establish direct focus dialogue.",
                isActive = makeActive,
                isWaiting = !makeActive
            )
            
            var finalRoomId = 0
            var remoteRoomCreated: ChatRoomEntity? = null
            
            // Sync with backend FIRST if connected to obtain the proper server sequential ID
            if (_isBackendConnected.value) {
                try {
                    val remoteRoom = ApiClient.getService().createChatRoom(newRoom.copy(id = 0))
                    finalRoomId = remoteRoom.id
                    remoteRoomCreated = remoteRoom
                } catch (e: Exception) {
                    android.util.Log.e("AppViewModel", "Failed to create remote room synchronically: ${e.message}", e)
                }
            }
            
            // Fallback to local auto-increment ID if backend creation failed or is offline
            if (finalRoomId <= 0) {
                finalRoomId = chatDao.insertRoom(newRoom).toInt()
            } else {
                remoteRoomCreated?.let {
                    chatDao.insertRoom(it.copy(isActive = makeActive, isWaiting = !makeActive))
                }
            }
            
            _selectedRoomId.value = finalRoomId
            _currentTab.value = DashboardTab.Messaging
            closeProfileDialog()
            
            if (makeActive) {
                SocketManager.joinRoom(finalRoomId)
                _toastMessage.emit("Focus Connection initialized with ${user.name}!")
            } else {
                _toastMessage.emit("${user.name} queued. Terminal capacity at limit (Max 3). Swap connections in Messaging tab!")
            }
        }
    }

    fun updateUserProfile(
        name: String,
        username: String,
        bio: String,
        territory: String,
        flagEmoji: String,
        profilePhoto: String
    ) {
        viewModelScope.launch {
            val me = userDao.getUserById("me") ?: return@launch
            val normalizedNewUsername = if (username.trim().startsWith("@")) username.trim() else "@${username.trim()}"
            val cleanNewUsername = normalizedNewUsername.removePrefix("@").lowercase().trim()
            val cleanCurrentUsername = me.username.removePrefix("@").lowercase().trim()

            // Check local database for uniqueness
            if (cleanNewUsername != cleanCurrentUsername) {
                val localUser = userDao.getUserByEmailOrUsername(cleanNewUsername)
                if (localUser != null && localUser.id != "me" && localUser.email.lowercase().trim() != me.email.lowercase().trim()) {
                    _toastMessage.emit("Username already taken")
                    return@launch
                }
            }

            val isFlagLocked = me.flagEmoji.isNotEmpty() && me.flagEmoji != "🌍"
            val calculatedRank = if (me.currentRank.equals("King", ignoreCase = true) || me.currentRank.equals("Queen", ignoreCase = true)) {
                me.currentRank
            } else {
                getRankFromCredits(me.knowledgeCredits, me.contributionCredits)
            }

            val updated = me.copy(
                name = name,
                username = normalizedNewUsername,
                bio = bio,
                territory = if (isFlagLocked) me.territory else territory,
                flagEmoji = if (isFlagLocked) me.flagEmoji else flagEmoji,
                profilePhoto = profilePhoto,
                currentRank = calculatedRank
            )

            if (_isBackendConnected.value) {
                try {
                    val remoteUser = ApiClient.getService().updateUserProfile(me.email.lowercase().trim(), updated)
                    val localMe = remoteUser.copy(id = "me")
                    userDao.insertUser(localMe)
                    SocketManager.joinUser(localMe.email)
                    _toastMessage.emit("Profile paradigm synchronized successfully!")
                } catch (e: retrofit2.HttpException) {
                    val errorBody = e.response()?.errorBody()?.string()
                    if (errorBody != null && errorBody.contains("Username already taken", ignoreCase = true)) {
                        _toastMessage.emit("Username already taken")
                    } else {
                        _toastMessage.emit("Username already taken")
                    }
                    return@launch
                } catch (e: Exception) {
                    _toastMessage.emit("Synchronization error: ${e.message}")
                    return@launch
                }
            } else {
                userDao.insertUser(updated)
                SocketManager.joinUser(updated.email)
                _toastMessage.emit("Profile paradigm synchronized successfully!")
            }
        }
    }

    fun performLogin(identifier: String, passphraseInput: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val lowercaseId = identifier.trim().lowercase()

            // Try real server login first if backend is active
            if (_isBackendConnected.value) {
                try {
                    val remoteUser = ApiClient.getService().loginUser(LoginRequest(lowercaseId, passphraseInput))
                    ApiClient.authToken = remoteUser.token
                    val clonedMe = remoteUser.copy(id = "me")
                    saveUserAndRecalculateRank(clonedMe)
                    userDao.insertUser(remoteUser.copy(id = remoteUser.email.lowercase()))
                    fetchCurrentMonarchs() // Fetch real-time monarchs immediately
                    queryAndSendFcmToken()
                    _toastMessage.emit("Welcome Back (Sync Active), ${remoteUser.name}!")
                    _currentScreen.value = Screen.MainDashboard
                    onResult(true)
                    return@launch
                } catch (e: Exception) {
                    val errorMsg = when {
                        e.message?.contains("401") == true -> "Passport authentication failed. Passphrase mismatch."
                        e.message?.contains("404") == true -> "User identity does not exist in Empire registry."
                        e.message?.contains("400") == true -> "Identifier or passphrase is invalid."
                        else -> null
                    }
                    if (errorMsg != null) {
                        _toastMessage.emit("Authentication Error: $errorMsg")
                        onResult(false)
                        return@launch
                    }
                }
            }
            
            // Try matching registered user in Room
            val matchedUser = userDao.getUserByEmailOrUsername(lowercaseId)

            if (matchedUser != null) {
                // Check if passphrase matches
                if (matchedUser.passphrase == passphraseInput || (matchedUser.passphrase == "1234" && passphraseInput.isEmpty())) {
                    val clonedMe = matchedUser.copy(id = "me")
                    saveUserAndRecalculateRank(clonedMe)
                    queryAndSendFcmToken()
                    _toastMessage.emit("Welcome Back, ${matchedUser.name}!")
                    _currentScreen.value = Screen.MainDashboard
                    onResult(true)
                } else {
                    _toastMessage.emit("Fail: Passport passphrase mismatch. Try '1234' or your registered password.")
                    onResult(false)
                }
            } else {
                _toastMessage.emit("Authentication Error: User identity not registered. Please register first.")
                onResult(false)
            }
        }
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    fun triggerToast(msg: String) {
        viewModelScope.launch {
            _toastMessage.emit(msg)
        }
    }

    fun selectTab(tab: DashboardTab) {
        _currentTab.value = tab
        if (tab == DashboardTab.PublicSquare) {
            syncAllWithBackend()
        }
    }

    fun notifyProfileView(hostUser: UserEntity) {
        viewModelScope.launch {
            val me = userDao.getUserById("me")
            if (me != null && me.id != hostUser.id) {
                // Record profile visit on backend (recipient receives remote notification)
                if (_isBackendConnected.value) {
                    try {
                        val hostEmail = if (hostUser.email.isNotBlank()) hostUser.email else hostUser.id
                        ApiClient.getService().recordProfileVisit(hostEmail.lowercase().trim())
                    } catch (e: Exception) {
                        android.util.Log.e("AppViewModel", "Failed to record backend profile view: ${e.message}", e)
                    }
                }
            }
        }
    }

    fun sendFriendRequest(targetUser: UserEntity) {
        viewModelScope.launch {
            _toastMessage.emit("Friend request dispatched to ${targetUser.name}!")
            if (_isBackendConnected.value) {
                try {
                    val targetEmail = if (targetUser.email.isNotBlank()) targetUser.email else targetUser.id
                    ApiClient.getService().sendFriendRequest(targetEmail.lowercase().trim())
                } catch (e: Exception) {
                    android.util.Log.e("AppViewModel", "Failed to send friend request on backend: ${e.message}", e)
                }
            }
        }
    }

    fun refreshNotifications() {
        viewModelScope.launch {
            if (!_isBackendConnected.value) return@launch
            try {
                val remoteNotifs = ApiClient.getService().getNotifications()
                notificationDao.deleteAllNotifications()
                if (remoteNotifs.isNotEmpty()) {
                    val mapped = remoteNotifs.map { dto ->
                        NotificationEntity(
                            id = dto.id,
                            recipientId = dto.recipientId,
                            senderId = dto.senderId,
                            type = dto.type,
                            title = dto.title,
                            body = dto.body,
                            isRead = dto.isRead,
                            createdAt = dto.createdAt,
                            roomId = dto.roomId,
                            postId = dto.postId,
                            userId = dto.userId
                        )
                    }
                    notificationDao.insertNotifications(mapped)
                }
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "refreshNotifications error: ${e.message}", e)
            }
        }
    }

    fun markNotificationAsRead(id: String) {
        viewModelScope.launch {
            notificationDao.markAsRead(id)
            if (_isBackendConnected.value) {
                try {
                    ApiClient.getService().markNotificationAsRead(id)
                } catch (e: Exception) {
                    android.util.Log.e("AppViewModel", "markNotificationAsRead failure: ${e.message}", e)
                }
            }
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            notificationDao.markAllAsRead()
            if (_isBackendConnected.value) {
                try {
                    ApiClient.getService().markAllNotificationsAsRead()
                } catch (e: Exception) {
                    android.util.Log.e("AppViewModel", "markAllNotificationsAsRead failure: ${e.message}", e)
                }
            }
        }
    }

    fun showWelcomeDialog() {
        _showDailyWelcome.value = true
    }

    fun dismissWelcomeDialog() {
        _showDailyWelcome.value = false
    }

    fun showExitDialog() {
        _showExitSummary.value = true
    }

    fun dismissExitDialog() {
        _showExitSummary.value = false
    }

    fun selectPostForComments(postId: Int?) {
        _selectedCommentsPostId.value = postId
        if (postId != null && _isBackendConnected.value) {
            viewModelScope.launch {
                try {
                    val remoteComments = ApiClient.getService().getComments(postId)
                    remoteComments.forEach { comment ->
                        val localMatch = commentDao.getCommentByPostAuthorAndContent(comment.postId, comment.authorName, comment.content)
                        if (localMatch != null && localMatch.id != comment.id) {
                            commentDao.deleteComment(localMatch)
                        }
                        commentDao.insertComment(comment)
                    }
                } catch (e: Exception) {
                    // safe fallback
                }
            }
        }
    }

    fun selectRoom(roomId: Int?) {
        _chatLimit.value = 40
        _selectedRoomId.value = roomId
        
        if (roomId != null) {
            SocketManager.joinRoom(roomId)
            markRoomAsRead(roomId)
            if (_isBackendConnected.value) {
                viewModelScope.launch {
                    try {
                        val remoteMessages = ApiClient.getService().getChatMessages(roomId)
                        remoteMessages.forEach { msg ->
                            val localMatch = chatDao.getMessageByRoomSenderAndText(msg.roomId, msg.senderId, msg.messageText)
                            if (localMatch != null && localMatch.id != msg.id) {
                                chatDao.deleteMessage(localMatch)
                            }
                            chatDao.insertMessage(msg)
                        }
                        try {
                            val remoteReceipts = ApiClient.getService().getChatReceipts(roomId)
                            remoteReceipts.forEach { receipt ->
                                chatDao.insertReceipt(receipt)
                            }
                        } catch (receiptEx: Exception) {
                            // Safe fallback
                        }
                        markRoomAsRead(roomId)
                    } catch (e: Exception) {
                        // safe fallback
                    }
                }
            }
        }
    }

    // Interactive Quiz (Knowledge Arena Leveling)
    val quizQuestions = listOf(
        QuizQuestion(
            id = 1,
            subject = "Philosophy",
            question = "Which system emphasizes wisdom, ethical contribution, and service over simple numeric majorities as a driver of societal rank?",
            options = listOf("Technocratic Autocracy", "Dopamine Plutocracy", "Platonic Meritocracy", "Oligarchic Populism"),
            correctAnswerIndex = 2,
            explanation = "Plato's Republic envisioned a society governed by Philosopher Guardians where wisdom and service govern societal standing."
        ),
        QuizQuestion(
            id = 2,
            subject = "Economics",
            question = "In the 'One Earth One Family' civilization, why are Knowledge Credits (KC) and Contribution Credits (CC) non-transferable?",
            options = listOf("Because of network latency", "To avoid monetary commodification of character", "To mimic national currencies", "To encourage anonymous trading"),
            correctAnswerIndex = 1,
            explanation = "To prevent wealth-based capture of administrative influence. Status must be earned through pure personal merit and cannot be purchased."
        ),
        QuizQuestion(
            id = 3,
            subject = "Science",
            question = "What global biosphere feedback mechanism suggests that living organisms interact with their inorganic surroundings to maintain homeostasis?",
            options = "Kepler Principle,Nebular Hypothesis,Ecosystem Static Rule,Gaia Hypothesis".split(","),
            correctAnswerIndex = 3,
            explanation = "The Gaia Hypothesis, formulated by James Lovelock, views Earth as a complex self-regulating system."
        ),
        QuizQuestion(
            id = 4,
            subject = "Technology",
            question = "Which paradigm prevents dopamine-farming, infinite scroll metrics by placing structural limits on conversational slots?",
            options = "Socket Throttling,The Three-Connection Rule,Cognitive Load Compression,Interactive Queueing".split(","),
            correctAnswerIndex = 1,
            explanation = "OEOF enforces active connection limits to combat infinite distraction, prioritizing qualitative depth in human relationship."
        ),
        QuizQuestion(
            id = 5,
            subject = "History",
            question = "Which historical leader's philosophy directly aligns with 'Leadership through Service' through non-violent communal reform?",
            options = "Julius Caesar,Napoleon Bonaparte,Mahatma Gandhi,Alexander the Great".split(","),
            correctAnswerIndex = 2,
            explanation = "Gandhi's model of leadership focused on self-discipline, service to truth, and direct positive community action."
        )
    )

    private val _currentQuizIndex = MutableStateFlow(0)
    val currentQuizIndex: StateFlow<Int> = _currentQuizIndex.asStateFlow()

    private val _quizCompleted = MutableStateFlow(false)
    val quizCompleted: StateFlow<Boolean> = _quizCompleted.asStateFlow()

    private val _selectedAnswers = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val selectedAnswers: StateFlow<Map<Int, Int>> = _selectedAnswers.asStateFlow()

    fun answerQuizQuestion(questionId: Int, optionIndex: Int) {
        val updated = _selectedAnswers.value.toMutableMap()
        updated[questionId] = optionIndex
        _selectedAnswers.value = updated

        // Award points if correct
        val question = quizQuestions.find { it.id == questionId }
        if (question != null && question.correctAnswerIndex == optionIndex) {
            viewModelScope.launch {
                val currentMe = userDao.getUserById("me")
                if (currentMe != null) {
                    val pointsEarnt = 15
                    val updatedUser = currentMe.copy(
                        knowledgeCredits = currentMe.knowledgeCredits + pointsEarnt
                    )
                    saveUserAndRecalculateRank(updatedUser)
                    _toastMessage.emit("+15 Knowledge Credits (KC) awarded for system excellence!")
                }
            }
        } else {
            viewModelScope.launch {
                _toastMessage.emit("Incorrect choice. Review the philosophical material.")
            }
        }

        // Advance or complete
        if (_currentQuizIndex.value < quizQuestions.size - 1) {
            _currentQuizIndex.value = _currentQuizIndex.value + 1
        } else {
            _quizCompleted.value = true
        }
    }

    fun resetQuiz() {
        _currentQuizIndex.value = 0
        _quizCompleted.value = false
        _selectedAnswers.value = emptyMap()
    }

    // Complete Onboarding Data Integration
    fun completeOnboarding() {
        viewModelScope.launch {
            val user = UserEntity(
                id = "me",
                name = tempName,
                username = if (tempUsername.startsWith("@")) tempUsername else "@$tempUsername",
                email = tempEmail,
                dob = tempDob,
                gender = tempGender,
                territory = selectedTerritory,
                flagEmoji = selectedFlag,
                personalityTraits = selectedTraitsList.joinToString(","),
                onboardingCompleted = true,
                citizenOathAccepted = true,
                knowledgeCredits = 50, // Starting bonus
                contributionCredits = 25,
                reputationScore = 98,
                passphrase = tempPassword
            )
            // Save current session
            saveUserAndRecalculateRank(user)

            // Register in the permanent database under their email & username for real sign-in
            val emailRegId = tempEmail.trim().lowercase()
            val userRegId = tempUsername.trim().lowercase().removePrefix("@")
            
            userDao.insertUser(user.copy(id = emailRegId))
            userDao.insertUser(user.copy(id = "user_$userRegId"))

            // Try real server registration (attempt directly to ensure real-time storage)
            try {
                val registeredUser = ApiClient.getService().registerUser(user)
                ApiClient.authToken = registeredUser.token
                saveUserAndRecalculateRank(registeredUser.copy(id = "me"))
                _isBackendConnected.value = true
                _toastMessage.emit("Security Passport registered online.")
            } catch (e: Exception) {
                // If register fails because user already exists on the server, try to update their profile
                try {
                    val updatedProfile = ApiClient.getService().updateUserProfile(user.email.lowercase().trim(), user)
                    ApiClient.authToken = updatedProfile.token ?: ApiClient.authToken
                    saveUserAndRecalculateRank(updatedProfile.copy(id = "me"))
                    _isBackendConnected.value = true
                    _toastMessage.emit("Security Passport updated online.")
                } catch (ex: Exception) {
                    _toastMessage.emit("Registered locally. Cloud sync pending connection.")
                }
            }

            fetchCurrentMonarchs() // Fetch real-time monarchs immediately
            queryAndSendFcmToken() // Immediately sync FCM token for newly registered user
            _showDailyWelcome.value = true
            _currentScreen.value = Screen.MainDashboard
        }
    }

    // Reaction Handling System
    fun reactToPost(postId: Int, type: String) {
        viewModelScope.launch {
            val post = postDao.getPostById(postId) ?: return@launch
            val me = userDao.getUserById("me")
            val myId = if (me != null && me.email.isNotBlank()) me.email.lowercase().trim() else "me"
            var updatedPost = post

            when (type) {
                "Wise" -> {
                    val reactors = post.reactedWiseUsers.split(",").filter { it.isNotBlank() }.toMutableList()
                    if (reactors.contains("me") || reactors.contains(myId)) {
                        reactors.remove("me")
                        reactors.remove(myId)
                        updatedPost = post.copy(
                            knowledgeValue = (post.knowledgeValue - 1).coerceAtLeast(0),
                            reactedWiseUsers = reactors.joinToString(",")
                        )
                        _toastMessage.emit("Retracted Wise reaction")
                    } else {
                        reactors.add(myId)
                        updatedPost = post.copy(
                            knowledgeValue = post.knowledgeValue + 1,
                            reactedWiseUsers = reactors.joinToString(",")
                        )
                        _toastMessage.emit("Your wise reaction has been recorded")
                    }
                }
                "Helpful" -> {
                    val reactors = post.reactedHelpfulUsers.split(",").filter { it.isNotBlank() }.toMutableList()
                    if (reactors.contains("me") || reactors.contains(myId)) {
                        reactors.remove("me")
                        reactors.remove(myId)
                        updatedPost = post.copy(
                            contributionProof = (post.contributionProof - 1).coerceAtLeast(0),
                            reactedHelpfulUsers = reactors.joinToString(",")
                        )
                        _toastMessage.emit("Retracted Helpful reaction")
                    } else {
                        reactors.add(myId)
                        updatedPost = post.copy(
                            contributionProof = post.contributionProof + 1,
                            reactedHelpfulUsers = reactors.joinToString(",")
                        )
                        _toastMessage.emit("Your helpful reaction has been recorded")
                    }
                }
                "Inspiring" -> {
                    val reactors = post.reactedInspiringUsers.split(",").filter { it.isNotBlank() }.toMutableList()
                    if (reactors.contains("me") || reactors.contains(myId)) {
                        reactors.remove("me")
                        reactors.remove(myId)
                        updatedPost = post.copy(
                            reputationImpact = (post.reputationImpact - 1).coerceAtLeast(90),
                            reactedInspiringUsers = reactors.joinToString(",")
                        )
                        _toastMessage.emit("Retracted Inspiring reaction")
                    } else {
                        reactors.add(myId)
                        updatedPost = post.copy(
                            reputationImpact = (post.reputationImpact + 1).coerceAtMost(100),
                            reactedInspiringUsers = reactors.joinToString(",")
                        )
                        _toastMessage.emit("Your inspiring reaction has been recorded")
                    }
                }
            }
            postDao.updatePost(updatedPost)

            // Submit reaction to server
            if (_isBackendConnected.value) {
                try {
                    val remoteUpdated = ApiClient.getService().reactToPost(postId, ReactionRequest(myId, type))
                    postDao.updatePost(remoteUpdated)
                } catch (e: Exception) {
                    // Revert to original post state on server failure
                    postDao.updatePost(post)
                    _toastMessage.emit("Failed to record reaction. Please try again.")
                }
            }
        }
    }

    private suspend fun awardAuthorPoints(authorId: String, kDelta: Int, cDelta: Int) {
        val me = userDao.getUserById("me")
        if (authorId == "me" || (me != null && authorId.lowercase().trim() == me.email.lowercase().trim())) {
            if (me == null) return
            saveUserAndRecalculateRank(me.copy(
                knowledgeCredits = me.knowledgeCredits + kDelta,
                contributionCredits = me.contributionCredits + cDelta
            ))
        } else {
            val user = userDao.getUserById(authorId) ?: return
            saveUserAndRecalculateRank(user.copy(
                knowledgeCredits = user.knowledgeCredits + kDelta,
                contributionCredits = user.contributionCredits + cDelta
            ))
        }
    }

    private suspend fun incrementReputation(authorId: String, delta: Int) {
        val me = userDao.getUserById("me")
        if (authorId == "me" || (me != null && authorId.lowercase().trim() == me.email.lowercase().trim())) {
            if (me == null) return
            saveUserAndRecalculateRank(me.copy(
                reputationScore = (me.reputationScore + delta).coerceAtMost(100)
            ))
        } else {
            val user = userDao.getUserById(authorId) ?: return
            saveUserAndRecalculateRank(user.copy(
                reputationScore = (user.reputationScore + delta).coerceAtMost(100)
            ))
        }
    }

    // Comment Flow
    fun addComment(postId: Int, commentText: String) {
        if (commentText.isBlank()) return
        viewModelScope.launch {
            val me = userDao.getUserById("me") ?: return@launch
            val comment = CommentEntity(
                postId = postId,
                authorName = me.name,
                authorFlag = me.flagEmoji,
                authorRank = me.currentRank,
                content = commentText
            )



            // Submit to backend first if online to get backend IDs and avoid duplication
            if (_isBackendConnected.value) {
                try {
                    ApiClient.getService().addComment(postId, comment)
                    val remoteComments = ApiClient.getService().getComments(postId)
                    remoteComments.forEach { item ->
                        val localMatch = commentDao.getCommentByPostAuthorAndContent(item.postId, item.authorName, item.content)
                        if (localMatch != null && localMatch.id != item.id) {
                            commentDao.deleteComment(localMatch)
                        }
                        commentDao.insertComment(item)
                    }
                } catch (e: Exception) {
                    commentDao.insertComment(comment)
                    _toastMessage.emit("Comment recorded offline. Live synchronization pending.")
                }
            } else {
                commentDao.insertComment(comment)
            }
        }
    }

    // Creating post
    fun createPost(content: String, category: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val me = userDao.getUserById("me") ?: return@launch
            val authorIdToUse = if (me.email.isNotBlank()) me.email.lowercase().trim() else "me"
            val post = PostEntity(
                authorId = authorIdToUse,
                authorName = me.name,
                authorUsername = me.username,
                authorRank = me.currentRank,
                authorTerritory = me.territory,
                authorFlag = me.flagEmoji,
                content = content,
                category = category,
                reputationImpact = me.reputationScore
            )

            // Submit to backend first if online to prevent duplication
            if (_isBackendConnected.value) {
                try {
                    val created = ApiClient.getService().createPost(post)
                    postDao.insertPost(created)
                    _toastMessage.emit("Broadcasting entry into the Public Square!")
                } catch (e: Exception) {
                    postDao.insertPost(post)
                    _toastMessage.emit("Broadcasting synchronized offline.")
                }
            } else {
                postDao.insertPost(post)
                _toastMessage.emit("Broadcasting entry into the Public Square!")
            }
        }
    }

    fun deletePost(postId: Int) {
        viewModelScope.launch {
            postDao.getPostById(postId)?.let { post ->
                postDao.deletePost(post)
                _toastMessage.emit("Post successfully deleted from Ledger.")
            }
        }
    }

    fun updatePostContent(postId: Int, newContent: String) {
        viewModelScope.launch {
            postDao.getPostById(postId)?.let { post ->
                val updated = post.copy(content = newContent)
                postDao.updatePost(updated)
                try {
                    ApiClient.getService().editPost(postId, mapOf("content" to newContent))
                    _toastMessage.emit("Post updated in the Public Square.")
                } catch (e: Exception) {
                    _toastMessage.emit("Post updated locally. Server synchronization pending.")
                }
            }
        }
    }

    // Direct Messaging - The 3 Connection Manager Implementation
    fun swapOrActivateRoom(roomId: Int) {
        viewModelScope.launch {
            val room = chatDao.getRoomById(roomId) ?: return@launch
            if (room.isActive) {
                selectRoom(roomId)
                return@launch
            }

            val activeRooms = chatDao.getActiveRoomsFlow().first()
            if (activeRooms.size >= 3) {
                _toastMessage.emit("Slots Full! Please terminate or archive one of your 3 Active Connections to activate this.")
                return@launch
            }

            val updated = room.copy(isActive = true, isWaiting = false)
            chatDao.updateRoom(updated)
            selectRoom(roomId)
            if (_isBackendConnected.value) {
                try {
                    ApiClient.getService().swapRoom(roomId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _toastMessage.emit("Focus Connected! Dialogue established.")
        }
    }

    fun archiveRoom(roomId: Int) {
        viewModelScope.launch {
            val room = chatDao.getRoomById(roomId) ?: return@launch
            val updated = room.copy(isActive = false, isWaiting = true)
            chatDao.updateRoom(updated)
            SocketManager.leaveRoom(roomId)
            if (_selectedRoomId.value == roomId) {
                _selectedRoomId.value = null
            }
            if (_isBackendConnected.value) {
                try {
                    ApiClient.getService().archiveRoom(roomId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _toastMessage.emit("Connection archived. Vacated slot for focus.")
        }
    }

    fun deleteRoom(roomId: Int) {
        viewModelScope.launch {
            val room = chatDao.getRoomById(roomId) ?: return@launch
            chatDao.markRoomAsDeleted(roomId)
            // chatDao.deleteRoomFromDb(roomId) // soft delete only so that it is not redownloaded and recreated during sync/polling
            chatDao.deleteMessagesForRoom(roomId)
            SocketManager.leaveRoom(roomId)
            if (_selectedRoomId.value == roomId) {
                _selectedRoomId.value = null
            }
            if (_isBackendConnected.value) {
                try {
                    ApiClient.getService().deleteChatRoom(roomId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _toastMessage.emit("Chat with ${room.participantName} deleted.")
        }
    }

    fun sendMessage(roomId: Int, text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val user = userDao.getUserById("me")
            val myName = user?.name ?: "Me"
            val myEmail = if (user != null && user.email.isNotBlank()) user.email else "me"

            val lastSent = chatDao.getMessageByRoomSenderAndText(roomId, "me", trimmed) ?:
                           chatDao.getMessageByRoomSenderAndText(roomId, myEmail, trimmed)
            if (lastSent != null && System.currentTimeMillis() - lastSent.timestamp < 3000) {
                return@launch
            }

            val timestamp = System.currentTimeMillis()
            val message = ChatMessageEntity(
                id = 0,
                roomId = roomId,
                senderId = myEmail,
                senderName = myName,
                messageText = trimmed,
                timestamp = timestamp,
                status = "Sent"
            )

            // Insert locally first for UI responsiveness
            chatDao.insertMessage(message)

            if (_isBackendConnected.value) {
                SocketManager.sendMessage(message)
            }

            val room = chatDao.getRoomById(roomId)
            if (room != null) {
                chatDao.updateRoom(room.copy(
                    lastMessage = trimmed,
                    lastMessageTime = timestamp
                ))
            }

            // Simulate Delivered and Read loops for realistic UX receipts
            val resolvedPartner = if (room != null) mapRoomForUser(room, myName).participantName else "CCC"
            viewModelScope.launch {
                // 1. Deliver the message in 1.2 second
                kotlinx.coroutines.delay(1200)
                onMessageDelivered(roomId, timestamp, resolvedPartner)

                // 2. Read the message in 2.2 seconds
                kotlinx.coroutines.delay(1000)
                onMessageRead(roomId, timestamp, resolvedPartner)
            }
        }
    }

    private fun mapRoomForUser(room: ChatRoomEntity, myName: String): ChatRoomEntity {
        val parts = room.participantName.split(" | ")
        if (parts.size >= 2) {
            val isCreator = parts[0].equals(myName, ignoreCase = true)
            val partnerName = if (isCreator) parts.last() else parts[0]
            val flags = room.participantFlag.split(" | ")
            val partnerFlag = if (flags.size >= 2) (if (isCreator) flags.last() else flags[0]) else room.participantFlag
            val ranks = room.participantRank.split(" | ")
            val partnerRank = if (ranks.size >= 2) (if (isCreator) ranks.last() else ranks[0]) else room.participantRank
            val territories = room.participantTerritory.split(" | ")
            val partnerTerritory = if (territories.size >= 2) (if (isCreator) territories.last() else territories[0]) else room.participantTerritory
            return room.copy(
                participantName = partnerName,
                participantFlag = partnerFlag,
                participantRank = partnerRank,
                participantTerritory = partnerTerritory,
                roomName = "Dialogue with $partnerName"
            )
        }
        return room
    }

    fun markRoomAsRead(roomId: Int) {
        viewModelScope.launch {
            val user = userDao.getUserById("me")
            val myName = user?.name ?: "Me"
            val msgs = chatDao.getMessagesForRoomFlow(roomId).first()
            val latestIncoming = msgs.filter {
                !it.senderName.equals(myName, ignoreCase = true) &&
                !it.senderName.equals("Me", ignoreCase = true) &&
                !it.senderId.startsWith("read_receipt") &&
                !it.senderId.startsWith("delivered_receipt")
            }.maxOfOrNull { it.timestamp }
            if (latestIncoming != null) {
                triggerReadReceipt(roomId, latestIncoming)
            }
        }
    }

    fun triggerReadReceipt(roomId: Int, timestamp: Long) {
        viewModelScope.launch {
            val user = userDao.getUserById("me")
            val myName = user?.name ?: "Me"
            val existingReceipt = chatDao.getReceipt(roomId, myName, "read")
            if (existingReceipt != null && existingReceipt.timestamp >= timestamp) {
                return@launch
            }
            val receipt = ChatReceiptEntity(
                id = "${roomId}_${myName}_read",
                roomId = roomId,
                senderName = myName,
                type = "read",
                timestamp = timestamp
            )
            chatDao.insertReceipt(receipt)
            if (_isBackendConnected.value) {
                SocketManager.sendReadReceipt(roomId, timestamp, myName)
            }
        }
    }

    fun triggerDeliveryReceipt(roomId: Int, timestamp: Long) {
        viewModelScope.launch {
            val user = userDao.getUserById("me")
            val myName = user?.name ?: "Me"
            val existingReceipt = chatDao.getReceipt(roomId, myName, "delivered")
            if (existingReceipt != null && existingReceipt.timestamp >= timestamp) {
                return@launch
            }
            val receipt = ChatReceiptEntity(
                id = "${roomId}_${myName}_delivered",
                roomId = roomId,
                senderName = myName,
                type = "delivered",
                timestamp = timestamp
            )
            chatDao.insertReceipt(receipt)
            if (_isBackendConnected.value) {
                SocketManager.sendDeliveryReceipt(roomId, timestamp, myName)
            }
        }
    }


    // Register candidacy for Sovereign Crown Elections
    fun registerElectionsCandidate(manifesto: String, vision: String) {
        viewModelScope.launch {
            var me = userDao.getUserById("me") ?: return@launch
            // Validation requirements: must maintain Verified status & high-tier marks
            if (_isBackendConnected.value && me.email.isNotBlank()) {
                try {
                    val serverProfile = ApiClient.getService().getUserProfile(me.email.lowercase().trim())
                    me = serverProfile.copy(id = "me")
                    userDao.insertUser(me)
                } catch (e: Exception) {
                    // Safe fallback
                }
            }

            if (me.knowledgeCredits < 150 || me.contributionCredits < 80) {
                _toastMessage.emit("Eligibility Fail: Need at least 150 KC & 80 CC to run for administrative office.")
                return@launch
            }

            val updated = me.copy(
                isCandidate = true,
                campaignManifesto = manifesto,
                campaignVision = vision,
                votesCount = maxOf(me.votesCount, 1) // Start with their own vote
            )
            userDao.insertUser(updated)
            
            if (_isBackendConnected.value && me.email.isNotBlank()) {
                try {
                    ApiClient.getService().updateUserProfile(me.email.lowercase().trim(), updated)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _toastMessage.emit("Dossier submitted! You are registered for the democratic Sovereign crown!")
        }
    }

    fun castVote(candidateId: String) {
        viewModelScope.launch {
            val me = userDao.getUserById("me") ?: return@launch
            if (me.hasVoted) {
                _toastMessage.emit("Covenant Guard: Every Citizen has exactly 1 democratic ballot per cycle.")
                return@launch
            }

            val candidate = userDao.getUserById(candidateId)
            if (candidate != null) {
                val updatedCandidate = candidate.copy(votesCount = candidate.votesCount + 1)
                val updatedMe = me.copy(hasVoted = true)
                userDao.insertUser(updatedCandidate)
                userDao.insertUser(updatedMe)
                
                if (_isBackendConnected.value) {
                    try {
                        if (candidate.email.isNotBlank()) {
                            ApiClient.getService().updateUserProfile(candidate.email.lowercase().trim(), updatedCandidate)
                        }
                        if (me.email.isNotBlank()) {
                            ApiClient.getService().updateUserProfile(me.email.lowercase().trim(), updatedMe)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                _toastMessage.emit("Ballot securely logged in the open Ledger!")
            }
        }
    }

    // Contribute to Imperial Missions
    fun contributeToMission(missionId: Int, points: Int) {
        viewModelScope.launch {
            val me = userDao.getUserById("me") ?: return@launch
            if (me.contributionCredits < points) {
                _toastMessage.emit("Insufficient Contribution Credits (CC) to allocate.")
                return@launch
            }

            // Update user credit and rank (resolves rank threshold update lag)
            saveUserAndRecalculateRank(me.copy(contributionCredits = me.contributionCredits - points))

            // Update mission progress
            // In our database we find the active mission
            val active = activeMissions.value.toMutableList()
            val m = active.find { it.id == missionId }
            if (m != null) {
                val updatedM = m.copy(
                    currentProgress = (m.currentProgress + points).coerceAtMost(m.targetValue)
                )
                missionDao.updateMission(updatedM)
                _toastMessage.emit("Contributed $points CC to: ${m.title}! Together we advance.")
            }
        }
    }

    // Custom method to award Knowledge Credits
    fun awardKnowledgeCredits(points: Int) {
        viewModelScope.launch {
            val me = userDao.getUserById("me") ?: return@launch
            saveUserAndRecalculateRank(me.copy(
                knowledgeCredits = me.knowledgeCredits + points
            ))
            _toastMessage.emit("+$points Knowledge Credits (KC) awarded!")
        }
    }

    // Custom method to set home territory and flag
    fun alignWithTerritory(name: String, flag: String) {
        viewModelScope.launch {
            val me = userDao.getUserById("me") ?: return@launch
            saveUserAndRecalculateRank(me.copy(
                territory = name,
                flagEmoji = flag
            ))
            _toastMessage.emit("Alignment synchronized with Territory of $name!")
        }
    }

    fun clearAllAppAndBackendData() {
        viewModelScope.launch {
            _toastMessage.emit("Clearing local database & cleaning remote backend...")
            
            if (_isBackendConnected.value) {
                try {
                    val rooms = ApiClient.getService().getChatRooms(null)
                    rooms.forEach { room ->
                        try {
                            ApiClient.getService().deleteChatRoom(room.id)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    val posts = ApiClient.getService().getPosts()
                    posts.forEach { post ->
                        try {
                            ApiClient.getService().deletePost(post.id)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            chatDao.deleteAllRooms()
            chatDao.deleteAllMessages()
            userDao.deleteAllUsers()
            postDao.deleteAllPosts()
            commentDao.deleteAllComments()
            visitorDao.deleteAllVisitors()
            missionDao.deleteAllMissions()
            legendsDao.deleteAllLegends()

            _selectedRoomId.value = null
            _currentScreen.value = Screen.Splash
            _selectedProfileUser.value = null
            _currentTab.value = DashboardTab.PublicSquare
            _missionsSubTab.value = 0
            _showEditProfileDialog.value = false
            _selectedCommentsPostId.value = null
            _showDailyWelcome.value = false
            _showExitSummary.value = false
            _showSyncSettingsDialog.value = false
            _isConnectingToBackend.value = false
            resetQuiz()

            _toastMessage.emit("Database cleared successfully. Starting clean slate!")
        }
    }

    // Database pre-population logic
    private fun prepopulateDb() {
        viewModelScope.launch {
            // Clean up legacy dummy IDs and stale on-disk cached mock users from previous compilations
            userDao.deleteUserById("user_test_citizen")
            userDao.deleteUserById("default_king")
            userDao.deleteUserById("default_queen")
            userDao.deleteUserById("default_king_id")
            userDao.deleteUserById("default_queen_id")
            userDao.deleteUserById("dr.linusvance@oneearth.io")
            userDao.deleteUserById("sovereignqueenelena@oneearth.io")
            userDao.deleteUserById("dr.louisvance@oneearth.io")
            userDao.deleteUserById("dr.louisvance")
            userDao.deleteUserById("dr.linusvance")
            userDao.deleteUserById("sovereignqueenelena")

            // Check if any missions exist to see if we need prepopulation
            val existingCount = missionDao.getMissionsCount()
            if (existingCount > 0) return@launch // Already pre-populated

            // Create global active mission objectives
            val missions = listOf(
                ImperialMissionEntity(id = 1, title = "Plant 100,000 Trees", description = "Territories competing of planting robust saplings in fragile global drylines.", targetMetric = "Saplings Logged", targetValue = 100000, currentProgress = 32800),
                ImperialMissionEntity(id = 2, title = "Mentor 10,000 Rural Students", description = "Teaching coding, scientific methodology, and historical philosophy across borders.", targetMetric = "Mentored Hours", targetValue = 10000, currentProgress = 5400),
                ImperialMissionEntity(id = 3, title = "Publish 500 Open Blueprints", description = "Distributing functional physical blueprints for sustainable water, power, and architecture.", targetMetric = "Blueprints Contributed", targetValue = 500, currentProgress = 195)
            )
            for (m in missions) {
                missionDao.insertMission(m)
            }

            // Create initial Hall of Legends historical records
            val legends = listOf(
                HallOfLegendsEntity(name = "Sovereign Queen Elena", role = "Supreme Diplomat", details = "Brokered the Unified Territory Accord of 2024 which ended digital border competition in favor of collaborative merit rankings.", achievement = "Sovereign Crown 2024-2025"),
                HallOfLegendsEntity(name = "Dr. Linus Vance", role = "Grand Educator", details = "Contributed over 4,500 Knowledge Credits. Authorized the Open Quantum Blueprints which are used today in school programs worldwide.", achievement = "Leaderboard Top Educator"),
                HallOfLegendsEntity(name = "Keren Smith", role = "Reforestation Catalyst", details = "Led the South Saharan Green Belt initiative. Under her administration, over 12,000 contribution hours were verified.", achievement = "Civic Legend")
            )
            for (l in legends) {
                legendsDao.insertLegend(l)
            }
        }
    }

    // ── Royal Profile State ──────────────────────────────────
    private val _viewedMonarchProfile = MutableStateFlow<MonarchProfileDTO?>(null)
    val viewedMonarchProfile: StateFlow<MonarchProfileDTO?> = _viewedMonarchProfile.asStateFlow()

    private val _viewedMonarchUser = MutableStateFlow<UserEntity?>(null)
    val viewedMonarchUser: StateFlow<UserEntity?> = _viewedMonarchUser.asStateFlow()

    private val _throneWorthiness = MutableStateFlow<ThroneWorthinessDTO?>(null)
    val throneWorthiness: StateFlow<ThroneWorthinessDTO?> = _throneWorthiness.asStateFlow()

    private val _monarchTimeline = MutableStateFlow<List<TimelineMilestone>>(emptyList())
    val monarchTimeline: StateFlow<List<TimelineMilestone>> = _monarchTimeline.asStateFlow()

    private val _royalDecrees = MutableStateFlow<List<RoyalDecreeDTO>>(emptyList())
    val royalDecrees: StateFlow<List<RoyalDecreeDTO>> = _royalDecrees.asStateFlow()

    private val _royalCouncil = MutableStateFlow<List<CouncilMemberDTO>>(emptyList())
    val royalCouncil: StateFlow<List<CouncilMemberDTO>> = _royalCouncil.asStateFlow()

    private val _hallOfMonarchs = MutableStateFlow<List<HallOfMonarchEntry>>(emptyList())
    val hallOfMonarchs: StateFlow<List<HallOfMonarchEntry>> = _hallOfMonarchs.asStateFlow()

    private val _showRoyalProfile = MutableStateFlow(false)
    val showRoyalProfile: StateFlow<Boolean> = _showRoyalProfile.asStateFlow()

    // Load full royal profile for a monarch user
    fun loadMonarchProfile(monarchId: String) {
        viewModelScope.launch {
            try {
                val dbUser = userDao.getUserById(monarchId) ?: allUsers.value.firstOrNull { it.id == monarchId }
                if (dbUser != null) {
                    _viewedMonarchUser.value = dbUser
                }

                // Generates highly responsive, premium offline/fallback state to prevent double-standard loading and instant closes!
                val fallbackWorthiness = ThroneWorthinessDTO(
                    percentage = 88f,
                    knowledgeComponent = 92f,
                    contributionComponent = 85f,
                    reputationComponent = 98f,
                    publicSupportComponent = 77f
                )
                val isQueen = dbUser?.gender?.equals("Female", ignoreCase = true) == true || dbUser?.currentRank?.equals("Queen", ignoreCase = true) == true
                val title = if (isQueen) "Queen" else "King"
                val crown = if (isQueen) "Royal Diamond" else "Imperial Gold"
                val aura = if (isQueen) "Diamond Moon Aura" else "Golden Sun Aura"

                val fallbackTimeline = listOf(
                    TimelineMilestone("Citizen", "Registered in OneEarth Network", System.currentTimeMillis() - 365 * 24 * 3600 * 1000L),
                    TimelineMilestone("Scholar", "Earned 500 Knowledge Credits", System.currentTimeMillis() - 180 * 24 * 3600 * 1000L),
                    TimelineMilestone("Noble", "Elected as Senior Leader of regional territory", System.currentTimeMillis() - 30 * 24 * 3600 * 1000L),
                    TimelineMilestone(title, "Formally crowned Sovereign of the United Earth Realities", System.currentTimeMillis() - 15 * 24 * 3600 * 1000L)
                )

                val calculatedLegacyPoints = if ((dbUser?.knowledgeCredits ?: 0) > 0 || (dbUser?.contributionCredits ?: 0) > 0) {
                    Math.max(1200, (dbUser?.knowledgeCredits ?: 0) * 2 + (dbUser?.contributionCredits ?: 0))
                } else {
                    if (isQueen) 1500 else 1800
                }

                val fallbackProfile = MonarchProfileDTO(
                    monarchCitizenId = monarchId,
                    title = title,
                    reignStartDate = System.currentTimeMillis() - 15 * 24 * 3600 * 1000L, // 15 days ago
                    reignEndDate = null,
                    territoryId = dbUser?.territory ?: "Global",
                    throneWorthiness = fallbackWorthiness,
                    legacyPoints = calculatedLegacyPoints,
                    citizensHelped = 412,
                    policiesInitiated = 14,
                    territoriesInfluenced = 6,
                    decreesPosted = 5,
                    crownType = crown,
                    auraType = aura,
                    approvalRating = 94,
                    royalCouncil = listOf("bbb", "ccc"),
                    timeline = fallbackTimeline
                )

                // Fill screen details instantly
                _viewedMonarchProfile.value = fallbackProfile
                _throneWorthiness.value = fallbackWorthiness
                _monarchTimeline.value = fallbackTimeline
                _royalDecrees.value = listOf(
                    RoyalDecreeDTO("DK-201", "Decree on High-Quality Knowledge Exchange", "Every constructive reply and inquiry is awarded positive reputation score and bonus merit multipliers.", monarchId, "active", dbUser?.territory ?: "Global", System.currentTimeMillis() - 10 * 24 * 3600 * 1000L),
                    RoyalDecreeDTO("DK-202", "Empowerment of the Regional Councils", "Official recognition of Duke and Duchess administration with localized sovereign authority.", monarchId, "active", dbUser?.territory ?: "Global", System.currentTimeMillis() - 5 * 24 * 3600 * 1000L)
                )
                _royalCouncil.value = listOf(
                    CouncilMemberDTO("Councilor-1", "bbb", "Chief Intellect Adviser", "Golden", System.currentTimeMillis() - 12 * 24 * 3600 * 1000L),
                    CouncilMemberDTO("Councilor-2", "ccc", "Sovereign Scribe & Historian", "Silver", System.currentTimeMillis() - 12 * 24 * 3600 * 1000L)
                )

                // Show dialog immediately
                _showRoyalProfile.value = true

                // Retrieve live updates safely without blocking or cancelling
                kotlinx.coroutines.supervisorScope {
                    launch {
                        try {
                            val response = ApiClient.getService().getMonarchProfile(monarchId)
                            if (response.success && response.profile != null) {
                                _viewedMonarchProfile.value = response.profile
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AppViewModel", "getMonarchProfile error: ${e.message}")
                        }
                    }
                    launch {
                        try {
                            val worthiness = ApiClient.getService().getThroneWorthiness(monarchId)
                            if (worthiness.success && worthiness.worthiness != null) {
                                _throneWorthiness.value = worthiness.worthiness
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AppViewModel", "getThroneWorthiness error: ${e.message}")
                        }
                    }
                    launch {
                        try {
                            val timeline = ApiClient.getService().getMonarchTimeline(monarchId)
                            if (timeline.isNotEmpty()) {
                                _monarchTimeline.value = timeline
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AppViewModel", "getMonarchTimeline error: ${e.message}")
                        }
                    }
                    launch {
                        try {
                            val decrees = ApiClient.getService().getRoyalDecrees(monarchId)
                            if (decrees.isNotEmpty()) {
                                _royalDecrees.value = decrees
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AppViewModel", "getRoyalDecrees error: ${e.message}")
                        }
                    }
                    launch {
                        try {
                            val council = ApiClient.getService().getRoyalCouncil(monarchId)
                            if (council.isNotEmpty()) {
                                _royalCouncil.value = council
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AppViewModel", "getRoyalCouncil error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "General error in loadMonarchProfile: ${e.message}")
            }
        }
    }

    fun loadHallOfMonarchs() {
        viewModelScope.launch {
            try {
                _hallOfMonarchs.value = ApiClient.getService().getHallOfMonarchsDetailed()
            } catch (e: Exception) { /* silent */ }
        }
    }

    fun dismissRoyalProfile() {
        _showRoyalProfile.value = false
        _viewedMonarchProfile.value = null
        _viewedMonarchUser.value = null
        _throneWorthiness.value = null
        _monarchTimeline.value = emptyList()
        _royalDecrees.value = emptyList()
        _royalCouncil.value = emptyList()
    }

    fun dismissHallOfMonarchs() {
        _hallOfMonarchs.value = emptyList()
    }
}

// Data models
data class QuizQuestion(
    val id: Int,
    val subject: String,
    val question: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    val explanation: String
)

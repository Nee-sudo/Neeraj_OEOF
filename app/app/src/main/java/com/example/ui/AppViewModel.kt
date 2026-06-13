package com.example.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val userDao = db.userDao()
    private val postDao = db.postDao()
    private val commentDao = db.commentDao()
    private val chatDao = db.chatDao()
    private val visitorDao = db.visitorDao()
    private val missionDao = db.missionDao()
    private val legendsDao = db.legendsDao()

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

    // Candidates Flow
    val electionCandidates: StateFlow<List<UserEntity>> = userDao.getCandidatesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Full Leaderboard users Flow
    val leaderboardUsers: StateFlow<List<UserEntity>> = userDao.getLeaderboardUsersFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All registered users except "me" for Find Friends
    val allFriends: StateFlow<List<UserEntity>> = userDao.getAllFriendsFlow()
        .combine(currentUserFlow) { friends, me ->
            if (me == null) {
                friends
            } else {
                val myEmail = me.email.lowercase().trim()
                val myUsername = me.username.lowercase().trim().removePrefix("@")
                friends.filter { friend ->
                    val friendEmail = friend.email.lowercase().trim()
                    val friendUsername = friend.username.lowercase().trim().removePrefix("@")
                    friendEmail != myEmail && friendUsername != myUsername
                }
            }
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

    private val _activeNotification = MutableStateFlow<InAppNotification?>(null)
    val activeNotification: StateFlow<InAppNotification?> = _activeNotification.asStateFlow()

    fun dismissNotification() {
        _activeNotification.value = null
    }

    val currentChatMessages: StateFlow<List<ChatMessageEntity>> = _selectedRoomId
        .flatMapLatest { id ->
            if (id != null) {
                chatDao.getMessagesForRoomFlow(id)
            } else {
                flowOf(emptyList())
            }
        }
        .combine(currentUserFlow) { list, user ->
            val myName = user?.name ?: "Me"
            val readReceipts = list.filter { it.senderId == "read_receipt" }
            val deliveredReceipts = list.filter { it.senderId == "delivered_receipt" }
            val partnerReadReceipts = readReceipts.filter { !it.senderName.equals(myName, ignoreCase = true) && !it.senderName.equals("Me", ignoreCase = true) }
            val partnerDeliveredReceipts = deliveredReceipts.filter { !it.senderName.equals(myName, ignoreCase = true) && !it.senderName.equals("Me", ignoreCase = true) }
            val maxPartnerReadTime = partnerReadReceipts.maxOfOrNull { it.timestamp } ?: 0L
            val maxPartnerDeliveredTime = partnerDeliveredReceipts.maxOfOrNull { it.timestamp } ?: 0L
            list.filter { !it.senderId.startsWith("read_receipt") && !it.senderId.startsWith("delivered_receipt") }
                .distinctBy { it.id }
                .distinctBy { it.senderId + "_" + it.messageText.trim() + "_" + (it.timestamp / 3000) }
                .map { msg ->
                    val isMe = msg.senderName.equals(myName, ignoreCase = true) || 
                               msg.senderName.equals("Me", ignoreCase = true) ||
                               msg.senderId == "me" ||
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
            ApiClient.updateBaseUrl("https://b768-2409-40e3-1ec-5736-dc6b-6c8f-a26-1f95.ngrok-free.app/")
            _backendBaseUrl.value = ApiClient.getBaseUrl()
        }
        // Prepopulate database with rich data for demonstration
        prepopulateDb()
        // Start backend connection monitoring
        startBackendHealthChecking()
        // Start real-time chat messages polling
        startRealtimeChatPolling()
        // Check if user is already logged in
        checkAutoLogin()
    }

    private fun checkAutoLogin() {
        viewModelScope.launch {
            val loggedInUser = userDao.getUserById("me")
            if (loggedInUser != null) {
                ApiClient.authToken = loggedInUser.token
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

    fun performLogout() {
        viewModelScope.launch {
            _selectedRoomId.value = null
            _activeNotification.value = null
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
            while (true) {
                if (_isAppInForeground.value) {
                    try {
                        val response = ApiClient.getService().checkHealth()
                        val wasConnected = _isBackendConnected.value
                        _isBackendConnected.value = (response.status == "ok" || response.status.isNotBlank())
                        if (!wasConnected && _isBackendConnected.value) {
                            _toastMessage.emit("Connected to One Earth Network Backend!")
                            syncAllWithBackend()
                        } else if (_isBackendConnected.value) {
                            // Periodic background sync of users, posts, and chats
                            syncAllWithBackend()
                        }
                    } catch (e: Exception) {
                        _isBackendConnected.value = false
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

    private fun startRealtimeChatPolling() {
        viewModelScope.launch {
            var allRoomsPollTick = 0
            while (true) {
                if (_isAppInForeground.value) {
                    try {
                        val roomId = _selectedRoomId.value
                        val user = userDao.getUserById("me")
                        if (user == null) {
                            _activeNotification.value = null
                            kotlinx.coroutines.delay(1500)
                            continue
                        }
                        val myName = user.name ?: "Me"
                        
                        // 1. Poll currently open/active chat room for immediate responsiveness (every 1.5s)
                        if (roomId != null && _isBackendConnected.value) {
                            val remoteMessages = ApiClient.getService().getChatMessages(roomId)
                            var latestIncomingTime = 0L
                            remoteMessages.forEach { msg ->
                                val localMatch = chatDao.getMessageByRoomSenderAndText(msg.roomId, msg.senderId, msg.messageText)
                                val alreadyExists = localMatch != null
                                if (localMatch != null && localMatch.id != msg.id) {
                                    chatDao.deleteMessage(localMatch)
                                }
                                chatDao.insertMessage(msg)
                                
                                // Check if it's an incoming message from the other participant
                                if (!msg.senderName.equals(myName, ignoreCase = true) && 
                                    !msg.senderName.equals("Me", ignoreCase = true) &&
                                    !msg.senderId.startsWith("read_receipt") && 
                                    !msg.senderId.startsWith("delivered_receipt")) {
                                    
                                    // Direct delivery receipt triggered as it has reached the client
                                    triggerDeliveryReceipt(roomId, msg.timestamp)
                                    
                                    if (!alreadyExists && !isChatRoomOpen(roomId) && msg.timestamp >= appStartedTime - 5000) {
                                        val notifKey = "${roomId}_${msg.timestamp}"
                                        if (!shownNotificationKeys.contains(notifKey)) {
                                            shownNotificationKeys.add(notifKey)
                                            _activeNotification.value = InAppNotification(
                                                roomId = roomId,
                                                senderName = msg.senderName,
                                                messageText = msg.messageText
                                            )
                                        }
                                    }
                                    
                                    if (msg.timestamp > latestIncomingTime) {
                                        latestIncomingTime = msg.timestamp
                                    }
                                }
                            }
                            if (latestIncomingTime > 0) {
                                triggerReadReceipt(roomId, latestIncomingTime)
                            }
                        }
                        
                        // 2. Poll other background active rooms to trigger Delivery status in the background (every ~4.5s)
                        allRoomsPollTick++
                        if (allRoomsPollTick >= 3 && _isBackendConnected.value) {
                            allRoomsPollTick = 0
                            // Safely collect active rooms
                            val activeRooms = chatDao.getActiveRoomsFlow().first()
                            activeRooms.forEach { activeRoom ->
                                if (activeRoom.id != roomId) {
                                    val remoteMsgs = ApiClient.getService().getChatMessages(activeRoom.id)
                                    remoteMsgs.forEach { msg ->
                                        val localMatch = chatDao.getMessageByRoomSenderAndText(msg.roomId, msg.senderId, msg.messageText)
                                        val alreadyExists = localMatch != null
                                        if (localMatch != null && localMatch.id != msg.id) {
                                            chatDao.deleteMessage(localMatch)
                                        }
                                        chatDao.insertMessage(msg)
                                        
                                        // Trigger Delivery receipt instantly
                                        if (!msg.senderName.equals(myName, ignoreCase = true) && 
                                            !msg.senderName.equals("Me", ignoreCase = true) &&
                                            !msg.senderId.startsWith("read_receipt") && 
                                            !msg.senderId.startsWith("delivered_receipt")) {
                                            
                                            triggerDeliveryReceipt(activeRoom.id, msg.timestamp)
          
                                             if (!alreadyExists && !isChatRoomOpen(activeRoom.id) && msg.timestamp >= appStartedTime - 5000) {
                                                 val notifKey = "${activeRoom.id}_${msg.timestamp}"
                                                 if (!shownNotificationKeys.contains(notifKey)) {
                                                     shownNotificationKeys.add(notifKey)
                                                     _activeNotification.value = InAppNotification(
                                                         roomId = activeRoom.id,
                                                         senderName = msg.senderName,
                                                         messageText = msg.messageText
                                                     )
                                                 }
                                             }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Fail-safe
                    }
                }
                kotlinx.coroutines.delay(1500) // Poll every 1.5 seconds for real-time responsiveness
            }
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
                            saveUserAndRecalculateRank(reg.toEntity("me").copy(id = "me"))
                        }
                    } catch (e: Exception) {
                        try {
                            val upd = ApiClient.getService().updateUserProfile(me.email.lowercase().trim(), me)
                            if (!upd.token.isNullOrBlank()) {
                                ApiClient.authToken = upd.token
                                saveUserAndRecalculateRank(upd.toEntity("me").copy(id = "me"))
                            }
                        } catch (updateEx: Exception) {
                            // Safe fallback
                        }
                    }
                }

                // 1. Sync Posts from Backend
                try {
                    val remotePosts = ApiClient.getService().getPosts()
                    postDao.deleteAllPosts()
                    if (remotePosts.isNotEmpty()) {
                        remotePosts.forEach { post ->
                            postDao.insertPost(post)
                        }
                    }
                } catch (postEx: Exception) {
                    android.util.Log.e("AppViewModel", "Sync Posts Error: ${postEx.message}", postEx)
                }

                // 2. Sync Chat Rooms from Backend
                try {
                    val remoteRooms = ApiClient.getService().getChatRooms(me?.name)
                    chatDao.deleteAllRooms()
                    if (remoteRooms.isNotEmpty()) {
                        remoteRooms.forEach { room ->
                            chatDao.insertRoom(room)
                        }
                    }
                } catch (roomEx: Exception) {
                    android.util.Log.e("AppViewModel", "Sync Chat Rooms Error: ${roomEx.message}", roomEx)
                }

                // 3. Sync All Users from Cloud to Local Friends List
                try {
                    val remoteUsers = ApiClient.getService().getAllUsers()
                    userDao.deleteAllNonMeUsers()
                    if (remoteUsers.isNotEmpty()) {
                        remoteUsers.forEach { userDto ->
                            val user = userDto.toEntity(userDto.email ?: "")
                            // Skip local logged-in user profile to prevent self duplicates
                            val matchesMe = me != null && (
                                user.email.lowercase().trim() == me.email.lowercase().trim() ||
                                user.username.lowercase().trim() == me.username.lowercase().trim()
                            )
                            if (!matchesMe && user.email.isNotBlank()) {
                                // Map the received user entity into sqlite with their email as secondary key ID
                                val friendEntity = user.copy(
                                    id = user.email.lowercase().trim()
                                )
                                userDao.insertUser(friendEntity)
                            }
                        }
                    }
                } catch (userEx: Exception) {
                    android.util.Log.e("AppViewModel", "Sync Users Error: ${userEx.message}", userEx)
                }
            } catch (e: Exception) {
                // Fail-safe
                android.util.Log.e("AppViewModel", "SyncAllWithBackend Error: ${e.message}", e)
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
        val calculatedRank = getRankFromCredits(user.knowledgeCredits, user.contributionCredits)
        val updated = user.copy(currentRank = calculatedRank)
        userDao.insertUser(updated)
        
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
            val user = userDao.getUserById(userId)
            if (user != null) {
                _selectedProfileUser.value = user
            } else {
                _selectedProfileUser.value = UserEntity(
                    id = userId,
                    name = userId.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                    username = "@$userId",
                    email = "$userId@oneearth.io",
                    dob = "1995-01-01",
                    territory = "Global",
                    flagEmoji = "🌍",
                    currentRank = "Noble Member",
                    knowledgeCredits = 120,
                    contributionCredits = 65,
                    reputationScore = 95,
                    bio = "Co-building a beautiful world with high-quality, constructive contributions."
                )
            }
        }
    }

    fun showProfileForUserByName(name: String, flag: String, rank: String, territory: String = "Global") {
        viewModelScope.launch {
            val id = when (name) {
                "Arjun Patel" -> "gandhi_avatar"
                "Clara Dupont" -> "clara_nobel"
                "Kofi Mensah" -> "kenya_leader"
                else -> name.lowercase().replace(" ", "_")
            }
            val existing = userDao.getUserById(id)
            if (existing != null) {
                _selectedProfileUser.value = existing
            } else {
                _selectedProfileUser.value = UserEntity(
                    id = id,
                    name = name,
                    username = "@" + name.lowercase().replace(" ", ""),
                    email = "${name.lowercase().replace(" ", "")}@oneearth.io",
                    dob = "1993-05-18",
                    territory = territory,
                    flagEmoji = flag,
                    currentRank = rank,
                    knowledgeCredits = 110,
                    contributionCredits = 80,
                    reputationScore = 97,
                    bio = "Proud citizen of our digital Empire. Active in $territory."
                )
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
            
            val seedMessage = ChatMessageEntity(
                roomId = finalRoomId,
                senderId = "other",
                senderName = user.name,
                messageText = "Greetings. I am honored to synchronize minds with you.",
                timestamp = System.currentTimeMillis(),
                status = "Read"
            )
            chatDao.insertMessage(seedMessage)
            
            _selectedRoomId.value = finalRoomId
            _currentTab.value = DashboardTab.Messaging
            closeProfileDialog()
            
            if (makeActive) {
                _toastMessage.emit("Focus Connection initialized with ${user.name}!")
            } else {
                _toastMessage.emit("${user.name} queued. Terminal capacity at limit (Max 3). Swap connections in Messaging tab!")
            }

            // Dispatch seed message to backend
            if (_isBackendConnected.value && finalRoomId > 0) {
                try {
                    ApiClient.getService().sendChatMessage(finalRoomId, seedMessage)
                } catch (e: Exception) {
                    // Fail-safe
                }
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
            val updated = me.copy(
                name = name,
                username = if (username.startsWith("@")) username else "@$username",
                bio = bio,
                territory = territory,
                flagEmoji = flagEmoji,
                profilePhoto = profilePhoto
            )
            saveUserAndRecalculateRank(updated)
            _toastMessage.emit("Profile paradigm synchronized successfully!")

            if (_isBackendConnected.value) {
                try {
                    ApiClient.getService().updateUserProfile(me.email.lowercase(), updated)
                } catch (e: Exception) {
                    // Fail-safe
                }
            }
        }
    }

    fun performLogin(identifier: String, passphraseInput: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val lowercaseId = identifier.trim().lowercase()

            // Try real server login first if backend is active
            if (_isBackendConnected.value) {
                try {
                    val remoteUserDto = ApiClient.getService().loginUser(LoginRequest(lowercaseId, passphraseInput))
                    val remoteUser = remoteUserDto.toEntity(lowercaseId)
                    ApiClient.authToken = remoteUserDto.token
                    val clonedMe = remoteUser.copy(id = "me")
                    saveUserAndRecalculateRank(clonedMe)
                    userDao.insertUser(remoteUser.copy(id = remoteUser.email.lowercase().trim()))
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
            
            // Try matching pre-populated user or registered user in Room
            val matchedUser = when {
                lowercaseId == "arjun" || lowercaseId == "gandhi" || lowercaseId.contains("arjun") -> userDao.getUserById("gandhi_avatar")
                lowercaseId == "clara" || lowercaseId.contains("clara") -> userDao.getUserById("clara_nobel")
                lowercaseId == "kofi" || lowercaseId.contains("kofi") -> userDao.getUserById("kenya_leader")
                else -> {
                    userDao.getUserByEmailOrUsername(lowercaseId)
                }
            }

            if (matchedUser != null) {
                // Check if passphrase matches
                if (matchedUser.passphrase == passphraseInput || (matchedUser.passphrase == "1234" && passphraseInput.isEmpty())) {
                    val clonedMe = matchedUser.copy(id = "me")
                    saveUserAndRecalculateRank(clonedMe)
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
                val existing = visitorDao.getVisitorRecord(hostUser.id, me.name)
                val visitor = if (existing != null) {
                    existing.copy(timestamp = System.currentTimeMillis())
                } else {
                    ProfileVisitorEntity(
                        hostUserId = hostUser.id,
                        visitorName = me.name,
                        visitorTerritory = me.territory,
                        visitorFlag = me.flagEmoji,
                        visitorRank = me.currentRank,
                        timestamp = System.currentTimeMillis()
                    )
                }
                visitorDao.insertVisitor(visitor)
                _toastMessage.emit("Notification dispatched: ${me.name} (${me.flagEmoji}) viewed ${hostUser.name}'s profile!")
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
        _selectedRoomId.value = roomId
        if (roomId != null) {
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
                val registeredUserDto = ApiClient.getService().registerUser(user)
                val registeredUser = registeredUserDto.toEntity("me")
                ApiClient.authToken = registeredUserDto.token
                saveUserAndRecalculateRank(registeredUser.copy(id = "me"))
                _isBackendConnected.value = true
                _toastMessage.emit("Security Passport registered online.")
            } catch (e: Exception) {
                // If register fails because user already exists on the server, try to update their profile
                try {
                    val updatedProfileDto = ApiClient.getService().updateUserProfile(user.email.lowercase().trim(), user)
                    val updatedProfile = updatedProfileDto.toEntity("me")
                    ApiClient.authToken = updatedProfileDto.token ?: ApiClient.authToken
                    saveUserAndRecalculateRank(updatedProfile.copy(id = "me"))
                    _isBackendConnected.value = true
                    _toastMessage.emit("Security Passport updated online.")
                } catch (ex: Exception) {
                    _toastMessage.emit("Registered locally. Cloud sync pending connection.")
                }
            }

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
                        _toastMessage.emit("+1 KC added to author's merit record!")
                        awardAuthorPoints(post.authorId, kDelta = 5, cDelta = 0)
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
                        _toastMessage.emit("+1 CC added to author's social action index!")
                        awardAuthorPoints(post.authorId, kDelta = 0, cDelta = 5)
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
                        _toastMessage.emit("Author's Reputation score elevated by +1%!")
                        incrementReputation(post.authorId, delta = 1)
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
                    // Fail-safe
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
                _toastMessage.emit("Post updated in the Public Square.")
            }
        }
    }

    // Direct Messaging - The 3 Connection Manager Implementation
    fun swapOrActivateRoom(roomId: Int) {
        viewModelScope.launch {
            val room = chatDao.getRoomById(roomId) ?: return@launch
            if (room.isActive) {
                _selectedRoomId.value = roomId
                markRoomAsRead(roomId)
                return@launch
            }

            val activeRooms = chatDao.getActiveRoomsFlow().first()
            if (activeRooms.size >= 3) {
                _toastMessage.emit("Slots Full! Please terminate or archive one of your 3 Active Connections to activate this.")
                return@launch
            }

            val updated = room.copy(isActive = true, isWaiting = false)
            chatDao.updateRoom(updated)
            _selectedRoomId.value = roomId
            markRoomAsRead(roomId)
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

            val message = ChatMessageEntity(
                roomId = roomId,
                senderId = myEmail,
                senderName = myName,
                messageText = trimmed,
                timestamp = System.currentTimeMillis(),
                status = "Sent"
            )

            if (_isBackendConnected.value) {
                try {
                    val savedMessageOnBackend = ApiClient.getService().sendChatMessage(roomId, message)
                    val localDraft = chatDao.getMessageByRoomSenderAndText(roomId, "me", trimmed) ?:
                                     chatDao.getMessageByRoomSenderAndText(roomId, myEmail, trimmed)
                    if (localDraft != null) {
                        chatDao.deleteMessage(localDraft)
                    }
                    chatDao.insertMessage(savedMessageOnBackend)
                } catch (e: Exception) {
                    chatDao.insertMessage(message)
                }
            } else {
                chatDao.insertMessage(message)
            }

            val room = chatDao.getRoomById(roomId)
            if (room != null) {
                chatDao.updateRoom(room.copy(
                    lastMessage = trimmed,
                    lastMessageTime = System.currentTimeMillis()
                ))
            }

            simulatePartnerReaction(roomId, message.timestamp)
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
            val existingReceipt = chatDao.getMessageByRoomSenderAndText(roomId, "read_receipt", myName)
            if (existingReceipt != null && existingReceipt.timestamp >= timestamp) {
                return@launch
            }
            val receipt = ChatMessageEntity(
                roomId = roomId,
                senderId = "read_receipt",
                senderName = myName,
                messageText = timestamp.toString(),
                timestamp = timestamp
            )
            chatDao.insertMessage(receipt)
            if (_isBackendConnected.value) {
                try {
                    ApiClient.getService().sendChatMessage(roomId, receipt)
                } catch (e: Exception) { }
            }
        }
    }

    fun triggerDeliveryReceipt(roomId: Int, timestamp: Long) {
        viewModelScope.launch {
            val user = userDao.getUserById("me")
            val myName = user?.name ?: "Me"
            val existingReceipt = chatDao.getMessageByRoomSenderAndText(roomId, "delivered_receipt", myName)
            if (existingReceipt != null && existingReceipt.timestamp >= timestamp) {
                return@launch
            }
            val receipt = ChatMessageEntity(
                roomId = roomId,
                senderId = "delivered_receipt",
                senderName = myName,
                messageText = timestamp.toString(),
                timestamp = timestamp
            )
            chatDao.insertMessage(receipt)
            if (_isBackendConnected.value) {
                try {
                    ApiClient.getService().sendChatMessage(roomId, receipt)
                } catch (e: Exception) { }
            }
        }
    }

    private fun simulatePartnerReaction(roomId: Int, messageTimestamp: Long) {
        viewModelScope.launch {
            val me = userDao.getUserById("me") ?: return@launch
            val room = chatDao.getRoomById(roomId) ?: return@launch
            val myName = me.name ?: "Me"
            val parts = room.participantName.split(" | ")
            val partnerName = if (parts.size == 2) {
                val isCreator = parts[0].equals(myName, ignoreCase = true)
                if (isCreator) parts[1] else parts[0]
            } else {
                room.participantName
            }

            // 1. WhatsApp Delivery indicator simulation
            kotlinx.coroutines.delay(800)
            val deliveryReceipt = ChatMessageEntity(
                roomId = roomId,
                senderId = "delivered_receipt",
                senderName = partnerName,
                messageText = messageTimestamp.toString(),
                timestamp = messageTimestamp
            )
            chatDao.insertMessage(deliveryReceipt)
            if (_isBackendConnected.value) {
                try {
                    ApiClient.getService().sendChatMessage(roomId, deliveryReceipt)
                } catch (e: Exception) { }
            }

            // 2. WhatsApp Read receipt indicator simulation (Blue ticks!)
            kotlinx.coroutines.delay(1000)
            val readReceipt = ChatMessageEntity(
                roomId = roomId,
                senderId = "read_receipt",
                senderName = partnerName,
                messageText = messageTimestamp.toString(),
                timestamp = messageTimestamp
            )
            chatDao.insertMessage(readReceipt)
            if (_isBackendConnected.value) {
                try {
                    ApiClient.getService().sendChatMessage(roomId, readReceipt)
                } catch (e: Exception) { }
            }
        }
    }

    private fun getDynamicPartnerReply(name: String): String {
        return listOf(
            "An interesting inquiry, citizen. We must work together on our home territory missions.",
            "I agree. True meritocracy in our digital Empire will restore social health.",
            "Let's focus our contributions on planting trees this weekend. What do you think?",
            "Wisdom is indeed the currency of absolute value. Let's research more on this.",
            "Your profile visitor ledger is looking great. I left an endorsement for your Rank progress."
        ).random()
    }

    // Register candidacy for Sovereign Crown Elections
    fun registerElectionsCandidate(manifesto: String, vision: String) {
        viewModelScope.launch {
            var me = userDao.getUserById("me") ?: return@launch
            // Validation requirements: must maintain Verified status & high-tier marks
            if (_isBackendConnected.value && me.email.isNotBlank()) {
                try {
                    val serverProfileDto = ApiClient.getService().getUserProfile(me.email.lowercase().trim())
                    me = serverProfileDto.toEntity("me").copy(id = "me")
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
        if (true) return // Completely clean start, no predefined users inside the database
        viewModelScope.launch {
            // Clean up legacy dummy IDs from previous compilations
            userDao.deleteUserById("user_test_citizen")

            val existingMe = userDao.getUserById("me")
            if (existingMe != null) return@launch // Already pre-populated

            // Create some sample users as administrative figures and mock candidates
            val users = listOf(
                UserEntity(
                    id = "gandhi_avatar",
                    name = "Arjun Patel",
                    username = "@arjun_vision",
                    email = "arjun@oneearth.io",
                    dob = "1994-04-12",
                    territory = "India",
                    flagEmoji = "🇮🇳",
                    gender = "Male",
                    currentRank = "Royal Candidate",
                    knowledgeCredits = 340,
                    contributionCredits = 180,
                    reputationScore = 99,
                    personalityTraits = "Visionary,Leader,Philosopher,Teacher,Humanitarian",
                    bio = "Coordinating organic local waste recycling systems in Maharashtra. Let's build the empire from the soil.",
                    isCandidate = true,
                    campaignVision = "United Global Ecological Safeguards",
                    campaignManifesto = "My campaign centers around tying modern technology directly with micro-farming community actions. Let's make every territory garden green.",
                    votesCount = 12
                ),
                UserEntity(
                    id = "clara_nobel",
                    name = "Clara Dupont",
                    username = "@clara_sage",
                    email = "clara@oneearth.io",
                    dob = "1990-11-20",
                    territory = "France",
                    flagEmoji = "🇫🇷",
                    gender = "Female",
                    currentRank = "Guardian",
                    knowledgeCredits = 410,
                    contributionCredits = 150,
                    reputationScore = 98,
                    personalityTraits = "Scientist,Educator,Philosopher,Visionary,Creator",
                    bio = "Physics educator. Translating scientific literacy into open global civic solutions.",
                    isCandidate = true,
                    campaignVision = "Absolute Scientific Open Access",
                    campaignManifesto = "Education is the foundation of meritocratic leadership. I intend to build the Open Digital Library with zero economic payload.",
                    votesCount = 15
                ),
                UserEntity(
                    id = "kenya_leader",
                    name = "Kofi Mensah",
                    username = "@kofi_builder",
                    email = "kofi@oneearth.io",
                    dob = "1988-06-03",
                    territory = "Kenya",
                    flagEmoji = "🇰🇪",
                    gender = "Male",
                    currentRank = "Contributor",
                    knowledgeCredits = 175,
                    contributionCredits = 210,
                    reputationScore = 97,
                    personalityTraits = "Builder,Humanitarian,Leader,Explorer,Creator",
                    bio = "Constructing modular solar installations in dry-zones across East Africa.",
                    isCandidate = true,
                    campaignVision = "Grassroots Infrastructure Mobilization",
                    campaignManifesto = "Action outweighs debate. I will introduce regional solar and clean water blueprints as active Imperial missions for collective credit.",
                    votesCount = 8
                ),
                UserEntity(
                    id = "test@oneearth.io",
                    name = "Test Citizen",
                    username = "@test_citizen",
                    email = "test@oneearth.io",
                    dob = "1999-01-01",
                    territory = "United States",
                    flagEmoji = "🇺🇸",
                    gender = "Male",
                    currentRank = "Explorer",
                    knowledgeCredits = 120,
                    contributionCredits = 60,
                    reputationScore = 98,
                    personalityTraits = "Explorer,Builder,Creator",
                    bio = "Dedicated pioneer testing our One Earth connection hub.",
                    isCandidate = false,
                    campaignVision = "",
                    campaignManifesto = "",
                    votesCount = 0,
                    hasVoted = false,
                    onboardingCompleted = true,
                    citizenOathAccepted = true,
                    followers = 10,
                    following = 15,
                    profilePhoto = "",
                    passphrase = "password123"
                )
            )
            for (u in users) {
                userDao.insertUser(u)
            }

            // Create post entries for feed
            val posts = listOf(
                PostEntity(
                    id = 1,
                    authorId = "gandhi_avatar",
                    authorName = "Arjun Patel",
                    authorUsername = "@arjun_vision",
                    authorRank = "Royal Candidate",
                    authorTerritory = "India",
                    authorFlag = "🇮🇳",
                    content = "We have completed the micro-reservoir blueprint. By shifting our daily action from empty popularity loops, we spent 40 hours building an irrigation system for 3 smallholder farms. Here is the open-access guide to local sand-dams.",
                    category = "Article",
                    knowledgeValue = 18,
                    contributionProof = 24,
                    reputationImpact = 99
                ),
                PostEntity(
                    id = 2,
                    authorId = "clara_nobel",
                    authorName = "Clara Dupont",
                    authorUsername = "@clara_sage",
                    authorRank = "Guardian",
                    authorTerritory = "France",
                    authorFlag = "🇫🇷",
                    content = "What is the collective responsibility of technological builders when designing attention architectures? We should explicitly reject arbitrary metric casinos in favor of qualitative dialogue focus. Join our structural inquiry.",
                    category = "Inquiry",
                    knowledgeValue = 35,
                    contributionProof = 12,
                    reputationImpact = 98
                ),
                PostEntity(
                    id = 3,
                    authorId = "kenya_leader",
                    authorName = "Kofi Mensah",
                    authorUsername = "@kofi_builder",
                    authorRank = "Contributor",
                    authorTerritory = "Kenya",
                    authorFlag = "🇰🇪",
                    content = "The solar micro-grid model for Lake Victoria fishing villages has successfully completed 120 run-hours. To combat grid-vulnerability, we implemented locally serviceable battery units. Looking for developers to join the telemetry code debaters.",
                    category = "Debate",
                    knowledgeValue = 15,
                    contributionProof = 32,
                    reputationImpact = 97
                )
            )
            for (p in posts) {
                postDao.insertPost(p)
            }

            // Create initial commenting entries
            val comments = listOf(
                CommentEntity(postId = 1, authorName = "Clara Dupont", authorFlag = "🇫🇷", authorRank = "Guardian", content = "This sand-dam technique is spectacular Arjun. The physics of sediment water retention is pristine.", timestamp = System.currentTimeMillis() - 7000000),
                CommentEntity(postId = 1, authorName = "Kofi Mensah", authorFlag = "🇰🇪", authorRank = "Contributor", content = "Can we customize these sand-dams for clay-heavy soil profiles? Let's check with some geologists in Kenya.", timestamp = System.currentTimeMillis() - 3000000),
                CommentEntity(postId = 2, authorName = "Arjun Patel", authorFlag = "🇮🇳", authorRank = "Noble", content = "Indeed Clara. Depth of connection directly protects systemic human sanity.", timestamp = System.currentTimeMillis() - 10000000)
            )
            for (c in comments) {
                commentDao.insertComment(c)
            }

            // Create mock Chat rooms representing Active Connections (max 3 max)
            val chatRooms = listOf(
                ChatRoomEntity(
                    roomName = "Focus Group India",
                    participantName = "Arjun Patel",
                    participantFlag = "🇮🇳",
                    participantRank = "Royal Candidate",
                    participantTerritory = "India",
                    lastMessage = "Let's review the sand-dam schematics this afternoon.",
                    lastMessageTime = System.currentTimeMillis() - 40000,
                    isActive = true,
                    isWaiting = false
                ),
                ChatRoomEntity(
                    roomName = "Mentorship Science",
                    participantName = "Clara Dupont",
                    participantFlag = "🇫🇷",
                    participantRank = "Guardian",
                    participantTerritory = "France",
                    lastMessage = "I recommend checking out Platonic Meritocracy in the library.",
                    lastMessageTime = System.currentTimeMillis() - 600000,
                    isActive = true,
                    isWaiting = false
                ),
                ChatRoomEntity(
                    roomName = "Alliance Alliance",
                    participantName = "Kofi Mensah",
                    participantFlag = "🇰🇪",
                    participantRank = "Contributor",
                    participantTerritory = "Kenya",
                    lastMessage = "The solar telemetry code is compiled.",
                    lastMessageTime = System.currentTimeMillis() - 1200000,
                    isActive = true,
                    isWaiting = false
                ),
                // This room resides in the Waiting Queue because the user already has 3 active chats!
                ChatRoomEntity(
                    roomName = "Design Council",
                    participantName = "Yuki Tanaka",
                    participantFlag = "🇯🇵",
                    participantRank = "Noble",
                    participantTerritory = "Japan",
                    lastMessage = "Hey there! I would love to partner up for the global education mission.",
                    lastMessageTime = System.currentTimeMillis() - 3600000,
                    isActive = false,
                    isWaiting = true
                ),
                ChatRoomEntity(
                    roomName = "Ecosystem Sync",
                    participantName = "Mateo Silva",
                    participantFlag = "🇧🇷",
                    participantRank = "Baron",
                    participantTerritory = "Brazil",
                    lastMessage = "Our reforestation team is logging points. Connect with us!",
                    lastMessageTime = System.currentTimeMillis() - 7200000,
                    isActive = false,
                    isWaiting = true
                )
            )
            for (r in chatRooms) {
                val rId = chatDao.insertRoom(r).toInt()
                // Insert initial message history
                if (r.isActive) {
                    chatDao.insertMessage(ChatMessageEntity(roomId = rId, senderId = "other", senderName = r.participantName, messageText = r.lastMessage, timestamp = r.lastMessageTime - 2000))
                } else {
                    chatDao.insertMessage(ChatMessageEntity(roomId = rId, senderId = "other", senderName = r.participantName, messageText = r.lastMessage, timestamp = r.lastMessageTime))
                }
            }

            // Create initial profile visitors logs
            val visitors = listOf(
                ProfileVisitorEntity(visitorName = "Arjun Patel", visitorTerritory = "India", visitorFlag = "🇮🇳", visitorRank = "Noble", timestamp = System.currentTimeMillis() - 120000),
                ProfileVisitorEntity(visitorName = "Clara Dupont", visitorTerritory = "France", visitorFlag = "🇫🇷", visitorRank = "Guardian", timestamp = System.currentTimeMillis() - 360000),
                ProfileVisitorEntity(visitorName = "Yuki Tanaka", visitorTerritory = "Japan", visitorFlag = "🇯🇵", visitorRank = "Noble", timestamp = System.currentTimeMillis() - 1800000),
                ProfileVisitorEntity(visitorName = "Mateo Silva", visitorTerritory = "Brazil", visitorFlag = "🇧🇷", visitorRank = "Baron", timestamp = System.currentTimeMillis() - 3600000)
            )
            for (v in visitors) {
                visitorDao.insertVisitor(v)
            }

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

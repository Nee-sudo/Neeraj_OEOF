package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.AppViewModel
import com.example.ui.MainLayout
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.AppThemeMode

import androidx.compose.runtime.collectAsState
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Safe Firebase initialization logging verification as per Safe FCM Implementation Guide
        try {
            val apps = com.google.firebase.FirebaseApp.getApps(this)
            val defaultApp = apps.firstOrNull()
            if (defaultApp?.options?.gcmSenderId?.isNullOrBlank() != false) {
                android.util.Log.e("FirebaseInit", 
                    "❌ CRITICAL: Firebase not properly initialized. " +
                    "Check google-services.json exists in app/ directory and is valid.")
            } else {
                android.util.Log.i("FirebaseInit", 
                    "✓ Firebase initialized with GCM Sender ID: ${defaultApp.options.gcmSenderId}")
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseInit", "Firebase init error: ${e.message}", e)
        }

        // 2. Request Android 13+ POST_NOTIFICATIONS permissions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }

        enableEdgeToEdge()
        setContent {
            val themeMode = viewModel.themeMode.collectAsState().value
            MyApplicationTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainLayout(viewModel)
                }
            }
        }

        handleIntentExtras(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.setAppInForeground(true)
        handleIntentExtras(intent)
    }

    override fun onPause() {
        super.onPause()
        viewModel.setAppInForeground(false)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: android.content.Intent?) {
        if (intent == null) return
        val type = intent.getStringExtra("deep_link_type") ?: return
        android.util.Log.i("DeepLink", "Processing deep-link intent of type: $type")

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val loggedInUser = db.userDao().getUserById("me")
            if (loggedInUser != null) {
                when (type) {
                    "message" -> {
                        val roomIdStr = intent.getStringExtra("room_id") ?: intent.getIntExtra("room_id", -1).toString()
                        val roomId = roomIdStr.toIntOrNull() ?: -1
                        if (roomId != -1) {
                            viewModel.selectTab(com.example.ui.DashboardTab.Messaging)
                            viewModel.selectRoom(roomId)
                        }
                    }
                    "reaction", "comment" -> {
                        val postIdStr = intent.getStringExtra("post_id") ?: intent.getIntExtra("post_id", -1).toString()
                        val postId = postIdStr.toIntOrNull() ?: -1
                        if (postId != -1) {
                            viewModel.selectTab(com.example.ui.DashboardTab.PublicSquare)
                            viewModel.selectPostForComments(postId)
                        }
                    }
                    "election" -> {
                        viewModel.selectTab(com.example.ui.DashboardTab.ProfileAndElections)
                    }
                    "profile_view" -> {
                        val userId = intent.getStringExtra("user_id")
                        if (!userId.isNullOrBlank()) {
                            viewModel.showProfileForUser(userId)
                        }
                    }
                }
            }
        }
    }
}

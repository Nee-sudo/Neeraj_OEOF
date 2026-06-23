package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import com.example.data.*
import com.example.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun MainLayout(viewModel: AppViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val showSyncDialog by viewModel.showSyncSettingsDialog.collectAsState()
    val activeNotification by viewModel.activeNotification.collectAsState()

    // Request notification permission dynamically in Android 13+
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            android.util.Log.i("NotificationPermission", "Notification permission granted: $isGranted")
        }
        LaunchedEffect(Unit) {
            val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewModel.setAppInForeground(true)
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.setAppInForeground(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Collect Toast/Notification events from ViewModel
    LaunchedEffect(key1 = true) {
        viewModel.toastMessage.collectLatest { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepOceanSapphire, CharcoalObsidian)
                )
            )
    ) {
        // App Navigation Routing Controller
        when (currentScreen) {
            Screen.Splash -> SplashScreenView(viewModel)
            Screen.OnboardingCarousel -> OnboardingCarouselView(viewModel)
            Screen.Registration -> RegistrationView(viewModel)
            Screen.Login -> LoginView(viewModel)
            Screen.TerritorySelection -> TerritorySelectionView(viewModel)
            Screen.PersonalitySetup -> PersonalitySetupView(viewModel)
            Screen.CitizenOath -> CitizenOathView(viewModel)
            Screen.MainDashboard -> MainDashboardView(viewModel, snackbarHostState)
        }

        // Floating Connection Sync Status / Settings icon for onboarding & log-in screens
        if (currentScreen != Screen.MainDashboard) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isConnected by viewModel.isBackendConnected.collectAsState()
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isConnected) EmeraldSuccess else CrimsonRep, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = { viewModel.setShowSyncSettingsDialog(true) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Sync Config",
                        tint = RegalGold,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Floating Toast Notification
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .padding(bottom = 80.dp),
            snackbar = { snap ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = VelvetCard),
                    border = BorderStroke(1.dp, RegalGold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = "Imperial Notice",
                            tint = RegalGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = snap.visuals.message,
                            color = GhostWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        )

        // Instagram-style dropdown in-app notification banner on top
        AnimatedVisibility(
            visible = activeNotification != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 12.dp, end = 12.dp)
        ) {
            activeNotification?.let { notification ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            if (notification.roomId > 0) {
                                // Tap notification to seamlessly open that specific chat room!
                                viewModel.navigateTo(Screen.MainDashboard)
                                viewModel.selectTab(DashboardTab.Messaging)
                                viewModel.selectRoom(notification.roomId)
                                viewModel.dismissNotification()
                            } else {
                                // Tap general notification to seamlessly route to the notifications alerts center
                                viewModel.navigateTo(Screen.MainDashboard)
                                viewModel.selectTab(DashboardTab.ProfileAndElections)
                                viewModel.showNotificationsDialogFlow.tryEmit(true)
                                viewModel.dismissNotification()
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VelvetCard.copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, RegalGold.copy(alpha = 0.8f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = RegalGold.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = RegalGold,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(20.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = notification.senderName,
                                    color = GhostWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "now",
                                    color = MutedSlate,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = notification.messageText,
                                color = GhostWhite.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        IconButton(
                            onClick = { viewModel.dismissNotification() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MutedSlate,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSyncDialog) {
        SyncSettingsDialogView(viewModel)
    }
}

// ============================================================================
// 1. SPLASH SCREEN (Deep holographic rotational aesthetic)
// ============================================================================
@Composable
fun SplashScreenView(viewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    var animationState by remember { mutableStateOf(0) }

    // Start Sequential Word animation
    LaunchedEffect(key1 = true) {
        kotlinx.coroutines.delay(600)
        animationState = 1 // Show ONE EARTH
        kotlinx.coroutines.delay(800)
        animationState = 2 // Show ONE FAMILY
        kotlinx.coroutines.delay(800)
        animationState = 3 // Show ENTER THE EMPIRE
    }

    // Rotating Core effect
    val infiniteTransition = rememberInfiniteTransition(label = "Globe rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Holographic Globe representation
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(220.dp)
        ) {
            // Radiant gold background glow
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(RegalGold.copy(alpha = 0.15f), shape = CircleShape)
            )

            // Dynamic rotating orbit icons
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Drawing planetary abstract rings
                drawCircle(
                    color = RegalGold.copy(alpha = 0.4f),
                    radius = 90.dp.toPx(),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * density)
                )
                drawCircle(
                    color = ElectricBlue.copy(alpha = 0.3f),
                    radius = 110.dp.toPx(),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f * density)
                )
            }

            // Central Crown Crest
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = "Cosmic Globe",
                tint = RegalGold,
                modifier = Modifier
                    .size(100.dp)
            )

            Icon(
                imageVector = Icons.Default.MilitaryTech,
                contentDescription = "Embassy Shield",
                tint = LustrousAmber,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = (-15).dp)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Sequenced typography fades
        AnimatedVisibility(
            visible = animationState >= 1,
            enter = fadeIn(tween(600)) + slideInVertically { 20 }
        ) {
            Text(
                text = "ONE EARTH",
                color = GhostWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        AnimatedVisibility(
            visible = animationState >= 2,
            enter = fadeIn(tween(600)) + slideInVertically { 20 }
        ) {
            Text(
                text = "ONE FAMILY",
                color = RegalGold,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(
            visible = animationState >= 3,
            enter = fadeIn(tween(600)) + slideInVertically { 20 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "A Meritocratic Digital Civilization",
                    color = MutedSlate,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(36.dp))
                Button(
                    onClick = { viewModel.navigateTo(Screen.OnboardingCarousel) },
                    colors = ButtonDefaults.buttonColors(containerColor = RegalGold, contentColor = CharcoalObsidian),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, LightGold),
                    modifier = Modifier.width(220.dp)
                ) {
                    Text(
                        text = "ENTER THE EMPIRE",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.navigateTo(Screen.Login) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RegalGold),
                    border = BorderStroke(1.dp, RegalGold),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.width(220.dp)
                ) {
                    Text(
                        text = "CITIZEN SIGN IN",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

// ============================================================================
// 2. ONBOARDING CAROUSEL (Symmetrical Gold framed Slides)
// ============================================================================
@Composable
fun OnboardingCarouselView(viewModel: AppViewModel) {
    var activeSlideIndex by remember { mutableStateOf(0) }

    val slides = listOf(
        OnboardingSlide(
            title = "Wisdom over Popularity",
            desc = "In this digital Empire, character and verifiable knowledge wield greater structural influence than cheap follow metrics or clout chase loops.",
            icon = Icons.Default.MenuBook,
            themeColor = RegalGold
        ),
        OnboardingSlide(
            title = "Hierarchy by Merit",
            desc = "Every citizen starts their journey equal. Ascend from Citizen to Guardian, Noble, or Prince purely based on educational and community service credits.",
            icon = Icons.Default.EmojiEvents,
            themeColor = LustrousAmber
        ),
        OnboardingSlide(
            title = "Represent Your Territory",
            desc = "Engage in friendly competitions, resolve planetary challenges, coordinate reforestation, and run for the democratic Sovereign crown of the world.",
            icon = Icons.Default.Map,
            themeColor = ElectricBlue
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Stars, contentDescription = null, tint = RegalGold, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "ONE EARTH ONE FAMILY",
                color = GhostWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }

        // Middle slide card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 32.dp),
            colors = CardDefaults.cardColors(containerColor = VelvetCard),
            border = BorderStroke(1.5.dp, slides[activeSlideIndex].themeColor),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(slides[activeSlideIndex].themeColor.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = slides[activeSlideIndex].icon,
                        contentDescription = null,
                        tint = slides[activeSlideIndex].themeColor,
                        modifier = Modifier.size(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = slides[activeSlideIndex].title,
                    color = GhostWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = slides[activeSlideIndex].desc,
                    color = GhostWhite.copy(alpha = 0.8f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }

        // Indicator dots & button row
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Indicator row
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                slides.forEachIndexed { idx, slide ->
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(height = 8.dp, width = if (activeSlideIndex == idx) 24.dp else 8.dp)
                            .background(
                                color = if (activeSlideIndex == idx) slide.themeColor else MutedSlate.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }

            // Nav actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeSlideIndex > 0) {
                    TextButton(onClick = { activeSlideIndex-- }) {
                        Text("BACK", color = MutedSlate, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(modifier = Modifier.width(60.dp))
                }

                Button(
                    onClick = {
                        if (activeSlideIndex < slides.size - 1) {
                            activeSlideIndex++
                        } else {
                            viewModel.navigateTo(Screen.Registration)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = slides[activeSlideIndex].themeColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.width(140.dp)
                ) {
                    Text(
                        text = if (activeSlideIndex == slides.size - 1) "FOUND ACCOUNT" else "NEXT",
                        fontWeight = FontWeight.Bold,
                        color = CharcoalObsidian
                    )
                }
            }
        }
    }
}

// ============================================================================
// 3. REGISTRATION VIEW (Noble Dark Symmetrical Form + OTP Verification)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationView(viewModel: AppViewModel) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var dob by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("Male") }
    var selectedFlag by remember { mutableStateOf("🇮🇳") }
    var showAllFlagsDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedRegionFilter by remember { mutableStateOf("All") }

    val isEmailValid = remember(email) {
        email.isEmpty() || email.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$"))
    }

    // OTP verification modal state
    var showOtpDialog by remember { mutableStateOf(false) }
    var inputOtp by remember { mutableStateOf("") }
    var isVerified by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Icon(
            imageVector = Icons.Default.AccountBalance,
            contentDescription = null,
            tint = RegalGold,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Create Citizen Dossier",
            color = GhostWhite,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Secure your place in the planetary ledger",
            color = MutedSlate,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        // Symmetrical Gold Styled Inputs
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.replace("|", "") },
            label = { Text("Full Legal Name") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = RegalGold) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RegalGold,
                unfocusedBorderColor = MutedSlate.copy(alpha = 0.5f),
                focusedLabelColor = RegalGold,
                unfocusedLabelColor = MutedSlate,
                focusedTextColor = GhostWhite,
                unfocusedTextColor = GhostWhite
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it.filter { !it.isWhitespace() } },
            label = { Text("Unique Handle (@username)") },
            leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = RegalGold) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RegalGold,
                unfocusedBorderColor = MutedSlate.copy(alpha = 0.5f),
                focusedLabelColor = RegalGold,
                unfocusedLabelColor = MutedSlate,
                focusedTextColor = GhostWhite,
                unfocusedTextColor = GhostWhite
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.filter { !it.isWhitespace() } },
            label = { Text("Secure Email Address") },
            leadingIcon = { Icon(Icons.Default.Mail, contentDescription = null, tint = RegalGold) },
            isError = !isEmailValid,
            supportingText = if (!isEmailValid) {
                { Text("Please enter a complete email address (e.g., citizen@oneearth.io)") }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RegalGold,
                unfocusedBorderColor = MutedSlate.copy(alpha = 0.5f),
                focusedLabelColor = RegalGold,
                unfocusedLabelColor = MutedSlate,
                focusedTextColor = GhostWhite,
                unfocusedTextColor = GhostWhite,
                errorBorderColor = MaterialTheme.colorScheme.error,
                errorLabelColor = MaterialTheme.colorScheme.error
            ),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = dob,
            onValueChange = { newValue ->
                val digitsAndDashes = newValue.filter { it.isDigit() || it == '-' }
                if (digitsAndDashes.length <= 10) {
                    dob = digitsAndDashes
                }
            },
            label = { Text("Date of Birth (YYYY-MM-DD)") },
            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, tint = RegalGold) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RegalGold,
                unfocusedBorderColor = MutedSlate.copy(alpha = 0.5f),
                focusedLabelColor = RegalGold,
                unfocusedLabelColor = MutedSlate,
                focusedTextColor = GhostWhite,
                unfocusedTextColor = GhostWhite
            ),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        // Gender Selection Segment
        Text(
            text = "Gender Identity",
            style = androidx.compose.ui.text.TextStyle(
                color = MutedSlate,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).align(Alignment.Start)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val genderOptions = listOf("Male", "Female", "Other")
            genderOptions.forEach { option ->
                val isSelected = selectedGender == option
                val emoji = when (option) {
                    "Male" -> "♂️"
                    "Female" -> "♀️"
                    else -> "✨"
                }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) RegalGold.copy(alpha = 0.15f) else CharcoalObsidian
                    ),
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = if (isSelected) RegalGold else MutedSlate.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable { selectedGender = option }
                        .testTag("gender_option_$option")
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(emoji, modifier = Modifier.padding(end = 4.dp), fontSize = 14.sp)
                            Text(
                                text = option,
                                color = if (isSelected) RegalGold else GhostWhite,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("System Entry Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = RegalGold) },
            trailingIcon = {
                val icon = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(icon, contentDescription = "Toggle password visibility", tint = RegalGold)
                }
            },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RegalGold,
                unfocusedBorderColor = MutedSlate.copy(alpha = 0.5f),
                focusedLabelColor = RegalGold,
                unfocusedLabelColor = MutedSlate,
                focusedTextColor = GhostWhite,
                unfocusedTextColor = GhostWhite
            ),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        // Territory / Country Flag Selection
        Text(
            text = "Broadcast Territory Flag",
            style = androidx.compose.ui.text.TextStyle(
                color = MutedSlate,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).align(Alignment.Start)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val flags = listOf(
                Pair("🇮🇳", "India"),
                Pair("🇫🇷", "France"),
                Pair("🇰🇪", "Kenya"),
                Pair("🇧🇷", "Brazil"),
                Pair("🇯🇵", "Japan")
            )
            flags.forEach { pair ->
                val isSelected = selectedFlag == pair.first
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) RegalGold.copy(alpha = 0.15f) else CharcoalObsidian
                    ),
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = if (isSelected) RegalGold else MutedSlate.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clickable { selectedFlag = pair.first }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(pair.first, fontSize = 20.sp)
                        Text(pair.second, color = if (isSelected) RegalGold else MutedSlate, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // More Countries / Custome trigger
            val hasCustomSelect = flags.none { it.first == selectedFlag }
            val labelText = if (hasCustomSelect) {
                TerritoryDatabase.list.find { it.flag == selectedFlag }?.name?.take(8) ?: "Selected"
            } else "More"

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (hasCustomSelect) RegalGold.copy(alpha = 0.15f) else CharcoalObsidian
                ),
                border = BorderStroke(
                    width = 1.5.dp,
                    color = if (hasCustomSelect) RegalGold else RegalGold.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1.1f)
                    .height(52.dp)
                    .clickable { showAllFlagsDialog = true }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (hasCustomSelect) selectedFlag else "🌍", fontSize = 20.sp)
                    Text(labelText, color = RegalGold, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }

        if (showAllFlagsDialog) {
            Dialog(onDismissRequest = { showAllFlagsDialog = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = VelvetCard),
                    border = BorderStroke(1.5.dp, RegalGold),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Select Global Territory",
                            color = GhostWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Region pills row
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val regions = listOf("All", "Africa", "Asia", "Europe", "North America", "South America", "Oceania")
                            regions.forEach { region ->
                                val isSel = selectedRegionFilter == region
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSel) RegalGold else CharcoalObsidian
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.clickable { selectedRegionFilter = region }
                                ) {
                                    Text(
                                        text = region,
                                        color = if (isSel) CharcoalObsidian else GhostWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        
                        // Search text field
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search territory...", color = MutedSlate) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = GhostWhite,
                                unfocusedTextColor = GhostWhite,
                                focusedBorderColor = RegalGold,
                                unfocusedBorderColor = MutedSlate.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )
                        
                        // List of matching territories
                        val filtered = TerritoryDatabase.list.filter {
                            (selectedRegionFilter == "All" || it.region.equals(selectedRegionFilter, ignoreCase = true)) &&
                            (it.name.contains(searchQuery, ignoreCase = true) || it.capital.contains(searchQuery, ignoreCase = true))
                        }
                        
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered) { item ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedFlag == item.flag) RegalGold.copy(alpha = 0.15f) else CharcoalObsidian
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selectedFlag == item.flag) RegalGold else MutedSlate.copy(alpha = 0.2f)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedFlag = item.flag
                                            showAllFlagsDialog = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(item.flag, fontSize = 28.sp)
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(item.name, color = GhostWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            Text("Capital: ${item.capital}  •  Pop: ${item.population}", color = MutedSlate, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                            if (filtered.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No territories found.", color = MutedSlate, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showAllFlagsDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = CharcoalObsidian),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Dismiss", color = GhostWhite)
                        }
                    }
                }
            }
        }

        // Activation Button (One Word)
        Button(
            onClick = {
                if (name.isBlank() || username.isBlank() || email.isBlank() || dob.isBlank() || password.isBlank()) {
                    return@Button
                }
                viewModel.tempName = name
                viewModel.tempUsername = username
                viewModel.tempEmail = email
                viewModel.tempDob = dob
                viewModel.tempPassword = password
                viewModel.tempGender = selectedGender
                viewModel.selectedFlag = selectedFlag
                val matchedCountry = TerritoryDatabase.list.find { it.flag == selectedFlag }
                viewModel.selectedTerritory = matchedCountry?.name ?: "India"
                showOtpDialog = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = RegalGold),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = name.isNotBlank() && username.isNotBlank() && email.isNotBlank() && isEmailValid && email.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$"))
        ) {
            Text(
                "Register",
                fontWeight = FontWeight.Bold,
                color = CharcoalObsidian,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Already registered? ", color = MutedSlate, fontSize = 12.sp)
            Text(
                "Login", 
                color = RegalGold, 
                fontSize = 12.sp, 
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { viewModel.navigateTo(Screen.Login) }
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Identity Verification Dialog Modal
        if (showOtpDialog) {
            Dialog(
                onDismissRequest = { showOtpDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(max = 450.dp)
                        .fillMaxWidth(0.9f)
                        .wrapContentHeight()
                        .imePadding()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VelvetCard),
                    border = BorderStroke(1.dp, RegalGold),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = RegalGold,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Asynchronous OTP Gateway",
                            color = GhostWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "A simulated cryptographic code has been sent to your email to verify account validity.",
                            color = MutedSlate,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        Text(
                            "Enter Gateway Key: '1234'",
                            color = RegalGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = inputOtp,
                            onValueChange = { inputOtp = it },
                            placeholder = { Text("Code") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RegalGold,
                                focusedTextColor = GhostWhite,
                                unfocusedTextColor = GhostWhite
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.width(130.dp)
                        )

                        if (hasError) {
                            Text(
                                "Invalid Cryptographic Key. Try '1234'",
                                color = CrimsonRep,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = { showOtpDialog = false }) {
                                Text("CANCEL", color = MutedSlate)
                            }

                            Button(
                                onClick = {
                                    if (inputOtp == "1234") {
                                        isVerified = true
                                        showOtpDialog = false
                                        viewModel.navigateTo(Screen.TerritorySelection)
                                    } else {
                                        hasError = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RegalGold)
                            ) {
                                Text("VALIDATE", color = CharcoalObsidian)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3B. LOGIN SYSTEM (Noble Dark Symmetrical Form + Symmetrical Profiles)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginView(viewModel: AppViewModel) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Icon(
            imageVector = Icons.Default.AccountBalance,
            contentDescription = null,
            tint = RegalGold,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome Back, Citizen",
            color = GhostWhite,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Sign in to synchronize your merit index",
            color = MutedSlate,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
        )

        // Symmetrical Gold Styled Inputs
        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it.filter { !it.isWhitespace() } },
            label = { Text("Email Address or Handle") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = RegalGold) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RegalGold,
                unfocusedBorderColor = MutedSlate.copy(alpha = 0.5f),
                focusedLabelColor = RegalGold,
                unfocusedLabelColor = MutedSlate,
                focusedTextColor = GhostWhite,
                unfocusedTextColor = GhostWhite
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Account Passphrase") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = RegalGold) },
            trailingIcon = {
                val icon = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(icon, contentDescription = "Toggle password visibility", tint = RegalGold)
                }
            },
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RegalGold,
                unfocusedBorderColor = MutedSlate.copy(alpha = 0.5f),
                focusedLabelColor = RegalGold,
                unfocusedLabelColor = MutedSlate,
                focusedTextColor = GhostWhite,
                unfocusedTextColor = GhostWhite
            ),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        )

        // Login Action Button (One Word)
        Button(
            onClick = {
                if (identifier.isBlank()) {
                    Toast.makeText(context, "Please enter your legal identifier.", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                
                viewModel.performLogin(identifier, password) { success ->
                    if (success) {
                        Toast.makeText(context, "Merit index synchronized!", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = RegalGold),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = identifier.isNotBlank()
        ) {
            Text(
                "Login",
                fontWeight = FontWeight.Bold,
                color = CharcoalObsidian,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("New to the Empire? ", color = MutedSlate, fontSize = 12.sp)
            Text(
                "Register", 
                color = RegalGold, 
                fontSize = 12.sp, 
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { viewModel.navigateTo(Screen.Registration) }
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

// ============================================================================
// 4. INTERACTIVE TERRITORY MAP SELECTION
// ============================================================================
@Composable
fun TerritorySelectionView(viewModel: AppViewModel) {
    val territories = TerritoryDatabase.list

    var selectedTerritoryName by remember { mutableStateOf<String?>(viewModel.selectedTerritory.ifEmpty { null }) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedRegion by remember { mutableStateOf("All") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Icon(
            imageVector = Icons.Default.Public,
            contentDescription = null,
            tint = RegalGold,
            modifier = Modifier.size(54.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Geopolitical Alignment",
            color = GhostWhite,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Select your home territory. All territories are equal and united under one Earth.",
            color = MutedSlate,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        // Region pills row
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val regions = listOf("All", "Africa", "Asia", "Europe", "North America", "South America", "Oceania")
            regions.forEach { region ->
                val isSel = selectedRegion == region
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSel) RegalGold else CharcoalObsidian
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clickable { selectedRegion = region }
                ) {
                    Text(
                        text = region,
                        color = if (isSel) CharcoalObsidian else GhostWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Search text field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search 115+ global territories...", color = MutedSlate, fontSize = 12.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = GhostWhite,
                unfocusedTextColor = GhostWhite,
                focusedBorderColor = RegalGold,
                unfocusedBorderColor = MutedSlate.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        val displayedTerritories = territories.filter {
            (selectedRegion == "All" || it.region.equals(selectedRegion, ignoreCase = true)) &&
            (it.name.contains(searchQuery, ignoreCase = true) || it.capital.contains(searchQuery, ignoreCase = true))
        }

        // Territory Option Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(displayedTerritories.size) { index ->
                val territory = displayedTerritories[index]
                val isSelected = selectedTerritoryName == territory.name
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clickable {
                            selectedTerritoryName = territory.name
                            viewModel.selectedTerritory = territory.name
                            viewModel.selectedFlag = territory.flag
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) RegalGold.copy(alpha = 0.15f) else VelvetCard
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) RegalGold else MutedSlate.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = territory.flag,
                                fontSize = 32.sp
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = RegalGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = territory.name,
                                color = GhostWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Pop: ${territory.population}",
                                color = MutedSlate,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (selectedTerritoryName != null) {
                    viewModel.navigateTo(Screen.PersonalitySetup)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = RegalGold),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = selectedTerritoryName != null
        ) {
            Text(
                "Align with ${selectedTerritoryName ?: "Territory"}",
                color = CharcoalObsidian,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

// ============================================================================
// 5. PERSONALITY MATRIX SETUP
// ============================================================================
@Composable
fun PersonalitySetupView(viewModel: AppViewModel) {
    val traitsAvailable = listOf(
        "Explorer", "Creator", "Teacher", "Leader", "Scientist",
        "Artist", "Philosopher", "Builder", "Visionary", "Humanitarian"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Icon(
            imageVector = Icons.Default.FilterVintage,
            contentDescription = null,
            tint = RegalGold,
            modifier = Modifier.size(54.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Personality Alignment",
            color = GhostWhite,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "You must select exactly five (5) defining traits to complete your profile structure.",
            color = MutedSlate,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
        )

        // Trait Selected counter
        Card(
            colors = CardDefaults.cardColors(containerColor = VelvetCard),
            border = BorderStroke(1.dp, RegalGold),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = RegalGold, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Selected Matrix: ${viewModel.selectedTraitsList.size} / 5",
                    color = GhostWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large select options
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(traitsAvailable) { trait ->
                val isSelected = viewModel.selectedTraitsList.contains(trait)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isSelected) {
                                viewModel.selectedTraitsList.remove(trait)
                            } else {
                                if (viewModel.selectedTraitsList.size < 5) {
                                    viewModel.selectedTraitsList.add(trait)
                                }
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) RegalGold.copy(alpha = 0.15f) else VelvetCard
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) RegalGold else MutedSlate.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = trait,
                            color = GhostWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) RegalGold else MutedSlate,
                                    shape = CircleShape
                                )
                                .background(
                                    color = if (isSelected) RegalGold else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = CharcoalObsidian,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (viewModel.selectedTraitsList.size == 5) {
                    viewModel.navigateTo(Screen.CitizenOath)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = RegalGold),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = viewModel.selectedTraitsList.size == 5
        ) {
            Text(
                "Lock",
                color = CharcoalObsidian,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

// ============================================================================
// 6. THE CITIZEN OATH
// ============================================================================
@Composable
fun CitizenOathView(viewModel: AppViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Gavel,
                contentDescription = null,
                tint = RegalGold,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "The Covenant Oath",
                color = GhostWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Text(
                text = "Every citizen must bind themselves to the values of our digital civilization prior to entry.",
                color = MutedSlate,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )

            // Oath Text Box Panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(2.dp, RegalGold),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.FormatQuote,
                        contentDescription = null,
                        tint = RegalGold.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "\"I belong to One Earth and One Family. I shall respect all citizens. I shall contribute positively. I shall seek knowledge and truth. I shall help build a better future.\"",
                        color = GhostWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp
                    )
                }
            }
        }

        // Action Entrance Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Text(
                "By tapping below, your signature is permanently sealed",
                color = MutedSlate,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Button(
                onClick = { viewModel.completeOnboarding() },
                colors = ButtonDefaults.buttonColors(containerColor = RegalGold),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, LightGold),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "Accept",
                    fontWeight = FontWeight.Bold,
                    color = CharcoalObsidian,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ============================================================================
// 7. MAIN DASHBOARD VIEW (With Top bar stats, 5 tabs, dialogs)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MainDashboardView(viewModel: AppViewModel, snackbarHostState: SnackbarHostState) {
    val currentTab by viewModel.currentTab.collectAsState()
    val me by viewModel.currentUserFlow.collectAsState()
    val selectedId by viewModel.selectedRoomId.collectAsState()
    val commentsPostId by viewModel.selectedCommentsPostId.collectAsState()
    val selectedProfileUser by viewModel.selectedProfileUser.collectAsState()

    val showRoyalProfile by viewModel.showRoyalProfile.collectAsState()
    val monarchProfile by viewModel.viewedMonarchProfile.collectAsState()
    val viewedMonarchUser by viewModel.viewedMonarchUser.collectAsState()
    val throneWorthiness by viewModel.throneWorthiness.collectAsState()
    val monarchTimeline by viewModel.monarchTimeline.collectAsState()
    val royalDecrees by viewModel.royalDecrees.collectAsState()
    val royalCouncil by viewModel.royalCouncil.collectAsState()
    val hallOfMonarchs by viewModel.hallOfMonarchs.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()

    val showWelcome by viewModel.showDailyWelcome.collectAsState()
    val showExit by viewModel.showExitSummary.collectAsState()

    val isBackendConnected by viewModel.isBackendConnected.collectAsState()
    val isConnectingToBackend by viewModel.isConnectingToBackend.collectAsState()
    val backendBaseUrl by viewModel.backendBaseUrl.collectAsState()

    Scaffold(
        containerColor = DeepOceanSapphire,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.exclude(WindowInsets.ime),
        topBar = {
            if (currentTab != DashboardTab.Messaging) {
                TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            viewModel.selectTab(DashboardTab.ProfileAndElections)
                            viewModel.setShowEditProfileDialog(true)
                        }
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(CharcoalObsidian)
                                    .border(1.dp, RegalGold, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (me?.profilePhoto?.isNotBlank() == true) me!!.profilePhoto.take(1).uppercase() else (me?.name ?: "N").take(1).uppercase(),
                                    color = RegalGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 1.dp, y = 1.dp)
                                    .background(CharcoalObsidian, CircleShape)
                                    .border(0.5.dp, RegalGold.copy(alpha = 0.5f), CircleShape)
                                    .padding(horizontal = 2.dp, vertical = 0.5.dp)
                            ) {
                                Text(me?.flagEmoji ?: "🌍", fontSize = 8.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = me?.name ?: "Noble Citizen",
                                color = GhostWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Card(
                                colors = CardDefaults.cardColors(containerColor = RegalGold.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "${me?.currentRank ?: "Citizen"} Rank",
                                    color = RegalGold,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        // KC
                        Row(
                            modifier = Modifier
                                .background(CharcoalObsidian, RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🧠", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                "${me?.knowledgeCredits ?: 0}",
                                color = GhostWhite,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // CC
                        Row(
                            modifier = Modifier
                                .background(CharcoalObsidian, RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🤝", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                "${me?.contributionCredits ?: 0}",
                                color = RegalGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(2.dp))

                        // Alerts Badge Bell with Notification Center Dialog
                        val unreadCount by viewModel.unreadNotificationsCount.collectAsState()
                        var showNotificationsDialog by remember { mutableStateOf(false) }

                        LaunchedEffect(Unit) {
                            viewModel.showNotificationsDialogFlow.collect { show ->
                                if (show) {
                                    viewModel.refreshNotifications()
                                }
                                showNotificationsDialog = show
                            }
                        }

                        val activeNotifByScope by viewModel.activeNotification.collectAsState()
                        LaunchedEffect(activeNotifByScope) {
                            if (activeNotifByScope != null) {
                                viewModel.refreshNotifications()
                            }
                        }

                        Box(contentAlignment = Alignment.TopEnd) {
                            IconButton(
                                onClick = { 
                                    viewModel.refreshNotifications()
                                    showNotificationsDialog = true 
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = "Alerts", tint = RegalGold, modifier = Modifier.size(18.dp))
                            }
                            if (unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp, end = 4.dp)
                                        .size(14.dp)
                                        .background(Color.Red, shape = CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = unreadCount.toString(),
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (showNotificationsDialog) {
                            val notifications by viewModel.allNotifications.collectAsState()

                            AlertDialog(
                                onDismissRequest = { showNotificationsDialog = false },
                                containerColor = VelvetCard,
                                titleContentColor = RegalGold,
                                textContentColor = MutedSlate,
                                title = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Notification Center", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        if (notifications.any { !it.isRead }) {
                                            TextButton(
                                                onClick = { viewModel.markAllNotificationsAsRead() }
                                            ) {
                                                Text("Mark All Read", color = LightGold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                },
                                text = {
                                    if (notifications.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(150.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No notifications found", color = MutedSlate, fontSize = 14.sp)
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.heightIn(max = 400.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(notifications) { notif ->
                                                val icon = when (notif.type) {
                                                    "message" -> Icons.Default.QuestionAnswer
                                                    "comment" -> Icons.Default.Comment
                                                    "reaction" -> Icons.Default.Stars
                                                    "profile_view" -> Icons.Default.Visibility
                                                    "friend_request" -> Icons.Default.Person
                                                    else -> Icons.Default.Notifications
                                                }
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (notif.isRead) DeepOceanSapphire.copy(alpha = 0.5f) else VelvetCard
                                                    ),
                                                    border = BorderStroke(1.dp, if (notif.isRead) Color.Transparent else RegalGold.copy(alpha = 0.4f)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    onClick = {
                                                        if (!notif.isRead) {
                                                            viewModel.markNotificationAsRead(notif.id)
                                                        }
                                                    }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(32.dp)
                                                                .background(
                                                                    if (notif.isRead) MutedSlate.copy(alpha = 0.1f) else RegalGold.copy(alpha = 0.15f),
                                                                    shape = CircleShape
                                                                ),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = icon,
                                                                contentDescription = null,
                                                                tint = if (notif.isRead) MutedSlate else RegalGold,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = notif.title,
                                                                color = if (notif.isRead) MutedSlate else Color.White,
                                                                fontSize = 13.sp,
                                                                fontWeight = if (notif.isRead) FontWeight.Normal else FontWeight.Bold
                                                            )
                                                            if (notif.body.isNotBlank()) {
                                                                Text(
                                                                    text = notif.body,
                                                                    color = MutedSlate,
                                                                    fontSize = 11.sp,
                                                                    maxLines = 2,
                                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                        if (!notif.isRead) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(8.dp)
                                                                    .background(RegalGold, shape = CircleShape)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showNotificationsDialog = false }) {
                                        Text("Close", color = RegalGold)
                                    }
                                }
                            )
                        }

                        // Top Right 3-dot Dropdown Menu with Theme Toggle & Logout
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Menu",
                                    tint = RegalGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(VelvetCard)
                            ) {
                                val currentTheme = viewModel.themeMode.collectAsState().value
                                val themeLabel = when (currentTheme) {
                                    AppThemeMode.TWILIGHT_DUSK -> "Theme: Twilight Dusk 🌅"
                                    AppThemeMode.SPACE_ABYSS_DARK -> "Theme: Space Abyss 🌌"
                                    AppThemeMode.MINIMALIST_SLATE_LIGHT -> "Theme: Slate Light ☀️"
                                    AppThemeMode.DARK -> "Theme: Classic Dark 🌙"
                                    AppThemeMode.LIGHT -> "Theme: Royal Blue Light 💙"
                                }
                                val themeIcon = when (currentTheme) {
                                    AppThemeMode.TWILIGHT_DUSK -> Icons.Default.LightMode
                                    AppThemeMode.SPACE_ABYSS_DARK -> Icons.Default.DarkMode
                                    AppThemeMode.MINIMALIST_SLATE_LIGHT -> Icons.Default.LightMode
                                    AppThemeMode.DARK -> Icons.Default.DarkMode
                                    AppThemeMode.LIGHT -> Icons.Default.LightMode
                                }
                                DropdownMenuItem(
                                    text = { Text(themeLabel, color = GhostWhite) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = themeIcon,
                                            contentDescription = "Theme Toggle",
                                            tint = RegalGold
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        viewModel.toggleTheme()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Network Sync", color = GhostWhite)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(
                                                        if (isBackendConnected) EmeraldSuccess else CrimsonRep, 
                                                        CircleShape
                                                    )
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Backend Sync Settings",
                                            tint = RegalGold
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        viewModel.setShowSyncSettingsDialog(true)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear All Data & Backend", color = CrimsonRep) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Clear All Data",
                                            tint = CrimsonRep
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        viewModel.clearAllAppAndBackendData()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Logout", color = CrimsonRep) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Logout,
                                            contentDescription = "Logout",
                                            tint = CrimsonRep
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        viewModel.showExitDialog()
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VelvetCard
                )
            )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = VelvetCard,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == DashboardTab.PublicSquare,
                    onClick = { viewModel.selectTab(DashboardTab.PublicSquare) },
                    icon = { Icon(Icons.Default.Feed, contentDescription = null) },
                    label = { Text("Feed", fontSize = 10.sp, maxLines = 1, softWrap = false) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RegalGold,
                        selectedTextColor = RegalGold,
                        unselectedIconColor = MutedSlate,
                        unselectedTextColor = MutedSlate,
                        indicatorColor = RegalGold.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == DashboardTab.FindFriends,
                    onClick = { viewModel.selectTab(DashboardTab.FindFriends) },
                    icon = { Icon(Icons.Default.People, contentDescription = null) },
                    label = { Text("Find Friends", fontSize = 9.sp, maxLines = 1, softWrap = false) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RegalGold,
                        selectedTextColor = RegalGold,
                        unselectedIconColor = MutedSlate,
                        unselectedTextColor = MutedSlate,
                        indicatorColor = RegalGold.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == DashboardTab.Messaging,
                    onClick = { viewModel.selectTab(DashboardTab.Messaging) },
                    icon = { Icon(Icons.Default.QuestionAnswer, contentDescription = null) },
                    label = { Text("Chats", fontSize = 10.sp, maxLines = 1, softWrap = false) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RegalGold,
                        selectedTextColor = RegalGold,
                        unselectedIconColor = MutedSlate,
                        unselectedTextColor = MutedSlate,
                        indicatorColor = RegalGold.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == DashboardTab.ProfileAndElections,
                    onClick = { viewModel.selectTab(DashboardTab.ProfileAndElections) },
                    icon = { Icon(Icons.Default.HowToVote, contentDescription = null) },
                    label = { Text("Elections", fontSize = 10.sp, maxLines = 1, softWrap = false) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RegalGold,
                        selectedTextColor = RegalGold,
                        unselectedIconColor = MutedSlate,
                        unselectedTextColor = MutedSlate,
                        indicatorColor = RegalGold.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == DashboardTab.MissionsAndLegends,
                    onClick = { viewModel.selectTab(DashboardTab.MissionsAndLegends) },
                    icon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null) },
                    label = { Text("Missions", fontSize = 10.sp, maxLines = 1, softWrap = false) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RegalGold,
                        selectedTextColor = RegalGold,
                        unselectedIconColor = MutedSlate,
                        unselectedTextColor = MutedSlate,
                        indicatorColor = RegalGold.copy(alpha = 0.1f)
                    )
                )
            }
        }
    ) { innerPadding ->
        val isKeyboardOpen = androidx.compose.foundation.layout.WindowInsets.isImeVisible
        val bottomPadding = if (isKeyboardOpen) 0.dp else innerPadding.calculateBottomPadding()
        Box(
            modifier = Modifier
                .padding(
                    start = innerPadding.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                    top = innerPadding.calculateTopPadding(),
                    end = innerPadding.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current),
                    bottom = bottomPadding
                )
                .fillMaxSize()
        ) {
            when (currentTab) {
                DashboardTab.PublicSquare -> PublicSquareTab(viewModel)
                DashboardTab.FindFriends -> FindFriendsTab(viewModel)
                DashboardTab.Messaging -> MessagingTab(viewModel)
                DashboardTab.ProfileAndElections -> ElectionsAndProfileTab(viewModel)
                DashboardTab.MissionsAndLegends -> MissionsTab(viewModel)
            }

            // Floating logout button removed to prevent overlap with send/input fields. Access via top-right 3-dot menu.
        }
    }

    // Modal: The Daily Royal Welcome Notification
    if (showWelcome) {
        Dialog(onDismissRequest = { viewModel.dismissWelcomeDialog() }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(1.5.dp, RegalGold),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = RegalGold, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "The Daily Royal Welcome",
                        color = GhostWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Good Evening, Citizen.\n\nToday your profile received 14 new visits, you accumulated 32 Knowledge Credits, and your Territory rose to Rank #3 worldwide.\n\nThe general elections commence in 2 days.",
                        color = GhostWhite.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.dismissWelcomeDialog() },
                        colors = ButtonDefaults.buttonColors(containerColor = RegalGold)
                    ) {
                        Text("Honor", color = CharcoalObsidian, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Modal: The Exit Summary
    if (showExit) {
        Dialog(onDismissRequest = { viewModel.dismissExitDialog() }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(1.5.dp, RegalGold),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Timeline, contentDescription = null, tint = RegalGold, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Daily Progress Ledger",
                        color = GhostWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Real-time Reflection Analytics:\n\n+32 KC (Knowledge Credits)\n+15 CC (Contribution Credits)\n\nRank Progress: +7% closer to Contributor Tier!\n\nReturn tomorrow to unlock your next Imperial Mission.",
                        color = GhostWhite.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { viewModel.dismissExitDialog() }) {
                            Text("Stay", color = MutedSlate)
                        }
                        Button(
                            onClick = {
                                viewModel.dismissExitDialog()
                                viewModel.performLogout()
                            },
                            colors = ButtonColors(
                                containerColor = CrimsonRep,
                                contentColor = GhostWhite,
                                disabledContainerColor = MutedSlate,
                                disabledContentColor = MutedSlate
                            )
                        ) {
                            Text("Logout", color = GhostWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    selectedProfileUser?.let { selectedUser ->
        ProfileDisplayDialog(user = selectedUser, viewModel = viewModel, onClose = { viewModel.closeProfileDialog() })
    }

    if (showRoyalProfile && monarchProfile != null) {
        val profile = monarchProfile!!
        val monarchUser = viewedMonarchUser ?: allUsers.firstOrNull { it.id == profile.monarchCitizenId }
        val userName = monarchUser?.name ?: "The Sovereign"
        val userTerritory = monarchUser?.territory ?: "Global"
        val userFlag = monarchUser?.flagEmoji ?: "👑"

        if (profile.title == "King") {
            KingProfileDialog(
                profile = profile,
                worthiness = throneWorthiness,
                timeline = monarchTimeline,
                decrees = royalDecrees,
                council = royalCouncil,
                userName = userName,
                userTerritory = userTerritory,
                userFlag = userFlag,
                viewModel = viewModel,
                onClose = { viewModel.dismissRoyalProfile() }
            )
        } else {
            QueenProfileDialog(
                profile = profile,
                worthiness = throneWorthiness,
                timeline = monarchTimeline,
                decrees = royalDecrees,
                council = royalCouncil,
                userName = userName,
                userTerritory = userTerritory,
                userFlag = userFlag,
                viewModel = viewModel,
                onClose = { viewModel.dismissRoyalProfile() }
            )
        }
    }

    if (hallOfMonarchs.isNotEmpty()) {
        HallOfMonarchsDialog(
            monarchs = hallOfMonarchs,
            onClose = { viewModel.dismissHallOfMonarchs() }
        )
    }
}

@Composable
fun ProfileDisplayDialog(user: UserEntity, viewModel: AppViewModel, onClose: () -> Unit) {
    var isEditingProfile by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(user.name.replace("|", "")) }
    var editUsername by remember { mutableStateOf(user.username.removePrefix("@").filter { !it.isWhitespace() }) }
    var editBio by remember { mutableStateOf(user.bio) }
    var editTerritory by remember { mutableStateOf(user.territory) }
    var editFlagEmoji by remember { mutableStateOf(user.flagEmoji) }
    var postToDelete by remember { mutableStateOf<com.example.data.PostEntity?>(null) }

    val isKingOrQueen = user.currentRank.equals("King", ignoreCase = true) || user.currentRank.equals("Queen", ignoreCase = true)
    val isCandidate = user.isCandidate
    val isNobility = !isKingOrQueen && (user.currentRank.equals("Prince", ignoreCase = true) || 
                     user.currentRank.equals("Princess", ignoreCase = true) || 
                     user.currentRank.equals("Duke", ignoreCase = true) || 
                     user.currentRank.equals("Duchess", ignoreCase = true) || 
                     user.currentRank.contains("Noble", ignoreCase = true))

    // Customize border and background based on royalty
    val isLight = ThemeState.isLight
    val borderStroke = when {
        isKingOrQueen -> BorderStroke(2.5.dp, if (isLight) Color(0xFFB48A10) else Color(0xFFD4AF37))
        isNobility -> BorderStroke(2.2.dp, if (isLight) Color(0xFF7C3AED) else Color(0xFFA78BFA))
        isCandidate -> BorderStroke(1.5.dp, if (isLight) Color(0xFF0284C7) else Color(0xFF4FC3F7)) // Cyan Candidate theme
        else -> BorderStroke(1.dp, MutedSlate.copy(alpha = 0.35f)) // Neutral theme for normal citizen/scholars
    }

    val cardBg = when {
        isKingOrQueen -> if (isLight) Color(0xFFFBF8F1) else Color(0xFF0F0E07) // Deep Imperial Black-Gold Hue
        isNobility -> if (isLight) Color(0xFFFAF5FF) else Color(0xFF130D1E) // Deep Amethyst Velvet
        isCandidate -> if (isLight) Color(0xFFF0F9FF) else Color(0xFF020E1C) // Deep Cobalt Midnight
        else -> VelvetCard // Original VelvetCard for standard citizens
    }

    LaunchedEffect(user.id) {
        viewModel.notifyProfileView(user)
    }

    val textPrimaryColor = if (isLight) Color(0xFF1E1E1E) else GhostWhite
    val textSecondaryColor = if (isLight) Color(0xFF5E5E5E) else MutedSlate
    val context = androidx.compose.ui.platform.LocalContext.current
    var bowingReputationBonus by remember { mutableStateOf(0) }

    Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = borderStroke,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .widthIn(max = 450.dp)
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .imePadding()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isKingOrQueen) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = if (isLight) listOf(Color(0xFFFFF7E3), Color(0xFFFBF8F1)) else listOf(Color(0xFF2E1C00), Color(0xFF0F0E07))
                                )
                            )
                            .padding(top = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("👑", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "👑 IMPERIAL SOVEREIGN CHAMBER 👑",
                                color = if (isLight) Color(0xFF906D00) else Color(0xFFD4AF37),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = if (isKingOrQueen) 12.dp else 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                val avatarBorderColor = when {
                    isKingOrQueen -> if (isLight) Color(0xFFB48A10) else Color(0xFFD4AF37)
                    isNobility -> if (isLight) Color(0xFF7C3AED) else Color(0xFFA78BFA)
                    isCandidate -> if (isLight) Color(0xFF0284C7) else Color(0xFF4FC3F7)
                    else -> MutedSlate.copy(alpha = 0.5f)
                }
                val avatarBg = when {
                    isKingOrQueen -> if (isLight) Color(0xFFF5EFE0) else Color(0xFF1E1705)
                    isNobility -> if (isLight) Color(0xFFF3E8FF) else Color(0xFF23173A)
                    isCandidate -> if (isLight) Color(0xFFE0F2FE) else Color(0xFF001F3D)
                    else -> CharcoalObsidian
                }
                val avatarTextColor = when {
                    isKingOrQueen -> if (isLight) Color(0xFF906D00) else Color(0xFFD4AF37)
                    isNobility -> if (isLight) Color(0xFF7C3AED) else Color(0xFFC084FC)
                    isCandidate -> if (isLight) Color(0xFF0284C7) else Color(0xFF4FC3F7)
                    else -> textPrimaryColor
                }

                Box(
                    modifier = Modifier.size(105.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(avatarBg)
                            .border(if (isKingOrQueen) 2.5.dp else 1.5.dp, avatarBorderColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (editName.isNotBlank()) editName.take(1).uppercase() else user.name.take(1).uppercase(),
                            color = avatarTextColor,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Flag overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-4).dp, y = (-4).dp)
                            .background(CharcoalObsidian, CircleShape)
                            .border(1.dp, avatarBorderColor.copy(alpha = 0.5f), CircleShape)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(editFlagEmoji, fontSize = 18.sp)
                    }

                    // IMPERIAL BADGE OVERLAY
                    if (isKingOrQueen) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 4.dp, y = 4.dp)
                                .background(if (isLight) Color(0xFFFFF7E3) else Color(0xFF2E1C00), CircleShape)
                                .border(1.dp, if (isLight) Color(0xFFB48A10) else Color(0xFFD4AF37), CircleShape)
                                .padding(4.dp)
                        ) {
                            Text("👑", fontSize = 14.sp)
                        }
                    } else if (isNobility) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 4.dp, y = 4.dp)
                                .background(if (isLight) Color(0xFFF5EBFF) else Color(0xFF23143B), CircleShape)
                                .border(1.dp, if (isLight) Color(0xFF7C3AED) else Color(0xFFA78BFA), CircleShape)
                                .padding(4.dp)
                        ) {
                            Text("🛡️", fontSize = 12.sp)
                        }
                    } else if (isCandidate) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 4.dp, y = 4.dp)
                                .background(if (isLight) Color(0xFFE6F3FF) else Color(0xFF001F3D), CircleShape)
                                .border(1.dp, if (isLight) Color(0xFF0284C7) else Color(0xFF4FC3F7), CircleShape)
                                .padding(4.dp)
                        ) {
                            Text("⚡", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isEditingProfile) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Display Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimaryColor,
                            unfocusedTextColor = textPrimaryColor,
                            focusedBorderColor = RegalGold,
                            unfocusedBorderColor = MutedSlate
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { editUsername = it },
                        label = { Text("Handle (Username)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimaryColor,
                            unfocusedTextColor = textPrimaryColor,
                            focusedBorderColor = RegalGold,
                            unfocusedBorderColor = MutedSlate
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = { Text("Mission Bio") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimaryColor,
                            unfocusedTextColor = textPrimaryColor,
                            focusedBorderColor = RegalGold,
                            unfocusedBorderColor = MutedSlate
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = editTerritory,
                        onValueChange = { editTerritory = it },
                        label = { Text("Territory Representation") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimaryColor,
                            unfocusedTextColor = textPrimaryColor,
                            focusedBorderColor = RegalGold,
                            unfocusedBorderColor = MutedSlate
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = editFlagEmoji,
                        onValueChange = { editFlagEmoji = it },
                        label = { Text("Flag Emoji") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimaryColor,
                            unfocusedTextColor = textPrimaryColor,
                            focusedBorderColor = RegalGold,
                            unfocusedBorderColor = MutedSlate
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { isEditingProfile = false },
                            colors = ButtonDefaults.buttonColors(containerColor = MutedSlate),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CANCEL", color = if (isLight) Color.White else GhostWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                viewModel.updateUserProfile(
                                    name = editName,
                                    username = editUsername,
                                    bio = editBio,
                                    territory = editTerritory,
                                    flagEmoji = editFlagEmoji,
                                    profilePhoto = ""
                                )
                                isEditingProfile = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RegalGold),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save", color = CharcoalObsidian, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Text(
                        text = user.name,
                        color = textPrimaryColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = user.username,
                            color = textSecondaryColor,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val genderEmoji = when (user.gender) {
                            "Male" -> "♂️"
                            "Female" -> "♀️"
                            else -> "✨"
                        }
                        Box(
                            modifier = Modifier
                                .background(MutedSlate.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val genderTextColor = if (isLight) Color(0xFF444444) else GhostWhite
                                Text(text = genderEmoji, fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(text = user.gender.uppercase(), color = genderTextColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val badgeBg = when {
                        isKingOrQueen -> if (isLight) Color(0xFFFDF6E2) else Color(0xFF221901)
                        isNobility -> if (isLight) Color(0xFFF5EBFF) else Color(0xFF221333)
                        isCandidate -> if (isLight) Color(0xFFE6F3FF) else Color(0xFF01182D)
                        else -> MutedSlate.copy(alpha = 0.15f)
                    }
                    val badgeBorder = when {
                        isKingOrQueen -> BorderStroke(1.5.dp, if (isLight) Color(0xFFC5A023) else Color(0xFFD4AF37))
                        isNobility -> BorderStroke(1.2.dp, if (isLight) Color(0xFFAC94FA) else Color(0xFFA78BFA))
                        isCandidate -> BorderStroke(1.2.dp, if (isLight) Color(0xFF81CDFF) else Color(0xFF4FC3F7))
                        else -> BorderStroke(0.5.dp, MutedSlate.copy(alpha = 0.4f))
                    }
                    val badgeTextColor = when {
                        isKingOrQueen -> if (isLight) Color(0xFF906D00) else Color(0xFFD4AF37)
                        isNobility -> if (isLight) Color(0xFF6D28D9) else Color(0xFFC084FC)
                        isCandidate -> if (isLight) Color(0xFF0284C7) else Color(0xFF4FC3F7)
                        else -> textPrimaryColor
                    }
                    val badgeText = when {
                        isKingOrQueen -> "⚜️ SOVEREIGN MONARCH ⚜️"
                        isNobility -> "🌌 ROYAL COURT • ${user.currentRank.uppercase()}"
                        isCandidate -> "⚡ SOVEREIGN CAMPAIGN CANDIDATE"
                        else -> "${user.currentRank.uppercase()} CITIZEN"
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = badgeBg),
                        border = badgeBorder,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = badgeTextColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "KC (Knowledge)", color = textSecondaryColor, fontSize = 10.sp)
                            Text(
                                text = "${user.knowledgeCredits}",
                                color = ElectricBlue,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                              )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "CC (Contribution)", color = textSecondaryColor, fontSize = 10.sp)
                            Text(
                                text = "${user.contributionCredits}",
                                color = LustrousAmber,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "Reputation", color = textSecondaryColor, fontSize = 10.sp)
                            Text(
                                  text = "${user.reputationScore}%",
                                  color = EmeraldSuccess,
                                  fontSize = 15.sp,
                                  fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Paradigm Bio:",
                        color = RegalGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Text(
                        text = user.bio,
                        color = textPrimaryColor.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(top = 4.dp, bottom = 12.dp)
                    )

                    if (isKingOrQueen) {
                        Button(
                            onClick = {
                                bowingReputationBonus += 1
                                val honorTitle = if (user.gender.equals("Female", ignoreCase = true)) "Her Imperial Majesty" else "His Imperial Majesty"
                                android.widget.Toast.makeText(context, "🙇 You have bowed before $honorTitle. Blessing received! (+1 Sovereign Respect)", android.widget.Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isLight) Color(0xFFB48A10) else Color(0xFFD4AF37)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("☀️", fontSize = 16.sp)
                                Text("BOW IN REVERENCE ($bowingReputationBonus)", color = if (isLight) Color.White else Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("☀️", fontSize = 16.sp)
                            }
                        }
                    }

                    if (user.isCandidate) {
                        Divider(color = MutedSlate.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "Sovereign Campaign Vision:",
                            color = RegalGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Text(
                            text = user.campaignVision,
                            color = textPrimaryColor,
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.Start).padding(top = 2.dp)
                        )
                    }

                    if (user.id == "me") {
                        Button(
                            onClick = { isEditingProfile = true },
                            colors = ButtonDefaults.buttonColors(containerColor = RegalGold.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, RegalGold),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = RegalGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("EDIT PERSONAL PROFILE", color = RegalGold, fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startChatWithUser(user) },
                            colors = ButtonDefaults.buttonColors(containerColor = RegalGold),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Forum, contentDescription = null, tint = CharcoalObsidian, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("INITIALIZE DIALOGUE CONNECTION", color = CharcoalObsidian, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                val posts by viewModel.rankedPosts.collectAsState()
                val userPosts = posts.filter { 
                    it.authorName == user.name || 
                    it.authorId == user.id || 
                    (user.id == "me" && (it.authorId == "me" || it.authorId.equals(user.email, ignoreCase = true))) || 
                    (user.email.isNotBlank() && it.authorId.equals(user.email, ignoreCase = true)) 
                }

                Divider(color = MutedSlate.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = "Broadcast Archives:",
                    color = RegalGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )

                if (userPosts.isEmpty()) {
                    Text(
                        text = "No broadcast entries verified on the scroll ledger.",
                        color = MutedSlate,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.Start).padding(vertical = 8.dp)
                    )
                } else {
                    userPosts.forEach { post ->
                        var isEditingPost by remember { mutableStateOf(false) }
                        var editedContent by remember { mutableStateOf(post.content) }

                        val archiveCardBg = if (isLight) Color(0xFFF1ECE1) else CharcoalObsidian
                        val archiveTextColor = if (isLight) Color(0xFF2C2414) else GhostWhite
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = archiveCardBg),
                            border = BorderStroke(0.5.dp, MutedSlate.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = post.category, color = RegalGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)

                                    if (user.id == "me") {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            IconButton(
                                                onClick = { isEditingPost = !isEditingPost },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Post", tint = RegalGold, modifier = Modifier.size(14.dp))
                                            }
                                            IconButton(
                                                onClick = { postToDelete = post },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Post", tint = CrimsonRep, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                if (isEditingPost) {
                                    OutlinedTextField(
                                        value = editedContent,
                                        onValueChange = { editedContent = it },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = archiveTextColor,
                                            unfocusedTextColor = archiveTextColor,
                                            focusedBorderColor = RegalGold,
                                            unfocusedBorderColor = MutedSlate
                                        ),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { isEditingPost = false }) {
                                            Text("CANCEL", color = MutedSlate, fontSize = 9.sp)
                                        }
                                        TextButton(onClick = {
                                            viewModel.updatePostContent(post.id, editedContent)
                                            isEditingPost = false
                                        }) {
                                            Text("SAVE", color = RegalGold, fontSize = 9.sp)
                                        }
                                    }
                                } else {
                                    Text(text = post.content, color = archiveTextColor, fontSize = 11.sp, lineHeight = 15.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = RegalGold),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Text("CLOSE PARADIGM", color = CharcoalObsidian, fontWeight = FontWeight.Bold)
                }
                }
            }
        }
    }

    if (postToDelete != null) {
        Dialog(onDismissRequest = { postToDelete = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(1.dp, CrimsonRep),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = CrimsonRep,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Verify Post Deletion",
                        color = GhostWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Are you sure you want to permanently delete this broadcast entry from the Ledger?",
                        color = MutedSlate,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = { postToDelete = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CANCEL", color = MutedSlate, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                postToDelete?.let { post ->
                                    viewModel.deletePost(post.id)
                                }
                                postToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRep),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("DELETE", color = GhostWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// TAB 1: PUBLIC SQUARE (Home feed with merit reactions, Custom composer)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicSquareTab(viewModel: AppViewModel) {
    val posts by viewModel.rankedPosts.collectAsState()
    var lastVisiblePostIndex by remember { mutableStateOf(20) }
    val visiblePosts = remember(posts, lastVisiblePostIndex) {
        posts.take(lastVisiblePostIndex)
    }
    val currentUser by viewModel.currentUserFlow.collectAsState()
    var postContent by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Inquiry") }
    var showComposerDialog by remember { mutableStateOf(false) }

    val commentsPostId by viewModel.selectedCommentsPostId.collectAsState()
    val commentsList by viewModel.currentPostComments.collectAsState()
    val commentCounts by viewModel.postCommentCounts.collectAsState()
    var commentText by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            // Screen Title + Manual Refresh Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Global Public Square",
                        color = GhostWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Citizen dialogues on merit, knowledge, & contribution",
                        color = MutedSlate,
                        fontSize = 11.sp
                    )
                }
                IconButton(
                    onClick = { viewModel.syncAllWithBackend() },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(RegalGold.copy(alpha = 0.15f))
                        .size(38.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                        contentDescription = "Sync Feed",
                        tint = RegalGold,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Merit ranked Scroll feed
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visiblePosts) { post ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = VelvetCard),
                        border = BorderStroke(0.5.dp, MutedSlate.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Post author identity block
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        viewModel.showProfileForUserByName(post.authorName, post.authorFlag, post.authorRank, post.authorTerritory)
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier.size(46.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(CharcoalObsidian)
                                                .border(1.dp, RegalGold, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = post.authorName.take(1).uppercase(),
                                                color = RegalGold,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .offset(x = 2.dp, y = 2.dp)
                                                .background(CharcoalObsidian, CircleShape)
                                                .border(0.5.dp, RegalGold.copy(alpha = 0.5f), CircleShape)
                                                .padding(horizontal = 3.dp, vertical = 1.dp)
                                        ) {
                                            Text(post.authorFlag, fontSize = 10.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "${post.authorName} (${post.authorUsername})",
                                            color = GhostWhite,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row {
                                            Text(
                                                text = post.authorRank,
                                                color = RegalGold,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Reputation: ${post.reputationImpact}%",
                                                color = EmeraldSuccess,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                // Category badge tag
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = RegalGold.copy(alpha = 0.1f)),
                                    border = BorderStroke(0.5.dp, RegalGold),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = post.category,
                                        color = RegalGold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Content
                            Text(
                                text = post.content,
                                color = GhostWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )

                            if (post.royalSignature != null && post.royalSignature.monarchName.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = if (ThemeState.isLight) Color(0xFFFFF9EB) else Color(0xFF1E1402)),
                                    border = BorderStroke(1.2.dp, if (ThemeState.isLight) Color(0xFFC5A023) else Color(0xFFD4AF37)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text("⚜️", fontSize = 16.sp)
                                            Text(
                                                text = "ROYAL IMPERIAL SIGNATURE DECREE",
                                                color = if (ThemeState.isLight) Color(0xFF906D00) else Color(0xFFD4AF37),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text("👑", fontSize = 14.sp)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "“Verified signed under the golden crown by ${post.royalSignature.monarchTitle.uppercase()} ${post.royalSignature.monarchName}”",
                                            color = if (ThemeState.isLight) Color(0xFF2C2414) else GhostWhite,
                                            fontSize = 11.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            lineHeight = 15.sp,
                                            modifier = Modifier.padding(start = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val formattedDate = try {
                                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                            sdf.format(java.util.Date(post.royalSignature.signedAt))
                                        } catch (e: Exception) {
                                            "Imperial reign chronicle ledger time"
                                        }
                                        Text(
                                            text = "Imperial Reign Ledger Date: $formattedDate",
                                            color = if (ThemeState.isLight) Color(0xFF6B5E49) else MutedSlate,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Custom Reactions row (Rejects generic dopamine likes)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CharcoalObsidian.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Wise - adds KC to author
                                ReactionButton(
                                    emoji = "🧠",
                                    tooltipText = "Wise",
                                    count = post.knowledgeValue,
                                    active = post.reactedWiseUsers.split(",").any { it == "me" || (currentUser != null && it.equals(currentUser?.email, ignoreCase = true)) },
                                    color = RegalGold,
                                    onClick = { viewModel.reactToPost(post.id, "Wise") }
                                )

                                // Helpful - adds CC to author
                                ReactionButton(
                                    emoji = "🤝",
                                    tooltipText = "Helpful",
                                    count = post.contributionProof,
                                    active = post.reactedHelpfulUsers.split(",").any { it == "me" || (currentUser != null && it.equals(currentUser?.email, ignoreCase = true)) },
                                    color = LustrousAmber,
                                    onClick = { viewModel.reactToPost(post.id, "Helpful") }
                                )

                                // Inspiring - elevates reputation score % of author
                                ReactionButton(
                                    emoji = "✨",
                                    tooltipText = "Inspiring",
                                    count = (post.reputationImpact - 90).coerceAtLeast(0),
                                    active = post.reactedInspiringUsers.split(",").any { it == "me" || (currentUser != null && it.equals(currentUser?.email, ignoreCase = true)) },
                                    color = ElectricBlue,
                                    onClick = { viewModel.reactToPost(post.id, "Inspiring") }
                                )

                                // Comment trigger
                                val commentCount = commentCounts[post.id] ?: 0
                                IconButton(
                                    onClick = { viewModel.selectPostForComments(post.id) },
                                    modifier = Modifier.padding(start = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Comment,
                                            contentDescription = "Comments",
                                            tint = MutedSlate,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = commentCount.toString(),
                                            color = MutedSlate,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (posts.size > lastVisiblePostIndex) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(
                                onClick = { lastVisiblePostIndex += 20 },
                                colors = ButtonDefaults.textButtonColors(contentColor = RegalGold)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "LOAD MORE CITIZEN POSTS (${posts.size - lastVisiblePostIndex} REMAINING)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating '+' action button for drafting new Posts (bottom right, above bottom nav bar)
        FloatingActionButton(
            onClick = { showComposerDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 16.dp),
            containerColor = RegalGold,
            contentColor = CharcoalObsidian,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "Broadcast Entry", modifier = Modifier.size(24.dp))
        }
    }

    // Modal popup dialog containing "Broadcast Entry to Public Square" composer
    if (showComposerDialog) {
        Dialog(
            onDismissRequest = { showComposerDialog = false }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(1.dp, RegalGold.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Broadcast to Public Square",
                            color = GhostWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showComposerDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MutedSlate)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = postContent,
                        onValueChange = { postContent = it },
                        placeholder = { Text("Draft long-form inquiry, article or societal debate...", color = MutedSlate) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RegalGold,
                            unfocusedBorderColor = MutedSlate.copy(alpha = 0.3f),
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedPlaceholderColor = MutedSlate,
                            unfocusedPlaceholderColor = MutedSlate
                        ),
                        minLines = 4,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category selector row
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Inquiry", "Article", "Debate").forEach { cat ->
                                val isSel = cat == selectedCategory
                                Card(
                                    modifier = Modifier.clickable { selectedCategory = cat },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSel) RegalGold else MutedSlate.copy(alpha = 0.2f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = cat,
                                        color = if (isSel) CharcoalObsidian else GhostWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Send Button
                        IconButton(
                            onClick = {
                                if (postContent.isNotBlank()) {
                                    viewModel.createPost(postContent, selectedCategory)
                                    postContent = ""
                                    showComposerDialog = false
                                }
                            },
                            modifier = Modifier
                                .background(RegalGold, CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = CharcoalObsidian, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    // Modal comments sliding tray
    if (commentsPostId != null) {
        Dialog(
            onDismissRequest = { viewModel.selectPostForComments(null) },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(1.dp, RegalGold),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .widthIn(max = 450.dp)
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 500.dp)
                    .imePadding()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Public Dialogue Comments",
                        color = GhostWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Nested list of comment bodies
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (commentsList.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No nested comments. Be the first to add a wise inquiry.", color = MutedSlate, fontSize = 12.sp, textAlign = TextAlign.Center)
                                }
                            }
                        } else {
                            items(commentsList) { comment ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CharcoalObsidian.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable {
                                                viewModel.showProfileForUserByName(comment.authorName, comment.authorFlag, comment.authorRank)
                                            }
                                        ) {
                                            Box(
                                                modifier = Modifier.size(36.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(CircleShape)
                                                        .background(CharcoalObsidian)
                                                        .border(1.dp, RegalGold, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = comment.authorName.take(1).uppercase(),
                                                        color = RegalGold,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .offset(x = 1.dp, y = 1.dp)
                                                        .background(CharcoalObsidian, CircleShape)
                                                        .border(0.5.dp, RegalGold.copy(alpha = 0.5f), CircleShape)
                                                        .padding(horizontal = 2.dp, vertical = 0.5.dp)
                                                ) {
                                                    Text(comment.authorFlag, fontSize = 8.sp)
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(comment.authorName, color = RegalGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(comment.authorRank, color = MutedSlate, fontSize = 9.sp)
                                        }
                                        Text(comment.content, color = GhostWhite, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Keyboard input row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            placeholder = { Text("Add constructive comment...") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = GhostWhite,
                                unfocusedTextColor = GhostWhite,
                                focusedBorderColor = RegalGold,
                                unfocusedBorderColor = MutedSlate
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        IconButton(
                            onClick = {
                                val trimmedMsg = commentText.trim()
                                if (trimmedMsg.isNotBlank()) {
                                    commentsPostId?.let { postId ->
                                        viewModel.addComment(postId, trimmedMsg)
                                    }
                                    commentText = ""
                                }
                            },
                            modifier = Modifier
                                .background(RegalGold, CircleShape)
                                .size(40.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Publish", tint = CharcoalObsidian, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReactionButton(
    emoji: String,
    tooltipText: String,
    count: Int,
    active: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }

    LaunchedEffect(isHovered) {
        if (isHovered) {
            delay(2000)
            isHovered = false
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(2.dp)
    ) {
        Card(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { isHovered = true }
                    )
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Enter) {
                                isHovered = true
                            } else if (event.type == PointerEventType.Exit) {
                                isHovered = false
                            }
                        }
                    }
                },
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (active) color.copy(alpha = 0.15f) else Color.Transparent
            ),
            border = BorderStroke(0.5.dp, if (active) color else Color.Transparent)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$emoji $count",
                    color = if (active) color else MutedSlate,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isHovered) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -90),
                properties = PopupProperties(focusable = false, dismissOnBackPress = true, dismissOnClickOutside = true),
                onDismissRequest = { isHovered = false }
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CharcoalObsidian),
                    border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text(
                        text = tooltipText,
                        color = GhostWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
// HOVERABLE FLAG FOR FIND FRIENDS (Hover/Click shows Territory tooltip)
// ============================================================================
@Composable
fun HoverableFlag(flagEmoji: String, territory: String) {
    var showTooltip by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val isHovered = event.type == PointerEventType.Enter || event.type == PointerEventType.Move
                        val isExit = event.type == PointerEventType.Exit
                        if (isHovered) {
                            showTooltip = true
                        } else if (isExit) {
                            showTooltip = false
                        }
                    }
                }
            }
            .clickable { showTooltip = !showTooltip }
    ) {
        Text(
            text = flagEmoji,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        if (showTooltip) {
            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopCenter,
                onDismissRequest = { showTooltip = false }
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CharcoalObsidian),
                    border = BorderStroke(0.5.dp, RegalGold),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(top = 22.dp)
                ) {
                    Text(
                        text = territory,
                        color = RegalGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
// FIND FRIENDS TAB (Active citizen profiles with interactive profile messaging)
// ============================================================================
@Composable
fun FindFriendsTab(viewModel: AppViewModel) {
    val friends by viewModel.allFriends.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedSortOption by remember { mutableStateOf("Rank") } // options: "Rank", "Recent Activity", "Newest Members"
    var expandedSortMenu by remember { mutableStateOf(false) }

    val filteredAndSortedFriends = remember(friends, searchQuery, selectedSortOption) {
        val filtered = if (searchQuery.isBlank()) {
            friends
        } else {
            friends.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.username.contains(searchQuery, ignoreCase = true) ||
                it.territory.contains(searchQuery, ignoreCase = true)
            }
        }

        when (selectedSortOption) {
            "Rank" -> {
                filtered.sortedWith(
                    compareByDescending<UserEntity> {
                        when (it.currentRank.lowercase()) {
                            "royal candidate" -> 4
                            "guardian" -> 3
                            "noble" -> 2
                            "contributor" -> 1
                            else -> 0
                        }
                    }.thenByDescending { it.knowledgeCredits + it.contributionCredits }
                )
            }
            "Recent Activity" -> {
                // Sort by contribution credits descending as metric of active system interactions
                filtered.sortedByDescending { it.contributionCredits }
            }
            "Newest Members" -> {
                // Sort by ID descending (reverse order)
                filtered.sortedByDescending { it.id }
            }
            else -> filtered
        }
    }

    var lastVisibleIndex by remember { mutableStateOf(20) }

    LaunchedEffect(searchQuery, selectedSortOption) {
        lastVisibleIndex = 20
    }

    val visibleFriends = remember(filteredAndSortedFriends, lastVisibleIndex) {
        filteredAndSortedFriends.take(lastVisibleIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // High-contrast Search & Sorting Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = RegalGold, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = MutedSlate, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GhostWhite,
                    unfocusedTextColor = GhostWhite,
                    focusedBorderColor = RegalGold,
                    unfocusedBorderColor = RegalGold.copy(alpha = 0.3f),
                    focusedContainerColor = VelvetCard,
                    unfocusedContainerColor = VelvetCard
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
            )

            // Sorting Selection Dropdown menu button
            Box {
                Button(
                    onClick = { expandedSortMenu = true },
                    colors = ButtonDefaults.buttonColors(containerColor = VelvetCard),
                    border = BorderStroke(0.5.dp, RegalGold.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 52.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort Icon",
                            tint = RegalGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = selectedSortOption,
                            color = GhostWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Dropdown Arrow",
                            tint = MutedSlate,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = expandedSortMenu,
                    onDismissRequest = { expandedSortMenu = false },
                    modifier = Modifier
                        .background(VelvetCard)
                        .border(0.5.dp, RegalGold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Rank", color = GhostWhite, fontSize = 13.sp) },
                        onClick = {
                            selectedSortOption = "Rank"
                            expandedSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Recent Activity", color = GhostWhite, fontSize = 13.sp) },
                        onClick = {
                            selectedSortOption = "Recent Activity"
                            expandedSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Newest Members", color = GhostWhite, fontSize = 13.sp) },
                        onClick = {
                            selectedSortOption = "Newest Members"
                            expandedSortMenu = false
                        }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            if (filteredAndSortedFriends.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "Searching for global citizens..." else "No citizens match your search filter.",
                            color = MutedSlate,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(visibleFriends) { friend ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.notifyProfileView(friend)
                                viewModel.showProfileForUserByName(
                                    friend.name,
                                    friend.flagEmoji,
                                    friend.currentRank,
                                    friend.territory
                                )
                            },
                        colors = CardDefaults.cardColors(containerColor = VelvetCard),
                        border = BorderStroke(0.5.dp, RegalGold.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(CharcoalObsidian)
                                                .border(1.dp, RegalGold, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = friend.name.take(1).uppercase(),
                                                color = RegalGold,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        // Custom overlapping status indicator dot for live/active status
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(EmeraldSuccess)
                                                .border(2.dp, VelvetCard, CircleShape)
                                                .align(Alignment.BottomEnd)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = friend.name,
                                                color = GhostWhite,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            HoverableFlag(friend.flagEmoji, friend.territory)
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(top = 1.dp)
                                        ) {
                                            Text(
                                                text = friend.username,
                                                color = MutedSlate,
                                                fontSize = 12.sp
                                            )
                                            Text("•", color = MutedSlate, fontSize = 11.sp)
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(EmeraldSuccess)
                                            )
                                            Text(
                                                text = "Active Now",
                                                color = EmeraldSuccess,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = RegalGold.copy(alpha = 0.12f)),
                                    border = BorderStroke(0.5.dp, RegalGold.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = friend.currentRank,
                                        color = RegalGold,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = friend.bio,
                                color = GhostWhite.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                maxLines = 2,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column {
                                        Text("KNOWLEDGE", color = MutedSlate, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${friend.knowledgeCredits} KC", color = RegalGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("CONTRIBUTION", color = MutedSlate, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                        Text("${friend.contributionCredits} CC", color = LustrousAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            viewModel.notifyProfileView(friend)
                                            viewModel.startChatWithUser(friend)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = RegalGold),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.Send, contentDescription = null, tint = CharcoalObsidian, modifier = Modifier.size(12.dp))
                                            Text("Message", color = CharcoalObsidian, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (filteredAndSortedFriends.size > lastVisibleIndex) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(
                                onClick = { lastVisibleIndex += 20 },
                                colors = ButtonDefaults.textButtonColors(contentColor = RegalGold)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        "LOAD MORE GLOBAL CITIZENS (${filteredAndSortedFriends.size - lastVisibleIndex} REMAINING)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// TAB 2: KNOWLEDGE ARENA (Interactive System Trivia Exams)
// ============================================================================
@Composable
fun KnowledgeArenaTab(viewModel: AppViewModel) {
    val currentQuestionIdx by viewModel.currentQuizIndex.collectAsState()
    val isQuizCompleted by viewModel.quizCompleted.collectAsState()
    val selectedAnswers by viewModel.selectedAnswers.collectAsState()

    val currentQuestion = viewModel.quizQuestions[currentQuestionIdx]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Topic Title Badge
        Card(
            colors = CardDefaults.cardColors(containerColor = VelvetCard),
            border = BorderStroke(1.dp, RegalGold),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.School, contentDescription = null, tint = RegalGold, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Imperial Evaluation Hub",
                    color = GhostWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!isQuizCompleted) {
            Text(
                "Verify Domain Excellence",
                color = GhostWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                "Earn Knowledge Credits (KC) by responding accurately below.",
                color = MutedSlate,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Current question panel
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(1.5.dp, RegalGold),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        // Question Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Question ${currentQuestionIdx + 1} of ${viewModel.quizQuestions.size}",
                                color = RegalGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Card(
                                colors = CardDefaults.cardColors(containerColor = ElectricBlue.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = currentQuestion.subject,
                                    color = ElectricBlue,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Question Text
                        Text(
                            text = currentQuestion.question,
                            color = GhostWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 24.sp
                        )
                    }

                    // Answer Options Column
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        currentQuestion.options.forEachIndexed { optionIdx, option ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.answerQuizQuestion(currentQuestion.id, optionIdx)
                                    },
                                colors = CardDefaults.cardColors(containerColor = CharcoalObsidian.copy(alpha = 0.5f)),
                                border = BorderStroke(1.dp, MutedSlate.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .border(1.dp, RegalGold, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ('A' + optionIdx).toString(),
                                            color = RegalGold,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = option,
                                        color = GhostWhite,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Exam success congratulations panel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 40.dp),
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(2.dp, RegalGold),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.School, contentDescription = null, tint = RegalGold, modifier = Modifier.size(72.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Evaluation Successful!",
                        color = GhostWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Your domain expertise has been recorded in the open ledger. You have completed this evaluation loop and boosted your territory's global intellectual ranking.",
                        color = MutedSlate,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    Button(
                        onClick = { viewModel.resetQuiz() },
                        colors = ButtonDefaults.buttonColors(containerColor = RegalGold)
                    ) {
                        Text("LAUNCH NEW PRACTICE MODULE", color = CharcoalObsidian, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ============================================================================
// TAB 3: MERCANTILE CHATS (The Three-Connection Rule direct messenger)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagingTab(viewModel: AppViewModel) {
    val activeRooms by viewModel.activeChatRooms.collectAsState()
    val waitingRooms by viewModel.waitingChatRooms.collectAsState()
    val selectedId by viewModel.selectedRoomId.collectAsState()
    val messagesList by viewModel.currentChatMessages.collectAsState()

    var chatMessageText by remember { mutableStateOf("") }
    val typingUsers by viewModel.typingUsers.collectAsState()

    LaunchedEffect(chatMessageText) {
        val roomId = selectedId
        if (roomId != null) {
            if (chatMessageText.isNotEmpty()) {
                // Throttle typing updates with a 400ms debounce
                delay(400)
                viewModel.sendTyping(roomId)
                delay(3000)
                viewModel.sendStopTyping(roomId)
            } else {
                viewModel.sendStopTyping(roomId)
            }
        }
    }
    var roomToDelete by remember { mutableStateOf<ChatRoomEntity?>(null) }

    if (roomToDelete != null) {
        Dialog(onDismissRequest = { roomToDelete = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(1.dp, RegalGold.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Chat",
                        tint = CrimsonRep,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Delete Connection?",
                        color = GhostWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Are you sure you want to permanently delete your chat with ${roomToDelete?.participantName?.split("|")?.lastOrNull()?.trim() ?: roomToDelete?.participantName}? This action will wipe all local messages.",
                        color = MutedSlate,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = { roomToDelete = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CANCEL", color = MutedSlate, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                roomToDelete?.id?.let { id ->
                                    viewModel.deleteRoom(id)
                                }
                                roomToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRep),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("DELETE", color = GhostWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (selectedId == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Connection Limitation Warning Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(1.dp, LustrousAmber),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.SafetyCheck, contentDescription = null, tint = LustrousAmber, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "The Three-Connection Rule",
                            color = GhostWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "To preserve conversational focus, you may hold exactly 3 active chats. Others reside in the waiting ledger below.",
                            color = MutedSlate,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Divider: Active Slots count
            Text(
                "Active Connections (Slots Used: ${activeRooms.size} / 3)",
                color = RegalGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // ACTIVE CHATS LIST
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (activeRooms.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = VelvetCard.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No active slots. Swap a room from the wait pool below.", color = MutedSlate, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    items(activeRooms) { room ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.swapOrActivateRoom(room.id) },
                            colors = CardDefaults.cardColors(containerColor = VelvetCard),
                            border = BorderStroke(1.dp, RegalGold.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(46.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(CharcoalObsidian)
                                                .border(1.dp, RegalGold, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = room.participantName.take(1).uppercase(),
                                                color = RegalGold,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .offset(x = 2.dp, y = 2.dp)
                                                .background(CharcoalObsidian, CircleShape)
                                                .border(0.5.dp, RegalGold.copy(alpha = 0.5f), CircleShape)
                                                .padding(horizontal = 2.dp, vertical = 0.5.dp)
                                        ) {
                                            Text(room.participantFlag, fontSize = 8.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(room.participantName, color = GhostWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text(room.lastMessage, color = MutedSlate, fontSize = 12.sp, maxLines = 1)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Archive slot action button
                                    IconButton(onClick = { viewModel.archiveRoom(room.id) }) {
                                        Icon(Icons.Default.Inventory, contentDescription = "Archive Slot", tint = MutedSlate, modifier = Modifier.size(20.dp))
                                    }
                                    // Delete connection button
                                    IconButton(onClick = { roomToDelete = room }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Connection", tint = CrimsonRep.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = RegalGold)
                                }
                            }
                        }
                    }
                }

                // Divider: Waiting ledger queue
                item {
                    Text(
                        "Incoming Waiting Queue (${waitingRooms.size} requests)",
                        color = MutedSlate,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                    )
                }

                // BLURRED / LOCKED WAITING QUEUE LIST
                if (waitingRooms.isEmpty()) {
                    item {
                        Text(
                            "Waiting matrix is completely clear.",
                            color = MutedSlate,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                        )
                    }
                } else {
                    items(waitingRooms) { room ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.swapOrActivateRoom(room.id)
                                },
                            colors = CardDefaults.cardColors(containerColor = VelvetCard.copy(alpha = 0.4f)),
                            border = BorderStroke(0.5.dp, MutedSlate.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.blur(if (activeRooms.size >= 3) 4.dp else 0.dp) // Blur effect representing structural limits
                                ) {
                                    Box(
                                        modifier = Modifier.size(46.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(CharcoalObsidian)
                                                .border(1.dp, RegalGold, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = room.participantName.take(1).uppercase(),
                                                color = RegalGold,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .offset(x = 2.dp, y = 2.dp)
                                                .background(CharcoalObsidian, CircleShape)
                                                .border(0.5.dp, RegalGold.copy(alpha = 0.5f), CircleShape)
                                                .padding(horizontal = 2.dp, vertical = 0.5.dp)
                                        ) {
                                            Text(room.participantFlag, fontSize = 8.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(room.participantName, color = GhostWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text("[Locked queue: swap to read]", color = MutedSlate, fontSize = 11.sp)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { roomToDelete = room },
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Connection",
                                            tint = CrimsonRep.copy(alpha = 0.8f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Button(
                                        onClick = { viewModel.swapOrActivateRoom(room.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = RegalGold),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("ACTIVATE", color = CharcoalObsidian, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Individual Chat messaging room interface view
        val activeRoom = (activeRooms + waitingRooms).find { it.id == selectedId }
        Column(modifier = Modifier.fillMaxSize()) {
            // Room Header strip - allows clicking profile details of chat partner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VelvetCard)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.selectRoom(null) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = RegalGold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Row(
                        modifier = Modifier.clickable {
                            activeRoom?.let { room ->
                                viewModel.showProfileForUserByName(room.participantName, room.participantFlag, room.participantRank, room.participantTerritory)
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(46.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(CharcoalObsidian)
                                    .border(1.dp, RegalGold, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (activeRoom?.participantName ?: "C").take(1).uppercase(),
                                    color = RegalGold,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 2.dp, y = 2.dp)
                                    .background(CharcoalObsidian, CircleShape)
                                    .border(0.5.dp, RegalGold.copy(alpha = 0.5f), CircleShape)
                                    .padding(horizontal = 2.dp, vertical = 0.5.dp)
                            ) {
                                Text(activeRoom?.participantFlag ?: "🌍", fontSize = 8.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(activeRoom?.participantName ?: "Conversation partner", color = GhostWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(activeRoom?.participantRank ?: "Citizen", color = RegalGold, fontSize = 11.sp)
                        }
                    }
                }

                // Fast actions inside room
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            activeRoom?.id?.let { id ->
                                viewModel.archiveRoom(id)
                            }
                        }
                    ) {
                        Text("ARCHIVE", color = RegalGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    IconButton(
                        onClick = {
                            activeRoom?.let { room ->
                                roomToDelete = room
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Connection",
                            tint = CrimsonRep,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            val listState = rememberLazyListState()
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            val isKeyboardOpen = androidx.compose.foundation.layout.WindowInsets.isImeVisible

            var lastLoadedTopId by remember { mutableStateOf<Int?>(null) }
            val currentMessagesState by androidx.compose.runtime.rememberUpdatedState(messagesList)
            var isLoadingOlder by remember { mutableStateOf(false) }
            var hasMoreMessages by remember { mutableStateOf(true) }

            // Whenever the room changes, reset pagination state
            LaunchedEffect(selectedId) {
                hasMoreMessages = true
                isLoadingOlder = false
                lastLoadedTopId = null
            }

            // Infinite scrolling pagination: load older messages when user scrolls to top
            LaunchedEffect(listState.firstVisibleItemIndex) {
                if (listState.firstVisibleItemIndex == 0 && currentMessagesState.isNotEmpty() && !isLoadingOlder && hasMoreMessages) {
                    val topMsg = currentMessagesState.firstOrNull()
                    if (topMsg != null && topMsg.id != lastLoadedTopId) {
                        val countAtTrigger = currentMessagesState.size
                        if (countAtTrigger >= 30) {
                            lastLoadedTopId = topMsg.id
                            isLoadingOlder = true
                            
                            val visibleIndex = listState.firstVisibleItemIndex
                            val visibleOffset = listState.firstVisibleItemScrollOffset
                            val anchorItem = currentMessagesState.getOrNull(visibleIndex)

                            viewModel.loadMoreMessages()
                            // Delay to allow Database/Room to emit new messages
                            kotlinx.coroutines.delay(1000)
                            isLoadingOlder = false

                            if (currentMessagesState.size <= countAtTrigger) {
                                hasMoreMessages = false
                            } else if (anchorItem != null) {
                                val newIndex = currentMessagesState.indexOfFirst { it.id == anchorItem.id }
                                if (newIndex != -1) {
                                    listState.scrollToItem(newIndex, visibleOffset)
                                }
                            }
                        }
                    }
                }
            }

            var lastRoomId by remember { mutableStateOf<Int?>(null) }
            var lastCount by remember { mutableStateOf(0) }

            LaunchedEffect(selectedId) {
                if (selectedId != lastRoomId) {
                    lastRoomId = selectedId
                    lastCount = 0
                }
            }

            // Auto-scrolling on new message insertion or room open, matching WhatsApp
            LaunchedEffect(messagesList) {
                if (messagesList.isNotEmpty()) {
                    val lastMsg = messagesList.lastOrNull()
                    if (lastMsg != null) {
                        val isMe = lastMsg.senderId == "me"
                        val sizeIncreased = messagesList.size > lastCount && lastCount > 0
                        val isRoomOpening = lastCount == 0

                        val isNearBottom = if (messagesList.size > 1) {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                            lastVisible != null && lastVisible.index >= messagesList.size - 4
                        } else true

                        if (isRoomOpening) {
                            listState.scrollToItem(messagesList.size - 1)
                        } else if (sizeIncreased) {
                            if (isMe || isNearBottom) {
                                listState.animateScrollToItem(messagesList.size - 1)
                            }
                        }
                    }
                    lastCount = messagesList.size
                } else {
                    lastCount = 0
                }
            }

            // Smoothly scroll to bottom when keyboard opens to prevent clipping
            LaunchedEffect(isKeyboardOpen) {
                if (isKeyboardOpen && messagesList.isNotEmpty()) {
                    listState.animateScrollToItem(messagesList.size - 1)
                }
            }

            // Messaging thread log with auto scrolls to bottom
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)
            ) {
                itemsIndexed(
                    items = messagesList,
                    key = { _, message -> message.id }
                ) { index, message ->
                    val isMe = message.senderId == "me"
                    val prevMessage = if (index > 0) messagesList[index - 1] else null
                    val isConsecutive = prevMessage != null && prevMessage.senderId == message.senderId
                    val spaceBefore = if (isConsecutive) 2.dp else 8.dp

                    Spacer(modifier = Modifier.height(spaceBefore))

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        val clipboardManager = LocalClipboardManager.current
                        val localContext = LocalContext.current
                        val textToCopy = message.messageText.trim()
                        val messageTimeAndDay = remember(message.timestamp) {
                            try {
                                val sdf = java.text.SimpleDateFormat("EEEE, hh:mm a", java.util.Locale.getDefault())
                                sdf.format(java.util.Date(message.timestamp))
                            } catch (e: Exception) {
                                ""
                            }
                        }
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMe) RegalGold.copy(alpha = 0.15f) else VelvetCard
                            ),
                            border = BorderStroke(0.5.dp, if (isMe) RegalGold else MutedSlate.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(
                                topStart = if (isConsecutive && isMe) 4.dp else 12.dp,
                                topEnd = if (isConsecutive && !isMe) 4.dp else 12.dp,
                                bottomStart = if (isMe) 12.dp else 0.dp,
                                bottomEnd = if (isMe) 0.dp else 12.dp
                            ),
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(textToCopy))
                                    Toast.makeText(localContext, "Message copied!", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(
                                    text = textToCopy,
                                    color = GhostWhite,
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = messageTimeAndDay,
                                        color = MutedSlate,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                    if (isMe) {
                                        val (icon, tint) = when (message.status) {
                                            "Read" -> Pair(Icons.Default.DoneAll, androidx.compose.ui.graphics.Color(0xFF34B7F1))
                                            "Delivered" -> Pair(Icons.Default.DoneAll, MutedSlate.copy(alpha = 0.6f))
                                            else -> Pair(Icons.Default.Done, MutedSlate.copy(alpha = 0.6f))
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = message.status,
                                            tint = tint,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Lock typing unless the connection is fully active
            val isConnectionActive = activeRoom?.isActive == true
            
            // Typing indicator
            val typingUser = typingUsers[selectedId]
            if (typingUser != null) {
                Text(
                    text = "$typingUser is typing...",
                    color = RegalGold,
                    fontSize = 11.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                )
            }

            // Keyboard bottom write tray
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VelvetCard)
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = if (isConnectionActive) chatMessageText else "",
                    onValueChange = { if (isConnectionActive) chatMessageText = it },
                    placeholder = { 
                        Text(
                            text = if (isConnectionActive) "Message..." 
                            else "Archived slot: Swap from queue to activate & send",
                            fontSize = 13.sp,
                            color = MutedSlate
                        ) 
                    },
                    enabled = isConnectionActive,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = GhostWhite,
                        unfocusedTextColor = GhostWhite,
                        focusedContainerColor = CharcoalObsidian,
                        unfocusedContainerColor = CharcoalObsidian,
                        disabledContainerColor = CharcoalObsidian.copy(alpha = 0.5f),
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = GhostWhite,
                        fontSize = 14.sp,
                        lineHeight = 18.sp
                    ),
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 4,
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, if (chatMessageText.isNotEmpty()) RegalGold else MutedSlate.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (isConnectionActive && chatMessageText.isNotBlank()) {
                            activeRoom?.id?.let { id ->
                                viewModel.sendMessage(id, chatMessageText)
                            }
                            chatMessageText = ""
                        }
                    },
                    enabled = isConnectionActive,
                    modifier = Modifier
                        .background(if (isConnectionActive) RegalGold else MutedSlate.copy(alpha = 0.3f), CircleShape)
                        .size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send, 
                        contentDescription = "Shoot", 
                        tint = if (isConnectionActive) CharcoalObsidian else MutedSlate,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
// TAB 4: PROFILE & DEMOCRATIC monarchy elections
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectionsAndProfileTab(viewModel: AppViewModel) {
    val me by viewModel.currentUserFlow.collectAsState()
    val visitors by viewModel.profileVisitors.collectAsState()
    val candidates by viewModel.electionCandidates.collectAsState()
    val leaderboardUsers by viewModel.leaderboardUsers.collectAsState()
    val currentKing by viewModel.currentKing.collectAsState()
    val currentQueen by viewModel.currentQueen.collectAsState()

    var showCampaignModalByMe by remember { mutableStateOf(false) }
    var campaignVision by remember { mutableStateOf("") }
    var campaignManifesto by remember { mutableStateOf("") }

    var editingPostState by remember { mutableStateOf<PostEntity?>(null) }
    var editPostContentText by remember { mutableStateOf("") }

    // Edit Profile state flows
    val showEditProfileModal by viewModel.showEditProfileDialog.collectAsState()
    var editName by remember(me?.name) { mutableStateOf(me?.name?.replace("|", "") ?: "") }
    var editUsername by remember(me?.username) { mutableStateOf(me?.username?.removePrefix("@")?.filter { !it.isWhitespace() } ?: "") }
    var editBio by remember(me?.bio) { mutableStateOf(me?.bio ?: "") }
    var editTerritory by remember(me?.territory) { mutableStateOf(me?.territory ?: "") }
    var editFlagEmoji by remember(me?.flagEmoji) { mutableStateOf(me?.flagEmoji ?: "🌍") }
    var editProfilePhoto by remember(me?.profilePhoto) { mutableStateOf(me?.profilePhoto ?: "") }
    var postToDelete by remember { mutableStateOf<PostEntity?>(null) }

    LaunchedEffect(showEditProfileModal) {
        if (showEditProfileModal && me != null) {
            editName = me!!.name
            editUsername = me!!.username.removePrefix("@")
            editBio = me!!.bio
            editTerritory = me!!.territory
            editFlagEmoji = me!!.flagEmoji
            editProfilePhoto = me!!.profilePhoto
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Real-Time Profile Ledger Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(1.dp, RegalGold),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column {
                    // Accent cover panel
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(RegalGold.copy(alpha = 0.4f), ElectricBlue.copy(alpha = 0.4f))
                                )
                            )
                    )

                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            // Circular Avatar and Flag Overlay
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .offset(y = (-20).dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(CharcoalObsidian)
                                        .border(2.dp, RegalGold, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (me?.profilePhoto?.isNotBlank() == true) me!!.profilePhoto.take(1).uppercase() else (me?.name ?: "N").take(1).uppercase(),
                                        color = RegalGold,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = (-2).dp, y = (-2).dp)
                                        .background(CharcoalObsidian, CircleShape)
                                        .border(1.dp, RegalGold.copy(alpha = 0.5f), CircleShape)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(me?.flagEmoji ?: "🌍", fontSize = 14.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        me?.name ?: "Noble Citizen",
                                        color = GhostWhite,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = RegalGold.copy(alpha = 0.15f)),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            me?.currentRank ?: "Citizen",
                                            color = RegalGold,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    me?.username ?: "@username",
                                    color = MutedSlate,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Personality Traits selection array
                        Text(
                            "Personality Blueprint Traits",
                            color = RegalGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            me?.personalityTraits?.split(",")?.forEach { trait ->
                                if (trait.isNotBlank()) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = CharcoalObsidian),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = trait,
                                            color = GhostWhite,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Biography Content Field
                        Text(
                            text = me?.bio ?: "Dedicated to open collaborative work across territories.",
                            color = GhostWhite.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )

                        // Edit Personal Profile Synchronization Button
                        Button(
                            onClick = { viewModel.setShowEditProfileDialog(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = RegalGold.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, RegalGold),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = RegalGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Edit", color = RegalGold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // MERITOCRATIC RANK ADVANCEMENT CARD
        item {
            val meUser = me
            val currentRank = meUser?.currentRank ?: "Citizen"
            val kc = meUser?.knowledgeCredits ?: 0
            val cc = meUser?.contributionCredits ?: 0
            val totalCredits = kc + cc

            val (nextRank, targetCredits, prevThreshold) = when {
                totalCredits < 100 -> Triple("Guardian", 100, 0)
                totalCredits < 250 -> Triple("Noble", 250, 100)
                totalCredits < 500 -> Triple("Prince", 500, 250)
                else -> Triple("Arch-Prince", 1000, 500)
            }

            val progressInLevel = (totalCredits - prevThreshold).coerceAtLeast(0)
            val levelSpan = targetCredits - prevThreshold
            val progressFraction = if (levelSpan > 0) {
                progressInLevel.toFloat() / levelSpan.toFloat()
            } else {
                1.0f
            }.coerceIn(0.0f, 1.0f)

            val creditsNeeded = (targetCredits - totalCredits).coerceAtLeast(0)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(1.dp, RegalGold.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Meritocratic Rank Status",
                                color = GhostWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Sovereign Advancement Pathway",
                                color = MutedSlate,
                                fontSize = 11.sp
                            )
                        }
                        
                        // Active tier badge
                        Box(
                            modifier = Modifier
                                .background(RegalGold.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "TIER ${when(currentRank) {
                                    "Citizen" -> "I"
                                    "Guardian" -> "II"
                                    "Noble" -> "III"
                                    "Prince" -> "IV"
                                    else -> "MAX"
                                }}",
                                color = RegalGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "Current: $currentRank ${when(currentRank) {
                                    "Citizen" -> "🌱"
                                    "Guardian" -> "🛡️"
                                    "Noble" -> "⚜️"
                                    "Prince" -> "👑"
                                    else -> "💎"
                                }}",
                                color = GhostWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$totalCredits / $targetCredits credits accumulated",
                                color = MutedSlate,
                                fontSize = 11.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Next: $nextRank",
                                color = RegalGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$creditsNeeded remaining",
                                color = MutedSlate,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MutedSlate.copy(alpha = 0.15f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressFraction)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(RegalGold, LustrousAmber)
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Key details checklist / Benefits of Next Tier
                    Text(
                        text = "Next Tier Privileges & Missions",
                        color = RegalGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val missionDescription = when (nextRank) {
                            "Guardian" -> listOf(
                                "🛡️ Register as political candidate",
                                "🛡️ Unlock secondary messaging forums",
                                "🛡️ Gain 20% faster reputation recovery"
                            )
                            "Noble" -> listOf(
                                "⚜️ Propose formal bills in Public Square",
                                "⚜️ Set up collaborative service quests",
                                "⚜️ Moderate lower citizen arbitration disputes"
                            )
                            "Prince" -> listOf(
                                "👑 Issue sovereign structural decrees",
                                "👑 Command special elite legions",
                                "👑 Secure high advisory executive seats"
                            )
                            else -> listOf(
                                "💎 Arch-status elite merit achieved",
                                "💎 Full cosmic sovereignty active",
                                "💎 Influence global policy and protocols"
                            )
                        }

                        missionDescription.forEach { benefit ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 1.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldSuccess,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = benefit,
                                    color = GhostWhite.copy(alpha = 0.8f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Boost Rank Action Button (One Word)
                    Button(
                        onClick = {
                            viewModel.selectTab(DashboardTab.MissionsAndLegends)
                            viewModel.selectMissionsSubTab(1)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RegalGold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Earn",
                            color = CharcoalObsidian,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // TRANSPARENT RECIPROCAL VISIBILITY LEDGER
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(0.5.dp, MutedSlate.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Reciprocal Profile Visitors Ledger",
                            color = GhostWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(Icons.Default.Visibility, contentDescription = null, tint = RegalGold, modifier = Modifier.size(16.dp))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    visitors.forEach { visitor ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    viewModel.showProfileForUserByName(visitor.visitorName, visitor.visitorFlag, visitor.visitorRank, visitor.visitorTerritory)
                                }
                            ) {
                                Box(
                                    modifier = Modifier.size(46.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(CharcoalObsidian)
                                            .border(1.dp, RegalGold, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = visitor.visitorName.take(1).uppercase(),
                                            color = RegalGold,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = 2.dp, y = 2.dp)
                                            .background(CharcoalObsidian, CircleShape)
                                            .border(0.5.dp, RegalGold.copy(alpha = 0.5f), CircleShape)
                                            .padding(horizontal = 2.dp, vertical = 0.5.dp)
                                    ) {
                                        Text(visitor.visitorFlag, fontSize = 8.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(visitor.visitorName, color = GhostWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${visitor.visitorRank} • ${visitor.visitorTerritory}", color = MutedSlate, fontSize = 9.sp)
                                }
                            }

                            Text("Checked now", color = RegalGold, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // My Broadcasted Entries Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(0.5.dp, RegalGold.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "My Broadcasted Entries",
                            color = GhostWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(Icons.Default.Feed, contentDescription = null, tint = RegalGold, modifier = Modifier.size(16.dp))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val allPosts by viewModel.rankedPosts.collectAsState()
                    val meUser by viewModel.currentUserFlow.collectAsState()
                    val myPosts = remember(allPosts, meUser) { 
                        allPosts.filter { 
                            it.authorId == "me" || 
                            (meUser != null && it.authorId.equals(meUser?.email, ignoreCase = true)) ||
                            (meUser != null && it.authorName.equals(meUser?.name, ignoreCase = true)) 
                        } 
                    }

                    if (myPosts.isEmpty()) {
                        Text(
                            text = "No entries broadcasted to the Public Square yet.",
                            color = MutedSlate,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        myPosts.forEach { post ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CharcoalObsidian),
                                border = BorderStroke(0.5.dp, MutedSlate.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = RegalGold.copy(alpha = 0.15f)),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = post.category,
                                                color = RegalGold,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            IconButton(
                                                onClick = {
                                                    editingPostState = post
                                                    editPostContentText = post.content
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Post", tint = ElectricBlue, modifier = Modifier.size(14.dp))
                                            }

                                            IconButton(
                                                onClick = {
                                                    postToDelete = post
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete Post", tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = post.content,
                                        color = GhostWhite,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("🧠 ${post.knowledgeValue} Wise", color = MutedSlate, fontSize = 10.sp)
                                        Text("🤝 ${post.contributionProof} Helpful", color = MutedSlate, fontSize = 10.sp)
                                        Text("✨ ${(post.reputationImpact - 90).coerceAtLeast(0)} Inspiring", color = MutedSlate, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // MONARCHY AND DEMOCRATIC CREDENTIALS LEADERBOARD
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(1.dp, RegalGold),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Monarch Sovereign & Merit Leaderboard",
                            color = GhostWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = RegalGold, modifier = Modifier.size(18.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // King and Queen highlights - Using authoritative backend flows with local leaderboard fallback (strict monarch check + score fallback)
                    val kingUser = currentKing
                        ?: me?.takeIf { it.currentRank.equals("King", ignoreCase = true) }
                        ?: leaderboardUsers.firstOrNull { it.currentRank.equals("King", ignoreCase = true) }
                        ?: leaderboardUsers.firstOrNull { it.gender.equals("Male", ignoreCase = true) }

                    val queenUser = currentQueen
                        ?: me?.takeIf { it.currentRank.equals("Queen", ignoreCase = true) }
                        ?: leaderboardUsers.firstOrNull { it.currentRank.equals("Queen", ignoreCase = true) && it.id != kingUser?.id }
                        ?: leaderboardUsers.firstOrNull { it.gender.equals("Female", ignoreCase = true) && it.id != kingUser?.id }

                    // Defensive logging to trace rendering stability across devices
                    LaunchedEffect(kingUser, queenUser) {
                        android.util.Log.d("LeaderboardUI", "👑 [RENDER] King: ${kingUser?.name ?: "NULL"}, Queen: ${queenUser?.name ?: "NULL"}")
                    }

                    if (kingUser != null) {
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            border = BorderStroke(1.5.dp, RegalGold),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .clickable { viewModel.showProfileForUser(kingUser.id) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                CharcoalObsidian,
                                                Color(0xFF2C2214)
                                            )
                                        )
                                    )
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Crown section with soft background glow
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(46.dp)
                                            .background(RegalGold.copy(alpha = 0.12f), CircleShape)
                                            .border(1.dp, RegalGold.copy(alpha = 0.3f), CircleShape)
                                    ) {
                                        Text("👑", fontSize = 22.sp)
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("KING OF THE REALM", color = RegalGold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(kingUser.name, color = GhostWhite, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("${kingUser.currentRank} • ${kingUser.flagEmoji} ${kingUser.territory}", color = MutedSlate.copy(alpha = 0.85f), fontSize = 10.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = RegalGold.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.5.dp, RegalGold.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "${kingUser.knowledgeCredits + kingUser.contributionCredits} ★",
                                            color = RegalGold,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Elegant Loading / Empty Placeholder for King
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CharcoalObsidian.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, RegalGold.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(RegalGold.copy(alpha = 0.04f), CircleShape)
                                        .border(1.dp, RegalGold.copy(alpha = 0.1f), CircleShape)
                                ) {
                                    Text("👑", fontSize = 22.sp, color = RegalGold.copy(alpha = 0.4f))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text("KING OF THE REALM", color = RegalGold.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Crown Vacant / Loading...", color = GhostWhite.copy(alpha = 0.5f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (queenUser != null) {
                        Card(
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            border = BorderStroke(1.5.dp, Color(0xFFE5A4F4)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .clickable { viewModel.showProfileForUser(queenUser.id) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                CharcoalObsidian,
                                                Color(0xFF271B2B)
                                            )
                                        )
                                    )
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Crown section with soft background glow
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(46.dp)
                                            .background(Color(0xFFE5A4F4).copy(alpha = 0.12f), CircleShape)
                                            .border(1.dp, Color(0xFFE5A4F4).copy(alpha = 0.3f), CircleShape)
                                    ) {
                                        Text("👑", fontSize = 22.sp)
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("QUEEN OF THE REALM", color = Color(0xFFE5A4F4), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(queenUser.name, color = GhostWhite, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("${queenUser.currentRank} • ${queenUser.flagEmoji} ${queenUser.territory}", color = MutedSlate.copy(alpha = 0.85f), fontSize = 10.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = Color(0xFFE5A4F4).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.5.dp, Color(0xFFE5A4F4).copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "${queenUser.knowledgeCredits + queenUser.contributionCredits} ★",
                                            color = Color(0xFFE5A4F4),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Elegant Loading / Empty Placeholder for Queen
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CharcoalObsidian.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, Color(0xFFE5A4F4).copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(Color(0xFFE5A4F4).copy(alpha = 0.04f), CircleShape)
                                        .border(1.dp, Color(0xFFE5A4F4).copy(alpha = 0.1f), CircleShape)
                                ) {
                                    Text("👑", fontSize = 22.sp, color = Color(0xFFE5A4F4).copy(alpha = 0.4f))
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text("QUEEN OF THE REALM", color = Color(0xFFE5A4F4).copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Crown Vacant / Loading...", color = GhostWhite.copy(alpha = 0.5f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Divider(color = MutedSlate.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        "Merit Rank Ledger:",
                        color = RegalGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    leaderboardUsers.forEachIndexed { index, user ->
                        val totalCredits = user.knowledgeCredits + user.contributionCredits
                        val rowBg = if (ThemeState.isLight) Color(0xFFF1F5F9) else Color.White.copy(alpha = 0.02f)
                        val rowBorder = if (ThemeState.isLight) Color(0xFFE2E8F0) else Color.White.copy(alpha = 0.05f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .background(rowBg, RoundedCornerShape(10.dp))
                                .border(0.5.dp, rowBorder, RoundedCornerShape(10.dp))
                                .clickable { viewModel.showProfileForUser(user.id) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                // Dynamic premium badge numbers
                                val badgeBg = if (index == 0) RegalGold.copy(alpha = 0.15f)
                                              else if (ThemeState.isLight) Color(0xFFE2E8F0)
                                              else Color.White.copy(alpha = 0.05f)
                                val badgeBorder = if (index == 0) RegalGold.copy(alpha = 0.5f)
                                                  else if (ThemeState.isLight) Color(0xFFCBD5E1)
                                                  else Color.White.copy(alpha = 0.1f)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(badgeBg, CircleShape)
                                        .border(0.5.dp, badgeBorder, CircleShape)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = if (index == 0) RegalGold else GhostWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(user.flagEmoji, fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        user.name,
                                        color = GhostWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${user.currentRank} • ${user.territory}",
                                            color = MutedSlate,
                                            fontSize = 9.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = ElectricBlue.copy(alpha = 0.15f)),
                                            border = BorderStroke(0.5.dp, ElectricBlue.copy(alpha = 0.5f)),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = getAutoSuggestedRole(user),
                                                color = ElectricBlue,
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = "$totalCredits Credits",
                                color = if (index < 2) RegalGold else GhostWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // CONSTITUTION ELECTION CARD
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(1.dp, RegalGold),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Sovereign House of Representatives Election",
                        color = GhostWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Power is not dynastic. Our King and Queen are elected democratically using merit weights:",
                        color = MutedSlate,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Formula visualization
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CharcoalObsidian),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Leadership_Score Formula:",
                                color = RegalGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Score = (0.70 * KnowledgeCredits_Normalized) +\n            (0.30 * PublicVoting_Normalized)",
                                color = GhostWhite,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Active Candidates Pool",
                            color = GhostWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Register candidacy switch button
                        if (me?.isCandidate == false) {
                            Button(
                                onClick = {
                                    campaignVision = me?.campaignVision ?: ""
                                    campaignManifesto = me?.campaignManifesto ?: ""
                                    showCampaignModalByMe = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RegalGold),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("RUN FOR CROWN", color = CharcoalObsidian, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = EmeraldSuccess.copy(alpha = 0.15f)),
                                    border = BorderStroke(0.5.dp, EmeraldSuccess),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "REGISTERED",
                                        color = EmeraldSuccess,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        campaignVision = me?.campaignVision ?: ""
                                        campaignManifesto = me?.campaignManifesto ?: ""
                                        showCampaignModalByMe = true
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Campaign",
                                        tint = RegalGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Candidate card lists
                    candidates.forEach { candidate ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = CharcoalObsidian.copy(alpha = 0.4f)),
                            border = BorderStroke(0.5.dp, MutedSlate.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            viewModel.showProfileForUser(candidate.id)
                                        }
                                    ) {
                                        Text(candidate.flagEmoji, fontSize = 22.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(candidate.name, color = GhostWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text("Sovereign Candidate • Pop: ${candidate.votesCount} votes", color = RegalGold, fontSize = 10.sp)
                                        }
                                    }

                                    Button(
                                        onClick = { viewModel.castVote(candidate.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = RegalGold),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(28.dp),
                                        enabled = me?.hasVoted == false
                                    ) {
                                        Text("CAST VOTE", color = CharcoalObsidian, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Text(
                                    text = "Vision: ${candidate.campaignVision}",
                                    color = GhostWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 8.dp)
                                )

                                Text(
                                    text = candidate.campaignManifesto,
                                    color = MutedSlate,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Register candidacy compiler
    if (showCampaignModalByMe) {
        Dialog(
            onDismissRequest = { showCampaignModalByMe = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(1.dp, RegalGold),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .widthIn(max = 450.dp)
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight()
                    .imePadding()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Dossier: Royal Sovereign Crown Setup",
                        color = GhostWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = campaignVision,
                        onValueChange = { campaignVision = it },
                        label = { Text("Planetary Vision Statement") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedBorderColor = RegalGold,
                            unfocusedBorderColor = MutedSlate
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = campaignManifesto,
                        onValueChange = { campaignManifesto = it },
                        label = { Text("Campaign Manifesto & Methods") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedBorderColor = RegalGold,
                            unfocusedBorderColor = MutedSlate
                        ),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = { showCampaignModalByMe = false }) {
                            Text("ABORT", color = MutedSlate)
                        }

                        Button(
                            onClick = {
                                if (campaignVision.isNotBlank() && campaignManifesto.isNotBlank()) {
                                    viewModel.registerElectionsCandidate(campaignManifesto, campaignVision)
                                    showCampaignModalByMe = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RegalGold)
                        ) {
                            Text("SUBMIT BALLOT DOSSIER", color = CharcoalObsidian)
                        }
                    }
                }
            }
        }
    }

    if (showEditProfileModal) {
        Dialog(
            onDismissRequest = { viewModel.setShowEditProfileDialog(false) },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(1.dp, RegalGold),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .widthIn(max = 450.dp)
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight()
                    .imePadding()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Synchronize Imperial Profile",
                        color = GhostWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Noble Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedBorderColor = RegalGold,
                            unfocusedBorderColor = MutedSlate
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { editUsername = it },
                        label = { Text("System Handle / Username") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedBorderColor = RegalGold,
                            unfocusedBorderColor = MutedSlate
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = { Text("Personal Biography / Credo") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedBorderColor = RegalGold,
                            unfocusedBorderColor = MutedSlate
                        ),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = editTerritory,
                        onValueChange = { editTerritory = it },
                        label = { Text("Territory Assigned") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedBorderColor = RegalGold,
                            unfocusedBorderColor = MutedSlate
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = editFlagEmoji,
                        onValueChange = { editFlagEmoji = it },
                        label = { Text("Territory Emoji Flag") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedBorderColor = RegalGold,
                            unfocusedBorderColor = MutedSlate
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = editProfilePhoto,
                        onValueChange = { editProfilePhoto = it },
                        label = { Text("Profile Photo URL or Avatar") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedBorderColor = RegalGold,
                            unfocusedBorderColor = MutedSlate
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = { viewModel.setShowEditProfileDialog(false) }) {
                            Text("ABORT", color = MutedSlate)
                        }

                        Button(
                            onClick = {
                                if (editName.isNotBlank() && editUsername.isNotBlank()) {
                                    viewModel.updateUserProfile(
                                        name = editName,
                                        username = editUsername,
                                        bio = editBio,
                                        territory = editTerritory,
                                        flagEmoji = editFlagEmoji,
                                        profilePhoto = editProfilePhoto
                                    )
                                    viewModel.setShowEditProfileDialog(false)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RegalGold)
                        ) {
                            Text("SYNCHRONIZE", color = CharcoalObsidian)
                        }
                    }
                }
            }
        }
    }

    if (editingPostState != null) {
        Dialog(
            onDismissRequest = { editingPostState = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(1.dp, RegalGold),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .widthIn(max = 450.dp)
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight()
                    .imePadding()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Edit Public Square Entry",
                        color = GhostWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editPostContentText,
                        onValueChange = { editPostContentText = it },
                        label = { Text("What would you like to say?") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GhostWhite,
                            unfocusedTextColor = GhostWhite,
                            focusedBorderColor = RegalGold,
                            unfocusedBorderColor = MutedSlate
                        ),
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = { editingPostState = null }) {
                            Text("CANCEL", color = MutedSlate)
                        }

                        Button(
                            onClick = {
                                if (editPostContentText.isNotBlank()) {
                                    viewModel.updatePostContent(editingPostState!!.id, editPostContentText)
                                    editingPostState = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RegalGold)
                        ) {
                            Text("UPDATE", color = CharcoalObsidian)
                        }
                    }
                }
            }
        }
    }

    if (postToDelete != null) {
        Dialog(onDismissRequest = { postToDelete = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                border = BorderStroke(1.5.dp, CrimsonRep),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = CrimsonRep,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Verify Post Deletion",
                        color = GhostWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Are you sure you want to permanently delete this broadcast entry from the Ledger?",
                        color = MutedSlate,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = { postToDelete = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CANCEL", color = MutedSlate, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                postToDelete?.let { post ->
                                    viewModel.deletePost(post.id)
                                }
                                postToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonRep),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Text("DELETE", color = GhostWhite, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// TAB 5: ACTIVE MISSION BOARDS & HISTORIC HALL OF LEGENDS
// ============================================================================
@Composable
fun MissionsTab(viewModel: AppViewModel) {
    val subTab by viewModel.missionsSubTab.collectAsState()
    val missions by viewModel.activeMissions.collectAsState()
    val legends by viewModel.hallOfLegends.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = subTab,
            containerColor = VelvetCard,
            contentColor = RegalGold,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[subTab]),
                    color = RegalGold
                )
            }
        ) {
            Tab(
                selected = subTab == 0,
                onClick = { viewModel.selectMissionsSubTab(0) },
                text = { Text("Missions", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1) }
            )
            Tab(
                selected = subTab == 1,
                onClick = { viewModel.selectMissionsSubTab(1) },
                text = { Text("Arena Exam", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1) }
            )
            Tab(
                selected = subTab == 2,
                onClick = { viewModel.selectMissionsSubTab(2) },
                text = { Text("Flags Codex", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1) }
            )
        }

        when (subTab) {
            0 -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Imperial Active Missions Panel
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Active Imperial Missions",
                            color = GhostWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Collaborative environmental and intellectual work to earn credit points.",
                            color = MutedSlate,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        missions.forEach { mission ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                                border = BorderStroke(0.5.dp, MutedSlate.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            mission.title,
                                            color = GhostWhite,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = RegalGold.copy(alpha = 0.15f)),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = mission.targetMetric,
                                                color = RegalGold,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = mission.description,
                                        color = MutedSlate,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )

                                    // Numerical progress metrics
                                    val progressPercent = (mission.currentProgress.toFloat() / mission.targetValue.toFloat())
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Progress: ${mission.currentProgress} / ${mission.targetValue}",
                                            color = GhostWhite,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "${(progressPercent * 100).toInt()}% completed",
                                            color = EmeraldSuccess,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    LinearProgressIndicator(
                                        progress = progressPercent,
                                        color = RegalGold,
                                        trackColor = CharcoalObsidian,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )

                                    // Allocation button trigger
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Button(
                                            onClick = { viewModel.contributeToMission(mission.id, 10) },
                                            colors = ButtonDefaults.buttonColors(containerColor = RegalGold),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(36.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                        ) {
                                            Text("ALLOCATE 10 CC", color = CharcoalObsidian, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // HISTORICAL HALL OF LEGENDS BOARD
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            "Imperial Hall of Legends",
                            color = GhostWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Permanent immutable archive of legendary monarchs, grand educators, and founders.",
                            color = MutedSlate,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        // Button to view Hall of Monarchs detailed archive
                        Button(
                            onClick = { viewModel.loadHallOfMonarchs() },
                            colors = ButtonDefaults.buttonColors(containerColor = RegalGold.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, RegalGold.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = RegalGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("VIEW HALL OF MONARCHS ARCHIVE", color = RegalGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        legends.forEach { legend ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = VelvetCard),
                                border = BorderStroke(1.dp, RegalGold.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Stars,
                                        contentDescription = null,
                                        tint = RegalGold,
                                        modifier = Modifier.size(36.dp)
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                legend.name,
                                                color = GhostWhite,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = ElectricBlue.copy(alpha = 0.15f)),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = legend.achievement,
                                                    color = ElectricBlue,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = legend.role,
                                            color = RegalGold,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )

                                        Text(
                                            text = legend.details,
                                            color = MutedSlate,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
            1 -> {
                KnowledgeArenaTab(viewModel)
            }
            2 -> {
                TerritoryFlagsExplorerTab(viewModel)
            }
        }
    }
}

// Data models for layout presentation
data class OnboardingSlide(
    val title: String,
    val desc: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val themeColor: androidx.compose.ui.graphics.Color
)

data class TerritoryItem(
    val name: String,
    val flag: String,
    val region: String,
    val population: String,
    val color: androidx.compose.ui.graphics.Color
)

fun getAutoSuggestedRole(user: UserEntity): String {
    val traits = user.personalityTraits.lowercase()
    val bio = user.bio.lowercase()
    return when {
        user.id == "gandhi_avatar" || user.name.contains("Arjun") -> "Ecological Sovereign"
        user.id == "clara_nobel" || user.name.contains("Clara") -> "Quantum Archon"
        user.id == "kenya_leader" || user.name.contains("Kofi") -> "Solar Marshal"
        traits.contains("scientist") || traits.contains("teacher") || bio.contains("physics") || bio.contains("education") -> "Chief Intellect Adviser"
        traits.contains("humanitarian") || traits.contains("leader") || bio.contains("recycle") || bio.contains("waste") || bio.contains("eco") -> "Sovereign Green Architect"
        traits.contains("builder") || bio.contains("solar") || bio.contains("infra") || bio.contains("power") -> "Imperial Grid Marshal"
        traits.contains("philosopher") || bio.contains("wisdom") || bio.contains("plato") -> "High Philosopher Guide"
        user.knowledgeCredits > user.contributionCredits -> "Grand Knowledge Archivist"
        user.contributionCredits >= user.knowledgeCredits -> "Supreme Social Director"
        else -> "Civic Envoy"
    }
}

@Composable
fun SyncSettingsDialogView(viewModel: AppViewModel) {
    val showSyncDialog by viewModel.showSyncSettingsDialog.collectAsState()
    if (!showSyncDialog) return

    val isBackendConnected by viewModel.isBackendConnected.collectAsState()
    val isConnectingToBackend by viewModel.isConnectingToBackend.collectAsState()
    val backendBaseUrl by viewModel.backendBaseUrl.collectAsState()
    var urlInput by remember { mutableStateOf(backendBaseUrl) }

    Dialog(
        onDismissRequest = { viewModel.setShowSyncSettingsDialog(false) },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = VelvetCard),
            border = BorderStroke(1.5.dp, RegalGold),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .widthIn(max = 450.dp)
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .imePadding()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Cosmos Network Sync",
                    color = GhostWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                if (isBackendConnected) EmeraldSuccess else CrimsonRep,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBackendConnected) "Connected Online" else "Offline / Disconnected",
                        color = if (isBackendConnected) EmeraldSuccess else CrimsonRep,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Server Base URL", color = RegalGold) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GhostWhite,
                        unfocusedTextColor = GhostWhite,
                        focusedBorderColor = RegalGold,
                        unfocusedBorderColor = RegalGold.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text("Connecting to Local VS Code Backend?", color = RegalGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Since this app preview runs on a secure Cloud VM inside Google AI Studio, it cannot directly reach 'localhost' or '10.0.2.2' of your home computer.\n\nTo connect to your VS Code local server, use a free tunneling tool (like ngrok or pinggy) on your PC:\n• Run: 'ngrok http 4000'\n• Copy the public https://... URL it provides\n• Paste that URL in the field above!",
                    color = MutedSlate,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text("Local Emulators (if running APK locally):", color = MutedSlate, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { urlInput = "http://10.0.2.2:4000/" },
                        colors = ButtonDefaults.buttonColors(containerColor = RegalGold.copy(alpha = 0.2f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("10.0.2.2", fontSize = 11.sp, color = RegalGold)
                    }
                    Button(
                        onClick = { urlInput = "http://localhost:4000/" },
                        colors = ButtonDefaults.buttonColors(containerColor = RegalGold.copy(alpha = 0.2f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("localhost", fontSize = 11.sp, color = RegalGold)
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                 Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { 
                            viewModel.updateBackendUrl(urlInput) {
                                viewModel.setShowSyncSettingsDialog(false)
                            }
                        },
                        enabled = !isConnectingToBackend,
                        colors = ButtonDefaults.buttonColors(containerColor = RegalGold),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isConnectingToBackend) {
                            CircularProgressIndicator(
                                color = CharcoalObsidian,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Syncing...", color = CharcoalObsidian, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Connect", color = CharcoalObsidian, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Button(
                        onClick = { viewModel.setShowSyncSettingsDialog(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = CharcoalObsidian),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close", color = GhostWhite)
                    }
                }
            }
        }
    }
}

// ============================================================================
// TAB 3: TERRITORY FLAGS CODEX & ACADEMY TRIVIA QUIZ
// ============================================================================
@Composable
fun TerritoryFlagsExplorerTab(viewModel: AppViewModel) {
    var selectedTabMode by remember { mutableStateOf(0) } // 0: Codex, 1: Quiz

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Mode Selector Pills
        Card(
            colors = CardDefaults.cardColors(containerColor = CharcoalObsidian.copy(alpha = 0.5f)),
            border = BorderStroke(1.dp, RegalGold.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { selectedTabMode = 0 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTabMode == 0) RegalGold else Color.Transparent
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "Flags Codex",
                        color = if (selectedTabMode == 0) CharcoalObsidian else RegalGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = { selectedTabMode = 1 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTabMode == 1) RegalGold else Color.Transparent
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "Guardian Quiz",
                        color = if (selectedTabMode == 1) CharcoalObsidian else RegalGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (selectedTabMode == 0) {
            // Codex Mode
            var searchQuery by remember { mutableStateOf("") }
            var selectedRegionFilter by remember { mutableStateOf("All") }
            var selectedTerritoryForDetail by remember { mutableStateOf<TerritoryDetails?>(null) }
            
            // Region Pills Row
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val regions = listOf("All", "Africa", "Asia", "Europe", "North America", "South America", "Oceania")
                regions.forEach { region ->
                    val isSel = selectedRegionFilter == region
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSel) RegalGold else CharcoalObsidian
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.clickable { selectedRegionFilter = region }
                    ) {
                        Text(
                            text = region,
                            color = if (isSel) CharcoalObsidian else GhostWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Search text field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search 115+ territories & capitals...", color = MutedSlate, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GhostWhite,
                    unfocusedTextColor = GhostWhite,
                    focusedBorderColor = RegalGold,
                    unfocusedBorderColor = MutedSlate.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            val filtered = TerritoryDatabase.list.filter {
                (selectedRegionFilter == "All" || it.region.equals(selectedRegionFilter, ignoreCase = true)) &&
                (it.name.contains(searchQuery, ignoreCase = true) || it.capital.contains(searchQuery, ignoreCase = true))
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered.size) { idx ->
                    val item = filtered[idx]
                    val isSelectedDetail = selectedTerritoryForDetail?.code == item.code
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelectedDetail) RegalGold.copy(alpha = 0.15f) else VelvetCard
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelectedDetail) RegalGold else MutedSlate.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clickable {
                                selectedTerritoryForDetail = if (isSelectedDetail) null else item
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.flag, fontSize = 28.sp)
                            Text(
                                item.name,
                                color = GhostWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Box(modifier = Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No matching territories found.", color = MutedSlate, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Info Detail Sheet below
            AnimatedVisibility(
                visible = selectedTerritoryForDetail != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                selectedTerritoryForDetail?.let { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = VelvetCard),
                        border = BorderStroke(1.5.dp, RegalGold),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 80.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.flag, fontSize = 36.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(item.name, color = GhostWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text("${item.region} Sovereign Domain", color = MutedSlate, fontSize = 11.sp)
                                    }
                                }
                                IconButton(onClick = { selectedTerritoryForDetail = null }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close Detail",
                                        tint = MutedSlate,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Specs Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("CAPITAL", color = RegalGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(item.capital, color = GhostWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Column {
                                    Text("POPULATION", color = RegalGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(item.population, color = GhostWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Column {
                                    Text("INDEX CODE", color = RegalGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text(item.code, color = GhostWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = MutedSlate.copy(alpha = 0.2f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text("FLAG SYMBOLISM", color = RegalGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                item.fact,
                                color = GhostWhite.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = {
                                    viewModel.alignWithTerritory(item.name, item.flag)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RegalGold),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Public, contentDescription = null, tint = CharcoalObsidian, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Declare Geopolitical Alignment", color = CharcoalObsidian, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Trivia Quiz Mode
            var currentTarget by remember { mutableStateOf<TerritoryDetails?>(null) }
            var choices by remember { mutableStateOf<List<TerritoryDetails>>(emptyList()) }
            var optionSelected by remember { mutableStateOf<Int?>(null) }
            var isAnswered by remember { mutableStateOf(false) }
            var isCorrectAnswer by remember { mutableStateOf(false) }
            var quizStreak by remember { mutableStateOf(0) }
            var bestStreak by remember { mutableStateOf(0) }
            var kcEarnedTotal by remember { mutableStateOf(0) }

            // Helper function to generate question
            val generateQuestion = {
                val db = TerritoryDatabase.list
                val target = db.random()
                currentTarget = target
                
                val decoys = db.filter { it.code != target.code }.shuffled().take(3)
                choices = (decoys + target).shuffled()
                optionSelected = null
                isAnswered = false
                isCorrectAnswer = false
            }

            if (currentTarget == null) {
                generateQuestion()
            }

            currentTarget?.let { target ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ElectricBlue.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "Streak: $quizStreak  •  Best: $bestStreak",
                                color = ElectricBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RegalGold.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "KC Awarded: +$kcEarnedTotal",
                                color = RegalGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = VelvetCard),
                        border = BorderStroke(1.5.dp, RegalGold),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 80.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Academy Flag Challenge",
                                color = RegalGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .background(CharcoalObsidian, RoundedCornerShape(16.dp))
                                    .border(1.dp, MutedSlate.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(target.flag, fontSize = 64.sp)
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                "Which sovereign territory on Earth flies this custom flag?",
                                color = GhostWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                choices.forEachIndexed { index, option ->
                                    val isCurrentOptSelected = optionSelected == index
                                    val borderStrokeColor = when {
                                        isAnswered && option.code == target.code -> EmeraldSuccess
                                        isCurrentOptSelected && isAnswered && !isCorrectAnswer -> CrimsonRep
                                        isCurrentOptSelected -> RegalGold
                                        else -> MutedSlate.copy(alpha = 0.25f)
                                    }
                                    
                                    val bkgColor = when {
                                        isAnswered && option.code == target.code -> EmeraldSuccess.copy(alpha = 0.12f)
                                        isCurrentOptSelected && isAnswered && !isCorrectAnswer -> CrimsonRep.copy(alpha = 0.12f)
                                        isCurrentOptSelected -> RegalGold.copy(alpha = 0.08f)
                                        else -> CharcoalObsidian.copy(alpha = 0.5f)
                                    }

                                    val labelColor = when {
                                        isAnswered && option.code == target.code -> EmeraldSuccess
                                        isCurrentOptSelected && isAnswered && !isCorrectAnswer -> CrimsonRep
                                        isCurrentOptSelected -> RegalGold
                                        else -> GhostWhite
                                    }

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = bkgColor),
                                        border = BorderStroke(1.2.dp, borderStrokeColor),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !isAnswered) {
                                                optionSelected = index
                                                isAnswered = true
                                                if (option.code == target.code) {
                                                    isCorrectAnswer = true
                                                    quizStreak++
                                                    if (quizStreak > bestStreak) {
                                                        bestStreak = quizStreak
                                                    }
                                                    kcEarnedTotal += 10
                                                    viewModel.awardKnowledgeCredits(10)
                                                } else {
                                                    isCorrectAnswer = false
                                                    quizStreak = 0
                                                    viewModel.triggerToast("Incorrect flag selected.")
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .background(borderStrokeColor.copy(alpha = 0.2f), CircleShape)
                                                    .border(1.dp, borderStrokeColor, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = ('A' + index).toString(),
                                                    color = labelColor,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = option.name,
                                                color = labelColor,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(visible = isAnswered) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isCorrectAnswer) EmeraldSuccess.copy(alpha = 0.08f) else CrimsonRep.copy(alpha = 0.08f)
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isCorrectAnswer) EmeraldSuccess.copy(alpha = 0.3f) else CrimsonRep.copy(alpha = 0.3f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = if (isCorrectAnswer) "CORRECT EXAM EXCELLENCE" else "CORRECT ANSWER",
                                                color = if (isCorrectAnswer) EmeraldSuccess else CrimsonRep,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${target.name} (Capital: ${target.capital})",
                                                color = GhostWhite,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                            Text(
                                                text = target.fact,
                                                color = MutedSlate,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Button(
                                        onClick = { generateQuestion() },
                                        colors = ButtonDefaults.buttonColors(containerColor = RegalGold),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Next Flag Challenge", color = CharcoalObsidian, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = CharcoalObsidian, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun auraColor(auraLevel: String): Color = when (auraLevel) {
    "Bronze"    -> Color(0xFFCD7F32)
    "Silver"    -> Color(0xFFC0C0C0)
    "Golden"    -> Color(0xFFFFD700)
    "Imperial"  -> Color(0xFF9B59B6)
    "Legendary" -> Color(0xFFFF4500)
    else        -> Color.Transparent
}

fun auraEmoji(auraLevel: String): String = when (auraLevel) {
    "Bronze"    -> "🥉"
    "Silver"    -> "🥈"
    "Golden"    -> "🥇"
    "Imperial"  -> "👑"
    "Legendary" -> "⭐"
    else        -> ""
}

@Composable
fun AuraBadge(auraLevel: String, modifier: Modifier = Modifier) {
    if (auraLevel == "None") return
    val color = auraColor(auraLevel)
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(auraEmoji(auraLevel), fontSize = 11.sp)
            Text(
                text = "$auraLevel Aura",
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ThroneWorthinessPanel(worthiness: ThroneWorthinessDTO) {
    val percentage = worthiness.percentage.toInt()
    val isLight = ThemeState.isLight
    val cardBg = if (isLight) Color(0xFFFDFBF7) else Color(0xFF16130B)
    val cardBorder = if (isLight) Color(0xFFC5A023) else RegalGold
    val textHeading = if (isLight) Color(0xFF906D00) else RegalGold
    val textBody = if (isLight) Color(0xFF2C2414) else GhostWhite
    val textSub = if (isLight) Color(0xFF6B5E49) else MutedSlate

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.5.dp, cardBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "⚖️ Throne Worthiness",
                color = textHeading,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Big percentage
            Text(
                "$percentage% Merit Score",
                color = textBody,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Overall bar
            LinearProgressIndicator(
                progress = { percentage / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = textHeading,
                trackColor = textHeading.copy(alpha = 0.15f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Component breakdown
            WorthinessRow("📚 Knowledge Credits", worthiness.knowledgeComponent, "50%", ElectricBlue)
            WorthinessRow("🏗️ Contribution Credits", worthiness.contributionComponent, "20%", LustrousAmber)
            WorthinessRow("⭐ Reputation Score", worthiness.reputationComponent, "15%", EmeraldSuccess)
            WorthinessRow("🤝 Public Support", worthiness.publicSupportComponent, "10%", Color(0xFF64B5F6))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Leadership earned through merit and public trust.",
                color = textSub,
                fontSize = 10.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

@Composable
fun WorthinessRow(label: String, value: Float, weight: String, color: Color) {
    val isLight = ThemeState.isLight
    val textMain = if (isLight) Color(0xFF2C2414) else GhostWhite
    val textSub = if (isLight) Color(0xFF6B5E49) else MutedSlate
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, color = textMain, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(weight, color = textSub, fontSize = 10.sp)
        LinearProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier.width(60.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
        Text("${value.toInt()}%", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RoyalTimelinePanel(
    timeline: List<TimelineMilestone>,
    selectedRank: String? = null,
    onRankClick: (String) -> Unit = {}
) {
    val isLight = ThemeState.isLight
    val rankOrder = listOf("Citizen", "Scholar", "Sage", "Noble", "Duke", "Duchess", "Prince", "Princess", "Royal Candidate", "King", "Queen")
    val rankEmoji = mapOf(
        "Citizen" to "👤", "Scholar" to "📖", "Sage" to "🔮", "Noble" to "🎖️",
        "Duke" to "🏰", "Duchess" to "🏰", "Prince" to "⚔️", "Princess" to "💎",
        "Royal Candidate" to "🗳️", "King" to "👑", "Queen" to "💎"
    )
    val cardBg = if (isLight) Color(0xFFF7F3E8) else Color(0xFF11100C)
    val cardBorder = if (isLight) Color(0xFFC5A023) else RegalGold
    val textHeading = if (isLight) Color(0xFF906D00) else RegalGold
    val textMain = if (isLight) Color(0xFF2C2414) else GhostWhite
    val textSub = if (isLight) Color(0xFF6B5E49) else MutedSlate

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, cardBorder.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("🗺️ Journey to the Crown", color = textHeading, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Click any stage to inspect authority privileges.", color = textSub, fontSize = 9.sp)
            Spacer(modifier = Modifier.height(12.dp))
            val sortedMilestones = timeline.sortedBy { rankOrder.indexOf(it.rank) }
            sortedMilestones.forEachIndexed { index, milestone ->
                val isLast = index == sortedMilestones.lastIndex
                val isHighest = milestone.rank == sortedMilestones.last().rank
                val isSelected = milestone.rank == selectedRank
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) textHeading.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onRankClick(milestone.rank) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Timeline line + dot
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(32.dp)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    if (isHighest) textHeading else textSub.copy(alpha = 0.3f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(rankEmoji[milestone.rank] ?: "•", fontSize = 12.sp)
                        }
                        if (!isLast) {
                            Box(modifier = Modifier.width(2.dp).height(24.dp).background(textSub.copy(alpha = 0.3f)))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            milestone.rank,
                            color = if (isHighest) textHeading else textMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            milestone.milestone,
                            color = textSub,
                            fontSize = 10.sp
                        )
                        if (milestone.date > 0) {
                            Text(
                                java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
                                    .format(java.util.Date(milestone.date)),
                                color = textSub.copy(alpha = 0.7f),
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoyalImpactStatsGrid(profile: MonarchProfileDTO) {
    val isLight = ThemeState.isLight
    val headingBg = if (isLight) Color(0xFF906D00) else RegalGold
    val cardBg = if (isLight) Color(0xFFF7F3E8) else CharcoalObsidian
    val borderCol = if (isLight) Color(0xFFC5A023).copy(alpha = 0.5f) else RegalGold.copy(alpha = 0.3f)
    val valueCol = if (isLight) Color(0xFF906D00) else RegalGold
    val labelCol = if (isLight) Color(0xFF6B5E49) else MutedSlate

    val stats = listOf(
        Triple("Legacy Points", "${profile.legacyPoints}", "🏆"),
        Triple("Citizens Helped", "${profile.citizensHelped}", "🤝"),
        Triple("Policies Initiated", "${profile.policiesInitiated}", "📜"),
        Triple("Territories Influenced", "${profile.territoriesInfluenced}", "🌍"),
        Triple("Decrees Issued", "${profile.decreesPosted}", "⚖️"),
        Triple("Approval Rating", "${profile.approvalRating}%", "⭐")
    )
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("📊 Royal Impact", color = headingBg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        stats.chunked(2).forEach { rowStats ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowStats.forEach { (label, value, emoji) ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, borderCol),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(emoji, fontSize = 18.sp)
                            Text(value, color = valueCol, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(label, color = labelCol, fontSize = 9.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
                if (rowStats.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun RoyalDecreesSection(
    decrees: List<RoyalDecreeDTO>,
    reveredDecrees: Set<String> = emptySet(),
    onRevere: (String) -> Unit = {}
) {
    val isLight = ThemeState.isLight
    val headingBg = if (isLight) Color(0xFF906D00) else RegalGold
    val cardBg = if (isLight) Color(0xFFF7F3E8) else Color(0xFF16130B)
    val cardBorder = if (isLight) Color(0xFFC5A023) else RegalGold
    val textMain = if (isLight) Color(0xFF2C2414) else GhostWhite
    val textSub = if (isLight) Color(0xFF6B5E49) else MutedSlate

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("📜 Royal Decrees", color = headingBg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        if (decrees.isEmpty()) {
            Text("No decrees issued yet.", color = textSub, fontSize = 11.sp)
        } else {
            decrees.take(5).forEach { decree ->
                val isRevered = decree.id in reveredDecrees
                Card(
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    border = BorderStroke(1.dp, cardBorder.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(decree.title, color = headingBg, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(EmeraldSuccess.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(decree.status.uppercase(), color = EmeraldSuccess, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(if (isRevered) Color(0xFFFFF2F4) else textSub.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                        .border(0.5.dp, if (isRevered) Color(0xFFF43F5E) else textSub.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                        .clickable { onRevere(decree.id) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(if (isRevered) "❤️ REVERED" else "🤍 REVERE", color = if (isRevered) Color(0xFFE11D48) else textSub, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(decree.content, color = textMain.copy(alpha = 0.9f), fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "⚖️ Royal Seal — ${java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(decree.publishedAt))}",
                            color = headingBg.copy(alpha = 0.7f),
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoyalCouncilSection(council: List<CouncilMemberDTO>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isLight = ThemeState.isLight
    val headingBg = if (isLight) Color(0xFF906D00) else RegalGold
    val cardBg = if (isLight) Color(0xFFF7F3E8) else CharcoalObsidian
    val textMain = if (isLight) Color(0xFF2C2414) else GhostWhite
    val textSub = if (isLight) Color(0xFF6B5E49) else MutedSlate

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("🏛️ Royal Council", color = headingBg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        if (council.isEmpty()) {
            Text("No council members appointed yet.", color = textSub, fontSize = 11.sp)
        } else {
            council.forEach { member ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(cardBg.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .border(0.5.dp, headingBg.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .clickable {
                            android.widget.Toast.makeText(context, "🛡️ Councilor ${member.name} is presiding over the Royal Chambers.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(auraColor(member.auraLevel).copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, auraColor(member.auraLevel), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(member.name.take(1).uppercase(), color = headingBg, fontWeight = FontWeight.Bold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(member.name, color = textMain, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(member.role, color = textSub, fontSize = 10.sp)
                    }
                    AuraBadge(member.auraLevel)
                }
            }
        }
    }
}

@Composable
fun RoyalPermissionsPanel() {
    val isLight = ThemeState.isLight
    val cardBg = if (isLight) Color(0xFFFAF7F2) else Color(0xFF11100D)
    val textHeading = if (isLight) Color(0xFF906D00) else RegalGold
    val textMain = if (isLight) Color(0xFF2C2414) else GhostWhite
    val textSub = if (isLight) Color(0xFF6B5E49) else MutedSlate

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(0.5.dp, if (isLight) Color(0xFFDCCFBD) else MutedSlate.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("🔒 Royal Authority Scope", color = textHeading, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            listOf("Initiatives", "Speeches", "Awards", "Council Appointments").forEach {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("✅", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(it, color = textMain, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            listOf("Ban Users", "Delete Posts", "Edit Scores", "Change Elections", "Modify Credits").forEach {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("❌", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(it, color = textSub, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun KingProfileDialog(
    profile: MonarchProfileDTO,
    worthiness: ThroneWorthinessDTO?,
    timeline: List<TimelineMilestone>,
    decrees: List<RoyalDecreeDTO>,
    council: List<CouncilMemberDTO>,
    userName: String,
    userTerritory: String,
    userFlag: String,
    viewModel: AppViewModel,
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Decrees", "Council", "Timeline", "Legacy")

    var reveredDecrees by remember { mutableStateOf(setOf<String>()) }
    var selectedMilestoneRank by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val isLight = ThemeState.isLight
    val dialogBg = if (isLight) Color(0xFFFDFBF7) else Color(0xFF0D0B01)
    val goldAccentColor = if (isLight) Color(0xFFB48A10) else Color(0xFFD4AF37)
    val textMain = if (isLight) Color(0xFF2C2414) else GhostWhite
    val textSub = if (isLight) Color(0xFF6B5E49) else MutedSlate
    val badgeBg = if (isLight) Color(0xFFFFF7E3) else Color(0xFF2E1C00)
    val primaryTextGold = if (isLight) Color(0xFF906D00) else Color(0xFFD4AF37)
    val borderStrokeColor = if (isLight) Color(0xFFC5A023) else Color(0xFFD4AF37)

    val crownGlowColor = if (isLight) Color(0xFFDFBA45).copy(alpha = 0.3f) else Color(0xFFFFEA7A).copy(alpha = 0.25f)

    Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = dialogBg),
            border = BorderStroke(2.5.dp, borderStrokeColor),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── IMPERIAL HEADER ──────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = if (isLight) listOf(Color(0xFFFFF9EB), Color(0xFFFDFBF7)) else listOf(Color(0xFF231703), Color(0xFF0D0B01))
                            )
                        )
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .clickable {
                                    android.widget.Toast.makeText(context, "☀️ His Majesty the King welcomes your presence inside the Chamber.", android.widget.Toast.LENGTH_LONG).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(105.dp)
                                    .background(crownGlowColor, CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(CircleShape)
                                    .background(if (isLight) Color(0xFFFAECE1) else Color(0xFF1E1402))
                                    .border(3.dp, goldAccentColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    userName.take(1).uppercase(),
                                    color = goldAccentColor,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y = (-11).dp)
                            ) {
                                Text("👑", fontSize = 28.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "HIS MAJESTY THE KING",
                            color = primaryTextGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            userName,
                            color = textMain,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(userFlag, fontSize = 14.sp)
                            Text(userTerritory, color = textSub, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        val reignDays = ((System.currentTimeMillis() - profile.reignStartDate) / 86400000).toInt()
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$reignDays", color = goldAccentColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Days Reigning", color = textSub, fontSize = 9.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${profile.approvalRating}%", color = EmeraldSuccess, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Approval", color = textSub, fontSize = 9.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${profile.legacyPoints}", color = LustrousAmber, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Legacy Pts", color = textSub, fontSize = 9.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .background(badgeBg, RoundedCornerShape(8.dp))
                                .border(1.2.dp, goldAccentColor, RoundedCornerShape(8.dp))
                                .clickable {
                                    android.widget.Toast.makeText(context, "☀️ Golden Sun Aura represents ultimate sovereign light, casting prosperity across all realms.", android.widget.Toast.LENGTH_LONG).show()
                                }
                                .padding(horizontal = 14.dp, vertical = 5.dp)
                        ) {
                            Text("☀️ Golden Sun Aura — Imperial Crown", color = primaryTextGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── GOVERNANCE TABS ───────────────────────────────────────
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = dialogBg,
                    contentColor = goldAccentColor,
                    edgePadding = 8.dp
                ) {
                    tabs.forEachIndexed { index, label ->
                        val tabEmoji = when (index) {
                            0 -> "📊"
                            1 -> "📜"
                            2 -> "🏛️"
                            3 -> "🗺️"
                            4 -> "🏆"
                            else -> "✨"
                        }
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(tabEmoji, fontSize = 11.sp)
                                    Text(
                                        label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedTab == index) goldAccentColor else textSub
                                    )
                                }
                            }
                        )
                    }
                }

                // ── TAB CONTENT ───────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> { // Overview
                            RoyalImpactStatsGrid(profile)
                            Divider(color = goldAccentColor.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
                            if (worthiness != null) {
                                ThroneWorthinessPanel(worthiness)
                                Divider(color = goldAccentColor.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
                            }
                            RoyalPermissionsPanel()
                        }
                        1 -> RoyalDecreesSection(
                            decrees = decrees,
                            reveredDecrees = reveredDecrees,
                            onRevere = { reqId ->
                                reveredDecrees = if (reqId in reveredDecrees) reveredDecrees - reqId else reveredDecrees + reqId
                                if (reqId in reveredDecrees) {
                                    android.widget.Toast.makeText(context, "❤️ You have sworn fealty to this royal decree!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        2 -> RoyalCouncilSection(council)
                        3 -> { // Timeline
                            val milestoneDetails = mapOf(
                                "Citizen" to "The foundation of OneEarth. Earn credits to rise in rank.",
                                "Scholar" to "Academics and deep thinkers. Authorized to submit long-form articles.",
                                "Sage" to "Masters of wisdom. Trusted review authorities of the library.",
                                "Noble" to "Distinguished service leaders with local governance authority.",
                                "Duke" to "High-level administrative rulers overseeing entire continental sectors.",
                                "Duchess" to "High-level administrative rulers overseeing entire continental sectors.",
                                "Prince" to "Imperial heirs of the sovereign. Direct authority below the throne.",
                                "Princess" to "Imperial heirs of the sovereign. Direct authority below the throne.",
                                "Royal Candidate" to "Senior leaders elected to compete in the democratic sovereign elections.",
                                "King" to "His Majesty. Supreme Ruler of the United realities.",
                                "Queen" to "Her Majesty. Supreme Ruler of the United realities."
                            )

                            if (selectedMilestoneRank != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = if (isLight) Color(0xFFFAF2E4) else Color(0xFF261B01)),
                                    border = BorderStroke(1.dp, borderStrokeColor),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                "🛡️ ${selectedMilestoneRank} Rank Scope",
                                                color = primaryTextGold,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            IconButton(
                                                onClick = { selectedMilestoneRank = null },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Text("×", color = textSub, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            milestoneDetails[selectedMilestoneRank] ?: "Standard rank with active service records.",
                                            color = textMain,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            if (timeline.isNotEmpty()) {
                                RoyalTimelinePanel(
                                    timeline = timeline,
                                    selectedRank = selectedMilestoneRank,
                                    onRankClick = { clickedRank ->
                                        selectedMilestoneRank = clickedRank
                                    }
                                )
                            } else {
                                Text("Timeline loading...", color = textSub, fontSize = 11.sp)
                            }
                        }
                        4 -> { // Legacy
                            Text("🏛️ Hall of Monarchs", color = goldAccentColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "The current monarch's achievements will be permanently enshrined in the Hall of Monarchs upon completion.",
                                color = textSub,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // ── CLOSE BUTTON ──────────────────────────────────────────
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = goldAccentColor),
                    shape = RoundedCornerShape(0.dp, 0.dp, 20.dp, 20.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("CLOSE IMPERIAL CHAMBER", color = if (isLight) Color.White else Color(0xFF0D0B01), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun QueenProfileDialog(
    profile: MonarchProfileDTO,
    worthiness: ThroneWorthinessDTO?,
    timeline: List<TimelineMilestone>,
    decrees: List<RoyalDecreeDTO>,
    council: List<CouncilMemberDTO>,
    userName: String,
    userTerritory: String,
    userFlag: String,
    viewModel: AppViewModel,
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Decrees", "Council", "Timeline", "Legacy")

    var reveredDecrees by remember { mutableStateOf(setOf<String>()) }
    var selectedMilestoneRank by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val isLight = ThemeState.isLight
    val dialogBg = if (isLight) Color(0xFFF6FAFD) else Color(0xFF000D1A)
    val royalBlueColor = if (isLight) Color(0xFF0288D1) else Color(0xFF4FC3F7)
    val textMain = if (isLight) Color(0xFF102533) else GhostWhite
    val textSub = if (isLight) Color(0xFF50687E) else MutedSlate
    val badgeBg = if (isLight) Color(0xFFE8F6FF) else Color(0xFF001931)
    val primaryTextBlue = if (isLight) Color(0xFF01579B) else Color(0xFF4FC3F7)
    val borderStrokeColor = if (isLight) Color(0xFF4FC3F7) else Color(0xFF4FC3F7)

    val crownGlowColor = if (isLight) Color(0xFFB3E5FC).copy(alpha = 0.45f) else Color(0xFF4FC3F7).copy(alpha = 0.25f)

    Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = dialogBg),
            border = BorderStroke(2.5.dp, borderStrokeColor),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── IMPERIAL HEADER ──────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = if (isLight) listOf(Color(0xFFE0F2F1), Color(0xFFF6FAFD)) else listOf(Color(0xFF001F3D), Color(0xFF000D1A))
                            )
                        )
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .clickable {
                                    android.widget.Toast.makeText(context, "💎 Her Majesty the Queen welcomes your presence inside the Chamber.", android.widget.Toast.LENGTH_LONG).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(105.dp)
                                    .background(crownGlowColor, CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(CircleShape)
                                    .background(if (isLight) Color(0xFFE1F5FE) else Color(0xFF001428))
                                    .border(3.dp, royalBlueColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    userName.take(1).uppercase(),
                                    color = royalBlueColor,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y = (-11).dp)
                            ) {
                                Text("👑", fontSize = 28.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "HER MAJESTY THE QUEEN",
                            color = primaryTextBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            userName,
                            color = textMain,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(userFlag, fontSize = 14.sp)
                            Text(userTerritory, color = textSub, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        val reignDays = ((System.currentTimeMillis() - profile.reignStartDate) / 86400000).toInt()
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$reignDays", color = royalBlueColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Days Reigning", color = textSub, fontSize = 9.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${profile.approvalRating}%", color = EmeraldSuccess, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Approval", color = textSub, fontSize = 9.sp)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${profile.legacyPoints}", color = LustrousAmber, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Legacy Pts", color = textSub, fontSize = 9.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .background(badgeBg, RoundedCornerShape(8.dp))
                                .border(1.2.dp, royalBlueColor, RoundedCornerShape(8.dp))
                                .clickable {
                                    android.widget.Toast.makeText(context, "🌙 Diamond Moon Aura represents ultimate serene wisdom, guarding prosperity across all elements.", android.widget.Toast.LENGTH_LONG).show()
                                }
                                .padding(horizontal = 14.dp, vertical = 5.dp)
                        ) {
                            Text("🌙 Diamond Moon Aura — Royal Crown", color = royalBlueColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = dialogBg,
                    contentColor = royalBlueColor,
                    edgePadding = 8.dp
                ) {
                    tabs.forEachIndexed { index, label ->
                        val tabEmoji = when (index) {
                            0 -> "📊"
                            1 -> "📜"
                            2 -> "🏛️"
                            3 -> "🗺️"
                            4 -> "🏆"
                            else -> "✨"
                        }
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(tabEmoji, fontSize = 11.sp)
                                    Text(
                                        label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedTab == index) royalBlueColor else textSub
                                    )
                                }
                            }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> { // Overview
                            RoyalImpactStatsGrid(profile)
                            Divider(color = royalBlueColor.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
                            if (worthiness != null) {
                                ThroneWorthinessPanel(worthiness)
                                Divider(color = royalBlueColor.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 8.dp))
                            }
                            RoyalPermissionsPanel()
                        }
                        1 -> RoyalDecreesSection(
                            decrees = decrees,
                            reveredDecrees = reveredDecrees,
                            onRevere = { reqId ->
                                reveredDecrees = if (reqId in reveredDecrees) reveredDecrees - reqId else reveredDecrees + reqId
                                if (reqId in reveredDecrees) {
                                    android.widget.Toast.makeText(context, "❤️ You have sworn fealty to this royal decree!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        2 -> RoyalCouncilSection(council)
                        3 -> { // Timeline
                            val milestoneDetails = mapOf(
                                "Citizen" to "The foundation of OneEarth. Earn credits to rise in rank.",
                                "Scholar" to "Academics and deep thinkers. Authorized to submit long-form articles.",
                                "Sage" to "Masters of wisdom. Trusted review authorities of the library.",
                                "Noble" to "Distinguished service leaders with local governance authority.",
                                "Duke" to "High-level administrative rulers overseeing entire continental sectors.",
                                "Duchess" to "High-level administrative rulers overseeing entire continental sectors.",
                                "Prince" to "Imperial heirs of the sovereign. Direct authority below the throne.",
                                "Princess" to "Imperial heirs of the sovereign. Direct authority below the throne.",
                                "Royal Candidate" to "Senior leaders elected to compete in the democratic sovereign elections.",
                                "King" to "His Majesty. Supreme Ruler of the United realities.",
                                "Queen" to "Her Majesty. Supreme Ruler of the United realities."
                            )

                            if (selectedMilestoneRank != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = if (isLight) Color(0xFFE1F5FE) else Color(0xFF001A33)),
                                    border = BorderStroke(1.dp, borderStrokeColor),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.padding(bottom = 12.dp).fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                "🛡️ ${selectedMilestoneRank} Rank Scope",
                                                color = royalBlueColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            IconButton(
                                                onClick = { selectedMilestoneRank = null },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Text("×", color = textSub, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            milestoneDetails[selectedMilestoneRank] ?: "Standard rank with active service records.",
                                            color = textMain,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            if (timeline.isNotEmpty()) {
                                RoyalTimelinePanel(
                                    timeline = timeline,
                                    selectedRank = selectedMilestoneRank,
                                    onRankClick = { clickedRank ->
                                        selectedMilestoneRank = clickedRank
                                    }
                                )
                            } else {
                                Text("Timeline loading...", color = textSub, fontSize = 11.sp)
                            }
                        }
                        4 -> { // Legacy
                            Text("🏛️ Hall of Monarchs", color = royalBlueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "The current monarch's achievements will be permanently enshrined in the Hall of Monarchs upon completion.",
                                color = textSub,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = royalBlueColor),
                    shape = RoundedCornerShape(0.dp, 0.dp, 20.dp, 20.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("CLOSE ROYAL GARDEN", color = if (isLight) Color.White else Color(0xFF000D1A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun HallOfMonarchsDialog(
    monarchs: List<HallOfMonarchEntry>,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A12)),
            border = BorderStroke(2.dp, RegalGold),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(0.95f).fillMaxHeight(0.88f).padding(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1000)).padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🏛️", fontSize = 32.sp)
                        Text("Hall of Monarchs", color = RegalGold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Permanent Archive of Sovereign Rulers", color = MutedSlate, fontSize = 11.sp)
                    }
                }
                // Monarch list
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)
                ) {
                    if (monarchs.isEmpty()) {
                        Text("The Hall of Monarchs awaits its first ruler.", color = MutedSlate, fontSize = 12.sp)
                    } else {
                        monarchs.forEachIndexed { index, monarch ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1000)),
                                border = BorderStroke(1.dp, RegalGold.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(RegalGold.copy(alpha = 0.15f), CircleShape)
                                                .border(1.dp, RegalGold, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(monarch.name.take(1).uppercase(), color = RegalGold, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${if (monarch.title == "King") "👑" else "💎"} ${monarch.title} ${monarch.name}",
                                                color = RegalGold,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text("${monarch.territory}", color = MutedSlate, fontSize = 10.sp)
                                        }
                                        // Legacy badge
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("${monarch.legacyPoints}", color = LustrousAmber, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            Text("Legacy Pts", color = MutedSlate, fontSize = 8.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        val startFmt = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(monarch.reignStartDate))
                                        val endFmt = monarch.reignEndDate?.let {
                                            java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it))
                                        } ?: "Present"
                                        Text("Reign: $startFmt – $endFmt", color = GhostWhite.copy(alpha = 0.7f), fontSize = 10.sp)
                                        Text("${monarch.approvalRating}% Approval", color = EmeraldSuccess, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = RegalGold),
                    shape = RoundedCornerShape(0.dp, 0.dp, 20.dp, 20.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("CLOSE HALL OF MONARCHS", color = Color(0xFF0D0B00), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

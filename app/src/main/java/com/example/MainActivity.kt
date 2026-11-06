package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.UserProfile
import com.example.data.repository.AdminRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.ui.screens.admin.AdminDashboardScreen
import com.example.ui.screens.admin.AdminViewModel
import com.example.ui.screens.ai.AiHelpAssistantBottomSheet
import com.example.ui.screens.ai.AiHelpViewModel
import com.example.ui.screens.auth.AuthUiState
import com.example.ui.screens.auth.AuthViewModel
import com.example.ui.screens.auth.JoinWithInviteScreen
import com.example.ui.screens.auth.OwnerBootstrapScreen
import com.example.ui.screens.auth.RecoveryScreen
import com.example.ui.screens.auth.WelcomeScreen
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.chat.ChatViewModel
import com.example.ui.screens.common.OfflineBanner
import com.example.ui.screens.profile.ProfileSettingsScreen
import com.example.ui.theme.KinfolkTheme
import com.example.util.GoogleAuthHelper
import com.example.util.NetworkMonitor
import com.example.util.configureAppCheck

enum class AppScreen {
    CHAT,
    PROFILE_SETTINGS,
    ADMIN_DASHBOARD
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Mandatory App Check configuration with launch intent
        configureAppCheck(this, intent)

        val authRepository = AuthRepository(this)
        val chatRepository = ChatRepository(this)
        val adminRepository = AdminRepository(this)
        val networkMonitor = NetworkMonitor(this)

        setContent {
            KinfolkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KinfolkApp(
                        authRepository = authRepository,
                        chatRepository = chatRepository,
                        adminRepository = adminRepository,
                        networkMonitor = networkMonitor
                    )
                }
            }
        }
    }
}

@Composable
fun KinfolkApp(
    authRepository: AuthRepository,
    chatRepository: ChatRepository,
    adminRepository: AdminRepository,
    networkMonitor: NetworkMonitor
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val authViewModel: AuthViewModel = viewModel { AuthViewModel(authRepository) }
    val authUiState by authViewModel.uiState.collectAsState()
    val isConnected by networkMonitor.isConnected.collectAsState(initial = true)

    val createdInviteCode by authViewModel.createdInviteCode.collectAsState()
    val recoveryCode by authViewModel.recoveryCode.collectAsState()
    val actionLoading by authViewModel.actionLoading.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()

    // Silent background auto sign-in check on app launch
    LaunchedEffect(Unit) {
        GoogleAuthHelper.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            scope = coroutineScope,
            onAuthSuccess = { authViewModel.onGoogleAuthSuccess() },
            onUnauthenticated = {}
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OfflineBanner(isOffline = !isConnected)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val state = authUiState) {
                is AuthUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                is AuthUiState.Welcome -> {
                    WelcomeScreen(
                        hasOwner = state.hasOwner,
                        isGoogleAuthenticated = state.isGoogleAuthenticated,
                        errorMessage = errorMessage,
                        isLoading = actionLoading,
                        onGoogleSignIn = {
                            GoogleAuthHelper.onGoogleSignInClicked(
                                context = context,
                                credentialManager = credentialManager,
                                scope = coroutineScope,
                                onAuthSuccess = { authViewModel.onGoogleAuthSuccess() },
                                onAuthError = { Log.e("GoogleSignIn", it) }
                            )
                        },
                        onLogin = { user, pass -> authViewModel.login(user, pass) },
                        onNavigateToJoin = { authViewModel.navigateToJoinWithInvite() },
                        onNavigateToOwnerBootstrap = { authViewModel.navigateToOwnerBootstrap() },
                        onNavigateToRecovery = { authViewModel.navigateToRecovery() }
                    )
                }

                is AuthUiState.OwnerBootstrap -> {
                    OwnerBootstrapScreen(
                        errorMessage = errorMessage,
                        isLoading = actionLoading,
                        createdInviteCode = createdInviteCode,
                        onBootstrap = { u, d, p -> authViewModel.bootstrapOwner(u, d, p) },
                        onBack = { authViewModel.navigateToWelcome() }
                    )
                }

                is AuthUiState.JoinWithInvite -> {
                    JoinWithInviteScreen(
                        errorMessage = errorMessage,
                        isLoading = actionLoading,
                        recoveryCode = recoveryCode,
                        onJoin = { code, u, d, p -> authViewModel.joinWithInvite(code, u, d, p) },
                        onBack = { authViewModel.navigateToWelcome() }
                    )
                }

                is AuthUiState.Recovery -> {
                    RecoveryScreen(
                        errorMessage = errorMessage,
                        isLoading = actionLoading,
                        onRecover = { u, code, newP -> authViewModel.recoverAccount(u, code, newP) },
                        onBack = { authViewModel.navigateToWelcome() }
                    )
                }

                is AuthUiState.LoggedIn -> {
                    MainLoggedInFlow(
                        currentUser = state.profile,
                        authRepository = authRepository,
                        chatRepository = chatRepository,
                        adminRepository = adminRepository,
                        onLogout = {
                            GoogleAuthHelper.signOut(credentialManager, coroutineScope) {
                                authViewModel.logout()
                            }
                        }
                    )
                }

                is AuthUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "An error occurred: ${state.message}")
                    }
                }
            }
        }
    }
}

@Composable
fun MainLoggedInFlow(
    currentUser: UserProfile,
    authRepository: AuthRepository,
    chatRepository: ChatRepository,
    adminRepository: AdminRepository,
    onLogout: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(AppScreen.CHAT) }
    var showAiHelpBottomSheet by remember { mutableStateOf(false) }

    val aiHelpViewModel: AiHelpViewModel = viewModel { AiHelpViewModel() }
    val adminViewModel: AdminViewModel = viewModel(key = currentUser.userId) {
        AdminViewModel(adminRepository, currentUser.userId)
    }

    // Determine other family member ID.
    // For a 2-person family (Owner & Sister), the other member is dynamically resolved
    // from all registered members. If none yet, placeholder target allows conversation creation upon joining.
    val members by adminViewModel.members.collectAsState()
    val otherMember = members.firstOrNull { it.userId != currentUser.userId }
    val otherUserId = otherMember?.userId ?: "family_sister_placeholder"

    val chatViewModel: ChatViewModel = viewModel(key = "${currentUser.userId}_$otherUserId") {
        ChatViewModel(
            chatRepository = chatRepository,
            currentUserId = currentUser.userId,
            currentUserName = currentUser.displayName,
            otherUserId = otherUserId
        )
    }

    when (currentScreen) {
        AppScreen.CHAT -> {
            ChatScreen(
                viewModel = chatViewModel,
                onNavigateToSettings = { currentScreen = AppScreen.PROFILE_SETTINGS },
                onOpenAiHelp = { showAiHelpBottomSheet = true }
            )
        }

        AppScreen.PROFILE_SETTINGS -> {
            ProfileSettingsScreen(
                userProfile = currentUser,
                authRepository = authRepository,
                onNavigateToAdmin = { currentScreen = AppScreen.ADMIN_DASHBOARD },
                onOpenAiHelp = { showAiHelpBottomSheet = true },
                onLogout = onLogout,
                onBack = { currentScreen = AppScreen.CHAT }
            )
        }

        AppScreen.ADMIN_DASHBOARD -> {
            AdminDashboardScreen(
                viewModel = adminViewModel,
                onBack = { currentScreen = AppScreen.PROFILE_SETTINGS }
            )
        }
    }

    if (showAiHelpBottomSheet) {
        AiHelpAssistantBottomSheet(
            viewModel = aiHelpViewModel,
            onDismiss = { showAiHelpBottomSheet = false }
        )
    }
}

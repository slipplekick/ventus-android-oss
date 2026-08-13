package com.ventus.sys

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.ventus.sys.data.local.AppPreferences
import com.ventus.sys.ui.home.HomeScreen
import com.ventus.sys.ui.onboarding.AuthUiState
import com.ventus.sys.ui.onboarding.AuthViewModel
import com.ventus.sys.ui.onboarding.LoginScreen
import com.ventus.sys.ui.onboarding.OnboardingScreen
import com.ventus.sys.ui.onboarding.OnboardingViewModel
import com.ventus.sys.ui.theme.VentusTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

// Single-activity host for the whole Compose navigation graph. Flow:
// LoginScreen -> OnboardingScreen -> HomeScreen (hosts the real NavHost).
// launchMode="singleTask" (AndroidManifest.xml) is what makes AppAuth's
// activity-result callback
// below reliable, together with the AuthorizationManagementActivity
// launchMode="singleInstance" override (also AndroidManifest.xml) that
// fixes AppAuth issue #977 on Android 14.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VentusTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val authLauncher =
                        rememberLauncherForActivityResult(
                            ActivityResultContracts.StartActivityForResult(),
                        ) { result ->
                            result.data?.let { authViewModel.handleAuthResponseIntent(it) }
                        }
                    VentusApp(
                        authViewModel = authViewModel,
                        isOnboardedInitially = appPreferences.isOnboarded,
                        onLoginClick = {
                            authViewModel.buildLoginIntent()?.let { authLauncher.launch(it) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun VentusApp(
    authViewModel: AuthViewModel,
    isOnboardedInitially: Boolean,
    onLoginClick: () -> Unit,
) {
    val authState by authViewModel.uiState.collectAsState()
    var isOnboarded by remember { mutableStateOf(isOnboardedInitially) }

    when {
        authState !is AuthUiState.LoggedIn -> {
            LoginScreen(authViewModel, onLoginClick)
        }

        !isOnboarded -> {
            val onboardingViewModel: OnboardingViewModel = hiltViewModel()
            OnboardingScreen(onboardingViewModel, onDone = { isOnboarded = true })
        }

        else -> {
            HomeScreen()
        }
    }
}

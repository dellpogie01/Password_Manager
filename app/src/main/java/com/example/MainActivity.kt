package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.model.VaultEntry
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.VaultViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { it.remove() }
        }
        val vaultViewModel = androidx.lifecycle.ViewModelProvider(this)[VaultViewModel::class.java]
        // The first app frame is the login/setup screen, rather than an intermediate spinner.
        val content = findViewById<android.view.View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (vaultViewModel.isLoading.value) return false
                content.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel = vaultViewModel
                    val isInitialized by viewModel.isInitialized.collectAsState()
                    val isUnlocked by viewModel.isUnlocked.collectAsState()
                    val isLoading by viewModel.isLoading.collectAsState()

                    DisposableEffect(Unit) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_STOP) {
                                viewModel.lockVault()
                            }
                        }
                        lifecycle.addObserver(observer)
                        onDispose {
                            lifecycle.removeObserver(observer)
                        }
                    }

                    if (isLoading) {
                        return@Surface
                    }
                    if (isInitialized && !isUnlocked) {
                        UnlockScreen(viewModel = viewModel, onUnlocked = {})
                        return@Surface
                    }

                    val navController = rememberNavController()
                    val startDestination = when {
                        !isInitialized -> "setup"
                        !isUnlocked -> "unlock"
                        else -> "dashboard"
                    }

                    NavHost(navController = navController, startDestination = startDestination) {
                        composable("setup") {
                            SetupScreen(
                                viewModel = viewModel,
                                onVaultCreated = {
                                    navController.navigate("dashboard") {
                                        popUpTo("setup") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("unlock") {
                            UnlockScreen(
                                viewModel = viewModel,
                                onUnlocked = {
                                    navController.navigate("dashboard") {
                                        popUpTo("unlock") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("dashboard") {
                            if (!isUnlocked) {
                                LaunchedEffect(Unit) {
                                    navController.navigate("unlock") {
                                        popUpTo("dashboard") { inclusive = true }
                                    }
                                }
                            } else {
                                DashboardScreen(
                                    viewModel = viewModel,
                                    onLockClicked = {
                                        viewModel.lockVault()
                                        navController.navigate("unlock") {
                                            popUpTo("dashboard") { inclusive = true }
                                        }
                                    },
                                    onSettingsClicked = { navController.navigate("settings") },
                                    onAddEntryClicked = { navController.navigate("edit_entry?id=0") },
                                    onEditEntryClicked = { entry ->
                                        navController.navigate("edit_entry?id=${entry.id}")
                                    }
                                )
                            }
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onVaultReset = {
                                    viewModel.lockVault()
                                    navController.navigate("setup") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("edit_entry?id={entryId}") { backStackEntry ->
                            val entryId = backStackEntry.arguments?.getString("entryId")?.toLongOrNull() ?: 0L
                            val entries by viewModel.allEntries.collectAsState()
                            val entryToEdit = entries.find { it.id == entryId }

                            EntryEditAddScreen(
                                viewModel = viewModel,
                                entryToEdit = entryToEdit,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

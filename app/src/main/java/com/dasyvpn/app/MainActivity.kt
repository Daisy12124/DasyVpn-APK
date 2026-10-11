package com.dasyvpn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dasyvpn.app.ui.MainViewModel
import com.dasyvpn.app.ui.screens.HomeScreen
import com.dasyvpn.app.ui.screens.LogsScreen
import com.dasyvpn.app.ui.screens.ServersScreen
import com.dasyvpn.app.ui.screens.SettingsScreen
import com.dasyvpn.app.ui.theme.DasyTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            vm.consumePrepareIntent()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DasyTheme {
                val state by vm.ui.collectAsStateWithLifecycle()
                val prepare by vm.prepareIntent.collectAsStateWithLifecycle()

                LaunchedEffect(prepare) {
                    prepare?.let { vpnPermission.launch(it) }
                }
                LaunchedEffect(state.errorMessage) {
                    // خطاها در UI نمایش داده می‌شوند
                }

                DasyAppUi(vm)
            }
        }
    }
}

@Composable
private fun DasyAppUi(vm: MainViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val state by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            vm.clearError()
        }
    }

    val tabs = listOf(
        Tab("home", "خانه", Icons.Filled.Home),
        Tab("servers", "سرورها", Icons.Filled.List),
        Tab("settings", "تنظیمات", Icons.Filled.Settings),
        Tab("logs", "گزارش", Icons.Filled.Terminal),
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val back by nav.currentBackStackEntryAsState()
            val current = back?.destination?.route ?: "home"
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") { HomeScreen(vm, state) }
            composable("servers") { ServersScreen(vm, state) }
            composable("settings") { SettingsScreen(vm, state) }
            composable("logs") { LogsScreen(vm) }
        }
    }
}

private data class Tab(val route: String, val title: String, val icon: ImageVector)

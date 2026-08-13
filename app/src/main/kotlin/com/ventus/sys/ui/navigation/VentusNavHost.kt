package com.ventus.sys.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ventus.sys.ui.audit.PlaylistAuditScreen
import com.ventus.sys.ui.dashboard.DashboardScreen
import com.ventus.sys.ui.discover.DiscoverScreen
import com.ventus.sys.ui.history.SessionHistoryScreen
import com.ventus.sys.ui.mastervault.MasterVaultScreen
import com.ventus.sys.ui.queue.QueueScreen
import com.ventus.sys.ui.scorer.LiveScorerScreen
import com.ventus.sys.ui.settings.SettingsScreen
import com.ventus.sys.ui.signals.SignalsScreen
import com.ventus.sys.ui.transport.TransportBar
import com.ventus.sys.ui.vault.VaultScreen
import kotlinx.coroutines.launch

/**
 * Drawer-nav shell — replaces the bottom nav bar used through 5
 * destinations (Material's own upper bound for a labeled bottom bar; this
 * screen was the 6th). Each new screen is still one more
 * [VentusDestination] entry + one more [NavigationDrawerItem], not a
 * restructure — same growth pattern as before, different container.
 */
private enum class VentusDestination(
    val route: String,
    val label: String,
) {
    SCORER("scorer", "Scorer"),
    VAULT("vault", "Vault"),
    DASHBOARD("dashboard", "Dashboard"),
    AUDIT("audit", "Audit"),
    HISTORY("history", "History"),
    SETTINGS("settings", "Settings"),
    DISCOVER("discover", "Discover"),
    QUEUE("queue", "Queue"),
    SIGNALS("signals", "Signals"),
    MASTER_VAULT("master_vault", "Master Vault"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentusNavHost() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination =
        VentusDestination.entries.find { d ->
            backStackEntry?.destination?.hierarchy?.any { it.route == d.route } == true
        }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(navController, currentDestination, drawerState)
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentDestination?.label.orEmpty()) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                )
            },
            // Persistent now-playing deck, same as when this was the bottomBar
            // slot's content alongside NavigationBar — TransportBar's own
            // visibility rule (hidden when nothing's loaded) is unchanged.
            bottomBar = { TransportBar() },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = VentusDestination.SCORER.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(VentusDestination.SCORER.route) { LiveScorerScreen() }
                composable(VentusDestination.VAULT.route) { VaultScreen() }
                composable(VentusDestination.DASHBOARD.route) { DashboardScreen() }
                composable(VentusDestination.AUDIT.route) { PlaylistAuditScreen() }
                composable(VentusDestination.HISTORY.route) { SessionHistoryScreen() }
                composable(VentusDestination.SETTINGS.route) { SettingsScreen() }
                composable(VentusDestination.DISCOVER.route) { DiscoverScreen() }
                composable(VentusDestination.QUEUE.route) { QueueScreen() }
                composable(VentusDestination.SIGNALS.route) { SignalsScreen() }
                composable(VentusDestination.MASTER_VAULT.route) { MasterVaultScreen() }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    navController: NavController,
    currentDestination: VentusDestination?,
    drawerState: DrawerState,
) {
    val scope = rememberCoroutineScope()
    ModalDrawerSheet {
        Text(
            text = "VENTUS // SYS",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
        )
        VentusDestination.entries.forEach { destination ->
            NavigationDrawerItem(
                label = { Text(destination.label) },
                selected = destination == currentDestination,
                icon = { Icon(imageVector = iconFor(destination), contentDescription = null) },
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                    scope.launch { drawerState.close() }
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

private fun iconFor(destination: VentusDestination) =
    when (destination) {
        VentusDestination.SCORER -> Icons.Filled.Radar
        VentusDestination.VAULT -> Icons.Filled.LibraryMusic
        VentusDestination.DASHBOARD -> Icons.Filled.Dashboard
        VentusDestination.AUDIT -> Icons.AutoMirrored.Filled.FactCheck
        VentusDestination.HISTORY -> Icons.Filled.History
        VentusDestination.SETTINGS -> Icons.Filled.Settings
        VentusDestination.DISCOVER -> Icons.Filled.Search
        VentusDestination.QUEUE -> Icons.AutoMirrored.Filled.QueueMusic
        VentusDestination.SIGNALS -> Icons.AutoMirrored.Filled.ShowChart
        VentusDestination.MASTER_VAULT -> Icons.Filled.Storage
    }

package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Member
import com.example.ui.screens.*
import com.example.ui.viewmodel.RasmViewModel
import kotlinx.coroutines.launch

enum class Screen(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    MEMBERS("Members", Icons.Default.Groups),
    DUES("Dues", Icons.Default.ReceiptLong),
    EVENTS("Events", Icons.Default.Event),
    FINANCES("Finance", Icons.Default.AccountBalance),
    MEETINGS("Meetings", Icons.Default.CoPresent),
    SERVICES("Services", Icons.Default.Storefront),
    ACTIVITY("Audit Log", Icons.Default.History),
    SETTINGS("Settings", Icons.Default.Settings),
    FAMILY("My Family", Icons.Default.FamilyRestroom),
    ASK_AI("Ask AI", Icons.Default.AutoAwesome),
    HELP("Help & FAQ", Icons.Default.Info),
    CONTACT("Contact Us", Icons.Default.ContactPhone),
    PRIVACY("Privacy Policy", Icons.Default.PrivacyTip)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: RasmViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val isAdmin by viewModel.isAdmin.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Listen for user messages
    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    if (currentUser == null) {
        AuthScreen(
            viewModel = viewModel,
            onAuthSuccess = {
                viewModel.checkAdminStatus()
            }
        )
    } else {
        var currentScreen by remember { mutableStateOf(Screen.HOME) }

        // System back returns to Home from any other screen instead of closing the app
        BackHandler(enabled = currentScreen != Screen.HOME) {
            currentScreen = Screen.HOME
        }
        var membersInitialFilter by remember { mutableStateOf<String?>(null) }
        var profileMemberToView by remember { mutableStateOf<Member?>(null) }

        var showMenu by remember { mutableStateOf(false) }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = when (currentScreen) {
                                    Screen.HOME -> settings.associationName.ifBlank { "RASM" }
                                    else -> currentScreen.title
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 1
                            )
                            if (currentScreen == Screen.HOME && settings.associationLocation.isNotBlank()) {
                                Text(
                                    text = settings.associationLocation,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        // Quick switch to Meetings
                        IconButton(onClick = { currentScreen = Screen.MEETINGS }) {
                            Icon(Icons.Default.CoPresent, contentDescription = "Meetings")
                        }
                        // Quick switch to Services
                        IconButton(onClick = { currentScreen = Screen.SERVICES }) {
                            Icon(Icons.Default.Storefront, contentDescription = "Services")
                        }

                        // Overflow menu
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Meetings & Minutes") },
                                leadingIcon = { Icon(Icons.Default.CoPresent, contentDescription = null) },
                                onClick = {
                                    currentScreen = Screen.MEETINGS
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Services Directory") },
                                leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                                onClick = {
                                    currentScreen = Screen.SERVICES
                                    showMenu = false
                                }
                            )
                            if (isAdmin) {
                                DropdownMenuItem(
                                    text = { Text("Audit / Activity Log") },
                                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                                    onClick = {
                                        currentScreen = Screen.ACTIVITY
                                        showMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("My Family") },
                                leadingIcon = { Icon(Icons.Default.FamilyRestroom, contentDescription = null) },
                                onClick = {
                                    currentScreen = Screen.FAMILY
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Ask AI") },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                                onClick = {
                                    currentScreen = Screen.ASK_AI
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    currentScreen = Screen.SETTINGS
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Help & FAQ") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    currentScreen = Screen.HELP
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Contact Us") },
                                leadingIcon = { Icon(Icons.Default.ContactPhone, contentDescription = null) },
                                onClick = {
                                    currentScreen = Screen.CONTACT
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Privacy Policy") },
                                leadingIcon = { Icon(Icons.Default.PrivacyTip, contentDescription = null) },
                                onClick = {
                                    currentScreen = Screen.PRIVACY
                                    showMenu = false
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Sign Out", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    viewModel.logout()
                                }
                            )
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    val navItems = listOf(
                        Screen.HOME,
                        Screen.MEMBERS,
                        Screen.DUES,
                        Screen.EVENTS,
                        Screen.FINANCES
                    )
                    navItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title, fontSize = 11.sp) },
                            selected = currentScreen == screen,
                            onClick = { currentScreen = screen },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag("nav_${screen.name.lowercase()}")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    Screen.HOME -> HomeScreen(
                        viewModel = viewModel,
                        onNavigateToMembers = { filter ->
                            membersInitialFilter = filter
                            currentScreen = Screen.MEMBERS
                        },
                        onViewMemberProfile = { member ->
                            profileMemberToView = member
                        }
                    )
                    Screen.MEMBERS -> MembersScreen(
                        viewModel = viewModel,
                        initialFilter = membersInitialFilter
                    )
                    Screen.DUES -> MaintenanceScreen(
                        viewModel = viewModel
                    )
                    Screen.EVENTS -> EventsScreen(
                        viewModel = viewModel
                    )
                    Screen.FINANCES -> FinancialReportsScreen(
                        viewModel = viewModel
                    )
                    Screen.MEETINGS -> MeetingsScreen(
                        viewModel = viewModel
                    )
                    Screen.SERVICES -> ServicesScreen(
                        viewModel = viewModel
                    )
                    Screen.ACTIVITY -> ActivityLogScreen(
                        viewModel = viewModel
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        viewModel = viewModel,
                        onLogout = { currentScreen = Screen.HOME }
                    )
                    Screen.FAMILY -> FamilyScreen(viewModel = viewModel)
                    Screen.ASK_AI -> AskAiScreen(rasm = viewModel)
                    Screen.HELP -> HelpScreen()
                    Screen.CONTACT -> ContactScreen(viewModel = viewModel)
                    Screen.PRIVACY -> PrivacyPolicyScreen(viewModel = viewModel)
                }
            }
        }
    }
}

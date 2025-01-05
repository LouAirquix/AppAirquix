package com.example.airquix01.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.airquix01.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreens(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    // Füge "YamNet" (oder wie du es nennen möchtest) hinzu:
    val tabs = listOf("Camera", "AI Logs", "Manual", "Audio", "Activity", "YamNet")

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Airquix App") }
            )
        },
        content = { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                ScrollableTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            text = { Text(title) },
                            selected = selectedTab == index,
                            onClick = { selectedTab = index }
                        )
                    }
                }
                when (selectedTab) {
                    0 -> CameraScreen(viewModel)
                    1 -> AiScreen(viewModel)
                    2 -> ManualScreen(viewModel)
                    3 -> OtherInputsScreen(viewModel)
                    4 -> ActivityScreen(viewModel)
                    5 -> YamNetScreen(viewModel)  // <-- Hier kommt dein neuer Screen!
                }
            }
        }
    )
}


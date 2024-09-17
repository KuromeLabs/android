package com.kuromelabs.kurome.presentation.ui.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kuromelabs.kurome.presentation.util.Screen
import com.kuromelabs.kurome.presentation.util.Utils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    viewModel: DeviceViewModel = hiltViewModel(),
    navController: NavController
) {
    val devices = viewModel.allDevices.collectAsState().value

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    NavDrawer(drawerState = drawerState, navController) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Kurome") },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                drawerState.apply {
                                    if (isClosed) open() else close()
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Menu, contentDescription = null)
                        }
                    })
            },
        ) { innerPadding ->
            LazyColumn(modifier = Modifier.padding(innerPadding)) {
                items(devices) { deviceContext ->
                    DeviceRow(deviceContext, Modifier.clickable {
                        navController.navigate("${Screen.DeviceDetailScreen.route}/${deviceContext.id}/${deviceContext.name}")
                    })
                }
            }
        }
    }


}

@Composable
fun NavDrawer(drawerState: DrawerState, navController: NavController, content: @Composable () -> Unit) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Kurome", modifier = Modifier.padding(16.dp))
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text(text = "Add Device") },
                    selected = false,
                    onClick = { navController.navigate(Screen.AddDeviceScreen.route) }
                )
                // ...other drawer items
            }
        }
    ) {
        content()
    }
}


@Composable
fun DeviceRow(state: DeviceState, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,

        ) {

        Icon(
            imageVector = Icons.Filled.Computer,
            contentDescription = null,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .size(48.dp),
        )
        Column {
            Text(
                text = state.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = Utils.GetStatusMessage(state.pairStatus, state.connected, LocalContext.current),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
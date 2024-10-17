package com.kuromelabs.kurome.presentation.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kuromelabs.kurome.infrastructure.device.DeviceState
import com.kuromelabs.kurome.infrastructure.device.PairStatus
import com.kuromelabs.kurome.presentation.util.Utils


@Composable
fun DeviceDetailsScreen(
    viewModel: DeviceDetailsViewModel = hiltViewModel(),
    navController: NavController
) {
    val deviceState = viewModel.deviceContext.collectAsState(DeviceState("", "", PairStatus.UNPAIRED, false)).value!!

    DeviceDetails(
        deviceState.name,
        deviceState.id,
        deviceState,
        viewModel,
        onBackButtonClicked = {
            navController.popBackStack()
        })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetails(
    name: String,
    id: String,
    state: DeviceState,
    viewModel: DeviceDetailsViewModel,
    onBackButtonClicked: () -> Unit = { }
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val stateString = Utils.GetStatusMessage(state.pairStatus, state.connected, LocalContext.current)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                title = {
                    Text(
                        text = "Device Details",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        onBackButtonClicked()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->


        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(padding)

        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Computer,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                        Text(
                            text = name,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stateString,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                }
            }
            item {
                ActionRow(state, viewModel)
            }
        }


    }
}

@Composable
fun ActionRow(state: DeviceState, viewModel: DeviceDetailsViewModel) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        if (state.pairStatus == PairStatus.UNPAIRED) {
            Button(
                onClick = { viewModel.pairDevice(state.id) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddLink,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(text = "Pair")
                }
            }
        } else if (state.pairStatus == PairStatus.PAIRED) {
            Button(
                onClick = { /*TODO*/ },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddLink,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(text = "Unpair")
                }
            }
        }

    }
}





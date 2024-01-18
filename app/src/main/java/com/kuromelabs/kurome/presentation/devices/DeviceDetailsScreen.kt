package com.kuromelabs.kurome.presentation.devices

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
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.LinkOff
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kuromelabs.kurome.R
import com.kuromelabs.kurome.application.repository.DeviceContext


@Composable
fun DeviceDetailsScreen(
    viewModel: DeviceDetailsViewModel = hiltViewModel(),
    navController: NavController
) {
    val deviceContext = viewModel.deviceContext.collectAsState().value

    DeviceDetails(deviceContext.device.name, deviceContext.device.id, deviceContext.state, onBackButtonClicked = {
        navController.popBackStack()
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetails(
    name: String,
    id: String,
    state: DeviceContext.State,
    onBackButtonClicked: () -> Unit = { }
) {
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val resources = LocalContext.current.resources
    val stateString = when (state) {
        DeviceContext.State.CONNECTED_TRUSTED -> resources.getString(R.string.status_connected)
        DeviceContext.State.DISCONNECTED -> resources.getString(R.string.status_disconnected)
        DeviceContext.State.CONNECTED_UNTRUSTED -> resources.getString(R.string.status_available)
        DeviceContext.State.CONNECTING -> resources.getString(R.string.status_connecting)
        else -> "Unknown"
    }

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
                            imageVector = Icons.Filled.ArrowBack,
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
                ActionRow(state)
            }
        }


    }
}

@Composable
fun ActionRow(state: DeviceContext.State) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        if (state == DeviceContext.State.CONNECTED_TRUSTED) {
            Button(onClick = { /*TODO*/ },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column {
                    Icon(
                        imageVector = Icons.Filled.AddLink,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(text = "Pair")
                }
            }
        } else if (state == DeviceContext.State.CONNECTED_UNTRUSTED) {
            IconButton(onClick = { /*TODO*/ }) {
                Column {
                    Icon(
                        imageVector = Icons.Filled.LinkOff,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(text = "Unpair")
                }
            }
        }

    }
}

@Preview
@Composable
fun DevicesDetailScreenPreview() {
    DeviceDetails(
        name = "Device Name",
        id = "Device ID",
        state = DeviceContext.State.CONNECTED_TRUSTED
    )
}





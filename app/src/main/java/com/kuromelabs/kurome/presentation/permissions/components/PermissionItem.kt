package com.kuromelabs.kurome.presentation.permissions.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kuromelabs.kurome.R

@Composable
fun PermissionRow(
    permissionTitle: String,
    permissionBody: String,
    onClick: (() -> Unit),
    hasPermission: Boolean
) {
    val resources = LocalContext.current.resources
    Surface(
        modifier = Modifier
            .padding(top = 20.dp)
            .border(BorderStroke(2.dp, MaterialTheme.colors.primary), RoundedCornerShape(5.dp))
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(0.7f)) {
                Text(
                    text = permissionTitle,
                    style = MaterialTheme.typography.subtitle1.copy(fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    text = permissionBody,
                    style = MaterialTheme.typography.body2,
                )
            }
            TextButton(
                modifier = Modifier.weight(0.3f),
                onClick = onClick,
                enabled = !hasPermission
            ) {
                Text(
                    text = if (hasPermission) resources.getString(R.string.granted_permission)
                    else resources.getString(R.string.grant_permission),
                    style = MaterialTheme.typography.button
                )
            }
        }
    }
}
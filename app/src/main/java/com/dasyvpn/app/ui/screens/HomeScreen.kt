package com.dasyvpn.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.North
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasyvpn.app.model.VpnState
import com.dasyvpn.app.ui.MainViewModel
import com.dasyvpn.app.ui.MainViewModel.UiState

@Composable
fun HomeScreen(vm: MainViewModel, state: UiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // انتخاب سرور
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { /* رفتن به صفحه سرورها توسط نوار پایین */ },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "سرور فعال",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = state.selectedServer?.name ?: "سروری انتخاب نشده",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
        }

        Spacer(Modifier.height(16.dp))

        // دکمه اصلی
        PowerButton(state.vpnState) { vm.toggleConnect() }

        Spacer(Modifier.height(16.dp))

        // وضعیت
        Text(
            text = when (state.vpnState) {
                VpnState.DISCONNECTED -> "قطع شده"
                VpnState.CONNECTING -> "در حال اتصال…"
                VpnState.CONNECTED -> "متصل"
                VpnState.STOPPING -> "در حال قطع…"
                VpnState.ERROR -> "خطا"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = when (state.vpnState) {
                VpnState.CONNECTED -> MaterialTheme.colorScheme.secondary
                VpnState.CONNECTING, VpnState.STOPPING -> MaterialTheme.colorScheme.primary
                VpnState.ERROR -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onBackground
            }
        )

        if (state.vpnState == VpnState.CONNECTED) {
            Text(
                text = formatDuration(state.connectedSeconds),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(24.dp))

        // سرعت‌ها
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SpeedItem(
                    icon = { Icon(Icons.Filled.North, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp)) },
                    label = "آپلود",
                    value = formatSpeed(state.upSpeed)
                )
                SpeedItem(
                    icon = { Icon(Icons.Filled.South, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                    label = "دانلود",
                    value = formatSpeed(state.downSpeed)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PowerButton(state: VpnState, onClick: () -> Unit) {
    val color by animateColorAsState(
        targetValue = when (state) {
            VpnState.CONNECTED -> Color(0xFF00D4AA)
            VpnState.CONNECTING, VpnState.STOPPING -> Color(0xFFFFB74D)
            VpnState.ERROR -> Color(0xFFFF6B6B)
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(400),
        label = "powerColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (state == VpnState.CONNECTING) 1.08f else 1f,
        animationSpec = tween(400),
        label = "powerScale"
    )

    Box(
        modifier = Modifier
            .size(180.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = "اتصال",
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
        }
    }
}

@Composable
private fun SpeedItem(
    icon: @Composable () -> Unit,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
    }
}

fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "0 KB/s"
    return when {
        bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024 -> String.format("%.0f KB/s", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
    }
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

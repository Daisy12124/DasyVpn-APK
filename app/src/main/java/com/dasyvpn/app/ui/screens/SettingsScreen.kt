package com.dasyvpn.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dasyvpn.app.ui.MainViewModel
import com.dasyvpn.app.ui.MainViewModel.UiState

@Composable
fun SettingsScreen(vm: MainViewModel, state: UiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "تنظیمات",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        SettingCard(
            title = "بایپس ترافیک داخلی ایران",
            description = "سایت‌ها و سرویس‌های داخلی بدون VPN باز می‌شوند (سریع‌تر و پایدارتر)"
        ) {
            Switch(checked = state.bypassIran, onCheckedChange = vm::setBypassIran)
        }

        SettingCard(
            title = "اتصال از اپ‌های انتخابی",
            description = "فقط اپ‌هایی که خودتان انتخاب کنید از VPN رد شوند"
        ) {
            Switch(checked = state.perAppProxy, onCheckedChange = vm::setPerAppProxy)
        }

        if (state.perAppProxy) {
            var showPicker by remember { mutableStateOf(false) }

            SettingCard(
                title = if (state.perAppBypassMode) "حالت: عبور از VPN" else "حالت: فقط از VPN",
                description = if (state.perAppBypassMode)
                    "اپ‌های انتخابی از VPN عبور می‌کنند"
                else
                    "فقط اپ‌های انتخابی از VPN استفاده می‌کنند"
            ) {
                Switch(
                    checked = state.perAppBypassMode,
                    onCheckedChange = vm::setPerAppBypassMode
                )
            }

            Card(
                onClick = { showPicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("انتخاب اپ‌ها", fontWeight = FontWeight.Bold)
                        Text(
                            "${state.perAppPackages.size} اپ انتخاب شده",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (showPicker) {
                AppPickerDialog(
                    installed = remember { vm.installedApps() },
                    selected = state.perAppPackages,
                    onToggle = vm::togglePerAppPackage,
                    onDismiss = { showPicker = false }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("درباره DASY VPN", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "نسخه ۱.۰.۰\nموتور: Xray-core (libv2ray از پروژه v2rayNG)\nپروتکل‌ها: VLESS، VMess، Trojan، Shadowsocks\nپشتیبانی Reality، TLS، WebSocket، gRPC",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingCard(
    title: String,
    description: String,
    trailing: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
private fun AppPickerDialog(
    installed: List<android.content.pm.ApplicationInfo>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val pm = LocalContext.current.packageManager
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب اپ‌ها") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                items(installed.size) { i ->
                    val app = installed[i]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            app.loadLabel(pm).toString(),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = app.packageName in selected,
                            onCheckedChange = { onToggle(app.packageName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("تمام") }
        }
    )
}

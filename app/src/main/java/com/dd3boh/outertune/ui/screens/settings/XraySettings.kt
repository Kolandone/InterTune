/*
 * Copyright (C) 2026 InterTune
 *
 * SPDX-License-Identifier: GPL-3.0
 */
package com.dd3boh.outertune.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.AetherProxyEnabledKey
import com.dd3boh.outertune.constants.VisitorDataKey
import com.dd3boh.outertune.constants.XrayAutoSelectKey
import com.dd3boh.outertune.constants.XrayConfigInputKey
import com.dd3boh.outertune.constants.XrayPortKey
import com.dd3boh.outertune.constants.XrayProxyEnabledKey
import com.dd3boh.outertune.ui.component.ColumnWithContentPadding
import com.dd3boh.outertune.ui.component.EditTextPreference
import com.dd3boh.outertune.ui.component.FloatingTopBar
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SettingsClickToReveal
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.dialog.InfoLabel
import com.dd3boh.outertune.utils.YouTubeRegion
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.utils.xray.XrayProfileStore
import com.dd3boh.outertune.utils.xray.XrayProxyManager
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XraySettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            XrayProfileStore.ensureLoaded(context)
        }
    }

    val (enabled, onEnabledChange) = rememberPreference(XrayProxyEnabledKey, false)
    val (autoSelect, onAutoSelectChange) = rememberPreference(XrayAutoSelectKey, true)
    val (configInput, onConfigInputChange) = rememberPreference(XrayConfigInputKey, "")
    val (port, onPortChange) = rememberPreference(XrayPortKey, XrayProxyManager.DEFAULT_PORT)

    val state by XrayProxyManager.state.collectAsState()
    val lastError by XrayProxyManager.lastError.collectAsState()
    val activePort by XrayProxyManager.activePort.collectAsState()
    val activeProfileName by XrayProxyManager.activeProfileName.collectAsState()
    val scanProgress by XrayProxyManager.scanProgress.collectAsState()
    val logs by XrayProxyManager.logs.collectAsState()

    val subscriptions by XrayProfileStore.subscriptions.collectAsState()
    val profiles by XrayProfileStore.profiles.collectAsState()
    val selectedProfileId by XrayProfileStore.selectedProfileId.collectAsState()

    val coreVersion = remember { XrayProxyManager.coreVersion() }
    val refreshFailedText = stringResource(R.string.aether_refresh_yt_failed)

    var showLogsDialog by remember { mutableStateOf(false) }
    var showAddSubDialog by remember { mutableStateOf(false) }
    var subNameInput by remember { mutableStateOf("") }
    var subUrlInput by remember { mutableStateOf("") }
    var isFetchingSub by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var isRefreshingYt by remember { mutableStateOf(false) }

    fun activateProfile(profile: XrayProfileStore.Profile) {
        XrayProfileStore.selectProfile(context, profile.id)
        onConfigInputChange(profile.rawConfig)
        testResultText = null
        if (enabled) {
            XrayProxyManager.start(context, profile.rawConfig, port = port, forceRestart = true)
        }
    }

    if (showAddSubDialog) {
        AlertDialog(
            onDismissRequest = { if (!isFetchingSub) showAddSubDialog = false },
            title = { Text(stringResource(R.string.xray_add_sub)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = subNameInput,
                        onValueChange = { subNameInput = it },
                        label = { Text(stringResource(R.string.xray_sub_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subUrlInput,
                        onValueChange = { subUrlInput = it },
                        label = { Text(stringResource(R.string.xray_sub_url)) },
                        placeholder = { Text("https://...") },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isFetchingSub && subUrlInput.isNotBlank(),
                    onClick = {
                        isFetchingSub = true
                        coroutineScope.launch {
                            val res = XrayProfileStore.fetchAndImportSubscription(
                                context = context,
                                url = subUrlInput.trim(),
                                name = subNameInput.trim()
                            )
                            isFetchingSub = false
                            res.fold(
                                onSuccess = { (_, count) ->
                                    showAddSubDialog = false
                                    subNameInput = ""
                                    subUrlInput = ""
                                    XrayProfileStore.getSelectedProfile(context)?.let { first ->
                                        if (configInput.isBlank()) {
                                            onConfigInputChange(first.rawConfig)
                                        }
                                    }
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.xray_sub_imported, count),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onFailure = { err ->
                                    Toast.makeText(
                                        context,
                                        err.localizedMessage ?: "Subscription fetch failed",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                    }
                ) {
                    Text(if (isFetchingSub) stringResource(R.string.xray_sub_fetching) else stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isFetchingSub,
                    onClick = { showAddSubDialog = false }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            title = { Text(stringResource(R.string.xray_logs_title)) },
            text = {
                val logScroll = rememberScrollState()
                SelectionContainer {
                    Text(
                        text = if (logs.isEmpty()) stringResource(R.string.aether_logs_empty)
                        else logs.joinToString("\n"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 420.dp)
                            .verticalScroll(logScroll)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Xray Logs", logs.joinToString("\n")))
                    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.aether_copy_logs))
                }
            },
            dismissButton = {
                TextButton(onClick = { XrayProxyManager.clearLogs() }) {
                    Text(stringResource(R.string.aether_clear_logs))
                }
            }
        )
    }

    ColumnWithContentPadding(
        modifier = Modifier.fillMaxHeight(),
        columnModifier = Modifier.padding(horizontal = 16.dp)
    ) {
        // 1. Master Switch, Auto-Select & Status
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = { Text(stringResource(R.string.xray_proxy_title)) },
                description = stringResource(R.string.xray_proxy_desc),
                icon = { Icon(Icons.Rounded.VpnKey, null) },
                checked = enabled,
                onCheckedChange = { isChecked ->
                    testResultText = null
                    if (isChecked) {
                        val effectiveConfig = configInput.ifBlank {
                            XrayProfileStore.getSelectedProfile(context)?.rawConfig.orEmpty()
                        }
                        if (effectiveConfig.isBlank() && profiles.isEmpty()) {
                            Toast.makeText(context, R.string.xray_config_empty_toast, Toast.LENGTH_SHORT).show()
                            return@SwitchPreference
                        }
                        onEnabledChange(true)
                        coroutineScope.launch(Dispatchers.IO) {
                            context.dataStore.edit { it[AetherProxyEnabledKey] = false }
                        }
                        if (autoSelect && profiles.size > 1) {
                            coroutineScope.launch {
                                val res = XrayProxyManager.autoSelectAndConnect(context, port = port)
                                res.onFailure {
                                    if (effectiveConfig.isNotBlank()) {
                                        XrayProxyManager.start(context, effectiveConfig, port = port, forceRestart = true)
                                    }
                                }
                            }
                        } else {
                            onConfigInputChange(effectiveConfig)
                            XrayProxyManager.start(context, effectiveConfig, port = port, forceRestart = true)
                        }
                    } else {
                        onEnabledChange(false)
                        XrayProxyManager.stop()
                    }
                }
            )

            SwitchPreference(
                title = { Text(stringResource(R.string.xray_auto_select)) },
                description = stringResource(R.string.xray_auto_select_desc),
                icon = { Icon(Icons.Rounded.AutoAwesome, null) },
                checked = autoSelect,
                onCheckedChange = onAutoSelectChange
            )

            val statusText = when (state) {
                XrayProxyManager.State.CONNECTED -> {
                    val base = stringResource(R.string.xray_status_connected, activePort)
                    activeProfileName?.let { "$base • $it" } ?: base
                }
                XrayProxyManager.State.STARTING ->
                    stringResource(R.string.aether_status_connecting)
                XrayProxyManager.State.ERROR ->
                    lastError ?: "Error"
                XrayProxyManager.State.STOPPED ->
                    stringResource(R.string.aether_status_stopped)
            }

            InfoLabel(
                text = "$statusText • $coreVersion",
                isError = state == XrayProxyManager.State.ERROR
            )

            scanProgress?.let { prog ->
                InfoLabel(
                    text = stringResource(R.string.xray_testing_progress, prog),
                    isError = false
                )
            }

            testResultText?.let { result ->
                InfoLabel(
                    text = result,
                    isError = result.startsWith("Error") || result.startsWith("Failed")
                )
            }

            if (profiles.isNotEmpty()) {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.xray_auto_connect_now)) },
                    description = stringResource(R.string.xray_auto_connect_now_desc),
                    icon = { Icon(Icons.Rounded.AutoAwesome, null) },
                    isEnabled = scanProgress == null,
                    onClick = {
                        testResultText = null
                        onEnabledChange(true)
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                context.dataStore.edit { it[AetherProxyEnabledKey] = false }
                            }
                            val res = XrayProxyManager.autoSelectAndConnect(context, port = port)
                            res.fold(
                                onSuccess = { best ->
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.xray_auto_selected_toast, best.name, best.delayMs ?: 0L),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onFailure = { err ->
                                    Toast.makeText(
                                        context,
                                        err.localizedMessage ?: "No working server found",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
                    }
                )
            }

            AnimatedVisibility(enabled) {
                Column {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.aether_reconnect)) },
                        description = stringResource(R.string.xray_reconnect_desc),
                        icon = { Icon(Icons.Rounded.Refresh, null) },
                        onClick = {
                            testResultText = null
                            val cfg = configInput.ifBlank { XrayProfileStore.getSelectedProfile(context)?.rawConfig.orEmpty() }
                            if (cfg.isNotBlank()) {
                                XrayProxyManager.start(context, cfg, port = port, forceRestart = true)
                            }
                        }
                    )

                    PreferenceEntry(
                        title = { Text(stringResource(R.string.aether_test_connection)) },
                        description = if (isTesting) stringResource(R.string.aether_testing)
                        else stringResource(R.string.aether_test_connection_desc),
                        icon = { Icon(Icons.Rounded.Speed, null) },
                        isEnabled = state == XrayProxyManager.State.CONNECTED && !isTesting,
                        onClick = {
                            isTesting = true
                            testResultText = null
                            coroutineScope.launch {
                                val res = XrayProxyManager.testConnection(activePort)
                                testResultText = res.fold(
                                    onSuccess = { r ->
                                        "OK • IP: ${r.ip} • Country: ${r.country} • ${r.latencyMs} ms"
                                    },
                                    onFailure = { e ->
                                        "Failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
                                    }
                                )
                                isTesting = false
                            }
                        }
                    )

                    PreferenceEntry(
                        title = { Text(stringResource(R.string.aether_refresh_yt)) },
                        description = stringResource(R.string.aether_refresh_yt_desc),
                        icon = { Icon(Icons.Rounded.Sync, null) },
                        isEnabled = state == XrayProxyManager.State.CONNECTED && !isRefreshingYt,
                        onClick = {
                            isRefreshingYt = true
                            coroutineScope.launch {
                                val fresh = withContext(Dispatchers.IO) {
                                    YouTube.forgetIdleConnections()
                                    val token = YouTube.visitorData().getOrNull()
                                    if (token != null) {
                                        YouTube.visitorData = token
                                        context.dataStore.edit { it[VisitorDataKey] = token }
                                    }
                                    YouTubeRegion.refresh(context)
                                    token
                                }
                                if (fresh != null) {
                                    val cc = YouTubeRegion.countryOf(fresh) ?: "OK"
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.aether_refresh_yt_done, cc),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(context, refreshFailedText, Toast.LENGTH_SHORT).show()
                                }
                                isRefreshingYt = false
                            }
                        }
                    )
                }
            }

            PreferenceEntry(
                title = { Text(stringResource(R.string.xray_logs_title)) },
                description = stringResource(R.string.aether_logs_desc, logs.size),
                icon = { Icon(Icons.Rounded.Terminal, null) },
                onClick = { showLogsDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Subscriptions
        PreferenceGroupTitle(title = stringResource(R.string.xray_grp_subscriptions))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.xray_add_sub)) },
                description = stringResource(R.string.xray_add_sub_desc),
                icon = { Icon(Icons.Rounded.AddLink, null) },
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                    if (clip.startsWith("http://", ignoreCase = true) || clip.startsWith("https://", ignoreCase = true)) {
                        subUrlInput = clip
                    }
                    showAddSubDialog = true
                }
            )

            subscriptions.forEach { sub ->
                HorizontalDivider()
                val count = profiles.count { it.subscriptionId == sub.id }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sub.name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.xray_sub_server_count, count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        enabled = !isFetchingSub,
                        onClick = {
                            isFetchingSub = true
                            coroutineScope.launch {
                                val res = XrayProfileStore.fetchAndImportSubscription(
                                    context = context,
                                    url = sub.url,
                                    name = sub.name,
                                    existingSubId = sub.id
                                )
                                isFetchingSub = false
                                res.fold(
                                    onSuccess = { (_, updatedCount) ->
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.xray_sub_imported, updatedCount),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onFailure = { err ->
                                        Toast.makeText(
                                            context,
                                            err.localizedMessage ?: "Update failed",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                )
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Update")
                    }
                    IconButton(
                        onClick = { XrayProfileStore.deleteSubscription(context, sub.id) }
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Servers & Real Delay Ping
        PreferenceGroupTitle(title = stringResource(R.string.xray_grp_servers, profiles.size))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.xray_paste_clipboard)) },
                description = stringResource(R.string.xray_paste_clipboard_desc),
                icon = { Icon(Icons.Rounded.ContentPaste, null) },
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                    if (clip.isBlank()) {
                        Toast.makeText(context, R.string.xray_clipboard_empty, Toast.LENGTH_SHORT).show()
                    } else if (clip.startsWith("http://", ignoreCase = true) || clip.startsWith("https://", ignoreCase = true)) {
                        subUrlInput = clip
                        showAddSubDialog = true
                    } else {
                        val imported = XrayProfileStore.importRawText(context, clip)
                        if (imported.isNotEmpty()) {
                            onConfigInputChange(imported.first().rawConfig)
                            Toast.makeText(
                                context,
                                context.getString(R.string.xray_sub_imported, imported.size),
                                Toast.LENGTH_SHORT
                            ).show()
                            if (enabled) {
                                XrayProxyManager.start(context, imported.first().rawConfig, port = port, forceRestart = true)
                            }
                        } else {
                            onConfigInputChange(clip)
                            Toast.makeText(context, R.string.xray_config_saved, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )

            if (profiles.isNotEmpty()) {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.xray_test_all_delays)) },
                    description = scanProgress?.let { stringResource(R.string.xray_testing_progress, it) }
                        ?: stringResource(R.string.xray_test_all_delays_desc),
                    icon = { Icon(Icons.Rounded.NetworkPing, null) },
                    isEnabled = scanProgress == null,
                    onClick = {
                        coroutineScope.launch {
                            XrayProxyManager.testAllRealDelays(context)
                        }
                    }
                )

                PreferenceEntry(
                    title = { Text(stringResource(R.string.xray_sort_by_delay)) },
                    icon = { Icon(Icons.Rounded.Sort, null) },
                    onClick = {
                        XrayProfileStore.sortByDelay(context)
                    }
                )

                profiles.forEach { profile ->
                    HorizontalDivider()
                    val isSelected = profile.id == selectedProfileId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activateProfile(profile) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${profile.protocol} • ${profile.server}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        val (delayText, delayColor) = when {
                            profile.delayMs == null -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
                            profile.delayMs < 0 -> "Timeout" to Color(0xFFE53935)
                            profile.delayMs < 450 -> "${profile.delayMs} ms" to Color(0xFF43A047)
                            profile.delayMs < 1000 -> "${profile.delayMs} ms" to Color(0xFFFB8C00)
                            else -> "${profile.delayMs} ms" to Color(0xFFE53935)
                        }
                        Text(
                            text = delayText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = delayColor
                        )
                        IconButton(
                            onClick = { XrayProfileStore.deleteProfile(context, profile.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Delete",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 4. Advanced
        SettingsClickToReveal(stringResource(R.string.advanced)) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                EditTextPreference(
                    title = { Text(stringResource(R.string.xray_edit_config)) },
                    icon = { Icon(Icons.Rounded.Edit, null) },
                    value = configInput,
                    singleLine = false,
                    isInputValid = { true },
                    onValueChange = { newVal ->
                        onConfigInputChange(newVal.trim())
                        testResultText = null
                        if (enabled && newVal.isNotBlank()) {
                            XrayProxyManager.start(context, newVal.trim(), port = port, forceRestart = true)
                        }
                    },
                    inputHint = { "vless://... | vmess://... | trojan://... | ss://... | {JSON}" }
                )

                EditTextPreference(
                    title = { Text(stringResource(R.string.xray_port)) },
                    icon = { Icon(Icons.Rounded.Tune, null) },
                    value = port.toString(),
                    isInputValid = { it.toIntOrNull()?.let { p -> p in 1024..65530 } == true },
                    onValueChange = { pStr ->
                        pStr.toIntOrNull()?.let { p ->
                            onPortChange(p)
                            val cfg = configInput.ifBlank { XrayProfileStore.getSelectedProfile(context)?.rawConfig.orEmpty() }
                            if (enabled && cfg.isNotBlank()) {
                                XrayProxyManager.start(context, cfg, port = p, forceRestart = true)
                            }
                        }
                    },
                    inputHint = { "10808" }
                )

                if (configInput.isNotBlank() || profiles.isNotEmpty()) {
                    PreferenceEntry(
                        title = { Text(stringResource(R.string.xray_clear_config)) },
                        icon = { Icon(Icons.Rounded.DeleteSweep, null) },
                        onClick = {
                            onConfigInputChange("")
                            XrayProfileStore.clearAllProfiles(context)
                            if (enabled) {
                                onEnabledChange(false)
                                XrayProxyManager.stop()
                            }
                            testResultText = null
                            Toast.makeText(context, R.string.xray_config_cleared, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    FloatingTopBar(title = stringResource(R.string.xray_proxy_title), navController = navController)
}

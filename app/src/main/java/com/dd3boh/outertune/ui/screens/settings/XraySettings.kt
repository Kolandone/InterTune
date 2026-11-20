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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.AetherProxyEnabledKey
import com.dd3boh.outertune.constants.VisitorDataKey
import com.dd3boh.outertune.constants.XrayConfigInputKey
import com.dd3boh.outertune.constants.XrayPortKey
import com.dd3boh.outertune.constants.XrayProxyEnabledKey
import com.dd3boh.outertune.ui.component.ColumnWithContentPadding
import com.dd3boh.outertune.ui.component.EditTextPreference
import com.dd3boh.outertune.ui.component.FloatingTopBar
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.dialog.InfoLabel
import com.dd3boh.outertune.utils.YouTubeRegion
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.rememberPreference
import com.dd3boh.outertune.utils.xray.XrayConfigParser
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

    val (enabled, onEnabledChange) = rememberPreference(XrayProxyEnabledKey, false)
    val (configInput, onConfigInputChange) = rememberPreference(XrayConfigInputKey, "")
    val (port, onPortChange) = rememberPreference(XrayPortKey, XrayProxyManager.DEFAULT_PORT)

    val state by XrayProxyManager.state.collectAsState()
    val lastError by XrayProxyManager.lastError.collectAsState()
    val activePort by XrayProxyManager.activePort.collectAsState()
    val logs by XrayProxyManager.logs.collectAsState()
    val coreVersion = remember { XrayProxyManager.coreVersion() }
    val summary = remember(configInput) { XrayConfigParser.summarize(configInput) }

    val refreshFailedText = stringResource(R.string.aether_refresh_yt_failed)
    var showLogsDialog by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var isRefreshingYt by remember { mutableStateOf(false) }

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
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = { Text(stringResource(R.string.xray_proxy_title)) },
                description = stringResource(R.string.xray_proxy_desc),
                icon = { Icon(Icons.Rounded.VpnKey, null) },
                checked = enabled,
                onCheckedChange = { isChecked ->
                    testResultText = null
                    if (isChecked) {
                        if (configInput.isBlank()) {
                            Toast.makeText(context, R.string.xray_config_empty_toast, Toast.LENGTH_SHORT).show()
                            return@SwitchPreference
                        }
                        onEnabledChange(true)
                        coroutineScope.launch(Dispatchers.IO) {
                            context.dataStore.edit { it[AetherProxyEnabledKey] = false }
                        }
                        XrayProxyManager.start(context, configInput, port = port, forceRestart = true)
                    } else {
                        onEnabledChange(false)
                        XrayProxyManager.stop()
                    }
                }
            )

            val statusText = when (state) {
                XrayProxyManager.State.CONNECTED ->
                    stringResource(R.string.xray_status_connected, activePort)
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

            testResultText?.let { result ->
                InfoLabel(
                    text = result,
                    isError = result.startsWith("Error") || result.startsWith("Failed")
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
                            XrayProxyManager.start(context, configInput, port = port, forceRestart = true)
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

        PreferenceGroupTitle(title = stringResource(R.string.xray_grp_config))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.xray_paste_clipboard)) },
                description = summary?.let { s ->
                    val name = if (s.remark.isNotBlank()) " (${s.remark})" else ""
                    "${s.protocol} • ${s.server}$name"
                } ?: stringResource(R.string.xray_paste_clipboard_desc),
                icon = { Icon(Icons.Rounded.ContentPaste, null) },
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                    if (clip.isBlank()) {
                        Toast.makeText(context, R.string.xray_clipboard_empty, Toast.LENGTH_SHORT).show()
                    } else {
                        onConfigInputChange(clip)
                        testResultText = null
                        Toast.makeText(context, R.string.xray_config_saved, Toast.LENGTH_SHORT).show()
                        if (enabled) {
                            XrayProxyManager.start(context, clip, port = port, forceRestart = true)
                        }
                    }
                }
            )

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
                        if (enabled && configInput.isNotBlank()) {
                            XrayProxyManager.start(context, configInput, port = p, forceRestart = true)
                        }
                    }
                },
                inputHint = { "10808" }
            )

            if (configInput.isNotBlank()) {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.xray_clear_config)) },
                    icon = { Icon(Icons.Rounded.DeleteSweep, null) },
                    onClick = {
                        onConfigInputChange("")
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

    FloatingTopBar(title = stringResource(R.string.xray_proxy_title), navController = navController)
}

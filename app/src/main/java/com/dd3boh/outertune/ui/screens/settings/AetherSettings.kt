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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.AetherChainModeKey
import com.dd3boh.outertune.constants.AetherCustomPeerKey
import com.dd3boh.outertune.constants.AetherDnsKey
import com.dd3boh.outertune.constants.AetherEchDnsKey
import com.dd3boh.outertune.constants.AetherEchKey
import com.dd3boh.outertune.constants.AetherExitLocKey
import com.dd3boh.outertune.constants.AetherExtraArgsKey
import com.dd3boh.outertune.constants.AetherFragmentDelayKey
import com.dd3boh.outertune.constants.AetherFragmentKey
import com.dd3boh.outertune.constants.AetherFragmentSizeKey
import com.dd3boh.outertune.constants.AetherInnerPeerKey
import com.dd3boh.outertune.constants.AetherIpVersionKey
import com.dd3boh.outertune.constants.AetherMasqueSniKey
import com.dd3boh.outertune.constants.AetherMasqueTransportKey
import com.dd3boh.outertune.constants.AetherNoizeKey
import com.dd3boh.outertune.constants.AetherOuterPeerKey
import com.dd3boh.outertune.constants.AetherPortKey
import com.dd3boh.outertune.constants.AetherProtocolKey
import com.dd3boh.outertune.constants.AetherProxyEnabledKey
import com.dd3boh.outertune.constants.AetherQuicV2Key
import com.dd3boh.outertune.constants.AetherQuickReconnectKey
import com.dd3boh.outertune.constants.AetherScanModeKey
import com.dd3boh.outertune.constants.AetherTeamKey
import com.dd3boh.outertune.constants.AetherTorBridgesModeKey
import com.dd3boh.outertune.constants.AetherTorCustomBridgeKey
import com.dd3boh.outertune.constants.AetherUpstreamKey
import com.dd3boh.outertune.constants.VisitorDataKey
import com.dd3boh.outertune.constants.XrayProxyEnabledKey
import com.dd3boh.outertune.ui.component.ColumnWithContentPadding
import com.dd3boh.outertune.ui.component.EditTextPreference
import com.dd3boh.outertune.ui.component.FloatingTopBar
import com.dd3boh.outertune.ui.component.ListPreference
import com.dd3boh.outertune.ui.component.PreferenceEntry
import com.dd3boh.outertune.ui.component.PreferenceGroupTitle
import com.dd3boh.outertune.ui.component.SettingsClickToReveal
import com.dd3boh.outertune.ui.component.SwitchPreference
import com.dd3boh.outertune.ui.dialog.InfoLabel
import com.dd3boh.outertune.utils.YouTubeRegion
import com.dd3boh.outertune.utils.aether.AetherConfig
import com.dd3boh.outertune.utils.aether.AetherProxyManager
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.rememberPreference
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AetherSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val (enabled, onEnabledChange) = rememberPreference(AetherProxyEnabledKey, false)
    val (savedProtocol, onProtocolChange) = rememberPreference(AetherProtocolKey, "masque")
    val protocol = if (savedProtocol in AetherConfig.VALID_PROTOCOLS) savedProtocol else "masque"
    val (masqueTransport, onMasqueTransportChange) = rememberPreference(AetherMasqueTransportKey, "h2")
    val (savedChainMode, onChainModeChange) = rememberPreference(AetherChainModeKey, "tor")
    val chainMode = if (savedChainMode in AetherConfig.VALID_CHAINS) savedChainMode else "tor"
    val (scanMode, onScanModeChange) = rememberPreference(AetherScanModeKey, "ironclad")
    val (ipVersion, onIpVersionChange) = rememberPreference(AetherIpVersionKey, "v4")
    val (noize, onNoizeChange) = rememberPreference(AetherNoizeKey, "default")
    val (quickReconnect, onQuickReconnectChange) = rememberPreference(AetherQuickReconnectKey, true)
    val (fragment, onFragmentChange) = rememberPreference(AetherFragmentKey, false)
    val (fragmentSize, onFragmentSizeChange) = rememberPreference(AetherFragmentSizeKey, "")
    val (fragmentDelay, onFragmentDelayChange) = rememberPreference(AetherFragmentDelayKey, "")
    val (quicV2, onQuicV2Change) = rememberPreference(AetherQuicV2Key, true)
    val (masqueSni, onMasqueSniChange) = rememberPreference(AetherMasqueSniKey, "")
    val (ech, onEchChange) = rememberPreference(AetherEchKey, "off")
    val (echDns, onEchDnsChange) = rememberPreference(AetherEchDnsKey, "")
    val (exitLoc, onExitLocChange) = rememberPreference(AetherExitLocKey, "")
    val (torBridgesMode, onTorBridgesModeChange) = rememberPreference(AetherTorBridgesModeKey, "auto")
    val (torCustomBridge, onTorCustomBridgeChange) = rememberPreference(AetherTorCustomBridgeKey, "")
    val (customPeer, onCustomPeerChange) = rememberPreference(AetherCustomPeerKey, "")
    val (outerPeer, onOuterPeerChange) = rememberPreference(AetherOuterPeerKey, "")
    val (innerPeer, onInnerPeerChange) = rememberPreference(AetherInnerPeerKey, "")
    val (dns, onDnsChange) = rememberPreference(AetherDnsKey, "")
    val (upstream, onUpstreamChange) = rememberPreference(AetherUpstreamKey, "")
    val (team, onTeamChange) = rememberPreference(AetherTeamKey, "")
    val (extraArgs, onExtraArgsChange) = rememberPreference(AetherExtraArgsKey, "")
    val (port, onPortChange) = rememberPreference(AetherPortKey, AetherProxyManager.DEFAULT_PORT)

    val currentConfig = AetherConfig(
        protocol = protocol,
        masqueTransport = masqueTransport,
        chainMode = chainMode,
        scanMode = scanMode,
        ipVersion = ipVersion,
        noize = noize,
        quickReconnect = quickReconnect,
        fragment = fragment,
        fragmentSize = fragmentSize,
        fragmentDelay = fragmentDelay,
        quicV2 = quicV2,
        masqueSni = masqueSni,
        ech = ech,
        echDns = echDns,
        exitLoc = exitLoc,
        torBridgesMode = torBridgesMode,
        torCustomBridge = torCustomBridge,
        customPeer = customPeer,
        outerPeer = outerPeer,
        innerPeer = innerPeer,
        dns = dns,
        upstream = upstream,
        team = team,
        extraArgs = extraArgs,
        port = port
    )

    fun restartIfEnabled(updated: AetherConfig) {
        if (enabled) {
            AetherProxyManager.start(context, updated, forceRestart = true)
        }
    }

    val state by AetherProxyManager.state.collectAsState()
    val lastError by AetherProxyManager.lastError.collectAsState()
    val statusDetail by AetherProxyManager.statusDetail.collectAsState()
    val activePort by AetherProxyManager.activePort.collectAsState()
    val logs by AetherProxyManager.logs.collectAsState()

    val refreshFailedText = stringResource(R.string.aether_refresh_yt_failed)
    var showLogsDialog by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var isRefreshingYt by remember { mutableStateOf(false) }

    fun applyPreset(
        pProtocol: String,
        pTransport: String = "h2",
        pChain: String = "tor",
        pScan: String = "ironclad",
        pNoize: String = "default",
        pFragment: Boolean = false,
        pExitLoc: String = "",
    ) {
        onProtocolChange(pProtocol)
        onMasqueTransportChange(pTransport)
        onChainModeChange(pChain)
        onScanModeChange(pScan)
        onNoizeChange(pNoize)
        onFragmentChange(pFragment)
        onExitLocChange(pExitLoc)
        testResultText = null
        val updated = currentConfig.copy(
            protocol = pProtocol,
            masqueTransport = pTransport,
            chainMode = pChain,
            scanMode = pScan,
            noize = pNoize,
            fragment = pFragment,
            exitLoc = pExitLoc,
        )
        Toast.makeText(context, R.string.aether_preset_applied, Toast.LENGTH_SHORT).show()
        if (enabled) {
            AetherProxyManager.start(context, updated, forceRestart = true)
        }
    }

    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            title = { Text(stringResource(R.string.aether_logs_title)) },
            text = {
                val logScroll = rememberScrollState()
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Aether Logs", logs.joinToString("\n")))
                    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                }) {
                    Text(stringResource(R.string.aether_copy_logs))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    AetherProxyManager.clearLogs()
                }) {
                    Text(stringResource(R.string.aether_clear_logs))
                }
            }
        )
    }

    ColumnWithContentPadding(
        modifier = Modifier.fillMaxHeight(),
        columnModifier = Modifier.padding(horizontal = 16.dp)
    ) {
        // 1. Master Switch & Status Card
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = { Text(stringResource(R.string.aether_proxy_title)) },
                description = stringResource(R.string.aether_proxy_desc),
                icon = { Icon(Icons.Rounded.VpnLock, null) },
                checked = enabled,
                onCheckedChange = { isChecked ->
                    onEnabledChange(isChecked)
                    testResultText = null
                    if (isChecked) {
                        coroutineScope.launch(Dispatchers.IO) {
                            context.dataStore.edit { it[XrayProxyEnabledKey] = false }
                        }
                        AetherProxyManager.start(context, currentConfig, forceRestart = true)
                    } else {
                        AetherProxyManager.stop()
                    }
                }
            )

            val statusText = when (state) {
                AetherProxyManager.State.CONNECTED ->
                    stringResource(R.string.aether_status_connected, activePort)
                AetherProxyManager.State.STARTING ->
                    statusDetail?.let { "${stringResource(R.string.aether_status_connecting)} — $it" }
                        ?: stringResource(R.string.aether_status_connecting)
                AetherProxyManager.State.ERROR ->
                    lastError ?: "Error"
                AetherProxyManager.State.STOPPED ->
                    stringResource(R.string.aether_status_stopped)
            }

            InfoLabel(
                text = statusText,
                isError = state == AetherProxyManager.State.ERROR
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
                        description = stringResource(R.string.aether_reconnect_desc),
                        icon = { Icon(Icons.Rounded.Refresh, null) },
                        onClick = {
                            testResultText = null
                            AetherProxyManager.start(context, currentConfig, forceRestart = true)
                        }
                    )

                    PreferenceEntry(
                        title = { Text(stringResource(R.string.aether_test_connection)) },
                        description = if (isTesting) stringResource(R.string.aether_testing)
                        else stringResource(R.string.aether_test_connection_desc),
                        icon = { Icon(Icons.Rounded.Speed, null) },
                        isEnabled = state == AetherProxyManager.State.CONNECTED && !isTesting,
                        onClick = {
                            isTesting = true
                            testResultText = null
                            coroutineScope.launch {
                                val res = AetherProxyManager.testConnection(activePort)
                                testResultText = res.fold(
                                    onSuccess = { r ->
                                        "OK • IP: ${r.ip} • Country: ${r.country} • WARP: ${r.warp} • ${r.latencyMs} ms"
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
                        isEnabled = state == AetherProxyManager.State.CONNECTED && !isRefreshingYt,
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
                title = { Text(stringResource(R.string.aether_logs_title)) },
                description = stringResource(R.string.aether_logs_desc, logs.size),
                icon = { Icon(Icons.Rounded.Terminal, null) },
                onClick = { showLogsDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1.5 Quick Presets (1-tap)
        PreferenceGroupTitle(title = stringResource(R.string.aether_grp_presets))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.aether_preset_tor_title)) },
                description = stringResource(R.string.aether_preset_tor_desc),
                icon = { Icon(Icons.Rounded.AutoAwesome, null) },
                onClick = {
                    applyPreset(
                        pProtocol = "masque",
                        pTransport = "h2",
                        pChain = "tor",
                        pScan = "ironclad",
                        pNoize = "default",
                        pFragment = false,
                        pExitLoc = "",
                    )
                }
            )
            PreferenceEntry(
                title = { Text(stringResource(R.string.aether_preset_fast_title)) },
                description = stringResource(R.string.aether_preset_fast_desc),
                icon = { Icon(Icons.Rounded.Speed, null) },
                onClick = {
                    applyPreset(
                        pProtocol = "masque",
                        pTransport = "h3",
                        pChain = "none",
                        pScan = "turbo",
                        pNoize = "default",
                        pFragment = false,
                        pExitLoc = "",
                    )
                }
            )
            PreferenceEntry(
                title = { Text(stringResource(R.string.aether_preset_ytm_title)) },
                description = stringResource(R.string.aether_preset_ytm_desc),
                icon = { Icon(Icons.Rounded.Public, null) },
                onClick = {
                    applyPreset(
                        pProtocol = "gool",
                        pTransport = "h3",
                        pChain = "none",
                        pScan = "balanced",
                        pNoize = "default",
                        pFragment = false,
                        pExitLoc = "",
                    )
                }
            )
            PreferenceEntry(
                title = { Text(stringResource(R.string.aether_preset_dpi_title)) },
                description = stringResource(R.string.aether_preset_dpi_desc),
                icon = { Icon(Icons.Rounded.Security, null) },
                onClick = {
                    applyPreset(
                        pProtocol = "masque",
                        pTransport = "h2",
                        pChain = "none",
                        pScan = "balanced",
                        pNoize = "gfw",
                        pFragment = true,
                        pExitLoc = "",
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Protocol & Connection Type
        PreferenceGroupTitle(title = stringResource(R.string.aether_grp_connection))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            ListPreference(
                title = { Text(stringResource(R.string.aether_protocol)) },
                icon = { Icon(Icons.Rounded.Public, null) },
                selectedValue = protocol,
                values = listOf("masque", "wg", "gool", "gool-classic", "mim", "tor-only"),
                valueText = {
                    when (it) {
                        "masque" -> "MASQUE (HTTP/2 TLS / HTTP/3 QUIC)"
                        "wg" -> "WireGuard (WARP)"
                        "gool" -> "Gool (WireGuard over MASQUE)"
                        "gool-classic" -> "Gool Classic (WARP-in-WARP)"
                        "mim" -> "MiM (MASQUE-in-MASQUE)"
                        "tor-only" -> "Tor Only (Direct / Bridges)"
                        else -> it
                    }
                },
                onValueSelected = { proto ->
                    onProtocolChange(proto)
                    restartIfEnabled(currentConfig.copy(protocol = proto))
                }
            )

            AnimatedVisibility(currentConfig.usesMasque) {
                ListPreference(
                    title = { Text(stringResource(R.string.aether_masque_transport)) },
                    selectedValue = masqueTransport,
                    values = listOf("h2", "h3"),
                    valueText = {
                        when (it) {
                            "h2" -> "HTTP/2 (TLS / TCP)"
                            "h3" -> "HTTP/3 (QUIC / UDP)"
                            else -> it
                        }
                    },
                    onValueSelected = { t ->
                        onMasqueTransportChange(t)
                        restartIfEnabled(currentConfig.copy(masqueTransport = t))
                    }
                )
            }

            AnimatedVisibility(!currentConfig.isStandaloneTor) {
                Column {
                    ListPreference(
                        title = { Text(stringResource(R.string.aether_chain_mode)) },
                        selectedValue = chainMode,
                        values = listOf("none", "tor", "tor-reverse"),
                        valueText = {
                            when (it) {
                                "none" -> "Direct (No Tor chain)"
                                "tor" -> "MASQUE/WARP → Tor (--tor)"
                                "tor-reverse" -> "Tor → WARP (--tor-reverse, H2)"
                                else -> it
                            }
                        },
                        onValueSelected = { chain ->
                            onChainModeChange(chain)
                            restartIfEnabled(currentConfig.copy(chainMode = chain))
                        }
                    )

                    if (chainMode == "tor-reverse" && (protocol == "wg" || protocol == "gool-classic")) {
                        InfoLabel(
                            text = stringResource(R.string.aether_reverse_tcp_note),
                            isError = false
                        )
                    }
                }
            }

            val exitLocHint = stringResource(R.string.aether_exit_loc_hint)
            EditTextPreference(
                title = { Text(stringResource(R.string.aether_exit_loc)) },
                value = exitLoc,
                isInputValid = { true },
                onValueChange = { loc ->
                    onExitLocChange(loc)
                    restartIfEnabled(currentConfig.copy(exitLoc = loc))
                },
                inputHint = { exitLocHint }
            )
        }

        // 3. Scanning & Discovery
        AnimatedVisibility(!currentConfig.isStandaloneTor) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                PreferenceGroupTitle(title = stringResource(R.string.aether_grp_scanning))
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    ListPreference(
                        title = { Text(stringResource(R.string.aether_scan_mode)) },
                        selectedValue = scanMode,
                        values = listOf("ironclad", "turbo", "balanced", "thorough", "verified"),
                        valueText = {
                            when (it) {
                                "ironclad" -> "Ironclad (Full HTTP test)"
                                "turbo" -> "Turbo (First responding)"
                                "balanced" -> "Balanced (Fastest of several)"
                                "thorough" -> "Thorough (Sweep full ranges)"
                                "verified" -> "Verified (Connect-IP edges)"
                                else -> it
                            }
                        },
                        onValueSelected = { mode ->
                            onScanModeChange(mode)
                            restartIfEnabled(currentConfig.copy(scanMode = mode))
                        }
                    )

                    ListPreference(
                        title = { Text(stringResource(R.string.aether_ip_version)) },
                        selectedValue = ipVersion,
                        values = listOf("v4", "v6", "both"),
                        valueText = {
                            when (it) {
                                "v4" -> "IPv4 (-4)"
                                "v6" -> "IPv6 (-6)"
                                "both" -> "Dual Stack IPv4 + IPv6 (--dual)"
                                else -> it
                            }
                        },
                        onValueSelected = { v ->
                            onIpVersionChange(v)
                            restartIfEnabled(currentConfig.copy(ipVersion = v))
                        }
                    )

                    SwitchPreference(
                        title = { Text(stringResource(R.string.aether_quick_reconnect)) },
                        description = stringResource(R.string.aether_quick_reconnect_desc),
                        checked = quickReconnect,
                        onCheckedChange = { qr ->
                            onQuickReconnectChange(qr)
                            restartIfEnabled(currentConfig.copy(quickReconnect = qr))
                        }
                    )
                }

                // 4. Anti-Censorship & Obfuscation
                Spacer(modifier = Modifier.height(16.dp))
                PreferenceGroupTitle(title = stringResource(R.string.aether_grp_obfuscation))
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    ListPreference(
                        title = { Text(stringResource(R.string.aether_noize)) },
                        icon = { Icon(Icons.Rounded.Security, null) },
                        selectedValue = noize,
                        values = listOf("default", "off", "light", "firewall", "balanced", "gfw", "aggressive"),
                        valueText = {
                            when (it) {
                                "default" -> "Default (Auto)"
                                "off" -> "Off"
                                "light" -> "Light"
                                "firewall" -> "Firewall (MASQUE default)"
                                "balanced" -> "Balanced (WG default)"
                                "gfw" -> "GFW (Strict DPI)"
                                "aggressive" -> "Aggressive"
                                else -> it
                            }
                        },
                        onValueSelected = { n ->
                            onNoizeChange(n)
                            restartIfEnabled(currentConfig.copy(noize = n))
                        }
                    )

                    SwitchPreference(
                        title = { Text(stringResource(R.string.aether_fragment)) },
                        description = stringResource(R.string.aether_fragment_desc),
                        checked = fragment,
                        onCheckedChange = { f ->
                            onFragmentChange(f)
                            restartIfEnabled(currentConfig.copy(fragment = f))
                        }
                    )

                    AnimatedVisibility(fragment) {
                        Column {
                            EditTextPreference(
                                title = { Text(stringResource(R.string.aether_fragment_size)) },
                                value = fragmentSize,
                                isInputValid = { true },
                                onValueChange = { s ->
                                    onFragmentSizeChange(s)
                                    restartIfEnabled(currentConfig.copy(fragmentSize = s))
                                },
                                inputHint = { "8-16" }
                            )
                            EditTextPreference(
                                title = { Text(stringResource(R.string.aether_fragment_delay)) },
                                value = fragmentDelay,
                                isInputValid = { true },
                                onValueChange = { d ->
                                    onFragmentDelayChange(d)
                                    restartIfEnabled(currentConfig.copy(fragmentDelay = d))
                                },
                                inputHint = { "2-10" }
                            )
                        }
                    }

                    AnimatedVisibility(currentConfig.usesMasque) {
                        Column {
                            SwitchPreference(
                                title = { Text(stringResource(R.string.aether_quic_v2)) },
                                description = stringResource(R.string.aether_quic_v2_desc),
                                checked = quicV2,
                                onCheckedChange = { q ->
                                    onQuicV2Change(q)
                                    restartIfEnabled(currentConfig.copy(quicV2 = q))
                                }
                            )

                            EditTextPreference(
                                title = { Text(stringResource(R.string.aether_masque_sni)) },
                                value = masqueSni,
                                isInputValid = { true },
                                onValueChange = { sni ->
                                    onMasqueSniChange(sni)
                                    restartIfEnabled(currentConfig.copy(masqueSni = sni))
                                },
                                inputHint = { "www.cloudflare.com" }
                            )
                        }
                    }

                    ListPreference(
                        title = { Text(stringResource(R.string.aether_ech)) },
                        selectedValue = if (ech == "off" || ech == "auto") ech else "auto",
                        values = listOf("off", "auto"),
                        valueText = {
                            when (it) {
                                "off" -> "Off"
                                "auto" -> "Auto (Encrypted Client Hello)"
                                else -> it
                            }
                        },
                        onValueSelected = { e ->
                            onEchChange(e)
                            restartIfEnabled(currentConfig.copy(ech = e))
                        }
                    )

                    AnimatedVisibility(ech != "off") {
                        EditTextPreference(
                            title = { Text(stringResource(R.string.aether_ech_dns)) },
                            value = echDns,
                            isInputValid = { true },
                            onValueChange = { d ->
                                onEchDnsChange(d)
                                restartIfEnabled(currentConfig.copy(echDns = d))
                            },
                            inputHint = { "udp://1.1.1.1 or https://1.1.1.1/dns-query" }
                        )
                    }
                }
            }
        }

        // 5. Tor Settings
        AnimatedVisibility(currentConfig.usesTor) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                PreferenceGroupTitle(title = stringResource(R.string.aether_grp_tor))
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    ListPreference(
                        title = { Text(stringResource(R.string.aether_tor_bridges)) },
                        selectedValue = torBridgesMode,
                        values = listOf("auto", "force", "off"),
                        valueText = {
                            when (it) {
                                "auto" -> "Auto (Fetch bridges if Tor is blocked)"
                                "force" -> "Always use bridges (--tor-bridges)"
                                "off" -> "Never use bridges (--no-tor-bridges)"
                                else -> it
                            }
                        },
                        onValueSelected = { m ->
                            onTorBridgesModeChange(m)
                            restartIfEnabled(currentConfig.copy(torBridgesMode = m))
                        }
                    )

                    EditTextPreference(
                        title = { Text(stringResource(R.string.aether_tor_custom_bridge)) },
                        value = torCustomBridge,
                        singleLine = false,
                        isInputValid = { true },
                        onValueChange = { b ->
                            onTorCustomBridgeChange(b)
                            restartIfEnabled(currentConfig.copy(torCustomBridge = b))
                        },
                        inputHint = { "obfs4 1.2.3.4:443 FINGERPRINT cert=... iat-mode=0" }
                    )
                }
            }
        }

        // 6. Advanced & Custom Endpoints
        SettingsClickToReveal(stringResource(R.string.advanced)) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                if (protocol == "masque" || protocol == "wg" || protocol == "gool") {
                    EditTextPreference(
                        title = { Text(stringResource(R.string.aether_custom_peer)) },
                        value = customPeer,
                        isInputValid = { true },
                        onValueChange = { p ->
                            onCustomPeerChange(p)
                            restartIfEnabled(currentConfig.copy(customPeer = p))
                        },
                        inputHint = { "162.159.192.1:2408" }
                    )
                }

                if (protocol == "gool-classic" || protocol == "mim") {
                    EditTextPreference(
                        title = { Text(stringResource(R.string.aether_outer_peer)) },
                        value = outerPeer,
                        isInputValid = { true },
                        onValueChange = { p ->
                            onOuterPeerChange(p)
                            restartIfEnabled(currentConfig.copy(outerPeer = p))
                        },
                        inputHint = { "162.159.192.1:2408" }
                    )
                }

                if (protocol == "gool" || protocol == "gool-classic" || protocol == "mim") {
                    EditTextPreference(
                        title = { Text(stringResource(R.string.aether_inner_peer)) },
                        value = innerPeer,
                        isInputValid = { true },
                        onValueChange = { p ->
                            onInnerPeerChange(p)
                            restartIfEnabled(currentConfig.copy(innerPeer = p))
                        },
                        inputHint = { "188.114.96.1:2408" }
                    )
                }

                EditTextPreference(
                    title = { Text(stringResource(R.string.aether_port)) },
                    icon = { Icon(Icons.Rounded.Tune, null) },
                    value = port.toString(),
                    isInputValid = { it.toIntOrNull()?.let { p -> p in 1024..65530 } == true },
                    onValueChange = { pStr ->
                        pStr.toIntOrNull()?.let { p ->
                            onPortChange(p)
                            restartIfEnabled(currentConfig.copy(port = p))
                        }
                    },
                    inputHint = { "1819" }
                )

                EditTextPreference(
                    title = { Text(stringResource(R.string.aether_dns)) },
                    icon = { Icon(Icons.Rounded.Dns, null) },
                    value = dns,
                    isInputValid = { true },
                    onValueChange = { d ->
                        onDnsChange(d)
                        restartIfEnabled(currentConfig.copy(dns = d))
                    },
                    inputHint = { "1.1.1.1,1.0.0.1" }
                )

                EditTextPreference(
                    title = { Text(stringResource(R.string.aether_upstream)) },
                    value = upstream,
                    isInputValid = { true },
                    onValueChange = { u ->
                        onUpstreamChange(u)
                        restartIfEnabled(currentConfig.copy(upstream = u))
                    },
                    inputHint = { "socks5://127.0.0.1:1080" }
                )

                EditTextPreference(
                    title = { Text(stringResource(R.string.aether_team)) },
                    value = team,
                    isInputValid = { true },
                    onValueChange = { t ->
                        onTeamChange(t)
                        restartIfEnabled(currentConfig.copy(team = t))
                    },
                    inputHint = { "Zero Trust team name" }
                )

                EditTextPreference(
                    title = { Text(stringResource(R.string.aether_extra_args)) },
                    value = extraArgs,
                    isInputValid = { true },
                    onValueChange = { a ->
                        onExtraArgsChange(a)
                        restartIfEnabled(currentConfig.copy(extraArgs = a))
                    },
                    inputHint = { "--log-level debug" }
                )

                PreferenceEntry(
                    title = { Text(stringResource(R.string.aether_reset_identity)) },
                    description = stringResource(R.string.aether_reset_identity_desc),
                    icon = { Icon(Icons.Rounded.DeleteSweep, null) },
                    onClick = {
                        AetherProxyManager.resetIdentity(context)
                        coroutineScope.launch(Dispatchers.IO) {
                            context.dataStore.edit { it.remove(VisitorDataKey) }
                            YouTube.visitorData = null
                        }
                        Toast.makeText(context, R.string.aether_reset_identity_done, Toast.LENGTH_SHORT).show()
                        if (enabled) {
                            AetherProxyManager.start(context, currentConfig, forceRestart = true)
                        }
                    }
                )
            }
        }
    }

    FloatingTopBar(title = stringResource(R.string.aether_proxy_title), navController = navController)
}

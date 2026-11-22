<div align="center">

<img src="assets/kltune.svg" width="128" height="128" alt="KLtune">

# KLtune

**English** | [فارسی](README.fa.md)

### YouTube Music & local music player for Android — with built-in Aether + Tor and Xray proxy engines

[![Release](https://img.shields.io/github/v/release/Kolandone/InterTune?label=release&color=E51928&labelColor=131318)](https://github.com/Kolandone/InterTune/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Kolandone/InterTune/total?color=E51928&labelColor=131318)](https://github.com/Kolandone/InterTune/releases)
[![Telegram](https://img.shields.io/badge/Telegram-@kolandjs1-26A5E4?logo=telegram&logoColor=white&labelColor=131318)](https://t.me/kolandjs1)
[![License](https://img.shields.io/badge/license-GPL--3.0-E51928?labelColor=131318)](LICENSE)

[Download](#download) · [Features](#features) · [Community & Support](#community--support) · [Credits & License](#credits--license)

</div>

---

**KLtune** is a free, open-source Android client for YouTube Music and your local audio library, forked from **[InterTune](https://github.com/ItzSkyeYT/InterTune)** (which is based on [OuterTune](https://github.com/OuterTune/OuterTune) and [InnerTune](https://github.com/z-huang/InnerTune)).

It preserves the complete InterTune experience—unified online/local playback, synced lyrics, offline downloads, Smart Recommendations, Android Auto, Equalizer, and Cast/DLNA—while adding self-contained censorship circumvention directly inside the app so you don't need an external VPN app to listen smoothly.

---

## Features

### Music & Audio
- Ad-free YouTube Music streaming combined with your device's local audio folders in one unified queue
- Offline caching and background downloads with synced lyrics
- 10-band equalizer, spatial audio, loudness normalization, and playback transition fade
- On-device Quick Picks & recommendations, song recognition, Cast / DLNA support, and Android Auto

### Built-in Aether + Tor Engine
- **No external VPN required:** routes YouTube Music, streams, artwork, and Google sign-in WebView inside the app
- **Default ready-to-use profile:** `MASQUE H2 → Tor` with the `Ironclad` scanner for reliable non-restricted exits
- Full protocol & circumvention suite: `MASQUE` (HTTP/2 & HTTP/3), `WireGuard`, `Gool` (WG-over-MASQUE), `Gool Classic`, `MiM` (MASQUE-in-MASQUE), and `Tor` (direct or chained)
- Advanced controls: `Noize` DPI obfuscation profiles, TLS ClientHello Fragmentation, QUIC v2 opener, Custom SNI, Encrypted Client Hello (ECH), exit-country filter, custom peers, live logs, and built-in Cloudflare trace connection test

### Built-in Xray Core
- Run your own proxy configuration directly inside KLtune without any extra app
- Supports `vless://` (TLS & REALITY), `vmess://`, `trojan://`, `ss://` (Shadowsocks), and full Xray JSON configs
- **Subscription manager:** import, update, and manage multiple subscription URLs (Base64 or plain URI lists)
- **Real-delay testing & Auto-select:** measures concurrent end-to-end HTTP delay through Xray core, sorts servers by latency, and automatically connects to the fastest working server

### App Experience 
- **English & Persian (RTL):** switch between System Default, English, and Persian right inside **Settings**, with full right-to-left layout support for Persian and clean LTR handling for technical values

---

## Download

Get the latest APK directly from **[GitHub Releases](https://github.com/Kolandone/InterTune/releases/latest)**:

| Architecture | File suffix | Recommended for |
| :--- | :--- | :--- |
| **`arm64-v8a`** | `*-arm64-v8a-release-*.apk` | Almost all modern Android phones (64-bit ARM) |
| **`armeabi-v7a`** | `*-armeabi-v7a-release-*.apk` | Older 32-bit Android phones |
| **`x86_64`** | `*-x86_64-release-*.apk` | Android emulators and x86_64 tablets/PCs |

> Android 7.0+ is supported. All releases are signed with a consistent release key so updates install directly over previous KLtune releases without uninstalling.

---

## Community & Support

- **Telegram Channel:** [@kolandjs1](https://t.me/kolandjs1)

If KLtune helps you listen without limits, you can also support development using the in-app heart button or any address below:

| Network | Address |
| :--- | :--- |
| **USDT (TRC20)** | `TJj1J8keFZBUrwL6vncrLXZzE1QveFohhM` |
| **USDT (BEP20)** | `0x16789E769B729032A9FB361b725246DE1FB4B584` |
| **TON (GRAM)** | `UQCczkKrpZaQ5url-2XBmX4Xa_6NPrdhKRYuwlZHcSPfoZVm` |

---

## Credits & License

- **Upstream fork base:** [ItzSkyeYT/InterTune](https://github.com/ItzSkyeYT/InterTune)
- **Original ancestors:** [OuterTune](https://github.com/OuterTune/OuterTune) and [InnerTune](https://github.com/z-huang/InnerTune)
- **Built-in Aether engine:** [CluvexStudio/Aether](https://github.com/CluvexStudio/Aether)
- Licensed under the **GNU General Public License v3.0 (GPL-3.0)** — see [LICENSE](LICENSE) for details.

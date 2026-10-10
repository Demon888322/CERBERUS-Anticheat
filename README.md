<div align="center">

# CERBERUS

### A configurable, server-side anti-cheat for Paper

[![Build](https://img.shields.io/github/actions/workflow/status/Demon888322/CERBERUS-Anticheat/build.yml?branch=main&label=build)](https://github.com/Demon888322/CERBERUS-Anticheat/actions/workflows/build.yml)
[![Minecraft](https://img.shields.io/badge/Minecraft-Paper%201.20.4-orange)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-17-blue)](https://adoptium.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

CERBERUS combines packet observations and Paper gameplay events to detect
suspicious behavior, stop certain out-of-range actions, and give staff
configurable violation alerts.

[![Download latest JAR](https://img.shields.io/badge/Download-Latest%20JAR-brightgreen?logo=github)](https://github.com/Demon888322/CERBERUS-Anticheat/releases/latest/download/CERBERUS-1.0.0.jar)

[Features](#features) · [Installation](#installation) · [Checks](#checks)
· [Configuration](#configuration) · [Commands](#commands) · [Limitations](#important-limitations)

</div>

---

## Features

- **Packet-aware client identifiers** using PacketEvents and configurable
  brand/channel matching.
- **Reach protection** that cancels selected out-of-range block interactions
  and entity attacks.
- **Movement heuristics** for unusually fast horizontal movement and sustained
  flight-like movement.
- **Inventory and combat heuristics** for rapid AutoTotem restocking and
  sustained high arm-swing rates.
- **X-ray mining-pattern alerts** for repeated, closely spaced finds of
  concealed high-value ores.
- **Violation tracking and staff alerts**, with configurable thresholds and
  automatic violation decay.
- **Built-in 24-hour temporary bans** for established checks, with opt-in
  automatic bans for the experimental AutoClicker and Xray checks.
- **Live configuration reload** through `/cerberus reload`.

## Compatibility

| Component | Target |
| --- | --- |
| Server API | Paper 1.20.4 |
| Java | 17 |
| PacketEvents | 2.7.0, bundled and relocated in the plugin JAR |

The Paper API version is the project's compile target. Other server or
Minecraft versions have not been verified and may not be compatible.

## Installation

### Download a release

Download the latest ready-to-install
[CERBERUS JAR from GitHub Releases](https://github.com/Demon888322/CERBERUS-Anticheat/releases/latest/download/CERBERUS-1.0.0.jar).
You do not need Maven to install a release.

### Build from source

Requirements: JDK 17 or later, Maven 3.9 or later, and network access to the
Paper and CodeMC Maven repositories.

```shell
git clone https://github.com/Demon888322/CERBERUS-Anticheat.git
cd CERBERUS-Anticheat
mvn --batch-mode clean verify
```

The shaded plugin is created at `target/CERBERUS-1.0.0.jar`.

### Install on Paper

1. Back up your server and test the plugin on a staging server first.
2. Download the release JAR above, or build it from source.
3. Copy `CERBERUS-1.0.0.jar` into the server's `plugins/` directory.
4. Start the server once to create `plugins/CERBERUS/config.yml`.
5. Review the configuration and staff alerts; tune checks for your server.
6. Restart the server and confirm CERBERUS appears in `/plugins`.

When upgrading, CERBERUS merges newly shipped client identifiers into the
existing blacklist while preserving custom entries. Restart the server when
installing or updating the JAR; do not use `/reload` or plugin-reload tools.

## Checks

| Check | What it observes | Default response |
| --- | --- | --- |
| Client identifier | Configured substrings in reported client-brand payloads, plugin-message channels, and registered channel lists. | 24-hour temporary ban on a match. |
| Reach | Out-of-range block damage, breaking, right-click interactions, placement/entity interactions, and direct entity attacks. | Cancels detected actions; 24-hour ban at VL 10. |
| Movement | Heuristics for excessive horizontal movement and sustained flight-like movement. | 24-hour ban at VL 6. |
| AutoTotem | Repeated offhand totem replenishments measured below the configured timing threshold. | 24-hour ban at VL 5. |
| AutoClicker | At least 18 arm swings per second across six consecutive one-second windows. | Staff alert at VL 8; auto-ban is opt-in. |
| Xray | Repeated, closely spaced finds of concealed diamond, emerald, or ancient-debris ore groups. | Staff alert at VL 6; auto-ban is opt-in. |

Violation levels decay by 1 every 30 seconds by default. Each check's
threshold, enablement, and related heuristic settings can be adjusted in
`config.yml`.

## Configuration

The generated `plugins/CERBERUS/config.yml` is the canonical, commented
configuration. Use `/cerberus reload` after changing runtime settings.

| Setting | Default | Purpose |
| --- | --- | --- |
| `checks.brand.enabled` / `checks.brand.blacklist` | `true` / configured list | Toggle and match reported brand/channel identifiers. |
| `checks.autototem.minimum-restock-ms` | `100` | Minimum observed restock timing in milliseconds. |
| `checks.reach.maximum-block-distance` | `5.0` | Maximum distance to the nearest point on a block. |
| `checks.reach.maximum-distance` | `5.2` | Maximum distance for block placement and entity interactions. |
| `checks.reach.maximum-entity-distance` | `3.5` | Maximum distance to the nearest point on a target hitbox for attacks. |
| `checks.movement.max-horizontal-per-tick` | `0.75` | Horizontal movement allowance used by the speed heuristic. |
| `checks.movement.flight-air-ticks` / `flight-max-rise` | `12` / `1.8` | Air-time and height settings for the flight-like heuristic. |
| `checks.xray.minimum-rare-ore-groups` | `4` | Distinct high-value ore groups before suspicious mining counts. |
| `checks.xray.max-blocks-between-ores` | `24` | Maximum solid blocks mined between qualifying ore groups. |
| `checks.autoclicker.minimum-swings-per-second` | `18` | Minimum swings in a complete one-second window. |
| `checks.autoclicker.consecutive-seconds` | `6` | Consecutive high-rate windows required for an AutoClicker violation. |
| `checks.autoclicker.auto-ban-enabled` | `false` | Opt in to a 24-hour AutoClicker ban after validating on your server. |
| `checks.xray.auto-ban-enabled` | `false` | Opt in to a 24-hour Xray ban after validating on your server. |
| `checks.decay.amount` / `interval-seconds` | `1.0` / `30` | Violation decay amount and interval. Set amount to `0` to disable decay. |

Established checks use a fixed `1d` temporary ban when their configured
threshold is reached. AutoClicker and Xray alert staff at their thresholds by
default; enable their `auto-ban-enabled` options only after observing and
validating alerts on your server.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/cerberus` or `/cerberus help` | Show help. | `cerberus.admin` |
| `/cerberus info` | Show check status and punishment duration. | `cerberus.admin` |
| `/cerberus reload` | Reload the configuration. | `cerberus.admin` |
| `/cerberustempban <player> <duration> <reason>` | Apply a timed name ban; console only. | `cerberus.admin` |

`/cerb` is an alias for `/cerberus`. The `cerberus.admin` permission defaults
to operators and receives staff alerts. The temporary-ban command accepts a
positive integer and a unit: `s`, `m`, `h`, or `d` (for example, `30m`, `12h`,
or `1d`).

## Important limitations

Anti-cheat detections are heuristics, not proof. **A server plugin cannot scan
a player's installed client files or reliably identify every installed mod.**
Client brand and channel data are supplied by the client and can be spoofed,
hidden, or omitted.

Movement, reach, mining-pattern, inventory-timing, and arm-swing checks can be
affected by latency, server tick load, plugins, game mechanics, and player
behavior. Xray and AutoClicker checks are experimental; review their alerts
before enabling automatic bans. CERBERUS does not claim feature parity with
Grim, Vulcan, Themis, or any other anti-cheat.

Start on a staging server, monitor staff alerts under real conditions, and tune
or disable checks that produce false positives. No anti-cheat can guarantee
detection of every cheat or provide a false-positive-free result.

## Troubleshooting

- **CERBERUS is missing from `/plugins`:** verify Paper is running and the JAR
  is in the active server's `plugins/` directory.
- **The plugin fails to enable:** check `logs/latest.log` for the first
  CERBERUS or PacketEvents error and verify the server's Java compatibility.
- **The build cannot resolve dependencies:** verify JDK/Maven requirements and
  network access to the Paper and CodeMC repositories.
- **A ban was not applied:** check that `cerberustempban` is registered and
  inspect the console for a command-dispatch error.
- **Alerts seem incorrect:** review the relevant threshold and exemption
  settings, then test under normal latency, tick load, and plugin conditions.

## Contributing and security

Contributions and reproducible bug reports are welcome. See
[CONTRIBUTING.md](CONTRIBUTING.md) and the
[bug report template](.github/ISSUE_TEMPLATE/bug_report.md). Include the
CERBERUS version, Paper build, Java version, relevant redacted logs, and steps
to reproduce. Do not post player IP addresses, credentials, or other private
information.

For suspected security vulnerabilities, do not publish exploit details in a
public issue. Contact the maintainers privately through a channel they have
published.

## License

CERBERUS is distributed under the [MIT License](LICENSE).

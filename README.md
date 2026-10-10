# CERBERUS

CERBERUS is a modular anti-cheat plugin for Paper servers. It uses PacketEvents
for inbound plugin messages and Paper events for gameplay checks. PacketEvents
is bundled in the release JAR; do not install a separate PacketEvents plugin
unless you are intentionally managing a shared PacketEvents installation.

> **Detection limitations:** CERBERUS checks are heuristics, not proof of cheating.
> A Minecraft server cannot inspect a player's installed client files. The client
> identifier check only matches brand/channel data reported over the network;
> clients can spoof or omit it. Inventory timing and event-based reach
> measurements can be affected by latency, server tick load, game mechanics, and
> other plugins. Review alerts and tune checks before using automatic bans.
> CERBERUS implements its own limited set of server-side checks; it is not a
> complete feature-for-feature replacement for Grim, Vulcan, or Themis.

## Compatibility

| Component | Project target | Notes |
| --- | --- | --- |
| Minecraft server | Paper 1.20.4 API | `plugin.yml` declares API version `1.20`. Later Paper versions are not verified by this project; compatibility is not guaranteed. |
| Java bytecode | Java 17 | The project compiles with `--release 17`. Use the Java runtime required by your Paper server; newer Minecraft versions may require a newer Java runtime. |
| PacketEvents | 2.7.0 | Bundled and relocated into the plugin JAR. |

The Maven dependency targets Paper `1.20.4-R0.1-SNAPSHOT`. This identifies the
compile API, not a claim that every later Paper/Minecraft release has been
tested. Check your server's Paper requirements before selecting a Java runtime.

## Installation

1. Back up your server and use a test server first.
2. Build CERBERUS as described below, or obtain the packaged JAR from your
   trusted release source.
3. Copy `target/CERBERUS-1.0.0.jar` into the Paper server's `plugins/` folder.
4. Start the server once to generate `plugins/CERBERUS/config.yml`.
5. Review and tune the configuration before enabling automatic bans.
6. Restart the server and verify startup in the console with `/plugins`.

On upgrade from an older CERBERUS version, the plugin merges newly shipped
client identifiers into the existing blacklist while preserving custom entries.

Do not use `/reload` or a plugin-reload utility to install or update CERBERUS.
Restart the server instead.

## Build from source

Requirements:

- JDK 17 or newer
- Maven 3.9 or newer
- Network access to the Paper and CodeMC Maven repositories for dependencies

Run from the project root:

```shell
mvn --batch-mode clean verify
```

The shaded plugin is produced at `target/CERBERUS-1.0.0.jar`. Paper API is a
provided dependency; PacketEvents is bundled in the artifact.

## Commands and permissions

| Command | Description | Permission |
| --- | --- | --- |
| `/cerberus` or `/cerberus help` | Show available administration commands. | `cerberus.admin` |
| `/cerberus info` | Show the plugin version and configured check toggles. | `cerberus.admin` |
| `/cerberus reload` | Reload `config.yml` without restarting. | `cerberus.admin` |
| `/cerberustempban <player> <duration> <reason>` | Apply a timed name ban; console only. | `cerberus.admin` |

`/cerb` is an alias for `/cerberus`. The `cerberus.admin` permission defaults
to operators and receives staff violation alerts. The built-in temporary-ban
command accepts a positive integer followed by `s`, `m`, `h`, or `d` (for
example, `30m`, `12h`, or `7d`).

## Checks and default behavior

| Check | Default | Signal and behavior | Default punishment |
| --- | --- | --- | --- |
| Client identifier | Enabled | Matches configured substrings in reported `minecraft:brand` payloads, plugin-message channel names, or registered channel lists. This does not enumerate installed mods. | 1-day temporary name ban on a match. |
| AutoClicker | Enabled | Checks for at least 18 arm swings per second across six consecutive one-second windows; one burst alone does not add a violation. This is an experimental heuristic, not proof of automation. | Staff alert at VL 8; auto-ban is opt-in. |
| AutoTotem | Enabled | Buffers offhand totem replenishments measured below 100 ms; adds one VL when the buffer reaches 5. | 1-day temporary name ban when VL reaches 5. |
| Reach | Enabled | Cancels remote block damage, mining, and right-click interactions beyond 5 blocks, block placement/entity interactions beyond 5.2 blocks, and direct entity attacks beyond 3.5 blocks. Repeated attempts against one block are rate-limited for VL scoring. | 1-day temporary name ban when VL reaches 10. |
| Movement | Enabled | Checks excessive horizontal movement and sustained airborne movement outside common exempt states. | 1-day temporary name ban when VL reaches 6. |
| Xray | Enabled | Flags repeated, closely spaced finds of fully concealed diamond, emerald, or ancient-debris ore groups. Veins are grouped to reduce obvious false positives. This is experimental and may have false positives. | Staff alert at VL 6; auto-ban is opt-in. |

Violation levels decay by 1 every 30 seconds by default. AutoTotem's buffer also
decays by 0.25 per decay interval and on slower replenishments. These settings
are adjustable in `config.yml`.

**Roll out conservatively:** start on a staging server and tune checks before
using them on a live server. Established checks can apply a fixed 24-hour ban
at their configured thresholds. AutoClicker and Xray are experimental and
alert staff only by default; their automatic bans are opt-in. Watch alerts
under real server load and review false positives and interactions with other
plugins. Reported brand/channel matches are spoofable and should not be used as
ban evidence on their own.

## Configuration reference

The generated `plugins/CERBERUS/config.yml` is the canonical, commented
configuration. Back it up before editing. Use `/cerberus reload` to reload
changes to runtime check settings and messages; restart the server after
updating the plugin JAR.

| Setting | Default | Meaning |
| --- | --- | --- |
| `messages.prefix` | `&8[&cCERBERUS&8] ` | Prefix for staff alerts. |
| `messages.alert` | Configured template | Supports `{player}`, `{check}`, `{vl}`, and `{detail}`. |
| `checks.decay.amount` / `interval-seconds` | `1.0` / `30` | VL decay amount and interval. Use amount `0` to disable VL decay. |
| `checks.brand.enabled` / `blacklist` | `true` / list in config | Toggle and substrings checked against reported brand and channel-registration data; cannot enumerate installed client mods. |
| `checks.autoclicker.minimum-swings-per-second` | `18` | Minimum arm-swing count per complete one-second window. |
| `checks.autoclicker.consecutive-seconds` | `6` | Consecutive high-rate windows required for one violation. |
| `checks.autoclicker.vl-threshold` | `8.0` | AutoClicker threshold for a staff alert. |
| `checks.autoclicker.auto-ban-enabled` | `false` | Opt in to a fixed 24-hour ban at the AutoClicker threshold after validation. |
| `checks.autototem.minimum-restock-ms` | `100` | Faster observed restocks increment the buffer. |
| `checks.autototem.buffer-increment` / `buffer-decay` / `buffer-threshold` | `1.0` / `0.25` / `5.0` | Buffer increments, decay, and threshold for adding VL. |
| `checks.autototem.vl-threshold` | `5.0` | VL threshold for a 24-hour ban. |
| `checks.reach.maximum-distance` | `5.2` | Maximum eye-to-target-center distance in blocks. |
| `checks.reach.maximum-block-distance` | `5.0` | Maximum eye-to-block distance; out-of-range damage, breaking, and right-click interactions are cancelled. |
| `checks.reach.maximum-entity-distance` | `3.5` | Maximum distance to the closest point on an entity hitbox for direct attacks. |
| `checks.reach.vl-threshold` | `10.0` | VL threshold for a 24-hour ban. |
| `checks.movement.max-horizontal-per-tick` | `0.75` | Horizontal movement allowance before a speed violation is added. |
| `checks.movement.flight-air-ticks` / `flight-max-rise` | `12` / `1.8` | Air duration and height gain used by the flight-like movement heuristic. |
| `checks.movement.vl-threshold` | `6.0` | Movement violation threshold for a 24-hour ban. |
| `checks.xray.minimum-rare-ore-groups` | `4` | Number of distinct high-value ore groups required before suspicious mining patterns count. |
| `checks.xray.max-blocks-between-ores` | `24` | Maximum solid blocks mined between concealed high-value ore groups for the pattern to count. |
| `checks.xray.vl-threshold` | `6.0` | Xray threshold for a staff alert. |
| `checks.xray.auto-ban-enabled` | `false` | Opt in to a fixed 24-hour ban at the Xray threshold after validation. |

Established checks use the built-in `cerberustempban` command and a fixed `1d`
duration after their thresholds are reached. Experimental AutoClicker and Xray
checks alert staff at their thresholds by default; set the corresponding
`checks.autoclicker.auto-ban-enabled` or `checks.xray.auto-ban-enabled` option
to `true` only after testing on your server to opt into 24-hour bans. Disable
checks or adjust thresholds to tune detection. Client-reported mod identifiers
are spoofable and are never a reliable scan of installed client files.

## Troubleshooting

- **Plugin is missing from `/plugins`:** check the server console during
  startup, confirm the JAR is in the active server's `plugins/` directory, and
  verify that the server is Paper rather than an unsupported server type.
- **Plugin fails to enable:** check the server's `logs/latest.log` for the first
  CERBERUS or PacketEvents exception. Confirm the Java runtime meets that
  Paper version's requirements and that the packaged artifact is the shaded
  `CERBERUS-1.0.0.jar`.
- **Build cannot resolve a dependency:** confirm JDK 17+, Maven 3.9+, and
  network access to the Paper and CodeMC Maven repositories, then retry
  `mvn --batch-mode clean verify`.
- **A CERBERUS ban was not applied:** verify that `cerberustempban` is registered
  in `plugin.yml` and inspect the server console for the logged dispatch error.
- **Reload does not appear to update a setting:** review the console for YAML
  parse errors and confirm the key is under the correct section. Restart after
  replacing the plugin JAR.
- **Unexpected alerts or punishments:** disable the affected check or raise
  its violation threshold while investigating; review server tick health, latency, and
  interactions with other gameplay plugins.

## Contributing and reporting issues

See [CONTRIBUTING.md](CONTRIBUTING.md) for development and pull request
guidance. When reporting a reproducible issue, include the CERBERUS version,
Paper build, Java version, relevant redacted log excerpt, relevant
configuration values, and steps to reproduce. Do not include player IP
addresses, credentials, or other private data.

For suspected security vulnerabilities, avoid posting exploit details in a
public issue. Contact the project maintainers through a private channel they
have published, and include only the minimum information needed to reproduce.

## License

CERBERUS is distributed under the [MIT License](LICENSE).

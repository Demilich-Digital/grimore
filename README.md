# Demilich Digital Quick Grimoire

A floating overlay of tabletop-RPG tools for Android. A small bubble hovers over
your note-taking app (Obsidian, say); tap it and a panel opens with dice
rollers, oracle tables, and other quick-reference tools, so you can roll and
paste the result into your notes without leaving them.

- **Fully offline.** The app never declares the `INTERNET` permission, so the
  overlay cannot send your keystrokes or data anywhere. Fonts are bundled
  locally.
- **Local state only.** Settings and recent tools are stored on-device with
  Jetpack DataStore.
- **Clipboard-friendly.** Tools can copy their results to the system clipboard
  for pasting into other apps.
- **MIT licensed.** See [`LICENSE`](LICENSE) and [`NOTICE.md`](NOTICE.md) for
  third-party content.

## Tools

Tools are grouped into packs that you can switch on or off from the main
screen. Core and Ironsworn are enabled by default.

| Pack | Contents |
| --- | --- |
| **Core** | Dice Bot (dice notation such as `2d6+3`, advantage/disadvantage, drop-lowest), Custom Dice, Calculator, Card Deck (standard 52-card deck), Action Roll (Ironsworn, Starforged, Apocalypse World) |
| **Ironsworn** | Oracles from the original *Ironsworn* rulebook: Ask the Oracle, Plot & Twist, names, settlements and places |
| **Starforged** | Oracle tools for *Ironsworn: Starforged*: Core, Move, Misc, Character, Creature, Faction, Settlement, Starship, Space, Planet, Location Theme, Derelict and Precursor Vault |
| **Delve** | Oracle tools for *Ironsworn: Delve*: Site, Site Name, Character, Move, Monstrosity and Threat |

Oracle text comes from [Datasworn](https://github.com/rsek/datasworn) under
CC BY 4.0. *Ironsworn*, *Starforged* and *Delve* are by Shawn Tomkin.

## Requirements

- Android 10 (API 29) or newer
- The "Display over other apps" permission (`SYSTEM_ALERT_WINDOW`). The app
  explains why and links to the system setting on first run.
- Notification permission. The overlay runs as a foreground service, and its
  notification has a **Dismiss Overlay** action. You can also drag the bubble
  to the on-screen drop target to close it.

## Building

The build runs in Docker, so you only need Docker installed. The JDK, Gradle
and Android SDK all live in the image.

```sh
./build.sh                  # debug APK -> app/build/outputs/apk/debug/app-debug.apk
./build.sh bundleRelease    # release AAB -> app/build/outputs/bundle/release/app-release.aab
```

Any other Gradle task works too: `./build.sh <task>`.

Without further setup you get a debug build with a generic placeholder icon,
and release builds come out unsigned. Two optional environment variables
customize this:

| Variable | Purpose |
| --- | --- |
| `GRIMOIRE_BRANDING_RES_DIR` | A `res/` directory with your own launcher icon (`mipmap-anydpi-v26/ic_launcher*.xml`, `drawable/ic_launcher_foreground.xml`, `ic_launcher_background` colour). Defaults to `branding/oss/res`. |
| `GRIMOIRE_KEYSTORE_DIR` | A directory holding `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) and the keystore file, used to sign release builds. |

Without Docker, `gradle assembleDebug` also works if you have JDK 17, Gradle
8.13 and the Android SDK 36 installed. You can pass `-PbrandingResDir=` and
`-PkeystoreDir=` instead of the environment variables.

## Architecture

- **Native shell (Kotlin, Jetpack Compose).** `MainActivity` handles
  onboarding, permissions and pack toggles. `OverlayService` is the foreground
  service that draws the bubble and panel. Under `data/` are the tool and pack
  registries and the DataStore-backed settings.
- **Tools are static web pages.** Each tool is a self-contained single-page app
  in `app/src/main/assets/tools/<id>/index.html`, rendered in an offline
  WebView. Shared code (dice, oracle rolling, theme CSS, bundled fonts) lives
  in `assets/tools/shared/`. `GrimoireBridge` connects the pages to native
  features such as the clipboard.
- **Registry.** `GrimoireTool` lists every tool, along with its icon,
  description, tags, pack and credit.

### Adding a tool

1. Add a page at `app/src/main/assets/tools/<id>/index.html`.
2. Add an icon under `app/src/main/res/drawable/`.
3. Add an entry to `GrimoireTool` and assign it to a `ToolPack`.

The tool then shows up in the searchable tool list and the bubble's
recent-tools fan with no other native changes.

## Project docs

- [`PRD-Demilich_Digital_Quick_Grimoire.md`](PRD-Demilich_Digital_Quick_Grimoire.md):
  product requirements and constraints
- [`diary/`](diary/): design notes on why the project looks the way it does
- [`NOTICE.md`](NOTICE.md): third-party licenses and attribution

## License

MIT, copyright (c) 2026 Demilich Digital. The official Demilich Digital
launcher icon and brand colours are proprietary and not included. The
placeholder icon in `branding/oss/` is MIT like the rest of the code.

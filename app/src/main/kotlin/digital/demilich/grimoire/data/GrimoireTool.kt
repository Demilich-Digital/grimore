package digital.demilich.grimoire.data

import digital.demilich.grimoire.R

/**
 * Registry of tools. Adding a tool is: drop a self-contained page under
 * assets/tools/<id>/, add an entry here with its icon, description, tags, and
 * credit, and it appears in both the bubble's recent-tools fan (once opened at
 * least once) and the full searchable tool list automatically - no other native
 * code needed per tool.
 *
 * `description` and `tags` drive OverlayService.ToolList's search box and tag
 * filter chips; `credit` is shown on each row there too - both the list and the
 * fan-out are documented in NOTICE.md as one source of truth for licensing, but
 * this is what a user actually sees at the point of use.
 */
enum class GrimoireTool(
    val id: String,
    val assetPath: String,
    val displayName: String,
    val description: String,
    val tags: List<String>,
    val iconRes: Int,
    // Attribution for where a tool's content comes from - "Demilich Digital"
    // for original in-house tools, or the actual source/license for ported
    // content (see NOTICE.md for the full license text this summarizes).
    val credit: String,
    // Real tools appear in the tool grid; an unlisted one (like DEMO below) only
    // opens via a direct call, e.g. a low-key link elsewhere in the UI - it's
    // still a fully working tool.
    val unlisted: Boolean = false,
) {
    DICE(
        id = "dice",
        assetPath = "tools/dice/index.html",
        displayName = "Dice Bot",
        description = "Roll tabletop dice notation - 2d6+3, advantage/disadvantage, drop-lowest, and more.",
        tags = listOf("games", "tabletop", "random"),
        iconRes = R.drawable.ic_dice,
        credit = "Demilich Digital",
    ),
    CALCULATOR(
        id = "calculator",
        assetPath = "tools/calculator/index.html",
        displayName = "Calculator",
        description = "A simple calculator keypad for quick arithmetic.",
        tags = listOf("utility", "math"),
        iconRes = R.drawable.ic_calculator,
        credit = "Demilich Digital",
    ),
    DEMO(
        id = "demo",
        assetPath = "tools/demo/index.html",
        displayName = "How It Works",
        description = "A quick walkthrough of collapsing, moving, and dismissing the overlay.",
        tags = listOf("help"),
        iconRes = R.drawable.ic_demo,
        credit = "Demilich Digital",
        unlisted = true,
    ),
    IRONSWORN_ORACLES(
        id = "ironsworn-oracles",
        assetPath = "tools/ironsworn-oracles/index.html",
        displayName = "Ironsworn Oracles",
        description = "Ask the Oracle, Plot & Twist, Name Generator, and Settlement & Place - pick one from the menu, from Ironsworn.",
        tags = listOf("oracle", "ironsworn", "tabletop", "random", "yes-no", "prompts", "names", "worldbuilding"),
        iconRes = R.drawable.ic_oracle,
        credit = "Ironsworn by Shawn Tomkin • CC BY 4.0",
    ),
    CUSTOM_DICE(
        id = "custom-dice",
        assetPath = "tools/custom-dice/index.html",
        displayName = "Custom Dice",
        description = "Save your own dice - d13, d3, whatever your game needs - and build rolls with them.",
        tags = listOf("games", "tabletop", "random", "custom"),
        iconRes = R.drawable.ic_custom_dice,
        credit = "Demilich Digital",
    ),
    STARFORGED_CORE_ORACLES(
        id = "starforged-core-oracles",
        assetPath = "tools/starforged-core-oracles/index.html",
        displayName = "Starforged Core Oracles",
        description = "Ask the Oracle and the Core Oracles (Action, Theme, Descriptor, Focus) - pick one from the menu, from Ironsworn: Starforged.",
        tags = listOf("oracle", "starforged", "ironsworn", "tabletop", "random", "yes-no", "prompts"),
        iconRes = R.drawable.ic_oracle,
        credit = "Ironsworn: Starforged by Shawn Tomkin • CC BY 4.0",
    ),
    STARFORGED_MOVE_ORACLES(
        id = "starforged-move-oracles",
        assetPath = "tools/starforged-move-oracles/index.html",
        displayName = "Starforged Move Oracles",
        description = "Begin a Session, Make a Discovery, Pay the Price, and the rest of the move-triggered oracles from Ironsworn: Starforged.",
        tags = listOf("oracle", "starforged", "ironsworn", "tabletop", "random", "moves"),
        iconRes = R.drawable.ic_oracle,
        credit = "Ironsworn: Starforged by Shawn Tomkin • CC BY 4.0",
    ),
    STARFORGED_MISC_ORACLES(
        id = "starforged-misc-oracles",
        assetPath = "tools/starforged-misc-oracles/index.html",
        displayName = "Starforged Misc Oracles",
        description = "Story Complication, Story Clue, Anomaly Effect, and Combat Action - grab-bag GM oracles from Ironsworn: Starforged.",
        tags = listOf("oracle", "starforged", "ironsworn", "tabletop", "random", "prompts"),
        iconRes = R.drawable.ic_oracle,
        credit = "Ironsworn: Starforged by Shawn Tomkin • CC BY 4.0",
    ),
    STARFORGED_CHARACTER_ORACLES(
        id = "starforged-character-oracles",
        assetPath = "tools/starforged-character-oracles/index.html",
        displayName = "Starforged Character Oracles",
        description = "Character, Name, and Campaign Launch oracles - pick one from the menu, from Ironsworn: Starforged.",
        tags = listOf("oracle", "starforged", "ironsworn", "tabletop", "random", "names", "worldbuilding"),
        iconRes = R.drawable.ic_oracle,
        credit = "Ironsworn: Starforged by Shawn Tomkin • CC BY 4.0",
    ),
    STARFORGED_CREATURE_ORACLES(
        id = "starforged-creature-oracles",
        assetPath = "tools/starforged-creature-oracles/index.html",
        displayName = "Starforged Creature Oracles",
        description = "Creature and Basic Form oracles - pick one from the menu, from Ironsworn: Starforged.",
        tags = listOf("oracle", "starforged", "ironsworn", "tabletop", "random", "worldbuilding"),
        iconRes = R.drawable.ic_oracle,
        credit = "Ironsworn: Starforged by Shawn Tomkin • CC BY 4.0",
    ),
    STARFORGED_FACTION_ORACLES(
        id = "starforged-faction-oracles",
        assetPath = "tools/starforged-faction-oracles/index.html",
        displayName = "Starforged Faction Oracles",
        description = "Faction and Faction Name oracles - pick one from the menu, from Ironsworn: Starforged.",
        tags = listOf("oracle", "starforged", "ironsworn", "tabletop", "random", "names", "worldbuilding"),
        iconRes = R.drawable.ic_oracle,
        credit = "Ironsworn: Starforged by Shawn Tomkin • CC BY 4.0",
    ),
    STARFORGED_SETTLEMENT_ORACLES(
        id = "starforged-settlement-oracles",
        assetPath = "tools/starforged-settlement-oracles/index.html",
        displayName = "Starforged Settlement Oracles",
        description = "Settlement and Population oracles - pick one from the menu, from Ironsworn: Starforged.",
        tags = listOf("oracle", "starforged", "ironsworn", "tabletop", "random", "names", "worldbuilding"),
        iconRes = R.drawable.ic_oracle,
        credit = "Ironsworn: Starforged by Shawn Tomkin • CC BY 4.0",
    ),
    STARFORGED_STARSHIP_ORACLES(
        id = "starforged-starship-oracles",
        assetPath = "tools/starforged-starship-oracles/index.html",
        displayName = "Starforged Starship Oracles",
        description = "Starship and Mission oracles - pick one from the menu, from Ironsworn: Starforged.",
        tags = listOf("oracle", "starforged", "ironsworn", "tabletop", "random", "names", "worldbuilding"),
        iconRes = R.drawable.ic_oracle,
        credit = "Ironsworn: Starforged by Shawn Tomkin • CC BY 4.0",
    ),
    STARFORGED_SPACE_ORACLES(
        id = "starforged-space-oracles",
        assetPath = "tools/starforged-space-oracles/index.html",
        displayName = "Starforged Space Oracles",
        description = "Space Encounter, Sighting, and Sector Name oracles - pick one from the menu, from Ironsworn: Starforged.",
        tags = listOf("oracle", "starforged", "ironsworn", "tabletop", "random", "names", "worldbuilding"),
        iconRes = R.drawable.ic_oracle,
        credit = "Ironsworn: Starforged by Shawn Tomkin • CC BY 4.0",
    ),
    STARFORGED_LOCATION_THEME_ORACLES(
        id = "starforged-location-theme-oracles",
        assetPath = "tools/starforged-location-theme-oracles/index.html",
        displayName = "Starforged Location Theme Oracles",
        description = "Theme Type plus all eight location themes (Chaotic, Fortified, Haunted, Infested, Inhabited, Mechanical, Ruined, Sacred) - pick one from the menu, from Ironsworn: Starforged.",
        tags = listOf("oracle", "starforged", "ironsworn", "tabletop", "random", "worldbuilding"),
        iconRes = R.drawable.ic_oracle,
        credit = "Ironsworn: Starforged by Shawn Tomkin • CC BY 4.0",
    ),
    STARFORGED_DERELICT_ORACLES(
        id = "starforged-derelict-oracles",
        assetPath = "tools/starforged-derelict-oracles/index.html",
        displayName = "Starforged Derelict Oracles",
        description = "Derelict, all eight interior zones, and type/zone-by-location oracles - pick one from the menu, from Ironsworn: Starforged.",
        tags = listOf("oracle", "starforged", "ironsworn", "tabletop", "random", "worldbuilding"),
        iconRes = R.drawable.ic_oracle,
        credit = "Ironsworn: Starforged by Shawn Tomkin • CC BY 4.0",
    ),
    STARFORGED_PRECURSOR_VAULT_ORACLES(
        id = "starforged-precursor-vault-oracles",
        assetPath = "tools/starforged-precursor-vault-oracles/index.html",
        displayName = "Starforged Precursor Vault Oracles",
        description = "Vault, Interior, and Sanctum oracles - pick one from the menu, from Ironsworn: Starforged.",
        tags = listOf("oracle", "starforged", "ironsworn", "tabletop", "random", "worldbuilding"),
        iconRes = R.drawable.ic_oracle,
        credit = "Ironsworn: Starforged by Shawn Tomkin • CC BY 4.0",
    ),
    ;

    companion object {
        fun fromId(id: String?): GrimoireTool = entries.find { it.id == id } ?: DICE
    }
}

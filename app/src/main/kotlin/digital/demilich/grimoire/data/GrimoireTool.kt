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
    ;

    companion object {
        fun fromId(id: String?): GrimoireTool = entries.find { it.id == id } ?: DICE
    }
}

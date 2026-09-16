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
    ASK_THE_ORACLE(
        id = "ask-the-oracle",
        assetPath = "tools/ask-the-oracle/index.html",
        displayName = "Ask the Oracle",
        description = "Yes/no answers at five levels of likelihood, from Ironsworn.",
        tags = listOf("oracle", "ironsworn", "tabletop", "random", "yes-no"),
        iconRes = R.drawable.ic_oracle,
        credit = "Ironsworn by Shawn Tomkin • CC BY 4.0",
    ),
    PLOT_AND_TWIST(
        id = "plot-and-twist",
        assetPath = "tools/plot-and-twist/index.html",
        displayName = "Plot & Twist",
        description = "Combine an Action and a Theme into a prompt for a discovery, event, or twist, from Ironsworn.",
        tags = listOf("oracle", "ironsworn", "tabletop", "random", "prompts"),
        iconRes = R.drawable.ic_plot_twist,
        credit = "Ironsworn by Shawn Tomkin • CC BY 4.0",
    ),
    NAME_GENERATOR(
        id = "name-generator",
        assetPath = "tools/name-generator/index.html",
        displayName = "Name Generator",
        description = "Names for Ironlanders, elves, giants, varou, and trolls, from Ironsworn.",
        tags = listOf("oracle", "ironsworn", "tabletop", "random", "names"),
        iconRes = R.drawable.ic_name_generator,
        credit = "Ironsworn by Shawn Tomkin • CC BY 4.0",
    ),
    SETTLEMENT_AND_PLACE(
        id = "settlement-and-place",
        assetPath = "tools/settlement-and-place/index.html",
        displayName = "Settlement & Place",
        description = "Sketch a region, location, settlement trouble, or settlement name, from Ironsworn.",
        tags = listOf("oracle", "ironsworn", "tabletop", "random", "worldbuilding"),
        iconRes = R.drawable.ic_settlement,
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

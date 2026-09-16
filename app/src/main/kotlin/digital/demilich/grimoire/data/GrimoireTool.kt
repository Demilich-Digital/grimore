package digital.demilich.grimoire.data

import digital.demilich.grimoire.R

/**
 * Registry of tools shown as tiles in the overlay's tool grid. Adding a tool is:
 * drop a self-contained page under assets/tools/<id>/, add an entry here with its
 * icon, description, and tags, and it appears in the grid automatically - no other
 * native code needed per tool.
 *
 * `description` and `tags` are exactly what the grid's search box and tag filters
 * (if any) would match against.
 */
enum class GrimoireTool(
    val id: String,
    val assetPath: String,
    val displayName: String,
    val description: String,
    val tags: List<String>,
    val iconRes: Int,
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
    ),
    CALCULATOR(
        id = "calculator",
        assetPath = "tools/calculator/index.html",
        displayName = "Calculator",
        description = "A simple calculator keypad for quick arithmetic.",
        tags = listOf("utility", "math"),
        iconRes = R.drawable.ic_calculator,
    ),
    DEMO(
        id = "demo",
        assetPath = "tools/demo/index.html",
        displayName = "How It Works",
        description = "A quick walkthrough of collapsing, moving, and dismissing the overlay.",
        tags = listOf("help"),
        iconRes = R.drawable.ic_demo,
        unlisted = true,
    ),
    ASK_THE_ORACLE(
        id = "ask-the-oracle",
        assetPath = "tools/ask-the-oracle/index.html",
        displayName = "Ask the Oracle",
        description = "Yes/no answers at five levels of likelihood, from Ironsworn.",
        tags = listOf("oracle", "ironsworn", "tabletop", "random", "yes-no"),
        iconRes = R.drawable.ic_oracle,
    ),
    PLOT_AND_TWIST(
        id = "plot-and-twist",
        assetPath = "tools/plot-and-twist/index.html",
        displayName = "Plot & Twist",
        description = "Combine an Action and a Theme into a prompt for a discovery, event, or twist, from Ironsworn.",
        tags = listOf("oracle", "ironsworn", "tabletop", "random", "prompts"),
        iconRes = R.drawable.ic_plot_twist,
    ),
    NAME_GENERATOR(
        id = "name-generator",
        assetPath = "tools/name-generator/index.html",
        displayName = "Name Generator",
        description = "Names for Ironlanders, elves, giants, varou, and trolls, from Ironsworn.",
        tags = listOf("oracle", "ironsworn", "tabletop", "random", "names"),
        iconRes = R.drawable.ic_name_generator,
    ),
    SETTLEMENT_AND_PLACE(
        id = "settlement-and-place",
        assetPath = "tools/settlement-and-place/index.html",
        displayName = "Settlement & Place",
        description = "Sketch a region, location, settlement trouble, or settlement name, from Ironsworn.",
        tags = listOf("oracle", "ironsworn", "tabletop", "random", "worldbuilding"),
        iconRes = R.drawable.ic_settlement,
    ),
    CUSTOM_DICE(
        id = "custom-dice",
        assetPath = "tools/custom-dice/index.html",
        displayName = "Custom Dice",
        description = "Save your own dice - d13, d3, whatever your game needs - and build rolls with them.",
        tags = listOf("games", "tabletop", "random", "custom"),
        iconRes = R.drawable.ic_custom_dice,
    ),
    ;

    companion object {
        fun fromId(id: String?): GrimoireTool = entries.find { it.id == id } ?: DICE
    }
}

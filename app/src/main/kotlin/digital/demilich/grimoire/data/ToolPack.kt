package digital.demilich.grimoire.data

/**
 * Coarse-grained groups of tools a user can enable or disable as a whole
 * from MainActivity, so the overlay's tool list doesn't grow unbounded as
 * more content gets added. Every [GrimoireTool] belongs to exactly one pack
 * (unlike its free-form, multi-valued `tags`, which stay purely a search/
 * filter aid within the list) - see [EnabledPacks] for how the enabled set
 * is persisted and read.
 */
enum class ToolPack(
    val id: String,
    val displayName: String,
    val description: String,
    // Only matters the first time this app runs with packs at all (or after
    // an upgrade from a version that predates them) - see EnabledPacks.flow.
    // Core and Ironsworn on by default keeps the list short but non-empty
    // out of the box; Starforged and Delve (19 of the app's 24 listed tools
    // between them) are opt-in so a fresh list doesn't just look like the
    // old one.
    val defaultEnabled: Boolean,
) {
    CORE(
        id = "core",
        displayName = "Core",
        description = "Dice Bot, Calculator, Custom Dice, and Action Roll (Ironsworn/Starforged/Apocalypse World).",
        defaultEnabled = true,
    ),
    IRONSWORN(
        id = "ironsworn",
        displayName = "Ironsworn",
        description = "Oracles from the original Ironsworn rulebook.",
        defaultEnabled = true,
    ),
    STARFORGED(
        id = "starforged",
        displayName = "Starforged",
        description = "The full set of Ironsworn: Starforged oracle tools.",
        defaultEnabled = false,
    ),
    DELVE(
        id = "delve",
        displayName = "Delve",
        description = "Dungeon-delving oracles from Ironsworn: Delve.",
        defaultEnabled = false,
    ),
    ;

    companion object {
        fun fromId(id: String?): ToolPack? = entries.find { it.id == id }
    }
}

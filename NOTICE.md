# Third-party notices

Demilich Digital's Quick Grimoire is licensed under the MIT License (see
`LICENSE`). It bundles the following third-party content and code, each under
its own original license, retained as required:

## Ironsworn oracle content

The Ironsworn Oracles tool (`app/src/main/assets/tools/ironsworn-oracles/`,
combining Ask the Oracle, Plot & Twist, Name Generator, and Settlement &
Place into one tool with an oracle picker) embeds oracle table text from
*Ironsworn*, by Shawn Tomkin, licensed under **CC BY 4.0**
(https://creativecommons.org/licenses/by/4.0). Sourced from
[Datasworn](https://github.com/rsek/datasworn) (tooling/schema MIT; rulebook
content CC BY 4.0 or CC BY-NC 4.0 depending on source - only CC BY 4.0 content
is used here). Table rows are reproduced verbatim; the surrounding page code is
original.

## Ironsworn: Starforged oracle content

The Starforged oracle tools (`app/src/main/assets/tools/starforged-*-oracles/`
- Core, Move, Misc, Character, Creature, Faction, Settlement, Starship,
Space, Location Theme, Derelict, and Precursor Vault, each combining a
Datasworn oracle collection into one tool with an oracle picker; Planet
Oracles uses a two-tap tile grid instead, since its eleven planet types
don't fit one dropdown) embed oracle table text from *Ironsworn:
Starforged*, by Shawn Tomkin, licensed under **CC BY 4.0**
(https://creativecommons.org/licenses/by/4.0). Sourced from
[Datasworn](https://github.com/rsek/datasworn) - the entire Starforged
ruleset file is CC BY 4.0, unlike some other Datasworn-packaged content.
Table rows are reproduced verbatim (a handful of source rows that are
never actually rollable - e.g. a Lifeless planet's life-dependent Peril
entries - are dropped rather than kept as dead weight); the surrounding
page code is original.

## Ironsworn: Delve oracle content

The Delve oracle tools (`app/src/main/assets/tools/ironsworn-delve-*-oracles/`
- Site (Site Nature theme/domain picker plus Feature, Danger, Aspect/Focus,
and Trap), Site Name, Character, Move, Monstrosity, and Threat, each
combining a Datasworn oracle collection into one tool with a picker or a
two-tap navigation grid) embed oracle table text from *Ironsworn: Delve*, by
Shawn Tomkin, licensed under **CC BY 4.0**
(https://creativecommons.org/licenses/by/4.0). Sourced from
[Datasworn](https://github.com/rsek/datasworn) - the entire Delve ruleset
file is CC BY 4.0. Table rows are reproduced verbatim; the Site tool's
Feature/Danger rolls and the Site Name tool's Full Site Name composer
reimplement Delve's own table-branching and name-template logic by hand (the
same approach already used for Ironsworn's Settlement Name and Starforged's
Faction Name); the surrounding page code is original.

## Bundled web fonts

The Dice Bot and Custom Dice tools (`app/src/main/assets/tools/{dice,
custom-dice}/fonts/`) each bundle the same three Google Fonts as local
`.woff2` files, licensed under the **SIL Open Font License 1.1**
(https://openfontlicense.org):

- [Figtree](https://fonts.google.com/specimen/Figtree) by Erik Kennedy
- [Unbounded](https://fonts.google.com/specimen/Unbounded) by the Unbounded
  Project authors
- [IBM Plex Mono](https://fonts.google.com/specimen/IBM+Plex+Mono) by IBM

Bundled rather than loaded from Google's CDN so the overlay keeps working
offline - it has no other network dependency (the app never declares the
`INTERNET` permission).

## Android/Kotlin build dependencies

AndroidX (Jetpack Compose, Activity, Lifecycle, Core, WebKit, DataStore) and
the Kotlin standard library/coroutines, pulled in via Gradle and linked into
the compiled APK, are licensed under the **Apache License, Version 2.0** by
Google/JetBrains and the Kotlin Foundation respectively. They are not vendored
as source in this repository.

## Proprietary branding

The real Demilich Digital launcher icon and brand colors
(`branding/demilich/res/`, gitignored, not present in this repository) are
**not** covered by the MIT License above - they remain proprietary to Demilich
Digital. The generic placeholder icon checked into `branding/oss/res/` is what
this repository actually builds with by default, and *is* covered by the MIT
License like the rest of the code.

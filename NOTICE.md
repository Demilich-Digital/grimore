# Third-party notices

Demilich Digital's Quick Grimoire is licensed under the MIT License (see
`LICENSE`). It bundles the following third-party content and code, each under
its own original license, retained as required:

## Ironsworn oracle content

The Ask the Oracle, Plot & Twist, Name Generator, and Settlement & Place tools
(`app/src/main/assets/tools/{ask-the-oracle,plot-and-twist,name-generator,
settlement-and-place}/`) embed oracle table text from *Ironsworn*, by Shawn
Tomkin, licensed under **CC BY 4.0**
(https://creativecommons.org/licenses/by/4.0). Sourced from
[Datasworn](https://github.com/rsek/datasworn) (tooling/schema MIT; rulebook
content CC BY 4.0 or CC BY-NC 4.0 depending on source - only CC BY 4.0 content
is used here). Table rows are reproduced verbatim; the surrounding page code is
original.

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

# Tinker Leveling for NeoForge 1.21.1

This branch ports Tinker Leveling to the local NeoForge 1.21.1 Tinkers' Construct codebase.

## Versions

- Minecraft 1.21.1
- NeoForge 21.1.241
- Tinkers' Construct 3.12.5
- Mantle 1.12.4
- Java 21

## Build

The default development setup expects these sibling projects and their built JARs:

```text
../TinkersConstruct-1.21.1/build/libs/TinkersConstruct-1.21.1-3.12.5.jar
../Mantle-1.21.1/build/libs/Mantle-1.21.1-1.12.4.jar
```

Build both dependencies first, then run:

```powershell
.\gradlew.bat build
```

Use `-Ptconstruct_jar=<path>` and `-Pmantle_jar=<path>` to override the default JAR locations.

The release JAR is written to `build/libs/TinkerLeveling-1.21.1-0.5.1.jar`.

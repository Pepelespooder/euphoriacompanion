# Euphoria Companion Mod

A Minecraft Fabric mod designed to assist in analyzing and comparing shaderpack block configurations against the actual blocks present in the game. This tool helps identify mismatches, unused shader blocks, and missing shader entries for modded blocks.

## Features

- **Shaderpack Analysis**: Scans `.zip` or directory-based shaderpacks for `block.properties` files.
- **Block Comparison**:
  - Compares blocks registered in-game (including modded blocks) with those defined in shaderpacks.
  - Identifies:
    - Blocks missing from shaderpacks (useful for ensuring shader compatibility with mods).
    - Unused blocks in shaderpacks (reduces unnecessary shader processing). # Will be useful for Optifine because it nukes the log with warnings
- **Automated Reporting**:
  - Generates detailed logs in `logs/block_comparison_[shaderpack].txt`.
  - Organizes blocks by mod for easier troubleshooting.
  - Provides summary statistics (total blocks, missing counts, etc.).
- **Cross-Version Support**: Works with Minecraft 1.16+.
- **Key Binding**: Includes a client-side keybind (`F6`) for potential manual triggering.

## How It Works

1. **Initialization**:
   - Scans the Minecraft instance's `shaderpacks` directory for shaderpacks.
   - Collects all registered blocks from the game (vanilla and modded).

2. **Shaderpack Processing**:
   - Extracts and parses `shaders/block.properties` from shaderpacks (supports both folders and `.zip` files).

3. **Comparison**:
   - Compares in-game blocks with shader-defined blocks.
   - Detects:
     - Blocks present in-game but missing from the shaderpack.
     - Blocks defined in the shaderpack but not present in the game.

4. **Reporting**:
   - Writes a categorized report to `logs/block_comparison_[shaderpack].txt`, including:
     - Summary statistics.
     - Missing blocks grouped by mod.
     - Full block lists for debugging.

## Installation

1. **Requires Fabric Loader** and **Fabric API**.
2. Place the mod JAR in your `mods` folder.

## Usage

1. Launch the game with the mod installed.
2. The mod automatically scans shaderpacks on startup. (Only on Modern)
3. Press `F6` to process block.properties again. (Rebindable)
4. Check the `logs` folder for generated reports.

## For Shaderpack Authors

Use the generated logs to:
- Ensure compatibility with popular mods by adding missing block entries.

## Technical Notes

- **Supported Minecraft Versions**: 1.16 to 1.19.2 (via legacy registry handling) and 1.19.3+ (via modern registry handling)
- **Log Format**: Reports are human-readable and sorted alphabetically by mod ID.

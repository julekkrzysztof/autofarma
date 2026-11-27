# AutoFarm

Fabric mod for Minecraft 1.21.4. The existing cactus farm remains available; building is now driven by ordinary client movement and vanilla block-use interactions. The server validates the resulting world state and reports progress. No world blocks are written directly by the builder.

## Use

1. Install the mod on both client and server. In multiplayer, use it only where automation is allowed. All `/autofarm` player commands are available without OP.
2. Select the chunk with `/autofarm set 1` and `/autofarm set 2`.
3. Optionally set cactus-farm floors with `/autofarm give <1-30>`.
4. Run `/autofarm check`, put the listed building items on the hotbar, then run `/autofarm start`.
5. `/autofarm pause` pauses; `/autofarm start` resumes. `/autofarm stop` stops the current run. `/autofarm delate` (also `/autofarm delete`) clears the task and chunk selection so you can select a new location. Blocks already placed stay in the world.
6. `/autofarm speed <1-20>` sets the delay between placement attempts in ticks. `/autofarm info` shows status and progress.

The client follows the current placement with normal movement and only uses the vanilla interaction manager when the target block is under the crosshair. Unreachable placements, missing hotbar items, and obstructed target locations stop or pause the run instead of editing the world directly.

## Loading a JSON schematic

Open `/autofarm gui` and drag a `.json` file into the schematic area. The file is validated and copied to the active world's `schematics` directory, and selected for the next build. Alternatively, place `<name>.json` in that directory and run `/autofarm schema load <name>`. This works without OP; the mod handles the file import. Schematic dimensions must be at most 16 blocks wide and deep and 256 blocks tall. The block list is ordered: supports should appear before blocks placed on them. Blocks use their default Minecraft state.

```json
{
  "name": "Small platform",
  "width": 2,
  "height": 1,
  "depth": 2,
  "blocks": [
    { "x": 0, "y": 0, "z": 0, "block": "minecraft:smooth_stone" },
    { "x": 1, "y": 0, "z": 0, "block": "minecraft:smooth_stone" },
    { "x": 0, "y": 0, "z": 1, "block": "minecraft:smooth_stone" },
    { "x": 1, "y": 0, "z": 1, "block": "minecraft:smooth_stone" }
  ]
}
```
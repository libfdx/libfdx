# Ridgeline

Ridgeline opens on the title screen. Choose **Play** to begin in Level 1, Glasshouse Ravine, **Options** to adjust the sound, or **How to play** to see the controls. During play, **Pause** lets you resume, adjust the sound, restart the current level, or return to the main menu. Starting from the main menu begins a fresh journey.

Each trail has four places to discover, optional brass gears, and a gated workshop on the way to its destination. Follow the valley floor, climb the upper galleries, or drop through a bridge to rejoin the path below. Choose **Next level** at the destination to continue. At the lighthouse, **Play again** starts a new journey.

| Trail | A place to explore | Destination |
| --- | --- | --- |
| Glasshouse Ravine | Wheel Loft | The glasshouse |
| Amber Quarry | Ore Siding | The winding house |
| Fernwood Hollow | Heron Rookery | The canopy treehouse |
| Snowline Ridge | Eagle Saddle | The observatory |
| Cinder Crossing | Chain Gantry | The old furnace |
| Lantern Sound | Driftwood Cove | The lighthouse |

In a bell workshop, use the clue to work out the order of the three numbered bells. In a signal workshop, switch all three lamps on. Each wheel changes the lamps shown in its prompt. Stand by a numbered bell or wheel and press **Down** or tap **Turn**. Solving the workshop raises the gate and sets a checkpoint on its far side.

Jump on a creature from above to bounce. Some patrols also shoot. When a creature turns toward you and a shot glows at its mouth, it has picked a direction. Jump over the shot, take cover or stomp the creature before it fires.

| Trail | Creatures | Ranged attack |
| --- | --- | --- |
| Glasshouse Ravine | Armored beetles, lantern moths, spring frogs | Beetles spit seeds straight ahead. |
| Amber Quarry | Crystal armadillos, cave bats, geode toads | Armadillos launch fast amber shards. |
| Fernwood Hollow | Moss hedgehogs, dragonflies, walking mushrooms | Mushrooms release slow, bobbing spores. |
| Snowline Ridge | Mountain rams, snowy owls, arctic hares | Hares lob snowballs. |
| Cinder Crossing | Furnace tortoises, clockwork wasps, ember salamanders | Tortoises throw embers in a shallow arc. |
| Lantern Sound | Hermit crabs, storm petrels, mudskippers | Crabs spit pearls that bounce once. |

Taking a hit costs one of the three health pips. Lighting a new checkpoint restores one pip. Spikes and falls end the attempt. Retrying from a checkpoint keeps gears, defeated enemies and puzzle settings and clears incoming shots. Restarting the trail resets them.

## Play

Run a desktop or browser build from the repository root.

```powershell
.\gradlew.bat :samples:2d:platformer:platform:desktop:libfdx_desktop_jvm_gl_run
.\gradlew.bat :samples:2d:platformer:platform:web:libfdx_web_js_webgl_run
```

| Action | Keyboard | Gamepad |
| --- | --- | --- |
| Play, resume, retry or continue after finishing a level | Enter | South button |
| Move | A / D or Left / Right | Left stick or D-pad |
| Jump | Space, W or Up | South button |
| Turn the nearby puzzle dial or drop through a bridge | S or Down | D-pad down |
| Pause or resume | P or Escape | Start |
| Restart the current trail | R | |
| Continue from the level-complete screen | N | East button |
| Volume down / up, mute | Q / E, M | |

Touch brings up movement, jump and drop buttons during play. Hold a direction with one finger and jump or drop with another. Keyboard, mouse and gamepad play use the compact HUD.

## How it works

The [six Tiled maps](assets/levels) define each trail’s cliffs, pass-through bridges, cable lifts, working mills, encounter points and discoveries. Characters, props and puzzle instruments use a shared sprite atlas. Each trail also loads its own scenery atlas with three places and a destination landmark. Terrain and sky textures are in the [art folder](assets/levels/art). The fixed-step simulation drives the lifts, collisions and contact effects. See the [Tiled importer](../../../libfdx/extensions/maps/tiled/README.md) for format details.

The [packed atlases](assets/levels/atlases) store the sprite artwork. Each atlas has a PNG page and a JSON file that names the regions on that page. The game loads these files directly.

`PlatformerLevel` reads each map into the fixed-step simulation. `PlatformerApplication` manages the trail’s asset scope, input router and audio mixer. `PlatformerHud` uses UI Kit for menus, touch controls and the results card. The illustrated assets and synthesized audio use the repository license.

Run the sample tests from the repository root.

```powershell
.\gradlew.bat :samples:2d:platformer:core:test
```

# FT's Geology: Feel the Nature

An addon for [FT's Geology](https://github.com/Jelada-Studios/fts-geology) that lets you hear, see and feel the world it builds.
Forge 1.20.1.

FT's Geology decides *why* the land looks the way it does: plates, faults, volcanoes, rivers, a regional weather.
**Feel the Nature makes that world alive and calm**: the rain is heard where it falls on you, the wind moves through
what grows round you, and the country is clothed the way its rock and its climate would clothe it.

---

## Key Highlights

- **Weather You Hear Where You Stand**: Rain follows FT's Geology's regional storms, not a single world-wide flag. A
  drizzle, a steady rain and a downpour each have their own recording; a rain after a dry spell is heard beginning,
  and a long one is heard dying away. Snow falls in silence.
- **What the Rain Falls On**: Leaves, conifer needles, open water, bare rock, the crown of a tree above you, a wooden,
  metal or glass roof, a window. Drips keep falling from the leaves after the rain stops.
- **Wind That Gusts**: From a breeze to a gale, strongest on the heights, in the leaves, the pines and the grass. It
  swells and lulls with FT's Geology's gusts, the same moment on every client. In a storm the trees creak.
- **Water With a Voice**: Brooks, FT's Geology's rivers where they actually run (a river stilled behind a dam is as
  quiet as a pond), and waterfalls.
- **The Umbrella**: Sixteen colours. Hear the rain drum on its cloth; with Tough As Nails it keeps you dry. A strong
  gust turns it inside out, a breeze never does.
- **Small Life**: Fireflies on warm, dry nights, pollen over flowers by day, leaves falling in the wind and in autumn,
  snow blown off the ground in a gale.
- **The Country Clothed**: FT's Geology's mountain belts get their flowers, dwarf spruce, mossy forests, fallen logs
  and scree; boulders are cut from the rock beneath them; river banks grow tall grass, ferns and reeds; volcanic bombs
  lie round the craters of large volcanoes; and Terralith's basalt cliffs become columns stepping down into the sea.
- **The Country's Creatures**: birds by day in what grows round you, the dawn chorus, crickets on warm nights, frogs by
  still water in the evening, bees over flowers; now and then a hawk, an owl, a loon far out on a lake. Rain and cold
  quiet them, each in its season.
- **Small Details and Mornings**: stones at the water's edge, old snow lying on the north slopes into summer, the spray
  at a waterfall's foot, morning mist off still water (off unless turned on), motes in the sun through the crowns,
  snow shaken off the trees by a gust, rain running down the windows.
- **Fire Lookouts** on prominent summits, built as the real ones are: a glazed cab twenty blocks up on splayed timber
  legs, a switchback stair, a walkway, a lantern-room roof. A weather station with FT's Geology's instruments, lived
  in, with the keeper's log on a lectern. A trail winds down to the valley, with rescue boards on its trees.
  `/ftn locate lookout` finds the nearest.
- **On the High Pastures**: flag trees bent the way the prevailing wind blows, cairns, dry-stone mountain shelters with
  a map to the nearest lookout on the wall, benches where the ground falls away before a view, bird hides on the lakes.
- **Music by the Land**: forest, meadow, peaks, snow, stony ground, taiga and the tree line.
- **English and Turkish**: Full `en_us` and `tr_tr` translations.

## Requirements

- Minecraft 1.20.1, Forge 47.2 or newer
- **FT's Geology 0.11.0 or newer** (required); 0.11.4 or newer for the stones, the falls' spray, the flag trees and
  the lookouts' named mountains

## Works With

All optional: **Serene Seasons** (autumn leaves, the creatures' seasons), **Tough As Nails** (the umbrella keeps you
dry), **AmbientSounds** (its rain and wind stand down so they never play twice), **Terralith** (its basalt cliffs,
re-done along the sea), **Naturalist** (its animals in FT's Geology's forests and meadows), **the Simple Radio ham
fork** (each lookout a radio station on the rescue frequency, with a coal generator at its foot), **Advanced
Chimneys** (the stoves' chimneys) and **Pollution of the Realms** (no fire left burning).
Runs on dedicated servers; the sounds and particles are client-side only.

## Building

The addon compiles against FT's Geology's jar, which is not in this repository: copy an FT's Geology 0.11.0 jar into
`libs/` as `ftn-core-0.11.0.jar`, then

```
./gradlew build
```

The jar is written to `build/libs/`.

## License

Copyright (C) 2026 Jelada Studios. Feel the Nature is free software under the GNU General Public License v3.0 or
later: see `LICENSE`. The recordings are from Freesound, all CC0 but the loon's (nicStage, CC BY 4.0); the
recordists are credited in `CREDITS-sounds.md`.

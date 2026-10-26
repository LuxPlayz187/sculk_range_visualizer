# Sculk Range Visualizer (Fabric, Minecraft 26.3, client-only)

- Hotkey: **V** (rebind under Controls > "Sculk Visualizer")
- Standard Sculk Sensor: 8-block sphere (cyan). Calibrated: 16-block sphere (teal).
- Only sensors within 48 blocks (3 chunks) are scanned; rescans every 0.5 s from already-loaded chunk data.
- No packets, no server component.

Build: `gradle wrapper` (Gradle 9.5+, JDK 25) then `./gradlew build` -> build/libs/
Dev run: `./gradlew runClient`

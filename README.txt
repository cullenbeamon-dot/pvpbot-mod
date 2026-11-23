PvP Bot (Fabric, Minecraft 1.21.11)

EASIEST: let GitHub build the jar for you (no installs)
 1. Make a free account at github.com and create a new repository.
 2. Click "Add file" > "Upload files", drag in EVERYTHING from this folder
    (including the hidden .github folder), and commit.
 3. Open the "Actions" tab, click the latest "build" run, wait ~3 minutes.
 4. Download "pvpbot-jar" at the bottom, unzip it, and you have pvpbot-1.0.0.jar.
 5. Put the jar + Fabric API into your Dawn mods folder. Launch 1.21.11.

OR build locally: install JDK 21 and Gradle 9.2.1, then run:  gradle build
 The jar is in build/libs/pvpbot-1.0.0.jar

IN GAME (singleplayer / LAN world):
 /pvpbot spawn <sword|pot|mace|cart|smp> [level 1-10]
 /pvpbot clear

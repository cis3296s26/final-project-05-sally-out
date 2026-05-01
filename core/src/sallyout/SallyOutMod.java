package sallyout;

import arc.Events;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import mindustry.mod.Mods.LoadedMod;
import sallyout.ui.SetupPhaseUI;
import sallyout.ui.SetupPhaseOverlay;

/**
 * SallyOutMod
 * ===========
 * Mindustry mod entry point.
 *
 * Declared in mod.hjson:
 * 
 * <pre>
 *   name:        "Sally Out PvP"
 *   author:      "your-name"
 *   description: "Tactical PvP gamemode with setup phase, unit stats, and directional combat."
 *   version:     "2.0.0"
 *   minGameVersion: "154"
 *   main:        "mindustry.sallyout.SallyOutMod"
 * </pre>
 */


public class SallyOutMod extends Mod {
    public SallyOutMod() {}

    @Override
    public void init() {
        Log.infoTag("SallyOut", "Initializing SallyOut Mod");
        
        // Initialize rules extension
        SallyOutRulesExtension.init();
        
        // Initialize hooks
        SallyOutModInit.initializeHooks();
        
        // Register the world-draw overlay (deployment zones + unit stat bars)
        SetupPhaseOverlay.init();
        
        // Hook into world load to set up UI if in sallyout mode
        Events.on(EventType.WorldLoadEvent.class, e -> {
            if (SallyOutModInit.isCurrentGameSallyOut()) {
                SetupPhaseUI.openForAll(
                    new arc.math.geom.Rect(0, 0, 300, 300),
                    new arc.math.geom.Rect(400, 400, 300, 300),
                    SallyOutGamemode.DEFAULT_SUPPLY_BUDGET);
            }
        });

        System.out.println("SallyOut loaded");
        System.out.println("SallyOut running inside dev build");
    }
}
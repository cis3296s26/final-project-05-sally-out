package sallyout;

import arc.Events;
import mindustry.game.EventType;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.mod.Mod;
import sallyout.ui.SetupPhaseOverlay;
import sallyout.ui.SetupPhaseUI;

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
 *   version:     "1.0.0"
 *   minGameVersion: "146"
 *   main:        "mindustry.sallyout.SallyOutMod"
 * </pre>
 */
public class SallyOutMod extends Mod {

    public SallyOutMod() {
    }

    @Override
    public void loadContent() {
        // Register all unit types into Mindustry's content registry
        BaseSallyOutUnitDefs.load();
    }

    @Override
    public void init() {
        // Start the gamemode orchestrator
        SallyOutGamemode.init();

        // Register the world-draw overlay (deployment zones + unit stat bars)
        SetupPhaseOverlay.init();

        System.out.println("SallyOut loaded");
        System.out.println("SallyOut running inside dev build");

        // // Update the setup UI timer every tick
        // Events.run(EventType.Trigger.update, () -> {
        // SallyOutGamemode gm = SallyOutGamemode.get();
        // if (gm != null && gm.isSetupPhase()) {
        // float secondsLeft = gm.setupTimeLeft() / 60f;
        // mindustry.sallyout.ui.SetupPhaseUI instance =
        // mindustry.sallyout.ui.SetupPhaseUI.class.cast(null); // placeholder – see
        // note
        // // SetupPhaseUI exposes a static updateTimer if needed;
        // // in practice the label reads setupTimeLeft() directly in its render.
        // }
        // });
    }
}

package mindustry.sallyout;

import arc.Events;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import mindustry.sallyout.ui.SetupPhaseOverlay;

/**
 * SallyOutMod
 * ===========
 * Mindustry mod entry point.
 *
 * Declared in mod.hjson:
 * <pre>
 *   name:        "Sally Out PvP"
 *   author:      "your-name"
 *   description: "Tactical PvP gamemode with setup phase, unit stats, and directional combat."
 *   version:     "1.0.0"
 *   minGameVersion: "146"
 *   main:        "mindustry.pvp.SallyOutMod"
 * </pre>
 */
public class SallyOutMod extends Mod {

    public SallyOutMod() {}

    @Override
    public void loadContent() {
        // Register all tactical unit types into Mindustry's content registry
        BaseSallyOutUnitDefs.load();
    }

    @Override
    public void init() {
        // Start the gamemode orchestrator
        SallyOutGamemode.init();

        // Register the world-draw overlay (deployment zones + unit stat bars)
        SetupPhaseOverlay.init();

        // Update the setup UI timer every tick
        Events.run(EventType.Trigger.update, () -> {
            SallyOutGamemode gm = SallyOutGamemode.get();
            if (gm != null && gm.isSetupPhase()) {
                float secondsLeft = gm.setupTimeLeft() / 60f;
                mindustry.sallyout.ui.SetupPhaseUI instance =
                    mindustry.sallyout.ui.SetupPhaseUI.class.cast(null); // placeholder – see note
                // SetupPhaseUI exposes a static updateTimer if needed;
                // in practice the label reads setupTimeLeft() directly in its render.
            }
        });
    }
}

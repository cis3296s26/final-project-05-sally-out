package sallyout;

import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Rules;
import arc.Events;

/**
 * Initialization module for SallyOut that hooks into game events.
 * Detects when SallyOut mode is active and initializes gamemode accordingly.
 */
public class SallyOutModInit {

    public static void initializeHooks() {
        // Hook into world load to detect sallyout mode
        Events.on(EventType.WorldLoadEvent.class, e -> {
            if (Vars.state != null && Vars.state.rules != null) {
                if (SallyOutGamemodeManager.isSallyOutMode(Vars.state.rules)) {
                    SallyOutGamemode.init();
                }
            }
        });
    }

    /**
     * Apply sallyout configuration to a ruleset.
     */
    public static void applySallyOutRules(Rules rules) {
        SallyOutGamemodeManager.configureForSallyOut(rules);
    }

    /**
     * Check if current game is sallyout mode.
     */
    public static boolean isCurrentGameSallyOut() {
        return Vars.state != null && 
               Vars.state.rules != null && 
               SallyOutGamemodeManager.isSallyOutMode(Vars.state.rules);
    }
}
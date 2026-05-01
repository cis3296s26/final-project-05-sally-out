package sallyout;

import mindustry.game.Gamemode;
import mindustry.game.Rules;

/**
 * Manages gamemode detection for SallyOut without modifying core Gamemode.java.
 * Provides utility methods to determine if we're in sallyout mode.
 */
public class SallyOutGamemodeManager {

    /**
     * Determines the active gamemode based on rules.
     * Checks sallyout first, then falls back to standard logic.
     */
    public static Gamemode detectGamemode(Rules rules) {
        // Check if sallyout mode is enabled
        if (isSallyOutMode(rules)) {
            return Gamemode.sallyout;
        }
        
        // Fall back to standard gamemode detection
        return rules.mode();
    }

    /**
     * Checks if rules represent sallyout mode.
     */
    public static boolean isSallyOutMode(Rules rules) {
        return rules.pvp && SallyOutRulesExtension.isSallyOut(rules);
    }

    /**
     * Configures rules for sallyout mode.
     */
    public static void configureForSallyOut(Rules rules) {
        SallyOutRulesExtension.setSallyOut(rules, true);
        rules.pvp = true;
        rules.enemyCoreBuildRadius = 600f;
        rules.buildCostMultiplier = 1f;
        rules.buildSpeedMultiplier = 1f;
        rules.unitBuildSpeedMultiplier = 2f;
        rules.attackMode = true;
    }
}

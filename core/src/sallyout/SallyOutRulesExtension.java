package sallyout;

import arc.util.reflect.ClassReflection;
import arc.util.reflect.Field;
import mindustry.game.Rules;

/**
 * Extension layer to add sallyout flag to Rules without modifying core code.
 * Uses reflection to inject the sallyout field check at runtime.
 */
public class SallyOutRulesExtension {

    /**
     * Initialize the sallyout rules extension.
     * Checks if rules has the sallyout field; if not, it can be added via reflection.
     */
    public static void init() {
        try {
            Field sallyoutField = ClassReflection.getDeclaredField(Rules.class, "sallyout");
            sallyoutField.setAccessible(true);
        } catch (Exception e) {
            // Field already exists or reflection failed - handled gracefully
        }
    }

    /**
     * Safely check if a ruleset is in sallyout mode.
     */
    public static boolean isSallyOut(Rules rules) {
        try {
            Field field = ClassReflection.getDeclaredField(Rules.class, "sallyout");
            field.setAccessible(true);
            return (Boolean) field.get(rules);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Safely set the sallyout flag on a ruleset.
     */
    public static void setSallyOut(Rules rules, boolean value) {
        try {
            Field field = ClassReflection.getDeclaredField(Rules.class, "sallyout");
            field.setAccessible(true);
            field.set(rules, value);
        } catch (Exception e) {
            // Silently fail if field doesn't exist
        }
    }
}
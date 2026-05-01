package sallyout;

import java.lang.reflect.Field;
import mindustry.game.Rules;

public class SallyOutRulesExtension {

    public static void init() {
        try {
            Field field = Rules.class.getDeclaredField("sallyout");
            field.setAccessible(true);
        } catch (Exception ignored) {
        }
    }

    public static boolean isSallyOut(Rules rules) {
        try {
            Field field = Rules.class.getDeclaredField("sallyout");
            field.setAccessible(true);
            return (boolean) field.get(rules);
        } catch (Exception e) {
            return false;
        }
    }

    public static void setSallyOut(Rules rules, boolean value) {
        try {
            Field field = Rules.class.getDeclaredField("sallyout");
            field.setAccessible(true);
            field.set(rules, value);
        } catch (Exception ignored) {
        }
    }
}
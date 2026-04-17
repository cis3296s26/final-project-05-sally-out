package mindustry.ui.dialogs;

import mindustry.game.Gamemode;
import mindustry.game.Rules;
import mindustry.maps.*;
import static mindustry.Vars.*;

public class CustomGameDialog extends MapListDialog {
    private MapPlayDialog dialog = new MapPlayDialog();

    public CustomGameDialog() {
        super("@customgame", false);
    }

    @Override
    void showMap(Map map) {
        Gamemode mode = Gamemode.survival.valid(map) ? Gamemode.survival : null;

        if (mode == null) {
            for (Gamemode m : Gamemode.all) {
                if (m.valid(map)) {
                    mode = m;
                    break;
                }
            }
        }

        if (mode == null) mode = Gamemode.survival;

        Rules rules = map.applyRules(mode);
        control.playMap(map, rules);
    }
}
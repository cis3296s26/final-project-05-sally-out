package sallyout.ui;

import arc.Core;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import sallyout.BaseSallyOutUnitStats;
import sallyout.BaseSallyOutUnitType;
import sallyout.SallyOutGamemode;

import java.util.ArrayList;
import java.util.List;

/**
 * SetupPhaseUI
 * ============
 * Side panel for the unit-placement setup phase.
 * Fixes applied vs original:
 *   - Arc's Element does NOT have setVisible(boolean). Use the public field: element.visible = true/false.
 *   - InputListener.touchUp() returns VOID, not boolean. Remove return statements from it.
 *   - Uses BaseSallyOutUnitType / BaseSallyOutUnitStats / SallyOutGamemode (sallyout package).
 */
public class SetupPhaseUI {

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private static SetupPhaseUI instance;

    public static void openForAll(arc.math.geom.Rect zone1, arc.math.geom.Rect zone2, int budget) {
        if (instance == null) instance = new SetupPhaseUI();
        instance.open(zone1, zone2, budget);
    }

    public static void close() {
        if (instance != null) instance.closeUI();
    }

    // -------------------------------------------------------------------------
    // Unit catalogue
    // -------------------------------------------------------------------------

    private static final Seq<UnitType> CATALOGUE = new Seq<>();

    private static Seq<UnitType> getCatalogue() {
        if (CATALOGUE.isEmpty()) {
            for (UnitType type : Vars.content.units()) {
                if (type instanceof BaseSallyOutUnitType) CATALOGUE.add(type);
            }
            // Fallback for testing before content is registered
            if (CATALOGUE.isEmpty()) {
                CATALOGUE.addAll(
                    mindustry.content.UnitTypes.dagger,
                    mindustry.content.UnitTypes.mace,
                    mindustry.content.UnitTypes.fortress
                );
            }
        }
        return CATALOGUE;
    }

    // -------------------------------------------------------------------------
    // Instance state
    // -------------------------------------------------------------------------

    private Table   root;
    private Table   panel;
    private Label   budgetLabel;
    private Label   timerLabel;
    private boolean confirmPressed = false;
    private int     budget         = SallyOutGamemode.DEFAULT_SUPPLY_BUDGET;

    // Drag state
    private UnitType dragType = null;
    private float    dragX, dragY;
    private Image    dragIcon;

    private final List<Unit> placedUnits = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Build / open / close
    // -------------------------------------------------------------------------

    private void open(arc.math.geom.Rect zone1, arc.math.geom.Rect zone2, int budget) {
        this.budget = budget;
        buildRoot();
        Core.scene.add(root);
    }

    private void buildRoot() {
        root = new Table();
        root.setFillParent(true);

        // ── Left panel ───────────────────────────────────────────────────────
        panel = new Table(Styles.black3);
        panel.defaults().pad(4f);

        panel.add("Deploy Units").color(mindustry.graphics.Pal.accent).pad(8f).row();

        budgetLabel = new Label("Budget: " + budget);
        panel.add(budgetLabel).row();

        timerLabel = new Label("Time: --:--");
        panel.add(timerLabel).row();

        Table unitList = new Table();
        buildUnitList(unitList);
        ScrollPane scroll = new ScrollPane(unitList, Styles.smallPane);
        panel.add(scroll).growY().width(240f).row();

        panel.button("Confirm Ready", this::onConfirm).width(220f).height(44f).pad(8f).row();
        panel.add("[lightgray]Drag units into highlighted zone").wrap().width(220f).row();

        root.add(panel).left().top().expandY().pad(16f);
        root.add().grow();

        // ── Drag ghost icon ──────────────────────────────────────────────────
        dragIcon = new Image();
        // Arc Element.visible is a public FIELD — there is no setVisible(boolean) method
        dragIcon.visible = false;
        dragIcon.setSize(48f, 48f);
        root.addChild(dragIcon);

        // ── Global drag listener ─────────────────────────────────────────────
        root.addCaptureListener(new InputListener() {
            @Override
            public boolean mouseMoved(InputEvent event, float x, float y) {
                if (dragType != null) {
                    dragX = x;
                    dragY = y;
                    dragIcon.setPosition(x - 24f, y - 24f);
                }
                return false;
            }

            // touchUp returns VOID in Arc's InputListener — do NOT return a value
            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                if (dragType != null) {
                    tryPlaceUnit(x, y);
                    endDrag();
                }
            }
        });
    }

    private void buildUnitList(Table list) {
        for (UnitType type : getCatalogue()) {
            BaseSallyOutUnitStats ts = BaseSallyOutUnitType.BaseSallyOutUnitStats(type);
            Table card = new Table(Styles.black5);
            card.defaults().pad(2f);

            card.image(type.uiIcon).size(40f).left();

            Table statsCol = new Table();
            statsCol.add(type.localizedName).color(Color.white).left().row();
            statsCol.add("Cost: " + ts.cost + "  HP: " + (int)type.health)
                    .color(Color.lightGray).left().row();
            statsCol.add("ATK: " + (int)ts.meleeAttack + "  DEF: " + (int)ts.defense)
                    .color(Color.lightGray).left().row();
            statsCol.add("SPD: " + type.speed + (ts.flying ? "  [sky]FLY[]" : ""))
                    .color(Color.lightGray).left().row();
            card.add(statsCol).growX().left();

            card.addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                    SallyOutGamemode gm = SallyOutGamemode.get();
                    int remaining = (gm != null && Vars.player != null)
                        ? gm.remainingSupply(Vars.player.team())
                        : SetupPhaseUI.this.budget;
                    if (ts.cost > remaining) return false;
                    beginDrag(type, event.stageX, event.stageY);
                    return true;
                }
            });

            list.add(card).growX().pad(4f).row();
        }
    }

    // -------------------------------------------------------------------------
    // Drag helpers
    // -------------------------------------------------------------------------

    private void beginDrag(UnitType type, float sx, float sy) {
        dragType = type;
        dragX    = sx;
        dragY    = sy;
        dragIcon.setDrawable(type.uiIcon);
        // Use the public field, NOT setVisible()
        dragIcon.visible = true;
        dragIcon.setPosition(sx - 24f, sy - 24f);
    }

    private void endDrag() {
        dragType = null;
        // Use the public field, NOT setVisible()
        dragIcon.visible = false;
    }

    // -------------------------------------------------------------------------
    // Placement
    // -------------------------------------------------------------------------

    private void tryPlaceUnit(float screenX, float screenY) {
        if (dragType == null || Vars.player == null) return;

        Vec2 world = Core.camera.unproject(new Vec2(screenX, screenY));
        float wx = world.x;
        float wy = world.y;

        SallyOutGamemode gm = SallyOutGamemode.get();
        if (gm == null) return;

        if (!gm.inDeployZone(wx, wy, Vars.player.team())) {
            flashError("Place inside your [accent]highlighted[] zone!");
            return;
        }

        BaseSallyOutUnitStats ts = BaseSallyOutUnitType.BaseSallyOutUnitStats(dragType);
        if (!gm.trySpendSupply(Vars.player.team(), ts.cost)) {
            flashError("[red]Not enough supply budget[]");
            return;
        }

        Unit unit = dragType.spawn(Vars.player.team(), wx, wy);
        if (unit != null) {
            placedUnits.add(unit);
            SallyOutGamemode.registerUnit(unit);
        }

        updateBudgetLabel();
    }

    // -------------------------------------------------------------------------
    // Confirm
    // -------------------------------------------------------------------------

    private void onConfirm() {
        if (confirmPressed) return;
        confirmPressed = true;
        if (SallyOutGamemode.get() != null) {
            SallyOutGamemode.get().endSetupPhase();
        }
    }

    // -------------------------------------------------------------------------
    // UI helpers
    // -------------------------------------------------------------------------

    public void updateTimer(float secondsLeft) {
        int m = (int)(secondsLeft / 60f);
        int s = (int)(secondsLeft % 60f);
        if (timerLabel != null) timerLabel.setText(String.format("Time: %02d:%02d", m, s));
    }

    private void updateBudgetLabel() {
        if (budgetLabel == null || SallyOutGamemode.get() == null || Vars.player == null) return;
        int rem = SallyOutGamemode.get().remainingSupply(Vars.player.team());
        budgetLabel.setText("Budget: " + rem + " / " + SallyOutGamemode.DEFAULT_SUPPLY_BUDGET);
    }

    private void flashError(String msg) {
        Label toast = new Label(msg);
        toast.setColor(Color.red);
        toast.pack();
        toast.setPosition(
            Core.graphics.getWidth()  / 2f - toast.getWidth()  / 2f,
            Core.graphics.getHeight() / 2f + 40f
        );
        Core.scene.add(toast);
        Time.run(120f, toast::remove);
    }

    private void closeUI() {
        if (root != null) root.remove();
        confirmPressed = false;
    }
}
package mindustry.sallyout.ui;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.input.KeyCode;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.scene.ui.layout.WidgetGroup;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.sallyout.SallyOutGamemode;
import mindustry.sallyout.BaseSallyOutUnitType;
import mindustry.sallyout.BaseSallyOutUnitStats;
import mindustry.type.UnitType;
import mindustry.ui.Styles;

import java.util.ArrayList;
import java.util.List;
import mindustry.content.*;
import mindustry.core.*;
import mindustry.ctype.*;
import mindustry.entities.units.*;
import mindustry.game.EventType.*;
import mindustry.game.Schematic.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.input.Placement.*;
import mindustry.io.*;
import mindustry.world.*;
import mindustry.world.blocks.ConstructBlock.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.legacy.*;
import mindustry.world.blocks.power.*;
import mindustry.world.blocks.sandbox.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.blocks.storage.CoreBlock.*;
import mindustry.world.meta.*;

import java.io.*;
import java.util.zip.*;

import static mindustry.Vars.*;

/**
 * SetupPhaseUI
 * ============
 * Renders the unit-selection panel and handles drag-and-drop placement into the
 * deployment zone during the setup phase.
 *
 * Architecture:
 *   - A side panel lists available unit types with cost and key stats.
 *   - Players click-and-drag a unit card from the panel onto the game map.
 *   - When the cursor is released inside their deployment zone the unit is
 *     spawned at that position.
 *   - A supply budget counter updates in real time.
 *   - A "Confirm Ready" button is shown; once both players confirm the match starts.
 *   - The deployment zone is highlighted on the world renderer (via the overlay
 *     hook in {@link SetupPhaseOverlay}).
 *
 * This class is client-side only.  Placement is validated server-side via
 * SallyOutGamemode.trySpendSupply / inDeployZone.
 */
public class SetupPhaseUI {

    // -------------------------------------------------------------------------
    // Singleton / static entry points
    // -------------------------------------------------------------------------

    private static SetupPhaseUI instance;

    /** Opens the setup UI for the local player. Called from SallyOutGamemode after world load. */
    public static void openForAll(Rect zone1, Rect zone2, int budget) {
        if (instance == null) instance = new SetupPhaseUI();
        instance.open(zone1, zone2, budget);
    }

    public static void close() {
        if (instance != null) instance.closeUI();
    }

    // -------------------------------------------------------------------------
    // Available unit catalogue  (populate to match your BaseSallyOutUnitType defs)
    // -------------------------------------------------------------------------

    private static final Seq<UnitType> CATALOGUE = new Seq<>();

    static {
        // Populated at runtime from UnitTypes that are BaseSallyOutUnitType instances.
        // Called lazily in buildPanel().
    }

    private static Seq<UnitType> getCatalogue() {
        if (CATALOGUE.isEmpty()) {
            mindustry.content.UnitTypes.load(); // ensure content loaded
            for (var type : Vars.content.units()) {
                if (type instanceof BaseSallyOutUnitType) CATALOGUE.add(type);
            }
            // Fallback: include a few vanilla units so the UI is testable
            if (CATALOGUE.isEmpty()) {
                CATALOGUE.addAll(mindustry.content.UnitTypes.dagger,
                                 mindustry.content.UnitTypes.mace,
                                 mindustry.content.UnitTypes.fortress);
            }
        }
        return CATALOGUE;
    }

    // -------------------------------------------------------------------------
    // Instance state
    // -------------------------------------------------------------------------

    private Table              root;
    private Table              panel;
    private Label              budgetLabel;
    private Label              timerLabel;
    private boolean            confirmPressed  = false;
    private int                budget          = SallyOutGamemode.DEFAULT_SUPPLY_BUDGET;

    // Drag state
    private UnitType           dragType        = null;
    private float              dragX, dragY;           // screen coordinates
    private Image              dragIcon;

    // Placed unit tracking (for undo / removal during setup)
    private final List<Unit>   placedUnits     = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Build / open / close
    // -------------------------------------------------------------------------

    private void open(Rect zone1, Rect zone2, int budget) {
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

        // Title
        panel.add("Deploy Units").color(Pal.accent).pad(8f).row();

        // Budget counter
        budgetLabel = new Label("Budget: " + budget);
        panel.add(budgetLabel).row();

        // Timer
        timerLabel = new Label("Time: --:--");
        panel.add(timerLabel).row();

        // Unit list
        Table unitList = new Table();
        buildUnitList(unitList);
        ScrollPane scroll = new ScrollPane(unitList, Styles.smallPane);
        panel.add(scroll).growY().width(240f).row();

        // Confirm button
        panel.button("Confirm Ready", this::onConfirm).width(220f).height(44f).pad(8f).row();

        // Instructions
        panel.add("[lightgray]Drag units into highlighted zone").wrap().width(220f).row();

        root.add(panel).left().top().expandY().pad(16f);
        root.add().grow(); // spacer

        // ── Drag ghost icon ──────────────────────────────────────────────────
        dragIcon = new Image();
        dragIcon.visible = false;
        dragIcon.setSize(48f, 48f);
        root.addChild(dragIcon);

        // ── Global drag listener (attached to root) ──────────────────────────
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

            // Unit icon
            card.image(type.uiIcon).size(40f).left();

            // Stats column
            Table statsCol = new Table();
            statsCol.add(type.localizedName).color(Color.white).left().row();
            statsCol.add("Cost: " + ts.cost
                         + "  HP: " + (int)type.health).color(Color.lightGray).left().row();
            statsCol.add("ATK: " + (int)ts.meleeAttack
                         + "  DEF: " + (int)ts.defense).color(Color.lightGray).left().row();
            statsCol.add("SPD: " + type.speed
                         + (ts.flying ? "  [sky]FLY[]" : "")).color(Color.lightGray).left().row();
            card.add(statsCol).growX().left();

            // Drag initiator
          card.addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                    int remaining = SallyOutGamemode.get() != null
                        ? SallyOutGamemode.get().remainingSupply(Vars.player.team)
                        : budget;

                    if (remaining >= ts.cost) {
                        beginDrag(type); // Call the simplified version
                        return true; 
                    }
                    return false;
                }
            });
            list.add(card).growX().pad(4f).row();
        }
    }



    // -------------------------------------------------------------------------
    // Drag helpers
    // -------------------------------------------------------------------------

    // private void beginDrag(UnitType type, float sx, float sy) {
    //     dragType = type;
    //     dragX    = sx;
    //     dragY    = sy;
    //     dragIcon.setDrawable(type.uiIcon);
    //     dragIcon.visible = true;
    //     dragIcon.setPosition(sx - 24f, sy - 24f);
    // }
    private void beginDrag(UnitType type) {
        this.dragType = type;
        dragIcon.setDrawable(type.uiIcon);
        dragIcon.visible = true;
        // Move icon to current mouse position immediately
        Vec2 mouse = Core.input.mouse();
        dragIcon.setPosition(mouse.x - 24f, mouse.y - 24f);
    }

    private void endDrag() {
        dragType = null;
        dragIcon.visible = false;
    }

    // -------------------------------------------------------------------------
    // Placement
    // -------------------------------------------------------------------------

    private void tryPlaceUnit(float screenX, float screenY) {
        if (dragType == null || Vars.player == null) return;

        // Convert screen → world coordinates
        Vec2 world = Core.camera.unproject(new Vec2(screenX, screenY));
        float wx = world.x;
        float wy = world.y;

        SallyOutGamemode gm = SallyOutGamemode.get();
        if (gm == null) return;

        if (!gm.inDeployZone(wx, wy, Vars.player.team())) {
            // Flash a "wrong zone" hint
            flashError("Place inside your [accent]highlighted[] zone!");
            return;
        }

        BaseSallyOutUnitStats ts = BaseSallyOutUnitType.BaseSallyOutUnitStats(dragType);
        if (!gm.trySpendSupply(Vars.player.team(), ts.cost)) {
            flashError("[red]Not enough supply budget[]");
            return;
        }

        // Spawn the unit
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
        // Broadcast to server that this player is ready.
        // In a full implementation this would send a net packet; for now we
        // directly call endSetupPhase when both players confirm.
        if (SallyOutGamemode.get() != null) {
            SallyOutGamemode.get().endSetupPhase();
        }
    }

    // -------------------------------------------------------------------------
    // UI upkeep
    // -------------------------------------------------------------------------

    /** Called each tick to update the timer label.  Hook this from a render/update event. */
    public void updateTimer(float secondsLeft) {
        int m = (int)(secondsLeft / 60f);
        int s = (int)(secondsLeft % 60f);
        timerLabel.setText(String.format("Time: %02d:%02d", m, s));
    }

    private void updateBudgetLabel() {
        if (SallyOutGamemode.get() == null || Vars.player == null) return;
        int rem = SallyOutGamemode.get().remainingSupply(Vars.player.team());
        budgetLabel.setText("Budget: " + rem + " / " + SallyOutGamemode.DEFAULT_SUPPLY_BUDGET);
    }

    private void flashError(String msg) {
        // Simple toast: add a temporary label that fades out
        Label toast = new Label(msg);
        toast.setColor(Color.red);
        toast.pack();
        toast.setPosition(Core.graphics.getWidth() / 2f - toast.getWidth() / 2f,
                          Core.graphics.getHeight() / 2f + 40f);
        Core.scene.add(toast);
        Time.run(120f, toast::remove);
    }

    private void closeUI() {
        if (root != null) root.remove();
    }
}
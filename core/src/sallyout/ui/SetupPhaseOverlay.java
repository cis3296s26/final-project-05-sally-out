package sallyout.ui;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Rect;
import arc.util.Time;
import mindustry.game.EventType;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import sallyout.BaseSallyOutUnitEntity;
import sallyout.SallyOutGamemode;

/**
 * SetupPhaseOverlay
 * =================
 * Hooks into Mindustry's world-draw event to:
 *   - Paint the two highlighted deployment zones during setup.
 *   - Draw HP / Organisation / Stamina bars above each unit during combat.
 * Register once via {@link #init()}.
 * Note: Pal.healthBack does NOT exist in Mindustry. Use Color.darkGray for bar backgrounds.
 */
public class SetupPhaseOverlay {

    private static final Color ZONE1_FILL   = new Color(0.2f, 0.5f, 1.0f, 0.15f);
    private static final Color ZONE1_BORDER = new Color(0.3f, 0.6f, 1.0f, 0.90f);
    private static final Color ZONE2_FILL   = new Color(1.0f, 0.3f, 0.2f, 0.15f);
    private static final Color ZONE2_BORDER = new Color(1.0f, 0.4f, 0.3f, 0.90f);

    /** Dark colour used for empty portions of status bars. Replaces non-existent Pal.healthBack. */
    private static final Color BAR_EMPTY = new Color(0.15f, 0.15f, 0.15f, 1f);

    public static void init() {
        Events.run(EventType.Trigger.drawOver, SetupPhaseOverlay::draw);
    }

    private static void draw() {
        SallyOutGamemode gm = SallyOutGamemode.get();
        if (gm == null) return;

        // ── Deployment zones ────────────────────────────────────────────────
        if (gm.isSetupPhase()) {
            drawZone(gm.getTeam1Zone(), ZONE1_FILL, ZONE1_BORDER);
            drawZone(gm.getTeam2Zone(), ZONE2_FILL, ZONE2_BORDER);
        }

        // ── Per-unit status bars ─────────────────────────────────────────────
        Groups.unit.each(unit -> {
            BaseSallyOutUnitEntity entity = SallyOutGamemode.getEntity(unit);
            if (entity != null) drawUnitBars(unit, entity);
        });
    }

    // -------------------------------------------------------------------------
    // Zone rendering
    // -------------------------------------------------------------------------

    private static void drawZone(Rect zone, Color fill, Color border) {
        if (zone == null) return;

        float pulse = 0.5f + 0.5f * Mathf.sin(Time.time * 0.05f);

        Draw.color(fill.r, fill.g, fill.b, fill.a * pulse);
        Fill.rect(zone.x + zone.width / 2f, zone.y + zone.height / 2f,
                  zone.width, zone.height);

        Draw.color(border);
        Lines.stroke(2f);
        Lines.rect(zone.x, zone.y, zone.width, zone.height);

        Draw.reset();
    }

    // -------------------------------------------------------------------------
    // Unit status bars
    // -------------------------------------------------------------------------

    private static void drawUnitBars(Unit unit, BaseSallyOutUnitEntity entity) {
        // unit.hitSize is a public field
        float x  = unit.x;
        float y  = unit.y + unit.hitSize + 4f;
        float bw = unit.hitSize * 2.2f;
        float bh = 3f;
        float gap = 1.5f;

        // HP bar — green/red gradient based on fraction
        float hpFrac = unit.health() / unit.maxHealth;
        Color hpColor = new Color(1f - hpFrac, hpFrac, 0f, 1f); // red→green
        drawBar(x, y,                    bw, bh, hpFrac, hpColor,       BAR_EMPTY);

        // Organisation bar — accent (cyan)
        float orgFrac = entity.organization / entity.stats.maxOrganization;
        drawBar(x, y + (bh + gap),       bw, bh, orgFrac, Pal.accent,   BAR_EMPTY);

        // Stamina bar — yellow
        float stamFrac = entity.stamina / entity.stats.maxStamina;
        drawBar(x, y + 2f * (bh + gap),  bw, bh, stamFrac, Color.yellow, BAR_EMPTY);

        // Routing indicator — red pulsing circle
        if (entity.routing) {
            Draw.color(Color.red);
            Lines.stroke(1.5f);
            Lines.circle(x, y + 16f, 6f);
        }

        Draw.reset();
    }

    private static void drawBar(float cx, float y, float w, float h,
                                 float frac, Color fg, Color bg) {
        float x = cx - w / 2f;
        // Background
        Draw.color(bg);
        Fill.rect(cx, y + h / 2f, w, h);
        // Foreground
        if (frac > 0f) {
            Draw.color(fg);
            Fill.rect(x + w * frac / 2f, y + h / 2f, w * frac, h);
        }
    }
}
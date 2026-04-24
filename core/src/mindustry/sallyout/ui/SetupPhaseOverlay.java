package mindustry.sallyout.ui;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.geom.Rect;
import arc.util.Time;
import mindustry.game.EventType;
import mindustry.graphics.Pal;
import mindustry.sallyout.SallyOutGamemode;

/**
 * SetupPhaseOverlay
 * =================
 * Hooks into Mindustry's world-draw event to paint the two deployment zones
 * and unit stat bars (organisation, stamina, HP) on top of units during play.
 *
 * Register once via {@link #init()}.
 */
public class SetupPhaseOverlay {

    private static final Color ZONE1_FILL   = new Color(0.2f, 0.5f, 1.0f, 0.15f);
    private static final Color ZONE1_BORDER = new Color(0.3f, 0.6f, 1.0f, 0.9f);
    private static final Color ZONE2_FILL   = new Color(1.0f, 0.3f, 0.2f, 0.15f);
    private static final Color ZONE2_BORDER = new Color(1.0f, 0.4f, 0.3f, 0.9f);

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
            var entity = SallyOutGamemode.getEntity(unit);
            if (entity == null) return;
            drawUnitBars(unit, entity);
        });
    }

    // -------------------------------------------------------------------------
    // Zone rendering
    // -------------------------------------------------------------------------

    private static void drawZone(Rect zone, Color fill, Color border) {
        if (zone == null) return;

        // Pulsing alpha for fill
        float pulse = 0.5f + 0.5f * arc.math.Mathf.sin(Time.time * 0.05f);

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

    private static void drawUnitBars(mindustry.gen.Unit unit, mindustry.sallyout.BaseSallyOutUnitEntity entity) {
        float x  = unit.x();
        float y  = unit.y() + unit.hitSize() + 4f;
        float bw = unit.hitSize() * 2.2f;   // bar width
        float bh = 3f;                       // bar height
        float gap = 1.5f;

        // HP bar (green → red)
        float hpFrac = unit.health() / unit.maxHealth();
        drawBar(x, y,             bw, bh, hpFrac, Pal.health,     Color.darkGray);

        // Organisation bar (cyan)
        float orgFrac = entity.organization / entity.stats.maxOrganization;
        drawBar(x, y + bh + gap, bw, bh, orgFrac,
                mindustry.graphics.Pal.accent, Color.darkGray);

        // Stamina bar (yellow)
        float stamFrac = entity.stamina / entity.stats.maxStamina;
        drawBar(x, y + 2f * (bh + gap), bw, bh, stamFrac,
                Color.yellow, Color.darkGray);

        // Routing indicator
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
        Draw.color(bg);
        Fill.rect(cx, y + h / 2f, w, h);
        Draw.color(fg);
        Fill.rect(x + w * frac / 2f, y + h / 2f, w * frac, h);
    }
}
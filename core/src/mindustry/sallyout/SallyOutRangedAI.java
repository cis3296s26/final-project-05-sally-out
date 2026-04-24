package mindustry.sallyout.ai;

import arc.math.geom.Vec2;
import mindustry.entities.units.AIController;
import mindustry.gen.Unit;
import mindustry.sallyout.SallyOutGamemode;
import mindustry.sallyout.BaseSallyOutUnitEntity;
import mindustry.sallyout.BaseSallyOutUnitType;
import mindustry.sallyout.BaseSallyOutUnitStats;

/**
 * TacticalRangedAI
 * ================
 * AI controller for player-commanded units with ranged capabilities.
 *
 * Behaviour priority:
 *   1. If routing → flee away from nearest enemy.
 *   2. If in melee → stay and fight (handled by SallyOutGamemode).
 *   3. If target in rangeRadius → use ranged weapon.
 *   4. Otherwise → advance toward nearest enemy (default AIController logic).
 *
 * Player commands override this AI via the standard Mindustry command system.
 */
public class SallyOutRangedAI extends AIController {

    private static final Vec2 tmpVec = new Vec2();

    @Override
    public void updateMovement(Unit unit) {
        BaseSallyOutUnitEntity entity = SallyOutGamemode.getEntity(unit);
        if (entity == null) { super.updateMovement(unit); return; }

        BaseSallyOutUnitStats stats = entity.stats;

        // ── Routing: flee away from closest enemy ────────────────────────────
        if (entity.routing) {
            Unit nearest = findNearestEnemy(unit);
            if (nearest != null) {
                tmpVec.set(unit.x() - nearest.x(), unit.y() - nearest.y())
                      .nor().scl(unit.type().speed * BaseSallyOutUnitEntity.ROUTE_SPEED_MULTI);
                unit.vel(tmpVec);
            }
            return;
        }

        // ── Flying landing/takeoff management ───────────────────────────────
        if (stats.flying) {
            updateFlyingBehavior(unit, entity);
            return;
        }

        // ── Ranged attack ─────────────────────────────────────────────────────
        if (stats.rangeAttack > 0f && stats.rangeRadius > 0f) {
            Unit rangeTarget = findEnemyInRange(unit, stats.rangeRadius);
            if (rangeTarget != null) {
                // Face and shoot target – Mindustry's WeaponsComp handles firing
                unit.aim(rangeTarget.x(), rangeTarget.y());
                unit.vel().setZero(); // hold position when shooting
                return;
            }
        }

        // ── Default advance behaviour ─────────────────────────────────────────
        super.updateMovement(unit);
    }

    // Flying state machine
    private void updateFlyingBehavior(Unit unit, BaseSallyOutUnitEntity entity) {
        Unit nearest = findNearestEnemy(unit);
        if (nearest == null) return;

        float dst = unit.dst(nearest);
        float meleeRange = unit.hitSize() + nearest.hitSize();

        if (entity.isFlying) {
            // Approach until melee range, then land
            if (dst <= meleeRange * 1.2f) {
                entity.land();
            } else {
                moveToward(unit, nearest.x(), nearest.y());
            }
        } else {
            // Landed – fight until routing or enemy moves away
            if (entity.routing || dst > meleeRange * 2f) {
                entity.takeOff();
            }
        }
    }

    // Helpers
    private Unit findNearestEnemy(Unit unit) {
        final Unit[] best = {null};
        final float[] bestDst = {Float.MAX_VALUE};
        mindustry.gen.Groups.unit.each(other -> {
            if (other.team() == unit.team() || other.dead()) return;
            float d = unit.dst(other);
            if (d < bestDst[0]) { bestDst[0] = d; best[0] = other; }
        });
        return best[0];
    }

    private Unit findEnemyInRange(Unit unit, float range) {
        final Unit[] best = {null};
        final float[] bestDst = {Float.MAX_VALUE};
        mindustry.gen.Groups.unit.each(other -> {
            if (other.team() == unit.team() || other.dead()) return;
            float d = unit.dst(other);
            if (d <= range && d < bestDst[0]) { bestDst[0] = d; best[0] = other; }
        });
        return best[0];
    }

    private void moveToward(Unit unit, float tx, float ty) {
        tmpVec.set(tx - unit.x(), ty - unit.y()).nor().scl(unit.type().speed);
        unit.vel(tmpVec);
    }
}

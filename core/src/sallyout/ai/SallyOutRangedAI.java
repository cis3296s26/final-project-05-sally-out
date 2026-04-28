package sallyout.ai;

import arc.math.geom.Vec2;
import mindustry.entities.units.AIController;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import sallyout.BaseSallyOutUnitEntity;
import sallyout.BaseSallyOutUnitStats;
import sallyout.SallyOutGamemode;

/**
 * SallyOutRangedAI
 * ================
 * AI controller for ranged units (archers etc.) in the Sally-Out PvP mode.
 *
 * AIController.updateMovement() takes ZERO parameters.
 * The unit being controlled is accessed via the inherited protected field `this.unit`.
 * Never write updateMovement(Unit unit) — that does not match the superclass.
 *
 * Behaviour priority:
 *   1. If routing → flee away from nearest enemy.
 *   2. If flying → handle land/takeoff state machine.
 *   3. If enemy in rangeRadius → hold position and aim at target.
 *   4. Otherwise → advance toward nearest enemy.
 */
public class SallyOutRangedAI extends AIController {

    private static final Vec2 tmpVec = new Vec2();

    @Override
    public void updateMovement() {
        // 'unit' is the protected field inherited from AIController — do NOT add it as a parameter
        BaseSallyOutUnitEntity entity = SallyOutGamemode.getEntity(unit);
        if (entity == null) return;

        BaseSallyOutUnitStats stats = entity.stats;

        // ── 1. Routing: flee away from closest enemy ─────────────────────────
        if (entity.routing) {
            Unit nearest = findNearestEnemy();
            if (nearest != null) {
                // unit.x, unit.y are public fields; unit.type.speed is a field
                tmpVec.set(unit.x - nearest.x, unit.y - nearest.y)
                      .nor().scl(unit.type.speed * BaseSallyOutUnitEntity.ROUTE_SPEED_MULTI);
                unit.vel.set(tmpVec);
            }
            return;
        }

        // ── 2. Flying: state machine (land to fight, take off to move) ────────
        if (stats.flying) {
            updateFlyingBehavior(entity);
            return;
        }

        // ── 3. Ranged: hold position, aim at target ───────────────────────────
        if (stats.rangeAttack > 0f && stats.rangeRadius > 0f) {
            Unit rangeTarget = findEnemyInRange(stats.rangeRadius);
            if (rangeTarget != null) {
                unit.aim(rangeTarget.x, rangeTarget.y);
                unit.vel.setZero();
                return;
            }
        }

        // ── 4. Default: advance toward nearest enemy ──────────────────────────
        Unit nearest = findNearestEnemy();
        if (nearest != null) {
            moveToward(nearest.x, nearest.y);
        }
    }

    // -------------------------------------------------------------------------
    // Flying state machine
    // -------------------------------------------------------------------------

    private void updateFlyingBehavior(BaseSallyOutUnitEntity entity) {
        Unit nearest = findNearestEnemy();
        if (nearest == null) return;

        float dst        = unit.dst(nearest);
        float meleeRange = unit.hitSize + nearest.hitSize;

        if (entity.isFlying) {
            if (dst <= meleeRange * 1.2f) {
                entity.land();
            } else {
                moveToward(nearest.x, nearest.y);
            }
        } else {
            if (entity.routing || dst > meleeRange * 2f) {
                entity.takeOff();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Unit findNearestEnemy() {
        Unit[] best   = {null};
        float[] bestD = {Float.MAX_VALUE};
        Groups.unit.each(other -> {
            if (other.team() == unit.team() || other.dead()) return;
            float d = unit.dst(other);
            if (d < bestD[0]) { bestD[0] = d; best[0] = other; }
        });
        return best[0];
    }

    private Unit findEnemyInRange(float range) {
        Unit[] best   = {null};
        float[] bestD = {Float.MAX_VALUE};
        Groups.unit.each(other -> {
            if (other.team() == unit.team() || other.dead()) return;
            float d = unit.dst(other);
            if (d <= range && d < bestD[0]) { bestD[0] = d; best[0] = other; }
        });
        return best[0];
    }

    private void moveToward(float tx, float ty) {
        tmpVec.set(tx - unit.x, ty - unit.y).nor().scl(unit.type.speed);
        unit.vel.set(tmpVec);
    }
}
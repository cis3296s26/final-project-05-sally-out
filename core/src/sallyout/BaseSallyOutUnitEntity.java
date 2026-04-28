package sallyout;

import arc.math.Angles;
import arc.math.Mathf;
import mindustry.gen.Groups;
import mindustry.gen.Unit;

/**
 * Runtime mutable state for one unit in the Sally-Out PvP mode.
 *
 * Stored as a sidecar keyed by unit.id() in SallyOutGamemode.entityMap.
 * Updated every game tick by SallyOutGamemode.
 *
 * Key note on Mindustry generated Unit fields vs methods:
 *   x, y, rotation, hitSize, type, vel  →  public FIELDS (NOT getter methods)
 *   health(), team(), id(), dead()       →  generated methods
 *   vel is a mutable Vec2 field; modify in-place (vel.set / vel.scl / vel.setLength)
 */
public class BaseSallyOutUnitEntity {

    // -------------------------------------------------------------------------
    // References
    // -------------------------------------------------------------------------

    public final Unit unit;
    public final BaseSallyOutUnitStats stats;

    // -------------------------------------------------------------------------
    // Mutable runtime state
    // -------------------------------------------------------------------------

    public float   organization;
    public float   stamina;
    public boolean isCharging      = false;
    public boolean isFlying        = false;
    public float   chargeCooldown  = 0f;
    public float   chargeTimer     = 0f;
    public boolean chargeBonusUsed = false;
    public boolean routing         = false;

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    public static final float CHARGE_DURATION     = 60f;
    public static final float CHARGE_COOLDOWN     = 180f;
    public static final float ROUTE_SPEED_MULTI   = 1.6f;
    public static final float ROUTE_ORG_THRESHOLD = 10f;
    public static final float RALLY_ORG_THRESHOLD = 30f;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public BaseSallyOutUnitEntity(Unit unit) {
        this.unit         = unit;
        // unit.type is a public field — do NOT call unit.type()
        this.stats        = BaseSallyOutUnitType.BaseSallyOutUnitStats(unit.type);
        this.organization = stats.maxOrganization;
        this.stamina      = stats.maxStamina;
        this.isFlying     = stats.flying;
    }

    // -------------------------------------------------------------------------
    // Tick update
    // -------------------------------------------------------------------------

    public void update() {
        if (!unit.isAdded() || unit.dead()) return;
        updateStamina();
        updateOrganization();
        updateCharge();
        updateRouting();
        applySpeedModifiers();
    }

    // -------------------------------------------------------------------------
    // Stamina
    // -------------------------------------------------------------------------

    private void updateStamina() {
        // unit.vel is a public Vec2 field — do NOT call unit.vel()
        boolean moving = unit.vel.len() > 0.5f;
        boolean melee  = SallyOutGamemode.isInMelee(unit);

        if (isCharging)  stamina -= stats.staminaChargeDrain;
        else if (melee)  stamina -= stats.staminaMeleeDrain;
        else if (moving) stamina -= stats.staminaMoveDrain;
        else             stamina += stats.staminaRegenPerTick;

        stamina = Mathf.clamp(stamina, 0f, stats.maxStamina);
    }

    // -------------------------------------------------------------------------
    // Organisation
    // -------------------------------------------------------------------------

    private void updateOrganization() {
        if (!routing) organization += stats.organizationRegen;
        organization = Mathf.clamp(organization, 0f, stats.maxOrganization);
    }

    public void drainOrg(float amount) {
        organization = Math.max(0f, organization - amount);
    }

    // -------------------------------------------------------------------------
    // Charge
    // -------------------------------------------------------------------------

    private void updateCharge() {
        if (chargeCooldown > 0f) chargeCooldown--;
        if (isCharging) {
            chargeTimer--;
            if (chargeTimer <= 0f) endCharge();
        }
    }

    public boolean beginCharge() {
        if (isCharging || routing)             return false;
        if (chargeCooldown > 0f)               return false;
        if (stamina < stats.maxStamina * 0.3f) return false;
        isCharging      = true;
        chargeTimer     = CHARGE_DURATION;
        chargeBonusUsed = false;
        return true;
    }

    private void endCharge() {
        isCharging     = false;
        chargeCooldown = CHARGE_COOLDOWN;
    }

    // -------------------------------------------------------------------------
    // Routing
    // -------------------------------------------------------------------------

    private void updateRouting() {
        if (!routing && organization <= ROUTE_ORG_THRESHOLD) {
            routing    = true;
            isCharging = false;
        } else if (routing && organization >= RALLY_ORG_THRESHOLD) {
            routing = false;
        }
    }

    // -------------------------------------------------------------------------
    // Speed modifiers
    // -------------------------------------------------------------------------

    private void applySpeedModifiers() {
        // unit.type is a public field; unit.type.speed is correct
        float baseSpeed = unit.type.speed;

        // unit.vel is a mutable Vec2 public field.
        // setLength() mutates in-place and returns the Vec2 — there is NO vel(Vec2) setter.
        if (routing) {
            unit.vel.setLength(baseSpeed * ROUTE_SPEED_MULTI);
        } else if (isCharging) {
            unit.vel.setLength(baseSpeed * 1.8f);
        }

        if (stamina < stats.maxStamina * 0.2f) {
            float penalty = 0.5f + 0.5f * (stamina / (stats.maxStamina * 0.2f));
            unit.vel.scl(penalty);  // scl() mutates in-place
        }
    }

    // -------------------------------------------------------------------------
    // Directional damage
    // -------------------------------------------------------------------------

    public void applyDirectionalDamage(float rawDamage,
                                       float attackerX, float attackerY,
                                       boolean isChargingHit,
                                       BaseSallyOutUnitStats attackerStats) {
        // unit.x, unit.y, unit.rotation are public fields — do NOT call unit.x() etc.
        float attackAngle = Angles.angle(unit.x, unit.y, attackerX, attackerY);
        float dmgMulti    = stats.damageMultiplierForAttack(attackAngle, unit.rotation);
        float orgMulti    = stats.orgMultiplierForAttack(attackAngle, unit.rotation);

        float effectiveDefense = isChargingHit ? stats.defense * 0.5f : stats.defense;

        float chargeMod = 1.0f;
        if (isChargingHit) {
            chargeMod = attackerStats.chargeBonus * stats.chargeResistance;
        }

        float finalDamage   = Math.max(0f, (rawDamage * dmgMulti * chargeMod) - effectiveDefense);
        float finalOrgDrain = attackerStats.meleeOrgDrain * orgMulti;

        // health() and health(float) ARE generated methods — this is correct
        unit.health(unit.health() - finalDamage);
        drainOrg(finalOrgDrain);

        if (unit.health() <= 0f) unit.kill();
    }

    // -------------------------------------------------------------------------
    // Org aura broadcast
    // -------------------------------------------------------------------------

    public void broadcastOrgAura() {
        if (stats.organizationRadiusBonus == 0f || stats.organizationRadius == 0f) return;
        float r = stats.organizationRadius;

        Groups.unit.each(ally -> {
            // team() and dead() are generated methods — correct
            if (ally == unit || ally.team() != unit.team() || ally.dead()) return;
            if (unit.dst(ally) <= r) {
                BaseSallyOutUnitEntity allyEntity = SallyOutGamemode.getEntity(ally);
                if (allyEntity != null) {
                    allyEntity.drainOrg(-stats.organizationRadiusBonus);
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Flying helpers
    // -------------------------------------------------------------------------

    public void land() {
        if (!stats.flying) return;
        isFlying = false;
    }

    public void takeOff() {
        if (!stats.flying) return;
        isFlying = true;
    }

    public boolean isMeleeTargetableBy(BaseSallyOutUnitEntity attacker) {
        if (!isFlying) return true;
        return attacker.stats.canAttackFlying;
    }

    // -------------------------------------------------------------------------
    // Rectangular hitbox helper
    // -------------------------------------------------------------------------

    public boolean rectContains(float wx, float wy) {
        // unit.hitSize, unit.rotation, unit.x, unit.y are all public fields
        float hs  = unit.hitSize;
        float hw  = hs;
        float hh  = hs * 0.6f;

        float rad   = (float) Math.toRadians(unit.rotation);
        float cosR  = (float) Math.cos(rad);
        float sinR  = (float) Math.sin(rad);
        float dx    = wx - unit.x;
        float dy    = wy - unit.y;
        float localX =  dx * cosR + dy * sinR;
        float localY = -dx * sinR + dy * cosR;

        return Math.abs(localX) <= hw && Math.abs(localY) <= hh;
    }
}
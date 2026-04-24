package mindustry.sallyout;

import arc.math.Angles;
import arc.math.geom.Vec2;
import mindustry.gen.*;
import mindustry.type.UnitType;

/**
 * Runtime mutable state for one unit participating in the Sally-Out PvP mode.
 *
 * A TacticalUnitEntity is created and stored in {@link TacticalGamemode#entityMap}
 * alongside every living unit.  It mirrors organisational / stamina / charge state
 * and is updated each game tick by TacticalGamemode.
 *
 * Rectangular hitbox:
 *   Mindustry units already expose hitSize (radius).  We model the rectangular
 *   hitbox as width = hitSize * 2 and height = hitSize * 1.2 so that the
 *   contact zone naturally captures front/rear differentiation without
 *   re-implementing the physics engine.
 *
 * Directional damage:
 *   When damage is applied via {@link #applyDirectionalDamage} the angle from
 *   the attacker to this unit is compared to this unit's facing rotation to
 *   determine front / flank / rear multipliers from TacticalUnitStats.
 */
public class BaseSallyOutUnitEntity {

    // -------------------------------------------------------------------------
    // References
    // -------------------------------------------------------------------------

    /** The Mindustry unit this state belongs to. */
    public final Unit unit;

    /** Cached tactical stat blueprint for this unit's type. */
    public final BaseSallyOutUnitStats stats;

    // -------------------------------------------------------------------------
    // Mutable runtime state
    // -------------------------------------------------------------------------

    public float organization;
    public float stamina;

    /** True while the unit is executing a charge action. */
    public boolean isCharging       = false;

    /**
     * True while the unit is in flight (only relevant when stats.flying == true).
     * A flying unit must set isLanded = true before melee/charge resolves.
     */
    public boolean isFlying         = false;

    /**
     * Cooldown ticks before the unit may charge again after the last charge ended.
     */
    public float chargeCooldown     = 0f;

    /** Ticks remaining in the current charge. */
    public float chargeTimer        = 0f;

    /** True if the charge-bonus hit has already been applied this charge cycle. */
    public boolean chargeBonusUsed  = false;

    /** True while the unit is routing (fleeing). */
    public boolean routing          = false;

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /** Ticks a charge lasts (1 second at 60 UPS). */
    public static final float CHARGE_DURATION     = 60f;

    /** Ticks of charge cooldown after a charge ends. */
    public static final float CHARGE_COOLDOWN     = 180f;

    /** Speed multiplier applied while routing. */
    public static final float ROUTE_SPEED_MULTI   = 1.6f;

    /** Org threshold below which routing begins. */
    public static final float ROUTE_ORG_THRESHOLD = 10f;

    /** Org threshold above which routing stops (hysteresis). */
    public static final float RALLY_ORG_THRESHOLD = 30f;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public BaseSallyOutUnitEntity(Unit unit) {
        this.unit  = unit;
        this.stats = BaseSallyOutUnitType.tacticalStats(unit.type());
        this.organization = stats.maxOrganization;
        this.stamina      = stats.maxStamina;
        this.isFlying     = stats.flying;
    }

    // -------------------------------------------------------------------------
    // Tick update  (called from TacticalGamemode each game tick)
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
        boolean moving  = unit.vel().len() > 0.5f;
        boolean melee   = isMeleeing();

        if (isCharging) {
            stamina -= stats.staminaChargeDrain;
        } else if (melee) {
            stamina -= stats.staminaMeleeDrain;
        } else if (moving) {
            stamina -= stats.staminaMoveDrain;
        } else {
            stamina += stats.staminaRegenPerTick;
        }
        stamina = arc.math.Mathf.clamp(stamina, 0f, stats.maxStamina);
    }

    /** Approximation: unit is meleeing if it has any nearby hostile it last attacked. */
    private boolean isMeleeing() {
        // Delegate to TacticalGamemode which tracks active combats.
        return SallyOutGamemode.isInMelee(unit);
    }

    // -------------------------------------------------------------------------
    // Organisation
    // -------------------------------------------------------------------------

    private void updateOrganization() {
        if (!routing) {
            organization += stats.organizationRegen;
        }
        organization = arc.math.Mathf.clamp(organization, 0f, stats.maxOrganization);
    }

    public void drainOrg(float amount) {
        organization -= amount;
        organization = Math.max(0f, organization);
    }

    // -------------------------------------------------------------------------
    // Charge
    // -------------------------------------------------------------------------

    private void updateCharge() {
        if (chargeCooldown > 0f) chargeCooldown--;

        if (isCharging) {
            chargeTimer--;
            if (chargeTimer <= 0f) {
                endCharge();
            }
        }
    }

    /**
     * Initiates a charge if the unit is not routing, has sufficient stamina,
     * and the cooldown has expired.  Returns true if the charge started.
     */
    public boolean beginCharge() {
        if (isCharging || routing) return false;
        if (chargeCooldown > 0f)  return false;
        if (stamina < stats.maxStamina * 0.3f) return false;

        isCharging     = true;
        chargeTimer    = CHARGE_DURATION;
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
            routing = true;
            isCharging = false;
        } else if (routing && organization >= RALLY_ORG_THRESHOLD) {
            routing = false;
        }
    }

    // -------------------------------------------------------------------------
    // Speed modifiers
    // -------------------------------------------------------------------------

    private void applySpeedModifiers() {
        float baseSpeed = unit.type().speed;

        if (routing) {
            unit.vel(unit.vel().setLength(baseSpeed * ROUTE_SPEED_MULTI));
        } else if (isCharging) {
            unit.vel(unit.vel().setLength(baseSpeed * 1.8f));
        }
        // Low stamina causes a speed penalty
        if (stamina < stats.maxStamina * 0.2f) {
            float penalty = 0.5f + 0.5f * (stamina / (stats.maxStamina * 0.2f));
            unit.vel(unit.vel().scl(penalty));
        }
    }

    // -------------------------------------------------------------------------
    // Directional damage
    // -------------------------------------------------------------------------

    /**
     * Applies damage to the unit taking direction into account.
     *
     * @param rawDamage    base damage amount before multipliers
     * @param attackerX    world X of the attacker (for angle calculation)
     * @param attackerY    world Y of the attacker
     * @param isChargingHit whether this hit benefits from the attacker's chargeBonus
     * @param attackerStats BaseSallyOutUnitStats of the attacking unit
     */
    public void applyDirectionalDamage(float rawDamage,
                                       float attackerX, float attackerY,
                                       boolean isChargingHit,
                                       BaseSallyOutUnitStats attackerStats) {
        // Angle FROM this unit TOWARD the attacker (so 0° = attacker is dead ahead).
        float attackAngle = Angles.angle(unit.x(), unit.y(), attackerX, attackerY);

        // Directional multipliers
        float dmgMulti = stats.damageMultiplierForAttack(attackAngle, unit.rotation());
        float orgMulti = stats.orgMultiplierForAttack(attackAngle, unit.rotation());

        // Defense reduction
        float effectiveDefense = isChargingHit ? stats.defense * 0.5f : stats.defense;

        // Charge bonus on the attacker side
        float chargeMod = 1.0f;
        if (isChargingHit && !attackerStats.equals(stats)) {
            chargeMod = attackerStats.chargeBonus * stats.chargeResistance;
        }

        float finalDamage = Math.max(0f, (rawDamage * dmgMulti * chargeMod) - effectiveDefense);
        float finalOrgDrain = attackerStats.meleeOrgDrain * orgMulti;

        unit.health(unit.health() - finalDamage);
        drainOrg(finalOrgDrain);

        if (unit.health() <= 0f) unit.kill();
    }

    // -------------------------------------------------------------------------
    // Aura – broadcast org bonus to nearby friendlies
    // -------------------------------------------------------------------------

    /**
     * Must be called every N ticks (e.g. every 30 ticks) by TacticalGamemode.
     * Iterates nearby allied units and adjusts their organization.
     */
    public void broadcastOrgAura() {
        if (stats.organizationRadiusBonus == 0f || stats.organizationRadius == 0f) return;
        float r = stats.organizationRadius;
        mindustry.Vars.unitGroups[unit.team().id].each(ally -> {
            if (ally == unit) return;
            float dst = ally.dst(unit);
            if (dst <= r) {
                TacticalUnitEntity allyEntity = TacticalGamemode.getEntity(ally);
                if (allyEntity != null) {
                    allyEntity.drainOrg(-stats.organizationRadiusBonus); // negative drain = gain
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Flying helpers
    // -------------------------------------------------------------------------

    /** Transitions a flying unit into landed state for melee/charge. */
    public void land() {
        if (!stats.flying) return;
        isFlying = false;
    }

    /** Transitions a landed flying unit back into flight. */
    public void takeOff() {
        if (!stats.flying) return;
        isFlying = true;
    }

    /**
     * Returns true if this unit can currently be targeted in melee by the given
     * attacker entity.
     */
    public boolean isMeleeTargetableBy(BaseSallyOutUnitEntity attacker) {
        if (!isFlying) return true;
        // Flying units can only be hit in melee if the attacker has canAttackFlying
        return attacker.stats.canAttackFlying;
    }

    // -------------------------------------------------------------------------
    // Rectangular hitbox helpers
    // -------------------------------------------------------------------------

    /**
     * Returns true if the given world point overlaps this unit's rectangular
     * hitbox (width = hitSize*2, height = hitSize*1.2, rotated with unit.rotation).
     */
    public boolean rectContains(float wx, float wy) {
        float hs = unit.hitSize();
        float hw = hs;        // half-width  (front/rear extent)
        float hh = hs * 0.6f; // half-height (flank extent)

        // Rotate point into unit-local space
        float rad    = (float) Math.toRadians(unit.rotation());
        float cosR   = (float) Math.cos(rad);
        float sinR   = (float) Math.sin(rad);
        float dx     = wx - unit.x();
        float dy     = wy - unit.y();
        float localX = dx * cosR + dy * sinR;
        float localY = -dx * sinR + dy * cosR;

        return Math.abs(localX) <= hw && Math.abs(localY) <= hh;
    }
}
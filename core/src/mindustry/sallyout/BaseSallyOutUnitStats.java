package mindustry.sallyout;

public class BaseSallyOutUnitStats {
    //Stats
    public float maxOrganization = 100f; //morale, unit flees when under <= 0
    public float currentOrganization = 100f; //current morale
    public float organizationRegen = 1f; //morale recovery
    public float organizationRadius = 1f; //morale bonus for being close to other friendly units range
    public float organizationRadiusBonus = 20f; //morale bonus for being close to other friendly units
    public float defense = 5f; //lat subtraction from every hit, halved when charging
    public float meleeAttack = 10f; //damage per tick in contact
    public float meleeOrgDrain = 2f; //morale drain on target while attacking
    public float rangeRadius = 0f;
    public float rangeAttack = 0f;
    public float chargeBonus = 1.5f; //
    public float chargeResistance = 1.5f;
    public float maxStamina = 100f;
    public float staminaMoveDrain = 0.1f;
    public float staminaMeleeDrain = 0.2f;
    public float staminaChargeDrain = 0.4f;
    public float staminaRegenPerTick = 0.15f;
    public float movementSpeed = 10f;
    public boolean freeMovement = false;
    public boolean flying = false;
    public boolean canAttackFlying = false;
    public int cost = 100;
    public float frontArcHalf = 60f;
    public float flankArcHalf = 120f;
    public float flankDamageMulti = 1.4f;
    public float flankOrgMulti = 1.5f;
    public float rearDamageMulti = 2.0f;
    public float rearOrgMulti = 2.5f;

    public float damageMultiplierForAttack(float attackAngle, float unitRotation) {
        // Angle difference between the hit direction and the unit's facing.
        // 0 = pure front hit, 180 = pure rear hit.
        float diff = angleDiff(attackAngle, unitRotation + 180f); // +180 so front=0
        float abs  = Math.abs(diff);
        if (abs <= frontArcHalf)  return 1.0f;
        if (abs <= flankArcHalf)  return flankDamageMulti;
        return rearDamageMulti;
    }

    public float orgMultiplierForAttack(float attackAngle, float unitRotation) {
        float diff = angleDiff(attackAngle, unitRotation + 180f);
        float abs  = Math.abs(diff);
        if (abs <= frontArcHalf)  return 1.0f;
        if (abs <= flankArcHalf)  return flankOrgMulti;
        return rearOrgMulti;
    }

    private static float angleDiff(float a, float b) {
        float d = ((a - b) % 360f + 360f) % 360f;
        return d > 180f ? d - 360f : d;
    }


}

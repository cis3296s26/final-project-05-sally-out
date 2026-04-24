package mindustry.sallyout;

import mindustry.type.UnitType;


public class BaseSallyOutUnitType extends UnitType {

    /** Default stats shared by all plain UnitTypes (read-only fallback). */
    private static final BaseSallyOutUnitStats DEFAULT_STATS = new BaseSallyOutUnitStats();

    /** Mutable stat block for this type. */
    public final BaseSallyOutUnitStats tStats = new BaseSallyOutUnitStats();

    public BaseSallyOutUnitType(String name) {
        super(name);
    }

    /**
     * Returns the BaseSallyOutUnitStats for any UnitType, using defaults when the
     * unit was not defined as a BaseSallyOutUnitType.
     */
    public static BaseSallyOutUnitStats BaseSallyOutUnitStats(UnitType type) {
        if (type instanceof BaseSallyOutUnitType t) return t.tStats;
        return DEFAULT_STATS;
    }
}
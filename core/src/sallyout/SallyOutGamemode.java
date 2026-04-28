package sallyout;

import arc.Events;
import arc.math.geom.Rect;
import arc.struct.IntMap;
import arc.struct.Seq;
import arc.util.Interval;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import sallyout.ui.SetupPhaseUI;

/**
 * SallyOutGamemode – orchestrates the Sally-Out PvP mode.
 *
 * Team.id is a public final int FIELD — access as team.id, never team.id().
 * unit.x, unit.y, unit.hitSize are public FIELDS — access directly.
 * unit.team(), unit.id(), unit.dead(), unit.health() are generated methods.
 */
public class SallyOutGamemode {

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private static SallyOutGamemode INSTANCE;
    public static SallyOutGamemode get() { return INSTANCE; }

    // -------------------------------------------------------------------------
    // Config
    // -------------------------------------------------------------------------

    public static final int   DEFAULT_SUPPLY_BUDGET = 20;
    public static final float ZONE_FRACTION         = 0.25f;
    public static final float SETUP_PHASE_DURATION  = 60f * 60f * 3f;
    private static final int  AURA_INTERVAL_TICKS   = 30;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private static final IntMap<BaseSallyOutUnitEntity> entityMap  = new IntMap<>();
    private static final IntMap<Boolean>               inMeleeMap = new IntMap<>();

    private final Interval auraTimer = new Interval();

    private boolean setupPhase = true;
    private float   setupTimer = SETUP_PHASE_DURATION;

    // Team.id is a public final int field — use as IntMap key directly
    private final IntMap<Integer> supplyUsed = new IntMap<>();

    private Rect team1Zone, team2Zone;

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    public static void init() {
        INSTANCE = new SallyOutGamemode();
        INSTANCE.registerEvents();
    }

    private void registerEvents() {
        Events.on(EventType.WorldLoadEvent.class,   e -> onWorldLoad());
        Events.on(EventType.UnitDestroyEvent.class, e -> onUnitDestroy(e.unit));
        Events.on(EventType.GameOverEvent.class,    e -> onGameOver());
        Events.run(EventType.Trigger.update,        this::onUpdate);
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    private void onWorldLoad() {
        entityMap.clear();
        inMeleeMap.clear();
        supplyUsed.clear();
        setupPhase = true;
        setupTimer = SETUP_PHASE_DURATION;

        float mapW = Vars.world.unitWidth();
        float mapH = Vars.world.unitHeight();

        team1Zone = new Rect(0f,                          0f, mapW * ZONE_FRACTION,       mapH);
        team2Zone = new Rect(mapW * (1f - ZONE_FRACTION), 0f, mapW * ZONE_FRACTION,       mapH);

        SetupPhaseUI.openForAll(team1Zone, team2Zone, DEFAULT_SUPPLY_BUDGET);
    }

    private void onUnitDestroy(Unit unit) {
        entityMap.remove(unit.id());
        inMeleeMap.remove(unit.id());
    }

    private void onGameOver() {
        entityMap.clear();
        inMeleeMap.clear();
    }

    // -------------------------------------------------------------------------
    // Main update loop
    // -------------------------------------------------------------------------

    private void onUpdate() {
        if (!isActive()) return;

        if (setupPhase) {
            setupTimer -= Time.delta;
            if (setupTimer <= 0f) endSetupPhase();
            return;
        }

        updateAllEntities();
        resolveMelee();
        if (auraTimer.get(AURA_INTERVAL_TICKS)) broadcastAuras();
        checkWinCondition();
    }

    private boolean isActive() {
        return Vars.state.isGame() && INSTANCE != null;
    }

    // -------------------------------------------------------------------------
    // Setup phase
    // -------------------------------------------------------------------------

    public void endSetupPhase() {
        setupPhase = false;
        SetupPhaseUI.close();
        Groups.unit.each(u -> entityMap.put(u.id(), new BaseSallyOutUnitEntity(u)));
    }

    // -------------------------------------------------------------------------
    // Supply budget
    // -------------------------------------------------------------------------

    /** Team.id is a public final int FIELD. Use team.id, never team.id(). */
    public boolean trySpendSupply(Team team, int cost) {
        int used = supplyUsed.get(team.id, 0);
        if (used + cost > DEFAULT_SUPPLY_BUDGET) return false;
        supplyUsed.put(team.id, used + cost);
        return true;
    }

    public int remainingSupply(Team team) {
        return DEFAULT_SUPPLY_BUDGET - supplyUsed.get(team.id, 0);
    }

    public void refundSupply(Team team, int cost) {
        int used = supplyUsed.get(team.id, 0);
        supplyUsed.put(team.id, Math.max(0, used - cost));
    }

    public boolean inDeployZone(float wx, float wy, Team team) {
        if (team == Team.sharded) return team1Zone != null && team1Zone.contains(wx, wy);
        if (team == Team.blue)    return team2Zone != null && team2Zone.contains(wx, wy);
        return false;
    }

    // -------------------------------------------------------------------------
    // Entity updates
    // -------------------------------------------------------------------------

    private void updateAllEntities() {
        inMeleeMap.clear();
        Seq<BaseSallyOutUnitEntity> snapshot = new Seq<>(entityMap.values().toArray());
        snapshot.each(BaseSallyOutUnitEntity::update);
    }

    // -------------------------------------------------------------------------
    // Melee resolution
    // -------------------------------------------------------------------------

    private void resolveMelee() {
        Seq<BaseSallyOutUnitEntity> team1Units = new Seq<>();
        Seq<BaseSallyOutUnitEntity> team2Units = new Seq<>();

        entityMap.values().toArray().each(e -> {
            if (!e.unit.isAdded() || e.unit.dead() || e.routing) return;
            // team() IS a generated method — correct
            if (e.unit.team() == Team.sharded) team1Units.add(e);
            else                               team2Units.add(e);
        });

        for (BaseSallyOutUnitEntity a : team1Units) {
            for (BaseSallyOutUnitEntity b : team2Units) {
                // hitSize is a public FIELD — use unit.hitSize, not unit.hitSize()
                float contactRange = a.unit.hitSize + b.unit.hitSize;
                if (a.unit.dst(b.unit) > contactRange) continue;

                inMeleeMap.put(a.unit.id(), true);
                inMeleeMap.put(b.unit.id(), true);

                if (!b.isMeleeTargetableBy(a)) continue;

                if (b.isFlying) b.land();
                if (a.isFlying) a.land();

                // unit.x and unit.y are public FIELDS — use directly, not unit.x()
                b.applyDirectionalDamage(a.stats.meleeAttack, a.unit.x, a.unit.y, a.isCharging, a.stats);
                a.applyDirectionalDamage(b.stats.meleeAttack, b.unit.x, b.unit.y, b.isCharging, b.stats);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Aura broadcasts
    // -------------------------------------------------------------------------

    private void broadcastAuras() {
        entityMap.values().toArray().each(e -> {
            if (e.unit.isAdded() && !e.unit.dead()) e.broadcastOrgAura();
        });
    }

    // -------------------------------------------------------------------------
    // Win condition
    // -------------------------------------------------------------------------

    private void checkWinCondition() {
        boolean t1 = Groups.unit.contains(u -> u.team() == Team.sharded && !u.dead());
        boolean t2 = Groups.unit.contains(u -> u.team() == Team.blue    && !u.dead());

        if (!t1 && !t2) {
            Events.fire(new EventType.GameOverEvent(Team.derelict));
        } else if (!t1) {
            Events.fire(new EventType.GameOverEvent(Team.blue));
        } else if (!t2) {
            Events.fire(new EventType.GameOverEvent(Team.sharded));
        }
    }

    // -------------------------------------------------------------------------
    // Static accessors
    // -------------------------------------------------------------------------

    public static boolean isInMelee(Unit unit) {
        return inMeleeMap.containsKey(unit.id());
    }

    public static BaseSallyOutUnitEntity getEntity(Unit unit) {
        return entityMap.get(unit.id());
    }

    public static void registerUnit(Unit unit) {
        if (INSTANCE == null) return;
        entityMap.put(unit.id(), new BaseSallyOutUnitEntity(unit));
    }

    // -------------------------------------------------------------------------
    // Accessors for UI
    // -------------------------------------------------------------------------

    public Rect    getTeam1Zone()   { return team1Zone; }
    public Rect    getTeam2Zone()   { return team2Zone; }
    public boolean isSetupPhase()   { return setupPhase; }
    public float   setupTimeLeft()  { return setupTimer; }
}
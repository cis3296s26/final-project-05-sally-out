package mindustry.pvp;

import arc.Events;
import arc.math.geom.Rect;
import arc.struct.IntMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Interval;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.pvp.ui.SetupPhaseUI;
import mindustry.type.UnitType;

/**
 * SallyOutGamemode – orchestrates the Sally-Out PvP mode.
 *
 * Life-cycle
 * ----------
 * 1. Two players join.  A countdown starts and SetupPhaseUI opens for each.
 * 2. During setup both players drag units from a panel into their highlighted
 *    deployment zone.  A supply-point budget limits the total cost.
 * 3. When both players confirm (or the timer runs out) the match begins.
 * 4. Every tick the gamemode:
 *      - Updates every BaseSallyOutUnitEntity (org, stamina, charge timers).
 *      - Resolves melee contact between opposing units.
 *      - Broadcasts org-radius auras.
 *      - Checks win condition (all enemy units dead or routed off-map).
 *
 * Deployment zones
 * ----------------
 *   Team1 zone : left side  (x: 0 .. MAP_WIDTH * ZONE_FRACTION)
 *   Team2 zone : right side (x: MAP_WIDTH * (1-ZONE_FRACTION) .. MAP_WIDTH)
 *   The zone height covers the full map height.
 *
 * How to hook in
 * --------------
 * Call {@code SallyOutGamemode.init()} once from your mod/plugin entry point.
 * The singleton listens to Mindustry's event bus.
 */
public class SallyOutGamemode {

    // Singleton
    private static SallyOutGamemode INSTANCE;
    public static SallyOutGamemode get() { return INSTANCE; }
  
    // Configuration constants
    /** Supply points each player may spend. */
    public static final int DEFAULT_SUPPLY_BUDGET = 20;
    /** Fraction of the map width used for each deployment zone. */
    public static final float ZONE_FRACTION = 0.25f;
    /** Duration of the setup phase in ticks (3 minutes at 60 UPS). */
    public static final float SETUP_PHASE_DURATION = 60f * 60f * 3f;
    /** How often (ticks) to run the org-aura broadcast. */
    private static final int AURA_INTERVAL_TICKS = 30;

    // Runtime state
    /** Maps unit.id -> BaseSallyOutUnitEntity for every active tactical unit. */
    private static final IntMap<BaseSallyOutUnitEntity> entityMap = new IntMap<>();
    /** Tracks which units are currently in melee contact. */
    private static final IntMap<Boolean> inMeleeMap = new IntMap<>();
    private final Interval auraTimer  = new Interval();
    private boolean setupPhase = true;
    private float   setupTimer = SETUP_PHASE_DURATION;
    // Per-team supply usage
    private final IntMap<Integer> supplyUsed = new IntMap<>();
    // Deployment zone rectangles (world-units), computed once map loads
    private Rect team1Zone, team2Zone;

    // Init
    public static void init() {
        INSTANCE = new SallyOutGamemode();
        INSTANCE.registerEvents();
    }
    private void registerEvents() {
        Events.on(EventType.WorldLoadEvent.class, e -> onWorldLoad());
        Events.on(EventType.UnitDestroyEvent.class, e -> onUnitDestroy(e.unit));
        Events.on(EventType.GameOverEvent.class, e -> onGameOver());
        Events.run(EventType.Trigger.update, this::onUpdate);
    }

 
    // Event handlers
    private void onWorldLoad() {
        entityMap.clear();
        inMeleeMap.clear();
        supplyUsed.clear();
        setupPhase = true;
        setupTimer = SETUP_PHASE_DURATION;
        float mapW = Vars.world.unitWidth();
        float mapH = Vars.world.unitHeight();
        team1Zone = new Rect(0f,                          0f, mapW * ZONE_FRACTION,         mapH);
        team2Zone = new Rect(mapW * (1f - ZONE_FRACTION), 0f, mapW * ZONE_FRACTION,         mapH);
        // Open setup UI for connected players (called server-side; client UI opened via packet)
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

    // Main update loop
    private void onUpdate() {
        if (!isActive()) return;
        if (setupPhase) {
            tickSetupPhase();
            return;
        }
        // --- Combat phase ---
        updateAllEntities();
        resolveMelee();
        if (auraTimer.get(AURA_INTERVAL_TICKS)) broadcastAuras();
        checkWinCondition();
    }
    private boolean isActive() {
        return Vars.state.isGame() && INSTANCE != null;
    }

    // Setup phase
    private void tickSetupPhase() {
        setupTimer -= Time.delta;
        if (setupTimer <= 0f) {
            endSetupPhase();
        }
    }

    /** Called when both players confirm or the timer expires. */
    public void endSetupPhase() {
        setupPhase = false;
        SetupPhaseUI.close();
        // Wrap every unit currently on the map in a BaseSallyOutUnitEntity
        Groups.unit.each(u -> entityMap.put(u.id(), new BaseSallyOutUnitEntity(u)));
    }

    // Supply budget
    /**
     * Attempts to spend {@code cost} supply points for {@code team}.
     * @return true if the spend succeeded (budget available), false otherwise.
     */
    public boolean trySpendSupply(Team team, int cost) {
        int used = supplyUsed.get(team.id(), 0);
        if (used + cost > DEFAULT_SUPPLY_BUDGET) return false;
        supplyUsed.put(team.id(), used + cost);
        return true;
    }
    /** Returns remaining supply budget for the given team. */
    public int remainingSupply(Team team) {
        return DEFAULT_SUPPLY_BUDGET - supplyUsed.get(team.id(), 0);
    }
    /** Refunds the cost of a unit (called when a placed unit is removed during setup). */
    public void refundSupply(Team team, int cost) {
        int used = supplyUsed.get(team.id(), 0);
        supplyUsed.put(team.id(), Math.max(0, used - cost));
    }
    /** Checks that the given world position is inside the correct deployment zone for the team. */
    public boolean inDeployZone(float wx, float wy, Team team) {
        if (team == Team.sharded) return team1Zone != null && team1Zone.contains(wx, wy);
        if (team == Team.blue)    return team2Zone != null && team2Zone.contains(wx, wy);
        return false;
    }

    // Entity updates
    private void updateAllEntities() {
        inMeleeMap.clear();
        entityMap.values().toArray().each(BaseSallyOutUnitEntity::update);
    }

    // Melee resolution
    /**
     * For every pair of opposing units within melee range, apply damage and
     * organisation drain each tick.
     *
     * Melee range = sum of both units' hitSize values (rectangular hitbox edge contact).
     */
    private void resolveMelee() {
        // Build a list sorted by team to avoid double-processing
        Seq<BaseSallyOutUnitEntity> team1Units = new Seq<>();
        Seq<BaseSallyOutUnitEntity> team2Units = new Seq<>();

        entityMap.values().toArray().each(e -> {
            if (!e.unit.isAdded() || e.unit.dead() || e.routing) return;
            Team t = e.unit.team();
            if (t == Team.sharded) team1Units.add(e);
            else                   team2Units.add(e);
        });

        for (BaseSallyOutUnitEntity a : team1Units) {
            for (BaseSallyOutUnitEntity b : team2Units) {
                float contactRange = a.unit.hitSize() + b.unit.hitSize();
                if (a.unit.dst(b.unit) <= contactRange) {
                    // Mark both as in melee
                    inMeleeMap.put(a.unit.id(), true);
                    inMeleeMap.put(b.unit.id(), true);

                    // Skip melee if defender is flying and attacker can't reach
                    if (!b.isMeleeTargetableBy(a)) continue;

                    // Flying unit must land to fight
                    if (b.isFlying)  b.land();
                    if (a.isFlying)  a.land();

                    // Apply damage in both directions
                    b.applyDirectionalDamage(
                        a.stats.meleeAttack,
                        a.unit.x(), a.unit.y(),
                        a.isCharging,
                        a.stats
                    );
                    a.applyDirectionalDamage(
                        b.stats.meleeAttack,
                        b.unit.x(), b.unit.y(),
                        b.isCharging,
                        b.stats
                    );
                }
            }
        }
    }

    // Aura broadcasts

    private void broadcastAuras() {
        entityMap.values().toArray().each(e -> {
            if (e.unit.isAdded() && !e.unit.dead()) e.broadcastOrgAura();
        });
    }

    // Win condition
    private void checkWinCondition() {
        boolean team1Alive = Groups.unit.contains(u -> u.team() == Team.sharded && !u.dead());
        boolean team2Alive = Groups.unit.contains(u -> u.team() == Team.blue    && !u.dead());

        if (!team1Alive && !team2Alive) {
            Events.fire(new EventType.GameOverEvent(Team.derelict));
        } else if (!team1Alive) {
            Events.fire(new EventType.GameOverEvent(Team.blue));
        } else if (!team2Alive) {
            Events.fire(new EventType.GameOverEvent(Team.sharded));
        }
    }

    // Static accessors used by BaseSallyOutUnitEntity
    public static boolean isInMelee(Unit unit) {
        return inMeleeMap.containsKey(unit.id());
    }

    public static BaseSallyOutUnitEntity getEntity(Unit unit) {
        return entityMap.get(unit.id());
    }

    /** Registers a freshly spawned unit during the setup phase. */
    public static void registerUnit(Unit unit) {
        if (INSTANCE == null) return;
        entityMap.put(unit.id(), new BaseSallyOutUnitEntity(unit));
    }

    // Zone rects (for UI rendering)
    public Rect getTeam1Zone() { return team1Zone; }
    public Rect getTeam2Zone() { return team2Zone; }
    public boolean isSetupPhase()  { return setupPhase; }
    public float   setupTimeLeft() { return setupTimer; }
}
package mindustry.ai.types;

import arc.math.*;
import arc.math.geom.Geometry;
import mindustry.ai.*;
import mindustry.entities.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.meta.BlockFlag;

import static mindustry.Vars.*;

public class GroundAI extends AIController{

    @Override
    public void updateMovement(){
        // find nearest player unit (if any)
        Unit playerUnit = findNearestPlayerUnit();
        // find nearest enemy core (which belongs to player team)
        Building playerCore = findNearestEnemyCore();

        // for weapon targeting, prioritize player unit if nearby, else core
        Teamc targetEntity = playerUnit != null ? playerUnit : playerCore;
        if(targetEntity != null && unit.within(targetEntity, unit.range() / 1.3f + (targetEntity instanceof Sized s ? s.hitSize()/2f : 0f))){
            target = targetEntity;
            for(var mount : unit.mounts){
                if(mount.weapon.controllable && mount.weapon.bullet.collidesGround){
                    mount.target = targetEntity;
                }
            }
        }

        // movement decision
        boolean move = true;

        // keep original spawner idle behavior
        if(state.rules.waves && unit.team == state.rules.defaultTeam){
            Tile spawner = getClosestSpawner();
            if(spawner != null && unit.within(spawner, state.rules.dropZoneRadius + 120f)) move = false;
            if(spawner == null && playerUnit == null && playerCore == null) move = false;
        }

        // move toward player if we have any target and not already close
        if(move && (playerUnit != null || playerCore != null)){
            float distToTarget = Float.MAX_VALUE;
            if(playerUnit != null) distToTarget = unit.dst(playerUnit);
            if(playerCore != null) distToTarget = Math.min(distToTarget, unit.dst(playerCore));

            // only move if beyond half weapon range
            if(distToTarget > unit.type.range * 0.5f){
                pathfind(Pathfinder.fieldPlayer);
            }
        }

        if(unit.type.canBoost && unit.elevation > 0.001f && !unit.onSolid()){
            unit.elevation = Mathf.approachDelta(unit.elevation, 0f, unit.type.riseSpeed);
        }

        faceTarget();
    }

    //finds the closest enemy unit controlled by a player
    private Unit findNearestPlayerUnit(){
        return Units.closest(unit.team, unit.x, unit.y, Float.MAX_VALUE,
            u -> u.controller() instanceof Player && u.team != unit.team);
    }

    // finds the closest enemy core building
    private Building findNearestEnemyCore(){
        // use Geometry.findClosest which works for any Position and filters with BlockFlag.core
        return (Building)Geometry.findClosest(unit.x, unit.y, indexer.getEnemy(unit.team, BlockFlag.core));
    }
}
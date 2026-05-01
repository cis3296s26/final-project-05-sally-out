package sallyout;

import mindustry.content.UnitTypes;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ai.*;
import mindustry.ai.types.*;
import mindustry.annotations.Annotations.*;
import mindustry.entities.*;
import mindustry.entities.abilities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.effect.*;
import mindustry.entities.part.*;
import mindustry.entities.pattern.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.type.ammo.*;
import mindustry.type.unit.*;
import mindustry.type.weapons.*;
import mindustry.world.meta.*;
import sallyout.ai.SallyOutRangedAI;

import static arc.graphics.g2d.Draw.*;
import static arc.graphics.g2d.Lines.*;
import static arc.math.Angles.*;
import static mindustry.Vars.*;



public class BaseSallyOutUnitDefs {

    // Unit types (public for access from other systems)
    public static BaseSallyOutUnitType spearman;
    public static BaseSallyOutUnitType swordsman;
    public static BaseSallyOutUnitType archer;
    public static BaseSallyOutUnitType knight;
    public static BaseSallyOutUnitType scout;
    public static BaseSallyOutUnitType cataphract;
    public static BaseSallyOutUnitType gryphon;
    public static BaseSallyOutUnitType general;

    // Load
    public static void load() {
        // --- Spearman ---
        spearman = new BaseSallyOutUnitType("spearman") {{
            // Re-use mace visuals and constructor
            assetSource = UnitTypes.mace;
            uiIcon      = UnitTypes.mace.uiIcon;
            constructor = UnitTypes.mace.constructor;
            health      = 200f;
            speed       = 1.3f;
            hitSize     = 8f;

            tStats.cost             = 1;
            tStats.meleeAttack      = 12f;
            tStats.meleeOrgDrain    = 2.5f;
            tStats.defense          = 4f;
            tStats.chargeBonus      = 2.0f;
            tStats.chargeResistance = 1.0f;
            tStats.organizationRadius        = 80f;
            tStats.organizationRadiusBonus   = 0.04f;
            tStats.maxOrganization  = 80f;
            tStats.maxStamina       = 100f;
        }};

        // --- Swordsman ---
        swordsman = new BaseSallyOutUnitType("swordsman") {{
            assetSource = UnitTypes.dagger;
            uiIcon      = UnitTypes.dagger.uiIcon;
            constructor = UnitTypes.dagger.constructor;
            health      = 350f;
            speed       = 1.1f;
            hitSize     = 9f;

            tStats.cost             = 2;
            tStats.meleeAttack      = 18f;
            tStats.meleeOrgDrain    = 3f;
            tStats.defense          = 10f;
            tStats.chargeBonus      = 1.5f;
            tStats.chargeResistance = 1.2f;
            tStats.organizationRadius        = 90f;
            tStats.organizationRadiusBonus   = 0.05f;
            tStats.maxOrganization  = 100f;
            tStats.maxStamina       = 100f;
        }};

        // --- Archer ---
        archer = new BaseSallyOutUnitType("archer") {{
            assetSource = UnitTypes.dagger;
            uiIcon      = UnitTypes.dagger.uiIcon;
            constructor = UnitTypes.dagger.constructor;
            health      = 150f;
            speed       = 1.4f;
            hitSize     = 7f;

            tStats.cost             = 2;
            tStats.meleeAttack      = 5f;
            tStats.meleeOrgDrain    = 1f;
            tStats.rangeAttack      = 20f;
            tStats.rangeRadius      = 180f;
            tStats.defense          = 3f;
            tStats.chargeBonus      = 1.2f;
            tStats.chargeResistance = 0.8f;
            tStats.organizationRadius        = 70f;
            tStats.organizationRadiusBonus   = 0.03f;
            tStats.maxOrganization  = 70f;
            tStats.maxStamina       = 90f;
            // Archers use the ranged AI
            aiController = SallyOutRangedAI::new;
        }};

        // --- Knight ---
        knight = new BaseSallyOutUnitType("knight") {{
            assetSource = UnitTypes.fortress;
            uiIcon      = UnitTypes.fortress.uiIcon;
            constructor = UnitTypes.fortress.constructor;
            health      = 500f;
            speed       = 0.9f;
            hitSize     = 12f;

            tStats.cost             = 3;
            tStats.meleeAttack      = 22f;
            tStats.meleeOrgDrain    = 4f;
            tStats.defense          = 18f;
            tStats.chargeBonus      = 1.8f;
            tStats.chargeResistance = 1.5f;
            tStats.organizationRadius        = 100f;
            tStats.organizationRadiusBonus   = 0.06f;
            tStats.maxOrganization  = 120f;
            tStats.maxStamina       = 110f;
        }};

        // --- Scout ---
        scout = new BaseSallyOutUnitType("scout") {{
            assetSource = UnitTypes.dagger;
            uiIcon      = UnitTypes.dagger.uiIcon;
            constructor = UnitTypes.dagger.constructor;
            health      = 80f;
            speed       = 2.2f;
            hitSize     = 6f;

            tStats.cost             = 1;
            tStats.meleeAttack      = 8f;
            tStats.meleeOrgDrain    = 1.5f;
            tStats.defense          = 2f;
            tStats.chargeBonus      = 1.3f;
            tStats.chargeResistance = 0.7f;
            tStats.freeMovement   = true;   // moves through obstacles
            tStats.organizationRadius        = 60f;
            tStats.organizationRadiusBonus   = 0.02f;
            tStats.maxOrganization  = 60f;
            tStats.maxStamina       = 120f;
        }};

        // --- Cataphract (heavy cavalry) ---
        cataphract = new BaseSallyOutUnitType("cataphract") {{
            assetSource = UnitTypes.fortress;
            uiIcon      = UnitTypes.fortress.uiIcon;
            constructor = UnitTypes.fortress.constructor;
            health      = 600f;
            speed       = 1.6f;
            hitSize     = 14f;

            tStats.cost             = 4;
            tStats.meleeAttack      = 28f;
            tStats.meleeOrgDrain    = 5f;
            tStats.defense          = 20f;
            tStats.chargeBonus      = 2.5f;
            tStats.chargeResistance = 1.8f;
            tStats.organizationRadius        = 110f;
            tStats.organizationRadiusBonus   = 0.06f;
            tStats.maxOrganization  = 130f;
            tStats.maxStamina       = 100f;
            // Heavier defense penalty when charging
            tStats.flankDamageMulti = 1.6f;
            tStats.rearDamageMulti  = 2.5f;
        }};

        // --- Gryphon (flying melee) ---
        gryphon = new BaseSallyOutUnitType("gryphon") {{
            assetSource = UnitTypes.vela;
            uiIcon      = UnitTypes.vela.uiIcon;
            constructor = UnitTypes.vela.constructor;
            health      = 280f;
            speed       = 1.8f;
            hitSize     = 10f;

            tStats.cost             = 3;
            tStats.flying           = true;
            tStats.canAttackFlying  = true;
            tStats.meleeAttack      = 15f;
            tStats.meleeOrgDrain    = 3f;
            tStats.defense          = 6f;
            tStats.chargeBonus      = 1.6f;
            tStats.chargeResistance = 1.0f;
            tStats.organizationRadius        = 130f;
            tStats.organizationRadiusBonus   = 0.05f;
            tStats.maxOrganization  = 90f;
            tStats.maxStamina       = 130f;
        }};

        // --- General (command / morale unit) ---
        general = new BaseSallyOutUnitType("general") {{
            assetSource = UnitTypes.scepter;
            uiIcon      = UnitTypes.scepter.uiIcon;
            constructor = UnitTypes.scepter.constructor;
            health      = 300f;
            speed       = 1.0f;
            hitSize     = 10f;

            tStats.cost             = 3;
            tStats.meleeAttack      = 10f;
            tStats.meleeOrgDrain    = 2f;
            tStats.defense          = 8f;
            tStats.chargeBonus      = 1.4f;
            tStats.chargeResistance = 1.2f;
            // Large aura radius and bonus – primary value is morale support
            tStats.organizationRadius        = 200f;
            tStats.organizationRadiusBonus   = 0.15f;
            tStats.maxOrganization  = 150f;
            tStats.maxStamina       = 100f;
        }};
    }
}
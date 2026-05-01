package sallyout;

import mindustry.type.UnitType;


public class BaseSallyOutUnitType extends UnitType {

    /** Default stats shared by all plain UnitTypes (read-only fallback). */
    private static final BaseSallyOutUnitStats DEFAULT_STATS = new BaseSallyOutUnitStats();

    /** Mutable stat block for this type. */
    public final BaseSallyOutUnitStats tStats = new BaseSallyOutUnitStats();
    /** Reference to an existing unit type to reuse art assets and icons. */
    public UnitType assetSource;

    public BaseSallyOutUnitType(String name) {
        super(name);
    }

    @Override
    public void load(){
        super.load();

        if(assetSource != null){
            if(uiIcon == null || !uiIcon.found()) uiIcon = assetSource.uiIcon;
            if(fullIcon == null || !fullIcon.found()) fullIcon = assetSource.fullIcon;
            if(region == null || !region.found()) region = assetSource.region;
            if(previewRegion == null || !previewRegion.found()) previewRegion = assetSource.previewRegion;
            if(legRegion == null || !legRegion.found()) legRegion = assetSource.legRegion;
            if(jointRegion == null || !jointRegion.found()) jointRegion = assetSource.jointRegion;
            if(baseJointRegion == null || !baseJointRegion.found()) baseJointRegion = assetSource.baseJointRegion;
            if(footRegion == null || !footRegion.found()) footRegion = assetSource.footRegion;
            if(treadRegion == null || !treadRegion.found()) treadRegion = assetSource.treadRegion;
            if(treadRegions == null || treadRegions.length == 0) treadRegions = assetSource.treadRegions;
            if(legBaseRegion == null || !legBaseRegion.found()) legBaseRegion = assetSource.legBaseRegion;
            if(baseRegion == null || !baseRegion.found()) baseRegion = assetSource.baseRegion;
            if(cellRegion == null || !cellRegion.found()) cellRegion = assetSource.cellRegion;
            if(outlineRegion == null || !outlineRegion.found()) outlineRegion = assetSource.outlineRegion;
            if(shadowRegion == null || !shadowRegion.found()) shadowRegion = assetSource.shadowRegion;
            if(softShadowRegion == null || !softShadowRegion.found()) softShadowRegion = assetSource.softShadowRegion;
            if(wreckRegions == null || wreckRegions.length == 0) wreckRegions = assetSource.wreckRegions;
            if(segmentRegions == null || segmentRegions.length == 0) segmentRegions = assetSource.segmentRegions;
            if(segmentOutlineRegions == null || segmentOutlineRegions.length == 0) segmentOutlineRegions = assetSource.segmentOutlineRegions;
        }
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
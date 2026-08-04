package com.dropai.rewrite.mechanicalengine.productplanner;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConveyorProductPlanner extends AbstractCatalogProductPlanner {
    public ProductFamily family() { return ProductFamily.CONVEYOR; }
    public MechanicalDesignSpec plan(String requirement) {
        return build("conveyor", "Modular belt conveyor", "transport packaged products between workstations",
                "a geared motor drives the head roller and belt while idlers support the conveyed load",
                List.of(
                        module("M01","Frame Module","support belt and conveyed load",List.of("leg mounts","roller bearing seats"),"bolted aluminum frame"),
                        module("M02","Drive Module","drive and tension the belt",List.of("motor flange","head roller axis"),"mounted at discharge end"),
                        module("M03","Support Module","support return and carrying belt",List.of("idler axes","side rails"),"regular pitch installation"),
                        module("M04","Guard Module","prevent access to moving transmission parts",List.of("guard tabs","frame holes"),"removable fastened guard")),
                List.of(
                        new PartSeed("P001","Side frame","M01","carry conveyor load","6061-T6","CNC profile machining",2000,120,12,false,10,6,"FIXED",0,"datum","world","fix conveyor frame"),
                        new PartSeed("P002","Motor bracket","M02","support geared motor","Q235B","laser cut and bending",240,180,8,false,12,4,"COINCIDENT",0,"mount face","drive end","mount motor"),
                        new PartSeed("P003","Head roller","M02","transmit drive torque to belt","45 steel","turning",120,120,600,true,20,3,"CONCENTRIC",0,"axis","bearing axis","align drive roller"),
                        new PartSeed("P004","Idler support","M03","support idler shafts","Q235B","laser cut",160,90,8,false,12,3,"COINCIDENT",0,"bottom","rail datum","mount idler"),
                        new PartSeed("P005","Drive guard","M04","cover rotating transmission","5052-H32","sheet metal bending",420,260,2,false,6,2,"COINCIDENT",0,"tabs","frame holes","fasten guard")),
                List.of(parameter("conveyor_length",2000,"mm","two-workstation spacing"),parameter("belt_width",500,"mm","package footprint plus lateral margin"),parameter("distributed_load",50,"kg/m","target product accumulation"),parameter("belt_speed",0.3,"m/s","manual workstation cycle"),parameter("design_safety_factor",2.0,"","start-up shock and accumulation")),
                List.of("product","belt","rollers","bearing seats","frame","legs","floor"),List.of("motor rotation","head roller rotation","belt translation","product translation"));
    }
}

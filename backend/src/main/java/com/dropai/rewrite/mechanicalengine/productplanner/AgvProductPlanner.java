package com.dropai.rewrite.mechanicalengine.productplanner;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgvProductPlanner extends AbstractCatalogProductPlanner {
    public ProductFamily family() { return ProductFamily.AGV; }
    public MechanicalDesignSpec plan(String requirement) {
        return build("agv", "Low-profile industrial AGV", "transport a 100 kg payload autonomously",
                "dual motor differential drive converts wheel torque into vehicle translation and steering",
                List.of(
                        module("M01","Chassis Module","carry payload and close the structural load path",List.of("payload deck","wheel mounts"),"welded and bolted chassis"),
                        module("M02","Drive Module","generate traction and steering",List.of("motor flange","wheel hub"),"bolted to chassis side rails"),
                        module("M03","Power Module","store and distribute electrical energy",List.of("battery tray","service connector"),"retained inside chassis"),
                        module("M04","Control Module","mount controller and navigation sensors",List.of("controller plate","sensor mast"),"isolated from drive vibration")),
                List.of(
                        new PartSeed("P001","Chassis plate","M01","carry payload","Q345B","laser cut and CNC finish",900,600,12,false,12,8,"FIXED",0,"datum","world","fix chassis"),
                        new PartSeed("P002","Left drive bracket","M02","support left drive motor","6061-T6","CNC milling",180,120,16,false,10,4,"COINCIDENT",0,"mount face","left rail","mount drive"),
                        new PartSeed("P003","Right drive bracket","M02","support right drive motor","6061-T6","CNC milling",180,120,16,false,10,4,"COINCIDENT",0,"mount face","right rail","mount drive"),
                        new PartSeed("P004","Battery tray","M03","locate traction battery","5052-H32","sheet metal bending",420,260,3,false,8,2,"COINCIDENT",0,"bottom","deck","locate battery"),
                        new PartSeed("P005","Controller plate","M04","mount controller and navigation electronics","6061-T6","CNC milling",300,220,6,false,6,2,"COINCIDENT",0,"bottom","deck","mount controls")),
                List.of(parameter("payload",100,"kg","specified transport duty"),parameter("maximum_speed",1.2,"m/s","safe indoor mixed-traffic operation"),parameter("endurance",8,"h","one production shift"),parameter("chassis_length",900,"mm","payload footprint plus collision margin"),parameter("design_safety_factor",2.0,"","dynamic floor impacts and load uncertainty")),
                List.of("payload","chassis","wheel brackets","wheels","floor"),List.of("motor rotation","wheel rotation","vehicle translation"));
    }
}

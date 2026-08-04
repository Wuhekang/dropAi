package com.dropai.rewrite.mechanicalengine.productplanner;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RobotProductPlanner extends AbstractCatalogProductPlanner {
    public ProductFamily family() { return ProductFamily.ROBOT; }
    public MechanicalDesignSpec plan(String requirement) {
        return build("mobile_robot", "Mobile inspection robot", "carry sensors through an industrial inspection route",
                "differential wheel drive moves a protected sensor platform through the inspection area",
                List.of(
                        module("M01","Frame Module","support all robot modules",List.of("drive mounts","sensor deck"),"bolted modular frame"),
                        module("M02","Drive Module","provide translation and steering",List.of("motor mounts","wheel axes"),"symmetric side installation"),
                        module("M03","Sensor Module","position inspection sensors",List.of("mast flange","camera plate"),"bolted above frame"),
                        module("M04","Power Module","retain battery and power distribution",List.of("battery tray","cable channels"),"low central installation"),
                        module("M05","Control Module","protect controller and communication hardware",List.of("sealed enclosure","service cover"),"vibration-isolated mounting")),
                List.of(
                        new PartSeed("P001","Main frame","M01","carry robot load","6061-T6","CNC milling",650,480,12,false,10,6,"FIXED",0,"datum","world","fix frame"),
                        new PartSeed("P002","Drive carrier","M02","support drive motors","6061-T6","CNC milling",260,120,14,false,10,4,"COINCIDENT",0,"mount face","side datum","mount drive"),
                        new PartSeed("P003","Sensor mast","M03","elevate inspection sensors","6061-T6","CNC milling",120,100,420,false,8,5,"COINCIDENT",0,"base","top datum","mount mast"),
                        new PartSeed("P004","Battery tray","M04","locate battery","5052-H32","sheet metal bending",300,220,3,false,6,2,"COINCIDENT",0,"bottom","deck","locate battery"),
                        new PartSeed("P005","Control enclosure base","M05","mount sealed control enclosure","6061-T6","CNC milling",280,200,8,false,6,3,"COINCIDENT",0,"bottom","deck","mount controller")),
                List.of(parameter("robot_mass",45,"kg","portable inspection platform target"),parameter("maximum_speed",0.8,"m/s","safe inspection speed"),parameter("sensor_height",550,"mm","line of sight above nearby equipment"),parameter("runtime",6,"h","inspection route duration"),parameter("design_safety_factor",2.0,"","obstacle impacts and payload variation")),
                List.of("sensor payload","mast","frame","drive carrier","wheels","floor"),List.of("motor rotation","wheel rotation","robot translation and yaw"));
    }
}

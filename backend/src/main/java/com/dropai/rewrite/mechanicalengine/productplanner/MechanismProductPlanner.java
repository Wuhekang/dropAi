package com.dropai.rewrite.mechanicalengine.productplanner;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MechanismProductPlanner extends AbstractCatalogProductPlanner {
    public ProductFamily family() { return ProductFamily.MECHANISM; }
    public MechanicalDesignSpec plan(String requirement) {
        return build("mechanism", "Crank-slider mechanism", "convert rotary input into controlled linear reciprocation",
                "a crank drives a connecting rod that constrains a slider along a linear guide",
                List.of(
                        module("M01","Base Module","establish mechanism datums",List.of("shaft seat","guide seat"),"bolted to machine frame"),
                        module("M02","Input Module","receive rotary power",List.of("input shaft","crank pin"),"bearing-supported shaft"),
                        module("M03","Linkage Module","transmit crank force",List.of("crank joint","slider joint"),"pin-jointed assembly"),
                        module("M04","Guide Module","constrain output translation",List.of("slider faces","linear guide"),"aligned to base datum")),
                List.of(
                        new PartSeed("P001","Mechanism base","M01","support shaft and guide","Q235B","CNC milling",500,240,18,false,12,6,"FIXED",0,"datum","world","fix mechanism"),
                        new PartSeed("P002","Crank disk","M02","convert shaft rotation into eccentric motion","45 steel","turning and milling",160,160,20,true,20,3,"CONCENTRIC",0,"axis","shaft seat","align crank"),
                        new PartSeed("P003","Connecting rod","M03","transmit force between crank and slider","40Cr","CNC milling",320,60,18,false,20,6,"DISTANCE",1,"pin bore","crank pin","connect linkage"),
                        new PartSeed("P004","Slider","M04","deliver linear output force","45 steel","CNC milling",120,100,60,false,20,4,"SLIDER",0,"guide face","linear guide","constrain output")),
                List.of(parameter("crank_radius",60,"mm","required 120 mm stroke"),parameter("connecting_rod_length",320,"mm","limits transmission angle and side thrust"),parameter("input_speed",120,"rpm","target reciprocation frequency"),parameter("output_force",2000,"N","process load requirement"),parameter("design_safety_factor",2.2,"","reversing inertia and joint clearance")),
                List.of("input shaft","crank","connecting rod","slider","guide","base"),List.of("shaft rotation","crank orbit","rod oscillation","slider translation"));
    }
}

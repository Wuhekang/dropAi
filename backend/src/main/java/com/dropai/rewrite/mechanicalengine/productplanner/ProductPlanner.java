package com.dropai.rewrite.mechanicalengine.productplanner;

import com.dropai.rewrite.mechanicalengine.domain.MechanicalDesignSpec;

import java.util.List;

public interface ProductPlanner {
    ProductFamily family();
    MechanicalDesignSpec plan(String requirement);

    default List<MechanicalDesignSpec.FunctionNode> generateFunctionTree(String requirement) { return plan(requirement).functions(); }
    default MechanicalDesignSpec.Architecture generateArchitecture(String requirement) { return plan(requirement).architecture(); }
    default List<MechanicalDesignSpec.PartPlan> generatePartPlan(String requirement) { return plan(requirement).parts(); }
    default List<MechanicalDesignSpec.Parameter> generateParameters(String requirement) { return plan(requirement).parameters(); }
    default List<MechanicalDesignSpec.AssemblyIntent> generateAssemblyIntent(String requirement) { return plan(requirement).assemblyIntent(); }
}

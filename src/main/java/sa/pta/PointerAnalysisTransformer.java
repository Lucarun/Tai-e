package sa.pta;

import sa.pta.analysis.context.ContextInsensitiveSelector;
import sa.pta.analysis.data.HashDataManager;
import sa.pta.analysis.heap.AllocationSiteBasedModel;
import sa.pta.analysis.solver.PointerAnalysis;
import sa.pta.analysis.solver.PointerAnalysisImpl;
import sa.pta.jimple.JimpleProgramManager;
import sa.pta.set.HybridPointsToSet;
import sa.pta.set.PointsToSetFactory;
import soot.SceneTransformer;

import java.util.Map;

public class PointerAnalysisTransformer extends SceneTransformer {

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        PointerAnalysis pta = new PointerAnalysisImpl();
        PointsToSetFactory setFactory = new HybridPointsToSet.Factory();
        pta.setProgramManager(new JimpleProgramManager());
        pta.setDataManager(new HashDataManager(setFactory));
        pta.setContextSelector(new ContextInsensitiveSelector());
        pta.setHeapModel(new AllocationSiteBasedModel());
        pta.setPointsToSetFactory(setFactory);
        pta.solve();
        pta.getCallGraph().forEach(System.out::println);
    }
}

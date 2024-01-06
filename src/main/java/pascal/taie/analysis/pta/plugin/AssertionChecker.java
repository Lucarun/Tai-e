/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.pta.plugin;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.analysis.graph.callgraph.CallGraph;
import pascal.taie.analysis.pta.PointerAnalysisResult;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.core.solver.Solver;
import pascal.taie.analysis.pta.plugin.util.InvokeUtils;
import pascal.taie.ir.IR;
import pascal.taie.ir.exp.IntLiteral;
import pascal.taie.ir.exp.StringLiteral;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.StoreArray;
import pascal.taie.language.classes.ClassHierarchy;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.ClassType;
import pascal.taie.util.collection.CollectionUtils;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.Sets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class AssertionChecker implements Plugin {

    private static final Logger logger = LogManager.getLogger(AssertionChecker.class);

    /**
     * Name of the stub class that provides assertion APIs.
     */
    private static final String PTA_ASSERT = "PTAAssert";

    private Solver solver;

    private ClassHierarchy hierarchy;

    private JClass ptaAssert;

    private Map<JMethod, Consumer<Invoke>> checkers;

    private PointerAnalysisResult pta;

    private CallGraph<Invoke, JMethod> callGraph;

    private List<Invoke> failures;

    @Override
    public void setSolver(Solver solver) {
        this.solver = solver;
        this.hierarchy = solver.getHierarchy();
        this.ptaAssert = hierarchy.getClass(PTA_ASSERT);
    }

    @Override
    public void onStart() {
        if (ptaAssert != null) {
            ptaAssert.getDeclaredMethods().forEach(solver::addIgnoredMethod);
        }
    }

    @Override
    public void onFinish() {
        if (ptaAssert == null) {
            logger.warn("class '{}' is not loaded, failed to enable {}",
                    PTA_ASSERT, AssertionChecker.class.getSimpleName());
            return;
        }
        registerCheckers();
        pta = solver.getResult();
        callGraph = pta.getCallGraph();
        failures = new ArrayList<>();
        for (JMethod assertApi : checkers.keySet()) {
            for (Invoke invoke : callGraph.getCallersOf(assertApi)) {
                checkers.get(assertApi).accept(invoke);
            }
        }
        if (!failures.isEmpty()) {
            String message = "Pointer analysis assertion failures:\n" +
                    failures.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining("\n"));
            throw new AssertionError(message);
        }
    }

    /**
     * Registers a checker.
     */
    private void register(String assertApiSig, Consumer<Invoke> checker) {
        JMethod assertApi = hierarchy.getMethod(assertApiSig);
        checkers.put(assertApi, checker);
    }

    private void _assert(boolean result, Invoke invoke) {
        if (!result) {
            failures.add(invoke);
        }
    }

    private void registerCheckers() {
        checkers = Maps.newLinkedHashMap();
        register("<PTAAssert: void notEmpty(java.lang.Object[])>", invoke -> {
            _assert(getStoredVariables(invoke, 0)
                            .stream()
                            .map(pta::getPointsToSet)
                            .noneMatch(Set::isEmpty),
                    invoke);
        });
        register("<PTAAssert: void sizeEquals(int,java.lang.Object[])>", invoke -> {
            Var expectedVar = InvokeUtils.getVar(invoke, 0);
            int expected = ((IntLiteral) expectedVar.getConstValue()).getValue();
            _assert(getStoredVariables(invoke, 1)
                            .stream()
                            .map(pta::getPointsToSet)
                            .allMatch(pts -> pts.size() == expected),
                    invoke);
        });
        register("<PTAAssert: void equals(java.lang.Object[])>", invoke -> {
            Set<Var> vars = getStoredVariables(invoke, 0);
            Set<Obj> pts = pta.getPointsToSet(CollectionUtils.getOne(vars));
            _assert(vars.stream()
                            .map(pta::getPointsToSet)
                            .allMatch(pts::equals),
                    invoke);
        });
        register("<PTAAssert: void contains(java.lang.Object,java.lang.Object[])>", invoke -> {
            Var x = InvokeUtils.getVar(invoke, 0);
            Set<Obj> xPts = pta.getPointsToSet(x);
            _assert(getStoredVariables(invoke, 1)
                            .stream()
                            .map(pta::getPointsToSet)
                            .allMatch(xPts::containsAll),
                    invoke);
        });
        register("<PTAAssert: void hasInstanceOf(java.lang.String,java.lang.Object[])>", invoke -> {
            Var classNameVar = InvokeUtils.getVar(invoke, 0);
            String className = ((StringLiteral) classNameVar.getConstValue()).getString();
            JClass expected = hierarchy.getClass(className);
            _assert(getStoredVariables(invoke, 1)
                            .stream()
                            .map(pta::getPointsToSet)
                            .allMatch(pts -> pts.stream()
                                    .map(obj -> ((ClassType) obj.getType()).getJClass())
                                    .anyMatch(actual -> hierarchy.isSubclass(expected, actual))),
                    invoke);
        });
        register("<PTAAssert: void hasInstanceOf(java.lang.Object,java.lang.String[])>", invoke -> {
            Var x = InvokeUtils.getVar(invoke, 0);
            Set<JClass> actualClasses = pta.getPointsToSet(x)
                    .stream()
                    .map(obj -> ((ClassType) obj.getType()).getJClass())
                    .collect(Collectors.toSet());
            Set<JClass> expectedClasses = getStoredVariables(invoke, 1)
                    .stream()
                    .map(v -> ((StringLiteral) v.getConstValue()).getString())
                    .map(hierarchy::getClass)
                    .collect(Collectors.toSet());
            _assert(expectedClasses.stream()
                            .allMatch(expected -> actualClasses.stream()
                                    .anyMatch(actual -> hierarchy.isSubclass(expected, actual))),
                    invoke);
        });
        register("<PTAAssert: void notEquals(java.lang.Object,java.lang.Object)>", invoke -> {
            Var x = InvokeUtils.getVar(invoke, 0);
            Var y = InvokeUtils.getVar(invoke, 1);
            _assert(!pta.getPointsToSet(x).equals(pta.getPointsToSet(y)), invoke);
        });
        register("<PTAAssert: void disjoint(java.lang.Object,java.lang.Object)>", invoke -> {
            Var x = InvokeUtils.getVar(invoke, 0);
            Var y = InvokeUtils.getVar(invoke, 1);
            _assert(!Sets.haveOverlap(pta.getPointsToSet(x), pta.getPointsToSet(y)), invoke);
        });
        register("<PTAAssert: void calls(java.lang.String[])>", invoke -> {
            Invoke callSite = findCallSiteBefore(invoke);
            Set<JMethod> callees = callGraph.getCalleesOf(callSite);
            _assert(getStoredVariables(invoke, 0)
                            .stream()
                            .map(v -> ((StringLiteral) v.getConstValue()).getString())
                            .map(hierarchy::getMethod)
                            .allMatch(callees::contains),
                    invoke);
        });
        register("<PTAAssert: void reachable(java.lang.String[])>", invoke -> {
            _assert(getStoredVariables(invoke, 0)
                            .stream()
                            .map(v -> ((StringLiteral) v.getConstValue()).getString())
                            .map(hierarchy::getMethod)
                            .allMatch(callGraph::contains),
                    invoke);
        });
    }

    private static Set<Var> getStoredVariables(Invoke invoke, int index) {
        Var array = InvokeUtils.getVar(invoke, index);
        return invoke.getContainer().getIR()
                .stmts()
                .filter(s -> s instanceof StoreArray store
                        && store.getArrayAccess().getBase().equals(array))
                .map(s -> ((StoreArray) s).getRValue())
                .collect(Collectors.toSet());
    }

    private static Invoke findCallSiteBefore(Invoke invoke) {
        IR ir = invoke.getContainer().getIR();
        for (int i = invoke.getIndex() - 1; i >= 0; --i) {
            if (ir.getStmt(i) instanceof Invoke callSite) {
                return callSite;
            }
        }
        throw new RuntimeException("No call site before " + invoke);
    }
}

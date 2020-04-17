/*
 * Bamboo - A Program Analysis Framework for Java
 *
 * Copyright (C) 2020 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020 Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * This software is designed for the "Static Program Analysis" course at
 * Nanjing University, and it supports a subset of Java features.
 * Bamboo is only for educational and academic purposes, and any form of
 * commercial use is disallowed.
 */

package bamboo.dataflow.analysis.deadcode;

import bamboo.dataflow.analysis.constprop.ConstantPropagation;
import bamboo.dataflow.analysis.livevar.LiveVariableAnalysis;
import soot.Body;
import soot.G;
import soot.Unit;
import soot.jimple.GotoStmt;
import soot.jimple.NopStmt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Boosts dead code elimination and checks whether the analysis result
 * is correct by comparing it with prepared expected result.
 */
public class ResultChecker {

    // ---------- static members ----------
    /**
     * The current result checker
     */
    private static ResultChecker checker;

    private static void setChecker(ResultChecker checker) {
        ResultChecker.checker = checker;
    }

    public static boolean isAvailable() {
        return checker != null;
    }

    public static ResultChecker get() {
        return checker;
    }

    /**
     * The entry function of whole checking mechanism.
     * @param args the arguments for running Soot
     * @param path the path string of the expected result file
     * @return the mismatched information in form of set of strings
     */
    public static Set<String> check(String[] args, String path) {
        ResultChecker checker = new ResultChecker(Paths.get(path));
        setChecker(checker);
        ConstantPropagation.setOutput(false);
        LiveVariableAnalysis.setOutput(false);
        DeadCodeElimination.setOutput(false);

        G.reset(); // reset the whole Soot environment
        Main.main(args);
        return checker.getMismatches();
    }

    // ---------- instance members ----------
    private Map<String, Set<Integer>> expectedResult;

    private Set<String> mismatches = new TreeSet<>();

    ResultChecker(Path filePath) {
        readExpectedResult(filePath);
    }

    Set<String> getMismatches() {
        return mismatches;
    }

    /**
     * Compares the analysis result with expected result, and stores
     * any found mismatches.
     */
    public void compare(Body body, Set<Unit> analysisResult) {
        String method = body.getMethod().getSignature();
        Set<Integer> expectedDeadCode = expectedResult.get(method);
        if (expectedDeadCode != null) {
            body.getUnits()
                    .stream()
                    .filter(u -> !(u instanceof GotoStmt || u instanceof NopStmt))
                    .forEach(u -> {
                        int lineNumber = u.getJavaSourceStartLineNumber();
                        if (analysisResult.contains(u)
                                && !expectedDeadCode.contains(lineNumber)) {
                            mismatches.add(String.format("\n%s:L%d, '%s' should not be dead code",
                                  method, lineNumber, u));
                        } if (!analysisResult.contains(u)
                                && expectedDeadCode.contains(lineNumber)) {
                            mismatches.add(String.format("\n%s:L%d, '%s' should be dead code",
                                    method, lineNumber, u));
                }
            });
        }
    }

    /**
     * Reads expected result from given file path.
     */
    private void readExpectedResult(Path filePath) {
        expectedResult = new TreeMap<>();
        String currentMethod = null;  // method signature
        try {
            for (String line : Files.readAllLines(filePath)) {
                if (isMethodSignature(line)) {
                    currentMethod = line;
                    expectedResult.put(currentMethod, new TreeSet<>());
                } else if (!isEmpty(line)) {
                    expectedResult.get(currentMethod).add(Integer.valueOf(line));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read " + filePath
                    + " caused by " + e);
        }
    }

    private boolean isMethodSignature(String line) {
        return line.startsWith("<");
    }

    private boolean isEmpty(String line) {
        return line.trim().equals("");
    }

}

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

package bamboo.pta.jimple;

import bamboo.pta.element.Method;
import bamboo.pta.element.Variable;
import bamboo.pta.statement.Statement;
import soot.SootMethod;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class JimpleMethod implements Method {

    private final SootMethod method;

    private final JimpleType classType;

    private JimpleVariable thisVar;

    private List<Variable> parameters = Collections.emptyList();

    private Set<Variable> returnVars = Collections.emptySet();

    private Set<Statement> statements = Collections.emptySet();

    JimpleMethod(SootMethod method, JimpleType classType) {
        this.method = method;
        this.classType = classType;
    }

    void setThisVar(JimpleVariable thisVar) {
        this.thisVar = thisVar;
    }

    void setParameters(List<Variable> parameters) {
        this.parameters = parameters;
    }

    void addReturnVar(JimpleVariable returnVar) {
        if (returnVars.isEmpty()) {
            returnVars = new HashSet<>(4);
        }
        returnVars.add(returnVar);
    }

    void addStatement(Statement statement) {
        if (statements.isEmpty()) {
            statements = new HashSet<>(8);
        }
        statements.add(statement);
    }

    SootMethod getSootMethod() {
        return method;
    }

    @Override
    public boolean isInstance() {
        return !method.isStatic();
    }

    @Override
    public boolean isStatic() {
        return method.isStatic();
    }

    @Override
    public boolean isNative() {
        return method.isNative();
    }

    @Override
    public JimpleType getClassType() {
        return classType;
    }

    @Override
    public String getName() {
        return method.getName();
    }

    @Override
    public JimpleVariable getThis() {
        return thisVar;
    }

    @Override
    public List<Variable> getParameters() {
        return parameters;
    }

    @Override
    public Set<Variable> getReturnVariables() {
        return returnVars;
    }

    @Override
    public Set<Statement> getStatements() {
        return statements;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JimpleMethod that = (JimpleMethod) o;
        return method.equals(that.method);
    }

    @Override
    public int hashCode() {
        return method.hashCode();
    }

    @Override
    public String toString() {
        return method.toString();
    }
}

/*
 * Tai-e: A Program Analysis Framework for Java
 *
 * Copyright (C) 2020 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020 Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * This software is designed for the "Static Program Analysis" course at
 * Nanjing University, and it supports a subset of Java features.
 * Tai-e is only for educational and academic purposes, and any form of
 * commercial use is disallowed.
 */

package pascal.taie.java.classes;

import pascal.taie.java.types.Type;
import pascal.taie.util.InternalCanonicalized;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static pascal.taie.util.CollectionUtils.newConcurrentMap;

/**
 * Method name and descriptor.
 */
@InternalCanonicalized
public class Subsignature {

    private static final ConcurrentMap<String, Subsignature> map
            = newConcurrentMap();

    private final String subsig;

    public static Subsignature get(
            String name, List<Type> parameterTypes, Type returnType) {
        return get(StringReps.toSubsignature(name, parameterTypes, returnType));
    }

    public static Subsignature get(String subsig) {
        return map.computeIfAbsent(subsig, Subsignature::new);
    }

    public static void reset() {
        map.clear();
    }

    private Subsignature(String subsig) {
        this.subsig = subsig;
    }

    @Override
    public String toString() {
        return subsig;
    }
}

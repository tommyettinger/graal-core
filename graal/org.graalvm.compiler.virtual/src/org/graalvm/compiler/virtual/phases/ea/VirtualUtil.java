/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.virtual.phases.ea;

import static org.graalvm.compiler.core.common.GraalOptions.TraceEscapeAnalysis;

import java.util.List;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeFlood;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.util.EconomicMap;
import org.graalvm.util.Equivalence;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class VirtualUtil {

    private VirtualUtil() {
        GraalError.shouldNotReachHere();
    }

    public static boolean assertNonReachable(StructuredGraph graph, List<Node> obsoleteNodes) {
        // Helper code that determines the paths that keep obsolete nodes alive.
        // Nodes with support for GVN can be kept alive by GVN and are therefore not part of the
        // assertion.

        NodeFlood flood = graph.createNodeFlood();
        EconomicMap<Node, Node> path = EconomicMap.create(Equivalence.IDENTITY);
        flood.add(graph.start());
        for (Node current : flood) {
            if (current instanceof AbstractEndNode) {
                AbstractEndNode end = (AbstractEndNode) current;
                flood.add(end.merge());
                if (!path.containsKey(end.merge())) {
                    path.put(end.merge(), end);
                }
            } else {
                for (Node successor : current.successors()) {
                    flood.add(successor);
                    if (!path.containsKey(successor)) {
                        path.put(successor, current);
                    }
                }
            }
        }

        for (Node node : obsoleteNodes) {
            if (node instanceof FixedNode && !node.isDeleted()) {
                assert !flood.isMarked(node) : node;
            }
        }

        for (Node node : graph.getNodes()) {
            if (flood.isMarked(node)) {
                for (Node input : node.inputs()) {
                    flood.add(input);
                    if (!path.containsKey(input)) {
                        path.put(input, node);
                    }
                }
            }
        }
        for (Node current : flood) {
            for (Node input : current.inputs()) {
                flood.add(input);
                if (!path.containsKey(input)) {
                    path.put(input, current);
                }
            }
        }
        boolean success = true;
        for (Node node : obsoleteNodes) {
            if (!node.isDeleted() && flood.isMarked(node) && !node.getNodeClass().valueNumberable()) {
                TTY.println("offending node path:");
                Node current = node;
                TTY.print(current.toString());
                while (true) {
                    current = path.get(current);
                    if (current != null) {
                        TTY.print(" -> " + current.toString());
                        if (current instanceof FixedNode && !obsoleteNodes.contains(current)) {
                            break;
                        }
                    }
                }
                success = false;
            }
        }
        if (!success) {
            TTY.println();
            Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "assertNonReachable");
        }
        return success;
    }

    public static void trace(OptionValues options, String msg) {
        if (Debug.isEnabled() && TraceEscapeAnalysis.getValue(options) && Debug.isLogEnabled()) {
            Debug.log(msg);
        }
    }

    public static void trace(OptionValues options, String format, Object obj) {
        if (Debug.isEnabled() && TraceEscapeAnalysis.getValue(options) && Debug.isLogEnabled()) {
            Debug.logv(format, obj);
        }
    }

    public static void trace(OptionValues options, String format, Object obj, Object obj2) {
        if (Debug.isEnabled() && TraceEscapeAnalysis.getValue(options) && Debug.isLogEnabled()) {
            Debug.logv(format, obj, obj2);
        }
    }

    public static void trace(OptionValues options, String format, Object obj, Object obj2, Object obj3) {
        if (Debug.isEnabled() && TraceEscapeAnalysis.getValue(options) && Debug.isLogEnabled()) {
            Debug.logv(format, obj, obj2, obj3);
        }
    }

    public static void trace(OptionValues options, String format, Object obj, Object obj2, Object obj3, Object obj4) {
        if (Debug.isEnabled() && TraceEscapeAnalysis.getValue(options) && Debug.isLogEnabled()) {
            Debug.logv(format, obj, obj2, obj3, obj4);
        }
    }

    public static boolean matches(StructuredGraph graph, String filter) {
        if (filter != null) {
            return matchesHelper(graph, filter);
        }
        return true;
    }

    private static boolean matchesHelper(StructuredGraph graph, String filter) {
        if (filter.startsWith("~")) {
            ResolvedJavaMethod method = graph.method();
            return method == null || !method.format("%H.%n").contains(filter.substring(1));
        } else {
            ResolvedJavaMethod method = graph.method();
            return method != null && method.format("%H.%n").contains(filter);
        }
    }
}

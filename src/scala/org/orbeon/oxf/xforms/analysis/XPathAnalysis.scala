/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis

import controls.ControlAnalysis
import org.orbeon.saxon.expr._
import java.io.{PrintStream, IOException, StringWriter}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.WriterOutputStream
import org.orbeon.oxf.xforms.function.Instance
import org.orbeon.saxon.value.StringValue
import org.orbeon.saxon.om.NamePool
import collection.mutable.{HashMap, HashSet}

import scala.collection.JavaConversions._

class XPathAnalysis(x: String, p: PathMap, d: Int) {

    private val xpathString: String = x
    private var pathmap: PathMap = p
    private val dependencies: Int = d

    private val paths = new HashSet[String]
//    private val instanceIds: List[String]

    def isDependOnContext: Boolean = (dependencies & StaticProperty.DEPENDS_ON_CONTEXT_ITEM) != 0

    def intersects(touchedPaths: java.util.Set[String]): Boolean = {
        // Return true if any path matches
        // TODO: for now naively just check exact paths
        for (path <- paths) {
            if (touchedPaths.contains(path))
                return true
        }
        false
    }

//    private Set<PathMap.PathMapRoot> findRootsDependingOnContext() {
//        final Set<PathMap.PathMapRoot> result = new HashSet<PathMap.PathMapRoot>();
//        for (final PathMap.PathMapRoot root: pathmap.getPathMapRoots()) {
//            if (root.getRootExpression() instanceof ContextItemExpression) {
//                result.add(root);
//            }
//        }
//        return result;
//    }
//
//    private Set<PathMap.PathMapRoot> findRootsDependingOnVariables() {
//        final Set<PathMap.PathMapRoot> result = new HashSet<PathMap.PathMapRoot>();
//        for (final PathMap.PathMapRoot root: pathmap.getPathMapRoots()) {
//            if (root.getRootExpression() instanceof VariableReference) {
//                result.add(root);
//            }
//        }
//        return result;
//    }

    def rebase(parentAnalysis: XPathAnalysis, inScopeVariables: java.util.Map[String, ControlAnalysis]) {
        val finalNodes = new HashMap[String, java.util.List[PathMap.PathMapNode]];

        // TODO: check returnable stuff

        for (root <- pathmap.getPathMapRoots()) {
            val rootExpression = root.getRootExpression()

            // TODO xxx try case matching

            if (rootExpression.isInstanceOf[VariableReference]) {
                val variableName = getSaxonVariableName(rootExpression.asInstanceOf[VariableReference]);
                val variableAnalysis = inScopeVariables.get(variableName).valueAnalysis;

                val finals = getFinals(pathmap, finalNodes, variableAnalysis, variableName);
                rebase(finals, root);
                pathmap.removeRoot(root);
            } else if (rootExpression.isInstanceOf[ContextItemExpression]) {
                val finals = getFinals(pathmap, finalNodes, parentAnalysis, ".");
                rebase(finals, root);
                pathmap.removeRoot(root);
            } else {
                // Keep root as is
            }
        }
    }

    private def getFinals(result: PathMap, finalNodes: HashMap[String, java.util.List[PathMap.PathMapNode]], baseAnalysis: XPathAnalysis, name: String): java.util.List[PathMap.PathMapNode] = {
        if (finalNodes.contains(name))
            finalNodes(name)
        else {
            // Clone the parent and find its final nodes
            val cloneParent = baseAnalysis.pathmap.clone()
            val finals = cloneParent.findFinalNodes()
            finalNodes += (name -> finals)

            // Add all the parent roots
            result.addRoots(cloneParent.getPathMapRoots())

            return finals
        }
    }

    private def rebase(finals: java.util.List[PathMap.PathMapNode], root: PathMap.PathMapRoot) {
        if (root.getArcs().length > 0) {
            val arcs = java.util.Arrays.asList(root.getArcs()).asInstanceOf[java.util.List[PathMap.PathMapArc]]
            for (returnedNode <- finals) {
                returnedNode.addArcs(arcs);
            }
        }
    }

//    private PathMap rebase(XPathAnalysis parentAnalysis, List<PathMap.PathMapNode> roots) {
//
//        final List<PathMap.PathMapNode> returnedNodes = result.findFinalNodes();
//
//        for (final PathMap.PathMapNode returnedNode: returnedNodes) {
//            for (final PathMap.PathMapNode contextRoot: roots) {
//                returnedNode.addArcs(Arrays.asList(contextRoot.getArcs()));
//            }
//        }
//        return result;
//    }

    private def getSaxonVariableName(variableReference: VariableReference): String = {
        // FIXME: Hack to get variable name with Saxon 8. Saxon 9 has getDisplayName() and getBinding.getVariableQName().
        val sb = new StringWriter()
        val os =
            try {
                new WriterOutputStream(sb)
            } catch {
                case e: IOException => throw new OXFException(e)
            }
        os.setCharset("utf-8");
        variableReference.display(0, new PrintStream(os), null);
        return sb.toString().trim().substring(1);
    }

    def processPaths() {
        val stack = new java.util.ArrayList[Expression]
        for (root <- pathmap.getPathMapRoots()) {
            stack.add(root.getRootExpression())
            processNode(paths, stack, root)
            stack.remove(stack.size() - 1)
        }
    }

    def processNode(result: HashSet[String], stack: java.util.List[Expression], node: PathMap.PathMapNode) {
        if (node.getArcs().length == 0) {
            val sb = new StringBuilder();
            for (expression <- stack) {
                expression match {
                    case x: Instance =>
                        sb.append("instance('")
                        sb.append(x.getArguments()(0).asInstanceOf[StringValue].getStringValue())
                        sb.append("')")
                    case x: AxisExpression =>
                        if (sb.length > 0)
                            sb.append('/')
                        val fingerprint = x.getNodeTest().getFingerprint()
                        sb.append(if (fingerprint != -1 ) NamePool.getDefaultNamePool().getDisplayName(fingerprint) else "text()")
                }

//                if (expression.isInstanceOf[Instance]) {
//                    sb.append("instance('")
//                    sb.append(expression.asInstanceOf[Instance].getArguments()(0).asInstanceOf[StringValue].getStringValue())
//                    sb.append("')")
//                } else if (expression.isInstanceOf[AxisExpression]) {
//                    if (sb.length > 0)
//                        sb.append('/')
//                    sb.append(NamePool.getDefaultNamePool().getDisplayName(expression.asInstanceOf[AxisExpression].getNodeTest().getFingerprint()))
//                }
            }
            result.add(sb.toString())
        } else {
            for (arc <- node.getArcs()) {
                stack += arc.getStep()
                processNode(result, stack, arc.getTarget())
                stack.remove(stack.size - 1)
            }
        }
    }

//        private static List<String> foobar(PathMap pathmap) {
//
//            List<String> instanceIds = null;
//            final PathMap.PathMapRoot[] roots = pathmap.getPathMapRoots();
//            for (final PathMap.PathMapRoot root: roots) {
//                final Expression rootExpression = root.getRootExpression();
//
//                if (rootExpression instanceof Instance || rootExpression instanceof XXFormsInstance) {
//                    final FunctionCall functionCall = (FunctionCall) rootExpression;
//
//                    // TODO: Saxon 9.0 expressions should test "instanceof StringValue" to "instanceof StringLiteral"
//                    if (functionCall.getArguments()[0] instanceof StringValue) {
//                        final String instanceName = ((StringValue) functionCall.getArguments()[0]).getStringValue();
//                        if (instanceIds == null)
//                            instanceIds = new ArrayList<String>();
//                        instanceIds.add(instanceName);
//                    } else {
//                        // Instance name is not known at compile time
//                        return null;
//                    }
//                } else if (rootExpression instanceof Doc) {// don't need document() function as that is XSLT
//                    final FunctionCall functionCall = (FunctionCall) rootExpression;
//
//                    // TODO: Saxon 9.0 expressions should test "instanceof StringValue" to "instanceof StringLiteral"
//                    if (functionCall.getArguments()[0] instanceof StringValue) {
//    //                            final String literalURI = ((StringValue) functionCall.getArguments()[0]).getStringValue();
//                        return true;
//                    } else {
//                        // Document name is not known at compile time
//                        return true;
//                    }
//                } else if (rootExpression instanceof ContextItemExpression) {
//                    return true;
//                } else if (rootExpression instanceof RootExpression) {
//                    // We depend on the current XForms model.
//                    return true;
//                }
//
//    //                                final PathMap.PathMapArc[] rootArcs = root.getArcs();
//    //
//    //                                for (int j = 0; j < rootArcs.length; j++) {
//    //                                    final PathMapArc currentArc = rootArcs[j];
//    //                                    final AxisExpression getStep
//    //                                }
//
//            }
//            return false;
//        }

    def dump(out: PrintStream) {
        out.println("PATHMAP - expression: " + xpathString);

        for (path <- paths)
            out.println("  path: " + path);

        pathmap.diagnosticDump(out);
        dumpDependencies(out);
//            if (instanceIds == null)
//                out.println("  EXPRESSION DEPENDS ON MORE THAN INSTANCES: " + xpathString);
//            else {
//                out.println("  EXPRESSION DEPENDS ON INSTANCES: " + xpathString);
//                for (final String instanceId: instanceIds) {
//                    out.println("    instance: " + instanceId);
//                }
//            }
    }

    def dumpDependencies(out: PrintStream) {
        if ((dependencies & StaticProperty.DEPENDS_ON_CONTEXT_ITEM) != 0) {
            out.println("  DEPENDS_ON_CONTEXT_ITEM");
        }
        if ((dependencies & StaticProperty.DEPENDS_ON_CURRENT_ITEM) != 0) {
            out.println("  DEPENDS_ON_CURRENT_ITEM");
        }
        if ((dependencies & StaticProperty.DEPENDS_ON_CONTEXT_DOCUMENT) != 0) {
            out.println("  DEPENDS_ON_CONTEXT_DOCUMENT");
        }
        if ((dependencies & StaticProperty.DEPENDS_ON_LOCAL_VARIABLES) != 0) {
            out.println("  DEPENDS_ON_LOCAL_VARIABLES");
        }
        if ((dependencies & StaticProperty.NON_CREATIVE) != 0) {
            out.println("  NON_CREATIVE");
        }
    }
}
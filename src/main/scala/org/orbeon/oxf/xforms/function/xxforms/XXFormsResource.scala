/**
 * Copyright (C) 2013 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xforms.function.{Instance, FunctionSupport, XFormsFunction}
import org.orbeon.saxon.expr.{AxisExpression, StringLiteral, PathMap, XPathContext}
import org.orbeon.saxon.om.{Axis, NamespaceConstant, StructuredQName, NodeInfo}
import org.orbeon.saxon.`type`.Type
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.xforms.XFormsInstance
import org.orbeon.saxon.value.StringValue
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.analysis.AVTLangRef
import org.orbeon.saxon.pattern.NameTest
import org.orbeon.oxf.util.ScalaUtils._
import org.apache.commons.lang3.StringUtils

class XXFormsResource extends XFormsFunction with FunctionSupport {

    override def evaluateItem(xpathContext: XPathContext): StringValue = {

        implicit val ctx = xpathContext

        def findResourcesElement =
            resolveOrFindByEffectiveId("orbeon-resources") orElse
            resolveOrFindByEffectiveId("fr-form-resources") collect
            { case instance: XFormsInstance ⇒ instance.rootElement }

        def findResourceElementForLang(resourcesElement: NodeInfo, requestedLang: String) = {
            val availableLangs = resourcesElement \ "resource" \@ "lang"
            availableLangs find (_ === requestedLang) orElse availableLangs.headOption flatMap (_.parentOption)
        }

        val resultOpt =
            for {
                elementAnalysis ← elementAnalysisForSource
                resources       ← findResourcesElement
                requestedLang   ← XXFormsLang.resolveXMLangHandleAVTs(getContainingDocument, elementAnalysis)
                resourceRoot    ← findResourceElementForLang(resources, requestedLang)
                leaf            ← path(resourceRoot, StringUtils.replace(stringArgument(0), ".", "/"))
            } yield
                stringToStringValue(leaf.stringValue)

        resultOpt.orNull
    }

    override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet): PathMap.PathMapNodeSet = {

        // Only support dependencies if we can figure out the resource path statically
        // In theory, we could also support the case where we don't know the path, and assume that any change to the
        // resources would cause a miss. But I don't think we have a way to express this right now. Note that the case
        // where a single resource changes is rare as we tend to use a readonly instance for resources anyway. But the
        // function must work in either case, readonly and readwrite.
        val resourcePath = arguments(0) match {
            case s: StringLiteral ⇒
                split[List](s.getStringValue, "/.")
            case _ ⇒
                pathMap.setInvalidated(true)
                return null
        }

        // Dependency on language

        val avtLangAnalysis = sourceElementAnalysis(pathMap).lang collect {
            case ref: AVTLangRef ⇒ ref.att.getValueAnalysis.get
        }

        // Only add the dependency if xml:lang is not a literal
        avtLangAnalysis foreach {
            case analysis: PathMapXPathAnalysis ⇒
                // There is a pathmap for the xml:lang AVT, so add the new roots
                pathMap.addRoots(analysis.pathmap.get.clone.getPathMapRoots)
                //pathMap.findFinalNodes // FIXME: needed?
                //pathMap.updateFinalNodes(finalNodes)
            case analysis if ! analysis.figuredOutDependencies ⇒
                // Dependencies not found
                pathMap.setInvalidated(true)
                return null
            case _ ⇒ // NOP
        }

        // Dependency on all arguments
        arguments foreach (_.addToPathMap(pathMap, pathMapNodeSet))

        val namePool = getExecutable.getConfiguration.getNamePool

        // Add dependency as if on instance('my-instance-name')
        def addInstanceDependency(name: String) = {

            def newInstanceExpression = {
                val instanceExpression = new Instance

                instanceExpression.setFunctionName(new StructuredQName("", NamespaceConstant.FN, "instance"))
                instanceExpression.setArguments(Array(new StringLiteral(name)))

                instanceExpression
            }

            // Start with new instance() root
            var target = new PathMap.PathMapNodeSet(pathMap.makeNewRoot(newInstanceExpression))

            // Add path elements to pathmap
            "resource" :: resourcePath foreach { name ⇒
                val test = new NameTest(Type.ELEMENT, "", name, namePool)
                target = new AxisExpression(Axis.CHILD, test).addToPathMap(pathMap, target)
            }

            // The result is used as an atomic value
            target.setAtomized()
        }

        addInstanceDependency("orbeon-resources")
        addInstanceDependency("fr-form-resources")

        // We return an atomic value
        null
    }
}

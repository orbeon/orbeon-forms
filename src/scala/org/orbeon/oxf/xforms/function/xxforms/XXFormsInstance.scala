/**
 * Copyright (C) 2010 Orbeon, Inc.
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

import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr._
import org.orbeon.saxon.om._

/**
 * xxforms:instance() function. This function operates like the standard instance() function, except that it looks for
 * instances globally instead of using the current model.
 */
class XXFormsInstance extends XFormsFunction {

    override def iterate(xpathContext: XPathContext): SequenceIterator = {
        val containingDocument = getContainingDocument(xpathContext)

        val instanceId     = argument(0).evaluateAsString(xpathContext).toString
        // TODO: Argument is undocumented. Is it ever used at all? We now have a syntax for absolute ids so don't really need it.
        val useEffectiveId = argument.lift(1) map (_.effectiveBooleanValue(xpathContext)) getOrElse false

        val rootNodeIterator =
            if (useEffectiveId) {
                // Find a concrete instance
                Option(containingDocument.getObjectByEffectiveId(instanceId)) collect
                    { case i: XFormsInstance ⇒ i } map
                        (instance ⇒ SingletonIterator.makeIterator(instance.instanceRoot))
            } else {
                // Search ancestor-or-self containers as suggested here: http://wiki.orbeon.com/forms/projects/xforms-model-scoping-rules

                val startContainer = getXBLContainer(xpathContext)

                // The idea here is that we first try to find a concrete instance. If that fails, we try to see if it
                // exists statically. If it does exist statically only, we return an empty sequence, but we don't warn
                // as the instance actually exists. The case where the instance might exist statically but not
                // dynamically is when this function is used during xforms-model-construct. At that time, instances in
                // this or other models might not yet have been constructed, however they might be referred to, for
                // example with model variables.

                def findDynamic = {
                    val containers = Iterator.iterate(startContainer)(_.getParentXBLContainer) takeWhile (_ ne null)
                    val instances  = containers flatMap (c ⇒ Option(c.findInstance(instanceId)))
                    if (instances.hasNext) Some(SingletonIterator.makeIterator(instances.next().instanceRoot)) else None
                }

                def findStatic = {

                    val ops = containingDocument.getStaticOps
                    val startScope = startContainer.innerScope

                    val scopes    = Iterator.iterate(startScope)(_.parent) takeWhile (_ ne null)
                    val instances = scopes flatMap ops.getModelsForScope flatMap (_.instances.get(instanceId))

                    if (instances.hasNext) Some(EmptyIterator.getInstance) else None
                }

                findDynamic orElse findStatic
            }

        // Return or warn
        rootNodeIterator match {
            case Some(iterator) ⇒
                iterator
            case None ⇒
                getContainingDocument(xpathContext).getIndentedLogger(XFormsModel.LOGGING_CATEGORY).logWarning("xxforms:instance()", "instance not found", "instance id", instanceId)
                EmptyIterator.getInstance
        }
    }

    override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet) = {
        // TODO: if argument[1] is true, must search globally
        argument(0).addToPathMap(pathMap, pathMapNodeSet)
        new PathMap.PathMapNodeSet(pathMap.makeNewRoot(this))
    }
}
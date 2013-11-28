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
package org.orbeon.oxf.xforms.function

import org.apache.commons.lang3.StringUtils
import org.orbeon.oxf.xforms.XFormsModel
import org.orbeon.saxon.expr._
import org.orbeon.saxon.om._

/**
 * XForms instance() function.
 *
 * 7.11.1 The instance() Function
 */
class Instance extends XFormsFunction {

    override def iterate(xpathContext: XPathContext): SequenceIterator = {

        implicit val ctx = xpathContext

        // "If the argument is omitted or is equal to the empty string, then the root element node (also called the
        // document element node) is returned for the default instance in the model that contains the current context
        // node."

        val instanceId = argument.lift(0) map (_.evaluateAsString(xpathContext).toString.trim) filter StringUtils.isNotBlank

        // Get model and instance with given id for that model only

        // "If a match is located, and the matching instance data is associated with the same XForms Model as the
        // current context node, this function returns a node-set containing just the root element node (also called
        // the document element node) of the referenced instance data. In all other cases, an empty node-set is
        // returned."

        // NOTE: Model can be null when there is no model in scope at all
        val iterator =
            Option(XFormsFunction.context.model) match {
                case Some(model) ⇒

                    // The idea here is that we first try to find a concrete instance. If that fails, we try to see if it
                    // exists statically. If it does exist statically only, we return an empty sequence, but we don't warn
                    // as the instance actually exists. The case where the instance might exist statically but not
                    // dynamically is when this function is used during xforms-model-construct. At that time, instances in
                    // this or other models might not yet have been constructed, however they might be referred to, for
                    // example with model variables.

                    val dynamicInstance = instanceId match {
                        case Some(instanceId) ⇒ Option(model.getInstance(instanceId))
                        case None             ⇒ Option(model.getDefaultInstance)
                    }

                    val staticInstance = instanceId match {
                        case Some(instanceId) ⇒ model.staticModel.instances.get(instanceId)
                        case None             ⇒ model.staticModel.defaultInstance
                    }

                    def findDynamic = dynamicInstance map (instance ⇒ SingletonIterator.makeIterator(instance.rootElement))
                    def findStatic  = staticInstance map (_ ⇒ EmptyIterator.getInstance)

                    findDynamic orElse findStatic
                case _ ⇒ None
            }

        iterator match {
            case Some(iterator) ⇒
                iterator
            case None ⇒
                XFormsFunction.context.containingDocument.getIndentedLogger(XFormsModel.LOGGING_CATEGORY).logWarning("instance()", "instance not found", "instance id", instanceId.orNull)
                EmptyIterator.getInstance
        }
    }

    override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet): PathMap.PathMapNodeSet = {
        if (argument.length > 0) argument(0).addToPathMap(pathMap, pathMapNodeSet)
        new PathMap.PathMapNodeSet(pathMap.makeNewRoot(this))
    }
}

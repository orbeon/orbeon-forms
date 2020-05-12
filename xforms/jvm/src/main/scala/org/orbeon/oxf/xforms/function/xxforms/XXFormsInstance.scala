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

import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.model.{XFormsInstance, XFormsModel}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.saxon.expr._
import org.orbeon.saxon.om._
import org.orbeon.xforms.XFormsId

/**
 * xxf:instance() function. This function operates like the standard instance() function, except that it looks for
 * instances globally instead of using the current model.
 */
class XXFormsInstance extends XFormsFunction {

  override def iterate(xpathContext: XPathContext): SequenceIterator = {

    implicit val ctx = xpathContext

    val instanceId = stringArgument(0)

    val rootElementOpt =
      XXFormsInstance.findInAncestorScopes(XFormsFunction.context.container, instanceId)

    // Return or warn
    rootElementOpt match {
      case Some(root) =>
        SingletonIterator.makeIterator(root)
      case None =>
        EmptyIterator.getInstance
    }
  }

  override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet) = {
    argument(0).addToPathMap(pathMap, pathMapNodeSet)
    new PathMap.PathMapNodeSet(pathMap.makeNewRoot(this))
  }
}

object XXFormsInstance {

  // Search ancestor-or-self containers as suggested here: http://wiki.orbeon.com/forms/projects/xforms-model-scoping-rules
  // Also allow an absolute instance id
  def findInAncestorScopes(startContainer: XBLContainer, instanceId: String): Option[NodeInfo] = {

    // The idea here is that we first try to find a concrete instance. If that fails, we try to see if it
    // exists statically. If it does exist statically only, we return an empty sequence, but we don't warn
    // as the instance actually exists. The case where the instance might exist statically but not
    // dynamically is when this function is used during xforms-model-construct. At that time, instances in
    // this or other models might not yet have been constructed, however they might be referred to, for
    // example with model variables.

    def findObjectByAbsoluteId(id: String) =
      startContainer.containingDocument.getObjectByEffectiveId(XFormsId.absoluteIdToEffectiveId(id))

    def findAbsolute =
      if (XFormsId.isAbsoluteId(instanceId))
        collectByErasedType[XFormsInstance](findObjectByAbsoluteId(instanceId)) map (_.rootElement)
      else
        None

    def findDynamic = {
      val containers = Iterator.iterateOpt(startContainer)(_.parentXBLContainer)
      val instances  = containers flatMap (_.findInstance(instanceId))

      instances.nextOption() map (_.rootElement)
    }

    def findStatic = {

      val containingDocument = startContainer.containingDocument
      val ops = containingDocument.getStaticOps
      val startScope = startContainer.innerScope

      val scopes    = Iterator.iterateOpt(startScope)(_.parent)
      val instances = scopes flatMap ops.getModelsForScope flatMap (_.instances.get(instanceId))

      if (! instances.hasNext)
        containingDocument.getIndentedLogger(XFormsModel.LoggingCategory).logWarning("xxf:instance()", "instance not found", "instance id", instanceId)

      None
    }

    findAbsolute orElse findDynamic orElse findStatic
  }
}
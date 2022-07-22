/**
  * Copyright (C) 2007 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.xforms.model

import org.orbeon.saxon.om

import scala.collection.compat._
import scala.collection.{mutable => m}


trait RebuildBindOps {

  self: XFormsModelBinds =>

  // TEMP: Picked a different name or `fullOptJS` fails!
  import Private1._

  final def topLevelBinds = _topLevelBinds

  final val singleNodeContextBinds   = m.HashMap[String, RuntimeBind]()
  final val iterationsForContextItem = m.HashMap[om.Item, List[BindIteration]]()

  // Rebuild all binds, computing all bind nodesets (but not computing the MIPs)
  def rebuild(): Unit =
    withDebug("performing rebuild", List("model id" -> model.getEffectiveId)) {

      // NOTE: Assume that model.getContextStack().resetBindingContext(model) was called

      // Clear all instances that might have InstanceData
      // Only need to do this after the first rebuild
      if (! _isFirstRebuildForModel)
        for (instance <- model.instancesIterator) {
          // Only clear instances that are impacted by xf:bind/(@ref|@nodeset), assuming we were able to
          // figure out the dependencies.
          // The reason is that clearing this state can take quite some time
          val instanceMightBeSchemaValidated =
            model.hasSchema && instance.isSchemaValidation

          val instanceMightHaveMips =
            dependencies.hasAnyCalculationBind(staticModel, instance.getPrefixedId) ||
            dependencies.hasAnyValidationBind(staticModel, instance.getPrefixedId)

          if (instanceMightBeSchemaValidated || instanceMightHaveMips)
            DataModel.visitElement(instance.rootElement, InstanceData.clearStateForRebuild)
        }

      // Not ideal, but this state is updated when the bind tree is updated below
      singleNodeContextBinds.clear()
      iterationsForContextItem.clear()

      // Iterate through all top-level bind elements to create new bind tree
      // TODO: In the future, XPath dependencies must allow for partial rebuild of the tree as is the case with controls
      // Even before that, the bind tree could be modified more dynamically as is the case with controls
      _topLevelBinds =
        for (staticBind <- staticModel.topLevelBinds)
          yield new RuntimeBind(model, staticBind, null, isSingleNodeContext = true)

      _isFirstRebuildForModel = false
    }

  // Implement "4.7.2 References to Elements within a bind Element":
  //
  // "When a source object expresses a Single Node Binding or Node Set Binding with a bind attribute, the IDREF of the
  // bind attribute is resolved to a target bind object whose associated nodeset is used by the Single Node Binding or
  // Node Set Binding. However, if the target bind element has one or more bind element ancestors, then the identified
  // bind may be a target element that is associated with more than one target bind object.
  //
  // If a target bind element is outermost, or if all of its ancestor bind elements have nodeset attributes that
  // select only one node, then the target bind only has one associated bind object, so this is the desired target
  // bind object whose nodeset is used in the Single Node Binding or Node Set Binding. Otherwise, the in-scope
  // evaluation context node of the source object containing the bind attribute is used to help select the appropriate
  // target bind object from among those associated with the target bind element.
  //
  // From among the bind objects associated with the target bind element, if there exists a bind object created with
  // the same in-scope evaluation context node as the source object, then that bind object is the desired target bind
  // object. Otherwise, the IDREF resolution produced a null search result."
  def resolveBind(bindId: String, contextItemOpt: Option[om.Item]): Option[RuntimeBind] =
    singleNodeContextBinds.get(bindId) match {
      case some @ Some(_) =>
        // This bind has a single-node context (incl. top-level bind), so ignore context item and just return
        // the bind nodeset
        some
      case None =>
        // Nested bind: use context item

        // "From among the bind objects associated with the target bind element, if there exists a bind object
        // created with the same in-scope evaluation context node as the source object, then that bind object is
        // the desired target bind object. Otherwise, the IDREF resolution produced a null search result."
        val it =
          for {
            contextItem <- contextItemOpt.iterator
            iterations  <- iterationsForContextItem.get(contextItem).iterator
            iteration   <- iterations
            childBind   <- iteration.findChildBindByStaticId(bindId)
          } yield
            childBind

        it.nextOption()
    }

  private object Private1 {
    var _topLevelBinds          : List[RuntimeBind] = Nil
    var _isFirstRebuildForModel : Boolean           = model.containingDocument.initializing
  }
}

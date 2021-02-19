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
package org.orbeon.oxf.xforms.library

import cats.syntax.option._
import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.control.controls.XFormsSwitchControl
import org.orbeon.oxf.xforms.function.XFormsFunction.relevantControl
import org.orbeon.oxf.xforms.function._
import org.orbeon.oxf.xforms.model.{RuntimeBind, XFormsModel}
import org.orbeon.oxf.xml.dom.Extensions
import org.orbeon.oxf.xml.{OrbeonFunctionLibrary, SaxonUtils}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om
import org.orbeon.saxon.trans.XPathException
import org.orbeon.saxon.value.StringValue
import org.orbeon.scaxon.Implicits
import org.orbeon.xforms.Namespaces

import scala.jdk.CollectionConverters._


/**
 * XForms functions that depend on the XForms environment.
 */
trait XFormsEnvFunctions extends OrbeonFunctionLibrary {

  import XFormsEnvFunctions._

  @XPathFunction
  def index(repeatStaticId: String)(implicit xfc: XFormsFunction.Context): Int =
    findIndexForRepeatId(repeatStaticId)

  @XPathFunction
  def property(propertyName: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[om.Item] = {

    import Property._

//    var arg: Either[(String, String), Map[String, String]] = null
//
//    val uriLocal =
//      arg match {
//        case Left(uriLocal)    => uriLocal
//        case Right(namespaces) =>
//          val propertyNameString = propertyName
//          val qName =
//            Extensions.resolveQName(namespaces, propertyNameString, unprefixedIsNoNamespace = false) getOrElse
//              (throw new XPathException(s"Missing property name"))
//          (qName.namespace.uri, qName.localName)
//      }

    // XXX FIXME hardcoding `xxf` prefix for now
    val uriLocal = {
      SaxonUtils.parseQName(propertyName) match {
        case ("xxf", local) => (Namespaces.XXF, local)
        case other => other
      }
    }

    uriLocal match {
      case (_, local) if local.toLowerCase.contains("password") =>
        // Never return any property containing the string "password" as a first line of defense
        None
      case ("", VersionProperty) =>
        StringValue.makeStringValue(Version).some
      case ("", ConformanceLevelProperty) =>
        StringValue.makeStringValue(ConformanceLevel) .some
      case (Namespaces.XXF, local) =>
        // Property in the `xxf` namespace: return our properties
        Option(xfc.containingDocument.getProperty(local)) map
          (v => SaxonUtils.convertJavaObjectToSaxonObject(v).asInstanceOf[om.Item])
      case (_, _) =>
        throw new XPathException(s"Unknown property: property('$propertyName')")
    }
  }

  // XXX TODO: must extend `InstanceTrait`
  @XPathFunction
  def instance(
    instanceId : String = "")(implicit
    xpc        : XPathContext,
    xfc        : XFormsFunction.Context
  ): Option[om.NodeInfo] =
    instanceImpl(instanceId)

  def instanceImpl(
    instanceId : String = "")(implicit
    xpc        : XPathContext,
    xfc        : XFormsFunction.Context
  ): Option[om.NodeInfo] = {

    // "If the argument is omitted or is equal to the empty string, then the root element node (also called the
    // document element node) is returned for the default instance in the model that contains the current context
    // node."

    val instanceIdOpt = instanceId.trimAllToOpt

    // Get model and instance with given id for that model only

    // "If a match is located, and the matching instance data is associated with the same XForms Model as the
    // current context node, this function returns a node-set containing just the root element node (also called
    // the document element node) of the referenced instance data. In all other cases, an empty node-set is
    // returned."

    // NOTE: Model can be null when there is no model in scope at all
    val rootElemOptOpt =
      xfc.modelOpt match {
        case Some(model) =>

          // The idea here is that we first try to find a concrete instance. If that fails, we try to see if it
          // exists statically. If it does exist statically only, we return an empty sequence, but we don't warn
          // as the instance actually exists. The case where the instance might exist statically but not
          // dynamically is when this function is used during xforms-model-construct. At that time, instances in
          // this or other models might not yet have been constructed, however they might be referred to, for
          // example with model variables.

          def dynamicInstanceOpt = instanceIdOpt match {
            case Some(instanceId) => model.findInstance(instanceId)
            case None             => model.defaultInstanceOpt
          }

          def staticInstanceOpt = instanceIdOpt match {
            case Some(instanceId) => model.staticModel.instances.get(instanceId)
            case None             => model.staticModel.defaultInstanceOpt
          }

          def findDynamic = dynamicInstanceOpt map (_.rootElement.some)
          def findStatic  = staticInstanceOpt.isDefined option None

          findDynamic orElse findStatic
        case _ => None
      }

    rootElemOptOpt match {
      case Some(rootElemOpt) =>
        rootElemOpt
      case None =>
        xfc.containingDocument.getIndentedLogger(XFormsModel.LoggingCategory)
          .logWarning("instance()", "instance not found", "instance id", instanceIdOpt.orNull)
        None
    }
  }

  @XPathFunction
  def current()(implicit xpc: XPathContext): Option[om.Item] = {
    // Go up the stack to find the top-level context
    var currentContext = xpc
    while (currentContext.getCaller ne null)
      currentContext = currentContext.getCaller
    Option(currentContext.getContextItem)
  }

  @XPathFunction
  def context()(implicit xfc: XFormsFunction.Context): Option[om.Item] =
    Option(xfc.bindingContext.contextItem)

  @XPathFunction
  def event(name: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Iterator[om.Item] = {

    // XXX FIXME hardcoding `xxf` prefix for now. Extract namespaces from the static state or `xfc`.
    val namespaceMappings = Map(
      "xxf" -> Namespaces.XXF
    )

    for {
      currentEvent <- xfc.containingDocument.currentEventOpt.iterator
      qName        <- Extensions.resolveQName(namespaceMappings, name, unprefixedIsNoNamespace = true).iterator
      item         <- Implicits.asScalaIterator(currentEvent.getAttribute(qName.clarkName))
    } yield
      item
  }

  // TODO: Should be `Iterator`?
  @XPathFunction
  def valid(items: Iterable[om.Item], relevant: Boolean = true, recurse: Boolean = true): Boolean = throw new NotImplementedError("valid")

  // So we can call as `bind()` and `xxf:bind()`
  def bindImpl(bindId: String, searchAncestors: Boolean)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Iterable[om.Item] = { // should be `om.NodeInfo`?

    val contextItemOpt =
      Option(xpc.getContextItem)

    val startContainer = xfc.container

    val startContainerIt =
      startContainer.searchContainedModelsInScope(xfc.sourceEffectiveId, bindId, contextItemOpt).iterator

    val searchIt =
      if (searchAncestors)
        startContainerIt ++
          startContainer.ancestorsIterator.drop(1).flatMap(_.searchContainedModels(bindId, contextItemOpt))
      else
        startContainerIt

    searchIt.nextOption() match {
      case Some(bind: RuntimeBind) => bind.items.asScala
      case _                       => Nil
    }
  }

  // XForms 2.0
  @XPathFunction
  def bind(bindId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Iterable[om.Item] =
    bindImpl(bindId, searchAncestors = false)

  // XForms 2.0
  @XPathFunction
  def `case`(caseId: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Option[String] =
    for {
      control      <- relevantControl(caseId)
      switch       <- collectByErasedType[XFormsSwitchControl](control)
      selectedCase <- switch.selectedCaseIfRelevantOpt
    } yield
      selectedCase.getId

//    Fun("valid", classOf[XFormsValid], op = 0, min = 0, BOOLEAN, EXACTLY_ONE,
//      Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
//      Arg(BOOLEAN, EXACTLY_ONE),
//      Arg(BOOLEAN, EXACTLY_ONE)
//    )
}

object XFormsEnvFunctions {

  object Property {
    val Version                  = "2.0"
    val ConformanceLevel         = "full"
    val VersionProperty          = "version"
    val ConformanceLevelProperty = "conformance-level"
  }

  def findIndexForRepeatId(repeatStaticId: String)(implicit xfc: XFormsFunction.Context): Int =
    xfc.container.getRepeatIndex(xfc.sourceEffectiveIdOrThrow, repeatStaticId) match {
      case Some(index) =>
        index
      case None =>
        // CHECK: Was `BasicLocationData(getSystemId, getLineNumber, getColumnNumber)`.
        throw new OXFException(s"Function index uses repeat id `$repeatStaticId` which is not in scope")
    }
}
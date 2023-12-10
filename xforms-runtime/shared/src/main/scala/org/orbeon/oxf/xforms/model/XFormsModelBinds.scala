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
package org.orbeon.oxf.xforms.model

import cats.syntax.option._
import org.orbeon.dom.QName
import org.orbeon.dom.saxon.TypedNodeWrapper
import org.orbeon.oxf.common.OrbeonLocationException
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.XPath.Reporter
import org.orbeon.oxf.util.{IndentedLogger, XPath}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.analysis.{XPathDependencies, XPathErrorDetails}
import org.orbeon.oxf.xforms.analysis.model.ModelDefs._
import org.orbeon.oxf.xforms.analysis.model.{Model, StaticBind}
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.events.XXFormsXPathErrorEvent
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData
import org.orbeon.saxon.value.{AtomicValue, QNameValue}
import org.orbeon.scaxon.Implicits._
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.analysis.model.ValidationLevel.ErrorLevel

import scala.collection.compat._
import scala.language.postfixOps
import scala.util.control.NonFatal


class XFormsModelBinds(protected val model: XFormsModel)
  extends RebuildBindOps
     with ValidationBindOps
     with CalculateBindOps {

  protected implicit val containingDocument: XFormsContainingDocument = model.containingDocument
  protected implicit def logger            : IndentedLogger           = model.indentedLogger
  protected implicit def reporter          : XPath.Reporter           = containingDocument.getRequestStats.addXPathStat

  protected val dependencies               : XPathDependencies        = containingDocument.xpathDependencies
  protected val staticModel                : Model                    = model.staticModel

  // Support for `xxf:evaluate-bind-property` function
  def evaluateBindByType(bind: RuntimeBind, position: Int, mipType: QName): Option[AtomicValue] =
    // We don't want to dispatch events while we are performing the actual recalculate/revalidate operation,
    // so we collect them here and dispatch them altogether once everything is done.
    EventCollector.withBufferCollector { collector =>

      val bindNode = bind.getOrCreateBindNode(position)

      def makeQNameValue(qName: QName) =
        new QNameValue(qName.namespace.prefix, qName.namespace.uri, qName.localName)

      def hasSuccessfulErrorConstraints =
        bind.staticBind.constraintsByLevel.nonEmpty option {
          bindNode.staticBind.constraintsByLevel.get(ErrorLevel).to(List) flatMap { mips =>
            failedConstraintMIPs(mips, bindNode, collector)
          } isEmpty
        }

      // NOTE: This only evaluates the first custom MIP of the given name associated with the bind. We do store multiple
      // ones statically, but don't have yet a solution to combine them. Should we string-join them?
      def evaluateCustomMIPByName(mipType: QName) =
        evaluateCustomMIP(
          bindNode,
          bindNode.staticBind.customMIPNameToXPathMIP(buildInternalCustomMIPName(mipType)).head,
          collector
        )

      mipType match {
        case TYPE_QNAME            => bind.staticBind.dataType                                            map makeQNameValue
        case RELEVANT_QNAME        => evaluateBooleanMIP(bindNode, Relevant, DEFAULT_RELEVANT, collector) map booleanToBooleanValue
        case READONLY_QNAME        => evaluateBooleanMIP(bindNode, Readonly, DEFAULT_READONLY, collector) map booleanToBooleanValue
        case REQUIRED_QNAME        => evaluateBooleanMIP(bindNode, Required, DEFAULT_REQUIRED, collector) map booleanToBooleanValue
        case CONSTRAINT_QNAME      => hasSuccessfulErrorConstraints                                       map booleanToBooleanValue
        case CALCULATE_QNAME       => evaluateCalculatedBind(bindNode, Calculate, collector)              map stringToStringValue
        case XXFORMS_DEFAULT_QNAME => evaluateCalculatedBind(bindNode, Default, collector)                map stringToStringValue
        case mipType               => evaluateCustomMIPByName(mipType)                                    map stringToStringValue
      }
    }
}

object XFormsModelBinds {

  type StaticMIP      = StaticBind.MIP
  type StaticXPathMIP = StaticBind.XPathMIP
  type StaticTypeMIP  = StaticBind.TypeMIP

  // Create an instance of XFormsModelBinds if the given model has xf:bind elements.
  def apply(model: XFormsModel): Option[XFormsModelBinds] =
    model.staticModel.hasBinds option new XFormsModelBinds(model)

  // Modified email validator which:
  //
  // 1. Doesn't check whether a TLD is known, as there are two many of those now (2015) and changing constantly.
  // 2. Doesn't support IP addresses (we probably could, but we don't care right now).
  //
  object EmailValidatorNoDomainValidation extends EmailValidator(allowLocal = false) {

    private val DomainLabelRegex = "\\p{Alnum}(?>[\\p{Alnum}-]*\\p{Alnum})*"
    private val TopLabelRegex    = "\\p{Alpha}{2,}"
    private val DomainNameRegex  = "^(?:" + DomainLabelRegex + "\\.)+" + "(" + TopLabelRegex + ")$"

    private val DomainRegex = new RegexValidator(DomainNameRegex)

    override def isValidDomain(domain: String): Boolean =
      Option(DomainRegex.matches(domain)) exists (_.nonEmpty)
  }

  def isEmptyValue(value: String): Boolean = "" == value

  def iterateBinds(topLevelBinds: List[RuntimeBind], fn: BindNode => Unit): Unit =
    for (currentBind <- topLevelBinds)
      try currentBind.applyBinds(fn)
      catch {
        case NonFatal(t) =>
          throw OrbeonLocationException.wrapException(
            t,
            XmlExtendedLocationData(
              currentBind.staticBind.locationData,
              "evaluating XForms binds".some,
              element = currentBind.staticBind.element.some
            )
          )
      }

  def evaluateBooleanExpression(
    model    : XFormsModel,
    bindNode : BindNode,
    xpathMIP : StaticXPathMIP)(implicit
    reporter : Reporter
  ): Boolean =
    XPath.evaluateSingle(
      contextItems        = bindNode.parentBind.items,
      contextPosition     = bindNode.position,
      compiledExpression  = xpathMIP.compiledExpression,
      functionContext     = model.getContextStack.getFunctionContext(model.getEffectiveId, Some(bindNode)),
      variableResolver    = model.variableResolver
    ).asInstanceOf[Boolean]

  def evaluateStringExpression(
    model    : XFormsModel,
    bindNode : BindNode,
    xpathMIP : StaticXPathMIP)(implicit
    reporter : Reporter
  ): String =
    XPath.evaluateAsString(
      contextItems       = bindNode.parentBind.items,
      contextPosition    = bindNode.position,
      compiledExpression = xpathMIP.compiledExpression,
      functionContext    = model.getContextStack.getFunctionContext(model.getEffectiveId, Some(bindNode)),
      variableResolver   = model.variableResolver
    )

  def handleMIPXPathException(
    throwable     : Throwable,
    bindNode      : BindNode,
    xpathMIP      : StaticXPathMIP,
    contextMessage: String,
    collector     : ErrorEventCollector)(implicit
    logger    : IndentedLogger
  ): Unit = {
    XFormsCrossPlatformSupport.getRootThrowable(throwable) match {
      case e: TypedNodeWrapper.TypedValueException =>
        // Consider validation errors as ignorable. The rationale is that if the function (the XPath
        // expression) works on inputs that are not valid (hence the validation error), then the function cannot
        // produce a meaningful result. We think that it is worth handling this condition slightly differently
        // from other dynamic and static errors, so that users can just write expression without constant checks
        // with `castable as` or `instance of`.
        debug("typed value exception", List(
          "node name"     -> e.nodeName,
          "expected type" -> e.typeName,
          "actual value"  -> e.nodeValue
        ))
      case t =>
        // All other errors dispatch an event and will cause the usual fatal-or-not behavior
        val ve = OrbeonLocationException.wrapException(t,
          XmlExtendedLocationData(
            locationData = bindNode.locationData,
            description  = Option(contextMessage),
            params       = List("expression" -> xpathMIP.compiledExpression.string),
            element      = Some(bindNode.staticBind.element)
          )
        )

        collector(
          new XXFormsXPathErrorEvent(
            target         = bindNode.parentBind.model,
            expression     = xpathMIP.compiledExpression.string,
            details        = XPathErrorDetails.ForBindMip(bindNode.staticBind.nameOpt, xpathMIP.name),
            message        = XFormsCrossPlatformSupport.getRootThrowable(t).getMessage,
            throwable      = ve
          )
        )
    }
  }
}
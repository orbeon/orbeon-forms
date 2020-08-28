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

import org.orbeon.oxf.common.{OrbeonLocationException, ValidationException}
import org.orbeon.oxf.util.{Logging, XPath}
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.oxf.xforms.analysis.model.Model.{Constraint, Required, Type}
import org.orbeon.oxf.xforms.analysis.model.ValidationLevel.ErrorLevel
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.model.XFormsModelBinds._
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.oxf.xml.{XMLConstants, XMLParsing}
import org.orbeon.saxon.`type`.{BuiltInAtomicType, BuiltInType, ValidationFailure}
import org.orbeon.saxon.expr.XPathContextMajor
import org.orbeon.saxon.om.{NodeInfo, StandardNames}
import org.orbeon.saxon.sxpath.{IndependentContext, XPathEvaluator}
import org.orbeon.saxon.value.StringValue
import org.orbeon.scaxon.NodeConversions.unsafeUnwrapElement
import org.orbeon.xforms.XFormsNames
import org.orbeon.xml.NamespaceMapping
import org.w3c.dom.Node

import scala.collection.{mutable => m}
import scala.util.control.NonFatal


trait ValidationBindOps extends Logging {

  self: XFormsModelBinds =>

  import Private._

  def applyValidationBinds(invalidInstances: m.Set[String], collector: XFormsEvent => Unit): Unit = {
    if (! staticModel.mustRevalidate) {
      debug("skipping bind revalidate", List("model id" -> model.getEffectiveId, "reason" -> "no validation binds"))
    } else {

      // Reset context stack just to re-evaluate the variables
      model.resetAndEvaluateVariables()

      // 1. Validate based on type and requiredness
      if (staticModel.hasTypeBind || staticModel.hasRequiredBind)
        iterateBinds(topLevelBinds, bindNode =>
          if (bindNode.staticBind.dataType.isDefined || bindNode.staticBind.hasXPathMIP(Required))
            validateTypeAndRequired(bindNode, invalidInstances)
        )

      // 2. Validate constraints
      if (staticModel.hasConstraintBind)
        iterateBinds(topLevelBinds, bindNode =>
          if (bindNode.staticBind.constraintsByLevel.nonEmpty)
            validateConstraint(bindNode, invalidInstances, collector)
        )
    }
  }

  protected def failedConstraintMIPs(
    mips      : List[StaticXPathMIP],
    bindNode  : BindNode,
    collector : XFormsEvent => Unit
  ): List[StaticXPathMIP] =
    for {
      mip       <- mips
      succeeded = evaluateBooleanExpressionStoreProperties(bindNode, mip, collector)
      if ! succeeded
    } yield
      mip

  private object Private {

    lazy val xformsValidator = {
      val validator = new XFormsModelSchemaValidator("oxf:/org/orbeon/oxf/xforms/xforms-types.xsd")
      validator.loadSchemas(containingDocument)
      validator
    }

    def validateTypeAndRequired(bindNode: BindNode, invalidInstances: m.Set[String]): Unit = {

      val staticBind = bindNode.staticBind

      assert(staticBind.typeMIPOpt.isDefined || staticBind.hasXPathMIP(Required))

      // Don't try to apply validity to a node if it has children nodes or if it's not a node
      // "The type model item property is not applied to instance nodes that contain child elements"
      val currentNodeInfo = bindNode.node
      if ((currentNodeInfo eq null) || bindNode.hasChildrenElements)
        return

      // NOTE: 2011-02-03: Decided to also apply this to required validation.
      // See: http://forge.ow2.org/tracker/index.php?func=detail&aid=315821&group_id=168&atid=350207

      // Current required value (computed during previous recalculate)
      val isRequired = InstanceData.getRequired(currentNodeInfo)

      val requiredMIPOpt = staticBind.firstXPathMIP(Required)

      // 1. Check type validity

      // Type MIP `@type` attribute is special:
      //
      // - it is not an XPath expression
      // - but because type validation can be expensive, we want to optimize that if we can
      // - so `requireModelMIPUpdate(Model.TYPE)` actually means "do we need to update type validity"
      //
      // `xxf:XML` and `xxf:XPath2` also depend on requiredness, which is probably not a good idea. To handle
      // this condition (partially), if the same bind has `@type` and `@required`, we also reevaluate type validity if
      // requiredness has changed. Ideally:
      //
      // - we would not depend on requiredness
      // - but if we did, we should handle also the case where another bind is setting requiredness on the node
      //
      val typeValidity =
        staticBind.dataType match {
          case Some(_) =>
            if (dependencies.requireModelMIPUpdate(model, staticBind, Type, null) ||
              requiredMIPOpt.isDefined && dependencies.requireModelMIPUpdate(model, staticBind, Required, null)) {
              // Compute new type validity if the value of the node might have changed OR the value of requiredness
              // might have changed
              val typeValidity = validateType(bindNode.parentBind, currentNodeInfo, isRequired)
              bindNode.setTypeValid(typeValidity, staticBind.typeMIPOpt.get)
              typeValidity
            } else {
              // Keep current value
              bindNode.typeValid
            }
          case None =>
            // Keep current value (defaults to true when no type attribute)
            bindNode.typeValid
        }

      // 2. Check required validity
      // We compute required validity every time
      val requiredValidity =
        ! isRequired || ! isEmptyValue(DataModel.getValue(currentNodeInfo))

      bindNode.setRequiredValid(requiredValidity, requiredMIPOpt)

      // Remember invalid instances
      if (! typeValidity || ! requiredValidity) {
        containingDocument.instanceForNodeOpt(currentNodeInfo) foreach
          (invalidInstances += _.getEffectiveId)
      }
    }

    def validateType(bind: RuntimeBind, currentNodeInfo: NodeInfo, required: Boolean): Boolean = {

      val staticBind = bind.staticBind

      // NOTE: xf:bind/@type is a literal type value, and it is the same that applies to all nodes pointed to by xf:bind/@ref
      val typeQName = staticBind.dataType.get

      val typeNamespaceURI = typeQName.namespace.uri
      val typeLocalname    = typeQName.localName

      // Get value to validate if not already computed above

      val nodeValue = DataModel.getValue(currentNodeInfo)

      // TODO: "[...] these datatypes can be used in the type model item property without the addition of the
      // XForms namespace qualifier if the namespace context has the XForms namespace as the default
      // namespace."
      val isBuiltInSchemaType  = XMLConstants.XSD_URI == typeNamespaceURI
      val isBuiltInXFormsType  = XFormsNames.XFORMS_NAMESPACE_URI == typeNamespaceURI
      val isBuiltInXXFormsType = XFormsNames.XXFORMS_NAMESPACE_URI == typeNamespaceURI

      // TODO: Check what XForms event must be dispatched.
      def throwError() =
        throw new ValidationException(s"Invalid schema type `${typeQName.qualifiedName}`", staticBind.locationData)

      if (isBuiltInXFormsType && nodeValue.isEmpty) {
        // Don't consider the node invalid if the string is empty with `xf:*` types
        true
      } else if (isBuiltInXFormsType && typeLocalname == "email") {
        EmailValidatorNoDomainValidation.isValid(nodeValue)
      } else if (isBuiltInXFormsType && (typeLocalname == "HTMLFragment")) {
        // Just a marker type
        true
      } else if (isBuiltInXFormsType && Model.XFormsSchemaTypeNames(typeLocalname)) {
        // `xf:dayTimeDuration`, `xf:yearMonthDuration`, `xf:email`, `xf:card-number`
        val validationError = xformsValidator.validateDatatype(
          nodeValue,
          typeNamespaceURI,
          typeLocalname,
          typeQName.qualifiedName,
          staticBind.locationData
        )
        validationError eq null
      } else if (isBuiltInSchemaType || isBuiltInXFormsType) {
        // Built-in schema or XForms type

        // Use XML Schema namespace URI as Saxon doesn't know anything about XForms types
        val newTypeNamespaceURI = XMLConstants.XSD_URI

        // Get type information
        val requiredTypeFingerprint = StandardNames.getFingerprint(newTypeNamespaceURI, typeLocalname)
        if (requiredTypeFingerprint == -1)
          throwError()

        // Need an evaluator to check and convert type below
        val xpathEvaluator =
          try {
            val evaluator = new XPathEvaluator(XPath.GlobalConfiguration)

            // NOTE: Not sure declaring namespaces here is necessary just to perform the cast
            val context = evaluator.getStaticContext.asInstanceOf[IndependentContext]
            for ((prefix, uri) <- staticBind.namespaceMapping.mapping)
              context.declareNamespace(prefix, uri)

            evaluator
          } catch {
            case NonFatal(t) =>
              throw OrbeonLocationException.wrapException(t, staticBind.locationData)
              // TODO: Check what XForms event must be dispatched.
          }

        // Try to perform casting
        // TODO: Should we actually perform casting? This for example removes leading and trailing space around tokens.
        // Is that expected?
        val stringValue = new StringValue(nodeValue)
        stringValue.convertPrimitive(
          BuiltInType.getSchemaType(requiredTypeFingerprint).asInstanceOf[BuiltInAtomicType],
          true,
          new XPathContextMajor(stringValue, xpathEvaluator.getExecutable)
        ) match {
          case _: ValidationFailure => false
          case _                    => true
        }
      } else if (isBuiltInXXFormsType) {
        // Built-in extension types

        val isOptionalAndEmpty = ! required && nodeValue == ""
        if (ValidationBindOps.SupportedXmlTypeNames(typeLocalname)) {
          isOptionalAndEmpty || XMLParsing.isWellFormedXML(nodeValue)
        } else if (ValidationBindOps.SupportedXPath2TypeNames(typeLocalname)) {

          // Find element which scopes namespaces
          val namespaceNodeInfo =
            if (currentNodeInfo.getNodeKind == Node.ELEMENT_NODE)
              currentNodeInfo
            else
              currentNodeInfo.getParent

          if ((namespaceNodeInfo ne null) && namespaceNodeInfo.getNodeKind == Node.ELEMENT_NODE) {
            // ASSUMPTION: Binding to dom4j-backed node (which InstanceData assumes too)
            val namespaceElem    = unsafeUnwrapElement(namespaceNodeInfo)
            val namespaceMapping = NamespaceMapping(namespaceElem.getNamespaceContextNoDefault)
            isOptionalAndEmpty || XPath.isXPath2ExpressionOrValueTemplate(
              nodeValue,
              namespaceMapping,
              containingDocument.functionLibrary,
              typeLocalname == "XPath2ValueTemplate"
            )
          } else {
            // This means that we are bound to a node which is not an element and which does not have a
            // parent element. This could be a detached attribute, or an element node, etc. Unsure if we
            // would have made it this far anyway! We can't validate the expression so we only consider
            // the "optional-and-empty" case.
            isOptionalAndEmpty
          }
        } else {
          throwError()
        }
      } else if (model.hasSchema) {
        // Other type and there is a schema

        // There are possibly types defined in the schema
        val validationError = model.schemaValidator.validateDatatype(
          nodeValue,
          typeNamespaceURI,
          typeLocalname,
          typeQName.qualifiedName,
          staticBind.locationData
        )

        validationError eq null
      } else {
        throwError()
      }
    }

    def validateConstraint(
      bindNode         : BindNode,
      invalidInstances : m.Set[String],
      collector        : XFormsEvent => Unit
    ): Unit = {

      assert(bindNode.staticBind.constraintsByLevel.nonEmpty)

      // Don't try to apply constraints if it's not a node (it's set to null in that case)
      val currentNode = bindNode.node
      if (currentNode eq null)
        return

      // NOTE: 2011-02-03: Decided to allow setting a constraint on an element with children. Handles the case of
      // assigning validity to an enclosing element.
      // See: http://forge.ow2.org/tracker/index.php?func=detail&aid=315821&group_id=168&atid=350207

      // NOTE: 2015-05-27: We used to not run constraints if the datatype was not valid. This could cause a bug
      // when the type would switch from valid to invalid and back. In addition, we do want to run constraints so
      // that validation properties such as `max-length` are computed even when the datatype is not valid. So now we
      // keep the list of constraints up to date even when the datatype is not valid.

      for {
        (level, mips) <- bindNode.staticBind.constraintsByLevel
      } locally {
        if (dependencies.requireModelMIPUpdate(model, bindNode.staticBind, Constraint, level)) {
          // Re-evaluate and set
          val failedConstraints = failedConstraintMIPs(mips, bindNode, collector)
          if (failedConstraints.nonEmpty)
            bindNode.failedConstraints += level -> failedConstraints
          else
            bindNode.failedConstraints -= level
        } else {
          // Don't change list of failed constraints for this level
        }
      }

      // Remember invalid instances
      if (! bindNode.constraintsSatisfiedForLevel(ErrorLevel)) {
        containingDocument.instanceForNodeOpt(currentNode) foreach
          (invalidInstances += _.getEffectiveId)
      }
    }

    def evaluateBooleanExpressionStoreProperties(
      bindNode  : BindNode,
      xpathMIP  : StaticXPathMIP,
      collector : XFormsEvent => Unit
    ): Boolean =
      try {
        // LATER: If we implement support for allowing binds to receive events, source must be bind id.
        val functionContext =
          model.getContextStack.getFunctionContext(model.getEffectiveId, Some(bindNode))

        val result =
          XPath.evaluateSingle(
            contextItems       = bindNode.parentBind.items,
            contextPosition    = bindNode.position,
            compiledExpression = xpathMIP.compiledExpression,
            functionContext    = functionContext,
            variableResolver   = model.variableResolver
          ).asInstanceOf[Boolean]

        functionContext.properties foreach { propertiesMap =>
          propertiesMap foreach {
            case (name, Some(s)) => bindNode.setCustom(name, s)
            case (name, None)    => bindNode.clearCustom(name)
          }
        }

        result
      } catch {
        case NonFatal(t) =>
          handleMIPXPathException(t, bindNode, xpathMIP, "evaluating XForms constraint bind", collector)
          ! Model.DEFAULT_VALID
      }
  }
}

object ValidationBindOps {

  val SupportedXmlTypeNames: Set[String] = Set(
    "xml",
    "XML"
  )

  val SupportedXPath2TypeNames: Set[String] = Set(
    "xpath2",
    "XPath2",
    "XPath2ValueTemplate"
  )

}
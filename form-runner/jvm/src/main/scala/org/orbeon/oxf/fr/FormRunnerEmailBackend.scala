package org.orbeon.oxf.fr

import cats.implicits.catsSyntaxOptionId
import org.orbeon.dom.{Element, Text}
import org.orbeon.oxf.fr.FormRunnerCommon.{ControlBindPathHoldersResources, frc}
import org.orbeon.oxf.fr.email.EmailMetadata.TemplateValue
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xml.NamespaceMapping

import scala.jdk.CollectionConverters.*


// Only used for emails and not needed offline.
trait FormRunnerEmailBackend {

  // Given a form body and instance data:
  //
  // - find all controls with the given conjunction of class names
  // - for each control, find the associated bind
  // - return all data holders in the instance data to which the bind would apply
  //
  // The use case is, for example, to find all data holders pointed to by controls with the class
  // `fr-email-recipient` and, optionally, `fr-email-attachment`.
  //
  def searchHoldersForClassTopLevelOnly(
    body      : NodeInfo,
    data      : NodeInfo,
    classNames: String
  ): List[NodeInfo] =
    frc.searchControlsTopLevelOnly(
      data      = Option(data),
      predicate = frc.hasAllClassesPredicate(classNames.splitTo[List]())
    )(
      new InDocFormRunnerDocContext(body)
    ).toList.flatMap {
      case ControlBindPathHoldersResources(_, _, _, Some(holders), _) => holders
      case ControlBindPathHoldersResources(_, _, _, None, _)          => Nil
    }

  // Given a form head, form body and instance data:
  //
  // - find all section templates in use
  // - for each section
  //   - determine the associated data holder in instance data
  //   - find the inline binding associated with the section template
  //   - find all controls with the given conjunction of class names in the section template
  //   - for each control, find the associated bind in the section template
  //   - return all data holders in the instance data to which the bind would apply
  //
  // The use case is, for example, to find all data holders pointed to by controls with the class
  // `fr-email-recipient` and, optionally, `fr-email-attachment`, which appear within section templates.
  //
  def searchHoldersForClassUseSectionTemplates(
    head      : NodeInfo,
    body      : NodeInfo,
    data      : NodeInfo,
    classNames: String
  ): List[NodeInfo] =
    frc.searchControlsUnderSectionTemplates(
      head             = head,
      data             = Option(data),
      sectionPredicate = _ => true,
      controlPredicate = frc.hasAllClassesPredicate(classNames.splitTo[List]())
    )(
      new InDocFormRunnerDocContext(body)
    ).toList.flatMap {
      case ControlBindPathHoldersResources(_, _, _, Some(holders), _) => holders
      case ControlBindPathHoldersResources(_, _, _, None, _)          => Nil
    }

  def evaluatedTemplateValue(
    templateValue: TemplateValue)(implicit
    ctx          : InDocFormRunnerDocContext
  ): List[String] =
    templateValue match {
      case TemplateValue.Control(controlName, sectionOpt) => controlValueAsStrings(controlName, sectionOpt)
      case TemplateValue.Expression(expression)           => evaluatedExpressionAsStrings(expression)
      case TemplateValue.Text(text)                       => List(text)
    }

  def controlValueAsStrings(
    controlName: String,
    ctx        : InDocFormRunnerDocContext
    sectionOpt : Option[String]
  )(implicit
  ): List[String] =
    controlValueAsNodeInfos(controlName, sectionOpt).map(_.getStringValue)

  def controlValueAsNodeInfos(
    controlName: String,
    sectionOpt : Option[String]
  )(implicit
    ctx        : FormRunnerDocContext
  ): List[NodeInfo] =
    sectionOpt match {
      case None =>
        // Control not in section template
        frc.searchControlsTopLevelOnly(
          data      = frc.formInstance.root.some,
          predicate = FormRunnerCommon.frc.getControlName(_) == controlName
        ).toList.flatMap(_.holders).flatten

      case Some(section) =>
        // Control in section template
        frc.searchControlsUnderSectionTemplates(
          head             = ctx.formDefinitionRootElem.child("*:head").head,
          data             = frc.formInstance.root.some,
          controlPredicate = FormRunnerCommon.frc.getControlName(_) == controlName,
          sectionPredicate = frc.getControlNameOpt(_).contains(section)
        ).toList.flatMap(_.holders).flatten
    }

  private def expressionWithProcessedVarReferences(
    expression: String)(implicit
    ctx       : InDocFormRunnerDocContext
  ): String = {

    FormRunner.replaceVarReferencesWithFunctionCallsFromString(
      elemOrAtt   = ctx.modelElem,
      xpathString = expression,
      avt         = false,
      libraryName = "",
      norewrite   = Array()
    )
  }

  private def evaluatedExpression(
    expression: String)(implicit
    ctx       : InDocFormRunnerDocContext
  ): List[Any] = {

    // TODO: check if another namespace is defined for "frf" and prevent namespace collision (renaming, etc.)
    val namespaceMapping = NamespaceMapping(
      ctx.modelElem.namespaceMappings.toMap +
      ("frf" -> "java:org.orbeon.oxf.fr.FormRunner")
    )

    val doc = inScopeContainingDocument

    val functionContext = XFormsFunction.Context(
      container         = doc,
      bindingContext    = frc.formModelOpt.get.getDefaultEvaluationContext,
      sourceEffectiveId = doc.effectiveId,
      modelOpt          = frc.formModelOpt,
      bindNodeOpt       = None
    )

    XPathCache.evaluate(
      contextItem        = frc.formInstance.rootElement,
      xpathString        = expression,
      namespaceMapping   = namespaceMapping,
      variableToValueMap = null,
      functionLibrary    = inScopeContainingDocument.functionLibrary,
      functionContext    = functionContext,
      baseURI            = null,
      locationData       = null,
      reporter           = null
    ).asScala.toList
  }

  def evaluatedExpressionAsBoolean(
    expression: String)(implicit
    ctx       : InDocFormRunnerDocContext
  ): Boolean =
    evaluatedExpression(expressionWithProcessedVarReferences(expression)).flatMap {
      case b: java.lang.Boolean => Some(b)
      case                    _ => None // TODO: should we try and convert string values ("false"/"true") to boolean?
    }.headOption.getOrElse {
      throw new IllegalArgumentException(s"Expression '$expression' did not evaluate to a boolean")
    }

  def evaluatedExpressionAsStrings(
    expression: String)(implicit
    ctx       : InDocFormRunnerDocContext
  ): List[String] =
    evaluatedExpression(expressionWithProcessedVarReferences(expression)).map {
      case string: String     => string
      case element: Element   => element.getStringValue
      case text: Text         => text.getStringValue
      case nodeInfo: NodeInfo => nodeInfo.stringValue
      case any: Any           => any.toString // TODO: should we throw instead?
    }
}

package org.orbeon.oxf.fr

import org.orbeon.dom.{Element, Text}
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.email.EmailMetadata.HeaderName.Custom
import org.orbeon.oxf.fr.email.EmailMetadata.TemplateValue
import org.orbeon.oxf.fr.email.EmailMetadataParsing
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.saxon.value.{BooleanValue, StringValue}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath.NodeInfoOps
import org.orbeon.xml.NamespaceMapping

import scala.jdk.CollectionConverters.asScalaBufferConverter


trait FormRunnerEmailBackend {

  // Only used from `email-form.xsl` and not needed offline.
  //@XPathFunction
  def emailAttachmentFilename(
    data           : NodeInfo,
    attachmentType : String,
    app            : String,
    form           : String
  ): Option[String] = {

    // NOTE: We don't use `FormRunnerParams()` for that this works in tests.
    // Callees only require, as of 2018-05-31, `app` and `form`.
    implicit val params = FormRunnerParams(AppForm(app, form), "email")

    for {
      (expr, mapping) <- formRunnerPropertyWithNs(s"oxf.fr.email.$attachmentType.filename")
      trimmedExpr     <- expr.trimAllToOpt
      name            = process.SimpleProcess.evaluateString(trimmedExpr, data, mapping)
    } yield {
      // This appears necessary for non-ASCII characters to make it through.
      // Verified that this works with GMail.
      javax.mail.internet.MimeUtility.encodeText(name, CharsetNames.Utf8, null)
    }
  }


  //@XPathFunction
  def customHeaderNames(
    formDefinition      : NodeInfo,
    emailTemplateElemOpt: Option[NodeInfo]
  ): SequenceIterator = {

    val emailTemplateOpt = emailTemplateElemOpt.map(EmailMetadataParsing.parseCurrentTemplate(_, formDefinition))
    emailTemplateOpt.toSeq.flatMap(_.headers).collect { case (Custom(headerName), _) => headerName }
  }

  // Returns the values of email headers, e.g. "to" or "bcc". The source of the values is defined in the form metadata,
  // part of the form definition. At the moment, the following sources are supported: form controls, expressions, and
  // static text.

  //@XPathFunction
  def headerValues(
    formDefinition      : NodeInfo,
    emailTemplateElemOpt: Option[NodeInfo],
    formData            : NodeInfo,
    headerName          : String
  ): SequenceIterator = {
    val emailTemplateOpt = emailTemplateElemOpt.map(EmailMetadataParsing.parseCurrentTemplate(_, formDefinition))
    val templateValues   = emailTemplateOpt.toList.flatMap(_.headers.filter(_._1.entryName == headerName).map(_._2))

    values(formDefinition, formData, templateValues)
  }

  //@XPathFunction
  def attachments(
    formDefinition      : NodeInfo,
    emailTemplateElemOpt: Option[NodeInfo],
    formData            : NodeInfo
  ): SequenceIterator = {
    val emailTemplateOpt = emailTemplateElemOpt.map(EmailMetadataParsing.parseCurrentTemplate(_, formDefinition))
    val templateValues   = emailTemplateOpt.toList.flatMap(_.attachControls)

    values(formDefinition, formData, templateValues)
  }

  private def values(
    formDefinition      : NodeInfo,
    formData            : NodeInfo,
    templateValues      : List[TemplateValue]
  ): SequenceIterator = {

    val formDefinitionCtx  = new InDocFormRunnerDocContext(formDefinition)

    templateValues.flatMap {
      // Control not in section template
      case TemplateValue.Control(controlName, None) =>
        frc.searchControlsTopLevelOnly(
          data      = Some(formData),
          predicate = FormRunnerCommon.frc.getControlName(_) == controlName)(
          ctx       = formDefinitionCtx
        ).flatMap(_.holders).flatten

      // Control in section template
      case TemplateValue.Control(controlName, Some(sectionTemplate)) =>
        frc.searchControlsUnderSectionTemplates(
          head             = formDefinition.rootElement.child("*:head").head,
          data             = Some(formData),
          controlPredicate = FormRunnerCommon.frc.getControlName(_) == controlName,
          sectionPredicate = frc.getControlNameOpt(_).contains(sectionTemplate))(
          ctx              = formDefinitionCtx
        ).flatMap(_.holders).flatten

      case TemplateValue.Expression(expression) =>

        val expressionWithProcessedVarReferences =
          FormRunner.replaceVarReferencesWithFunctionCalls(
            elemOrAtt   = formDefinitionCtx.modelElem,
            xpathString = expression,
            avt         = false,
            libraryName = "",
            norewrite   = Array()
          )

        evaluatedExpressionAsStrings(formDefinition, expressionWithProcessedVarReferences)

      case TemplateValue.Text(text) =>
        List(StringValue.makeStringValue(text))
    }
  }

  def evaluatedExpression(formDefinition: NodeInfo, expression: String): List[Any] = {
    val ctx: FormRunnerDocContext = new InDocFormRunnerDocContext(formDefinition)

    // TODO: check if another namespace is defined for "frf" and prevent namespace collision (renaming, etc.)
    val namespaceMapping = NamespaceMapping(
      ctx.modelElem.namespaceMappings.toMap +
      ("frf" -> "java:org.orbeon.oxf.fr.FormRunner")
    )

    val doc = inScopeContainingDocument

    val functionContext = XFormsFunction.Context(
      container         = doc,
      bindingContext    = doc.getDefaultModel.getDefaultEvaluationContext,
      sourceEffectiveId = doc.effectiveId,
      modelOpt          = doc.findDefaultModel,
      bindNodeOpt       = None
    )

    XPathCache.evaluate(
      contextItem        = ctx.modelElem,
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

  //@XPathFunction
  def evaluatedExpressionAsBoolean(formDefinition: NodeInfo, expression: String): BooleanValue =
    evaluatedExpression(formDefinition, expression).flatMap {
      case b: java.lang.Boolean => Some(BooleanValue.get(b))
      case                    _ => None // TODO: should we try and convert string values ("false"/"true") to boolean?
    }.headOption.getOrElse {
      throw new IllegalArgumentException(s"Expression '$expression' did not evaluate to a boolean")
    }

  //@XPathFunction
  def evaluatedExpressionAsStrings(formDefinition: NodeInfo, expression: String): List[StringValue] =
    evaluatedExpression(formDefinition, expression).map {
      case string: String     => string
      case element: Element   => element.getStringValue
      case text: Text         => text.getStringValue
      case nodeInfo: NodeInfo => nodeInfo.stringValue
      case any: Any           => any.toString // TODO: should we throw instead?
    }.map {
      StringValue.makeStringValue
    }
}

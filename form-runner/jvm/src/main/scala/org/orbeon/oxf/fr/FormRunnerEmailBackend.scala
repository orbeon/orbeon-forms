package org.orbeon.oxf.fr

import cats.implicits.catsSyntaxOptionId
import org.orbeon.dom.{Element, Text}
import org.orbeon.io.CharsetNames
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.email.EmailMetadata.HeaderName.Custom
import org.orbeon.oxf.fr.email.EmailMetadata.{TemplateMatch, TemplateValue}
import org.orbeon.oxf.fr.email.{Attachment, EmailContent, EmailMetadataParsing}
import org.orbeon.oxf.fr.persistence.S3
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.process.RenderedFormat
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupportTrait, IndentedLogger, TryUtils, XPathCache}
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.saxon.value.{BooleanValue, StringValue}
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xml.NamespaceMapping
import software.amazon.awssdk.services.s3.model.PutObjectResponse

import java.net.URI
import scala.jdk.CollectionConverters.*
import scala.util.Try


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
      jakarta.mail.internet.MimeUtility.encodeText(name, CharsetNames.Utf8, null)
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
    val templateValues   = emailTemplateOpt.toList.flatMap(_.controlsToAttach)

    values(formDefinition, formData, templateValues)
  }

  def values(
    formDefinition      : NodeInfo,
    formData            : NodeInfo,
    templateValues      : List[TemplateValue]
  ): SequenceIterator = {

    val formDefinitionCtx = new InDocFormRunnerDocContext(formDefinition)

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
        evaluatedExpressionAsStrings(formDefinition, expressionWithProcessedVarReferences(formDefinition, expression))

      case TemplateValue.Text(text) =>
        List(StringValue.makeStringValue(text))
    }
  }

  private def expressionWithProcessedVarReferences(formDefinition: NodeInfo, expression: String): String = {
    val formDefinitionCtx = new InDocFormRunnerDocContext(formDefinition)

    FormRunner.replaceVarReferencesWithFunctionCallsFromString(
      elemOrAtt   = formDefinitionCtx.modelElem,
      xpathString = expression,
      avt         = false,
      libraryName = "",
      norewrite   = Array()
    )
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

  def emailContents(
    urisByRenderedFormat    : Map[RenderedFormat, URI],
    emailDataFormatVersion  : DataFormatVersion,
    templateMatch           : TemplateMatch,
    language                : String,
    templateNameOpt         : Option[String]
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    formRunnerParams        : FormRunnerParams
  ): Try[List[EmailContent]] = {

    // Form definition
    PersistenceApi.readPublishedFormDefinition(
      appName  = formRunnerParams.app,
      formName = formRunnerParams.form,
      version  = FormDefinitionVersion.Specific(formRunnerParams.formVersion)
    ) map { case ((_, formDefinition), _) =>

      // Parse email metadata
      val emailMetadataNodeOpt = frc.metadataInstanceRootOpt(formDefinition).flatMap(metadata => (metadata / "email").headOption)
      val emailMetadata        = parseEmailMetadata(emailMetadataNodeOpt, formDefinition)

      // 1) Filter templates by language and name
      val templatesFilteredByLanguageAndName = emailMetadata.templates.filter { template =>
        (template.lang.isEmpty || template.lang.contains(language)) && templateNameOpt.forall(template.name == _)
      }

      // 2) Consider first template or all templates
      val firstOrAllTemplates = templateMatch match {
        case TemplateMatch.First => templatesFilteredByLanguageAndName.take(1)
        case TemplateMatch.All   => templatesFilteredByLanguageAndName
      }

      // 3) Consider only templates with enableIfTrue expression, if any, evaluating to true
      val enabledTemplates = firstOrAllTemplates.filter { template =>
        template.enableIfTrue.forall { expression =>
          evaluatedExpressionAsBoolean(formDefinition, expressionWithProcessedVarReferences(formDefinition, expression)).getBooleanValue
        }
      }

      // TODO: check if steps 2) and 3) should be reversed (as of 2025-02-20, the XPL/XSL implementation does them in this order)

      // For each email template, generate email content
      enabledTemplates.map { template =>
        EmailContent(
          formDefinition         = formDefinition.rootElement,
          formData               = frc.formInstance.root,
          emailDataFormatVersion = emailDataFormatVersion,
          urisByRenderedFormat   = urisByRenderedFormat,
          template               = template,
          parameters             = emailMetadata.params
        )
      }
    }
  }

  def storeEmailContentToS3(
    emailContent            : EmailContent,
    s3PathPrefix            : String
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    formRunnerParams        : FormRunnerParams,
    s3Config                : S3.Config
  ): Try[Unit] = {
    // TODO: store email body and headers as well
    TryUtils.sequenceLazily(emailContent.allAttachments)(storeAttachmentToS3(_, s3PathPrefix)).map(_ => ())
  }

  private def storeAttachmentToS3(
    attachment              : Attachment,
    s3PathPrefix            : String
  )(implicit
    logger                  : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait,
    formRunnerParams        : FormRunnerParams,
    s3Config                : S3.Config
  ): Try[PutObjectResponse] = {

    val key = s3PathPrefix + attachment.filename

    attachment.data match {
      case Left(uri)        =>
        // Attachment given as URI, stream/download it to S3
        val connectionResult = PersistenceApi.connectPersistence(
          method         = HttpMethod.GET,
          pathQuery      = uri.toString,
          formVersionOpt = Left(FormDefinitionVersion.Specific(formRunnerParams.formVersion)).some
        )

        S3.write(key, connectionResult.content.stream, connectionResult.content.contentLength)
      case Right(byteArray) =>
        // Attachment given as byte array, store it to S3 directly
        S3.write(key, byteArray)
    }
  }
}

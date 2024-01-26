package org.orbeon.oxf.fr.email

import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.XMLNames
import org.orbeon.oxf.fr.email.EmailMetadata.{HeaderName, Legacy, TemplateValue}
import org.orbeon.oxf.util.ContentTypes
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsNames

import scala.util.Try

/**
 * Parsing XML into either the 2021 legacy format, or the current format
 */
object EmailMetadataParsing {

  def parseCurrentMetadata(emailMetadata: Option[NodeInfo], formDefinition: NodeInfo): EmailMetadata.Metadata =
    EmailMetadata.Metadata(
      templates = emailMetadata.toList.flatMap(_.child("templates").child("template").toList).map(parseCurrentTemplate(_, formDefinition)),
      params    = emailMetadata.toList.flatMap(_.child("parameters").child("param").toList).map(parseParam)
    )

  def parseLegacy2021Metadata(emailMetadata: Option[NodeInfo], formDefinition: NodeInfo): Legacy.Metadata2021 =
    Legacy.Metadata2021(
      subject    = emailMetadata.flatMap(_.child("subject").headOption).map(parseLegacy2021Part),
      body       = emailMetadata.flatMap(_.child("body"   ).headOption).map(parseLegacy2021Part),
      formFields = parseLegacy2021FormFields(formDefinition)
    )

  def parseLegacy2022Metadata(emailMetadata: Option[NodeInfo], formDefinition: NodeInfo): Legacy.Metadata2022 =
    Legacy.Metadata2022(
      templates = emailMetadata.toList.flatMap(_.child("templates").child("template").toList).map(parseTemplate2022(_, formDefinition)),
      params    = emailMetadata.toList.flatMap(_.child("parameters").child("param").toList).map(parseParam)
    )

  def parseCurrentTemplate(templateNodeInfo: NodeInfo, formDefinition: NodeInfo): EmailMetadata.Template = {
    def templateValuesInRootElement(rootElementName: String): List[(Option[HeaderName], TemplateValue)] =
      templateNodeInfo.child(rootElementName).headOption.toList.flatMap(parseCurrentTemplateValues(_, rootElementName, formDefinition))

    def withHeaderNamesOnly(values: List[(Option[HeaderName], TemplateValue)]): List[(HeaderName, TemplateValue)] =
      values.collect { case (Some(headerName), templateValue) => headerName -> templateValue }

    def controlsOnly(values: List[(Option[HeaderName], TemplateValue)]): List[TemplateValue.Control] =
      values.collect { case (_, templateValue: TemplateValue.Control) => templateValue }

    EmailMetadata.Template(
      name                        = templateNodeInfo.attValue("name"),
      lang                        = templateNodeInfo.attValueOpt(XMLConstants.XML_LANG_QNAME),
      headers                     = withHeaderNamesOnly(templateValuesInRootElement("headers")),
      subject                     = templateNodeInfo.child("subject").headOption.map(parseCurrentPart),
      body                        = templateNodeInfo.child("body"   ).headOption.map(parseCurrentPart),
      attachPdf                   = templateNodeInfo.child("attach").att("pdf"  ).headOption.map(_.stringValue == "true"),
      attachXml                   = templateNodeInfo.child("attach").att("xml"  ).headOption.map(_.stringValue == "true"),
      attachFiles                 = templateNodeInfo.child("attach").att("files").headOption.map(_.stringValue),
      attachControls              = controlsOnly(templateValuesInRootElement("attach")),
      excludeFromAllControlValues = controlsOnly(templateValuesInRootElement("exclude-from-all-control-values"))
    )
  }

  private def parseCurrentPart(partNodeInfo: NodeInfo): EmailMetadata.Part =
    EmailMetadata.Part(
      isHTML = partNodeInfo.attValueOpt(XFormsNames.MEDIATYPE_QNAME).contains(ContentTypes.HtmlContentType),
      text   = partNodeInfo.stringValue
    )

  // Parse template/headers/header, template/attach/control, or template/exclude-from-all-control-values/control
  private def parseCurrentTemplateValues(
     nodeInfo       : NodeInfo,
     rootElementName: String,
     formDefinition : NodeInfo
  ): List[(Option[HeaderName], TemplateValue)] = {

    def parseTemplateValues(
      nodeInfo         : NodeInfo,
      nodeInfoToSection: NodeInfo => Option[String]
    ): List[(Option[HeaderName], TemplateValue)] =
      nodeInfo.child("header" or "control").toList.map { childNodeInfo =>

        val headerNameOpt = (childNodeInfo.localname == "header").option {
          val headerNameString = childNodeInfo.attValue("name")
          Try(HeaderName.withName(headerNameString)).getOrElse(HeaderName.Custom(headerNameString))
        }

        val templateValue = childNodeInfo.attValueOpt("type") match {
          case Some("control-value") | None => TemplateValue.Control   (childNodeInfo.stringValue, nodeInfoToSection(childNodeInfo))
          case Some("expression")           => TemplateValue.Expression(childNodeInfo.stringValue)
          case Some("text")                 => TemplateValue.Text      (childNodeInfo.stringValue)
          case Some(unexpectedType)         => throw new IllegalArgumentException(s"Unexpected header type: $unexpectedType")
        }

        headerNameOpt -> templateValue
      }

    val templateValuesFromMetadata =
      parseTemplateValues(nodeInfo, _.attValueOpt("section-template"))

    val templateValuesFromSectionTemplates =
      flatMapXblBindings(formDefinition, (xblBindingNodeInfo, frSectionNames) => {
        val metadataXfInstanceOpt     = frc.findXblInstance(xblBindingNodeInfo, "fr-form-metadata")
        val templateValuesNodeInfoOpt = metadataXfInstanceOpt.flatMap {
          _.child("metadata").child("email").child("templates").child("template").child(rootElementName).headOption
        }

        templateValuesNodeInfoOpt.toList.flatMap(templateValuesNodeInfo =>
          frSectionNames.flatMap { frSectionName =>
            parseTemplateValues(templateValuesNodeInfo, _ => Some(frSectionName))
          }
        )
      })

    templateValuesFromMetadata ++ templateValuesFromSectionTemplates
  }

  private def parseLegacy2021Part(partNodeInfo: NodeInfo): EmailMetadata.Legacy.Part2021 =
    EmailMetadata.Legacy.Part2021(
      templates = partNodeInfo.child("template").toList.map { templateNodeInfo =>
        EmailMetadata.Legacy.Template2021(
          lang   = templateNodeInfo.attValue(XMLConstants.XML_LANG_QNAME),
          isHTML = templateNodeInfo.attValueOpt(XFormsNames.MEDIATYPE_QNAME).contains(ContentTypes.HtmlContentType),
          text   = templateNodeInfo.stringValue
        )
      },
      params = partNodeInfo.child(XMLNames.FRParamTest).toList.map(parseParam)
    )

  private def parseLegacy2021FormFields(formDefinition: NodeInfo): List[Legacy.FormField] = {

    Legacy.FormFieldRole.values.toList.flatMap { formFieldRole =>

      def controlNamesForCurrentRole(
        container: NodeInfo
      ): List[String] =
        container.descendant(*)
          .filter(_.attClasses(s"fr-email-${formFieldRole.entryName}"))
          .map(_.attValue("id").substringBefore("-control"))
          .toList

      val sectionTemplateFields =
        flatMapXblBindings(formDefinition, (xblBindingElem, frSectionNames) =>
          xblBindingElem.child(XMLNames.XBLTemplateTest)
            .toList
            .flatMap(controlNamesForCurrentRole)
            .flatMap { controlName =>
              frSectionNames.map { frSectionName =>
                Legacy.FormField(
                  role        = formFieldRole,
                  sectionOpt  = Some(frSectionName),
                  controlName = controlName
                )
              }
            }
        )

      val bodyFields =
        frc.findFormRunnerBodyElem(formDefinition)
        .toList
        .flatMap(controlNamesForCurrentRole)
        .map { controlName =>
          Legacy.FormField(
            role        = formFieldRole,
            sectionOpt  = None,
            controlName = controlName
          )
        }

      bodyFields ++ sectionTemplateFields
    }
  }

  private def parseTemplate2022(templateNodeInfo: NodeInfo, formDefinition: NodeInfo): Legacy.Template2022 =
    Legacy.Template2022(
      name        = templateNodeInfo.attValue("name"),
      lang        = templateNodeInfo.attValueOpt(XMLConstants.XML_LANG_QNAME),
      subject     = templateNodeInfo.child("subject"    ).headOption.map(parseCurrentPart),
      body        = templateNodeInfo.child("body"       ).headOption.map(parseCurrentPart),
      formFields  = templateNodeInfo.child("form-fields").headOption.toList.flatMap(parseFields2022(_, formDefinition)),
      attachPdf   = templateNodeInfo.child("attach").att("pdf"  ).headOption.map(_.stringValue == "true"),
      attachFiles = templateNodeInfo.child("attach").att("files").headOption.map(_.stringValue)
    )

  private def parseFields2022(formFieldsNodeInfo: NodeInfo, formDefinition: NodeInfo): List[Legacy.FormField] = {

    def parseFormFields(
      formFieldsNodeInfo: NodeInfo,
      formFieldNodeInfoToSection: NodeInfo => Option[String]
    ): List[Legacy.FormField] =
      formFieldsNodeInfo.child(*).toList.map { formFieldNodeInfo =>
        Legacy.FormField(
          role         = Legacy.FormFieldRole.withName(formFieldNodeInfo.localname),
          sectionOpt   = formFieldNodeInfoToSection(formFieldNodeInfo),
          controlName  = formFieldNodeInfo.attValue("name")
        )
      }

    val formFieldsFromMetadata =
      parseFormFields(formFieldsNodeInfo, _.attValueOpt("section-template"))
    val formFieldsFromSectionTemplates =
      flatMapXblBindings(formDefinition, (xblBindingNodeInfo, frSectionNames) => {
        val metadataXfInstanceOpt = frc.findXblInstance(xblBindingNodeInfo, "fr-form-metadata")
        val formFieldsNodeInfoOpt = metadataXfInstanceOpt.flatMap(_.child("metadata")
          .child("email").child("templates").child("template").child("form-fields").headOption)
        formFieldsNodeInfoOpt.toList.flatMap(formFieldsNodeInfo =>
          frSectionNames.flatMap(frSectionName =>
            parseFormFields(formFieldsNodeInfo, _ => Some(frSectionName))
          )
        )
      })

    formFieldsFromMetadata ++ formFieldsFromSectionTemplates
  }

  private def parseParam(paramNodeInfo: NodeInfo): EmailMetadata.Param = {
    val name = paramNodeInfo.child(XMLNames.FRNameTest).stringValue
    paramNodeInfo.attValue("type") match {
      case "ControlValueParam"      => EmailMetadata.Param.ControlValueParam     (name, paramNodeInfo.child(XMLNames.FRControlNameTest).stringValue)
      case "ExpressionParam"        => EmailMetadata.Param.ExpressionParam       (name, paramNodeInfo.child(XMLNames.FRExprTest).stringValue)
      case "AllControlValuesParam"  => EmailMetadata.Param.AllControlValuesParam (name)
      case "LinkToEditPageParam"    => EmailMetadata.Param.LinkToEditPageParam   (name)
      case "LinkToViewPageParam"    => EmailMetadata.Param.LinkToViewPageParam   (name)
      case "LinkToNewPageParam"     => EmailMetadata.Param.LinkToNewPageParam    (name)
      case "LinkToSummaryPageParam" => EmailMetadata.Param.LinkToSummaryPageParam(name)
      case "LinkToHomePageParam"    => EmailMetadata.Param.LinkToHomePageParam   (name)
      case "LinkToFormsPageParam"   => EmailMetadata.Param.LinkToFormsPageParam  (name)
      case "LinkToAdminPageParam"   => EmailMetadata.Param.LinkToAdminPageParam  (name)
      case "LinkToPdfParam"         => EmailMetadata.Param.LinkToPdfParam        (name)
    }
  }

  private def flatMapXblBindings[FormFieldOrTemplateValue](
    formDefinition                         : NodeInfo,
    formFieldsOrTemplateValuesForXblBinding: (NodeInfo, List[String]) => List[FormFieldOrTemplateValue]
  ): List[FormFieldOrTemplateValue] =
    formDefinition
      .child(XMLNames.XHHeadTest)
      .child(XMLNames.XBLXBLTest)
      .child(XMLNames.XBLBindingTest)
      .toList
      .flatMap { xblBindingElem =>

        // Names of the sections for this section template
        val frSectionNames = {
          val sectionTemplateQName     = frc.bindingFirstURIQualifiedName(xblBindingElem)
          val sectionTemplateInstances = frc.findFormRunnerBodyElem(formDefinition).toList.flatMap(_.descendant(sectionTemplateQName))
          val frSections               = sectionTemplateInstances.map(_.getParent)

          frSections.flatMap(frc.getControlNameOpt(_))
        }

        formFieldsOrTemplateValuesForXblBinding(xblBindingElem, frSectionNames)
      }
}

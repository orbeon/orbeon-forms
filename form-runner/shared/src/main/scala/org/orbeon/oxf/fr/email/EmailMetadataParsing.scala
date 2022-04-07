package org.orbeon.oxf.fr.email

import org.orbeon.oxf.fr.XMLNames
import org.orbeon.oxf.fr.email.EmailMetadata.{FormField, FormFieldRole}
import org.orbeon.oxf.util.ContentTypes
import org.orbeon.oxf.util.StringUtils.StringOps
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsNames

/**
 * Parsing XML into either the 2021 legacy format, or the current format
 */
object EmailMetadataParsing {

  def parseCurrentMetadata(emailMetadata: NodeInfo): EmailMetadata.Metadata =
    EmailMetadata.Metadata(
      templates = emailMetadata.child("templates").child("template").toList.map(parseCurrentTemplate),
      params    = emailMetadata.child("parameters").child(XMLNames.FRParamTest).toList.map(parseParam)
    )

  def parseCurrentTemplate(templateNodeInfo: NodeInfo): EmailMetadata.Template =
    EmailMetadata.Template(
      name       = templateNodeInfo.attValue("name"),
      lang       = templateNodeInfo.attValueOpt(XMLConstants.XML_LANG_QNAME),
      subject    = parseCurrentPart  (templateNodeInfo.child("subject").head),
      body       = parseCurrentPart  (templateNodeInfo.child("body").head),
      formFields = parseCurrentFields(templateNodeInfo.child("form-fields").head)
    )

  def parseCurrentPart(partNodeInfo: NodeInfo): EmailMetadata.Part =
    EmailMetadata.Part(
      isHTML = partNodeInfo.attValueOpt(XFormsNames.MEDIATYPE_QNAME).contains(ContentTypes.HtmlContentType),
      text   = partNodeInfo.stringValue
    )

  def parseCurrentFields(formFieldsNodeInfo: NodeInfo): List[FormField] =
    formFieldsNodeInfo.child(*).toList.map { formFieldNodeInfo =>
      FormField(
        role      = FormFieldRole.withName(formFieldNodeInfo.localname),
        fieldName = formFieldNodeInfo.attValue("name")
      )
    }

  def parseLegacy2021Metadata(emailMetadata: NodeInfo, formBody: NodeInfo): EmailMetadata.Legacy2021.Metadata =
    EmailMetadata.Legacy2021.Metadata(
      subject = parseLegacy2021Part(emailMetadata.child("subject").head),
      body    = parseLegacy2021Part(emailMetadata.child("body"   ).head),
      formFields = parseLegacyFormFields(formBody)
    )

  def parseLegacy2021Part(partNodeInfo: NodeInfo): EmailMetadata.Legacy2021.Part =
    EmailMetadata.Legacy2021.Part(
      templates = partNodeInfo.child("template").toList.map { templateNodeInfo =>
        EmailMetadata.Legacy2021.Template(
          lang   = templateNodeInfo.attValue(XMLConstants.XML_LANG_QNAME),
          isHTML = templateNodeInfo.attValueOpt(XFormsNames.MEDIATYPE_QNAME).contains(ContentTypes.HtmlContentType),
          text   = templateNodeInfo.stringValue
        )
      },
      params = partNodeInfo.child(XMLNames.FRParamTest).toList.map(parseParam)
    )

  def parseLegacyFormFields(formBody: NodeInfo): List[FormField] =
    FormFieldRole.values.toList.flatMap { formFieldRole =>
      formBody
        .descendant(*)
        .filter(_.attClasses(s"fr-email-${formFieldRole.entryName}"))
        .map(controlNodeInfo =>
          FormField(
            role      = formFieldRole,
            fieldName = controlNodeInfo.attValue("id").substringBefore("-control")
          )
      )
    }

  def parseParam(paramNodeInfo: NodeInfo): EmailMetadata.Param = {
    val name = paramNodeInfo.child(XMLNames.FRNameTest).stringValue
    paramNodeInfo.attValue("type") match {
      case "ControlValueParam" => EmailMetadata.ControlValueParam(name, paramNodeInfo.child(XMLNames.FRControlNameTest).stringValue)
      case "ExpressionParam"   => EmailMetadata.ExpressionParam  (name, paramNodeInfo.child(XMLNames.FRExprTest).stringValue)
    }
  }
}

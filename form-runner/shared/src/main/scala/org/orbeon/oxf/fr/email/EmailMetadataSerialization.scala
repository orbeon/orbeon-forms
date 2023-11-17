package org.orbeon.oxf.fr.email

import org.orbeon.dom.QName
import org.orbeon.oxf.fr.email.EmailMetadata.TemplateValue
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.xforms.NodeInfoFactory
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.saxon.om.{Item, NodeInfo}
import org.orbeon.saxon.value.StringValue

/**
 * Serialize case classes for the current format to XML
 */
object EmailMetadataSerialization {

  def serializeMetadata(metadata: EmailMetadata.Metadata): NodeInfo = {
    NodeInfoFactory.elementInfo(
      QName("email"),
      List(
        serializeTemplates(metadata.templates),
        serializeParams(metadata.params)
      )
    )
  }

  def serializeTemplates(templates: List[EmailMetadata.Template]): NodeInfo =
    NodeInfoFactory.elementInfo(
      QName("templates"),
      templates.map(serializeTemplate)
    )

  def serializeTemplate(template: EmailMetadata.Template): NodeInfo =
    NodeInfoFactory.elementInfo(
      QName("template"),
      List(
        Some(NodeInfoFactory.attributeInfo(QName("name"), template.name)),
        template.lang.map(NodeInfoFactory.attributeInfo(XMLConstants.XML_LANG_QNAME, _)),
        Some(NodeInfoFactory.elementInfo(
          QName("headers"),
          template.headers.map((serializeHeader _).tupled)
        )),
        //template.subject.map(subject => <subject mediatype={subject.isHTML.option("text/html").orNull}>{ subject.text }</subject>).orNull,
        template.subject.map(subject =>
          NodeInfoFactory.elementInfo(
            QName("subject"),
            List(
              subject.isHTML.option(NodeInfoFactory.attributeInfo(QName("mediatype"), "text/html")),
              Some(StringValue.makeStringValue(subject.text))
            ).flatten
          )
        ),
        template.body.map(body =>
          NodeInfoFactory.elementInfo(
            QName("body"),
            List(
              body.isHTML.option(NodeInfoFactory.attributeInfo(QName("mediatype"), "text/html")),
              Some(StringValue.makeStringValue(body.text))
            ).flatten
          )
        ),
        (template.attachPdf || template.attachFiles.isDefined || template.attachControls.nonEmpty).option {
          NodeInfoFactory.elementInfo(
            QName("attach"),
            template.attachPdf.option(NodeInfoFactory.attributeInfo(QName("pdf"), "true")).toList :::
              template.attachFiles.map(NodeInfoFactory.attributeInfo(QName("files"), _)).toList :::
              template.attachControls.map(serializeControl)
          )
        },
        Some(NodeInfoFactory.elementInfo(
          QName("exclude-from-all-control-values"),
          template.excludeFromAllControlValues.map(serializeControl)
        ))
      ).flatten
    )

  def serializeHeader(headerName: EmailMetadata.HeaderName, templateValue: EmailMetadata.TemplateValue): NodeInfo =
    NodeInfoFactory.elementInfo(
      QName("header"),
      NodeInfoFactory.attributeInfo(QName("name"), headerName.entryName) :: templateValueToItems(templateValue)
    )

  def serializeControl(templateValue: EmailMetadata.TemplateValue.Control): NodeInfo =
    NodeInfoFactory.elementInfo(
      QName("control"),
      templateValueToItems(templateValue)
    )

  def templateValueToItems(templateValue: EmailMetadata.TemplateValue): List[Item] =
    (templateValue match {
      case TemplateValue.Control(controlName, sectionOpt) =>
        List(
          Some(NodeInfoFactory.attributeInfo(QName("type"), "control-value")),
          sectionOpt.map(NodeInfoFactory.attributeInfo(QName("section-template"), _)),
          Some(StringValue.makeStringValue(controlName))
        )
      case TemplateValue.Expression(expression) =>
        List(
          Some(NodeInfoFactory.attributeInfo(QName("type"), "expression")),
          Some(StringValue.makeStringValue(expression))
        )
      case TemplateValue.Text(text) =>
        List(
          Some(NodeInfoFactory.attributeInfo(QName("type"), "text")),
          Some(StringValue.makeStringValue(text))
        )
    }).flatten

  def serializeParams(params: List[EmailMetadata.Param]): NodeInfo =
    NodeInfoFactory.elementInfo(
      QName("parameters"),
      params.map(serializeParam)
    )

  def serializeParam(param: EmailMetadata.Param): NodeInfo =
    param match {
      case EmailMetadata.Param.ControlValueParam(name, controlName) =>
        NodeInfoFactory.elementInfo(
          QName("param"),
          List(
            NodeInfoFactory.attributeInfo(QName("type"       ), "ControlValueParam"),
            NodeInfoFactory.elementInfo  (QName("name"       ), List(StringValue.makeStringValue(name))),
            NodeInfoFactory.elementInfo  (QName("controlName"), List(StringValue.makeStringValue(controlName))),
          )
        )
      case EmailMetadata.Param.ExpressionParam(name, expression) =>
        NodeInfoFactory.elementInfo(
          QName("param"),
          List(
            NodeInfoFactory.attributeInfo(QName("type"), "ExpressionParam"),
            NodeInfoFactory.elementInfo  (QName("name"), List(StringValue.makeStringValue(name))),
            NodeInfoFactory.elementInfo  (QName("expr"), List(StringValue.makeStringValue(expression))),
          )
        )
      case _ =>
        NodeInfoFactory.elementInfo(
          QName("param"),
          List(
            NodeInfoFactory.attributeInfo(QName("type"), param.entryName),
            NodeInfoFactory.elementInfo  (QName("name"), List(StringValue.makeStringValue(param.name))),
          )
        )
    }
}

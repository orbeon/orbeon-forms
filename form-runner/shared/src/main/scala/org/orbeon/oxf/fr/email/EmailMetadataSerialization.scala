package org.orbeon.oxf.fr.email

import org.orbeon.oxf.util.CoreUtils.BooleanOps

import scala.xml.Elem

/**
 * Serialize case classes for the current format to XML
 */
object EmailMetadataSerialization {

  def serializeMetadata(metadata: EmailMetadata.Metadata): Elem =
    <email>{
      List(
        serializeTemplates(metadata.templates),
        serializeParams   (metadata.params)
      )
    }</email>

  def serializeTemplates(templates: List[EmailMetadata.Template]): Elem =
    <templates>{ templates.map(serializeTemplate) }</templates>

  def serializeTemplate(template: EmailMetadata.Template): Elem =
    <template name={template.name} xml:lang={template.lang.orNull}>
      <form-fields>{ template.formFields.map(serializeField) }</form-fields>
      <subject mediatype={template.subject.isHTML.option("text/html").orNull}>{ template.subject.text }</subject>
      <body    mediatype={template.body   .isHTML.option("text/html").orNull}>{ template.body.text    }</body>
    </template>

  def serializeField(field: EmailMetadata.FormField): Elem =
    <_ name={field.fieldName}/>.copy(label = field.role.entryName)

  def serializeParams(params: List[EmailMetadata.Param]): Elem =
    <parameters>{ params.map(serializeParam) }</parameters>

  def serializeParam(param: EmailMetadata.Param): Elem =
    param match {
      case EmailMetadata.ControlValueParam(_, controlName) =>
        <fr:param type="ControlValueParam" xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
            <fr:name       >{ param.name  }</fr:name>
            <fr:controlName>{ controlName }</fr:controlName>
        </fr:param>
      case EmailMetadata.ExpressionParam(_, expression) =>
        <fr:param type="ExpressionParam" xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
            <fr:name>{ param.name  }</fr:name>
            <fr:expr>{ expression  }</fr:expr>
        </fr:param>
    }

}

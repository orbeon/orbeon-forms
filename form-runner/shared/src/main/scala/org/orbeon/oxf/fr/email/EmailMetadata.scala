package org.orbeon.oxf.fr.email

import enumeratum.EnumEntry.{Camelcase, Hyphencase}
import enumeratum.{Enum, EnumEntry}

import scala.collection.immutable

object EmailMetadata {

  case class Metadata(
    templates  : List[Template],
    params     : List[Param]
  )

  case class Template(
    name                       : String,
    lang                       : Option[String],
    headers                    : List[(HeaderName, TemplateValue)],
    subject                    : Option[Part],
    body                       : Option[Part],
    attachPdf                  : Boolean,
    attachFiles                : Option[String],
    attachControls             : List[TemplateValue.Control],
    excludeFromAllControlValues: List[TemplateValue.Control]
  )

  sealed trait TemplateValue
  object TemplateValue {
    case class Control(controlName: String, sectionOpt: Option[String]) extends TemplateValue

    case class Expression(expression: String)                           extends TemplateValue

    case class Text(text: String)                                       extends TemplateValue
  }

  sealed trait HeaderName extends EnumEntry with Hyphencase
  object HeaderName extends Enum[HeaderName] {
    case object From                       extends HeaderName
    case object To                         extends HeaderName
    case object CC                         extends HeaderName
    case object BCC                        extends HeaderName
    case object ReplyTo                    extends HeaderName
    case class  Custom(headerName: String) extends HeaderName {
      override def entryName: String = headerName
    }
    override def values: immutable.IndexedSeq[HeaderName] = super.findValues
  }

  case class Part(isHTML: Boolean, text: String)

  sealed trait Param extends EnumEntry with Camelcase {
    def name: String
    override def toString: String = getClass.getSimpleName // For `entryName` not to hold the above `name` value
  }

  object Param extends Enum[Param] {
    case class ControlValueParam     (name: String, controlName: String) extends Param
    case class ExpressionParam       (name: String, expression : String) extends Param
    case class AllControlValuesParam (name: String)                      extends Param
    case class LinkToEditPageParam   (name: String)                      extends Param
    case class LinkToViewPageParam   (name: String)                      extends Param
    case class LinkToNewPageParam    (name: String)                      extends Param
    case class LinkToSummaryPageParam(name: String)                      extends Param
    case class LinkToHomePageParam   (name: String)                      extends Param
    case class LinkToFormsPageParam  (name: String)                      extends Param
    case class LinkToAdminPageParam  (name: String)                      extends Param
    case class LinkToPdfParam        (name: String)                      extends Param
    override def values: immutable.IndexedSeq[Param] = super.findValues
  }

  object Legacy {
    case class FormField(
      role       : FormFieldRole,
      sectionOpt : Option[String],
      controlName: String
    )

    sealed trait FormFieldRole extends EnumEntry with Hyphencase

    object FormFieldRole extends Enum[FormFieldRole] {
      case object Recipient            extends FormFieldRole

      case object CC                   extends FormFieldRole

      case object BCC                  extends FormFieldRole

      case object Sender               extends FormFieldRole

      case object ReplyTo              extends FormFieldRole

      case object Attachment           extends FormFieldRole

      case object ExcludeFromAllFields extends FormFieldRole

      override def values: immutable.IndexedSeq[FormFieldRole] = super.findValues
    }

    case class Metadata2021(
      subject   : Option[Part2021],
      body      : Option[Part2021],
      formFields: List[FormField],
    )

    case class Part2021(
      templates: List[Template2021],
      params   : List[Param]
    )

    case class Template2021(
      lang  : String,
      isHTML: Boolean,
      text  : String
    )

    case class Metadata2022(
      templates: List[Template2022],
      params   : List[Param]
    )

    case class Template2022(
      name       : String,
      lang       : Option[String],
      formFields : List[FormField],
      subject    : Option[Part],
      body       : Option[Part],
      attachPdf  : Boolean,
      attachFiles: Option[String]
    )
  }
}

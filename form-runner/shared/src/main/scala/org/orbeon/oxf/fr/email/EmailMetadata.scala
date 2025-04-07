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
    name                                 : String,
    lang                                 : Option[String],
    headers                              : List[(HeaderName, TemplateValue)],
    enableIfTrue                         : Option[String],
    subject                              : Option[Part],
    body                                 : Option[Part],
    attachPdf                            : Option[Boolean],
    attachXml                            : Option[Boolean],
    filesToAttach                        : Option[FilesToAttach],
    controlsToAttach                     : List[TemplateValue.Control],
    controlsToExcludeFromAllControlValues: List[TemplateValue.Control]
  )

  sealed trait FilesToAttach extends EnumEntry with Hyphencase
  object FilesToAttach extends Enum[FilesToAttach] {
    case object All      extends FilesToAttach
    case object None     extends FilesToAttach
    case object Selected extends FilesToAttach

    override def values: immutable.IndexedSeq[FilesToAttach] = super.findValues
  }

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
    override def toString: String =
      try {
        // For `entryName` not to hold the above `name` value
        getClass.getSimpleName
      } catch {
        case _: InternalError =>
          // Workaround for Java 8 ("Malformed class name")
          getClass.toString.split('$').last
      }
  }

  sealed trait LinkParam extends Param

  sealed trait TokenLinkParam extends LinkParam {
    def token: Boolean
  }

  object Param extends Enum[Param] {
    case class ControlValueParam     (name: String, controlName: String) extends Param
    case class ExpressionParam       (name: String, expression : String) extends Param
    case class AllControlValuesParam (name: String                     ) extends Param
    case class LinkToEditPageParam   (name: String, token: Boolean     ) extends TokenLinkParam
    case class LinkToViewPageParam   (name: String, token: Boolean     ) extends TokenLinkParam
    case class LinkToPdfParam        (name: String, token: Boolean     ) extends TokenLinkParam
    case class LinkToNewPageParam    (name: String                     ) extends LinkParam
    case class LinkToSummaryPageParam(name: String                     ) extends LinkParam
    case class LinkToHomePageParam   (name: String                     ) extends LinkParam
    case class LinkToFormsPageParam  (name: String                     ) extends LinkParam
    case class LinkToAdminPageParam  (name: String                     ) extends LinkParam

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
      attachPdf  : Option[Boolean],
      attachFiles: Option[String]
    )
  }

  sealed trait TemplateMatch extends EnumEntry with Hyphencase
  object TemplateMatch extends Enum[TemplateMatch] {
    case object First extends TemplateMatch
    case object All   extends TemplateMatch

    override def values: immutable.IndexedSeq[TemplateMatch] = super.findValues
  }
}

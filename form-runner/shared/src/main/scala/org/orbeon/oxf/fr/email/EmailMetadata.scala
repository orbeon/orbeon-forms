package org.orbeon.oxf.fr.email

import enumeratum.{Enum, EnumEntry}
import enumeratum.EnumEntry.Hyphencase

import scala.collection.immutable

/**
 * Case classes
 */
object EmailMetadata {

  case class Metadata(
    templates  : List[Template],
    params     : List[Param]
  )

  case class Template(
    name       : String,
    lang       : Option[String],
    formFields : List[FormField],
    subject    : Part,
    body       : Part
  )

  case class FormField(
    role       : FormFieldRole,
    fieldName  : String
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

  case class Part(isHTML: Boolean, text: String)

  sealed trait Param
  case class ControlValueParam(name: String, controlName: String) extends Param
  case class ExpressionParam  (name: String, expression : String) extends Param

  implicit class ParamOps(private val p: Param) {
    // Common to all params
    def name: String = p match {
      case ControlValueParam(name, _) => name
      case ExpressionParam  (name, _) => name
    }
  }

  object Legacy2021 {

    case class Metadata(
      subject                 : Part,
      body                    : Part,
      formFields              : List[FormField],
    )

    case class Part(
      templates               : List[Template],
      params                  : List[Param]
    )

    case class Template(
      lang                    : String,
      isHTML                  : Boolean,
      text                    : String
    )
  }
}

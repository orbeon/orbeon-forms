package org.orbeon.oxf.fr.pdf.definitions20231


// See also `EmailMetadata.Param`
sealed trait Param { val name: String }
object Param {
  case class ControlValueParam     (name: String, controlName: String)           extends Param // preserve `controlName` to match `<*:controlName>`
  case class ExpressionParam       (name: String, expr: String)                  extends Param // preserve `expr` to match `<*:expr>`
  case class LinkToEditPageParam   (name: String)                                extends Param
  case class LinkToViewPageParam   (name: String)                                extends Param
  case class LinkToNewPageParam    (name: String)                                extends Param
  case class LinkToSummaryPageParam(name: String)                                extends Param
  case class LinkToHomePageParam   (name: String)                                extends Param
  case class LinkToFormsPageParam  (name: String)                                extends Param
  case class LinkToAdminPageParam  (name: String)                                extends Param
  case class LinkToPdfParam        (name: String)                                extends Param
  case class PageNumberParam       (name: String, format: Option[CounterFormat]) extends Param
  case class PageCountParam        (name: String, format: Option[CounterFormat]) extends Param
  case class FormTitleParam        (name: String)                                extends Param
  case class FormLogoParam         (name: String, url: Option[String])           extends Param
}

sealed trait HeaderFooterPosition
object HeaderFooterPosition {
  case object Left   extends HeaderFooterPosition
  case object Center extends HeaderFooterPosition
  case object Right  extends HeaderFooterPosition

  val AllValues: List[HeaderFooterPosition] = List(Left, Center, Right)
}

sealed trait HeaderFooterType
object HeaderFooterType {
  case object Header extends HeaderFooterType
  case object Footer extends HeaderFooterType

  val AllValues: List[HeaderFooterType] = List(Header, Footer)
}

// Enum for future extensibility
sealed trait HeaderFooterPageType
object HeaderFooterPageType {
  case object First extends HeaderFooterPageType
  case object Odd   extends HeaderFooterPageType
  case object Even  extends HeaderFooterPageType
  case object All   extends HeaderFooterPageType

  // Keep in order of precedence
  val AllValues: List[HeaderFooterPageType] = List(All, Even, Odd, First)
}

sealed trait CounterFormat
object CounterFormat {
  // Start with Western numbering systems (follow CSS names)
  case object Decimal            extends CounterFormat
  case object DecimalLeadingZero extends CounterFormat
  case object LowerRoman         extends CounterFormat
  case object UpperRoman         extends CounterFormat
  case object LowerGreek         extends CounterFormat
  case object LowerAlpha         extends CounterFormat
  case object UpperAlpha         extends CounterFormat
}

sealed trait PdfHeaderFooterCellConfig
object PdfHeaderFooterCellConfig {
  case object None                                                            extends PdfHeaderFooterCellConfig
  case object Inherit                                                         extends PdfHeaderFooterCellConfig
  case class  Template(values: Map[String, String], relevant: Option[String]) extends PdfHeaderFooterCellConfig
}

case class FormRunnerPdfConfigRoot(
  pages: Map[
      HeaderFooterPageType,
      Map[
        HeaderFooterType,
        Map[
            HeaderFooterPosition,
            PdfHeaderFooterCellConfig
        ]
      ]
    ],
  parameters: List[Param]
)

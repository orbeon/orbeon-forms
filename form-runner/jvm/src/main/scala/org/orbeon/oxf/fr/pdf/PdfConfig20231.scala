package org.orbeon.oxf.fr.pdf

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}
import org.orbeon.oxf.fr.pdf.definitions20231._
import org.orbeon.oxf.fr.ui.ScalaToXml
import org.orbeon.oxf.fr.{FormRunner, FormRunnerParams}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.xml.NamespaceMapping


object PdfConfig20231 extends ScalaToXml {

  val HeaderFooterPropertyName        = "oxf.fr.detail.pdf.header-footer"
  val HeaderFooterDefaultPropertyName = "oxf.fr.detail.pdf.header-footer.default"

  type MyState = FormRunnerPdfConfigRoot

//  import io.circe.generic.extras.semiauto._
//  import io.circe.generic.extras.Configuration
//  implicit val config: Configuration = Configuration.default.withKebabCaseMemberNames.withKebabCaseConstructorNames
  import io.circe.generic.auto._

  implicit val headerFooterTypeEncoder: KeyEncoder[HeaderFooterType] = {
    case HeaderFooterType.Header => "header"
    case HeaderFooterType.Footer => "footer"
  }

  implicit val fooKeyDecoder: KeyDecoder[HeaderFooterType] = {
    case "header" => Some(HeaderFooterType.Header)
    case "footer" => Some(HeaderFooterType.Footer)
  }

  implicit val headerFooterPageTypeEncoder: KeyEncoder[HeaderFooterPageType] = {
    case HeaderFooterPageType.All   => "all"
    case HeaderFooterPageType.Even  => "even"
    case HeaderFooterPageType.Odd   => "odd"
    case HeaderFooterPageType.First => "first"
  }

  implicit val headerFooterPageTypeDecoder: KeyDecoder[HeaderFooterPageType] = {
    case "all"   => Some(HeaderFooterPageType.All)
    case "even"  => Some(HeaderFooterPageType.Even)
    case "odd"   => Some(HeaderFooterPageType.Odd)
    case "first" => Some(HeaderFooterPageType.First)
  }

  implicit val headerFooterPositionEncoder: KeyEncoder[HeaderFooterPosition] = {
    case HeaderFooterPosition.Left   => "left"
    case HeaderFooterPosition.Center => "center"
    case HeaderFooterPosition.Right  => "right"
  }

  implicit val headerFooterPositionDecoder: KeyDecoder[HeaderFooterPosition] = {
    case "left"   => Some(HeaderFooterPosition.Left)
    case "center" => Some(HeaderFooterPosition.Center)
    case "right"  => Some(HeaderFooterPosition.Right)
  }

//  implicit val counterFormatEncoder: Encoder[CounterFormat] = {
//    case CounterFormat.Decimal            => Json.fromString("decimal")
//    case CounterFormat.DecimalLeadingZero => Json.fromString("decimal-leading-zero")
//    case CounterFormat.LowerRoman         => Json.fromString("lower-roman")
//    case CounterFormat.UpperRoman         => Json.fromString("upper-roman")
//    case CounterFormat.LowerGreek         => Json.fromString("lower-greek")
//    case CounterFormat.LowerAlpha         => Json.fromString("lower-alpha")
//    case CounterFormat.UpperAlpha         => Json.fromString("upper-alpha")
//  }

//  implicit val counterFormatDecoder: Decoder[CounterFormat] = (c: HCursor) => {
//    case "decimal"             => Right(CounterFormat.Decimal)
//    case "decimal-leading-zero" => Right(CounterFormat.DecimalLeadingZero)
//    case "lower-roman"         => Right(CounterFormat.LowerRoman)
//    case "upper-roman"         => Right(CounterFormat.UpperRoman)
//    case "lower-greek"         => Right(CounterFormat.LowerGreek)
//    case "lower-alpha"         => Right(CounterFormat.LowerAlpha)
//    case "upper-alpha"         => Right(CounterFormat.UpperAlpha)
//    case other                 => Left(DecodingFailure(s"Invalid counter format: `$other`", Nil))
//  }

  val encoder: Encoder[MyState] = implicitly
  val decoder: Decoder[MyState] = implicitly

  // Called from XSLT
  //@XPathFunction
  def camelToKebab(s: String): String =
    s.camelToKebab

  // Called from XSLT and XForms
  // TODO: cache config at property level
  //@XPathFunction
  def getHeaderFooterConfigXml(app: String, form: String): DocumentInfo = {

    // Only `app` and `form` are used
    implicit val params =
      FormRunnerParams(
        app         = app,
        form        = form,
        formVersion = 1,
        document    = None,
        isDraft     = None,
        mode        = "pdf"
      )

    def findConfig(propertyName: String): Option[(MyState, NamespaceMapping)] =
      FormRunner.formRunnerPropertyWithNs(propertyName) filter (_._1.nonAllBlank) map {
        case (configJson, configNs) =>
          decode(configJson).get -> configNs
        }

    findConfig(HeaderFooterPropertyName) match {
      case Some((userConfig, userConfigNs)) =>

        val (combinedConfig, combinedConfigNs) =
          findConfig(HeaderFooterDefaultPropertyName) match {
            case Some((defaultConfig, defaultConfigNs)) =>
              merge(defaultConfig, userConfig) ->
                NamespaceMapping.merge(defaultConfigNs, userConfigNs) // merging not perfect as one just wins over the other
            case None =>
              userConfig -> userConfigNs
          }

        fullXmlToSimplifiedXml(stateToFullXml(combinedConfig), namespaceMapping = combinedConfigNs)
      case None =>
        findConfig(HeaderFooterDefaultPropertyName) match {
          case Some((defaultConfig, defaultConfigNs)) =>
            fullXmlToSimplifiedXml(stateToFullXml(defaultConfig), namespaceMapping = defaultConfigNs)
          case None =>
            null
        }
    }
  }

  // Cell configurations of the new config override those of the base config. The `Inherit` value can be used to
  // explicitly inherit a value from the base config. The `None` value can be used to explicitly make a cell blank.
  def merge(baseConfig: MyState, newConfig: MyState): MyState = {

    val pageTypes =
      HeaderFooterPageType.AllValues flatMap { headerFooterPageType =>

        val headerOrFooters =
          HeaderFooterType.AllValues flatMap { headerFooterType =>

            val cells =
              HeaderFooterPosition.AllValues flatMap { headerFooterPosition =>
                val baseValue = baseConfig.pages.get(headerFooterPageType).flatMap(_.get(headerFooterType)).flatMap(_.get(headerFooterPosition))
                val newValue  = newConfig .pages.get(headerFooterPageType).flatMap(_.get(headerFooterType)).flatMap(_.get(headerFooterPosition))

                (
                  if (newValue.contains(PdfHeaderFooterCellConfig.Inherit))
                    baseValue
                  else
                    newValue.orElse(baseValue)
                ).map(headerFooterPosition ->)
              }

            cells.nonEmpty option (headerFooterType -> cells.toMap)
          }

        headerOrFooters.nonEmpty option (headerFooterPageType -> headerOrFooters.toMap)
      }

    FormRunnerPdfConfigRoot(pageTypes.toMap, baseConfig.parameters ++ newConfig.parameters) // parameters are not merged, just overridden
  }
}

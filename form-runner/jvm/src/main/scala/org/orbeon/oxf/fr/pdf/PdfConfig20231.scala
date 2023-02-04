package org.orbeon.oxf.fr.pdf

import io.circe._
import io.circe.syntax.EncoderOps
import org.orbeon.oxf.fr.pdf.definitions20231._
import org.orbeon.oxf.fr.ui.ScalaToXml
import org.orbeon.oxf.fr.{AppForm, FormRunner, FormRunnerParams}
import org.orbeon.oxf.properties.Property
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.xml.NamespaceMapping


object PdfConfig20231 extends ScalaToXml {

  private val HeaderFooterUserPropertyName    = "oxf.fr.detail.pdf.header-footer"
  private val HeaderFooterDefaultPropertyName = "oxf.fr.detail.pdf.header-footer.default"

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

  implicit val counterFormatEncoder: Encoder[CounterFormat] = {
    case CounterFormat.Decimal            => Json.fromString("decimal")
    case CounterFormat.DecimalLeadingZero => Json.fromString("decimal-leading-zero")
    case CounterFormat.LowerRoman         => Json.fromString("lower-roman")
    case CounterFormat.UpperRoman         => Json.fromString("upper-roman")
    case CounterFormat.LowerGreek         => Json.fromString("lower-greek")
    case CounterFormat.LowerAlpha         => Json.fromString("lower-alpha")
    case CounterFormat.UpperAlpha         => Json.fromString("upper-alpha")
  }

  implicit val counterFormatDecoder: Decoder[CounterFormat] = (c: HCursor) =>
    c.value.asString match {
      case None | Some("decimal")       => Right(CounterFormat.Decimal)
      case Some("decimal-leading-zero") => Right(CounterFormat.DecimalLeadingZero)
      case Some("lower-roman")          => Right(CounterFormat.LowerRoman)
      case Some("upper-roman")          => Right(CounterFormat.UpperRoman)
      case Some("lower-greek")          => Right(CounterFormat.LowerGreek)
      case Some("lower-alpha")          => Right(CounterFormat.LowerAlpha)
      case Some("upper-alpha")          => Right(CounterFormat.UpperAlpha)
      case other                        => Left(DecodingFailure(s"Invalid counter format: `$other`", Nil))
  }

  implicit val pdfHeaderFooterCellConfigEncoder: Encoder[PdfHeaderFooterCellConfig] = {
    case PdfHeaderFooterCellConfig.None                      => Json.fromString("none")
    case PdfHeaderFooterCellConfig.Inherit                   => Json.fromString("inherit")
    case PdfHeaderFooterCellConfig.Template(values, visible) =>
      Json.fromFields(
        ("values" -> values.asJson) :: visible.map(v => "visible" -> Json.fromString(v)).toList ::: Nil
      )
  }

  implicit val pdfHeaderFooterCellConfigDecoder: Decoder[PdfHeaderFooterCellConfig] = (c: HCursor) =>
    c.value.asString.map {
      case "none"    => Right(PdfHeaderFooterCellConfig.None)
      case "inherit" => Right(PdfHeaderFooterCellConfig.Inherit)
      case other     => Left(DecodingFailure(s"Invalid PDF header/footer cell config: `$other`", Nil))
    } getOrElse {
      for {
        values  <- c.get[Map[String, String]]("values")
        visible <- c.getOrElse[Option[String]]("visible")(None)
      } yield
        PdfHeaderFooterCellConfig.Template(values, visible)
    }

  val encoder: Encoder[MyState] = implicitly
  val decoder: Decoder[MyState] = implicitly

  // Called from XSLT and XForms
  // TODO: cache config at property level
  //@XPathFunction
  def getHeaderFooterConfigXml(app: String, form: String): DocumentInfo = {

    // Only `app` and `form` are used
    implicit val params: FormRunnerParams = FormRunnerParams(AppForm(app, form), "pdf")

    type CachedConfig = (MyState, NamespaceMapping, Option[DocumentInfo])

    def findAndCacheDecodedConfig(propertyName: String): Option[(Property, CachedConfig)] =
      FormRunner.formRunnerRawProperty(propertyName) collect { case p if p.stringValue.nonAllBlank =>
        p ->
          p.associatedValue { _ =>
            (
              decode(p.stringValue).get, // can throw if invalid JSON
              p.namespaceMapping,
              None // don't associate any XML here, see other comments
            )
          }
      }

    findAndCacheDecodedConfig(HeaderFooterUserPropertyName) match {
      case Some((_, (_, _, Some(xml)))) =>
        // Already fully associated, including the XML
        xml
      case Some((userProperty, (userConfig, userConfigNs, None))) =>
        // Local state is associated, but not the XML

        val (combinedConfig, combinedConfigNs, canAssociate) =
          findAndCacheDecodedConfig(HeaderFooterDefaultPropertyName) match {
            case Some((defaultProperty, (defaultConfig, defaultConfigNs, _))) =>
              (
                merge(defaultConfig, userConfig),
                NamespaceMapping.merge(defaultConfigNs, userConfigNs), // not perfect as one just wins over the other
                // Associating a final XML result with the default property works in all cases. Associating a final XML
                // result with the user property works only if the base property is less specific than the user property.
                // This means things will be slower if we cannot associate with the user property, but we don't expect
                // that to happen. We should probably warn in that case.
                FormRunner.trailingAppFormFromProperty(HeaderFooterUserPropertyName, userProperty)
                  .isNotLessSpecificThan(FormRunner.trailingAppFormFromProperty(HeaderFooterDefaultPropertyName, defaultProperty))
              )
            case None =>
              // This shouldn't happen as we have a default property for `*.*` in `properties-form-runner.xml`
              (userConfig, userConfigNs, true)
          }

        val combinedConfigXml = fullXmlToSimplifiedXml(stateToFullXml(combinedConfig), namespaceMapping = combinedConfigNs)
        if (canAssociate)
          userProperty.associateValue((combinedConfig, combinedConfigNs, Some(combinedConfigXml)))
        else
          () // TODO: warn?
        combinedConfigXml
      case None =>
        // No user property found, just use the default property
        findAndCacheDecodedConfig(HeaderFooterDefaultPropertyName) match {
          case Some((_, (_, _, Some(xml)))) =>
            // Already fully associated, including the XML
            xml
          case Some((defaultProperty, (defaultConfig, defaultConfigNs, None))) =>
            // State is associated, but not the XML, so compute that and update the associated value
            val defaultConfigXml = fullXmlToSimplifiedXml(stateToFullXml(defaultConfig), namespaceMapping = defaultConfigNs)
            defaultProperty.associateValue((defaultConfig, defaultConfigNs, Some(defaultConfigXml)))
            defaultConfigXml
          case None =>
            // This shouldn't happen as we have a default property for `*.*` in `properties-form-runner.xml`
            null
        }
    }
  }

  // Cell configurations of the new config override those of the base config. The `Inherit` value can be used to
  // explicitly inherit a value from the base config. The `None` value can be used to explicitly make a cell blank.
  def merge(superConfig: MyState, subConfig: MyState): MyState = {

    val pageTypes =
      HeaderFooterPageType.AllValues flatMap { headerFooterPageType =>

        val headerOrFooters =
          HeaderFooterType.AllValues flatMap { headerFooterType =>

            val cells =
              HeaderFooterPosition.AllValues flatMap { headerFooterPosition =>
                val superValue = superConfig.pages.get(headerFooterPageType).flatMap(_.get(headerFooterType)).flatMap(_.get(headerFooterPosition))
                val subValue   = subConfig  .pages.get(headerFooterPageType).flatMap(_.get(headerFooterType)).flatMap(_.get(headerFooterPosition))

                (
                  if (subValue.contains(PdfHeaderFooterCellConfig.Inherit))
                    superValue
                  else
                    subValue.orElse(superValue)
                ).map(headerFooterPosition ->)
              }

            cells.nonEmpty option (headerFooterType -> cells.toMap)
          }

        headerOrFooters.nonEmpty option (headerFooterPageType -> headerOrFooters.toMap)
      }

    FormRunnerPdfConfigRoot(pageTypes.toMap, superConfig.parameters ++ subConfig.parameters) // parameters are not merged, just overridden
  }
}

package org.orbeon.oxf.xforms

import org.orbeon.oxf.util.{IndentedLogger, StaticXPath}
import org.orbeon.oxf.xforms.XFormsProperties.UploadMaxSizeAggregateExpressionProperty
import org.orbeon.oxf.xforms.analysis.TopLevelPartAnalysis
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.oxf.util.StaticXPath.CompiledExpression
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.{XFormsProperties => P}


class XFormsStaticStateDynamicPropertiesImpl(
  nonDefaultProperties : Map[String, (String, Boolean)],
  staticProperties     : XFormsStaticStateStaticProperties,
  topLevelPart         : TopLevelPartAnalysis)(implicit
  logger               : IndentedLogger
) extends XFormsStaticStateDynamicProperties {

  val uploadMaxSizeAggregateExpression: Option[CompiledExpression] = {

    val compiledExpressionOpt =
      for {
        rawProperty <- staticProperties.staticStringProperty(UploadMaxSizeAggregateExpressionProperty).trimAllToOpt
        model       = topLevelPart.getDefaultModel
      } yield
        StaticXPath.compileExpression(
          xpathString      = rawProperty,
          namespaceMapping = model.namespaceMapping,
          locationData     = null,
          functionLibrary  = topLevelPart.functionLibrary,
          avt              = false
        )

    compiledExpressionOpt match {
      case Some(CompiledExpression(expr, _, _)) if StaticXPath.expressionType(expr) == StaticXPath.IntegerType =>
        compiledExpressionOpt
      case Some(_) =>
        throw new IllegalArgumentException(s"property `$UploadMaxSizeAggregateExpressionProperty` must return `xs:integer` type")
      case None =>
        None
    }
  }

  private val nonDefaultPropertiesOnly: Map[String, Either[Any, CompiledExpression]] =
    nonDefaultProperties map { case (name, (rawPropertyValue, isInline)) =>
      name -> {
        val maybeAVT = XMLUtils.maybeAVT(rawPropertyValue)
        topLevelPart.getDefaultModel match {
          case model if isInline && maybeAVT =>
            Right(StaticXPath.compileExpression(rawPropertyValue, model.namespaceMapping, null, topLevelPart.functionLibrary, avt = true))
          case _ =>
            Left(P.SupportedDocumentProperties(name).parseProperty(rawPropertyValue))
        }
      }
    }

  // For properties which can be AVTs
  // `Any` stands for `Int | Boolean | String`
  def propertyMaybeAsExpression(name: String): Either[Any, CompiledExpression] =
    nonDefaultPropertiesOnly.getOrElse(name, Left(P.SupportedDocumentProperties(name).defaultValue))
}
package org.orbeon.oxf.xforms

import org.orbeon.oxf.util.StaticXPath.CompiledExpression
import org.orbeon.oxf.util.{IndentedLogger, StaticXPath}
import org.orbeon.oxf.xforms.analysis.TopLevelPartAnalysis
import org.orbeon.oxf.xforms.{XFormsProperties => P}
import org.orbeon.oxf.xml.XMLUtils


class XFormsStaticStateDynamicPropertiesImpl(
  nonDefaultProperties : Map[String, (String, Boolean)],
  topLevelPart         : TopLevelPartAnalysis)(implicit
  logger               : IndentedLogger
) extends XFormsStaticStateDynamicProperties {

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
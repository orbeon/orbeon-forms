package org.orbeon.oxf.xforms

import org.orbeon.datatypes.MaximumSize
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.XFormsProperties.{UploadMaxSizeAggregateProperty, UploadMaxSizeProperty}
import org.orbeon.oxf.xforms.{XFormsProperties => P}


abstract class XFormsStaticStateStaticPropertiesImpl(
  val nonDefaultProperties    : Map[String, (String, Boolean)],
  globalMaxSizeProperty       : Int
) extends XFormsStaticStateStaticProperties {

  val isClientStateHandling   : Boolean     = staticStringProperty(P.StateHandlingProperty) == P.StateHandlingClientValue
  val isServerStateHandling   : Boolean     = staticStringProperty(P.StateHandlingProperty) == P.StateHandlingServerValue
  val isXPathAnalysis         : Boolean     = isPEFeatureEnabled(staticBooleanProperty(P.XpathAnalysisProperty),     P.XpathAnalysisProperty)
  val isCalculateDependencies : Boolean     = isPEFeatureEnabled(staticBooleanProperty(P.CalculateAnalysisProperty), P.CalculateAnalysisProperty)
  val allowErrorRecoveryOnInit: Boolean     = staticBooleanProperty(P.AllowErrorRecoveryOnInit)
  val isInlineResources       : Boolean     = staticBooleanProperty(P.InlineResourcesProperty)
  val allowedExternalEvents   : Set[String] = staticStringProperty(P.ExternalEventsProperty).tokenizeToSet

  val uploadMaxSize: MaximumSize =
    staticStringProperty(UploadMaxSizeProperty).trimAllToOpt flatMap
      MaximumSize.unapply orElse
      MaximumSize.unapply(globalMaxSizeProperty.toString) getOrElse
      MaximumSize.LimitedSize(0L)

  val uploadMaxSizeAggregate: MaximumSize =
    staticStringProperty(UploadMaxSizeAggregateProperty).trimAllToOpt flatMap
      MaximumSize.unapply getOrElse
      MaximumSize.UnlimitedSize

  // For properties known to be static
  private def staticPropertyOrDefault(name: String) =
    nonDefaultProperties.get(name)                      map
      (_._1)                                            map
      P.SupportedDocumentProperties(name).parseProperty getOrElse
      P.SupportedDocumentProperties(name).defaultValue

  def staticProperty       (name: String): Any     = staticPropertyOrDefault(name: String)
  def staticStringProperty (name: String): String  = staticPropertyOrDefault(name: String).toString
  def staticBooleanProperty(name: String): Boolean = staticPropertyOrDefault(name: String).asInstanceOf[Boolean]
  def staticIntProperty    (name: String): Int     = staticPropertyOrDefault(name: String).asInstanceOf[Int]
}
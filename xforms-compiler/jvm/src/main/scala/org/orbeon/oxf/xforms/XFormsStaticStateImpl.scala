/**
 * Copyright (C) 2011 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms

import org.orbeon.oxf.xml.XMLConstants.XS_STRING_QNAME
import org.orbeon.datatypes.MaximumSize
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.properties.Property
import org.orbeon.oxf.util.StaticXPath.CompiledExpression
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xforms.{XFormsProperties => P}
import org.orbeon.xforms.xbl.Scope


object XFormsStaticStateImpl {

  def apply(
    encodedState        : String,
    digest              : String,
    startScope          : Scope,
    metadata            : Metadata,
    template            : Option[AnnotatedTemplate], // for tests only?
    staticStateDocument : StaticStateDocument
  ): XFormsStaticStateImpl = {

    require(encodedState ne null)
    require(digest ne null)

    implicit val logger: IndentedLogger = Loggers.getIndentedLogger("analysis")

    val staticProperties =
      new XFormsStaticStateStaticPropertiesImpl(
        staticStateDocument.nonDefaultProperties,
        RequestGenerator.getMaxSizeProperty
      ) {
        protected def isPEFeatureEnabled(featureRequested: Boolean, featureName: String): Boolean =
          Version.instance.isPEFeatureEnabled(featureRequested, featureName)
      }

    val topLevelPart =
      PartAnalysisBuilder(staticProperties, None, startScope, metadata, staticStateDocument)

    val dynamicProperties =
      new XFormsStaticStateDynamicPropertiesImpl(
        staticStateDocument.nonDefaultProperties,
        staticProperties,
        topLevelPart
      )

    new XFormsStaticStateImpl(
      encodedState,
      digest,
      topLevelPart,
      template,
      staticStateDocument.isHTMLDocument,
      staticStateDocument.nonDefaultProperties,
      staticProperties,
      dynamicProperties,
      staticStateDocument
    )
  }
}

class XFormsStaticStateImpl(
  val encodedState                : String,
  val digest                      : String,
  val topLevelPart                : TopLevelPartAnalysis,
  val template                    : Option[AnnotatedTemplate],      // for serialization and tests
  val isHTMLDocument              : Boolean,
  val nonDefaultProperties        : Map[String, (String, Boolean)], // for serialization
  staticProperties                : XFormsStaticStateStaticProperties,
  dynamicProperties               : XFormsStaticStateDynamicProperties,
  val staticStateDocumentForTests : StaticStateDocument)(implicit
  val getIndentedLogger           : IndentedLogger
) extends XFormsStaticState
  with XFormsStaticStateStaticProperties
  with XFormsStaticStateDynamicProperties {

  // Delegate (Scala 3's `export` would be nice!)
  // export staticProperties._
  // export dynamicProperties._
  def isClientStateHandling               : Boolean          = staticProperties.isClientStateHandling
  def isServerStateHandling               : Boolean          = staticProperties.isServerStateHandling
  def isXPathAnalysis                     : Boolean          = staticProperties.isXPathAnalysis
  def isCalculateDependencies             : Boolean          = staticProperties.isCalculateDependencies
  def isInlineResources                   : Boolean          = staticProperties.isInlineResources
  def uploadMaxSize                       : MaximumSize      = staticProperties.uploadMaxSize
  def uploadMaxSizeAggregate              : MaximumSize      = staticProperties.uploadMaxSizeAggregate
  def staticProperty       (name: String) : Any              = staticProperties.staticProperty       (name)
  def staticStringProperty (name: String) : String           = staticProperties.staticStringProperty (name)
  def staticBooleanProperty(name: String) : Boolean          = staticProperties.staticBooleanProperty(name)
  def staticIntProperty    (name: String) : Int              = staticProperties.staticIntProperty    (name)
  def clientNonDefaultProperties          : Map[String, Any] = staticProperties.clientNonDefaultProperties
  def allowedExternalEvents               : Set[String]      = staticProperties.allowedExternalEvents

  def uploadMaxSizeAggregateExpression        : Option[CompiledExpression]      = dynamicProperties.uploadMaxSizeAggregateExpression
  def propertyMaybeAsExpression(name: String) : Either[Any, CompiledExpression] = dynamicProperties.propertyMaybeAsExpression(name)

  // TODO: don't use spray JSON and switch to Circe
  lazy val sanitizeInput: String => String = StringReplacer(staticProperties.staticStringProperty(P.SanitizeProperty))

  // TODO: don't use spray JSON and switch to Circe
  lazy val assets: XFormsAssets =
    XFormsAssetsBuilder.updateAssets(
      XFormsAssetsBuilder.fromJsonProperty,
      staticProperties.staticStringProperty(P.AssetsBaselineExcludesProperty).trimAllToOpt,
      staticProperties.staticStringProperty(P.AssetsBaselineUpdatesProperty).trimAllToOpt.map(propValue =>
        Property(
          XS_STRING_QNAME,
          propValue,
          topLevelPart
            .defaultModel
            .map(_.namespaceMapping.mapping)
            // FIXME: It's unclear what namespace mapping to use! With Form Runner/Form Builder, the
            //   property value is read and copied over to an attribute on the first model, but the
            //   namespace context from the properties file is lost in this process. Maybe we should
            //   instead convert QNames to EQNames.
            .getOrElse(throw new IllegalArgumentException("can't find namespace mapping"))
        )
      )
    )
}

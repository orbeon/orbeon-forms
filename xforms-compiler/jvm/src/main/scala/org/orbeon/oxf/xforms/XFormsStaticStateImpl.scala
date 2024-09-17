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

import org.orbeon.datatypes.MaximumSize
import org.orbeon.dom.QName
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.properties.Property
import org.orbeon.oxf.util.StaticXPath.CompiledExpression
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.*
import org.orbeon.oxf.xforms.analysis.*
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xforms.xbl.{BindingLoader, XBLAssets}
import org.orbeon.oxf.xforms.XFormsProperties as P
import org.orbeon.oxf.xml.XMLConstants.XS_STRING_QNAME
import org.orbeon.xforms.xbl.Scope

import java.net.URI
import scala.collection.immutable.SortedMap
import scala.collection.mutable


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

    implicit val logger: IndentedLogger = Loggers.newIndentedLogger("analysis") // https://github.com/orbeon/orbeon-forms/issues/179

    val staticProperties =
      new XFormsStaticStateStaticPropertiesImpl(
        staticStateDocument.nonDefaultProperties,
        RequestGenerator.getMaxSizeProperty
      ) {
        def isPEFeatureEnabled(featureRequested: Boolean, featureName: String): Boolean =
          Version.instance.isPEFeatureEnabled(featureRequested, featureName)
      }

    val topLevelPart =
      PartAnalysisBuilder(staticProperties, None, startScope, metadata, staticStateDocument)

    val dynamicProperties =
      new XFormsStaticStateDynamicPropertiesImpl(
        staticStateDocument.nonDefaultProperties,
        topLevelPart
      )

    new XFormsStaticStateImpl(
      encodedState,
      digest,
      topLevelPart,
      topLevelPart.metadata,
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
  metadata                        : Metadata,
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
  def isPEFeatureEnabled(featureRequested: Boolean, featureName: String): Boolean =
    staticProperties.isPEFeatureEnabled(featureRequested, featureName)

  def isClientStateHandling               : Boolean          = staticProperties.isClientStateHandling
  def isServerStateHandling               : Boolean          = staticProperties.isServerStateHandling
  def isXPathAnalysis                     : Boolean          = staticProperties.isXPathAnalysis
  def isCalculateDependencies             : Boolean          = staticProperties.isCalculateDependencies
  def singleUseStaticState                : Boolean          = staticProperties.singleUseStaticState
  def allowErrorRecoveryOnInit            : Boolean          = staticProperties.allowErrorRecoveryOnInit
  def isInlineResources                   : Boolean          = staticProperties.isInlineResources
  def uploadMaxSizePerFile                : MaximumSize      = staticProperties.uploadMaxSizePerFile
  def uploadMaxSizeAggregatePerControl    : MaximumSize      = staticProperties.uploadMaxSizeAggregatePerControl
  def uploadMaxSizeAggregatePerForm       : MaximumSize      = staticProperties.uploadMaxSizeAggregatePerForm
  def staticProperty       (name: String) : Any              = staticProperties.staticProperty       (name)
  def staticStringProperty (name: String) : String           = staticProperties.staticStringProperty (name)
  def staticBooleanProperty(name: String) : Boolean          = staticProperties.staticBooleanProperty(name)
  def staticIntProperty    (name: String) : Int              = staticProperties.staticIntProperty    (name)
  def allowedExternalEvents               : Set[String]      = staticProperties.allowedExternalEvents

  def propertyMaybeAsExpression(name: String) : Either[Any, CompiledExpression] = dynamicProperties.propertyMaybeAsExpression(name)

  lazy val sanitizeInput: String => String =
    StringReplacer(staticProperties.staticStringProperty(P.SanitizeProperty))

  lazy val baselineAssets: XFormsAssets = {

    val xblBaseline = metadata.xblBaselineAssets

    val updatedAssets =
      XFormsAssetsBuilder.updateAssets(
        globalAssetsBaseline = XFormsAssetsBuilder.fromJsonProperty(CoreCrossPlatformSupport.properties),
        globalXblBaseline    = xblBaseline.keySet,
        localExcludesProp    = staticProperties.staticStringProperty(P.AssetsBaselineExcludesProperty).trimAllToOpt,
        localUpdatesProp     = staticProperties.staticStringProperty(P.AssetsBaselineUpdatesProperty).trimAllToOpt.map(propValue =>
          Property(
            XS_STRING_QNAME,
            propValue,
            topLevelPart.getDefaultModel
              .namespaceMapping.mapping,
            // FIXME: It's unclear what namespace mapping to use! With Form Runner/Form Builder, the
            //   property value is read and copied over to an attribute on the first model, but the
            //   namespace context from the properties file is lost in this process. Maybe we should
            //   instead convert QNames to EQNames.
            P.AssetsBaselineUpdatesProperty
          )
        )
      )

    val allInUseBindingAssets =
      BindingLoader.collectBindingAssets(metadata.allBindingsMaybeDuplicates)

    // This is in `QName` order
    val partiallyResolvedXblBaselineAssetPaths =
      updatedAssets.xbl.toList.sorted.map { qname =>
        xblBaseline
          .get(qname)
          .orElse(allInUseBindingAssets.get(qname))
          .toLeft(qname)
      }

    // Do a single call to get the missing bindings
    val missingBindings =
      BindingLoader.findXblAssetsUnordered(partiallyResolvedXblBaselineAssetPaths.collect{ case Right(qname) => qname }.toSet)

    // This keeps the `QName` order
    val resolvedXblBaselineAssetPaths =
      partiallyResolvedXblBaselineAssetPaths.map {
        case Left(value)  => value
        case Right(qname) => missingBindings.getOrElse(qname, throw new IllegalArgumentException(qname.qualifiedName))
      }

    XFormsAssets(
      css = updatedAssets.css ::: resolvedXblBaselineAssetPaths.flatMap(_.css),
      js  = updatedAssets.js  ::: resolvedXblBaselineAssetPaths.flatMap(_.js)
    )
  }

  lazy val bindingAssets: XBLAssets = {
    val orderedBindings = SortedMap[QName, XBLAssets]() ++ topLevelPart.allXblAssetsMaybeDuplicates
    XBLAssets(
      css = orderedBindings.values.iterator.flatMap(_.css).to(mutable.LinkedHashSet).toList,
      js  = orderedBindings.values.iterator.flatMap(_.js) .to(mutable.LinkedHashSet).toList
    )
  }

  // No cache on the JVM for now
  // https://github.com/orbeon/orbeon-forms/issues/6462
  def fromUriCacheOrElse(uri: URI, compute: => URI): URI = compute
  def clearUriCache(): Unit = ()

  // No resource resolver on the JVM for now
  val resourceResolverOpt: Option[ResourceResolver] = None
}

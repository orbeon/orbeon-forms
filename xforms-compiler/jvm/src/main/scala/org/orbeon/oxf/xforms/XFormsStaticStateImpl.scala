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
import org.orbeon.dom.tree.CompactElement
import org.orbeon.dom.{Document, Element, Namespace, QName}
import org.orbeon.oxf.cache.{MemorySizeEstimator, SizedCacheable}
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.processor.generator.RequestGenerator
import org.orbeon.oxf.properties.Property
import org.orbeon.oxf.util.StaticXPath.CompiledExpression
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.*
import org.orbeon.oxf.xforms.analysis.*
import org.orbeon.oxf.xforms.analysis.controls.ComponentControl
import org.orbeon.oxf.xforms.analysis.model.{Instance, Model}
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xforms.xbl.{BindingLoader, XBLAssets}
import org.orbeon.oxf.xforms.XFormsProperties as P
import org.orbeon.oxf.xml.{EncodeDecode, XMLConstants}
import org.orbeon.oxf.xml.XMLConstants.XS_STRING_QNAME
import org.orbeon.oxf.xml.dom.Extensions.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.xbl.Scope

import java.lang as jl
import java.net.URI
import java.util as ju
import scala.collection.immutable.SortedMap
import scala.collection.mutable
import scala.util.Try


object XFormsStaticStateImpl {

  final case class FormRunnerStaticStateMetadata(
    appOpt      : Option[String],
    formOpt     : Option[String],
    versionOpt  : Option[Int],
    titleOpt    : Option[String],
    majorModeOpt: Option[String],
    configTraits: List[String]
  )

  private val FRNamespace        = Namespace("fr", "http://orbeon.org/oxf/xml/form-runner")
  private val FRAppQName         = QName("app",           FRNamespace)
  private val FRFormQName        = QName("form",          FRNamespace)
  private val FRFormVersionQName = QName("form-version",  FRNamespace)
  private val FRMajorModeQName   = QName("major-mode",    FRNamespace)
  private val FRTraitsQName      = QName("config-traits", FRNamespace)

  private def computeStepXmlBaseOpt(analysis: ElementAnalysis): Option[String] =
    analysis.compileElementOpt.flatMap { srcElem =>
      val stopElemOpt = analysis.parent.flatMap(_.compileElementOpt)
      var xmlBasesRootToLeaf: List[String] = Nil
      var cur: Element = srcElem
      while ((cur ne null) && ! stopElemOpt.contains(cur)) {
        val b = cur.attributeValue(XMLConstants.XML_BASE_QNAME)
        if (b ne null)
          xmlBasesRootToLeaf = b :: xmlBasesRootToLeaf
        cur = cur.getParent
      }
      xmlBasesRootToLeaf match {
        case Nil           => None
        case single :: Nil => Some(single)
        case many          =>
          Try {
            many.foldLeft(null: URI) { (acc, s) =>
              val u = URI.create(s)
              if (acc eq null) u else acc.resolve(u)
            }.toString
          }.toOption.orElse(many.lastOption)
      }
    }

  private def compactAnalyses(analysesIt: Iterator[ElementAnalysis]): Unit = {
    val seen = ju.Collections.newSetFromMap(new ju.IdentityHashMap[ElementAnalysis, jl.Boolean])
    val all  = new mutable.ArrayBuffer[ElementAnalysis]
    analysesIt.foreach { a =>
      if ((a ne null) && seen.add(a))
        all += a
    }

    val rawSteps = new ju.IdentityHashMap[ElementAnalysis, String](all.size)
    all.foreach { a =>
      computeStepXmlBaseOpt(a).foreach(rawSteps.put(a, _))
    }

    all.foreach { a =>
      val stepOpt = Option(rawSteps.get(a)).filterNot { s =>
        s == "." &&
          ElementAnalysis
            .ancestorsIterator(a, includeSelf = false)
            .flatMap(p => Option(rawSteps.get(p)))
            .nextOption()
            .contains(".")
      }
      a.compactForRuntime(stepOpt)
    }
  }

  def apply(
    encodedState        : String,
    digest              : String,
    startScope          : Scope,
    metadata            : Metadata,
    template            : Option[AnnotatedTemplate], // for tests only?
    staticStateDocument : StaticStateDocument
  ): XFormsStaticStateImpl = {
    require(encodedState ne null)
    apply(
      Right(encodedState),
      digest,
      startScope,
      metadata,
      template,
      staticStateDocument
    )
  }

  def apply(
    encodedStatePayload : Either[Array[Byte], String],
    digest              : String,
    startScope          : Scope,
    metadata            : Metadata,
    template            : Option[AnnotatedTemplate], // for tests only?
    staticStateDocument : StaticStateDocument
  ): XFormsStaticStateImpl = {

    require(encodedStatePayload ne null)
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

    val isHTMLDocument       = staticStateDocument.isHTMLDocument
    val nonDefaultProperties = staticStateDocument.nonDefaultProperties

    staticStateDocument.trimAfterExtraction()

    new XFormsStaticStateImpl(
      encodedStatePayload,
      digest,
      topLevelPart,
      topLevelPart.metadata,
      template,
      isHTMLDocument,
      nonDefaultProperties,
      staticProperties,
      dynamicProperties
    )
  }
}

class XFormsStaticStateImpl(
  val encodedStatePayload  : Either[Array[Byte], String],
  val digest               : String,
  val topLevelPart         : TopLevelPartAnalysis,
  metadata                 : Metadata,
  val template             : Option[AnnotatedTemplate],      // for serialization and tests
  val isHTMLDocument       : Boolean,
  val nonDefaultProperties : Map[String, (String, Boolean)], // for serialization
  staticProperties         : XFormsStaticStateStaticProperties,
  dynamicProperties        : XFormsStaticStateDynamicProperties
)(implicit
  val getIndentedLogger    : IndentedLogger
) extends XFormsStaticState
  with XFormsStaticStateStaticProperties
  with XFormsStaticStateDynamicProperties
  with SizedCacheable {

  lazy val encodedState: String =
    encodedStatePayload match {
      case Right(s)    => s
      case Left(bytes) => EncodeDecode.encodeCompressedBytesToString(bytes, encrypt = isClientStateHandling, SecureUtils.KeyUsage.GeneralNoCheck)
    }

  def serverEncodedStateBytes: Array[Byte] =
    encodedStatePayload match {
      case Left(bytes) => bytes
      case Right(s)    => Compressor.compressBytes(EncodeDecode.decodeBytes(s, forceEncryption = false, SecureUtils.KeyUsage.GeneralNoCheck))
    }

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
  def propertyProfileOpt                  : Option[String]   = staticProperties.propertyProfileOpt

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

  def encodedStateBytes  : Long =
    encodedStatePayload match {
      case Left(bytes) => MemorySizeEstimator.estimateBytes(bytes)
      case Right(s)    => MemorySizeEstimator.estimateStringBytes(s)
    }
  def templateSizeBytes  : Long = template.map(_.saxStore.approximateSizeBytes).getOrElse(0L)
  def staticDomSizeBytes : Long = {
    val seenDocs  = ju.Collections.newSetFromMap(new ju.IdentityHashMap[Document, jl.Boolean])
    val seenElems = ju.Collections.newSetFromMap(new ju.IdentityHashMap[Element, jl.Boolean])
    var sum       = 0L

    def addDetachedOrDocumentElem(elem: Element): Unit = {
      val doc = elem.getDocument
      if (doc ne null) {
        if (seenDocs.add(doc))
          sum += MemorySizeEstimator.estimateOrbeonDomDocument(doc)
      } else if (seenElems.add(elem)) {
        sum += MemorySizeEstimator.estimateOrbeonDomElement(elem, maxElements = 25000)
      }
    }

    for (control <- topLevelPart.controlAnalysisMap.valuesIterator) {
      control.compileElementOpt match {
        case Some(elem) =>
          addDetachedOrDocumentElem(elem)
        case None =>
          sum += org.orbeon.oxf.cache.DomMemorySizeEstimator.estimateRawAttributesArray(control.rawAtts)
      }

      control match {
        case inst: Instance =>
          inst.inlineRootElemOpt.foreach(addDetachedOrDocumentElem)
        case model: Model if model.isCompactedForRuntime =>
          model.inlineSchemaElements.foreach(addDetachedOrDocumentElem)
        case _ =>
      }
    }

    sum
  }

  lazy val formRunnerStaticStateMetadataOpt: Option[XFormsStaticStateImpl.FormRunnerStaticStateMetadata] = {
    import XFormsStaticStateImpl.*

    val paramsModelOpt = topLevelPart.findModel("fr-parameters-model")

    val configMajorModeOpt = paramsModelOpt.flatMap(_.attributeValueOpt(FRMajorModeQName)).flatMap(_.trimAllToOpt)
    val configAppOpt       = paramsModelOpt.flatMap(_.attributeValueOpt(FRAppQName)).flatMap(_.trimAllToOpt)
    val configFormOpt      = paramsModelOpt.flatMap(_.attributeValueOpt(FRFormQName)).flatMap(_.trimAllToOpt)
    val configVersionOpt   = paramsModelOpt.flatMap(_.attributeValueOpt(FRFormVersionQName)).flatMap(_.trimAllToOpt).flatMap(_.toIntOption).filter(_ > 0)
    val configTraits       = paramsModelOpt.flatMap(_.attributeValueOpt(FRTraitsQName)).toList.flatMap(_.splitTo[List]())

    val formMetaTupleOpt: Option[(String, String, Option[String])] =
      for {
        model <- topLevelPart.findModel("fr-form-model")
        inst  <- model.instances.get("fr-form-metadata")
        res   <- inst.constantContent match {
          case Some(docNode) =>
            for {
              root <- (docNode / *).headOption
              app  <- (root / "application-name").headOption.flatMap(_.stringValue.trimAllToOpt)
              form <- (root / "form-name").headOption.flatMap(_.stringValue.trimAllToOpt)
            } yield (app, form, (root / "title").headOption.flatMap(_.stringValue.trimAllToOpt))
          case None =>
            for {
              rootElem <- inst.inlineRootElemOpt
              app      <- rootElem.elementOpt("application-name").flatMap(_.getText.trimAllToOpt)
              form     <- rootElem.elementOpt("form-name").flatMap(_.getText.trimAllToOpt)
            } yield (app, form, rootElem.elementOpt("title").flatMap(_.getText.trimAllToOpt))
        }
      } yield res

    val appOpt   = configAppOpt.orElse(formMetaTupleOpt.map(_._1))
    val formOpt  = configFormOpt.orElse(formMetaTupleOpt.map(_._2))
    val titleOpt = formMetaTupleOpt.flatMap(_._3)
    val effectiveVersionOpt =
      if (appOpt.contains("orbeon") && formOpt.contains("builder")) None
      else configVersionOpt

    if (configMajorModeOpt.isDefined || appOpt.isDefined)
      Some(
        FormRunnerStaticStateMetadata(
          appOpt       = appOpt,
          formOpt      = formOpt,
          versionOpt   = effectiveVersionOpt,
          titleOpt     = titleOpt,
          majorModeOpt = configMajorModeOpt,
          configTraits = configTraits
        )
      )
    else
      None
  }

  // Diagnostic metadata extracted from `fr-form-metadata` when compiled by Form Runner: (app, form, titleOpt)
  lazy val formRunnerMetadataOpt: Option[(String, String, Option[String])] =
    for {
      meta <- formRunnerStaticStateMetadataOpt
      app  <- meta.appOpt
      form <- meta.formOpt
    } yield (app, form, meta.titleOpt)

  // Release duplicate Orbeon DOM subtrees on `readonly && useInlineContent` instances, detach mutable inline instance
  // roots, and compact all `ElementAnalysis` instances in place for server-side execution.
  // Offline Scala.js compilation (`XFormsCompiler`) omits this step because `XFormsStaticStateSerializer` reads `inlineRootElemOpt`.
  def trimAfterServerCompilation(): this.type = {
    import XFormsStaticStateImpl.compactAnalyses

    val _ = formRunnerStaticStateMetadataOpt

    // 1. Materialize readonly `TinyTree`s and detach mutable inline instance roots first while parent namespaces are intact
    for {
      model <- topLevelPart.iterateModels
      inst  <- model.instances.valuesIterator
    } inst.releaseInlineDom()

    // 2. Compact every `ElementAnalysis` (preserving `<xs:schema>` children on `Model` and minimal `xml:base` steps)
    compactAnalyses(
      topLevelPart.controlAnalysisMap.valuesIterator ++
        topLevelPart.iterateControls ++
        topLevelPart.iterateModels.flatMap(ElementAnalysis.iterateDescendants(_, includeSelf = true))
    )

    this
  }

  def estimatedMemoryBytes: Long = {
    val controlsBytes = topLevelPart.controlAnalysisMap.size.toLong * 600L
    val componentTemplatesBytes =
      topLevelPart
        .controlAnalysisMap
        .valuesIterator
        .collect { case cc: ComponentControl => cc.bindingOpt.map(_.templateTree.approximateSizeBytes).getOrElse(0L) }
        .sum
    val topLevelConstantInstancesBytes =
      topLevelPart
        .iterateModels
        .flatMap(_.instances.valuesIterator)
        .filter(i => i.readonly && i.useInlineContent && ! i.parent.flatMap(_.parent).exists(_.isInstanceOf[ComponentControl]))
        .flatMap(_.constantContent)
        .map(MemorySizeEstimator.estimateBytes)
        .sum

    512L + encodedStateBytes + templateSizeBytes + staticDomSizeBytes + controlsBytes + componentTemplatesBytes + topLevelConstantInstancesBytes
  }
}

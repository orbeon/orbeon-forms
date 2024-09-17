/**
 * Copyright (C) 2015 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.xbl

import org.orbeon.dom.{Document, Element, QName}
import org.orbeon.oxf.pipeline.Transform
import org.orbeon.oxf.properties.{Property, PropertySet}
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, IndentedLogger, Logging}
import org.orbeon.oxf.xforms.{AssetPath, XFormsAssets, XFormsAssetsBuilder}
import org.orbeon.oxf.xforms.xbl.XBLAssetsBuilder.HeadElementBuilder
import org.orbeon.oxf.xml.ParserConfiguration
import org.orbeon.oxf.xml.dom.Extensions
import org.orbeon.xforms.HeadElement
import org.orbeon.xforms.XFormsNames.*
import org.xml.sax.Attributes

import scala.collection.mutable


trait BindingLoader extends Logging {

  private val XBLMappingPropertyPrefix = "oxf.xforms.xbl.mapping."
  private val XBLLibraryProperty       = "oxf.xforms.xbl.library"
  private val XBLBaselineProperty      = "oxf.xforms.resources.baseline"

  def getPropertySet: PropertySet
  def lastModifiedByPath(path: String)(implicit indentedLogger: IndentedLogger): Long
  def existsByPath(path: String)(implicit indentedLogger: IndentedLogger): Boolean
  def contentAsOrbeonDom(path: String)(implicit indentedLogger: IndentedLogger): Document

  def findXblAssetsUnordered(xbl: Set[QName])(implicit indentedLogger: IndentedLogger): Map[QName, XFormsAssets] = {

    val (foundBaselinePaths, _) =
      pathsForQNames(xbl, readURLMappingsCacheAgainstProperty)

    val (_, abstractBindings) =
      extractAndIndexFromPaths(
        GlobalBindingIndex.Empty,
        foundBaselinePaths
      )

    collectBindingAssets(abstractBindings)
  }

  def getUpToDateLibraryAndBaseline(
    indexOpt      : Option[BindingIndex[IndexableBinding]],
    checkUpToDate : Boolean // 2016-10-06: always set to `true`
  )(implicit
    indentedLogger: IndentedLogger
  ): (BindingIndex[IndexableBinding], Set[String], Map[QName, XFormsAssets]) = {

    var originalOrUpdatedIndexOpt = indexOpt

    val (baseline, checkedPaths) = {

      val propertySet = getPropertySet

      val libraryProperty  = propertySet.getPropertyOrThrow(XBLLibraryProperty)
      val baselineProperty = propertySet.getPropertyOrThrow(XBLBaselineProperty)

      def readAndIndexBindings(implicit indentedLogger: IndentedLogger): (List[AbstractBinding], Set[String]) = {

        debug("reloading library and baseline")

        // These are not namespace mappings! They map a namespace URI to a prefix, such as
        // `http://orbeon.org/oxf/xml/form-runner -> orbeon`.
        val nsUriToPrefix = readURLMappingsCacheAgainstProperty

        def propertyQNames(property: Property) =
          property.stringValue.tokenizeToSet flatMap
            (Extensions.resolveQName(property.namespaces.get, _, unprefixedIsNoNamespace = true))

        // 1. Legacy `oxf.xforms.resources.baseline` property
        val (foundBaselinePathsStep1, notFoundBaselinePathsStep1) =
          pathsForQNames(propertyQNames(baselineProperty), nsUriToPrefix)

        val (baselineIndexStep1, baselineBindingsStep1) =
          extractAndIndexFromPaths(
            GlobalBindingIndex.Empty, // note the empty index
            foundBaselinePathsStep1
          )

        // 2. `oxf.xforms.assets.baseline` property
        // https://github.com/orbeon/orbeon-forms/issues/4810
        val (foundBaselinePathsStep2, notFoundBaselinePathsStep2) =
          pathsForQNames(XFormsAssetsBuilder.fromJsonPropertyWithXbl(getPropertySet).xbl, nsUriToPrefix)

        val (baselineIndexStep2, baselineBindingsStep2) =
          extractAndIndexFromPaths(
            baselineIndexStep1,
            foundBaselinePathsStep2 -- foundBaselinePathsStep1
          )

        // 3. `oxf.xforms.xbl.library` property
        val (foundLibraryPaths, notFoundLibraryPaths) =
          pathsForQNames(propertyQNames(libraryProperty), nsUriToPrefix)

        val (newIndex, _) =
          extractAndIndexFromPaths(
            baselineIndexStep2,
            foundLibraryPaths -- foundBaselinePathsStep2 -- foundBaselinePathsStep1
          )

        val allCheckedPaths =
          foundBaselinePathsStep1    ++
          notFoundBaselinePathsStep1 ++
          foundBaselinePathsStep2    ++
          notFoundBaselinePathsStep2 ++
          foundLibraryPaths          ++
          notFoundLibraryPaths

        // Side-effect!
        originalOrUpdatedIndexOpt = Some(newIndex)

        (baselineBindingsStep1 ::: baselineBindingsStep2 /* TODO: Check duplicates? */, allCheckedPaths)
      }

      lazy val lazyIndexAndBindings = readAndIndexBindings

      // If the original index is empty, force the evaluation of the library and baseline
      // https://github.com/orbeon/orbeon-forms/issues/3327
      if (indexOpt.isEmpty)
        lazyIndexAndBindings

      def reloadLibraryAndBaseline: (Map[QName, XFormsAssets], Set[String]) =
        (collectBindingAssets(lazyIndexAndBindings._1), lazyIndexAndBindings._2)

      // We read and associate the value with 2 properties, but evaluation occurs at most once
      lazy val lazyEvaluatedValue = reloadLibraryAndBaseline
      def evaluate(property: Property) = lazyEvaluatedValue

      // NOTE: For `oxf.xforms.assets.baseline` we already associated the JSON so we can't also
      // associate this.
      libraryProperty.associatedValue(evaluate)
      baselineProperty.associatedValue(evaluate)

      // Right here, `reloadLibraryAndBaseline` has been called exactly once if a property has changed, and none otherwise. The index
      // `originalOrUpdatedIndexOpt` is updated in this case, and also if the original index was empty.
    }

    // If the index is unmodified, it might contain out-of-date bindings. If it is modified, it is guaranteed by
    // evaluate() above to be a new index with up-to-date library bindings.
    if (checkUpToDate)
      for {
        index <- indexOpt
        if originalOrUpdatedIndexOpt exists (_ eq index)
      } locally {
        originalOrUpdatedIndexOpt = Some(updateOutOfDateBindings(index, checkedPaths))
      }

    debug(
      "library and baseline details",
      List(
        "paths"    -> checkedPaths.mkString(", "),
        "baseline" -> baseline.toString,
      )
    )

    (originalOrUpdatedIndexOpt getOrElse (throw new IllegalStateException), checkedPaths, baseline)
  }

  def findMostSpecificBinding(
    index        : BindingIndex[IndexableBinding],
    checkedPaths : Option[Set[String]],
    uri          : String,
    localname    : String,
    atts         : Attributes
  )(implicit
    indentedLogger: IndentedLogger
  ): (BindingIndex[IndexableBinding], Set[String], Option[IndexableBinding]) = {

    var currentIndex        = index
    var currentCheckedPaths = checkedPaths getOrElse Set.empty

    def mustCheckBindingPath(binding: IndexableBinding) =
      checkedPaths.isDefined && (
        binding.path match {
          case Some(path) if ! currentCheckedPaths(path) => true
          case _                                         => false
        }
      )

    def findFromAlreadyLoadedBindings: Option[IndexableBinding] = {

      val qName   = QName(localname, "", uri)
      val attsSeq = convertAttributes(atts)

      BindingIndex.findMostSpecificBinding(currentIndex, qName, attsSeq) match {
        case Some((binding, true)) if mustCheckBindingPath(binding) =>

          // We found a binding by name, but we haven't checked if that path is up-to-date yet. So make sure
          // the binding is removed or updated, and add the path to the list of paths we have checked.
          val newIndex = updateBindingIfOutOfDate(currentIndex, binding.path.get, binding.lastModified)

          val bindingUpdated = newIndex ne currentIndex

          currentIndex = newIndex
          currentCheckedPaths += binding.path.get

          // If the binding was updated, try again but only once
          if (bindingUpdated)
            BindingIndex.findMostSpecificBinding(currentIndex, qName, attsSeq) map (_._1)
          else
            Some(binding)

        case Some((binding, _)) =>
          // Binding is either not by name, or it's by name and we don't need to check that it is up-to-date
          Some(binding)
        case _ =>
          None
      }
    }

    def findFromAutomaticBinding(implicit indentedLogger: IndentedLogger): Option[AbstractBinding] =
      findBindingPathByName(uri, localname) match {
        case Some((path, true)) =>
          val (newIndex, newBindings) = extractAndIndexFromPaths(currentIndex, List(path))

          currentIndex = newIndex
          currentCheckedPaths += path

          newBindings.headOption
        case Some((path, false)) =>
          currentCheckedPaths += path
          None
        case None =>
          None
      }

    // Since type selectors (i.e. by element name) have the lowest priority, AND attribute selectors are loaded
    // preemptively, we can first check the bindings already loaded, and only after that check automatic bindings,
    // as those are guaranteed to have only type selectors, and so are of the lowest priority.

    val result = findFromAlreadyLoadedBindings orElse findFromAutomaticBinding

    (currentIndex, currentCheckedPaths, result)
  }

  def extractAndIndexFromElements(
    index    : BindingIndex[IndexableBinding],
    elements : Iterable[Element]
  ): (BindingIndex[IndexableBinding], List[AbstractBinding]) = {

    val newBindings =
      for {
        elem       <- elements.to(List)
        newBinding <- extractXBLBindings(None, -1, elem)
      } yield
        newBinding

    val currentIndex =
      indexBindings(index, newBindings)

    (currentIndex, newBindings)
  }

  def bindingPathByName(prefix: String, localname: String) =
    s"/xbl/$prefix/$localname/$localname.xbl"

  private def pathsForQNames(
    qNames        : Set[QName],
    nsUriToPrefix : Map[String, String]
  )(implicit
    indentedLogger: IndentedLogger
  ): (Set[String], Set[String]) =
    qNames flatMap { qName =>
      findBindingPathByNameUseMappings(nsUriToPrefix, qName.namespace.uri, qName.localName)
    } partition (_._2) match {
      case (found, notFound) => (found map (_._1), notFound map (_._1))
    }

  def collectBindingAssets(
    bindings      : Iterable[AbstractBinding]
  )(implicit
    indentedLogger: IndentedLogger
  ): Map[QName, XFormsAssets] = {

    debug("collecting assets baselines")

    bindings
      .flatMap { binding =>
        binding.commonBinding.directName.map { directName =>
          directName ->
          XFormsAssets(
            binding.assets.css .collect{ case e: HeadElement.Reference => AssetPath(e.src, None) },
            binding.assets.js  .collect{ case e: HeadElement.Reference => AssetPath(e.src, None) },
          )
        }
      }
      .toMap
  }

  private def extractAndIndexFromPaths(
    index         : BindingIndex[IndexableBinding],
    paths         : IterableOnce[String]
  )(implicit
    indentedLogger: IndentedLogger
  ): (BindingIndex[IndexableBinding], List[AbstractBinding]) = {

    val newBindings =
      for {
        xblPath              <- paths.iterator.toList
        (elem, lastModified) = readXBLResource(xblPath)
        newBinding           <- extractXBLBindings(Some(xblPath), lastModified, elem)
      } yield
        newBinding

    val currentIndex =
      indexBindings(index, newBindings)

    (currentIndex, newBindings)
  }

  private def indexBindings(index: BindingIndex[IndexableBinding], bindings: List[AbstractBinding]) = {
    var currentIndex = index

    bindings foreach { binding =>
      currentIndex = BindingIndex.indexBinding(currentIndex, binding)
    }

    currentIndex
  }

  private def updateBindingIfOutOfDate(
    index         : BindingIndex[IndexableBinding],
    path          : String,
    lastModified  : Long
  )(implicit
    indentedLogger: IndentedLogger
  ) = {

    var currentIndex = index

    val resourceLastModified = lastModifiedByPath(path)

    val hasNoDate   = resourceLastModified <= 0
    val isOutOfDate = resourceLastModified > lastModified

    if (hasNoDate || isOutOfDate)
      currentIndex = BindingIndex.deIndexBindingByPath(currentIndex, path)

    if (isOutOfDate)
      currentIndex = extractAndIndexFromPaths(currentIndex, List(path))._1

    currentIndex
  }

  // Missing or out of date bindings are removed, and out-of-date bindings are reloaded
  private def updateOutOfDateBindings(
    index         : BindingIndex[IndexableBinding],
    paths         : Set[String]
  )(implicit
    indentedLogger: IndentedLogger
  ): BindingIndex[IndexableBinding] = {

    var currentIndex = index

    for {
      path    <- paths
      binding <- BindingIndex.distinctBindingsForPath(currentIndex, path)
    } locally {
      currentIndex = updateBindingIfOutOfDate(currentIndex, path, binding.lastModified)
    }

    currentIndex
  }

  // E.g. `http://orbeon.org/oxf/xml/form-runner -> orbeon`
  // NOTE: The caching is not optimal, as we need, to cache, to iterate all properties with propertiesStartsWith!
  // Would need a way to cache against PropertySet.
  private def readURLMappingsCacheAgainstProperty: Map[String, String] = {

    val propertySet = getPropertySet

    val mappingProperties = propertySet.propertiesStartsWith(XBLMappingPropertyPrefix, matchWildcards = false)

    def evaluate(property: Property) = (
      for {
        propertyName <- mappingProperties
        uri          = propertySet.getNonBlankString(propertyName) getOrElse ""
        prefix       = propertyName.substring(XBLMappingPropertyPrefix.length)
      } yield
        uri -> prefix
    ) toMap

    // Associate result with the property so it won't be computed until properties are reloaded
    mappingProperties.headOption match {
      case Some(firstPropertyName) =>
        propertySet.getPropertyOrThrow(firstPropertyName).associatedValue(evaluate)
      case None =>
        Map()
    }
  }

  // E.g. `fr:tabview -> oxf:/xbl/orbeon/tabview/tabview.xbl`
  private def findBindingPathByNameUseMappings(
    nsUriToPrefix : Map[String, String],
    uri           : String,
    localname     : String
  )(implicit
    indentedLogger: IndentedLogger
  ) =
    nsUriToPrefix.get(uri) map { prefix =>
      val path = bindingPathByName(prefix, localname)
      (path, existsByPath(path))
    }

  private def findBindingPathByName(uri: String, localname: String)(implicit indentedLogger: IndentedLogger) =
    findBindingPathByNameUseMappings(readURLMappingsCacheAgainstProperty, uri, localname)

  private def readXBLResource(xblPath: String)(implicit indentedLogger: IndentedLogger): (Element, Long) = {

    val lastModified = lastModifiedByPath(xblPath)

    (
      Transform.readDocumentOrSimplifiedStylesheet(
        Transform.InlineReadDocument(
          xblPath,
          contentAsOrbeonDom(xblPath),
          lastModified
        )
      ).getRootElement,
      lastModified
    )
  }

  private def extractXBLBindings(
    path         : Option[String],
    lastModified : Long,
    xblElement   : Element
  ): List[AbstractBinding] = {

    // Extract xbl:xbl/xbl:script
    // TODO: should do this differently, in order to include only the scripts and assets actually used
    val scriptElements = xblElement.elements(XBL_SCRIPT_QNAME) map HeadElementBuilder.apply

    // Create binding for all xbl:binding[@element]
    for {
      bindingElement <- xblElement.elements(XBL_BINDING_QNAME).toList
      _              <- bindingElement.attributeValueOpt(ELEMENT_QNAME)
    } yield
      AbstractBinding.fromBindingElement(bindingElement, path, lastModified, scriptElements)
  }

  private def convertAttributes(atts: Attributes) =
    for (i <- 0 until atts.getLength)
      yield QName(atts.getLocalName(i), "", atts.getURI(i)) -> atts.getValue(i)
}

object BindingLoader extends BindingLoader {

  import org.orbeon.oxf.resources.ResourceManagerWrapper

  private val rm = ResourceManagerWrapper.instance

  def getPropertySet = CoreCrossPlatformSupport.properties

  def lastModifiedByPath(path: String)(implicit indentedLogger: IndentedLogger): Long = {
    debug("checking last modified", List("path" -> path))
    rm.lastModified(path, true)
  }

  def existsByPath(path: String)(implicit indentedLogger: IndentedLogger): Boolean = {
    debug("checking existence", List("path" -> path))
    rm.exists(path)
  }

  def contentAsOrbeonDom(path: String)(implicit indentedLogger: IndentedLogger): Document = {
    debug("reading content", List("path" -> path))
    rm.getContentAsOrbeonDom(path, ParserConfiguration.XIncludeOnly, false)
  }
}
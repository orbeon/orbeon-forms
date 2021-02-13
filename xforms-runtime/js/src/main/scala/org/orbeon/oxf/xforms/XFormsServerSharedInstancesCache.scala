package org.orbeon.oxf.xforms

import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.xforms.model.InstanceCaching


// TODO: This implementation doesn't yet handle `timeToLive`.
object XFormsServerSharedInstancesCache extends XFormsServerSharedInstancesCacheTrait {

  import Private._

  private var cache = Map[CacheKeyType, DocumentNodeInfoType]()

  // Try to find instance content in the cache but do not attempt to load it if not found
  def findContent(
    instanceCaching  : InstanceCaching,
    readonly         : Boolean,
    exposeXPathTypes : Boolean)(implicit
    indentedLogger   : IndentedLogger
  ): Option[DocumentNodeInfoType] =
    cache.get(createCacheKey(instanceCaching))

  def findContentOrLoad(
    instanceCaching  : InstanceCaching,
    readonly         : Boolean,
    exposeXPathTypes : Boolean,
    loadInstance     : InstanceLoader)(implicit
    indentedLogger   : IndentedLogger
  ): DocumentNodeInfoType = {

    val cacheKey = createCacheKey(instanceCaching)

    cache.getOrElse(cacheKey, {
      val result = loadInstance(instanceCaching.pathOrAbsoluteURI, instanceCaching.handleXInclude)
      cache += cacheKey -> result
      result
    })
  }

  def remove(
    instanceSourceURI : String,
    requestBodyHash   : String,
    handleXInclude    : Boolean)(implicit
    indentedLogger    : IndentedLogger
  ): Unit =
    cache -= createCacheKey(instanceSourceURI, handleXInclude, Option(requestBodyHash))

  def removeAll(implicit indentedLogger: IndentedLogger): Unit =
    cache = Map.empty

  def sideLoad(
    instanceCaching : InstanceCaching,
    doc             : DocumentNodeInfoType
  ): Unit =
    cache += createCacheKey(instanceCaching) -> doc

  private object Private {

    type CacheKeyType = String

    // Make key also depend on handleXInclude and on request body hash if present
    def createCacheKey(instanceCaching: InstanceCaching): CacheKeyType =
      createCacheKey(instanceCaching.pathOrAbsoluteURI, instanceCaching.handleXInclude, instanceCaching.requestBodyHash)

    def createCacheKey(sourceURI: String, handleXInclude: Boolean, requestBodyHash: Option[String]): CacheKeyType =
      sourceURI + "|" + handleXInclude.toString + (requestBodyHash map ('|' + _) getOrElse "")
  }
}

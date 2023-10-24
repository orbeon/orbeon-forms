package org.orbeon.oxf.xforms

import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{IndentedLogger, PathUtils}
import org.orbeon.oxf.xforms.model.InstanceCaching
import org.orbeon.oxf.xforms.model.XFormsInstance._


// TODO: This implementation doesn't yet handle `timeToLive`.
object XFormsServerSharedInstancesCache extends XFormsServerSharedInstancesCacheTrait {

  import Private._

  def sideLoad(
    instanceCaching: InstanceCaching,
    instanceContent: DocumentNodeInfoType
  ): Unit =
    cache += createCacheKey(instanceCaching) -> instanceContent

  protected def add(
    instanceCaching: InstanceCaching,
    instanceContent: InstanceContent,
    timeToLive     : Long
  )(implicit
    indentedLogger : IndentedLogger
  ): Unit = {
    debug("adding instance", instanceCaching.debugPairs)
    cache += createCacheKey(instanceCaching) -> instanceContent.documentInfo
  }

  protected def find(
    instanceCaching: InstanceCaching
  )(implicit
    logger: IndentedLogger
  ): Option[DocumentNodeInfoType] =
    cache.get(createCacheKey(instanceCaching))

  def remove(
    instanceSourceURI : String,
    requestBodyHash   : Option[String],
    handleXInclude    : Boolean,
    ignoreQueryString : Boolean
  )(implicit
    indentedLogger    : IndentedLogger
  ): Unit =
    if (ignoreQueryString) {

      def extractUriOpt(key: CacheKeyType) =
        key.splitTo[List]("|").headOption

      val uriNoQueryString = PathUtils.removeQueryString(instanceSourceURI)

      cache.keysIterator collect { case key
        if extractUriOpt(key).map(PathUtils.removeQueryString).contains(uriNoQueryString) => key
      } foreach { key =>
        cache -= key
      }

    } else {
      cache -= createCacheKey(instanceSourceURI, handleXInclude, requestBodyHash)
    }

  def removeAll(implicit indentedLogger: IndentedLogger): Unit =
    cache = Map.empty

  private object Private {

    type CacheKeyType = String

    var cache = Map[CacheKeyType, DocumentNodeInfoType]()

    // Make key also depend on handleXInclude and on request body hash if present
    def createCacheKey(instanceCaching: InstanceCaching): CacheKeyType =
      createCacheKey(instanceCaching.pathOrAbsoluteURI, instanceCaching.handleXInclude, instanceCaching.requestBodyHash)

    def createCacheKey(sourceURI: String, handleXInclude: Boolean, requestBodyHash: Option[String]): CacheKeyType =
      sourceURI + "|" + handleXInclude.toString + (requestBodyHash map ('|' + _) getOrElse "")
  }
}

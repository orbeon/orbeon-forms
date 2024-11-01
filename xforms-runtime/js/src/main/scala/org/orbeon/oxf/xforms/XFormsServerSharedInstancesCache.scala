package org.orbeon.oxf.xforms

import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.{IndentedLogger, PathUtils}
import org.orbeon.oxf.xforms.model.InstanceCaching
import org.orbeon.oxf.xforms.model.XFormsInstance.*


// TODO: This implementation doesn't yet handle `timeToLive`.
object XFormsServerSharedInstancesCache extends XFormsServerSharedInstancesCacheTrait {

  import Private.*

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
    handleXInclude    : Option[Boolean],
    ignoreQueryString : Boolean
  )(implicit
    indentedLogger    : IndentedLogger
  ): Unit = {
    debug(
      "removing instance",
      List(
        "instanceSourceURI" -> instanceSourceURI,
        "handleXInclude"    -> handleXInclude.toString,
        "ignoreQueryString" -> ignoreQueryString.toString
      )
    )

    val cacheKeysIt =
      cache
        .keysIterator

    def matchesXInclude(v: Boolean): Boolean =
      handleXInclude.isEmpty || handleXInclude.contains(v)

    if (ignoreQueryString) {
      val uriNoQueryString = PathUtils.removeQueryString(instanceSourceURI)
      cacheKeysIt
        .collect {
          case key @ SharedInstanceCacheKey(uri, xinclude, _)
            if PathUtils.removeQueryString(uri) == uriNoQueryString && matchesXInclude(xinclude) => key
        }
        .foreach(cache -= _)
    } else {
      cacheKeysIt
        .collect {
          case key @ SharedInstanceCacheKey(`instanceSourceURI`, xinclude, _)
            if matchesXInclude(xinclude) => key
        }
        .foreach(cache -= _)
    }
  }

  def removeAll(implicit indentedLogger: IndentedLogger): Unit =
    cache = Map.empty

  private object Private {

    case class SharedInstanceCacheKey(
      sourceURI      : String,
      handleXInclude : Boolean,
      requestBodyHash: Option[String]
    )

    var cache = Map[SharedInstanceCacheKey, DocumentNodeInfoType]()

    // Make key also depend on handleXInclude and on request body hash if present
    def createCacheKey(instanceCaching: InstanceCaching): SharedInstanceCacheKey =
      SharedInstanceCacheKey(instanceCaching.pathOrAbsoluteURI, instanceCaching.handleXInclude, instanceCaching.requestBodyHash)
  }
}
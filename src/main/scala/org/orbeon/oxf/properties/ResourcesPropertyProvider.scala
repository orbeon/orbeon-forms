package org.orbeon.oxf.properties

import cats.Eval
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.pipeline.InitUtils.withNewPipelineContext
import org.orbeon.oxf.processor.{DOMSerializer, Processor, ProcessorImpl}
import org.orbeon.oxf.util.PipelineUtils
import org.orbeon.properties.api

import java.util as ju
import java.util.concurrent.Semaphore
import scala.jdk.OptionConverters.*
import scala.util.chaining.*


class ResourcesPropertyProvider extends api.PropertyProvider {

  import ResourcesPropertyProvider.*

  @volatile
  private var lastCheckedTimestampOpt: Option[Long] = None

  private val semaphore = new Semaphore(1)

  private def readUnconditionally(semaphore: java.util.concurrent.Semaphore): Option[(api.PropertyDefinitions, api.ETag)] =
    ResourcesPropertyProvider.withAcquiredResourceOrNone(semaphore) {
      withNewPipelineContext("ResourcesPropertyProvider.readUnconditionally()") { pipelineContext =>

        val (urlGenerator, domSerializer) = processors.value

        urlGenerator.reset(pipelineContext)
        domSerializer.reset(pipelineContext)

        PropertyLoader.logger.debug("Reloading properties unconditionally.")

        val document = domSerializer.runGetDocument(pipelineContext)
        if (document == null || document.content.isEmpty)
          throw new OXFException("Failure to initialize Orbeon Forms properties")

        // Q: Do we need to reset again?
        urlGenerator.reset(pipelineContext)
        domSerializer.reset(pipelineContext)

        val lastModified = domSerializer.findInputLastModified(pipelineContext)

        PropertyStore.parseToPropertyDefinitions(document) -> lastModified.toString
      }
    }

  private def hasEnoughTimeElapsed(currentTime: Long): Boolean =
    lastCheckedTimestampOpt match {
      case Some(lastCheckedTimestamp) if currentTime <= lastCheckedTimestamp + ResourcesPropertyProvider.ReloadDelay =>
        false
      case _ =>
        true
    }

  private def findResourceLastModified(semaphore: java.util.concurrent.Semaphore): Option[Long] =
    ResourcesPropertyProvider.withAcquiredResourceOrNone(semaphore) {
      withNewPipelineContext("ResourcesPropertyProvider.findResourceLastModified()") { pipelineContext =>

        val (urlGenerator, domSerializer) = processors.value

        urlGenerator.reset(pipelineContext)
        domSerializer.reset(pipelineContext)

        domSerializer.findInputLastModified(pipelineContext)
      }
    }

  def getPropertiesIfNeeded(
    cacheKey   : ju.Optional[api.CacheKey],
    eTag       : ju.Optional[api.ETag],
    request    : ju.Optional[api.Request],
    credentials: ju.Optional[api.Credentials],
    session    : ju.Optional[api.Session],
    extension  : ju.Map[String, Any],
  ): ju.Optional[api.PropertyDefinitionsWithETag] = {

    val currentTime = System.currentTimeMillis

    val mustUpdate =
      eTag.toScala match {
        case None =>
          // No ETag provided, must update
          true
        case Some(_) if ! hasEnoughTimeElapsed(currentTime) =>
          // ETag provided, but not enough time has elapsed since the last check
          return ju.Optional.empty()
        case Some(eTag) =>
          // ETag provided, check, it against the resource
          findResourceLastModified(semaphore) match {
            case None =>
              // Resource is locked, skip checking
              return ju.Optional.empty()
            case Some(resourceLastModified) =>
              resourceLastModified.toString != eTag
          }
      }

    // Remember the last time we checked so that `hasEnoughTimeElapsed()` can work
    lastCheckedTimestampOpt = Some(currentTime)

    if (mustUpdate)
      readUnconditionally(semaphore).map { case (newPropertyDefinitions, newETag) =>
        new api.PropertyDefinitionsWithETag {
          val getProperties: api.PropertyDefinitions = newPropertyDefinitions
          val getETag: api.ETag = newETag
        }
      }
      .toJava
    else
      ju.Optional.empty()
  }
}

object ResourcesPropertyProvider {

  private val DefaultPropertiesUri = "oxf:/properties.xml"
  private val ReloadDelay          = 5 * 1000 // TODO: `Duration`

  private var propertiesURI = DefaultPropertiesUri

  private def newEval: Eval[(Processor, DOMSerializer)] =
    Eval.later {
      val urlGenerator =
        PipelineUtils.createURLGenerator(ResourcesPropertyProvider.propertiesURI, true) // enable XInclude too
      val domSerializer =
        (new DOMSerializer)
          .tap(PipelineUtils.connect(urlGenerator, ProcessorImpl.OUTPUT_DATA, _, ProcessorImpl.INPUT_DATA))
      (urlGenerator, domSerializer)
    }

  private var processors: Eval[(Processor, DOMSerializer)] = newEval

  // Set URI of the resource we will read the properties from and initialize them
  def configure(propertiesURI: String): Unit = {
    this.propertiesURI = propertiesURI
    processors = newEval
  }

  private def withAcquiredResourceOrNone[T](lock: java.util.concurrent.Semaphore)(thunk: => T): Option[T] =
    if (lock.tryAcquire()) {
      try
        Some(thunk)
      finally
        lock.release()
    } else {
      None
    }
}
package org.orbeon.oxf.xforms

import org.orbeon.oxf.util.CoreCrossPlatformSupport

import scala.jdk.CollectionConverters._


object XFormsGlobalProperties {

  val PropertyPrefix = "oxf.xforms."

  private def propertySet =
    CoreCrossPlatformSupport.properties

  def getDebugLogging: collection.Set[String] =
    Option(propertySet.getNmtokens(PropertyPrefix + "logging.debug")) map (_.asScala) getOrElse Set.empty

  def getErrorLogging: collection.Set[String] =
    Option(propertySet.getNmtokens(PropertyPrefix + "logging.error")) map (_.asScala) getOrElse Set.empty

  def isCacheDocument           : Boolean = propertySet.getBoolean("cache.document",                                      default = true)
  def isGZIPState               : Boolean = propertySet.getBoolean(PropertyPrefix + "gzip-state",                         default = true)
  def isHostLanguageAVTs        : Boolean = propertySet.getBoolean(PropertyPrefix + "host-language-avts",                 default = false)
  def isMinimalResources        : Boolean = propertySet.getBoolean(PropertyPrefix + "minimal-resources",                  default = true)
  def isCombinedResources       : Boolean = propertySet.getBoolean(PropertyPrefix + "combine-resources",                  default = true)
  def isReplication             : Boolean = propertySet.getBoolean(PropertyPrefix + "replication",                        default = false)
  def getDebugLogXPathAnalysis  : Boolean = propertySet.getBoolean(PropertyPrefix + "debug.log-xpath-analysis",           default = false)
  def isRequestStats            : Boolean = propertySet.getBoolean(PropertyPrefix + "debug.log-request-stats",            default = false)
  def getAjaxTimeout            : Long    = propertySet.getInteger(PropertyPrefix + "delay-before-ajax-timeout",          default = 30000).toLong
  def uploadXFormsAccessTimeout : Long    = propertySet.getInteger(PropertyPrefix + "upload.delay-before-xforms-timeout", default = 45000).toLong
  def getRetryDelayIncrement    : Int     = propertySet.getInteger(PropertyPrefix + "retry.delay-increment",              default = 5000)
  def getRetryMaxDelay          : Int     = propertySet.getInteger(PropertyPrefix + "retry.max-delay",                    default = 30000)
  def isKeepLocation            : Boolean = propertySet.getString (PropertyPrefix + "location-mode", "none") != "none"
}
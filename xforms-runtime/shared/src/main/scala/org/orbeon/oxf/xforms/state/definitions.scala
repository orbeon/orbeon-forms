package org.orbeon.oxf.xforms.state

import java.util.concurrent.locks.Lock

import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.model.{InstanceCaching, XFormsInstance}
import org.orbeon.xforms.XFormsId


sealed trait LockResponse
object LockResponse {
  case class  Success(lock: Lock)           extends LockResponse
  case object Busy                          extends LockResponse
  case object Timeout                       extends LockResponse
  case class  Failure(exception: Throwable) extends LockResponse // `InterruptedException | SessionExpiredException`
}

// Minimal immutable representation of a serialized control
case class ControlState(
  effectiveId : String,
  visited     : Boolean,
  keyValues   : Map[String, String]
)

// Minimal immutable representation of a serialized instance
// If there is caching information, don't include the actual content
case class InstanceState(
  effectiveId      : String,
  modelEffectiveId : String,
  cachingOrContent : InstanceCaching Either String,
  readonly         : Boolean,
  modified         : Boolean,
  valid            : Boolean
) {

  def this(instance: XFormsInstance) =
    this(
      instance.getEffectiveId,
      instance.parent.getEffectiveId,
      instance.instanceCaching.toLeft(instance.contentAsString),
      instance.readonly,
      instance.modified,
      instance.valid)
}

case class InstancesControls(instances: List[InstanceState], controls: Map[String, ControlState])

case class RequestParameters(
  uuid                         : String,
  sequenceOpt                  : Option[Long],
  encodedClientStaticStateOpt  : Option[String],
  encodedClientDynamicStateOpt : Option[String]
) {
  require(
    (encodedClientStaticStateOpt.isDefined && encodedClientDynamicStateOpt.isDefined) ||
      (encodedClientStaticStateOpt.isEmpty && encodedClientDynamicStateOpt.isEmpty)
  )
}

trait XFormsStateLifecycle {

  def getClientEncodedStaticState (containingDocument: XFormsContainingDocument): Option[String]
  def getClientEncodedDynamicState(containingDocument: XFormsContainingDocument): Option[String]

  def afterInitialResponse(
    containingDocument   : XFormsContainingDocument,
    disableDocumentCache : Boolean
  ): Unit

  def findOrRestoreDocument(
    parameters           : RequestParameters,
    disableUpdates       : Boolean,
    disableDocumentCache : Boolean
  ): XFormsContainingDocument

  def acquireDocumentLock (uuid: String, timeout: Long): LockResponse
  def releaseDocumentLock (lock: Lock): Unit

  def beforeUpdate        (parameters: RequestParameters, disableDocumentCache: Boolean): XFormsContainingDocument
  def beforeUpdateResponse(containingDocument: XFormsContainingDocument, ignoreSequence: Boolean): Unit
  def afterUpdateResponse (containingDocument: XFormsContainingDocument): Unit
  def afterUpdate         (containingDocument: XFormsContainingDocument, keepDocument: Boolean, disableDocumentCache: Boolean): Unit

  def createInitialDocumentFromStore(parameters: RequestParameters): XFormsContainingDocument

  def onAddedToCache      (uuid: String): Unit
  def onRemovedFromCache  (uuid: String): Unit
  def onEvictedFromCache  (containingDocument: XFormsContainingDocument): Unit
}
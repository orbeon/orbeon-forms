package org.orbeon.oxf.xforms.state

import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.model.XFormsInstance.InstanceDocument
import org.orbeon.oxf.xforms.model.{InstanceCaching, XFormsInstance}

import java.util.concurrent.locks.Lock


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
  cachingOrDocument: InstanceCaching Either InstanceDocument,
  readonly         : Boolean,
  modified         : Boolean,
  valid            : Boolean
) {

  def this(instance: XFormsInstance) =
    this(
      instance.effectiveId,
      instance.parent.effectiveId,
      instance.instanceCaching.toLeft(instance.contentAsInstanceDocument),
      instance.readonly,
      instance.modified,
      instance.valid
    )
}

case class InstancesControls(instances: List[InstanceState], controls: Map[String, ControlState])

case class RequestParameters(
  uuid                         : String,
  sequenceOpt                  : Option[Long],
  submissionIdOpt              : Option[String],
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
  )(implicit
    indentedLogger       : IndentedLogger
  ): Unit

  def findOrRestoreDocument(
    parameters           : RequestParameters,
    disableUpdates       : Boolean,
    disableDocumentCache : Boolean
  ): Option[XFormsContainingDocument]

  def acquireDocumentLock (uuid: String, timeout: Long): LockResponse
  def releaseDocumentLock (lock: Lock): Unit

  def beforeUpdate        (parameters: RequestParameters, disableDocumentCache: Boolean): Option[XFormsContainingDocument]
  def beforeUpdateResponse(containingDocument: XFormsContainingDocument, ignoreSequence: Boolean): Unit
  def afterUpdateResponse (containingDocument: XFormsContainingDocument): Unit
  def afterUpdate         (containingDocument: XFormsContainingDocument, keepDocument: Boolean, disableDocumentCache: Boolean): Unit

  def createInitialDocumentFromStore(parameters: RequestParameters): Option[XFormsContainingDocument]

  def onAddedToCache      (uuid: String): Unit
  def onRemovedFromCache  (uuid: String): Unit
  def onEvictedFromCache  (containingDocument: XFormsContainingDocument): Unit
}
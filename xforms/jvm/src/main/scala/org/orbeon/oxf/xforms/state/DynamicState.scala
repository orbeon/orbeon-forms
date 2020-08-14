/**
 *  Copyright (C) 2012 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.state

import collection.JavaConverters._
import sbinary.Operations._
import XFormsOperations._
import XFormsProtocols._
import org.orbeon.oxf.util.URLRewriterUtils.PathMatcher
import org.orbeon.oxf.xforms._
import control.Controls.ControlsIterator
import org.orbeon.dom
import org.orbeon.oxf.xml.{EncodeDecode, SAXStore, TransformerUtils}
import org.orbeon.dom.{Document, DocumentFactory, Element}
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsControl}
import org.orbeon.oxf.xforms.model.{InstanceCaching, XFormsInstance}
import org.orbeon.xforms.XFormsId

// Immutable representation of the dynamic state
case class DynamicState(
  uuid                : String,
  sequence            : Long,
  deploymentType      : Option[String],
  requestMethod       : Option[HttpMethod],
  requestContextPath  : Option[String],
  requestPath         : Option[String],
  requestHeaders      : List[(String, List[String])],
  requestParameters   : List[(String, List[String])],
  containerType       : Option[String],
  containerNamespace  : Option[String],
  pathMatchers        : Seq[Byte],
  focusedControl      : Option[String],
  pendingUploads      : Seq[Byte],
  lastAjaxResponse    : Seq[Byte],
  instances           : Seq[Byte],
  controls            : Seq[Byte],
  initialClientScript : Option[String],
  delayedEvents       : Seq[Byte]
) {
  // Decode individual bits
  def decodePathMatchers           = fromByteSeq[List[PathMatcher]](pathMatchers)
  def decodePendingUploads         = fromByteSeq[Set[String]](pendingUploads)
  def decodeLastAjaxResponse       = fromByteSeq[Option[SAXStore]](lastAjaxResponse)
  def decodeInstances              = fromByteSeq[List[InstanceState]](instances)
  def decodeControls               = fromByteSeq[List[ControlState]](controls)
  def decodeDelayedEvents          = fromByteSeq[List[DelayedEvent]](delayedEvents)

  // For Java callers
  def decodeRequestPathJava        = requestPath.orNull
  def decodeContainerTypeJava      = containerType.orNull
  def decodeContainerNamespaceJava = containerNamespace.orNull
  def decodePathMatchersJava       = decodePathMatchers.asJava
  def decodeFocusedControlJava     = focusedControl.orNull
  def decodePendingUploadsJava     = decodePendingUploads.asJava
  def decodeLastAjaxResponseJava   = decodeLastAjaxResponse.orNull
  def decodeInstancesJava          = decodeInstances.asJava
  def decodeControlsJava           = decodeControls.asJava

  def decodeInstancesControls      = InstancesControls(decodeInstances, decodeControls map (c => (c.effectiveId, c)) toMap)

  // Encode to a string representation
  def encodeToString(compress: Boolean, isForceEncryption: Boolean): String =
    EncodeDecode.encodeBytes(
      toByteArray(this),
      compress,
      isForceEncryption
    )

  // Encode to an XML representation (as of 2012-02-05, used only by unit tests)
  def toXML = {

    val document = dom.Document()
    val rootElement = document.addElement("dynamic-state")

    // Add UUIDs
    rootElement.addAttribute("uuid", uuid)
    rootElement.addAttribute("sequence", sequence.toString)

    // Add request information
    deploymentType foreach { value =>
      rootElement.addAttribute("deployment-type", value)
    }
    requestContextPath foreach { value =>
      rootElement.addAttribute("request-context-path", value)
    }
    requestPath foreach { value =>
      rootElement.addAttribute("request-path", value)
    }
    containerType foreach { value =>
      rootElement.addAttribute("container-type", value)
    }
    deploymentType foreach { value =>
      rootElement.addAttribute("container-namespace", value)
    }

    // Add upload information
    if (decodePendingUploads.nonEmpty)
      rootElement.addAttribute("pending-uploads", decodePendingUploads mkString " ")

    // Serialize instances
    locally {
      val instanceStates = decodeInstances
      if (instanceStates.nonEmpty) {
        val instancesElement = rootElement.addElement("instances")

        // Encode to an XML representation (as of 2012-02-05, used only by unit tests)
        def instanceToXML(instanceState: InstanceState): Element = {
          val instanceElement = DocumentFactory.createElement("instance")

          def att(name: String,  value: String): Unit = instanceElement.addAttribute(name, value)

          att("id", XFormsId.getStaticIdFromId(instanceState.effectiveId))
          att("model-id", instanceState.modelEffectiveId)

          if (instanceState.readonly) att("readonly", "true")

          instanceState.cachingOrContent match {
            case Left(caching)  => caching.writeAttributes(att)
            case Right(content) => instanceElement.addText(content)

          }

          instanceElement
        }

        instanceStates foreach (instanceState => instancesElement.add(instanceToXML(instanceState)))
      }
    }

    // Serialize controls
    locally {
      val controls = decodeControls
      if (controls.nonEmpty) {
        val controlsElement = rootElement.addElement("controls")
        controls foreach {
          case ControlState(effectiveId, visited, keyValues) =>
            val controlElement = controlsElement.addElement("control")
            controlElement.addAttribute("effective-id", effectiveId)
            if (visited)
              controlElement.addAttribute("visited", "true")
            for ((k, v) <- keyValues)
              controlElement.addAttribute(k, v)
        }
      }
    }

    // Template and Ajax response
    List(("response", decodeLastAjaxResponse)) collect {
      case (elementName, Some(saxStore)) =>
        val templateElement = rootElement.addElement(elementName)
        val document = TransformerUtils.saxStoreToDom4jDocument(saxStore)
        templateElement.add(document.getRootElement.detach())
    }

    document
  }

  private def debug(): Unit = {
    val bytes = toByteSeq(this)
    println("  size: " + bytes.size)
    println("   versionedPathMatchers: " + pathMatchers.size)
    println("   pendingUploads: " + pendingUploads.size)
    println("   instances: " + instances.size)
    println("   controls: " + controls.size)
    println("   lastAjaxResponse: " + lastAjaxResponse.size)

    val decodedParts = Array(
      decodePathMatchersJava.toArray,
      decodeFocusedControlJava,
      decodePendingUploadsJava,
      decodeControlsJava,
      decodeInstancesJava.toArray,
      decodeLastAjaxResponseJava
    )

    val deserialized = fromByteSeq[DynamicState](bytes)
    assert(this == deserialized)
  }
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

object DynamicState {

  // Create a DynamicState from a document
  def apply(document: XFormsContainingDocument): DynamicState =
    apply(document, document.getControls.getCurrentControlTree.rootOpt)

  // Create a DynamicState from a control
  def apply(document: XFormsContainingDocument, startOpt: Option[XFormsControl]): DynamicState = {

    val startContainerOpt = startOpt match {
      case Some(componentControl: XFormsComponentControl) => componentControl.nestedContainerOpt
      case Some(other)                                    => Some(other.container)
      case None                                           => Some(document)
    }

    // Serialize relevant controls that have data
    //
    // - Repeat, switch and dialogs controls serialize state (have been for a long time). The state of all the other
    //   controls is rebuilt from model data. This way we minimize the size of serialized controls. In the future,
    //   more information might be serialized.
    // - VisitableTrait controls serialize state if `visited == true`
    def controlsToSerialize =
      for {
        start        <- startOpt.toList
        control      <- ControlsIterator(start, includeSelf = false)
        if control.isRelevant
        controlState <- control.controlState
      } yield
        controlState

    // Create the dynamic state object. A snapshot of the state is taken, whereby mutable parts of the state, such
    // as instances, controls, HTML template, Ajax response, are first serialized to Seq[Byte]. A couple of notes:
    //
    // 1. We could serialize everything right away to a Seq[Byte] instead of a DynamicState instance, but in the
    //    scenario where the state is put in cache, then retrieved a bit later without having been pushed to
    //    external storage, this would be a waste.
    //
    // 2. Along the same lines, content that is already (conceptually) immutable, namely pathMatchers,
    //    annotatedTemplate, and lastAjaxResponse, could be serialized to bytes lazily.
    //
    // 3. In the cases where there is a large number of large instances or templates, parallel serialization might
    //    be something to experiment with.
    DynamicState(
      uuid                = document.getUUID,
      sequence            = document.getSequence,
      deploymentType      = Option(document.getDeploymentType) map (_.toString),
      requestMethod       = Option(document.getRequestMethod),
      requestContextPath  = Option(document.getRequestContextPath),
      requestPath         = Option(document.getRequestPath),
      requestHeaders      = document.getRequestHeaders mapValues (_.toList) toList, // mapValues ok because of toList
      requestParameters   = document.getRequestParameters mapValues (_.toList) toList, // mapValues ok because of toList
      containerType       = Option(document.getContainerType),
      containerNamespace  = Option(document.getContainerNamespace),
      pathMatchers        = toByteSeq(document.getVersionedPathMatchers.asScala.toList),
      focusedControl      = document.getControls.getFocusedControl map (_.getEffectiveId),
      pendingUploads      = toByteSeq(document.getPendingUploads.asScala.toSet),
      lastAjaxResponse    = toByteSeq(Option(document.getLastAjaxResponse)),
      instances           = toByteSeq(startContainerOpt.iterator flatMap (_.allModels) flatMap (_.instancesIterator) filter (_.mustSerialize) map (new InstanceState(_)) toList),
      controls            = toByteSeq(controlsToSerialize),
      initialClientScript = document.initialClientScript,
      delayedEvents       = toByteSeq(document.delayedEvents),
    )
  }

  // Create a DynamicState from an encoded string representation
  def apply(encoded: String): DynamicState = {
    val bytes = EncodeDecode.decodeBytes(encoded, false)
    fromByteArray[DynamicState](bytes)
  }

  // Encode the given document to a string representation
  def encodeDocumentToString(document: XFormsContainingDocument, compress: Boolean, isForceEncryption: Boolean): String =
    DynamicState(document).encodeToString(compress, isForceEncryption || document.isClientStateHandling)

  // For unit tests only
  def decodeDynamicStateString(dynamicState: String): Document =
    DynamicState(dynamicState).toXML
}
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

import org.orbeon.dom
import org.orbeon.dom.{Document, Element}
import org.orbeon.oxf.http.HttpMethod
import org.orbeon.oxf.util.{PathMatcher, SecureUtils}
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.control.{Controls, XFormsComponentControl, XFormsControl}
import org.orbeon.oxf.xforms.model.XFormsInstance
import org.orbeon.oxf.xforms.state.XFormsOperations._
import org.orbeon.oxf.xforms.state.XFormsProtocols._
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.SAXStoreBinaryFormat._
import org.orbeon.oxf.xml.SBinaryDefaultFormats._
import org.orbeon.oxf.xml.{EncodeDecode, SAXStore, TransformerUtils}
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.runtime.DelayedEvent
import sbinary.Operations._

import scala.collection.immutable


// Immutable representation of the dynamic state
case class DynamicState(
  uuid              : String,
  sequence          : Long,
  deploymentType    : Option[String],
  requestMethod     : Option[HttpMethod],
  requestContextPath: Option[String],
  requestPath       : Option[String],
  requestHeaders    : List[(String, List[String])],
  requestParameters : List[(String, List[String])],
  containerType     : Option[String],
  containerNamespace: Option[String],
  pathMatchers      : Seq[Byte],
  focusedControl    : Option[String],
  pendingUploads    : Seq[Byte],
  lastAjaxResponse  : Seq[Byte],
  instances         : Seq[Byte],
  controls          : Seq[Byte],
  initializationData: Option[(Option[String], String)],
  delayedEvents     : Seq[Byte]
) {
  // Decode individual bits
  def decodePathMatchers     : List[PathMatcher]           = fromByteSeq[List[PathMatcher]](pathMatchers)
  def decodePendingUploads   : Set[String]                 = fromByteSeq[Set[String]](pendingUploads)
  def decodeLastAjaxResponse : Option[SAXStore]            = fromByteSeq[Option[SAXStore]](lastAjaxResponse)
  def decodeInstances        : List[InstanceState]         = fromByteSeq[List[InstanceState]](instances)
  def decodeControls         : List[ControlState]          = fromByteSeq[List[ControlState]](controls)
  def decodeDelayedEvents    : immutable.Seq[DelayedEvent] = fromByteSeq[List[DelayedEvent]](delayedEvents)

  def decodeInstancesControls: InstancesControls = InstancesControls(decodeInstances, decodeControls map (c => (c.effectiveId, c)) toMap)

  // Encode to a string representation
  def encodeToString(compress: Boolean, isForceEncryption: Boolean): String =
    EncodeDecode.encodeBytes(
      toByteArray(this),
      compress,
      isForceEncryption,
      SecureUtils.KeyUsage.GeneralNoCheck
    )

  // Encode to an XML representation (as of 2012-02-05, used only by unit tests)
  def toXML: Document = {

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
          val instanceElement = dom.Element("instance")

          def att(name: String,  value: String): Unit = instanceElement.addAttribute(name, value)

          att("id", XFormsId.getStaticIdFromId(instanceState.effectiveId))
          att("model-id", instanceState.modelEffectiveId)

          if (instanceState.readonly) att("readonly", "true")

          instanceState.cachingOrDocument match {
            case Left(caching)   => caching.writeAttributes(att)
            case Right(document) => instanceElement.addText(XFormsInstance.serializeInstanceDocumentToString(document))
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
        val document = TransformerUtils.saxStoreToOrbeonDomDocument(saxStore)
        templateElement.add(document.getRootElement.detach())
    }

    document
  }

//  private def debug(): Unit = {
//    val bytes = toByteSeq(this)
//    println("  size: " + bytes.size)
//    println("   versionedPathMatchers: " + pathMatchers.size)
//    println("   pendingUploads: " + pendingUploads.size)
//    println("   instances: " + instances.size)
//    println("   controls: " + controls.size)
//    println("   lastAjaxResponse: " + lastAjaxResponse.size)
//
//    val decodedParts = Array(
//      decodePathMatchers.asJava.toArray,
//      decodeFocusedControlJava,
//      decodePendingUploadsJava,
//      decodeControlsJava,
//      decodeInstancesJava.toArray,
//      decodeLastAjaxResponseJava
//    )
//
//    val deserialized = fromByteSeq[DynamicState](bytes)
//    assert(this == deserialized)
//  }
}

object DynamicState {

  // Create a `DynamicState` from a document
  def apply(document: XFormsContainingDocument): DynamicState =
    apply(document, document.controls.getCurrentControlTree.rootOpt)

  // Create a DynamicState from a control. A snapshot of the state is taken, whereby mutable parts of the state, such
  // as instances, controls, HTML template, Ajax response, are first serialized to `Seq[Byte]`. A couple of notes:
  //
  // 1. We could serialize everything right away to a `Seq[Byte]` instead of a `DynamicState` instance, but in the
  //    scenario where the state is put in cache, then retrieved a bit later without having been pushed to
  //    external storage, this would be a waste.
  //
  // 2. Along the same lines, content that is already (conceptually) immutable, namely `pathMatchers`,
  //    `annotatedTemplate`, and `lastAjaxResponse`, could be serialized to bytes lazily.
  //
  // 3. In the cases where there is a large number of large instances or templates, parallel serialization might
  //    be something to experiment with.
  def apply(document: XFormsContainingDocument, startOpt: Option[XFormsControl]): DynamicState =
    DynamicState(
      uuid               = document.uuid,
      sequence           = document.sequence,
      deploymentType     = Option(document.getDeploymentType) map (_.toString),
      requestMethod      = Option(document.getRequestMethod),
      requestContextPath = Option(document.getRequestContextPath),
      requestPath        = Option(document.getRequestPath),
      requestHeaders     = document.getRequestHeaders mapValues (_.toList) toList, // mapValues ok because of `toList`
      requestParameters  = document.getRequestParameters mapValues (_.toList) toList, // mapValues ok because of `toList`
      containerType      = Option(document.getContainerType),
      containerNamespace = Option(document.getContainerNamespace),
      pathMatchers       = toByteSeq(document.getVersionedPathMatchers),
      focusedControl     = document.controls.getFocusedControl map (_.getEffectiveId),
      pendingUploads     = toByteSeq(document.getPendingUploads),
      lastAjaxResponse   = toByteSeq(document.lastAjaxResponse),
      instances          = toByteSeq(Controls.iterateInstancesToSerialize(findStartContainer(startOpt.toLeft(document)), XFormsInstance.mustSerialize).toList),
      controls           = toByteSeq(Controls.iterateControlsToSerialize(startOpt).toList),
      initializationData = document.getInitializationData,
      delayedEvents      = toByteSeq(document.delayedEvents),
    )

  // Create a `DynamicState` from an encoded string representation
  def apply(encoded: String): DynamicState = {
    val bytes = EncodeDecode.decodeBytes(encoded, forceEncryption = false, SecureUtils.KeyUsage.GeneralNoCheck)
    fromByteArray[DynamicState](bytes)
  }

  private def findStartContainer(controlOrDoc: XFormsControl Either XFormsContainingDocument): Option[XBLContainer] =
    controlOrDoc match {
      case Left(control: XFormsComponentControl) => control.nestedContainerOpt
      case Left(other)                           => Some(other.container)
      case Right(document)                       => Some(document)
    }

  // Encode the given document to a string representation
  def encodeDocumentToString(document: XFormsContainingDocument, compress: Boolean, isForceEncryption: Boolean): String =
    DynamicState(document).encodeToString(compress, isForceEncryption || document.isClientStateHandling)

  // For unit tests only
  //@XPathFunction
  def decodeDynamicStateString(dynamicState: String): Document =
    DynamicState(dynamicState).toXML
}
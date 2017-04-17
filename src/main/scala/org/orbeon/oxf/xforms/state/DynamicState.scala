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
import org.orbeon.oxf.xml.{SAXStore, TransformerUtils}
import org.orbeon.dom.{DocumentFactory, Element}
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsControl}

// Immutable representation of the dynamic state
case class DynamicState(
  uuid                : String,
  sequence            : Long,
  deploymentType      : Option[String],
  requestContextPath  : Option[String],
  requestPath         : Option[String],
  requestHeaders      : List[(String, List[String])],
  requestParameters   : List[(String, List[String])],
  containerType       : Option[String],
  containerNamespace  : Option[String],
  pathMatchers        : Seq[Byte],
  focusedControl      : Option[String],
  pendingUploads      : Seq[Byte],
  annotatedTemplate   : Option[Seq[Byte]],
  lastAjaxResponse    : Seq[Byte],
  instances           : Seq[Byte],
  controls            : Seq[Byte]
) {
  // Decode individual bits
  def decodePathMatchers           = fromByteSeq[List[PathMatcher]](pathMatchers)
  def decodePendingUploads         = fromByteSeq[Set[String]](pendingUploads)
  def decodeAnnotatedTemplate      = annotatedTemplate map (AnnotatedTemplate(_))
  def decodeLastAjaxResponse       = fromByteSeq[Option[SAXStore]](lastAjaxResponse)
  def decodeInstances              = fromByteSeq[List[InstanceState]](instances)
  def decodeControls               = fromByteSeq[List[ControlState]](controls)

  // For Java callers
  def decodeDeploymentTypeJava     = deploymentType.orNull
  def decodeRequestContextPathJava = requestContextPath.orNull
  def decodeRequestPathJava        = requestPath.orNull
  def decodeContainerTypeJava      = containerType.orNull
  def decodeContainerNamespaceJava = containerNamespace.orNull
  def decodePathMatchersJava       = decodePathMatchers.asJava
  def decodeFocusedControlJava     = focusedControl.orNull
  def decodePendingUploadsJava     = decodePendingUploads.asJava
  def decodeAnnotatedTemplateJava  = decodeAnnotatedTemplate.orNull
  def decodeLastAjaxResponseJava   = decodeLastAjaxResponse.orNull
  def decodeInstancesJava          = decodeInstances.asJava
  def decodeControlsJava           = decodeControls.asJava

  def decodeInstancesControls      = InstancesControls(decodeInstances, decodeControls map (c ⇒ (c.effectiveId, c)) toMap)

  // For tests only
  def copyUpdateSequence(sequence: Int) = copy(sequence = sequence)

  // Encode to a string representation
  def encodeToString(compress: Boolean, isForceEncryption: Boolean): String =
    XFormsUtils.encodeBytes(
      toByteArray(this),
      compress,
      isForceEncryption
    )

  // Encode to an XML representation (as of 2012-02-05, used only by unit tests)
  def toXML = {

    val document = DocumentFactory.createDocument
    val rootElement = document.addElement("dynamic-state")

    // Add UUIDs
    rootElement.addAttribute("uuid", uuid)
    rootElement.addAttribute("sequence", sequence.toString)

    // Add request information
    deploymentType foreach { value ⇒
      rootElement.addAttribute("deployment-type", value)
    }
    requestContextPath foreach { value ⇒
      rootElement.addAttribute("request-context-path", value)
    }
    requestPath foreach { value ⇒
      rootElement.addAttribute("request-path", value)
    }
    containerType foreach { value ⇒
      rootElement.addAttribute("container-type", value)
    }
    deploymentType foreach { value ⇒
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

          att("id", XFormsUtils.getStaticIdFromId(instanceState.effectiveId))
          att("model-id", instanceState.modelEffectiveId)

          if (instanceState.readonly) att("readonly", "true")

          instanceState.cachingOrContent match {
            case Left(caching)  ⇒ caching.writeAttributes(att)
            case Right(content) ⇒ instanceElement.addText(content)

          }

          instanceElement
        }

        instanceStates foreach (instanceState ⇒ instancesElement.add(instanceToXML(instanceState)))
      }
    }

    // Serialize controls
    locally {
      val controls = decodeControls
      if (controls.nonEmpty) {
        val controlsElement = rootElement.addElement("controls")
        controls foreach {
          case ControlState(effectiveId, visited, keyValues) ⇒
            val controlElement = controlsElement.addElement("control")
            controlElement.addAttribute("effective-id", effectiveId)
            if (visited)
              controlElement.addAttribute("visited", "true")
            for ((k, v) ← keyValues)
              controlElement.addAttribute(k, v)
        }
      }
    }

    // Template and Ajax response
    Seq(("template", decodeAnnotatedTemplate map (_.saxStore)), ("response", decodeLastAjaxResponse)) collect {
      case (elementName, Some(saxStore)) ⇒
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
    println("   annotatedTemplate: " + (annotatedTemplate map (_.size) getOrElse 1))
    println("   lastAjaxResponse: " + lastAjaxResponse.size)

    val decodedParts = Array(
      decodePathMatchersJava.toArray,
      decodeFocusedControlJava,
      decodePendingUploadsJava,
      decodeControlsJava,
      decodeInstancesJava.toArray,
      decodeAnnotatedTemplateJava,
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

case class InstancesControls(instances: List[InstanceState], controls: Map[String, ControlState]) {
  def instancesJava = instances.asJava
}

object DynamicState {

  // Create a DynamicState from a document
  def apply(document: XFormsContainingDocument): DynamicState =
    apply(document, document.getControls.getCurrentControlTree.rootOpt)

  // Create a DynamicState from a control
  def apply(document: XFormsContainingDocument, startOpt: Option[XFormsControl]): DynamicState = {

    val startContainer = startOpt match {
      case Some(componentControl: XFormsComponentControl) ⇒ componentControl.nestedContainer
      case Some(other)                                    ⇒ other.container
      case None                                           ⇒ document
    }

    // Serialize relevant controls that have data
    //
    // - Repeat, switch and dialogs controls serialize state (have been for a long time). The state of all the other
    //   controls is rebuilt from model data. This way we minimize the size of serialized controls. In the future,
    //   more information might be serialized.
    // - VisitableTrait controls serialize state if `visited == true`
    def controlsToSerialize = {
      val iterator =
        for {
          start        ← startOpt.toList
          control      ← ControlsIterator(start, includeSelf = false)
          if control.isRelevant
          controlState ← control.controlState
        } yield
          controlState

      iterator.toList
    }

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
      document.getUUID,
      document.getSequence,
      Option(document.getDeploymentType) map (_.toString),
      Option(document.getRequestContextPath),
      Option(document.getRequestPath),
      document.getRequestHeaders mapValues (_.toList) toList, // mapValues ok because of toList
      document.getRequestParameters mapValues (_.toList) toList, // mapValues ok because of toList
      Option(document.getContainerType),
      Option(document.getContainerNamespace),
      toByteSeq(document.getVersionedPathMatchers.asScala.toList),
      Option(document.getControls.getFocusedControl) map (_.getEffectiveId),
      toByteSeq(document.getPendingUploads.asScala.toSet),
      document.getTemplate map (_.asByteSeq), // template returns its own serialization
      toByteSeq(Option(document.getLastAjaxResponse)),
      toByteSeq(startContainer.allModels flatMap (_.getInstances.asScala) filter (_.mustSerialize) map (new InstanceState(_)) toList),
      toByteSeq(controlsToSerialize)
    )
  }

  // Create a DynamicState from an encoded string representation
  def apply(encoded: String): DynamicState = {
    val bytes = XFormsUtils.decodeBytes(encoded, false)
    fromByteArray[DynamicState](bytes)
  }

  // Encode the given document to a string representation
  def encodeDocumentToString(document: XFormsContainingDocument, compress: Boolean, isForceEncryption: Boolean): String =
    DynamicState(document).encodeToString(compress, isForceEncryption || document.isClientStateHandling)
}
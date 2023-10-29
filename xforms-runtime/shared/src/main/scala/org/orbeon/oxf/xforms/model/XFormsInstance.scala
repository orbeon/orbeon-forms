/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.model

import org.orbeon.datatypes.{BasicLocationData, LocationData}
import org.orbeon.dom
import org.orbeon.dom._
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.Connection.isInternalPath
import org.orbeon.oxf.util.StaticXPath.{DocumentNodeInfoType, VirtualNodeType}
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.XFormsServerSharedInstancesCache.InstanceLoader
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.model.Instance
import org.orbeon.oxf.xforms.event._
import org.orbeon.oxf.xforms.event.events._
import org.orbeon.oxf.xforms.model.XFormsInstance.InstanceDocument
import org.orbeon.oxf.xforms.state.InstanceState
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.saxon.om
import org.orbeon.scaxon.NodeInfoConversions._
import org.orbeon.xforms.{XFormsCrossPlatformSupport, XFormsId}
import shapeless.syntax.typeable._

import java.net.URI
import scala.collection.compat._
import scala.jdk.CollectionConverters._


// Caching information associated with an instance loaded with xxf:cache="true"
case class InstanceCaching(
  timeToLive        : Long,
  handleXInclude    : Boolean,
  pathOrAbsoluteURI : String, // for internal requests must be an internal path so that replication works
  requestBodyHash   : Option[String]
) {

  require(
    pathOrAbsoluteURI ne null,
    """Only XForms instances externally loaded through the src attribute may have xxf:cache="true"."""
  )

  def debugPairs: List[(String, String)] = List(
    "timeToLive"      -> timeToLive.toString,
    "handleXInclude"  -> handleXInclude.toString,
    "sourceURI"       -> pathOrAbsoluteURI,
    "requestBodyHash" -> requestBodyHash.orNull
  )

  def writeAttributes(att: (String, String) => Unit): Unit = {
    att("cache", "true")
    if (timeToLive >= 0) att("ttl", timeToLive.toString)
    if (handleXInclude)  att("xinclude", "true")
    att("source-uri", pathOrAbsoluteURI)
    requestBodyHash foreach (att("request-body-hash", _))
  }
}

object InstanceCaching {

  // Not using "apply" as that causes issues for Java callers
  def fromValues(
    timeToLive      : Long,
    handleXInclude  : Boolean,
    sourceURI       : String,
    requestBodyHash : Option[String]
  ): InstanceCaching =
    InstanceCaching(
      timeToLive        = timeToLive,
      handleXInclude    = handleXInclude,
      pathOrAbsoluteURI = Connection.findInternalUrl(
        normalizedUrl = URI.create(sourceURI).normalize,
        filter        = isInternalPath)(
        request         = XFormsCrossPlatformSupport.externalContext.getRequest
      ) getOrElse sourceURI, // adjust for internal path so replication works
      requestBodyHash   = requestBodyHash
    )
}

/**
 * Represent an XForms instance. An instance is made of:
 *
 * - immutable information
 *   - a reference to its parent model
 *   - a reference to its static representation
 * - mutable information
 *   - XML document with the instance content
 *   - information to reload the instance after deserialization if needed
 *   - whether the instance is readonly
 *   - whether the instance has been modified
 */
class XFormsInstance(
  val parent                  : XFormsModel,              // concrete parent model
  val instance                : Instance,                 // static instance
  private var _instanceCaching: Option[InstanceCaching],  // information to restore cached instance content
  private var _documentInfo   : DocumentNodeInfoType,     // fully wrapped document
  private var _readonly       : Boolean,                  // whether instance is readonly (can change upon submission)
  private var _modified       : Boolean,                  // whether instance was modified
  var valid                   : Boolean                   // whether instance was valid as of the last revalidation
) extends ListenersTrait
  with XFormsInstanceIndex
  with XFormsEventTarget
  with Logging {

  require(! (_readonly && _documentInfo.isInstanceOf[VirtualNodeType]))

  requireNewIndex()

  def containingDocument = parent.containingDocument

  // Getters
  def instanceCaching = _instanceCaching
  def documentInfo    = _documentInfo
  def readonly        = _readonly
  def modified        = _modified

  // Mark the instance as modified
  // This is used so we can optimize serialization: if an instance is inline and not modified, we don't need to
  // serialize its content
  def markModified() = _modified = true

  // Update the instance upon submission with instance replacement
  def update(instanceCaching: Option[InstanceCaching], documentInfo: DocumentNodeInfoType, readonly: Boolean): Unit = {

    _instanceCaching = instanceCaching
    _documentInfo    = documentInfo
    _readonly        = readonly

    requireNewIndex()

    markModified()
  }

  def exposeXPathTypes = instance.exposeXPathTypes
  def isSchemaValidation = instance.isSchemaValidation && ! _readonly

  // Return the model that contains this instance
  def model = parent

  // The instance root node
  def root: DocumentNodeInfoType = _documentInfo

  // The instance root element as with the `instance()` function
  def rootElement: om.NodeInfo = DataModel.firstChildElement(_documentInfo)

  def getId = instance.staticId
  def getPrefixedId = XFormsId.getPrefixedId(getEffectiveId)
  def getEffectiveId = XFormsId.getRelatedEffectiveId(parent.getEffectiveId, instance.staticId)

  def scope = model.staticModel.scope
  def container = model.container

  def getLocationData =
    underlyingDocumentOpt match {
      case Some(doc) => getNodeLocationData(doc.getRootElement)
      case None      => BasicLocationData(_documentInfo.getSystemId, _documentInfo.getLineNumber, -1)
    }

  private def getNodeLocationData(node: Node): LocationData = {
    val data =
      node match {
        case elem: Element  => elem.getData
        case att: Attribute => att.getData
        case _              => null
      }
    // TODO: other node types
    data match {
      case ld: LocationData => ld
      case id: InstanceData => id.getLocationData
      case _                => null
    }
  }

  def parentEventObserver: XFormsEventTarget = model

  def performDefaultAction(event: XFormsEvent): Unit =
    event match {
      case _: XXFormsInstanceInvalidate =>
        implicit val indentedLogger: IndentedLogger = event.containingDocument.getIndentedLogger(XFormsModel.LoggingCategory)
        _instanceCaching match {
          case Some(instanceCaching) =>
            XFormsServerSharedInstancesCache.remove(
              instanceSourceURI = instanceCaching.pathOrAbsoluteURI,
              requestBodyHash   = None,
              handleXInclude    = instanceCaching.handleXInclude,
              ignoreQueryString = false
            )
          case None =>
            warn(
              "xxforms-instance-invalidate event dispatched to non-cached instance",
              Seq("instance id" -> getEffectiveId)
            )
        }
      case ev: XXFormsActionErrorEvent =>
        XFormsError.handleNonFatalActionError(this, ev.throwable)
      case _ =>
    }

  def performTargetAction(event: XFormsEvent): Unit =
    event match {
      case insertEvent: XFormsInsertEvent =>
        // New nodes were just inserted

        val insertedNodes = insertEvent.insertedNodes

        // Update index
        // If this was a root element replacement, rely on `XXFormsReplaceEvent` instead
        if (! insertEvent.isRootElementReplacement)
          updateIndexForInsert(insertedNodes)

      case deleteEvent: XFormsDeleteEvent =>
        // New nodes were just deleted
        if (deleteEvent.deletedNodes.nonEmpty) {
          // Find affected repeats and update them
          updateIndexForDelete(deleteEvent.deletedNodes)
        }
      case replaceEvent: XXFormsReplaceEvent =>
        // A node was replaced
        // As of 2013-02-18, this happens for:
        // - a root element replacement
        // - an id attribute replacement
        updateIndexForReplace(replaceEvent.formerNode, replaceEvent.currentNode)
      case valueChangeEvent: XXFormsValueChangedEvent =>
        updateIndexForValueChange(valueChangeEvent)
      case _ =>
    }

  // Return the instance document as an Orbeon DOM `Document`
  // If the instance is readonly, this returns `None`. Callers should use root() whenever possible.
  def underlyingDocumentOpt: Option[Document] =
    _documentInfo match {
      case virtualNode: VirtualNodeType => Some(virtualNode.getUnderlyingNode.asInstanceOf[Document])
      case _                            => None
    }

  // For state serialization
  def contentAsInstanceDocument: InstanceDocument =
    underlyingDocumentOpt.toLeft(_documentInfo)

  // Don't allow any external events
  def allowExternalEvent(eventName: String) = false

//  // Write the instance document to the specified ContentHandler
//  def write(xmlReceiver: XMLReceiver): Unit =
//    TransformerUtils.sourceToSAX(_documentInfo, xmlReceiver)
//
  // Log the instance
  def logContent(indentedLogger: IndentedLogger, message: String): Unit = {
    implicit val logger = indentedLogger
    debug(message, Seq(
      "model effective id"    -> parent.getEffectiveId,
      "instance effective id" -> getEffectiveId,
      "instance"              -> StaticXPath.tinyTreeToString(rootElement)
    ))
  }
//
//  // Print the instance with extra annotation attributes to Console.out. For debug only.
//  def debugPrintOut(): Unit = {
//    val identityTransformerHandler: TransformerXMLReceiver = TransformerUtils.getIdentityTransformerHandler
//    identityTransformerHandler.setResult(new StreamResult(Console.out))
//    write(identityTransformerHandler)
//  }
//
//  // Log the current MIP values applied to this instance
//  def debugLogMIPs(): Unit = {
//    XFormsUtils.logDebugDocument(
//      "MIPs: ",
//      XFormsInstance.createDebugMipsDocument(underlyingDocumentOpt.get)
//    )
//  }

  // Replace the instance with the given document
  // This includes marking the structural change as well as dispatching events
  def replace(
    newDocumentInfo : DocumentNodeInfoType,
    dispatch        : Boolean                 = true,
    instanceCaching : Option[InstanceCaching] = instanceCaching,
    isReadonly      : Boolean                 = readonly,
    applyDefaults   : Boolean                 = false
  ): Unit = {

    val formerRoot = rootElement

    // Update the instance (this also marks it as modified)
    update(
      instanceCaching,
      newDocumentInfo,
      isReadonly
    )

    // Call this directly, since we are not using insert/delete here
    model.markStructuralChange(Some(this), if (applyDefaults) FlaggedDefaultsStrategy else NoDefaultsStrategy)

    val currentRoot = rootElement

    if (dispatch) {
      // Dispatch xxforms-replace event
      // NOTE: For now, still dispatch xforms-insert for backward compatibility.
      Dispatch.dispatchEvent(
        new XXFormsReplaceEvent(
          this,
          formerRoot,
          currentRoot
        )
      )

      // Dispatch xforms-insert event for backward compatibility
      // NOTE: use the root node as insert location as it seems to make more sense than pointing to the former
      // root element.
      Dispatch.dispatchEvent(
        new XFormsInsertEvent(
          this,
          Seq[om.NodeInfo](currentRoot).asJava,
          null,   // CHECK
          currentRoot.getRoot,
          "into", // "into" makes more sense than "after" or "before"! We used to have "after", not sure why.
          0
        )
      )
    }
  }
}

// For instances which declare xxf:index="id", keep up-to-date an index of ids to elements. The index is set on
// DocumentWrapper, so that the XPath id() function works out of the box.
//
// Implementation notes:
//
// - set an IdGetter on DocumentWrapper when a new Orbeon DOM DocumentWrapper is set on the instance
// - index all elements with an attribute whose local name is "id"
// - initial index is created the first time an id is required
// - upon subsequent document updates (insert, delete, setvalue), the index is incrementally updated
// - keep reference to all elements which have a given id so that we support insert/delete in any order
// - sort the elements in case there is more than one possible result; this is not very efficient so it's better to
//   make sure that every id is unique
//
// Possible improvements:
//
// - should just index "id" and "xml:id"
// - handle schema xs:ID type as well

trait BasicIdIndex {

  import org.orbeon.scaxon.SimplePath._
  import org.w3c.dom.Node.ATTRIBUTE_NODE

  import collection.{mutable => m}

  protected def documentInfo: DocumentNodeInfoType

  def hasIdIndex: Boolean = idIndex ne null

  def idGetter(wrapper: DocumentWrapper): String => Element =
    id => {

      object ElementOrdering extends Ordering[Element] {
        def compare(x: Element, y: Element): Int =
          wrapper.wrap(x).compareOrder(wrapper.wrap(y))
      }

      // Lazily create index the first time if needed
      createIndexIfNeeded()

      // Query index
      idIndex.get(id) match {
        case Some(list) if list.size > 1 => list.min(ElementOrdering) // get first in document order
        case Some(list)                  => list.head                 // empty list not allowed in the map
        case None                        => null
      }
    }

  protected var idIndex: m.Map[String, List[Element]] = _

  protected def addId(id: String, element: Element): Unit =
    idIndex(id) = element :: (
      idIndex.get(id) match {
        case Some(list) =>
          // We should enable the assert below, but first we need to make sure we skip xforms-insert
          // processing for an attribute replacement, because xxforms-replace has already handled the updated
          // attribute. For now, filter so we don't get duplicates.
          //assert(! (list exists (_ eq element)))
          list filter (_ ne element)
        case None       => Nil
      }
    )

  protected def mappingsInSubtree(start: om.NodeInfo): Seq[(String, Element)] =
    idsInSubtree(start) map (id => id.getStringValue -> unsafeUnwrapElement(id.parentUnsafe))

  protected def combineMappings(mappings: Iterable[(String, Element)]): Unit =
    for ((id, element) <- mappings)
      addId(id, element)

  protected def createIndexIfNeeded(): Unit =
    if (idIndex eq null) {
      idIndex = m.Map()
      combineMappings(mappingsInSubtree(documentInfo))
    }

  private def idsInSubtree(start: om.NodeInfo) =
    if (start.getNodeKind == ATTRIBUTE_NODE)
      start self "id"
    else
      start descendantOrSelf * att "id"
}

trait XFormsInstanceIndex extends BasicIdIndex {

  self: XFormsInstance =>

  import org.orbeon.scaxon.SimplePath._
  import org.w3c.dom.Node.{ATTRIBUTE_NODE, ELEMENT_NODE}

  def requireNewIndex(): Unit = {
    idIndex = null
    if (instance.indexIds)
      self.documentInfo.narrowTo[DocumentWrapper] foreach (wrapper => wrapper.setIdGetter(idGetter(wrapper)))
  }

  def updateIndexForInsert(nodes: Seq[om.NodeInfo]): Unit =
    if (hasIdIndex)
      for (node <- nodes)
        combineMappings(mappingsInSubtree(node))

  def updateIndexForDelete(nodes: Seq[om.NodeInfo]): Unit =
    if (hasIdIndex)
      for (node <- nodes; (id, element) <- mappingsInSubtree(node))
        removeId(id, element)

  def updateIndexForReplace(formerNode: om.NodeInfo, currentNode: om.NodeInfo): Unit =
    if (hasIdIndex) {
      if (currentNode.getNodeKind == ATTRIBUTE_NODE && currentNode.getLocalPart == "id")
        // Don't use updateIndexForDelete, because formerNode.getParent will fail
        removeId(formerNode.stringValue, unsafeUnwrapElement(currentNode.parentUnsafe))
      else if (currentNode.getNodeKind == ELEMENT_NODE)
        updateIndexForDelete(Seq(formerNode))

      updateIndexForInsert(Seq(currentNode))
    }

  def updateIndexForValueChange(valueChangeEvent: XXFormsValueChangedEvent): Unit =
    if (hasIdIndex && valueChangeEvent.node.getLocalPart == "id") {

      val parentElement = unsafeUnwrapElement(valueChangeEvent.node.parentUnsafe)

      removeId(valueChangeEvent.oldValue, parentElement)
      addId(valueChangeEvent.newValue, parentElement)
    }

  private def removeId(id: String, parentElement: Element): Unit =
    idIndex.get(id) match {
      case Some(list) if list.size > 1 =>
        idIndex(id) = list filter (_ ne parentElement)
        assert(idIndex(id).nonEmpty)
      case Some(_)                     => idIndex -= id // don't leave an empty list in the map
      case None                        => // NOP
    }
}

object XFormsInstance extends Logging {

  type InstanceDocument = Document Either DocumentNodeInfoType

  // Create an initial instance without caching information
  def apply(model: XFormsModel, instance: Instance, documentInfo: DocumentNodeInfoType): XFormsInstance =
    new XFormsInstance(
      model,
      instance,
      None,
      documentInfo,
      instance.readonly,
      false,
      true
    )

  def createDocumentInfo(doc: InstanceDocument, exposeXPathTypes: Boolean): DocumentNodeInfoType = doc match {
    case Left(domDocument)   => XFormsInstanceSupport.wrapDocument(domDocument, exposeXPathTypes)
    case Right(documentInfo) => documentInfo
  }

  // Take a non-wrapped `DocumentInfo` and wrap it if needed
  def wrapDocumentInfoIfNeeded(
    documentInfo    : DocumentNodeInfoType,
    readonly        : Boolean,
    exposeXPathTypes: Boolean
  ): DocumentNodeInfoType = {

    require(
      ! documentInfo.isInstanceOf[VirtualNodeType],
      "DocumentInfo must not be a VirtualNode, i.e. it must be a native readonly tree like TinyTree"
    )

    // NOTE: We don't honor `exposeXPathTypes` on readonly instances, anyway they don't support MIPs at this time
    if (readonly)
      documentInfo // the optimal case: no copy of the cached document is needed
    else
      XFormsInstanceSupport.wrapDocument(StaticXPath.tinyTreeToOrbeonDom(documentInfo), exposeXPathTypes)
  }

  // Restore an instance on the model, given InstanceState
  def restoreInstanceFromState(model: XFormsModel, instanceState: InstanceState, loader: InstanceLoader): Unit = {

    implicit val logger = model.indentedLogger

    val instance = model.staticModel.instances(XFormsId.getStaticIdFromId(instanceState.effectiveId))

    val (caching, documentInfo) =
      instanceState.cachingOrDocument match {
        case Left(caching)  =>
          debug("restoring instance from instance cache", Seq("id" -> instanceState.effectiveId))

          // NOTE: No XInclude supported to read instances with @src for now
          // TODO: must pass method and request body in case of POST/PUT

          (
            Some(caching),
            XFormsServerSharedInstancesCache.findContentOrLoad(
              caching,
              instanceState.readonly,
              instance.exposeXPathTypes,
              loader
            )
          )

        case Right(content) =>
          debug("using initialized instance from state", Seq("id" -> instanceState.effectiveId))
          (
            None,
            createDocumentInfo(
              content,
              instance.exposeXPathTypes
            )
          )
      }

    model.indexInstance(
      new XFormsInstance(
        model,
        instance,
        caching,
        documentInfo,
        instanceState.readonly,
        instanceState.modified,
        instanceState.valid
      )
    )
  }

  def createDebugMipsDocument(doc: Document): Document = {

    val result      = dom.Document()
    val rootElement = result.addElement("mips")

    doc.accept(new VisitorSupport {

      var currentElement: Element = null

      final override def visit(element: Element) = {
        currentElement = rootElement.addElement("element")
        currentElement.addAttribute("qname", element.getQualifiedName)
        currentElement.addAttribute("namespace-uri", element.getNamespaceURI)
        addMIPInfo(currentElement, element)
      }

      final override def visit(attribute: Attribute) = {
        val attributeElement = currentElement.addElement("attribute")
        attributeElement.addAttribute("qname", attribute.getQualifiedName)
        attributeElement.addAttribute("namespace-uri", attribute.getNamespaceURI)
        addMIPInfo(attributeElement, attribute)
      }

      private def addMIPInfo(parentInfoElement: Element, node: Node) = {
        parentInfoElement.addAttribute("readonly", InstanceData.getInheritedReadonly(node).toString)
        parentInfoElement.addAttribute("relevant", InstanceData.getInheritedRelevant(node).toString)
        parentInfoElement.addAttribute("required", InstanceData.getRequired(node).toString)
        parentInfoElement.addAttribute("valid", InstanceData.getValid(node).toString)
        val typeQName = InstanceData.getType(node)
        parentInfoElement.addAttribute("type", Option(typeQName) map (_.qualifiedName) getOrElse "")
      }
    })

    result
  }

  // Search ancestor-or-self containers as suggested here: http://wiki.orbeon.com/forms/projects/xforms-model-scoping-rules
  // Also allow an absolute instance id
  def findInAncestorScopes(startContainer: XBLContainer, instanceId: String): Option[om.NodeInfo] = {

    // The idea here is that we first try to find a concrete instance. If that fails, we try to see if it
    // exists statically. If it does exist statically only, we return an empty sequence, but we don't warn
    // as the instance actually exists. The case where the instance might exist statically but not
    // dynamically is when this function is used during xforms-model-construct. At that time, instances in
    // this or other models might not yet have been constructed, however they might be referred to, for
    // example with model variables.

    def findObjectByAbsoluteId(id: String) =
      startContainer.containingDocument.getObjectByEffectiveId(XFormsId.absoluteIdToEffectiveId(id))

    def findAbsolute =
      if (XFormsId.isAbsoluteId(instanceId))
        collectByErasedType[XFormsInstance](findObjectByAbsoluteId(instanceId)) map (_.rootElement)
      else
        None

    def findDynamic = {
      val containers = Iterator.iterateOpt(startContainer)(_.parentXBLContainer)
      val instances  = containers flatMap (_.findInstance(instanceId))

      instances.nextOption() map (_.rootElement)
    }

    def findStatic = {

      val containingDocument = startContainer.containingDocument
      val ops = containingDocument.staticOps
      val startScope = startContainer.innerScope

      val scopes    = Iterator.iterateOpt(startScope)(_.parent)
      val instances = scopes flatMap ops.getModelsForScope flatMap (_.instances.get(instanceId))

      if (! instances.hasNext)
        containingDocument.getIndentedLogger(XFormsModel.LoggingCategory).logDebug("xxf:instance()", "instance not found", "instance id", instanceId)

      None
    }

    findAbsolute orElse findDynamic orElse findStatic
  }

  // Don't serialize if the instance is inline and hasn't been modified
  // NOTE: If the instance is cacheable, its metadata gets serialized, but not it's XML content
  def mustSerialize(instance: XFormsInstance): Boolean =
    ! (instance.instance.useInlineContent && ! instance.modified)

  def serializeInstanceDocumentToString(instanceDocument: InstanceDocument): String =
    instanceDocument match {
      case Left(doc)  => doc.serializeToString(dom.io.XMLWriter.DefaultFormat)
      case Right(doc) => StaticXPath.tinyTreeToString(doc)
    }

  def deserializeInstanceDocumentFromString(xmlString: String, readonly: Boolean): InstanceDocument =
    if (! readonly)
      Left(XFormsCrossPlatformSupport.readOrbeonDom(xmlString))
    else
      Right(XFormsCrossPlatformSupport.stringToTinyTree(
        XPath.GlobalConfiguration,
        xmlString,
        handleXInclude = false,
        handleLexical  = true
      ))

}
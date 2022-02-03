package org.orbeon.oxf.fr

import org.orbeon.dom.QName
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.NodeInfoFactory.elementInfo
import org.orbeon.oxf.xforms.NodeInfoFactory
import org.orbeon.oxf.xforms.action.XFormsAPI.{delete, insert}
import org.orbeon.oxf.xforms.xbl.BindingDescriptor.{findMostSpecificWithoutDatatype, getAllRelevantDescriptors}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsNames
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.NodeInfoConversions

import scala.collection.compat._


object FormRunnerTemplatesOps {

  // Make sure all template instances reflect the current bind structure
  def updateTemplates(
    ancestorContainerNames : Option[Set[String]],
    componentBindings      : Iterable[NodeInfo])(implicit
    ctx                    : FormRunnerDocContext
  ): Unit =
    for {
      templateInstance <- templateInstanceElements(ctx.formDefinitionRootElem)
      repeatName       = controlNameFromId(templateInstance.id)
      if ancestorContainerNames.isEmpty || ancestorContainerNames.exists(_(repeatName))
      iterationName    <- findRepeatIterationName(repeatName)
      template         <- createTemplateContentFromBindName(iterationName, componentBindings)
    } locally {
      ensureTemplateReplaceContent(repeatName, template)
    }

  def createTemplateContentFromBindName(
    bindName : String,
    bindings : Iterable[NodeInfo])(implicit
    ctx      : FormRunnerDocContext
  ): Option[NodeInfo] =
    findBindByName(bindName) map (createTemplateContentFromBind(_, bindings))

private val AttributeRe = "@(.+)".r

  // Create an instance template based on a hierarchy of binds rooted at the given bind
  // This checks each control binding in case the control specifies a custom data holder.
  def createTemplateContentFromBind(
    startBindElem : NodeInfo,
    bindings      : Iterable[NodeInfo])(implicit
    ctx           : FormRunnerDocContext
  ): NodeInfo = {

    val descriptors = getAllRelevantDescriptors(bindings)

    val allControlsByName = getAllControlsWithIds map (c => controlNameFromId(c.id) -> c) toMap

    def holderForBind(bind: NodeInfo, topLevel: Boolean): Option[NodeInfo] = {

      val controlName    = getBindNameOrEmpty(bind)
      val controlElemOpt = allControlsByName.get(controlName)

      // Handle non-standard cases, see https://github.com/orbeon/orbeon-forms/issues/2470
      def fromNonStandardRef =
        bind attValueOpt "ref" match {
          case Some(AttributeRe(att)) => Some(Some(NodeInfoFactory.attributeInfo(att)))
          case Some(".")              => Some(None)
          case _                      => None
        }

      def fromBinding =
        for {
          controlElem <- controlElemOpt
          appearances = controlElem attTokens XFormsNames.APPEARANCE_QNAME
          descriptor  <- findMostSpecificWithoutDatatype(controlElem.uriQualifiedName, appearances, descriptors)
          binding     <- descriptor.binding
        } yield
          Some(newDataHolder(controlName, binding))

      def fromPlainControlName =
        Some(Some(elementInfo(controlName)))

      val elementTemplateOpt = fromNonStandardRef orElse fromBinding orElse fromPlainControlName flatten

      elementTemplateOpt foreach { elementTemplate =>

        val iterationCount = {

          // If the current control is a repeated fr:grid or fr:section with the attribute set, find the first occurrence
          // in the data of this  repeat, and use its concrete initial number of iterations to update the template. We
          // can imagine other values for the attribute in the future, maybe an integer value (`0`, `1`, ...) setting
          // the initial number of iterations.
          // See https://github.com/orbeon/orbeon-forms/issues/2379
          def useInitialIterations(controlElem: NodeInfo) =
            ! topLevel && isRepeat(controlElem) && getInitialIterationsAttribute(controlElem).contains("first")

          controlElemOpt match {
            case Some(controlElem) if useInitialIterations(controlElem) =>

              val firstDataHolder   = findDataHolders(controlName) take 1
              val iterationsHolders = firstDataHolder / *

              iterationsHolders.size

            case _ =>
              1
          }
        }

        // Recursively insert elements in the template
        if (iterationCount > 0) {

          // If iterationCount > 1, we just duplicate the children `iterationCount` times. In practice, this means
          // multiple iteration elements:
          //
          // <repeated-section-2-iteration>
          //   ...
          // </repeated-section-2-iteration>
          // <repeated-section-2-iteration>
          //   ...
          // </repeated-section-2-iteration>
          val nested         = bind / "*:bind" flatMap (holderForBind(_, topLevel = false))
          val repeatedNested = (1 to iterationCount) flatMap (_ => nested)

          insert(into = elementTemplate, origin = repeatedNested)
        }
      }

      elementTemplateOpt
    }

    holderForBind(startBindElem, topLevel = true) getOrElse (throw new IllegalStateException)
  }

  def bindingMetadata(binding: NodeInfo) =
    binding / FBMetadataTest

  def ensureTemplateReplaceContent(
    controlName : String,
    content     : NodeInfo)(implicit
    ctx         : FormRunnerDocContext
  ): Unit = {

    val templateInstanceId = templateId(controlName)
    val modelElement = getModelElem(ctx.formDefinitionRootElem)
    modelElement / XFInstanceTest find (_.hasIdValue(templateInstanceId)) match {
      case Some(templateInstance) =>
        // clear existing template instance content
        delete(templateInstance / *)
        insert(into = templateInstance , origin = content)

      case None =>
        // Insert template instance if not present
        val template: NodeInfo =
          <xf:instance
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
            id={templateInstanceId}
            fb:readonly="true"
            xxf:exclude-result-prefixes="#all">{nodeInfoToElem(content)}</xf:instance>

        insert(into = modelElement, after = modelElement / XFInstanceTest takeRight 1, origin = template)
    }
  }

  // Create a new data holder given the new control name, using the instance template if found
  def newDataHolder(controlName: String, binding: NodeInfo): NodeInfo = {

    val instanceTemplate = bindingMetadata(binding) / FBTemplatesTest / FBInstanceTest
    if (instanceTemplate.nonEmpty) {
      // Because `elementInfo` doesn't support being passed text `NodeInfo`!
      val mutable = NodeInfoConversions.extractAsMutableDocument(instanceTemplate.head).rootElement
      elementInfo(controlName, (mutable.head /@ @*) ++ (mutable / (Text || *)))
    } else
      elementInfo(controlName)
  }

  def getInitialIterationsAttribute(controlElem: NodeInfo): Option[String] =
    controlElem attValueOpt FBInitialIterations flatMap trimAllToOpt

  // TODO: These are only needed by the persistence proxy for form definition migration
  private val FBPrefix = "fb"
  private val FB       = "http://orbeon.org/oxf/xml/form-builder"

  private val FBInitialIterations         : QName = QName("initial-iterations", FBPrefix, FB)
  private val FBTemplatesTest             : Test  = FB -> "templates"
  private val FBInstanceTest              : Test  = FB -> "instance"
  private val FBMetadataTest              : Test  = FB -> "metadata"
}

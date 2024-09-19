/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fr

import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.fr
import org.orbeon.oxf.fr.FormRunnerCommon.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.action.XFormsAPI.inScopeContainingDocument
import org.orbeon.oxf.xforms.control.{Controls, XFormsComponentControl}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeInfoConversions
import org.orbeon.scaxon.SimplePath.{URIQualifiedName, _}
import org.orbeon.xforms.XFormsId
import shapeless.syntax.typeable.*

import java.util as ju
import scala.jdk.CollectionConverters.*


trait FormRunnerSectionTemplateOps {

  private val MatchesComponentUriLibraryRegex = """http://orbeon.org/oxf/xml/form-builder/component/([^/]+)/library""".r
  private val MatchesSectionTemplateUriRegex  = """^http://orbeon.org/oxf/xml/form-builder/component/([^/]+)/([^/]+)$""".r

  // For XSLT/XForms callers
  // NOTE: This is used only by Form Builder. Move?

  //@XPathFunction
  def globalLibraryAppName : String = Names.GlobalLibraryAppName

  //@XPathFunction
  def specialLibraryAppName: String = Names.SpecialLibraryAppName

  //@XPathFunction
  def elemNameForSectionTemplateApp(sectionTemplateApp: String): String =
    sectionTemplateApp match {
      case Names.GlobalLibraryAppName  => Names.GlobalLibraryVersionElemName
      case Names.SpecialLibraryAppName => Names.OrbeonLibraryVersionElemName
      case _                           => Names.AppLibraryVersionElemName
    }

  def sectionTemplateXBLBindingsByURIQualifiedName(xblElems: collection.Seq[NodeInfo]): Map[URIQualifiedName, DocumentWrapper] = {

    // All xbl:binding elements available for section templates
    def availableSectionTemplateXBLBindings(componentBindings: Iterable[NodeInfo]): Iterable[NodeInfo] =
      componentBindings filter (_.attClasses("fr-section-component"))

    val bindingsForSectionTemplates =
      availableSectionTemplateXBLBindings(xblElems / fr.XMLNames.XBLBindingTest)

    (
      bindingsForSectionTemplates map { binding =>
        bindingFirstURIQualifiedName(binding) -> NodeInfoConversions.extractAsMutableDocument(binding)
      }
    ).toMap
  }

  //@XPathFunction
  def findAppFromSectionTemplateUri(uri: String): Option[String] = uri match {
    case MatchesComponentUriLibraryRegex(app) => Option(app)
    case _ => None
  }

  //@XPathFunction
  def controlMatchesNameAndLibrary(controlAbsoluteId: String, controlNames: ju.List[String], libraryName: String): Boolean = {

    val controlName =
      frc.controlNameFromIdOpt(XFormsId.getStaticIdFromId(XFormsId.absoluteIdToEffectiveId(controlAbsoluteId)))

    def nameMatches: Boolean =
      controlNames.asScala.exists(controlName.contains)

    def libraryNameMatches: Boolean = {

      val libraryUri = s"${Controls.SectionTemplateUriPrefix}$libraryName/library"

      inScopeContainingDocument.controls.getCurrentControlTree.findControl(XFormsId.absoluteIdToEffectiveId(controlAbsoluteId))
        .exists(
          _.container.ancestorsIterator.flatMap(_.associatedControlOpt)
            .exists(_.element.getNamespaceURI == libraryUri)
        )
    }

    nameMatches && libraryNameMatches
  }

  def sectionTemplateForSection(frSectionComponent: XFormsComponentControl): Option[XFormsComponentControl] = {

    // Find the concrete section template component (`component:foo`)
    // A bit tricky because there might not be an id on the component element:
    //
    //     <component:eid xmlns:component="http://orbeon.org/oxf/xml/form-builder/component/orbeon/library"/>
    //
    val sectionTemplateElementOpt =
      frSectionComponent.staticControl.descendants find
      (c => matchesComponentURI(c.element.getNamespaceURI))

    sectionTemplateElementOpt                       flatMap
      (e => frSectionComponent.resolve(e.staticId)) flatMap
      (_.narrowTo[XFormsComponentControl])
  }

  def matchesComponentURI(uri: String): Boolean =
    MatchesComponentUriLibraryRegex.findFirstIn(uri).isDefined

  def findXblBinding(publishedFormDoc: NodeInfo, uriQualifiedName: URIQualifiedName): Option[NodeInfo] = {

    val bindings =
      for {
        xblBinding <- publishedFormDoc.rootElement / fr.XMLNames.XHHeadTest / fr.XMLNames.XBLXBLTest / fr.XMLNames.XBLBindingTest
        if xblBinding.hasAtt("element") && bindingFirstURIQualifiedName(xblBinding) == uriQualifiedName
      } yield
        xblBinding

    assert(bindings.length <= 1, s"expect no more than one `<xbl:binding>` for component `Q{${uriQualifiedName.uri}${uriQualifiedName.localName}`")
    bindings.headOption
  }

  def findXblInstance(bindingElem: NodeInfo, instanceId: String): Option[NodeInfo] =
    bindingElem                       /
    fr.XMLNames.XBLImplementationTest /
    fr.XMLNames.XFModelTest           /
    fr.XMLNames.XFInstanceTest        find
    (_.id == instanceId)

  // Find top-level `xf:bind`, which is the one without a `ref`
  // Q: There is a `xf:bind` making `instance('fr-form-instance')` readonly in certain cases; is it really needed?
  def findXblBinds(bindingElem: NodeInfo): NodeColl = {
    val els =
      bindingElem                         /
        fr.XMLNames.XBLImplementationTest /
        fr.XMLNames.XFModelTest           /
        fr.XMLNames.XFBindTest            filter
        (_.id == fr.Names.FormBinds)

    assert(els.length == 1, "expect exactly one top-level bind in component")
    els / "*:bind"
  }

  def findComponentNodeForSection(sectionNode: NodeInfo): Option[NodeInfo] =
    sectionNode child * find (e => matchesComponentURI(e.getURI))

  def isSectionWithTemplateContent(containerElem: NodeInfo): Boolean =
    frc.IsSection(containerElem) && (containerElem / * exists isSectionTemplateContent)

  def isSectionTemplateContent(containerElem: NodeInfo): Boolean =
    (containerElem parent * exists frc.IsSection) &&
      MatchesSectionTemplateUriRegex.findFirstIn(containerElem.namespaceURI).nonEmpty

  def sectionTemplateBindingName(section: NodeInfo): Option[URIQualifiedName] =
    (section / * filter isSectionTemplateContent map (_.uriQualifiedName)).headOption

  def findSectionsWithTemplates(implicit ctx: FormRunnerDocContext): Seq[NodeInfo] =
    ctx.bodyElemOpt.toList descendant * filter frc.IsSection filter (_ / * exists isSectionTemplateContent)

  // Find the binding's first URI qualified name
  // For now takes the first CSS rule and assume the form `foo|bar`.
  def bindingFirstURIQualifiedName(bindingElem: NodeInfo): URIQualifiedName = {
    val firstElementCSSName = (bindingElem attValue "element").splitTo[List](",").head
    val elementQName        = firstElementCSSName.replace('|', ':')

    bindingElem.resolveURIQualifiedName(elementQName)
  }
}

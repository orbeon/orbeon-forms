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
import org.orbeon.oxf.fr.DataMigration.PathElem
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.xforms.XFormsUtils._
import org.orbeon.oxf.xml.NamespaceMapping
import org.orbeon.saxon.om.{Item, NodeInfo}
import org.orbeon.scaxon.XML._

import scala.collection.JavaConverters._

trait FormRunnerControlOps extends FormRunnerBaseOps {

  import Private._

  // Extensible records would be cool here. see:
  //
  // - https://github.com/lampepfl/dotty/issues/964
  // - https://github.com/milessabin/shapeless/wiki/Feature-overview:-shapeless-2.0.0#extensible-records
  //
  case class BindPath              (                   bind: NodeInfo, path: List[DataMigration.PathElem]                         )
  case class BindPathHolders       (                   bind: NodeInfo, path: List[DataMigration.PathElem], holders: Option[List[NodeInfo]])
  case class ControlBindPathHolders(control: NodeInfo, bind: NodeInfo, path: List[DataMigration.PathElem], holders: Option[List[NodeInfo]])

  def hasAllClassesPredicate(classNamesList: List[String])(control: NodeInfo): Boolean = {
    val controlClasses = control.attClasses
    classNamesList forall controlClasses.contains
  }

  def hasAnyClassPredicate(classNamesList: Set[String])(control: NodeInfo): Boolean = {
    val controlClasses = control.attClasses
    classNamesList exists controlClasses.contains
  }

  private val ControlName     = """(.+)-(control|bind|grid|section|template|repeat)""".r // `repeat` is for legacy FB
  private val FBLangPredicate = "[@xml:lang = $fb-lang]"

  val LHHAInOrder = Seq("label", "hint", "help", "alert")
  val LHHANames   = LHHAInOrder.to[Set]

  // Get the control name based on the control, bind, grid, section or template id
  //@XPathFunction
  def controlNameFromId(controlOrBindId: String) =
    getStaticIdFromId(controlOrBindId) match {
      case ControlName(name, _) ⇒ name
      case _                    ⇒ null
    }

  //@XPathFunction
  def controlNameFromIdOpt(controlOrBindId: String) =
    Option(controlNameFromId(controlOrBindId))

  // Whether the given id is for a control (given its reserved suffix)
  def isIdForControl(controlOrBindId: String) = controlNameFromId(controlOrBindId) ne null

  // Whether the given node corresponds to a control
  // TODO: should be more restrictive
  val IsControl: NodeInfo ⇒ Boolean = hasName

  private val PossibleControlSuffixes = List("control", "grid", "section", "repeat")

  // Find a control by name (less efficient than searching by id)
  def findControlByName(inDoc: NodeInfo, controlName: String) = (
    for {
      suffix  ← PossibleControlSuffixes.iterator
      control ← findInViewTryIndex(inDoc, controlName + '-' + suffix).iterator
    } yield
      control
  ).nextOption()

  // Find a control id by name
  def findControlIdByName(inDoc: NodeInfo, controlName: String) =
    findControlByName(inDoc, controlName) map (_.id)

  // XForms callers: find a control element by name or null (the empty sequence)
  def findControlByNameOrEmpty(inDoc: NodeInfo, controlName: String) =
    findControlByName(inDoc, controlName).orNull

  // Get the control's name based on the control element
  def getControlName(control: NodeInfo) = getControlNameOpt(control).get

  // Get the control's name based on the control element
  def getControlNameOpt(control: NodeInfo) =
    (control \@ "id" headOption) flatMap
      (id ⇒ Option(controlNameFromId(id.stringValue)))

  def hasName(control: NodeInfo) = getControlNameOpt(control).isDefined

  // Return a bind ref or nodeset attribute value if present
  def bindRefOrNodeset(bind: NodeInfo): Option[String] =
    bind \@ ("ref" || "nodeset") map (_.stringValue) headOption

  // Find a bind by name
  def findBindByName(inDoc: NodeInfo, name: String): Option[NodeInfo] =
    findInBindsTryIndex(inDoc, bindId(name))

  // XForms callers: find a bind by name or null (the empty sequence)
  def findBindByNameOrEmpty(inDoc: NodeInfo, name: String) =
    findBindByName(inDoc, name).orNull

  // Find a bind by predicate
  private def findBind(inDoc: NodeInfo, p: NodeInfo ⇒ Boolean): Option[NodeInfo] =
    findTopLevelBind(inDoc).toSeq \\ "*:bind" find p

  // NOTE: Not sure why we search for anything but id or name, as a Form Runner bind *must* have an id and a name
  def isBindForName(bind: NodeInfo, name: String) =
    hasIdValue(bind, bindId(name)) || bindRefOrNodeset(bind).contains(name) // also check ref/nodeset in case id is not present

  // Canonical way: use the `name` attribute
  def getBindNameOrEmpty(bind: NodeInfo) =
    bind attValue "name"

  def findBindName(bind: NodeInfo) =
    bind attValueOpt "name"

  // 2017-04-25: Don't use enclosing parentheses anymore. This now ensures that the `ref` is a single
  // reference to an XML element. See https://github.com/orbeon/orbeon-forms/issues/3174.
  //
  // Also, specific case for Form Builder: drop language predicate, as we want to index/return values
  // for all languages. So far, this is handled as a special case, as this is not something that happens
  // in other forms.
  private def buildBindPath(bind: NodeInfo): List[DataMigration.PathElem] =
    (bind ancestorOrSelf XFBindTest flatMap bindRefOrNodeset).reverse.tail map { bindRef ⇒
      PathElem(
        if (bindRef.endsWith(FBLangPredicate))
          bindRef.dropRight(FBLangPredicate.length)
        else
          bindRef
      )
    } toList

  def findBindAndPathStatically(inDoc: NodeInfo, controlName: String): Option[BindPath] =
    findBindByName(inDoc, controlName) map { bindNode ⇒
      BindPath(bindNode, buildBindPath(bindNode))
    }

  // If `contextItemOpt` is `None`, don't search for holders.
  def findBindPathHoldersInDocument(inDoc: NodeInfo, controlName: String, contextItemOpt: Option[Item]): Option[BindPathHolders] =
    findBindAndPathStatically(inDoc, controlName) map { case BindPath(bind, path) ⇒

      // Assume that namespaces in scope on leaf bind apply to ancestor binds (in theory mappings could be
      // overridden along the way!)
      val namespaces = new NamespaceMapping(bind.namespaceMappings.toMap.asJava)

      // Evaluate path from instance root element
      // NOTE: Don't pass Reporter as not very useful and some tests don't have a containingDocument scoped.
      BindPathHolders(
        bind,
        path,
        contextItemOpt map { contextItem ⇒
          eval(
            item       = contextItem,
            expr       = path map (_.value) mkString "/",
            namespaces = namespaces
          ).asInstanceOf[Seq[NodeInfo]].to[List]
        }
      )
    }

  def hasHTMLMediatype(nodes: Seq[NodeInfo]) =
    nodes exists (element ⇒ (element attValue "mediatype") == "text/html")

  //@XPathFunction
  def isSingleSelectionControl(localName: String) =
    localName == "select1" || localName.endsWith("-select1")

  //@XPathFunction
  def isMultipleSelectionControl(localName: String) =
    localName == "select" || localName.endsWith("-select")

  def searchControlsTopLevelOnly(
    body      : NodeInfo,
    data      : Option[NodeInfo],
    predicate : NodeInfo ⇒ Boolean
  ): Seq[ControlBindPathHolders] =
    searchControlBindPathHoldersInDoc(
      controls       = body descendant * filter IsControl,
      inDoc          = body,
      contextItemOpt = data map (_.rootElement),
      predicate      = predicate
    )

  def searchControlsUnderSectionTemplates(
    head      : NodeInfo,
    body      : NodeInfo,
    data      : Option[NodeInfo],
    predicate : NodeInfo ⇒ Boolean
  ): Seq[ControlBindPathHolders] =
    for {
      section                                                 ← findSectionsWithTemplates(body)
      sectionName                                             ← getControlNameOpt(section).toList
      BindPathHolders(_, sectionPath, sectionHoldersOpt)      ← findBindPathHoldersInDocument(body, sectionName, data map (_.rootElement)).toList

      contextItem ← sectionHoldersOpt match {
        case None | Some(Nil) ⇒ List(None)
        case Some(holders)    ⇒ holders map Some.apply
      }

      xblBinding                                              ← xblBindingForSection(head, section).toList
      ControlBindPathHolders(control, bind, path, holdersOpt) ← searchControlBindPathHoldersInDoc(
        controls       = xblBinding.rootElement / XBLTemplateTest descendant * filter IsControl,
        inDoc          = xblBinding,
        contextItemOpt = contextItem,
        predicate      = predicate
      )
    } yield
      ControlBindPathHolders(control, bind, sectionPath ::: path, holdersOpt)

  private object Private {

    def searchControlBindPathHoldersInDoc(
      controls       : Seq[NodeInfo],
      inDoc          : NodeInfo,
      contextItemOpt : Option[NodeInfo],
      predicate      : NodeInfo ⇒ Boolean
    ): Seq[ControlBindPathHolders] =
      for {
        control                              ← controls
        if predicate(control)
        bindId                               ← control.attValueOpt("bind").toList
        bindName                             ← controlNameFromIdOpt(bindId).toList
        BindPathHolders(bind, path, holders) ← findBindPathHoldersInDocument(inDoc, bindName, contextItemOpt).toList
      } yield
        ControlBindPathHolders(control, bind, path, holders)

    def xblBindingForSection(head: NodeInfo, section: NodeInfo): Option[DocumentWrapper] = {
      val mapping = sectionTemplateXBLBindingsByURIQualifiedName(head / XBLXBLTest)
      sectionTemplateBindingName(section) flatMap mapping.get
    }
  }
}

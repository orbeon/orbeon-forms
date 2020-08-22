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
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.fr.datamigration.MigrationSupport.{findMigrationForVersion, findMigrationOps}
import org.orbeon.oxf.fr.datamigration.PathElem
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.xml.NamespaceMapping
import org.orbeon.saxon.om.{DocumentInfo, Item, NodeInfo}
import org.orbeon.scaxon.SimplePath._
import org.orbeon.scaxon.XPath._
import org.orbeon.xforms.XFormsNames

import scala.collection.compat._

trait FormRunnerControlOps extends FormRunnerBaseOps {

  import Private._

  // Extensible records would be cool here. see:
  //
  // - https://github.com/lampepfl/dotty/issues/964
  // - https://github.com/milessabin/shapeless/wiki/Feature-overview:-shapeless-2.0.0#extensible-records
  //
  case class BindPath                       (                   bind: NodeInfo, path: List[PathElem]                         )
  case class BindPathHolders                (                   bind: NodeInfo, path: List[PathElem], holders: Option[List[NodeInfo]])
  case class ControlBindPathHoldersResources(control: NodeInfo, bind: NodeInfo, path: List[PathElem], holders: Option[List[NodeInfo]], resources: Seq[(String, NodeInfo)])

  def hasAllClassesPredicate(classNamesList: List[String])(control: NodeInfo): Boolean = {
    val controlClasses = control.attClasses
    classNamesList forall controlClasses.contains
  }

  def hasAnyClassPredicate(classNamesList: Set[String])(control: NodeInfo): Boolean = {
    val controlClasses = control.attClasses
    classNamesList exists controlClasses.contains
  }

  val LHHAInOrder = LHHA.values map (_.entryName) toList

  // Get the control name based on the control, bind, grid, section or template id
  //@XPathFunction
  def controlNameFromId(controlOrBindId: String): String =
    ControlOps.controlNameFromIdOpt(controlOrBindId).orNull

  //@XPathFunction
  def controlNameFromIdOpt(controlOrBindId: String) =
    ControlOps.controlNameFromIdOpt(controlOrBindId)

  // Whether the given id is for a control (given its reserved suffix)
  def isIdForControl(controlOrBindId: String): Boolean =
    ControlOps.controlNameFromIdOpt(controlOrBindId).isDefined

  // Whether the given node corresponds to a control
  // TODO: should be more restrictive
  val IsControl: NodeInfo => Boolean = hasName

  // Find a control by name (less efficient than searching by id)
  def findControlByName(inDoc: NodeInfo, controlName: String): Option[NodeInfo] = (
    for {
      suffix  <- PossibleControlSuffixes.iterator
      control <- findInViewTryIndex(inDoc, controlName + '-' + suffix).iterator
    } yield
      control
  ).nextOption()

  // Find a control id by name
  def findControlIdByName(inDoc: NodeInfo, controlName: String): Option[String] =
    findControlByName(inDoc, controlName) map (_.id)

  // Find a control element by name or null (the empty sequence)
  //@XPathFunction
  def findControlByNameOrEmpty(inDoc: NodeInfo, controlName: String): NodeInfo =
    findControlByName(inDoc, controlName).orNull

  // Get the control's name based on the control element
  def getControlName(control: NodeInfo): String = getControlNameOpt(control).get

  // Get the control's name based on the control element
  def getControlNameOpt(control: NodeInfo): Option[String] =
    control.idOpt flatMap controlNameFromIdOpt

  def hasName(control: NodeInfo): Boolean = getControlNameOpt(control).isDefined

  // Return a bind ref or nodeset attribute value if present
  def bindRefOpt(bind: NodeInfo): Option[String] =
    bind attValueOpt "ref"

  // Find a bind by name
  def findBindByName(inDoc: NodeInfo, name: String): Option[NodeInfo] =
    findInBindsTryIndex(inDoc, bindId(name))

  // Find a bind by name or null (the empty sequence)
  //@XPathFunction
  def findBindByNameOrEmpty(inDoc: NodeInfo, name: String): NodeInfo =
    findBindByName(inDoc, name).orNull

  // NOTE: Not sure why we search for anything but id or name, as a Form Runner bind *must* have an id and a name
  def isBindForName(bind: NodeInfo, name: String): Boolean =
    bind.hasIdValue(bindId(name)) || bindRefOpt(bind).contains(name) // also check ref/nodeset in case id is not present

  // Canonical way: use the `name` attribute
  def getBindNameOrEmpty(bind: NodeInfo): String =
    findBindName(bind).orNull

  def findBindName(bind: NodeInfo): Option[String] =
    bind attValueOpt "name"

  def findBindAndPathStatically(inDoc: NodeInfo, controlName: String): Option[BindPath] =
    findBindByName(inDoc, controlName) map { bindNode =>
      BindPath(bindNode, buildBindPath(bindNode))
    }

  // If `contextItemOpt` is `None`, don't search for holders.
  def findBindPathHoldersInDocument(
    inDoc          : NodeInfo,
    controlName    : String,
    contextItemOpt : Option[Item]
  ): Option[BindPathHolders] =
    findBindAndPathStatically(inDoc, controlName) map { case BindPath(bind, path) =>

      // Assume that namespaces in scope on leaf bind apply to ancestor binds (in theory mappings could be
      // overridden along the way!)
      val namespaces = NamespaceMapping(bind.namespaceMappings.toMap)

      // Evaluate path from instance root element
      // NOTE: Don't pass Reporter as not very useful and some tests don't have a containingDocument scoped.
      BindPathHolders(
        bind,
        path,
        contextItemOpt map { contextItem =>
          eval(
            item       = contextItem,
            expr       = path map (_.value) mkString "/",
            namespaces = namespaces
          ).asInstanceOf[Seq[NodeInfo]].to(List)
        }
      )
    }

  def hasHTMLMediatype(nodes: Seq[NodeInfo]): Boolean =
    nodes exists (element => (element attValue "mediatype") == "text/html")

  //@XPathFunction
  def isSingleSelectionControl(localName: String): Boolean =
    localName == "select1" || localName.endsWith("-select1")

  //@XPathFunction
  def isMultipleSelectionControl(localName: String): Boolean =
    localName == "select" || localName.endsWith("-select")

  def searchControlsInFormByClass(
    formDoc           : DocumentInfo,
    classes           : Set[String],
    dataFormatVersion : DataFormatVersion
  ): Seq[FormRunner.ControlBindPathHoldersResources] = {
    val headOpt = (formDoc / "*:html" / "*:head").headOption
    val bodyOpt = (formDoc / "*:html" / "*:body").headOption
    val controlBindPathHoldersResourcesList =
      bodyOpt.toList flatMap { body =>

        val topLevelOnly =
          FormRunner.searchControlsTopLevelOnly(
            body      = body,
            data      = None,
            predicate = FormRunner.hasAnyClassPredicate(classes)
          )

        val withSectionTemplatesOpt =
          headOpt map { head =>
            FormRunner.searchControlsUnderSectionTemplates(
              head      = head,
              body      = body,
              data      = None,
              predicate = FormRunner.hasAnyClassPredicate(classes)
            )
          }

        topLevelOnly ++ withSectionTemplatesOpt.toList.flatten
      }
    dataFormatVersion match {
      case DataFormatVersion.Edge => controlBindPathHoldersResourcesList
      case _                      =>
        val (_, migrationOpsToApply) =
          findMigrationOps(
            srcVersion = DataFormatVersion.Edge,
            dstVersion = dataFormatVersion
          )

        // Find the migration functions once and for all
        val pathMigrationFunctions =
          for {
            metadataElem <- FormRunner.metadataInstanceRootOpt(formDoc).toList
            ops          <- migrationOpsToApply
            json         <- findMigrationForVersion(metadataElem, ops.version)
          } yield
            ops.adjustPathTo40(ops.decodeMigrationSetFromJson(json), _)
        controlBindPathHoldersResourcesList map { controlBindPathHoldersResources =>
          val path = controlBindPathHoldersResources.path
          val adjustedBindPathElems =
            (pathMigrationFunctions.iterator flatMap (_.apply(path)) nextOption()) getOrElse path
          controlBindPathHoldersResources.copy(path = adjustedBindPathElems)
        }
    }
  }

  def searchControlsTopLevelOnly(
    body      : NodeInfo,
    data      : Option[NodeInfo],
    predicate : NodeInfo => Boolean
  ): Seq[ControlBindPathHoldersResources] =
    searchControlBindPathHoldersInDoc(
      controlElems       = body descendant * filter IsControl,
      inDoc          = body,
      contextItemOpt = data map (_.rootElement),
      predicate      = predicate
    )

  def searchControlsUnderSectionTemplates(
    head      : NodeInfo,
    body      : NodeInfo,
    data      : Option[NodeInfo],
    predicate : NodeInfo => Boolean
  ): Seq[ControlBindPathHoldersResources] =
    for {
      section         <- findSectionsWithTemplates(body)
      sectionName     <- getControlNameOpt(section).toList

      BindPathHolders(
        _,
        sectionPath,
        sectionHoldersOpt
      )              <- findBindPathHoldersInDocument(body, sectionName, data map (_.rootElement)).toList

      contextItemOpt <- sectionHoldersOpt match {
                         case None | Some(Nil) => List(None)
                         case Some(holders)    => holders map Some.apply
                       }

      xblBinding     <- xblBindingForSection(head, section).toList

      ControlBindPathHoldersResources(
        control,
        bind,
        path,
        holdersOpt,
        labels
      )              <- searchControlBindPathHoldersInDoc(
                         controlElems   = xblBinding.rootElement / XBLTemplateTest descendant * filter IsControl,
                         inDoc          = xblBinding,
                         contextItemOpt = contextItemOpt,
                         predicate      = predicate
                       )
    } yield
      ControlBindPathHoldersResources(control, bind, sectionPath ::: path, holdersOpt, labels)

  // NOTE: Return at most one `ControlBindPathHoldersResources` per incoming control element.
  def searchControlBindPathHoldersInDoc(
    controlElems   : Seq[NodeInfo],
    inDoc          : NodeInfo,
    contextItemOpt : Option[NodeInfo],
    predicate      : NodeInfo => Boolean
  ): Seq[ControlBindPathHoldersResources] =
    for {
      control                              <- controlElems
      if predicate(control)
      bindId                               <- control.attValueOpt(XFormsNames.BIND_QNAME).toList
      controlName                          <- controlNameFromIdOpt(bindId).toList
      BindPathHolders(bind, path, holders) <- findBindPathHoldersInDocument(inDoc, controlName, contextItemOpt).toList
      resourceHoldersWithLang              = FormRunnerResourcesOps.findResourceHoldersWithLangUseDoc(inDoc, controlName)
    } yield
      ControlBindPathHoldersResources(control, bind, path, holders, resourceHoldersWithLang)

  def xblBindingForSection(head: NodeInfo, section: NodeInfo): Option[DocumentWrapper] = {
    val mapping = sectionTemplateXBLBindingsByURIQualifiedName(head / XBLXBLTest)
    sectionTemplateBindingName(section) flatMap mapping.get
  }

  private object Private {

    val FBLangPredicate         = "[@xml:lang = $fb-lang]"
    val PossibleControlSuffixes = List("control", "grid", "section", "repeat")

    // Find a bind by predicate
    def findBind(inDoc: NodeInfo, p: NodeInfo => Boolean): Option[NodeInfo] =
      findTopLevelBind(inDoc).toSeq descendant "*:bind" find p

    // 2017-04-25: Don't use enclosing parentheses anymore. This now ensures that the `ref` is a single
    // reference to an XML element. See https://github.com/orbeon/orbeon-forms/issues/3174.
    //
    // Also, specific case for Form Builder: drop language predicate, as we want to index/return values
    // for all languages. So far, this is handled as a special case, as this is not something that happens
    // in other forms.
    def buildBindPath(bind: NodeInfo): List[PathElem] =
      (bind ancestorOrSelf XFBindTest flatMap bindRefOpt).reverse.tail map { bindRef =>
        PathElem(
          if (bindRef.endsWith(FBLangPredicate))
            bindRef.dropRight(FBLangPredicate.length)
          else
            bindRef
        )
      } toList
  }
}

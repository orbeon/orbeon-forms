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
package org.orbeon.oxf.fb

import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.NodeInfoFactory.elementInfo
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._

trait ResourcesOps extends BaseOps {

  val HelpRefMatcher = """\$form-resources/([^/]+)/help""".r

  def currentResources(implicit ctx: FormBuilderDocContext): NodeInfo = ctx.formBuilderModel.get.unsafeGetVariableAsNodeInfo("current-resources")
  def currentLang     (implicit ctx: FormBuilderDocContext): String   = currentResources attValue XMLLangQName
  def resourcesRoot   (implicit ctx: FormBuilderDocContext): NodeInfo = currentResources.parentUnsafe

  def resourcesInLang(lang: String)(implicit ctx: FormBuilderDocContext): NodeInfo =
    allResources(resourcesRoot) find (_.attValue(XMLLangQName) == lang) getOrElse currentResources

  // Find the current resource holder for the given name
  def findCurrentResourceHolder(controlName: String)(implicit ctx: FormBuilderDocContext): Option[NodeInfo] =
    currentResources child controlName headOption

  // Get the control's resource value or blank
  def getControlResourceOrEmpty(
    controlName  : String,
    resourceName : String)(implicit
    ctx          : FormBuilderDocContext
  ): String =
    findCurrentResourceHolder(controlName) flatMap
      (_ elemValueOpt resourceName) getOrElse ""

  // Get the control's resource holders (e.g. in the case of alerts there will be multiple of those
  def getControlResources(
    controlName  : String,
    resourceName : String)(implicit
    ctx          : FormBuilderDocContext
  ): List[NodeInfo] =
    findCurrentResourceHolder(controlName).toList flatMap
      (n => n / resourceName)

  // NOTE: Doesn't enforce that the same number of e.g. <alert> elements are present per lang
  def getControlResourcesWithLang(
    controlName  : String,
    resourceName : String,
    langs        : Iterable[String])(implicit
    ctx          : FormBuilderDocContext
  ): Seq[(String, Seq[NodeInfo])] = {
    val langsSet = langs.toSet
    findResourceHoldersWithLang(controlName, resourcesRoot) collect {
      case (lang, holder) if langsSet(lang) => lang -> (holder child resourceName)
    }
  }

  // Set a control's resources given lang/values
  //
  // - only touches the specified languages
  // - simplicity and consistency, all existing resources are first deleted
  // - the maximum number of values across langs is determined to create a consistent number of resources
  // - if values are missing for a given lang, the remaining resources will exist bug be empty
  //
  def setControlResourcesWithLang(
    controlName  : String,
    resourceName : String,
    langValues   : Seq[(String, Seq[String])])(implicit
    ctx          : FormBuilderDocContext
  ): Unit = {

    val maxResources = if (langValues.nonEmpty) langValues map (_._2.size) max else 0
    val langs        = langValues map (_._1)

    delete(getControlResourcesWithLang(controlName, resourceName, langs) flatMap (_._2))

    if (maxResources > 0) {
      val valuesMap = langValues.toMap
      for {
        (lang, holders) <- ensureResourceHoldersForLangs(controlName, resourceName, maxResources, langs)
        valuesForLang   <- valuesMap.get(lang)
        (holder, value) <- holders zip valuesForLang
      } locally {
        setvalue(holder, value)
      }
    }
  }

  def hasItemHintEditor(controlName: String)(implicit ctx: FormBuilderDocContext): Boolean =
    findControlByName(controlName) exists (e => FormBuilder.hasEditor(e, "item-hint"))

  // Get the control's items for all languages
  def getControlItemsGroupedByValue(controlName: String)(implicit ctx: FormBuilderDocContext): Seq[NodeInfo] = {

    val localResourcesRoot = resourcesRoot

    val holdersWithLang = findResourceHoldersWithLang(controlName, localResourcesRoot)

    // All unique values in the order they appear
    val distinctValues = (
      for {
        (lang, holder) <- holdersWithLang
        item           <- holder / "item"
      } yield
        item / "value" stringValue
    ).distinct

    // All languages in the order they appear
    val allLangs =
      holdersWithLang map (_._1)

    def holderItemForValue(holder: NodeInfo, value: String) =
      holder / "item" find (_ / "value" === value)

    def lhhaForLangAndValue(lang: String, value: String, lhha: String) = (
      findResourceHolderForLang(controlName, lang, localResourcesRoot)
      map (holderItemForValue(_, value).toList / lhha stringValue)
      getOrElse ""
    )

    val addHints = hasItemHintEditor(controlName)

    val newItemElems =
      for (value <- distinctValues)
      yield
        <item>
          {
            for (lang <- allLangs)
            yield
              <label lang={lang}>{lhhaForLangAndValue(lang, value, "label")}</label> ::
              (addHints list <hint lang={lang}>{lhhaForLangAndValue(lang, value, "hint")}</hint>)
          }
          <value>{value}</value>
        </item>

    def emptyItemElem =
      <item>
        {
          for (lang <- allLangs)
          yield
            <label lang={lang}/> :: (addHints list <hint lang={lang}/>)
        }
        <value/>
      </item>

    def itemElemsToReturn =
      if (newItemElems.nonEmpty) newItemElems else List(emptyItemElem)

    itemElemsToReturn map elemToNodeInfo
  }

  // Set a control's current resource for the current language
  // Return `true` if changed
  def setControlResource(
    controlName  : String,
    resourceName : String,
    value        : String
  )(implicit ctx: FormBuilderDocContext): Boolean = {
    val resourceHolder = ensureResourceHolder(controlName, resourceName)
    setvalue(resourceHolder, value) exists (_._2)
  }

  // Delete existing control resources and set new resource values
  // Unused as of 2017-10-12.
//  def setControlResources(controlName: String, resourceName: String, values: Seq[String]): Unit = {
//    // For simplicity and consistency, delete and recreate
//    delete(getControlResources(controlName, resourceName))
//    val resourceHolders = ensureResourceHoldersForCurrentLang(controlName, resourceName, values.size)
//    resourceHolders zip values foreach { case (holder, value) => setvalue(holder, value) }
//  }

  // Ensure the existence of the resource holder for the current language
  // NOTE: Assume enclosing control resource holder already exists
  def ensureResourceHolder(
    controlName  : String,
    resourceName : String)(implicit
    ctx          : FormBuilderDocContext
  ): NodeInfo =
    ensureResourceHoldersForCurrentLang(controlName, resourceName, 1).head

  // NOTE: Assume enclosing control resource holder already exists
  def ensureResourceHoldersForCurrentLang(
    controlName  : String,
    resourceName : String,
    count        : Int)(implicit
    ctx          : FormBuilderDocContext
  ): Seq[NodeInfo] =
    ensureResourceHoldersForLang(controlName, resourceName, count, currentLang)

  // NOTE: Assume enclosing control resource holder already exists
  def ensureResourceHoldersForLang(
    controlName  : String,
    resourceName : String,
    count        : Int,
    lang         : String)(implicit
    ctx          : FormBuilderDocContext
  ): Seq[NodeInfo] = {
    val controlHolder = findResourceHolderForLang(controlName, lang, resourcesRoot).get

    val existing = controlHolder child resourceName

    if (existing.size > count) {
      delete(existing drop count)
      existing take count
    } else if (existing.size == count)
      existing
    else
      insertElementsImposeOrder(
        into   = controlHolder,
        origin = 1 to count - existing.size map (_ => elementInfo(resourceName)),
        order  = LHHAInOrder
      )
  }

  // NOTE: Assume enclosing control resource holder already exists
  def ensureResourceHoldersForLangs(
    controlName  : String,
    resourceName : String,
    count        : Int = 1,
    langs        : Seq[String])(implicit // TODO: `langs` is not used. Oversight or correct?
    ctx          : FormBuilderDocContext
  ): Seq[(String, Seq[NodeInfo])] =
    allLangs(resourcesRoot) map
      (lang => lang -> ensureResourceHoldersForLang(controlName, resourceName, count, lang))

  def findResourceHolderForLang(
    controlName       : String,
    lang              : String,
    resourcesRootElem : NodeInfo
  ): Option[NodeInfo] =
    findResourceHoldersWithLang(controlName ensuring (_.nonAllBlank), resourcesRootElem) collectFirst
      { case (`lang`, holder) => holder }

  // Find control resource holders
  def findResourceHolders(controlName: String)(implicit ctx: FormBuilderDocContext): Seq[NodeInfo] =
    findResourceHoldersWithLang(controlName, resourcesRoot) map (_._2)

  // For the given bind and lang, find all associated resource holders
  def iterateSelfAndDescendantBindsResourceHolders(
    rootBind          : NodeInfo,
    lang              : String,
    resourcesRootElem : NodeInfo
  ): Iterator[NodeInfo] =
    for {
      bindNode <- FormBuilder.iterateSelfAndDescendantBinds(rootBind)
      bindName <- findBindName(bindNode)
      holder   <- findResourceHolderForLang(bindName, lang, resourcesRootElem)
    } yield
      holder

  // NOTE: Also return `Nil` if there are any children `fr:param`.
  def holdersToRemoveIfHasBlankOrMissingLHHAForAllLangs(
    controlName  : String,
    lhhaElements : Iterable[NodeInfo],
    lhhaName     : String)(implicit
    ctx          : FormBuilderDocContext
  ): (Boolean, Seq[NodeInfo]) = {

    val hasParams =
      lhhaElements exists (e => FormBuilder.lhhatChildrenParams(e).nonEmpty)

    if (hasParams) {
      false -> Nil
    } else {

      val holders = findResourceHoldersWithLangUseDocUseContext(controlName)

      val allBlankOrMissing =
        holders forall { case (_, controlHolder) => (controlHolder child lhhaName stringValue).isAllBlank }

      allBlankOrMissing -> (
        if (allBlankOrMissing)
          holders flatMap { case (_, controlHolder) => controlHolder child lhhaName}
        else
          Nil
      )
    }
  }
}

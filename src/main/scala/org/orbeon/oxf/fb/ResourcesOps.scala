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

import org.apache.commons.lang3.StringUtils
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.scaxon.XML._

trait ResourcesOps extends BaseOps {

  def currentResources      = asNodeInfo(topLevelModel("fr-form-model").get.getVariable("current-resources"))
  def currentLang           = currentResources attValue "*:lang"
  def resourcesRoot         = currentResources parent * head

  def resourcesInLang(lang: String)      = allResources(resourcesRoot) find (_.attValue("*:lang") == lang) getOrElse currentResources
  def allResources(resources: NodeInfo)  = resources child "resource"
  def allLangs(resources: NodeInfo)      = allResources(resources) attValue "*:lang"

  def allLangsWithResources(resources: NodeInfo) =
    allLangs(resources) zip allResources(resources)

  // Find the current resource holder for the given name
  def findCurrentResourceHolder(controlName: String) =
    currentResources child controlName headOption

  // Get the control's resource value or blank
  def getControlResourceOrEmpty(controlName: String, resourceName: String) =
    findCurrentResourceHolder(controlName) flatMap
      (n ⇒ n \ resourceName map (_.stringValue) headOption) getOrElse ""

  // Get the control's resource holders (e.g. in the case of alerts there will be multiple of those
  def getControlResources(controlName: String, resourceName: String) =
    findCurrentResourceHolder(controlName).toList flatMap
      (n ⇒ n \ resourceName)

  // NOTE: Doesn't enforce that the same number of e.g. <alert> elements are present per lang
  def getControlResourcesWithLang(controlName: String, resourceName: String, langs: Seq[String] = allLangs(resourcesRoot)) = {
    val langsSet = langs.toSet
    findResourceHoldersWithLang(controlName, resourcesRoot) collect {
      case (lang, holder) if langsSet(lang) ⇒ lang → (holder child resourceName)
    }
  }

  // Set a control's resources given lang/values
  // - only touches the specified languages
  // - simplicity and consistency, all existing resources are first deleted
  // - the maximum number of values across langs is determined to create a consistent number of resources
  // - if values are missing for a given lang, the remaining resources will exist bug be empty
  def setControlResourcesWithLang(controlName: String, resourceName: String, langValues: Seq[(String, Seq[String])]): Unit = {

    val maxResources = if (langValues.nonEmpty) langValues map (_._2.size) max else 0
    val langs        = langValues map (_._1)

    delete(getControlResourcesWithLang(controlName, resourceName, langs) flatMap (_._2))

    if (maxResources > 0) {
      val valuesMap = langValues.toMap
      for {
        (lang, holders) ← ensureResourceHoldersForLangs(controlName, resourceName, maxResources, langs)
        valuesForLang   ← valuesMap.get(lang)
        (holder, value) ← holders zip valuesForLang
      } locally {
        setvalue(holder, value)
      }
    }
  }

  def getControlHelpOrEmpty(controlName: String) =
    getControlResourceOrEmpty(controlName, "help")

  def hasItemHintEditor(controlName: String) =
    findControlByName(getFormDoc, controlName) exists (e ⇒ FormBuilder.hasEditor(e, "item-hint"))

  // Get the control's items for all languages
  def getControlItemsGroupedByValue(controlName: String): Seq[NodeInfo] = {

    val localResourcesRoot = resourcesRoot

    val holdersWithLang = findResourceHoldersWithLang(controlName, localResourcesRoot)

    // All unique values in the order they appear
    val distinctValues = (
      for {
        (lang, holder) ← holdersWithLang
        item           ← holder / "item"
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
      for (value ← distinctValues)
      yield
        <item>
          {
            for (lang ← allLangs)
            yield
              <label lang={lang}>{lhhaForLangAndValue(lang, value, "label")}</label> ::
              (addHints list <hint lang={lang}>{lhhaForLangAndValue(lang, value, "hint")}</hint>)
          }
          <value>{value}</value>
        </item>

    def emptyItemElem =
      <item>
        {
          for (lang ← allLangs)
          yield
            <label lang={lang}/> :: (addHints list <hint lang={lang}/>)
        }
        <value/>
      </item>

    def itemElemsToReturn =
      if (newItemElems.nonEmpty) newItemElems else List(emptyItemElem)

    itemElemsToReturn map elemToNodeInfo
  }

  // Set the control's items for all languages
  def setControlItems(controlName: String, items: NodeInfo): Unit = {

    val addHints = hasItemHintEditor(controlName)

    for ((lang, holder) ← findResourceHoldersWithLang(controlName, resourcesRoot)) {

      delete(holder / "item")

      val newItemElems =
        for (item ← items / "item")
        yield {
          <item>
            <label>{item / "label" filter (_.attValue("lang") == lang) stringValue}</label>
            {
              if (addHints)
                <hint>{ item / "hint"  filter (_.attValue("lang") == lang) stringValue}</hint>
            }
            <value>{item / "value" stringValue}</value>
          </item>
        }

      insert(into = holder, after = holder / *, origin = newItemElems map elemToNodeInfo toList)
    }
  }

  // Set a control's current resource for the current language
  def setControlResource(controlName: String, resourceName: String, value: String) = {
    val resourceHolder = ensureResourceHolder(controlName, resourceName)
    setvalue(resourceHolder, value)
  }

  // Delete existing control resources and set new resource values
  def setControlResources(controlName: String, resourceName: String, values: Seq[String]) = {
    // For simplicity and consistency, delete and recreate
    delete(getControlResources(controlName, resourceName))
    val resourceHolders = ensureResourceHoldersForCurrentLang(controlName, resourceName, values.size)
    resourceHolders zip values foreach { case (holder, value) ⇒ setvalue(holder, value) }
  }

  // Ensure the existence of the resource holder for the current language
  // NOTE: Assume enclosing control resource holder already exists
  def ensureResourceHolder(controlName: String, resourceName: String) =
    ensureResourceHoldersForCurrentLang(controlName, resourceName, 1).head

  // NOTE: Assume enclosing control resource holder already exists
  def ensureResourceHoldersForCurrentLang(controlName: String, resourceName: String, count: Int) =
    ensureResourceHoldersForLang(controlName, resourceName, count, currentLang)

  // NOTE: Assume enclosing control resource holder already exists
  def ensureResourceHoldersForLang(controlName: String, resourceName: String, count: Int, lang: String) = {
    val controlHolder = findResourceHolderForLang(controlName, lang, resourcesRoot).get

    val existing = controlHolder child resourceName

    if (existing.size > count) {
      delete(existing drop count)
      existing take count
    } else if (existing.size == count)
      existing
    else
      insertElementsImposeOrder(into = controlHolder, origin = 1 to count - existing.size map (_ ⇒ elementInfo(resourceName)), order = LHHAInOrder)
  }

  // NOTE: Assume enclosing control resource holder already exists
  def ensureResourceHoldersForLangs(controlName: String, resourceName: String, count: Int = 1, langs: Seq[String] = allLangs(resourcesRoot)) =
    allLangs(resourcesRoot) map (lang ⇒ lang → ensureResourceHoldersForLang(controlName, resourceName, count, lang))

  def findResourceHolderForLang(controlName: String, lang: String, resources: NodeInfo) =
    findResourceHoldersWithLang(controlName ensuring (StringUtils.isNotBlank(_)), resources) collectFirst { case (`lang`, holder) ⇒ holder }

  // Find control resource holders
  def findResourceHolders(controlName: String): Seq[NodeInfo] =
    findResourceHoldersWithLang(controlName, resourcesRoot) map (_._2)

  // Find control resource holders with their language
  def findResourceHoldersWithLang(controlName: String, resources: NodeInfo): Seq[(String, NodeInfo)] =
    for {
      (lang, resource) ← allLangsWithResources(resources)
      holder           ← resource child controlName headOption // there *should* be only one
    } yield
      (lang, holder)

  // For the given bind and lang, find all associated resource holders
  def iterateSelfAndDescendantBindsResourceHolders(rootBind: NodeInfo, lang: String, resources: NodeInfo) =
    for {
      bindNode ← FormBuilder.iterateSelfAndDescendantBinds(rootBind)
      bindName ← findBindName(bindNode)
      holder   ← findResourceHolderForLang(bindName, lang, resources)
    } yield
      holder

  //@XPathFunction
  def iterateSelfAndDescendantBindsResourceHoldersXPath(rootBind: NodeInfo, lang: String, resources: NodeInfo): SequenceIterator =
    iterateSelfAndDescendantBindsResourceHolders(rootBind, lang, resources)

  // Same as above but doesn't require a Form Builder context
  def findResourceHoldersWithLangUseDoc(inDoc: NodeInfo, controlName: String): Seq[(String, NodeInfo)] =
    findResourceHoldersWithLang(controlName, resourcesInstanceRoot(inDoc))

  def lhhaHoldersForAllLangsUseDoc(inDoc: NodeInfo, controlName: String, lhha: String) =
    for {
      (_, controlHolder) ← findResourceHoldersWithLangUseDoc(inDoc, controlName)
      lhhaHolder         ← controlHolder child lhha
    } yield
      lhhaHolder

  def hasBlankOrMissingLHHAForAllLangsUseDoc(inDoc: NodeInfo, controlName: String, lhha: String) =
    findResourceHoldersWithLangUseDoc(inDoc, controlName) forall
    { case (_, holder) ⇒ StringUtils.isBlank(holder child lhha stringValue) }
}

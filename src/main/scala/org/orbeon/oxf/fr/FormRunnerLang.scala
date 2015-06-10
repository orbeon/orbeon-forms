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

import java.util.{List ⇒ JList}
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.NetUtils
import org.orbeon.saxon.om.{NodeInfo, Item}
import collection.JavaConverters._
import org.orbeon.oxf.pipeline.api.ExternalContext.Request
import org.orbeon.oxf.externalcontext.ExternalContextOps._
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.xforms.action.XFormsAPI._

// NOTE: Language is currently assumed to be only the plain language part, e.g. "en", "it", "zh".
trait FormRunnerLang {

    import FormRunner._

    case class AppForm(app: String, form: String) {
        val toList = List(app, form)
    }

    // The client passes "*" or blank to indicate that there is no current app/form name
    def hasAppForm(app: String, form: String) = app != "*" && app.nonEmpty && form != "*" && form.nonEmpty
    def getAppForm(app: String, form: String) = hasAppForm(app, form) option AppForm(app, form)

    def currentLang          = asNodeInfo(topLevelModel(ResourcesModel).get.getVariable("lang"))
    def currentFRLang        = asNodeInfo(topLevelModel(ResourcesModel).get.getVariable("fr-lang"))
    def currentFRResources   = asNodeInfo(topLevelModel(ResourcesModel).get.getVariable("fr-fr-resources"))
    //@XPathFunction
    def currentFormResources = asNodeInfo(topLevelModel(ResourcesModel).get.getVariable("fr-form-resources"))
    def allResources(resources: NodeInfo)  = resources child "resource"

    def formResourcesInLang(lang: String): NodeInfo = {
        val formResources = topLevelModel("fr-form-model").get.getInstance("fr-form-resources").documentInfo.rootElement
        (formResources \ *).find(_.attValue("*:lang") == lang).getOrElse(currentFormResources)
    }

    // List of available languages for the given form
    // Empty if the form doesn't have resources
    // If all of the form's resources are filtered via property, return the first language of the form, if any.
    //@XPathFunction
    def getFormLangSelection(app: String, form: String, formLanguages: JList[String]): List[String] = {

        val appForm = getAppForm(app, form)

        val allowedFormLanguages = formLanguages.asScala.toList filter isAllowedLang(appForm)
        val defaultLanguage = getDefaultLang(appForm)

        // Reorder to put default language first if it is allowed
        if (allowedFormLanguages contains defaultLanguage)
            defaultLanguage :: (allowedFormLanguages filterNot (_ == defaultLanguage))
        else
            allowedFormLanguages
    }

    // Find the best match for the current form language
    // Can be null (empty sequence) if there are no resources (or no allowed resources) in the form
    //@XPathFunction
    def selectFormLang(app: String, form: String, requestedLang: String, formLangs: JList[String]): String = {

        val appForm = getAppForm(app, form)

        val availableFormLangs  = getFormLangSelection(app, form, formLangs)
        val actualRequestedLang = findRequestedLang(appForm, requestedLang) filter isAllowedLang(appForm)

        selectLangUseDefault(appForm, actualRequestedLang, availableFormLangs).orNull
    }

    // Get the Form Runner language
    // If possible, try to match the form language, otherwise
    //@XPathFunction
    def selectFormRunnerLang(app: String, form: String, requestedLang: String, formRunnerLangs: JList[String]): String = {
        val appForm = getAppForm(app, form)
        val actualRequestedLang = findRequestedLang(appForm, requestedLang) filter isAllowedLang(appForm)
        selectLangUseDefault(appForm, actualRequestedLang, formRunnerLangs.asScala.toList).orNull
    }

    // Get the default language for the given app/form
    // If none is configured, return the global default "en"
    // Public for unit tests
    def getDefaultLang(appForm: Option[AppForm]): String = {
        val suffix = appForm.toList flatMap (_.toList)
        Option(properties.getString("oxf.fr.default-language" :: suffix mkString ".")) map  cleanLanguage getOrElse "en"
    }

    // Return a predicate telling whether a language is allowed based on properties. If app/form are specified, then the
    // result applies to that app/form, otherwise it is valid globally for Form Runner.
    // Public for unit tests
    def isAllowedLang(appForm: Option[AppForm]): String ⇒ Boolean = {
        val suffix = appForm.toList flatMap (_.toList)
        val set    = stringOptionToSet(Option(properties.getString("oxf.fr.available-languages" :: suffix mkString "."))) map cleanLanguage
        // If none specified via property or property contains a wildcard, all languages are considered available
        if (set.isEmpty || set("*")) _ ⇒ true else set
    }

    // The requested language, trying a few things in order (given parameter, request, session, default)
    // Public for unit tests
    def findRequestedLang(appForm: Option[AppForm], requestedLang: String): Option[String] = {
        val request = NetUtils.getExternalContext.getRequest

        def fromHeader  = request.getFirstHeader       (LiferayLanguageHeader) map cleanLanguage
        def fromRequest = request.getFirstParamAsString(LanguageParam)         map cleanLanguage
        def fromSession = stringFromSession(request, LanguageParam)

        nonEmptyOrNone(requestedLang) orElse
            fromHeader                orElse
            fromRequest               orElse
            fromSession               orElse
            Some(getDefaultLang(appForm))
    }

    // Support e.g. en_US with underscore, but normalize to en-US and only keep the language part for now. Later we can
    // support country/variant etc. See Java Locale and comments in XXFormsFormatMessage.
    private def cleanLanguage(lang: String) =
        split[List](lang, "-_").head

    private def selectLangUseDefault(appForm: Option[AppForm], requestedLang: Option[String], availableLangs: List[String]) = {
        def matchingLanguage = availableLangs intersect requestedLang.toList headOption
        def defaultLanguage  = availableLangs intersect List(getDefaultLang(appForm)) headOption
        def firstLanguage    = availableLangs headOption

        matchingLanguage orElse defaultLanguage orElse firstLanguage
    }

    private def stringFromSession(request: Request, name: String) =
        Option(request.getSession(false)) flatMap
            (s ⇒ Option(s.getAttributesMap.get(LanguageParam))) map {
                case item: Item ⇒ item.getStringValue
                case other ⇒ other.toString
            }
}

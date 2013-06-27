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
import org.apache.commons.lang3.StringUtils
import org.orbeon.saxon.om.{Item, NodeInfo}
import collection.JavaConverters._
import org.orbeon.oxf.pipeline.api.ExternalContext.Request
import org.orbeon.scaxon.XML
import XML._

trait FormRunnerLang {

    import FormRunner._

    // List of available languages for the given form
    // Empty if the form doesn't have resources
    // If all of the form's resources are filtered via property, return the first language of the form, if any.
    def getFormLangSelection(app: String, form: String, formLanguages: JList[String]): JList[String] = {

        val allowedFormLanguages = formLanguages.asScala.toList filter isAllowedLang(app, form)
        val defaultLanguage = getDefaultLang(app, form)

        val withDefaultPrepended =
            if (allowedFormLanguages contains defaultLanguage)
                defaultLanguage :: (allowedFormLanguages filterNot (_ == defaultLanguage))
            else
                allowedFormLanguages

        withDefaultPrepended.asJava
    }

    // Find the best match for the current form language
    // Can be null (empty sequence) if there are no resources (or no allowed resources) in the form
    def selectFormLang(app: String, form: String, requestedLang: String, formLangs: JList[String]): String = {

        val availableFormLangs  = getFormLangSelection(app, form, formLangs).asScala.toList
        val actualRequestedLang = findRequestedLang(app, form, requestedLang) filter isAllowedLang(app, form)

        selectLangUseDefault(app, form, actualRequestedLang, availableFormLangs).orNull
    }

    // Get the Form Runner language
    // If possible, try to match the form language, otherwise
    def selectFormRunnerLang(app: String, form: String, formLang: String, formRunnerLangs: JList[String]): String =
        selectLangUseDefault(app, form, Option(formLang), formRunnerLangs.asScala.toList).orNull

    // Get the default language for the given app/form
    // If none is configured, return the global default "en"
    // Public for unit tests
    def getDefaultLang(app: String, form: String): String =
        Option(properties.getString(Seq("oxf.fr.default-language", app, form) mkString ".")) getOrElse "en"

    // Return a predicate telling whether a language is allowed for the given form, based on properties
    // Public for unit tests
    def isAllowedLang(app: String, form: String): String ⇒ Boolean = {
        val set = stringOptionToSet(Option(properties.getString(Seq("oxf.fr.available-languages", app, form) mkString ".")))
        // If none specified via property or property contains a wildcard, all languages are considered available
        if (set.isEmpty || set("*")) _ ⇒ true else set
    }

    // The requested language, trying a few things in order (given parameter, request, session, default)
    // Public for unit tests
    def findRequestedLang(app: String, form: String, requestedLang: String): Option[String] = {
        val request = NetUtils.getExternalContext.getRequest

        def fromRequest = Option(request.getParameterMap.get("fr-language")) flatMap (_.lift(0)) map (_.toString)
        def fromSession = stringFromSession(request, "fr-language")

        Option(StringUtils.trimToNull(requestedLang)) orElse
            fromRequest orElse
            fromSession orElse
            Option(getDefaultLang(app, form))
    }

    // Get a field's label for the summary page
    def summaryLanguage(name: String, resources: NodeInfo): String = {
        def resourceLabelOpt = (resources \ name \ "label" map (_.getStringValue)).headOption
        resourceLabelOpt getOrElse '[' + name + ']'
    }

    private def selectLangUseDefault(app: String, form: String, requestedLang: Option[String], availableLangs: List[String]) = {
        def matchingLanguage = availableLangs intersect requestedLang.toList headOption
        def defaultLanguage  = availableLangs intersect List(getDefaultLang(app, form)) headOption
        def firstLanguage    = availableLangs headOption

        matchingLanguage orElse defaultLanguage orElse firstLanguage
    }

    private def stringFromSession(request: Request, name: String) =
        Option(request.getSession(false)) flatMap
            (s ⇒ Option(s.getAttributesMap.get("fr-language"))) map {
                case item: Item ⇒ item.getStringValue
                case other ⇒ other.toString
            }
}

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

import java.util.{List => JList}

import org.orbeon.oxf.externalcontext.ExternalContext.Request
import org.orbeon.oxf.fr.Names._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.instruct.NumberInstruction
import org.orbeon.saxon.om.{Item, NodeInfo}
import org.orbeon.scaxon.SimplePath._

import scala.jdk.CollectionConverters._

// NOTE: Language is currently assumed to be only the plain language part, e.g. "en", "it", "zh".
trait FormRunnerLang {

  import Private._
  import FormRunner._

  // The client passes "*" or blank to indicate that there is no current app/form name
  def hasAppForm(app: String, form: String) = app != "*" && app.nonEmpty && form != "*" && form.nonEmpty
  def getAppForm(app: String, form: String) = hasAppForm(app, form) option AppForm(app, form)

  def currentLang          = topLevelModel(ResourcesModel).get.unsafeGetVariableAsNodeInfo("lang").stringValue
  def currentFRLang        = topLevelModel(ResourcesModel).get.unsafeGetVariableAsNodeInfo("fr-lang").stringValue
  def currentFRResources   = topLevelModel(ResourcesModel).get.unsafeGetVariableAsNodeInfo("fr-fr-resources")
  //@XPathFunction
  def currentFormResources = topLevelModel(ResourcesModel).get.unsafeGetVariableAsNodeInfo("fr-form-resources")

  def formResourcesInLang(lang: String): NodeInfo = {
    val formResources = topLevelModel(FormModel).get.getInstance(FormResources).documentInfo.rootElement
    (formResources / *).find(_.attValue("*:lang") == lang).getOrElse(currentFormResources)
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
    properties.getNonBlankString("oxf.fr.default-language" :: suffix mkString ".") map  cleanLanguage getOrElse "en"
  }

  // Return a predicate telling whether a language is allowed based on properties. If app/form are specified, then the
  // result applies to that app/form, otherwise it is valid globally for Form Runner.
  // Public for unit tests
  def isAllowedLang(appForm: Option[AppForm]): String => Boolean = {
    val suffix = appForm.toList flatMap (_.toList)
    val set    = stringOptionToSet(properties.getNonBlankString("oxf.fr.available-languages" :: suffix mkString ".")) map cleanLanguage
    // If none specified via property or property contains a wildcard, all languages are considered available
    if (set.isEmpty || set("*")) _ => true else set
  }

  // The requested language, trying a few things in order (given parameter, request, session, default)
  // Public for unit tests
  def findRequestedLang(appForm: Option[AppForm], requestedLang: String): Option[String] = {

    val request = NetUtils.getExternalContext.getRequest

    def fromHeader  = request.getFirstHeader       (LiferayLanguageHeader) map cleanLanguage
    def fromRequest = request.getFirstParamAsString(LanguageParam)         map cleanLanguage
    def fromSession = stringFromSession(request, LanguageParam)

    requestedLang.trimAllToOpt orElse
      fromHeader               orElse
      fromRequest              orElse
      fromSession              orElse
      Some(getDefaultLang(appForm))
  }

  // Whether there is a Saxon XPath numberer for the given language
  //@XPathFunction
  def hasXPathNumberer(lang: String) =
    NumberInstruction.makeNumberer(lang, null, null).getClass.getName.endsWith("Numberer_" + lang)

  private object Private {

    private val OldLocaleRe = "([a-z|A-Z]{2,3})(?:_.*)?".r

    // We support incoming languages of the form `en_US` (not in the IETF BCP 47 format) for backward compatibility reasons
    // and because for historical reasons Java's `Locale.toString` produces that kind of strings containing an underscore.
    //
    // The proxy portlet passes the result of `LanguageUtil.getLanguageId`, which is in `Locale.toString` format. Since
    // Liferay 6.2, there is a `LanguageUtil.getBCP47LanguageId` method which we should use if possible.
    //
    // The only language codes currently in use in `languages.xml` which have an associated country are `zh_CN` and `zh_TW`.
    // So we explicitly map those to `zh-Hans` and `zh-Hant` even though Java 1.7's doesn't do this:
    //
    //     Locale.CHINA.getScript == ""
    //
    // References:
    //
    // - https://github.com/orbeon/orbeon-forms/issues/2688
    // - https://github.com/orbeon/orbeon-forms/issues/2700
    // - https://docs.oracle.com/javase/7/docs/api/java/util/Locale.html
    // - https://docs.liferay.com/portal/6.1/javadocs/com/liferay/portal/kernel/language/LanguageUtil.html#getLanguageId(javax.servlet.http.HttpServletRequest)
    // - https://docs.liferay.com/portal/6.2/javadocs/com/liferay/portal/kernel/language/LanguageUtil.html#getBCP47LanguageId(javax.servlet.http.HttpServletRequest)
    //
    def cleanLanguage(lang: String): String =
      lang.trimAllToEmpty match {
        case OldLocaleRe("zh_CN") => "zh-Hans"
        case OldLocaleRe("zh_TW") => "zh-Hant"
        case OldLocaleRe(oldLang) => oldLang
        case newLang              => newLang
      }

    def selectLangUseDefault(
      appForm        : Option[AppForm],
      requestedLang  : Option[String],
      availableLangs : List[String]
    ): Option[String] = {

      def matchingLanguage = availableLangs intersect requestedLang.toList headOption
      def defaultLanguage  = availableLangs intersect List(getDefaultLang(appForm)) headOption
      def firstLanguage    = availableLangs headOption

      matchingLanguage orElse defaultLanguage orElse firstLanguage
    }

    def stringFromSession(request: Request, name: String) =
      request.sessionOpt flatMap
        (_.getAttribute(LanguageParam)) map {
          case item: Item => item.getStringValue
          case other      => other.toString
        }

  }
}

object FormRunnerLang extends FormRunnerLang
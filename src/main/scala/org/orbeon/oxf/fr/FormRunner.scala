/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.webapp.HttpStatusCodeException
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.scaxon.XML._

object FormRunner
        extends FormRunnerPersistence
        with FormRunnerPermissions
        with FormRunnerPDF
        with FormRunnerLang {

    val NS = "http://orbeon.org/oxf/xml/form-runner"
    val XF = XFORMS_NAMESPACE_URI

    def properties = Properties.instance.getPropertySet

    def buildPropertyName(name: String)(implicit p: FormRunnerParams) =
        name :: p.app :: p.form :: Nil mkString "."

    // Return a property using the form's app/name, None if the property is not defined
    def formRunnerProperty(name: String)(implicit p: FormRunnerParams) =
        Option(properties.getObject(buildPropertyName(name))) map (_.toString)

    // Return a boolean property using the form's app/name, false if the property is not defined
    def booleanFormRunnerProperty(name: String)(implicit p: FormRunnerParams) =
        Option(properties.getObject(buildPropertyName(name))) map (_.toString) exists (_ == "true")

    // Interrupt current processing and send an error code to the client.
    // NOTE: This could be done through ExternalContext
    def sendError(code: Int) = throw new HttpStatusCodeException(code)
    def sendError(code: Int, resource: String) = throw new HttpStatusCodeException(code, Option(resource))

    // Append a query string to a URL
    def appendQueryString(urlString: String, queryString: String) = NetUtils.appendQueryString(urlString, queryString)

    // Return specific Form Runner instances
    def formInstance         = topLevelInstance("fr-form-model",          "fr-form-instance")          get
    def parametersInstance   = topLevelInstance("fr-parameters-model",    "fr-parameters-instance")    get
    def errorSummaryInstance = topLevelInstance("fr-error-summary-model", "fr-error-summary-instance") get
    def persistenceInstance  = topLevelInstance("fr-persistence-model",   "fr-persistence-instance")   get

    def currentFRResources   = asNodeInfo(topLevelModel("fr-resources-model").get.getVariable("fr-fr-resources"))
    def currentFormResources = asNodeInfo(topLevelModel("fr-resources-model").get.getVariable("fr-form-resources"))

    // Whether the form has a captcha
    def hasCaptcha = formRunnerProperty("oxf.fr.detail.captcha")(FormRunnerParams()) exists Set("reCAPTCHA", "SimpleCaptcha")

    // The standard Form Runner parameters
    case class FormRunnerParams(app: String, form: String, document: Option[String], mode: String)

    object FormRunnerParams {
        def apply(): FormRunnerParams = {
            val params = parametersInstance.rootElement

            FormRunnerParams(
                app      = params \ "app",
                form     = params \ "form",
                document = nonEmptyOrNone(params \ "document"),
                mode     = params \ "mode"
            )
        }
    }

    // Display a success message
    def successMessage(message: String): Unit = {
        setvalue(persistenceInstance.rootElement \ "message", message)
        toggle("fr-message-success")
    }

    // Display an error message
    def errorMessage(message: String): Unit =
        dispatch(name = "fr-show", targetId = "fr-error-dialog", properties = Map("message" → Some(message)))
}
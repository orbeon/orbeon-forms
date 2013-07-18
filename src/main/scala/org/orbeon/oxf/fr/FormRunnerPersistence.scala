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

import org.orbeon.oxf.util._
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xml.{XMLUtils, TransformerUtils}
import org.orbeon.oxf.util.Headers._
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.xforms.analysis.model.StaticBind.ValidationLevel

trait FormRunnerPersistence {

    import FormRunner._

    // Check whether a value correspond to an uploaded file
    //
    // For this to be true
    // - the protocol must be file:
    // - the URL must have a valid signature
    //
    // This guarantees that the local file was in fact placed there by the upload control, and not tampered with.
    def isUploadedFileURL(value: String): Boolean =
        value.startsWith("file:/") && XFormsUploadControl.verifyMAC(value)

    // Create a new attachment path
    def createAttachmentPath(app: String, form: String, document: String, isDraft: Boolean, localURL: String): String = {

        // Here we could decide to use a nicer extension for the file. But since initially the filename comes from the
        // client, it cannot be trusted, nor can its mediatype. A first step would be to do content-sniffing to
        // determine a more trusted mediatype. A second step would be to put in an API for virus scanning. For now, we
        // just use .bin as an extension.

        //val nonTrustedFilename = XFormsUploadControl.getParameter(localURL, "filename")
        //val nonTrustedMediatype = XFormsUploadControl.getParameter(localURL, "mediatype")

        createFormDataAttachmentPath(app, form, document, isDraft, SecureUtils.randomHexId + ".bin")
    }

    // Path for a form data
    def createFormDataBasePath(app: String, form: String, document: String, isDraft: Boolean): String =
        "/fr/service/persistence/crud/" + app + '/' + form + (if (isDraft) "/draft/" else "/data/") + document + "/"

    // Path for a form data attachment
    def createFormDataAttachmentPath(app: String, form: String, document: String, isDraft: Boolean, filename: String): String =
        createFormDataBasePath(app, form, document, isDraft) + filename

    // Path for a form definition attachment
    def createFormDefinitionAttachmentPath(app: String, form: String, filename: String): String =
        "/fr/service/persistence/crud/" + app + '/' + form + "/form/" + filename

    // Whether the given path is an attachment path
    def isDataAttachmentPath(app: String, form: String, document: String, path: String): Boolean =
        path.startsWith("/fr/service/persistence/crud/" + app + '/' + form + "/data/" + document + '/') &&
            path.endsWith(".bin")

    // For a given attachment path, return the filename
    def getAttachmentPathFilename(path: String): String = path.split('/').last

    def getPersistenceURLHeaders(app: String, form: String, formOrData: String) = {

        require(augmentString(app).nonEmpty)
        require(augmentString(form).nonEmpty)
        require(Set("form", "data")(formOrData))

        // Find provider
        val provider = {
            val providerProperty = Seq("oxf.fr.persistence.provider", app, form, formOrData) mkString "."
            properties.getString(providerProperty)
        }

        getPersistenceURLHeadersFromProvider(provider)
    }

    def getPersistenceURLHeadersFromProvider(provider: String) = {

        // Find provider URI
        def findProviderURL = {
            val uriProperty = Seq("oxf.fr.persistence", provider, "uri") mkString "."
            properties.getStringOrURIAsString(uriProperty)
        }

        val propertyPrefix = "oxf.fr.persistence." + provider

        // Build headers map
        val headers = (
            for {
                propertyName ← properties.propertiesStartsWith(propertyPrefix)
                lowerSuffix = propertyName.substring(propertyPrefix.length + 1)
                if lowerSuffix != "uri"
                headerName = "Orbeon-" + capitalizeSplitHeader(lowerSuffix)
                headerValue = properties.getObject(propertyName).toString
            } yield
                headerName → headerValue) toMap

        (findProviderURL, headers)
    }

    def getPersistenceHeadersAsXML(app: String, form: String, formOrData: String) = {

        val (_, headers) = getPersistenceURLHeaders(app, form, formOrData)

        // Build headers document
        val headersXML =
            <headers>{
                for {
                    (name, value) ← headers
                } yield
                    <header><name>{XMLUtils.escapeXMLMinimal(name)}</name><value>{XMLUtils.escapeXMLMinimal(value)}</value></header>
            }</headers>.toString

        // Convert to TinyTree
        TransformerUtils.stringToTinyTree(XPathCache.getGlobalConfiguration, headersXML, false, false)
    }

    // Retrieves a form from the persistence layer
    def readPublishedForm(appName: String, formName: String)(implicit logger: IndentedLogger): Option[DocumentInfo] = {
        val uri = "/fr/service/persistence/crud/" + appName + "/" + formName + "/form/form.xhtml"
        val urlString = URLRewriterUtils.rewriteServiceURL(NetUtils.getExternalContext.getRequest, uri, URLRewriter.REWRITE_MODE_ABSOLUTE)
        val url = URLFactory.createURL(urlString)

        val headers = Connection.buildConnectionHeaders(None, Map(), Option(Connection.getForwardHeaders))
        val connectionResult = Connection("GET", url, credentials = None, messageBody = None, headers = headers, loadState = true, logBody = false).connect(saveState = true)

        // Libraries are typically not present. In that case, the persistence layer should return a 404 (thus the first test),
        // but the MySQL persistence layer returns a [200 with an empty body][1] (thus the second test).
        //   [1]: https://github.com/orbeon/orbeon-forms/issues/771
        connectionResult.statusCode == 200 && connectionResult.hasContent option
            useAndClose(connectionResult.getResponseInputStream) { inputStream ⇒
                // do process XInclude, so FB's model gets included
                TransformerUtils.readTinyTree(XPathCache.getGlobalConfiguration, inputStream, url.toString, true, false)
            }
    }

    // Whether the form data is valid as per the error summary
    // We use instance('fr-error-summary-instance')/valid and not xxf:valid() because the instance validity may not be
    // reflected with the use of XBL components.
    def dataValid = errorSummaryInstance.rootElement \ "valid" === "true"

    // Return the number of failed validations captured by the error summary for the given level
    def countValidationsByLevel(level: ValidationLevel) = (errorSummaryInstance.rootElement \ "counts" \@ level.name stringValue).toInt
}

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
import org.orbeon.oxf.xforms.action.XFormsAPI._

trait FormRunnerPersistence {

    import FormRunner._

    val PersistenceBasePath = "/fr/service/persistence/crud"

    // Check whether a value correspond to an uploaded file
    //
    // For this to be true
    // - the protocol must be file:
    // - the URL must have a valid signature
    //
    // This guarantees that the local file was in fact placed there by the upload control, and not tampered with.
    def isUploadedFileURL(value: String): Boolean =
        value.startsWith("file:/") && XFormsUploadControl.verifyMAC(value)

    // Base path for form data
    def createFormDataBasePath(app: String, form: String, isDraft: Boolean, document: String): String =
        PersistenceBasePath :: app :: form :: (if (isDraft) "draft" else "data") :: document :: "" :: Nil mkString "/"

    // Base path for form definition
    def createFormDefinitionBasePath(app: String, form: String) =
        PersistenceBasePath :: app :: form :: "form" :: "" :: Nil mkString "/"

    // Whether the given path is an attachment path (ignoring an optional query string)
    def isAttachmentURLFor(basePath: String, url: String) =
        url.startsWith(basePath) && splitQuery(url)._1.endsWith(".bin")

    // For a given attachment path, return the filename
    def getAttachmentPathFilenameRemoveQuery(pathQuery: String) = splitQuery(pathQuery)._1.split('/').last

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
        val uri = createFormDefinitionBasePath(appName, formName) + "form.xhtml"
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

    def collectAttachments(data: DocumentInfo, fromBasePath: String, toBasePath: String, forceAttachments: Boolean) = (
        for {
            holder        ← data \\ Node
            if isAttribute(holder) || isElement(holder) && ! hasChildElement(holder)
            beforeURL     = holder.stringValue.trim
            isUploaded    = isUploadedFileURL(beforeURL)
            if isUploaded ||
                isAttachmentURLFor(fromBasePath, beforeURL) && ! isAttachmentURLFor(toBasePath, beforeURL) ||
                isAttachmentURLFor(toBasePath, beforeURL) && forceAttachments
        } yield {
            // Here we could decide to use a nicer extension for the file. But since initially the filename comes from
            // the client, it cannot be trusted, nor can its mediatype. A first step would be to do content-sniffing to
            // determine a more trusted mediatype. A second step would be to put in an API for virus scanning. For now,
            // we just use .bin as an extension.
            val filename =
                if (isUploaded)
                    SecureUtils.randomHexId + ".bin"
                else
                    getAttachmentPathFilenameRemoveQuery(beforeURL)

            val afterURL =
                toBasePath + filename

            (holder, beforeURL, afterURL)
        }
    ).unzip3

    def putWithAttachments(
            data: DocumentInfo,
            toBaseURI: String,
            fromBasePath: String,
            toBasePath: String,
            filename: String,
            commonQueryString: String,
            forceAttachments: Boolean,
            username: Option[String] = None,
            password: Option[String] = None) = {

        // Find all instance nodes containing file URLs we need to upload
        val (uploadHolders, beforeURLs, afterURLs) =
            collectAttachments(data, fromBasePath, toBasePath, forceAttachments)

        // Save all attachments
        // - also pass a "valid" argument with whether the data was valid
        def saveAttachments(): Unit =
            uploadHolders zip afterURLs foreach { case (holder, resource) ⇒
                sendThrowOnError("fr-create-update-attachment-submission", Map(
                    "holder"   → Some(holder),
                    "resource" → Some(appendQueryString(toBaseURI + resource, commonQueryString)),
                    "username" → username,
                    "password" → password)
                )
            }

        // Update the paths on success
        def updatePaths() =
            uploadHolders zip afterURLs foreach { case (holder, resource) ⇒
                setvalue(holder, resource)
            }

        // Save XML document
        // - always store form data as "data.xml"
        // - also pass a "valid" argument with whether the data was valid
        def saveData() =
            sendThrowOnError("fr-create-update-submission", Map(
                "holder"   → Some(data.rootElement),
                "resource" → Some(appendQueryString(toBaseURI + toBasePath + filename, commonQueryString)),
                "username" → username,
                "password" → password)
            )

        // Do things in order, so we don't update path or save the data if any the upload fails
        saveAttachments()
        updatePaths()
        saveData()

        beforeURLs → afterURLs
    }
}

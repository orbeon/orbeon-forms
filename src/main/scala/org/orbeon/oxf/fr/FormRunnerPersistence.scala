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

import java.net.URI

import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.http.Headers._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.analysis.model.ValidationLevels._
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xml.{TransformerUtils, XMLUtils}
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.XML._

import scala.collection.JavaConverters._

trait FormRunnerPersistence {

    import org.orbeon.oxf.fr.FormRunner._

    val CRUDBasePath                       = "/fr/service/persistence/crud"
    val FormMetadataBasePath               = "/fr/service/persistence/form"
    val PersistencePropertyPrefix          = "oxf.fr.persistence"
    val PersistenceProviderPropertyPrefix  = PersistencePropertyPrefix + ".provider"

    val StandardProviderProperties = Set("uri", "autosave", "active", "permissions")

    // NOTE: We generate .bin, but sample data can contain other extensions
    private val RecognizedAttachmentExtensions = Set("bin", "jpg", "jpeg", "gif", "png", "pdf")

    // Check whether a value correspond to an uploaded file
    //
    // For this to be true
    // - the protocol must be file:
    // - the URL must have a valid signature
    //
    // This guarantees that the local file was in fact placed there by the upload control, and not tampered with.
    def isUploadedFileURL(value: String): Boolean =
        value.startsWith("file:/") && XFormsUploadControl.verifyMAC(value)

    def createFormDataBasePath(app: String, form: String, isDraft: Boolean, document: String): String =
        CRUDBasePath :: app :: form :: (if (isDraft) "draft" else "data") :: document :: "" :: Nil mkString "/"

    def createFormDefinitionBasePath(app: String, form: String) =
        CRUDBasePath :: app :: form :: "form" :: "" :: Nil mkString "/"

    def createFormMetadataPath(app: String, form: String) =
        FormMetadataBasePath :: app :: form :: Nil mkString "/"

    // Whether the given path is an attachment path (ignoring an optional query string)
    def isAttachmentURLFor(basePath: String, url: String) =
        url.startsWith(basePath) && (split[List](splitQuery(url)._1, ".").lastOption exists RecognizedAttachmentExtensions)

    // For a given attachment path, return the filename
    def getAttachmentPathFilenameRemoveQuery(pathQuery: String) = splitQuery(pathQuery)._1.split('/').last

    def findProvider(app: String, form: String, formOrData: String) = {
        val providerProperty = PersistenceProviderPropertyPrefix :: app :: form :: formOrData :: Nil mkString "."
        Option(properties.getString(providerProperty))
    }

    def providerPropertyAsURL(provider: String, property: String) =
        properties.getStringOrURIAsString(PersistencePropertyPrefix :: provider :: property :: Nil mkString ".")

    def providerPropertyAsBoolean(provider: String, property: String, default: Boolean) =
        properties.getBoolean(PersistencePropertyPrefix :: provider :: property :: Nil mkString ".", default)

    def autosaveSupported(app: String, form: String) =
        providerPropertyAsBoolean(findProvider(app, form, "data").get, "autosave", default = false)

    def ownerGroupPermissionsSupported(app: String, form: String) =
        providerPropertyAsBoolean(findProvider(app, form, "data").get, "permissions", default = false)

    def versioningSupported(app: String, form: String) =
        providerPropertyAsBoolean(findProvider(app, form, "data").get, "versioning", default = false)

    def isActiveProvider(provider: String) =
        providerPropertyAsBoolean(provider, "active", default = true)

    def getPersistenceURLHeaders(app: String, form: String, formOrData: String) = {

        require(augmentString(app).nonEmpty)
        require(augmentString(form).nonEmpty)
        require(Set("form", "data")(formOrData))

        getPersistenceURLHeadersFromProvider(findProvider(app, form, formOrData).get)
    }

    def getPersistenceURLHeadersFromProvider(provider: String) = {

        val propertyPrefix = PersistencePropertyPrefix :: provider :: Nil mkString "."
        val propertyPrefixTokenCount = split[List](propertyPrefix, ".").size

        // Build headers map
        val headers = (
            for {
                propertyName ← properties.propertiesStartsWith(propertyPrefix, matchWildcards = false)
                lowerSuffix  ← split[List](propertyName, ".").drop(propertyPrefixTokenCount).headOption
                if ! StandardProviderProperties(lowerSuffix)
                headerName  = "Orbeon-" + capitalizeSplitHeader(lowerSuffix)
                headerValue = properties.getObject(propertyName).toString
            } yield
                headerName → headerValue) toMap

        (providerPropertyAsURL(provider, "uri"), headers)
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
        TransformerUtils.stringToTinyTree(XPath.GlobalConfiguration, headersXML, false, false)
    }

    // Reads a document forwarding headers. The URL is rewritten, and is expected to be like "/fr/…"
    def readDocument(urlString: String)(implicit logger: IndentedLogger): Option[DocumentInfo] = {

        val rewrittenURLString =
            URLRewriterUtils.rewriteServiceURL(
                NetUtils.getExternalContext.getRequest,
                urlString,
                URLRewriter.REWRITE_MODE_ABSOLUTE
            )

        val url = new URI(rewrittenURLString)

        val cxr = Connection(
            httpMethod  = "GET",
            url         = url,
            credentials = None,
            content     = None,
            headers     = Connection.buildConnectionHeadersLowerIfNeeded(url.getScheme, None, Map(), Option(Connection.getForwardHeaders)),
            loadState   = true,
            logBody     = false
        ).connect(
            saveState = true
        )

        // Libraries are typically not present. In that case, the persistence layer should return a 404 (thus the test
        // on status code),  but the MySQL persistence layer returns a [200 with an empty body][1] (thus a body is
        // required).
        //   [1]: https://github.com/orbeon/orbeon-forms/issues/771
        ConnectionResult.tryWithSuccessConnection(cxr, closeOnSuccess = true) { is ⇒
            // do process XInclude, so FB's model gets included
            TransformerUtils.readTinyTree(XPath.GlobalConfiguration, is, rewrittenURLString, true, false)
        } toOption
    }

    // Retrieves a form definition from the persistence layer
    def readPublishedForm(appName: String, formName: String)(implicit logger: IndentedLogger): Option[DocumentInfo] =
        readDocument(createFormDefinitionBasePath(appName, formName) + "form.xhtml")

    // Retrieves the metadata for a form from the persistence layer
    def readFormMetadata(appName: String, formName: String)(implicit logger: IndentedLogger): Option[DocumentInfo] =
        readDocument(createFormMetadataPath(appName, formName))

    // Whether the form data is valid as per the error summary
    // We use instance('fr-error-summary-instance')/valid and not valid() because the instance validity may not be
    // reflected with the use of XBL components.
    def dataValid = errorSummaryInstance.rootElement \ "valid" === "true"

    // Return the number of failed validations captured by the error summary for the given level
    def countValidationsByLevel(level: ValidationLevel) = (errorSummaryInstance.rootElement \ "counts" \@ level.name stringValue).toInt

    // Return all nodes which refer to data attachments
    def collectDataAttachmentNodesJava(data: NodeInfo, fromBasePath: String) =
        collectAttachments(data.getDocumentRoot, fromBasePath, fromBasePath, forceAttachments = true)._1.asJava

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
        data              : DocumentInfo,
        toBaseURI         : String,
        fromBasePath      : String,
        toBasePath        : String,
        filename          : String,
        commonQueryString : String,
        forceAttachments  : Boolean,
        username          : Option[String] = None,
        password          : Option[String] = None,
        formVersion       : Option[String] = None
    ) = {

        // Find all instance nodes containing file URLs we need to upload
        val (uploadHolders, beforeURLs, afterURLs) =
            collectAttachments(data, fromBasePath, toBasePath, forceAttachments)

        // Save all attachments
        def saveAttachments(): Unit =
            uploadHolders zip afterURLs foreach { case (holder, resource) ⇒
                sendThrowOnError("fr-create-update-attachment-submission", Map(
                    "holder"       → Some(holder),
                    "resource"     → Some(appendQueryString(toBaseURI + resource, commonQueryString)),
                    "username"     → username,
                    "password"     → password,
                    "form-version" → formVersion)
                )
            }

        // Update the paths on success
        def updatePaths() =
            uploadHolders zip afterURLs foreach { case (holder, resource) ⇒
                setvalue(holder, resource)
            }

        // Save XML document
        def saveData() =
            sendThrowOnError("fr-create-update-submission", Map(
                "holder"       → Some(data.rootElement),
                "resource"     → Some(appendQueryString(toBaseURI + toBasePath + filename, commonQueryString)),
                "username"     → username,
                "password"     → password,
                "form-version" → formVersion)
            )

        // Do things in order, so we don't update path or save the data if any the upload fails
        saveAttachments()
        updatePaths()

        // Save and try to retrieve returned version
        val versionOpt =
            for {
                done     ← saveData()
                headers  ← done.headers
                versions ← headers collectFirst { case (name, values) if name equalsIgnoreCase "Orbeon-Form-Definition-Version" ⇒ values }
                version  ← versions.headOption
            } yield
                version

        (beforeURLs, afterURLs, versionOpt map (_.toInt) getOrElse 1)
    }
}

/**
 * Copyright (C) 2012 Orbeon, Inc.
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

import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util._
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.xml.{XMLConstants, TransformerUtils}
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.resources.URLFactory
import java.io.OutputStreamWriter
import org.orbeon.scaxon.XML._
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import xml._
import xml.Attribute
import org.dom4j.QName
import org.orbeon.oxf.common.OXFException
import scala.Some
import xml.Text
import xml.NamespaceBinding
import org.orbeon.oxf.xforms.XFormsConstants.XFORMS_NAMESPACE_URI

/**
 *  Supported:
 *  - Simple types, adding type="…" on the xs:element
 *  - Repeats, appropriately adding min="…" max="…" on the xs:element
 *  - Section templates, using the binds defined in the app or global library
 *  - Custom namespaces, properly including namespace declarations
 *
 *  To do:
 *  - For testing, we're passing the orbeon-token from the app context, the service is defined as a page in page-flow.xml
 *
 *  Not supported:
 *  - Custom XML instance (adds lots of complexity, thinking of dropping for 4.0, and maybe foreseeable future)
 */
class SchemaGenerator extends ProcessorImpl {

    private val SchemaPath = """/fr/service/schema/([^/]+)/([^/]+)""".r
    private val ComponentNSPrefix = "http://orbeon.org/oxf/xml/form-builder/component/"
    private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[SchemaGenerator]), "")

    case class Libraries(orbeon: Option[DocumentInfo], app: Option[DocumentInfo])

    override def start(pipelineContext: PipelineContext) {

        // Read form and library
        val ec = NetUtils.getExternalContext
        val response = ec.getResponse
        val SchemaPath(appName, formName) = ec.getRequest.getRequestPath
        val formSource = read(appName, formName).get
        val library = Libraries(read("orbeon", "library"), read(appName, "library"))

        // Compute root xs:element
        val rootBind = formSource \ "*:html" \ "*:head" \ "*:model" \ "*:bind" head
        val rootXsElement: Elem = handleBind(library, rootBind)

        // Import XForms schema if necessary and return
        val schema =
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                {
                    val hasXFormsNamespace = rootXsElement.descendant_or_self exists (_.scope.getPrefix(XFORMS_NAMESPACE_URI) != null)
                    if (hasXFormsNamespace)
                        <xs:import namespace="http://www.w3.org/2002/xforms"
                                   schemaLocation="http://www.w3.org/MarkUp/Forms/2007/XForms-11-Schema.xsd"/>
                }
                { rootXsElement }
            </xs:schema>
        response.setContentType("application/xml")
        useAndClose(new OutputStreamWriter(response.getOutputStream))(_.write(schema.toString))
    }

    // Retrieves a form from the persistence layer
    private def read(appName: String, formName: String): Option[DocumentInfo] = {
        val uri = "/fr/service/persistence/crud/" + appName + "/" + formName + "/form/form.xhtml"
        val urlString = URLRewriterUtils.rewriteServiceURL(NetUtils.getExternalContext.getRequest, uri, URLRewriter.REWRITE_MODE_ABSOLUTE)
        val url = URLFactory.createURL(urlString)

        val headers = Connection.buildConnectionHeaders(None, Map(), Option(Connection.getForwardHeaders))
        val connectionResult = Connection("GET", url, credentials = None, messageBody = None, headers = headers, loadState = true, logBody = false).connect(saveState = true)

        if (connectionResult.hasContent)
            Some(useAndClose(connectionResult.getResponseInputStream) { inputStream ⇒
                TransformerUtils.readTinyTree(XPathCache.getGlobalConfiguration, inputStream, url.toString, false, false)
            })
        else
            None
    }

    // Recursive function generating an xs:element from an xforms:bind
    private def handleBind(libraries: Libraries, bind: NodeInfo): Elem = {

        case class BindInfo(elemName: String, elemType: Option[String], repeated: Boolean, min: Option[String], max: Option[String])

        // Returns control corresponding to a bind, with a given name (e.g. *:grid)
        def control(bind: NodeInfo, controlName: String): Option[NodeInfo] = {
            val bindId: String = bind \@ "id"
            bind.rootElement \\ controlName filter (_ \@ "bind" === bindId) headOption
        }

        // Extract information from bind
        object RootBind { def unapply(bind: NodeInfo): Boolean = bind parent "*:model" nonEmpty }
        object Bind { def unapply(bind: NodeInfo): Option[BindInfo] = {
            val repeatGrid = control(bind, "*:grid") filter (_ \@ "repeat" === "true")
            Some(BindInfo(
                elemName = Seq("ref", "nodeset") flatMap (bind \@ _) map (_ stringValue) head,
                elemType = bind \@ "type",
                repeated = repeatGrid nonEmpty,
                min = repeatGrid.toSeq \@ "min",
                max = repeatGrid.toSeq \@ "max"
            ))
        }}

        // Build xs:element for this bind
        val xsElem = bind match {
            case RootBind() ⇒ <xs:element name="form"/>
            case Bind(BindInfo(elemName, elemType, repeated, min, max)) ⇒ {

                // Optional type attribute
                def attr(name: String, value: String) = Attribute(None, name, Text(value), Null)
                val typeAttr = elemType map (attr("type", _))

                // Get type qname, so we can declare a namespace if necessary, filtering the already declared XSD namespace
                val typeQName = elemType map (resolveQName(bind, _)) filter (qname ⇒
                    (qname.getNamespacePrefix, qname.getNamespaceURI) match {
                        case (XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI) ⇒ false
                        case (XMLConstants.XSD_PREFIX, _) ⇒ throw new OXFException("Non-schema types with the 'xs' prefix are not supported")
                        case _ ⇒ true
                    })

                // Optional min/max attributes
                def attrDefault(value: Option[String], name: String, default: String) =
                    if (repeated) Some(attr(name, value getOrElse default)) else None
                val minAttr = attrDefault(min, "minOccurs", "0")
                val maxAttr = attrDefault(max, "maxOccurs", "unbounded")

                // Build element with optional attributes and new namespace
                val xsElem = <xs:element name={elemName}/>
                def newScope(qname: QName) = NamespaceBinding(qname.getNamespacePrefix, qname.getNamespaceURI, xsElem.scope)
                val xsElemWithNS = typeQName map (qname ⇒ xsElem.copy(scope = newScope(qname))) getOrElse xsElem
                val attributes = typeAttr ++ minAttr ++ maxAttr
                attributes.foldLeft(xsElemWithNS)(_ % _)
            }
        }

        // Get children of bind, or if there aren't any see if this is a component, in which case we get the binds from the library
        val childBinds = bind \ * match {
            case Nil ⇒
                val section = control(bind, "*:section")
                val component = section.toSeq \ * filter (_.getURI startsWith ComponentNSPrefix) headOption
                val library = component flatMap ( c ⇒ {
                        val componentNamespaceURISuffix = c.getURI.substring(ComponentNSPrefix.length)
                        componentNamespaceURISuffix match {
                            case "orbeon/library" ⇒ libraries.orbeon
                            case _ ⇒ libraries.app
                        }
                    }
                )
                val libraryRootBind = library.toSeq \ "*:html" \ "*:head" \ "*:model" \ "*:bind"
                val componentBind = libraryRootBind \ "*:bind" filter (_ \@ "name" === component.get.getLocalPart)
                componentBind \ "*:bind"
            case children ⇒ children
        }

        // Recurse on children if any
        childBinds flatMap (handleBind(libraries, _)) match {
            case Nil ⇒ xsElem
            case xsElems ⇒ xsElem.copy(child = <xs:complexType><xs:sequence> { xsElems } </xs:sequence></xs:complexType>)
        }
    }
}

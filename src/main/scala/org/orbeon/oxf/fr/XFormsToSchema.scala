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

import org.orbeon.oxf.pipeline.api.{ExternalContext, XMLReceiver, PipelineContext}
import org.orbeon.oxf.util._
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.XMLConstants.XS_STRING_QNAME
import org.orbeon.oxf.xforms.XFormsConstants.XFORMS_STRING_QNAME
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.scaxon.XML._
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import xml._
import xml.Attribute
import org.dom4j.QName
import org.orbeon.oxf.common.{Version, OXFException}
import org.orbeon.oxf.xforms.XFormsConstants.XFORMS_NAMESPACE_URI
import org.orbeon.oxf.xforms.{XFormsObject, XFormsContainingDocument}
import org.orbeon.oxf.xforms.processor.XFormsToSomething
import org.orbeon.oxf.xforms.processor.XFormsToSomething.Stage2CacheableState
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control
import org.orbeon.oxf.xforms.control.XFormsComponentControl
import xml.Text
import xml.NamespaceBinding
import org.orbeon.scaxon.XML

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
class XFormsToSchema extends XFormsToSomething {

    private val SchemaPath  = """/fr/([^/^.]+)/([^/^.]+)/schema""".r
    private val ComponentNS = """http://orbeon.org/oxf/xml/form-builder/component/([^/]+)/library""".r

    private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[XFormsToSchema]), "")

    case class Libraries(orbeon: Option[DocumentInfo], app: Option[DocumentInfo])

    protected def produceOutput(pipelineContext: PipelineContext,
                                outputName: String,
                                externalContext: ExternalContext,
                                indentedLogger: IndentedLogger,
                                stage2CacheableState: Stage2CacheableState,
                                containingDocument: XFormsContainingDocument,
                                xmlReceiver: XMLReceiver): Unit = {
        // This is a PE feature
        Version.instance.requirePEFeature("XForms schema generator service")

        // Read form and library
        val ec = NetUtils.getExternalContext
        val SchemaPath(appName, formName) = ec.getRequest.getRequestPath
        val formSource = FormRunner.readPublishedForm(appName, formName).get
        val library = Libraries(FormRunner.readPublishedForm("orbeon", "library"), FormRunner.readPublishedForm(appName, "library"))

        // Compute root xs:element
        val rootBind = formSource \ "*:html" \ "*:head" \ "*:model" \ "*:bind" head
        val resolve = containingDocument.getControls.getCurrentControlTree.getRoot.resolve(_: String)
        val rootXsElement: Elem = handleBind(library, resolve, rootBind)

        // Import XForms schema if necessary
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

        // Send result to output
        XML.elemToSAX(schema, xmlReceiver)
    }

    // Recursive function generating an xs:element from an xf:bind
    private def handleBind(libraries: Libraries, resolve: String ⇒ Option[XFormsObject], bind: NodeInfo): Elem = {

        case class BindInfo(elemName: String, elemType: Option[QName], repeated: Boolean, min: Option[String], max: Option[String])

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
                elemName = bind \@ ("ref" || "nodeset"),
                elemType = (bind \@ "type").headOption map (_.stringValue) map (resolveQName(bind, _)) filterNot Set(XS_STRING_QNAME, XFORMS_STRING_QNAME),
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
                val typeAttr = elemType map (_.getQualifiedName) map (attr("type", _))

                // Create namespace binding for type, filtering the already declared XSD namespace
                val typeNamespaceBinding = elemType flatMap (qname ⇒
                    (qname.getNamespacePrefix, qname.getNamespaceURI) match {
                        case (XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI) ⇒ None
                        case (XMLConstants.XSD_PREFIX, _) ⇒ throw new OXFException("Non-schema types with the 'xs' prefix are not supported")
                        case (prefix, uri) ⇒ Some(NamespaceBinding(prefix, uri, _: NamespaceBinding))
                    })

                // Optional min/max attributes
                def attrDefault(value: Option[String], name: String, default: String) =
                    if (repeated) Some(attr(name, value getOrElse default)) else None
                val minAttr = attrDefault(min, "minOccurs", "0")
                val maxAttr = attrDefault(max, "maxOccurs", "unbounded")

                // For controls with an itemset, generate a xs:simpleType
                val xformsObject = resolve(elemName + "-control")
                val itemset = xformsObject collectFirst { case select1: XFormsSelect1Control if select1.isRelevant ⇒ select1.getItemset }
                val itemsetValues = itemset.toSeq flatMap (_.children) map (_.value) toSeq
                val simpleType = if (itemsetValues.length == 0) Seq() else {
                    <xs:simpleType>
                        <xs:restriction base="xs:string">
                            { itemsetValues map (value ⇒ <xs:enumeration value={value}/>) }
                        </xs:restriction>
                    </xs:simpleType>
                }

                // Build element with optional attributes and new namespace
                val xsElem = <xs:element name={elemName}>{ simpleType }</xs:element>
                val xsElemWithNS = typeNamespaceBinding map (_(xsElem.scope)) map (s ⇒ xsElem.copy(scope = s)) getOrElse xsElem
                val attributes = typeAttr ++ minAttr ++ maxAttr
                attributes.foldLeft(xsElemWithNS)(_ % _)
            }
        }

        def matchesComponent(uri: String) = ComponentNS.findFirstIn(uri).isDefined

        // Get children of bind, or if there aren't any see if this is a component, in which case we get the binds from the library
        // If there are no children, and this is a section template, return the nestedContainer for the XBL component
        val (childBinds, newResolve) = bind \ * match {
            case Nil ⇒
                val section = control(bind, "*:section")
                val component = section.toSeq \ * find (e ⇒ matchesComponent(e.getURI))
                val binds = {
                    val library = component map (_.getURI) flatMap {
                        case ComponentNS("orbeon") ⇒ libraries.orbeon
                        case _ ⇒ libraries.app
                    }
                    val libraryRootBind = library.toSeq \ "*:html" \ "*:head" \ "*:model" \ "*:bind"
                    val componentBind = libraryRootBind \ "*:bind" filter (_ \@ "name" === component.get.getLocalPart)
                    componentBind \ "*:bind"
                }

                def newResolve(component: NodeInfo) = {

                    def asComponent(o: Option[AnyRef]) = o collect { case c: XFormsComponentControl ⇒ c }

                    // Find the concrete section component (fr:section)
                    val sectionComponent = {
                        val sectionName = xsElem.attributes("name").head.text
                        asComponent(resolve(sectionName + "-control")).get
                    }

                    // Find the concrete section template component (component:foo)
                    val sectionTemplateComponent = {
                        val sectionTemplateElementOpt = sectionComponent.staticControl.descendants find (c ⇒ matchesComponent(c.element.getNamespaceURI))
                        asComponent(sectionTemplateElementOpt flatMap (e ⇒ sectionComponent.resolve(e.staticId))).get
                    }

                    // Return the inner resolver function
                    sectionTemplateComponent.innerRootControl.resolve(_: String)
                }

                (binds, component map newResolve getOrElse resolve)
            case children ⇒ (children, resolve)
        }

        // Recurse on children if any
        childBinds flatMap (handleBind(libraries, newResolve, _)) match {
            case Nil ⇒ xsElem
            case xsElems ⇒ xsElem.copy(child = <xs:complexType><xs:sequence> { xsElems } </xs:sequence></xs:complexType>)
        }
    }
}

/**
 * Copyright (C) 2014 Orbeon, Inc.
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
package org.orbeon.oxf.fr.schema

import org.dom4j.QName
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.control.XFormsComponentControl
import org.orbeon.oxf.xforms.control.controls.{XFormsSelectControl, XFormsSelect1Control}
import org.orbeon.oxf.xforms.{XFormsObject, XFormsContainingDocument}
import org.orbeon.oxf.xml.XMLConstants._
import org.orbeon.saxon.om.{NodeInfo, DocumentInfo}
import org.orbeon.scaxon.XML._
import scala.xml._
import org.orbeon.oxf.util.{LoggerFactory, IndentedLogger}

object SchemaGenerator {

    case class Libraries(orbeon: Option[DocumentInfo], app: Option[DocumentInfo])
    private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[XFormsToSchema]), "")
    private val ComponentNS = """http://orbeon.org/oxf/xml/form-builder/component/([^/]+)/library""".r

    def createSchema(appName: String, formSource: DocumentInfo, containingDocument: XFormsContainingDocument): Elem = {

        // Compute root xs:element
        val rootBind = FormRunner.findTopLevelBind(formSource).get
        val resolve = containingDocument.getControls.getCurrentControlTree.getRoot.resolve(_: String)
        val rootXsElem = handleBind(formSource, resolve, rootBind)

        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                   xmlns:xf="http://www.w3.org/2002/xforms"
                   xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning" vc:minVersion="1.1">
            {val hasXFormsNamespace = rootXsElem.descendant_or_self exists (_.scope.getPrefix(XFORMS_NAMESPACE_URI) != null)
        if (hasXFormsNamespace)
                <xs:import namespace="http://www.w3.org/2002/xforms"
                           schemaLocation="http://www.w3.org/MarkUp/Forms/2007/XForms-11-Schema.xsd"/>}{rootXsElem}
        </xs:schema>
    }

    // Recursive function generating an xs:element from an xf:bind
    private def handleBind(formSource: DocumentInfo, resolve: String ⇒ Option[XFormsObject], bind: NodeInfo): Elem = {

        case class BindInfo(
            elemName   : String,
            required   : Boolean,
            hasRelevant: Boolean,
            elemType   : Option[QName],
            repeated   : Boolean,
            min        : Option[String],
            max        : Option[String]
        )

        // Returns control corresponding to a bind, with a given name (e.g. *:grid)
        def findControlNodeForBind(bind: NodeInfo, controlName: Test): Option[NodeInfo] = {
            val bindId: String = bind \@ "id"
            bind.rootElement \\ controlName find (_.attValue("bind") == bindId)
        }

        // Extract information from bind
        object RootBind {
            def unapply(bind: NodeInfo): Boolean = bind parent "*:model" nonEmpty
        }
        object Bind {
            def unapply(bind: NodeInfo): Option[BindInfo] = {
                val repeatGridNode = findControlNodeForBind(bind, "*:grid") filter (_.attValue("repeat") == "true") toList

                Some(BindInfo(
                    elemName    = bind \@ ("ref" || "nodeset"),
                    required    = bind.attValue("required") == "true()",
                    hasRelevant = bind.att("has-relevant").nonEmpty,
                    elemType    = bind.att("type").headOption.map(_.stringValue).map(bind.resolveQName).filterNot(Set(XS_STRING_QNAME, XFORMS_STRING_QNAME)),
                    repeated    = repeatGridNode nonEmpty,
                    min         = repeatGridNode \@ "min",
                    max         = repeatGridNode \@ "max"
                ))
            }
        }

        // Build xs:element for this bind
        val xsElem = bind match {
            case RootBind() ⇒ <xs:element name="form"/>
            case Bind(BindInfo(elemName, required, hasRelevant, elemType, repeated, min, max)) ⇒

                // Optional type attribute
                def attr(name: String, value: String) = scala.xml.Attribute(None, name, scala.xml.Text(value), Null)

                // Optional min/max attributes
                def attrDefault(value: Option[String], name: String, default: String) =
                    if (repeated) Some(attr(name, value getOrElse default)) else None

                val minAttr = attrDefault(min, "minOccurs", "0")
                val maxAttr = attrDefault(max, "maxOccurs", "unbounded")

                // For controls with an itemset, generate a xs:simpleType
                val simpleTypeRestrictionElemOpt =
                    for {
                        control       ← resolve(elemName + "-control")
                        select        ← collectByErasedType[XFormsSelect1Control](control)
                        if select.isRelevant
                        itemset       = select.getItemset
                        itemsetValues = itemset.children map (_.value)
                        if itemsetValues.nonEmpty
                    } yield {

                        def oneValueSimpleType(allowEmpty: Boolean): Elem = {
                            val values = if (allowEmpty) "" :: itemsetValues else itemsetValues
                            <xs:simpleType>
                                <xs:restriction base="xs:string">
                                    {values map (value ⇒ <xs:enumeration value={value}/>)}
                                </xs:restriction>
                            </xs:simpleType>
                        }

                        def listSimpleType: Elem =
                                <xs:simpleType>
                                    <xs:list>
                                        {oneValueSimpleType(allowEmpty = false)}
                                    </xs:list>
                                </xs:simpleType>

                        def listMinLengthOneSimpleType: Elem =
                            <xs:simpleType>
                                <xs:restriction>
                                    {listSimpleType}
                                    <xs:minLength value="1"/>
                                </xs:restriction>
                            </xs:simpleType>

                        val allowEmpty = ! required || hasRelevant
                        select match {
                            case _: XFormsSelectControl ⇒
                                if (allowEmpty)
                                    listSimpleType
                                else
                                    listMinLengthOneSimpleType
                            case _ ⇒
                                oneValueSimpleType(allowEmpty)
                        }
                    }

                // The xf:bind is for an attachment control if it has type="xs|xf:anyURI" and the corresponding control
                // has a class 'fr-attachment'
                val isBindForAttachmentControl = {
                    val xsdOrXFormsURI = Set(XSD_URI, XFORMS_NAMESPACE_URI)
                    def isBindTypeAnyURI = elemType.exists(qname ⇒ xsdOrXFormsURI(qname.getNamespaceURI) && qname.getName == "anyURI")
                    def isControlAttachment = findControlNodeForBind(bind, *) exists (_.attClasses("fr-attachment"))
                    isBindTypeAnyURI && isControlAttachment
                }

                // Value of xs:type attribute added to the xs:element, except for attachment controls, where
                // that type is defined as part of a complex type (see complexTypeForAttachment below)
                val typeAttr =
                    if (isBindForAttachmentControl) None
                    else elemType map (_.getQualifiedName) map (attr("type", _))

                // Create namespace binding for type, filtering the already declared XSD namespace
                val typeNamespaceBinding =
                    if (isBindForAttachmentControl) None
                    else elemType flatMap (qname ⇒
                        (qname.getNamespacePrefix, qname.getNamespaceURI) match {
                            case (XSD_PREFIX, XSD_URI) ⇒ None
                            case (XSD_PREFIX, _) ⇒ throw new OXFException("Non-schema types with the 'xs' prefix are not supported")
                            case (XFORMS_SHORT_PREFIX, XFORMS_NAMESPACE_URI) ⇒ None
                            case (XFORMS_SHORT_PREFIX, _) ⇒ throw new OXFException("Non-XForms types with the 'xf' prefix are not supported")
                            case (prefix, uri) ⇒ Some(NamespaceBinding(prefix, uri, _: NamespaceBinding))
                        })

                val complexTypeForAttachment =
                    isBindForAttachmentControl option
                        <xs:complexType>
                            <xs:simpleContent>
                                <xs:extension base="xs:anyURI">
                                    <xs:attribute name="filename" type="xs:string"/>
                                    <xs:attribute name="mediatype" type="xs:string"/>
                                    <xs:attribute name="size" type="xf:integer"/>
                                </xs:extension>
                            </xs:simpleContent>
                        </xs:complexType>

                // Build element with optional attributes and new namespace
                val xsElemContent = simpleTypeRestrictionElemOpt.toList ++ complexTypeForAttachment.toList
                val xsElem = <xs:element name={elemName}>
                    {xsElemContent}
                </xs:element>
                val xsElemWithNS = typeNamespaceBinding map (_(xsElem.scope)) map (s ⇒ xsElem.copy(scope = s)) getOrElse xsElem
                val attributes = typeAttr ++ minAttr ++ maxAttr

                attributes.foldLeft(xsElemWithNS)(_ % _)
        }

        // Get children of bind, or if there aren't any see if this is a component, in which case we get the binds from
        // the library.
        val (childrenBinds, newResolve) = bind \ * match {
            case Nil ⇒
                // No children for this bind, but there might be a section template associated with this bind

                def matchesComponentURI(uri: String) =
                    ComponentNS.findFirstIn(uri).isDefined

                def findComponentNodeForSection(sectionNode: NodeInfo) =
                    sectionNode child * find (e ⇒ matchesComponentURI(e.getURI))

                // NOTE: This also returns Nil if we can't find the library. Is this the best thing to do?
                def findComponentBindNodes(componentNode: NodeInfo): Seq[NodeInfo] = {

                    // Find the <xbl:xbl> XBL container, as we might have two (one for the orbeon library, one for the app library)
                    val xblEl = {
                        val xblEls = formSource.rootElement \ "*:head" \ "*:xbl"
                        val xblEl = xblEls filter { xblEl ⇒
                            val componentNamespace = xblEl.namespaces.filter(_.getLocalPart == "component")
                            componentNamespace.head.getStringValue == componentNode.namespaceURI
                        }
                        assert(xblEl.length == 1, "expect exactly one <xbl:xbl> container for given namespace")
                        xblEl.head
                    }

                    // Find the <xbl:binding> for this component
                    val xblBindingEl = {
                        val els = xblEl \ "*:binding" filter (_.attValue("element").endsWith("|" + componentNode.localname))
                        assert(els.length == 1, "expect exactly one <xbl:binding> for given component")
                        els.head
                    }

                    // Find top-level xf:bind, which is the one without a `ref`
                    // (there is a xf:bind making instance('fr-form-instance') readonly in certain cases; is it really needed?)
                    val componentTopLevelBind = xblBindingEl \ "*:implementation" \ "*:model" \ "*:bind" filter (_.att("ref").isEmpty)
                    assert(componentTopLevelBind.length == 1, "expect exactly one top-level bind in component")

                    // Find xf:bind for the nodes inside this component
                    componentTopLevelBind \ "*:bind"
                }

                // We want a new resolver as what's inside the section template component is in inner scope
                def resolverForSectionComponent(sectionStaticId: String) = {

                    def asComponent(o: Option[XFormsObject]) =
                        o collect { case c: XFormsComponentControl ⇒ c} get

                    // Find the concrete section component (fr:section)
                    val frSectionComponent = asComponent(resolve(sectionStaticId))

                    // Find the concrete section template component (component:foo)
                    // A bit tricky because there might not be an id on the component element:
                    // <component:eid xmlns:component="http://orbeon.org/oxf/xml/form-builder/component/orbeon/library"/>
                    val sectionTemplateComponent = {
                        val sectionTemplateElementOpt = frSectionComponent.staticControl.descendants find (c ⇒ matchesComponentURI(c.element.getNamespaceURI))
                        asComponent(sectionTemplateElementOpt flatMap (e ⇒ frSectionComponent.resolve(e.staticId)))
                    }

                    // Return the inner resolver function
                    sectionTemplateComponent.innerRootControl.resolve(_: String)
                }

                // Combine everything together
                def findSectionTemplateBindsAndResolver =
                    for {
                        sectionNode ← findControlNodeForBind(bind, "*:section")
                        componentNode ← findComponentNodeForSection(sectionNode)
                        sectionStaticId = sectionNode.id
                    } yield
                        (findComponentBindNodes(componentNode), resolverForSectionComponent(sectionStaticId))

                findSectionTemplateBindsAndResolver getOrElse(Nil, resolve)

            case children ⇒
                (children, resolve)
        }

        // Recurse on children if any
        childrenBinds flatMap (handleBind(formSource, newResolve, _)) match {
            case Nil ⇒ xsElem
            case xsElems ⇒ xsElem.copy(child = <xs:complexType>
                <xs:all>
                    {xsElems}
                </xs:all>
            </xs:complexType>)
        }
    }
}

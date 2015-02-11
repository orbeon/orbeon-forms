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

        val schemaElem =
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:xf="http://www.w3.org/2002/xforms"
                       xmlns:vc="http://www.w3.org/2007/XMLSchema-versioning">
                {
                    val hasXFormsNamespace = rootXsElem.descendant_or_self exists (_.scope.getPrefix(XFORMS_NAMESPACE_URI) != null)
                    if (hasXFormsNamespace)
                            <xs:import namespace="http://www.w3.org/2002/xforms"
                                       schemaLocation="http://www.w3.org/MarkUp/Forms/2007/XForms-11-Schema.xsd"/>
                }
                {rootXsElem}
            </xs:schema>


        // On add the vc:minVersion="1.1" if we're using a 1.1 feature: the xs:all containing a repeated grid
        val hasAllWithChildRepeatedGrid =
            rootXsElem.descendant_or_self.exists { xsElem ⇒
                def isXsAll              = xsElem.label == "all"
                def hasRepeatedGridChild = xsElem.child.exists(_.attribute("minOccurs").isDefined)
                isXsAll && hasRepeatedGridChild
            }
        if (hasAllWithChildRepeatedGrid) {
            val minVersion = scala.xml.Attribute(Some("vc"), "minVersion", scala.xml.Text("1.1"), Null)
            schemaElem % minVersion
        } else {
            schemaElem
        }
    }

    // Recursive function generating an xs:element from an xf:bind
    private def handleBind(formSource: DocumentInfo, resolve: String ⇒ Option[XFormsObject], bind: NodeInfo): Elem = {

        case class BindInfo(
            elemName      : String,
            maybeRequired : Boolean,
            hasRelevant   : Boolean,
            elemType      : Option[QName],
            repeated      : Boolean,
            min           : Option[String],
            max           : Option[String]
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
                val repeatGridNode = findControlNodeForBind(bind, "*:grid") filter FormRunner.isRepeat toList

                // NOTE: Don't support deprecated xf:validation elements, as they were not in a release. But support
                // xf:relevant, xf:required, and xf:type.

                val hasRelevant =
                    bind ++ (bind / XFORMS_RELEVANT_QNAME) /@ "has-relevant" nonEmpty

                val maybeRequired =
                    (bind /@ REQUIRED_QNAME) ++ (bind / XFORMS_REQUIRED_QNAME /@ VALUE_QNAME) exists (_.stringValue != "false()")

                def typeFromBind =
                    (bind /@ TYPE_QNAME headOption) map (_.stringValue)

                def typeFromChildElem =
                    (bind / XFORMS_TYPE_QNAME headOption) map (_.stringValue)

                val elemType = (
                    typeFromBind
                    orElse    typeFromChildElem
                    map       bind.resolveQName
                    filterNot Set(XS_STRING_QNAME, XFORMS_STRING_QNAME)
                )

                Some(BindInfo(
                    elemName      = bind /@ ("ref" || "nodeset"),
                    maybeRequired = maybeRequired,
                    hasRelevant   = hasRelevant,
                    elemType      = elemType,
                    repeated      = repeatGridNode nonEmpty,
                    min           = repeatGridNode /@ "min",
                    max           = repeatGridNode /@ "max"
                ))
            }
        }

        // Build xs:element for this bind
        val (repeated, xsElem) = bind match {
            case RootBind() ⇒ (false, <xs:element name="form"/>)
            case Bind(BindInfo(elemName, maybeRequired, hasRelevant, elemTypeOpt, repeated, min, max)) ⇒

                // Optional type attribute
                def attr(name: String, value: String) = scala.xml.Attribute(None, name, scala.xml.Text(value), Null)
                // Optional min/max attributes
                def attrDefault(value: Option[String], name: String, default: String) =
                    if (repeated) Some(attr(name, value getOrElse default)) else None

                // Build the <xs:element> we end up returning
                def xsElement(
                    attributes       : List[Attribute],
                    namespaceBinding : Option[(String, String)],
                    content          : Option[Elem]
                ):  Elem = {
                    val xsElem =
                        <xs:element name={elemName}>
                            {content.toList}
                        </xs:element>
                    val xsElemWithNS = namespaceBinding.map(ns ⇒ NamespaceBinding(ns._1, ns._2, xsElem.scope)).map(s ⇒ xsElem.copy(scope = s)).getOrElse(xsElem)
                    attributes.foldLeft(xsElemWithNS)(_ % _)
                }

                case class ItemsetTypeValues(isSelect: Boolean, values: List[String])
                val itemsetOpt: Option[ItemsetTypeValues] = {
                    val control = resolve(elemName + "-control")
                    val select  = control.flatMap(collectByErasedType[XFormsSelect1Control](_))
                    select.map(s ⇒ {
                        val itemsetOpt = s.isRelevant.option(s.getItemset)
                        val values     = itemsetOpt.toList.flatMap(_.children.map(_.value))
                        val isSelect   = s.isInstanceOf[XFormsSelectControl]
                        ItemsetTypeValues(isSelect, values)
                    })
                }

                def isBindForAttachmentControl = {
                    val xsdOrXFormsURI = Set(XSD_URI, XFORMS_NAMESPACE_URI)
                    def isBindTypeAnyURI = elemTypeOpt.exists(qname ⇒ xsdOrXFormsURI(qname.getNamespaceURI) && qname.getName == "anyURI")
                    def isControlAttachment = findControlNodeForBind(bind, *) exists (_.attClasses("fr-attachment"))
                    isBindTypeAnyURI && isControlAttachment
                }

                // Different types of schema element we generate
                sealed trait              SchemaGenerationCase
                object Repeated   extends SchemaGenerationCase
                object Attachment extends SchemaGenerationCase
                object Selection  extends SchemaGenerationCase
                object MaybeTyped extends SchemaGenerationCase

                val controlType =
                    if (repeated)                        Repeated
                    else if (itemsetOpt.isDefined)       Selection
                    else if (isBindForAttachmentControl) Attachment
                    else                                 MaybeTyped

                val element =
                    controlType match {

                        case Repeated ⇒
                            val minAttr    = attrDefault(min, "minOccurs", "0")
                            val maxAttr    = attrDefault(max, "maxOccurs", "unbounded")
                            val attributes = (minAttr ++ maxAttr).toList
                            xsElement(attributes, None, None)

                        case Attachment ⇒
                            val content =
                                <xs:complexType>
                                    <xs:simpleContent>
                                        <xs:extension base="xs:anyURI">
                                            <xs:attribute name="filename" type="xs:string"/>
                                            <xs:attribute name="mediatype" type="xs:string"/>
                                            <xs:attribute name="size" type="xf:integer"/>
                                        </xs:extension>
                                    </xs:simpleContent>
                                </xs:complexType>
                            xsElement(Nil, None, Some(content))

                        case Selection ⇒
                            val itemset = itemsetOpt.get
                            def oneValueSimpleType(allowEmpty: Boolean): Elem = {
                                val values = if (allowEmpty) "" :: itemset.values else itemset.values
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

                            val allowEmpty = ! maybeRequired || hasRelevant
                            val content =
                                if (itemset.isSelect)
                                    if (allowEmpty) listSimpleType else listMinLengthOneSimpleType
                                else
                                    oneValueSimpleType(allowEmpty)
                            xsElement(Nil, None, Some(content))

                        case MaybeTyped ⇒

                            val (attributes, namespaceBinding) =
                                if (hasRelevant) {
                                    // Don't check type if the control might not be shown to users
                                    (Nil, None)
                                } else {
                                    val typeQNameOpt = elemTypeOpt.map(_.getQualifiedName)
                                    val typeAttrOpt = typeQNameOpt.map(attr("type", _))
                                    // Create namespace binding for type, filtering the already declared XSD namespace
                                    val typeNamespaceBindingOpt = elemTypeOpt flatMap (qname ⇒
                                        (qname.getNamespacePrefix, qname.getNamespaceURI) match {
                                            case (XSD_PREFIX, XSD_URI) ⇒ None
                                            case (XSD_PREFIX, _) ⇒ throw new OXFException("Non-schema types with the 'xs' prefix are not supported")
                                            case (XFORMS_SHORT_PREFIX, XFORMS_NAMESPACE_URI) ⇒ None
                                            case (XFORMS_SHORT_PREFIX, _) ⇒ throw new OXFException("Non-XForms types with the 'xf' prefix are not supported")
                                            case (prefix, uri) ⇒ Some(prefix → uri)
                                        })
                                    (typeAttrOpt.toList, typeNamespaceBindingOpt)
                                }
                            xsElement(attributes, namespaceBinding, None)
                    }

                (repeated, element)
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
                    val componentTopLevelBind = xblBindingEl \ "*:implementation" \ "*:model" \ "*:bind" filter (_.attValue("id") == "fr-form-binds")
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

            case iterationBinds if repeated ⇒
                // If repeated, skip nested iteration bind so that the schema validates the old data format
                // NOTE: This could be made configurable in the future.
                (iterationBinds \ *, resolve)
            case children ⇒
                (children, resolve)
        }

        // Recurse on children if any
        childrenBinds flatMap (handleBind(formSource, newResolve, _)) match {
            case Nil ⇒ xsElem
            case xsElems ⇒
                // With 1 child, use xs:sequence instead of xs:all, to give the schema a chance to be 1.0 compliant
                val container = if (xsElems.length == 1) <xs:sequence/> else <xs:all/>
                xsElem.copy(child = <xs:complexType> { container.copy(child = xsElems) } </xs:complexType>)
        }
    }
}

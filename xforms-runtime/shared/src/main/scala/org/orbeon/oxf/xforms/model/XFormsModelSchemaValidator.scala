/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.model

import org.orbeon.datatypes.LocationData
import org.orbeon.dom.{Attribute, Element, Node, QName}
import org.orbeon.msv.datatype.xsd.{DatatypeFactory, XSDatatype}
import org.orbeon.msv.grammar.{Expression, Grammar, IDContextProvider2}
import org.orbeon.msv.grammar.xmlschema.*
import org.orbeon.msv.relaxng.datatype.{Datatype, DatatypeException}
import org.orbeon.msv.util.{DatatypeRef, StartTagInfo, StringRef}
import org.orbeon.msv.verifier.Acceptor
import org.orbeon.msv.verifier.regexp.{REDocumentDeclaration, SimpleAcceptor, StringToken}
import org.orbeon.msv.verifier.regexp.xmlschema.{XSAcceptor, XSREDocDecl}
import org.orbeon.oxf.externalcontext.UrlRewriteMode
import org.orbeon.oxf.processor.validation.SchemaValidationException
import org.orbeon.oxf.util.*
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.msv.IDConstraintChecker
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.oxf.xml.dom.{Extensions, LocationSAXWriter}
import org.orbeon.xforms.{XFormsCrossPlatformSupport, XFormsNames}
import org.orbeon.xforms.runtime.ErrorInfo
import org.xml.sax.helpers.AttributesImpl

/**
 * Provides XML Schema validation services for the XForms model.
 */
class XFormsModelSchemaValidator extends XFormsModelSchemaValidatorPlatform {

  import XFormsModelSchemaValidator.*

  private var modelElement: Element = _
  private var indentedLogger: IndentedLogger = _

  private var schemaGrammar: Grammar = _
  private var schemaURIs: Array[String] = _
  private var schemaElements: List[Element] = _

  private var documentDeclaration: REDocumentDeclaration = _

  def this(modelElement: Element, indentedLogger: IndentedLogger) = {
    this()
    this.modelElement = modelElement
    this.indentedLogger = indentedLogger

    val schemaAttribute = modelElement.attributeValue(XFormsNames.SCHEMA_QNAME)
    if (schemaAttribute != null)
      this.schemaURIs = StringUtils.splitToArray(MarkupUtils.encodeHRRI(schemaAttribute, false))

    this.schemaElements = modelElement.elements(XMLConstants.XML_SCHEMA_QNAME).toList
  }

  def this(schemaURI: String) = {
    this()
    this.schemaURIs = Array(schemaURI)
  }

  def hasSchema: Boolean = schemaGrammar != null

  def getSchemaURIs: Array[String] = schemaURIs

  private def addSchemaError(element: Element, errMsg: String): Unit = {
    val newErrorMessage =
      if (errMsg == null)
        "Missing character data."
      else
        errMsg
    if (indentedLogger != null && indentedLogger.debugEnabled)
      indentedLogger.logDebug("schema", "validation error", "error", newErrorMessage)
    InstanceData.addSchemaError(element)
  }

  private def addSchemaError(attribute: Attribute, schemaError: String): Unit = {
    if (indentedLogger != null && indentedLogger.debugEnabled)
      indentedLogger.logDebug("schema", "validation error", "error", schemaError)
    InstanceData.addSchemaError(attribute)
  }

  private def handleIDErrors(icc: IDConstraintChecker): Boolean = {
    var isValid = true
    var errorInfo: ErrorInfo = icc.clearErrorInfo()
    while (errorInfo != null) {
      if (indentedLogger != null && indentedLogger.debugEnabled)
        indentedLogger.logDebug("schema", "validation error", "error", errorInfo.message)
      addSchemaError(errorInfo.element, errorInfo.message)
      isValid = false
      errorInfo = icc.clearErrorInfo()
    }
    isValid
  }

  private def validateElement(
    element         : Element,
    acceptor        : Acceptor,
    icc             : IDConstraintChecker,
    isReportErrors  : Boolean
  ): Boolean = {

    var isElementValid = true

    val uri = element.getNamespaceURI
    val name = element.getName
    val qName = element.getQualifiedName
    val attributes = new AttributesImpl
    for (attribute <- element.attributes) {
      attributes.addAttribute(
        attribute.getNamespaceURI,
        attribute.getName,
        attribute.getQualifiedName,
        null,
        attribute.getValue
      )
    }
    validationContext.setCurrentElement(element)
    val startTagInfo = new StartTagInfo(uri, name, qName, attributes, validationContext)

    val stringRef = new StringRef

    var tempChildAcceptor = acceptor.createChildAcceptor(startTagInfo, null)
    if (tempChildAcceptor == null) {
      if (isReportErrors) {
        tempChildAcceptor = acceptor.createChildAcceptor(startTagInfo, stringRef)
        addSchemaError(element, stringRef.str)
        isElementValid = false
      } else {
        return false
      }
    }
    val childAcceptor = tempChildAcceptor

    if (icc != null && isReportErrors) {
      icc.onNextAcceptorReady(startTagInfo, childAcceptor, element)
      isElementValid &= handleIDErrors(icc)
    }

    val datatypeRef = new DatatypeRef
    val childrenValid = validateChildren(element, childAcceptor, startTagInfo, icc, datatypeRef, isReportErrors)
    if (!childrenValid) {
      if (isReportErrors)
        isElementValid = false
      else
        return false
    }

    if (!childAcceptor.isAcceptState(null)) {
      if (isReportErrors) {
        childAcceptor.isAcceptState(stringRef)
        addSchemaError(element, stringRef.str)
        isElementValid = false
      } else {
        return false
      }
    } else {
      setDataType(datatypeRef, element)
    }

    if (icc != null && isReportErrors) {
      icc.endElement(element, datatypeRef.types)
      isElementValid &= handleIDErrors(icc)
    }

    if (!acceptor.stepForward(childAcceptor, null)) {
      if (isReportErrors) {
        acceptor.stepForward(childAcceptor, stringRef)
        addSchemaError(element, stringRef.str)
        isElementValid = false
      } else {
        return false
      }
    }

    if (isReportErrors) isElementValid else true
  }

  private def setDataType(datatypeRef: DatatypeRef, node: Node): Unit = {
    if (datatypeRef.types != null && datatypeRef.types.length > 0) {
      val datatype = datatypeRef.types(0)
      datatype match {
        case xsDatatype: XSDatatype =>
          val dataTypeURI = xsDatatype.getNamespaceUri
          val dataTypeName = xsDatatype.getName
          if (dataTypeName != null && dataTypeName.nonEmpty)
            InstanceData.setSchemaType(node, QName(dataTypeName, "", dataTypeURI))
        case _ =>
      }
    }
  }

  private def validateElementLax(element: Element): Boolean = {
    val xsiType = Extensions.resolveAttValueQNameJava(element, XMLConstants.XSI_TYPE_QNAME, false)
    val (elementURI, elementName) =
      if (xsiType != null)
        (xsiType.namespace.uri, xsiType.localName)
      else
        (element.getNamespaceURI, element.getName)

    var isValid = true
    val expression: Expression =
      schemaGrammar match {
        case xsGrammar: XMLSchemaGrammar =>
          val schema = xsGrammar.getByNamespace(elementURI)
          if (schema != null) {
            val elementDeclExp = schema.elementDecls.get(elementName)
            if (elementDeclExp != null) {
              elementDeclExp
            } else if (xsiType != null) {
              schema.complexTypes.get(elementName)
            } else {
              null
            }
          } else {
            null
          }
        case _ => null
      }

    if (expression != null) {
      val acceptor = documentDeclaration.createAcceptor()
      isValid &= validateElement(element, acceptor, null, isReportErrors = true)
    } else {
      for (childElement <- element.elements) {
        isValid &= validateElementLax(childElement)
      }
    }
    isValid
  }

  private def validateChildren(
    element         : Element,
    acceptor        : Acceptor,
    startTagInfo    : StartTagInfo,
    icc             : IDConstraintChecker,
    datatypeRef     : DatatypeRef,
    isReportErrors  : Boolean
  ): Boolean = {

    var isElementChildrenValid = true
    val stringRef = new StringRef
    val attributeDatatypeRef = new DatatypeRef
    val attributes = startTagInfo.attributes
    val end = attributes.getLength
    var i = 0
    while (i < end) {
      val uri = attributes.getURI(i)
      val name = attributes.getLocalName(i)
      val qName = attributes.getQName(i)
      val value = attributes.getValue(i)
      val attribute = element.attribute(i)

      if (!acceptor.onAttribute2(uri, name, qName, value, startTagInfo.context, null, attributeDatatypeRef)) {
        if (isReportErrors) {
          acceptor.onAttribute2(uri, name, qName, value, startTagInfo.context, stringRef, null)
          addSchemaError(attribute, stringRef.str)
          isElementChildrenValid = false
        } else {
          return false
        }
      }

      setDataType(attributeDatatypeRef, attribute)

      if (icc != null && isReportErrors) {
        icc.feedAttribute(acceptor, attribute, attributeDatatypeRef.types)
        isElementChildrenValid &= handleIDErrors(icc)
      }
      i += 1
    }

    if (!acceptor.onEndAttributes(startTagInfo, null)) {
      if (isReportErrors) {
        acceptor.onEndAttributes(startTagInfo, stringRef)
        addSchemaError(element, stringRef.str)
        isElementChildrenValid = false
      } else {
        return false
      }
    }

    val stringCareLevel = acceptor.getStringCareLevel
    for (childElement <- element.elements) {
      val isChildElementValid = validateElement(childElement, acceptor, icc, isReportErrors)
      if (!isChildElementValid) {
        if (isReportErrors)
          isElementChildrenValid = false
        else
          return false
      }
    }

    val text = element.getText
    if (stringCareLevel == Acceptor.STRING_IGNORE) {
      datatypeRef.types = null
    } else if (stringCareLevel == Acceptor.STRING_PROHIBITED) {
      val trimmed = StringUtils.trimAllToEmpty(text)
      if (trimmed.nonEmpty) {
        if (isReportErrors) {
          addSchemaError(element, stringRef.str)
          isElementChildrenValid = false
        } else {
          return false
        }
      }
      datatypeRef.types = null
    } else if (stringCareLevel == Acceptor.STRING_STRICT) {
      if (!acceptor.onText2(text, startTagInfo.context, null, datatypeRef)) {
        if (isReportErrors) {
          acceptor.onText2(text, startTagInfo.context, stringRef, null)
          addSchemaError(element, stringRef.str)
          isElementChildrenValid = false
        } else {
          return false
        }
      }
    }

    if (isReportErrors) isElementChildrenValid else true
  }

  def loadSchemas(containingDocument: XFormsContainingDocument): Unit = {
    if (schemaURIs != null && schemaURIs.length > 0) {
      val resolvedURLString =
        if (containingDocument != null)
          XFormsCrossPlatformSupport.resolveServiceURL(
            containingDocument,
            modelElement,
            schemaURIs(0),
            UrlRewriteMode.Absolute
          )
        else
          schemaURIs(0)
      schemaGrammar = loadCacheGrammar(containingDocument, resolvedURLString)
    }

    if (schemaElements != null && schemaElements.nonEmpty) {
      schemaGrammar = loadInlineGrammar(containingDocument, schemaElements.head)
    }
  }

  private def loadInlineGrammar(containingDocument: XFormsContainingDocument, schemaElement: Element): Grammar = {
    val reader = newXMLSchemaReader(containingDocument)

    val locationSAXWriter = new LocationSAXWriter
    locationSAXWriter.setContentHandler(reader)
    locationSAXWriter.write(Extensions.createDocumentCopyParentNamespacesJava(schemaElement, false))
    reader.getResult
  }

  def validateInstance(instance: XFormsInstance): Boolean =
    schemaGrammar == null ||
      validateDocument(
        instance.underlyingDocumentOpt.get.getRootElement,
        isLax    = instance.instance.isLaxValidation,
        isStrict = instance.instance.isStrictValidation
      )

  def validateDocument(rootElement: Element, isLax: Boolean, isStrict: Boolean): Boolean =
    if (schemaGrammar != null) {
      if (documentDeclaration == null)
        documentDeclaration = createDocumentDeclaration(schemaGrammar)

      var isValid = true
      if (isLax) {
        isValid &= validateElementLax(rootElement)
      } else if (isStrict) {
        val acceptor = documentDeclaration.createAcceptor()
        val idConstraintChecker = new IDConstraintChecker

        isValid &= validateElement(rootElement, acceptor, idConstraintChecker, isReportErrors = true)
        idConstraintChecker.endDocument()
        isValid &= handleIDErrors(idConstraintChecker)
      }
      isValid
    } else {
      true
    }

  def validateDatatype(
    value             : String,
    typeNamespaceURI  : String,
    typeLocalname     : String,
    typeQName         : String,
    locationData      : LocationData
  ): String = {

    val typeNs = if (typeNamespaceURI == null) "" else typeNamespaceURI

    if (documentDeclaration == null)
      documentDeclaration = createDocumentDeclaration(schemaGrammar)

    val contentModelExpression: Expression =
      if (typeNs == XSAcceptor.XMLSchemaNamespace) {
        try {
          schemaGrammar.getPool.createData(DatatypeFactory.getTypeByName(typeLocalname))
        } catch {
          case _: DatatypeException =>
            throw new SchemaValidationException(s"Built-in schema type not found: $typeLocalname", locationData)
        }
      } else {
        val schema = schemaGrammar match {
          case xsGrammar: XMLSchemaGrammar => xsGrammar.getByNamespace(typeNs)
          case _                           => null
        }
        if (schema == null)
          throw new SchemaValidationException(s"No schema found for namespace: $typeNs", locationData)

        val simpleTypeExpression = schema.simpleTypes.get(typeLocalname)
        if (simpleTypeExpression != null) {
          simpleTypeExpression
        } else {
          val complexTypeExpression = schema.complexTypes.get(typeLocalname)
          if (complexTypeExpression != null) {
            if (complexTypeExpression.simpleBaseType != null) {
              complexTypeExpression
            } else {
              throw new SchemaValidationException(s"Simple type or complex type with simple content required for type: $typeQName", locationData)
            }
          } else {
            val elementDeclExp = schema.elementDecls.get(typeLocalname)
            if (elementDeclExp != null) {
              val xsElementExp = elementDeclExp.getElementExp
              val contentModel = xsElementExp.contentModel
              contentModel match {
                case cte: ComplexTypeExp if cte.simpleBaseType != null =>
                  contentModel
                case _ =>
                  throw new SchemaValidationException(s"Simple type or complex type with simple content required for type: $typeQName", locationData)
              }
            } else {
              throw new SchemaValidationException(s"Simple type or complex type with simple content required for type: $typeQName", locationData)
            }
          }
        }
      }

    val expressionAcceptor = new SimpleAcceptor(documentDeclaration, contentModelExpression, null, null)
    val errorStringRef = new StringRef
    val datatypeRef = new DatatypeRef

    if (!expressionAcceptor.onText2(value, validationContext, errorStringRef, datatypeRef)) {
      if (errorStringRef.str == null)
        errorStringRef.str = "Error validating simple type"
      return errorStringRef.str
    }

    if (!expressionAcceptor.isAcceptState(errorStringRef)) {
      if (errorStringRef.str == null)
        errorStringRef.str = "Error validating simple type"
      return errorStringRef.str
    }

    null
  }

  private def createDocumentDeclaration(grammar: Grammar): REDocumentDeclaration =
    grammar match {
      case xsg: XMLSchemaGrammar => new XSREDocDecl(xsg)
      case _                     => new REDocumentDeclaration(grammar)
    }
}

object XFormsModelSchemaValidator {

  val logger: org.log4s.Logger = LoggerFactory.createLogger(classOf[XFormsModelSchemaValidator])

  private val validationContext = new ValidationContext

  private class ValidationContext extends IDContextProvider2 {
    private var currentElement: Element = _

    def setCurrentElement(element: Element): Unit =
      currentElement = element

    def resolveNamespacePrefix(prefix: String): String =
      if (currentElement != null)
        currentElement.namespaceForPrefix(prefix).orNull
      else
        null

    def getBaseUri: String = null
    def isUnparsedEntity(s: String): Boolean = false
    def isNotation(s: String): Boolean = false
    def onID(dt: Datatype, st: StringToken): Unit = ()
  }
}

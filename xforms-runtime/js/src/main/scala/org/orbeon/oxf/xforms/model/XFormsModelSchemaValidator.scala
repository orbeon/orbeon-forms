package org.orbeon.oxf.xforms.model

import org.orbeon.datatypes.LocationData
import org.orbeon.dom
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.XFormsContainingDocument


class XFormsModelSchemaValidator(schemaURI: String) {

  // For:
  //
  // - `dayTimeDuration`
  // - `yearMonthDuration`
  // - `card-number`
  //
  def validateDatatype(
    value            : String,
    typeNamespaceURI : String,
    typeLocalname    : String,
    typeQName        : String,
    locationData     : LocationData
  ): String = null // TODO

  // For now, we don't support XML Schema validation on the JavaScript platform
  def this(modelElement: dom.Element, indentedLogger: IndentedLogger) = this(null)
  def loadSchemas(containingDocument: XFormsContainingDocument): Unit = ()
  def validateInstance(instance: XFormsInstance): Boolean = throw new UnsupportedOperationException
  def hasSchema: Boolean = false
  def getSchemaURIs: Array[String] = Array.empty
}

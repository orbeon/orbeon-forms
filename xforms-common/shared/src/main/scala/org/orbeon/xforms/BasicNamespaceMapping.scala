package org.orbeon.xforms

import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.oxf.xml.XMLConstants.{XHTML_PREFIX, XHTML_SHORT_PREFIX}
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xml.NamespaceMapping


object BasicNamespaceMapping {

  val Mapping =
    NamespaceMapping(Map(
      XFORMS_PREFIX        -> Namespaces.XF,
      XFORMS_SHORT_PREFIX  -> Namespaces.XF,
      XXFORMS_PREFIX       -> Namespaces.XXF,
      XXFORMS_SHORT_PREFIX -> Namespaces.XXF,
      XML_EVENTS_PREFIX    -> XML_EVENTS_NAMESPACE_URI,
      XHTML_PREFIX         -> XMLConstants.XHTML_NAMESPACE_URI,
      XHTML_SHORT_PREFIX   -> XMLConstants.XHTML_NAMESPACE_URI
    ))
}

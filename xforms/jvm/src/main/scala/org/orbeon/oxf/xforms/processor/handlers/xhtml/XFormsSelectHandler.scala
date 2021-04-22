package org.orbeon.oxf.xforms.processor.handlers.xhtml

import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.processor.handlers.HandlerContext
import org.xml.sax.Attributes


class XFormsSelectHandler(
  uri            : String,
  localname      : String,
  qName          : String,
  attributes     : Attributes,
  matched        : ElementAnalysis,
  handlerContext : HandlerContext
) extends
  XFormsSelect1Handler(
    uri,
    localname,
    qName,
    attributes,
    matched,
    handlerContext
  )
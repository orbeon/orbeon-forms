package org.orbeon.oxf.xml

import javax.xml.transform.{Result, Source}

import org.orbeon.saxon.event._
import org.orbeon.saxon.trans.XPathException
import org.orbeon.saxon.{Configuration, Controller}
import org.xml.sax.SAXParseException

// Custom version of Saxon's IdentityTransformer which hooks up a `ComplexContentOutputter`
class IdentityTransformerWithFixup(config: Configuration) extends Controller(config) {

  override def transform(source: Source, result: Result): Unit =
    try {
      val pipelineConfig = makePipelineConfiguration

      val receiver =
        getConfiguration.getSerializerFactory.getReceiver(result, pipelineConfig, getOutputProperties)

      // To remove duplicate namespace declarations
      val reducer = new NamespaceReducer
      reducer.setUnderlyingReceiver(receiver)
      reducer.setPipelineConfiguration(pipelineConfig)

      // To fixup namespaces
      val cco = new ComplexContentOutputter
      cco.setHostLanguage(pipelineConfig.getHostLanguage)
      cco.setPipelineConfiguration(pipelineConfig)
      cco.setReceiver(reducer)

      new Sender(pipelineConfig).send(source, cco, true)
    } catch {
      case xpe: XPathException =>
        xpe.getException match {
          case spe: SAXParseException if ! spe.getException.isInstanceOf[RuntimeException] => // NOP
          case _ => reportFatalError(xpe)
        }
        throw xpe
    }
}

//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is Michael H. Kay
//
// Portions created by (your name) are Copyright (C) (your legal entity). All Rights Reserved.
//
// Contributor(s): None
//

/**
 * Copyright (C) 2018 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.submission

import org.orbeon.dom._
import org.orbeon.oxf.test.XMLSupport
import org.orbeon.oxf.xforms.XFormsConstants
import org.scalatest.FunSpec
import org.orbeon.oxf.xml.Dom4j._

class SubmissionRelevanceTest extends FunSpec with XMLSupport {
  
  describe("Submission relevance options") {

    val doc: Document =
      <form xmlns:xf="http://www.w3.org/2002/xforms" xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
        <e1 xf:relevant="false"/>
        <e2 xxf:relevant="false"/>
      </form>

    val expected: Document =
      <form xmlns:xxf="http://orbeon.org/oxf/xml/xforms" xmlns:xf="http://www.w3.org/2002/xforms">
        <e1 xf:relevant="false" xxf:relevant="false"/>
        <e2 xxf:relevant="false"/>
      </form>

    val XfRelevantQName  = QName("relevant", XFormsConstants.XFORMS_NAMESPACE_SHORT)
    val XXFRelevantQName = QName("relevant", XFormsConstants.XXFORMS_NAMESPACE_SHORT)

    val isNonRelevant: Node ⇒ Boolean = {
      case e: Element   ⇒ (e.attributeValueOpt(XfRelevantQName) contains false.toString) || (e.attributeValueOpt(XXFRelevantQName) contains false.toString)
      case _            ⇒ false
    }

    it("must annotate and keep existing annotation when needed") {
      XFormsModelSubmissionBase.annotateNonRelevantElements(doc, XXFRelevantQName, isNonRelevant)
      assertXMLDocumentsIgnoreNamespacesInScope(expected, doc)
    }
  }

}

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
import org.orbeon.oxf.xml.dom.Converter._
import org.orbeon.xforms.{RelevanceHandling, XFormsNames}
import org.scalatest.funspec.AnyFunSpec

class SubmissionRelevanceTest extends AnyFunSpec with XMLSupport {

  describe("Submission relevance options") {

    def newDoc: Document =
        <form xmlns:xf="http://www.w3.org/2002/xforms" xmlns:xxf="http://orbeon.org/oxf/xml/xforms">
          <e1 xf:relevant="false">e1</e1>
          <e2 xxf:relevant="false"/>
          <e3 xf:relevant="true"/>
          <e4 xxf:relevant="true"/>
          <e5>
            <e5-iteration xf:relevant="false">
              <e6 xf:relevant="true">e61</e6>
            </e5-iteration>
            <e5-iteration>
              <e6 xf:relevant="false">e62</e6>
            </e5-iteration>
          </e5>
        </form>.toDocument

    val expected: Map[RelevanceHandling, Document] =
      Map(
        RelevanceHandling.Keep ->
          <form xmlns:xxf="http://orbeon.org/oxf/xml/xforms" xmlns:xf="http://www.w3.org/2002/xforms">
            <e1 xxf:relevant="false">e1</e1>
            <e2/>
            <e3/>
            <e4/>
            <e5>
              <e5-iteration xxf:relevant="false">
                <e6>e61</e6>
              </e5-iteration>
              <e5-iteration>
                <e6 xxf:relevant="false">e62</e6>
              </e5-iteration>
            </e5>
          </form>.toDocument,
        RelevanceHandling.Remove ->
          <form xmlns:xxf="http://orbeon.org/oxf/xml/xforms" xmlns:xf="http://www.w3.org/2002/xforms">
            <e2/>
            <e3/>
            <e4/>
            <e5>
              <e5-iteration/>
            </e5>
          </form>.toDocument,
        RelevanceHandling.Empty ->
          <form xmlns:xxf="http://orbeon.org/oxf/xml/xforms" xmlns:xf="http://www.w3.org/2002/xforms">
            <e1 xxf:relevant="false"/>
            <e2/>
            <e3/>
            <e4/>
            <e5>
              <e5-iteration xxf:relevant="false">
                <e6/>
              </e5-iteration>
              <e5-iteration>
                <e6 xxf:relevant="false"/>
              </e5-iteration>
            </e5>
          </form>.toDocument
      )

    val XFRelevantQName  = QName("relevant", XFormsNames.XFORMS_NAMESPACE_SHORT)
    val XXFRelevantQName = QName("relevant", XFormsNames.XXFORMS_NAMESPACE_SHORT)

    for (relevanceHandling <- RelevanceHandling.values)
      it(s"must annotate and keep existing annotation when needed with $relevanceHandling") {

        val doc = newDoc

        XFormsModelSubmissionBase.processRelevant(
          doc                           = doc,
          relevanceHandling             = relevanceHandling,
          relevantAttOpt                = Some(XFRelevantQName),
          relevantAnnotationAttQNameOpt = Some(XXFRelevantQName)
        )
        assertXMLDocumentsIgnoreNamespacesInScope(expected(relevanceHandling), doc)
      }
  }

}

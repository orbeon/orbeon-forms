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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.SimpleProcessor
import org.orbeon.oxf.xforms.XFormsStaticStateImpl
import org.orbeon.oxf.xml.XMLReceiver


/**
 * Simple processor used by unit tests to output XPath analysis information.
 *
 * See tests-xforms-xpath-analysis.xml
 */
class XPathAnalysisProcessor extends SimpleProcessor {
  def generateAnalysis(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {
    val formDocument = readInputAsOrbeonDom(pipelineContext, "form")
    val staticState = XFormsStaticStateImpl.createFromDocument(formDocument)

    PartAnalysisDebugSupport.writePart(staticState.topLevelPart)(xmlReceiver)
  }
}

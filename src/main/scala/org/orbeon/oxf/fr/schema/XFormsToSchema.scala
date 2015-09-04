/**
 * Copyright (C) 2012 Orbeon, Inc.
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

import org.orbeon.oxf.common.Version
import org.orbeon.oxf.pipeline.api.{ExternalContext, PipelineContext}
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.control.controls.XFormsSelectControl
import org.orbeon.oxf.xforms.processor.XFormsToSomething
import org.orbeon.oxf.xforms.processor.XFormsToSomething.Stage2CacheableState
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xml._
import org.orbeon.saxon.om.DocumentInfo
import org.orbeon.scaxon.XML.{Attribute ⇒ _, Text ⇒ _, _}

/**
 *  Supported:
 *  - Simple types, adding type="…" on the xs:element
 *  - Repeats, appropriately adding min="…" max="…" on the xs:element
 *  - Section templates, using the binds defined in the app or global library
 *  - Custom namespaces, properly including namespace declarations
 *
 *  To do:
 *  - For testing, we're passing the orbeon-token from the app context, the service is defined as a page in page-flow.xml
 *
 *  Not supported:
 *  - Custom XML instance (adds lots of complexity, thinking of dropping for 4.0, and maybe foreseeable future)
 */
class XFormsToSchema extends XFormsToSomething {

  private val SchemaPath  = """/fr/service/?([^/^.]+)/([^/^.]+)/[^/^.]+""".r

  case class Libraries(orbeon: Option[DocumentInfo], app: Option[DocumentInfo])

  protected def produceOutput(pipelineContext: PipelineContext,
                outputName: String,
                externalContext: ExternalContext,
                indentedLogger: IndentedLogger,
                stage2CacheableState: Stage2CacheableState,
                containingDocument: XFormsContainingDocument,
                xmlReceiver: XMLReceiver): Unit = {
    // This is a PE feature
    Version.instance.requirePEFeature("XForms schema generator service")

    val SchemaPath(appName, _) = NetUtils.getExternalContext.getRequest.getRequestPath
    val formSource = readInputAsTinyTree(pipelineContext, getInputByName(ProcessorImpl.INPUT_DATA), XPath.GlobalConfiguration)

    // Send result to output
    val schema = SchemaGenerator.createSchema(appName, formSource, containingDocument)
    elemToSAX(schema, xmlReceiver)
  }
}

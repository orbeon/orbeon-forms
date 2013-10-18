/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.fr.relational.crud

import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.pipeline.api.PipelineContext


class CRUD
        extends ProcessorImpl
        with RequestResponse
        with Common
        with Get
        with Put {

    override def start(pipelineContext: PipelineContext) {
        httpRequest.getMethod match {
            case "GET" ⇒ get()
            case "PUT" ⇒ put()
            case _     ⇒ httpResponse.setStatus(405)
        }
    }
}
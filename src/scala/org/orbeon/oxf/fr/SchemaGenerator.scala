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
package org.orbeon.oxf.fr

import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.processor.ProcessorImpl
import org.orbeon.oxf.common.OXFException

class SchemaGenerator extends ProcessorImpl {

    private val FormPath = """/fr/service/[^/]+/[^/]+/schema""".r

    override def start(pipelineContext: PipelineContext) {
        val ec = NetUtils.getExternalContext
        val incomingPath = ec.getRequest.getRequestPath
    }
}

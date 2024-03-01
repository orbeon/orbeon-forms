/**
  * Copyright (C) 2016 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.test

import org.log4s.Logger
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.LoggerFactory
import org.scalatest.{BeforeAndAfter, Suite}


object ResourceManagerSupportInitializer extends WithResourceManagerSupport {
  override lazy val logger: Logger        = LoggerFactory.createLogger(ResourceManagerSupportInitializer.getClass)
  override lazy val propertiesUrl: String = "oxf:/ops/unit-tests/properties.xml"
}

trait ResourceManagerSupport extends Suite with BeforeAndAfter {

  ResourceManagerSupportInitializer

  locally {
    var pipelineContext: Option[PipelineContext] = None

    before { pipelineContext = Some(PipelineSupport.createPipelineContextWithExternalContext()) }
    after  { pipelineContext foreach (_.destroy(true)) }
  }
}

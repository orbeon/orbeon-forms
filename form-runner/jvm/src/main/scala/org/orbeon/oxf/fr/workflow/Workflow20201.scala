/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.oxf.fr.workflow

import io.circe.{Decoder, Encoder}
import org.orbeon.oxf.fr.ui.ScalaToXml


object Workflow20201 extends ScalaToXml {

  type MyState = definitions20201.WorkflowConfig

  import io.circe.generic.auto._

  val encoder: Encoder[MyState] = implicitly
  val decoder: Decoder[MyState] = implicitly

  val StartedStageName   = "started"
  val SubmittedStageName = "submitted"
}

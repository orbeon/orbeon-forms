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
package org.orbeon.oxf.fb

import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.orbeon.dom.Document
import org.orbeon.oxf.processor.{DOMSerializer, Processor}
import org.orbeon.oxf.test.ProcessorTest

import java.{util ⇒ ju}

// Not ideal: this is so that we can run the annotate.xpl/deannotate.xpl tests using `ProcessorTest`
@RunWith(classOf[Parameterized])
class FormBuilderProcessorTest(
  description      : String,
  processor        : Processor,
  requestURL       : String,
  domSerializers   : ju.List[DOMSerializer],
  expectedDocuments: ju.List[Document]
) extends ProcessorTest(
  description,
  processor,
  requestURL,
  domSerializers,
  expectedDocuments
)
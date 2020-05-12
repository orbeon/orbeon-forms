/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */
package org.orbeon.oxf.fr.excel

import java.{lang => jl}

import org.scalatest.funspec.AnyFunSpecLike

class NumberToTextConverterTest extends AnyFunSpecLike {

  describe("All number conversion examples") {
    for (example <- NumberToTextConversionExamples.Examples)
      it(s"must pass `$example`") {
        if (example.isNaN) {
          assert("NaN" == jl.Double.toString(jl.Double.longBitsToDouble(example.rawDoubleBits)))
          assert(example.excelRendering == NumberToTextConverter.rawDoubleBitsToText(example.rawDoubleBits))
        } else {
          assert(example.excelRendering == NumberToTextConverter.toText(example.doubleValue))
        }
      }
  }

  describe("Simple rendering bug 56156") {
    val dResult = 0.05 + 0.01 // values chosen to produce rounding anomaly
    val actualText = NumberToTextConverter.toText(dResult)
    val jdkText = jl.Double.toString(dResult)
    // "0.060000000000000005"
    it("must pass") {
      assert(jdkText != actualText)
      assert("0.06" == actualText)
    }
  }
}

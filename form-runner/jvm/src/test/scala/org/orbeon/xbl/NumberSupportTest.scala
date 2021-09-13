/**
  * Copyright (C) 2018 Orbeon, Inc.
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
package org.orbeon.xbl

import org.orbeon.dom.QName
import org.scalatest.funspec.AnyFunSpec

class NumberSupportTest extends AnyFunSpec {

  case class TestNode(value: String, datatype: Option[QName], customMips: Map[String, String])

  object TestNumberSupport extends NumberSupport[TestNode] {
    def getStringValue (binding: TestNode): String                       = binding.value
    def getDatatypeOpt (binding: TestNode): Option[QName]                = binding.datatype
    def getCustomMipOpt(binding: TestNode, name: String): Option[String] = binding.customMips.get(name)
  }

  describe("The `displayValue` function") {

    val Expected = List[(String, Char, Option[Char], Option[Int], Boolean, String)](
      ("123456.789", '.', Some(','), Some(2), true,   "123,456.79"  ),
      ("123456.789", '.', None     , Some(2), true,   "123456.79"   ),

      ("123456.789", '.', Some(','), None   , true,   "123,456.789" ),
      ("123456.789", '.', None     , None   , true,   "123456.789"  ),

      ("123456.789", '.', Some(','), None   , false,  "123,456.789" ),
      ("123456.789", '.', None     , None   , false,  "123456.789"  ),

      ("123456.789", '.', Some(','), Some(4), true,   "123,456.7890"),
      ("123456.789", '.', None     , Some(4), true,   "123456.7890" ),

      ("123456.789", '.', Some(','), Some(4), false,  "123,456.7890"),
      ("123456.789", '.', None     , Some(4), false,  "123456.7890" ),

      ("123456."   , '.', Some(','), Some(2), true,   "123,456.00"  ),
      ("123456."   , '.', None     , Some(2), true,   "123456.00"   ),

      ("123456."   , '.', Some(','), Some(2), false,  "123,456.00"  ),
      ("123456."   , '.', None     , Some(2), false,  "123456.00"   ),

      ("123456."   , '.', Some(','), Some(4), true,   "123,456.0000"),
      ("123456."   , '.', None     , Some(4), true,   "123456.0000" ),

      ("123456"    , '.', Some(','), Some(2), true,   "123,456.00"  ),
      ("123456"    , '.', None     , Some(2), true,   "123456.00"   ),

      ("123456"    , '.', Some(','), Some(2), false,  "123,456.00"  ),
      ("123456"    , '.', None     , Some(2), false,  "123456.00"   ),

      ("123456"    , '.', Some(','), Some(0), true,   "123,456"     ),
      ("123456"    , '.', None     , Some(0), true,   "123456"      ),

      ("123456"    , '.', Some(','), Some(0), false,  "123,456"     ),
      ("123456"    , '.', None     , Some(0), false,  "123456"      ),

      ("123456"    , '.', Some(','), Some(4), true,   "123,456.0000"),
      ("123456"    , '.', None     , Some(4), true,   "123456.0000" ),

      ("123456,78" , '.', Some(','), Some(2), true,   "123456,78"   ), // ideal behavior unclear
      ("123456,78" , '.', None     , Some(2), true,   "123456,78"   ), // ideal behavior unclear

      ("123456,78" , '.', Some(','), Some(2), false,  "123456,78"   ), // ideal behavior unclear
      ("123456,78" , '.', None     , Some(2), false,  "123456,78"   ), // ideal behavior unclear

      ("abcd"      , '.', Some(','), Some(2), true,   "abcd"        ),
      ("abcd"      , '.', Some(','), Some(2), false,  "abcd"        ),

      ("1234≡56"   , ',', None     , Some(2), false,  "1234.56"     )
    )

    for (t @ (stored, decimalSeparator, groupingSeparatorOpt, digitsAfterDecimalOpt, roundWhenFormatting, expected) <- Expected)
      it (s"must format for $t") {

        val data = TestNode(stored, None, Map.empty)

        implicit val params = NumberConfig(
          decimalSeparator    = decimalSeparator,
          groupingSeparator   = groupingSeparatorOpt,
          prefix              = "",
          digitsAfterDecimal  = digitsAfterDecimalOpt,
          roundWhenFormatting = roundWhenFormatting,
          roundWhenStoring    = true // unused by `displayValue`
        )

        assert(expected === TestNumberSupport.displayValue(data))
      }
  }

  describe("The `storageValue` function") {

    val Expected = List[(String, Char, Option[Char], Option[Int], Boolean, String)](
      ("123,456.789",     '.', Some(','), Some(2), true,  "123456.79"     ),
      ("123,456.789",     '.', None     , Some(2), true,  "123,456.789"   ),

      (" 123,456.789",    '.', Some(','), Some(2), true,  "123456.79"     ),
      (" 123,456.789",    '.', None     , Some(2), true,  " 123,456.789"  ),

      ("123,456.789",     '.', Some(','), Some(3), true,  "123456.789"    ),
      ("123,456.789",     '.', None     , Some(3), true,  "123,456.789"   ),

      ("123,456.789",     '.', Some(','), Some(4), true,  "123456.789"    ),
      ("123,456.789",     '.', None     , Some(4), true,  "123,456.789"   ),

      ("123,456.789",     '.', Some(','), Some(2), false, "123456.789"    ),
      ("123,456.789",     '.', None     , Some(2), false, "123,456.789"   ),

      ("123,456.79",      '.', Some(','), Some(2), true,  "123456.79"     ),
      ("123,456.79",      '.', None     , Some(2), true,  "123,456.79"    ),

      ("123,456.",        '.', Some(','), Some(2), false, "123456"        ),
      ("123,456.",        '.', None     , Some(2), false, "123,456."      ),

      ("123,456",         '.', Some(','), Some(2), false, "123456"        ),
      ("123,456",         '.', None     , Some(2), false, "123,456"       ),

      ("123,456.",        '.', Some(','), Some(2), true,  "123456"        ),
      ("123,456.",        '.', None     , Some(2), true,  "123,456."      ),

      ("123,456",         '.', Some(','), Some(2), true,  "123456"        ),
      ("123,456",         '.', None     , Some(2), true,  "123,456"       ),

      ("123,456.",        '.', Some(','), None,    true,  "123456"        ),
      ("123,456.",        '.', None     , None,    true,  "123,456."      ),

      ("123,456",         '.', Some(','), None,    true,  "123456"        ),
      ("123,456",         '.', None     , None,    true,  "123,456"       ),

      ("123,456.789",     '.', Some(','), None,    true,  "123456.789"    ),
      ("123,456.789",     '.', None     , None,    true,  "123,456.789"   ),

      ("123,456.789123",  '.', Some(','), None,    true,  "123456.789123" ),
      ("123,456.789123",  '.', None     , None,    true,  "123,456.789123"),

      (",,123,456.89",    '.', Some(','), Some(2), true,  "123456.89"     ),
      (",,123,456.89",    '.', None     , Some(2), true,  ",,123,456.89"  ),

      (".42",             '.', Some(','), Some(2), true,  "0.42"          ),
      (".0",              '.', Some(','), Some(2), true,  "0"             ),
      ("0.",              '.', Some(','), Some(2), true,  "0"             ),
      ("0.000",           '.', Some(','), Some(2), true,  "0"             ),
      ("42",              '.', Some(','), Some(2), true,  "42"            ),
      (" 42 ",            '.', Some(','), Some(2), true,  "42"            ),
      (" 42 ",            '.', Some(','), None,    true,  "42"            ),
      ("abcd",            '.', Some(','), Some(2), true,  "abcd"          ),
      ("  abcd  ",        '.', Some(','), Some(2), true,  "  abcd  "      ), // Q: Should trim?

      // Polish
      ("123,456",         ',', Some(' '), None, true,  "123.456"          ),
      ("123 456",         ',', Some(' '), None, true,  "123456"           ),
      ("123.456",         ',', Some(' '), None, true,  "123.456"          ),
      ("123.456.789",     ',', Some(' '), None, true,  "123≡456≡789"      ), // `.` → `≡` not needed, see comment in `storageValue`

      // French
      ("123.456,789",     ',', Some('.'), None, true,  "123456.789"       ),
      ("123,456.789",     ',', Some('.'), None, true,  "123.456789"       ),
      ("123.456.789",     ',', Some('.'), None, true,  "123456789"        ),
      ("123,456,789",     ',', Some('.'), None, true,  "123,456,789"      ),
    )

    for (t @ (value, decimalSeparator, groupingSeparatorOpt, digitsAfterDecimalOpt, roundWhenStoring, expected) <- Expected)
      it (s"must format for $t") {

        val data = TestNode("", None, Map.empty)

        implicit val params = NumberConfig(
          decimalSeparator    = decimalSeparator,
          groupingSeparator   = groupingSeparatorOpt,
          prefix              = "",
          digitsAfterDecimal  = digitsAfterDecimalOpt,
          roundWhenFormatting = true, // unused by `storageValue`
          roundWhenStoring    = roundWhenStoring
        )

        assert(expected === TestNumberSupport.storageValue(value, data))
      }
  }

  describe("The `roundBigDecimal` function") {

    val Expected = List[(String, Int, String)](
      ("123456.789", 2, "123456.79"),
      ("123456.789", 3, "123456.789"),
      ("123456.0",   3, "123456.000"),
      ("123456.",    3, "123456.000"),
      ("123456",     3, "123456.000"),
      ("1.5",        0, "2"),
      ("2.5",        0, "3"),
      ("-1.5",       0, "-1"),
      ("-2.5",       0, "-2")
    )

    for (t @ (value, precision, expected) <- Expected)
      it (s"must round for $t") {
        assert(expected === TestNumberSupport.roundBigDecimal(scala.BigDecimal(value), precision).bigDecimal.toPlainString)
      }
  }
}

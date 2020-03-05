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

import java.util.Locale
import java.{lang => jl}


object NumberToTextConversionExamples {

  object ExampleConversion {

    private def doubleToHexString(d: Double) =
      "0x" + jl.Long.toHexString(jl.Double.doubleToLongBits(d)).toUpperCase(Locale.ROOT) + "L"

    def apply(rawDoubleBits: Long, javaRendering: String, excelRendering: String): ExampleConversion = {
      val doubleValue = jl.Double.longBitsToDouble(rawDoubleBits)
      if (javaRendering == "NaN") {
        if (! jl.Double.isNaN(doubleValue))
          throw new IllegalArgumentException("value must be NaN")
      } else {
        if (jl.Double.isNaN(doubleValue))
          throw new IllegalArgumentException("value must not be NaN")
        // just to be dead sure test conversion in Java both ways
        val javaToStringOk = javaRendering == jl.Double.toString(doubleValue)
        val javaParseOk    = javaRendering.toDouble == doubleValue
        if (! javaToStringOk || ! javaParseOk) {

          val msgA = s"Specified `rawDoubleBits` `${ExampleConversion.doubleToHexString(doubleValue)}` encodes to double `$doubleValue`."
          val msgB = s"Specified `javaRendering` `$javaRendering` parses as double with `rawDoubleBits` `${ExampleConversion.doubleToHexString(javaRendering.toDouble)}`."

          throw new RuntimeException(msgA + ' ' + msgB)
        }
      }
      ExampleConversion(
        javaRendering,
        excelRendering,
        doubleValue,
        rawDoubleBits,
      )
    }
  }

  case class ExampleConversion(
    javaRendering  : String,
    excelRendering : String,
    doubleValue    : Double,
    rawDoubleBits  : Long
  ) {
    def isNaN: Boolean = jl.Double.isNaN(doubleValue)
  }

  // Number rendering examples as observed from Excel.
  val Examples = List(
    // basic  rs
    ExampleConversion(0x0000000000000000L, "0.0",                     "0"),
    ExampleConversion(0x3FF0000000000000L, "1.0",                     "1"),
    ExampleConversion(0x3FF00068DB8BAC71L, "1.0001",                  "1.0001"),
    ExampleConversion(0x4087A00000000000L, "756.0",                   "756"),
    ExampleConversion(0x401E3D70A3D70A3DL, "7.56",                    "7.56"),
    ExampleConversion(0x405EDD3C07FB4C8CL, "123.4567890123455",       "123.456789012345"),
    ExampleConversion(0x405EDD3C07FB4C99L, "123.45678901234568",      "123.456789012346"),
    ExampleConversion(0x405EDD3C07FB4CAEL, "123.45678901234598",      "123.456789012346"),
    ExampleConversion(0x4132D687E3DF2180L, "1234567.8901234567",      "1234567.89012346"),
    ExampleConversion(0x3F543A272D9E0E49L, "0.001234567890123455",    "0.00123456789012345"),
    ExampleConversion(0x3F543A272D9E0E4AL, "0.0012345678901234552",   "0.00123456789012346"),
    ExampleConversion(0x3F543A272D9E0E55L, "0.0012345678901234576",   "0.00123456789012346"),
    ExampleConversion(0x3F543A272D9E0E72L, "0.0012345678901234639",   "0.00123456789012346"),
    ExampleConversion(0x3F543A272D9E0E76L, "0.0012345678901234647",   "0.00123456789012346"),
    ExampleConversion(0x3F543A272D9E0E77L, "0.001234567890123465",    "0.00123456789012346"),
    ExampleConversion(0x3F543A272D9E0E78L, "0.0012345678901234652",   "0.00123456789012347"),
    ExampleConversion(0x3F543A272D9E0EA5L, "0.001234567890123475",    "0.00123456789012347"),
    ExampleConversion(0x3F543A272D9E0EA6L, "0.0012345678901234751",   "0.00123456789012348"),
    ExampleConversion(0x544CE6345CF3209CL, "1.2345678901234549E98",   "1.23456789012345E+98"),
    ExampleConversion(0x544CE6345CF3209DL, "1.234567890123455E98",    "1.23456789012346E+98"),
    ExampleConversion(0x544CE6345CF320DEL, "1.2345678901234649E98",   "1.23456789012346E+98"),
    ExampleConversion(0x544CE6345CF320DFL, "1.234567890123465E98",    "1.23456789012347E+98"),
    ExampleConversion(0x544CE6345CF32120L, "1.234567890123475E98",    "1.23456789012347E+98"),
    ExampleConversion(0x544CE6345CF32121L, "1.2345678901234751E98",   "1.23456789012348E+98"),
    ExampleConversion(0x54820FE0BA17F5E9L, "1.23456789012355E99",     "1.2345678901236E+99"),
    ExampleConversion(0x54820FE0BA17F5EAL, "1.2345678901235502E99",   "1.2345678901236E+99"),
    ExampleConversion(0x54820FE0BA17F784L, "1.2345678901236498E99",   "1.2345678901237E+99"),
    ExampleConversion(0x54820FE0BA17F785L, "1.23456789012365E99",     "1.2345678901237E+99"),
    ExampleConversion(0x54820FE0BA17F920L, "1.2345678901237498E99",   "1.2345678901238E+99"),
    ExampleConversion(0x54820FE0BA17F921L, "1.23456789012375E99",     "1.2345678901238E+99"),
    // transitions around the E98,E99,E100 boundaries
    ExampleConversion(0x547D42AEA2879F19L, "9.999999999999974E98",    "9.99999999999997E+98"),
    ExampleConversion(0x547D42AEA2879F1AL, "9.999999999999975E98",    "9.99999999999998E+98"),
    ExampleConversion(0x547D42AEA2879F21L, "9.999999999999984E98",    "9.99999999999998E+98"),
    ExampleConversion(0x547D42AEA2879F22L, "9.999999999999985E98",    "9.99999999999999E+98"),
    ExampleConversion(0x547D42AEA2879F2AL, "9.999999999999995E98",    "9.99999999999999E+98"),
    ExampleConversion(0x547D42AEA2879F2BL, "9.999999999999996E98",    "1E+99"),
    ExampleConversion(0x547D42AEA287A0A0L, "1.0000000000000449E99",   "1E+99"),
    ExampleConversion(0x547D42AEA287A0A1L, "1.000000000000045E99",    "1.0000000000001E+99"),
    ExampleConversion(0x547D42AEA287A3D8L, "1.0000000000001449E99",   "1.0000000000001E+99"),
    ExampleConversion(0x547D42AEA287A3D9L, "1.000000000000145E99",    "1.0000000000002E+99"),
    ExampleConversion(0x547D42AEA287A710L, "1.000000000000245E99",    "1.0000000000002E+99"),
    ExampleConversion(0x547D42AEA287A711L, "1.0000000000002451E99",   "1.0000000000003E+99"),
    ExampleConversion(0x54B249AD2594C2F9L, "9.999999999999744E99",    "9.9999999999997E+99"),
    ExampleConversion(0x54B249AD2594C2FAL, "9.999999999999746E99",    "9.9999999999998E+99"),
    ExampleConversion(0x54B249AD2594C32DL, "9.999999999999845E99",    "9.9999999999998E+99"),
    ExampleConversion(0x54B249AD2594C32EL, "9.999999999999847E99",    "9.9999999999999E+99"),
    ExampleConversion(0x54B249AD2594C360L, "9.999999999999944E99",    "9.9999999999999E+99"),
    ExampleConversion(0x54B249AD2594C361L, "9.999999999999946E99",    "1E+100"),
    ExampleConversion(0x54B249AD2594C464L, "1.0000000000000449E100",  "1E+100"),
    ExampleConversion(0x54B249AD2594C465L, "1.000000000000045E100",   "1.0000000000001E+100"),
    ExampleConversion(0x54B249AD2594C667L, "1.000000000000145E100",   "1.0000000000001E+100"),
    ExampleConversion(0x54B249AD2594C668L, "1.0000000000001451E100",  "1.0000000000002E+100"),
    ExampleConversion(0x54B249AD2594C86AL, "1.000000000000245E100",   "1.0000000000002E+100"),
    ExampleConversion(0x54B249AD2594C86BL, "1.0000000000002452E100",  "1.0000000000003E+100"),
    ExampleConversion(0x2B95DF5CA28EF4A8L, "1.0000000000000251E-98",  "1.00000000000003E-98"),
    ExampleConversion(0x2B95DF5CA28EF4A7L, "1.000000000000025E-98",   "1.00000000000002E-98"),
    ExampleConversion(0x2B95DF5CA28EF46AL, "1.000000000000015E-98",   "1.00000000000002E-98"),
    ExampleConversion(0x2B95DF5CA28EF469L, "1.0000000000000149E-98",  "1.00000000000001E-98"),
    ExampleConversion(0x2B95DF5CA28EF42DL, "1.0000000000000051E-98",  "1.00000000000001E-98"),
    ExampleConversion(0x2B95DF5CA28EF42CL, "1.000000000000005E-98",   "1E-98"),
    ExampleConversion(0x2B95DF5CA28EF3ECL, "9.999999999999946E-99",   "1E-98"),
    ExampleConversion(0x2B95DF5CA28EF3EBL, "9.999999999999944E-99",   "9.9999999999999E-99"),
    ExampleConversion(0x2B95DF5CA28EF3AEL, "9.999999999999845E-99",   "9.9999999999999E-99"),
    ExampleConversion(0x2B95DF5CA28EF3ADL, "9.999999999999843E-99",   "9.9999999999998E-99"),
    ExampleConversion(0x2B95DF5CA28EF371L, "9.999999999999746E-99",   "9.9999999999998E-99"),
    ExampleConversion(0x2B95DF5CA28EF370L, "9.999999999999744E-99",   "9.9999999999997E-99"),
    ExampleConversion(0x2B617F7D4ED8C7F5L, "1.000000000000245E-99",   "1.0000000000003E-99"),
    ExampleConversion(0x2B617F7D4ED8C7F4L, "1.0000000000002449E-99",  "1.0000000000002E-99"),
    ExampleConversion(0x2B617F7D4ED8C609L, "1.0000000000001452E-99",  "1.0000000000002E-99"),
    ExampleConversion(0x2B617F7D4ED8C608L, "1.000000000000145E-99",   "1.0000000000001E-99"),
    ExampleConversion(0x2B617F7D4ED8C41CL, "1.000000000000045E-99",   "1.0000000000001E-99"),
    ExampleConversion(0x2B617F7D4ED8C41BL, "1.0000000000000449E-99",  "1E-99"),
    ExampleConversion(0x2B617F7D4ED8C323L, "9.999999999999945E-100",  "1E-99"),
    ExampleConversion(0x2B617F7D4ED8C322L, "9.999999999999943E-100",  "9.9999999999999E-100"),
    ExampleConversion(0x2B617F7D4ED8C2F2L, "9.999999999999846E-100",  "9.9999999999999E-100"),
    ExampleConversion(0x2B617F7D4ED8C2F1L, "9.999999999999844E-100",  "9.9999999999998E-100"),
    ExampleConversion(0x2B617F7D4ED8C2C1L, "9.999999999999746E-100",  "9.9999999999998E-100"),
    ExampleConversion(0x2B617F7D4ED8C2C0L, "9.999999999999744E-100",  "9.9999999999997E-100"),
    // small numbers
    ExampleConversion(0x3EE9E409302678BAL, "1.2345678901234568E-5",   "1.23456789012346E-05"),
    ExampleConversion(0x3F202E85BE180B74L, "1.2345678901234567E-4",   "0.000123456789012346"),
    ExampleConversion(0x3F543A272D9E0E51L, "0.0012345678901234567",   "0.00123456789012346"),
    ExampleConversion(0x3F8948B0F90591E6L, "0.012345678901234568",    "0.0123456789012346"),
    ExampleConversion(0x3EE9E409301B5A02L, "1.23456789E-5",           "0.0000123456789"),
    ExampleConversion(0x3E6E7D05BDABDE50L, "5.6789012345E-8",         "0.000000056789012345"),
    ExampleConversion(0x3E6E7D05BDAD407EL, "5.67890123456E-8",        "5.67890123456E-08"),
    ExampleConversion(0x3E6E7D06029F18BEL, "5.678902E-8",             "0.00000005678902"),
    ExampleConversion(0x2BCB5733CB32AE6EL, "9.999999999999123E-98",   "9.99999999999912E-98"),
    ExampleConversion(0x2B617F7D4ED8C59EL, "1.0000000000001235E-99",  "1.0000000000001E-99"),
    ExampleConversion(0x0036319916D67853L, "1.2345678901234578E-307", "1.2345678901235E-307"),
    ExampleConversion(0x359DEE7A4AD4B81FL, "2.0E-50",                 "2E-50"),
    // large numbers
    ExampleConversion(0x41678C29DCD6E9E0L, "1.2345678901234567E7",    "12345678.9012346"),
    ExampleConversion(0x42A674E79C5FE523L, "1.2345678901234568E13",   "12345678901234.6"),
    ExampleConversion(0x42DC12218377DE6BL, "1.2345678901234567E14",   "123456789012346"),
    ExampleConversion(0x43118B54F22AEB03L, "1.2345678901234568E15",   "1234567890123460"),
    ExampleConversion(0x43E56A95319D63E1L, "1.2345678901234567E19",   "12345678901234600000"),
    ExampleConversion(0x441AC53A7E04BCDAL, "1.2345678901234568E20",   "1.23456789012346E+20"),
    ExampleConversion(0xC3E56A95319D63E1L, "-1.2345678901234567E19",  "-12345678901234600000"),
    ExampleConversion(0xC41AC53A7E04BCDAL, "-1.2345678901234568E20",  "-1.23456789012346E+20"),
    ExampleConversion(0x54820FE0BA17F46DL, "1.2345678901234577E99",   "1.2345678901235E+99"),
    ExampleConversion(0x54B693D8E89DF188L, "1.2345678901234576E100",  "1.2345678901235E+100"),
    ExampleConversion(0x4A611B0EC57E649AL, "2.0E50",                  "2E+50"),
    // range extremities
    ExampleConversion(0x7FEFFFFFFFFFFFFFL, "1.7976931348623157E308",  "1.7976931348623E+308"),
    ExampleConversion(0x0010000000000000L, "2.2250738585072014E-308", "2.2250738585072E-308"),
    ExampleConversion(0x000FFFFFFFFFFFFFL, "2.225073858507201E-308",  "0"),
    ExampleConversion(0x0000000000000001L, "4.9E-324",                "0"), // infinity
    ExampleConversion(0x7FF0000000000000L, "Infinity",                "1.7976931348623E+308"),
    ExampleConversion(0xFFF0000000000000L, "-Infinity",               "1.7976931348623E+308"),
    // shortening due to rounding
    ExampleConversion(0x441AC7A08EAD02F2L, "1.234999999999999E20",    "1.235E+20"),
    ExampleConversion(0x40FE26BFFFFFFFF9L, "123499.9999999999",       "123500"),
    ExampleConversion(0x3E4A857BFB2F2809L, "1.234999999999999E-8",    "0.00000001235"),
    ExampleConversion(0x3BCD291DEF868C89L, "1.234999999999999E-20",   "1.235E-20"),
    // carry up due to rounding
    // For clarity these tests choose values that don't round in Java,
    // but will round in Excel. In some cases there is almost no difference
    // between Excel and Java (e.g. 9.9..9E-8)
    ExampleConversion(0x444B1AE4D6E2EF4FL, "9.999999999999999E20",    "1E+21"),
    ExampleConversion(0x412E847FFFFFFFFFL, "999999.9999999999",       "1000000"),
    ExampleConversion(0x3E45798EE2308C39L, "9.999999999999999E-9",    "0.00000001"),
    ExampleConversion(0x3C32725DD1D243ABL, "9.999999999999999E-19",   "0.000000000000000001"),
    ExampleConversion(0x3BFD83C94FB6D2ABL, "9.999999999999999E-20",   "1E-19"),
    ExampleConversion(0xC44B1AE4D6E2EF4FL, "-9.999999999999999E20",   "-1E+21"),
    ExampleConversion(0xC12E847FFFFFFFFFL, "-999999.9999999999",      "-1000000"),
    ExampleConversion(0xBE45798EE2308C39L, "-9.999999999999999E-9",   "-0.00000001"),
    ExampleConversion(0xBC32725DD1D243ABL, "-9.999999999999999E-19",  "-0.000000000000000001"),
    ExampleConversion(0xBBFD83C94FB6D2ABL, "-9.999999999999999E-20",  "-1E-19"),
    // NaNs
    // Currently these test cases are not critical, since other limitations prevent any variety in
    // or control of the bit patterns used to encode NaNs in evaluations.
    ExampleConversion(0xFFFF0420003C0000L, "NaN",                     "3.484840871308E+308"),
    ExampleConversion(0x7FF8000000000000L, "NaN",                     "2.6965397022935E+308"),
    ExampleConversion(0x7FFF0420003C0000L, "NaN",                     "3.484840871308E+308"),
    ExampleConversion(0xFFF8000000000000L, "NaN",                     "2.6965397022935E+308"),
    ExampleConversion(0xFFFF0AAAAAAAAAAAL, "NaN",                     "3.4877119413344E+308"),
    ExampleConversion(0x7FF80AAAAAAAAAAAL, "NaN",                     "2.7012211948322E+308"),
    ExampleConversion(0xFFFFFFFFFFFFFFFFL, "NaN",                     "3.5953862697246E+308"),
    ExampleConversion(0x7FFFFFFFFFFFFFFFL, "NaN",                     "3.5953862697246E+308"),
    ExampleConversion(0xFFF7FFFFFFFFFFFFL, "NaN",                     "2.6965397022935E+308")
  )
}

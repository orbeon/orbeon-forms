package org.orbeon.saxon.number;

/**
 * Numberer class for Traditional Chinese (BCP 47: {@code zh-Hant}).
 *
 * Note: Saxon selects the class name by keeping only letters from the language code,
 * so {@code zh-Hant} maps to {@code Numberer_zhHant}.
 */
public class Numberer_zhHant extends AbstractNumberer {

    private static final long serialVersionUID = 1L;

    @Override
    public String toWords(long number) {
        if (number == 0) return "\u96F6"; // 零
        if (number < 0) return "\u8CA0" + toWords(-number); // 負
        return toChinese(number, DigitsTraditional, LargeUnitsTraditional);
    }

    @Override
    public String toOrdinalWords(String ordinalParam, long number, int wordCase) {
        return "\u7B2C" + toWords(number); // 第
    }

    private static final String[] DigitsTraditional = {
            "\u96F6", "\u4E00", "\u4E8C", "\u4E09", "\u56DB",
            "\u4E94", "\u516D", "\u4E03", "\u516B", "\u4E5D"
    };

    private static final String[] SmallUnits = {"", "\u5341", "\u767E", "\u5343"}; // , 十, 百, 千
    private static final String[] LargeUnitsTraditional = {"", "\u842C", "\u5104", "\u5146"}; // , 萬, 億, 兆

    private static String toChinese(long number, String[] digits, String[] largeUnits) {
        StringBuilder out = new StringBuilder();
        int largeUnitIndex = 0;
        boolean pendingZero = false;

        while (number > 0) {
            int group = (int) (number % 10000);
            if (group == 0) {
                pendingZero = out.length() > 0;
            } else {
                if (pendingZero) {
                    out.insert(0, digits[0]);
                    pendingZero = false;
                }
                String groupText = toChineseGroup(group, digits);
                if (largeUnitIndex > 0) groupText += largeUnits[largeUnitIndex];
                out.insert(0, groupText);
            }
            number /= 10000;
            largeUnitIndex++;
        }
        return out.toString();
    }

    private static String toChineseGroup(int n, String[] digits) {
        StringBuilder sb = new StringBuilder();

        int[] parts = {n / 1000, (n / 100) % 10, (n / 10) % 10, n % 10};
        boolean started = false;
        boolean zeroPending = false;

        for (int i = 0; i < 4; i++) {
            int digit = parts[i];
            int unitIndex = 3 - i;
            if (digit == 0) {
                if (started) zeroPending = true;
            } else {
                if (zeroPending) {
                    sb.append(digits[0]);
                    zeroPending = false;
                }
                if (unitIndex == 1 && digit == 1 && !started) {
                    sb.append(SmallUnits[unitIndex]);
                } else {
                    sb.append(digits[digit]).append(SmallUnits[unitIndex]);
                }
                started = true;
            }
        }
        return sb.toString();
    }

    @Override
    public String monthName(int month, int minWidth, int maxWidth) {
        String name = toChinese(month, DigitsTraditional, LargeUnitsTraditional) + "\u6708"; // 月
        if (maxWidth < 2) maxWidth = 2;
        if (name.length() > maxWidth) name = name.substring(0, maxWidth);
        while (name.length() < minWidth) name = name + ' ';
        return name;
    }

    @Override
    public String dayName(int day, int minWidth, int maxWidth) {
        String name = traditionalDays[day - 1];
        if (maxWidth < 2) maxWidth = 2;
        if (name.length() > maxWidth) {
            name = traditionalDayAbbreviations[day - 1];
            if (name.length() > maxWidth) name = name.substring(0, maxWidth);
        }
        while (name.length() < minWidth) name = name + ' ';
        if (minWidth == 1 && maxWidth == 2) {
            name = name.substring(0, minUniqueDayLength[day - 1]);
        }
        return name;
    }

    private static final String[] traditionalDays = {
            "\u661F\u671F\u4E00", "\u661F\u671F\u4E8C", "\u661F\u671F\u4E09",
            "\u661F\u671F\u56DB", "\u661F\u671F\u4E94", "\u661F\u671F\u516D", "\u661F\u671F\u65E5"
    };

    private static final String[] traditionalDayAbbreviations = {
            "\u9031\u4E00", "\u9031\u4E8C", "\u9031\u4E09", "\u9031\u56DB", "\u9031\u4E94", "\u9031\u516D", "\u9031\u65E5"
    };

    private static final int[] minUniqueDayLength = {
            1, 1, 1, 1, 1, 1, 1
    };
}

//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file
//
// The Initial Developer of the Original Code is Michael H. Kay.
//
// Contributor(s): Orbeon, Inc.


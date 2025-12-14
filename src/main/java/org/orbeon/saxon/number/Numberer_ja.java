package org.orbeon.saxon.number;

/**
 * Numberer class for the Japanese language.
 */
public class Numberer_ja extends AbstractNumberer {

    private static final long serialVersionUID = 1L;

    @Override
    public String toWords(long number) {
        if (number == 0) return "\u96F6"; // 零
        if (number < 0) return "\u30DE\u30A4\u30CA\u30B9" + toWords(-number); // マイナス
        return toKanji(number);
    }

    @Override
    public String toOrdinalWords(String ordinalParam, long number, int wordCase) {
        // Japanese ordinal: 第 + cardinal.
        return "\u7B2C" + toWords(number); // 第
    }

    private static final String[] DIGITS = {
            "\u96F6", "\u4E00", "\u4E8C", "\u4E09", "\u56DB",
            "\u4E94", "\u516D", "\u4E03", "\u516B", "\u4E5D"
    };

    private static final String[] SMALL_UNITS = {"", "\u5341", "\u767E", "\u5343"}; // , 十, 百, 千
    private static final String[] LARGE_UNITS = {"", "\u4E07", "\u5104", "\u5146"}; // , 万, 億, 兆

    private static String toKanji(long number) {
        StringBuilder out = new StringBuilder();

        int largeUnitIndex = 0;
        boolean pendingZero = false;
        while (number > 0) {
            int group = (int) (number % 10000);
            if (group == 0) {
                pendingZero = out.length() > 0; // remember internal zeros
            } else {
                if (pendingZero) {
                    out.insert(0, DIGITS[0]);
                    pendingZero = false;
                }
                String groupText = toKanjiGroup(group);
                if (largeUnitIndex > 0) groupText += LARGE_UNITS[largeUnitIndex];
                out.insert(0, groupText);
            }
            number /= 10000;
            largeUnitIndex++;
        }
        return out.toString();
    }

    private static String toKanjiGroup(int n) {
        // 1..9999 in Kanji, omitting leading "一" for 十/百/千.
        StringBuilder sb = new StringBuilder();
        int thousands = n / 1000;
        int hundreds = (n / 100) % 10;
        int tens = (n / 10) % 10;
        int ones = n % 10;

        if (thousands != 0) {
            if (thousands != 1) sb.append(DIGITS[thousands]);
            sb.append(SMALL_UNITS[3]);
        }
        if (hundreds != 0) {
            if (hundreds != 1) sb.append(DIGITS[hundreds]);
            sb.append(SMALL_UNITS[2]);
        }
        if (tens != 0) {
            if (tens != 1) sb.append(DIGITS[tens]);
            sb.append(SMALL_UNITS[1]);
        }
        if (ones != 0) {
            sb.append(DIGITS[ones]);
        }

        return sb.toString();
    }

    @Override
    public String monthName(int month, int minWidth, int maxWidth) {
        // Common Japanese month representation: "1月", "2月", ...
        String name = Integer.toString(month) + "\u6708"; // 月
        if (maxWidth < 2) maxWidth = 2;
        if (name.length() > maxWidth) name = name.substring(0, maxWidth);
        while (name.length() < minWidth) name = name + ' ';
        return name;
    }

    @Override
    public String dayName(int day, int minWidth, int maxWidth) {
        String name = japaneseDays[day - 1];
        if (maxWidth < 2) maxWidth = 2;
        if (name.length() > maxWidth) {
            name = japaneseDayAbbreviations[day - 1];
            if (name.length() > maxWidth) name = name.substring(0, maxWidth);
        }
        while (name.length() < minWidth) name = name + ' ';
        if (minWidth == 1 && maxWidth == 2) {
            name = name.substring(0, minUniqueDayLength[day - 1]);
        }
        return name;
    }

    private static final String[] japaneseDays = {
            "\u6708\u66DC\u65E5", // 月曜日
            "\u706B\u66DC\u65E5", // 火曜日
            "\u6C34\u66DC\u65E5", // 水曜日
            "\u6728\u66DC\u65E5", // 木曜日
            "\u91D1\u66DC\u65E5", // 金曜日
            "\u571F\u66DC\u65E5", // 土曜日
            "\u65E5\u66DC\u65E5"  // 日曜日
    };

    private static final String[] japaneseDayAbbreviations = {
            "\u6708", "\u706B", "\u6C34", "\u6728", "\u91D1", "\u571F", "\u65E5"
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


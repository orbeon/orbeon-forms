package org.orbeon.saxon.number;

/**
 * Numberer class for the Arabic language.
 */
public class Numberer_ar extends AbstractNumberer {

    private static final long serialVersionUID = 1L;

    private static final char[] ARABIC_INDIC_DIGITS = {
            '\u0660', '\u0661', '\u0662', '\u0663', '\u0664',
            '\u0665', '\u0666', '\u0667', '\u0668', '\u0669'
    };

    @Override
    public String toWords(long number) {
        // Minimal, but locale-friendly: format using Arabic-Indic digits.
        if (number == 0) {
            return String.valueOf(ARABIC_INDIC_DIGITS[0]);
        }

        boolean negative = number < 0;
        String digits = Long.toString(Math.abs(number));

        StringBuilder sb = new StringBuilder(digits.length() + (negative ? 1 : 0));
        if (negative) sb.append('-');
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            sb.append(ARABIC_INDIC_DIGITS[c - '0']);
        }
        return sb.toString();
    }

    @Override
    public String toOrdinalWords(String ordinalParam, long number, int wordCase) {
        // Prefer a simple numeric ordinal representation over an incorrect Arabic word form.
        return toWords(number);
    }

    @Override
    public String monthName(int month, int minWidth, int maxWidth) {
        String name = arabicMonths[month - 1];
        if (maxWidth < 3) maxWidth = 3;
        if (name.length() > maxWidth) name = name.substring(0, maxWidth);
        while (name.length() < minWidth) name = name + ' ';
        return name;
    }

    private static final String[] arabicMonths = {
            "\u064A\u0646\u0627\u064A\u0631",   // يناير
            "\u0641\u0628\u0631\u0627\u064A\u0631", // فبراير
            "\u0645\u0627\u0631\u0633",     // مارس
            "\u0623\u0628\u0631\u064A\u0644",   // أبريل
            "\u0645\u0627\u064A\u0648",     // مايو
            "\u064A\u0648\u0646\u064A\u0648",   // يونيو
            "\u064A\u0648\u0644\u064A\u0648",   // يوليو
            "\u0623\u063A\u0633\u0637\u0633",   // أغسطس
            "\u0633\u0628\u062A\u0645\u0628\u0631", // سبتمبر
            "\u0623\u0643\u062A\u0648\u0628\u0631", // أكتوبر
            "\u0646\u0648\u0641\u0645\u0628\u0631", // نوفمبر
            "\u062F\u064A\u0633\u0645\u0628\u0631"  // ديسمبر
    };

    @Override
    public String dayName(int day, int minWidth, int maxWidth) {
        String name = arabicDays[day - 1];
        if (maxWidth < 2) maxWidth = 2;
        if (name.length() > maxWidth) {
            name = arabicDayAbbreviations[day - 1];
            if (name.length() > maxWidth) name = name.substring(0, maxWidth);
        }
        while (name.length() < minWidth) name = name + ' ';
        if (minWidth == 1 && maxWidth == 2) {
            name = name.substring(0, minUniqueDayLength[day - 1]);
        }
        return name;
    }

    private static final String[] arabicDays = {
            "\u0627\u0644\u0627\u062B\u0646\u064A\u0646",   // الاثنين
            "\u0627\u0644\u062B\u0644\u0627\u062B\u0627\u0621", // الثلاثاء
            "\u0627\u0644\u0623\u0631\u0628\u0639\u0627\u0621", // الأربعاء
            "\u0627\u0644\u062E\u0645\u064A\u0633",     // الخميس
            "\u0627\u0644\u062C\u0645\u0639\u0629",     // الجمعة
            "\u0627\u0644\u0633\u0628\u062A",      // السبت
            "\u0627\u0644\u0623\u062D\u062F"       // الأحد
    };

    private static final String[] arabicDayAbbreviations = {
            "\u0627\u062B\u0646", // اثن
            "\u062B\u0644\u0627", // ثلا
            "\u0623\u0631\u0628", // أرب
            "\u062E\u0645\u064A", // خمي
            "\u062C\u0645\u0639", // جمع
            "\u0633\u0628\u062A", // سبت
            "\u0623\u062D\u062F"  // أحد
    };

    private static final int[] minUniqueDayLength = {
            2, 2, 2, 2, 2, 2, 2
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


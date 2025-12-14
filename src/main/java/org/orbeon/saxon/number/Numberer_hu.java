package org.orbeon.saxon.number;

/**
 * Numberer class for the Hungarian language.
 */
public class Numberer_hu extends AbstractNumberer {

    private static final long serialVersionUID = 1L;

    @Override
    public String toWords(long number) {
        // Keep this simple and safe: Hungarian words are non-trivial (linking vowels, etc.).
        // For now, return a decimal representation.
        return Long.toString(number);
    }

    @Override
    public String toOrdinalWords(String ordinalParam, long number, int wordCase) {
        // Prefer a numeric ordinal representation over an incorrect Hungarian word form.
        return Long.toString(number) + '.';
    }

    @Override
    public String monthName(int month, int minWidth, int maxWidth) {
        String name = hungarianMonths[month - 1];
        if (maxWidth < 3) maxWidth = 3;
        if (name.length() > maxWidth) name = name.substring(0, maxWidth);
        while (name.length() < minWidth) name = name + ' ';
        return name;
    }

    private static final String[] hungarianMonths = {
            "janu\u00E1r", "febru\u00E1r", "m\u00E1rcius", "\u00E1prilis", "m\u00E1jus", "j\u00FAnius",
            "j\u00FAlius", "augusztus", "szeptember", "okt\u00F3ber", "november", "december"
    };

    @Override
    public String dayName(int day, int minWidth, int maxWidth) {
        String name = hungarianDays[day - 1];
        if (maxWidth < 2) maxWidth = 2;
        if (name.length() > maxWidth) {
            name = hungarianDayAbbreviations[day - 1];
            if (name.length() > maxWidth) name = name.substring(0, maxWidth);
        }
        while (name.length() < minWidth) name = name + ' ';
        if (minWidth == 1 && maxWidth == 2) {
            name = name.substring(0, minUniqueDayLength[day - 1]);
        }
        return name;
    }

    private static final String[] hungarianDays = {
            "h\u00E9tf\u0151", "kedd", "szerda", "cs\u00FCt\u00F6rt\u00F6k", "p\u00E9ntek", "szombat", "vas\u00E1rnap"
    };

    private static final String[] hungarianDayAbbreviations = {
            "h\u00E9tf", "ked", "sze", "cs\u00FCt", "p\u00E9n", "szo", "vas"
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


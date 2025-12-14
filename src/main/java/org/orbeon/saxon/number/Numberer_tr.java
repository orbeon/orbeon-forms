package org.orbeon.saxon.number;

/**
 * Numberer class for the Turkish language.
 */
public class Numberer_tr extends AbstractNumberer {

    private static final long serialVersionUID = 1L;

    @Override
    public String toWords(long number) {
        if (number == 0) return "s\u0131f\u0131r";
        if (number < 0) return "eksi " + toWords(-number);

        StringBuilder out = new StringBuilder();

        long billions = number / 1000000000L;
        long remAfterBillions = number % 1000000000L;
        if (billions > 0) {
            out.append(toWordsBelowThousand(billions)).append(" milyar");
            if (remAfterBillions > 0) out.append(' ');
        }

        long millions = remAfterBillions / 1000000L;
        long remAfterMillions = remAfterBillions % 1000000L;
        if (millions > 0) {
            out.append(toWordsBelowThousand(millions)).append(" milyon");
            if (remAfterMillions > 0) out.append(' ');
        }

        long thousands = remAfterMillions / 1000L;
        int belowThousand = (int) (remAfterMillions % 1000L);
        if (thousands > 0) {
            if (thousands == 1) out.append("bin");
            else out.append(toWordsBelowThousand(thousands)).append(" bin");
            if (belowThousand > 0) out.append(' ');
        }

        if (belowThousand > 0) out.append(toWordsBelowThousand(belowThousand));

        return out.toString();
    }

    private static String toWordsBelowThousand(long number) {
        final String[] units = {"", "bir", "iki", "\u00FC\u00E7", "d\u00F6rt", "be\u015F", "alt\u0131", "yedi", "sekiz", "dokuz"};
        final String[] tens = {"", "on", "yirmi", "otuz", "k\u0131rk", "elli", "altm\u0131\u015F", "yetmi\u015F", "seksen", "doksan"};

        StringBuilder sb = new StringBuilder();
        int n = (int) number;
        int hundreds = n / 100;
        int rem = n % 100;
        if (hundreds > 0) {
            if (hundreds != 1) sb.append(units[hundreds]).append(' ');
            sb.append("y\u00FCz");
            if (rem > 0) sb.append(' ');
        }
        if (rem >= 10) {
            sb.append(tens[rem / 10]);
            if (rem % 10 != 0) sb.append(' ').append(units[rem % 10]);
        } else if (rem > 0) {
            sb.append(units[rem]);
        }

        return sb.toString();
    }

    @Override
    public String toOrdinalWords(String ordinalParam, long number, int wordCase) {
        // Keep it simple: return a numeric ordinal form.
        return Long.toString(number) + '.';
    }

    @Override
    public String monthName(int month, int minWidth, int maxWidth) {
        String name = turkishMonths[month - 1];
        if (maxWidth < 3) maxWidth = 3;
        if (name.length() > maxWidth) name = name.substring(0, maxWidth);
        while (name.length() < minWidth) name = name + ' ';
        return name;
    }

    private static final String[] turkishMonths = {
            "Ocak", "\u015Eubat", "Mart", "Nisan", "May\u0131s", "Haziran",
            "Temmuz", "A\u011Fustos", "Eyl\u00FCl", "Ekim", "Kas\u0131m", "Aral\u0131k"
    };

    @Override
    public String dayName(int day, int minWidth, int maxWidth) {
        String name = turkishDays[day - 1];
        if (maxWidth < 2) maxWidth = 2;
        if (name.length() > maxWidth) {
            name = turkishDayAbbreviations[day - 1];
            if (name.length() > maxWidth) name = name.substring(0, maxWidth);
        }
        while (name.length() < minWidth) name = name + ' ';
        if (minWidth == 1 && maxWidth == 2) {
            name = name.substring(0, minUniqueDayLength[day - 1]);
        }
        return name;
    }

    private static final String[] turkishDays = {
            "Pazartesi", "Sal\u0131", "\u00C7ar\u015Famba", "Per\u015Fembe", "Cuma", "Cumartesi", "Pazar"
    };

    private static final String[] turkishDayAbbreviations = {
            "Pzt", "Sal", "\u00C7ar", "Per", "Cum", "Cmt", "Paz"
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


package org.orbeon.saxon.number;

/**
 * Numberer class for the Spanish language
 */
public class Numberer_es extends AbstractNumberer {

    /**
     * Construct the ordinal suffix for a number, for example "st", "nd", "rd"
     *
     * @param ordinalParam the value of the ordinal attribute (used in non-English
     *                     language implementations)
     * @param number       the number being formatted
     * @return the ordinal suffix to be appended to the formatted number
     */
    protected String ordinalSuffix(String ordinalParam, long number) {
        int penult = ((int) (number % 100)) / 10;
        int ult = (int) (number % 10);
        if (penult == 1) {
            // e.g. 11th, 12th, 13th
            return "th";
        } else {
            if (ult == 1) {
                return "st";
            } else if (ult == 2) {
                return "nd";
            } else if (ult == 3) {
                return "rd";
            } else {
                return "th";
            }
        }
    }

    /**
     * Show the number as words in title case. (We choose title case because
     * the result can then be converted algorithmically to lower case or upper case).
     *
     * @param number the number to be formatted
     * @return the number formatted as English words
     */
    public String toWords(long number) {
        if (number >= 1000000000) {
            long rem = number % 1000000000;
            return toWords(number / 1000000000) + " Billón/es" +
                    (rem == 0 ? "" : (rem < 100 ? " " : " ") + toWords(rem));
        } else if (number >= 1000000) {
            long rem = number % 1000000;
            return toWords(number / 1000000) + " Millón/es" +
                    (rem == 0 ? "" : (rem < 100 ? " " : " ") + toWords(rem));
        } else if (number >= 1000) {
            long rem = number % 1000;
            return toWords(number / 1000) + " Mil/es" +
                    (rem == 0 ? "" : (rem < 100 ? " " : " ") + toWords(rem));
        } else if (number >= 100) {
            long rem = number % 100;
            return toWords(number / 100) + " Ciento/s" +
                    (rem == 0 ? "" : " " + toWords(rem));
        } else {
            if (number < 20) return spanishUnits[(int) number];
            int rem = (int) (number % 10);
            return spanishTens[(int) number / 10] +
                    (rem == 0 ? "" : ' ' + spanishUnits[rem]);
        }
    }

    /**
     * Show an ordinal number as English words in a requested case (for example, Twentyfirst)
     *
     * @param ordinalParam the value of the "ordinal" attribute as supplied by the user
     * @param number       the number to be formatted
     * @param wordCase     the required case for example {@link #UPPER_CASE},
     *                     {@link #LOWER_CASE}, {@link #TITLE_CASE}
     * @return the formatted number
     */
    public String toOrdinalWords(String ordinalParam, long number, int wordCase) {
        String s;
        if (number >= 1000000000) {
            long rem = number % 1000000000;
            s = toWords(number / 1000000000) + " Billón/es" +
                    (rem == 0 ? "th" : (rem < 100 ? " " : " ") +
                            toOrdinalWords(ordinalParam, rem, wordCase));
        } else if (number >= 1000000) {
            long rem = number % 1000000;
            s = toWords(number / 1000000) + " Millón/es" +
                    (rem == 0 ? "th" : (rem < 100 ? " and " : " ") +
                            toOrdinalWords(ordinalParam, rem, wordCase));
        } else if (number >= 1000) {
            long rem = number % 1000;
            s = toWords(number / 1000) + " Mil/es" +
                    (rem == 0 ? "th" : (rem < 100 ? " " : " ") +
                            toOrdinalWords(ordinalParam, rem, wordCase));
        } else if (number >= 100) {
            long rem = number % 100;
            s = toWords(number / 100) + " Ciento/s" +
                    (rem == 0 ? "th" : " " +
                            toOrdinalWords(ordinalParam, rem, wordCase));
        } else {
            if (number < 20) {
                s = spanishOrdinalUnits[(int) number];
            } else {
                int rem = (int) (number % 10);
                if (rem == 0) {
                    s = spanishOrdinalTens[(int) number / 10];
                } else {
                    s = spanishTens[(int) number / 10] + '-' + spanishOrdinalUnits[rem];
                }
            }
        }
        if (wordCase == UPPER_CASE) {
            return s.toUpperCase();
        } else if (wordCase == LOWER_CASE) {
            return s.toLowerCase();
        } else {
            return s;
        }
    }

    private static String[] spanishUnits = {
            "", "", "Dos", "Tres", "Cuatro", "Cinco", "Seis", "Siete", "Ocho", "Nueve",
            "Diez", "Once", "Doce", "Trece", "Catorce", "Quince", "Dieciseis",
            "Diecisiete", "Dieciocho", "Diecinueve"};

    private static String[] spanishTens = {
            "", "Diez", "Veinte", "Treinta", "Cuarenta", "Cincuenta",
            "Sesenta", "Setenta", "Ochenta", "Noventa"};

    private static String[] spanishOrdinalUnits = {
            "", "Primero", "Segundo", "Tercero", "Cuarto", "Quinto", "Sexto", "Séptimo", "Octavo", "Noveno",
            "Décimo", "Undécimo", "Duodécimo", "Decimotercero", "Decimocuarto", "Decimoquinto", "Decimosexto",
            "Decimoséptimo", "Decimoctavo", "Decimonoveno"};

    private static String[] spanishOrdinalTens = {
            "", "Décimo", "Vigésimo", "Trigésimo", "Cuadragésimo", "Quincuagésimo",
            "Sexagésimo", "Septuagésimo", "Cctogésimo", "Nonagésimo"};


    /**
     * Get a month name or abbreviation
     *
     * @param month    The month number (1=January, 12=December)
     * @param minWidth The minimum number of characters
     * @param maxWidth The maximum number of characters
     */
    public String monthName(int month, int minWidth, int maxWidth) {
        String name = spanishMonths[month - 1];
        if (maxWidth < 3) {
            maxWidth = 3;
        }
        if (name.length() > maxWidth) {
            name = name.substring(0, maxWidth);
        }
        while (name.length() < minWidth) {
            name = name + ' ';
        }
        return name;
    }

    private static String[] spanishMonths = {
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };

    /**
     * Get a day name or abbreviation
     *
     * @param day      The day of the week (1=Monday, 7=Sunday)
     * @param minWidth The minimum number of characters
     * @param maxWidth The maximum number of characters
     */
    public String dayName(int day, int minWidth, int maxWidth) {
        String name = spanishDays[day - 1];
        if (maxWidth < 2) {
            maxWidth = 2;
        }
        if (name.length() > maxWidth) {
            name = spanishDayAbbreviations[day - 1];
            if (name.length() > maxWidth) {
                name = name.substring(0, maxWidth);
            }
        }
        while (name.length() < minWidth) {
            name = name + ' ';
        }
        if (minWidth == 1 && maxWidth == 2) {
            // special case
            name = name.substring(0, minUniqueDayLength[day - 1]);
        }
        return name;
    }

    private static String[] spanishDays = {
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
    };

    private static String[] spanishDayAbbreviations = {
            "Lun", "Mar", "Mie", "Jue", "Vie", "Sab", "Dom"
    };

    private static int[] minUniqueDayLength = {
            1, 2, 1, 2, 1, 2, 2
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
// Contributor(s):
//
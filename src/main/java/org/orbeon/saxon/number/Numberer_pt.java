package org.orbeon.saxon.number;

/**
 * Numberer class for the Portuguese language
 */
public class Numberer_pt extends AbstractNumberer {

    /**
     * Construct the ordinal suffix for a number, for example "st", "nd", "rd"
     *
     * @param ordinalParam the value of the ordinal attribute (used in non-English
     *                     language implementations)
     * @param number       the number being formatted
     * @return the ordinal suffix to be appended to the formatted number
     */
    protected String ordinalSuffix(String ordinalParam, long number) {
        return "º";
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
            return toWords(number / 1000000000) + " Mil Milhão(ões)" +
                    (rem == 0 ? "" : (rem < 100 ? " " : " ") + toWords(rem));
        } else if (number >= 1000000) {
            long rem = number % 1000000;
            return toWords(number / 1000000) + " Milhão(ões)" +
                    (rem == 0 ? "" : (rem < 100 ? " " : " ") + toWords(rem));
        } else if (number >= 1000) {
            long rem = number % 1000;
            return toWords(number / 1000) + " Mil" +
                    (rem == 0 ? "" : (rem < 100 ? " " : " ") + toWords(rem));
        } else if (number >= 100) {
            long rem = number % 100;
            return toWords(number / 100) + " Cento(s)" +
                    (rem == 0 ? "" : " " + toWords(rem));
        } else {
            if (number < 20) return portugueseUnits[(int) number];
            int rem = (int) (number % 10);
            return portugueseTens[(int) number / 10] +
                    (rem == 0 ? "" : ' ' + portugueseUnits[rem]);
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
            s = toWords(number / 1000000000) + " Mil Milhão(ões)" +
                    (rem == 0 ? "" : (rem < 100 ? " " : " ") +
                            toOrdinalWords(ordinalParam, rem, wordCase));
        } else if (number >= 1000000) {
            long rem = number % 1000000;
            s = toWords(number / 1000000) + " Milhão(ões)" +
                    (rem == 0 ? "" : (rem < 100 ? " and " : " ") +
                            toOrdinalWords(ordinalParam, rem, wordCase));
        } else if (number >= 1000) {
            long rem = number % 1000;
            s = toWords(number / 1000) + " Mil" +
                    (rem == 0 ? "" : (rem < 100 ? " " : " ") +
                            toOrdinalWords(ordinalParam, rem, wordCase));
        } else if (number >= 100) {
            long rem = number % 100;
            s = toWords(number / 100) + " Cento(s)" +
                    (rem == 0 ? "" : " " +
                            toOrdinalWords(ordinalParam, rem, wordCase));
        } else {
            if (number < 10) {
                s = portugueseOrdinalUnits[(int) number];
            } else {
                int rem = (int) (number % 10);
                if (rem == 0) {
                    s = portugueseOrdinalTens[(int) number / 10];
                } else {
                    s = portugueseTens[(int) number / 10] + '-' + portugueseOrdinalUnits[rem];
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

    private static String[] portugueseUnits = {
            "", "Um", "Dois", "Três", "Quatro", "Cinco", "Seis", "Sete", "Oito", "Nove",
            "Dez", "Onze", "Doze", "Treze", "Catorze", "Quinze", "Dezasseis",
            "Dezassete", "Dezoito", "Dezanove"};

    private static String[] portugueseTens = {
            "", "Dez", "Vinte", "Trinta", "Quarenta", "Cinquenta",
            "Sessenta", "Setenta", "Oitenta", "Noventa"};

    private static String[] portugueseOrdinalUnits = {
            "", "Primeiro", "Segundo", "Terceiro", "Quarto", "Quinto", "Sexto", "Sétimo", "Oitavo", "Nono",
            "Décimo"};

    private static String[] portugueseOrdinalTens = {
            "", "Décimo", "Vigésimo", "Trigésimo", "Quadragésimo", "Quinquagésimo",
            "Sexagésimo", "Septuagésimo", "Octogésimo", "Nonagésimo"};


    /**
     * Get a month name or abbreviation
     *
     * @param month    The month number (1=January, 12=December)
     * @param minWidth The minimum number of characters
     * @param maxWidth The maximum number of characters
     */
    public String monthName(int month, int minWidth, int maxWidth) {
        String name = portugueseMonths[month - 1];
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

    private static String[] portugueseMonths = {
            "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    };

    /**
     * Get a day name or abbreviation
     *
     * @param day      The day of the week (1=Monday, 7=Sunday)
     * @param minWidth The minimum number of characters
     * @param maxWidth The maximum number of characters
     */
    public String dayName(int day, int minWidth, int maxWidth) {
        String name = portugueseDays[day - 1];
        if (maxWidth < 2) {
            maxWidth = 2;
        }
        if (name.length() > maxWidth) {
            name = portugueseDayAbbreviations[day - 1];
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

    private static String[] portugueseDays = {
            "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo"
    };

    private static String[] portugueseDayAbbreviations = {
            "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"
    };

    private static int[] minUniqueDayLength = {
            3, 1, 3, 3, 3, 2, 1
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
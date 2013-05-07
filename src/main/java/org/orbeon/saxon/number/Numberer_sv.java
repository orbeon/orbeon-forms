package org.orbeon.saxon.number;

/**
 * Numberer class for the Swedish language.
 *
 * @see <a href="http://en.wikipedia.org/wiki/Swedish_grammar">http://en.wikipedia.org/wiki/Swedish_grammar</a>
 * @see <a href="http://www2.hhs.se/isa/swedish/chap4.htm">http://www2.hhs.se/isa/swedish/chap4.htm</a>
 */
public class Numberer_sv extends AbstractNumberer {

    private static final long serialVersionUID = 1L;

    private static String[] swedishOrdinalUnits = {
            "", "f\u00f6rsta", "andra", "tredje", "fj\u00e4rde", "femte",
            "sj\u00e4tte", "sjunde", "\u00e5ttonde", "nionde",
            "tionde", "elfte", "tolfte", "trettonde", "fjortonde", "femtonde",
            "sextonde", "sjuttonde", "artonde", "nittonde"};

    private static String[] swedishOrdinalTens = {
            "", "tionde", "tjugonde", "trettionde", "fyrtionde", "femtionde",
            "sextionde", "sjuttionde", "\u00e5ttionde", "nittionde"};

    private static String[] swedishUnits = {
            "", "ett", "tv\u00e5", "tre", "fyra", "fem", "sex", "sju", "\u00e5tta", "nio", "tio",
            "elva", "tolv", "tretton", "fjorton", "femton", "sexton", "sjutton", "arton", "nitton"};

    private static String[] swedishTens = {
            "", "tio", "tjugo", "trettio", "fyrtio", "femtio",
            "sextio", "sjuttio", "\u00e5ttio", "nittio"};

    private static long ONE_HUNDRED = 100L;
    private static long ONE_THOUSAND = 1000L;
    private static long ONE_MILLION = 1000000L;
    private static long ONE_BILLION = 1000000000L; // Swedish definition 10^9

    /**
     * Show an ordinal number as swedish words in a requested case (for example, Twentyfirst)
     *
     * @param ordinalParam not used.
     * @param number       the number to be converted to a word.
     * @param wordCase     UPPER_CASE or LOWER_CASE.
     * @return String representing the number in words.
     */
    public String toOrdinalWords(String ordinalParam, long number, int wordCase) {
        String s = (number == 0) ? "nollte" : toOrdinalWordsPriv(number);

        if (wordCase == UPPER_CASE) {
            return s.toUpperCase();
        } else if (wordCase == LOWER_CASE) {
            return s.toLowerCase();
        } else {
            return s;
        }
    }

    private String toOrdinalWordsPriv(long number) {
        String s = "";

        if (number >= ONE_BILLION) {
            s = toBillionOrdinalWordsPriv(number) + toOrdinalWordsPriv(number % ONE_BILLION);
        } else if (number >= ONE_MILLION) {
            s = toMillionOrdinalWordsPriv(number) + toOrdinalWordsPriv(number % ONE_MILLION);
        } else if (number >= ONE_THOUSAND) {
            s = toThousandOrdinalWordsPriv(number) + toOrdinalWordsPriv(number % ONE_THOUSAND);
        } else if (number >= ONE_HUNDRED) {
            s = toHundredOrdinalWordsPriv(number) + toOrdinalWordsPriv(number % ONE_HUNDRED);
        } else {
            if (number < 20) {
                s = swedishOrdinalUnits[(int) number];
            } else {
                int rem = (int) (number % 10);
                if (rem == 0) {
                    s = swedishOrdinalTens[(int) number / 10];
                } else {
                    s = swedishTens[(int) number / 10] + swedishOrdinalUnits[rem];
                }
            }
        }

        return s;
    }

    private String toBillionOrdinalWordsPriv(long number) {
        String result = "";
        long units = number / ONE_BILLION;
        long rem = number % ONE_BILLION;
        if (units > 0) {
            if (rem == 0) {
                result = (units == 1) ? "miljardte" : (toWordsPriv(units) + " miljardte");
            } else {
                if (units == 1) {
                    result = "en miljard ";
                } else {
                    result = toWordsPriv(units) + " miljarder ";
                    ;
                }
            }
        }
        return result;
    }

    private String toMillionOrdinalWordsPriv(long number) {
        String result = "";
        long units = number / ONE_MILLION;
        long rem = number % ONE_MILLION;
        if (units > 0) {
            if (rem == 0) {
                result = (units == 1) ? "miljonte" : (toWordsPriv(units) + " miljonte");
            } else {
                if (units == 1) {
                    result = "en miljon ";
                } else {
                    result = toWordsPriv(units) + " miljoner ";
                }
            }
        }
        return result;
    }

    private String toThousandOrdinalWordsPriv(long number) {
        String result = "";
        long units = number / ONE_THOUSAND;
        long rem = number % ONE_THOUSAND;
        if (units > 0) {
            if (rem == 0) {
                result = (units == 1) ? "tusende" : (toWordsPriv(units) + "tusende");
            } else {
                if (units == 1) {
                    result = "ettusen ";
                } else {
                    result = toWordsPriv(units) + "tusen ";
                }
            }
        }
        return result;
    }

    private String toHundredOrdinalWordsPriv(long number) {
        String result = "";
        long units = number / ONE_HUNDRED;
        long rem = number % ONE_HUNDRED;
        if (units > 0) {
            if (rem == 0) {
                result = toWordsPriv(units) + "hundrade";
            } else {
                if (units == 1) {
                    result = "etthundra";
                } else {
                    result = toWordsPriv(units) + "hundra";
                }
            }
        }
        return result;
    }

    public String toWords(long number) {
        return (number == 0) ? "noll" : toWordsPriv(number);
    }

    private String toWordsPriv(long number) {
        String result = "";
        if (number >= ONE_BILLION) {
            result = toBillionWordsPriv(number) + toWordsPriv(number % ONE_BILLION);
        } else if (number >= ONE_MILLION) {
            result = toMillionWordsPriv(number) + toWordsPriv(number % ONE_MILLION);
        } else if (number >= ONE_THOUSAND) {
            result = toThousandWordsPriv(number) + toWordsPriv(number % ONE_THOUSAND);
        } else if (number >= ONE_HUNDRED) {
            result = toHundredWordsPriv(number) + toWordsPriv(number % ONE_HUNDRED);
        } else {
            if (number < 20) return swedishUnits[(int) number];
            int rem = (int) (number % 10);
            return swedishTens[(int) number / 10] + swedishUnits[rem];
        }

        return result;
    }

    private String toBillionWordsPriv(long number) {
        String result = "";
        long units = number / ONE_BILLION;
        if (units > 0) {
            result = (units == 1) ? "en miljard" : toWordsPriv(units) + " miljarder";
            if (number % ONE_BILLION > 0) result += ' ';
        }
        return result;
    }

    private String toMillionWordsPriv(long number) {
        String result = "";
        long units = number / ONE_MILLION;
        if (units > 0) {
            result = (units == 1) ? "en miljon" : toWordsPriv(units) + " miljoner";
            if (number % ONE_MILLION > 0) result += ' ';
        }
        return result;
    }

    private String toThousandWordsPriv(long number) {
        String result = "";
        long units = number / ONE_THOUSAND;
        if (units > 0) {
            result = (units == 1) ? "ettusen" : toWordsPriv(units) + "tusen";
            if (number % ONE_THOUSAND > 0) result += ' ';
        }
        return result;
    }

    private String toHundredWordsPriv(long number) {
        String result = "";
        long units = number / ONE_HUNDRED;
        if (units > 0) {
            result = (units == 1) ? "etthundra" : toWordsPriv(units) + "hundra";
        }
        return result;
    }

    public String toWords(long number, int wordCase) {
        String s;
        if (number == 0) {
            s = "noll";
        } else {
            s = toWords(number);
        }
        if (wordCase == UPPER_CASE) {
            return s.toUpperCase();
        } else if (wordCase == LOWER_CASE) {
            return s.toLowerCase();
        } else {
            return s;
        }
    }


    private static String[] swedishMonths = {
            "januari", "februari", "mars", "april", "maj", "juni",
            "juli", "augusti", "september", "oktober", "november", "december"
    };

    /**
     * Get a month name or abbreviation
     *
     * @param month    The month number (1=January, 12=December)
     * @param minWidth The minimum number of characters
     * @param maxWidth The maximum number of characters
     */
    //@Override
    public String monthName(int month, int minWidth, int maxWidth) {
        String name = swedishMonths[month - 1];
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

    /**
     * Get a day name or abbreviation
     *
     * @param day      The day of the week (1=Monday, 7=Sunday)
     * @param minWidth The minimum number of characters
     * @param maxWidth The maximum number of characters
     */
    public String dayName(int day, int minWidth, int maxWidth) {
        String name = swedishDays[day - 1];
        if (maxWidth < 2) {
            maxWidth = 2;
        }
        if (name.length() > maxWidth) {
            name = swedishDayAbbreviations[day - 1];
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

    private static String[] swedishDays = {
            "m\u00e5ndag", "tisdag", "onsdag", "torsdag", "fredag", "l\u00f6rdag", "s\u00f6ndag"
    };

    private static String[] swedishDayAbbreviations = {
            "m\u00e5nd", "tisd", "onsd", "tors", "fred", "l\u00f6rd", "s\u00f6nd"
    };

    private static int[] minUniqueDayLength = {
            1, 2, 1, 2, 1, 1, 1
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
// Contributor(s): Karel Goossens, Hakan Soderstrom, Erik Bruchez
// See also: http://discuss.orbeon.com/Swedish-localization-contribution-offered-td4656518.html
//
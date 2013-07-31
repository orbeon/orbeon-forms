package org.orbeon.saxon.number;

public class Numberer_fi extends AbstractNumberer {

    private static final long serialVersionUID = 1L;

    private static String[] finnishOrdinalUnits = {
            "", "ensimm\u00e4inen", "toinen", "kolmas", "nelj\u00e4s", "viides",
            "kuudes", "seitsem\u00e4s", "kahdeksas", "yhdeks\u00e4s",
            "kymmenes", "yhdestoista", "kahdestoista", "kolmastoista", "nelj\u00e4stoista", "viidestoista",
            "kuudestoista", "seitsem\u00e4stoista", "kahdeksastoista", "yhdeks\u00e4stoista"};

    private static String[] finnishOrdinalTens = {
            "", "kymmenes", "kahdeskymmenes", "kolmaskymmenes", "nelj\u00e4skymmenes", "viideskymmenes",
            "kuudeskymmenes", "seitsem\u00e4skymmenes", "kahdeksaskymmenes", "yhdeks\u00e4skymmenes"};

    private static String[] finnishUnits = {
            "", "yksi", "kaksi", "kolme", "nelj\u00e4", "viisi", "kuusi", "seitsem\u00e4n", "kahdeksan", "yhdeks\u00e4n", "kymmenen",
            "yksitoista", "kaksitoista", "kolmetoista", "nelj\u00e4toista", "viisitoista", "kuusitoista", "seitsem\u00e4ntoista",
            "kahdeksantoista", "yhdeks\u00e4ntoista"};

    private static String[] finnishTens = {
            "", "kymmenen", "kaksikymment\u00e4", "kolmekymment\u00e4", "nelj\u00e4kymment\u00e4", "viisikymment\u00e4",
            "kuusikymment\u00e4", "seitsem\u00e4nkymment\u00e4", "kahdeksankymment\u00e4", "yhdeks\u00e4nkymment\u00e4"};

    private static long ONE_HUNDRED = 100L;
    private static long ONE_THOUSAND = 1000L;
    private static long ONE_MILLION = 1000000L;
    private static long ONE_BILLION = 1000000000L;

    /**
     *
     * Show an ordinal number as finnish words in a requested case (for example, Twentyfirst)
     *
     * @param ordinalParam not used.
     * @param number the number to be converted to a word.
     * @param wordCase UPPER_CASE or LOWER_CASE.
     * @return String representing the number in words.
     */
    public String toOrdinalWords(String ordinalParam, long number, int wordCase) {
        String s = (number == 0) ? "nolla" : toOrdinalWordsPriv(number);

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
                s = finnishOrdinalUnits[(int) number];
            } else {
                int rem = (int) (number % 10);
                if (rem == 0) {
                    s = finnishOrdinalTens[(int) number / 10];
                } else {
                    s = finnishTens[(int) number / 10] + finnishOrdinalUnits[rem];
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
                result = (units == 1) ? "miljardisosa" : (toWordsPriv(units) + " miljardisosa");
            } else {
                if (units == 1) {
                    result = "miljardi ";
                } else {
                    result = toWordsPriv(units) + " miljardi ";
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
                result = (units == 1) ? "miljoonas" : (toWordsPriv(units) + " miljoonas");
            } else {
                if (units == 1) {
                    result = "miljoona ";
                } else {
                    result = toWordsPriv(units) + " miljoona ";
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
                result = (units == 1) ? "tuhannesosa" : (toWordsPriv(units) + "tuhannesosa");
            } else {
                if (units == 1) {
                    result = "tuhat ";
                } else {
                    result = toWordsPriv(units) + "tuhat ";
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
                result = toWordsPriv(units) + "sadasosa";
            } else {
                if (units == 1) {
                    result = "sata";
                } else {
                    result = toWordsPriv(units) + "sata";
                }
            }
        }
        return result;
    }

    public String toWords(long number) {
        return (number == 0) ? "nolla" : toWordsPriv(number);
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
            if (number < 20) return finnishUnits[(int) number];
            int rem = (int) (number % 10);
            return finnishTens[(int) number / 10] + finnishUnits[rem];
        }

        return result;
    }

    private String toBillionWordsPriv(long number) {
        String result = "";
        long units = number / ONE_BILLION;
        if (units > 0) {
            result = (units == 1) ? "miljardi" : toWordsPriv(units) + " miljardi";
            if (number % ONE_BILLION > 0) result += ' ';
        }
        return result;
    }

    private String toMillionWordsPriv(long number) {
        String result = "";
        long units = number / ONE_MILLION;
        if (units > 0) {
            result = (units == 1) ? "miljoona" : toWordsPriv(units) + " miljoona";
            if (number % ONE_MILLION > 0) result += ' ';
        }
        return result;
    }

    private String toThousandWordsPriv(long number) {
        String result = "";
        long units = number / ONE_THOUSAND;
        if (units > 0) {
            result = (units == 1) ? "tuhat" : toWordsPriv(units) + "tuhat";
            if (number % ONE_THOUSAND > 0) result += ' ';
        }
        return result;
    }

    private String toHundredWordsPriv(long number) {
        String result = "";
        long units = number / ONE_HUNDRED;
        if (units > 0) {
            result = (units == 1) ? "sata" : toWordsPriv(units) + "sata";
        }
        return result;
    }

    public String toWords(long number, int wordCase) {
        String s;
        if (number == 0) {
            s = "nolla";
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


    private static String[] finnishMonths = {
            "tammikuu", "helmikuu", "maaliskuu", "huhtikuu", "toukokuu", "kes\u00e4kuu",
            "hein\u00e4kuu", "elokuu", "syyskuu", "lokakuu", "marraskuu", "joulukuu"
    };

    /**
     * Get a month name or abbreviation
     *
     * @param month The month number (1=January, 12=December)
     * @param minWidth The minimum number of characters
     * @param maxWidth The maximum number of characters
     */
    public String monthName(int month, int minWidth, int maxWidth) {
        String name = finnishMonths[month - 1];
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
     * @param day The day of the week (1=Monday, 7=Sunday)
     * @param minWidth The minimum number of characters
     * @param maxWidth The maximum number of characters
     */
    public String dayName(int day, int minWidth, int maxWidth) {
        String name = finnishDays[day - 1];
        if (maxWidth < 2) {
            maxWidth = 2;
        }
        if (name.length() > maxWidth) {
            name = finnishDayAbbreviations[day - 1];
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

    private static String[] finnishDays = {
            "maanantai", "tiistai", "keskiviikko", "torstai", "perjantai", "lauantai", "sunnuntai"
    };

    private static String[] finnishDayAbbreviations = {
            "ma", "ti", "ke", "to", "pe", "la", "su"
    };

    private static int[] minUniqueDayLength = {
            1, 2, 1, 2, 1, 1, 1
    };
}

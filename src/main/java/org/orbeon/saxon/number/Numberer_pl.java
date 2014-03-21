/**
 * Copyright (C) 2014 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.saxon.number;

public class Numberer_pl extends AbstractNumberer {

    private static final long serialVersionUID = 1L;

    private static String[] polishOrdinalUnits = { "", "pierwszy", "drugi",
            "trzeci", "czwarty", "piąty", "szósty", "siódmy", "ósmy",
            "dziewiąty", "dziesiąty", "jedenasty", "dwunasty", "trzynasty", "czternasty",
            "piętnasty", "szesnasty", "siedemnasty", "osiemnasty", "dziewiętnasty" };

    private static String[] polishOrdinalTens = { "", "dziesiąty", "dwudziesty",
            "trzydziesty", "czterdziesty", "pięćdziesiąty", "sześćdziesiąty", "siedemdziesiąty",
            "osiemdziesiąty", "dziewięćdziesiąty" };

    private static String[] polishOrdinalHundreds = { "", "setny", "dwusetny", "trzechsetny",
            "czterechsetny", "pięćsetny", "sześćsetny", "siedemsetny", "osiemsetny", "dziewięćsetny" };

    private static String[] polishUnits = { "", "jeden", "dwa", "trzy", "cztery",
            "pięć", "sześć", "siedem", "osiem", "dziewięć", "dziesięć", "jedenaście", "dwanaście",
            "trzynaście", "czternaście", "piętnaście", "szesnaście", "siedemnaście", "osiemnaście",
            "dziewiętnaście" };

    private static String[] polishTens = { "", "dziesięć", "dwadzieścia", "trzydzieści",
            "czterdzieści", "pięćdziesiąt", "sześćdziesiąt", "siedemdziesiąt", "osiemdziesiąt", "dziewięćdziesiąt" };

    private static String[] polishHundreds = { "", "sto", "dwieście", "trzysta",
            "czterysta", "pięćset", "sześćset", "siedemset", "osiemset", "dziewięćset" };

    public String toOrdinalWords(String ordinalParam, long number, int wordCase) {

        String s;
        if (number == 1000000000) {
            s = "miliardowy";
        } else if (number == 1000000) {
            s = "milionowy";
        } else if (number == 1000) {
            s = "tysięczny";
        } else if ( (number % 100 == 0) && (number / 100 < 10) ) {
            s = polishOrdinalHundreds[(int) number / 100];
        } else if (number >= 1000000000) {
            long rem = number % 1000000000;
            return (number / 1000000000 == 1 ? "" : toWords(number / 1000000000)) + " "
                + "miliard" + getEnding( (int) number / 1000000000 ) + " "
                + toOrdinalWords(ordinalParam, rem, wordCase) + " ";
        } else if (number >= 1000000) {
            long rem = number % 1000000;
            return (number / 1000000 == 1 ? "" : toWords(number / 1000000))  + " "
                + "milion" + getEnding( (int) number / 1000000 ) + " "
                + toOrdinalWords(ordinalParam, rem, wordCase) + " ";
        } else if (number >= 1000) {
            long rem = number % 1000;
            s = (number / 1000 == 1 ? "" : toWords(number / 1000)) + " ";

            if( ( number / 1000 > 10 ) && ( number / 1000 < 20 ) ) {
                s += "tysięcy" + " ";
            } else {
                if( (number / 1000) % 10 == 1 )
                    s += "tysiąc" + " ";
                else if( ( (number / 1000) % 10 > 1 ) && ( (number / 1000) % 10 < 5 ) )
                    s += "tysiące" + " ";
                else
                    s += "tysięcy" + " ";
            }
            s += toWords(rem) + " ";
            return s + " ";
        } else if (number >= 100) {
            long rem = number % 100;
            return polishHundreds[(int) number / 100] + " " + toOrdinalWords(ordinalParam, rem, wordCase) + " ";
        } else {
            if (number < 20) {
                return polishOrdinalUnits[(int) number] + " ";
            }
            int rem = (int) (number % 10);
            return polishOrdinalTens[(int) number / 10] + " " + polishOrdinalUnits[rem] + " ";
        }

        if (wordCase == UPPER_CASE) {
            return s.toUpperCase();
        } else if (wordCase == LOWER_CASE) {
            return s.toLowerCase();
        } else {
            return s;
        }
    }

    private String getEnding(int number) {
        if( ( number > 10 ) && ( number < 20 ) ) {
            return "ów";
        } else {
            if( number % 10 == 1 )
                return "";
            else if( ( number % 10 > 1 ) && ( number % 10 < 5 ) )
                return "y";
            else
                return "ów";
        }
    }

    public String toWords(long number) {
        String s;
        if (number >= 1000000000) {
            long rem = number % 1000000000;
            return (number / 1000000000 == 1 ? "" : toWords(number / 1000000000))
                + "miliard" + getEnding( (int) number / 1000000000 )
                + toWords(rem) + " ";
        } else if (number >= 1000000) {
            long rem = number % 1000000;
            return (number / 1000000 == 1 ? " " : toWords(number / 1000000))
                + "milion" + getEnding( (int) number / 1000000 )
                + toWords(rem) + " ";
        } else if (number >= 1000) {
            long rem = number % 1000;
            s = (number / 1000 == 1 ? "" : toWords(number / 1000)) + " ";

            if( ( number / 1000 > 10 ) && ( number / 1000 < 20 ) ) {
                s += "tysięcy" + " ";
            } else {
                if( (number / 1000) % 10 == 1 )
                    s += "tysiąc" + " ";
                else if( ( (number / 1000) % 10 > 1 ) && ( (number / 1000) % 10 < 5 ) )
                    s += "tysiące" + " ";
                else
                    s += "tysięcy" + " ";
            }
            s += toWords(rem);
            return s;
        } else if (number >= 100) {
            long rem = number % 100;
            return polishHundreds[(int) number / 100] + " " + toWords(rem) + " ";
        } else {
            if (number < 20) {
                return polishUnits[(int) number] + " ";
            }
            int rem = (int) (number % 10);
            return polishTens[(int) number / 10] + " " + polishUnits[rem] + " ";
        }
    }

    private static String[] polishMonths = { "styczeń", "luty", "marzec",
            "kwiecień", "maj", "czerwiec", "lipiec", "sierpień", "wrzesień", "październik",
            "listopad", "grudzień" };

    public String monthName(int month, int minWidth, int maxWidth) {
        String name = polishMonths[month - 1];
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

    public String dayName(int day, int minWidth, int maxWidth) {
        String name = polishDays[day - 1];
        if (maxWidth < 2) {
            maxWidth = 2;
        }
        if (name.length() > maxWidth) {
            name = polishDayAbbreviations[day - 1];
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

    private static String[] polishDays = { "poniedziałek", "wtorek", "środa",
            "czwartek", "piątek", "sobota", "niedziela" };

    private static String[] polishDayAbbreviations = { "pon.", "wt.", "śr.",
            "czw.", "pt.", "sob.", "niedz." };

    private static int[] minUniqueDayLength = { 2, 2, 2, 2, 2, 2, 2 };
}
    
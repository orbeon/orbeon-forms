package org.orbeon.saxon.number;


/**
 * Class Numberer_fr is a number formatter for french. This one will be
 * activated for language="fr"
 *
 * @author Luc Rochefort
 * @version 1.0
 *
 */

public class Numberer_fr extends Numberer_en {

	/**
	 * Automatically generated serialVersionUID number
	 */
	private static final long serialVersionUID = -222104830008011842L;

	private static String[] frenchUnits = { "", "Un", "Deux", "Trois", "Quatre", "Cinq", "Six", "Sept", "Huit", "Neuf", "Dix", "Onze", "Douze", "Treize", "Quatorze", "Quinze", "Seize", "Dix-sept", "Dix-huit", "Dix-neuf" };

	private static String[] frenchTens = { "", "Dix", "Vingt", "Trente", "Quarante", "Cinquante", "Soixante", "Soixante", "Quatre-vingt", "Quatre-vingt" };

	private static String[] frenchOrdinalUnits = { "", "Premier", "Deuxi\u00e8me", "Troisi\u00e8me", "Quatri\u00e8me", "Cinqui\u00e8me", "Sixi\u00e8me", "Septi\u00e8me", "Huiti\u00e8me", "Neuvi\u00e8me", "Dixi\u00e8me", "Onzi\u00e8me", "Douzi\u00e8me", "Treizi\u00e8me", "Quatorzi\u00e8me", "Quinzi\u00e8me", "Seizi\u00e8me", "Dix-septi\u00e8me", "Dix-huiti\u00e8me", "Dix-neuvi\u00e8me" };

	private static String[] frenchOrdinalTens = { "", "Dixi\u00e8me", "Vingti\u00e8me", "Trenti\u00e8me", "Quaranti\u00e8me", "Cinquanti\u00e8me", "Soixanti\u00e8me", "Soixante", "Quatre-vingti\u00e8me", "Quatre-vingt" };

	private static String[] frenchDays = { "Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi" };

	private static String[] frenchMonths = { "Janvier", "F\u00e9vrier", "Mars", "Avril", "Mai", "Juin", "Juillet", "Ao\u00fbt", "Septembre", "Octobre", "Novembre", "D\u00e9cembre" };

	/*
	 * (non-Javadoc)
	 *
	 * @see net.sf.saxon.number.Numberer_en#ordinalSuffix(java.lang.String,
	 *      long)
	 */
	protected String ordinalSuffix(String ordinalParam, long number) {
		if (number != 1) {
			return "e";
		} else {
			return "er";
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see net.sf.saxon.number.Numberer_en#toWords(long)
	 */
	public String toWords(long number) {
		return toWords(number, true);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see net.sf.saxon.number.Numberer_en#toWords(long, int)
	 */
	public String toWords(long number, int wordCase) {
		String s = toWords(number);
		if (wordCase == UPPER_CASE) {
			return s.toUpperCase();
		} else if (wordCase == LOWER_CASE) {
			return s.toLowerCase();
		} else {
			return s;
		}
	}

	private String toWords(long number, boolean terminal) {
		if (number == 0) {
			return "Z\u00e9ro";
		} else if (number >= 1000000000000000000l) {
			long rem = number % 1000000000000000000l;
			long n = number / 1000000000000000000l;
			String s = (n == 1 ? "Un" : toWords(n, true));
			return s + " quintillion" + (n > 1 ? "s" : "") + (rem == 0 ? "" : " " + toWords(rem, LOWER_CASE, terminal));
		} else if (number >= 1000000000000000l) {
			long rem = number % 1000000000000000l;
			long n = number / 1000000000000000l;
			String s = (n == 1 ? "Un" : toWords(n, true));
			return s + " quatrillion" + (n > 1 ? "s" : "") + (rem == 0 ? "" : " " + toWords(rem, LOWER_CASE, terminal));
		} else if (number >= 1000000000000l) {
			long rem = number % 1000000000000l;
			long n = number / 1000000000000l;
			String s = (n == 1 ? "Un" : toWords(n, true));
			return s + " trillion" + (n > 1 ? "s" : "") + (rem == 0 ? "" : " " + toWords(rem, LOWER_CASE, terminal));
		} else if (number >= 1000000000) {
			long rem = number % 1000000000;
			long n = number / 1000000000;
			String s = (n == 1 ? "Un" : toWords(n, true));
			return s + " milliard" + (n > 1 ? "s" : "") + (rem == 0 ? "" : " " + toWords(rem, LOWER_CASE, terminal));
		} else if (number >= 1000000) {
			long rem = number % 1000000;
			long n = number / 1000000;
			String s = (n == 1 ? "Un" : toWords(n, true));
			return s + " million" + (n > 1 ? "s" : "") + (rem == 0 ? "" : " " + toWords(rem, LOWER_CASE, terminal));
		} else if (number >= 1000) {
			long rem = number % 1000;
			long n = number / 1000;
			String s = (n == 1 ? "" : toWords(n, false));
			return s + (n == 1 ? "Mille" : " mille") + (rem == 0 ? "" : " " + toWords(rem, LOWER_CASE, terminal));
		} else if (number >= 100) {
			long rem = number % 100;
			long n = number / 100;
			String s = (n == 1 ? "" : toWords(n, false));
			return s + (n == 1 ? "Cent" : " cent") + (rem == 0 && n > 1 && terminal ? "s" : ((rem != 0) ? " " + toWords(rem, LOWER_CASE, terminal) : ""));
		} else {
			if (number < 20)
				return frenchUnits[(int) number];
			int rem = (int) (number % 10);
			int tens = (int) number / 10;
			if (tens == 7 || tens == 9) {
				rem += 10;
			}
			String link = (rem == 1 || rem == 11) ? ((tens == 8 || tens == 9) ? "-" : " et ") : "-";

			return frenchTens[tens] + (rem == 0 ? ((tens == 8 && terminal) ? "s" : "") : link) + (tens == 0 ? frenchUnits[rem] : frenchUnits[rem].toLowerCase());
		}
	}

	private String toWords(long number, int wordCase, boolean terminal) {
		String s = toWords(number, terminal);
		if (wordCase == UPPER_CASE) {
			return s.toUpperCase();
		} else if (wordCase == LOWER_CASE) {
			return s.toLowerCase();
		} else {
			return s;
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see net.sf.saxon.number.Numberer_en#toOrdinalWords(java.lang.String,
	 *      long, int)
	 */
	public String toOrdinalWords(String ordinalParam, long number, int wordCase) {
		String ord;
		if (number < 20) {
			if (number == 0) {
				ord = "Z\u00e9roi\u00e8me";
			} else {
				ord = frenchOrdinalUnits[(int) number];
			}
		} else if (number < 100) {
			long mod10 = number % 10;
			long int10 = number / 10;
			if (int10 == 7 || int10 == 9) {
				int10 -= 1;
				mod10 += 10;
			}
			if (mod10 == 0) {
				ord = frenchOrdinalTens[(int) int10];
			} else {
				String link = (mod10 == 1 || mod10 == 11) ? ((int10 == 8) ? "-" : " et ") : "-";
				String prefix = toWords(int10 * 10);
				if (int10 == 8) {
					prefix = prefix.substring(0, prefix.length() - 1);
				}
				String result = prefix + link;
				ord = result + ((mod10 == 1) ? "uni\u00e8me" : toOrdinalWords("", mod10, LOWER_CASE));
			}
		} else {
			String suffix = "i\u00e8me";
			long mod100 = number % 100;
			long int100 = number / 100;
			if (int100 == 70 || int100 == 90) {
				int100 -= 10;
				mod100 += 100;
			}

			String prefix = toWords(int100 * 100, false);
			if (int100 % 10000 == 0) {
				prefix = prefix.replaceFirst("Un ", "");
			}

			/* strip prefix, if needed */
			if ((prefix.endsWith("mille") || prefix.endsWith("Mille")) && mod100 == 0) {
				prefix = prefix.substring(0, prefix.length() - 1);
			} else if (prefix.endsWith("illions") || prefix.endsWith("illiards")) {
				prefix = prefix.substring(0, prefix.length() - 1);
			}

			ord = prefix + ((mod100 == 0) ? suffix : " " + ((mod100 == 1) ? "uni\u00e8me" : toOrdinalWords("", mod100, LOWER_CASE)));
		}
		if (wordCase == UPPER_CASE) {
			return ord.toUpperCase();
		} else if (wordCase == LOWER_CASE) {
			return ord.toLowerCase();
		} else {
			return ord;
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see net.sf.saxon.number.Numberer#monthName(int, int, int)
	 */
	public String monthName(int month, int minWidth, int maxWidth) {
		String name = frenchMonths[month - 1];
		if (maxWidth < 3) {
			maxWidth = 3;
		}
		if (name.length() > maxWidth) {
			name = name.substring(0, maxWidth);
		}
		while (name.length() < minWidth) {
			name = name + " ";
		}
		return name;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see net.sf.saxon.number.Numberer#dayName(int, int, int)
	 */
	public String dayName(int day, int minWidth, int maxWidth) {
		String name = frenchDays[day - 1];
		if (maxWidth < 3) {
			maxWidth = 3;
		}
		if (name.length() > maxWidth) {
			name = name.substring(0, maxWidth);
		}
		while (name.length() < minWidth) {
			name = name + " ";
		}
		return name;
	}

    public String format(long l, String clazz, int i, String clazz1, String clazz2, String clazz3) {
        return super.format(l, clazz, i, clazz1, clazz2, clazz3);
    }
}

//
// The contents of this file are subject to the Mozilla Public License Version
// 1.0 (the "License");
// you may not use this file except in compliance with the License. You may
// obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations
// under the License.
//
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is Michael H. Kay.
//
// Portions created by Luc Rochefort are Copyright (C) Luc Rochefort. All
// Rights Reserved.
//
// Contributor(s): 	Laurent Bourbeau, for the elaboration of JUnit tests
//					and Jean-Gr\u00e9goire Dj\u00e9nandji, for acceptance testing.
//
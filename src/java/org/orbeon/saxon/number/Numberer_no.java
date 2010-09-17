package org.orbeon.saxon.number;

public class Numberer_no extends AbstractNumberer {

	private static final long serialVersionUID = 1L;

	private static String[] norwegianOrdinalUnits = { "", "første", "andre",
			"tredje", "fjerde", "femte", "sjette", "syvende", "åttende",
			"niende", "tiende", "ellevte", "tolvte", "trettende", "fjortende",
			"femtende", "sekstende", "syttende", "attende", "nittende" };

	private static String[] norwegianOrdinalTens = { "", "tiende", "tjuende",
			"trettiende", "førtiende", "femtiende", "sekstiende", "syttiende",
			"åttiende", "nittiende" };

	private static String[] norwegianUnits = { "", "en", "to", "tre", "fire",
			"fem", "seks", "syv", "åtte", "ni", "ti", "elleve", "tolv",
			"tretten", "fjorten", "femten", "seksten", "sytten", "atten",
			"nitten" };

	private static String[] norwegianTens = { "", "ti", "tjue", "tredve",
			"førti", "femti", "seksti", "sytti", "åtti", "nitti" };

	@Override
	public String toOrdinalWords(String ordinalParam, long number, int wordCase) {

		String s;
		if (number == 1000000000) {
			s = "milliardte";
		} else if (number == 1000000) {
			s = "millionte";
		} else if (number == 1000) {
			s = "tusende";
		} else if (number == 100) {
			s = "hundrede";
		} else if (number >= 1000000000) {
			long rem = number % 1000000000;
			s = (number / 1000000000 == 1 ? "en" : toWords(number / 1000000000))
					+ "milliard"
					+ ((number / 1000000000) > 1 ? "er" : "")
					+ toOrdinalWords(ordinalParam, rem, wordCase);
		} else if (number >= 1000000) {
			long rem = number % 1000000;
			s = (number / 1000000 == 1 ? "en" : toWords(number / 1000000))
					+ "million" + ((number / 1000000) > 1 ? "er" : "")
					+ toOrdinalWords(ordinalParam, rem, wordCase);
		} else if (number >= 1000) {
			long rem = number % 1000;
			s = (number / 1000 == 1 ? "et" : toWords(number / 1000)) + "tusen"
					+ (rem < 100 ? "og" : "") + toOrdinalWords(ordinalParam, rem, wordCase);
		} else if (number >= 100) {
			long rem = number % 100;
			s = (number / 100 == 1 ? "ett" : toWords(number / 100)) + "hundre"
					+ "og" + toOrdinalWords(ordinalParam, rem, wordCase);
		} else {
			if (number < 20) {
				s = norwegianOrdinalUnits[(int) number];
			} else {
				int rem = (int) (number % 10);
				if (rem == 0) {
					s = norwegianOrdinalTens[(int) number / 10];
				} else {
					s = norwegianTens[(int) number / 10]
							+ norwegianOrdinalUnits[rem];
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

	@Override
	public String toWords(long number) {
		if (number >= 1000000000) {
			long rem = number % 1000000000;
			return (number / 1000000000 == 1 ? "en"
					: toWords(number / 1000000000))
					+ "milliard"
					+ ((number / 1000000000) > 1 ? "er" : "")
					+ (rem == 0 ? "" : "") + toWords(rem);
		} else if (number >= 1000000) {
			long rem = number % 1000000;
			return (number / 1000000 == 1 ? "en " : toWords(number / 1000000))
					+ "million" + ((number / 1000000) > 1 ? "er" : "")
					+ toWords(rem);
		} else if (number >= 1000) {
			long rem = number % 1000;
			return (number / 1000 == 1 ? "et" : toWords(number / 1000))
					+ "tusen"
					+ (rem == 0 ? "" : (rem < 100 ? "en" : "")
							+ (rem < 100 ? "og" : "") + toWords(rem));
		} else if (number >= 100) {
			long rem = number % 100;
			return (number / 100 == 1 ? "ett" : toWords(number / 100))
					+ "hundre" + "og" + toWords(rem);
		} else {
			if (number < 20) {
				return norwegianUnits[(int) number];
			}
			int rem = (int) (number % 10);
			return norwegianTens[(int) number / 10] + norwegianUnits[rem];
		}
	}

	@Override
	public String toWords(long number, int wordCase) {
		String s;
		if (number == 0) {
			s = "null";
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

	private static String[] norwegianMonths = { "januar", "februar", "mars",
			"april", "mai", "juni", "juli", "august", "september", "oktober",
			"november", "desember" };

	@Override
	public String monthName(int month, int minWidth, int maxWidth) {
		String name = norwegianMonths[month - 1];
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

	@Override
	public String dayName(int day, int minWidth, int maxWidth) {
		String name = norwegianDays[day - 1];
		if (maxWidth < 2) {
			maxWidth = 2;
		}
		if (name.length() > maxWidth) {
			name = norwegianDayAbbreviations[day - 1];
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

	private static String[] norwegianDays = { "mandag", "tirsdag", "onsdag",
			"torsdag", "fredag", "lørdag", "søndag" };

	private static String[] norwegianDayAbbreviations = { "ma", "ti", "on",
			"to", "fr", "lø", "sø" };

	private static int[] minUniqueDayLength = { 1, 2, 1, 2, 1, 2, 2 };

}

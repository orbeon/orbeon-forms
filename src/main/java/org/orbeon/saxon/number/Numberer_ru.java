package org.orbeon.saxon.number;

public class Numberer_ru extends AbstractNumberer {

	private static final long serialVersionUID = 1L;

	private static String[] russianOrdinalUnits = { "", "первый", "второй",
			"третий", "четвертый", "пятый", "шестой", "седьмой", "восьмой",
			"девятый", "десятый", "одиннадцатый", "двенадцатый", "тринадцатый", "четырнадцатый",
			"пятнадцатый", "шестнадцатый", "семнадцатый", "восемнадцатый", "девятнадцатый" };

	private static String[] russianOrdinalTens = { "", "десятый", "двадцатый",
			"тридцатый", "сороковой", "пятидесятый", "шестидесятый", "семидесятый",
			"восьмидесятый", "девяностый" };

	private static String[] russianOrdinalHundreds = { "", "сотый", "двухсотый", "трехсотый",
			"четырехсотый", "пятисотый", "шестисотый", "семисотый", "восьмисотый", "девятисотый" };

	private static String[] russianUnits = { "", "один", "два", "три", "четыре",
			"пять", "шесть", "семь", "восемь", "девять", "десять", "одиннадцать", "двенадцать",
			"тринадцать", "четырнадцать", "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать",
			"девятнадцать" };

	private static String[] russianTens = { "", "десять", "двадцать", "тридцать",
			"сорок", "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто" };

	private static String[] russianHundreds = { "", "сто", "двести", "триста",
			"четыреста", "пятьсот", "шестьсот", "семьсот", "восемьсот", "девятьсот" };

	@Override
	public String toOrdinalWords(String ordinalParam, long number, int wordCase) {

		String s;
		if (number == 1000000000) {
			s = "миллиардный";
		} else if (number == 1000000) {
			s = "миллионный";
		} else if (number == 1000) {
			s = "тысячный";
		} else if ( (number % 100 == 0) && (number / 100 < 10) ) {
			s = russianOrdinalHundreds[(int) number / 100];
		} else if (number >= 1000000000) {
			long rem = number % 1000000000;
			return (number / 1000000000 == 1 ? "один" : toWords(number / 1000000000))
				+ "миллиард" + getEnding( (int) number / 1000000000 )
				+ toOrdinalWords(ordinalParam, rem, wordCase);
		} else if (number >= 1000000) {
			long rem = number % 1000000;
			return (number / 1000000 == 1 ? "один " : toWords(number / 1000000)) 
				+ "миллион" + getEnding( (int) number / 1000000 )
				+ toOrdinalWords(ordinalParam, rem, wordCase);
		} else if (number >= 1000) {
			long rem = number % 1000;
			s = (number / 1000 == 1 ? "одна" : toWords(number / 1000));

			if( ( number / 1000 > 10 ) && ( number / 1000 < 20 ) ) {
				s += "тысяч";
			} else {
				if( (number / 1000) % 10 == 1 )
					s += "тысяча";
				else if( ( (number / 1000) % 10 > 1 ) && ( (number / 1000) % 10 < 5 ) )
					s += "тысячи";
				else
					s += "тысяч";
			}
			s += toWords(rem);
			return s;
		} else if (number >= 100) {
			long rem = number % 100;
			return russianHundreds[(int) number / 100] + toWords(rem);
		} else {
			if (number < 20) {
				return russianOrdinalUnits[(int) number];
			}
			int rem = (int) (number % 10);
			return russianTens[(int) number / 10] + russianUnits[rem];
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
			return "ов";
		} else {
			if( number % 10 == 1 )
				return "";
			else if( ( number % 10 > 1 ) && ( number % 10 < 5 ) )
				return "а";
			else
				return "ов";
		}
	}

	@Override
	public String toWords(long number) {
		String s;
		if (number >= 1000000000) {
			long rem = number % 1000000000;
			return (number / 1000000000 == 1 ? "один" : toWords(number / 1000000000))
				+ "миллиард" + getEnding( (int) number / 1000000000 )
				+ toWords(rem);
		} else if (number >= 1000000) {
			long rem = number % 1000000;
			return (number / 1000000 == 1 ? "один " : toWords(number / 1000000)) 
				+ "миллион" + getEnding( (int) number / 1000000 )
				+ toWords(rem);
		} else if (number >= 1000) {
			long rem = number % 1000;
			s = (number / 1000 == 1 ? "одна" : toWords(number / 1000));

			if( ( number / 1000 > 10 ) && ( number / 1000 < 20 ) ) {
				s += "тысяч";
			} else {
				if( (number / 1000) % 10 == 1 )
					s += "тысяча";
				else if( ( (number / 1000) % 10 > 1 ) && ( (number / 1000) % 10 < 5 ) )
					s += "тысячи";
				else
					s += "тысяч";
			}
			s += toWords(rem);
			return s;
		} else if (number >= 100) {
			long rem = number % 100;
			return russianHundreds[(int) number / 100] + toWords(rem);
		} else {
			if (number < 20) {
				return russianUnits[(int) number];
			}
			int rem = (int) (number % 10);
			return russianTens[(int) number / 10] + russianUnits[rem];
		}
	}

	@Override
	public String toWords(long number, int wordCase) {
		String s;
		if (number == 0) {
			s = "ноль";
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

	private static String[] russianMonths = { "января", "февраля", "марта",
			"апреля", "мая", "июня", "июля", "августа", "сентября", "октября",
			"ноября", "декабря" };

	@Override
	public String monthName(int month, int minWidth, int maxWidth) {
		String name = russianMonths[month - 1];
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
		String name = russianDays[day - 1];
		if (maxWidth < 2) {
			maxWidth = 2;
		}
		if (name.length() > maxWidth) {
			name = russianDayAbbreviations[day - 1];
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

	private static String[] russianDays = { "понедельник", "вторник", "среда",
			"четверг", "пятница", "суббота", "воскресенье" };

	private static String[] russianDayAbbreviations = { "пн", "вт", "ср",
			"чт", "пт", "сб", "вс" };

	private static int[] minUniqueDayLength = { 2, 2, 2, 2, 2, 2, 2 };

}

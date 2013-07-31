/**
 * Copyright (C) 2011 Orbeon, Inc.
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
(function() {

    ORBEON.xforms.control.CalendarResources = {
        "en": {
            properties: {
                "MONTHS_LONG": [ "January", "February", "March", "April", "May", "June", "July", "August",  "September",  "October",  "November",  "December" ],
                "WEEKDAYS_SHORT": ["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"],
                "START_WEEKDAY": 0
            },
            navigator: {
                month: "Month",
                year: "Year",
                submit: "OK",
                cancel: "Cancel",
                invalidYear: "Year needs to be a number"
            }
        },
        "fi": {
            properties: {
                "MONTHS_LONG": [ "Tammikuu", "Helmikuu", "Maaliskuu", "Huhtikuu", "Toukokuu", "Kes\u00e4kuu", "Hein\u00e4kuu", "Elokuu", "Syyskuu", "Lokakuu", "Marraskuu", "Joulukuu" ],
                "MONTHS_SHORT": ["Tammi", "Helmi", "Maalis", "Huhti", "Touko", "Kes\u00e4", "Hein\u00e4", "Elo", "Syys", "Loka", "Marras", "Joulu"],
                "WEEKDAYS_SHORT": ["Ma", "Ti", "Ke", "To", "Pe", "La", "Su"],
                "START_WEEKDAY": 0
            },
            navigator: {
                month: "Kuukausi",
                year: "Vuosi",
                submit: "OK",
                cancel: "Peruuttaa",
                invalidYear: "Vuosi on oltava numero"
            }
        },
	"it": {
            properties: {
                "MONTHS_LONG": [ "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno", "Luglio", "Agosto",  "Settembre",  "Ottobre",  "Novembre",  "Dicembre" ],
                "WEEKDAYS_SHORT": ["Do", "Lu", "Ma", "Me", "Gi", "Ve", "Sa"],
                "START_WEEKDAY": 0
            },
            navigator: {
                month: "Mese",
                year: "Anno",
                submit: "OK",
                cancel: "Cancella",
                invalidYear: "Anno deve essere un numero"
            }
        },
        "fr": {
            properties: {
                "MONTHS_LONG": [ "Janvier", "F\xe9vrier", "Mars", "Avril", "Mai", "Juin", "Juillet", "Ao\xfbt",  "Septembre",  "Octobre",  "Novembre",  "D\xe9cembre" ],
                "WEEKDAYS_SHORT": ["Di", "Lu", "Ma", "Me", "Je", "Ve", "Sa"],
                "START_WEEKDAY": 1
            },
            navigator: {
                month: "Moi",
                year: "Ann\xe9e",
                submit: "OK",
                cancel: "Annuler",
                invalidYear: "L'ann\xe9e est invalide"
            }
        },
        "es": {
            properties: {
                "MONTHS_LONG": [ "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto",  "Septiembre",  "Octubre",  "Noviembre",  "Diciembre" ],
                "WEEKDAYS_SHORT": ["Do", "Lu", "Ma", "Mi", "Ju", "Vi", "S\xe1"],
                "START_WEEKDAY": 1
            },
            navigator: {
                month: "Mes",
                year: "A\xf1o",
                submit: "OK",
                cancel: "Aancelar",
                invalidYear: "A\xf1o debe ser un n\xfamero"
            }
        },
        "nl": {
            properties: {
                "MONTHS_LONG": [ "Januari", "Februari", "Maart", "April", "Mei", "Juni", "Juli", "Augustus",  "September",  "Oktober",  "November",  "December" ],
                "WEEKDAYS_SHORT": ["Zo", "Ma", "Di", "Wo", "Do", "Vr", "Za"],
                "START_WEEKDAY": 1
            },
            navigator: {
               month: "Maand",
               year: "Jaar",
               submit: "OK",
               cancel: "Annuleren",
               invalidYear: "Jaar dient een getal te zijn"
            }
        },
        "de": {
            properties: {
                "MONTHS_LONG": [ "Januar", "Februar", "M\u00E4rz", "April", "Mai", "Juni", "July", "August",  "September",  "Oktober",  "November",  "Dezember" ],
                "WEEKDAYS_SHORT": ["So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"],
                "START_WEEKDAY": 0
            },
            navigator: {
                month: "Monat",
                year: "Jahr",
                submit: "OK",
                cancel: "Abbrechen",
                invalidYear: "Jahr muss eine Zahl sein"
            }
        },
        "no": {
            properties: {
                "MONTHS_LONG": [ "Januar", "Februar", "Mars", "April", "Mai", "Juni", "Juli", "August",  "September",  "Oktober",  "November",  "Desember" ],
                "WEEKDAYS_SHORT": ["s\u00F8", "ma", "ti", "on", "to", "fr", "l\u00F8"],
                "START_WEEKDAY": 1
            },
            navigator: {
                month: "M\u00E5ned",
                year: "\u00C5r",
                submit: "Ok",
                cancel: "Avbryt",
                invalidYear: "\u00C5r m\u00E5 v\u00E6re et tall"
            }
        },
        "ru": {
            properties: {
                "MONTHS_LONG": [ "\u042F\u043D\u0432\u0430\u0440\u044C", "\u0424\u0435\u0432\u0440\u0430\u043B\u044C", "\u041C\u0430\u0440\u0442", "\u0410\u043F\u0440\u0435\u043B\u044C", "\u041C\u0430\u0439", "\u0418\u044E\u043D\u044C", "\u0418\u044E\u043B\u044C", "\u0410\u0432\u0433\u0443\u0441\u0442",  "\u0421\u0435\u043D\u0442\u044F\u0431\u0440\u044C",  "\u041E\u043A\u0442\u044F\u0431\u0440\u044C",  "\u041D\u043E\u044F\u0431\u0440\u044C",  "\u0414\u0435\u043A\u0430\u0431\u0440\u044C" ],
                "MONTHS_SHORT": ["\u042f\u043d\u0432", "\u0424\u0435\u0432", "\u041c\u0430\u0440", "\u0410\u043f\u0440", "\u041c\u0430\u0439", "\u0418\u044e\u043d", "\u0418\u044e\u043b", "\u0410\u0432\u0433", "\u0421\u0435\u043d", "\u041e\u043a\u0442", "\u041d\u043e\u044f", "\u0414\u0435\u043a"],
                "WEEKDAYS_SHORT": ["\u0412\u0441", "\u041F\u043D", "\u0412\u0442", "\u0421\u0440", "\u0427\u0442", "\u041F\u0442", "\u0421\u0431"],
                "START_WEEKDAY": 1
            },
            navigator: {
               month: "\u041C\u0435\u0441\u044F\u0446",
               year: "\u0413\u043E\u0434",
               submit: "\u041E\u043A",
               cancel: "\u041E\u0442\u043C\u0435\u043D\u0438\u0442\u044C",
               invalidYear: "\u0413\u043E\u0434 \u0434\u043E\u043B\u0436\u0435\u043D \u0431\u044B\u0442\u044C \u0447\u0438\u0441\u043B\u043E\u043C"
            }
        },
		"pl": {
            properties: {
                "MONTHS_LONG": [ "Stycze\u0144", "Luty", "Marzec", "Kwiecie\u0144", "Maj", "Czerwiec", "Lipiec", "Sierpie\u0144",  "Wrzesie\u0144",  "Pa\u017Adziernik",  "Listopad",  "Grudzie\u0144" ],
                "WEEKDAYS_SHORT": ["Nd", "Pn", "Wt", "\u015Ar", "Cz", "Pt", "Sb"],
                "START_WEEKDAY": 0
            },
            navigator: {
                month: "Miesi\u0105c",
                year: "Rok",
                submit: "OK",
                cancel: "Anuluj",
                invalidYear: "Rok powinien by\u0107 liczb\u0105"
            }
        }
    };
})();

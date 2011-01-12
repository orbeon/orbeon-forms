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
                "MONTHS_LONG": [ "Januar", "Februar", "MŠrz", "April", "Mai", "Juni", "July", "August",  "September",  "Oktober",  "November",  "Dezember" ],
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
        }
    };
})();
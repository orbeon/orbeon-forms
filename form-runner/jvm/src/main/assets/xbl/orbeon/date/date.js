/**
 *  Copyright (C) 2009 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
YAHOO.namespace("xbl.fr");
YAHOO.xbl.fr.Date = function() {};
ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.Date, "xbl-fr-date");
YAHOO.xbl.fr.Date.prototype = {

    inputElement: null,
    mindateElement: null,
    maxdateElement: null,
    pagedateElement: null,

    init: function(target) {
        // Get information from the DOM
        var calendarDivElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-date-calendar-div", null, this.container)[0];

        // Only worry about initialization of inline calendar
        if (! _.isUndefined(calendarDivElement)) {
            this.inputElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-date-input", null, this.container)[0];
            this.mindateElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-date-mindate", null, this.container)[0];
            this.maxdateElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-date-maxdate", null, this.container)[0];
            this.pagedateElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-date-pagedate", null, this.container)[0];

            // Create YUI calendar
            var hasTwoMonths = ORBEON.util.Properties.datePickerTwoMonths.get();
            this.yuiCalendar = hasTwoMonths
                ? new YAHOO.widget.CalendarGroup(calendarDivElement)
                : new YAHOO.widget.Calendar(calendarDivElement);
            this.yuiCalendar.selectEvent.subscribe(this.dateSelected, this, true);
            this.setLanguage();
            this.valueChanged();
            this.parameterMindateChanged();
            this.parameterMaxdateChanged();
            this.yuiCalendar.render();
        }
    },

    valueChanged: function() {
        var date = ORBEON.util.DateTime.magicDateToJSDate(ORBEON.xforms.Document.getValue(this.inputElement.id));
        var pagedate = ORBEON.util.DateTime.magicDateToJSDate(ORBEON.xforms.Document.getValue(this.pagedateElement.id));
        if (date == null) {
            this.yuiCalendar.cfg.setProperty("selected", "", false);
            this.yuiCalendar.cfg.setProperty("pagedate", pagedate == null ? new Date() : pagedate, false);
        } else {
            // Date must be the internal format expected by YUI
            var dateStringForYUI = (date.getMonth() + 1)
               + "/" + date.getDate()
               + "/" + date.getFullYear();
            this.yuiCalendar.cfg.setProperty("selected", dateStringForYUI, false);
            this.yuiCalendar.cfg.setProperty("pagedate", pagedate == null ? date : pagedate, false);
        }
        this.yuiCalendar.render();
    },

    parameterMindateChanged: function() {
        if (! _.isUndefined(this.yuiCalendar)) {
            this.yuiCalendar.cfg.setProperty("mindate", this.mindateElement == null ? null :
                ORBEON.util.DateTime.magicDateToJSDate(ORBEON.xforms.Document.getValue(this.mindateElement.id)));
            this.yuiCalendar.render();
        }
    },

    parameterMaxdateChanged: function() {
        if (! _.isUndefined(this.yuiCalendar)) {
            this.yuiCalendar.cfg.setProperty("maxdate", this.maxdateElement == null ? null :
                ORBEON.util.DateTime.magicDateToJSDate(ORBEON.xforms.Document.getValue(this.maxdateElement.id)));
            this.yuiCalendar.render();
        }
    },

    parameterPagedateChanged: function() {
        if (! _.isUndefined(this.yuiCalendar)) {
            this.yuiCalendar.cfg.setProperty("pagedate", this.maxdateElement == null ? null :
                ORBEON.util.DateTime.magicDateToJSDate(ORBEON.xforms.Document.getValue(this.pagedateElement.id)));
            this.yuiCalendar.render();
        }
    },

    setLanguage: function() {
        var RESOURCES = {
            "en": {
                "MONTHS_LONG": [ "January", "February", "March", "April", "May", "June", "July",
                                 "August",  "September",  "October",  "November",  "December" ],
                "WEEKDAYS_SHORT": ["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"],
                "START_WEEKDAY": 0
            },
	        "it": {
                "MONTHS_LONG": [ "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno", "Luglio", "Agosto",  "Settembre",  "Ottobre",  "Novembre",  "Dicembre" ],
                "WEEKDAYS_SHORT": ["Do", "Lu", "Ma", "Me", "Gi", "Ve", "Sa"],
                "START_WEEKDAY": 1
            },
            "fr": {
                "MONTHS_LONG": [ "Janvier", "F\xe9vrier", "Mars", "Avril", "Mai", "Juin", "Juillet",
                                 "Ao\xfbt",  "Septembre",  "Octobre",  "Novembre",  "D\xe9cembre" ],
                "WEEKDAYS_SHORT": ["Di", "Lu", "Ma", "Me", "Je", "Ve", "Sa"],
                "START_WEEKDAY": 1
            },
            "es": {
                "MONTHS_LONG": [ "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio",
                                 "Agosto",  "Septiembre",  "Octubre",  "Noviembre",  "Diciembre" ],
                "WEEKDAYS_SHORT": ["Do", "Lu", "Ma", "Mi", "Ju", "Vi", "S\xe1"],
                "START_WEEKDAY": 1
            },
            "ru": {
                "MONTHS_LONG": [ "\u042F\u043D\u0432\u0430\u0440\u044C", "\u0424\u0435\u0432\u0440\u0430\u043B\u044C", "\u041C\u0430\u0440\u0442", "\u0410\u043F\u0440\u0435\u043B\u044C", "\u041C\u0430\u0439", "\u0418\u044E\u043D\u044C", "\u0418\u044E\u043B\u044C", "\u0410\u0432\u0433\u0443\u0441\u0442",  "\u0421\u0435\u043D\u0442\u044F\u0431\u0440\u044C",  "\u041E\u043A\u0442\u044F\u0431\u0440\u044C",  "\u041D\u043E\u044F\u0431\u0440\u044C",  "\u0414\u0435\u043A\u0430\u0431\u0440\u044C" ],
                "MONTHS_SHORT": ["\u042f\u043d\u0432", "\u0424\u0435\u0432", "\u041c\u0430\u0440", "\u0410\u043f\u0440", "\u041c\u0430\u0439", "\u0418\u044e\u043d", "\u0418\u044e\u043b", "\u0410\u0432\u0433", "\u0421\u0435\u043d", "\u041e\u043a\u0442", "\u041d\u043e\u044f", "\u0414\u0435\u043a"],
                "WEEKDAYS_SHORT": ["\u0412\u0441", "\u041F\u043D", "\u0412\u0442", "\u0421\u0440", "\u0427\u0442", "\u041F\u0442", "\u0421\u0431"],
                "START_WEEKDAY": 1
            }
        };

        // Get language from html/@lang
        var lang = ORBEON.util.Dom.getAttribute(document.documentElement, "lang");
        // If not language is set there, use English
        if (lang == null)
            lang = "en";
        // Just keep first 2 letters (fr_FR becomes fr)
        lang = lang.substring(0, 2);
        // Find resource for selected language
        var resources = RESOURCES[lang];
        for (var key in resources)
            this.yuiCalendar.cfg.setProperty(key, resources[key]);
    },

    dateSelected: function() {
        var jsDate = this.yuiCalendar.getSelectedDates()[0];
        var formattedDate = ORBEON.util.DateTime.jsDateToISODate(jsDate);
        ORBEON.xforms.Document.setValue(this.inputElement.id, formattedDate);
    }
};

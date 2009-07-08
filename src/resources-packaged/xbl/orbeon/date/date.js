YAHOO.namespace("xbl.fr");
YAHOO.xbl.fr.Date = {
    _instances: {},

    init: function(target) {
        var container = YAHOO.util.Dom.getAncestorByClassName(target, "xbl-fr-date");
        if (! YAHOO.xbl.fr.Date._instances[container.id]) {

            // Get information from the DOM
            var calendarDivElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-date-calendar-div", null, container)[0];
            var inputElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-date-input", null, container)[0];
            var mindateElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-date-mindate", null, container)[0];
            var maxdateElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-date-maxdate", null, container)[0];

            // Create instance
            var instance = {
                valueChanged: function() {
                    var date = ORBEON.util.DateTime.magicDateToJSDate(ORBEON.xforms.Document.getValue(inputElement.id));
                    if (date == null) {
                        yuiCalendar.cfg.setProperty("selected", "", false);
                        yuiCalendar.cfg.setProperty("pagedate", new Date(), false);
                    } else {
                        // Date must be the internal format expected by YUI
                        var dateStringForYUI = (date.getMonth() + 1)
                           + "/" + date.getDate()
                           + "/" + date.getFullYear();
                        yuiCalendar.cfg.setProperty("selected", dateStringForYUI, false);
                        yuiCalendar.cfg.setProperty("pagedate", date, false);
                    }
                    yuiCalendar.render();
                },
                propertyMindateChanged: function() {
                    yuiCalendar.cfg.setProperty("mindate", mindateElement == null ? null :
                        ORBEON.util.DateTime.magicDateToJSDate(ORBEON.xforms.Document.getValue(mindateElement.id)));
                    yuiCalendar.render();
                },
                propertyMaxdateChanged: function() {
                    yuiCalendar.cfg.setProperty("maxdate", maxdateElement == null ? null :
                        ORBEON.util.DateTime.magicDateToJSDate(ORBEON.xforms.Document.getValue(maxdateElement.id)));
                    yuiCalendar.render();
                },
                setLanguage: function() {
                    var RESOURCES = {
                        "en": {
                            "MONTHS_LONG": [ "January", "February", "March", "April", "May", "June", "July",
                                             "August",  "September",  "October",  "November",  "December" ],
                            "WEEKDAYS_SHORT": ["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"],
                            "START_WEEKDAY": 0
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
                        yuiCalendar.cfg.setProperty(key, resources[key]);
                },
                dateSelected: function() {
                    var jsDate = yuiCalendar.getSelectedDates()[0];
                    var formattedDate = ORBEON.util.DateTime.jsDateToISODate(jsDate);
                    ORBEON.xforms.Document.setValue(inputElement.id, formattedDate);
                }
            };

            // Create YUI calendar
            var yuiCalendar = ORBEON.util.Utils.getProperty(DATE_PICKER_NAVIGATOR_PROPERTY)
                ? new YAHOO.widget.CalendarGroup(calendarDivElement)
                : new YAHOO.widget.Calendar(calendarDivElement);
            yuiCalendar.selectEvent.subscribe(instance.dateSelected, instance, true);
            instance.setLanguage();
            instance.valueChanged();
            instance.propertyMindateChanged();
            instance.propertyMaxdateChanged();
            yuiCalendar.render();

            YAHOO.xbl.fr.Date._instances[container.id] = instance;
        }
    },
    valueChanged: function(target) {
        var container = YAHOO.util.Dom.getAncestorByClassName(target, "xbl-fr-date");
        var instance = YAHOO.xbl.fr.Date._instances[container.id];
        instance.valueChanged();
    },
    propertyMindateChanged: function(target) {
        var container = YAHOO.util.Dom.getAncestorByClassName(target, "xbl-fr-date");
        var instance = YAHOO.xbl.fr.Date._instances[container.id];
        if (! YAHOO.lang.isUndefined(instance))
            instance.propertyMindateChanged();
    },
    propertyMaxdateChanged: function(target) {
        var container = YAHOO.util.Dom.getAncestorByClassName(target, "xbl-fr-date");
        var instance = YAHOO.xbl.fr.Date._instances[container.id];
        if (! YAHOO.lang.isUndefined(instance))
            instance.propertyMaxdateChanged();
    }
};

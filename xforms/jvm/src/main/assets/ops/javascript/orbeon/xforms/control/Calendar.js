(function() {
  var $, Calendar, CalendarGroup, CalendarResources, Controls, DateTime, Event, Events, Language, Properties, YD, appliesToControl,
    __indexOf = [].indexOf || function(item) { for (var i = 0, l = this.length; i < l; i++) { if (i in this && this[i] === item) return i; } return -1; };

  $ = ORBEON.jQuery;

  Controls = ORBEON.xforms.Controls;

  Event = YAHOO.util.Event;

  Events = ORBEON.xforms.Events;

  Properties = ORBEON.util.Properties;

  Language = function() {
    return ORBEON.xforms.Language;
  };

  CalendarGroup = YAHOO.widget.CalendarGroup;

  Calendar = YAHOO.widget.Calendar;

  YD = YAHOO.util.Dom;

  DateTime = ORBEON.util.DateTime;

  CalendarResources = ORBEON.xforms.control.CalendarResources;

  appliesToControl = function(control) {
    return (YD.hasClass(control, "xforms-input")) && ((_.find(["xforms-type-date", "xforms-type-time", "xforms-type-dateTime"], function(c) {
      return YD.hasClass(control, c);
    })) != null);
  };

  Event.onDOMReady(function() {
    var calendarSelectEvent, closeCalendar, control, initUnder, inputField, mouseOverCalendar, setValue, yuiCalendar, yuiOverlay;
    setValue = function(control, formattedDate) {
      var changeEvent, inputHolder, isMinimal, value;
      inputHolder = $(control).children('.xforms-input-input').first();
      isMinimal = $(control).is(".xforms-input-appearance-minimal");
      if (isMinimal) {
        $(inputHolder).attr('alt', formattedDate);
      } else {
        $(inputHolder).val(formattedDate);
      }
      value = Controls.getCurrentValue(control);
      changeEvent = new ORBEON.xforms.server.AjaxServer.Event(null, control.id, value, "xxforms-value");
      return ORBEON.xforms.server.AjaxServer.fireEvent(changeEvent);
    };
    if (YAHOO.env.ua.webkit && YAHOO.env.ua.mobile) {
      initUnder = function(node) {
        var cssClass, forDate, forTime, inJS, input, toISO, toJS, type, _i, _len, _ref, _ref1, _results;
        forDate = ["input.xforms-type-date", DateTime.magicDateToJSDate, DateTime.jsDateToISODate, "date"];
        forTime = ["input.xforms-type-time", DateTime.magicTimeToJSDate, DateTime.jsDateToISOTime, "time"];
        _ref = [forDate, forTime];
        _results = [];
        for (_i = 0, _len = _ref.length; _i < _len; _i++) {
          _ref1 = _ref[_i], cssClass = _ref1[0], toJS = _ref1[1], toISO = _ref1[2], type = _ref1[3];
          _results.push((function() {
            var _j, _len1, _ref2, _results1;
            _ref2 = node.querySelectorAll(cssClass);
            _results1 = [];
            for (_j = 0, _len1 = _ref2.length; _j < _len1; _j++) {
              input = _ref2[_j];
              inJS = toJS(input.value);
              if (inJS != null) {
                input.value = toISO(inJS);
              }
              _results1.push(input.type = type);
            }
            return _results1;
          })());
        }
        return _results;
      };
      initUnder(document.body);
      Controls.typeChangedEvent.subscribe(function(event) {
        if (appliesToControl(event.control)) {
          return initUnder(event.control);
        }
      });
      Controls.fullUpdateEvent.subscribe(function(event) {
        return initUnder(event.control);
      });
      Events.blurEvent.subscribe(function(event) {
        var c, changeEvent, dateOrTimeClasses, isDateOrTimeInput, value;
        dateOrTimeClasses = ["xforms-type-date", "xforms-type-time", "xforms-type-dateTime"];
        isDateOrTimeInput = __indexOf.call((function() {
          var _i, _len, _results;
          _results = [];
          for (_i = 0, _len = dateOrTimeClasses.length; _i < _len; _i++) {
            c = dateOrTimeClasses[_i];
            _results.push(YD.hasClass(event.control, c));
          }
          return _results;
        })(), true) >= 0;
        if (isDateOrTimeInput) {
          value = Controls.getCurrentValue(event.control);
          changeEvent = new ORBEON.xforms.server.AjaxServer.Event(null, event.control.id, value, "xxforms-value");
          return ORBEON.xforms.server.AjaxServer.fireEvent(changeEvent);
        }
      });
      return Controls.valueChange.subscribe(function(event) {
        var clazz, input, value, values, _i, _len, _ref, _ref1, _results;
        if (appliesToControl(event.control)) {
          if (YD.hasClass(event.control, "xforms-type-dateTime")) {
            values = event.newValue.split("T");
            _ref = _.zip(values, ["xforms-type-date", "xforms-type-time"]);
            _results = [];
            for (_i = 0, _len = _ref.length; _i < _len; _i++) {
              _ref1 = _ref[_i], value = _ref1[0], clazz = _ref1[1];
              input = (YD.getElementsByClassName(clazz, "input", event.control))[0];
              _results.push(input.value = value);
            }
            return _results;
          } else {
            return setValue(event.control, event.newValue.substring(0, 10));
          }
        }
      });
    } else {
      yuiCalendar = null;
      yuiOverlay = null;
      control = null;
      inputField = null;
      mouseOverCalendar = false;
      closeCalendar = function() {
        if (yuiOverlay != null) {
          control = null;
          inputField = null;
          mouseOverCalendar = false;
          return yuiOverlay.cfg.setProperty("visible", false);
        }
      };
      calendarSelectEvent = function() {
        var formattedDate, jsDate;
        jsDate = yuiCalendar.getSelectedDates()[0];
        formattedDate = DateTime.jsDateToFormatDisplayDate(jsDate);
        setValue(control, formattedDate);
        $(control).children('input.xforms-input-input').first().focus();
        return closeCalendar();
      };
      Events.clickEvent.subscribe(function(event) {
        var bd, calendarDiv, canWrite, date, dateContainer, dateStringForYUI, hasTwoMonths, isDate, isDateContainer, key, lang, maxdateControl, mindateControl, overlayBodyId, pagedateControl, pagedateValue, parentCalenderDiv, resources;
        isDate = $(event.target).is('.xforms-input-input.xforms-type-date');
        canWrite = !$(event.control).is('.xforms-readonly');
        if (isDate && canWrite) {
          control = event.control;
          calendarDiv = YD.get("orbeon-calendar-div");
          if (calendarDiv == null) {
            calendarDiv = document.createElement("div");
            calendarDiv.id = "orbeon-calendar-div";
            control.appendChild(calendarDiv);
            yuiOverlay = new YAHOO.widget.Overlay(calendarDiv, {
              constraintoviewport: true
            });
            yuiOverlay.setBody("");
            yuiOverlay.render();
            $(calendarDiv).addClass('xforms-calendar-div');
            hasTwoMonths = Properties.datePickerTwoMonths.get();
            bd = (YD.getElementsByClassName("bd", null, calendarDiv))[0];
            overlayBodyId = YD.generateId(bd);
            yuiCalendar = hasTwoMonths ? new CalendarGroup(overlayBodyId) : new Calendar(overlayBodyId);
            yuiCalendar.renderEvent.subscribe(function() {
              var monthLeft, monthRight, yearLeft, yearRight;
              Event.addListener(calendarDiv, "mouseover", function() {
                return mouseOverCalendar = true;
              });
              Event.addListener(calendarDiv, "mouseout", function() {
                return mouseOverCalendar = false;
              });
              monthLeft = (YD.getElementsByClassName("calnavleft", null, calendarDiv))[0];
              yearLeft = document.createElement("a");
              yearLeft.innerHTML = "Previous Year";
              yearLeft.href = "#";
              YD.addClass(yearLeft, "calyearleft");
              YD.insertBefore(yearLeft, monthLeft);
              Event.addListener(yearLeft, "click", function(event) {
                Event.preventDefault(event);
                return setTimeout(function() {
                  var newYearLeft;
                  yuiCalendar.previousYear();
                  newYearLeft = YD.getElementsByClassName("calyearleft", "a", calendarDiv);
                  if (newYearLeft && newYearLeft[0]) {
                    return newYearLeft[0].focus();
                  }
                }, 0);
              });
              monthRight = (YD.getElementsByClassName("calnavright", null, calendarDiv))[0];
              yearRight = document.createElement("a");
              yearRight.innerHTML = "Next Year";
              yearRight.href = "#";
              YD.addClass(yearRight, "calyearright");
              YD.insertBefore(yearRight, monthRight);
              return Event.addListener(yearRight, "click", function(event) {
                Event.preventDefault(event);
                return setTimeout(function() {
                  var newYearRight;
                  yuiCalendar.nextYear();
                  newYearRight = YD.getElementsByClassName("calyearright", "a", calendarDiv);
                  if (newYearRight && newYearRight[0]) {
                    return newYearRight[0].focus();
                  }
                }, 0);
              });
            });
            yuiCalendar.selectEvent.subscribe(calendarSelectEvent);
          } else {
            control.appendChild(calendarDiv);
          }
          ORBEON.xforms.Globals.lastDialogZIndex += 2;
          YD.setStyle(calendarDiv, "z-index", ORBEON.xforms.Globals.lastDialogZIndex);
          lang = Language().getLang();
          resources = CalendarResources[lang];
          if (resources == null) {
            resources = CalendarResources["en"];
          }
          for (key in resources.properties) {
            yuiCalendar.cfg.setProperty(key, resources.properties[key]);
          }
          if (Properties.datePickerNavigator.get()) {
            yuiCalendar.cfg.setProperty("navigator", {
              strings: resources.navigator,
              monthFormat: YAHOO.widget.Calendar.SHORT,
              initialFocus: "year"
            });
          }
          date = DateTime.magicDateToJSDate(Controls.getCurrentValue(control));
          if (date == null) {
            yuiCalendar.cfg.setProperty("selected", "", false);
          } else {
            dateStringForYUI = (date.getMonth() + 1) + "/" + date.getDate() + "/" + date.getFullYear();
            yuiCalendar.cfg.setProperty("selected", dateStringForYUI, false);
          }
          dateContainer = YD.getAncestorByClassName(control, "xbl-fr-date");
          isDateContainer = dateContainer != null;
          mindateControl = (isDateContainer ? YD.getElementsByClassName("xbl-fr-date-mindate", null, dateContainer)[0] : null);
          yuiCalendar.cfg.setProperty("mindate", (mindateControl == null ? null : DateTime.magicDateToJSDate(ORBEON.xforms.Controls.getCurrentValue(mindateControl))));
          maxdateControl = (isDateContainer ? YD.getElementsByClassName("xbl-fr-date-maxdate", null, dateContainer)[0] : null);
          yuiCalendar.cfg.setProperty("maxdate", (maxdateControl == null ? null : DateTime.magicDateToJSDate(ORBEON.xforms.Controls.getCurrentValue(maxdateControl))));
          pagedateControl = (isDateContainer ? YD.getElementsByClassName("xbl-fr-date-pagedate", null, dateContainer)[0] : null);
          pagedateValue = (pagedateControl == null ? null : DateTime.magicDateToJSDate(ORBEON.xforms.Controls.getCurrentValue(pagedateControl)));
          yuiCalendar.cfg.setProperty("pagedate", (pagedateValue == null ? (date == null ? new Date() : date) : pagedateValue));
          ORBEON.xforms.Events.yuiCalendarCreated.fire({
            yuiCalendar: yuiCalendar,
            control: control
          });
          yuiCalendar.cfg.applyConfig();
          yuiCalendar.render();
          inputField = YD.getElementsByClassName("xforms-input-input", null, control)[0];
          YD.generateId(inputField);
          yuiOverlay.cfg.setProperty("constraintoviewport", false);
          yuiOverlay.cfg.setProperty("context", [inputField.id, YAHOO.widget.Overlay.TOP_LEFT, YAHOO.widget.Overlay.BOTTOM_LEFT]);
          if (!ORBEON.util.Utils.fitsInViewport(yuiOverlay.element)) {
            yuiOverlay.cfg.setProperty("context", [inputField.id, YAHOO.widget.Overlay.BOTTOM_LEFT, YAHOO.widget.Overlay.TOP_LEFT]);
            if (!ORBEON.util.Utils.fitsInViewport(yuiOverlay.element)) {
              yuiOverlay.cfg.setProperty("constraintoviewport", true);
              yuiOverlay.cfg.setProperty("context", [inputField.id, YAHOO.widget.Overlay.TOP_LEFT, YAHOO.widget.Overlay.TOP_RIGHT]);
            }
          }
          return yuiOverlay.cfg.setProperty("visible", true);
        } else {
          parentCalenderDiv = YD.getAncestorBy(event.target, function(e) {
            return e.id === "orbeon-calendar-div";
          });
          if (parentCalenderDiv == null) {
            return closeCalendar();
          }
        }
      });
      Events.blurEvent.subscribe(function(event) {
        if (appliesToControl(event.control)) {
          if (!mouseOverCalendar) {
            return closeCalendar();
          }
        }
      });
      return Events.keydownEvent.subscribe(function(event) {
        if (appliesToControl(event.control)) {
          if (event.target.className !== "yui-cal-nav-yc") {
            return closeCalendar();
          }
        }
      });
    }
  });

}).call(this);

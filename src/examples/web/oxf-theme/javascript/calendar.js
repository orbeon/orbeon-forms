var weekend = [0,6];
var weekendColor = "#9999CC";
var fontface = "Arial,Helvetica";
var fontsize = 8;

var calendarAllowWeekends = true;
var calendarAllowPastDates = true;
var calendarAllowFutureDates = true;

var gNowTime = new Date();
var gNow = new Date(gNowTime.getFullYear(), gNowTime.getMonth(), gNowTime.getDate());
var ggWinContent;
var ggPosX = -1;
var ggPosY = -1;

Calendar.Months = ["January", "February", "March", "April", "May", "June",
"July", "August", "September", "October", "November", "December"];

// Non-leap year month days
Calendar.DOMonth = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
// Leap year month days
Calendar.lDOMonth = [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

function Calendar(p_listener, p_item, p_month, p_year, p_format) {
	if ((p_month == null) && (p_year == null))	return;

	if (p_month == null) {
		this.gMonthName = null;
		this.gMonth = null;
		this.gYearly = true;
	} else {
		this.gMonthName = Calendar.get_month(p_month);
		this.gMonth = new Number(p_month);
		this.gYearly = false;
	}

	this.gYear = p_year;
	this.gFormat = p_format;
	this.gListener = p_listener;
	this.gBGColor = "white";
	this.gFGColor = "black";
	this.gTextColor = "black";
	this.gHeaderColor = "black";
	this.gReturnItem = p_item;
}

Calendar.get_month = Calendar_get_month;
Calendar.get_daysofmonth = Calendar_get_daysofmonth;
Calendar.calc_month_year = Calendar_calc_month_year;

function Calendar_get_month(monthNo) {
	return Calendar.Months[monthNo];
}

function Calendar_get_daysofmonth(monthNo, p_year) {
	/*
	Check for leap year
	1.Years evenly divisible by four are normally leap years, except for...
	2.Years also evenly divisible by 100 are not leap years, except for...
	3.Years also evenly divisible by 400 are leap years.
	*/
	if ((p_year % 4) == 0) {
		if ((p_year % 100) == 0 && (p_year % 400) != 0)
			return Calendar.DOMonth[monthNo];

		return Calendar.lDOMonth[monthNo];
	} else
		return Calendar.DOMonth[monthNo];
}

function Calendar_calc_month_year(p_Month, p_Year, incr) {
	/*
	Will return an 1-D array with 1st element being the calculated month
	and second being the calculated year
	after applying the month increment/decrement as specified by 'incr' parameter.
	'incr' will normally have 1/-1 to navigate thru the months.
	*/
	var ret_arr = new Array();

	if (incr == -1) {
		// B A C K W A R D
		if (p_Month == 0) {
			ret_arr[0] = 11;
			ret_arr[1] = parseInt(p_Year) - 1;
		}
		else {
			ret_arr[0] = parseInt(p_Month) - 1;
			ret_arr[1] = parseInt(p_Year);
		}
	} else if (incr == 1) {
		// F O R W A R D
		if (p_Month == 11) {
			ret_arr[0] = 0;
			ret_arr[1] = parseInt(p_Year) + 1;
		}
		else {
			ret_arr[0] = parseInt(p_Month) + 1;
			ret_arr[1] = parseInt(p_Year);
		}
	}

	return ret_arr;
}

function Calendar_calc_month_year(p_Month, p_Year, incr) {
	/*
	Will return an 1-D array with 1st element being the calculated month
	and second being the calculated year
	after applying the month increment/decrement as specified by 'incr' parameter.
	'incr' will normally have 1/-1 to navigate thru the months.
	*/
	var ret_arr = new Array();

	if (incr == -1) {
		// B A C K W A R D
		if (p_Month == 0) {
			ret_arr[0] = 11;
			ret_arr[1] = parseInt(p_Year) - 1;
		}
		else {
			ret_arr[0] = parseInt(p_Month) - 1;
			ret_arr[1] = parseInt(p_Year);
		}
	} else if (incr == 1) {
		// F O R W A R D
		if (p_Month == 11) {
			ret_arr[0] = 0;
			ret_arr[1] = parseInt(p_Year) + 1;
		}
		else {
			ret_arr[0] = parseInt(p_Month) + 1;
			ret_arr[1] = parseInt(p_Year);
		}
	}

	return ret_arr;
}

Calendar.prototype.getMonthlyCalendarCode = function() {
	var vCode = "";
	var vHeader_Code = "";
	var vData_Code = "";

	// Begin Table Drawing code here
	vCode += ("<div align='center'><table border='0' style='font-size:" + fontsize + "pt; background: white; border: 2px solid; border-color: #9999CC'>");

	vHeader_Code = this.cal_header();
	vData_Code = this.cal_data();
	vCode += (vHeader_Code + vData_Code);

	vCode += "</table></div>";

	return vCode;
}

Calendar.prototype.show = function() {
	// Show navigation buttons
	var prevMMYYYY = Calendar.calc_month_year(this.gMonth, this.gYear, -1);
	var prevMM = prevMMYYYY[0];
	var prevYYYY = prevMMYYYY[1];

	var nextMMYYYY = Calendar.calc_month_year(this.gMonth, this.gYear, 1);
	var nextMM = nextMMYYYY[0];
	var nextYYYY = nextMMYYYY[1];

	ggWinContent = "";

	ggWinContent += "<TABLE WIDTH='100%' BORDER=0 CELLSPACING=0 CELLPADDING=0 style='font-size:" + fontsize + "pt;'><tr><TD ALIGN=center>";
	ggWinContent += "<input type='submit' style='width: 2em; font-size: smaller' value='<<' " +
		"onMouseOver=\"window.status='Previous Year'; return true;\" " +
		"onMouseOut=\"window.status=''; return true;\" " +
		"onClick=\"Build(" +
		"'" + this.gListener + "','" + this.gReturnItem.split("'").join("\\'") + "', '" + this.gMonth + "', '" + (parseInt(this.gYear)-1) + "', '" + this.gFormat + "'" +
		");\"/></td><td align='center'>";
	ggWinContent += "<input type='submit' style='width: 2em; font-size: smaller' value='<' " +
		"onMouseOver=\"window.status='Previous Month'; return true;\" " +
		"onMouseOut=\"window.status=''; return true;\" " +
		"onClick=\"Build(" +
		"'" + this.gListener + "','" + this.gReturnItem.split("'").join("\\'") + "', '" + prevMM + "', '" + prevYYYY + "', '" + this.gFormat + "'" +
		");" +
		"\"/></td>";
    ggWinContent += "<td align='center' width='100%'>";
    ggWinContent += "<font face='" + fontface + "' ><b>" + this.gMonthName + " " + this.gYear + "</b><br>";
	ggWinContent += "</td><td align=center>";
	ggWinContent += "<input type='submit' style='width: 2em; font-size: smaller' value='>' " +
		"onMouseOver=\"window.status='Next Month'; return true;\" " +
		"onMouseOut=\"window.status=''; return true;\" " +
		"onClick=\"Build(" +
		"'" + this.gListener + "','" + this.gReturnItem.split("'").join("\\'") + "', '" + nextMM + "', '" + nextYYYY + "', '" + this.gFormat + "'" +
		");" +
		"\"/></td><td align='center'>";
	ggWinContent += "<input type='submit' style='width: 2em; font-size: smaller' value='>>' " +
		"onMouseOver=\"window.status='Next Year'; return true;\" " +
		"onMouseOut=\"window.status=''; return true;\" " +
		"onClick=\"Build(" +
		"'" + this.gListener + "','" + this.gReturnItem.split("'").join("\\'") + "', '" + this.gMonth + "', '" + (parseInt(this.gYear)+1) + "', '" + this.gFormat + "'" +
		");" +
		"\"/></td></tr></table><br>";

	// Get the complete calendar code for the month, and add it to the content
	ggWinContent += this.getMonthlyCalendarCode();
}

Calendar.prototype.showY = function() {
	var vCode = "";
	var i;

	ggWinContent += "<font face='" + fontface + "' ><B>"
	ggWinContent += ("Year : " + this.gYear);
	ggWinContent += "</B><br>";

	// Show navigation buttons
	var prevYYYY = parseInt(this.gYear) - 1;
	var nextYYYY = parseInt(this.gYear) + 1;

	ggWinContent += ("<TABLE WIDTH='100%' BORDER=1 CELLSPACING=0 CELLPADDING=0 BGCOLOR='#9999CC' style='font-size:" + fontsize + "pt;'><tr><TD ALIGN=center>");
	ggWinContent += ("[<a href=\"javascript:void(0);\" " +
		"onMouseOver=\"window.status='Go back one year'; return true;\" " +
		"onMouseOut=\"window.status=''; return true;\" " +
		"onClick=\"Build(" +
		"'" + this.gListener + "," + this.gReturnItem.split("'").join("\\'") + "', null, '" + prevYYYY + "', '" + this.gFormat + "'" +
		");" +
		"\"><<Year<\/A>]</td><TD ALIGN=center>");
	ggWinContent += "       </td><TD ALIGN=center>";
	ggWinContent += ("[<a href=\"javascript:void(0);\" " +
		"onMouseOver=\"window.status='Go forward one year'; return true;\" " +
		"onMouseOut=\"window.status=''; return true;\" " +
		"onClick=\"Build(" +
		"'" + this.gListener + "," + this.gReturnItem.split("'").join("\\'") + "', null, '" + nextYYYY + "', '" + this.gFormat + "'" +
		");" +
		"\">Year>><\/A>]</td></tr></TABLE><br>");

	// Get the complete calendar code for each month.
	// start a table and first row in the table
	ggWinContent += ("<TABLE WIDTH='100%' BORDER=0 CELLSPACING=0 CELLPADDING=5 style='font-size:" + fontsize + "pt;'><tr>");
	var j;
	for (i=0; i<12; i++) {
		// start the table cell
		ggWinContent += "<TD ALIGN='center' VALIGN='top'>";
		this.gMonth = i;
		this.gMonthName = Calendar.get_month(this.gMonth);
		vCode = this.getMonthlyCalendarCode();
		ggWinContent += (this.gMonthName + "/" + this.gYear + "<br>");
		ggWinContent += vCode;
		ggWinContent += "</td>";
		if (i == 3 || i == 7) {
			ggWinContent += "</tr><tr>";
        }
	}

	ggWinContent += "</tr></TABLE></font><br>";
}

Calendar.prototype.cal_header = function() {
	var vCode = "";

	vCode = vCode + "<tr bgcolor='#9999CC'>";
	vCode = vCode + "<td width='14%'><font face='" + fontface + "' COLOR='" + this.gHeaderColor + "'><B>Sun</B></font></td>";
	vCode = vCode + "<td width='14%'><font face='" + fontface + "' COLOR='" + this.gHeaderColor + "'><B>Mon</B></font></td>";
	vCode = vCode + "<td width='14%'><font face='" + fontface + "' COLOR='" + this.gHeaderColor + "'><B>Tue</B></font></td>";
	vCode = vCode + "<td width='14%'><font face='" + fontface + "' COLOR='" + this.gHeaderColor + "'><B>Wed</B></font></td>";
	vCode = vCode + "<td width='14%'><font face='" + fontface + "' COLOR='" + this.gHeaderColor + "'><B>Thu</B></font></td>";
	vCode = vCode + "<td width='14%'><font face='" + fontface + "' COLOR='" + this.gHeaderColor + "'><B>Fri</B></font></td>";
	vCode = vCode + "<td width='16%'><font face='" + fontface + "' COLOR='" + this.gHeaderColor + "'><B>Sat</B></font></td>";
	vCode = vCode + "</tr>";

	return vCode;
}

Calendar.prototype.cal_data = function() {
	var vDate = new Date();
	vDate.setDate(1);
	vDate.setMonth(this.gMonth);
	vDate.setFullYear(this.gYear);

	var vFirstDay=vDate.getDay();
	var vDay=1;
	var vLastDay=Calendar.get_daysofmonth(this.gMonth, this.gYear);
	var vOnLastDay=0;
	var vCode = "";

	/*
	Get day for the 1st of the requested month/year.
	Place as many blank cells before the 1st day of the month as necessary.
	*/
	vCode = vCode + "<tr>";
	for (i = 0; i < vFirstDay; i++) {
		vCode = vCode + "<td width='14%'" + this.getWeekendCellStyle(i) + "><font face='" + fontface + "'> </font></td>";
	}

	// Write rest of the 1st week
	for (j = vFirstDay; j < 7; j++) {
	    if (!calendarAllowWeekends && this.isWeekend(j) || !calendarAllowPastDates && (new Date(vDate.getFullYear(), vDate.getMonth(), vDay).getTime() < gNow.getTime())
	         || !calendarAllowFutureDates && (new Date(vDate.getFullYear(), vDate.getMonth(), vDay).getTime() > gNow.getTime())) {
	        vCode = vCode + "<td width='14%'" + this.getWeekendCellStyle(j) + "><font face='" + fontface + "'>" +
                    this.formatDay(vDay) + "</font></td>";
	    } else {
            vCode = vCode + "<td width='14%'" + this.getWeekendCellStyle(j) + "><font face='" + fontface + "'>" +
                "<a href='javascript:void(0);' " +
                    "onMouseOver=\"window.status='set date to " + this.formatDate(vDay) + "'; return true;\" " +
                    "onMouseOut=\"window.status=' '; return true;\" " +
                    "onClick=\"" +
                    ((this.gReturnItem != null && this.gReturnItem != "") ? "document.getElementById('" + this.gReturnItem +"').value='" + this.formatDate(vDay) + "';" : "") +
                    "ggPosX=-1;ggPosY=-1;nd();nd();" +
                    ((this.gListener != null && this.gListener != "") ? "eval('" + this.gListener + "(\\'" + this.formatDate(vDay)  + "\\')');" : "") +
                    "return false;" +
                    "\">" +
                    this.formatDay(vDay) +
                    "</a>" +
                    "</font></td>";
                
        }
		vDay++;
	}
	vCode = vCode + "</tr>";

	// Write the rest of the weeks
	for (k = 2; k < 7; k++) {
		vCode = vCode + "<tr>";

		for (j = 0; j < 7; j++) {
		    if (!calendarAllowWeekends && this.isWeekend(j) || !calendarAllowPastDates && (new Date(vDate.getFullYear(), vDate.getMonth(), vDay).getTime() < gNow.getTime())
		        || !calendarAllowFutureDates && (new Date(vDate.getFullYear(), vDate.getMonth(), vDay).getTime() > gNow.getTime())) {
		        vCode = vCode + "<td width='14%'" + this.getWeekendCellStyle(j) + "><font face='" + fontface + "'>" +
                    this.formatDay(vDay) +
                    "</font></td>";
		    } else {
                vCode = vCode + "<td width='14%'" + this.getWeekendCellStyle(j) + "><font face='" + fontface + "'>" +
                    "<a href='javascript:void(0);' " +
                        "onMouseOver=\"window.status='set date to " + this.formatDate(vDay) + "'; return true;\" " +
                        "onMouseOut=\"window.status=' '; return true;\" " +
                        "onClick=\"" +
                        ((this.gReturnItem != null && this.gReturnItem != "") ? "document.getElementById('" + this.gReturnItem +"').value='" + this.formatDate(vDay) + "';" : "") +
                        "ggPosX=-1;ggPosY=-1;nd();nd();" +
                        ((this.gListener != null && this.gListener != "") ? "eval('" + this.gListener + "(\\'" + this.formatDate(vDay)  + "\\')');" : "") +
                        "return false;" +
                        "\">" +
                        this.formatDay(vDay) +
                        "</a>" +
                        "</font></td>";
            }
			vDay++;

			if (vDay > vLastDay) {
				vOnLastDay = 1;
				break;
			}
		}

		if (j == 6)
			vCode = vCode + "</tr>";
		if (vOnLastDay == 1)
			break;
	}

	// Fill up the rest of last week with proper blanks, so that we get proper square blocks
	for (m = 1; m < (7 - j); m++) {
        if (this.gYearly) {
            vCode = vCode + "<td width='14%'" + this.getWeekendCellStyle(j+m) +
            "><font face='" + fontface + "' COLOR='gray'> </font></td>";
        } else {
            vCode = vCode + "<td width='14%'" + this.getWeekendCellStyle(j+m) +
            "><font face='" + fontface + "' COLOR='gray'>" + m + "</font></td>";
        }
	}

	return vCode;
}

Calendar.prototype.formatDay = function(vday) {
	var vNowDay = gNow.getDate();
	var vNowMonth = gNow.getMonth();
	var vNowYear = gNow.getFullYear();

	if (vday == vNowDay && this.gMonth == vNowMonth && this.gYear == vNowYear)
		return ("<font color=\"RED\"><b>" + vday + "</b></font>");
	else
		return (vday);
}

Calendar.prototype.getWeekendCellStyle = function(vday) {
	for (var i = 0; i < weekend.length; i++) {
		if (vday == weekend[i])
			return (" bgcolor=\"" + weekendColor + "\"");
	}
	return "";
}

Calendar.prototype.isWeekend = function(vday) {
	for (var i = 0; i < weekend.length; i++) {
		if (vday == weekend[i])
		    return true;
	}
	return false;
}

Calendar.prototype.formatDate = function(p_day) {
	var vData;
	var vMonth = 1 + this.gMonth;
	vMonth = (vMonth.toString().length < 2) ? "0" + vMonth : vMonth;
	var vMon = Calendar.get_month(this.gMonth).substr(0,3).toUpperCase();
	var vFMon = Calendar.get_month(this.gMonth).toUpperCase();
	var vY4 = new String(this.gYear);
	var vY2 = new String(this.gYear.substr(2,2));
	var vDD = (p_day.toString().length < 2) ? "0" + p_day : p_day;

	switch (this.gFormat) {
		case "MM\/DD\/YYYY" :
			vData = vMonth + "\/" + vDD + "\/" + vY4;
			break;
		case "MM\/DD\/YY" :
			vData = vMonth + "\/" + vDD + "\/" + vY2;
			break;
		case "MM-DD-YYYY" :
			vData = vMonth + "-" + vDD + "-" + vY4;
			break;
		case "YYYY-MM-DD" :
			vData = vY4 + "-" + vMonth + "-" + vDD;
			break;
		case "MM-DD-YY" :
			vData = vMonth + "-" + vDD + "-" + vY2;
			break;
		case "DD\/MON\/YYYY" :
			vData = vDD + "\/" + vMon + "\/" + vY4;
			break;
		case "DD\/MON\/YY" :
			vData = vDD + "\/" + vMon + "\/" + vY2;
			break;
		case "DD-MON-YYYY" :
			vData = vDD + "-" + vMon + "-" + vY4;
			break;
		case "DD-MON-YY" :
			vData = vDD + "-" + vMon + "-" + vY2;
			break;
		case "DD\/MONTH\/YYYY" :
			vData = vDD + "\/" + vFMon + "\/" + vY4;
			break;
		case "DD\/MONTH\/YY" :
			vData = vDD + "\/" + vFMon + "\/" + vY2;
			break;
		case "DD-MONTH-YYYY" :
			vData = vDD + "-" + vFMon + "-" + vY4;
			break;
		case "DD-MONTH-YY" :
			vData = vDD + "-" + vFMon + "-" + vY2;
			break;
		case "DD\/MM\/YYYY" :
			vData = vDD + "\/" + vMonth + "\/" + vY4;
			break;
		case "DD\/MM\/YY" :
			vData = vDD + "\/" + vMonth + "\/" + vY2;
			break;
		case "DD-MM-YYYY" :
			vData = vDD + "-" + vMonth + "-" + vY4;
			break;
		case "DD-MM-YY" :
			vData = vDD + "-" + vMonth + "-" + vY2;
			break;
		default :
			vData = vMonth + "\/" + vDD + "\/" + vY4;
	}

	return vData;
}

function Build(p_listener, p_item, p_month, p_year, p_format) {
	gCal = new Calendar(p_listener, p_item, p_month, p_year, p_format);

	// Choose appropriate show function
	if (gCal.gYearly) {
		// and, since the yearly calendar is so large, override the positioning and fontsize
		// warning: in IE6, it appears that "select" fields on the form will still show
		//	through the "over" div; Note: you can set these variables as part of the onClick
		//	javascript code before you call the show_yearly_calendar function
		if (ggPosX == -1) ggPosX = 10;
		if (ggPosY == -1) ggPosY = 10;
		if (fontsize == 8) fontsize = 6;
		// generate the calendar
		gCal.showY();
    } else {
		gCal.show();
    }

	// if this is the first calendar popup, use autopositioning with an offset
	if (ggPosX == -1 && ggPosY == -1) {
		overlib(ggWinContent, AUTOSTATUSCAP, STICKY, CLOSECLICK, CSSSTYLE,
			TEXTSIZEUNIT, "pt", TEXTSIZE, 8, CAPTIONSIZEUNIT, "pt", CAPTIONSIZE, 8, CLOSESIZEUNIT, "pt", CLOSESIZE, 8,
			CAPTION, "Select a Date", OFFSETX, 115, OFFSETY, -20, BGCOLOR, '#9999CC', FGCOLOR, '#red', CLOSECOLOR, '#281154');
		// save where the 'over' div ended up; we want to stay in the same place if the user
		//	clicks on one of the year or month navigation links
		if ( (ns4) || (ie4) ) {
            ggPosX = parseInt(over.left);
            ggPosY = parseInt(over.top);
        } else if (ns6) {
			ggPosX = parseInt(over.style.left);
			ggPosY = parseInt(over.style.top);
        }
    } else {
		// we have a saved X & Y position, so use those with the FIXX and FIXY options
		overlib(ggWinContent, AUTOSTATUSCAP, STICKY, CLOSECLICK, CSSSTYLE,
			TEXTSIZEUNIT, "pt", TEXTSIZE, 8, CAPTIONSIZEUNIT, "pt", CAPTIONSIZE, 8, CLOSESIZEUNIT, "pt", CLOSESIZE, 8,
			CAPTION, "Select a Date", FIXX, ggPosX, FIXY, ggPosY, BGCOLOR, '#9999CC', FGCOLOR, '#red', CLOSECOLOR, CLOSECOLOR, '#281154');
    }
}

function showCalendar() {
	/*
	    p_listener : Listener to call when a value is chosen
	    p_item	:    Where to store the return Item
		p_month :    0-11 for Jan-Dec; 12 for All Months
		p_year	:    4-digit year
		p_format:    Date format (mm/dd/yyyy, dd/mm/yy, ...)
	*/

    var p_listener = arguments[0];
	var p_item = arguments[1];
	var p_month = (arguments[2] == null) ? new String(gNow.getMonth()) : arguments[2];
	var p_year = (arguments[3] == "" || arguments[3] == null) ?  new String(gNow.getFullYear().toString()) : arguments[3];
	var p_format = (arguments[4] == null) ? "YYYY-MM-DD" : arguments[4];

	Build(p_listener, p_item, p_month, p_year, p_format);
}

function showYearlyCalendar() {
	// Load the defaults
	//if (p_year == null || p_year == "")
	//	p_year = new String(gNow.getFullYear().toString());
	//if (p_format == null || p_format == "")
	//	p_format = "YYYY-MM-DD";

	p_item = arguments[0];
	if (arguments[1] == "" || arguments[1] == null)
		p_year = new String(gNow.getFullYear().toString());
	else
		p_year = arguments[1];
	if (arguments[2] == null)
		p_format = "YYYY-MM-DD";
	else
		p_format = arguments[2];

	Build(p_item, null, p_year, p_format);
}

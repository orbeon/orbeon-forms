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
YAHOO.xbl.fr.FusionCharts = function() {};
ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.FusionCharts, "xbl-fr-fusion-charts");
YAHOO.xbl.fr.FusionCharts.prototype = {

    xmlSpan: null,
    xml: null,
    createChart: null,

    init: function() {
        // Get information from the DOM
        var chartDiv = YAHOO.util.Dom.getElementsByClassName("xbl-fr-fusion-charts-chart-div", null, this.container)[0];
        this.xmlSpan = YAHOO.util.Dom.getElementsByClassName("xbl-fr-fusion-charts-xml", null, this.container)[0];
        var uriToSwfElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-fusion-charts-uri-to-swf", null, this.container)[0];
        var uriToSwf = ORBEON.xforms.Document.getValue(uriToSwfElement.id);
        var swfElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-fusion-charts-swf", null, this.container)[0];
        var swf = ORBEON.xforms.Document.getValue(swfElement.id);
        var widthElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-fusion-charts-width", null, this.container)[0];
        var width = ORBEON.xforms.Document.getValue(widthElement.id);
        var heightElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-fusion-charts-height", null, this.container)[0];
        var height = ORBEON.xforms.Document.getValue(heightElement.id);
        var resourcesBaseURL = ORBEON.xforms.Globals.resourcesBaseURL[ORBEON.xforms.Controls.getForm(this.container).id];
        var pathToSwf = resourcesBaseURL + uriToSwf + "/" + swf + ".swf?registerWithJS=1";
        this.createChart = function() {
            var fusionChart = new FusionCharts(pathToSwf, this.container.id + "-fusion", width, height, "0", "0");
            fusionChart.setDataXML(this.xml);
            fusionChart.setTransparent(false);
            fusionChart.render(chartDiv.id);
        };
        this.updateChart();
        ORBEON.xforms.Events.ajaxResponseProcessedEvent.subscribe(function() { this.updateChart(); }, this, true);
    },

    updateChart: function() {
        var newXML = ORBEON.xforms.Document.getValue(this.xmlSpan.id);
        if (newXML != this.xml) {
            this.xml = newXML;
            // Recreate the chart on every update, as setDataXML() on an existing chart doesn't work on the free version
            this.createChart();
        }
    }
};

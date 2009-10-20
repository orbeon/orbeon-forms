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

    initialized: false,

    init: function() {

        if (this.initialized) return;
        this.initialized = true;

        // Get information from the DOM
        var chartDiv = YAHOO.util.Dom.getElementsByClassName("xbl-fr-fusion-charts-chart-div", null, container)[0];
        var xmlSpan = YAHOO.util.Dom.getElementsByClassName("xbl-fr-fusion-charts-xml", null, container)[0];
        var uriToSwfElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-fusion-charts-uri-to-swf", null, container)[0];
        var uriToSwf = ORBEON.xforms.Document.getValue(uriToSwfElement.id);
        var swfElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-fusion-charts-swf", null, container)[0];
        var swf = ORBEON.xforms.Document.getValue(swfElement.id);
        var widthElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-fusion-charts-width", null, container)[0];
        var width = ORBEON.xforms.Document.getValue(widthElement.id);
        var heightElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-fusion-charts-height", null, container)[0];
        var height = ORBEON.xforms.Document.getValue(heightElement.id);

        var pathToSwf = ORBEON.xforms.Globals.resourcesBaseURL + uriToSwf + "/" + swf + ".swf?registerWithJS=1";
        var fusionChart = new FusionCharts(pathToSwf, container.id + "-fusion", width, height, "0", "0");
        var xml = null;

        // Create instance
        var instance = {
            updateChart: function() {
                var newXML = ORBEON.util.Dom.getStringValue(xmlSpan);
                if (newXML != xml) {
                    xml = newXML;
                    fusionChart.setDataXML(xml);
                }
            }
        };
        instance.updateChart();
        fusionChart.render(chartDiv.id);
        ORBEON.xforms.Events.ajaxResponseProcessedEvent.subscribe(function() { instance.updateChart(); });
        this._instances[container.id] = instance;
    }
};

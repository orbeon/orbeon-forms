YAHOO.namespace("xbl.fr");
YAHOO.xbl.fr.FusionCharts = {
    _instances: {},

    _getInstance: function(target) {
        var container = YAHOO.util.Dom.getAncestorByClassName(target, "xbl-fr-fusion-charts");
        return this._instances[container.id];
    },

    init: function(target) {
        var container = YAHOO.util.Dom.getAncestorByClassName(target, "xbl-fr-fusion-charts");
        if (! this._instances[container.id]) {

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
    }
};

(function() {
    var Dom = YAHOO.util.Dom,
        Event = YAHOO.util.Event,
        Lang = YAHOO.lang,
        Document = ORBEON.xforms.Document;

    /**
     * Singleton with information about about each map, indexed by ID of the component.
     */
    var maps = {};

    /**
     * Map object constructor
     */
    var Map = function(element) { this.init(element); }
    Map.prototype = {

        /**
         * Attributes
         */
        element: null,
        gmapDivID: null,
        gmap: null,
        geocoder: null,
        addressOutputID: null,
        longitudeInputID: null,
        latitudeInputID: null,
        marker: null,

        /**
         * Constructor
         */
        init: function(element) {

            // Init object attributes
            var gmapDiv = Dom.getElementsByClassName("fb-map-gmap-div", null, element)[0];
            this.element = element;
            this.gmapDivID = gmapDiv.id;
            this.addressOutputID = Dom.getElementsByClassName("fb-map-address", null, element)[0].id;
            this.longitudeInputID = Dom.getElementsByClassName("fb-map-longitude", null, element)[0].id;
            this.latitudeInputID = Dom.getElementsByClassName("fb-map-latitude", null, element)[0].id;
            this.geocoder = new GClientGeocoder();

            // Create map with its controls
            this.gmap = new GMap2(gmapDiv);
            this.gmap.addControl(new GSmallZoomControl3D());
            this.gmap.addControl(new GScaleControl());
            this.gmap.addControl(new GOverviewMapControl());

            // Set location
            this.updateMarkerFromAddress();
        },

        /**
         * Updates the address
         */
        updateMarkerFromAddress: function() {
            var address = Document.getValue(this.addressOutputID);
            var map = this;
            console.log("new address", address);
            this.geocoder.getLatLng(address, function(longLat) {
                if (longLat != null) {
                    map._updateLongLat(longLat);
                    // Mark location
                    map.gmap.setCenter(longLat, 13);
                    if (map.marker == null) {
                        map.marker = new GMarker(longLat, {draggable: true});
                        map.gmap.addOverlay(map.marker);
                        GEvent.addListener(map.marker, "dragend", function(longLat) { map._updateLongLat(longLat); });
                    } else{
                        map.marker.setLatLng(longLat)
                    }
                }
            });
        },

        _updateLongLat: function(longLat) {
            console.log("long/lat", longLat);
            Document.setValue(this.longitudeInputID, longLat.lng());
            Document.setValue(this.latitudeInputID, longLat.lat());
        }
    };

    ORBEON.widget.MapEvents = {

        /**
         * Called when the group containing the map becomes enabled.
         */
        mapContainerXFormsEnabled: function() {
            var mapContainer = this;
            var mapID = mapContainer.parentNode.id;
            if (!Lang.isObject(maps[mapID])) {
                maps[mapID] = new Map(mapContainer.parentNode);
            }
        },

        /**
         * Called when the address provided to the component changes.
         */
        addressXFormsValueChanged: function() {
            var mapID = this.parentNode.parentNode.id;
            maps[mapID].updateMarkerFromAddress();
        }
    };

})();


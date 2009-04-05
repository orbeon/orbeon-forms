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
            var initialLatitude = Document.getValue(this.latitudeInputID);
            var initialLongitude = Document.getValue(this.longitudeInputID);
            if (initialLatitude != "" && initialLongitude != "") {
                var latLng = new GLatLng(new Number(initialLatitude), new Number(initialLongitude));
                this.updateMarkerFromLatLng(latLng);
            } else {
                this.updateMarkerFromAddress();
            }
        },

        updateMarkerFromAddress: function() {
            var map = this;
            var address = Document.getValue(map.addressOutputID);
            this.geocoder.getLatLng(address, function(latLng) {
                if (latLng != null) {
                    map.updateMarkerFromLatLng(latLng);
                }
            });
        },

        updateMarkerFromLatLng: function(latLng) {
            var map = this;
            map.gmap.setCenter(latLng, 13);
            if (map.marker == null) {
                map.marker = new GMarker(latLng, {draggable: true});
                map.gmap.addOverlay(map.marker);
                GEvent.addListener(map.marker, "dragend", function(latLng) { map._updateLongLat(latLng); });
            } else{
                map.marker.setLatLng(latLng);
            }
        },

        _updateLongLat: function(longLat) {
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


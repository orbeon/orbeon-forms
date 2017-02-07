/**
 * Copyright (C) 2014 Orbeon, Inc.
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
    var $ = ORBEON.jQuery;
    var Document = ORBEON.xforms.Document;

    YAHOO.namespace("xbl.fr");
    YAHOO.xbl.fr.Map= function() {};
    ORBEON.xforms.XBL.declareClass(YAHOO.xbl.fr.Map, "xbl-fr-map");
    YAHOO.xbl.fr.Map.prototype = {

        map              : null,
        geoCoder         : null,
        keyOutputID      : null,
        addressOutputID  : null,
        longitudeInputID : null,
        latitudeInputID  : null,
        marker           : null,

        init: function() {
            var me = this;

            // Init object attributes
            var mapDiv          = $(this.container).find('.fb-map-gmap-div' )[0];
            me.addressOutputID  = $(this.container).find('.fb-map-address'  )[0].id;
            me.longitudeInputID = $(this.container).find('.fb-map-longitude')[0].id;
            me.latitudeInputID  = $(this.container).find('.fb-map-latitude' )[0].id;

            // By default load the map zoomed out, and we'll then update it if we have a lat/lng or address
            var mapOptions = {
                center: { lat: 0, lng: 0},
                zoom: 1,
                scaleControl: true,
                zoomControl: true
            };

            // Create map with its controls
            me.map = new google.maps.Map(mapDiv, mapOptions);
            me.geoCoder = new google.maps.Geocoder();

            // Set location
            var initialLatitude  = Document.getValue(me.latitudeInputID);
            var initialLongitude = Document.getValue(me.longitudeInputID);
            if (initialLatitude != "" && initialLongitude != "") {
                var latLng = new google.maps.LatLng(Number(initialLatitude), Number(initialLongitude));
                me.updateMarkerFromLatLng(latLng);
                return latLng;
            } else {
                me.updateMarkerFromAddress();
            }
        },

        updateMarkerFromAddress: function() {
            var me = this;
            var address = Document.getValue(me.addressOutputID);
            me.geoCoder.geocode({ 'address': address}, function (results, status) {
                if (status == google.maps.GeocoderStatus.OK)
                    me.updateMarkerFromLatLng(results[0].geometry.location);
            });
        },

        updateMarkerFromLatLng: function(latLng) {
            var me = this;

            // Create marker and listen to user moving it, if we haven't done so already
            if (me.marker == null) {
                me.marker = new google.maps.Marker({
                    map       : me.map,
                    draggable : true
                });
                google.maps.event.addListener(me.marker, 'dragend', function() {
                    me._updateLongLat(me.marker.getPosition());
                });
            }

            // Center map and set the position of the marker
            me.map.setCenter(latLng);
            me.marker.setPosition(latLng);


            // If we are still showing the whole earth, now is the time to zoom in
            if (me.map.getZoom() == 1)
                me.map.setZoom(14);
        },

        _updateLongLat: function(longLat) {
            Document.setValue(this.longitudeInputID, longLat.lng());
            Document.setValue(this.latitudeInputID,  longLat.lat());
        }
    };
})();

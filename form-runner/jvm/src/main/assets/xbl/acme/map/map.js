/**
 * Copyright (C) 2022 Orbeon, Inc.
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

    ORBEON.xforms.XBL.declareCompanion("acme|map", {

        _googleMap : null,
        _geoCoder  : null,
        _marker    : null,
        _address   : null,

        init: function() {
            // Delegate to a recursive worker function, as recursively calling `init` would have not no effect,
            // as we override `init` so subsequent calls are ignored.
            this._initWorker();
        },

        _initWorker: function() {

            if (
                "google" in window &&
                "maps" in google &&
                "Map" in google.maps
            ) {
                var mapElement = this.container.querySelector('.acme-map-google');
                var mapOptions = {
                    center: { lat: 0, lng: 0},
                    zoom: 1,
                    scaleControl: true,
                    zoomControl: true
                };
                this._googleMap = new google.maps.Map(mapElement, mapOptions);
                this._geoCoder  = new google.maps.Geocoder();
            } else {
                // The additional scripts loaded by the map API haven't been loaded yet, try again after a short delay
                var shortDelay = ORBEON.util.Properties.internalShortDelay.get();
                setTimeout(this._initWorker.bind(this), shortDelay);
            }
        },

        xformsUpdateValue: function(address) {
            this._address = address;
            var me = this;
            me._geoCoder.geocode({ 'address': address}, function (results, status) {
                if (status == google.maps.GeocoderStatus.OK) {
                    var latLng = results[0].geometry.location;
                    me._updateMarkerFromLatLng(latLng);
                }
            });
        },

        xformsGetValue: function() {
            return this._address;
        },

        _updateMarkerFromLatLng: function(latLng) {
            var me = this;

            // Create marker and listen to user moving it, if we haven't done so already
            if (me._marker == null) {
                me._marker = new google.maps.Marker({
                    map       : me._googleMap
                });
            }

            // Center map and set the position of the marker
            me._googleMap.setCenter(latLng);
            me._marker.setPosition(latLng);

            // If we are still showing the whole earth, now is the time to zoom in
            if (me._googleMap.getZoom() == 1)
                me._googleMap.setZoom(14);
        }
    });
})();

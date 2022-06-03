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

ORBEON.xforms.XBL.declareCompanion("acme|map", {

    _googleMap   : null,
    _geoCoder    : null,
    _marker      : null,
    _address     : null,
    _initPromise : null,
    _marker      : null,

    init: function() {

        // If needed, dynamically load the JavaScript for the Google Maps API
        const scripts              = Array.from(document.scripts);
        const googleMapsJs         = "https://maps.googleapis.com/maps/api/js";
        const needToLoadJs         = scripts.find((script) => script.src.startsWith(googleMapsJs)) === undefined;
        if (needToLoadJs) {
            const mapElement       = this.container.querySelector('.acme-map-google');
            const googleMapsKey    = mapElement.getAttribute("data-google-maps-key");
            const googleMapsScript = document.createElement("script");
            googleMapsScript.src   = googleMapsJs + "?key=" + googleMapsKey;
            googleMapsScript.async = true;
            document.head.appendChild(googleMapsScript);
        }

        const jsLoadedPromise = new Promise((resolutionFunc) => {
            (function worker() {
                const shortDelay      = ORBEON.util.Properties.internalShortDelay.get();
                const googleMapsFound =
                    "google" in window &&
                    "maps"   in google &&
                    "Map"    in google.maps;
                if   (googleMapsFound) resolutionFunc();
                else setTimeout(worker, shortDelay);
            })();
        });

        var initPromiseResolutionFunc = null;
        this._initPromise = new Promise((resolutionFunc) => {
            initPromiseResolutionFunc = resolutionFunc;
        });

        jsLoadedPromise.then(() => {
            var mapElement = this.container.querySelector('.acme-map-google');
            var mapOptions = {
                center: { lat: 0, lng: 0},
                zoom: 1,
                scaleControl: true,
                zoomControl: true
            };
            this._googleMap = new google.maps.Map(mapElement, mapOptions);
            this._geoCoder  = new google.maps.Geocoder();
            initPromiseResolutionFunc();
        });
    },

    xformsUpdateValue: function(address) {
        var companion = this;
        return this._initPromise.then(() => {
            companion._address = address;
            companion._geoCoder.geocode(
                { 'address': address },
                (results, status) => {
                    if (status == google.maps.GeocoderStatus.OK) {
                        var latLng = results[0].geometry.location;
                        companion._updateMarkerFromLatLng(latLng);
                    }
                }
            );
        });
    },

    xformsGetValue: function() {
        return this._address;
    },

    _updateMarkerFromLatLng: function(latLng) {

        // Create marker if needed
        if (this._marker == null) {
            this._marker = new google.maps.Marker({
                map       : this._googleMap
            });
        }

        // Center map and set the position of the marker
        this._googleMap.setCenter(latLng);
        this._marker.setPosition(latLng);

        // If we are still showing the whole earth, now is the time to zoom in
        if (this._googleMap.getZoom() == 1)
            this._googleMap.setZoom(14);
    }
});

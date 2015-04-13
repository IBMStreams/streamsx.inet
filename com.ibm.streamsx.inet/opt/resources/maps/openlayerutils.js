createMarkerLayer = function(map) {
    var markerLayer = new OpenLayers.Layer.Markers("Markers");
    map.addLayer(markerLayer);    
    return markerLayer;
}

getMarkerIcon = function(markerLayer, markerType) {

    if (markerType == 'GREEN') {
         if (markerLayer.streamsx_inet_green == undefined) {
             markerLayer.streamsx_inet_green =
                     new OpenLayers.Icon('marker-green.png')
         }
         
         return markerLayer.streamsx_inet_green.clone();
    }
    if (markerType == 'YELLOW') {
         if (markerLayer.streamsx_inet_yellow == undefined) {
             markerLayer.streamsx_inet_yellow =
                     new OpenLayers.Icon('marker-gold.png')
         }
         
         return markerLayer.streamsx_inet_yellow.clone();
    }
    if (markerType == 'RED') {
         if (markerLayer.streamsx_inet_red == undefined) {
             markerLayer.streamsx_inet_red =
                     new OpenLayers.Icon('marker-red.png')
         }
         
         return markerLayer.streamsx_inet_red.clone();
    }
    if (markerType == 'WARNING') {
         if (markerLayer.streamsx_inet_warning == undefined) {
             markerLayer.streamsx_inet_warning =
                     new OpenLayers.Icon('marker-warning.png')
         }
         
         return markerLayer.streamsx_inet_warning.clone();
    }
    
    return null;
}


addMarkersToLayer = function(markerLayer, markers, response) {

   var tuples = response;
   
   var espg4326 = new OpenLayers.Projection("EPSG:4326");   
   var mapProjection = markerLayer.map.getProjectionObject();
      		
   var newMarkers = {} ;
   			
   for (var i = 0; i < tuples.length; i++) {
       var tuple = tuples[i];
       var id = tuple.id;
       
       if (id in markers) {
           markerLayer.removeMarker(markers[id]);
           delete markers[id];
       }
       
       var markerType = tuple.markerType;
       if (markerType == undefined)
            markerType = 'GREEN';
                 
       var icon = getMarkerIcon(markerLayer, markerType);
       var longLat = new OpenLayers.LonLat(tuple.longitude, tuple.latitude);
       longLat.transform(espg4326, mapProjection);	

       var marker = new OpenLayers.Marker(longLat, icon);
       markerLayer.addMarker(marker);
       newMarkers[id] = marker;
       
       if (i == 0 && markerLayer.map.getCenter() == undefined) {
           markerLayer.map.setCenter(longLat, 10);
       }
    }
    
    // Remove any markers for which there was no new value.
    for (var id in markers) {
       if (markers.hasOwnProperty(id)) {
            markerLayer.removeMarker(markers[id]);
            delete markers[id];
       }
    }
    for (var id in newMarkers) {
       if (newMarkers.hasOwnProperty(id)) {
            markers[id] = newMarkers[id];
       }
    }
    
}

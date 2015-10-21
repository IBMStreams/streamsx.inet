createMarkerLayer = function(map) {
//    var markerLayer = new OpenLayers.Layer.Markers("Markers");
    var markerLayer = new OpenLayers.Layer.Vector("Markers");
    map.addLayer(markerLayer);
    
    return markerLayer;
}

JSONreplacer = function(key, value)
{
   if (value == "" || key == "markerType")
      return undefined;
   
   return value;
}

makePopupText = function(tuple) {
   return JSON.stringify(tuple, JSONreplacer, "<br />")
               .replace(/["{}]/g, "")   // get rid of JSON delimiters
               .substring(7);           // skip the first newline
}

createPopup = function(feature) {
   feature.popup = new OpenLayers.Popup.FramedCloud("Popup",
                           feature.geometry.getBounds().getCenterLonLat(),
                           null,
                           '<div>' + makePopupText(feature.attributes.spltuple) + '</div>',
                           null,
                           false,
                           function() { controls['selector'].unselectAll(); }
                        );
   feature.layer.map.addPopup(feature.popup);
}

destroyPopup = function(feature) {
   feature.popup.destroy();
   feature.popup = null;
}

moveMarker = function(feature, targetLoc) {
   feature.move(targetLoc);
   
   if (feature.popup) {
      feature.popup.setContentHTML('<div>' + makePopupText(feature.attributes.spltuple) + '</div>');
      feature.popup.updateSize();
      feature.popup.lonlat.lon = targetLoc.lon;
      feature.popup.lonlat.lat = targetLoc.lat;
      feature.popup.updatePosition();
   }
}

addMarkersToLayer = function(markerLayer, markers, response) {

   var tuples = response;
   
   var epsg4326 = new OpenLayers.Projection("EPSG:4326");   
   var mapProjection = markerLayer.map.getProjectionObject();
      		
   var updated = [] ;
   			
   for (var i = 0; i < tuples.length; i++) {
      var tuple = tuples[i];
      var id = tuple.id;
      updated.push(id);
      var markerType = tuple.markerType;
      if (markerType == undefined)
         markerType = 'marker-blue.png';
           
      if (id in markers) {
         if (markers[id].attributes.spltuple.markerType != markerType)
            markers[id].style.externalGraphic = markerType;
         
         markers[id].attributes.spltuple = tuple;
         var point  = new OpenLayers.LonLat(tuple.longitude, tuple.latitude);
         point.transform(epsg4326, mapProjection);
         moveMarker(markers[id], point);
      } else {
         var point = new OpenLayers.Geometry.Point(tuple.longitude, tuple.latitude);
         point.transform(epsg4326, mapProjection);	
         var marker = new OpenLayers.Feature.Vector(point,
                              {spltuple: tuple},
                              {externalGraphic: markerType, 
                               graphicHeight: 25, graphicWidth: 21 /*,
                               graphicXOffset:-12, graphicYOffset:-25 */ 
                              }
                           );
         marker.fid = id;
         markerLayer.addFeatures(marker);
//         marker.map = markerLayer.map;    // Not necessary?
         markers[id] = marker;
      
         // First time only: set map viewport if not already set for geofences
         if (i == 0 && markerLayer.map.getCenter() == undefined) {
            markerLayer.map.setCenter(longLat, 12);
         }
      
         // marker.events.register('mouseover', marker, tupleShowPopup);     
      }
   }
   
   // Remove any markers for which there was no new value
   for (var id in markers) {
      if (markers.hasOwnProperty(id) && updated.indexOf(id) == -1) {
         markerLayer.removeMarker(markers[id]);
         markers[id].spltuple = null;
         markers[id].style.icon = null;
         delete markers[id];
      }
   }
}

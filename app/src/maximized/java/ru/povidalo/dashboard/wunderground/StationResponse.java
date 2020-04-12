package ru.povidalo.dashboard.wunderground;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class StationResponse extends BaseResponse {
    public Location location;
    
    public class Location {
        public String              type;
        public String              city;
        public String lat, lon;
    
        @SerializedName("l")
        public String              locationUrl;
        @SerializedName("requesturl")
        public String              requestUrl;
        
        @SerializedName("nearby_weather_stations")
        public Map<String, StationsArray> stations;
    }
    
    public class StationsArray {
        public Station station[];
    }
    
    public class Station {
        public String              city, icao, id, lat, lon;
        @SerializedName("distance_km")
        public Double distanceKm;
    }
}
package ru.povidalo.dashboard.wunderground;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface WundergroundProtocol {
    public static final String API_KEY = "aa4a0958f9e55332";
    
    @GET("geolookup/q/{geo}.json")
    Call<StationResponse> geolookup(@Path("geo") String geo);
    
    @GET("astronomy{location}.json")
    Call<AstronomyResponse> astronomy(@Path(value = "location", encoded = true) String location);
    
    @GET("conditions{location}.json")
    Call<ConditionsResponse> conditions(@Path(value = "location", encoded = true) String location);
    
    @GET("forecast10day{location}.json")
    Call<Forecast10dayResponse> forecast10day(@Path(value = "location", encoded = true) String location);
    
    @GET("hourly10day{location}.json")
    Call<HourlyForecast10dayResponse> hourlyForecast10day(@Path(value = "location", encoded = true) String location);
}

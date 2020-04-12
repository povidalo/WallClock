package ru.povidalo.dashboard.wunderground;

import com.google.gson.annotations.SerializedName;

public class AstronomyResponse extends BaseResponse {
    @SerializedName("moon_phase")
    public MoonPhaseData moonPhaseData;
    @SerializedName("sun_phase")
    public SunPhaseData sunPhaseData;
    
    public class MoonPhaseData {
        @SerializedName("percentIlluminated")
        public String percentIlluminated;
        @SerializedName("ageOfMoon")
        public String ageOfMoon;
        @SerializedName("phaseofMoon")
        public String phaseOfMoon;
        public String hemisphere;
        public TimeData moonrise, moonset, sunrise, sunset;
        @SerializedName("current_time")
        public TimeData currentTime;
    }
    
    public class SunPhaseData {
        public TimeData sunrise, sunset;
    }
    
    public class TimeData {
        public String hour, minute;
    }
}
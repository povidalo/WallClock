package ru.povidalo.dashboard.wunderground;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class BaseResponse {
    public WUResponseData response;
    
    public class WUResponseData {
        public String              version;
        @SerializedName("termsofService")
        public String              termsofService;
        public Map<String, String> features;
    }
}
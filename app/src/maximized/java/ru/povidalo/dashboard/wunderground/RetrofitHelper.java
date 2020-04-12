package ru.povidalo.dashboard.wunderground;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import ru.povidalo.dashboard.BuildConfig;
import ru.povidalo.dashboard.util.Utils;

/**
 * Created by user on 20.10.15.
 */
public class RetrofitHelper {
    private static final Object   lockObject = new Object();
    private static       WundergroundProtocol service   = null;
    
    private RetrofitHelper() {
    
    }
    
    public static WundergroundProtocol service() {
        if (service == null) {
            synchronized (lockObject) {
                if (service == null) {
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .writeTimeout(60, TimeUnit.SECONDS)
                            .addInterceptor(new Interceptor() {
                                @Override
                                public Response intercept(Chain chain) throws IOException {
                                    
                                    Request original = chain.request();
                                    Request request = original.newBuilder()
                                            .header("User-Agent", System.getProperty("http.agent"))
                                            .build();
                                    
                                    Buffer buffer  = new Buffer();
                                    if (request.body() != null) {
                                        request.body().writeTo(buffer);
                                    }
                                    Utils.log("Request " + request.method() + " to " + request.url() + "\n" + buffer.readUtf8());
                                    
                                    long t1 = System.nanoTime();
                                    Response response = chain.proceed(request);
                                    long t2 = System.nanoTime();
                                    
                                    Utils.log(String.format("Response %s from %s in %.1fms",
                                            response.code(), response.request().url(), (t2 - t1) / 1e6d));
                                    
                                    if (BuildConfig.DEBUG && Utils.LOGS_ENABLED) {
                                        String data = response.peekBody(1024000).string();
                                        try {
                                            JSONObject object = new JSONObject(data);
                                            String formatted = object.toString(4);
                                            if (formatted.split("\n").length < 100) {
                                                data = formatted;
                                            }
                                        } catch (Exception e) {
                                        }
                                        Utils.log("Data: " + data);
                                    }
                                    
                                    return response;
                                    /*
                                    return response.newBuilder()
                                            .body(ResponseBody.create(response.body().contentType(), msg))
                                            .build();
                                            */
                                }
                            }).build();
                    
                    String url = "http://api.wunderground.com/api/"+WundergroundProtocol.API_KEY+"/";
                    
                    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(url)
                            .addConverterFactory(GsonConverterFactory.create())
                            .client(client)
                            .build();
                    
                    service = retrofit.create(WundergroundProtocol.class);
                }
            }
        }
        return service;
    }
}

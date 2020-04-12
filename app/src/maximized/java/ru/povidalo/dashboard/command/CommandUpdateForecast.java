package ru.povidalo.dashboard.command;

import com.google.gson.JsonParseException;
import com.google.gson.stream.MalformedJsonException;

import retrofit2.Call;
import retrofit2.Response;
import ru.povidalo.dashboard.Dashboard;
import ru.povidalo.dashboard.util.Utils;
import ru.povidalo.dashboard.wunderground.RetrofitHelper;
import ru.povidalo.dashboard.wunderground.StationResponse;

public class CommandUpdateForecast extends Command {
    public CommandUpdateForecast(ICommand listener) {
        super(listener);
    }
    
    @Override
    protected void doInBackground() {
        ProtocolError error = new ProtocolError();
        if (!Dashboard.isOnline()) {
            error.type = ProtocolError.Type.NETWORK_ERROR;
            error.protocolMessage = error.type.name();
            if (listener != null) {
                listener.onCommandError(this, error);
            }
            failed();
            return;
        }
        
        Response<StationResponse> response = null;
        Call<StationResponse>                   call     = null;
        try {
            call = RetrofitHelper.service().geolookup("56.762471,37.152253");
        } catch (Exception e) {
            Utils.logError(e);
        }
    
        if (call == null) {
            error.type = ProtocolError.Type.DATA_ERROR;
            if (listener != null) {
                listener.onCommandError(this, error);
            }
            failed();
            return;
        }
    
        StationResponse station = null;
        try {
            response = call.execute();
        
            error.httpCode = response.code();
            error.httpMessage = response.message();
            station = response.body();
        } catch (JsonParseException |MalformedJsonException e) {
            Utils.logError(e);
            error.type = ProtocolError.Type.SERVER_ERROR;
            if (listener != null) {
                listener.onCommandError(this, error);
            }
            failed();
            return;
        } catch (Exception e) {
            Utils.logError(e);
        }
        
        if (station != null && station.location != null) {
            
            try {
                RetrofitHelper.service().astronomy(station.location.locationUrl).execute();
                RetrofitHelper.service().conditions(station.location.locationUrl).execute();
                RetrofitHelper.service().forecast10day(station.location.locationUrl).execute();
                RetrofitHelper.service().hourlyForecast10day(station.location.locationUrl).execute();
            } catch (Exception e) {
                Utils.logError(e);
            }
        }
    }
}
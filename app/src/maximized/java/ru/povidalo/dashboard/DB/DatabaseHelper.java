package ru.povidalo.dashboard.DB;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.j256.ormlite.android.apptools.OrmLiteSqliteOpenHelper;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;

import org.json.JSONArray;
import org.json.JSONException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ru.povidalo.dashboard.Dashboard;
import ru.povidalo.dashboard.R;
import ru.povidalo.dashboard.util.Utils;

public class DatabaseHelper extends OrmLiteSqliteOpenHelper {
    private static final Object                                      daoCacheLockObject = new Object();
    private static final Map<Class<DBEntity>, Dao<DBEntity, Object>> daoCache           = new HashMap<Class<DBEntity>, Dao<DBEntity, Object>>();
    
    private static Object         lockObject           = new Object();
    private static DatabaseHelper instance             = null;
    
    private static final List<Class<? extends DBEntity>> dbClasses;
    
    static {
        List<Class<? extends DBEntity>> entityClasses = new ArrayList<>();
        entityClasses.add(ImageEntry.class);
        entityClasses.add(IndexEntry.class);
        
        dbClasses = Collections.unmodifiableList(entityClasses);
    }
    
    public static DatabaseHelper getInstance() {
        if (instance == null) {
            synchronized (lockObject) {
                if (instance == null) {
                    instance = new DatabaseHelper(Dashboard.getContext());
                    instance.getWritableDatabase();
                }
            }
        }
        
        return instance;
    }

        public static void loadAllData() {
        getInstance();
        Utils.log("LoadAllData from DH");
        for (Class<? extends DBEntity> c : dbClasses) {
            Utils.log("LoadAllData from DH for "+c);
            try {
                DBEntity e = c.newInstance();
                e.loadAllData();
            } catch (IllegalAccessException e) {
                Utils.logError(e);
            } catch (InstantiationException e) {
                Utils.logError(e);
            } catch (SQLException e) {
                Utils.logError(e);
            }
        }
        Utils.log("LoadAllData from DH done");
    }
    
    public static <T extends DBEntity> Dao<T, Object> getCachedDao(Class<T> classs) {
        Utils.checkIfLooper();
        Dao<T, Object> dao = null;
        if (daoCache.containsKey(classs)) {
            dao = (Dao<T, Object>) daoCache.get(classs);
        }
        if (dao == null) {
            synchronized (daoCacheLockObject) {
                try {
                    dao = DaoManager.createDao(getInstance().getConnectionSource(), classs);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                if (dao != null) {
                    daoCache.put((Class<DBEntity>) classs,  (Dao<DBEntity, Object>) dao);
                }
            }
        }
        return dao;
    }
    
    public DatabaseHelper(Context context) {
        super(context, context.getResources().getString(R.string.db_name), null, Dashboard.getVersionCode());
    }
    
    @Override
    public void onCreate(SQLiteDatabase db, ConnectionSource connectionSource) {
        try {
            Utils.log("DatabaseHelper onCreate");
            for (Class<? extends DBEntity> c : dbClasses) {
                TableUtils.createTable(connectionSource, c);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Couldn't create tables", e);
        }
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, ConnectionSource connectionSource, int oldVersion, int newVersion) {
        /*try {
            
            if(oldVersion <= 25269000) {
                TableUtils.createTableIfNotExists(connectionSource, RestaurantFeatureType.class);
            }
            if(oldVersion <= 25269000) {
                TableUtils.createTableIfNotExists(connectionSource, SVGIcon.class);
            }
            if(oldVersion <= 25269005) {
                TableUtils.dropTable(connectionSource, UserPreferencesType.class, true);
                TableUtils.createTableIfNotExists(connectionSource, UserPreferencesType.class);
                TableUtils.createTableIfNotExists(connectionSource, RecentPlace.class);
                TableUtils.createTableIfNotExists(connectionSource, SetType.class);
            }
            if(oldVersion <= 25269007) {
                TableUtils.createTableIfNotExists(connectionSource, RecentRestaurant.class);
            } else if(oldVersion <= 25269009) {
                TableUtils.dropTable(connectionSource, RecentRestaurant.class, true);
                TableUtils.createTableIfNotExists(connectionSource, RecentRestaurant.class);
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Error on DB upgrade", e);
        }*/
        
        synchronized (daoCacheLockObject) {
            daoCache.clear();
        }
    }
    
    public static String serializeStrings(Collection<String> c) {
        JSONArray jsonArray = new JSONArray();
        if (c != null && c.size() > 0) {
            for (String s : c) {
                if (s != null && s.length() > 0) {
                    jsonArray.put(s);
                }
            }
        }
        return jsonArray.toString();
    }
    
    public static Collection<String> deserializeStrings(String s) {
        Collection<String> res = new ArrayList<String>();
        if (s != null && s.length() > 0) {
            try {
                JSONArray jsonArray = new JSONArray(s);
                for (int i = 0; i < jsonArray.length(); i++) {
                    String jsonString = jsonArray.optString(i);
                    if (jsonString != null && jsonString.length() > 0) {
                        res.add(jsonString);
                    }
                }
            } catch (JSONException e) {
                Utils.logError(e);
            }
        }
        
        return res;
    }
    
    public static String serializeIntegers(Collection<Integer> c) {
        JSONArray jsonArray = new JSONArray();
        if (c != null && c.size() > 0) {
            for (Integer i : c) {
                if (i != null) {
                    jsonArray.put(i);
                }
            }
        }
        return jsonArray.toString();
    }
    
    public static String serializeLongs(Collection<Long> c) {
        JSONArray jsonArray = new JSONArray();
        if (c != null && c.size() > 0) {
            for (Long i : c) {
                if (i != null) {
                    jsonArray.put(i);
                }
            }
        }
        return jsonArray.toString();
    }
    
    public static Collection<Integer> deserializeIntegers(String s) {
        Collection<Integer> res = new ArrayList<Integer>();
        if (s != null && s.length() > 0) {
            try {
                JSONArray jsonArray = new JSONArray(s);
                for (int i = 0; i < jsonArray.length(); i++) {
                    res.add(jsonArray.optInt(i));
                }
            } catch (JSONException e) {
                Utils.logError(e);
            }
        }
        
        return res;
    }
    
    public static Collection<Long> deserializeLongs(String s) {
        Collection<Long> res = new ArrayList<Long>();
        if (s != null && s.length() > 0) {
            try {
                JSONArray jsonArray = new JSONArray(s);
                for (int i = 0; i < jsonArray.length(); i++) {
                    res.add(jsonArray.optLong(i));
                }
            } catch (JSONException e) {
                Utils.logError(e);
            }
        }
        
        return res;
    }
}
package ru.povidalo.dashboard.DB;

import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import ru.povidalo.dashboard.util.Utils;

/**
 * Created by povidalo on 29.06.18.
 */

public abstract class DBEntityCached<IdType, Entity extends DBEntityCached> extends DBEntity {
    private final Class<Entity> clazz;

    public DBEntityCached(Class<Entity> clazz) {
        this.clazz = clazz;
    }

    protected abstract Map<IdType, Entity> getCache();
    protected abstract boolean isCacheInitialized();
    protected abstract void cacheInitialized();
    protected abstract IdType getCachedId();

    protected Entity getById_Cached(IdType id) {
        checkData();
        synchronized (getCache()) {
            return getCache().get(id);
        }
    }

    protected Collection<Entity> getAll_Cached() {
        checkData();
        Collection<Entity> res = new HashSet<>();
        synchronized (getCache()) {
            res.addAll(getCache().values());
        }
        return res;
    }

    @Override
    void loadAllData() throws SQLException {
        if (!isCacheInitialized()) {
            Utils.log("LoadAllData for "+getClass());
            synchronized (getCache()) {
                getCache().clear();
            }
            List<Entity> entities = DatabaseHelper.getCachedDao(clazz).queryForAll();
            ensureInCache(entities);
            cacheInitialized();
            Utils.log("LoadAllData for "+getClass()+" done");
        }
    }

    protected void checkData() {
        if (!isCacheInitialized()) {
            try {
                loadAllData();
            } catch (SQLException ex){
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public <T extends DBEntity> void delete() {
        super.delete();
        removeFromCache(this);
    }

    @Override
    public <T extends DBEntity> void save() {
        super.save();
        ensureInCache(this);
    }

    protected void saveBatch_Cached(final Collection<Entity> batch) {
        if (batch == null || batch.size() <= 0) {
            return;
        }

        ensureInCache(batch);
        saveMany(batch);
    }

    protected void removeFromCache(DBEntityCached entity) {
        if (entity != null && entity.getCachedId() != null) {
            synchronized (getCache()) {
                if (getCache().containsKey(entity.getCachedId())) {
                    getCache().remove(entity.getCachedId());
                }
            }
        } else {
            Utils.logError(getClass()+" with invalid ids cannot be deleted from cache");
        }
    }

    protected void ensureInCache(Collection<Entity> entities) {
        for (Entity e : entities) {
            ensureInCache(e);
        }
    }

    protected void ensureInCache(DBEntityCached entity) {
        if (entity != null && entity.getCachedId() != null) {
            synchronized (getCache()) {
                getCache().put((IdType) entity.getCachedId(), (Entity) entity);
            }
        } else {
            Utils.logError(getClass()+" with invalid ids cannot be added to cache");
        }
    }
}

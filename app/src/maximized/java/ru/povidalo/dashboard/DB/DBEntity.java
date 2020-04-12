package ru.povidalo.dashboard.DB;

import android.os.SystemClock;

import com.j256.ormlite.dao.Dao;

import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

import ru.povidalo.dashboard.util.ApplicationExceptionHandler;
import ru.povidalo.dashboard.util.Utils;

public abstract class DBEntity {
    protected Object oldId = null;
    private static final Object DBWriterLockObject = new Object();
    private static AsyncDBWriter DBWriter = null;

    public <T extends DBEntity> void save() {
        if (checkThread()) {
            DBWriter.save(this);
        }
    }
    
    public <T extends DBEntity> boolean saveSync() {
        if (checkThread()) {
            try {
                AsyncDBWriter.saveEntity(this);
                return true;
            } catch (SQLException e) {
                Utils.logError(e);
            }
        }
        return false;
    }

    abstract void loadAllData() throws SQLException;

    private static boolean checkThread() {
        synchronized (DBWriterLockObject) {
            if (DBWriter == null) {
                DBWriter = new AsyncDBWriter();
                DBWriter.start();
            } else if (DBWriter.isInterrupted()) {
                DBWriter = new AsyncDBWriter();
                DBWriter.start();
            }
        }
        return true;
    }

    protected void checkId(Object currId, Object newId) {
        if (oldId == null && currId != null && !currId.equals(newId)) {
            oldId = newId;
        }
    }

    public <T extends DBEntity> void delete() {
        if (checkThread()) {
            DBWriter.delete(this);
        }
        oldId = null;
    }
    
    public <T extends DBEntity> boolean deleteSync() {
        if (checkThread()) {
            try {
                AsyncDBWriter.deleteEntity(this);
                return true;
            } catch (SQLException e) {
                Utils.logError(e);
            }
        }
        return false;
    }

    static void saveMany(Collection<? extends DBEntity> batch) {
        if (batch != null && batch.size() > 0) {
            if (checkThread()) {
                DBWriter.saveBatch(batch);
            }
        }
    }

    @SuppressWarnings("unchecked")
    <T extends DBEntity> Dao<T, Object> getDao() {
        return DatabaseHelper.getCachedDao((Class<T>) getClass());
    }

    private static class AsyncDBWriter extends Thread {
        private final ConcurrentLinkedQueue<Action> actionsQueue = new ConcurrentLinkedQueue<>();

        private static class Action {
            private final DBEntity entity;
            private final boolean  forSave;
            private final boolean  forDelete;

            private Action(DBEntity entity, boolean forSave, boolean forDelete) {
                this.entity = entity;
                this.forSave = forSave;
                this.forDelete = forDelete;
            }

            public static Action save(DBEntity entity) {
                return new Action(entity, true, false);
            }

            public static Action delete(DBEntity entity) {
                return new Action(entity, false, true);
            }

            public DBEntity getEntity() {
                return entity;
            }

            public boolean isForDelete() {
                return forDelete;
            }

            public boolean isForSave() {
                return forSave;
            }
        }

        public AsyncDBWriter() {
            setPriority(Utils.DEFAULT_BG_THREAD_PRIORITY);
            setName(getClass().getSimpleName());
            setUncaughtExceptionHandler(new ApplicationExceptionHandler());
        }

        public void delete(DBEntity e) {
            synchronized (actionsQueue) {
                actionsQueue.add(Action.delete(e));
            }
            synchronized (this) {
                notify();
            }
        }

        public void save(DBEntity e) {
            synchronized (actionsQueue) {
                actionsQueue.add(Action.save(e));
            }
            synchronized (this) {
                notify();
            }
        }

        public void saveBatch(Collection<? extends DBEntity> batch) {
            synchronized (actionsQueue) {
                for (DBEntity e : batch) {
                    actionsQueue.add(Action.save(e));
                }
            }
            synchronized (this) {
                notify();
            }
        }

        private static void saveEntity(DBEntity e) throws SQLException {
            if (e.oldId != null) {
                DBEntity oldE = e.getDao().queryForId(e.oldId);
                if (oldE != null) {
                    oldE.delete();
                }
            }
            e.getDao().createOrUpdate(e);
            e.oldId = null;
        }

        private static void deleteEntity(DBEntity e) throws SQLException {
            e.getDao().delete(e);
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                final Set<DBEntity> entities = new HashSet<>();
                final Action        first;
                synchronized (actionsQueue) {
                    first = actionsQueue.poll();
                    if (first != null) {
                        entities.add(first.getEntity());
                        while (!actionsQueue.isEmpty()) {
                            Action next = actionsQueue.peek();
                            if (next != null && next.isForDelete() == first.isForDelete() && next.isForSave() == first.isForSave() && next.getEntity().getClass().equals(first.getEntity().getClass())) {
                                actionsQueue.poll();
                                entities.add(next.getEntity());
                            } else {
                                break;
                            }
                        }
                    }
                }

                if (entities.size() > 0) {
                    Utils.log("DBWriter processing "+first.getEntity().getClass());
                    try {
                        if (entities.size() > 1) {
                            DatabaseHelper.getCachedDao(first.getEntity().getClass()).callBatchTasks(new Callable<Object>() {
                                @Override
                                public Object call() throws Exception {
                                    for (Iterator<DBEntity> i = entities.iterator(); i.hasNext(); ) {
                                        DBEntity e = i.next();
                                        if (first.isForSave()) {
                                            saveEntity(e);
                                        } else if (first.isForDelete()) {
                                            deleteEntity(e);
                                        }
                                        i.remove();
                                    }
                                    return null;
                                }
                            });
                        } else {
                            DBEntity e = entities.iterator().next();
                            if (first.isForSave()) {
                                saveEntity(e);
                            } else if (first.isForDelete()) {
                                deleteEntity(e);
                            }
                        }
                    } catch (Exception e) {
                        /*if (first.getEntity().getClass().equals(Account.class)) {
                            //this is crytical, as we store auth data in Account table
                            throw new RuntimeException(e);
                        } else */{
                            //recycle unprocessed entities
                            if (entities.size() > 0) {
                                synchronized (actionsQueue) {
                                    for (DBEntity failedEntity : entities) {
                                        if (first.isForSave()) {
                                            save(failedEntity);
                                        } else if (first.isForDelete()) {
                                            delete(failedEntity);
                                        }
                                    }
                                }
                            }

                            //try cleanup

                            Utils.logError(e);
                        }
                    }
                } else {
                    try {
                        synchronized (this) {
                            wait();
                        }
                        long time;
                        do {
                            time = SystemClock.uptimeMillis();
                            synchronized (this) {
                                wait(300);
                            }
                        } while (SystemClock.uptimeMillis() - time < 290);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }
    }
}


package ru.povidalo.dashboard.DB;

import com.j256.ormlite.dao.CloseableIterator;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.stmt.PreparedQuery;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.Where;
import com.j256.ormlite.table.DatabaseTable;

import java.io.File;
import java.sql.SQLException;
import java.util.Collection;
import java.util.concurrent.Callable;

import ru.povidalo.dashboard.util.Utils;

@DatabaseTable(tableName = "image_entry")
public class ImageEntry extends DBEntity {

    public enum FileState {
        NOT_DOWNLOADED, INVALID, SAVED, FILTERED, GOOD
    }
    
    @DatabaseField(id = true, columnName = "fileName")
    private String fileName = "";
    @DatabaseField(columnName = "baseUrl")
    private String baseUrl     = "";
    @DatabaseField(columnName = "localBasePath")
    private String localBasePath  = "";
    @DatabaseField(columnName = "size")
    private String size     = "";
    @DatabaseField(columnName = "instrument")
    private String instrument     = "";
    @DatabaseField(columnName = "localName")
    private String localName     = "";
    @DatabaseField(columnName = "timestamp")
    private long timestamp     = 0;
    @DatabaseField(columnName = "state")
    private FileState state     = FileState.NOT_DOWNLOADED;
    
    public ImageEntry() {
    
    }
    
    public String getFileName() {
        return fileName;
    }
    
    @Override
    void loadAllData() throws SQLException {
    
    }
    
    public void setFileName(String fileName) {
        checkId(this.fileName, fileName);
        this.fileName = fileName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl == null || !baseUrl.equals(this.baseUrl)) {
            this.baseUrl = baseUrl;
        }
    }

    public String getLocalBasePath() {
        return localBasePath;
    }

    public void setLocalBasePath(String localBasePath) {
        if (localBasePath == null || !localBasePath.equals(this.localBasePath)) {
            this.localBasePath = localBasePath;
        }
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        if (size == null || !size.equals(this.size)) {
            this.size = size;
        }
    }

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String instrument) {
        if (instrument == null || !instrument.equals(this.instrument)) {
            this.instrument = instrument;
        }
    }

    public String getLocalName() {
        return localName;
    }

    public void setLocalName(String localName) {
        if (localName == null || !localName.equals(this.localName)) {
            this.localName = localName;
        }
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        if (this.timestamp != timestamp) {
            this.timestamp = timestamp;
        }
    }

    public FileState getState() {
        return state;
    }

    public void setState(FileState state) {
        if (this.state != state) {
            this.state = state;
        }
    }
    
    public File getFile() {
        File baseDir = new File(getLocalBasePath());
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return new File(baseDir, getLocalName());
    }
    
    public void rename(String newName) {
        if (newName != null && !newName.equals(getLocalName())) {
            File imgFile = getFile();
            if (imgFile.isFile()) {
                File newFile = new File(imgFile.getParentFile(), newName);
                if (newFile.isFile()) {
                    newFile.delete();
                }
                imgFile.renameTo(newFile);
            }
            setLocalName(newName);
            saveSync();
        }
    }
    
    @Override
    public <T extends DBEntity> void delete() {
        File imgFile = getFile();
        if (imgFile.isFile()) {
            imgFile.delete();
        }
        super.delete();
    }
    
    @Override
    public <T extends DBEntity> boolean deleteSync() {
        File imgFile = getFile();
        if (imgFile.isFile()) {
            imgFile.delete();
        }
        return super.deleteSync();
    }
    
    public long deleteAndGetFileSize() {
        long size = 0;
        File imgFile = getFile();
        if (imgFile.isFile()) {
            size = Utils.getFileSize(imgFile);
            if (!imgFile.delete()) {
                size = 0;
            }
            if (imgFile.exists()) {
                size = 0;
            }
        }
        super.deleteSync();
        return size;
    }
    
    public static void saveBatch(final Collection<ImageEntry> batch) {
        if (batch == null || batch.size() <= 0) {
            return;
        }

        try {
            DatabaseHelper.getCachedDao(ImageEntry.class).callBatchTasks(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    for (ImageEntry i : batch) {
                        i.saveSync();
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static ImageEntry getByFileName(String fileName) {
        try {
            return DatabaseHelper.getCachedDao(ImageEntry.class).queryForId(fileName);
        } catch (SQLException e) {
            Utils.logError(e);
        }
        return null;
    }
    
    private static PreparedQuery<ImageEntry> prepareQuery(boolean count, String instrument, String size, boolean ascending, Long startTimestamp, FileState... state) throws SQLException {
        QueryBuilder<ImageEntry, Object> query = DatabaseHelper.getCachedDao(ImageEntry.class).queryBuilder();
        if (count) {
            query = query.setCountOf(true);
        }
        Where<ImageEntry, Object> where = query.where();
        if (startTimestamp != null) {
            if (ascending) {
                where = where.gt("timestamp", startTimestamp);
            } else {
                where = where.lt("timestamp", startTimestamp);
            }
        }
        if (state == null && instrument == null && size == null) {
        
        } else {
            if (startTimestamp != null) {
                where = where.and();
            }
            if (state != null && state.length > 0) {
                where = where.in("state", state);
                if (instrument != null || size != null) {
                    where = where.and();
                }
            }
            if (instrument != null) {
                where = where.eq("instrument", instrument);
                if (size != null) {
                    where = where.and();
                }
            }
            if (size != null) {
                where = where.eq("size", size);
            }
        }
        query = query.orderBy("timestamp", ascending);
        return query.prepare();
    }
    
    public static CloseableIterator<ImageEntry> getAllOlderThan(String instrument, String size, Long startTimestamp, FileState... state) {
        return getAll(instrument, size, false, startTimestamp, state);
    }
    
    public static CloseableIterator<ImageEntry> getAllOldestFirst(String instrument, String size, FileState... state) {
        return getAll(instrument, size, true, null, state);
    }

    private static CloseableIterator<ImageEntry> getAll(String instrument, String size, boolean ascending, Long startTimestamp, FileState... state) {
        CloseableIterator<ImageEntry> res = null;
    
        try {
            PreparedQuery<ImageEntry> query = prepareQuery(false, instrument, size, ascending, startTimestamp, state);
        
            res = DatabaseHelper.getCachedDao(ImageEntry.class).iterator(query);
        } catch (SQLException e) {
            Utils.logError(e);
        }
        
        return res;
    }

    public static Long countAll(String instrument, String size, FileState... state) {
        try {
            return DatabaseHelper.getCachedDao(ImageEntry.class).countOf(prepareQuery(true, instrument, size, false, null, state));
        } catch (SQLException e) {
            Utils.logError(e);
        }
        return null;
    }
}

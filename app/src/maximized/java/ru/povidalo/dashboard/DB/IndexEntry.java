package ru.povidalo.dashboard.DB;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@DatabaseTable(tableName = "index_entry")
public class IndexEntry extends DBEntityCached<String, IndexEntry> {
    private static final Map<String, IndexEntry> cache = new HashMap<String, IndexEntry>();
    private static boolean cacheInitialized = false;

    public enum IndexState {
        NOT_PARSED, INCOMPLETE, COMPLETE
    }

    @DatabaseField(id = true, columnName = "date")
    private String date = "";
    @DatabaseField(columnName = "state")
    private IndexState state = IndexState.NOT_PARSED;

    public IndexEntry() {
        super(IndexEntry.class);
    }

    public String getDate() {
        return date;
    }

    @Override
    protected String getCachedId() {
        return getDate();
    }

    @Override
    protected boolean isCacheInitialized() {
        return cacheInitialized;
    }

    @Override
    protected Map<String, IndexEntry> getCache() {
        return cache;
    }

    @Override
    protected void cacheInitialized() {
        cacheInitialized = true;
    }

    public void setDate(String date) {
        checkId(this.date, date);
        this.date = date;
    }

    public IndexState getState() {
        return state;
    }

    public void setState(IndexState state) {
        this.state = state;
    }

    public static Map<String, IndexEntry> getAll() {
        Collection<IndexEntry> res = new IndexEntry().getAll_Cached();

        Map<String, IndexEntry> map = new HashMap<>();
        for (IndexEntry i : res) {
            map.put(i.getDate(), i);
        }

        return map;
    }
}

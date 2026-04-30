package com.kvstore.store;

import java.util.LinkedHashMap;
import java.util.Map;
public class LRUCache {
    private final int capacity;
    private final LinkedHashMap<String, String>cache;

    public LRUCache(int capacity){
        this.capacity = capacity;
        // accessOrder = true means get() moves the entry to the most recent 
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true){
            @Override
            protected boolean removeEldestEntry(Map.Entry<String,String>eldest){
                return size() > capacity; // auto evict when over capacity 
            }
        };
    }
    public synchronized void put(String key, String value){cache.put(key,value);}
    public synchronized String get(String key){return cache.getOrDefault(key, null);}
}

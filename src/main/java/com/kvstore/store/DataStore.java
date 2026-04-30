package com.kvstore.store;

import java.util.concurrent.ConcurrentHashMap;

public class DataStore {
    private static final ConcurrentHashMap<String ,String> store = new ConcurrentHashMap<>();

    private DataStore() {
    }

    public static String set(String key, String value){
        store.put(key,value);
        return "+OK";
    }

    public static String get(String key){
        String val = store.get(key);
        return val != null ? "+" + val : "$-1" ; // $-1 means nill in RESP
    }

    public static String del(String key){
        store.remove(key);
        return ":1"; //RESP integer reply
    }
}

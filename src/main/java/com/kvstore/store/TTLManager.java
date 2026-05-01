package com.kvstore.store;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

public class TTLManager {
    private static final PriorityQueue<long[]> heap = 
    new PriorityQueue<>(Comparator.comparingLong(a->a[0]));

    private TTLManager(){}

    private static final ConcurrentHashMap<String, Long> expiryMap = new ConcurrentHashMap<>();

    public static void setExpiry(String key, long ttlSeconds){
        long expiryTime = System.currentTimeMillis() + ttlSeconds * 1000;
        expiryMap.put(key, expiryTime);
        heap.offer(new long[]{expiryTime, key.hashCode()});
    }

    public static boolean isExpired(String key){
        Long expiry = expiryMap.get(key);
        if(expiry==null) return false;
        if(System.currentTimeMillis() > expiry){
            expiryMap.remove(key);
            DataStore.del(key); // lazy delete
            return true;
        }
        return false;
    }

    // background thread activity purge expired keys
    public static void startCleanup(){
        Thread t = new Thread(() -> {
    while(true){
        try{
            Thread.sleep(1000);
            long now = System.currentTimeMillis();
            while(!heap.isEmpty() && heap.peek()[0] <= now){
                heap.poll();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        }
    }
        }, "ttl-cleanup");

        t.setDaemon(true);  
        t.start();
    }
}

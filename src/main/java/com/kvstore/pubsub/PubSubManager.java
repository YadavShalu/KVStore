package com.kvstore.pubsub;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.io.PrintWriter;
import java.util.List;

public class PubSubManager {

    private PubSubManager() {
    }

    private static final ConcurrentHashMap<String, List<PrintWriter>> subscribers = 
        new ConcurrentHashMap<>();

        public static void subscribe(String channel, PrintWriter writer){
            subscribers.computeIfAbsent(channel, k->new CopyOnWriteArrayList<>()).add(writer);
        }

        public static int publish(String channel, String message){
            List<PrintWriter> subs = subscribers.getOrDefault(channel, List.of());
            for(PrintWriter w: subs){
                w.println("*3\r\n$7\r\nmessage\r\n$" + channel.length() + "\r\n" + channel
                            + "\r\n$" + message.length() + "\r\n" + message);
            }
            return subs.size();
        }
    
}

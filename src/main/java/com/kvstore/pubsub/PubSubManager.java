package com.kvstore.pubsub;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.io.PrintWriter;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;



public class PubSubManager {
    private static final Logger LOGGER = Logger.getLogger(PubSubManager.class.getName());
    private PubSubManager() {
    }

    private static final ConcurrentHashMap<String, List<PrintWriter>> subscribers = 
        new ConcurrentHashMap<>();

        public static void subscribe(String channel, PrintWriter writer){
            subscribers.computeIfAbsent(channel, k->new CopyOnWriteArrayList<>()).add(writer);
            LOGGER.log(Level.INFO, "Subscribed to: {0}", channel);
        }

        public static int publish(String channel, String message){
            List<PrintWriter> subs = subscribers.getOrDefault(channel, List.of());
            for(PrintWriter w: subs){
                w.print("*3\r\n");
                w.print("$7\r\nmessage\r\n");
                w.print("$" + channel.length() + "\r\n" + channel + "\r\n");
                w.print("$" + message.length() + "\r\n" + message + "\r\n");
                w.flush(); 
            }
            LOGGER.log(Level.INFO, () -> "Publishing to: " + channel + " message: " + message);
            LOGGER.log(Level.INFO,"Subscribers count: {0}",subs.size());
            return subs.size();
        }
    
}

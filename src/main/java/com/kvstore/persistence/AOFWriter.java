package com.kvstore.persistence;

import java.io.IOException;

import com.kvstore.store.DataStore;

import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.logging.Level;
import java.util.logging.Logger;

// Append Only File 
public class AOFWriter {
    private static final String FILE = "appendonly.aof";
    private static final Logger LOGGER = Logger.getLogger(AOFWriter.class.getName());
    private static volatile boolean replaying = false;
    private AOFWriter(){}

    public static synchronized void log(String command){
        if (replaying) return; 

        try(FileWriter fw = new FileWriter(FILE, true)){ // true = append mode
            fw.write(command + "\n");
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public static void replay() throws IOException{
        LOGGER.info("Replaying AOF...");
        File f = new File(FILE);
        if(!f.exists())return;
        replaying = true; 
        try(BufferedReader br = new BufferedReader(new FileReader(f))){
            String line;
            while((line = br.readLine())!= null){
                LOGGER.log(Level.INFO, "Line: {0}", line);
                String[] parts = line.split(" ");
                // Re-execute each command silently on startup

                if(parts[0].equals("SET")) DataStore.set(parts[1],parts[2]);
                if (parts[0].equals("DEL"))  DataStore.del(parts[1]);
            }
        }
        replaying = false;

    }
}

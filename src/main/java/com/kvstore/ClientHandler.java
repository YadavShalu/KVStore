package com.kvstore;

import java.net.Socket;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import com.kvstore.store.DataStore;

public class ClientHandler implements Runnable {
    private final Socket socket ;

    public ClientHandler(Socket socket){
        this.socket = socket;
    }

    @Override
    public void run(){
        try(BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(),true);
            String line;

            while((line = in.readLine()) != null){
                if(line.startsWith("*")){
                    int argCount = Integer.parseInt(line.substring(1));
                    String[] args = new String[argCount];
                    for(int i=0; i<argCount ; i++){
                        in.readLine();
                        args[i] = in.readLine();
                    }
                    String response = dispatch(args);
                    out.println(response);
                }
            }
        }
        catch(IOException e){
            e.printStackTrace();
        }
            
    }

    private String dispatch(String[] args){
        return switch(args[0].toUpperCase()){
            case "SET" -> DataStore.set(args[1],args[2]);
            case "GET" -> DataStore.get(args[1]);
            case "DEL" -> DataStore.del(args[1]);
            case "PING" -> "+PONG";
            default     -> "-ERR Unknown command";
        };
    }
    
}

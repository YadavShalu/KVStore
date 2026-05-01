package com.kvstore;

import java.net.Socket;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import com.kvstore.pubsub.PubSubManager;
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
                        String lenLine = in.readLine();
                        args[i] = in.readLine();
                    }
                    String response = dispatch(args,out);
                    if (response != null) {
                        out.print(response + "\r\n");
                        out.flush();
                    }
                }
            }
        }
        catch(IOException e){
            e.printStackTrace();
        }
            
    }

    private String dispatch(String[] args, PrintWriter out){
        return switch(args[0].toUpperCase()){
            case "SET" -> DataStore.set(args[1],args[2]);
            case "GET" -> DataStore.get(args[1]);
            case "DEL" -> DataStore.del(args[1]);
            case "PING" -> "+PONG";
            case "SUBSCRIBE" -> {
                PubSubManager.subscribe(args[1], out);
                yield null; 
            }
            case "PUBLISH" -> {
                    int count = PubSubManager.publish(args[1], args[2]);
                    yield ":" + count; // RESP integer reply
                }
            default     -> "-ERR Unknown command";
        };
    }
    
}

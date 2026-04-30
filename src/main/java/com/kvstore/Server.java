package com.kvstore;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Server {
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private static volatile boolean running = true;

    public static void main(String[] args) throws Exception {
        int port = args.length>0 ? Integer.parseInt(args[0]) : 6380;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            logger.info("Shutting down server...");
        }));
        try(ServerSocket serverSocket = new ServerSocket(port)){
            logger.log(Level.INFO, "KVStore running on port {0}", port);

            while (running) {
                Socket client = serverSocket.accept();
                logger.info("Client connected: "
                    + client.getInetAddress()
                    + ":" + client.getPort());
                client.close();
            }
        } catch(java.net.BindException  e){
            logger.severe("ERROR: Port "+ port +" is already in use.");
            logger.severe("Either kill the process using it, or run with a different port:");
            logger.severe("  mvn exec:java -Dexec.args=\"6381\"");
        } catch(IOException io){
            logger.severe("Server error: "+io.getMessage());
        }

    }
}
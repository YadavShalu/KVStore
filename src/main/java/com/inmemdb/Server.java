package com.inmemdb;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    public static void main(String[] args) throws IOException {
        int port = 6380;
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("InMemDB running on port " + port);

        while (true) {
            Socket client = serverSocket.accept();
            System.out.println("Client connected: " + client.getInetAddress());
            client.close();
        }
    }
}
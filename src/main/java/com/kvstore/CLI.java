package com.kvstore;

import java.io.*;
import java.net.Socket;
import java.net.ConnectException;
import java.util.Scanner;

public class CLI {
    private static final String versionString = "1.0.0";
    private static String host = "localhost";
    private static int port = 6380;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected = false;

    public static void main(String[] args){

        for(int i=0 ; i<args.length ; i++){
            switch(args[i]){
                case "-h" -> host = args[++i];
                case "-p" -> port = Integer.parseInt(args[++i]);
                case "--help" -> {printHelp(); return;}
                case "--version" -> {printVersion(); return;} 
            }
        }
        new CLI().start();
    }

    public void start(){
        printBanner();
        tryConnect();

        Scanner scanner = new Scanner(System.in);

        while(true){
            if(connected){
                System.out.print(host + ":"+ port + "->");
            }
            else{
                System.out.print("[disconnected] >");
            }
            if(!scanner.hasNextLine()) break;
            String input = scanner.nextLine().trim();

            if(input.isEmpty())continue;

            switch(input.toLowerCase()){
                case "exit", "quit" -> {
                    System.out.println("Bye!");
                    disconnect();
                    return ;
                }
                case "clear" -> {
                    clearScreen();
                    continue;
                }
                case "help" ->{
                    printHelp();
                    continue;
                }
                case "version" -> {
                    printVersion();
                    continue;
                }
                case "reconnect" ->{
                    disconnect();
                    tryConnect();
                    continue;
                }
                default ->{}
            }

        if(!connected){
            System.out.println("Not connected. Type 'reconnect' to try again");
            continue;
        }

        String[] tokens = parseInput(input);
        if(tokens.length == 0) continue;

        try{
            String response = sendCommand(tokens);
            printResponse(response, tokens[0].toUpperCase());
        }
        catch(IOException e){
            System.out.println("(error Connection lost. Type 'reconnect' to try again");
            connected = false;
        }
    }
}

    public void tryConnect(){
        try{
            socket = new Socket(host,port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            connected = true;
            System.out.println("Connected to "+ host + ":" + port);
        }catch(ConnectException e){
            System.out.println("Could not connect to "+ host + ":"+port+ "- is no longer running");
            connected = false;
        }
        catch(IOException e){
            System.out.println("Connection error: "+ e.getMessage());
            connected = false;
        }
    }

    private void disconnect(){
        try{
            if(socket != null) socket.close();
        }
        catch(IOException ignored){

        }
        connected = false;
    }

    private String sendCommand(String[] args) throws IOException{
        StringBuilder req = new StringBuilder();
        req.append("*").append(args.length).append("\r\n");

        for(String arg : args){
            req.append("$").append(arg.length()).append("\r\n");
            req.append(arg).append("\r\n");
        }
        out.print(req);
        out.flush();
        return in.readLine();
    }

    private void printResponse(String response, String command) {
            if (response == null) {
                System.out.println("(error) No response from server");
                return;
            }

            // Handle SUBSCRIBE — keep reading until Ctrl+C
            if (command.equals("SUBSCRIBE")) {
                System.out.println("Subscribed. Waiting for messages. Press Ctrl+C to stop.");
                try {
                    while (true) {
                        String line = in.readLine();
                        if (line == null) break;
                        if (!line.startsWith("*")
                                && !line.startsWith("$")
                                && !line.isEmpty()
                                && !line.equals("SUBSCRIBE")
                                && !line.equals("message")) {
                            System.out.println("Message: " + line);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("(error) Connection lost during subscribe");
                    connected = false;
                }
                return;
            }

            if (response.startsWith("+")) {
                // Simple string: +OK or +PONG
                System.out.println(response.substring(1));

            } else if (response.startsWith("-")) {
                // Error: -ERR unknown command
                System.out.println("(error) " + response.substring(1));

            } else if (response.startsWith(":")) {
                // Integer: :1
                System.out.println("(integer) " + response.substring(1));

            } else if (response.equals("$-1")) {
                // Nil bulk string
                System.out.println("(nil)");

            } else if (response.startsWith("$")) {
                // Bulk string — read next line for actual value
                try {
                    String value = in.readLine();
                    System.out.println("\"" + value + "\"");
                } catch (IOException e) {
                    System.out.println("(error) Failed to read bulk response");
                }

            } else {
                // Unknown — print raw
                System.out.println(response);
            }
        }

    private String[] parseInput(String input) {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : input.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) tokens.add(current.toString());
        return tokens.toArray(new String[0]);
    }

    private void printBanner() {
        System.out.println();
        System.out.println("  _  ____     _______ _                    _____ _      _____ ");
        System.out.println(" | |/ /\\ \\   / / ____| |                  / ____| |    |_   _|");
        System.out.println(" | ' /  \\ \\_/ /|  _| | |_ ___  _ __ ___ | |    | |      | |  ");
        System.out.println(" |  <    \\   / | |___| __/ _ \\| '__/ _ \\| |    | |      | |  ");
        System.out.println(" | . \\    | |  |_____|\\__\\___/|_|  \\___/ |____||_____|  |_|  ");
        System.out.println(" |_|\\_\\   |_|                                                  ");
        System.out.println();
        System.out.println("  KVStore CLI v" + versionString
            + " — Type 'help' for commands, 'exit' to quit");
        System.out.println();
    }
    private static void printHelp() {
        System.out.println();
        System.out.println("Server commands:");
        System.out.println("  PING                      Check if server is alive");
        System.out.println("  SET <key> <value>         Store a value");
        System.out.println("  GET <key>                 Retrieve a value");
        System.out.println("  DEL <key>                 Delete a key");
        System.out.println("  EXPIRE <key> <seconds>    Set expiry on a key");
        System.out.println("  SUBSCRIBE <channel>       Listen for messages");
        System.out.println("  PUBLISH <channel> <msg>   Send a message");
        System.out.println();
        System.out.println("CLI commands:");
        System.out.println("  help                      Show this help");
        System.out.println("  clear                     Clear the screen");
        System.out.println("  reconnect                 Reconnect to server");
        System.out.println("  version                   Show CLI version");
        System.out.println("  exit / quit               Exit the CLI");
        System.out.println();
        System.out.println("Tips:");
        System.out.println("  Use quotes for values with spaces:");
        System.out.println("  SET message \"hello world\"");
        System.out.println();
    }

    private static void printVersion() {
        System.out.println("KVStore CLI v" + versionString);
    }

    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

}
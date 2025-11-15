
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private final int port;
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    public Server(int port) {
        this.port = port;
    }

    public void start() {
        System.out.println("Server starting on port " + port);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket s = serverSocket.accept();
                ClientHandler ch = new ClientHandler(s);
                clients.add(ch);
                new Thread(ch).start();
                System.out.println("Client connected: " + s.getRemoteSocketAddress());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void broadcast(String message, ClientHandler exclude) {
        for (ClientHandler ch : clients) {
            if (ch != exclude) {
                ch.send(message);
            }
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username = "unknown";

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        void send(String msg) {
            if (out != null) {
                out.println(msg);
            }
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("HELLO:")) {
                        username = line.substring(6).trim();
                        continue;
                    }
                    broadcast(line, this);
                }
            } catch (IOException ignored) {}
            finally {
                try {
                    clients.remove(this);
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }

    public static void main(String[] args) {
        int port = 9000;
        if (args.length > 0) port = Integer.parseInt(args[0]);
        new Server(port).start();
    }
}

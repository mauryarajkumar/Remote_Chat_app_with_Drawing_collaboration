Snap Of The Project--
<img width="827" height="738" alt="image" src="https://github.com/user-attachments/assets/1f4ffe46-0d7f-4158-90bf-cb91d9e9c750" />

<img width="823" height="737" alt="image" src="https://github.com/user-attachments/assets/0b02792f-edd6-45c7-840b-c8a69de1fb20" />

🟦 PART 1 — SERVER CODE EXPLANATION (LINE-BY-LINE)

The server is responsible for:

Accepting client connections

Creating a thread per client

Reading messages from each client

Broadcasting messages to all other clients

This is a multi-client TCP chat/drawing server.

🔹 Imports
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;


These import the required libraries:

java.io → Streams for reading/writing text over network

java.net → Socket & ServerSocket classes

java.util → Collections

java.util.concurrent → Thread-safe data structures (ConcurrentHashMap)

🔹 Class definition
public class Server {


Defines the server class.

🔹 Server data fields
private final int port;
private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();


port = TCP port number where server listens (ex: 9000)

clients = set of all connected clients

Stored in a thread-safe set (ConcurrentHashMap.newKeySet())

Makes broadcast safe in multi-threaded environment

🔹 Constructor
public Server(int port) {
    this.port = port;
}


Saves the port number for later use.

🟩 SERVER START PROCESS
🔹 start() method
public void start() {
    System.out.println("Server starting on port " + port);


Prints server start message.

🔹 ServerSocket creation
try (ServerSocket serverSocket = new ServerSocket(port)) {


Creates a ServerSocket which listens for incoming client connections.

🔹 Accept client connections in a loop
while (true) {
    Socket s = serverSocket.accept();


accept() blocks until a client connects

Returns a Socket, representing the connection

🔹 Create a ClientHandler for each client
ClientHandler ch = new ClientHandler(s);
clients.add(ch);
new Thread(ch).start();


New ClientHandler created for each client

Each client gets its own thread

The thread starts handling that client immediately

This is how the server supports multiple clients simultaneously.

🟦 BROADCASTING LOGIC
🔹 broadcast() method
private void broadcast(String message, ClientHandler exclude) {
    for (ClientHandler ch : clients) {
        if (ch != exclude) ch.send(message);
    }
}


Meaning:

Send a message to all clients

Except the one who sent the original message

Used for:

Chat

Drawing commands

The server does NOT interpret messages.
It is simply a forwarder.

🟪 CLIENT HANDLER (Server’s inner class)
🔹 Purpose

Each connected client gets a ClientHandler object.

It manages:

Reading messages from that client

Sending messages to that client

🔹 Fields inside ClientHandler
private final Socket socket;
private PrintWriter out;
private BufferedReader in;
private String username = "unknown";


socket → connection to that client

out → used to send messages to the client

in → used to read messages from the client

username → stored when client sends HELLO message

🔹 Constructor
ClientHandler(Socket socket) {
    this.socket = socket;
}


Stores the socket reference.

🔹 send() method
void send(String msg) {
    if (out != null) {
        out.println(msg);
    }
}


Sends a string message to the client using PrintWriter.

🟥 CLIENT THREAD LOGIC
run() method

This method runs in a separate thread.

🔹 Setup streams
out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));


These convert network input/output streams into text-based I/O.

🔹 Read messages in a loop
while ((line = in.readLine()) != null) {


Reads line-by-line until the client disconnects.

🔹 Handle HELLO message
if (line.startsWith("HELLO:")) {
    username = line.substring(6).trim();
    continue;
}


Clients introduce themselves with:

HELLO:username

🔹 Broadcast other messages
broadcast(line, this);


Forward to all OTHER clients.

🔹 Clean up when client disconnects
clients.remove(this);
socket.close();

🟩 SERVER SUMMARY

In viva, explain:

✔ Server uses TCP sockets
✔ Each client handled by separate thread
✔ Uses broadcast mechanism
✔ Maintains thread-safe client list
✔ Server does NOT process messages; it only forwards

This shows understanding of concurrency + networking.

🟦 PART 2 — CLIENT CODE EXPLANATION

Client responsibilities:

Connect to server

Display GUI

Send chat messages

Send drawing commands

Receive updates from other clients

Draw remote strokes

Handle multi-threaded message listening

🟧 CLIENT FIELDS
private final String host;
private final int port;
private final String username;


Stores server details + username.

Networking fields:
private Socket socket;
private PrintWriter out;
private BufferedReader in;

GUI components:
private JFrame frame;
private JTextArea chatArea;
private JTextField inputField;
private DrawPanel drawPanel;


Everything needed for chat + drawing.

🟩 Client start sequence
start() method
SwingUtilities.invokeLater(() -> {
    buildGui();
    new Thread(() -> {
        connectToServer();
        startReaderThread();
        sendHello();
    }).start();
});

This ensures:

✔ GUI runs on EDT (Event Dispatch Thread)
✔ Networking runs on background thread
→ Prevents GUI freezing

🟥 GUI BUILDING
buildGui()

Creates:

Window (JFrame)

Drawing area (DrawPanel)

Chat area (JTextArea)

Input field + Send button (JTextField, JButton)

This organizes layout as:

-------------------------------------
|           DRAW PANEL              |
|                                   |
-------------------------------------
| CHAT AREA | SEND TEXT BOX + BTN  |
-------------------------------------

🟦 SENDING CHAT

When user presses ENTER or clicks Send:

sendChat(text);
chatArea.append("Me: " + text);


And actual message sent over network:

CHAT:username:message

🟥 CONNECTING TO SERVER
socket = new Socket(host, port);

🟩 READER THREAD

This receives messages from server:

while ((line = in.readLine()) != null) {
    handleMessage(line);
}

🟧 HANDLING INCOMING MESSAGES
If it’s chat:
CHAT:User:Message
→ Add text to chat area

If it’s drawing:
DRAW:User:PRESS/DRAG/RELEASE:x:y:color:stroke
→ Call drawPanel.processRemote()

🟥 DRAWING PANEL EXPLANATION

DrawPanel extends JPanel

Handles:

Mouse events (drawing locally)

Sends draw events to server

Draws remote strokes

Maintains canvas in Image

Mouse listeners
On press:
drawDot()
sendDraw("PRESS")

On drag:
drawLine()
sendDraw("DRAG")

On release:
drawLine()
sendDraw("RELEASE")

Remote drawing
processRemote(action, x, y, color, stroke)

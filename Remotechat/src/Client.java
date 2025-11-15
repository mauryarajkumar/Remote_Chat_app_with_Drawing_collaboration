
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class Client {
    private final String host;
    private final int port;
    private final String username;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private JFrame frame;
    private JTextArea chatArea;
    private JTextField inputField;
    private DrawPanel drawPanel;

    public Client(String host, int port, String username) {
        this.host = host;
        this.port = port;
        this.username = username;
    }

    public void start() {
        SwingUtilities.invokeLater(() -> {
            buildGui();
            new Thread(() -> {
                connectToServer();
                startReaderThread();
                sendHello();
            }).start();
        });
    }

    private void buildGui() {
        frame = new JFrame("Collab Draw - " + username);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setLayout(new BorderLayout());

        drawPanel = new DrawPanel();
        frame.add(drawPanel, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout());
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(chatArea);
        scroll.setPreferredSize(new Dimension(300, 400));
        right.add(scroll, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        JButton sendButton = new JButton("Send");

        sendButton.addActionListener(e -> sendUserChat());
        inputField.addActionListener(e -> sendUserChat());

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        right.add(inputPanel, BorderLayout.SOUTH);

        frame.add(right, BorderLayout.EAST);
        frame.setVisible(true);
    }

    private void sendUserChat() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        sendChat(text);
        chatArea.append("Me: " + text + "\n");
        inputField.setText("");
    }

    private void connectToServer() {
        try {
            socket = new Socket(host, port);
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, "Cannot connect"));
        }
    }

    private void sendHello() {
        if (out != null) out.println("HELLO:" + username);
    }

    private void startReaderThread() {
        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    String msg = line;
                    SwingUtilities.invokeLater(() -> handleMessage(msg));
                }
            } catch (IOException ignored) {}
        }).start();
    }

    private void handleMessage(String line) {
        if (line.startsWith("CHAT:")) {
            String[] p = line.split(":", 3);
            if (p.length >= 3) chatArea.append(p[1] + ": " + p[2] + "\n");
            return;
        }

        if (line.startsWith("DRAW:")) {
            String[] p = line.split(":");
            if (p.length >= 7) {
                String action = p[2];
                int x = Integer.parseInt(p[3]);
                int y = Integer.parseInt(p[4]);
                Color c = hexToColor(p[5]);
                float s = Float.parseFloat(p[6]);
                drawPanel.processRemote(action, x, y, c, s);
            }
        }
    }

    private void sendChat(String msg) {
        if (out != null) out.println("CHAT:" + username + ":" + msg);
    }

    private void sendDraw(String a, int x, int y, Color c, float s) {
        if (out == null) return;
        String h = String.format("%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
        out.println("DRAW:" + username + ":" + a + ":" + x + ":" + y + ":" + h + ":" + s);
    }

    private static Color hexToColor(String h) {
        return new Color(Integer.parseInt(h.substring(0, 2), 16),
                Integer.parseInt(h.substring(2, 4), 16),
                Integer.parseInt(h.substring(4, 6), 16));
    }

    private class DrawPanel extends JPanel {
        private Image img;
        private Graphics2D g;
        private int px = -1, py = -1;
        private Color col = Color.BLACK;
        private float stroke = 2f;

        DrawPanel() {
            setBackground(Color.WHITE);
            MouseAdapter ma = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    px = e.getX();
                    py = e.getY();
                    drawDot(px, py, col, stroke);
                    sendDraw("PRESS", px, py, col, stroke);
                }
                public void mouseDragged(MouseEvent e) {
                    int x = e.getX(), y = e.getY();
                    drawLine(px, py, x, y, col, stroke);
                    sendDraw("DRAG", x, y, col, stroke);
                    px = x; py = y;
                }
                public void mouseReleased(MouseEvent e) {
                    int x = e.getX(), y = e.getY();
                    drawLine(px, py, x, y, col, stroke);
                    sendDraw("RELEASE", x, y, col, stroke);
                    px = -1; py = -1;
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        private void initImage() {
            if (img == null) {
                img = createImage(getWidth(), getHeight());
                g = (Graphics2D) img.getGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }

        private void drawDot(int x, int y, Color c, float s) {
            initImage();
            g.setColor(c);
            g.fillOval(x - (int) s, y - (int) s, (int) s * 2, (int) s * 2);
            repaint();
        }

        private void drawLine(int x1, int y1, int x2, int y2, Color c, float s) {
            initImage();
            if (x1 < 0 || y1 < 0) {
                drawDot(x2, y2, c, s);
                return;
            }
            g.setColor(c);
            g.setStroke(new BasicStroke(s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(x1, y1, x2, y2);
            repaint();
        }

        void processRemote(String a, int x, int y, Color c, float s) {
            if (a.equals("PRESS")) drawDot(x, y, c, s);
            else drawLine(x - 1, y - 1, x, y, c, s);
        }

        protected void paintComponent(Graphics g2) {
            super.paintComponent(g2);
            if (img != null) g2.drawImage(img, 0, 0, this);
        }
    }

    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 9000;
        String user = "User" + (int) (Math.random() * 1000);

        if (args.length >= 1) host = args[0];
        if (args.length >= 2) port = Integer.parseInt(args[1]);
        if (args.length >= 3) user = args[2];

        new Client(host, port, user).start();
    }
}

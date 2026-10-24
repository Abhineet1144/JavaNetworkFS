import java.io.*;
import java.net.Socket;

public class Temp3 {
    public static void main(String[] args) throws IOException {
        String host = "localhost";
        int port = 10008; // match your actual LAN port

        try (Socket socket = new Socket(host, port)) {
            System.out.println("Connected: " + socket.isConnected());

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            // --- Build Handshake packet ---
            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            writeVarInt(handshake, 0x00);       // Packet ID: handshake
            writeVarInt(handshake, 767);         // Protocol version (change to match your MC version if needed)
            writeString(handshake, host);        // Server address
            handshake.write(port >> 8);          // Port (2 bytes, big endian)
            handshake.write(port);
            writeVarInt(handshake, 1);           // Next state: 1 = status

            writePacket(out, handshake.toByteArray());

            // --- Send Status Request packet ---
            ByteArrayOutputStream statusRequest = new ByteArrayOutputStream();
            writeVarInt(statusRequest, 0x00); // Packet ID: status request (empty body)
            writePacket(out, statusRequest.toByteArray());

            // --- Read response ---
            int length = readVarInt(in);
            int packetId = readVarInt(in);
            int strLen = readVarInt(in);
            byte[] strBytes = new byte[strLen];
            readFully(in, strBytes);

            String json = new String(strBytes, "UTF-8");
            System.out.println("Server status JSON:");
            System.out.println(json);

        } catch (IOException e) {
            System.err.println("Connection failed: " + e.getMessage());
            throw e;
        }
    }

    // --- Helper methods for Minecraft's VarInt encoding ---

    static void writeVarInt(OutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.write(value);
                return;
            }
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    static int readVarInt(InputStream in) throws IOException {
        int numRead = 0;
        int result = 0;
        int read;
        do {
            read = in.read();
            if (read == -1) throw new IOException("Stream ended prematurely");
            int value = read & 0b01111111;
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) throw new RuntimeException("VarInt too big");
        } while ((read & 0b10000000) != 0);
        return result;
    }

    static void writeString(OutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes("UTF-8");
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    static void writePacket(OutputStream out, byte[] data) throws IOException {
        ByteArrayOutputStream lengthPrefixed = new ByteArrayOutputStream();
        writeVarInt(lengthPrefixed, data.length);
        lengthPrefixed.write(data);
        out.write(lengthPrefixed.toByteArray());
        out.flush();
    }

    static void readFully(InputStream in, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = in.read(buffer, offset, buffer.length - offset);
            if (read == -1) throw new IOException("Stream ended prematurely");
            offset += read;
        }
    }
}

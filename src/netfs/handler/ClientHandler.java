package netfs.handler;

import netfs.net.FileSystemServer;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;

public class ClientHandler implements Runnable {
    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }


    @Override
    public void run() {
        try (
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
        ) {
            String path;
            File target;
            String cmd = readLine(in);
            if (cmd.startsWith(CommandConsts.Prefixes.LIST_CMD)) {
                path = cmd.substring(CommandConsts.Prefixes.LIST_CMD.length());
                target = new File(FileSystemServer.getConfig().getSharedFolder(), path);
                if (!target.isDirectory()) {
                    return;
                }
                for (File file : target.listFiles()) {
                    System.out.println(Arrays.toString(target.listFiles()));
                    writeLine(out, file.getName());
                    writeLine(out, (file.isDirectory() ?  2 : 1) + ":" + file.length());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            FileSystemServer.markSocketClose();
        }
    }

    public static String readLine(InputStream input) throws IOException {
        StringBuilder line = new StringBuilder();

        while (true) {
            int value = input.read();
            if (value == -1) {
                if (line.isEmpty()) {
                    return "";
                }
            }
            if (value == '\n') {
                return line.toString();
            }
            line.append((char) value);
        }
    }

    private static void writeLine(OutputStream out, String line) throws IOException {
        out.write((line + '\n').getBytes());
    }
}

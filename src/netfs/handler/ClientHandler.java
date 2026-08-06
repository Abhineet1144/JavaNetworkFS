package netfs.handler;

import netfs.net.FileSystemServer;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicLong;

public class ClientHandler implements Runnable {
    private static AtomicLong reqId = new AtomicLong();
    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
                BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());) {
            long id = reqId.getAndIncrement();
            String path;
            File target;
            String cmd = readLine(in);
            if (cmd.startsWith(CommandConsts.Prefixes.LIST_CMD)) {
                path = cmd.substring(CommandConsts.Prefixes.LIST_CMD.length());
                target = new File(FileSystemServer.getConfig().getSharedFolder(), path);
                if (!target.isDirectory() || !target.exists()) {
                    return;
                }
                FileSystemServer.getOperationStateHandler()
                        .addMetaGetOperationState(id, "list " + target.getAbsolutePath());
                for (File file : target.listFiles()) {
                    writeLine(out, file.getName());
                    // 2 for directory, 1 for files
                    writeLine(out, (file.isDirectory() ? 2 : 1) + ":" + file.length());
                }
            } else if (cmd.startsWith(CommandConsts.Prefixes.MKDIR_CMD)) {
                path = cmd.substring(CommandConsts.Prefixes.MKDIR_CMD.length());
                target = new File(FileSystemServer.getConfig().getSharedFolder(), path);
                FileSystemServer.getOperationStateHandler()
                        .addMetaGetOperationState(id, "mkdir: " + target.getAbsolutePath());
                if (target.mkdir()) {
                    writeLine(out, (target.isDirectory() ? 2 : 1) + ":" + target.length());
                } else {
                    writeLine(out, "F");
                }
            } else if (cmd.startsWith(CommandConsts.Prefixes.RMDIR_CMD)) {
                path = cmd.substring(CommandConsts.Prefixes.RMDIR_CMD.length());
                target = new File(FileSystemServer.getConfig().getSharedFolder(), path);
                FileSystemServer.getOperationStateHandler()
                        .addMetaGetOperationState(id, "rm dir: " + target.getAbsolutePath());
                if (deleteRecursive(target)) {
                    writeLine(out, "S");
                } else {
                    writeLine(out, "F");
                }
            } else if (cmd.startsWith(CommandConsts.Prefixes.CREATE_CMD)) {
                path = cmd.substring(CommandConsts.Prefixes.CREATE_CMD.length());
                target = new File(FileSystemServer.getConfig().getSharedFolder(), path);
                FileSystemServer.getOperationStateHandler()
                        .addMetaGetOperationState(id, "create: " + target.getAbsolutePath());
                if (target.createNewFile()) {
                    writeLine(out, "S");
                } else {
                    writeLine(out, "F");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            FileSystemServer.markSocketClose();
        }
    }

    public static boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] contents = file.listFiles();
            if (contents != null) {
                for (File f : contents) {
                    deleteRecursive(f);
                }
            }
        }
        return file.delete();
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

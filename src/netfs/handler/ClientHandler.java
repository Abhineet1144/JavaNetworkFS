package netfs.handler;

import netfs.diskio.JNFSInputStream;
import netfs.diskio.JNFSOutputStream;
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
        try (JNFSInputStream in = new JNFSInputStream(socket.getInputStream(), FileSystemServer.getOperationStateHandler());
             JNFSOutputStream out = new JNFSOutputStream(socket.getOutputStream(), FileSystemServer.getOperationStateHandler());
        ) {
            long id = reqId.getAndIncrement();
            String path;
            File target;
            String cmd = JNFSInputStream.readLine(in);
            if (cmd.startsWith(CommandConsts.Prefixes.LIST_CMD)) {
                path = cmd.substring(CommandConsts.Prefixes.LIST_CMD.length());
                target = new File(FileSystemServer.getConfig().getSharedFolder(), path);
                if (!target.isDirectory() || !target.exists()) {
                    return;
                }
                FileSystemServer.getOperationStateHandler()
                        .addMetaGetOperationState(id, "list: " + target.getAbsolutePath());
                for (File file : target.listFiles()) {
                    JNFSOutputStream.writeLine(out, file.getName());
                    // 2 for directory, 1 for files
                    JNFSOutputStream.writeLine(out, (file.isDirectory() ? 2 : 1) + ":" + file.length());
                }
            } else if (cmd.startsWith(CommandConsts.Prefixes.MKDIR_CMD)) {
                path = cmd.substring(CommandConsts.Prefixes.MKDIR_CMD.length());
                target = new File(FileSystemServer.getConfig().getSharedFolder(), path);
                FileSystemServer.getOperationStateHandler()
                        .addMetaGetOperationState(id, "mkdir: " + target.getAbsolutePath());
                if (target.mkdir()) {
                    JNFSOutputStream.writeLine(out, (target.isDirectory() ? 2 : 1) + ":" + target.length());
                } else {
                    JNFSOutputStream.writeLine(out, "F");
                }
            } else if (cmd.startsWith(CommandConsts.Prefixes.RMDIR_CMD)) {
                path = cmd.substring(CommandConsts.Prefixes.RMDIR_CMD.length());
                target = new File(FileSystemServer.getConfig().getSharedFolder(), path);
                FileSystemServer.getOperationStateHandler()
                        .addMetaGetOperationState(id, "rm dir: " + target.getAbsolutePath());
                if (deleteRecursive(target)) {
                    JNFSOutputStream.writeLine(out, "S");
                } else {
                    JNFSOutputStream.writeLine(out, "F");
                }
            } else if (cmd.startsWith(CommandConsts.Prefixes.CREATE_CMD)) {
                path = cmd.substring(CommandConsts.Prefixes.CREATE_CMD.length());
                target = new File(FileSystemServer.getConfig().getSharedFolder(), path);
                FileSystemServer.getOperationStateHandler()
                        .addMetaGetOperationState(id, "create: " + target.getAbsolutePath());
                if (target.exists()) {
                    JNFSOutputStream.writeLine(out, "S");
                }
                if (target.createNewFile()) {
                    JNFSOutputStream.writeLine(out, "S");
                } else {
                    JNFSOutputStream.writeLine(out, "F");
                }
            } else if (cmd.startsWith(CommandConsts.Prefixes.RENAME_CMD)) {
                path = cmd.substring(CommandConsts.Prefixes.RENAME_CMD.length());
                target = new File(FileSystemServer.getConfig().getSharedFolder(), path);
                String newPath = JNFSInputStream.readLine(in);
                File renamefile = new File(FileSystemServer.getConfig().getSharedFolder(), newPath);
                FileSystemServer.getOperationStateHandler()
                        .addMetaGetOperationState(id, "rename: " + target.getAbsolutePath() + "->" + renamefile.getAbsolutePath());
                if (target.renameTo(renamefile)) {
                    JNFSOutputStream.writeLine(out, (renamefile.isDirectory() ? 2 : 1) + ":" + renamefile.length());
                } else {
                    JNFSOutputStream.writeLine(out, "F");
                }
            } else if (cmd.startsWith(CommandConsts.Prefixes.READ_CMD)) {
                String[] insts = cmd.substring(CommandConsts.Prefixes.READ_CMD.length()).split(":");
                path = insts[0];
                long offset = Long.parseLong(insts[1]);
                int limit = Integer.parseInt(insts[2]);
                target = new File(FileSystemServer.getConfig().getSharedFolder(), path);
                if (!target.exists() || target.isDirectory()) {
                    JNFSOutputStream.writeLine(out, "F");
                }
                FileSystemServer.getOperationStateHandler()
                        .addMetaGetOperationState(id, "Reading " + path + " chunk with offset: "
                                + offset +" and chunksize: " + limit);
                out.writeFileChunk(target, offset, limit);
            } else if (cmd.startsWith(CommandConsts.Prefixes.OPEN_CMD)) {
                path = cmd.substring(CommandConsts.Prefixes.OPEN_CMD.length());
                target = new File(FileSystemServer.getConfig().getSharedFolder(), path);
                FileSystemServer.getOperationStateHandler()
                        .addMetaGetOperationState(id, "open: " + target.getAbsolutePath());
                if (target.exists()) {
                    JNFSOutputStream.writeLine(out, "S");
                } else {
                    JNFSOutputStream.writeLine(out, "F");
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
}

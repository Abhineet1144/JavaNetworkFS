package netfs.handler;

import netfs.diskio.JNFSInputStream;
import netfs.diskio.JNFSOutputStream;
import netfs.net.FileSystemServer;
import netfs.state.OperationLog;
import netfs.state.OperationLogEntry;
import netfs.state.OperationSource;
import netfs.state.TransferStats;

import java.io.*;
import java.net.Socket;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class ClientHandler implements Runnable {
    private static AtomicLong reqId = new AtomicLong();

    private static final Map<String, String> COMMAND_LABELS = new LinkedHashMap<>();
    static {
        COMMAND_LABELS.put(CommandConsts.Prefixes.LIST_CMD, "LIST");
        COMMAND_LABELS.put(CommandConsts.Prefixes.STAT_CMD, "STAT");
        COMMAND_LABELS.put(CommandConsts.Prefixes.MKDIR_CMD, "MKDIR");
        COMMAND_LABELS.put(CommandConsts.Prefixes.RMDIR_CMD, "RMDIR");
        COMMAND_LABELS.put(CommandConsts.Prefixes.CREATE_CMD, "CREATE");
        COMMAND_LABELS.put(CommandConsts.Prefixes.RENAME_CMD, "RENAME");
        COMMAND_LABELS.put(CommandConsts.Prefixes.READ_CMD, "READ");
        COMMAND_LABELS.put(CommandConsts.Prefixes.OPEN_CMD, "OPEN");
        COMMAND_LABELS.put(CommandConsts.Prefixes.WRITE_CMD, "WRITE");
        COMMAND_LABELS.put(CommandConsts.Prefixes.TRUNCATE_CMD, "TRUNCATE");
    }

    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    private static String labelFor(String cmd) {
        for (Map.Entry<String, String> entry : COMMAND_LABELS.entrySet()) {
            if (cmd.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "UNKNOWN";
    }

    private static String detailFor(String cmd) {
        int colon = cmd.indexOf(':');
        return colon >= 0 ? cmd.substring(colon + 1) : cmd;
    }


    @Override
    public void run() {
        long id = reqId.getAndIncrement();
        try (JNFSInputStream in = new JNFSInputStream(socket.getInputStream(),
                FileSystemServer.getOperationStateHandler());
                JNFSOutputStream out = new JNFSOutputStream(socket.getOutputStream(),
                        FileSystemServer.getOperationStateHandler())) {
            System.out.println("[SERVER] Handler " + id + " started for " + socket.getRemoteSocketAddress());
            String cmd;

            while (!socket.isClosed() && (cmd = JNFSInputStream.readLine(in)) != null) {
                if (cmd.isEmpty()) {
                    continue;
                }
                OperationLogEntry logEntry = OperationLog.start(OperationSource.HOST, labelFor(cmd), detailFor(cmd));
                try {
                String path;
                File target;
                if (cmd.startsWith(CommandConsts.Prefixes.LIST_CMD)) {
                    path = cmd.substring(CommandConsts.Prefixes.LIST_CMD.length());
                    target = resolveSharedPath(path);
                    if (!target.isDirectory() || !target.exists()) {
                        JNFSOutputStream.writeLine(out, "");
                        OperationLog.complete(logEntry);
                        continue;
                    }
                    FileSystemServer.getOperationStateHandler()
                            .addMetaGetOperationState(id, "list: " + target.getAbsolutePath());
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(target.toPath())) {
                        for (Path child : stream) {
                            File file = child.toFile();
                            JNFSOutputStream.writeLine(out, file.getName());
                            // 2 for directory, 1 for files
                            JNFSOutputStream.writeLine(out, (file.isDirectory() ? 2 : 1) + ":" + file.length());
                        }
                    }
                    JNFSOutputStream.writeLine(out, "");
                } else if (cmd.startsWith(CommandConsts.Prefixes.STAT_CMD)) {
                    path = cmd.substring(CommandConsts.Prefixes.STAT_CMD.length());
                    target = resolveSharedPath(path);
                    FileSystemServer.getOperationStateHandler()
                            .addMetaGetOperationState(id, "stat: " + target.getAbsolutePath());
                    if (target.exists()) {
                        JNFSOutputStream.writeLine(out, (target.isDirectory() ? 2 : 1) + ":" + target.length());
                    } else {
                        JNFSOutputStream.writeLine(out, "F");
                    }
                } else if (cmd.startsWith(CommandConsts.Prefixes.MKDIR_CMD)) {
                    path = cmd.substring(CommandConsts.Prefixes.MKDIR_CMD.length());
                    target = resolveSharedPath(path);
                    FileSystemServer.getOperationStateHandler()
                            .addMetaGetOperationState(id, "mkdir: " + target.getAbsolutePath());
                    if (target.mkdir()) {
                        JNFSOutputStream.writeLine(out, (target.isDirectory() ? 2 : 1) + ":" + target.length());
                    } else {
                        JNFSOutputStream.writeLine(out, "F");
                    }
                } else if (cmd.startsWith(CommandConsts.Prefixes.RMDIR_CMD)) {
                    path = cmd.substring(CommandConsts.Prefixes.RMDIR_CMD.length());
                    target = resolveSharedPath(path);
                    FileSystemServer.getOperationStateHandler()
                            .addMetaGetOperationState(id, "rm dir: " + target.getAbsolutePath());
                    if (deleteRecursive(target)) {
                        JNFSOutputStream.writeLine(out, "S");
                    } else {
                        JNFSOutputStream.writeLine(out, "F");
                    }
                } else if (cmd.startsWith(CommandConsts.Prefixes.CREATE_CMD)) {
                    path = cmd.substring(CommandConsts.Prefixes.CREATE_CMD.length());
                    target = resolveSharedPath(path);
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
                    target = resolveSharedPath(path);
                    String newPath = JNFSInputStream.readLine(in);
                    File renamefile = resolveSharedPath(newPath);
                    FileSystemServer.getOperationStateHandler().addMetaGetOperationState(id,
                            "rename: " + target.getAbsolutePath() + "->" + renamefile.getAbsolutePath());
                    if (target.renameTo(renamefile)) {
                        JNFSOutputStream.writeLine(out, (renamefile.isDirectory() ? 2 : 1) + ":" + renamefile.length());
                    } else {
                        JNFSOutputStream.writeLine(out, "F");
                    }
                } else if (cmd.startsWith(CommandConsts.Prefixes.READ_CMD)) {
                    String[] insts = cmd.substring(CommandConsts.Prefixes.READ_CMD.length()).split(":");
                    path = insts[0];
                    long offset = Long.parseLong(insts[1]);
                    int cacheSize = Integer.parseInt(insts[2]);

                    target = resolveSharedPath(path);
                    if (!target.exists() || target.isDirectory()) {
                        JNFSOutputStream.writeLine(out, "F");
                    }

                    FileSystemServer.getOperationStateHandler().addMetaGetOperationState(id,
                            "Reading " + path + " chunk with offset: " + offset + " and chunk size: " + cacheSize);
                    int sentBytes = out.writeFileChunk(target, offset, cacheSize);
                    TransferStats.addReadBytes(OperationSource.HOST, sentBytes);
                } else if (cmd.startsWith(CommandConsts.Prefixes.OPEN_CMD)) {
                    path = cmd.substring(CommandConsts.Prefixes.OPEN_CMD.length());
                    target = resolveSharedPath(path);
                    FileSystemServer.getOperationStateHandler()
                            .addMetaGetOperationState(id, "open: " + target.getAbsolutePath());
                    if (target.exists()) {
                        JNFSOutputStream.writeLine(out, "S");
                    } else {
                        JNFSOutputStream.writeLine(out, "F");
                    }
                } else if (cmd.startsWith(CommandConsts.Prefixes.WRITE_CMD)) {
                    String[] insts = cmd.substring(CommandConsts.Prefixes.WRITE_CMD.length()).split(":");
                    path = insts[0];
                    long offset = Long.parseLong(insts[1]);
                    int len = Integer.parseInt(insts[2]);

                    target = resolveSharedPath(path);

                    if (!target.exists() || target.isDirectory()) {
                        JNFSOutputStream.writeLine(out, "F");
                    }
                    FileSystemServer.getOperationStateHandler()
                            .addMetaGetOperationState(id, "Writing  " + path + " with offset: " + offset);
                    byte[] data = JNFSInputStream.readTill(in, len);
                    TransferStats.addWriteBytes(OperationSource.HOST, data.length);
                    try (RandomAccessFile raf = new RandomAccessFile(target, "rw")) {
                        raf.seek(offset);
                        raf.write(data);
                    }
                } else if (cmd.startsWith(CommandConsts.Prefixes.TRUNCATE_CMD)) {
                    path = cmd.substring(CommandConsts.Prefixes.TRUNCATE_CMD.length());
                    target = resolveSharedPath(path);
                    long size = Long.parseLong(JNFSInputStream.readLine(in));

                    FileSystemServer.getOperationStateHandler()
                            .addMetaGetOperationState(id, "truncate: " + target.getAbsolutePath() + " size: " + size);
                    if (target.exists()) {
                        try (RandomAccessFile raf = new RandomAccessFile(target, "rw")) {
                            raf.setLength(size);
                        }
                        JNFSOutputStream.writeLine(out, "1:" + size);
                    } else {
                        JNFSOutputStream.writeLine(out, "F");
                    }
                }
                OperationLog.complete(logEntry);
                } catch (Exception e) {
                    OperationLog.fail(logEntry, e.getMessage());
                    throw e;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            System.out.println("[SERVER] Handler " + id + " closed for " + socket.getRemoteSocketAddress());
            FileSystemServer.getOperationStateHandler().removeOperationState(id);
            FileSystemServer.markSocketClose();
        }
    }

    private File resolveSharedPath(String path) {
        String relativePath = path == null ? "" : path;
        while (relativePath.startsWith(File.separator)) {
            relativePath = relativePath.substring(1);
        }
        return new File(FileSystemServer.getConfig().getSharedFolder(), relativePath);
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

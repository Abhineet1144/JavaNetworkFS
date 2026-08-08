package netfs.diskio;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IllegalFormatCodePointException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import jnr.ffi.Pointer;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Statvfs;
import ru.serce.jnrfuse.struct.Timespec;

public class KernelFSHandler extends FuseStubFS {

    private final Map<String, String> map = new HashMap<>();
    private final Map<String, CacheBlock> cache = new ConcurrentHashMap<>();
    private final Map<String, Object> pathLocks = new ConcurrentHashMap<>();
    private final int cacheSize = 10485760;
    private final int maxCacheSize = 20971520;
    private final AtomicLong totalCacheBytes = new AtomicLong(0);

    private String host;
    private int port;

    public KernelFSHandler(String host, int port) {
        this.port = port;
        this.host = host;
        Thread cleanupThread = new Thread(() -> {
            while (true) {
                if (totalCacheBytes.get() >= maxCacheSize) {
                    cleanCache();
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    private void cleanCache() {
        System.out.println("CACHE MAXED: cleared " + totalCacheBytes.get());
        cache.clear();
        totalCacheBytes.set(0);
    }

    /**
     * Gets attributes (size, mode/permissions, timestamps, owner) of a file or directory. Called constantly by the OS
     * whenever listing or accessing files.
     */
    @Override
    public int getattr(String path, FileStat stat) {
        if ("/".equals(path)) {
            stat.st_mode.set(FileStat.S_IFDIR | 0755); // Directory permissions
            stat.st_nlink.set(2);
            return 0; // Success
        }

        String b = map.get(path);
        if (b != null) {
            stat.st_mode.set((b.split(":")[0].equals("2") ? FileStat.S_IFDIR : FileStat.S_IFREG) | 0755);
            stat.st_nlink.set(Integer.parseInt(b.split(":")[0]));
            stat.st_size.set(Long.parseLong(b.split(":")[1]));
            return 0;
        }

        // Return -ErrorCodes.ENOENT() if file doesn't exist
        //        System.out.println("unavail" + map);
        //        System.out.println(path);
        return -ErrorCodes.ENOENT();
    }

    /**
     * Returns total disk capacity, free space, and block numbers (used by OS drive space indicators).
     */
    @Override
    public int statfs(String path, Statvfs stbuf) {
        stbuf.f_bsize.set(4096);       // Block size
        stbuf.f_blocks.set(1000000L);  // Total blocks
        stbuf.f_bfree.set(500000L);    // Free blocks available
        stbuf.f_bavail.set(500000L);   // Free blocks for unprivileged users
        stbuf.f_namemax.set(255);      // File name size

        System.out.println("b: " + path);
        return 0;
    }

    /**
     * Reads directory entries (like 'ls' on Linux or opening a folder in Windows Explorer).
     */
    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filler, @off_t long offset, FuseFileInfo fi) {
        filler.apply(buf, ".", null, 0);  // Current directory
        filler.apply(buf, "..", null, 0); // Parent directory
        System.out.println("Getting ls for path: " + path);
        try {
            var s = new Socket(host, port);
            new PrintWriter(s.getOutputStream(), true).println("ls:" + path);
            var i = s.getInputStream();
            String li = "";
            while (!(li = JNFSInputStream.readLine(i)).isEmpty()) {
                filler.apply(buf, li, null, 0);
                String li2 = JNFSInputStream.readLine(i);
                map.put(path + (path.endsWith("/") ? "" : "/") + li, li2);
            }
            //            System.out.println(map);
            s.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }

    /**
     * Creates a new directory.
     */
    @Override
    public int mkdir(String path, long mode) {
        System.out.println("Creating folder: " + path);
        try {
            var s = new Socket(host, port);
            var i = s.getInputStream();
            new PrintWriter(s.getOutputStream(), true).println("mkdir:" + path);
            String resp = JNFSInputStream.readLine(i);
            if (isSuccess(resp)) {
                map.put(path, resp);
            } else {
                s.close();
                return -1;
            }
            s.close();
        } catch (IOException e) {
            throw new RuntimeException();
        }
        return 0;
    }

    /**
     * Removes an empty directory.
     */
    @Override
    public int rmdir(String path) {
        System.out.println("Remove folder: " + path);
        try {
            var s = new Socket(host, port);
            var i = s.getInputStream();
            new PrintWriter(s.getOutputStream(), true).println("rmdir:" + path);
            String resp = JNFSInputStream.readLine(i);
            if (isSuccess(resp)) {
                map.remove(path);
            } else {
                s.close();
                return -1;
            }
            s.close();
        } catch (IOException e) {
            throw new RuntimeException();
        }
        return 0;
    }

    /**
     * Creates and opens a new file.
     */
    @Override
    public int create(String path, long mode, FuseFileInfo fi) {
        System.out.println("Create file: " + path);
        try {
            var s = new Socket(host, port);
            var i = s.getInputStream();
            new PrintWriter(s.getOutputStream(), true).println("create:" + path);
            String resp = JNFSInputStream.readLine(i);
            if (isSuccess(resp)) {
                map.put(path, "1:0");
            } else {
                s.close();
                return -1;
            }
            s.close();
        } catch (IOException e) {
            throw new RuntimeException();
        }
        return 0;
    }

    /**
     * Opens an existing file. Check read/write access permissions here.
     */
    @Override
    public int open(String path, FuseFileInfo fi) {
        System.out.println("Open file: " + path);
        return 0;
    }

    /**
     * Called when a file is closed by the OS/application.
     */
    @Override
    public int release(String path, FuseFileInfo fi) {
        System.out.println("Closed file: " + path);

        CacheBlock removed = cache.remove(path);

        if (removed != null) {
            totalCacheBytes.addAndGet(-removed.getData().length);
        }

        return 0;
    }

    /**
     * Deletes a file.
     */
    @Override
    public int unlink(String path) {
        System.out.println("Delete file: " + path);
        try {
            var s = new Socket(host, port);
            var i = s.getInputStream();
            new PrintWriter(s.getOutputStream(), true).println("rmdir:" + path);
            String resp = JNFSInputStream.readLine(i);
            if (isSuccess(resp)) {
                map.remove(path);
            } else {
                s.close();
                return -1;
            }
            s.close();
        } catch (IOException e) {
            throw new RuntimeException();
        }
        return 0;
    }

    /**
     * Renames or moves a file or directory.
     */
    @Override
    public int rename(String oldPath, String newPath) {
        System.out.println("Rename " + oldPath + " -> " + newPath);
        try {
            var s = new Socket(host, port);
            var i = s.getInputStream();
            PrintWriter printWriter = new PrintWriter(s.getOutputStream(), true);
            printWriter.println("rename:" + oldPath);
            printWriter.println(newPath);
            String resp = JNFSInputStream.readLine(i);
            if (isSuccess(resp)) {
                map.remove(oldPath);
                map.put(newPath, resp);
            } else {
                s.close();
                return -1;
            }
            s.close();
        } catch (IOException e) {
            throw new RuntimeException();
        }
        return 0;
    }

    /**
     * Resizes (truncates/extends) a file to a specific size in bytes.
     */
    @Override
    public int truncate(String path, long size) {
        System.out.println("Truncate file " + path + " to size: " + size);
        try {
            var s = new Socket(host, port);
            var i = s.getInputStream();
            PrintWriter printWriter = new PrintWriter(s.getOutputStream(), true);
            printWriter.println("truncate:" + path);
            printWriter.println(size);
            String resp = JNFSInputStream.readLine(i);
            if (isSuccess(resp)) {
                map.remove(path);
                map.put(path, resp);
            } else {
                s.close();
                return -1;
            }
            s.close();
        } catch (IOException e) {
            throw new RuntimeException();
        }
        return 0;
    }

    /**
     * Reads data from a file into the provided memory pointer buffer.
     */
    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        Object lock = pathLocks.computeIfAbsent(path, p -> new Object());

        synchronized (lock) {
            try {
                CacheBlock cacheBlock = cache.get(path);
                if (cacheBlock != null) {
                    long cacheStart = cacheBlock.getCacheStartOffset();
                    long cacheEnd = cacheStart + cacheBlock.getData().length;

                    long requestEnd = offset + size;

                    if (offset >= cacheStart && requestEnd <= cacheEnd) {

                        int start = (int) (offset - cacheBlock.getCacheStartOffset());
                        int length = (int) size;

                        buf.put(0, cacheBlock.getData(), start, length);
                        System.out.println("CACHE HIT: " + path + " offset=" + offset + " size=" + size);
                        return length;
                    }

                    // Right partial hit
                    if (offset >= cacheStart && offset < cacheEnd && requestEnd > cacheEnd) {
                        int cachedBytes = (int) (cacheEnd - offset);
                        int missingBytes = (int) (requestEnd - cacheEnd);
                        int cacheStartIndex = (int) (offset - cacheStart);

                        buf.put(0, cacheBlock.getData(), cacheStartIndex, cachedBytes);

                        String mapEntry = map.get(path);
                        long targetFileSize = Long.parseLong(mapEntry.split(":")[1]);

                        if (cacheEnd >= targetFileSize) {
                            System.out.println("PARTIAL HIT + EOF: cached=" + cachedBytes);
                            return cachedBytes;
                        }

                        byte[] missingData = getData(path, cacheEnd, missingBytes);

                        int missingLen = Math.min(missingData.length, missingBytes);
                        buf.put(cachedBytes, missingData, 0, missingLen);

                        byte[] existing = cacheBlock.getData();
                        byte[] merged = new byte[existing.length + missingLen];
                        System.arraycopy(existing, 0, merged, 0, existing.length);
                        System.arraycopy(missingData, 0, merged, existing.length, missingLen);
                        cache.put(path, new CacheBlock(merged, (int) cacheStart));
                        totalCacheBytes.addAndGet(merged.length - existing.length);

                        System.out.println("PARTIAL HIT: cached=" + cachedBytes + " missing=" + missingLen);
                        return cachedBytes + missingLen;
                    }

                    //left partial hit
                    if (offset < cacheStart && requestEnd > cacheStart && requestEnd <= cacheEnd) {
                        int missingBytes = (int) (cacheStart - offset);
                        int cachedBytes = (int) (requestEnd - cacheStart);

                        byte[] missingData = getData(path, offset, missingBytes);
                        int missingLen = Math.min(missingData.length, missingBytes);
                        buf.put(0, missingData, 0, missingLen);

                        buf.put(missingLen, cacheBlock.getData(), 0, cachedBytes);

                        byte[] existing = cacheBlock.getData();
                        byte[] merged = new byte[missingLen + existing.length];
                        System.arraycopy(missingData, 0, merged, 0, missingLen);
                        System.arraycopy(existing, 0, merged, missingLen, existing.length);
                        cache.put(path, new CacheBlock(merged, (int) offset));
                        totalCacheBytes.addAndGet(merged.length - existing.length);

                        System.out.println("PARTIAL HIT (left): missing=" + missingLen + " cached=" + cachedBytes);
                        return missingLen + cachedBytes;
                    }
                }

                Socket s = new Socket(host, port);
                var i = s.getInputStream();

                System.out.println("Miss");

                new PrintWriter(s.getOutputStream(), true).println(
                        "read:" + path + ":" + offset + ":" + size + ":" + cacheSize);
                int resp = Integer.parseInt(JNFSInputStream.readLine(i));
                byte[] data = new byte[resp];
                new DataInputStream(i).readFully(data);
                CacheBlock block = new CacheBlock(data, (int) offset);
                CacheBlock oldBlock = cache.put(path, block);
                if (oldBlock != null) {
                    totalCacheBytes.addAndGet(data.length - oldBlock.getData().length);
                } else {
                    totalCacheBytes.addAndGet(data.length);
                }

                int copyLen = Math.min((int) size, data.length);
                buf.put(0, data, 0, copyLen);

                return copyLen;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public byte[] getData(String path, long offset, int cacheSize) throws IOException {
        Socket s = new Socket(host, port);
        var i = s.getInputStream();

        new PrintWriter(s.getOutputStream(), true).println("read:" + path + ":" + offset + ":000:" + cacheSize);
        int resp = Integer.parseInt(JNFSInputStream.readLine(i));
        byte[] data = new byte[resp];
        new DataInputStream(i).readFully(data);
        return data;
    }

    /**
     * Writes data from the buffer pointer into your virtual storage.
     */
    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        try {
            Socket s = new Socket(host, port);
            var i = s.getOutputStream();
            byte[] dataToWrite = new byte[(int) size];
            buf.get(0, dataToWrite, 0, (int) size);
            new PrintWriter(s.getOutputStream(), true).println(
                    "write:" + path + ":" + offset + ":" + dataToWrite.length);
            new DataOutputStream(i).write(dataToWrite);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Wrote " + size + " bytes to " + path + " at offset " + offset);
        return (int) size; // Return number of bytes written
    }

    /**
     * Flushes cached data before closing a file descriptor.
     */
    @Override
    public int flush(String path, FuseFileInfo fi) {
        return 0;
    }

    /**
     * Synchronizes file contents to permanent storage (like 'fsync' in C).
     */
    @Override
    public int fsync(String path, int isdatasync, FuseFileInfo fi) {
        return 0;
    }

    /**
     * Changes file permissions (e.g., chmod 777).
     */
    @Override
    public int chmod(String path, long mode) {
        return 0;
    }

    /**
     * Changes file owner and group IDs (chown).
     */
    @Override
    public int chown(String path, long uid, long gid) {
        return 0;
    }

    /**
     * Updates access and modification times (e.g., touch file).
     */
    @Override
    public int utimens(String path, Timespec[] tmsp) {
        return 0;
    }

    /**
     * Creates a symbolic link.
     */
    @Override
    public int symlink(String oldpath, String newpath) {
        return 0;
    }

    /**
     * Reads the target path of a symbolic link.
     */
    @Override
    public int readlink(String path, Pointer buf, @size_t long size) {
        return 0;
    }

    @Override
    public void mount(Path mountPoint) {
        super.mount(mountPoint);
    }

    public boolean isSuccess(String resp) {
        return !resp.equals("F");
    }
}

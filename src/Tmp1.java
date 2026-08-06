import netfs.diskio.JNFSInputStream;
import netfs.handler.ClientHandler;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Statvfs;
import ru.serce.jnrfuse.struct.Timespec;
import jnr.ffi.Pointer;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
// sudo apt install libfuse-dev
// win WinFsp

public class Tmp1 extends FuseStubFS {
    public Map<String, String> map = new HashMap<>();

    // =========================================================================
    // 1. FILE & DIRECTORY LOOKUP / METADATA
    // =========================================================================

    /**
     * Gets attributes (size, mode/permissions, timestamps, owner) of a file or directory.
     * Called constantly by the OS whenever listing or accessing files.
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
        System.out.println("unavail" + map);
        System.out.println(path);
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

    // =========================================================================
    // 2. DIRECTORY OPERATIONS
    // =========================================================================

    /**
     * Reads directory entries (like 'ls' on Linux or opening a folder in Windows Explorer).
     */
    @Override
    public int readdir(String path, Pointer buf, FuseFillDir filler, @off_t long offset, FuseFileInfo fi) {
        filler.apply(buf, ".", null, 0);  // Current directory
        filler.apply(buf, "..", null, 0); // Parent directory
        System.out.println("Getting ls for path: " + path);
        try {
            var s = new Socket("localhost", 10002);
            new PrintWriter(s.getOutputStream(), true).println("ls:" + path);
            var i = s.getInputStream();
            String li = "";
            while (!(li = JNFSInputStream.readLine(i)).isEmpty()) {
                filler.apply(buf, li, null, 0);
                String li2 = JNFSInputStream.readLine(i);
                map.put(path + (path.endsWith("/") ? "" : "/") + li, li2);
            }
            System.out.println(map);
            s.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Example: Add a virtual file
        // filler.apply(buf, "hello.txt", null, 0);
        return 0;
    }

    /**
     * Creates a new directory.
     */
    @Override
    public int mkdir(String path, long mode) {
        System.out.println("Creating folder: " + path);
        try {
            var s = new Socket("localhost", 10002);
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
            var s = new Socket("localhost", 10002);
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

    // =========================================================================
    // 3. FILE LIFECYCLE & ACCESS
    // =========================================================================

    /**
     * Creates and opens a new file.
     */
    @Override
    public int create(String path, long mode, FuseFileInfo fi) {
        System.out.println("Create file: " + path);
        try {
            var s = new Socket("localhost", 10002);
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
        return 0;
    }

    /**
     * Deletes a file.
     */
    @Override
    public int unlink(String path) {
        System.out.println("Delete file: " + path);
        try {
            var s = new Socket("localhost", 10002);
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
            var s = new Socket("localhost", 10002);
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
        return 0;
    }

    // =========================================================================
    // 4. DATA READ & WRITE
    // =========================================================================

    /**
     * Reads data from a file into the provided memory pointer buffer.
     */
    @Override
    public int read(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        Socket s = null;
        try {
            s = new Socket("localhost", 10002);
            var i = s.getInputStream();
            new PrintWriter(s.getOutputStream(), true).println("read:" + path + ":" + offset + ":" + (int) size);
            String resp = JNFSInputStream.readLine(i);
            byte[] data = new byte[Integer.parseInt(resp)];
            i.read(data, 0, Integer.parseInt(resp));
            if (offset < data.length) {
                int bytesToWrite = (int) Math.min(data.length - offset, size);
                buf.put(0, data, (int) offset, bytesToWrite);
                return bytesToWrite;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return 0; // EOF (End of File)
    }

    /**
     * Writes data from the buffer pointer into your virtual storage.
     */
    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        byte[] dataToWrite = new byte[(int) size];
        buf.get(0, dataToWrite, 0, (int) size);

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

    // =========================================================================
    // 5. PERMISSIONS & TIMESTAMPS
    // =========================================================================

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

    // =========================================================================
    // 6. SYMLINKS & HARDLINKS
    // =========================================================================

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

    public boolean isSuccess(String resp) {
        return !resp.equals("F");
    }

    // =========================================================================
    // MAIN ENTRY POINT
    // =========================================================================

    public static void main(String[] args) {
        Tmp1 fs = new Tmp1();

        String mountPoint = "/tmp/netfs1";

        System.out.println("Mounting drive at: " + mountPoint);

        // Mount options:
        // - true: run in foreground (so console stays open)
        // - false: run in background
        fs.mount(Paths.get(mountPoint), true);
    }
}
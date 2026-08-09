package netfs.diskio;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jnr.ffi.Pointer;
import jnr.ffi.types.off_t;
import jnr.ffi.types.size_t;
import netfs.cache.CacheBlock;
import netfs.cache.CacheManager;
import netfs.client.ConnectionPool;
import netfs.operations.CreateOperation;
import netfs.operations.ListOperation;
import netfs.operations.MkdirOperation;
import netfs.operations.ReadOperation;
import netfs.operations.RenameOperation;
import netfs.operations.RmdirOperation;
import netfs.operations.TruncateOperation;
import netfs.operations.UnlinkOperation;
import netfs.operations.WriteOperation;
import ru.serce.jnrfuse.ErrorCodes;
import ru.serce.jnrfuse.FuseFillDir;
import ru.serce.jnrfuse.FuseStubFS;
import ru.serce.jnrfuse.struct.FileStat;
import ru.serce.jnrfuse.struct.FuseFileInfo;
import ru.serce.jnrfuse.struct.Statvfs;
import ru.serce.jnrfuse.struct.Timespec;

public class KernelFSHandler extends FuseStubFS {

    public static final Map<String, String> map = new ConcurrentHashMap<>();
    private final Map<String, Object> pathLocks = new ConcurrentHashMap<>();

    private String host;
    private int port;

    public KernelFSHandler(String host, int port, int cacheSize, int maxFileCache) {
        this.port = port;
        this.host = host;
        CacheManager.start(cacheSize, maxFileCache);
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
        try {
            ListOperation listOperation = new ListOperation(path, filler, buf);
            ConnectionPool.addOperation(listOperation);
            listOperation.waitForCompletion();
        } catch (InterruptedException e) {
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
            MkdirOperation mkdirOperation = new MkdirOperation(path);
            ConnectionPool.addOperation(mkdirOperation);
            mkdirOperation.waitForCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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
            RmdirOperation rmdirOperation = new RmdirOperation(path);
            ConnectionPool.addOperation(rmdirOperation);
            rmdirOperation.waitForCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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
            CreateOperation createOperation = new CreateOperation(path);
            ConnectionPool.addOperation(createOperation);
            createOperation.waitForCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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
        CacheBlock block = CacheManager.getCache(path);
        if (block != null) {
            block.expired();
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
            UnlinkOperation unlinkOperation = new UnlinkOperation(path);
            ConnectionPool.addOperation(unlinkOperation);
            unlinkOperation.waitForCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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
            RenameOperation renameOperation = new RenameOperation(oldPath, newPath);
            ConnectionPool.addOperation(renameOperation);
            renameOperation.waitForCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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
            TruncateOperation truncateOperation = new TruncateOperation(path, size);
            ConnectionPool.addOperation(truncateOperation);
            truncateOperation.waitForCompletion();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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
                ReadOperation readOperation = new ReadOperation(path, buf, size, offset);
                ConnectionPool.addOperation(readOperation);
                readOperation.waitForCompletion();
                return readOperation.getBytesRead();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Writes data from the buffer pointer into your virtual storage.
     */
    @Override
    public int write(String path, Pointer buf, @size_t long size, @off_t long offset, FuseFileInfo fi) {
        try {
            WriteOperation writeOperation = new WriteOperation(path, buf, size, offset);
            ConnectionPool.addOperation(writeOperation);
            writeOperation.waitForCompletion();
            return writeOperation.getBytesWrote();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
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
}

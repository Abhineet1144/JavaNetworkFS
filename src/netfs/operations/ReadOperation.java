package netfs.operations;

import java.io.DataInputStream;
import java.io.IOException;

import jnr.ffi.Pointer;
import netfs.cache.CacheBlock;
import netfs.cache.CacheManager;
import netfs.client.ServerConnection;
import netfs.diskio.JNFSInputStream;

public class ReadOperation extends Operation {

    private final String path;
    private final Pointer buf;
    private final long size;
    private final long offset;
    private int bytesRead = 0;

    public ReadOperation(String path, Pointer buf, long size, long offset) {
        this.path = path;
        this.buf = buf;
        this.size = size;
        this.offset = offset;
        operationType = OperationType.READ;
    }

    @Override
    public String getDetail() {
        return path;
    }

    @Override
    public void execute(ServerConnection connection) throws IOException {
        try {
            Long prevOffset = CacheManager.getLastReadOffset().get(path);
            long jumpThreshold = (long) (CacheManager.getCacheSize() * CacheManager.getJumpThresholdMultiplier());
            boolean isBigJump = prevOffset != null && Math.abs(offset - prevOffset) > jumpThreshold;
            CacheManager.getLastReadOffset().put(path, offset);

            CacheBlock cacheBlock = CacheManager.getCache(path);

            if (isBigJump) {
                if (cacheBlock != null) {
                    CacheManager.deallocate(path);
                    cacheBlock = null;
                }
            }

            if (cacheBlock != null) {
                long cacheStart = cacheBlock.getCacheStartOffset();
                long cacheEnd = cacheStart + cacheBlock.getData().length;
                long requestEnd = offset + size;

                if (offset >= cacheStart && requestEnd <= cacheEnd) {
                    int start = (int) (offset - cacheStart);
                    int length = (int) size;
                    buf.put(0, cacheBlock.getData(), start, length);
                    bytesRead = length;
                    return;
                }
            }

            int fetchSize = isBigJump ? (int) size : Math.max((int) size, CacheManager.getCacheSize());
            byte[] data = getData(path, offset, fetchSize, connection);
            int copyLen = Math.min((int) size, data.length);
            buf.put(0, data, 0, copyLen);

            if (!isBigJump) {
                CacheBlock block = new CacheBlock(data, offset);
                CacheManager.allocate(path, block);
            }

            bytesRead = copyLen;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getData(String path, long offset, int cacheSize, ServerConnection connection) throws IOException {
        var i = connection.getInputStream();

        sendRequest("read:" + path + ":" + offset + ":" + cacheSize, connection);
        int resp = Integer.parseInt(JNFSInputStream.readLine(i));
        byte[] data = new byte[resp];
        new DataInputStream(i).readFully(data);
        return data;
    }

    public int getBytesRead() {
        return bytesRead;
    }
}

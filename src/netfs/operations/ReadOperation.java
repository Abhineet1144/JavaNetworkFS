package netfs.operations;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintWriter;

import jnr.ffi.Pointer;
import netfs.cache.CacheBlock;
import netfs.cache.CacheManager;
import netfs.client.ServerConnection;
import netfs.diskio.JNFSInputStream;
import netfs.diskio.KernelFSHandler;

public class ReadOperation extends Operation{

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
    public void execute(ServerConnection connection) throws IOException {
        try {
            Long prevOffset = CacheManager.getLastReadOffset().get(path);
            long jumpThreshold = (long) (CacheManager.getCacheSize() * CacheManager.getJumpThresholdMultiplier());
            boolean isBigJump = prevOffset != null && Math.abs(offset - prevOffset) > jumpThreshold;
            CacheManager.getLastReadOffset().put(path, offset);

            CacheBlock cacheBlock = CacheManager.getCache(path);

            if (isBigJump) {
                if (cacheBlock != null) {
                    CacheManager.evict(path);
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

                if (offset >= cacheStart && offset < cacheEnd && requestEnd > cacheEnd) {
                    int cachedBytes = (int) (cacheEnd - offset);
                    int missingBytes = (int) (requestEnd - cacheEnd);
                    int cacheStartIndex = (int) (offset - cacheStart);

                    buf.put(0, cacheBlock.getData(), cacheStartIndex, cachedBytes);

                    String mapEntry = KernelFSHandler.map.get(path);
                    long targetFileSize = Long.parseLong(mapEntry.split(":")[1]);

                    if (cacheEnd >= targetFileSize) {
                        bytesRead = cachedBytes;
                        return;
                    }

                    byte[] missingData = getData(path, cacheEnd, missingBytes, connection);
                    int missingLen = Math.min(missingData.length, missingBytes);
                    buf.put(cachedBytes, missingData, 0, missingLen);

                    byte[] existing = cacheBlock.getData();
                    byte[] merged = new byte[existing.length + missingLen];
                    System.arraycopy(existing, 0, merged, 0, existing.length);
                    System.arraycopy(missingData, 0, merged, existing.length, missingLen);
                    CacheManager.allocate(path, cacheStart, new CacheBlock(merged, cacheStart));

                    bytesRead = cachedBytes + missingLen;
                    return;
                }

                if (offset < cacheStart && requestEnd > cacheStart && requestEnd <= cacheEnd) {
                    int missingBytes = (int) (cacheStart - offset);
                    int cachedBytes = (int) (requestEnd - cacheStart);

                    byte[] missingData = getData(path, offset, missingBytes, connection);
                    int missingLen = Math.min(missingData.length, missingBytes);
                    buf.put(0, missingData, 0, missingLen);
                    buf.put(missingLen, cacheBlock.getData(), 0, cachedBytes);

                    byte[] existing = cacheBlock.getData();
                    byte[] merged = new byte[missingLen + existing.length];
                    System.arraycopy(missingData, 0, merged, 0, missingLen);
                    System.arraycopy(existing, 0, merged, missingLen, existing.length);
                    CacheManager.allocate(path, offset, new CacheBlock(merged, offset));

                    bytesRead = missingLen + cachedBytes;
                    return;
                }
            }

            var i = connection.getInputStream();

            new PrintWriter(connection.getOutputStream(), true).println(
                    "read:" + path + ":" + offset + ":" + size + ":" + CacheManager.getCacheSize());
            int resp = Integer.parseInt(JNFSInputStream.readLine(i));
            byte[] data = new byte[resp];
            new DataInputStream(i).readFully(data);

            int copyLen = Math.min((int) size, data.length);
            buf.put(0, data, 0, copyLen);

            if (!isBigJump) {
                CacheBlock block = new CacheBlock(data, offset);
                CacheManager.allocate(path, offset, block);
            }

            bytesRead = copyLen;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getData(String path, long offset, int cacheSize, ServerConnection connection) throws IOException {
        var i = connection.getInputStream();

        new PrintWriter(connection.getOutputStream(), true).println("read:" + path + ":" + offset + ":000:" + cacheSize);
        int resp = Integer.parseInt(JNFSInputStream.readLine(i));
        byte[] data = new byte[resp];
        new DataInputStream(i).readFully(data);
        return data;
    }

    public int getBytesRead() { return bytesRead; }
}

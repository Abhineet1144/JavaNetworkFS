package netfs.operations;

import java.io.DataInputStream;
import java.io.IOException;

import jnr.ffi.Pointer;
import netfs.cache.CacheBlock;
import netfs.cache.CacheManager;
import netfs.client.ServerConnection;
import netfs.diskio.JNFSInputStream;
import netfs.diskio.KernelFSHandler;

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
                    System.out.println("[CLIENT] Cache hit path=" + path + ", offset=" + offset + ", bytes=" + length);
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
                        System.out.println("[CLIENT] Cache hit path=" + path + ", offset=" + offset
                                + ", bytes=" + cachedBytes);
                        return;
                    }

                    byte[] missingData = getData(path, cacheEnd, missingBytes, connection);
                    int missingLen = Math.min(missingData.length, missingBytes);
                    buf.put(cachedBytes, missingData, 0, missingLen);

                    CacheManager.allocate(path, cacheStart,
                            mergeForward(cacheBlock.getData(), cacheStart, missingData, missingLen));

                    bytesRead = cachedBytes + missingLen;
                    System.out.println("[CLIENT] Cache partial hit path=" + path + ", offset=" + offset
                            + ", cachedBytes=" + cachedBytes + ", fetchedBytes=" + missingLen);
                    return;
                }

                if (offset < cacheStart && requestEnd > cacheStart && requestEnd <= cacheEnd) {
                    int missingBytes = (int) (cacheStart - offset);
                    int cachedBytes = (int) (requestEnd - cacheStart);

                    byte[] missingData = getData(path, offset, missingBytes, connection);
                    int missingLen = Math.min(missingData.length, missingBytes);
                    buf.put(0, missingData, 0, missingLen);
                    buf.put(missingLen, cacheBlock.getData(), 0, cachedBytes);

                    CacheManager.allocate(path, offset,
                            mergeBackward(missingData, missingLen, cacheBlock.getData(), cacheStart));

                    bytesRead = missingLen + cachedBytes;
                    System.out.println("[CLIENT] Cache partial hit path=" + path + ", offset=" + offset
                            + ", cachedBytes=" + cachedBytes + ", fetchedBytes=" + missingLen);
                    return;
                }
            }

            int fetchSize = Math.max((int) size, CacheManager.getCacheSize());
            System.out.println("[CLIENT] Cache miss path=" + path + ", offset=" + offset
                    + ", size=" + size + ", fetch=" + fetchSize);
            byte[] data = getData(path, offset, fetchSize, connection);
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

        sendRequest("read:" + path + ":" + offset + ":" + cacheSize, connection);
        int resp = Integer.parseInt(JNFSInputStream.readLine(i));
        byte[] data = new byte[resp];
        new DataInputStream(i).readFully(data);
        return data;
    }

    private static CacheBlock mergeForward(byte[] existing, long existingOffset, byte[] suffix, int suffixLength) {
        int maxCacheBytes = CacheManager.getCacheSize();
        int mergedLength = existing.length + suffixLength;
        int keptLength = Math.min(maxCacheBytes, mergedLength);
        byte[] merged = new byte[keptLength];
        int dropped = mergedLength - keptLength;
        long newOffset = existingOffset + dropped;

        copyWindow(existing, 0, existing.length, suffix, suffixLength, dropped, merged);
        return new CacheBlock(merged, newOffset);
    }

    private static CacheBlock mergeBackward(byte[] prefix, int prefixLength, byte[] existing, long existingOffset) {
        int maxCacheBytes = CacheManager.getCacheSize();
        int mergedLength = prefixLength + existing.length;
        int keptLength = Math.min(maxCacheBytes, mergedLength);
        byte[] merged = new byte[keptLength];

        copyWindow(prefix, 0, prefixLength, existing, existing.length, 0, merged);
        return new CacheBlock(merged, existingOffset - prefixLength);
    }

    private static void copyWindow(
            byte[] first,
            int firstOffset,
            int firstLength,
            byte[] second,
            int secondLength,
            int skip,
            byte[] target) {
        int targetOffset = 0;
        int firstCopyStart = Math.min(firstLength, skip);
        int firstCopyLength = firstLength - firstCopyStart;
        if (firstCopyLength > 0) {
            System.arraycopy(first, firstOffset + firstCopyStart, target, targetOffset, firstCopyLength);
            targetOffset += firstCopyLength;
        }

        int secondSkip = Math.max(0, skip - firstLength);
        int secondCopyLength = Math.min(secondLength - secondSkip, target.length - targetOffset);
        if (secondCopyLength > 0) {
            System.arraycopy(second, secondSkip, target, targetOffset, secondCopyLength);
        }
    }

    public int getBytesRead() {
        return bytesRead;
    }
}

package netfs.cache;

/** An immutable, bounded window of a file. */
public final class CacheBlock {
    private final byte[] data;
    private final long cacheStartOffset;
    private volatile boolean isExpired;

    public CacheBlock(byte[] data, long cacheStartOffset) {
        this.data = data;
        this.cacheStartOffset = cacheStartOffset;
    }

    public byte[] getData() {
        return data;
    }

    public long getCacheStartOffset() {
        return cacheStartOffset;
    }

    public void expired() {
        isExpired = true;
    }

    public boolean isExpired() {
        return isExpired;
    }

    public boolean contains(long offset, long length) {
        long end = offset + length;
        long cacheEnd = cacheStartOffset + data.length;
        return offset >= cacheStartOffset && end >= offset && end <= cacheEnd;
    }
}

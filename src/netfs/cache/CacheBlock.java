package netfs.cache;

public class CacheBlock {
    private final byte[] data;
    private final long cacheStartOffset;
    private boolean isExpired;

    public CacheBlock(byte[] data, long cacheStartOffset) {
        this.data = data;
        this.cacheStartOffset = cacheStartOffset;
        this.isExpired = false;
    }

    public byte[] getData() {
        isExpired = false;
        return data;
    }

    public long getCacheStartOffset() {
        isExpired = false;
        return cacheStartOffset;
    }

    public void expired() {
        isExpired = true;
    }

    public boolean isExpired() {
        return isExpired;
    }
}
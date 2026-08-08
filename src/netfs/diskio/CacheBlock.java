package netfs.diskio;

public class CacheBlock {
    private final byte[] data;
    private final int cacheStartOffset;

    public CacheBlock(byte[] data, int cacheStartOffset) {
        this.data = data;
        this.cacheStartOffset = cacheStartOffset;
    }

    public byte[] getData() {
        return data;
    }

    public int getCacheStartOffset() {
        return cacheStartOffset;
    }
}
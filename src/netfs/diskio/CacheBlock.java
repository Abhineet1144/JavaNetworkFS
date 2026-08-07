package netfs.diskio;

public class CacheBlock {
    private final long startOffset;
    private final long endOffset;
    private final byte[] data;

    public CacheBlock(byte[] data, long startOffset, long endOffset) {
        this.data = data;
        this.endOffset = endOffset;
        this.startOffset = startOffset;
    }

    public long getStartOffset() {
        return startOffset;
    }

    public long getEndOffset() {
        return endOffset;
    }

    public byte[] getData() {
        return data;
    }
}
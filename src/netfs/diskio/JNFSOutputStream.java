package netfs.diskio;

import netfs.handler.OperationHandler;

import java.io.*;

public class JNFSOutputStream extends BufferedOutputStream {
    private final OperationHandler operationHandler;

    public JNFSOutputStream(OutputStream out, OperationHandler operationHandler) {
        super(out);
        this.operationHandler = operationHandler;
    }

    @Override
    public void write(int b) throws IOException {
        operationHandler.incrementBytesWritten();
        super.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        operationHandler.addBytesWritten(len - off);
        super.write(b, off, len);
    }

    public void writeFileChunk(File file, long offset, int limit) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.getAbsoluteFile(), "r")) {
            long available = Math.max(0, raf.length() - offset);
            int total = (int) Math.min(Math.max(0, (long) limit), available);
            raf.seek(offset);
            writeLine(this, total + "");

            // Keep server-side memory fixed even when the cache window is large.
            byte[] buffer = new byte[Math.min(16 * 1024, Math.max(1, total))];
            int remaining = total;
            while (remaining > 0) {
                int read = raf.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                write(buffer, 0, read);
                remaining -= read;
            }
            flush();
        }
    }

    public static void writeLine(OutputStream out, String line) throws IOException {
        out.write((line + '\n').getBytes());
        out.flush();
    }
}

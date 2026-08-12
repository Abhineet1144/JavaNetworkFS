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

    public int writeFileChunk(File file, long offset, int limit) throws IOException {
        final int bufferSize = 64 * 1024;

        try (RandomAccessFile raf = new RandomAccessFile(file.getAbsoluteFile(), "r")) {
            long available = Math.max(0, file.length() - offset);
            int bytesToSend = (int) Math.min(limit, available);

            writeLine(this, String.valueOf(bytesToSend));
            raf.seek(offset);

            byte[] buffer = new byte[Math.min(bufferSize, Math.max(bytesToSend, 1))];
            int remaining = bytesToSend;

            while (remaining > 0) {
                int readSize = Math.min(buffer.length, remaining);
                int read = raf.read(buffer, 0, readSize);

                if (read == -1) {
                    break;
                }

                write(buffer, 0, read);
                remaining -= read;
            }

            flush();
            return bytesToSend - remaining;
        }
    }

    public static void writeLine(OutputStream out, String line) throws IOException {
        out.write((line + '\n').getBytes());
        out.flush();
    }
}

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
        RandomAccessFile raf = new RandomAccessFile(file.getAbsoluteFile(), "r");
        raf.seek(offset);
        byte[] buffer = new byte[limit];
        int read = raf.read(buffer, 0, limit);
        System.out.println(read);
        writeLine(this, read + "");
        write(buffer, 0, read);
        flush();
    }

    public static void writeLine(OutputStream out, String line) throws IOException {
        out.write((line + '\n').getBytes());
        out.flush();
    }
}

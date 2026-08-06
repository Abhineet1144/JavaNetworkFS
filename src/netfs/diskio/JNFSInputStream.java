package netfs.diskio;

import netfs.handler.OperationHandler;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public class JNFSInputStream extends BufferedInputStream {
    private final OperationHandler operationHandler;

    public JNFSInputStream(InputStream in, OperationHandler operationHandler) {
        super(in);
        this.operationHandler = operationHandler;
    }

    @Override
    public int read() throws IOException {
        operationHandler.incrementBytesRead();
        return super.read();
    }

    public static String readLine(InputStream input) throws IOException {
        StringBuilder line = new StringBuilder();

        while (true) {
            int value = input.read();
            if (value == -1) {
                if (line.isEmpty()) {
                    return "";
                }
            }
            if (value == '\n') {
                return line.toString();
            }
            line.append((char) value);
        }
    }
}

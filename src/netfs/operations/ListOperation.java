package netfs.operations;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import jnr.ffi.Pointer;
import netfs.client.ServerConnection;
import netfs.diskio.JNFSInputStream;
import netfs.diskio.KernelFSHandler;
import ru.serce.jnrfuse.FuseFillDir;

public class ListOperation extends Operation {

    private final String path;
    private final FuseFillDir filler;
    private final Pointer buf;

    public ListOperation(String path, FuseFillDir filler, Pointer buf) {
        this.path = path;
        this.filler = filler;
        this.buf = buf;
        operationType = OperationType.LIST;
    }

    @Override
    public String getDetail() {
        return path;
    }

    @Override
    public void execute(ServerConnection connection) throws IOException {
        sendRequest("ls:" + path, connection);
        var input = connection.getInputStream();

        String li;
        while ((li = JNFSInputStream.readLine(input)) != null && !li.isEmpty()) {
            String li2 = JNFSInputStream.readLine(input);
            if (li2 == null) {
                throw new IOException("Connection closed while reading metadata for " + path + "/" + li);
            }
            KernelFSHandler.map.put(path + (path.endsWith("/") ? "" : "/") + li, li2);
            filler.apply(buf, li, null, 0);
        }
        if (li == null) {
            throw new IOException("Connection closed while listing " + path);
        }
    }
}

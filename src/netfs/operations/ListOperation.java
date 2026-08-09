package netfs.operations;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

import jnr.ffi.Pointer;
import netfs.client.ServerConnection;
import netfs.diskio.JNFSInputStream;
import ru.serce.jnrfuse.FuseFillDir;

public class ListOperation extends Operation {

    private final String path;
    private final FuseFillDir filler;
    private final Pointer buf;
    private final Map<String, String> map;

    public ListOperation(String path, FuseFillDir filler, Pointer buf, Map<String, String> map) {
        this.path = path;
        this.filler = filler;
        this.buf = buf;
        this.map = map;
        operationType = OperationType.LIST;
    }

    @Override
    public void execute(ServerConnection connection) throws IOException {
        new PrintWriter(connection.getOutputStream(), true).println("ls:" + path);

        var input = connection.getInputStream();

        String li;
        while ((li = JNFSInputStream.readLine(input)) != null && !li.isEmpty()) {
            String li2 = JNFSInputStream.readLine(input);
            if (li2 == null) {
                throw new IOException("Connection closed while reading metadata for " + path + "/" + li);
            }
            map.put(path + (path.endsWith("/") ? "" : "/") + li, li2);
            filler.apply(buf, li, null, 0);
        }
        if (li == null) {
            throw new IOException("Connection closed while listing " + path);
        }
    }
}

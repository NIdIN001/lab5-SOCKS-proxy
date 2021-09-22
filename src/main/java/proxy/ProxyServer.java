package proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.Iterator;
import java.util.Set;

public class ProxyServer {
    private int srcPort;

    private ServerSocketChannel serverSocket;
    private Selector selector;

    public ProxyServer(int srcPort_) {
        srcPort = srcPort_;
        try {
            selector = Selector.open();
            serverSocket = ServerSocketChannel.open();
            serverSocket.bind(new InetSocketAddress("localhost", srcPort));
            serverSocket.configureBlocking(false);
            serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException exception) {
            System.out.println("can't create socket");
            return;
        }

    }

    public void run() throws IOException {
        while (true) {
            int count = selector.select();
            if (count == 0) {
                continue;
            }

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iter = selectedKeys.iterator();

            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                if (key.isAcceptable()) {
                    //todo acceptConnection
                }

                if (key.isReadable()) {
                    // todo resend data
                }
                iter.remove();
            }


        }
    }
}

package proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;

public class ProxyServer {
    private final int BufferSize = 100;
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
                    acceptConnection();
                }

                if (key.isReadable()) {
                    resendData();
                }
                iter.remove();
            }
        }
    }

    private void acceptConnection() throws IOException {
        SocketChannel client = serverSocket.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ);

        ByteBuffer recvBuffer = ByteBuffer.allocate(BufferSize);
        ByteBuffer sendBuffer = ByteBuffer.allocate(BufferSize);
        client.read(recvBuffer);
        if (recvBuffer.get(0) != 0x05 | recvBuffer.get(2) != 0x00) { // SOCKS version (0x05)
            System.out.println("bad handshake by user");
            client.close();
        }
        recvBuffer.clear();

        sendBuffer.put(0, (byte) 0x05);
        sendBuffer.put(1, (byte) 0x00);
        client.write(sendBuffer);
        sendBuffer.clear();

        client.read(recvBuffer);
        if (recvBuffer.get(0) != 0x05 | recvBuffer.get(1) != 0x01 | recvBuffer.get(2) != 0x00) {
            System.out.println("bad handshake by user");
            client.close();
        }

        byte[] destIP = new byte[4];
        byte[] destPort = new byte[2];
        System.arraycopy(recvBuffer.array(), 3, destIP, 0, 4);
        System.arraycopy(recvBuffer.array(), 7, destPort, 0, 2);
        recvBuffer.clear();

        sendBuffer.put(0, (byte) 0x05);
        sendBuffer.put(1, (byte) 0x00);
        sendBuffer.put(2, (byte) 0x00);
        //todo доделать https://en.wikipedia.org/wiki/SOCKS

    }

    private void resendData() {

    }
}

package proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;


public class ProxyServer {
    private final int BufferSize = 100;
    private final String DnsServer = "8.8.8.8";
    private final int DnsPort = 53;

    private int srcPort;
    private ServerSocketChannel serverSocket;
    private Selector selector;
    private DatagramChannel udpDnsResolver;

    private ArrayList<ConnectionTunnel> tunnels;

    public ProxyServer(int srcPort_) {
        srcPort = srcPort_;
        tunnels = new ArrayList<>();

        try {
            selector = Selector.open();

            serverSocket = ServerSocketChannel.open();
            serverSocket.bind(new InetSocketAddress("localhost", srcPort));

            udpDnsResolver = createUdpResolver(DnsServer);

            udpDnsResolver.configureBlocking(false);
            serverSocket.configureBlocking(false);

            serverSocket.register(selector, SelectionKey.OP_ACCEPT);
            udpDnsResolver.register(selector, SelectionKey.OP_READ);
        } catch (IOException exception) {
            System.out.println("can't create socket");
        }
    }

    private DatagramChannel createUdpResolver(String dnsServer) throws IOException {
        return DatagramChannel.open().connect(new InetSocketAddress(dnsServer, DnsPort));
    }

    public void run() throws IOException {
        while (true) {
            if (selector.select() == 0) {
                continue;
            }

            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (!key.isValid())
                    continue;

                if (key.isAcceptable())
                    acceptConnection(key);

                if (key.isReadable())
                    resendData(key);

            }
        }
    }

    private void acceptConnection(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();


    }

    private void resendData(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        findTunnelBySocketChannel(channel).resendData(channel);
    }

    private ConnectionTunnel findTunnelBySocketChannel(SocketChannel socket) {
        for (ConnectionTunnel tunnel : tunnels) {
            if (tunnel.getClient().equals(socket) | tunnel.getDestServer().equals(socket))
                return tunnel;
        }
        throw new IllegalArgumentException();
    }

    private InetSocketAddress createIpv4Addr(ByteBuffer recvBuffer) throws UnknownHostException {
        ByteBuffer ipv4Addr = ByteBuffer.allocate(4);
        ipv4Addr.put(recvBuffer.slice(4, 4));
        return new InetSocketAddress(InetAddress.getByAddress(ipv4Addr.array()), recvBuffer.getShort(4));
    }

    private InetSocketAddress createDomainNameAddr(ByteBuffer recvBuffer) throws UnknownHostException {
        //todo resolve ip dy domain name
        return null;
    }

    private boolean isCorrectGreeting(ByteBuffer recvBuffer) {
        int clientAuthMethodsCount = recvBuffer.get(1);
        boolean isWithNoAuthentication = false;
        for (int i = 0; i < clientAuthMethodsCount; i++) {
            if (recvBuffer.get(i + 2) == 0x00) {
                isWithNoAuthentication = true;
                break;
            }
        }
        // SOCKS version (0x05)
        return !(recvBuffer.get(0) != 0x05 | !isWithNoAuthentication);
    }
}

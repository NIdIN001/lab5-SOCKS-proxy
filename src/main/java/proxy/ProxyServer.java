package proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class ProxyServer {
    private final static Logger logger = LogManager.getLogger(ProxyServer.class);

    private int srcPort;
    private ServerSocketChannel serverSocket;
    private Selector selector;
    private DnsResolver dnsResolver;
    private ArrayList<ConnectionTunnel> tunnels;

    public ProxyServer(int srcPort_) throws ProxyServerException {
        srcPort = srcPort_;
        tunnels = new ArrayList<>();

        try {
            selector = Selector.open();

            serverSocket = ServerSocketChannel.open();
            serverSocket.bind(new InetSocketAddress("localhost", srcPort));

            serverSocket.configureBlocking(false);
            dnsResolver = new DnsResolver(false);

            serverSocket.register(selector, SelectionKey.OP_ACCEPT);
            dnsResolver.getChannel().register(selector, SelectionKey.OP_READ);
        } catch (IOException | DnsNotFoundException exception) {
            throw new ProxyServerException();
        }
    }

    public Selector getSelector() {
        return selector;
    }

    public void run() throws IOException, ProxyServerException {
        System.out.println("Proxy server up on:");
        System.out.println(serverSocket.socket().getInetAddress().getHostAddress() + ":" + serverSocket.socket().getLocalPort());
        logger.info("Proxy server up on:");
        logger.info(serverSocket.socket().getInetAddress().getHostAddress() + ":" + serverSocket.socket().getLocalPort());

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

                if (key.isReadable() & (dnsResolver.getChannel() == key.channel())) {
                    //случился resolve dns для какого-то соединения
                    AsyncDnsResolverAnswer answer = dnsResolver.asyncResolveResponse();
                    System.out.println("new dns answer: id " + answer.requestId + " ip: " + answer.ipAddress.get(0));
                    logger.info("new dns answer: id " + answer.requestId + " ip: " + answer.ipAddress.get(0));

                    for (ConnectionTunnel tunnel : tunnels) {
                        if (tunnel.getDnsRequestId() == answer.requestId) {
                            tunnel.setDestServer(answer);
                            break;
                        }
                    }
                    continue;
                }

                if (key.isAcceptable()) {
                    acceptConnection();
                    continue;
                }
                if (key.isReadable()) {
                    processClient(key);
                    continue;
                }
            }
        }
    }

    private void acceptConnection() {
        try {
            SocketChannel client = serverSocket.accept();
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ);
            ConnectionTunnel tunnel = new ConnectionTunnel(this, dnsResolver, client);
            tunnels.add(tunnel);
        } catch (IOException exception) {
            System.out.println("can't accept new connection");
            logger.error("can't accept new connection");
        }
    }

    private void processClient(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ConnectionTunnel tunnel = findTunnelBySocketChannel(channel);

        if (tunnel.isConfigured())
            tunnel.resendData(channel);
        else
            tunnel.configureConnection();
    }

    public void removeConnection(ConnectionTunnel tunnel) {
        tunnels.remove(tunnel);
    }

    private ConnectionTunnel findTunnelBySocketChannel(SocketChannel socket) {
        for (ConnectionTunnel tunnel : tunnels) {
            if (tunnel.getClient().equals(socket))
                return tunnel;

            if (tunnel.getDestServer() != null && tunnel.getDestServer().equals(socket))
                return tunnel;
        }
        throw new IllegalArgumentException();
    }
}

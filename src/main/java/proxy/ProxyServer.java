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

    public ProxyServer(int srcPort_) {
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
            System.out.println("can't create socket");
        }
    }

    public void run() throws IOException, ProxyServerException {
        System.out.println("Proxy server up on:");
        System.out.println(serverSocket.socket().getInetAddress().getHostAddress() + ":" + serverSocket.socket().getLocalPort());

        while (true) {
            if (selector.select() == 0) {
                continue;
            }

            System.out.println("new event");
            //logger.info("new event");

            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (!key.isValid())
                    continue;

                if (key.isReadable() & (dnsResolver.getChannel() == key.channel())) {
                    //случился resolve dns для какого-то соединения
                    AsyncDnsResolverAnswer answer = dnsResolver.asyncResolveResponse();
                    System.out.println("new dns answer: id" + answer.requestId + " ip: " + answer.ipAddress.get(0));
                    logger.info("new dns answer: id" + answer.requestId + " ip: " + answer.ipAddress.get(0));

                    for (ConnectionTunnel tunnel : tunnels) {
                        if (tunnel.getDnsRequestId() == answer.requestId) {
                            tunnel.setDestServer(answer);
                        }
                    }
                    break;
                }

                if (key.isAcceptable()) {
                    acceptConnection();
                    System.out.println("new connection");
                    //logger.info("new connection");
                    break;
                }
                if (key.isReadable()){
                    processClient(key);
                    //System.out.println("new readable socket");
                    //logger.info("new readable socket");
                    break;
                }
            }
        }
    }

    private void acceptConnection() throws IOException {
        SocketChannel client = serverSocket.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ);
        ConnectionTunnel tunnel = new ConnectionTunnel(selector, dnsResolver);
        tunnel.setClient(client);
        tunnels.add(tunnel);
    }

    private void processClient(SelectionKey key) throws IOException, ProxyServerException {
        SocketChannel channel = (SocketChannel) key.channel();
        ConnectionTunnel tunnel = findTunnelBySocketChannel(channel);

        System.out.println("read from socket: " + ((SocketChannel) key.channel()).socket().getInetAddress().getHostName() + ":" + ((SocketChannel) key.channel()).socket().getPort());
        if (tunnel.isConfigured())
            tunnel.resendData(channel);
        else
            tunnel.configureConnection();
    }

    private ConnectionTunnel findTunnelBySocketChannel(SocketChannel socket) {
        for (ConnectionTunnel tunnel : tunnels) {
            if (tunnel.getClient().equals(socket)) // || tunnel.getDestServer().equals(socket))
                return tunnel;

            if (tunnel.getDestServer() != null && tunnel.getDestServer().equals(socket))
                return tunnel;
        }
        throw new IllegalArgumentException();
    }
}

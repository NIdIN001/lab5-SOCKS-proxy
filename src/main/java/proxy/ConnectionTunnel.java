package proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class ConnectionTunnel {
    private int BufferSize = 4096;

    private SocketChannel client;
    private SocketChannel destServer;

    private Selector serverSelector;
    private DnsResolver dnsResolver;

    private int dnsRequestId;
    private boolean isConfigured;
    private boolean isWaitingDnsResponse;
    private int stepOfAuthentication;

    private int requestMode;
    private String destResource;
    private int destPort;

    public ConnectionTunnel(Selector selector, DnsResolver dnsResolver) {
        serverSelector = selector;
        this.dnsResolver = dnsResolver;
        client = null;
        destServer = null;
        isConfigured = false;
        isWaitingDnsResponse = false;
        stepOfAuthentication = 0;
        dnsRequestId = -1;
    }

    public ConnectionTunnel(SocketChannel client, SocketChannel destServer, Selector selector, DnsResolver dnsResolver) {
        this.client = client;
        this.destServer = destServer;
        this.dnsResolver = dnsResolver;
        serverSelector = selector;
    }

    public boolean isConfigured() {
        return isConfigured;
    }

    public SocketChannel getClient() {
        return client;
    }

    public SocketChannel getDestServer() {
        return destServer;
    }

    public void setClient(SocketChannel client) throws IOException {
        this.client = client;
    }

    public int getDnsRequestId() {
        return dnsRequestId;
    }

    //todo обратобка разного рода ошибок а также закрытия сокета

    public void setDestServer(AsyncDnsResolverAnswer answer) throws IOException {
        if(isWaitingDnsResponse) {
            createDestServerSocket(getAsyncDnsResponse(answer));
        }
    }

    public void configureConnection() throws IOException, ProxyServerException {
        try {
            ByteBuffer recvBuffer = ByteBuffer.allocate(BufferSize);
            int read = client.read(recvBuffer);
            if (read == -1) {
                System.out.println("remove: " + client.socket().getInetAddress().getHostName() + ":" + client.socket().getPort() );
                client.close();
                client.keyFor(serverSelector).cancel();
            }

            System.out.println("RECV:");
            for (int i = 0; i< read; i++){
                System.out.print(recvBuffer.get(i) + " ");
            }
            System.out.println(" ");

            if (stepOfAuthentication == 0 & isCorrectFirstGreeting(recvBuffer)) {
                ByteBuffer sendBuf = ByteBuffer.allocate(2);
                System.out.println("SEND:");

                sendBuf.put(0, (byte) 0x05);
                sendBuf.put(1, (byte) 0x00);
                for (int i = 0; i < 2; i++) {
                    System.out.print(sendBuf.get(i) + " ");
                }
                System.out.println(" ");
                client.write(sendBuf);
                stepOfAuthentication++;
                return;
            }

            InetSocketAddress destAddr;
            if (stepOfAuthentication == 1 & isCorrectSecondGreeting(recvBuffer.array())) {
                switch (recvBuffer.get(3)) {
                    case 0x01 -> {
                        requestMode = 0x01;
                        destAddr = createAddrByIpv4(recvBuffer);
                    }
                    case 0x03 -> {
                        requestMode = 0x03;
                        makeAsyncDnsRequest(recvBuffer);
                        return;
                    }
                    default -> throw new ProxyServerException();
                }

                createDestServerSocket(destAddr);
            }


        } catch (IOException | ProxyServerException exception) {
            throw exception;
        }
    }

    private void createDestServerSocket(InetSocketAddress destAddr) throws IOException {
        destServer = SocketChannel.open();
        destServer.configureBlocking(false);
        destServer.connect(destAddr);
        destServer.register(serverSelector, SelectionKey.OP_READ);

        //this.destServerInputStream = new BufferedInputStream(destServer.socket().getInputStream());
        //this.destServerOutputStream = new BufferedOutputStream(destServer.socket().getOutputStream());

        isConfigured = true;
        ByteBuffer sendBuffer = ByteBuffer.allocate(4 + destResource.getBytes().length + 2);
        sendBuffer.put(0, (byte) 0x05);
        sendBuffer.put(1, (byte) 0x00);
        sendBuffer.put(2, (byte) 0x00);
        sendBuffer.put(3, (byte) requestMode);
        sendBuffer.put(4, destResource.getBytes());
        sendBuffer.putShort(4 + destResource.getBytes().length, (short) destPort);
        client.write(sendBuffer);
        System.out.println("Connection success!");
    }

    public void resendData(SocketChannel socket) {
        if (socket.equals(destServer))
            sendToClient();
        else
            sendToDestServer();
    }

    private void sendToDestServer() {
        try {
            ByteBuffer recvBuffer = ByteBuffer.allocate(BufferSize);
            int read = client.read(recvBuffer);

            destServer.write(recvBuffer);
        } catch (IOException | NotYetConnectedException exception) {
            System.out.println("client " + client.socket().getInetAddress().getHostName() + " close connection");
        }
    }

    private void sendToClient() {
        try {
            ByteBuffer recvBuffer = ByteBuffer.allocate(BufferSize);
            int read = destServer.read(recvBuffer);
            client.write(recvBuffer);
        } catch (IOException exception) {

        }
    }

    private InetSocketAddress createAddrByIpv4(ByteBuffer recvBuffer) throws UnknownHostException {
        ByteBuffer ipv4Addr = ByteBuffer.allocate(4);
        ipv4Addr.put(recvBuffer.slice(4, 4));
        destResource = new String(recvBuffer.slice(4, 4).array());
        destPort = recvBuffer.getShort(4);

        return new InetSocketAddress(InetAddress.getByAddress(ipv4Addr.array()), destPort);
    }

    private void makeAsyncDnsRequest(ByteBuffer recvBuffer) throws IOException {
        int urlLength = recvBuffer.get(4);
        ByteBuffer url = ByteBuffer.allocate(urlLength);
        System.arraycopy(recvBuffer.array(), 5, url.array(), 0, urlLength);
        //ByteBuffer url = recvBuffer.slice(5, urlLength);
        String str = new String(url.array());
        destPort = recvBuffer.getShort(5 + urlLength);
        isWaitingDnsResponse = true;

        dnsRequestId = dnsResolver.asyncResolveRequest(new String(url.array()));
    }

    private InetSocketAddress getAsyncDnsResponse(AsyncDnsResolverAnswer answer) {
        isWaitingDnsResponse = false;
        destResource = answer.ipAddress.get(0);
        return new InetSocketAddress(destResource, destPort);
    }

    private boolean isCorrectFirstGreeting(ByteBuffer recvBuffer) {
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

    private boolean isCorrectSecondGreeting(byte[] recvBuffer) {
        return (recvBuffer[0] == 0x05 & recvBuffer[1] == 0x01 & recvBuffer[2] == 0x00);
    }
}

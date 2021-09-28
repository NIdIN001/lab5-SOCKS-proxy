package proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;

import org.xbill.DNS.*;

public class DnsResolver {
    private final String DnsServer = "8.8.8.8";
    private final int DnsPort = 53;

    private DatagramChannel udpDnsResolver;

    public DnsResolver() throws IOException {
        try {
            udpDnsResolver = createUdpResolver();
        } catch (IOException exception) {
            System.out.println("Can't create DNS resolver");
            throw exception;
        }
    }

    public DatagramChannel getChannel() {
        return udpDnsResolver;
    }

    public void resolve(String url) {


    }

    private DatagramChannel createUdpResolver() throws IOException {
        return DatagramChannel.open().connect(new InetSocketAddress(DnsServer, DnsPort));
    }
}

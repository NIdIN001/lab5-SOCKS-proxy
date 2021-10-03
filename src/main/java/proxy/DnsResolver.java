package proxy;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xbill.DNS.*;
import org.xbill.DNS.Record;

public class DnsResolver {
    private final String IPV4_PATTERN = "((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])";

    private final int BufferSize = 1024;
    private String DnsServer;
    private final int DnsPort = 53;

    private DatagramChannel udpDnsResolver;

    public DnsResolver() throws IOException, DnsNotFoundException {
        try {
            udpDnsResolver = createUdpResolver(findDnsServer());
            //udpDnsResolver.configureBlocking(false);
        } catch (IOException | DnsNotFoundException exception) {
            System.out.println("Can't create DNS resolver");
            throw exception;
        }
    }

    public DatagramChannel getChannel() {
        return udpDnsResolver;
    }

    public ArrayList<String> resolve(String addr) throws IOException {
        Record queryRecord = Record.newRecord(Name.fromString(addr + "."), Type.A, DClass.IN);
        Message queryMessage = Message.newQuery(queryRecord);
        return resolve(queryMessage);
    }

    public ArrayList<String> resolve(Message msg) throws IOException {
        udpDnsResolver.write(ByteBuffer.wrap(msg.toWire()));

        byte[] recvBuffer = new byte[BufferSize];
        udpDnsResolver.read(ByteBuffer.wrap(recvBuffer));
        Message response = new Message(recvBuffer);

        return parseDnsResponse(response);
    }

    private ArrayList<String> parseDnsResponse(Message response) {
        Pattern ipv4 = Pattern.compile(IPV4_PATTERN);
        Matcher matcher = ipv4.matcher(response.toString());

        ArrayList<String> listMatches = new ArrayList<>();

        while (matcher.find()) {
            listMatches.add(matcher.group());
        }

        return listMatches;
    }

    private DatagramChannel createUdpResolver(InetSocketAddress dnsServer) throws IOException {
        return DatagramChannel.open().connect(dnsServer);
    }

    private InetSocketAddress findDnsServer() throws DnsNotFoundException {
        List<InetSocketAddress> dnsServers = ResolverConfig.getCurrentConfig().servers();
        if (dnsServers.size() == 0)
            throw new DnsNotFoundException();

        return dnsServers.get(0);
    }
}

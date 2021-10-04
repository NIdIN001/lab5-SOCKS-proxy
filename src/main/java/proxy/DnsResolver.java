package proxy;

import java.io.IOException;
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
    private final String REQUEST_ID_PATTERN = "id: \\d+";

    private final int BufferSize = 1024;

    private final DatagramChannel udpDnsResolver;

    public DnsResolver(boolean isBlocking) throws DnsNotFoundException, IOException {
        udpDnsResolver = createUdpResolver(findDnsServer());
        udpDnsResolver.configureBlocking(isBlocking);
    }

    public DatagramChannel getChannel() {
        return udpDnsResolver;
    }

    public int asyncResolveRequest(String addr) throws IOException {
        Record queryRecord = Record.newRecord(Name.fromString(addr + "."), Type.A, DClass.IN);
        Message queryMessage = Message.newQuery(queryRecord);
        return asyncResolveRequest(queryMessage);
    }

    public AsyncDnsResolverAnswer asyncResolveResponse() throws IOException {
        byte[] recvBuffer = new byte[BufferSize];
        udpDnsResolver.read(ByteBuffer.wrap(recvBuffer));
        AsyncDnsResolverAnswer answer = new AsyncDnsResolverAnswer();

        Message response = new Message(recvBuffer);
        answer.requestId = findRequestId(response);
        answer.ipAddress = parseDnsResponse(response);
        return answer;
    }

    public int asyncResolveRequest(Message msg) throws IOException {
        udpDnsResolver.write(ByteBuffer.wrap(msg.toWire()));
        return findRequestId(msg);
    }

    public ArrayList<String> syncResolve(String addr) throws IOException {
        Record queryRecord = Record.newRecord(Name.fromString(addr + "."), Type.A, DClass.IN);
        Message queryMessage = Message.newQuery(queryRecord);
        return syncResolve(queryMessage);
    }

    public ArrayList<String> syncResolve(Message msg) throws IOException {
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

    private int findRequestId(Message msg) {
        Pattern idPattern = Pattern.compile(REQUEST_ID_PATTERN);
        Matcher matcher = idPattern.matcher(msg.toString());

        if (matcher.find())
            return Integer.parseInt(matcher.group().substring(4));
        else
            throw new NumberFormatException();
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

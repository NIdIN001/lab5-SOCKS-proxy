import org.xbill.DNS.*;
import org.xbill.DNS.Record;
import proxy.DnsNotFoundException;
import proxy.DnsResolver;
import proxy.ProxyServer;

import java.io.IOException;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class Application {
    private static final int Port = 0;

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException, DnsNotFoundException {
        /*
        if (args.length != 1) {
            System.out.println("You must enter port as argument!");
            return;
        }

        try {
            int srcPort = Integer.parseInt(args[Port]);

            ProxyServer server = new ProxyServer(srcPort);
            server.run();
        } catch (NumberFormatException exception) {
            System.out.println("Port must be an integer value");
        } catch (IOException exception) {
            System.out.println("Socket error");
        }
    }

         */

/*
        Record queryRecord = Record.newRecord(Name.fromString("google.com."), Type.A, DClass.IN);
        Message queryMessage = Message.newQuery(queryRecord);

        System.out.println("REQUEST:\n" + queryMessage.toString());
        System.out.println("END REQUEST");
*/
        DnsResolver dnsResolver = new DnsResolver();
        ArrayList<String> ip = dnsResolver.resolve("google.com");

        for (String addr : ip) {
            System.out.println(addr);
        }

    }
}

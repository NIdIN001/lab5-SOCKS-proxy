import proxy.ProxyServer;
import proxy.ProxyServerException;

import java.io.IOException;

public class Application {
    private static final int Port = 0;

    public static void main(String[] args) {
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
            exception.printStackTrace();
        } catch (ProxyServerException exception) {
            System.out.println("can't startup server");
        }
    }
}

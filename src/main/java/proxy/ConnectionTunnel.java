package proxy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.channels.SocketChannel;

public class ConnectionTunnel {
    private SocketChannel client;
    private SocketChannel destServer;

    private InputStream clientInputStream;
    private OutputStream clientOutputStream;

    private InputStream destServerInputStream;
    private OutputStream destServerOutputStream;

    public ConnectionTunnel() {
        client = null;
        destServer = null;
    }

    public ConnectionTunnel(SocketChannel client, SocketChannel destServer) {
        this.client = client;
        this.destServer = destServer;

        try {
            this.clientInputStream = new BufferedInputStream(client.socket().getInputStream());
            this.clientOutputStream = new BufferedOutputStream(destServer.socket().getOutputStream());

            this.destServerInputStream = new BufferedInputStream(destServer.socket().getInputStream());
            this.destServerOutputStream = new BufferedOutputStream(destServer.socket().getOutputStream());
        } catch (IOException exception) {

        }
    }

    public SocketChannel getClient() {
        return client;
    }

    public SocketChannel getDestServer() {
        return destServer;
    }

    public void setClient(SocketChannel client) {
        if (this.client == null)
            this.client = client;
    }

    public void setDestServer(SocketChannel destServer) {
        if (this.destServer == null)
            this.destServer = destServer;
    }

    public void resendData(SocketChannel socket) {
        if (socket.equals(destServer))
            sendToClient();
        else
            sendToDestServer();
    }

    private void sendToDestServer() {
        try {
            byte[] byteBuffer = new byte[clientInputStream.available()];
            int read = clientInputStream.read(byteBuffer);
            destServerOutputStream.write(byteBuffer);
        } catch (IOException exception) {

        }
    }

    private void sendToClient() {
        try {
            byte[] byteBuffer = new byte[destServerInputStream.available()];
            int read = destServerInputStream.read(byteBuffer);
            clientOutputStream.write(byteBuffer);
        } catch (IOException exception) {

        }
    }
}

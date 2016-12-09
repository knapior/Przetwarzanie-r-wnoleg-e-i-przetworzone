package IRC.bo.tak;

/**
 * Created by napior on 04.12.16.
 */

import IRC.bo.tak.server.Channel;
import IRC.bo.tak.server.Client;
import IRC.bo.tak.utils.CaseInsensitiveMap;
import IRC.bo.tak.utils.VarMap;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.stream.Collectors;


public class Server extends VarMap implements Runnable {
    private Selector selector;
    private Map<SocketChannel, List> dataMapper;
    private InetSocketAddress listenAddress;
    private InetAddress host;
    private List<String> motd;

    public static final String VERSION = "0.0.1";

    private Map<String, Channel> channels;

    public static void main(String[] args) throws Exception {
        Server server = new Server("localhost", 8090);
        new MsgHandler(server);
        new Thread(server).start();

    }

    public Server(String address, int port) throws IOException {
        listenAddress = new InetSocketAddress(address, port);
        dataMapper = new HashMap<SocketChannel, List>();
        channels = Collections.synchronizedMap(new CaseInsensitiveMap<>());
        host = InetAddress.getLocalHost();
    }

    // create server channel
    private void startServer() throws IOException {
        this.selector = Selector.open();
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);

        // retrieve server socket and bind to port
        serverChannel.socket().bind(this.listenAddress);
        serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);

        System.out.println("Server started...");

        while (true) {
            // wait for events
            this.selector.select();

            //work on selected keys
            Iterator keys = this.selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = (SelectionKey) keys.next();

                // this is necessary to prevent the same key from coming up
                // again the next time around.
                keys.remove();

                if (!key.isValid()) {
                    continue;
                }

                if (key.isAcceptable()) {
                    this.accept(key);
                } else if (key.isReadable()) {
                    String msg = MsgHandler.getInstance().read(key);
                    MsgHandler.getInstance().acceptNewClient(key);
                    MsgHandler.getInstance().handdleMessage(msg,key);
                }
            }
        }
    }

    //accept a connection made to this channel's socket
    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel channel = serverChannel.accept();
        channel.configureBlocking(false);
        Socket socket = channel.socket();
        SocketAddress remoteAddr = socket.getRemoteSocketAddress();
        System.out.println("Connected to: " + remoteAddr);

        // register channel with selector for further IO
        dataMapper.put(channel, new ArrayList());
        channel.register(this.selector, SelectionKey.OP_READ);
    }

    @Override
    public void run() {
        try {
            this.startServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Map<SocketChannel, List> getDataMapper() {
        return dataMapper;
    }

    public Selector getSelector() {
        return selector;
    }

    public InetAddress getHost() {
        return host;
    }

    public Map<String, Channel> getChannels() {
        return channels;
    }

    public synchronized Channel getChannel(String key)
    {
        return channels.get(key);
    }

    public synchronized Client getClient(String key){
        Optional<SelectionKey> key1 = selector.keys().stream().filter(k -> ((Client)k.attachment()).getNick().equals(key)).findFirst();
        return key1.isPresent() ? (Client) key1.get().attachment() : null;
    }

    public synchronized Map<String, Client> getClients(){
        return selector.keys().stream().map(k ->(Client) k.attachment()).collect(Collectors.toMap(Client::getNick,k -> k));
    }

    public List<String> getMotd() {
        return motd;
    }

    public void setMotd(List<String> motd) {
        this.motd = motd;
    }
}
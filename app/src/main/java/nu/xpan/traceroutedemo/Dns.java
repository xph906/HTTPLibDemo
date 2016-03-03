package nu.xpan.traceroutedemo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Created by a on 3/2/16.
 */
public interface Dns {
    /**
     * Returns the IP addresses of {@code hostname}, in the order they will be attempted by OkHttp. If
     * a connection to an address fails, OkHttp will retry the connection with the next address until
     * either a connection is made, the set of IP addresses is exhausted, or a limit is exceeded.
     */
    List<InetAddress> lookup(String hostname) throws UnknownHostException;
}

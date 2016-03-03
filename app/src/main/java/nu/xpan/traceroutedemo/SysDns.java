package nu.xpan.traceroutedemo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * Created by a on 3/2/16.
 */
public class SysDns implements Dns{
    private long lastDnsDelay;
    private String errorMsg;
    private boolean successful;
    public SysDns(){
        lastDnsDelay = 0;
        errorMsg = "";
        successful = true;
    }

    public List<InetAddress> lookup(String hostname) throws UnknownHostException
    {
        if (hostname == null) throw new UnknownHostException("hostname == null");
        long t1 = System.currentTimeMillis();
        List<InetAddress> rs = null;
        try {
            rs = Arrays.asList(InetAddress.getAllByName(hostname));
            successful = true;
            errorMsg = "";
        }
        catch(Exception e){
            errorMsg = String.format("DNS Fail. Hostname:%s Reason:%s\n",
                    hostname, e.toString());
            successful = false;
        }
        finally {
            lastDnsDelay = System.currentTimeMillis() - t1;
        }
        return rs;
    }

    public boolean isSuccessful(){
        return successful;
    }
    public String getErrorMsg(){
        return errorMsg;
    }
    public long getDNSDelay(){
        return lastDnsDelay;
    }
}

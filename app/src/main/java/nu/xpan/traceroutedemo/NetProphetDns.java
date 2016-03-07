package nu.xpan.traceroutedemo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.xbill.DNS.ARecord;
import org.xbill.DNS.Cache;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

/**
 * Created by a on 3/2/16.
 */
public class NetProphetDns implements Dns {
    final static private int SecondLevelCacheRecordCredibility = 5;
    //the key for default cache is the Name of DNSJava,
    //which can be hostname+'.' or some dns domain name.
    //like www.sina.com.cn
    private Cache defaultCache;
    //the key for secondLevelCache is always hostname+'.'
    private Cache secondLevelCache; //this cache stores cached DNS with user defined timeout
    private long userDefinedTTL;
    private long dnsTimeout;
    private String dnsServer;
    private Resolver resolver ;
    private Map<Name, Set<Name>> host2DNSName;
    private boolean enableSecondLevelCache;

    public static <K, V> Map<K, V> createLRUMap(final int maxEntries) {
        return new LinkedHashMap<K, V>(maxEntries * 10 / 7, 0.7f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxEntries;
            }
        };
    }

    public NetProphetDns(){
        defaultCache = new Cache();
        secondLevelCache = new Cache();
        dnsServer = null;
        dnsTimeout = 600; //default: 6 mins
        userDefinedTTL = 60 * 60; //default: 1 hour
        enableSecondLevelCache = false;
        resolver = createNewResolverBasedOnDnsServer();
        host2DNSName = createLRUMap(100);
    }

    private Resolver createNewResolverBasedOnDnsServer(){
        try{
            if (dnsServer == null)
                return new SimpleResolver();
            else
                return new SimpleResolver(dnsServer);
        }
        catch(Exception e){
            System.err.println("failed to initiate Resolver:"+e);
            e.printStackTrace();
            return null;
        }
    }

    private List<InetAddress> doLookUp(String hostname){
        try {
            hostname = hostname.trim().toLowerCase();
            Lookup lookup = new Lookup(hostname, Type.A);
            lookup.setResolver(resolver);
            lookup.setCache(defaultCache);
            Record[] records = lookup.run();
            ArrayList<InetAddress> rs = new ArrayList<InetAddress>(records.length);
            for (Record record : records){
                // create return value;
                ARecord ar = (ARecord)record;
                rs.add(ar.getAddress());

                // update host2DNSName
                Name rawHostName = generateNameFromHost(hostname);
                if(!record.getName().equals(rawHostName)){
                    if(host2DNSName.containsKey(rawHostName)){
                        host2DNSName.get(rawHostName).add(record.getName());
                    }
                    else{
                        Set<Name> newSet = new HashSet<Name>();
                        newSet.add(record.getName());
                        host2DNSName.put(rawHostName, newSet);
                    }
                }

                // update secondLevelCache
                ARecord newARecord =  new ARecord(rawHostName, record.getDClass(),
                                userDefinedTTL, ((ARecord)record).getAddress());
                secondLevelCache.addRecord(newARecord, SecondLevelCacheRecordCredibility, null);

                System.out.println(((ARecord) record).getAddress().toString() + " TTL:" + ((ARecord) record).getTTL());
            }
            return rs;
        }
        catch(Exception e){
            System.err.println("error in doLookUp: "+e);
            e.printStackTrace();
            return null;
        }
    }

    //now this function has no use, but it demonstrates how to modify superclass's private field.
    private void modifyARecordTTL(ARecord record){
        try {
            Class recordClass = record.getClass().getSuperclass();
            Field cf = recordClass.getDeclaredField("ttl");
            cf.setAccessible(true);
            cf.set(record, userDefinedTTL);
        }
        catch(Exception e){
            System.err.println("error in ModifyARecordTTL");
            e.printStackTrace();
        }
    }
    private Name generateNameFromHost(String host){
        try {
            return Name.fromString(host + '.');
        }
        catch(Exception e){
            System.err.println("error in generateNameFromHost: "+e);
            e.printStackTrace();
            return null;
        }
    }

    /*
     * 1. search system DNS cache
     * 2. search NetProphet DNS cache
     * 3.   if find, return result and do asynchronous lookup
     * 3.   if not, do synchronous lookup
     * */
    public List<InetAddress> lookup(String hostname) throws UnknownHostException
    {
        return null;
    }



    public boolean searchSystemDNSCache(String hostname,
                                        List<InetAddress> result, StringBuilder errorMsg) {
        try {
            String cacheName = "addressCache";
            Class<InetAddress> klass = InetAddress.class;
            Field acf = klass.getDeclaredField(cacheName);

            acf.setAccessible(true);
            Object addressCache = acf.get(null);
            Class cacheKlass = addressCache.getClass();
            Field cf = cacheKlass.getDeclaredField("cache");
            cf.setAccessible(true);
            Object realCacheClass = cf.get(addressCache);
            Class cacheClass = realCacheClass.getClass();
            Method snapshotMethod = cacheClass.getMethod("snapshot");
            snapshotMethod.setAccessible(true);

            Map<String, Object> cache = (Map<String, Object>) (snapshotMethod.invoke(realCacheClass));
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> hi : cache.entrySet()) {
                String host = hi.getKey();
                host = host.trim().toLowerCase();
                if (!host.equals(hostname))
                    continue;

                Object cacheEntry = hi.getValue();
                Class cacheEntryKlass = cacheEntry.getClass();
                Field af = cacheEntryKlass.getDeclaredField("value");
                af.setAccessible(true);
                InetAddress[] addresses =null;
                try {
                    System.err.println("start");
                    addresses = (InetAddress[]) af.get(cacheEntry);
                    System.err.println(addresses);
                }
                catch(java.lang.ClassCastException e){
                    errorMsg.append(((String)af.get(cacheEntry))+"error:"+e);
                    System.err.println(af.get(cacheEntry)+"error:"+e);
                    return true;
                }
                for (InetAddress address : addresses) {
                    result.add(address);
                }
                return true;
            }
            return false;
        }
        catch(Exception e){
            System.err.println("error in searchSystemDNSCache: "+e.toString());
            e.printStackTrace();
            return false;
        }
    }

    public String displaySysDNSCache()  {
        try {
            String cacheName = "addressCache";
            Class<InetAddress> klass = InetAddress.class;
            Field acf = klass.getDeclaredField(cacheName);

            acf.setAccessible(true);
            Object addressCache = acf.get(null);
            Class cacheKlass = addressCache.getClass();
            Field cf = cacheKlass.getDeclaredField("cache");
            cf.setAccessible(true);
            Object realCacheClass = cf.get(addressCache);
            Class cacheClass = realCacheClass.getClass();
            Method snapshotMethod = cacheClass.getMethod("snapshot");
            snapshotMethod.setAccessible(true);

            Map<String, Object> cache = (Map<String, Object>) (snapshotMethod.invoke(realCacheClass));
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> hi : cache.entrySet()) {
                Object cacheEntry = hi.getValue();
                Class cacheEntryKlass = cacheEntry.getClass();
                Field expf = cacheEntryKlass.getDeclaredField("expiryNanos");
                expf.setAccessible(true);
                long expires = (Long) expf.get(cacheEntry);
                Field af = cacheEntryKlass.getDeclaredField("value");
                af.setAccessible(true);
                InetAddress[] addresses = (InetAddress[]) af.get(cacheEntry);
                List<String> ads = new ArrayList<String>(addresses.length);
                for (InetAddress address : addresses) {
                    ads.add(address.getHostAddress());
                }

                sb.append(hi.getKey() + " ADDR:" + ads + "\n");
            }
            return sb.toString();
        }
        catch(Exception e){
            System.err.println(e);
            e.printStackTrace();
            return "error:"+e.toString();
        }
    }
}

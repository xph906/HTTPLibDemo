package nu.xpan.traceroutedemo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.xbill.DNS.ARecord;
import org.xbill.DNS.Cache;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Name;
import org.xbill.DNS.RRset;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

/**
 * Created by xpan on 3/2/16.
 * TODO: use setMaxEntries to limit the size of cache.
 */
public class NetProphetDns implements Dns {
    static private boolean inBadNetworking = false;
    static private int maxSecondLevCacheEntry = 3000;

    /*
     * 1. search system DNS cache
     * 2. search NetProphet DNS cache (defaultCache)
     *  a.  if find, return result;
     *  b.  if not:
     *    (a). in slow networking scenario, search secondLevelDNSCache
     *         (secondLevelCache), then do asynchronous lookup.
     *    (b). in good networking scenario, do synchronous lookup.
       *       do synchronous lookup.
     * 3. after lookup is done,
     *    (a). update NetProphet DNS cache (defaultCache).
     *    (b). update second level cache (secondLevelDNSCache).
     */
    public List<InetAddress> lookup(String hostname) throws UnknownHostException
    {
        List<InetAddress> cachedRecordList = new ArrayList<InetAddress>();
        StringBuilder errorMsg = new StringBuilder();
        if(searchSystemDNSCache(hostname,cachedRecordList, errorMsg)){
            System.err.println("found record in system cache!");
            return cachedRecordList;
        }
        InetAddress cachedRecord = searchCache(defaultCache, hostname);
        if (cachedRecord != null){
            cachedRecordList.add(cachedRecord);
            System.err.println("found record in default cache!");
            return cachedRecordList;
        }
        if (inBadNetworking){
            cachedRecord = searchCache(secondLevelCache, hostname);
            if (cachedRecord != null){
                cachedRecordList.add(cachedRecord);
                System.err.println("found record in second level cache!");
                return cachedRecordList;
            }
        }

        System.err.println("do synchronous lookup!  ");
        return synchronousLookup(hostname);
    }

    public InetAddress testCache(int cacheIndex, String hostname) throws UnknownHostException
    {
        if(cacheIndex == 1)
            return searchCache(defaultCache, hostname);
        else if(cacheIndex == 2)
            return searchCache(secondLevelCache, hostname);
        else
            return null;

    }


    final static private int SecondLevelCacheRecordCredibility = 5;
    /* Cache Key Rule:
     * The key for defaultCache is the Name of DNSJava,
     *   which can be hostname+'.' or some dns domain name.
     *   like www.sina.com.cn
     * The key for secondLevelCache is always hostname+'.'
     *
     * Three DNS Cache:
     * SystemCache: this cache is maintained by Android,
     *   It's searched by calling searchSystemDNSCache(...).
     * defaultCache: this cache is maintained by NetProphetDns object,
     *   It respects the TTL set by server.
     *   By default, all items searched by NetProphetDns will be
     *   cached in defaultCache.
     * secondLevelCache: this cache is maintained by NetPriphetDns
     *   object. The TTL is set by `userDefinedTTL`. All items will
     *   be cached here, but it's only cached when in slow scenario.
     */
    private Cache defaultCache;
    private Cache secondLevelCache; //this cache
    private long userDefinedTTL, userDefinedNegTTL;

    /* Default lookup server*/
    private long dnsDefaultTimeout;
    private String dnsServer;
    private Resolver resolver ;
    private Map<Name, Set<Name>> host2DNSName;
    private boolean enableSecondLevelCache;

    public NetProphetDns(){
        defaultCache = new Cache();
        defaultCache.setMaxEntries(maxSecondLevCacheEntry);
        secondLevelCache = new Cache();
        secondLevelCache.setMaxEntries(maxSecondLevCacheEntry);
        dnsServer = null;
        dnsDefaultTimeout = 10; //default: 10 s
        userDefinedTTL = 60 * 60; //default: 1 hour
        enableSecondLevelCache = false;
        resolver = createNewResolverBasedOnDnsServer();
        host2DNSName = createLRUMap(100);

    }

    /*
     * Search the Cache.
     *  */
    private InetAddress searchCache(Cache cache, String hostname){
        try {
            Name rawHostName = generateNameFromHost(hostname);
            if (host2DNSName.containsKey(rawHostName)) {
                Set<Name> names = host2DNSName.get(rawHostName);
                for (Name name : names) {
                    RRset[] rs = cache.findAnyRecords(name, Type.A);
                    if(rs == null)
                        continue;
                    for (RRset s : rs) {
                        try {
                            Iterator<Record> iter = s.rrs();
                            InetAddress addr = InetAddress.getByAddress(iter.next().rdataToWireCanonical());
                            return addr;
                        } catch (UnknownHostException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            RRset[] rs = cache.findAnyRecords(rawHostName, Type.A);
            if (rs == null)
                return null;
            for (RRset s : rs) {
                try {
                    Iterator<Record> iter = s.rrs();
                    InetAddress addr = InetAddress.getByAddress(iter.next().rdataToWireCanonical());
                    return addr;
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    private List<InetAddress> synchronousLookup(String hostname){
        try {
            hostname = hostname.trim().toLowerCase();
            Lookup lookup = new Lookup(hostname, Type.A);
            lookup.setResolver(resolver);
            lookup.setCache(defaultCache);
            long t1 = System.currentTimeMillis();
            Record[] records = lookup.run();
            long dnsDelay = System.currentTimeMillis() - t1;
            System.out.println("done DNS lookup in "+dnsDelay +" ms");

            //Cannot find the hostname, returns directly.
            if (records == null){
                return null;
            }

            // Update the cache.
            ArrayList<InetAddress> rs = new ArrayList<InetAddress>(records.length);
            RRset rrset = new RRset();
            Name rawHostName = generateNameFromHost(hostname);
            for (Record record : records){
                // create return value;
                ARecord ar = (ARecord)record;
                rs.add(ar.getAddress());

                // update host2DNSName if rawHostName cannot generate real hostname
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
                rrset.addRR(newARecord);
                System.out.println(
                        String.format("Name:%s IP:%s TTL:%d\n",
                            record.getName(),
                            ((ARecord) record).getAddress().toString(),
                            ((ARecord) record).getTTL()));
            }
            storeRRsetToSecondLevCache(rawHostName, rrset);

            return rs;
        }
        catch(Exception e){
            System.err.println("error in doLookUp: "+e);
            e.printStackTrace();
            return null;
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

    private static <K, V> Map<K, V> createLRUMap(final int maxEntries) {
        return new LinkedHashMap<K, V>(maxEntries * 10 / 7, 0.7f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /*
     * Create a resolver.
     *   dnsServer == null: use system default server
     *   dnsServer != null: use `dnsServer`
     * */
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

    synchronized private void  storeRRsetToSecondLevCache(Name rawHostName, RRset rrset){
        if (secondLevelCache.getSize() > maxSecondLevCacheEntry)
            secondLevelCache.clearCache();
        if(rrset.size() > 0){
            secondLevelCache.flushName(rawHostName);
            secondLevelCache.addRRset(rrset, SecondLevelCacheRecordCredibility);
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

}

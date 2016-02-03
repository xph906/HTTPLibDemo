package nu.xpan.traceroutedemo;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static nu.xpan.traceroutedemo.MainActivity.logger;

/**
 * Created by a on 2/2/16.
 */
public class LocalNetworkingState {

    public class LocalNetworkingStateSnapshot{
        public long timestamp;
        public String mIP;
        public float mLossRate;
        public float mLatency;
        public String raw;
        public float mLatencys[];

        private String IPADDRESS_PATTERN =
                "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
        private String LATENCY_PATTERN =
                "[0-9]+(\\.[0-9]+)?";
        private String STAR_PATTERN = "\\*";
        private Pattern mIPReg, mLatReg, mStarReg;
        //TODO
        public LocalNetworkingStateSnapshot(String raw){
            updateFields(raw);
            mIPReg = Pattern.compile(IPADDRESS_PATTERN);
            mLatReg = Pattern.compile(LATENCY_PATTERN);
            mStarReg = Pattern.compile(STAR_PATTERN);
            mLatencys = new float[5];
        }

        public synchronized void updateFields(String raw){
            this.raw = raw;
            if(raw.length()==0)
                return;
            try{
                String[] strs = raw.split("\n");
                if(strs.length >= 3) {
                    this.raw = strs[2].trim();
                    if(! this.raw.startsWith("2")){
                        logger.info("error format: "+this.raw);
                        return;
                    }
                    this.raw = this.raw.substring(2);

                    Matcher matcher = mIPReg.matcher(this.raw);
                    int index = 0;
                    if(matcher.find(index)){
                        mIP = matcher.group();
                        index = matcher.end();
                    }
                    matcher = mLatReg.matcher(this.raw);
                    int count=0;
                    while(matcher.find(index)){
                        mLatencys[count++] =
                                Float.valueOf( matcher.group());
                        index = matcher.end();
                    }
                    if(count == 0){
                        mLossRate = 1;
                        mLatency = 0;
                    }
                    else {
                        Arrays.sort(mLatencys, 0, count);
                        mLatency = mLatencys[count / 2];
                        mLossRate = (5 -count) / (float)5.0;
                    }
                    logger.info(String.format("IP:%s median:%f lossRate:%f", mIP, mLatency,mLossRate));
                }
            }
            catch(Exception e){
                this.raw += e.toString();
                logger.severe("error parsing raw of LocalNetworkingStateSnapshot: "+raw+" "+e);
            }
            this.timestamp = System.currentTimeMillis();
        }
    }
    public class SynchronizedBooleanData {
        private boolean val;
        public SynchronizedBooleanData(boolean val){
            this.val = val;
        }

        public synchronized void setVal(boolean val){
            this.val = val;
        }
        public synchronized boolean getVal(){
            return this.val;
        }

    };

    private LocalNetworkingStateSnapshot mSnapShot;
    private String mAppName;
    private String mIP;
    private float mLatency;
    private float mLossRate;
    private long mIPUpdateInteval; //s
    private long mLatencyUpdateTimeout; //s

    private String mType;
    private String mNetworkName;
    //private int mTimeout;  //s

    private SynchronizedBooleanData mIsRunning;
    private boolean mIsForeground;

    private Context mContext;
    private NetUtility mUtil;
    private ScheduledExecutorService scheduler;
    private TraceRoute mTraceroute;
    private Handler mHandler;

    private Runnable mUpdateIPTask = new Runnable(){

        public void run() {
            if(mIsRunning.getVal())
                return ;
            mIsRunning.setVal(true);

            if(mUtil.getNetworkingState() != NetUtility.State.CONNECTED){
                logger.info("networking is disconnected, do nothing");
                mIsRunning.setVal(false);
                return ;
            }
            if (!mUtil.getNetworkingType().equals(mType) ){
                logger.info("networking type is different("+
                        mUtil.getNetworkingType()+" VS "+mType+"), do nothing");
                mIsRunning.setVal(false);
                return ;
            }
            if(!isCurrentProcessRunningForeground()){
                logger.info("app is running background, do nothing");
                mIsRunning.setVal(false);
                return ;
            }
            long t1 = System.currentTimeMillis();

            mTraceroute.runTraceroute("173.194.46.49");
            long t2 = System.currentTimeMillis() - t1;
            logger.info("it takes "+ t2 +" ms to do traceroute");
            mIsRunning.setVal(false);
        }
    };

    private boolean isCurrentProcessRunningForeground(){
        ActivityManager am = (ActivityManager) mContext.getSystemService(mContext.ACTIVITY_SERVICE);
        ActivityManager.RunningTaskInfo foregroundTaskInfo = am.getRunningTasks(1).get(0);
        String foregroundTaskPackageName = foregroundTaskInfo .topActivity.getPackageName();
        //logger.info("foregroundTask:"+foregroundTaskPackageName+" myapp:"+mAppName);
        return mAppName.equals(foregroundTaskPackageName);
    }

    public LocalNetworkingState(String type, String name, int updateInteval, int latencyUpdateTimeout, NetUtility util,
                                Context context){
        this.mAppName = context.getPackageName();
        this.mIPUpdateInteval = updateInteval;
        this.mLatencyUpdateTimeout = latencyUpdateTimeout;
        this.mType = type;
        this.mNetworkName = name;

        this.mIP = null;
        this.mLatency = 0;
        this.mLossRate = 0;

        this.mIsForeground = false;
        this.mIsRunning = new SynchronizedBooleanData(false);

        this.mContext = context;
        this.mUtil = util;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.mSnapShot = new LocalNetworkingStateSnapshot("");
        this.mHandler = new Handler(Looper.getMainLooper()) {
            String contents;
            @Override
            public void handleMessage(Message inputMessage) {
                try {
                    switch (inputMessage.what) {
                        case InternalConst.MSGType.USER_NET_UPDATE_MSG:
                            contents = (String) inputMessage.obj;
                            logger.info("receive net update msg: " + contents);
                            mSnapShot.updateFields(contents);
                            break;
                        default:
                            break;
                    }
                }
                catch(Exception e){
                    logger.severe("error in localnetworkingstate handling message: "+e);

                    e.printStackTrace();
                }
            }
        };
        mTraceroute = new TraceRoute(context,5000, this.mHandler);
        if(!mTraceroute.isInstalled()) {
            mTraceroute.installTraceroute();
        }
    }
    public LocalNetworkingStateSnapshot getNetworkState(){
        long now = System.currentTimeMillis() / 1000;
        long snapshotTime = mSnapShot.timestamp / 1000;
        long delta = now - snapshotTime;
        if( delta > this.mLatencyUpdateTimeout ){
            startOnetimeRefresh();
        }
        return mSnapShot;
    }

    public void startRepeatedRefresh(){
        if(this.mIPUpdateInteval <= 0){
            logger.severe("mIPUpdateInteval is not correct "+mIPUpdateInteval);
        }
        if(this.scheduler == null)
            this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.scheduler.scheduleAtFixedRate(mUpdateIPTask, 0,
                this.mIPUpdateInteval, TimeUnit.MINUTES);
    }

    public void startOnetimeRefresh(){
        if(this.scheduler == null)
            this.scheduler = Executors.newSingleThreadScheduledExecutor();

        try {
            this.scheduler.execute(mUpdateIPTask);
        }
        catch(Exception e){
            logger.severe("error in startOneTimeRefresh: "+e);
        }
    }

    public void stopRepeatedRefresh(){
        if(this.scheduler != null)
            this.scheduler.shutdown();
        this.scheduler = null;
    }


    public void setIP(String mIP) {
        this.mIP = mIP;
    }

    public void setLatency(int mLatency) {
        this.mLatency = mLatency;
    }

    public void setLossRate(float mLossRate) {
        this.mLossRate = mLossRate;
    }

    public void setLatencyUpdateTimestamp(long mTimestamp) {
        this.mLatencyUpdateTimeout = mTimestamp;
    }
    public void setIPUpdateTimestamp(long mTimestamp) {
        this.mIPUpdateInteval = mTimestamp;
    }

    public void setType(String mType) {
        this.mType = mType;
    }

    public void setNetworkName(String mNetworkName) {
        this.mNetworkName = mNetworkName;
    }

    public String getIP() {
        return mIP;
    }

    public float getLatency() {
        return mLatency;
    }

    public float getLossRate() {
        return mLossRate;
    }

    public long getLatencyUpdateTimestamp() {
        return mLatencyUpdateTimeout;
    }

    public long getIPUpdateTimestamp() {
        return mIPUpdateInteval;
    }

    public String getType() {
        return mType;
    }

    public String getNetworkName() {
        return mNetworkName;
    }
}

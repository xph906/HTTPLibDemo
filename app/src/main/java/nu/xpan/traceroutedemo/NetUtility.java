package nu.xpan.traceroutedemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.telephony.CellInfoGsm;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.logging.Level;

import static nu.xpan.traceroutedemo.MainActivity.logger;

public class NetUtility extends PhoneStateListener {
    private State mState;
    private String mType;
    private String mName;
    private int mWIFISignalLevel;
    private int mCellSignalLevel;
    private int mMCC;   // mobile network code
    private int mMNC;   // mobile country code
    private int mLAC;   // local area code
    private String mIMEI;

    private Context mContext;
    private boolean mListening; //whether monitoring connection changes
    private boolean mIsFailover;
    private NetworkInfo.DetailedState mDetailedState;
    private int mCellularType;
    private String mReason;
    private NetworkInfo mNetworkInfo, mOtherNetworkInfo;
    private ConnectivityBroadcastReceiver mReceiver;
    private Handler handler;
    private int mCellSignalStrength;
    private int mWIFISignalStrength;
    private TelephonyManager mTelManager;
    private SignalStrength mSignalStrength;
    private LocalNetworkingState mWIFINetState;
    private LocalNetworkingState mCellNetState;

    public NetUtility(Context cx, Handler handler){
        this.mContext = cx;
        this.mState = State.UNKNOWN;
        this.mListening = false;
        this.mNetworkInfo = null;
        this.mOtherNetworkInfo = null;
        this.mIsFailover = false;
        this.mReceiver = new ConnectivityBroadcastReceiver();
        this.handler = handler;
        this.mName = null;
        this.startListening();
        this.mCellSignalStrength = 0;
        this.mWIFISignalStrength = 0;
        mTelManager.listen(this, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        //10min and 60s
        mWIFINetState = new LocalNetworkingState("WIFI", this.mName, 10, 60, this,cx);
        //mWIFINetState.startIPUpdate();
        mCellNetState = new LocalNetworkingState("mobile", this.mName, 10, 60, this,cx);

    }

    public State getNetworkingState(){
        return mState;
    }
    public String getNetworkingType() {
        return mType;
    }
    public String getNetworkingName() {
        return mName;
    }


    public enum Type {
        WIFI,
        CELLULAR
    };

    public enum State {
        UNKNOWN,
        CONNECTED,
        NOT_CONNECTED,
        CONNECTING
    };

    private class ConnectivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION) ||
                mListening == false) {
                return;
            }

            //handle no connectivity
            boolean noConnectivity =
                    intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
            mReason = intent.getStringExtra(ConnectivityManager.EXTRA_REASON);
            if (noConnectivity) {

                logger.log(Level.INFO, "Network disconnected: ");
                mState = State.NOT_CONNECTED;
                mName = null;
                String msg_obj = String.format(
                        "NetworkingState Updated: %s (%s)\n", mState, mReason == null ? "" : mReason);
                postMessage(InternalConst.MSGType.NETINFO_MSG, msg_obj);
                mWIFINetState.stopRepeatedRefresh();
                mCellNetState.stopRepeatedRefresh();

                return;
            }

            ConnectivityManager cm =
                    (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            mNetworkInfo = cm.getActiveNetworkInfo();
            if (mNetworkInfo == null) return;
            mIsFailover =
                    intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false);

            // get type and name
            String subType = mNetworkInfo.getSubtypeName();
            mType = mNetworkInfo.getTypeName();
            boolean isConnected = mNetworkInfo.isConnected();
            boolean isConnecting = mNetworkInfo.isConnectedOrConnecting();
            if(isConnected){
                logger.log(Level.INFO, "Network connected: "+mType+"/"+subType);
                mState = State.CONNECTED;
            }
            else if(isConnecting){
                logger.log(Level.INFO, "Network connecting: "+mType+'/'+subType);
                mState = State.CONNECTING;
            }
            try {
                // update type and name
                if (isConnected && mNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    mName = fetchWIFIName();
                    subType = mName;
                    mCellNetState.stopRepeatedRefresh();
                    mWIFINetState.startRepeatedRefresh();
                } else if (isConnected) {
                    mName = subType;
                    mCellularType = mTelManager.getNetworkType();
                    mWIFINetState.stopRepeatedRefresh();
                    mCellNetState.startRepeatedRefresh();
                }
            }
            catch(Exception e){
                System.err.println("Exception "+e);
                e.printStackTrace();
            }

            //update log information
            StringBuilder sb = new StringBuilder();
            sb.append(String.format( "NetworkingState Updated: %s\n", mState));
            sb.append(String.format( "  Type: %s \n  Subtype %s\n",  mType, subType));
            postMessage(InternalConst.MSGType.NETINFO_MSG, sb.toString());
        }
    };

    @Override
    public void onSignalStrengthsChanged(SignalStrength signalStrength) {
        super.onSignalStrengthsChanged(signalStrength);
        mSignalStrength = signalStrength;
    }

    public LocalNetworkingState.LocalNetworkingStateSnapshot getRefreshedFirstMileLatency(){
        if(mState == State.NOT_CONNECTED)
            return null;
        else if(mType.equals("mobile"))
            return mCellNetState.getNetworkState();
        else if(mType.equals("WIFI"))
            return mWIFINetState.getNetworkState();
        return null;
    }

    public void refreshFirstMileLatency(){
        if(mState == State.NOT_CONNECTED)
            return ;
        else if(mType.equals("mobile"))
            mCellNetState.startOnetimeRefresh();
        else if(mType.equals("WIFI"))
            mWIFINetState.startOnetimeRefresh();
    }

    public String getIMEI(){
        GsmCellLocation location = (GsmCellLocation) mTelManager.getCellLocation();
        mIMEI = mTelManager.getDeviceId();
        return mIMEI;
    }

    public int getLAC(){
        try {
            GsmCellLocation location = (GsmCellLocation) mTelManager.getCellLocation();
            mLAC = location.getLac();
            return mLAC;
        }
        catch(Exception e){
            logger.severe("failed to get LAC");
        }
        return 0;
    }

    public int getMCC(){
        try {
            String networkOperator = mTelManager.getNetworkOperator();

            if (networkOperator != null) {
                mMCC = Integer.valueOf(networkOperator.substring(0, 3));
                return mMCC;
            }
        }
        catch(Exception e){
            logger.severe("failed to get mcc");
        }
        return 0;
    }

    public int getMNC(){
        try {
            String networkOperator = mTelManager.getNetworkOperator();
            if (networkOperator != null) {
                mMNC = Integer.valueOf(networkOperator.substring(3));
                return mMNC;
            }
        }
        catch(Exception e){
            logger.severe("failed to get MNC ");
        }
        return 0;
    }

    public int getWIFISignalStrength(){
        WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        int numberOfLevels = 5;
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        mWIFISignalStrength = wifiInfo.getRssi();
        mWIFISignalLevel  = WifiManager.calculateSignalLevel(mWIFISignalStrength, numberOfLevels);
        logger.info(String.format("WIFI signal strength: %d (%d)",
                mWIFISignalStrength, mWIFISignalLevel));
        return mWIFISignalLevel;
    }

    public String toString(){
        try {
            LocalNetworkingState.LocalNetworkingStateSnapshot snapshot = getRefreshedFirstMileLatency();
            String localBAIP = "null";
            float lossRate = -1;
            float latency = -1;
            Date date = new Date();
            if (snapshot != null) {
                date = new Date(snapshot.timestamp);
                lossRate = snapshot.mLossRate;
                latency = snapshot.mLatency;
                localBAIP = snapshot.mIP;
            }

            return String.format("State : %s\nType  : %s\nName  : %s\nWIFISig: %d\nCELLSig: %d\n" +
                            "LAC   :%d\nMNC   :%d\nMCC   :%d\nIMEI:   %s\n "+
                            "LocalBAIP: %s\n Latency: %f\n LossRate: %f\n updateTime: %s\n",
                    getNetworkingState().toString(), getNetworkingType().toString(), getNetworkingName(),
                    getWIFISignalStrength(), getCellSignalStrength(),
                    getLAC(), getMNC(), getMCC(), getIMEI(), localBAIP, latency, lossRate, date.toString());
        }
        catch(Exception e){
            logger.severe("error in toString "+e);
            e.printStackTrace();
            return "";
        }
    }
    public synchronized void startListening() {
        if (!mListening) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            mContext.registerReceiver(mReceiver, filter);
            mListening = true;
        }
    }

    public synchronized void stopListening() {
        if (mListening) {
            mContext.unregisterReceiver(mReceiver);
            mContext = null;
            mNetworkInfo = null;
            mOtherNetworkInfo = null;
            mIsFailover = false;
            mReason = null;
            mListening = false;
        }
    }

    private String fetchWIFIName(){
        WifiManager manager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        if (manager.isWifiEnabled()) {
            WifiInfo wifiInfo = manager.getConnectionInfo();
            if (wifiInfo != null) {
                NetworkInfo.DetailedState state = WifiInfo.getDetailedStateOf(wifiInfo.getSupplicantState());
                if (state == NetworkInfo.DetailedState.CONNECTED ||
                        state == NetworkInfo.DetailedState.OBTAINING_IPADDR) {
                    return wifiInfo.getSSID();
                }
            }
        }
        return null;
    }

    private int getCellSignalStrength2(){
        try {
            TelephonyManager telephonyManager =
                    (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            System.err.println("getAllCellInfo:" + telephonyManager.getNeighboringCellInfo().size());
            CellInfoGsm cellinfogsm = (CellInfoGsm) telephonyManager.getAllCellInfo().get(0);
            CellSignalStrengthGsm cellSignalStrengthGsm = cellinfogsm.getCellSignalStrength();
            return cellSignalStrengthGsm.getDbm();
        }
        catch(Exception e){
            logger.severe("issue getting signal strength: "+e);
            return 0;
        }


    }

    private int getCellSignalStrength()
    {
        int dbm = 0;
        if(mCellularType == TelephonyManager.NETWORK_TYPE_LTE)
        {
            String ssignal = mSignalStrength.toString();
            String[] parts = ssignal.split(" ");
            int asu = Integer.parseInt(parts[8]);
            dbm = asu*2-113;
            logger.info("Prophet:phoneInfo " + "LTE:"+dbm);
        }
        else if(mCellularType == TelephonyManager.NETWORK_TYPE_CDMA
                || mCellularType == TelephonyManager.NETWORK_TYPE_1xRTT )
        {
            dbm = mSignalStrength.getCdmaDbm();
            logger.info("Prophet:phoneInfo " + "CDMA:"+dbm);
        }
        else if(mCellularType == TelephonyManager.NETWORK_TYPE_EVDO_0
                || mCellularType == TelephonyManager.NETWORK_TYPE_EVDO_A
                || mCellularType == TelephonyManager.NETWORK_TYPE_EVDO_B)
        {

            dbm = mSignalStrength.getEvdoDbm();
            logger.info("Prophet:phoneInfo "+"EVDO:"+dbm);
        }
        else if(mSignalStrength.isGsm())
        {
            int asu = mSignalStrength.getGsmSignalStrength();
            dbm = asu*2-113;
            logger.info("Prophet:phoneInfo "+"GSM:"+dbm);
        }
        else{
            dbm = -113;
        }

        WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mCellSignalLevel  = WifiManager.calculateSignalLevel(dbm, 5);
        return mCellSignalLevel;
    }


    //TODO: not implemented
    public String getDetailedState(){
        ConnectivityManager cm =
                (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        //NetworkInfo.DetailedState state =
        return "";
    }

    private void postMessage(int msgType, String content){
        if(handler == null)
            return;
        Message msg = new Message();
        msg.what = msgType;
        msg.obj = content;
        handler.sendMessage(msg);
        logger.log(Level.INFO, "Sent NetworkingInfo msg: "+content);
    }

}

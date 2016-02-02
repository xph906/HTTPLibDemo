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
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;

import java.util.logging.Level;

import static nu.xpan.traceroutedemo.MainActivity.logger;

public class NetUtility extends PhoneStateListener {
    private Context mContext;
    private boolean mListening; //whether monitoring connection changes
    private boolean mIsFailover;
    private State mState;
    private NetworkInfo.DetailedState mDetailedState;
    private String mType;
    private String mName;
    private String mReason;
    private NetworkInfo mNetworkInfo, mOtherNetworkInfo;
    private ConnectivityBroadcastReceiver mReceiver;
    private Handler handler;
    private int mCellSignalStrength;
    private int mCellSignalLevel;
    private int mWIFISignalStrength;
    private int mWIFISignalLevel;


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
        logger.log(Level.INFO, "testing if logger works...");
    }

    public String getNetworkingState(){
        return mState.toString();
    }
    public String getNetworkingType() {
        return mType;
    }
    public String getNetworkingName() {
        return mName;
    }


    public enum State {
        UNKNOWN,
        CONNECTED,
        NOT_CONNECTED,
        CONNECTING
    };

    public enum Type {
      WIFI,
      CELLULAR
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
                    "NetworkingState Updated: %s (%s)\n", mState, mReason==null ? "" : mReason);
                postMessage(InternalConst.MSGType.NETINFO_MSG, msg_obj);
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

            // update type and name
            if(isConnected &&  mNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI){
                mName = fetchWIFIName();
                subType = mName;
            }
            else if(isConnected){
                mName = subType;
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
        mCellSignalStrength = signalStrength.getGsmSignalStrength();

        mCellSignalLevel = (2 * mCellSignalStrength) - 113; // -> dBm
        logger.info(String.format("Cell signal strength : %d(%d)",
                mCellSignalStrength, mCellSignalLevel));
    }
    public int getCellSignalStrength(){
        mCellSignalLevel = (2 * mCellSignalStrength) - 113; // -> dBm
        logger.info(String.format("Cell signal strength : %d(%d)",
                mCellSignalStrength, mCellSignalLevel));
        return mCellSignalStrength;
    }

    public int getWIFISignalStrength(){
        WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        int numberOfLevels = 5;
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        mWIFISignalStrength = wifiInfo.getRssi();
        mWIFISignalLevel  = WifiManager.calculateSignalLevel(mWIFISignalStrength, numberOfLevels);
        logger.info(String.format("WIFI signal strength: %d (%d)",
                mWIFISignalStrength, mWIFISignalLevel));
        return mWIFISignalStrength;
    }

    public String toString(){
        return String.format("State : %s\nType  : %s\nName  : %s\nWIFISig: %d\nCELLSig: %d\n",
                getNetworkingState(), getNetworkingType(), getNetworkingName(),
                getWIFISignalStrength(), getCellSignalStrength());
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

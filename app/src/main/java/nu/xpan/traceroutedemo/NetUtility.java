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

import java.util.logging.Level;

import static nu.xpan.traceroutedemo.MainActivity.logger;

public class NetUtility {
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
        logger.log(Level.INFO, "testing if logger works...");
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

            boolean noConnectivity =
                    intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
            mReason = intent.getStringExtra(ConnectivityManager.EXTRA_REASON);

            if (noConnectivity) {
                logger.log(Level.INFO, "Network disconnected: ");
                mState = State.NOT_CONNECTED;
                mName = null;
                String msg_obj = String.format(
                    "NetworkingState Updated: %s (%s)\n",
                    mState, mReason==null ? "" : mReason);
                postMessage(InternalConst.MSGType.NETINFO_MSG, msg_obj);
                logger.log(Level.INFO, "Sent NetworkingInfo update msg: "+msg_obj);
                return;
            }

            ConnectivityManager cm =
                    (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            mNetworkInfo = cm.getActiveNetworkInfo();
            if (mNetworkInfo == null){
                String msg_obj = String.format(
                        "NetworkingState Updated Error: %s (%s)\n",
                        "mNetworkInfo is null", mState);
                postMessage(InternalConst.MSGType.NETINFO_MSG, msg_obj);
                logger.log(Level.INFO, "Sent NetworkingInfo update msg: "+msg_obj);
                return;
            }

            mOtherNetworkInfo = (NetworkInfo)
                    intent.getParcelableExtra(ConnectivityManager.EXTRA_OTHER_NETWORK_INFO);
            mIsFailover =
                    intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false);

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
            if(isConnected &&
                    mNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI){
                mName = fetchWIFIName();
                subType = mName;
                logger.log(Level.INFO, "WIFI name: "+mName);

            }
            else if(isConnected){
                mName = subType;
            }

            //update log information
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                "NetworkingState Updated: %s\n",
                    mState));
            sb.append(String.format(
                    "  Type: %s \n  Subtype %s\n",
                    mType, subType));
            postMessage(InternalConst.MSGType.NETINFO_MSG, sb.toString());
            logger.log(Level.INFO, "Sent NetworkingInfo update msg: "+sb.toString());
        }
    };

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

    public String getNetworkingName(){
        return mName;
    }

    public boolean isConnected(){
        return mState == State.CONNECTED;
    }

    public boolean isUsingWIFI(){
        ConnectivityManager cm =
                (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork==null) {
            return false;
        }
        boolean isWIFI = activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;

        return isWIFI;
    }

    public String getDetailedState(){
        ConnectivityManager cm =
                (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        //NetworkInfo.DetailedState state =
        return "";
    }

    private void postMessage(int msgType, String content){
        Message msg = new Message();
        msg.what = msgType;
        msg.obj = content;
        handler.sendMessage(msg);
    }

}

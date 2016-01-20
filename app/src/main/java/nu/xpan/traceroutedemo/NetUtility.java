package nu.xpan.traceroutedemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import java.util.logging.Level;

import static nu.xpan.traceroutedemo.MainActivity.logger;

/**
 * Created by xpan on 1/18/16.
 */
public class NetUtility {
    private Context mContext;
    private boolean mListening; //whether monitoring connection changes
    private boolean mIsFailover;
    private State mState;
    private NetworkInfo mNetworkInfo, mOtherNetworkInfo;
    private String mReason;
    private ConnectivityBroadcastReceiver mReceiver;

    public NetUtility(Context cx){
        this.mContext = cx;
        this.mState = State.UNKNOWN;
        this.mListening = false;
        this.mNetworkInfo = null;
        this.mOtherNetworkInfo = null;
        this.mIsFailover = false;
        this.mReceiver = new ConnectivityBroadcastReceiver();
        this.startListening();
        logger.log(Level.INFO, "testing if logger works...");
    }

    public enum State {
        UNKNOWN,
        CONNECTED,
        NOT_CONNECTED
    };
    public enum Type {
      WIFI,
      TwoG,
      ThreeG,
      FourG
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

            if (noConnectivity) {
                mState = State.NOT_CONNECTED;
                mReason = intent.getStringExtra(ConnectivityManager.EXTRA_REASON);
                logger.log(Level.INFO, "Network disconnected: "+mReason);
                return ;
            } else {
                mState = State.CONNECTED;
            }
            ConnectivityManager cm =
                    (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            mNetworkInfo = cm.getActiveNetworkInfo();
            mOtherNetworkInfo = (NetworkInfo)
                    intent.getParcelableExtra(ConnectivityManager.EXTRA_OTHER_NETWORK_INFO);
            mIsFailover =
                    intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false);

            String type = mNetworkInfo.getTypeName();
            String subType = mNetworkInfo.getSubtypeName();
            boolean isConnected = mNetworkInfo.isConnected();
            boolean isConnecting = mNetworkInfo.isConnectedOrConnecting();

            if(isConnected){
                logger.log(Level.INFO, "Network connected: "+type+"/"+subType);
            }
            else if(isConnecting){
                logger.log(Level.INFO, "Network connecting: "+type+'/'+subType);
            }
            /*logger.log(Level.INFO,
                    " onReceive(): mNetworkInfo=" + mNetworkInfo + "\n mOtherNetworkInfo = "
                    + (mOtherNetworkInfo == null ? "[none]" : mOtherNetworkInfo +
                    "\n noConn=" + noConnectivity) + "\n mState=" + mState.toString() +
                    "\n isFailOver=" + mIsFailover + "\n reason=" + mReason);*/
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

    public boolean isConnected(){
        ConnectivityManager cm =
                (ConnectivityManager)mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
        if(isConnected)
            mState = State.CONNECTED;
        else
            mState = State.NOT_CONNECTED;
        return isConnected;
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

    public String getNetworkName(){
        return "";
    }

}

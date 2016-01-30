package nu.xpan.traceroutedemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Message;

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
            mReason = intent.getStringExtra(ConnectivityManager.EXTRA_REASON);
            Message msg = new Message();
            msg.what = MainActivity.MSGType.NETINFO_MSG;

            if (noConnectivity) {
                logger.log(Level.INFO, "Network disconnected: ");
                mState = State.NOT_CONNECTED;
                String msg_obj = String.format(
                    "NetworkingState Updated: %s (%s)\n",
                    mState, mReason==null ? "" : mReason);
                msg.obj = msg_obj;
                handler.sendMessage(msg);
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
                msg.obj = msg_obj;
                handler.sendMessage(msg);
                logger.log(Level.INFO, "Sent NetworkingInfo update msg: "+msg_obj);
                return;
            }

            mOtherNetworkInfo = (NetworkInfo)
                    intent.getParcelableExtra(ConnectivityManager.EXTRA_OTHER_NETWORK_INFO);
            mIsFailover =
                    intent.getBooleanExtra(ConnectivityManager.EXTRA_IS_FAILOVER, false);

            String subType = mNetworkInfo.getSubtypeName();
            String type = mNetworkInfo.getTypeName();
            boolean isConnected = mNetworkInfo.isConnected();
            boolean isConnecting = mNetworkInfo.isConnectedOrConnecting();

            if(isConnected){
                logger.log(Level.INFO, "Network connected: "+type+"/"+subType);
                mState = State.CONNECTED;
            }
            else if(isConnecting){
                logger.log(Level.INFO, "Network connecting: "+type+'/'+subType);
                mState = State.CONNECTING;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                "NetworkingState Updated: %s\n",
                    mState));
            sb.append(String.format(
                    "  Type: %s \n  Subtype %s\n",
                    type,subType));

            msg.obj = sb.toString();
            handler.sendMessage(msg);
            logger.log(Level.INFO, "Sent NetworkingInfo update msg: "+sb.toString());
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

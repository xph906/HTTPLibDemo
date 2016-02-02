package nu.xpan.traceroutedemo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.TextView;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by a on 1/8/16.
 */
public class MyHTTPClient {
    private final static String TAG = "MY_HTTP_CLIENT";
    private final static Logger LOGGER = Logger.getLogger(TAG);
    private Handler handler;
    private Context context;

    public MyHTTPClient(Context context, Handler handler){
        this.context = context;
        this.handler = handler;
    }

    public void loadString(String url){
        HTTPRunnable runnable = new HTTPRunnable(context, url, handler,HTTPRunnable.HTTPTaskType.STRING);
        (new Thread(runnable)).start();
    }

    public void loadImage(String url){
        HTTPRunnable runnable = new HTTPRunnable(context, url, handler,HTTPRunnable.HTTPTaskType.IMAGE);
        (new Thread(runnable)).start();
    }
}

package nu.xpan.traceroutedemo;

import android.os.Handler;
import android.os.Message;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
/**
 * Created by a on 1/8/16.
 */
public class HTTPRunnable implements Runnable {
    private final static Logger LOGGER = Logger.getLogger("HTTPRunnable");

    private String url;
    private String method;
    private Handler handler;

    public HTTPRunnable(String method, String url, Handler handler){
        this.method = method;
        this.url = url;
        this.handler = handler;

    }

    @Override
    public void run() {
        HttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet(url);
        // Making HTTP Request
        try {
            HttpResponse response = client.execute(request);
            String result = EntityUtils.toString(response.getEntity());
            // writing response to log
            LOGGER.info("get response: " + result);
            Message msg = new Message();
            msg.what = MainActivity.HTTPRESPONSE_MSG;
            msg.obj = result;
            handler.sendMessage(msg);

        } catch (IOException e) {
            // writing exception to log
            LOGGER.severe("error: "+e.toString());
        } catch (Exception e) {
            LOGGER.severe("error: "+e.toString());
        }
    }
}

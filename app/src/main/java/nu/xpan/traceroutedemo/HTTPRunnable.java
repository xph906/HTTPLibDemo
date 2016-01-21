package nu.xpan.traceroutedemo;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by a on 1/8/16.
 */
public class HTTPRunnable implements Runnable {
    private final static Logger LOGGER = Logger.getLogger("HTTPRunnable");

    private String url;
    private String method;
    private Handler handler;
    private HTTPTaskType taskType;

    public enum HTTPTaskType{
        IMAGE,
        STRING,
        BINARY
    }

    public HTTPRunnable(String url, Handler handler, HTTPTaskType type){
        this.url = url;
        this.handler = handler;
        this.taskType = type;
    }

    public void getStringRequest(String url) throws Exception{
        OkHttpClient client = new OkHttpClient();
        Request request = new okhttp3.Request.Builder()
                .url(this.url)
                .build();
        Call c = client.newCall(request);
        Response response = c.execute();
        ResponseBody body = response.body();
        String contents = body.string();
        Call.CallTiming timingObj = c.getCallTiming();

        long t1 = timingObj.startTimeANP;
        long t2 = timingObj.endTimeANP;
        List<Request.RequestTimingANP> timingsANP = timingObj.timingsANP;
        List<String> urlsANP = timingObj.urlsANP;

        if(timingsANP.size() != urlsANP.size()){
            throw new Exception("the sizes of urlsANP and timingsANP are not the same ");
        }
        Iterator<String> urlIter = urlsANP.iterator();
        Iterator<Request.RequestTimingANP> timingIter = timingsANP.iterator();
        StringBuilder sb = new StringBuilder();
        while(urlIter.hasNext()){
            String curURL = urlIter.next();
            Request.RequestTimingANP timing = timingIter.next();
            long dnsDelay = timing.getDnsEndTimeANP() - timing.getDnsStartTimeANP();
            long connSetupDelay = timing.getConnSetupEndTimeANP() - timing.getConnSetupStartTimeANP();
            long reqWriteDelay = timing.getReqWriteEndTimeANP() - timing.getReqWriteStartTimeANP();
            long respDelay = timing.getRespEndTimeANP() - timing.getReqWriteStartTimeANP();
            long TTFB = timing.getRespStartTimeANP() - timing.getReqWriteEndTimeANP();
            long respTransDelay = timing.getRespEndTimeANP() - timing.getRespStartTimeANP();
            long overallDelay = timing.getRespEndTimeANP() - timing.getReqStartTimeANP();
            sb.append(String.format(
                    "Overall:%dms dns:%dms, connSetup:%dms (handshake:%dms), " +
                            "server:%dms, resp:%dms (1.reqwrite:%dms 2.TTFB:%dms, 3.respTrans:%dms ) \n for URL:%s\n",
                    overallDelay, dnsDelay, connSetupDelay,
                    timing.getHandshakeTimeANP(), timing.getEstimatedServerDelay(), respDelay, reqWriteDelay,  TTFB, respTransDelay, curURL));
        }

        LOGGER.info("get response: " + contents);
        Message msg = new Message();

        msg.what = MainActivity.MSGType.HTTPRESPONSE_MSG;
        msg.obj = sb.toString()+contents;
        handler.sendMessage(msg);
        LOGGER.info("");
    }

    public void getImageRequest(String url) throws Exception{
        OkHttpClient client = new OkHttpClient();
        Request request = new okhttp3.Request.Builder()
                .url(this.url)
                .build();
        Call c = client.newCall(request);
        Response response = c.execute();
        ResponseBody body = response.body();
        Bitmap bitmap = body.bitmap();
        if(bitmap == null){
            Message msg = new Message();

            msg.what = MainActivity.MSGType.ERROR_MSG;
            msg.obj = "BITMAP is NULL";
            handler.sendMessage(msg);
            return;
        }
        Call.CallTiming timingObj = c.getCallTiming();

        long t1 = timingObj.startTimeANP;
        long t2 = timingObj.endTimeANP;
        List<Request.RequestTimingANP> timingsANP = timingObj.timingsANP;
        List<String> urlsANP = timingObj.urlsANP;

        if(timingsANP.size() != urlsANP.size()){
            throw new Exception("the sizes of urlsANP and timingsANP are not the same ");
        }
        Iterator<String> urlIter = urlsANP.iterator();
        Iterator<Request.RequestTimingANP> timingIter = timingsANP.iterator();
        StringBuilder sb = new StringBuilder();
        while(urlIter.hasNext()){
            String curURL = urlIter.next();
            Request.RequestTimingANP timing = timingIter.next();
            long dnsDelay = timing.getDnsEndTimeANP() - timing.getDnsStartTimeANP();
            long connSetupDelay = timing.getConnSetupEndTimeANP() - timing.getConnSetupStartTimeANP();
            long reqWriteDelay = timing.getReqWriteEndTimeANP() - timing.getReqWriteStartTimeANP();
            long respDelay = timing.getRespEndTimeANP() - timing.getReqWriteStartTimeANP();
            long TTFB = timing.getRespStartTimeANP() - timing.getReqWriteEndTimeANP();
            long respTransDelay = timing.getRespEndTimeANP() - timing.getRespStartTimeANP();
            long overallDelay = timing.getRespEndTimeANP() - timing.getReqStartTimeANP();
            sb.append(String.format(
                    "Overall:%dms dns:%dms, size:%d, connSetup:%dms (handshake:%dms), " +
                            "server:%dms, resp:%dms (1.reqwrite:%dms 2.TTFB:%dms, 3.respTrans:%dms ) \n for URL:%s\n",
                    overallDelay, bitmap.getByteCount(), dnsDelay, connSetupDelay,
                    timing.getHandshakeTimeANP(), timing.getEstimatedServerDelay(), respDelay, reqWriteDelay,  TTFB, respTransDelay, curURL));
        }

        LOGGER.info("get image: " + bitmap.getByteCount()+" bytes");
        Message msg = new Message();

        msg.what = MainActivity.MSGType.IMAGE_MSG;
        msg.obj = bitmap;
        handler.sendMessage(msg);
        LOGGER.info("");
    }

    @Override
    public void run() {
        //HttpClient client = new DefaultHttpClient();
        //HttpGet request = new HttpGet(url);
        // Making HTTP Request
        try {
            switch(taskType){
                case IMAGE:
                    getImageRequest(this.url);
                    break;
                case STRING:
                    getStringRequest(this.url);
                    break;
                default:
                    Message msg = new Message();
                    msg.what = MainActivity.MSGType.ERROR_MSG;
                    msg.obj = "Error: task type is not supported";
                    handler.sendMessage(msg);
            }
        } catch (Exception e) {
            // writing exception to log
            LOGGER.severe("error: "+e.toString());
        }
    }
}

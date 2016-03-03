package nu.xpan.traceroutedemo;

import android.content.Context;
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
    private Context context;

    public enum HTTPTaskType{
        IMAGE,
        STRING,
        BINARY
    }

    public class ImageMsg{
        public String comments;
        public Bitmap map;
        public ImageMsg(String c, Bitmap b){
            this.comments = c;
            this.map = b;
        }
    }

    public HTTPRunnable(Context context, String url, Handler handler, HTTPTaskType type){
        this.context = context;
        this.url = url;
        this.handler = handler;
        this.taskType = type;
    }

    public void getStringRequest(String url) throws Exception{
        System.err.println("in getStringRequest 1");
        OkHttpClient client = new OkHttpClient();
        System.err.println("in getStringRequest 2");
        Request request = new okhttp3.Request.Builder()
                .url(this.url)
                .build();
        System.err.println("in getStringRequest 3");
        Call c = client.newCall(request);
        Response response = c.execute();
        System.err.println("in getStringRequest 4");
        ResponseBody body = response.body();
        System.err.println("in getStringRequest 5");
        String contents = body.string();
        System.err.println("Get contents: "+contents);
        Call.CallStatInfo timingObj = c.getCallStatInfo();
        c.storeCallStatInfo(true);

        if (timingObj == null)
            throw new Exception("timing is null!");

        long t1 = timingObj.getStartTimeANP();
        long t2 = timingObj.getEndTimeANP();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Timing: call overall delay: %d \n", t2 - t1));

        List<Request.RequestTimingANP> timingsANP = timingObj.getTimingsANP();
        List<String> urlsANP = timingObj.getUrlsANP();
        // Debugging purpose
        if (timingsANP.size() != urlsANP.size()) {
            throw new Exception(
                    "the sizes of urlsANP and timingsANP are not the same ");
        }

        // Display timing information
        Iterator<String> urlIter = urlsANP.iterator();
        Iterator<Request.RequestTimingANP> timingIter = timingsANP.iterator();
        while (urlIter.hasNext()) {
            String curURL = urlIter.next();
            Request.RequestTimingANP timing = timingIter.next();
            long dnsDelay = timing.getDnsEndTimeANP()
                    - timing.getDnsStartTimeANP();
            boolean useConnCache = timing.useConnCache();
            long connSetupDelay = timing.getConnSetupEndTimeANP()
                    - timing.getConnSetupStartTimeANP();
            long tlsConnDelay = timing.getTlsHandshakeTimeANP();
            long reqWriteDelay = timing.getReqWriteEndTimeANP()
                    - timing.getReqWriteStartTimeANP();
            long respDelay = timing.getRespEndTimeANP()
                    - timing.getReqWriteStartTimeANP();
            long TTFB = timing.getRespStartTimeANP()
                    - timing.getReqWriteEndTimeANP();
            long respTransDelay = timing.getRespEndTimeANP()
                    - timing.getRespStartTimeANP();
            long overallDelay = timing.getRespEndTimeANP()
                    - timing.getReqStartTimeANP();

            sb.append(String.format("  timing for url:%s\n", curURL));
            sb.append(String.format(
                    "    overall:%dms \n    dns:%dms \n    connSetup:%dms cache: %s  (handshake:%dms, tls:%dms) "
                            + "\n    server:%dms \n    resp:%dms (1.reqwrite:%dms 2.TTFB:%dms, 3.respTrans:%dms) ",
                    overallDelay, dnsDelay, connSetupDelay,String.valueOf(useConnCache),
                    timing.getHandshakeTimeANP(), tlsConnDelay,
                    timing.getEstimatedServerDelay(), respDelay,
                    reqWriteDelay, TTFB, respTransDelay));
            sb.append(String.format(
                    "    response info:\n    returncode:%d\n    reqsize:%d\n    returnsize:%d\n"
                            + "    errorMsg:%s\n    errorDetailedMsg:%s\n",
                    c.getCallStatInfo().getFinalCodeANP(),
                    timing.getReqSizeANP(),
                    c.getCallStatInfo().getSizeANP(),
                    c.getCallStatInfo().getCallErrorMsg(),
                    c.getCallStatInfo().getDetailedErrorMsgANP()));

            sb.append('\n');
        }


        LOGGER.info("get response: " + sb.toString());
        Message msg = new Message();

        msg.what = InternalConst.MSGType.HTTPRESPONSE_MSG;
        msg.obj = sb.toString();
        handler.sendMessage(msg);
        LOGGER.info("");
    }

    public void getImageRequest(String url) throws Exception{
       /* OkHttpClient client = new OkHttpClient();
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
        Call.CallStatInfo timingObj = c.getCallTiming();

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
                    overallDelay, dnsDelay, bitmap.getByteCount(), connSetupDelay,
                    timing.getHandshakeTimeANP(), timing.getEstimatedServerDelay(), respDelay, reqWriteDelay,  TTFB, respTransDelay, curURL));
            System.err.println("dns_starttime:"+timing.getDnsEndTimeANP()+
                    " dns_ebdtime:"+timing.getDnsStartTimeANP());
        }

        LOGGER.info("get image: " + bitmap.getByteCount()+" bytes");
        Message msg = new Message();

        msg.what = MainActivity.MSGType.IMAGE_MSG;
        msg.obj = new ImageMsg(sb.toString(), bitmap);
        handler.sendMessage(msg);
        LOGGER.info(""); */
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
                    System.err.println("1111");
                    getStringRequest(this.url);
                    System.err.println("22222");
                    break;
                default:
                    Message msg = new Message();
                    msg.what = InternalConst.MSGType.ERROR_MSG;
                    msg.obj = "Error: task type is not supported";
                    handler.sendMessage(msg);
            }
        } catch (Exception e) {
            // writing exception to log
            LOGGER.severe("error: "+e.toString());
        }
    }
}

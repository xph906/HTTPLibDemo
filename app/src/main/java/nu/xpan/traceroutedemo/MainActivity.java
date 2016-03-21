package nu.xpan.traceroutedemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import android.os.*;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;


public class MainActivity extends ActionBarActivity {

    private static String HTTP_LOG_TAG = "NETDEMO";
    public static final Logger logger = Logger.getLogger(HTTP_LOG_TAG);
    private static int timeout;
    static {
        timeout = 5000;
        logger.setLevel(Level.INFO);
    }

    android.widget.Button start_button, net_button, img_button;
    Button http_cnn_button,http_douban_button;
    EditText ip_view;
    TextView result_view;
    ImageView image_view;
    ScrollView text_sview, image_sview;
    String method;
    TraceRoute traceroute;
    Handler handler;
    MyHTTPClient client;
    NetUtility util;

    Button sys_dns_button, my_dns_button, dns_cache_button;
    EditText dns_view;
    Dns sys_dns, my_dns;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        start_button = (android.widget.Button)findViewById(R.id.trace_button);
        http_cnn_button = (android.widget.Button)findViewById(R.id.http_cnn_button);
        http_douban_button = (android.widget.Button)findViewById(R.id.http_douban_button);
        net_button = (android.widget.Button)findViewById(R.id.netinfo_button);
        img_button = (android.widget.Button)findViewById(R.id.image_button);
        ip_view = (android.widget.EditText)findViewById(R.id.ip_text);
        result_view = (TextView)findViewById(R.id.result_text);
        image_view = (ImageView)findViewById(R.id.image_view);
        text_sview = (ScrollView)findViewById(R.id.text_scroll_view);
        image_sview = (ScrollView)findViewById(R.id.image_scroll_view);

        my_dns_button = (Button)findViewById(R.id.my_dns_button);
        sys_dns_button = (Button)findViewById(R.id.sys_dns_button);
        dns_cache_button = (Button)findViewById(R.id.dns_cache_button);
        dns_view = (EditText)findViewById(R.id.dns_text);

        TelephonyManager mTelephony = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        String userid = mTelephony.getDeviceId();
        System.err.println("DEBUG userid: "+userid);
        OkHttpClient.initializeNetProphet(getApplicationContext());

        sys_dns = new SysDns();
        my_dns = new NetProphetDns();

        handler = new Handler(Looper.getMainLooper()) {
            String contents;
            HTTPRunnable.ImageMsg imageMsg;
            @Override
            public void handleMessage(Message inputMessage) {
                switch (inputMessage.what){
                    case InternalConst.MSGType.TRACEROUTE_MSG:
                        contents = (String)inputMessage.obj;
                        result_view.append(contents+"\n");
                        break;
                    case InternalConst.MSGType.HTTPRESPONSE_MSG:
                        contents = (String)inputMessage.obj;
                        result_view.append(contents+"\n");
                        break;
                    case InternalConst.MSGType.IMAGE_MSG:
                        imageMsg = (HTTPRunnable.ImageMsg)inputMessage.obj;
                        String rs = String.format(
                                "get image with size:%dbytes\n timing info: %s",
                                imageMsg.map.getByteCount(),imageMsg.comments);
                        result_view.append(rs+"\n");
                        image_view.setImageBitmap(imageMsg.map);
                        break;
                    case InternalConst.MSGType.ERROR_MSG:
                        contents = (String)inputMessage.obj;
                        result_view.append(contents+"\n");
                        break;
                    case InternalConst.MSGType.NETINFO_MSG:
                        String old_content = result_view.getText().toString();
                        contents = (String)inputMessage.obj;
                        result_view.setText("");
                        result_view.append(contents+"\n");
                        result_view.append(old_content);
                        break;
                    case InternalConst.MSGType.DNS_MSG:
                          contents = (String)inputMessage.obj;
                        result_view.setText(contents);
                        break;

                    default:
                        super.handleMessage(inputMessage);
                }

            }
        };
        client = new MyHTTPClient(getApplicationContext(), handler);
        util = new NetUtility(getApplicationContext(), handler);

        traceroute = new TraceRoute(getApplicationContext(),timeout, handler);
        if(!traceroute.isInstalled()) {
            traceroute.installTraceroute();
        }

        start_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                result_view.setText("start running ping for local gateway, click NetInfo for details");
                util.refreshFirstMileLatency();
            }
        });
        http_cnn_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                result_view.setText("");
                String url = "http://rss.cnn.com/rss/cnn_world.rss";
                logger.log(Level.INFO, "start loading HTTP object:" + url);
                client.loadString(url);
                logger.log(Level.INFO, "done handling  ...");
            }
        });
        http_douban_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                result_view.setText("");
                String url = "http://rss.news.sohu.com/rss/focus.xml";
                logger.log(Level.INFO, "start loading HTTP object:" + url);
                client.loadString(url);

            }
        });

        net_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
               // result_view.setText("");

                result_view.setText(util.toString());
            }
        });

        img_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String url = "http://garuda.cs.northwestern.edu:3000/image-large";
                logger.log(Level.INFO, "start request image:" + url);
                client.loadImage(url);
                logger.log(Level.INFO, "done loading image  ...");
            }
        });
        my_dns_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ThreadPerTaskExecutor executor = new ThreadPerTaskExecutor();
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String hostname = dns_view.getText().toString();
                            List<InetAddress> rs= my_dns.lookup(hostname);

                            StringBuilder sb = new StringBuilder();
                            if(rs == null){
                                System.err.println("failed dnslookup");
                            }
                            else{
                                for(InetAddress ad : rs){
                                    sb.append(ad.getHostAddress().toString()+'\n');
                                }
                            }

                            Message msg = new Message();

                            msg.what = InternalConst.MSGType.DNS_MSG;
                            msg.obj = sb.toString();
                            handler.sendMessage(msg);
                        }
                        catch(Exception e){
                            System.err.println("my_dns error: "+e.toString());
                            e.printStackTrace();
                        }
                    }
                });
            }
        });

        sys_dns_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ThreadPerTaskExecutor executor = new ThreadPerTaskExecutor();
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String hostname = dns_view.getText().toString();
                            sys_dns.lookup(hostname);
                            String rs = null;
                            if(((SysDns)sys_dns).isSuccessful())
                                rs = String.format("Successful DNS:%s\n Delay: %d",
                                    hostname,((SysDns)sys_dns).getDNSDelay() );
                            else
                                rs = String.format("Failed DNS:%s\n Delay: %d\n Msg: %s",
                                    hostname, ((SysDns)sys_dns).getDNSDelay(),
                                    ((SysDns)sys_dns).getErrorMsg());

                            Message msg = new Message();

                            msg.what = InternalConst.MSGType.DNS_MSG;
                            msg.obj = rs;
                            handler.sendMessage(msg);
                        }
                        catch(Exception e){
                            System.err.println("sys_dns_button error: "+e.toString());
                            e.printStackTrace();
                        }
                    }
                });
            }
        });

        dns_cache_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String hostname = dns_view.getText().toString();
                List<InetAddress> ads = new ArrayList<InetAddress>();
                StringBuilder errorMsg = new StringBuilder();
                boolean hasCache = ((NetProphetDns)my_dns).searchSystemDNSCache(hostname,ads,errorMsg);
                String rs = null;
                if (hasCache == false){
                    rs = String.format("%s has NO DNS cache\n", hostname);
                }
                else{
                    if(errorMsg.toString().length() != 0){
                        rs = String.format("Negative cache found for %s: %s \n", hostname,errorMsg.toString());
                    }
                    else{
                        StringBuilder sb = new StringBuilder();
                        for (InetAddress ad : ads){
                            sb.append(ad.getHostAddress()+"\n");
                        }
                        rs = String.format("%s has DNS cache:\n %s\n", hostname, sb.toString());
                    }
                }
                StringBuilder sb = new StringBuilder();
                try {
                    InetAddress addr = ((NetProphetDns) my_dns).testCache(1, hostname);
                    if (addr == null){
                        sb.append("NetProphet DefaultCache doesn't contain this item:\n");
                    }
                    else {
                        sb.append("NetProphet DefaultCache item:\n");
                        sb.append(addr.getHostAddress() + "\n");
                    }

                    addr = ((NetProphetDns) my_dns).testCache(2, hostname);
                    if (addr == null){
                        sb.append("NetProphet SecondLevelCache doesn't contain this item:\n");
                    }
                    else {
                        sb.append("NetProphet SecondLevelCache item:\n");
                        sb.append(addr.getHostAddress() + "\n");
                    }

                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }

                result_view.setText(rs+"\n"+sb.toString());
            }
        });

        //testing


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    class ThreadPerTaskExecutor implements Executor {
        public void execute(Runnable r) {
            new Thread(r).start();
        }
    }
}

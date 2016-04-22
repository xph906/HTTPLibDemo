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

import netprophet.NetProphet;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;


public class MainActivity extends ActionBarActivity {

    private static String HTTP_LOG_TAG = "NETPROPHET";
    public static final Logger logger = Logger.getLogger(HTTP_LOG_TAG);
    private static int timeout;
    static {
        timeout = 5000;
        logger.setLevel(Level.INFO);
    }
    /*
    * Testing apps:
    *   Most Popular app:   Douban.com
    *                       Cnn.com
    *                       avito.ru
    *   Least Popular app:  instalook.ru
    *                       zdal.cn
    *                       arenarating.com
    * */
    //Networking related buttons
    Button bw_testing_button, netinfo_button;
    //DNS related buttons
    Button dns_server_testing_button, dns_cache_display_button;
    //App related buttons
    Button http_cnn_button,http_douban_button, http_avito_button;
    Button http_instalook_button, http_zdal_button, http_aren_button;

    //Input field and result output
    EditText ip_view;
    TextView result_view;
    ScrollView text_sview;
    Button clear_output_button;

    String method;
    TraceRoute traceroute;
    Handler handler;
    MyHTTPClient client;
    netprophet.NetUtility netUtility;

    Button sys_dns_button, my_dns_button, dns_cache_button;
    EditText dns_view;
    Dns sys_dns, my_dns;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Networking related buttons
        bw_testing_button = (android.widget.Button)findViewById(R.id.bw_testing_button);
        netinfo_button = (android.widget.Button)findViewById(R.id.netinfo_button);
        //DNS related buttons
        dns_server_testing_button = (android.widget.Button)findViewById(R.id.dns_server_testing_button);
        dns_cache_display_button = (android.widget.Button)findViewById(R.id.dns_cache_display_button);
        //App related buttons
        http_cnn_button = (android.widget.Button)findViewById(R.id.http_cnn_button);
        http_douban_button = (android.widget.Button)findViewById(R.id.http_douban_button);
        http_avito_button = (android.widget.Button)findViewById(R.id.http_avito_button);
        http_instalook_button = (android.widget.Button)findViewById(R.id.http_instalook_button);
        http_zdal_button = (android.widget.Button)findViewById(R.id.http_zdal_button);
        http_aren_button = (android.widget.Button)findViewById(R.id.http_aren_button);

        //Input field and result output
        ip_view = (android.widget.EditText)findViewById(R.id.ip_text);
        result_view = (TextView)findViewById(R.id.result_text);
        text_sview = (ScrollView)findViewById(R.id.text_scroll_view);
        clear_output_button = (android.widget.Button)findViewById(R.id.clear_output_button);

        //Initialize NetProphet
        NetProphet.enableTestingMode();
        NetProphet.initializeNetProphet(getApplicationContext(), false);

        TelephonyManager mTelephony = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        //String userid = mTelephony.getDeviceId();

        handler = new Handler(Looper.getMainLooper()) {
            String contents;
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

                    case InternalConst.MSGType.ERROR_MSG:
                        contents = (String)inputMessage.obj;
                        result_view.append(contents+"\n");
                        break;

                    case InternalConst.MSGType.NETINFO_MSG:
                        String old_content = result_view.getText().toString();
                        contents = (String)inputMessage.obj;
                        result_view.setText(contents+"\n");
                        result_view.append(old_content);
                        break;

                    case InternalConst.MSGType.DNS_MSG:
                        contents = (String)inputMessage.obj;
                        result_view.setText(contents);
                        break;

                    case InternalConst.MSGType.APP_MEASURE_MSG:
                        contents = (String)inputMessage.obj;
                        result_view.setText(contents);
                        break;

                    default:
                        super.handleMessage(inputMessage);
                }

            }
        };

        client = new MyHTTPClient(getApplicationContext(), handler);
        netUtility = netprophet.NetUtility.getInstance(getApplicationContext(), null);

        /*traceroute = new TraceRoute(getApplicationContext(),timeout, handler);
        if(!traceroute.isInstalled()) {
            traceroute.installTraceroute();
        }*/

        //Client Event
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

        bw_testing_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                result_view.setText("");
                netprophet.LocalBandwidthMeasureTool tool = netprophet.LocalBandwidthMeasureTool.getInstance();
                tool.startMeasuringTask(netUtility);
            }
        });

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

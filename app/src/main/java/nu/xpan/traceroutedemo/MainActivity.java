package nu.xpan.traceroutedemo;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import java.util.logging.Level;
import java.util.logging.Logger;
import android.os.*;


public class MainActivity extends ActionBarActivity {
    public static final int TRACEROUTE_MSG = 1;
    public static final int HTTPRESPONSE_MSG = 2;
    private static String HTTP_LOG_TAG = "NETDEMO";
    public static final Logger logger = Logger.getLogger(HTTP_LOG_TAG);
    private static int timeout;
    static {
        timeout = 5000;
        logger.setLevel(Level.INFO);
    }

    android.widget.Button start_button, http_button, net_button;
    EditText ip_view;
    TextView result_view;
    String method;
    TraceRoute traceroute;
    Handler handler;
    MyHTTPClient client;
    NetUtility util;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        start_button = (android.widget.Button)findViewById(R.id.trace_button);
        http_button = (android.widget.Button)findViewById(R.id.http_button);
        net_button = (android.widget.Button)findViewById(R.id.netinfo_button);
        ip_view = (android.widget.EditText)findViewById(R.id.ip_text);
        result_view = (TextView)findViewById(R.id.result_text);

        util = new NetUtility(getApplicationContext());

        handler = new Handler(Looper.getMainLooper()) {
            String contents;
            @Override
            public void handleMessage(Message inputMessage) {
                switch (inputMessage.what){
                    case TRACEROUTE_MSG:
                        contents = (String)inputMessage.obj;
                        logger.log(Level.INFO,
                                "TRACEROUTE_MSG: having received the msg "+contents);
                        result_view.append(contents+"\n");
                        break;
                    /*case HTTPRESPONSE_MSG:
                        contents = (String)inputMessage.obj;
                        LOGGER.log(Level.INFO,
                                "HTTPRESPONSE_MSG: having received the msg "+contents);
                        result_view.append(contents+"\n");
                        break;*/
                    default:
                        super.handleMessage(inputMessage);
                }
            }
        };
        client = new MyHTTPClient(handler);


        traceroute = new TraceRoute(getApplicationContext(),timeout, handler);
        if(!traceroute.isInstalled()) {
            traceroute.installTraceroute();
        }

        start_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                result_view.setText("");
                String ip = ip_view.getText().toString();
                System.out.println("the ip is: " + ip);
                traceroute.runTraceroute(ip);
                System.out.println("done clicking ...");

            }
        });
        http_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                result_view.setText("");
                String url = "http://rss.cnn.com/rss/cnn_world.rss";
                logger.log(Level.INFO, "start request HTTP object:" + url);
                client.startNewRequest("get", url);
                logger.log(Level.INFO, "done handling  ...");
            }
        });

        net_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                result_view.setText("");
                String netType = util.isUsingWIFI()? "WIFI" : "Cellular";
                if(!util.isConnected()){
                    netType = "None";
                }
                String connectedText = String.format(
                        "isConnected:   %b\n",
                        util.isConnected());
                String typeText = String.format(
                        "type       :   %s\n",
                        netType
                );
                result_view.setText(connectedText+typeText);
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
}

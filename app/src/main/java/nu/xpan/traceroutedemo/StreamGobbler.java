package nu.xpan.traceroutedemo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import android.util.Log;
import android.os.Handler;
import android.os.Message;

class StreamGobbler
        extends Thread
{
    private static String LOG_TAG;
    static {
        LOG_TAG = "STREAMGOBBLER";
    }
    private InputStream inputStream;
    private String streamType;
    private Handler handler;
    private int msgType;

    /**
     * Constructor.
     *
     * @param inputStream the InputStream to be consumed
     * @param streamType the stream type (should be OUTPUT or ERROR)
     */
    StreamGobbler(final InputStream inputStream,
                  final String streamType, Handler handler, int msgType)
    {
        this.inputStream = inputStream;
        this.streamType = streamType;
        this.handler = handler;
        this.msgType = msgType;
    }

    /**
     * Consumes the output from the input stream and displays the lines consumed if configured to do so.
     */
    @Override
    public void run()
    {
        try
        {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line = null;
            StringBuilder sb = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null)
            {
                sb.append(line+"\n");
            }
            if(sb.toString().trim().length()==0)
                return;
            Message msg = new Message();
            msg.what = msgType;
            msg.obj = "["+streamType+"] \n"+sb.toString();
            handler.sendMessage(msg);
            //System.out.println("done sending the msg");
        }
        catch (IOException ex)
        {
            System.err.println("Failed to consume input stream of type " + streamType +
                    "."+ex.toString());
        }
        catch (Exception ex){
            System.err.println("Failed to consume input stream of type "+ streamType +
                    "."+ex.toString());
            ex.printStackTrace();
        }
    }
}
package nu.xpan.traceroutedemo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Created by xpan on 2/2/16.
 */
public class ImageProcessor {
    private Bitmap originalBitMap;
    public Bitmap readImage(String path){
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ALPHA_8;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        originalBitMap = bitmap;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Bitmap newBitMap = Bitmap.createBitmap(bitmap,740,15,80,52);
        System.err.println("width:"+width+" height:"+height);

        return newBitMap;
    }
    public Bitmap corp(int x, int y, int width, int height){
        return Bitmap.createBitmap(originalBitMap,x,y,width,height);
    }
}

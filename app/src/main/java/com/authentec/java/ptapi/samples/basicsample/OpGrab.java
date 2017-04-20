package com.authentec.java.ptapi.samples.basicsample;

import com.upek.android.ptapi.PtConnectionI;
import com.upek.android.ptapi.PtConstants;
import com.upek.android.ptapi.PtException;
import com.upek.android.ptapi.callback.PtGuiStateCallback;
import com.upek.android.ptapi.struct.*;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.widget.Toast;

public abstract class OpGrab extends Thread implements PtGuiStateCallback
{
    private PtConnectionI mConn;
    private Activity mActivity;
    private byte mbyGrabType;

    public OpGrab(PtConnectionI conn,byte byGrabType,Activity aActivity)
    {
        super("GrabThread");
        mConn = conn;
        mActivity = aActivity;
        mbyGrabType = byGrabType;
        Toast.makeText(mActivity.getApplicationContext(),mConn.toString(),Toast.LENGTH_SHORT).show();
    }

    /**
     * Callback function called by PTAPI.
     */
    public byte guiStateCallbackInvoke(int guiState, int message,  byte progress,
                                       PtGuiSampleImage sampleBuffer, byte[] data)
    {
        String s = PtHelper.GetGuiStateCallbackMessage(guiState,message,progress);

        if(s != null)
        {
            onDisplayMessage(s);
        }

        return isInterrupted() ? PtConstants.PT_CANCEL : PtConstants.PT_CONTINUE;
    }

    /**
     * Cancel running operation
     */
    @Override
    public void interrupt()
    {
        super.interrupt();

        try
        {
            mConn.cancel(0);
        }
        catch (PtException e)
        {
        	/* Ignore errors */
        }
    }


    public static Bitmap CreateBitmap(byte [] aImageData,int iWidth)
    {
        int[] data = new int[aImageData.length];
        int iLength = aImageData.length;
        int iHeight = iLength / iWidth;
        for(int i=0; i<iLength; i++)
        {
            int color = (int)aImageData[i];
            if(color < 0)
            {
                color = 256 + color;
            }
            data[i] = Color.rgb(color,color,color);
        }
        return Bitmap.createBitmap(data, iWidth, iHeight, Bitmap.Config.ARGB_8888);
    }


    private void ShowImage(byte [] aImage,int iWidth)
    {
        FPDisplay.mImage = CreateBitmap(aImage,iWidth);
        FPDisplay.msTitle = "Fingerprint Image";
        Intent aIntent = new Intent(mActivity, FPDisplay.class);
        mActivity.startActivityForResult(aIntent,0);
    }


    /**
     * Grab execution code. 
     */
    @Override
    public void run()
    {
        try
        {
            // Optional: Set session configuration 
            ModifyConfig();

            // Obtain finger template
            byte [] image = GrabImage(mbyGrabType);
            int iWidth = (mConn.info().imageWidth);
            onDisplayMessage("Image grabbed");
            switch(mbyGrabType)
            {
                case PtConstants.PT_GRAB_TYPE_THREE_QUARTERS_SUBSAMPLE:
                    iWidth = (iWidth * 3)>>2;
                case PtConstants.PT_GRAB_TYPE_508_508_8_SCAN508_508_8:
                    ShowImage(image,iWidth);
                    break;
                default:
                    // unsupported image type for displaying
            }
        }
        catch (PtException e)
        {

        }

        // Un-cancel session
        try
        {
            mConn.cancel(1);
        }
        catch (PtException e1) {}

        onFinished();
    }

    /**
     * Modify session configuration if needed
     */
    private void ModifyConfig()  throws PtException
    {
        /*try 
         * {
           final short SESSION_CFG_VERSION = 5;
            PtSessionCfgV5 sessionCfg = (PtSessionCfgV5) mConn.getSessionCfgEx(SESSION_CFG_VERSION);
        
            mConn.setSessionCfgEx(SESSION_CFG_VERSION, sessionCfg);
        }
         catch (PtException e) 
        {
            onDisplayMessage("Unable to set session cfg - " + e.getMessage());
            throw e;
        }*/
    }

    /**
     * Obtain finger template.
     */
    private byte[] GrabImage(byte byType) throws PtException
    {
        try
        {
            // Register notification callback of operation state
            // Valid for entire PTAPI session lifetime
            mConn.setGUICallbacks(null, this);

            return mConn.grab(byType,PtConstants.PT_BIO_INFINITE_TIMEOUT,true,null,null);
        }
        catch (PtException e)
        {
            onDisplayMessage("Grab failed - " + e.getMessage());
            throw e;
        }
    }


    /**
     * Display message. To be overridden by sample activity.
     * @param message Message text.
     */
    abstract protected void onDisplayMessage(String message);

    /**
     * Called, if operation is finished.
     * @param message Message text.
     */
    abstract protected void onFinished();

}

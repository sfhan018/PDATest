package com.authentec.java.ptapi.samples.basicsample;

import com.upek.android.ptapi.PtConnectionI;
import com.upek.android.ptapi.PtConstants;
import com.upek.android.ptapi.PtException;
import com.upek.android.ptapi.callback.PtNavigationCallback;
import com.upek.android.ptapi.struct.PtNavigationData;

public abstract class OpNavigate extends Thread implements PtNavigationCallback
{       
    private PtConnectionI mConn;
    private OpNavigateSettings mSettings;

    public OpNavigate(PtConnectionI conn, OpNavigateSettings settings)
    {
        super("NavigateThread");
        mConn = conn;
        mSettings = settings;
    }
    
    public byte navigationCallbackInvoke(PtNavigationData navigationData)
    {        
        int deltaX = -navigationData.dx;
        int deltaY = navigationData.dy;
        
        onDisplayMessage("dx:" + deltaX + "dy:" + deltaY + "signalBits:" + Integer.toHexString(navigationData.signalBits));

        try
        {
            sleep(25);
        }
        catch (InterruptedException e) 
        {
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
        } catch (PtException e)
        {
            // Ignore errors
        }
    }

    /** 
     * Operation execution code. 
     */
    @Override
    public void run()
    {       
        try
        {
            byte[] settings = (mSettings == null) ? null : mSettings.serialize(false, 0);
            mConn.navigate(-1, this, settings);
        }
        catch (PtException e)
        {
            onDisplayMessage("Navigation failed - " + e.getMessage());
        } 
        catch (Exception e)
        {
            onDisplayMessage(e.getLocalizedMessage());
        }

        onFinished();
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

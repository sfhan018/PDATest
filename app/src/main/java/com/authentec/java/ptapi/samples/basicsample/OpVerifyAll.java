package com.authentec.java.ptapi.samples.basicsample;

import com.upek.android.ptapi.PtConnectionI;
import com.upek.android.ptapi.PtConstants;
import com.upek.android.ptapi.PtException;
import com.upek.android.ptapi.callback.PtGuiStateCallback;
import com.upek.android.ptapi.struct.PtFingerListItem;
import com.upek.android.ptapi.struct.PtGuiSampleImage;

public abstract class OpVerifyAll extends Thread implements PtGuiStateCallback
{
    
    private PtConnectionI mConn;

    public OpVerifyAll(PtConnectionI conn)
    {
        super("VerifyAllThread");
        mConn = conn;
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

        // With sensor only solution isn't necessary because of PtCancel() presence
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
            int index = -1;

            // Register notification callback of operation state
            // Valid for entire PTAPI session lifetime
            mConn.setGUICallbacks(null, this);
            
            // List fingers stored in device
            PtFingerListItem[] fingerList = mConn.listAllFingers();
            
            if((fingerList == null) || (fingerList.length == 0))
            {
                onDisplayMessage("No templates enrolled");
            } 
            else
            {
            
                // Run verification against all templates stored at device
                // It is supposed that device contains only templates enrolled by
                // this sample. In opposite case, verifyEx() with listed templates
                // would be preferred call.
                index = mConn.verifyAll(null, null, null, null, null, null, null, 
                                        PtConstants.PT_BIO_INFINITE_TIMEOUT, true,
                                        null, null, null);
                if (-1 != index)
                {
                    // Display finger ID
                    for(int i=0; i<fingerList.length; i++)
                    {
                        PtFingerListItem item = fingerList[i];
                        int slotId = item.slotNr;
                        
                        if(slotId == index)
                        {
                            byte[] fingerData = item.fingerData;
        
                            if((fingerData != null) && (fingerData.length >= 1))
                            {
                                int fingerId = item.fingerData[0];
                                onDisplayMessage(FingerId.NAMES[fingerId] + " finger matched");
                                
                            } 
                            else
                            {
                                onDisplayMessage("No fingerData set");
                            }
                        }
                    }
                }
                else
                {
                    onDisplayMessage("No match found.");
                }
                
            }
        } 
        catch (PtException e)
        {
            onDisplayMessage("Verification failed - " + e.getMessage());
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

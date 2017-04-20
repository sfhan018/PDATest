package com.authentec.java.ptapi.samples.basicsample;

/**
 * Sample showing finger enrollment, identification, and removal.
 */

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.upek.android.ptapi.PtConnectionAdvancedI;
import com.upek.android.ptapi.PtConstants;
import com.upek.android.ptapi.PtException;
import com.upek.android.ptapi.PtGlobal;
import com.upek.android.ptapi.struct.PtInfo;
import com.upek.android.ptapi.struct.PtSessionCfgV5;
import com.upek.android.ptapi.usb.PtUsbHost;

import java.io.File;

public class SampleActivity extends Activity
{
    public static final String mDSN[] = {"usb,timeout=500", "wbf,timeout=500"};
    //Windows DSN for serial connection
    public static final String msDSNSerial = "sio,port=COM1,speed=115200,timeout=2000";
    //Linux DSN for serial connection
    //public static final String msDSNSerial = "sio,port=/dev/ttyS0,speed=115200,timeout=2000";
    public static final int miUseSerialConnection = 0;

    //This variable configures support for STM32 area reader. This sensor requires additional data storage (temporary file)
    //On emulated environment (emulator + bridge) it must be set to zero, so the default setting will be used
    //On real Android device the default place for storage doesn't work as it must be detected in runtime
    //Set this variable to 1 to enable this behavior
    public static final int miRunningOnRealHardware = 1;


    private PtGlobal mPtGlobal = null;
    private PtConnectionAdvancedI mConn = null;
    private PtInfo mSensorInfo = null;
    private Thread mRunningOp = null;
    private final Object mCond = new Object();

    //will contain path for temporary files on real Android device
    private String msNvmPath = null;
    private MyPrintLog mpl = null;


    /** Initialize activity and obtain PTAPI session. */
    @SuppressWarnings("unused")
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        if(miRunningOnRealHardware != 0)
        {
            //find the directory for temporary files
            Context aContext = getBaseContext();
            if(aContext != null)
            {
                File aDir = aContext.getDir("tcstore",Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE );
                if(aDir != null)
                {
                    msNvmPath = aDir.getAbsolutePath();
                }
            }
        }
        mpl = new MyPrintLog();
        setContentView(R.layout.main);
        mpl.writelog("PDATest:onCreate");
        // Load PTAPI library and initialize its interface
        if(initializePtapi())
        {
            // Open PTAPI session
            openPtapiSession();

            // If PTAPI session is available, register listeners for  buttons
            if(mConn != null)
            {
                mpl.writelog("PDATest:setClick");
                setEnrollButtonListener(R.id.ButtonLeftThumb,  FingerId.LEFT_THUMB);
                setEnrollButtonListener(R.id.ButtonLeftIndex,  FingerId.LEFT_INDEX);
                setEnrollButtonListener(R.id.ButtonLeftMiddle, FingerId.LEFT_MIDDLE);
                setEnrollButtonListener(R.id.ButtonLeftRing,   FingerId.LEFT_RING);
                setEnrollButtonListener(R.id.ButtonLeftLittle, FingerId.LEFT_LITTLE);

                setEnrollButtonListener(R.id.ButtonRightThumb, FingerId.RIGHT_THUMB);
                setEnrollButtonListener(R.id.ButtonRightIndex, FingerId.RIGHT_INDEX);
                setEnrollButtonListener(R.id.ButtonRightMiddle,FingerId.RIGHT_MIDDLE);
                setEnrollButtonListener(R.id.ButtonRightRing,  FingerId.RIGHT_RING);
                setEnrollButtonListener(R.id.ButtonRightLittle,FingerId.RIGHT_LITTLE);

                setIdentifyButtonListener();
                setDeleteAllButtonListener();
                setGrabButtonListener();
                setNavigateRawButtonListener();
                setNavigateMouseButtonListener();
                setNavigateDiscreteButtonListener();
            }
        }
        SetQuitButtonListener();
    }

    /** Close PTAPI session. */
    @Override
    protected void onDestroy()
    {
        // Cancel running operation
        synchronized(mCond)
        {
            while(mRunningOp != null)
            {
                mRunningOp.interrupt();
                try
                {
                    mCond.wait();
                } catch (InterruptedException e)
                {
                }
            }
        }

        mpl.close();
        // Close PTAPI session
        closeSession();

        // Terminate PTAPI library
        terminatePtapi();

        super.onDestroy();
    }

    /**
     * Set listener for an enrollment button.
     * @param buttonId Resource ID of a button
     * @param fingerId Finger ID.
     */
    private void setEnrollButtonListener(final int buttonId, final int fingerId)
    {
        Button aButton = (Button)findViewById(buttonId);
        OnClickListener aListener = new View.OnClickListener()
        {
            public void onClick(View view)
            {
                synchronized(mCond)
                {
                    if(mRunningOp == null)
                    {
                        mRunningOp = new OpEnroll(mConn, fingerId)
                        {
                            @Override
                            protected void onDisplayMessage(String message)
                            {
                                dislayMessage(message);
                            }

                            @Override
                            protected void onFinished()
                            {
                                synchronized(mCond)
                                {
                                    mRunningOp = null;
                                    mCond.notifyAll();  //notify onDestroy that operation has finished
                                }
                            }
                        };
                        mRunningOp.start();
                    }
                }
            }
        };
        aButton.setOnClickListener(aListener);
    }

    /**
     * Set listener for a identification button.
     */
    private void setIdentifyButtonListener()
    {
        Button aButton = (Button)findViewById(R.id.ButtonVerifyAll);
        OnClickListener aListener = new View.OnClickListener()
        {
            public void onClick(View view)
            {
                synchronized(mCond)
                {
                    if(mRunningOp == null)
                    {
                        mRunningOp = new OpVerifyAll(mConn)
                        {
                            @Override
                            protected void onDisplayMessage(String message)
                            {
                                dislayMessage(message);
                            }

                            @Override
                            protected void onFinished()
                            {
                                synchronized(mCond)
                                {
                                    mRunningOp = null;
                                    mCond.notifyAll();  //notify onDestroy that operation has finished
                                }
                            }
                        };
                        mRunningOp.start();
                    }
                }
            }
        };
        aButton.setOnClickListener(aListener);
    }
    /**
     * Common helper for setNavigatexxxButtonListener()
     */
    private OpNavigate createNavigationOperationHelper(OpNavigateSettings aSettings)
    {
        OpNavigate aOperation = new OpNavigate(mConn, aSettings)
        {
            @Override
            protected void onDisplayMessage(String message)
            {
                dislayMessage(message);
            }

            @Override
            protected void onFinished()
            {
                synchronized(mCond)
                {
                    mRunningOp = null;
                    mCond.notifyAll();  //notify onDestroy that operation has finished
                }
            }
        };
        return aOperation;
    }

    /**
     * Set listener for a navigate button in raw mode.
     */
    private void setNavigateRawButtonListener()
    {
        Button aButton = (Button)findViewById(R.id.ButtonNavigateRaw);
        //disable navigation for area sensors
        if((mSensorInfo.sensorType & PtConstants.PT_SENSORBIT_STRIP_SENSOR) == 0)
        {
            aButton.setVisibility(Button.GONE);
            return;
        }
        OnClickListener aListener = new View.OnClickListener()
        {
            public void onClick(View view)
            {
                synchronized(mCond)
                {
                    if(mRunningOp == null)
                    {
                        mRunningOp = createNavigationOperationHelper(null);
                        mRunningOp.start();
                    }
                }
            }
        };
        aButton.setOnClickListener(aListener);
    }

    /**
     * Set listener for a navigate button in mouse mode.
     */
    private void setNavigateMouseButtonListener()
    {
        Button aButton = (Button)findViewById(R.id.ButtonNavigateMouse);
        //disable navigation for area sensors
        if((mSensorInfo.sensorType & PtConstants.PT_SENSORBIT_STRIP_SENSOR) == 0)
        {
            aButton.setVisibility(Button.GONE);
            return;
        }
        OnClickListener aListener = new View.OnClickListener()
        {
            public void onClick(View view)
            {
                synchronized(mCond)
                {
                    if(mRunningOp == null)
                    {
                        mRunningOp = createNavigationOperationHelper(OpNavigateSettings.createDefaultMousePostprocessingParams());
                        mRunningOp.start();
                    }
                }
            }
        };
        aButton.setOnClickListener(aListener);
    }

    /**
     * Set listener for a navigate button in discrete mode.
     */
    private void setNavigateDiscreteButtonListener()
    {
        Button aButton = (Button)findViewById(R.id.ButtonNavigateDiscrete);
        //disable navigation for area sensors
        if((mSensorInfo.sensorType & PtConstants.PT_SENSORBIT_STRIP_SENSOR) == 0)
        {
            aButton.setVisibility(Button.GONE);
            return;
        }
        OnClickListener aListener = new View.OnClickListener()
        {
            public void onClick(View view)
            {
                synchronized(mCond)
                {
                    if(mRunningOp == null)
                    {
                        mRunningOp = createNavigationOperationHelper(OpNavigateSettings.createDefaultDiscretePostprocessingParams());
                        mRunningOp.start();
                    }
                }
            }
        };
        aButton.setOnClickListener(aListener);
    }

    /**
     * Set listener for a delete all button.
     */
    private void setDeleteAllButtonListener()
    {
        Button aButton = (Button)findViewById(R.id.ButtonDeleteAll);
        OnClickListener aListener = new View.OnClickListener()
        {
            public void onClick(View view)
            {
                synchronized(mCond)
                {
                    if(mRunningOp == null)
                    {
                        try
                        {
                            // No interaction with a user needed
                            mConn.deleteAllFingers();
                            dislayMessage("All fingers deleted");
                        }
                        catch (PtException e)
                        {
                            dislayMessage("Delete All failed - " + e.getMessage());
                        }
                    }
                }
            }
        };
        aButton.setOnClickListener(aListener);
    }

    /**
     * Set listener for a grab button.
     */
    private void setGrabButtonListener()
    {
        Button aButton = (Button)findViewById(R.id.ButtonGrab);
        OnClickListener aListener = new View.OnClickListener()
        {
            public void onClick(View view)
            {
                synchronized(mCond)
                {
                    if(mRunningOp == null)
                    {
                        mRunningOp = new OpGrab(mConn,PtConstants.PT_GRAB_TYPE_THREE_QUARTERS_SUBSAMPLE,SampleActivity.this)
                        {
                            @Override
                            protected void onDisplayMessage(String message)
                            {
                                dislayMessage(message);
                            }

                            @Override
                            protected void onFinished()
                            {
                                synchronized(mCond)
                                {
                                    mRunningOp = null;
                                    mCond.notifyAll();  //notify onDestroy that operation has finished
                                }
                            }
                        };
                        mRunningOp.start();
                    }
                }
            }
        };
        aButton.setOnClickListener(aListener);
    }


    private void SetQuitButtonListener()
    {
        Button aButton = (Button)findViewById(R.id.ButtonQuit);
        OnClickListener aListener = new View.OnClickListener()
        {
            public void onClick(View view)
            {
                System.exit(0);
            }
        };
        aButton.setOnClickListener(aListener);
    }


    /**
     * Load PTAPI library and initialize its interface.
     * @return True, if library is ready for use.
     */
    private boolean initializePtapi()
    {
        // Load PTAPI library
        Context aContext = getApplicationContext();
        mPtGlobal = new PtGlobal(aContext);
        //Toast.makeText(this,"initializePtapi",Toast.LENGTH_SHORT).show();
        try
        {
            // Initialize PTAPI interface
            // Note: If PtBridge development technology is in place and a network
            // connection cannot be established, this call hangs forever.
            mPtGlobal.initialize();
            return true;
        }
        catch (java.lang.UnsatisfiedLinkError ule)
        {
            // Library wasn't loaded properly during PtGlobal object construction
            dislayMessage("libjniPtapi.so not loaded");
            mPtGlobal = null;
            return false;

        }
        catch (PtException e)
        {
            dislayMessage(e.getMessage());
            return false;
        }
    }

    /**
     * Terminate PTAPI library.
     */
    private void terminatePtapi()
    {
        try{
            if(mPtGlobal != null)
            {
                mPtGlobal.terminate();
            }
        } catch (PtException e)
        {
            //ignore errors
        }
        mPtGlobal = null;
    }


    private void configureOpenedDevice() throws PtException
    {
        PtSessionCfgV5 sessionCfg = (PtSessionCfgV5) mConn.getSessionCfgEx(PtConstants.PT_CURRENT_SESSION_CFG);
        sessionCfg.sensorSecurityMode = PtConstants.PT_SSM_DISABLED;
        sessionCfg.callbackLevel |= PtConstants.CALLBACKSBIT_NO_REPEATING_MSG;
        mConn.setSessionCfgEx(PtConstants.PT_CURRENT_SESSION_CFG, sessionCfg);
    }

    @SuppressWarnings("unused")
    private void openPtapiSessionInternal(String dsn) throws PtException
    {
        // Try to open device with given DSN string
        Context aContext = getApplicationContext();
        try
        {
            mpl.writelog("PDATest:PtUsbCheckDevice");
            PtUsbHost.PtUsbCheckDevice(aContext,0);
        }
        catch (PtException e)
        {
            mpl.writelog("PDATest:PtUsbCheckDevice:catch");
            throw e;
        }
        mpl.writelog("1----------------");
        mConn = (PtConnectionAdvancedI)mPtGlobal.open(dsn);

        try
        {
            // Verify that emulated NVM is initialized and accessible
            mSensorInfo = mConn.info();
        }
        catch (PtException e)
        {

            if ((e.getCode() == PtException.PT_STATUS_EMULATED_NVM_INVALID_FORMAT)
                    || (e.getCode() == PtException.PT_STATUS_NVM_INVALID_FORMAT)
                    || (e.getCode() == PtException.PT_STATUS_NVM_ERROR))
            {
                if(miRunningOnRealHardware != 0)
                {
                    //try add storage configuration and reopen the device
                    dsn += ",nvmprefix="+msNvmPath+'/';
                    Toast.makeText(this,"2---"+dsn,Toast.LENGTH_SHORT).show();
                    // Reopen session
                    mConn.close();
                    mConn = null;

                    mConn = (PtConnectionAdvancedI)mPtGlobal.open(dsn);
                    try
                    {
                        // Verify that emulated NVM is initialized and accessible
                        mSensorInfo = mConn.info();
                        configureOpenedDevice();
                        return;
                    }
                    catch (PtException e2)
                    {
                        //ignore errors and continue
                    }
                }


                // We have found the device, but it seems to be either opened for the first time
                // or its emulated NVM was corrupted.
                // Perform the manufacturing procedure.
                //To properly initialize it, we have to:
                // 1. Format its emulated NVM storage
                // 2. Calibrate the sensor

                // Format internal NVM
                mConn.formatInternalNVM(0, null, null);

                // Reopen session
                mConn.close();
                mConn = null;
                Toast.makeText(this,"3---"+dsn,Toast.LENGTH_SHORT).show();
                mConn = (PtConnectionAdvancedI)mPtGlobal.open(dsn);

                // Verify that emulated NVM is initialized and accessible
                mSensorInfo = mConn.info();
                //check if sensor is calibrated
                if((mSensorInfo.sensorType & PtConstants.PT_SENSORBIT_CALIBRATED) == 0)
                {
                    // No, so calibrate it
                    mConn.calibrate(PtConstants.PT_CALIB_TYPE_TURBOMODE_CALIBRATION);
                    // Update mSensorInfo
                    mSensorInfo = mConn.info();
                }

                // Device successfully opened
            }
            else
            {
                throw e;
            }
        }

        configureOpenedDevice();
    }

    /**
     * Open PTAPI session.
     */
    @SuppressWarnings("unused")
    private void openPtapiSession()
    {
        PtException openException = null;

/*
        if(miUseSerialConnection != 0)
        {
        	 try
             {
             	// Try to open session
         		openPtapiSessionInternal(msDSNSerial);
                 // Device successfully opened
         		return;
             }
             catch (PtException e)
             {
                     // Remember error and try remaining devices
                     openException = e;
             }
        }
        else
        {
	        // Walk through the most common DSN strings on USB
	        for(int i=0; i<mDSN.length; i++)
	        {
	            PtDeviceListItem[] devices = null;
	            // Enumerate devices
	            try
	            {
	                devices = mPtGlobal.enumerateDevices(mDSN[i]);
	            }
	            catch (PtException e1)
	            {
	                if(e1.getCode() != PtException.PT_STATUS_INVALID_PARAMETER)
	                {
	                    dislayMessage("Enumeration failed - " + e1.getMessage());
	                    return;
	                }

	                // Try to enumerate next DSN string
	                continue;
	            }

	            // Walk through enumerated devices and try to open them
	            for(int d=0; d<devices.length; d++)
	            {
	            	String dsn = devices[d].dsnSubString;
	                try
	                {
	                	// Try to open session
	            		openPtapiSessionInternal(dsn);

	                    // Device successfully opened
	            		return;
	                }
	                catch (PtException e)
	                {
	                        // Remember error and try remaining devices
	                        openException = e;
	                }
	            }
	        }
        }
*/

        try
        {
            // Try to open session
            openPtapiSessionInternal("");

            // Device successfully opened
            return;
        }
        catch (PtException e)
        {
            // Remember error and try remaining devices
            openException = e;
        }


        // No device has been opened
        if(openException == null)
        {
            dislayMessage("No device found");
        }
        else
        {
            dislayMessage("Error during device opening - " + openException.getMessage());
        }
    }

    private void closeSession()
    {
        if(mConn != null)
        {
            try
            {
                mConn.close();
            }
            catch (PtException e)
            {
                // Ignore errors
            }
            mConn = null;
        }
    }

    /**
     * Display message in TextView.
     */
    public void dislayMessage(String text)
    {
        mHandler.sendMessage(mHandler.obtainMessage(0, 0, 0, text));
    }

    /**
     * Transfer messages to the main activity thread.
     */
    private Handler mHandler = new Handler()
    {
        public void handleMessage(Message aMsg)
        {
            ((TextView)findViewById(R.id.EnrollmentTextView)).setText((String) aMsg.obj);
        }
    };
}
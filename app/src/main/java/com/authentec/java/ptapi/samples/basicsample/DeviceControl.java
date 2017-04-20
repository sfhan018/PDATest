package com.authentec.java.ptapi.samples.basicsample;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


public class DeviceControl {

	private BufferedWriter CtrlFile;
	private File DeviceName;

	DeviceControl(String path) throws IOException{
		DeviceName = new File(path);
		CtrlFile = new BufferedWriter(new FileWriter(DeviceName, false)); // open																		// file
	}
	public void MTGpio(int gpio, int status) {
		String str = String.format("-wdout%d %d", gpio, status);
		try {
			CtrlFile.write(str);
			CtrlFile.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	public void MTGpioDir(int gpio, int status) {
		String str = String.format("-wdir%d %d", gpio, status);
		try {
			CtrlFile.write(str);
			CtrlFile.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}

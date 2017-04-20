package com.authentec.java.ptapi.samples.basicsample;


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class PowerActivity extends Activity {
	
	private Button PowerOnFp = null;
	private Button PowerOffFp = null;
	private DeviceControl DevCtrl;
	private String [] poewro={"63","128"};
	private boolean ispower=true;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		PowerOnFp = (Button) this.findViewById(R.id.PowerOnBn);
		PowerOffFp = (Button) this.findViewById(R.id.PowerOffBn);

		PowerOnFp.setOnClickListener(new ClickEvent());
		PowerOffFp.setOnClickListener(new ClickEvent());

		try {
			DevCtrl = new DeviceControl("/sys/class/misc/mtgpio/pin");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		
	}

	@Override
	protected void onResume() {
		super.onResume();
		getgpio();//获取 gpio
		for (int i = 1; i < list.size(); i++) {
			String lists = list.get(i).toString();
			String gpio = lists.substring(0, lists.indexOf(":"));
			String upordown = lists.substring(7, 8);
			for (int j = 0; j < poewro.length; j++) {
				if (poewro[j].equals(gpio.trim())) {//gpio去空格
					if (upordown.equals("1")) {//上电
						ispower=false;
					} else if (upordown.equals("0")) {//下点
						ispower=true;
					}
				}
			}

		}
	}

	class ClickEvent implements View.OnClickListener{

			public void onClick(View v) {
				// TODO Auto-generated method stub
				if(v == PowerOnFp)
				{
					if (ispower) {
						DevCtrl.MTGpio(63, 1);
						DevCtrl.MTGpio(128, 1);
					}else {
						//如果 gpio 63 128 为高 则直接跳转
						Intent intent=new Intent(PowerActivity.this,SampleActivity.class);
						startActivity(intent);
					}

				}else if(v == PowerOffFp){
					DevCtrl.MTGpio(63, 0);
					DevCtrl.MTGpio(128, 0);
					ispower=true;
				}
				
			}	
   }
	private List list;
	public void getgpio() {
		InputStream inputStream = null;
		try {
			inputStream = new FileInputStream("sys/class/misc/mtgpio/pin");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		list = new ArrayList();
		list = convertStreamToString(inputStream);
	}
	public List convertStreamToString(InputStream inputStream) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
		List lists = new ArrayList();
		String line = null;
		try {
			for (int i = 1; i < 170; i++) {
				if ((line = reader.readLine()) != null) {
					lists.add(line);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				inputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return lists;
	}
}

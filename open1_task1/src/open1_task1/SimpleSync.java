package open1_task1;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;

public class SimpleSync {
	
	// Timer to allow the LED to Blink
	private static Timer  	tblink;
	// Timer to fire the blinking to periodically
	private static Timer 	tfire;
	// Duration of blinking (i.e how long the LED should remain on for)
	private static long		BLINK_DURATION = 500l;
	// Period of the flashing of the LED
	private static long 	PERIOD = 2000l;
	
	static
	{
		
		
		
		/**
		 *  Create a simple timer so that the LED can blink, not just be left
		 *  on.
		 */
		tblink = new Timer();
		tblink.setCallback(new TimerEvent(null) 
		{
			@Override
			public void invoke(byte param, long time) 
			{
				SimpleSync.toggleLED(param, time);
			}
		});
		tblink.setParam((byte) 0);
		
		/**
		 * Create a second simple timer so that the Mote will fire every PERIOD
		 * seconds.
		 */
		tfire = new Timer();
		tfire.setCallback(new TimerEvent(null) 
		{
			@Override
			public void invoke(byte param, long time) {
				SimpleSync.fire(param, time);
			}
		});
		tfire.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, PERIOD));
	}
	
	public static void fire(byte param, long time)
	{
		logMessage(Mote.INFO, csr.s2b("Firing"));
		toggleLED(param, time);
		logMessage(Mote.INFO, csr.s2b("Setting Alarm to Wake Up in 2 Seconds"));
		tfire.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, PERIOD));
	}
	
	public static void toggleLED(byte param, long time)
	{
		logMessage(Mote.INFO, csr.s2b("Toggle LED Called"));
		if (LED.getState(param) == 1)
		{
			logMessage(Mote.INFO, csr.s2b("Turning LED Off"));
            LED.setState(param, (byte)0);
		}
        else
        {
        	logMessage(Mote.INFO, csr.s2b("Turning LED On"));
            LED.setState(param, (byte)1);
            tblink.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, 
            		BLINK_DURATION));
        }
	}
	
	/**
	 * Simple method to avoid having loads of the same line repeated all over
	 * the code.
	 * @param channel	The Channel you want to send the message on 
	 * @param message	The message you want to Log, 
	 * make sure it's a byte array.
	 */
	private static void logMessage(byte channel, byte[] message)
	{
		Logger.appendString(message);
		Logger.flush(channel);
	}
	
}

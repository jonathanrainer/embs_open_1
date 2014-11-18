package open1_task1;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;

public class SimpleSync {
	
	// Timer to allow the LED to Blink
	private static Timer  	tBlink;
	// Timer to fire the blinking to periodically
	private static Timer 	tFire;
	// Duration of blinking (i.e how long the LED should remain on for)
	private static long		BLINK_DURATION = 500l;
	// Period of the flashing of the LED
	private static long 	PERIOD = 2000l;
	// Radio for this Mote
	private static Radio 	radio = new Radio();
	// PAN ID for the PAN this more is on
	private static byte		panID = 0x42;
	// Short Address given to this Mote
	private static byte		shtAddr = 0x69;
	// Beacon Frame to Transmit
	private static byte[] 	frame;
	
	static
	{
		/**
		 * Do some initial configuring of the Radio
		 */
        radio.open(Radio.DID, null, 0, 0);
        radio.setPanId(panID, true);
        radio.setShortAddr(shtAddr);
        radio.setChannel((byte)6);
        
        /**
         * Configure a Radio Callback so that on receiving a frame the Mote 
         * reacts accordingly then set the radio to receive
         */
        radio.setRxHandler(new DevCallback(null){
            public int invoke (int flags, byte[] data, int len, int info, long time) {
                return  SimpleSync.onReceive(flags, data, len, info, time);
            }
        });
        radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
        
        /**
         * Create a frame to transmit 
         */
        frame = new byte[7];
        frame[0] = Radio.FCF_BEACON;
        frame[1] = Radio.FCA_SRC_SADDR;
        Util.set16le(frame, 3, panID);
        Util.set16le(frame, 5, shtAddr);
        
		/**
		 *  Create a simple timer so that the LED can blink, not just be left
		 *  on.
		 */
		tBlink = new Timer();
		tBlink.setCallback(new TimerEvent(null) 
		{
			@Override
			public void invoke(byte param, long time) 
			{
				SimpleSync.toggleLED(param, time);
			}
		});
		tBlink.setParam((byte) 0);
		
		/**
		 * Create a second simple timer so that the Mote will fire every PERIOD
		 * seconds.
		 */
		tFire = new Timer();
		tFire.setCallback(new TimerEvent(null) 
		{
			@Override
			public void invoke(byte param, long time) {
				SimpleSync.fire(param, time);
			}
		});
		tFire.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, PERIOD));
		
		/**
		 * Make sure that once the Mote is deleted it gives up the radio rather
		 * than causing errors later once it tries to be reclaimed by the Mote
		 * and it finds it can't.
		 */
		Assembly.setSystemInfoCallback(new SystemInfo(null) {
        	public int invoke(int type, int info) {
        		return SimpleSync.onDelete(type, info);
        		}
        	});
	}
	
	public static int onReceive(int flags, byte[] data, int len, 
			int info, long time)
	{
		tFire.setAlarmBySpan(0l);
		return 0;
	}
	
	public static void fire(byte param, long time)
	{
		logMessage(Mote.INFO, csr.s2b("Firing"));
		radio.transmit(Device.ASAP|Radio.TXMODE_CCA, frame, 0, 7, 0);
		toggleLED(param, time);
		logMessage(Mote.INFO, csr.s2b("Setting Alarm to Wake Up in 2 Seconds"));
		tFire.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, PERIOD));
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
            tBlink.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, 
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
	
	/**
	 * Small method to make sure the radio is relinquished by the Mote when it
	 * gets deleted.
	 * 
	 * @param type	Information as to the type of event that caused the Mote to
	 * be deleted.
	 * @param info	Extra information as returned by the onDelete Event.
	 * @return No information is returned, 0 indicates success
	 */
	private static int onDelete(int type, int info)
	{
		if(type == Assembly.SYSEV_DELETED)
		{
			radio.close();
		}
		return 0;
	}
	
}

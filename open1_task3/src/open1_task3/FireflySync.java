package open1_task3;

import org.omg.CORBA.TIMEOUT;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;

public class FireflySync {
	
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
	private static byte		PANID = 0x42;
	// Short Address given to this Mote
	private static byte		SHTADDR = 0x69;
	// Beacon Frame to Transmit
	private static byte[] 	frame;
	// time of the next scheduled firing (T_n in Pseudo) in milliseconds
	private static long nextFire; 
	// time of the firing after nextFire (T_n+1 in Pseudo) in milliseconds
	private static long futureFire; 
	// time of the most recent firing of the system
	private static long mostRecentFire;
	// delta value (fraction of how much to change the firing time by)
	// NOTE: Due to MoteRunner not supporting doubles this is scaled
	// by the DELTA_SCALING_FACTOR and then it's all cancelled out later. Just beware of 
	// this when changing the Delta Factor.
	private static long DELTA = 125l;
	// Factor by which delta is scaled, i.e if it's 1000 and DELTA is
	// 2 then the actual delta value is 0.002
	private static long DELTA_SCALING_FACTOR = 10000l;
	private static long PERIOD_SCALING_FACTOR = 10000l;
	
	
	static
	{
		/**
		 * Do some initial configuring of the Radio
		 */
        radio.open(Radio.DID, null, 0, 0);
        radio.setPanId(PANID, true);
        radio.setShortAddr(SHTADDR);
        radio.setChannel((byte)6);
        
        /**
         * Configure a Radio Callback so that on receiving a frame the Mote 
         * reacts accordingly then set the radio to receive
         */
        radio.setRxHandler(new DevCallback(null){
            public int invoke (int flags, byte[] data, int len, int info, long time) {
                return  FireflySync.onReceive(flags, data, len, info, time);
            }
        });
        radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
        
        /**
         * Create a frame to transmit 
         */
        frame = new byte[7];
        frame[0] = Radio.FCF_BEACON;
        frame[1] = Radio.FCA_SRC_SADDR;
        Util.set16le(frame, 3, PANID);
        Util.set16le(frame, 5, SHTADDR);
        
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
				FireflySync.toggleLED(param, time);
			}
		});
		tBlink.setParam((byte) 0);
		
		/**
		 * Set up the time values 
		 */
		nextFire = Time.currentTime(Time.MILLISECS) + PERIOD;
		futureFire = nextFire + PERIOD;
		mostRecentFire = Time.currentTime(Time.MILLISECS);
		
		/**
		 * Create a second simple timer so that the Mote will fire every PERIOD
		 * seconds.
		 */
		tFire = new Timer();
		tFire.setCallback(new TimerEvent(null) 
		{
			@Override
			public void invoke(byte param, long time) {
				FireflySync.fire(param, time);
			}
		});
		tFire.setAlarmTime(Time.toTickSpan(Time.MILLISECS, nextFire));
		
		
		
		
		/**
		 * Make sure that once the Mote is deleted it gives up the radio rather
		 * than causing errors later once it tries to be reclaimed by the Mote
		 * and it finds it can't.
		 */
		Assembly.setSystemInfoCallback(new SystemInfo(null) {
        	public int invoke(int type, int info) {
        		return FireflySync.onDelete(type, info);
        		}
        	});
	}
	
	public static int onReceive(int flags, byte[] data, int len, 
			int info, long time)
	{
		if(data == null)
		{
			return 0;
		}
		// The scaling is applied to get around the fact that MoteRunner
		// doesn't support doubles so it moves everything to being a long,
		// then gets scaled at the end.
		futureFire = 
				(futureFire - ((Time.currentTime(Time.MILLISECS)
						- mostRecentFire) * delta(Time.currentTime(Time.MILLISECS), futureFire))/DELTA_SCALING_FACTOR);
		return 0;
	}
	
	private static long delta(long curTime, long futureFire)
	{
		
		long var_delta = (DELTA * ((futureFire - curTime)*PERIOD_SCALING_FACTOR/PERIOD))/PERIOD_SCALING_FACTOR;
		Logger.appendString(csr.s2b("CurTime: "));
		Logger.appendLong(curTime);
		Logger.appendString(csr.s2b("Future Fire: "));
		Logger.appendLong(futureFire);
		Logger.appendString(csr.s2b("Delta: "));
		Logger.appendLong(var_delta);
		Logger.flush(Mote.INFO);
		return var_delta;
	}
	
	public static void fire(byte param, long time)
	{
		radio.transmit(Device.ASAP, frame, 0, 7, 0);
		toggleLED(param, time);
		nextFire = futureFire;
		futureFire = nextFire + PERIOD;
		mostRecentFire = Time.currentTime(Time.MILLISECS);
		tFire.setAlarmTime(Time.toTickSpan(Time.MILLISECS,  nextFire));
	}
	
	public static void toggleLED(byte param, long time)
	{
		if (LED.getState(param) == 1)
		{
            LED.setState(param, (byte)0);
		}
        else
        {
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

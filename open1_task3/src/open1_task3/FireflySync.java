package open1_task3;

import com.ibm.saguaro.system.*;

public class FireflySync {
	
	// Timer to allow the LED to Blink
	private static Timer  	tBlink;
	// Timer to fire the blinking to periodically
	private static Timer 	tFire;
	// Duration of blinking (i.e how long the LED should remain on for)
	private static long		BLINK_DURATION = 500l;
	// Period of the flashing of the LED
	private static long 	PERIOD = 2000l;
	/**
	 *  Scaling factor for the period when it's used in the denominator of the 
	 *  fraction later on.
	 */
	private static long 	PERIOD_SCALING_FACTOR = 10000l;
	// Radio for this Mote
	private static Radio 	radio = new Radio();
	// PAN ID for the PAN this more is on
	private static byte		PANID = 0x42;
	// Short Address given to this Mote
	private static byte		SHTADDR = 0x69;
	// Beacon Frame to Transmit
	private static byte[] 	frame;
	// time of the next scheduled firing (T_n in Pseudo) in milliseconds
	private static long 	nextFire; 
	// time of the firing after nextFire (T_n+1 in Pseudo) in milliseconds
	private static long 	futureFire; 
	// time of the most recent firing of the system
	private static long 	mostRecentFire;
	/**
	 *  delta value (fraction of how much to change the firing time by)
	 *  NOTE: Due to MoteRunner not supporting doubles this is scaled
	 *  by the DELTA_SCALING_FACTOR and then it's all cancelled out later.Just 
	 *  beware of this when changing the Delta Factor.
	 */
	private static long 	DELTA = 125l;
	/**
	 *  Factor by which delta is scaled, i.e if it's 1000 and DELTA is
	 *  2 then the actual delta value is 0.002	 
	 */
	private static long 	DELTA_SCALING_FACTOR = 10000l;
	
	static
	{
		setUpRadio();
		// Start the radio transmitting such that any pulses can be received.
        radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
        /**
         * Create a generic frame to transmit as the contents of the frame are
         * not really that important.
         */
        frame = createFrame();
        // Set up the time values 
        nextFire = Time.currentTime(Time.MILLISECS) + PERIOD;
		futureFire = nextFire + PERIOD;
		mostRecentFire = Time.currentTime(Time.MILLISECS);
        setUpTimers();
        setUpSystemCallbacks();
	}
	
	private static void setUpRadio()
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
	}
	
	private static byte[] createFrame()
	{
		// Create a frame to transmit 
		byte[] new_frame = new byte[7];
		// Set flags within the beacon frame
        new_frame[0] = Radio.FCF_BEACON;
        new_frame[1] = Radio.FCA_SRC_SADDR;
        // Set the data in the frame required by the flags set above.
        Util.set16le(new_frame, 3, PANID);
        Util.set16le(new_frame, 5, SHTADDR);
        // Return the frame that's been created.
        return new_frame;
	}
	
	private static void setUpTimers()
	{
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
		// Set a parameter for which LED to turn on 
		tBlink.setParam((byte) 0);
	}
	
	private static void setUpSystemCallbacks()
	{
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
		/**
		 * Check if the end of the reception period is reached by checking
		 * for a null transmission, in honesty this could probably be emitted
		 * but it's good for a graceful transition out of receiving should
		 * the motes ever get there during transmission.
		 */
		if(data == null)
		{
			return 0;
		}
		/**
		 * Calculate the new future fire time as the mote has received a 
		 * beacon frame. 
		 * 
		 * The essential calculation is FutureFire = FutureFire - (TimeSinceStepFour)*Delta
		 * 
		 * Where there are scaling factors included to account for Delta actually
		 * being much less than one. Delta is also variable as is explained in
		 * the function delta()
		 */
		futureFire = 
				(futureFire - ((Time.currentTime(Time.MILLISECS)-mostRecentFire) * 
						delta(Time.currentTime(Time.MILLISECS), futureFire))
						/DELTA_SCALING_FACTOR);
		return 0;
	}
	
	/**
	 * Calculate a new value of delta based on the difference between futureFire
	 * and curTime. 
	 * @param curTime		The current time within the model.
	 * @param futureFire	The time of fire after next
	 * @return
	 */
	private static long delta(long curTime, long futureFire)
	{
		/**
		 * Simplification of what's seen in the Ptolemy model, so Delta is scaled
		 * by the fraction (futureFire - currentTime)/Period. This will be low
		 * when the two motes that are exchanging information are more in sync
		 * and higher when they are out so Delta will adjust accordingly. 
		 * Scaling by the period allows delta to be scaled by the number of periods
		 * the motes are out of sync rather than an absolute value of time which
		 * would be harder to account for. 
		 */
		long var_delta = (DELTA * ((futureFire - curTime)*PERIOD_SCALING_FACTOR/PERIOD))/DELTA_SCALING_FACTOR;
		return var_delta;
	}
	
	/**
	 * Method called once T_n seconds have passed.
	 * @param param	Any parameter passed from the Timer
	 * @param time	The time at which this method was called.
	 */
	public static void fire(byte param, long time)
	{
		// Transmit the beacon frame and toggle the LED
		radio.transmit(Device.ASAP, frame, 0, 7, 0);
		toggleLED(param, time);
		// Set the firing times ready for the next iteration
		nextFire = futureFire;
		futureFire = nextFire + PERIOD;
		mostRecentFire = Time.currentTime(Time.MILLISECS);
		/**
		 *  Calculate a random back-off (less than 25 milliseconds) such that
		 *  you don't get loads of radio clashes.
		 */
		long backoff = Util.rand16() % 25;
		// Set wake up for the next firing. 
		tFire.setAlarmTime(Time.toTickSpan(Time.MILLISECS,  nextFire+backoff));
	}
	
	/**
	 * Simple method to toggle the LED 
	 * 
	 * @param param	The LED to turn on or off.
	 * @param time	The time this method was called at.
	 */
	public static void toggleLED(byte param, long time)
	{
		// If the LED is on turn it off
		if (LED.getState(param) == 1)
		{
            LED.setState(param, (byte)0);
		}
		/**
		 *  If it's off then turn it on and set an alarm to turn it off
		 *  again.
		 */
        else
        {
            LED.setState(param, (byte)1);
            tBlink.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, 
            		BLINK_DURATION));
        }
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

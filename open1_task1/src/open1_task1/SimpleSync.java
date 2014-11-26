package open1_task1;

import com.ibm.saguaro.system.*;

public class SimpleSync {
	
	// Timer to allow the LED to Blink
	private static Timer  	tBlink;
	// Timer to fire the blinking and transmitting to occur periodically
	private static Timer 	tFire;
	// Duration of blinking (how long should LED stay on for, stored in ms)
	private static long		BLINK_DURATION = 500l;
	// Period of the flashing and transmitting (stored in ms)
	private static long 	PERIOD = 2000l;
	// Claim the Radio object for this mote
	private static Radio 	radio = new Radio();
	// PAN ID for the PAN this Mote is on
	private static byte		panID = 0x42;
	// Short Address given to this Mote
	private static byte		shtAddr = 0x69;
	// Beacon Frame to Transmit
	private static byte[] 	frame;
	// LED to Light (0x00 - Yellow, 0x01 - Green, 0x02 - Red)
	private static byte 	LED_Colour	= 0x02;
	
	/**
	 * Constructor for the Mote when its instantiated.
	 */
	static
	{
		setUpRadio();
		// Create a generic beacon frame to be transmitted each frame.
		frame = createBeaconFrame();
		setUpTimers();
		setUpSystemCallbacks();
		/**
		 * Put the radio into receive mode so that it can start syncing up
		 * no matter when the other mote is activated. The long time
         * for the radio to listen gets round problems where if its introduced
         * first into the network it can miss the first pulse and thus even get
         * into a tentative sync with the other mote.
		 */
		radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
	}
	
	/**
	 * Perform operations to set up the Radio and 
	 */
	private static void setUpRadio()
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
         * reacts accordingly. 
         */
        radio.setRxHandler(new DevCallback(null){
            public int invoke (int flags, byte[] data, int len, int info, long time) {
                return  SimpleSync.onReceive(flags, data, len, info, time);
            }
        });
	}
	
	/**
	 * Create and initialise the Timers that will govern the operation of the
	 * System.
	 */
	private static void setUpTimers()
	{
		/**
		 *  Create a simple timer so that the LED can blink.
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
		/**
		 * Set a Parameter into the Timer such the LED we want to use can 
		 * be easily selected
		 */
		tBlink.setParam(LED_Colour);
		
		/**
		 * Create a second simple timer so that the Mote will fire every PERIOD
		 * milliseconds then set that timer to fire PERIOD milliseconds later.
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
		tFire.setParam(LED_Colour);
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
        		return SimpleSync.onDelete(type, info);
        		}
        	});
	}
	
	/**
     * Create a beacon frame to transmit 
     */
	private static byte[] createBeaconFrame()
	{
        byte[] new_frame = new byte[7];
        new_frame[0] = Radio.FCF_BEACON;
        new_frame[1] = Radio.FCA_SRC_SADDR;
        Util.set16le(new_frame, 3, panID);
        Util.set16le(new_frame, 5, shtAddr);
        return new_frame;
	}
	
	/**
	 * The method called upon receipt of a frame of data. Simply overwrite
	 * the value in the tFire timer with a 0 such that it fires immediately.
	 * as per the protocol.
	 */
	public static int onReceive(int flags, byte[] data, int len, 
			int info, long time)
	{
		/** 
		 * If a null frame is received then it's the end of the reception 
		 * period so don't do anything. 
		 */
		if(data == null)
		{
			return 0;
		}
		// Otherwise set the Mote to fire immediately as per the protocol.
		tFire.setAlarmBySpan(0l);
		return 0;
	}
	
	/**
	 * Simple fire method, just wake up, stop the Radio, transmit the beacon frame,
	 * blink the LED, and then set the alarm to do it again. 
	 * @param param
	 * @param time
	 */
	public static void fire(byte param, long time)
	{
		/**
		 * Stop the radio from receiving any frames such that you can't get
		 * into a situation where the LEDs are flip-flopping back and forth
		 * with each one firing the one before it. Also helps with lowering
		 * power consumption.
		 */
		radio.stopRx();
		// Transmit the required beacon frame and blink the LED.
		radio.transmit(Device.ASAP|Radio.TXMODE_CCA, frame, 0, 7, 0);
		toggleLED(param, time);
		// Reset the alarm to wake up and transmit again in PERIOD seconds.
		tFire.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, PERIOD));
	}
	
	/**
	 * Simple LED that turns the LED on if it's off and off if it's on. Also
	 * deals with turning on the Radio reception again after it was turned off.
	 * 
	 * @param param 	The LED to turn on or off
	 * @param time		The time at which this event was fired.
	 */
	public static void toggleLED(byte param, long time)
	{
		if (LED.getState(param) == 1)
		{
			/**
			 * When the LED has been turned off start the radio receiving again,
			 * to avoid the flip-flop problem.
			 */
            LED.setState(param, (byte)0);
            radio.startRx(Device.ASAP, Time.currentTicks(), 
            		Time.currentTicks()+(Time.toTickSpan(Time.MILLISECS,PERIOD)));
		}
        else
        {
        	/**
        	 * If the LED is not on turn it on, then set the alarm to turn it 
        	 * off after the BLINK_DURATION has elapsed.
        	 */
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

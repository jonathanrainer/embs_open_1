package open1_task1;

import org.omg.CORBA.PRIVATE_MEMBER;
import org.omg.CORBA.TIMEOUT;

import com.ibm.saguaro.system.*;

public class SimpleSync {
	
    private static Timer  	tsend;
    private static Timer 	ttoggle;
    private static byte[] 	xmit;
    private static byte 	panID = 0x22;
    private static byte 	moteShAddr = 0x31;
    private static int 		t = 2;
    private static long 	blink_duration = 500l;
    private static Radio 	radio = new Radio();

    static {
        // Open the default radio
        radio.open(Radio.DID, null, 0, 0);
        // Set the PAN ID to 0x22 and the short address to 0x31
        radio.setPanId(panID, true);
        radio.setShortAddr(moteShAddr);
        radio.setChannel((byte)1);

        // Prepare beacon frame with source addressing
        xmit = new byte[7];
        xmit[0] = Radio.FCF_BEACON;
        xmit[1] = Radio.FCA_SRC_SADDR;
        Util.set16le(xmit, 3, panID);
        Util.set16le(xmit, 5, moteShAddr);
        
        // Create a handler to generate an event should the Radio receive a Frame
        radio.setRxHandler(new DevCallback(null){
                public int invoke (int flags, byte[] data, int len, int info, 
                		long time) 
                {
                    return  SimpleSync.onReceive(flags, data, len, info, time);
                }
            });
        // Turn the Radio on for an initial 2 seconds so that it's 
        // guaranteed to catch the synchronisation frame.
        radio.startRx(Device.ASAP, 0, 
        		Time.toTickSpan(Time.SECONDS, 2) + Time.currentTicks());

        // Set a timer, that can be overriden, so that the mote will fire in
        // t seconds, in line with the given protocol.
        tsend = new Timer();
        tsend.setCallback(new TimerEvent(null){
                public void invoke(byte param, long time){
                    SimpleSync.onRecieve(param, time);
                }
            });
        // Start the timer so that it will fire in t seconds
        tsend.setAlarmBySpan(Time.toTickSpan(Time.SECONDS, t));
        
        // Create a second timer so that the LED will blink reliably
        ttoggle = new Timer();
        // Set the callback to toggle the LED but don't prime the timer as this
        // only needs to happen to turn the LED off.
        ttoggle.setCallback(new TimerEvent(null){
        	public void invoke(byte param, long time){
        		SimpleSync.toggleLED(param, time);
        	}
        });
        
    }
    
    /**
     * Toggle the LED based on its current state
     * @param param	A Parameter passed in from the Timer
     * @param time	The Time at which the timer was called.
     */
    private static void toggleLED(byte param, long time)
    {
    	if (LED.getState((byte)0) == 1)
            LED.setState((byte)0, (byte)0);
        else
            LED.setState((byte)0, (byte)1);
        	ttoggle.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, 
        			blink_duration));
    }
    
    
    private static int onReceive (int flags, byte[] data, int len, 
    		int info, long time) 
    {
    	radio.stopRx();
    	toggleLED((byte) 0x0, time);
    	// Start the timer so that it will fire in t seconds
        tsend.setAlarmBySpan(Time.toTickSpan(Time.SECONDS, t));
        return 0;
    }
}

package open1_task1;

import com.ibm.saguaro.system.*;

public class SimpleSync {
	
    private static Timer  tsend;
    private static byte[] xmit;
    private static long   xmitDelay;
    // Claim the Radio Object for this mote
    static Radio radio = new Radio();

    static {
        // Open the default radio
        radio.open(Radio.DID, null, 0, 0);
        // Set the PAN ID to 0x22 and the short address to 0x31
        radio.setPanId(0x22, true);
        radio.setShortAddr(0x31);

        // Prepare beacon frame with source addressing
        xmit = new byte[7];
        xmit[0] = Radio.FCF_BEACON;
        xmit[1] = Radio.FCA_SRC_SADDR;
        Util.set16le(xmit, 3, 0x22);
        Util.set16le(xmit, 5, 0x31);

        // Put radio into receive mode for a long time on channel 1
        radio.setChannel((byte)1);
        radio.setRxHandler(new DevCallback(null){
                public int invoke (int flags, byte[] data, int len, int info, long time) {
                    return  RadioTest.onReceive(flags, data, len, info, time);
                }
            });
        radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);

        // Setup a periodic timer callback for transmissions
        tsend = new Timer();
        tsend.setCallback(new TimerEvent(null){
                public void invoke(byte param, long time){
                    RadioTest.periodicSend(param, time);
                }
            });
        // Convert the periodic delay from ms to platform ticks
        xmitDelay = Time.toTickSpan(Time.MILLISECS, 2500);
        // Start the timer
        tsend.setAlarmBySpan(xmitDelay);
    }

    // On a received frame toggle LED 
    private static int onReceive (int flags, byte[] data, int len, int info, long time) {
        if (data == null) { // marks end of reception period
            // re-enable reception for a very long time
            radio.startRx(Device.ASAP, 0, Time.currentTicks()+0x7FFFFFFF);
            return 0;
        }
        if (LED.getState((byte)0) == 1)
            LED.setState((byte)0, (byte)0);
        else
            LED.setState((byte)0, (byte)1);
        return 0;
    }

    // Called on a timer alarm
    public static void periodicSend(byte param, long time) {
        // send the message
        radio.transmit(Device.ASAP|Radio.TXMODE_CCA, xmit, 0, 7, 0);
        // Setup a new alarm
        tsend.setAlarmBySpan(xmitDelay);
    }

}

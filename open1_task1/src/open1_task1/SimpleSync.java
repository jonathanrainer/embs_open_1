package open1_task1;

import com.ibm.saguaro.system.*;

public class SimpleSync {
	
	// Timer to allow the LED to Blink
	private static Timer  	tblink;
	// Duration of blinking (i.e how long the LED should remain on for)
	private static long		BLINK_DURATION = 500l;
	
	static
	{
		tblink = new Timer();
		tblink.setCallback(new TimerEvent(null) 
		{
			@Override
			public void invoke(byte param, long time) 
			{
				toggleLED((byte) 0x0, time);
			}
		});
	}
	
	private static void toggleLED(byte param, long time)
	{
		if (LED.getState(param) == 1)
		{
            LED.setState(param, (byte)0);
		}
        else
        {
            LED.setState(param, (byte)1);
            tblink.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, 
            		BLINK_DURATION));
        }
	}
	


}

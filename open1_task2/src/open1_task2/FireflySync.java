package open1_task2;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.util.Time;
import ptolemy.data.IntToken;
import ptolemy.domains.wireless.kernel.WirelessIOPort;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.vergil.icon.EditorIcon;
import ptolemy.vergil.kernel.attributes.EllipseAttribute;

@SuppressWarnings("serial")
public class FireflySync extends TypedAtomicActor
{

	protected WirelessIOPort input; 
	protected WirelessIOPort output;
	// time of the next scheduled firing (T_n in Pseudo)
	protected Time nextFire; 
	// time of the firing after nextFire (T_n+1 in Pseudo)
	protected Time futureFire; 
	// time of the most recent firing of the system
	protected Time mostRecentFire;

	protected String iconColor = "{0.0, 0.0, 0.0, 1.0}"; // black, LED off by default
	protected boolean stateLED = false; // state of the LED, off by default
	protected double flashDuration = 0.5; // for how long LEDs are on, for visual effects only, not used by the synchronisation mechanism
	protected double syncPeriod = 2.0; // synchronisation period
	protected double base_delta = 0.007;
	protected double delta = base_delta;
	
	// icon related
	protected EllipseAttribute _circle; 
	protected EditorIcon node_icon;

	
	public FireflySync(CompositeEntity container, String name)
	throws NameDuplicationException, IllegalActionException  
	{
		super(container, name);
		// Creates input and output channels
		input = new WirelessIOPort(this, "input", true, false);
		output = new WirelessIOPort(this, "output", false, true);
		input.outsideChannel.setExpression("Channel");
    	output.outsideChannel.setExpression("Channel");
		buildIcon();
	}
	
	
	 public void initialize() throws IllegalActionException {
		 
		 super.initialize();
		 // Set next firing equal to the firing period
		 nextFire = getDirector().getModelTime().add(Math.random());
		 // Set the firing after next equal to T_n + T
		 futureFire = nextFire.add(syncPeriod);
		 // Schedule a firing for T_n seconds later
		 mostRecentFire = getDirector().getModelTime();
		 getDirector().fireAt(this, nextFire);
	 }
	

	public void fire() throws IllegalActionException{	
		
		Time curTime = getDirector().getModelTime();

		if(input.hasToken(0))
		{ 
			input.get(0);
			Time timeSinceFour = curTime.subtract(mostRecentFire);
			futureFire = futureFire.subtract(timeSinceFour.getDoubleValue() * delta(curTime, futureFire));
		}

		else if(curTime.compareTo(nextFire)!=-1){ // time to fire: transmit and blink LED
			// transmit a message
			output.broadcast(new IntToken(0));
			
			// turn on the LED
			this.setLED(true);
			
			// schedule LED off
			getDirector().fireAt(this, curTime.add(flashDuration)); 
			
			nextFire = futureFire;
			futureFire = nextFire.add(syncPeriod);
			mostRecentFire = curTime;
			
			// schedule a firing in T time units
			if(nextFire.compareTo(getDirector().getModelTime()) < 0)
			{
				nextFire = getDirector().getModelTime();
			}
			getDirector().fireAt(this, nextFire); 
		}
		else
		{
			this.setLED(false);
		}
	}

	// change the filling colour of the icon
	protected void setLED(boolean on) throws IllegalActionException {
		stateLED=on;
		if(on){
			// Disco Stu likes these LEDs.
			// I'm sorry but this made it much more pretty
			_circle.fillColor.setToken("{" + Math.random() + 
					", " + Math.random() + ", " + Math.random() + 
					", " + Math.random() + "}");
		}
		else{
			_circle.fillColor.setToken("{0.0, 0.0, 0.0, 1.0}"); // black
		}
	}

	
	// set the actor icon as a 20x20 pixel black circle
	protected void buildIcon() throws IllegalActionException, NameDuplicationException {
		node_icon = new EditorIcon(this, "_icon");
		_circle = new EllipseAttribute(node_icon, "_circle");
		_circle.centered.setToken("true");
		_circle.width.setToken("20");
		_circle.height.setToken("20");
		_circle.fillColor.setToken(this.iconColor);
		_circle.lineColor.setToken("{0.0, 0.0, 0.0, 1.0}");
		node_icon.setPersistent(false);
	}
	
	protected double delta(Time currentTime, Time futureFire)
	{
		double diff = (futureFire.subtract(currentTime)).getDoubleValue()/syncPeriod;
		double new_delta = base_delta*(Math.exp(-Math.pow(diff-(syncPeriod/2),2)*10));
		//return new_delta;
		System.out.println("New Delta, " + new_delta);
		return new_delta;
	}
}

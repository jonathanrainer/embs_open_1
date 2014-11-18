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
public class SimpleSync extends TypedAtomicActor{

	protected WirelessIOPort input; 
	protected WirelessIOPort output; 
	protected Time nextFire; // time of the next scheduled firing

	protected String iconColor = "{0.0, 0.0, 0.0, 1.0}"; // black, LED off by default
	protected boolean stateLED = false; // state of the LED, off by default
	protected double flashDuration = 0.5; // for how long LEDs are on, for visual effects only, not used by the synchronisation mechanism
	protected double syncPeriod = 2.0; // synchronisation period

	
	// icon related
	protected EllipseAttribute _circle; 
	protected EditorIcon node_icon;

	
	public SimpleSync(CompositeEntity container, String name)
	throws NameDuplicationException, IllegalActionException  {

		super(container, name);
		input = new WirelessIOPort(this, "input", true, false);
		output = new WirelessIOPort(this, "output", false, true);
		input.outsideChannel.setExpression("Channel");
    	output.outsideChannel.setExpression("Channel");

		buildIcon();
	
	}
	
	
	 public void initialize() throws IllegalActionException {
		 
		 super.initialize();
		 
		 // schedule the first firing randomly within the first second of the simulation
		 nextFire = getDirector().getModelTime().add(Math.random());
		 getDirector().fireAt(this, nextFire);
	 }
	

	public void fire() throws IllegalActionException{	
		// turn off the LED
		this.setLED(false);
		
		Time curTime = getDirector().getModelTime();

		if(input.hasToken(0)){  // if another node has transmitted
			//discard token
			input.get(0);

			// turn on the LED
			this.setLED(true);
			
			// schedule LED off
			getDirector().fireAt(this, curTime.add(flashDuration)); 
			
			// schedule a firing in T time units
			nextFire = curTime.add(syncPeriod);
			getDirector().fireAt(this, nextFire); 
		}

		else if(curTime.compareTo(nextFire)!=-1){ // time to fire: transmit and blink LED
			// transmit a message
			output.broadcast(new IntToken(0));
			
			// turn on the LED
			this.setLED(true);
			
			// schedule LED off
			getDirector().fireAt(this, curTime.add(flashDuration)); 
			
			// schedule a firing in T time units
			nextFire = curTime.add(syncPeriod);
			getDirector().fireAt(this, nextFire); 
		}
		
			
		
		
	}

	// change the filling colour of the icon
	protected void setLED(boolean on) throws IllegalActionException {
		stateLED=on;
		if(on){
			_circle.fillColor.setToken("{1.0, 0.0, 0.0, 1.0}"); // red
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

	
}

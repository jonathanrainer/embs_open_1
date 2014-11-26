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

	// Port Setup
	protected WirelessIOPort input; 
	protected WirelessIOPort output;
	
	// time of the next scheduled firing (T_n in Pseudocode, stored in ms)
	protected Time nextFire; 
	// time of the firing after nextFire (T_n+1 in Pseudocode, stored in ms)
	protected Time futureFire; 
	// time of the most recent firing of the system
	protected Time mostRecentFire;
	// black, LED off by default
	protected String iconColor = "{0.0, 0.0, 0.0, 1.0}";
	// state of the LED, off by default
	protected boolean stateLED = false;
	// for how long LEDs are on, for visual effects only, not used by the synchronisation mechanism
	protected double flashDuration = 0.5; 
	// synchronisation period
	protected double syncPeriod = 2.0; 
	/**
	 * A starting value for Delta such that the Gaussian Function has an upper
	 * bound on the values it can give. In addition there is a starting point
	 * for delta in the first iteration before there's any observed data.
	 */
	protected double baseDelta = 0.012; // a baseline delta function to give 
	/**
	 * A value to store the value of Delta being used for the current iteration,
	 * start it off 
	 */
	protected double delta = baseDelta;
	
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
		 /**
		  * Initialise the firing to a random value so that there's something
		  *  to test in the model.
		  */
		 nextFire = getDirector().getModelTime().add(Math.random());
		 // Set the firing after next equal to T_n + T
		 futureFire = nextFire.add(syncPeriod);
		 // Schedule a firing for T_n seconds later
		 mostRecentFire = getDirector().getModelTime();
		 // Instruct the director to fire at nextFire seconds.
		 getDirector().fireAt(this, nextFire);
	 }
	

	public void fire() throws IllegalActionException{	
		
		// Grab the current time as this is used throughout this method
		Time curTime = getDirector().getModelTime();

		/**
		 * If the Mote has a token on it's input it was woken up by another
		 * Mote and consequently needs to alter it's futureFire value as
		 * per the Pseduocode. 
		 */
		if(input.hasToken(0))
		{ 
			// Discard the input, it's just a marker.
			input.get(0);
			// Calculate the time since you started listening
			Time timeSinceFour = curTime.subtract(mostRecentFire);
			/**
			 * Work out an adjustment from the Current Time and FutureFire with
			 * the Delta adjustment factor being calculated as a function of
			 * Current Time and FutureFire.
			 */
			futureFire = futureFire.subtract(timeSinceFour.getDoubleValue() * delta(curTime, futureFire));
		}

		/**
		 * If there's no input then check if it's time to fire again, if it is
		 * then fire.
		 */
		else if(curTime.compareTo(nextFire)!=-1){ 
			// transmit a message
			output.broadcast(new IntToken(0));
			
			// turn on the LED
			this.setLED(true);
			
			// schedule LED off
			getDirector().fireAt(this, curTime.add(flashDuration)); 
			
			// Update time values 
			nextFire = futureFire;
			futureFire = nextFire.add(syncPeriod);
			mostRecentFire = curTime;
			
			/**
			 * Check to make sure no exceptions are thrown in Ptolemy with methods
			 * trying to set times in the past. If that's the case then act like
			 * a saturation counter and set the nextFiring to happen immediately.
			 */
			if(nextFire.compareTo(getDirector().getModelTime()) < 0)
			{
				nextFire = getDirector().getModelTime();
			}
			// schedule a firing in T time units
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
			// “Expose yourself to as much randomness as possible.” Ben Casnocha
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
	
	/**
	 * Calculate a new value for Delta based on the difference between the 
	 * currentTime and the time to fire in the future.
	 * @param currentTime	The current time within the Model.
	 * @param futureFire	The time at which the fire after next will occur.
	 * @return	A value for delta that is variable to account for changes in 
	 * the needs of a delta value.
	 */
	protected double delta(Time currentTime, Time futureFire)
	{
		/**
		 *  Work out the difference as a fraction of the Period so you know
		 *  how many periods you are out of sync with the mote that fired.
		 */
		double diff = (futureFire.subtract(currentTime)).getDoubleValue() % (syncPeriod/2);
		/**
		 * Put this value into a Gaussian Function of the form:
		 * 
		 * f(x) = a*exp(-(x-b)^2/2c^2) + d 
		 * 
		 * Where a,b,c,d are as follows
		 * 
		 * a - The height of the Gaussian, determining the maximum output of this
		 * function, set to baseDelta
		 * b - The centre of the Gaussian, chosen to be half the period so the
		 * maximum output is when the Mote is maximally out of sync (i.e half
		 * the period out)
		 * c - Standard deviation set at 1/sqrt(2)b (mostly for reasons of 
		 * simplification and also because it keeps the curve mostly within
		 * the positive quadrant)
		 * d - Value approached by the function at the asymptote. Set to 0.
		 * 
		 */
		double new_delta = baseDelta*(Math.exp(-Math.pow(diff-(syncPeriod/2),2)));
		return new_delta;
	}
}



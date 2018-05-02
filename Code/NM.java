package implementation;
import com.ibm.saguaro.system.*;

public class NM {
	
	//NETWORK CONSTANTS
	private static byte XMIT_CHANNEL = 3; //The channel to start on
	private static int TIME_SLOT_LENGTH = 1000; //THE ACTUAL TS SIZE
	private static int SUPERFRAME_LENGTH = 10; // THE NUMBER OF TIMESLOTS IN THE SF
	private static byte[] CHANNELS = {3,2,3}; //The list of channels we switch between - can be any from 1 to 16
	private static int channel_counter = 0;;
	private static int bcast_counter = 1;
	
	//Radio Setup
	private static byte[] BCAST = new byte[16]; //The broadcast packet
	private static Radio radio = new Radio();
	private static byte panid = 0x01; //NM's address
	
	//Lights Setup
	private static boolean lights = false;
	
	
	//Timer Setup
	private static Timer btimer;
	
	static {
	
		//Open the radio and set it up on the fixed channel
		radio.open(Radio.DID,null,0,0);
		radio.setChannel(CHANNELS[channel_counter]); //set the initial channel
		radio.setPanId(panid, true);
		
		//Prepare the addresses of the broadcast
		BCAST[0] = Radio.FCF_BEACON;
		BCAST[1] = Radio.FCA_SRC_SADDR|Radio.FCA_DST_SADDR;
		Util.set16le(BCAST, 3, 0xFFFF); //PAN is set to broadcast for all of these messages, so all the mote will bother listening to them
		Util.set16le(BCAST, 5, 0xFFFF); //Broadcast
		Util.set16le(BCAST, 7, 0x22);
		Util.set16le(BCAST, 9, 0x01);
		
		
		
		//Setup the timer for the interrupts for broadcasts
		btimer = new Timer();
		btimer.setCallback(new TimerEvent(null){
			public void invoke(byte param, long time){
				NM.broadcastPulse(param,time);
			}
		});
		btimer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, SUPERFRAME_LENGTH * TIME_SLOT_LENGTH));
		
	
	}
	
	public static void broadcastPulse(byte param, long timer){
		
		radio.setChannel((byte)CHANNELS[channel_counter]);//Set the channel to this broadcast's next one
		channel_counter++; //Increment 
			
		
		//Turn on or off the leds dependent on their current state
		if(lights) {
			LED.setState((byte)2,(byte)0);
			LED.setState((byte)1,(byte)0);
			LED.setState((byte)0,(byte)0);
			lights = false;
		}else {
			LED.setState((byte)2,(byte)1);
			LED.setState((byte)1,(byte)1);
			LED.setState((byte)0,(byte)1);
			lights = true;
		}
		
		if(bcast_counter == CHANNELS.length){
			bcast_counter = 0; // wrap back around to the start of the channels list
		}
		
		if(channel_counter == CHANNELS.length){
			channel_counter = 0; // wrap back around to the start of the channels list
		}
		
		//It's time for a broadcast i.e the start of the next superframe;
		
		BCAST[11] = (byte)CHANNELS[bcast_counter]; //Something to identify the bcast
		bcast_counter++;
		//Prepare the bcast frame for transmit
		radio.transmit(Device.ASAP|Radio.TXMODE_CCA, BCAST, 0, 16, 0); //Transmit the broadcast
		
		btimer.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, SUPERFRAME_LENGTH * TIME_SLOT_LENGTH));
		
		
	
	}
}

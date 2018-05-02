package implementation;

import com.ibm.saguaro.system.*; 
import com.ibm.saguaro.logger.*;


public class NetworkDevice {
	
	//the network
	private static byte BCAST_CHANNEL = 3; //The broadcast channel we're using for the network
	private static int TIME_SLOT_LENGTH = 1000; //Length of the timeslot
	private static int SF_LENGTH = 10;
	private static int MAX_PAYLOAD = 50;
	private static byte NEW_CHANNEL = BCAST_CHANNEL; //The channel that this node should begin listening to for the first sync
	private static byte FUTURE_CHANNEL = 1;
	private static byte NEW_CHANNEL_TO_SET = 3; //The channel shall be the next one we listen on (as defined by the superframe bc)
	private static boolean STARTUP = true;
	//This device's network details
	private static byte panid = 0x11;
	private static byte address = 0x11;

	
	//This device's transmission schedule
	private static int[] SLOT_TABLE = {1,2,3,4,5,6,7,8,9};
	private static int[] SLOT_FUNCTION = {2,1,2,1,2,1,2,1,2}; //1 = Transmit, 2= Recieve
	private static byte[] DESTINATIONS = {0x12,0x12,0x12,0x12};
	
	//private static int[] F1_SLOT_CHANNEL = {};
	private static int OFFSET_INDEX = -1; //Set it to -1 so we can keep track
	private static byte[] msg = new byte[(12+MAX_PAYLOAD)]; //Dynamic payload size for when we change the payload for optimisations
	private static int DEST_INDEX = 0;
	
	//Radio Init
	static Radio radio = new Radio();
	
	//Timer Init
	private static Timer tslot; 
	
	static {
			
			//Open and setup the radio
			radio.open(Radio.DID, null, 0, 0);
			radio.setPanId(panid, false);
			radio.setShortAddr(address);
			radio.setChannel(BCAST_CHANNEL);
			
			//Setup the basic frame parameters
			 //Destination ADDRess?
			 
			msg[0] = Radio.FCF_DATA;
			msg[1] = Radio.FCA_SRC_SADDR|Radio.FCA_DST_SADDR;
			Util.set16le(msg, 7, panid); //This device's
			Util.set16le(msg, 9, address); //This device's 
			
			//Fill the packet payload with some dummy data 
			for(byte i = 11; i < MAX_PAYLOAD + 11; i++){
				
				msg[i] = (byte)(i + 15);
				
			}
			
			//Setup the recieved frame callback
			radio.setRxHandler(new DevCallback(null){
				public int invoke (int flags, byte[] data, int len, int info, long time){
					return NetworkDevice.handleFrame(flags, data, len, info, time);
				}
			});
			
			////The timer for offsets
			tslot = new Timer();
			tslot.setCallback(new TimerEvent(null){
				public void invoke(byte param, long time){
					NetworkDevice.timeSlot(param, time);
				}
			});
			
			//Start listening in the network
			// Put radio into receive mode
        		radio.startRx(Device.ASAP, 0, Time.currentTicks()+Time.toTickSpan(Time.MILLISECS, TIME_SLOT_LENGTH * SF_LENGTH)); //listen for a V long time, since if we miss the first broadcast we might got out of sync
	
			
			
			
	}
	
	private static int handleFrame(int flags, byte[] data, int len, int info, long time){
			
			
			if(data == null){
				//The receiev period ended but we didn't get anything.
				Logger.appendString(csr.s2b("None Recieved"));
 				Logger.flush(Mote.WARN);
				return 0;
			}else if(data[11] <= 16) { //IF this is a broadcast i.e. is it defining a superframe?? in which case it's sent a channel
				
				FUTURE_CHANNEL = data[11]; //The index where the next channel is
				ledsOff();
				LED.setState((byte)1,(byte)1); //Turn on the green LED
				OFFSET_INDEX = 0;
				Logger.appendString(csr.s2b("Sync Recieved"));
 				Logger.flush(Mote.WARN);
				tslot.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, SLOT_TABLE[OFFSET_INDEX] * TIME_SLOT_LENGTH)); //Listen until the first offset
			}else{
				//This is just a generic message
				//Say that we've recieved it
				Logger.appendString(csr.s2b("Packet Recieved "));
 				Logger.flush(Mote.WARN);
 				LED.setState((byte)1,(byte)1); //Packet recieved, set GREEN led

								
			}
			
		
		return 0;

	}
	
	
	
	public static void timeSlot(byte param, long time){
	
		ledsOff();
		
		if(OFFSET_INDEX == SLOT_TABLE.length){
			//End of superframe, go back to listening again
			// Put radio into receive mode for length of a superframe, just incase we're a bit out
			OFFSET_INDEX = -1; //For purposes of relistening if needs be.
			
			
			
        		radio.startRx(Device.ASAP, 0, Time.currentTicks()+Time.toTickSpan(Time.MILLISECS, (SF_LENGTH * TIME_SLOT_LENGTH)));
 			
 			
		}
		else {
			
			
			//Do the operation for this offset
			if(SLOT_FUNCTION[OFFSET_INDEX] == 1){
				//Transmit a frame
				LED.setState((byte)2,(byte)1);
				Util.set16le(msg, 5, DESTINATIONS[DEST_INDEX]); //The destination address we're sending this to
				Util.set16le(msg, 3, DESTINATIONS[DEST_INDEX]); //The destination address we're sending this to
				radio.transmit(Device.ASAP|Radio.TXMODE_CCA, msg, 0, 61, 0); //Transmit the message to the destination
				
				
			}
			else if(SLOT_FUNCTION[OFFSET_INDEX] ==2){
				//Recieve a frame, so just listen
				LED.setState((byte)0,(byte)1);
				radio.startRx(Device.ASAP, 0, Time.currentTicks()+Time.toTickSpan(Time.MILLISECS, (SF_LENGTH * TIME_SLOT_LENGTH)));
			}
			OFFSET_INDEX++;
			if(OFFSET_INDEX == SLOT_TABLE.length){
				//No more operations to do, so just listen for the next superframe
				//Open and setup the radio
				radio.stopRx(); //stop the radio - this is more  a bug fix 
				radio.setChannel(FUTURE_CHANNEL); //change the channel to the next one
				int endOfFrame = (SF_LENGTH * TIME_SLOT_LENGTH) - (SLOT_TABLE[OFFSET_INDEX -1] * TIME_SLOT_LENGTH); //How long to wait
				tslot.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, endOfFrame));
			}
			
			tslot.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, (SLOT_TABLE[OFFSET_INDEX] - SLOT_TABLE[OFFSET_INDEX-1]))*TIME_SLOT_LENGTH);//Setup the timer again for next offset
		}
	}
	
	public static void ledsOff(){
	
		LED.setState((byte)0, (byte)0); //LED OFF
		LED.setState((byte)1, (byte)0); //LED OFF
		LED.setState((byte)2, (byte)0); //LED OFF 		
	
	}
		
}

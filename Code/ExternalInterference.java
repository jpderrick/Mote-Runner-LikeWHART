package implementation;

import com.ibm.saguaro.system.*; 


public class InterferingDevice {
	
	
	
	private static int TRANSMIT_RATE = 10; //Rate of transmissions
	private static byte[] msg = new byte[20];
	private static byte[] channels_ordering = {12,1,2,5,7,2,5,3,6,16,3,13,13,1,3,6,7,9,14,12,1};//Randomly generated list of channels
	private static int index = 0;
	//Radio Init
	static Radio radio = new Radio();
	
	//Timer Init
	private static Timer tslot; 
	
	static {
			
			//Open and setup the radio
			radio.open(Radio.DID, null, 0, 0);
			radio.setPanId(0x11, false);
			radio.setShortAddr(0x09);
			 
			msg[0] = Radio.FCF_DATA;
			msg[1] = Radio.FCA_SRC_SADDR|Radio.FCA_DST_SADDR;
			Util.set16le(msg, 7, 0x09); //This device's
			Util.set16le(msg, 9, 0x09); //This device's 
			
			////The timer for offsets
			tslot = new Timer();
			tslot.setCallback(new TimerEvent(null){
				public void invoke(byte param, long time){
					InterferingDevice.timeSlot(param, time);
				}
			});
			
			tslot.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, TRANSMIT_RATE));
			
			
	}
	
	public static void timeSlot(byte param, long time){
			
			radio.setChannel(channels_ordering[index]); //choose a random channel between 1 and 16
			radio.transmit(Device.ASAP|Radio.TXMODE_CCA, msg, 0, 16, 0); //Transmit the message
			index++;
			
			if(index == channels_ordering.length) {
					index = 0;
			}
			tslot.setAlarmBySpan(Time.toTickSpan(Time.MILLISECS, TRANSMIT_RATE));//Setup the timer again for next offset
			
	}
		
}

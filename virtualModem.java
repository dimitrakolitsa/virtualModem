package ithakimodem;

import ithakimodem.Modem;
import java.util.stream.Stream;
import java.util.*;
import java.lang.Math;
import java.util.Timer;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.System;


public class virtualModem {
 
	public static void main(String[] param) throws IOException { //run demo function of virtualModem (demonstration of how it works)
 
		(new virtualModem()).demo();
	}
 
	public void demo() throws IOException {
		int k;
		boolean b;
		boolean noCarrierDetected = false;
		Modem modem;
		modem = new Modem();
		modem.setSpeed(80000);
		modem.setTimeout(1000);
		modem.open("ithaki");

		//temporary ithaki codes
		byte[] echo= "E0056\r".getBytes();
		byte[] imageNoError= "M7404\r".getBytes();
		byte[] imageWithError= "G7524\r".getBytes();
		byte[] gps1= "P0210R=1000199\r".getBytes(); //route 1, start from spot 1, total 99 spots
		byte[] ack= "Q5440\r".getBytes();
		byte[] nack= "R6315\r".getBytes();
 

		String message="";
	 
	 	for (;;) {  //break endless loop by catching sequence "\r\n\n\n"
	 		try {
	 			k=modem.read();
	 			if (k==-1) 
					break;
	 			System.out.print((char)k);
	 			message+=(char)k;
	 			if (message.indexOf("\r\n\n\n")>-1) 
		 			break;
	 		} catch (Exception x) {
	 			break;
	 		}	
	 	}


		long startTime = System.currentTimeMillis();
		long maxWaitingTime = 300000; //300000; //5 mins      ////////////////////////////////////////////////////////////////////////////////////////////
		long endTime = startTime + maxWaitingTime;

		//stop program execution when "NO CARRIER" is detected
		while (!noCarrierDetected && System.currentTimeMillis() < startTime + maxWaitingTime) {  
        	try {
            	k= modem.read();
             	if (k==-1){
                	break;
             	}
             	System.out.print((char)k);
             	if (k=='N') {
                 	//make noCarrierDetected true if the following characters are "O CARRIER"
                 	noCarrierDetected = (modem.read() == 'O' && modem.read() == ' ' && modem.read() == 'C'
                         && modem.read() == 'A' && modem.read() == 'R' && modem.read() == 'R'
                         && modem.read() == 'I' && modem.read() == 'E' && modem.read() == 'R');
             	}
         	} catch (Exception x) {
             break;
        	}
    	}

		//read until timeout -> enhances program behavior
		for (;;){
			try{
				k=modem.read();
				if (k==-1){
					break;
				}
				System.out.print((char)k);
			} catch (Exception x) {
				break;
			}
		}

     //--------------------------------------------------------------------------------------------------------------------//

		//1st dial
		for (int i=0; i<205; i++){
			try {
				k=modem.read();
				if (k==-1)
					break;
				System.out.print((char) k);
				// int start = message.indexOf("");
				// int end = message.indexOf("");
				// if ((start>=1)&&(end>=1)){
				// 	break;
				// }
			    } catch (Exception x) {
				break;
			}
		}

		PrintWriter responseTimeEchoPackets= new PrintWriter("responseTimeEchoPackets.txt", "UTF-8"); //prints response time of all echo packets
		FileOutputStream imageErrFree= new FileOutputStream("E1.jpg");
		FileOutputStream imageErr= new FileOutputStream("E2.jpg");
		FileOutputStream GPSimage= new FileOutputStream("M1.jpg");
		PrintWriter ARQresponseTime= new PrintWriter("ARQresponseTime.txt", "UTF-8");
		PrintWriter numberOfretransmissions= new PrintWriter("numberOfretransmissions.txt", "UTF-8");
 
     //--------------------------------------------------------------------------------------------------------------------//

		//echo packets
		while (System.currentTimeMillis()<endTime){
			responseTimeEchoPackets.println(echoes(echo, modem)); //adds response time of all echo packets to responseTimeEchoPackets
		}
		responseTimeEchoPackets.close();

	 //--------------------------------------------------------------------------------------------------------------------//
 
		//ARQ
		int Packs= 0; //counts number of packets received
		int retransmissions= 0; //counts number of retransmissions->errors
		int[] rw= new int[2]; //1st element holds num of correct packets, 2nd element holds num of incorrect packets
		
		long startTimeARQ= System.currentTimeMillis();
		long endTimeARQ= startTimeARQ + 300000; // 300000 -> 5 mins //run for at least 4 mins

		while (System.currentTimeMillis()<endTimeARQ){ //run for 4 mins at least-> 5mins
			rw= acknackARQ(modem, ack, nack, ARQresponseTime, numberOfretransmissions);
			Packs= Packs+ rw[0];
			retransmissions= retransmissions+ rw[1];
			try{
  				Thread.sleep(100);
			}catch(InterruptedException ex){
  				break;
			}
		}
		
		System.out.println();
		System.out.println("Number of total correct packets:"+Packs);
		System.out.println("Number of total errors:"+retransmissions);
 
		ARQresponseTime.close();
		numberOfretransmissions.close();
 

 
     //--------------------------------------------------------------------------------------------------------------------//

		//image with no error
		images(imageNoError, modem, imageErrFree);
		imageErrFree.close();
		
		//image with error in second half
		images(imageWithError, modem, imageErr);
		imageErr.close();

     //--------------------------------------------------------------------------------------------------------------------//		
 
		//gps with spots
		int[][] gpsMap= new int[99][80];
		gpsMap= gps(modem, gps1);
 
		long[] table= new long[6];
		table= gpsCoord(gpsMap); //store wanted spots from gpsMap to table
		String gpsCodeReq= "P0210";

		for (int i=0; i<6; i++){
			gpsCodeReq= gpsCodeReq+ "T="+ table[i];
		}
		
		gpsCodeReq= gpsCodeReq.concat("\r");
		System.out.println(gpsCodeReq);
		byte[] gpsSpots= gpsCodeReq.getBytes();
 
		images(gpsSpots,modem,GPSimage);
		GPSimage.close();
 
     

		modem.close();
 
	}

 //--------------------------------------------------------------------------------------------------------------------//	
 
	public int echoes(byte[] echoRequest, Modem modemTemp) {  //returns response time of each echo packet
		modemTemp.write(echoRequest);
		int k;
		long startingTime = System.currentTimeMillis(); //starting time of a packet in ms 
		long endingTime = 0;
		long responseTime;
		
		for (int count=0; count<35; count++) {  //run read function 35 times (length of each packet)
			try {
				k = modemTemp.read();
				if (k==-1) {
					break;
				}
				System.out.print((char) k);
			} catch (Exception x) {
				break;
			}
		}

		endingTime = System.currentTimeMillis(); //ending time of an echo packet in ms (operation is over)
		responseTime = endingTime - startingTime; //response time = endTime - startTime of operation for each packet
		return (int) responseTime;
 
	}
 
 //--------------------------------------------------------------------------------------------------------------------// 
 
	public void images(byte[] imageNoErr, Modem modemTmp, FileOutputStream noErrImg) throws IOException {
		modemTmp.write(imageNoErr);
		int lastByte=0;
		int previous=0; //variables to check for delimiters
		int m=0; //variable to check if I got an image so that the loop breaks

		for(;;){
				previous= lastByte; 
				lastByte= modemTmp.read();

				if (lastByte==-1){
					break;
				}

			if (previous==(int)0xFF && lastByte==(int)0xD8){ //delimiter for the start of an image: (FFD8)
					m=1; //image starts -> I received an image
					noErrImg.write(0xFF);
					noErrImg.write(0xD8);
				

				for (;;) {
					try {
						previous = lastByte;
						lastByte = modemTmp.read();

						if (lastByte==-1){
							break;
						}
						
						if (previous==(int)0xFF && lastByte==(int)0xD9){ //delimiter for the end of an image (FFD9)
							noErrImg.write(0xD9);
							break;
						}
						noErrImg.write((byte) lastByte); //write the image byte by byte 

					} catch (Exception x) {
						break;
					}
					
				}
			
				if (m==1){ //exit loop
				break;
				}
		  	}
		}

		noErrImg.close();
	}

 
 //--------------------------------------------------------------------------------------------------------------------// 
 

	public int[][] gps(Modem modemTemp, byte[] gpsTemp){
		modemTemp.write(gpsTemp);
		int k=0;
		int mapSpots[][] = new int[99][80];
		int spots=0; //number of spots
		int i=-1; //if $ is scanned (start of a spot name - coordinates), it increments
		int j=0;

		for (int p=0; p<27; p++){ //PSTART ITHAKI GPS TRACKING
			try {
				k=modemTemp.read();

				if (k==-1) {
					break;
				}

				System.out.print((char) k);
			}catch (Exception x) {
				break;
			}
		}

		System.out.println();
 
		for (;;) { //store all spots in matrix mapSpots
			try {
				k=modemTemp.read();

				if (k==-1){
					break;
				}

				if (spots==99){ //stop when last spot (and also packet) is received without the 'STOP' message 
					break;
				} 
				
				if (k==42){ // * character is 42 in ADCII code->there is one * character in each spot's coordinates->each * means one spot and therefore one packet received
					spots++; 
				}

				if (k==36) { //$ character is 36 in ASCII code -> if we scan $, that means new coordinate
					i++; //increment i to change line (row) because there is a new coordinate
					j=0; //store at 1st column of the matrix again
				}

				mapSpots[i][j]=k;
				j++; //go to next column
				System.out.print((char) k);
			}catch (Exception x) {
				break;
			}
 
		}

		System.out.println();

		for (int p=0; p<27; p++){ //PSTOP ITHAKI GPS TRACKING
			try {
				k=modemTemp.read();

				if (k==-1) {
					break;
				}

				System.out.print((char) k);
			}catch (Exception x) {
				break;
			}
		}

		System.out.println();
 
		return mapSpots; //return matrix with all gps spots
	}
 
 
 //--------------------------------------------------------------------------------------------------------------------// 
 
 
	public long[] gpsCoord(int[][] packets){
		int p=0; //variable used to skip '.' characters
		int[][] arrayLL= new int[99][17];//stores the spots coordinates (latitude longitude-LL)->number of characters of LL coords is 17
		int[] tablePlat= new int[99]; //platos
		int[] tableMhk= new int[99]; //mhkos
		
		for (int i=0; i<99; i++){ //store the latitude and longitude coordinates of all spots
			p=0; //reset for each row
			for (int j=18; j<27; j++){ //latitude (platos) coordinates start at 18th character and finish at 26th character
				if (packets[i][j]==46){ //'.' character is 46 in ASCII code -> skips the '.' characters
					p++; //there is a '.' character
					continue;
				}

				arrayLL[i][j-18-p]=packets[i][j]-48; //'0' character is 48 in ASCII code->subtracting 48 converts number from ASCII code to decimal
			}

			p=0; //reset p 
			for (int j=31; j<40; j++){ //longitude (mhkos) coordinates start at 31th character (30th char is '0') and finish at 39th character 
				if (packets[i][j]==46){ //'.' character is 46 in ASCII code -> skips the '.' characters
					p++; //there is a '.' character
					continue;
				}

				arrayLL[i][j-22-p]=packets[i][j]-48; //to continue filling the matrix where we left it off (we left it at index 7 and want to start from index 8 now)
			}   
 
		}

		String[] stringLL= new String[99]; //string matrix to store spots' coordinates 
		for (int i=0; i<99; i++){
			stringLL[i]=""; //initialise i-row's string elements as empty strings
			for (int j=0; j<17; j++){ //number of characters of latitude-longitude coords is 17
				stringLL[i]= stringLL[i] + Integer.toString(arrayLL[i][j]); //pass the coords as strings to stringLL matrix from int arrayLL matrix
			}
			
			//multiply LL minutes by 0.006 to convert from min to sec
			tablePlat[i]= (int)(Integer.parseInt(stringLL[i].substring(4,8))*0.006); //take the 4 characters after the first '.' (decimal point) (4th to 8th character) to round them -> rounds the latitude minutes
			tableMhk[i]= (int)(Integer.parseInt(stringLL[i].substring(13,17))*0.006); //take the 4 characters after the second '.' (decimal point) (8+5=13th to 13+4=17th character) to round them -> rounds the longtitude minutes
		}
		
		//we have to send back requestcode+(T=)+longitude+latitude 
		//we store the first 4 untouched characters of longitude and then the rounded ones in matrix finalSpots (untouched: elements with index 0,1,2,3 , rounded: element with index 4)
		//we store the first 4 untouched characters of latitude and then the rounded ones in matrix finalSpots (untouched: elements with index 5,6,7,8 , rounded: element with index 9)
		//rounded part is stored in one element but it may consist of more than 1 characters (ex. 37)
		//in total we have 10 elements so size of columns of finalSpots is 10

		int[][] finalSpots= new int[99][10]; //finalSpots: longitude-latitude 
 
		for (int i=0; i<99; i++){
			for (int j=0; j<4; j++){ 
				finalSpots[i][j]= arrayLL[i][j+9]; //untouched longitude characters
			}

			finalSpots[i][4]= tableMhk[i]; //rounded longitude characters

			for (int j=5; j<9; j++){
				finalSpots[i][j]= arrayLL[i][j-5]; //untouched latitude characters
			}

			finalSpots[i][9]= tablePlat[i]; //rounded latitue characters
		}

		for (int i=0; i<99;i++){
			for (int j=0; j<10;j++){
				System.out.print(finalSpots[i][j]);
			}
			System.out.println();
		}


		int[] times= new int[99]; //stores packet times in secs 
		for (int i=0; i<99; i++){ //time of the packets starts at 7th character and ends at 12th of each packet
			times[i]=((packets[i][7]-48)*10 + (packets[i][8]-48))*3600 + ((packets[i][9]-48)*10 + (packets[i][10]-48))*60 + ((packets[i][11]-48)*10 + (packets[i][12]-48)); //converts hours and minutes to secs and adds them all together with the secs so that we have the arrival time of each packet in secs
			//times[i]=(Integer.toString(packets[i][7]-48) + Integer.toString(packets[i][8]-48) + Integer.toString(packets[i][9]-48) + Integer.toString(packets[i][10]-48) + Integer.toString(packets[i][11]-48) + Integer.toString(packets[i][12]-48)); //shows hours:minutes:seconds
		} 

		for (int i=0; i<99; i++){
			System.out.println(times[i]);
		}

		int[] finalKeepSpots= new int[6]; //store wanted spots (we want at least 4 spots)->we'll request 6 spots
		finalKeepSpots[0]=0; //first spot
		int m=0;
		int s=1; //breaks for loop once 5 more spots are found

		//we want at least 4 spots that are at least 4 secs apart
		//they also have to be 17 characters apart (so that there is no override between them and they are different spots) 
		
		for (int i=0; i<99; i++){
			if (Math.abs(times[i]-times[m])>=17){
				finalKeepSpots[s]=i; //the elements of finalKeepSpots are the indexes of the elements (spots) of times matrix I wanna keep
				m=i;
				s++;
			}

			if (s==6){
				break;
			}
		}

		System.out.println(); //indexes of wanted spots
		System.out.println(finalKeepSpots[0]);
		System.out.println(finalKeepSpots[1]);
		System.out.println(finalKeepSpots[2]);
		System.out.println(finalKeepSpots[3]);
		System.out.println(finalKeepSpots[4]);
		System.out.println(finalKeepSpots[5]);

		String[] stringTemp= new String[6]; //string matrix to store wanted spots' coordinates
		long[] SpotsFin= new long[6]; //coordinates of the wanted spots
 
		for (int i=0; i<6; i++){
			stringTemp[i]= ""; //initialise i-row's string elements as empty strings
			for (int j=0; j<10; j++){
				stringTemp[i]= stringTemp[i]+ Integer.toString(finalSpots[finalKeepSpots[i]][j]); 
			}

			SpotsFin[i]= Long.parseLong(stringTemp[i]);
		}

		for (int i=0; i<6; i++){
			System.out.println(SpotsFin[i]);
		}
		return SpotsFin; //return matrix with wanted spots
	}


 //--------------------------------------------------------------------------------------------------------------------//	
 

	public int[] acknackARQ(Modem modemT, byte[] ack, byte[] nack, PrintWriter textA, PrintWriter textB){
		long starting= System.currentTimeMillis(); //when the operation starts
		long ending=0; //when the operation finishes 
		long resp; 
		int t=0;
		String Start="";
		String Stop="";
		String packetMessage="";
		boolean pass=true;

		modemT.write(ack); //ack->acknowledgement->packet correctly received
		int k=0;
		int[] packetTable= new int[58]; //length of each packet is 58 bytes
		int[] frameCheckSequence= new int[16]; //frame check sequence->code that detects errors
		int fcs; 
		int XOR;
		String FCS=""; //store fcs code as string
		int[] rightWrong= new int[2];
	
 
		for (;;){

			try{
				for (int i=0; i<58; i++){ //scans packet 
					t= modemT.read();
					packetTable[i]= t; //stores packet
				}
 
				for (int m=0; m<16; m++){ //extract 16-byte long code from packet
					frameCheckSequence[m]= packetTable[31+m]; //store the code which starts at the 31st character 
					                                            //and ends at the 46th of the packet into array frameCheckSequence
				}

				XOR= frameCheckSequence[0]^frameCheckSequence[1]; //initialise XOR->gets value 1 (if the elements are the same) or 0 (if they are different)
				for (int e=1; e<15; e++){
					XOR= XOR^frameCheckSequence[e+1]; //recursively apply xor to all bytes from 16-byte code
				}

				for (int c=0; c<3; c++){ //extract fcs code (3 bytes long) that starts at the 49th character and stops at the 51st character of the packet
					FCS= FCS+Integer.toString(packetTable[49+c]-48); // substracting 48->converting to decimal
				}
 
				fcs= Integer.parseInt(FCS);
				
				if (fcs==XOR){ //if packet was received correctly
					System.out.println("SUCCESS! :)  Correct packet received: ");
					for (int i=0; i<58; i++){
						System.out.print((char)packetTable[i]); //print packet
					}

					rightWrong[0]++; //rightWrong[0] stores number of correct packets
					System.out.println();
					break;
				}
				else{ //if packet was received incorrectly
					modemT.write(nack); //send nack code (nack->negative acknowledgement)
					System.out.println("FAIL! :(  Incorrect packet received. Waiting for retransmission from server... ");
					rightWrong[1]++; //rightWrong[1] stores number of incorrect packets
				}
				
				if (t==-1){
					break;
				}
 
			} catch (Exception x){
				break;
			}
 
		}

		ending=System.currentTimeMillis();
		resp=ending-starting;
		
		System.out.println("Response time:"+resp);
		textA.println(resp);
		System.out.println("Number of errors:"+rightWrong[1]);
		textB.println(rightWrong[1]);
		System.out.println("---Waiting for new packet---");
 
		return rightWrong;
	}
 
}
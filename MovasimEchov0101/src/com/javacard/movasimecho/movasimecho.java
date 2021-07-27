//-----------------------------------------------------------------------------
// PACKAGE DEFINITION
//----------------------------------------------------------------------------
package com.javacard.movasimecho;

//-----------------------------------------------------------------------------
// IMPORTS
//-----------------------------------------------------------------------------

import javacard.framework.*;
import uicc.toolkit.*;
import uicc.usim.access.USIMConstants.*;
import uicc.usim.toolkit.*;
//import uicc.usim.toolkit.USATEnvelopeHandler;
import uicc.access.*;


public class movasimecho extends javacard.framework.Applet implements 
	ToolkitInterface, uicc.toolkit.ToolkitConstants
{
		
	/**
	 * SMS general Info
	 */
	private static final short smsMaxLen=(short)160;
	final static private short IOD_TPDU_LENGTH               = (short) 205;// 205;

	private short bufferSMSLen;
	//EEPROM
    static private byte[] bufferSMS                      = new byte [(short) smsMaxLen ];
	//RAM
    //private byte[] bufferSMS   = JCSystem.makeTransientByteArray((short) smsMaxLen   , JCSystem.CLEAR_ON_RESET);
    private byte[] bufferSMSPP   = bufferSMS;
	private byte[] buffer = bufferSMSPP;
	private short lengthMsg = (short)0;

	private static byte nSendSMS = (byte)0x00;
	private static boolean bSendSMS = false;
	private static final byte SEND_SMS_MAX = (byte)3;

	// static private SIMView gsmFile;
    // USED FOR DEBUGGING IN A FILE
	//private static FileView usimFile;
	
    //***********************************************************************
	//EVENT_FORMATTED_SMS_PP_ENV - Static variable in interface uicc.usim.toolkit.ToolkitConstants
	//Event : Envelope SMS-PP Data Download (31.115 formatted) = 2
    final static private short EVENT_FORMATTED_SMS_PP_ENV = (short)2;
	//EVENT_FORMATTED_SMS_PP_UPD - Static variable in interface uicc.usim.toolkit.ToolkitConstants
	//Event : Update Record EF sms APDU (31.115 formatted) = 3
    final static private short EVENT_FORMATTED_SMS_PP_UPD = (short)3;
    //EVENT_UNFORMATTED_SMS_PP_ENV - Static variable in interface uicc.usim.toolkit.ToolkitConstants
    //Event : Envelope SMS-PP Data Download unformatted sms = 4
    final static private short EVENT_UNFORMATTED_SMS_PP_ENV = (short)4;
    //EVENT_UNFORMATTED_SMS_PP_UPD - Static variable in interface uicc.usim.toolkit.ToolkitConstants
    //Event : Update Record EFsms APDU unformatted sms = 5
    final static private short EVENT_UNFORMATTED_SMS_PP_UPD = (short)5;
    
    //***********************************************************************
    //***********************************************************************
    //VARIABLES FOR SENDING SMS
    /*
	private byte[] alphaID_SMS = { (byte) 'E', (byte) 'n',
			(byte) 'v', (byte) 'i', (byte) 'a', (byte) 'n', (byte) 'd',
			(byte) 'o', (byte) ' ', (byte) 'm', (byte) 'e', (byte) 'n',
			(byte) 's', (byte) 'a', (byte) 'j', (byte) 'e', (byte) '.',
			(byte) '.', (byte) '.'};
     */

    //***********************************************************************
    //FOR DEBUGGING
    /*
    private final short EF_LOG = 0x4F31;
    private final short MF = 0x3F00;
    private byte[] msg_true = { (byte)'T', (byte)'R', (byte)'U', (byte)'E'};
    private byte[] msg_false = { (byte)'F', (byte)'A', (byte)'L', (byte)'S', (byte)'E'};
    private byte[] msg_debug = { (byte)'D', (byte)'E', (byte)'B', (byte)'U', (byte)'G',
    		                     (byte)':', (byte)'0', (byte)'0' };
    private byte[] msg_debug_clr = { (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
    		                     (byte)0xFF, (byte)0xFF, (byte)0xFF};
    */
    //***********************************************************************
    
	private short posUdl; // User Data Length
	private short posUD; // Position of User Data on tpdu buffer
	
	private static final byte smsMTI=(byte)0x11;
	private static final byte smsMR=(byte)0xFF;
	private static final byte smsPID=(byte)0x00;
	//private static final byte smsValidityPeriod = (byte)0xA7;
	// 0xAD = 7 days, 0xA7 = 1 day
	private static final byte smsValidityPeriod = (byte)0xAD;
	private static final byte[] validityPeriod = { (byte) smsValidityPeriod };// {(byte)0x8F};
	//DCS = 7 bit Packed => 0x00
	//DCS = 8 bits Packed => 0x04
	private static final byte smsDataCodingScheme=(byte)0x04;
	//private static final byte smsDataCodingScheme=(byte)0x00;

	
    /**
    * Read record Usefull stuff
    **/
    private static final byte FCI_LENGTH                     = 15;

	/** Offset of the length of the OTA command inside the buffer */
	private final static short INCOMING_SMS_CHL_OFFSET 										= (short)0x06;

    /**
    * Buffer
    */
    static final private short IOD_BUFFER_LENGTH             = (short) IOD_TPDU_LENGTH; // >TPDU
    
    //RAM
    /*
    private byte[] generalBuffer = JCSystem.makeTransientByteArray(IOD_BUFFER_LENGTH, JCSystem.CLEAR_ON_RESET);
    */
    //EEPROM
    static private byte[] generalBuffer                      = new byte [(short) IOD_BUFFER_LENGTH ];

    //***********************************************************************
    //***********************************************************************
	

	
    //TPDA for Sending SMS
    //Example: 0x06 08 81 99 99 99 99
    //MOVASIM BY DEFAULT: 44536 => 0x05 81 44 35 F6
    static private byte [] TPDA = {
    	(byte)0x05, (byte)0x05, (byte)0x81, (byte)0x44, (byte)0x35, (byte)0xF6, (byte)0xFF, (byte)0xFF, (byte)0xFF };

     public static movasimecho AppletPluginHldr = null;

     /**
     * @param bArray
     * @param bOffset
     * @param bLength
     */
    public movasimecho(byte bArray[], short bOffset, byte bLength)
     {
    	 short i;
    	 
		// Process installation parameters
		bOffset += (short) (bArray[bOffset] + 1); // skip AID
   		bOffset += (short) (bArray[bOffset] + 1); // skip Privileges

   		bOffset++; // Skip parameter count

		//usimFile = UICCSystem.getTheUICCView(JCSystem.CLEAR_ON_RESET);

   		//************************************************************************
   		//NOT NEEDED PER NOW
   		/*
   		if(bArray.length > bOffset)
   		{
		    try {
		    	
		   		if(bArray[bOffset]>(byte)0x00)
		   		{
		   		    i=(short)(TPDA.length-(short)1);
		   		    if(i>(short)bArray[(short)bOffset])
		   		    	i=(short)bArray[(short)bOffset];
		   		    
		           	Util.arrayCopyNonAtomic(bArray, (short)(bOffset+(short)1), TPDA, (short)1, i);
		           	TPDA[(short)0]=(byte)i;
		           	
		           	bOffset+=(short)(bArray[(short)bOffset]+1);
		   		}
		   		
		      }catch(ISOException e)
		      {
				ISOException.throwIt(ISO7816.SW_DATA_INVALID);
		      }
   		}
   		*/
   		//************************************************************************
   		
     }

 	/**
 	 * @param bArray
 	 * @param bOffset
 	 * @param bLength
 	 * @throws ISOException
 	 */
 	public static void install(byte bArray[], short bOffset, byte bLength)
			throws ISOException {
		
		// create and register applet
        AppletPluginHldr = new movasimecho(bArray, bOffset, bLength);
        AppletPluginHldr.register();

    	// Get the reference of the applet ToolkitRegistry object
 		ToolkitRegistry tkReg = ToolkitRegistrySystem.getEntry();

	   	 /*
        reg.initMenuEntry(menuEntry,
                (short) 0,
                (short) menuEntry.length,
                (byte) 0x00, (boolean) false, (byte) 0x00,
                (short) 0x00);
		*/

   		tkReg.setEvent(EVENT_FORMATTED_SMS_PP_ENV);
   		tkReg.setEvent(EVENT_FORMATTED_SMS_PP_UPD);
   		tkReg.setEvent(EVENT_UNFORMATTED_SMS_PP_ENV);
   		tkReg.setEvent(EVENT_UNFORMATTED_SMS_PP_UPD);

		// Profile Download
		tkReg.setEvent(EVENT_PROFILE_DOWNLOAD);
		
		//POLLINTERVAL FOR STATUS COMMAND 30 seconds
		//FOR SENDING SMS
		tkReg.requestPollInterval((short)30);
		
   		//FOR TESTING - WRITTING DEBUG FILE
   		//usimFile = UICCSystem.getTheUICCView(JCSystem.CLEAR_ON_RESET);
		
 	}

     /**
     *
     */
    public boolean select()
     {
        return true;
     }

     /**
     *
     */
    public void deselect()
     {
        return;
     }

 	/**
 	 * This method allows to get the item identifier selected after a selectItem
 	 * proactive command. This method allows to not stack genRespHdlr
 	 * 
 	 * @return Item identifier value
 	 */
 	private byte getItemIdentifier() {
 		// Get the ResponseHandler system instance(Need to use command response)
 		ProactiveResponseHandler genRespHdlr = ProactiveResponseHandlerSystem
 				.getTheHandler();

 		return ((byte) genRespHdlr.getItemIdentifier());
 	}

     /**
     *
     */
    public void process(APDU apdu)
     {
 		// ignore the applet select command dispached to the process
 		if (selectingApplet()) {
 			return;
 		}
    	return;
     }


	/** 
	 * Process the events associated to the applet 
	 */
  	public void processToolkit(short event) throws ToolkitException
     {
  	// capture the event
  			switch (event) 
  			{
  				case EVENT_PROFILE_DOWNLOAD:
  					
  					//CLEAN DEBUG FILE
  					//fWriteDebugClear();
  					
  					break;
  					
  				case EVENT_STATUS_COMMAND:
  					
  					//FOR DEBUGGING
  					//fDebuggingText(TPDA, (short)0, (short) TPDA.length);

  					//SEND SMS AFTER x STATUS COMMAND BECAUSE A SMSPP WAS RECEIVED.
  					if(bSendSMS)
  					{
  						nSendSMS++;
  						
  						if(nSendSMS > SEND_SMS_MAX)
  						{
  							bSendSMS=false;
  							fSendSMS(bufferSMSPP, (short)0, lengthMsg);
  						}
  						
  					}
  					
  					break;
  					
  	            case EVENT_FORMATTED_SMS_PP_ENV:
  	            case EVENT_FORMATTED_SMS_PP_UPD:
  	            case EVENT_UNFORMATTED_SMS_PP_ENV:
  	            case EVENT_UNFORMATTED_SMS_PP_UPD:
  	            	
  	            	processSMSPP();
  					break;
  					
  	            default:
  	           	break;
  			}
  				
			return;
  				
     }


     /**
     *
     */
    public Shareable getShareableInterfaceObject(AID aid, byte param)
     {
 		if ((param == (byte) 0x01) && (aid == null)) {
			return ((Shareable) this);
		}
		return null;
     }

    /**
     * @param sMsg
     * @param nOffSet
     * @param nLen
     */
    public void serviceDisplayText(byte[] sMsg, short nOffSet, short nLen)
    {
    	ProactiveHandler proHdlr;

      proHdlr = ProactiveHandlerSystem.getTheHandler();
//      proHdlr.initDisplayText((byte)0x80,DCS_8_BIT_DATA,sMsg,(short)0,(short)nLen);
      //proHdlr.initDisplayText((byte)0x01,(byte)0x04,sMsg,(short)nOffSet,(short)nLen);
      proHdlr.initDisplayText((byte)0x80,(byte)smsDataCodingScheme,sMsg,(short)nOffSet,(short)nLen);
      proHdlr.send();
    }

    /**
     * 
     */
    public void processSMSPP()
    {
    	//EnvelopeHandler  envHdlr;
    	USATEnvelopeHandler  envHdlr;
    	
		short offsetSMS = (short)0;
		short lengthSMS = (short)0;
		short actOffset = (short)0;

		//byte[] buffer = bufferSMSPP;
		
		//fWriteDebugShort((short)1);
		
		// Copy the user data in tempBuffer[]
		//envHdlr = EnvelopeHandlerSystem.getTheHandler();
		envHdlr = USATEnvelopeHandlerSystem.getTheHandler();
		//ProactiveHandler proHandler = ProactiveHandlerSystem.getTheHandler();
		
		//GET TPDA SOURCE
		short nSMSLen = envHdlr.getShortMessageLength();
		envHdlr.copyValue((short)0, buffer, (short)0, nSMSLen);
		//Example: 0x44 0C 81 21 43 65 87 09 21 7F F6 00 00 00 00 00 00 00 21 02 70
		//TPDA: 0x0C 81 21 43 65 87 09 21
		fTakeTPDASourceToDestination(buffer, (short)0, (short)buffer.length, TPDA);
		//fDebuggingText(TPDA, (short)0, (short)TPDA.length);

		//GET DATA FROM SMS
		offsetSMS = envHdlr.getTPUDLOffset();
		lengthSMS = (short) (envHdlr.getValueLength()- offsetSMS -1);
		actOffset = (short)(INCOMING_SMS_CHL_OFFSET +  envHdlr.getValueByte((short)(offsetSMS+INCOMING_SMS_CHL_OFFSET)) + (short)1);
		lengthMsg = (short)(++lengthSMS-actOffset);
		envHdlr.copyValue((short)(offsetSMS + actOffset), buffer, (short)0, lengthMsg);
		
		//fDebuggingText(buffer, (short)0, lengthMsg);
		
		//serviceDisplayText(buffer, (short)0, lengthMsg );
		//SEND SMS DIRECTLY
		//fSendSMS(buffer, (short)0, lengthMsg);

		//SEND SMS
		//PREPARE COUNTERS AND ACTIVATE SEND SMS AFTER x STATUS COMMANDS
		bSendSMS=true;
		nSendSMS=(byte)0;
		

    	return;
    }

    /**
     * @param sSource
     * @param nSourceStart
     * @param nSourceLen
     * @param sTPDANew
     */
    private void fTakeTPDASourceToDestination(byte[] sSource, short nSourceStart, short nSourceLen, byte[] sTPDANew)
    {
    
    	//Example: 0x44 0C 81 21 43 65 87 09 21 7F F6 00 00 00 00 00 00 00 21 02 70
    	//TPDA: 0x0C 81 21 43 65 87 09 21
    	short n=(short)0;
    	short m;
    	byte nBytes=(byte)0;
    	boolean bLeave=false;

    	n=(short)(nSourceStart+1);
    	m=(short)1; //For TPDA, first record has the amount of bytes for coding
    	while(!bLeave && n<(short)(nSourceStart + nSourceLen) && n < (short)sSource.length && m<(short)sTPDANew.length )
    	{
    		//COMPARE TO 0x7F F6 for leaving while
    		if(sSource[n]==(byte)0x7F && sSource[(short)(n+1)]==(byte)0xF6)
    			bLeave=true;
    		else
    		{
	    		sTPDANew[m]=sSource[n];
	    		m++;
	    		nBytes++;
    		}
    		
    		n++;
    	}
    	
    	//ADD amount of bytes for TPDA
    	if(nBytes>(short)0)
    		sTPDANew[(short)0]=nBytes;
    	
    	return;
    }

    /**
     * @param sMsg
     * @param nMsgFrom
     * @param nMsgLen
     */
    private void fSendSMS(byte[] sMsg, short nMsgFrom, short nMsgLen)
    {
    	//SEND SMS
        // Cargar el mensaje preformateado en bufferSMS
        Util.arrayCopy(sMsg, nMsgFrom, bufferSMS, (short)0, nMsgLen);
        bufferSMSLen = nMsgLen;
	
        // SEND SMS
        buildAndSendSMS();
    }

	//***************************************************************************************************************
	//
	// buildAndSendSMS END
	//
    // ///////////////////////////////////////////////////
	// FOR SENDING SMS
	// ///////////////////////////////////////////////////

    /*
	private byte[] alphaID_SMS = {  (byte) 'S', (byte) 'e', (byte) 'n',
			(byte) 'd', (byte) 'i', (byte) 'n', (byte) 'g', (byte) ' ',
			(byte) '.', (byte) '.',	(byte) '.' };
	*/
    
	/*
	 * buildAndSendSMS START writes generalBuffer
	 */
	public boolean buildAndSendSMS() {
		short originalLen;
		ProactiveHandler smsHandler;

		try {
			smsBuildMsgHeader((byte) smsMTI, (byte) smsMR, (byte) smsPID,
					(byte) smsDataCodingScheme, validityPeriod, (byte) validityPeriod.length);
			// First param set 0x01 means no validity period
			originalLen = bufferSMSLen;
			if (originalLen > smsMaxLen)
				originalLen = (short) smsMaxLen;

			smsAddMessage2Body((short) ((short) originalLen & (short) 0x00FF),
					bufferSMS);

			// serviceDisplayText(DT_WAIT, generalBuffer, (short) 0, (short)
			// generalBuffer.length);

			smsHandler = ProactiveHandlerSystem.getTheHandler();

			// PRO_CMD_SEND_SHORT_MESSAGE = 0x13
			smsHandler.init((byte) 0x13, (byte) 0x01, DEV_ID_NETWORK);

			//NOT NEEDED
			//smsHandler.appendTLV(TAG_ALPHA_IDENTIFIER, alphaID_SMS, (short) 0,
			//		(short) alphaID_SMS.length);

			smsSendMessage(smsHandler);
			return true;
		} catch (ToolkitException te) {
			return false;
		}
	}

	/**
	 * The method buildMsgHeader builds the TPUD header
	 * 
	 * @param mti
	 *            see 03.40
	 * @param mr
	 *            message reference
	 * @param protId
	 *            protocol Identifier
	 * @param dataCodSch
	 *            Data Coding Scheme
	 * @param valPer
	 *            validity Period
	 * @param lenValPer
	 *            length of the buffer containing validity Period
	 * @return true: command built successfully - command built unsuccessfully
	 */
	private boolean smsBuildMsgHeader(byte mti, byte mr, byte protId,
			byte dataCodSch, byte[] valPer, byte lenValPer) {
//		short j;

		// for (j = (short)0; j < (short)generalBuffer.length; j++)
		// generalBuffer[j] = (byte)0;
		Util.arrayFillNonAtomic(generalBuffer, (short) 0,
				(short) generalBuffer.length, (byte) 0x00);

		posUD = (short) 0;
		// SMS_SUBMIT (MTI-RD-VPF-SRR-UDHI-RD)
		generalBuffer[posUD++] = (byte) mti;

		// TP-MR : Number of Short Message
		generalBuffer[posUD++] = (byte) mr;

		// Add Destination Address
		// for (j = 0; j < destAddLen; j++)
		// generalBuffer[posUD++] = destinationAddress[j];

		Util.arrayCopy(TPDA, (short) 1, generalBuffer,
				(short) posUD, (short) TPDA[(short)0]);
		posUD += TPDA[(short)0];

		// Protocol Identifier
		generalBuffer[posUD++] = protId;

		// Data Coding Scheme
		generalBuffer[posUD++] = dataCodSch;

		// Validity Period
		// for (j = 0; j < lenValPer; j++)
		// generalBuffer[posUD++] = valPer[j];

		Util.arrayCopy(valPer, (short) 0, generalBuffer, (short) posUD,
				(short) lenValPer);
		posUD += lenValPer;

		// Store the position of the UDL (User Data Length)
		posUdl = (byte) posUD;

		// Message Length
		generalBuffer[posUD++] = (byte) 0x00;

		return true;
	}

	/**
	 * The method addMessage2Body add the TPUD to the tlv proactive command
	 * 
	 * @param lenMsg
	 *            length of the message to be added
	 * @param message2Send
	 *            string to be added
	 * @return true: command built successfully - command built unsuccessfully
	 */
	private boolean smsAddMessage2Body(short Len, byte[] Src_String) {
		short k, lenMsg;
		lenMsg = Len;

		// Replaces try - catch(IndexOutOfBoundsException e)
		if (((short) ((short) posUdl + (short) lenMsg)) > ((short) IOD_TPDU_LENGTH))
			return false;

		// Add the Message length
		generalBuffer[posUdl] = (byte) (lenMsg & (short) 0x00FF);
		// Message Body
		for (k = (short) 0; k < lenMsg; k++)
			generalBuffer[posUD++] = Src_String[k];

		return true;
	}

	/**
	 * The method sendMessage executes the command and return the general result
	 * 
	 * @param smsHandler
	 *            ProactiveHandler used to build the command
	 * @return general result of the command execution - FF: exception occured
	 */
	// This methods returns the number of the exception
	private byte smsSendMessage(ProactiveHandler smsHandler) {
		try {
			// smsHandler.appendTLV(TAG_ADDRESS, smscAddress, (short)0x00,
			// (short)smscAddLen);
			// TAG_SMS_TPDU = 0x0B
			smsHandler.appendTLV((byte) 0x0B, generalBuffer, (short) 0x00,
					(short) ((short) posUD & (short) 0x00FF));
		} catch (ToolkitException e) {
			return (byte) 0xFF;
		}
		return (smsHandler.send());
	}

	//
	// buildAndSendSMS END
	//
	//***************************************************************************************************************

	//***************************************************************************************************************
	//DEBUGGING METHODS
	//WRITE LOG FILE FOR DEBUGGING
	/*
	//WRITE LOG FILE
	private boolean fWriteLog(byte[] sBuffer, short nFrom, short nLen)
	{
	
		try{

			usimFile.select((short) MF);
			usimFile.select((short) EF_LOG);
			usimFile.updateBinary((short)0, sBuffer, nFrom, nLen);
			return true;

		} catch (UICCException e) {
			return false;
		}
		
	}

	//WRITE msg_debug LOG WITH SHORT
	void fWriteDebugShort(short nVal)
	{
	      short n;

	      n=nVal;
	      msg_debug[(short)(msg_debug.length-2)]=(byte)((short)0x00FF & (n >> (short)8));
	      msg_debug[(short)(msg_debug.length-1)]=(byte)((short)0x00FF & nVal);
	      fWriteLog(msg_debug, (short)0, (short)msg_debug.length);
	}
	
	//WRITE LOG WITH SHORT
	void fWriteLogShort(short nVal)
	{
		fromShortToBytesIntoGeneralBuffer(nVal, (short)0);
		fWriteLog(generalBuffer, (short)0, (short)2);
	}
	
	//CLEAR DEBUG FILE
	void fWriteDebugClear()
	{
		fWriteLog(msg_debug_clr, (short)0, (short)msg_debug_clr.length);
	}
	
	//SHORT TO BYTES
	void fromShortToBytesIntoGeneralBuffer(short nValue, short nGeneralBufferStart)
	{
	      short n;

	      n=nValue;
	      generalBuffer[(short)nGeneralBufferStart++]=(byte)((short)0x00FF & (n >> (short)8));
	      generalBuffer[(short)nGeneralBufferStart]=(byte)((short)0x00FF & nValue);
	}
	
	//FOR DEBUGGING TEXT
	private void fDebuggingText(byte[] sVal, short nFrom, short nLen)
	{
		//FOR DEBUGGING
		if(fWriteLog(sVal, (short)nFrom, (short)nLen))
			serviceDisplayText(msg_true, (short)0, (short)msg_true.length );
		else
			serviceDisplayText(msg_false, (short)0, (short)msg_false.length );
	}
	*/
	//***************************************************************************************************************
	
}





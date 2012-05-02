package com.dobydigital.dobixchange.smartsocket;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import net.smartsocket.client.SmartSocketClient;
import net.smartsocket.protocols.binary.RemoteCall;
import android.util.Log;

import com.dobydigital.dobixchange.StatusBarFragment;
import com.google.gson.JsonObject;

public class SocketClient extends SmartSocketClient {

	public SocketClient(String host, int port, int timeout) {
		super(host, port, timeout);
	}

	@Override
	protected void onConnect(String connectMessage) {
		Log.i("SmartSocket", "Connection success.");
		StatusBarFragment.onConnect();
	}

	@Override
	protected void onConnectFail(Exception exception) {
		Log.i("SmartSocket", "Connection failed: "+exception);
		StatusBarFragment.onConnectFail(exception);
	}

	@Override
	protected void onDisconnect(String disconnectMessage) {
		Log.i("SmartSocket", "Connection lost.");
		StatusBarFragment.onDisconnect();
	}
	
	@Override
	protected void onSendFail(RemoteCall call, Exception e) {
		// TODO Auto-generated method stub
		StatusBarFragment.sendFileToServerFail(call, e);
	}
	
	/**
	 * This method is called from within the GUI part of the Android application.
	 * It's pretty self explanatory -- you pass the file to this method, and it sends
	 * to the SmartSocket server (DobiXchange Server) that it's currently connected to.
	 * @param file The file to send to the server.
	 */
	public void sendFileToServer(File file) {
		StatusBarFragment.sendFileToServerStarted(file);
		
		RemoteCall call = new RemoteCall("onImageFromClient");
		//# We want to tell the server the location of the file as hosted on the client.
		//# This lets the server send it back to the correct directory when changes to the image
		//# are detected on the server.
		call.put("folder", file.getParent());
		//# The actual name of the file.
		call.put("file", file.getName());
		//# The file object itself.
		call.put(file);
		
		//# Try to send the file through the open socket, and notifiy out GUI that the send was a success.
		if( send(call) ) {
			StatusBarFragment.sendFileToServerComplete(file);
		}		
	}
	
	/**
	 * This method is going to be called from the server when there is a sync needed due to a file change.
	 * @param json The JsonObject containing the components of the RemoteCall from the server.
	 * @param fileBytes The byte array containing the bytes of the file sent from the server.
	 */
	public void onFileUpdateFromServer(JsonObject json, byte[] fileBytes) {
		OutputStream out;
		//# Grab the path to the file as sent from the server.
		File file = new File(json.get("file").getAsString());
		
		//# Notify our GUI that there is an incoming file.
		StatusBarFragment.onFileUpdateFromServerStart(file);
		
		//# Basically we are just going to loop theough all of the bytes of the file, then
		//# write them to disk, overwriting what is currently there (there was a change on
		//# the fle on the computer hosting the server).
		//# We then do some GUI updates depending on the status of this write.
		try {
			out = new BufferedOutputStream( new FileOutputStream( file ) );
			
			for ( int b = 0; b < fileBytes.length; b++ ) {
				out.write( fileBytes[b] );
			}
			
			out.close();
			StatusBarFragment.onFileUpdateFromServer(file);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			StatusBarFragment.onFileUpdateFromServerFail(file, e);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			StatusBarFragment.onFileUpdateFromServerFail(file, e);
		}
	}

}

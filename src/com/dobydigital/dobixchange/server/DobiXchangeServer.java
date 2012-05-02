package com.dobydigital.dobixchange.server;

import com.dobydigital.dobixchange.forms.SettingsForm;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import net.smartsocket.Config;
import net.smartsocket.Logger;
import net.smartsocket.forms.ConsoleForm;
import net.smartsocket.protocols.binary.RemoteCall;
import net.smartsocket.serverclients.TCPClient;
import net.smartsocket.serverextensions.TCPExtension;

/**
 *
 * @author XaeroDegreaz
 */
public class DobiXchangeServer extends TCPExtension {
	public static JsonObject configuration;
	private Map<TCPClient, SyncMonitor> syncMonitors = new HashMap<TCPClient, SyncMonitor>();
	
	/**
	 * @param args the command line arguments
	 */
	public static void main( String[] args ) {
		// TODO code application logic here
		new DobiXchangeServer().start();
	}

	public DobiXchangeServer() {		
		super( 8888 );
		this.imageIcon = new ImageIcon( getContextClassLoader().getResource( "com/dobydigital/dobixchange/resources/logo.png") );
	}
	
	/**
	 * This method is called directly from the client. The client is sending a file that needs to be written on the computer.
	 * @param client The TCPClient associated with this call.
	 * @param json The JsonObject passed along with this call.
	 * @param fileBytes The File object, broken down into bytes for processing.
	 */
	public void onImageFromClient( TCPClient client, JsonObject json, byte[] fileBytes ) {
		Logger.log( "Receiving file from client..." );
		try {
			//# Create a File object according to the home directory, and the requested root folder, replicated by
			//# the folder on the Android file system.
			File file = new File(configuration.get( "home" ).getAsString()+"\\"+json.get( "folder" ).getAsString());
			//# Create the dirs if they are not alreay present
			file.mkdirs();
			
			//# Get a reference to the actual file that will be written
			file = new File(file.getAbsolutePath(), json.get( "file" ).getAsString());
			//# Create a blank temporary file, if it doesn't exist.
			file.createNewFile();
			
			//# Create an output stream, wrapped around a file output stream that we can write these bytes into.
			OutputStream out = new BufferedOutputStream( new FileOutputStream( file ) );
			
			//# Loop through each byte, and write them to disk.
			for ( int b = 0; b < fileBytes.length; b++ ) {
				out.write( fileBytes[b] );
			}
			
			//# Clean up
			out.close();
			
			Logger.log( file.getAbsolutePath()+" has been written." );			
			Logger.log( "File sent to Photoshop." );
			
			//# Use the Photoshop executable specified in the configuration to launch Photoshop, and open the file for editing.
			Runtime.getRuntime().exec(new String[] {
				configuration.get( "photoshop" ).getAsString(),				
				file.getAbsolutePath()
			});
			
			//# Get a regerence to the sync monitor for this TCPClient, and add this file as one to be listened to for changes.
			syncMonitors.get( client ).monitorFile(file, json.get( "folder" ).getAsString()+"/"+json.get( "file" ).getAsString());
			
		} catch (Exception e) {
			//# Something went wrong during this whole process.
			e.printStackTrace();
			Logger.log( "There was an error receiving file / pushing to Photoshop: "+e.getMessage() );
		}
	}
	
	/**
	 * This class basically monitors changes in files, and if one is detected, it sends it back to the client in its new form.
	 */
	private class SyncMonitor extends TimerTask {
		//# A queue of files to monitor for changes.
		private Map<File, Long> fileQueue = new HashMap<File, Long>();
		//# A list of directories pertaining to each file as they are stored on the Android device.
		private Map<File, String> filePaths = new HashMap<File, String>();
		private TCPClient client;
		
		public SyncMonitor(TCPClient client) {
			this.client = client;
		}
		
		public void monitorFile(File file, String clientPath) {
			fileQueue.put( file, file.lastModified() );
			filePaths.put( file, clientPath );
		}
		
		/**
		 * Check for changes, and send them to the server
		 */
		@Override
		public void run() {
			for(Map.Entry<File, Long> file : fileQueue.entrySet()) {
				File theFile = file.getKey();				
				File tempFile = null;
				long theLong = file.getValue();
				
				//# If the file's been modified since being sent here, go ahead and send it back to the client
				if(theFile.lastModified() > theLong) {
					Logger.log( "Syncing "+theFile.getAbsolutePath()+" to client." );
					
					//# Update our quwuw for this file, noting the change time
					file.setValue( theFile.lastModified() );
					
					//# Create a temporary file that we'll send to prevent locking of original file.
					tempFile = new File( theFile.getParent(), "temp-"+theFile.getName() );	
					
					try {
						FileInputStream in = new FileInputStream( theFile );
						byte[] outBytes = new byte[ in.available() ];
						FileOutputStream out = new FileOutputStream( tempFile );
						int i = 0;
						
						while ( ( i = in.read( outBytes ) )  != -1 ) {
							out.write( outBytes, 0, i );
						}
						
						out.flush();
						in.close();
						out.close();						
					}catch(Exception e) {
						e.printStackTrace();
					}					
					
					RemoteCall call = new RemoteCall( "onFileUpdateFromServer" );
					call.put( "file", filePaths.get( theFile ) );
					call.put( tempFile );
					//# Off we go!
					client.send( call );
				}
			}
		}
	}
	
	//# Perform some simple steps once this server is ready to accept connections.
	@Override
	public void onExtensionReady() {
		Logger.setLogLevel( Logger.DEBUG );
		Logger.log( "This server is powered by SmartSocket." );
		Logger.log( "DobiXchange Server is online, and awaiting connections..." );
		configuration = new JsonObject();
		
		//# Add something to the 'File' menu that allows users to change home, and Photoshop locations
		JMenu fileMenu = ConsoleForm.menuBar.getMenu( 0 );
		JMenuItem fileSettingsMenuItem = new JMenuItem( "Settings...");
		fileMenu.add( fileSettingsMenuItem );
		
		fileSettingsMenuItem.addActionListener( new ActionListener() {

			@Override
			public void actionPerformed( ActionEvent e ) {
				SettingsForm.main( null );
			}
		});
		
		//# Attempt to load the config file
		loadConfig();
	}
	
	/**
	 * Called when a user connects to the server. We just setup a SyncMonitor for them, and monitor
	 * files that they send to us for changes. WHen changed, they will send back to the client and updated version.
	 * @param client 
	 */
	@Override
	public void onConnect( TCPClient client ) {
		Logger.log( "Client connection has been established." );
		Timer timer = new Timer();
		SyncMonitor monitor = new SyncMonitor( client );
		
		syncMonitors.put( client, monitor );
		timer.scheduleAtFixedRate( monitor, 0, 5000);
	}
	
	/**
	 * Here, we just remove all information of the client, and stop all timers that are monitoring their files.
	 * @param client 
	 */
	@Override
	public void onDisconnect( TCPClient client ) {
		Logger.log( "Client connection has been lost." );
		SyncMonitor monitor = syncMonitors.get( client );
		monitor.cancel();
		
		syncMonitors.remove( client );
	}

	@Override
	public boolean onDataSpecial( TCPClient client, String methodName, JsonObject params ) {
		return false;
	}
	
	/**
	 * Attempt to load the configuration file. In none is present, then we go ahead and launch the form for them so they can input
	 * the paths to Photoshop, and preferred directory in which to save files received from the client.
	 */
	private void loadConfig() {
		Logger.log( "Loading DobiXchange Server configuration..." );
		try {
			configuration = (JsonObject) new JsonParser().parse( Config.readFile( "config-dobixchange.json" ).toString() );
			Logger.log( "Config loaded." );
		} catch (JsonParseException e) {
			//# Someone must have modified (poorly) the JSON configuration file.
			Logger.log( "Malformed JSON in the configuration file.", Logger.CRITICAL );
		} catch (FileNotFoundException e) {
			//# Couldn't find the config file, so we write our default one.
			Logger.log( "Could not load config. Launching settings form." );
			SettingsForm.main( null );
		}
	}
}

package com.example.dodedodo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

//import android.app.Notification;
//import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
//import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;


class ModuleKey implements Serializable {
	private static final long serialVersionUID = 1L;
	public String mName;
	public int mId;
	public ModuleKey(String name, int id) {
		mName = name;
		mId = id;
	}
//	public String getName() { return mName; }
//	public int getId() { return mId; }
//	public void setName(String name) { mName = name; }
//	public void setId(int id) { mId = id; }
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ModuleKey k = (ModuleKey)obj;
		if (mName == null || k.mName == null)
			return false;
		if (mId == k.mId && mName.equals(k.mName))
			return true;
		return false;
	}
	public int hashCode() {
		return (mName + mId).hashCode();
	}
	public String toString() {
		return new String(mName + "[" + mId + "]");
	}
}

class ModulePort implements Serializable {
	private static final long serialVersionUID = 1L;
	public String name; // Handy, but double info
	transient public Messenger messenger = null;
	public String otherModuleDevice;
	public String otherModuleName;
	public int otherModuleId = -1;
	public String otherPortName;
	public String toString() {
		String str = new String(name);
		ModuleKey key = new ModuleKey(otherModuleName, otherModuleId);
		str += "(" + otherModuleDevice + "/" + key.toString() + ":" + otherPortName + ")";
		return str;
	}
}

class Module implements Serializable {
	private static final long serialVersionUID = 1L;
	public ModuleKey key; // Handy, but double info
	transient public Messenger messenger = null;
	public HashMap<String, ModulePort> portsIn = new HashMap<String, ModulePort>(); // Key is port name
	public HashMap<String, ModulePort> portsOut = new HashMap<String, ModulePort>(); // Key is port name
	public String toString() {
		String str = new String(key.toString());
		str += " messenger=";
		if (messenger == null)
			str += "null";
		else
			str += messenger.toString();
		str += " IN:";
		for (ModulePort p : portsIn.values())
			str += ", " + p.toString();
		str += " OUT:";
		for (ModulePort p : portsOut.values())
			str += ", " + p.toString();
		return str;
	}
}

class InstalledModulePort implements Serializable {
	private static final long serialVersionUID = 1L;
	public String name;
	public int dataType;
	public String toString() {
		return new String(name + "<" + dataType + ">");
	}
}


class InstalledModule implements Serializable {
	private static final long serialVersionUID = 1L;
	public String name; // Handy but double info
	public String packageName;
	public String installUrl;
	public ArrayList<InstalledModulePort> portsIn = new ArrayList<InstalledModulePort>();
	public ArrayList<InstalledModulePort> portsOut = new ArrayList<InstalledModulePort>();
	public String toString() {
		String str = new String(name);
		str += " " + packageName;
		str += " " + installUrl;
		str += " IN:";
		for (InstalledModulePort p : portsIn)
			str += ", " + p.toString();
		str += " OUT:";
		for (InstalledModulePort p : portsOut)
			str += ", " + p.toString();
		return str;
	}
}

public class MsgService extends Service {
	private static final String TAG = "MsgService";

	Messenger mToXmppMessenger = null;
	final Messenger mFromXmppMessenger = new Messenger(new XmppMsgHandler());
	boolean mXmppServiceIsBound;
	
//	Messenger mXmppModuleMessenger = null;
	
	final Messenger mFromModuleMessenger = new Messenger(new IncomingMsgHandler());
	
	
//	/** For showing and hiding our notification. */
//	NotificationManager mNM;
	/** Keeps track of all current registered clients. */
	
	HashMap<String, InstalledModule> mInstalledModules = new HashMap<String, InstalledModule>();
	//ArrayList<Module> mModules = new ArrayList<Module>();
	HashMap<ModuleKey, Module> mModules = new HashMap<ModuleKey, Module>();
	

	public static final int MSG_REGISTER = 1;
	public static final int MSG_UNREGISTER = 2;
	public static final int MSG_SET_MESSENGER = 3;
	public static final int MSG_START = 4;
	public static final int MSG_STOP = 5;
	public static final int MSG_SEND = 6;
	public static final int MSG_XMPP_LOGIN = 7;
	public static final int MSG_ADD_PORT = 8;
	public static final int MSG_REM_PORT = 9;
	public static final int MSG_XMPP_LOGGED_IN = 10;
	public static final int MSG_XMPP_DISCONNECTED = 11;
	public static final int MSG_PORT_DATA = 12;
	public static final int MSG_USER_LOGIN = 13;
	public static final int MSG_GET_MESSENGER = 14;
	
	public static final int DATATYPE_FLOAT = 1;
	public static final int DATATYPE_FLOAT_ARRAY = 2;
	public static final int DATATYPE_STRING = 3;
	public static final int DATATYPE_IMAGE = 4;
	public static final int DATATYPE_BINARY = 5;
	
	public int GetDataType(String s) {
		int res = 0;
		if (s.equals("float"))
			res = DATATYPE_FLOAT;
		else if (s.equals("floatarray"))
			res = DATATYPE_FLOAT_ARRAY;
		else if (s.equals("string"))
			res = DATATYPE_STRING;
		else if (s.equals("image"))
			res = DATATYPE_IMAGE;
		else if (s.equals("binary"))
			res = DATATYPE_BINARY;
		return res;
	}
	
	public String GetDataType(int t) {
		String res = null;
		switch (t) {
		case DATATYPE_FLOAT:
			res = new String("float"); break;
		case DATATYPE_FLOAT_ARRAY:
			res = new String("floatarray"); break;
		case DATATYPE_STRING:
			res = new String("string"); break;
		case DATATYPE_IMAGE:
			res = new String("image"); break;
		case DATATYPE_BINARY:
			res = new String("binary"); break;
		}
		return res;
	}
	
	@Override
	public IBinder onBind(final Intent intent) {
//		return new LocalBinder<XMPPService>(this);
//		return null; // No binding provided
		return mFromModuleMessenger.getBinder();
	}

	@Override
	public void onCreate() {
		super.onCreate();

//		loadInstalledModuleMap();
		if (true) {
			{
				String name = new String("UI");
				InstalledModule m = new InstalledModule();
				m.name = name;
				InstalledModulePort p = new InstalledModulePort();
				p.name = "out";
				p.dataType = DATATYPE_STRING;
				m.portsOut.add(p);
				mInstalledModules.put(name, m);
			}
//			{
//				String name = new String("PictureSelectModule");
//				InstalledModule m = new InstalledModule();
//				m.name = name;
//				InstalledModulePort p = new InstalledModulePort();
//				p.name = "out";
//				p.dataType = DATATYPE_FLOAT_ARRAY;
//				m.portsOut.add(p);
//				mInstalledModules.put(name, m);
//			}
		}
		for (String n : mInstalledModules.keySet())
			Log.i(TAG, "name: " + n);
		for (InstalledModule m : mInstalledModules.values())
			Log.i(TAG, "Installed module: " + m.toString());
		
		
//		loadModuleMap();
		for (ModuleKey k : mModules.keySet())
			Log.i(TAG, "key:" + k.toString());
		for (Module m : mModules.values())
			Log.i(TAG, "Module: " + m.toString());

		{
			// The Xmpp module, which has dynamic ports, created by dodedodo server
			ModuleKey key = new ModuleKey("XMPP", 0);
			Module module = new Module();
			module.key = key;
			mModules.put(key, module);
		}
		
		if (false) {
			// This should normally be done by loading a file or by getting commands from the server
			{
				// The UI as module
				ModuleKey key = new ModuleKey("UI", 0);
				Module module = new Module();
				module.key = key;
				ModulePort portOut = new ModulePort();
				portOut.name = "out";
//				portOut.otherModuleName = "XMPP";
//				portOut.otherModuleId = 0;
//				portOut.otherPortName = "in.UI.0.out"; // Normally generated by server
				portOut.otherModuleName = "";
				portOut.otherModuleId = 0;
				portOut.otherPortName = ""; // Normally generated by server
				module.portsOut.put(portOut.name, portOut);
				mModules.put(key, module);
			}
			{
				// Picture select module
				ModuleKey key = new ModuleKey("PictureSelectModule", 0);
				Module module = new Module();
				module.key = key;
				ModulePort portOut = new ModulePort();
				portOut.name = "out";
//				portOut.otherModuleName = "XMPP";
//				portOut.otherModuleId = 0;
//				portOut.otherPortName = "in.PictureSelectModule.0.out"; // Normally generated by server
				portOut.otherModuleName = "";
				portOut.otherModuleId = 0;
				portOut.otherPortName = ""; // Normally generated by server
				module.portsOut.put(portOut.name, portOut);
				mModules.put(key, module);
			}
			{
				// The Xmpp module, which has dynamic ports, created by dodedodo server
				ModuleKey key = new ModuleKey("XMPP", 0);
				Module module = new Module();
				module.key = key;
				{
					ModulePort port = new ModulePort();
					port.name = "in.UI.0.out"; // Normally generated by server
//					port.otherModuleName = "UI";
//					port.otherModuleId = 0;
//					port.otherPortName = "out";
					module.portsIn.put(port.name, port);
				}
				{
					ModulePort port = new ModulePort();
					port.name = "in.PictureSelectModule.0.out"; // Normally generated by server
//					port.otherModuleName = "PictureSelectModule";
//					port.otherModuleId = 0;
//					port.otherPortName = "out";
					module.portsIn.put(port.name, port);
				}
				mModules.put(key, module);
			}
			for (ModuleKey k : mModules.keySet())
				Log.i(TAG, "key:" + k.toString());
			for (Module m : mModules.values())
				Log.i(TAG, "Module: " + m.toString());
		}
		
//        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

//		// Display a notification about us starting.
//        showNotification();
		doBindXmppService();
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId) {
		return Service.START_NOT_STICKY;
	}

	@Override
	public boolean onUnbind(final Intent intent) {
		return super.onUnbind(intent);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
//		// Cancel the persistent notification.
//        mNM.cancel(R.string.remote_service_started);

//		// Tell the user we stopped.
//        Toast.makeText(this, R.string.remote_service_stopped, Toast.LENGTH_SHORT).show();
		doUnbindXmppService();
		Log.i(TAG, "on destroy");
		
		storeModuleMap();
		storeInstalledModuleMap();
	}

	/** Handler of incoming messages from modules. */
	// TODO: give this class modulename and id variables, so that those don't have to be sent by the module
	class IncomingMsgHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_REGISTER:{
				ModuleKey key = new ModuleKey(msg.getData().getString("module"), msg.getData().getInt("id"));
				Log.i(TAG, "Registering module: " + key.toString() + " " + msg.replyTo);

				if (!mModules.containsKey(key)) {
					Log.i(TAG, "not registering module: not in mModules.");
					break;
				}
				
				Module module = mModules.get(key);
				module.messenger = msg.replyTo;
				
				// Try to connect all outgoing ports
				// TODO: only (re)connect ports of this module, not of all modules 
				connectAll();
				break;
			}
			case MSG_UNREGISTER:{
				Log.i(TAG, "module removed");

//				// Removal while iterating
//				Iterator<Entry<ModuleKey, Module>> it = mModules.entrySet().iterator();
//				while (it.hasNext()) {
//					Map.Entry<ModuleKey, Module> pairs = (Map.Entry<ModuleKey, Module>)it.next();
//					if (pairs.getValue().messenger != null && pairs.getValue().messenger.equals(msg.replyTo)) {
//						it.remove();
//					}
//				}
				
				// Better/easier:
//				ModuleKey key = new ModuleKey();
//				key.name = msg.getData().getString("module");
//				key.id = msg.getData().getInt("id");
//				mModules.remove(key);
				
				// Only set messenger(s) to null
				for (Module m : mModules.values()) {
					if (m.messenger != null && m.messenger.equals(msg.replyTo)) {
						m.messenger = null;
						for (ModulePort p : m.portsIn.values()) {
							p.messenger = null;
						}
					}
				}
				
				break;
			}
			case MSG_SET_MESSENGER:{
				ModuleKey key = new ModuleKey(msg.getData().getString("module"), msg.getData().getInt("id"));
				if (!mModules.containsKey(key))
					break;
				Module module = mModules.get(key);
				ModulePort port = module.portsIn.get(msg.getData().getString("port"));
				if (port != null) {
					port.messenger = msg.replyTo;
					Log.i(TAG, "set " + key.toString() + ":" + port.name + " to: " + port.messenger.toString());
					
					ModuleKey keyOut = new ModuleKey(port.otherModuleName, port.otherModuleId);
					connect(port.otherModuleDevice, keyOut, port.otherPortName, "local", key, port.name);
				}
				
//				connectAll();
				break;
			}
//			case MSG_PORT_DATA:{
//				Log.i(TAG, "port data");
//				for (int i=mModules.size()-1; i>=0; i--) {
//					if (mModules.get(i).messenger.equals(msg.replyTo)) {
//						if (mXmppService == null)
//							break;
//						Message xmppMsg = Message.obtain(null, XMPPService.MSG_SEND);
//						Bundle bundle = new Bundle();
//						bundle.putString("body", mModules.get(i).name + " " + msg.getData().getString("data"));
//						xmppMsg.setData(bundle);
//						msgSend(mXmppService, xmppMsg);
//						break;
//					}					
//				}
//				break;
//			}
			default:
				super.handleMessage(msg);
			}
		}
	}
	
	/** Handler of incoming messages from XMPP service. */
	class XmppMsgHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_SET_MESSENGER:{
//				Log.i(TAG, "set mXmppModuleMessenger: " + msg.replyTo.toString());
//				mXmppModuleMessenger = msg.replyTo;
				
				ModuleKey key = new ModuleKey("XMPP", 0);
				if (!mModules.containsKey(key))
					break;
				Module module = mModules.get(key);
				ModulePort port = module.portsIn.get(msg.getData().getString("port")); 
				if (port != null && (port.messenger == null || !port.messenger.equals(msg.replyTo))) {
					port.messenger = msg.replyTo;
					Log.i(TAG, "set XMPP[0]:" + port.name + " to: " + port.messenger.toString());
					
					ModuleKey keyOut = new ModuleKey(port.otherModuleName, port.otherModuleId);
					connect(port.otherModuleDevice, keyOut, port.otherPortName, "local", key, port.name);
				}
				
//				ModuleKey key = new ModuleKey();
//				key.name = msg.getData().getString("module");
//				key.id = msg.getData().getInt("id");
//				
//				Log.i(TAG, "setting " + key.name + "[" + key.id + "]:" + msg.getData().getString("port") + ".messenger to "
//						+ msg.replyTo.toString());
//				
//				if (mModules.containsKey(key)) {
//					Module module = mModules.get(key);
//					if (module.portsOut.containsKey(msg.getData().getString("port"))) {
//						module.portsOut.get(msg.getData().getString("port")).messenger = msg.replyTo;
//						
//						Log.i(TAG, "set " + key.name + "[" + key.id + "]:" + msg.getData().getString("port") + ".messenger to "
//								+ msg.replyTo.toString());
//						
//					}
//				}
				

				
				//connectAll();
				
//				Log.i(TAG, "connecting modules to " + mXmppModuleMessenger.toString());
//				Message messengerMsg = Message.obtain(null, MSG_SET_MESSENGER);
//				messengerMsg.replyTo = mXmppModuleMessenger;
//				msgBroadcast(messengerMsg);
				
				
				
				
				break;
			}
			case MSG_PORT_DATA:{
				if (msg.getData().getInt("datatype") == DATATYPE_STRING) {
					Log.i(TAG, "XMPP command: " + msg.getData().getString("data"));
					String[] words = msg.getData().getString("data").split(" ");
					if (words[1].equals("deploy")) {
						
//					ObjectMapper mapper = new ObjectMapper();						
//					try {
//						Log.i(TAG, "mModules as json: " + mapper.writeValueAsString(mModules));
//					} catch (JsonProcessingException e) {
//						Log.i(TAG, "JsonProcessingException: Could not write mModules as json: " + e.toString());
//					} catch (IOException e) {
//						Log.i(TAG, "IOException: Could not write mModules as json: " + e.toString());
//					}
					
						ObjectMapper mapper = new ObjectMapper();
						JsonNode rootNode;
						String json = new String(msg.getData().getString("data").substring(11)); // To remove "AIM deploy "
						try {
							rootNode = mapper.readTree(json);
							JsonNode androidNode = rootNode.path("android"); // Use .get() instead and check result for == null
							
							String moduleName = rootNode.path("name").textValue();
							String packageName = androidNode.path("package").textValue();
							String url = androidNode.path("url").textValue();
							
							Log.i(TAG, "Deploy: " + moduleName);
							Log.i(TAG, "package: " + packageName);
							Log.i(TAG, "url: " + url);
							
							InstalledModule m = new InstalledModule();
							m.name = moduleName;
							m.packageName = packageName;
							m.installUrl = url;
							
							JsonNode portsNode = rootNode.path("ports");
							Iterator<JsonNode> portIt = portsNode.elements();
							while (portIt.hasNext()) {
								JsonNode portNode = portIt.next();
								String portName = portNode.path("name").textValue();
								String portDir = portNode.path("dir").textValue();
								String portType = portNode.path("type").textValue();
								Log.i(TAG, "port: " + portName + " " + portDir + " " + portType);
								InstalledModulePort p = new InstalledModulePort();
								p.name = portName;
								p.dataType = GetDataType(portType);
								if (portDir.equals("in"))
									m.portsIn.add(p);
								else
									m.portsOut.add(p);
							}
							mInstalledModules.put(m.name, m);

						} catch (JsonProcessingException e) {
							Log.i(TAG, "JsonProcessingException: Could not read json: " + e.toString());
						} catch (IOException e) {
							Log.i(TAG, "IOException: Could not read json: " + e.toString());
						}
						
					}
					else if (words[1].equals("start")) {
						if (words.length != 4)
							break;
						int id;
						try {
							id = Integer.parseInt(words[3]);
						} catch (NumberFormatException e) {
							Log.i(TAG, "cannot convert " + words[3] + " to int");
							break;
						}
						ModuleKey key = new ModuleKey(words[2], id);
						Log.i(TAG, "Starting module: " + key);
						startModule(key);
					}
					else if (words[1].equals("stop")) {
						if (words.length != 4)
							break;
						int id;
						try {
							id = Integer.parseInt(words[3]);
						} catch (NumberFormatException e) {
							Log.i(TAG, "cannot convert " + words[3] + " to int");
							break;
						}
						ModuleKey key = new ModuleKey(words[2], id);
						Log.i(TAG, "Stopping module: " + key);
						stopModule(key);
					}
					else if (words[1].equals("connect")) {
						// 0AIM 1connect 2device 3module 4id 5port 6device 7module 8id 9port
						if (words.length != 10)
							break;
						int idOut, idIn;
						try {
							idOut = Integer.parseInt(words[4]);
							idIn = Integer.parseInt(words[8]);
						} catch (NumberFormatException e) {
							Log.i(TAG, "cannot convert " + words[4] + " or " + words[8] + " to int");
							break;
						}
						
						connect(words[2], new ModuleKey(words[3], idOut), words[5], words[6], new ModuleKey(words[7], idIn), words[9]);
						
//						// Check if port in exists
//						ModuleKey otherKey = new ModuleKey(words[5], otherId);
//						Module otherMod = mModules.get(otherKey);
//						if (otherMod == null) {
//							Log.i(TAG, "module " + otherKey.toString() + " isn't in mModules");
//							break;
//						}
//						ModulePort otherPort = otherMod.portsIn.get(words[7]);
//						if (otherPort == null) {
//							Log.i(TAG, "port " + words[7] + " isn't in " + otherKey.toString());
//							break;
//						}
//						// Set port in to port out
//						ModuleKey key = new ModuleKey(words[2], id);
//						Module m = mModules.get(key);
//						if (m != null) {
//							ModulePort p = m.portsOut.get(words[4]);
//							if (p != null) {
//								p.otherModuleName = words[5];
//								p.otherModuleId = otherId;
//								p.otherPortName = words[7];
//								
//								otherPort.otherModuleName = words[2];
//								otherPort.otherModuleId = id;
//								otherPort.otherPortName = words[4];
//								
//								// TODO: only this new connection
//								getMessengers();
//							}
//							else
//								Log.i(TAG, "port " + words[4] + " isn't in " + key.toString());
//						}
//						else
//							Log.i(TAG, "module " + key.toString() + " isn't in mModules");
					}
					else if (words[1].equals("uninstall")) {
						
					}
				}
			}
//			case XMPPService.MSG_RECEIVE:{
//				Log.i(TAG, "xmpp msg received");
//				Message portMsg = Message.obtain(null, MsgService.MSG_PORT_DATA);
//				Bundle bundle = new Bundle();
//				bundle.putString("data", msg.getData().getString("body"));
//				portMsg.setData(bundle);
//				msgBroadcast(portMsg);
//				break;
//			}
			
			
			default:
				super.handleMessage(msg);
			}
		}
	}
	
	private void msgSend(Messenger messenger, Message msg) {
//		if (messenger == null || msg == null)
//			return;
		Log.i(TAG, "msgSend to: " + messenger.toString());
		try {
			//msg.replyTo = mMessenger;
			messenger.send(msg);
		} catch (RemoteException e) {
			// do nothing?
		}
	}
	
//	private void msgBroadcast(Message msg) {
//		for (int i=mModules.size()-1; i>=0; i--) {
//			try {
//				mModules.get(i).messenger.send(msg);
//			} catch (RemoteException e) {
//				// The client is dead: remove it from the list.
//				// We are going through the list from back to front, so this is safe to do inside the loop.
//				mModules.remove(i);
//			}
//		}
//	}
	
	
//  How to iterate map safe for removals
//	public static void printMap(Map mp) {
//	    Iterator it = mp.entrySet().iterator();
//	    while (it.hasNext()) {
//	        Map.Entry pairs = (Map.Entry)it.next();
//	        System.out.println(pairs.getKey() + " = " + pairs.getValue());
//	        it.remove(); // avoids a ConcurrentModificationException
//	    }
//	}
	
	// This should either start an activity or a service.
	private void startModule(ModuleKey key) {

		InstalledModule installedMod = mInstalledModules.get(key.mName);
		if (installedMod == null) {
			Log.i(TAG, "Not installed: " + key.toString());
			return;
		}
		
		if (!mModules.containsKey(key)) {

			Intent intent = new Intent();
			intent.setAction(Intent.ACTION_RUN);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			intent.addCategory(Intent.CATEGORY_DEFAULT);
//			String packageName = "com.example." + key.mName.toLowerCase(Locale.US);
			intent.setPackage(installedMod.packageName);
			if (!isCallable(intent)) {
				Log.i(TAG, "Cannot start module " + key.toString() + ". No such package: " + installedMod.packageName);
				return;
			}

			Module m = new Module();
			m.key = key;
			for (InstalledModulePort p : installedMod.portsIn) {
				ModulePort port = new ModulePort();
				port.name = p.name;
//				port.otherModuleDevice = "";
//				port.otherModuleName = "";
//				port.otherModuleId = 0;
//				port.otherPortName = "";
				m.portsIn.put(p.name, port);
			}
			for (InstalledModulePort p : installedMod.portsOut) {
				ModulePort port = new ModulePort();
				port.name = p.name;
//				port.otherModuleDevice = "";
//				port.otherModuleName = "";
//				port.otherModuleId = 0;
//				port.otherPortName = "";
				m.portsOut.put(p.name, port);
			}
			mModules.put(key, m);
			
			startActivity(intent);
				
		}
	}
	
	private boolean isCallable(Intent intent) {
	    List<ResolveInfo> list = getPackageManager().queryIntentActivities(intent, 
	        PackageManager.MATCH_DEFAULT_ONLY);
	    if (list == null)
	    	return false;
	    return list.size() > 0;
	}
	
	private void stopModule(ModuleKey key) {
		// TODO: remove the module from mModules
		Module m = mModules.get(key);
		if (m != null)
		{
			Message messengerMsg = Message.obtain(null, MSG_STOP);
			msgSend(m.messenger, messengerMsg);
		}
		else
			Log.i(TAG, "Cannot stop module " + key + ": not in modules list.");
	}
	
	private void setMessenger(ModuleKey keyIn, String portIn) {
		Module mIn = mModules.get(keyIn);
		if (mIn == null) {
			Log.i(TAG, "module " + keyIn.toString() + " isn't in mModules");
			return;
		}
		ModulePort pIn = mIn.portsIn.get(portIn);
		if (pIn == null) {
			Log.i(TAG, "port " + portIn + " isn't in " + keyIn.toString());
			return;
		}
		ModuleKey keyOut = new ModuleKey(pIn.otherModuleName, pIn.otherModuleId);
		Module mOut = mModules.get(keyOut);
		if (mOut != null) {
			ModulePort pOut = mOut.portsOut.get(pIn.otherPortName);
			if (pOut != null) {
		
				pOut.messenger = pIn.messenger; 
				if (pOut.messenger != null) {
					Log.i(TAG, "set " + keyOut.toString() + ":" + pIn.otherPortName + ".messenger"
							+ " to " + keyIn.toString() + ":" + portIn + ".messenger"
							+ " " + pOut.messenger
							);
					Message messengerMsg = Message.obtain(null, MSG_SET_MESSENGER);
					Bundle bundle = new Bundle();
					bundle.putString("port", pIn.otherPortName);
					messengerMsg.setData(bundle);
					messengerMsg.replyTo = pOut.messenger;
					msgSend(mOut.messenger, messengerMsg);
				}
			}
		}
	}
	
	private void connect(String deviceOut, ModuleKey keyOut, String portNameOut, String deviceIn, ModuleKey keyIn, String portNameIn) {
		
		Module mOut;
		ModulePort pOut;
		Module mIn;
		ModulePort pIn;
		
		// Get correct module out
		if (!deviceOut.equals("local")) {
			keyOut.mName = "XMPP";
			keyOut.mId = 0;
		}
		mOut = mModules.get(keyOut);
		if (mOut == null) {
			Log.i(TAG, "module " + keyOut.toString() + " isn't in mModules");
			return;
		}
		
		// Get correct port out
		if (!deviceOut.equals("local")) {
			portNameOut = "out." + keyIn.mName + "." + keyIn.mId + "." + portNameIn;
			pOut = new ModulePort();
			pOut.name = portNameOut;
			mOut.portsOut.put(portNameOut, pOut);
		}
		else {
			pOut = mOut.portsOut.get(portNameOut);
			if (pOut == null) {
				Log.i(TAG, "port " + portNameOut + " isn't in " + keyOut.toString());
				return;
			}
		}
		
		// Get correct module in
		if (!deviceIn.equals("local")) {
			keyIn.mName = "XMPP";
			keyIn.mId = 0;
		}
		mIn = mModules.get(keyIn);
		if (mIn == null) {
			Log.i(TAG, "module " + keyIn.toString() + " isn't in mModules");
			return;
		}
		
		// Get correct port in
		if (!deviceIn.equals("local")) {
			portNameIn = "in." + keyOut.mName + "." + keyOut.mId + "." + portNameOut;
			pIn = new ModulePort();
			pIn.name = portNameIn;
			mIn.portsIn.put(portNameIn, pIn);
		}
		else {
			pIn = mIn.portsIn.get(portNameIn);
			if (pIn == null) {
				Log.i(TAG, "port " + portNameIn + " isn't in " + keyIn.toString());
				return;
			}
		}
		
		pOut.otherModuleDevice = deviceIn;
		pOut.otherModuleName = keyIn.mName;
		pOut.otherModuleId = keyIn.mId;
		pOut.otherPortName = portNameIn;

		pIn.otherModuleDevice = deviceOut;
		pIn.otherModuleName = keyOut.mName;
		pIn.otherModuleId = keyOut.mId;
		pIn.otherPortName = portNameOut;

		pOut.messenger = pIn.messenger; 
		if (pOut.messenger != null) {
			Log.i(TAG, "set " + keyOut.toString() + ":" + portNameOut + ".messenger"
					+ " to " + keyIn.toString() + ":" + portNameIn + ".messenger"
					+ " " + pOut.messenger
					);
			Message messengerMsg = Message.obtain(null, MSG_SET_MESSENGER);
			Bundle bundle = new Bundle();
			bundle.putString("port", portNameOut);
			messengerMsg.setData(bundle);
			messengerMsg.replyTo = pOut.messenger;
			msgSend(mOut.messenger, messengerMsg);
		}
		else {
			// TODO: only this new connection
			getMessengers();
		}
	}
	
	// Loop all modules and try to connect their ports
	private void connectAll() {
		Log.i(TAG, "connectAll");
		ModuleKey otherKey = new ModuleKey("", 0);
		for (Module module : mModules.values()) {

			Log.i(TAG, "module " + module.key.toString());
			
			if (module.messenger == null) {
				Log.i(TAG, "module.messenger == null");
				continue;
			}
			
			
			// Connect all incoming ports
			for (ModulePort port : module.portsIn.values()) {
//				ModuleKey otherKey = new ModuleKey();
				otherKey.mName = port.otherModuleName;
				otherKey.mId = port.otherModuleId;
				
//				Log.i(TAG, "in port " + port.name + " other module: " + otherKey.mName + "[" + otherKey.mId + "]:" + port.otherPortName);
				Log.i(TAG, "in port " + port.toString());
				
				if (mModules.containsKey(otherKey)) {
					Module otherMod = mModules.get(otherKey);
					
					if (otherMod.messenger == null)
						continue;
					
					if (otherMod.portsIn.containsKey(port.otherPortName)) {
						port.messenger = otherMod.portsIn.get(port.otherPortName).messenger;
						if (port.messenger != null) {
							Log.i(TAG, "set " + module.key.toString() + ":" + port.name + ".messenger"
									+ " to " + otherKey.toString() + ":" + port.otherPortName + ".messenger"
									+ " " + port.messenger
									);
							// Only have to SET_MESSENGER at out ports
//							Message messengerMsg = Message.obtain(null, MSG_SET_MESSENGER);
//							Bundle bundle = new Bundle();
//							bundle.putString("port", port.name);
//							messengerMsg.setData(bundle);
//							messengerMsg.replyTo = port.messenger;
//							msgSend(module.messenger, messengerMsg);
						}
					}
				}
			}
			
			// Connect all outgoing ports
			for (ModulePort port : module.portsOut.values()) {
//				ModuleKey otherKey = new ModuleKey();
				otherKey.mName = port.otherModuleName;
				otherKey.mId = port.otherModuleId;
				
				Log.i(TAG, "out port " + port.name + " other module: " + otherKey.toString() + ":" + port.otherPortName);
				
				if (mModules.containsKey(otherKey)) {
					Module otherMod = mModules.get(otherKey);
					if (otherMod.portsIn.containsKey(port.otherPortName)) {
						port.messenger = otherMod.portsIn.get(port.otherPortName).messenger; 
						if (port.messenger != null) {
							Log.i(TAG, "set " + module.key.mName + "[" + module.key.mId + "]:" + port.name + ".messenger"
									+ " to " + otherKey.mName + "[" + otherKey.mId + "]:" + port.otherPortName + ".messenger"
									+ " " + port.messenger
									);
							Message messengerMsg = Message.obtain(null, MSG_SET_MESSENGER);
							Bundle bundle = new Bundle();
							bundle.putString("port", port.name);
							messengerMsg.setData(bundle);
							messengerMsg.replyTo = port.messenger;
							msgSend(module.messenger, messengerMsg);
						}
					}
				}
			}
		}
	}
	
	private void getMessengers() {
		Log.i(TAG, "getMessengers()");
		ModuleKey key = new ModuleKey("XMPP", 0);
		Module module = mModules.get(key);
		if (module == null) {
			Log.i(TAG,"module XMPP is not in mModules");
			return;
		}
		for (ModulePort port : module.portsIn.values()) {
			if (port.otherModuleName == null || port.otherModuleId < 0 || port.otherPortName == null || port.otherModuleDevice == null)
				continue;
			
			Message msg = Message.obtain(null, MSG_GET_MESSENGER);
			msg.replyTo = mFromXmppMessenger;
			Bundle bundle = new Bundle();
//			bundle.putString("module", port.otherModuleName);
//			bundle.putInt("id", port.otherModuleId);
//			bundle.putString("port", port.otherPortName);
			String portName = new String();
			portName = "in." + port.otherModuleName + "." + port.otherModuleId + "." + port.otherPortName;
			bundle.putString("port", portName);
			
			ModuleKey otherKey = new ModuleKey(port.otherModuleName, port.otherModuleId);
			Module otherMod = mModules.get(otherKey);
			if (otherMod == null)
				continue;
			ModulePort otherPort = otherMod.portsOut.get(port.otherPortName);
			if (otherPort == null)
				continue;
			
			bundle.putString("device", otherPort.otherModuleDevice);
			msg.setData(bundle);
			msgSend(mToXmppMessenger, msg);
			Log.i(TAG,"Sent get messenger: " + portName + "/" + otherPort.otherModuleDevice);
		}
	}
	
	void storeModuleMap() {
//		for (Module m : mModules.values()) {
//			//Log.i(TAG, "Module: " + m.key.name + "[" + m.key.id + "]");
//			Log.i(TAG, "Module: " + m.toString());
//		}
		try {
			FileOutputStream fileOut = openFileOutput("moduleMap", Context.MODE_PRIVATE);
			ObjectOutputStream outStream = new ObjectOutputStream(fileOut);
			outStream.writeObject(mModules);
			outStream.close();
			fileOut.close();
		} catch (IOException e) {
			Log.i(TAG, "Failed to store module map: " + e.toString());
//			e.printStackTrace();
		}
	}
	
	void loadModuleMap() {
		try {
			FileInputStream fileIn = openFileInput("moduleMap");
			ObjectInputStream inStream = new ObjectInputStream(fileIn);
			mModules = (HashMap<ModuleKey, Module>) inStream.readObject();
			inStream.close();
			fileIn.close();
		} catch (IOException e) {
			Log.i(TAG, "Failed to load module map: " + e.toString());
		} catch (ClassNotFoundException e) {
			Log.i(TAG, "Failed to load module map: " + e.toString());
		}
	}
	
	void storeInstalledModuleMap() {
		for (String n : mInstalledModules.keySet())
			Log.i(TAG, "name: " + n);
		for (InstalledModule m : mInstalledModules.values())
			Log.i(TAG, "Installed module: " + m.toString());
		try {
			FileOutputStream fileOut = openFileOutput("installedModuleMap", Context.MODE_PRIVATE);
			ObjectOutputStream outStream = new ObjectOutputStream(fileOut);
			outStream.writeObject(mInstalledModules);
			outStream.close();
			fileOut.close();
		} catch (IOException e) {
			Log.i(TAG, "Failed to store installed module map: " + e.toString());
//			e.printStackTrace();
		}
	}
	
	void loadInstalledModuleMap() {
		try {
			FileInputStream fileIn = openFileInput("installedModuleMap");
			ObjectInputStream inStream = new ObjectInputStream(fileIn);
			mInstalledModules = (HashMap<String, InstalledModule>) inStream.readObject();
			inStream.close();
			fileIn.close();
		} catch (IOException e) {
			Log.i(TAG, "Failed to load installed module map: " + e.toString());
		} catch (ClassNotFoundException e) {
			Log.i(TAG, "Failed to load installed module map: " + e.toString());
		}
	}

	/** Class for interacting with the main interface of the service. */
	private ServiceConnection mXmppServiceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been established, giving us the service object
			// we can use to interact with the service.  We are communicating with our service through an IDL 
			// interface, so get a client-side representation of that from the raw service object.
			mToXmppMessenger = new Messenger(service);
			
			Log.i(TAG, "Attached to XmppService: " + mToXmppMessenger.toString());
			
			Message msg = Message.obtain(null, MSG_REGISTER);
			msg.replyTo = mFromXmppMessenger;
			msgSend(mToXmppMessenger, msg);
			
			msg = Message.obtain(null, MSG_XMPP_LOGIN);
			msgSend(mToXmppMessenger, msg);
			
			getMessengers();
			
//	        Toast.makeText(Binding.this, R.string.remote_service_connected, Toast.LENGTH_SHORT).show();
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been unexpectedly disconnected: its process crashed.
			mToXmppMessenger = null;

//	        Toast.makeText(Binding.this, R.string.remote_service_disconnected, Toast.LENGTH_SHORT).show();
			Log.i(TAG, "R.string.remote_service_disconnected");
		}
	};
	
	void doBindXmppService() {
		// Establish a connection with the service.  We use an explicit class name because there is no reason to be 
		// able to let other applications replace our component.
		//bindService(new Intent(this, XMPPService.class), mConnection, Context.BIND_AUTO_CREATE);
		
//		Intent intent = new Intent();
//		intent.setComponent(new ComponentName("com.example.dodedodo", "com.example.dodedodo.XMPPService"));
//		intent.setClassName("com.example.dodedodo", ".XMPPService");
//		bindService(intent, mXmppServiceConnection, Context.BIND_AUTO_CREATE);
		bindService(new Intent(this, XMPPService.class), mXmppServiceConnection, Context.BIND_AUTO_CREATE);
		Log.i(TAG, "binding to XMPP Service");
		mXmppServiceIsBound = true;
	}

	void doUnbindXmppService() {
		if (mXmppServiceIsBound) {
			// If we have received the service, and registered with it, then now is the time to unregister.
			if (mToXmppMessenger != null) {
				Message msg = Message.obtain(null, MSG_UNREGISTER);
				msg.replyTo = mFromModuleMessenger;
				msgSend(mToXmppMessenger, msg);
			}
			// Detach our existing connection.
			unbindService(mXmppServiceConnection);
			mXmppServiceIsBound = false;
//			mCallbackText.setText("Unbinding from service.");
		}
	}
	
//	protected void msgSend(Message msg) {
//		if (!mXmppServiceIsBound) {
//			Log.i(TAG, "Can't send message to service: not bound");
//			return;
//		}
//		try {
//			if (mMessenger == null)
//				Log.i(TAG, "mMessenger is null!");
//			msg.replyTo = mMessenger;
//			mXmppService.send(msg);
//			Log.i(TAG, "Sending msg to service.");
//		} catch (RemoteException e) {
//			Log.i(TAG, "Failed to send msg to service. " + e);
//			// There is nothing special we need to do if the service has crashed.
//		}
//	}


	/**
	 * Show a notification while this service is running.
	 */
	/*    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.remote_service_started);

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.stat_sample, text,
                System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, Controller.class), 0);

        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this, getText(R.string.remote_service_label),
                       text, contentIntent);

        // Send the notification.
        // We use a string id because it is a unique number.  We use it later to cancel.
        mNM.notify(R.string.remote_service_started, notification);
    }
	 */

}
package org.dobots.dodedodo;

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

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import android.app.ActivityManager;
import android.app.Service;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
//import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;


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
	
	public static final int MODULE_TYPE_UI = 0;
	public static final int MODULE_TYPE_BACKGROUND = 1;
	
	public String name; // Handy but double info
	public int version;
	public String git;
	public String packageName;
	public String installUrl;
	public int type; // UI or background
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
	private String mResource;
	private String mJid;
	private int mXmppConnectionStatus; // 0=connecting, -1=fail, 1=connected
	
	private Messenger mToXmppMessenger = null;
	private final Messenger mFromXmppMessenger = new Messenger(new XmppMsgHandler());
	private boolean mXmppServiceIsBound;
	
//	Messenger mXmppModuleMessenger = null;
	
	private final Messenger mFromModuleMessenger = new Messenger(new IncomingMsgHandler());
	
	
//	/** For showing and hiding our notification. */
//	NotificationManager mNM;
	/** Keeps track of all current registered clients. */
	
	private HashMap<String, InstalledModule> mInstalledModules = new HashMap<String, InstalledModule>();
	//ArrayList<Module> mModules = new ArrayList<Module>();
	private HashMap<ModuleKey, Module> mModules = new HashMap<ModuleKey, Module>();
	


	
	@Override
	public IBinder onBind(final Intent intent) {
//		return new LocalBinder<XMPPService>(this);
//		return null; // No binding provided
		return mFromModuleMessenger.getBinder();
	}

	@Override
	public void onCreate() {
		super.onCreate();

		mXmppConnectionStatus = 0;
		loadInstalledModuleMap();

		for (String n : mInstalledModules.keySet())
			Log.d(TAG, "name: " + n);
		for (InstalledModule m : mInstalledModules.values())
			Log.d(TAG, "Installed module: " + m.toString());
		
//		loadModuleMap();
		for (ModuleKey k : mModules.keySet())
			Log.d(TAG, "key:" + k.toString());
		for (Module m : mModules.values())
			Log.d(TAG, "Module: " + m.toString());

		{
			// The Xmpp module, which has dynamic ports, created by dodedodo server
			ModuleKey key = new ModuleKey("XMPP", 0);
			Module module = new Module();
			module.key = key;
			mModules.put(key, module);
		}
		{
			// The UI as module, has no port, since only the MsgService communicates with it.
			ModuleKey key = new ModuleKey("UI", 0);
			Module module = new Module();
			module.key = key;
			mModules.put(key, module);
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
		Log.d(TAG, "on destroy");
		
		storeModuleMap();
		storeInstalledModuleMap();
	}
	
	// Handler of incoming messages from modules.
	// TODO: give this class modulename and id variables, so that those don't have to be sent by the module
	class IncomingMsgHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case AimProtocol.MSG_REGISTER:{
				String packageName = msg.getData().getString("package");
				String moduleName = null;
				String moduleNameReported = msg.getData().getString("module");
				if (packageName == null) {
					Log.e(TAG, "error: msg_register: package=" + packageName + " module=" + moduleNameReported);
					break;
				}
				
				if (packageName.equals(getPackageName())) {
					moduleName = moduleNameReported;
				}
				else {
					for (InstalledModule m : mInstalledModules.values()) {
						if (m.packageName.equals(packageName)) {
							moduleName = m.name;
							break;
						}
					}
					if (moduleName == null) {
						Log.e(TAG, "error: msg_register not installed: package=" + packageName + " module=" + moduleNameReported);
						break;
					}

					// Extra check, can be removed?
					if (!moduleName.equals(moduleNameReported) && !moduleName.endsWith("/" + moduleNameReported)) {
						Log.e(TAG, "error msg_register module mismatch, reported: " + moduleNameReported + ", while name of " + packageName + " is: " + moduleName);
						break;
					}
				}
				
				ModuleKey key = new ModuleKey(moduleName, msg.getData().getInt("id"));
				Log.i(TAG, "Registering module: " + packageName + " " + key.toString() + " " + msg.replyTo);

				if (!mModules.containsKey(key)) {
					Log.i(TAG, "not registering module: not in mModules.");
					break;
				}
				
				Module module = mModules.get(key);
				module.messenger = msg.replyTo;
				
				if (key.mName == "UI") {
					syncUI();
				}
				else {
					sendCmdStatusStart(key, true);
					// Send status update to the UI
					updateUiNumModules();
					updateUiModule(key);
				}
				
				// Try to connect all outgoing ports
				// TODO: only (re)connect out ports of this module, not of all modules 
				connectAll();
				break;
			}
			case AimProtocol.MSG_UNREGISTER:{
				Log.i(TAG, "module unregistered");

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
				ModuleKey key = null;
				for (Module m : mModules.values()) {
					if (m.messenger != null && m.messenger.equals(msg.replyTo)) {
						m.messenger = null;
						key = m.key;
						for (ModulePort p : m.portsIn.values()) {
							p.messenger = null;
						}
						// TODO: check p.otherport ?
					}
				}
				
				
				// Send status update to the UI
				updateUiNumModules();
				if (key != null)
					updateUiModule(key);
				
				break;
			}
			case AimProtocol.MSG_SET_MESSENGER:{
				String packageName = msg.getData().getString("package");
				String moduleName = null;
				String moduleNameReported = msg.getData().getString("module");
				if (packageName == null) {
					Log.e(TAG, "error: msg_set_messenger: package=" + packageName + " module=" + moduleNameReported);
					break;
				}
				if (packageName.equals(getPackageName())) {
					moduleName = moduleNameReported;
				}
				else {
					for (InstalledModule m : mInstalledModules.values()) {
						if (m.packageName.equals(packageName)) {
							moduleName = m.name;
							break;
						}
					}
					if (moduleName == null) {
						Log.e(TAG, "error: msg_set_messenger not installed: " + packageName + " " + moduleNameReported);
						break;
					}

					// Extra check, can be removed?
					if (!moduleName.equals(moduleNameReported) && !moduleName.endsWith("/" + moduleNameReported)) {
						Log.e(TAG, "error: msg_set_messenger module mismatch, reported: " + moduleNameReported + ", while name of " + packageName + " is: " + moduleName);
						break;
					}
				}
				
				ModuleKey key = new ModuleKey(moduleName, msg.getData().getInt("id"));
				if (!mModules.containsKey(key))
					break;
				Module module = mModules.get(key);
				ModulePort port = module.portsIn.get(msg.getData().getString("port"));
				if (port != null) {
					port.messenger = msg.replyTo;
					Log.i(TAG, "msg_set_messenger: set " + key.toString() + ":" + port.name + " to: " + port.messenger.toString());
					
					ModuleKey keyOut = new ModuleKey(port.otherModuleName, port.otherModuleId);
					connect(port.otherModuleDevice, keyOut, port.otherPortName, "local", key, port.name);
				}
				else
					Log.w(TAG, "msg_set_messenger: - port not found: " + msg.getData().getString("port"));
				
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
			case AimProtocol.MSG_USER_LOGIN:{
				Log.i(TAG, "login");
				if (mToXmppMessenger != null) {
					Message msgLogin = Message.obtain(null, AimProtocol.MSG_XMPP_LOGIN);
					msgSend(mToXmppMessenger, msgLogin);
				}
				break;
			}
			default:
				super.handleMessage(msg);
			}
		}
	}
	
	// Handler of incoming messages from XMPP service.
	class XmppMsgHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case AimProtocol.MSG_XMPP_LOGGED_IN:{
				// At this point, out jid is known
				SharedPreferences sharedPref = getSharedPreferences("org.dobots.dodedodo.login", Context.MODE_PRIVATE);
				mResource = sharedPref.getString("resource", "");
				mJid = sharedPref.getString("jid", "");
				mXmppConnectionStatus = 1;
				
				// Send status update to the UI
				Log.d(TAG, "logged in, call updateUiConnectionStatus()");
				updateUiConnectionStatus();
				
				break;
			}
			case AimProtocol.MSG_XMPP_CONNECT_FAIL:{
				mXmppConnectionStatus = -1;
				
				// Send status update to the UI
				Log.d(TAG, "connect fail, call updateUiConnectionStatus()");
				updateUiConnectionStatus();
				
				break;
			}
			case AimProtocol.MSG_SET_MESSENGER:{
//				Log.i(TAG, "set mXmppModuleMessenger: " + msg.replyTo.toString());
//				mXmppModuleMessenger = msg.replyTo;
				
				ModuleKey key = new ModuleKey("XMPP", 0);
				if (!mModules.containsKey(key))
					break;
				Module module = mModules.get(key);
				ModulePort port = module.portsIn.get(msg.getData().getString("port"));
				if (port == null)
					Log.w(TAG, "set messenger - port not found: " + msg.getData().getString("port"));
				if (port != null && (port.messenger == null || !port.messenger.equals(msg.replyTo))) {
					port.messenger = msg.replyTo;
					Log.i(TAG, "set XMPP[0]:" + port.name + " to: " + port.messenger.toString());
					
					ModuleKey keyOut = new ModuleKey(port.otherModuleName, port.otherModuleId);
					
					String deviceIn = msg.getData().getString("otherDevice");
					String moduleIn = msg.getData().getString("otherModule");
					int idIn = msg.getData().getInt("otherID");
					String portIn = msg.getData().getString("otherPort");
					ModuleKey keyIn = new ModuleKey(moduleIn, idIn);
					connect(port.otherModuleDevice, keyOut, port.otherPortName, deviceIn, keyIn, portIn);
//					connect(port.otherModuleDevice, keyOut, port.otherPortName, "local", key, port.name);
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
			case AimProtocol.MSG_XMPP_MSG:{
				Log.i(TAG, "XMPP command: " + msg.getData().getString("body"));
				String[] words = msg.getData().getString("body").split(" ");
				if (words.length < 2)
					break;

				if (words[1].equals("deploy")) {	
					ObjectMapper mapper = new ObjectMapper();
					JsonNode rootNode;
					String json = new String(msg.getData().getString("body").substring(11)); // To remove "AIM deploy "
					try {
						rootNode = mapper.readTree(json);
						JsonNode androidNode = rootNode.path("android"); // Use .get() instead and check result for == null
						
						// name can be in the form: someuser/somerepo/SomeModule
//						String[] moduleNameSplit = rootNode.path("name").textValue().split("/");
//						String moduleName = moduleNameSplit[moduleNameSplit.length-1];
						String moduleName = rootNode.path("name").textValue();
						String git = rootNode.path("git").textValue();
						String packageName = androidNode.path("package").textValue();
						String url = androidNode.path("url").textValue();
						String typeString = rootNode.path("type").textValue();
						int type;
						if (typeString.equals("UI"))
							type = InstalledModule.MODULE_TYPE_UI;
						else
							type = InstalledModule.MODULE_TYPE_BACKGROUND;
						
						Log.i(TAG, "Deploy: " + moduleName);
						Log.i(TAG, "package: " + packageName);
						Log.i(TAG, "url: " + url);
						Log.i(TAG, "type: " + type);
						
						InstalledModule m = new InstalledModule();
						m.name = moduleName;
						m.git = git;
						m.packageName = packageName;
						m.installUrl = url;
						m.type = type;
						
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
							p.dataType = AimProtocol.getDataType(portType);
							if (portDir.equals("in"))
								m.portsIn.add(p);
							else
								m.portsOut.add(p);
						}
						PackageManager packetMngr = getPackageManager();
						try {
							ApplicationInfo appInfo = packetMngr.getApplicationInfo(packageName, 0);
							Log.i(TAG, "Application " + packageName + " found: " + appInfo.name);
							mInstalledModules.put(m.name, m);
							sendCmdStatusDeploy(m, true);
							
						} catch(NameNotFoundException e) {
							Log.i(TAG, "Application not found: " + e);
							sendCmdStatusDeploy(m, false);
							Module ui = mModules.get(new ModuleKey("UI", 0));
							if (ui != null && ui.messenger != null) {
								Message msgInstall = Message.obtain(null, AimProtocol.MSG_NOT_INSTALLED);
								Bundle b = new Bundle();
								b.putString("package", packageName);
								b.putString("module", moduleName);
								b.putString("url", url);
								msgInstall.setData(b);
								msgSend(ui.messenger, msgInstall);
							}
							
						}

					} catch (JsonProcessingException e) {
						Log.e(TAG, "JsonProcessingException: Could not read json: " + e.toString());
					} catch (IOException e) {
						Log.e(TAG, "IOException: Could not read json: " + e.toString());
					}
					
				}
				else if (words[1].equals("start")) {
					if (words.length < 4)
						break;
					int id;
					try {
						id = Integer.parseInt(words[3]);
					} catch (NumberFormatException e) {
						Log.e(TAG, "cannot convert " + words[3] + " to int");
						break;
					}
					// name can be in the form: someuser/somerepo/SomeModule
//					String[] moduleNameSplit = words[2].split("/");
//					String moduleName = moduleNameSplit[moduleNameSplit.length-1];
					String moduleName = words[2];
					ModuleKey key = new ModuleKey(moduleName, id);
					Log.i(TAG, "Starting module: " + key);
					startModule(key);
				}
				else if (words[1].equals("stop")) {
					if (words.length == 3 && words[2].equals("all")) {
						stopAllModules();
						break;
					}
					
					
					if (words.length < 4)
						break;
					int id;
					try {
						id = Integer.parseInt(words[3]);
					} catch (NumberFormatException e) {
						Log.e(TAG, "cannot convert " + words[3] + " to int");
						break;
					}
					// name can be in the form: someuser/somerepo/SomeModule
//					String[] moduleNameSplit = words[2].split("/");
//					String moduleName = moduleNameSplit[moduleNameSplit.length-1];
					String moduleName = words[2];
					ModuleKey key = new ModuleKey(moduleName, id);
					Log.i(TAG, "Stopping module: " + key);
					stopModule(key);
				}
				else if (words[1].equals("connect")) {
					// 0AIM 1connect 2jid 3module 4id 5port 6jid 7module 8id 9port
					if (words.length < 10)
						break;
					int idOut, idIn;
					try {
						idOut = Integer.parseInt(words[4]);
						idIn = Integer.parseInt(words[8]);
					} catch (NumberFormatException e) {
						Log.e(TAG, "cannot convert " + words[4] + " or " + words[8] + " to int");
						break;
					}
					// name can be in the form: someuser/somerepo/SomeModule
//					String[] moduleNameSplit = words[3].split("/");
//					String moduleNameOut = moduleNameSplit[moduleNameSplit.length-1];
					String moduleNameOut = words[3];
//					moduleNameSplit = words[7].split("/");
//					String moduleNameIn = moduleNameSplit[moduleNameSplit.length-1];
					String moduleNameIn = words[7];
					
					connect(words[2], new ModuleKey(moduleNameOut, idOut), words[5], words[6], new ModuleKey(moduleNameIn, idIn), words[9]);
				}
				else if (words[1].equals("uninstall")) {
					if (words.length < 3)
						break;
					
					String name = words[2];
					InstalledModule m = mInstalledModules.get(name);
					if (m == null) {
						m = new InstalledModule();
						m.name = name;
					}
					sendCmdStatusUninstall(m, false);
				}
				else if (words[1].equals("list")) {
//						ObjectMapper mapper = new ObjectMapper();						
//						try {
//							Log.i(TAG, "mModules as json: " + mapper.writeValueAsString(mModules));
//						} catch (JsonProcessingException e) {
//							Log.i(TAG, "JsonProcessingException: Could not write mModules as json: " + e.toString());
//						} catch (IOException e) {
//							Log.i(TAG, "IOException: Could not write mModules as json: " + e.toString());
//						}
					
					ObjectMapper mapper = new ObjectMapper();
//						ObjectNode rootNode = mapper.createObjectNode();
					ArrayNode rootNode = mapper.createArrayNode();
					for (InstalledModule m : mInstalledModules.values()) {
						if (m.name.equals("UI")) // Don't show the UI as module
							continue;
						ObjectNode moduleNode = rootNode.addObject();
						moduleNode.put("name", m.name);
						String type;
						switch (m.type) {
						case InstalledModule.MODULE_TYPE_UI:
							type = "UI";
							break;
						//case InstalledModule.MODULE_TYPE_BACKGROUND:
						default:
							type = "Background";
						}
						moduleNode.put("type", type);
						moduleNode.put("git", m.git);
						ObjectNode androidNode = moduleNode.putObject("android");
						androidNode.put("package", m.packageName);
						androidNode.put("url", m.installUrl);
						ArrayNode portsNode = moduleNode.putArray("ports");
						for (InstalledModulePort p : m.portsOut) {
							ObjectNode portNode = portsNode.addObject();
							portNode.put("name", p.name);
							portNode.put("dir", "out");
							portNode.put("type", AimProtocol.getDataType(p.dataType));
						}
						for (InstalledModulePort p : m.portsIn) {
							ObjectNode portNode = portsNode.addObject();
							portNode.put("name", p.name);
							portNode.put("dir", "in");
							portNode.put("type", AimProtocol.getDataType(p.dataType));
						}
					}
					try {
						String json = mapper.writeValueAsString(rootNode);
						Message listMsg = Message.obtain(null, AimProtocol.MSG_XMPP_MSG);
						Bundle bundle = new Bundle();
						bundle.putString("jid", msg.getData().getString("jid"));
						bundle.putString("body", "AIM list_result " + json);
						listMsg.setData(bundle);
						listMsg.replyTo = mFromXmppMessenger;
						msgSend(mToXmppMessenger, listMsg);
						
					} catch (JsonMappingException e) {
						Log.e(TAG, "JsonMappingException: Could not map json: " + e.toString());
					} catch (JsonGenerationException e) {
						Log.e(TAG, "JsonGenerationException: Could not generatre json: " + e.toString());
					} catch (IOException e) {
						Log.e(TAG, "IOException: Could not write json: " + e.toString());
					}
					
				}
				else if (words[1].equals("status")) {
					String json = null;
					switch(words.length) {
					case 2:
						json = getStatus(null, 0, null);
						
						// Reply status of all modules
						break;
					case 3:
						// Reply status of a module
						break;
					case 4:
						// Reply status of a port of a module
						break;
						
					}
					if (json != null) {
						Message statusMsg = Message.obtain(null, AimProtocol.MSG_XMPP_MSG);
						Bundle bundle = new Bundle();
						bundle.putString("jid", msg.getData().getString("jid"));
						bundle.putString("body", "AIM status_result " + json);
						statusMsg.setData(bundle);
						statusMsg.replyTo = mFromXmppMessenger;
						msgSend(mToXmppMessenger, statusMsg);
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
		if (messenger == null || msg == null) {
			Log.e(TAG, "msgSend() - messenger or msg is null");
			return;
		}
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
			sendCmdStatusStart(key, false);
			Log.i(TAG, "Not installed: " + key.toString());
			return;
		}
		
//		if (!mModules.containsKey(key)) {
		Module module = mModules.get(key);
		if (module != null && module.messenger != null) {
			Log.i(TAG, "Module " + key.toString() + " already started!");
			return;
		}

		Intent intent = new Intent();
		intent.setAction(Intent.ACTION_RUN);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.addCategory(Intent.CATEGORY_DEFAULT);
		intent.putExtra("id", key.mId);
//		String packageName = "org.dobots." + key.mName.toLowerCase(Locale.US);
		intent.setPackage(installedMod.packageName);
		
		
		if ((installedMod.type == InstalledModule.MODULE_TYPE_UI && !isCallableActivity(intent))
		 || (installedMod.type == InstalledModule.MODULE_TYPE_BACKGROUND && !isCallableService(intent))) {
			sendCmdStatusStart(key, false);
			Log.i(TAG, "Cannot start module " + key.toString() + ". No such package: " + installedMod.packageName);
			return;
		}

		if (module == null) {
			module = new Module();
			module.key = key;
			for (InstalledModulePort p : installedMod.portsIn) {
				ModulePort port = new ModulePort();
				port.name = p.name;
//				port.otherModuleDevice = "";
//				port.otherModuleName = "";
//				port.otherModuleId = 0;
//				port.otherPortName = "";
				module.portsIn.put(p.name, port);
			}
			for (InstalledModulePort p : installedMod.portsOut) {
				ModulePort port = new ModulePort();
				port.name = p.name;
//				port.otherModuleDevice = "";
//				port.otherModuleName = "";
//				port.otherModuleId = 0;
//				port.otherPortName = "";
				module.portsOut.put(p.name, port);
			}
			mModules.put(key, module);
		}
		
		if (installedMod.type == InstalledModule.MODULE_TYPE_UI) {
			startActivity(intent);
		}
		else {
			startService(intent);
		}
	}
//	}
	
	private boolean isCallableActivity(Intent intent) {
	    List<ResolveInfo> list = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
	    if (list == null)
	    	return false;
	    return list.size() > 0;
	}
	
	private boolean isCallableService(Intent intent) {
		List<ResolveInfo> list = getPackageManager().queryIntentServices(intent,PackageManager.MATCH_DEFAULT_ONLY);
	    if (list == null)
	    	return false;
	    return list.size() > 0;
	}
	
	private void stopModule(ModuleKey key) {
		// TODO: remove the module from mModules
		Module m = mModules.get(key);
		if (m != null) {
			if (m.messenger != null) {
				Message messengerMsg = Message.obtain(null, AimProtocol.MSG_STOP);
				msgSend(m.messenger, messengerMsg);
			}
			mModules.remove(key); // TODO: remove or wait for unregister? <-- stop should be different than unregister!
			
			sendCmdStatusStop(key, true);
			// Send status update to the UI
			updateUiNumModules();
			updateUiModule(key);
		}
		else
			Log.i(TAG, "Cannot stop module " + key + ": not in modules list.");
	}
	
	private void stopAllModules() {
		List<ModuleKey> toStop = new ArrayList<ModuleKey>();
		for (ModuleKey k : mModules.keySet()) {
			if (k.mName.endsWith("Module"))
				toStop.add(k);
		}
		for (ModuleKey k : toStop)
			stopModule(k);
	}
	
//	private void setMessenger(ModuleKey keyIn, String portIn) {
//		Module mIn = mModules.get(keyIn);
//		if (mIn == null) {
//			Log.i(TAG, "module " + keyIn.toString() + " isn't in mModules");
//			return;
//		}
//		ModulePort pIn = mIn.portsIn.get(portIn);
//		if (pIn == null) {
//			Log.i(TAG, "port " + portIn + " isn't in " + keyIn.toString());
//			return;
//		}
//		ModuleKey keyOut = new ModuleKey(pIn.otherModuleName, pIn.otherModuleId);
//		Module mOut = mModules.get(keyOut);
//		if (mOut != null) {
//			ModulePort pOut = mOut.portsOut.get(pIn.otherPortName);
//			if (pOut != null) {
//		
//				pOut.messenger = pIn.messenger; 
//				if (pOut.messenger != null) {
//					Log.i(TAG, "set " + keyOut.toString() + ":" + pIn.otherPortName + ".messenger"
//							+ " to " + keyIn.toString() + ":" + portIn + ".messenger"
//							+ " " + pOut.messenger
//							);
//					Message messengerMsg = Message.obtain(null, AimProtocol.MSG_SET_MESSENGER);
//					Bundle bundle = new Bundle();
//					bundle.putString("port", pIn.otherPortName);
//					messengerMsg.setData(bundle);
//					messengerMsg.replyTo = pOut.messenger;
//					msgSend(mOut.messenger, messengerMsg);
//				}
//			}
//		}
//	}
	
	// TODO: get rid of duplicate code
	private void connect(String deviceOut, ModuleKey keyOut, String portNameOut, String deviceIn, ModuleKey keyIn, String portNameIn) {
		if (deviceOut == null || keyOut == null || portNameOut == null || deviceIn == null || keyIn == null || portNameIn == null) {
			Log.i(TAG, "connect() failed: one or more arguments are null");
			return;
		}
		if (deviceOut.equals(mJid + "/" + mResource))
			deviceOut = "local";
		if (deviceIn.equals(mJid + "/" + mResource))
			deviceIn = "local";
		
		Log.i(TAG, "connect() " + deviceOut + " " + keyOut.toString() + ":" + portNameOut + " to " + deviceIn + " " + keyIn.toString() + ":" + portNameIn);
		
		// 3 Options: (in = local, out = local), (in = local, out != local), (in != local, out = local) 
	
		ModuleKey xmppKey = new ModuleKey("XMPP", 0);
		Module xmppModule = mModules.get(xmppKey);
		
		if (deviceOut.equals("local")) {
			Module mOut = mModules.get(keyOut);
			if (mOut == null) {
				Log.i(TAG, "module " + keyOut.toString() + " isn't in mModules, start it first");
				return;
			}
			
			ModulePort pOut = mOut.portsOut.get(portNameOut);
			if (pOut == null) {
				Log.i(TAG, "port " + portNameOut + " isn't in " + keyOut.toString());
				return;
			}
			
			pOut.otherModuleDevice = deviceIn;
			pOut.otherModuleName = keyIn.mName;
			pOut.otherModuleId = keyIn.mId;
			pOut.otherPortName = portNameIn;
			
			// Case: (in = local, out = local)
			if (deviceIn.equals("local")) {
				Module mIn = mModules.get(keyIn);
				if (mIn == null) {
					Log.i(TAG, "module " + keyIn.toString() + " isn't in mModules");
					return;
				}
				ModulePort pIn = mIn.portsIn.get(portNameIn);
				if (pIn == null) {
					Log.i(TAG, "port " + portNameIn + " isn't in " + keyIn.toString());
					return;
				}
				pIn.otherModuleDevice = deviceOut;
				pIn.otherModuleName = keyOut.mName;
				pIn.otherModuleId = keyOut.mId;
				pIn.otherPortName = portNameOut;
				
				if (pIn.messenger != null && mOut.messenger != null) {
					pOut.messenger = pIn.messenger;
					Log.i(TAG, "set " + keyOut.toString() + ":" + portNameOut + ".messenger"
							+ " to " + keyIn.toString() + ":" + portNameIn + ".messenger"
							+ " " + pOut.messenger
							);
					Message messengerMsg = Message.obtain(null, AimProtocol.MSG_SET_MESSENGER);
					Bundle bundle = new Bundle();
					bundle.putString("port", portNameOut);
					messengerMsg.setData(bundle);
					messengerMsg.replyTo = pOut.messenger;
					msgSend(mOut.messenger, messengerMsg);
				}
				else {
					Log.d(TAG, "pIn.messenger=" + pIn.messenger + " mOut.messenger=" + mOut.messenger);
					// messenger will be set when module registers
					//getMessengers();
				}
				
			}
			
			// Case: (in != local, out = local)
			else {
				if (xmppModule == null) {
					Log.i(TAG, "xmppModule isn't in mModules");
					return;
				}
				String xmppPortName = "in." + keyOut.mName + "." + keyOut.mId + "." + portNameOut;
				ModulePort xmppPort = xmppModule.portsIn.get(xmppPortName);
				if (xmppPort == null) {
					xmppPort = new ModulePort();
					xmppPort.name = xmppPortName;
					xmppPort.otherModuleDevice = deviceOut;
					xmppPort.otherModuleName = keyOut.mName;
					xmppPort.otherModuleId = keyOut.mId;
					xmppPort.otherPortName = portNameOut;
					xmppModule.portsIn.put(xmppPortName, xmppPort);
				}
				if (xmppPort.messenger != null && mOut.messenger != null) {
					pOut.messenger = xmppPort.messenger;
					Log.i(TAG, "set " + keyOut.toString() + ":" + portNameOut + ".messenger"
							+ " to " + xmppKey.toString() + ":" + xmppPortName + ".messenger"
							+ " " + xmppPort.messenger
							);
					Message messengerMsg = Message.obtain(null, AimProtocol.MSG_SET_MESSENGER);
					Bundle bundle = new Bundle();
					bundle.putString("port", portNameOut);
					// Add extra info, so that the xmppService knows where to send it to
					bundle.putString("otherDevice", deviceIn);
					bundle.putString("otherModule", keyIn.mName);
					bundle.putInt("otherID", keyIn.mId);
					bundle.putString("otherPort", portNameIn);
					messengerMsg.setData(bundle);
					messengerMsg.replyTo = pOut.messenger;
					msgSend(mOut.messenger, messengerMsg);
				}
				else {
					// TODO: only this new connection
					getMessengers();
				}
			}
		}
		
		// Case: (in = local, out != local)
		else {
			if (!deviceIn.equals("local")) {
				Log.i(TAG, "Can't connect 2 non local modules!");
				return;
			}
			
			Module mIn = mModules.get(keyIn);
			if (mIn == null) {
				Log.i(TAG, "module " + keyIn.toString() + " isn't in mModules, start it first");
				return;
			}
			
			ModulePort pIn = mIn.portsIn.get(portNameIn);
			if (pIn == null) {
				Log.i(TAG, "port " + portNameIn + " isn't in " + keyIn.toString());
				return;
			}
			
			pIn.otherModuleDevice = deviceOut;
			pIn.otherModuleName = keyOut.mName;
			pIn.otherModuleId = keyOut.mId;
			pIn.otherPortName = portNameOut;
			
			String xmppPortName = "out." + keyIn.mName + "." + keyIn.mId + "." + portNameIn;
			//String xmppPortName = XMPPService.
			ModulePort xmppPort = xmppModule.portsOut.get(xmppPortName);
			if (xmppPort == null) {
				xmppPort = new ModulePort();
				xmppPort.name = xmppPortName;
				xmppPort.otherModuleDevice = deviceIn;
				xmppPort.otherModuleName = keyIn.mName;
				xmppPort.otherModuleId = keyIn.mId;
				xmppPort.otherPortName = portNameIn;
				xmppModule.portsOut.put(xmppPortName, xmppPort);
			}
			if (pIn.messenger != null && mToXmppMessenger != null) {
				xmppPort.messenger = pIn.messenger;
				Log.i(TAG, "set " + xmppKey.toString() + ":" + xmppPort + ".messenger"
						+ " to " + keyIn.toString() + ":" + portNameIn + ".messenger"
						+ " " + pIn.messenger
						);
				Message messengerMsg = Message.obtain(null, AimProtocol.MSG_SET_MESSENGER);
				Bundle bundle = new Bundle();
				bundle.putString("port", xmppPortName);
				// Add extra info, so that the xmppService knows where the messages come from
				bundle.putString("otherDevice", deviceOut);
				bundle.putString("otherModule", keyOut.mName);
				bundle.putInt("otherID", keyOut.mId);
				bundle.putString("otherPort", portNameOut);
				messengerMsg.setData(bundle);
				messengerMsg.replyTo = xmppPort.messenger;
				msgSend(mToXmppMessenger, messengerMsg);
			}
			else {
				// messenger will be set when module registers
//				getMessengers();
			}
		}
	}
	
	// Loop all modules and try to connect their ports
	// TODO: get rid of duplicate code
	private void connectAll() {
		Log.i(TAG, "connectAll");
		ModuleKey otherKey = new ModuleKey("", 0);
		
		ModuleKey xmppKey = new ModuleKey("XMPP" , 0);
		Module xmppModule = mModules.get(xmppKey);
		
		for (Module module : mModules.values()) {

			Log.i(TAG, "module " + module.key.toString());
			
			if (module.messenger == null) {
				Log.i(TAG, "module.messenger == null");
				continue;
			}
			
			// Connect all outgoing ports
			for (ModulePort port : module.portsOut.values()) {
				
				if (port.otherModuleDevice == null || port.otherModuleName == null || port.otherModuleId < 0 || port.otherPortName == null)
					continue;
				
				// Case: out != local, in = local
				if (module.key.mName.equals("XMPP")) { // Can't happen? As XMPP.messenger is always null
					otherKey.mName = port.otherModuleName;
					otherKey.mId = port.otherModuleId;
					Log.i(TAG, "out port " + port.name + " other module: " + otherKey.toString() + ":" + port.otherPortName);
					Module otherMod = mModules.get(otherKey);
					if (otherMod != null) {
						ModulePort pIn = otherMod.portsIn.get(port.otherPortName);
						if (pIn != null) {
							port.messenger = pIn.messenger;
							if (port.messenger != null) {
								Log.i(TAG, "set " + module.key.mName + "[" + module.key.mId + "]:" + port.name + ".messenger"
										+ " to " + otherKey.mName + "[" + otherKey.mId + "]:" + port.otherPortName + ".messenger"
										+ " " + port.messenger
										);
								Message messengerMsg = Message.obtain(null, AimProtocol.MSG_SET_MESSENGER);
								Bundle bundle = new Bundle();
								bundle.putString("port", port.name);
								// Add extra info, so that the xmppService knows where the messages come from
								bundle.putString("otherDevice", pIn.otherModuleDevice);
								bundle.putString("otherModule", pIn.otherModuleName);
								bundle.putInt("otherID", pIn.otherModuleId);
								bundle.putString("otherPort", pIn.otherPortName);
								messengerMsg.setData(bundle);
								messengerMsg.replyTo = port.messenger;
//								msgSend(module.messenger, messengerMsg);
								msgSend(mToXmppMessenger, messengerMsg);
							}
						}
					}					
				}
				
				// Case: out = local, in = local
				else if (port.otherModuleDevice.equals("local")) {
					otherKey.mName = port.otherModuleName;
					otherKey.mId = port.otherModuleId;
					Log.i(TAG, "out port " + port.name + " other module: " + otherKey.toString() + ":" + port.otherPortName);
					
					Module otherMod = mModules.get(otherKey);
					if (otherMod != null) {
						ModulePort pIn = otherMod.portsIn.get(port.otherPortName);
						if (pIn != null) {
							port.messenger = pIn.messenger; 
							if (port.messenger != null) {
								Log.i(TAG, "set " + module.key.mName + "[" + module.key.mId + "]:" + port.name + ".messenger"
										+ " to " + otherKey.mName + "[" + otherKey.mId + "]:" + port.otherPortName + ".messenger"
										+ " " + port.messenger
										);
								Message messengerMsg = Message.obtain(null, AimProtocol.MSG_SET_MESSENGER);
								Bundle bundle = new Bundle();
								bundle.putString("port", port.name);
								messengerMsg.setData(bundle);
								messengerMsg.replyTo = port.messenger;
								msgSend(module.messenger, messengerMsg);
							}
						}
					}
				}
				
				// Case: out = local, in != local
				else {
					otherKey.mName = port.otherModuleName;
					otherKey.mId = port.otherModuleId;
					
					String xmppPortName = "in." + port.otherModuleName + "." + port.otherModuleId + "." + port.otherPortName;
					ModulePort xmppPort = xmppModule.portsIn.get(xmppPortName);
					if (xmppPort == null) {
						xmppPort = new ModulePort();
						xmppPort.name = xmppPortName;
						xmppPort.otherModuleDevice = port.otherModuleDevice;
						xmppPort.otherModuleName = otherKey.mName;
						xmppPort.otherModuleId = otherKey.mId;
						xmppPort.otherPortName = port.otherPortName;
						xmppModule.portsIn.put(xmppPortName, xmppPort);
						getMessengers(); // TODO: only this new connection
					}
					
					Log.i(TAG, "out port " + port.name + " other module: " + otherKey.toString() + ":" + port.otherPortName);
					
					port.messenger = xmppPort.messenger;
					if (port.messenger != null) {
						Log.i(TAG, "set " + module.key.mName + "[" + module.key.mId + "]:" + port.name + ".messenger"
								+ " to " + otherKey.mName + "[" + otherKey.mId + "]:" + port.otherPortName + ".messenger"
								+ " " + port.messenger
								);
						Message messengerMsg = Message.obtain(null, AimProtocol.MSG_SET_MESSENGER);
						Bundle bundle = new Bundle();
						bundle.putString("port", port.name);
//						// Add extra info, so that the xmppService knows where to send it to
//						bundle.putString("otherDevice", port.otherModuleDevice);
//						bundle.putString("otherModule", port.otherModuleName);
//						bundle.putInt("otherID", port.otherModuleId);
//						bundle.putString("otherPort", port.otherPortName);
						messengerMsg.setData(bundle);
						messengerMsg.replyTo = port.messenger;
						msgSend(module.messenger, messengerMsg);
					}
				}

			} // end port out loop
		} // end module loop
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
			
			if (port.messenger != null) // No need to get messenger again, right?
				continue;
			
			ModuleKey outKey = new ModuleKey(port.otherModuleName, port.otherModuleId);
			Module outModule = mModules.get(outKey);
			if (outModule == null)
				continue;
			ModulePort outPort = outModule.portsOut.get(port.otherPortName);
			if (outPort == null)
				continue;
			
			Message msg = Message.obtain(null, AimProtocol.MSG_GET_MESSENGER);
			msg.replyTo = mFromXmppMessenger;
			Bundle bundle = new Bundle();
			String portName = new String();
			portName = "in." + port.otherModuleName + "." + port.otherModuleId + "." + port.otherPortName;
			bundle.putString("port", portName);

			bundle.putString("otherDevice", outPort.otherModuleDevice);
			bundle.putString("otherModule", outPort.otherModuleName);
			bundle.putInt("otherID", outPort.otherModuleId);
			bundle.putString("otherPort", outPort.otherPortName);
			
			msg.setData(bundle);
			msgSend(mToXmppMessenger, msg);
			Log.i(TAG,"Sent get messenger: " + portName + " --> " + outPort.otherModuleDevice + " " +
					outPort.otherModuleName + "[" + outPort.otherModuleId + "]:" + outPort.otherPortName);
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
			Log.e(TAG, "Failed to store module map: " + e.toString());
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
			Log.e(TAG, "Failed to load module map: " + e.toString());
		} catch (ClassNotFoundException e) {
			Log.e(TAG, "Failed to load module map: " + e.toString());
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
			Log.e(TAG, "Failed to store installed module map: " + e.toString());
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
			Log.e(TAG, "Failed to load installed module map: " + e.toString());
		} catch (ClassNotFoundException e) {
			Log.e(TAG, "Failed to load installed module map: " + e.toString());
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
			
			Message msg = Message.obtain(null, AimProtocol.MSG_REGISTER);
			msg.replyTo = mFromXmppMessenger;
			msgSend(mToXmppMessenger, msg);
			
			msg = Message.obtain(null, AimProtocol.MSG_XMPP_LOGIN);
			msgSend(mToXmppMessenger, msg);
			
			getMessengers();
			
//	        Toast.makeText(Binding.this, R.string.remote_service_connected, Toast.LENGTH_SHORT).show();
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been unexpectedly disconnected: its process crashed.
			mToXmppMessenger = null;

//	        Toast.makeText(Binding.this, R.string.remote_service_disconnected, Toast.LENGTH_SHORT).show();
			Log.w(TAG, "R.string.remote_service_disconnected");
		}
	};
	
	void doBindXmppService() {
		// Establish a connection with the service.  We use an explicit class name because there is no reason to be 
		// able to let other applications replace our component.
		//bindService(new Intent(this, XMPPService.class), mConnection, Context.BIND_AUTO_CREATE);
		
//		Intent intent = new Intent();
//		intent.setComponent(new ComponentName("org.dobots.dodedodo", "org.dobots.dodedodo.XMPPService"));
//		intent.setClassName("org.dobots.dodedodo", ".XMPPService");
//		bindService(intent, mXmppServiceConnection, Context.BIND_AUTO_CREATE);
		bindService(new Intent(this, XMPPService.class), mXmppServiceConnection, Context.BIND_AUTO_CREATE);
		Log.i(TAG, "binding to XMPP Service");
		mXmppServiceIsBound = true;
	}

	void doUnbindXmppService() {
		if (mXmppServiceIsBound) {
			// If we have received the service, and registered with it, then now is the time to unregister.
			if (mToXmppMessenger != null) {
				Message msg = Message.obtain(null, AimProtocol.MSG_UNREGISTER);
				msg.replyTo = mFromModuleMessenger;
				msgSend(mToXmppMessenger, msg);
			}
			// Detach our existing connection.
			unbindService(mXmppServiceConnection);
			mXmppServiceIsBound = false;
//			mCallbackText.setText("Unbinding from service.");
		}
	}
	
	

////	void getPortStatus(ObjectNode n, ModulePort p) {
////		n.put("name", p.name);
////	}
//	String getPortStatus(Module m, ModulePort p) {
////		ModulePort p = m.portsIn.get(portName);
////		if (p == null) {
////			p = m.portsOut.get(portName);
////			if (p == null)
////				return "";
////		}
//		if (p.messenger != null)
//			return "connected";
//		else
//			return "disconnected";
//	}
//	
//	String getModuleStatus(String moduleName, int id) {
////        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
////        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
////            if (PictureTransformModuleService.class.getName().equals(service.service.getClassName())) {
////                return "running";
////            }
////        }
//		Module m = mModules.get(new ModuleKey(moduleName, id));
//		if (m == null)
//			return "stopped";
//		if (m.messenger == null)
//			return "stopped";
//		return "running";
//	}
	
	void getStatus(ModulePort p, ArrayNode parentNode) {
		ObjectNode portNode = parentNode.addObject();
		portNode.put("name", p.name);
		portNode.put("otherModuleDevice", p.otherModuleDevice);
		portNode.put("otherModule", p.otherModuleName);
		portNode.put("otherModuleId", p.otherModuleId);
		portNode.put("otherModulePort", p.otherPortName);
	}
	
	void getStatus(Module m, ArrayNode parentNode, String portName) {
		if (m.messenger == null)
			return;
		ObjectNode moduleNode = parentNode.addObject();
		moduleNode.put("name", m.key.mName);
		moduleNode.put("ID", m.key.mId);
		moduleNode.put("active", "1"); // TODO: check if active
		ArrayNode portsNode = moduleNode.putArray("ports");
		if (portName ==null) {
			for (ModulePort p : m.portsIn.values()) {
				getStatus(p, portsNode);
			}
			for (ModulePort p : m.portsOut.values()) {
				getStatus(p, portsNode);
			}
		}
		else {
			ModulePort p = m.portsIn.get(portName);
			if (p == null)
				p = m.portsOut.get(portName);
			if (p != null)
				getStatus(p, portsNode);
		}
	}
	
	String getStatus(String moduleName, int id, String portName) {
		ObjectMapper mapper = new ObjectMapper();
		ArrayNode rootNode = mapper.createArrayNode();
		if (moduleName == null) {		
			for (Module m : mModules.values()) {
				getStatus(m, rootNode, null);
			}
		}
		else {
//			id = 0; // TODO: should be able to have multiple ids
			Module m = mModules.get(new ModuleKey(moduleName, id));
			if (m != null) {
				getStatus(m, rootNode, portName);
			}
		}
		
		
		String json = null;
		try {
			json = mapper.writeValueAsString(rootNode);
			
		} catch (JsonMappingException e) {
			Log.e(TAG, "JsonMappingException: Could not map json: " + e.toString());
		} catch (JsonGenerationException e) {
			Log.e(TAG, "JsonGenerationException: Could not generatre json: " + e.toString());
		} catch (IOException e) {
			Log.e(TAG, "IOException: Could not write json: " + e.toString());
		}
		return json;
	}
	
	
	private void sendCmdStatusDeploy(InstalledModule m, boolean success) {
		sendCmdStatus("deploy", success, m.name + " " + m.version);
	}
	
	private void sendCmdStatusUninstall(InstalledModule m, boolean success) {
		sendCmdStatus("uninstall", success, m.name);
	}
	
	private void sendCmdStatusStart(ModuleKey k, boolean success) {
		sendCmdStatus("start", success, k.mName + " " + k.mId);
	}
	
	private void sendCmdStatusStop(ModuleKey k, boolean success) {
		sendCmdStatus("stop", success, k.mName + " " + k.mId);
	}
	
	
	private void sendCmdStatusConnect(String deviceOut, ModuleKey keyOut, String portNameOut, String deviceIn, ModuleKey keyIn, String portNameIn, boolean success) {
		String devOut = deviceOut, devIn = deviceIn;
		if (devOut.equals("local"))
			devOut = mJid + "/" + mResource;
		if (devIn.equals("local"))
			devOut = mJid + "/" + mResource;
		sendCmdStatus("connect", success, devOut + " " + keyOut.mName + " " + keyOut.mId + " " + portNameOut + " " + devIn + " " + keyIn.mName + " " + keyIn.mId + " " + portNameIn);		
	}
	
	private void sendCmdStatusDisconnect(String device, ModuleKey key, String portName, boolean success) {
		String dev = device;
		if (dev.equals("local"))
			dev = mJid + "/" + mResource;
		sendCmdStatus("disconnect", success, dev + " " + key.mName + " " + key.mId + " " + portName);
	}
	
	private void sendCmdStatus(String command, boolean success, String statusText) {
		Message statusMsg = Message.obtain(null, AimProtocol.MSG_XMPP_MSG);
		Bundle bundle = new Bundle();
		bundle.putString("jid", XMPPService.ADMIN_JID);
		String body = "AIM command_result " + command + " ";
		if (success)
			body += "ok";
		else
			body += "fail";
		body += " " + statusText;
		bundle.putString("body", body);
		statusMsg.setData(bundle);
		statusMsg.replyTo = mFromXmppMessenger;
		msgSend(mToXmppMessenger, statusMsg);
	}
	
	
	int getNumRunningModules() {
		int num=0;
		for (Module m : mModules.values())
			if (m.messenger != null && m.key.mName.endsWith("Module"))
				num++;
		return num;
	}
	
	
	private void updateUiConnectionStatus() {
		Module ui = mModules.get(new ModuleKey("UI", 0));
		if (ui != null && ui.messenger != null)
			updateUiConnectionStatus(ui.messenger);
	}
	
	private void updateUiConnectionStatus(Messenger messenger) {
		Message msgStatus;
		if (mXmppConnectionStatus == 0) {
			// Connecting..
		}
		else if (mXmppConnectionStatus < 0) {
			msgStatus = Message.obtain(null, AimProtocol.MSG_XMPP_CONNECT_FAIL);
			msgSend(messenger, msgStatus);
//			Log.d(TAG, "UI update: connect fail");
		}
		else {
			msgStatus = Message.obtain(null, AimProtocol.MSG_XMPP_LOGGED_IN);
			msgSend(messenger, msgStatus);
//			Log.d(TAG, "UI update: connected");
		}
	}
	
	private void updateUiNumModules() {
		Module ui = mModules.get(new ModuleKey("UI", 0));
		if (ui != null && ui.messenger != null)
			updateUiNumModules(ui.messenger);
	}
	
	private void updateUiNumModules(Messenger messenger) {
		Message msgStatus = Message.obtain(null, AimProtocol.MSG_STATUS_NUM_MODULES);
		Bundle b = new Bundle();
		b.putInt("numRunningModules", getNumRunningModules());
		msgStatus.setData(b);
		msgSend(messenger, msgStatus);
//		Log.d(TAG, "UI update: num modules");
	}
	
	private void updateUiModule(ModuleKey key) {
		Module ui = mModules.get(new ModuleKey("UI", 0));
		if (ui != null && ui.messenger != null)
			updateUiModule(ui.messenger, key);
	}
	
	private void updateUiModule(Messenger messenger, ModuleKey key) {
		if (!key.mName.endsWith("Module"))
			return;
		
		Module m = mModules.get(key);
		Message msgStatus;
		if (m != null && m.messenger != null)
			msgStatus = Message.obtain(null, AimProtocol.MSG_STATUS_STARTED_MODULE);
		else
			msgStatus = Message.obtain(null, AimProtocol.MSG_STATUS_STOPPED_MODULE);
		Bundle b = new Bundle();
		b.putString("module", key.mName);
		b.putInt("id", key.mId);
		if (mInstalledModules.containsKey(key.mName))
			b.putString("package", mInstalledModules.get(key.mName).packageName);
		msgStatus.setData(b);
		msgSend(messenger, msgStatus);
//		Log.d(TAG, "UI update: " + key.mName);
	}
	
	private void syncUI() {
		Module ui = mModules.get(new ModuleKey("UI", 0));
		if (ui == null || ui.messenger == null)
			return;

		if (mJid != null) {
			Message msgStatus = Message.obtain(null, AimProtocol.MSG_XMPP_LOGGED_IN);
			msgSend(ui.messenger, msgStatus);
		}
		
		updateUiNumModules(ui.messenger);
		
		for (ModuleKey k : mModules.keySet()) {
			updateUiModule(ui.messenger, k);
		}
	}
	
	
	
	private void stopService() {
		Intent intent = new Intent();
		intent.setClassName("org.dobots.bmptojpgmodule", "org.dobots.bmptojpgmodule.aim.BmpToJpgModuleService");
		stopService(intent);
	}
	
    private boolean checkServiceRunning(String packageName) {
		Log.d(TAG, "Checking if service is running");
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (packageName.equals(service.service.getPackageName())) {
				service.service.getClassName();
//				service.
				
				return true;
			}
		}
		return false;
	}
	

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
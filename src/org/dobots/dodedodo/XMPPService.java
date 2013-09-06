package org.dobots.dodedodo;

import org.dobots.dodedodo.MsgService;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.SmackAndroid;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.filetransfer.FileTransferManager;
import org.jivesoftware.smackx.filetransfer.FileTransferNegotiator;
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer;
import org.jivesoftware.smackx.filetransfer.FileTransfer.Status;

//import android.app.Notification;
//import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
//import android.os.Handler;
import android.os.IBinder;
//import android.os.Message;
import android.os.Messenger;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

public class XMPPService extends Service {
	private static final String TAG = "XMPPService";
	private static final int PORT = 5222;
	
	private String mJid;

	/** Target we publish for clients to send messages to IncomingHandler. */
	final Messenger mMessenger = new Messenger(new MsgHandler());
	//final Messenger mModuleMessenger = new Messenger(new ModuleMsgHandler());
	
//	class PortKey {
//		public String moduleName;
//		public int moduleId;
//		public String portName;
//		public boolean equals(Object obj) {}
//		public int hashCode() {}
//	}
	
//	HashMap<PortKey, Messenger> mPortsIn = new HashMap<PortKey, Messenger>();
	
	class PortIn {
		public String mDevice;
		public Messenger mMessenger;
		public PortIn(String device, Messenger messenger) {
			mDevice = device;
			mMessenger = messenger;
		}
	}
	
	HashMap<String, PortIn> mPortsIn = new HashMap<String, PortIn>();

//	/** For showing and hiding our notification. */
//	NotificationManager mNM;
	
	Messenger mMsgService = null;
	
	ArrayList<Messenger> mPortsOut = new ArrayList<Messenger>();

/*	*//** Command to register a client. The Message's replyTo field must be a Messenger of the client *//*
	public static final int MSG_REGISTER_CLIENT = 1;

	*//** Command to unregister a client. The Message's replyTo field must be a Messenger of the client *//*
	public static final int MSG_UNREGISTER_CLIENT = 2;

	*//** Command to (connect and) login *//*
	public static final int MSG_LOGIN = 3;
	
	*//** Status message to client *//*
	public static final int MSG_LOGGED_IN = 4;
	
	*//** Status message to client *//*
	public static final int MSG_NOT_LOGGED_IN = 5;
	
	*//** Command to send an XMPP message. *//*
	public static final int MSG_SEND = 6;

	*//** Service received an XMPP message for the client. *//*
	public static final int MSG_RECEIVE = 7;*/
	
	


	private XMPPConnection mXmppConnection;
	private PacketListener mPacketListener;
//	private PacketCollector mPacketCollector;
	private FileTransferManager mFileTransferManager;

	@Override
	public IBinder onBind(final Intent intent) {
//		return new LocalBinder<XMPPService>(this);
//		return null; // No binding provided
		return mMessenger.getBinder();
	}

	@Override
	public void onCreate() {
		super.onCreate();

//        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

//		// Display a notification about us starting.
//        showNotification();

//		ConnectionConfiguration connConfig = new ConnectionConfiguration(HOST, PORT);
//		connConfig.setSASLAuthenticationEnabled(true);
//		connConfig.setReconnectionAllowed(true);
//		connConfig.setCompressionEnabled(true);
//		connConfig.setRosterLoadedAtLogin(false);
//		mXmppConnection = new XMPPConnection(connConfig);
		
		mPacketListener = new PacketListener() {
			public void processPacket(Packet packet) {
				Log.i(TAG, "Received msg: " + packet.toXML());
//				Log.i(TAG, "is Message: " + Message.class.isInstance(packet));

//				android.os.Message msg = android.os.Message.obtain(null, XMPPService.MSG_RECEIVE);
				android.os.Message msg = android.os.Message.obtain(null, MsgService.MSG_PORT_DATA);
				Bundle bundle = new Bundle();
				//bundle.putString("body", packet.getBody());
				//bundle.putString("body", packet.getProperty("body").toString());
				Message message = (Message) packet;
				String body = message.getBody();
//				String[] words = body.split(" ");
//				body.s
//				Log.i(TAG, "body words: " + words.toString());
				Log.i(TAG, "body: " + body);
				if (body.startsWith("AIM ")) {
					bundle.putInt("datatype", MsgService.DATATYPE_STRING);
					bundle.putString("data", body);
					msg.setData(bundle);
					msgSend(mMsgService, msg);
				}
				
//				bundle.putInt("datatype", MsgService.DATATYPE_STRING);
//				bundle.putString("data", packet.toXML());
//				msg.setData(bundle);
//				msgBroadcast(msg);
			}
		};
		
		
		
//		Connect();
		
//		// Start a conversation
//		Chat chat = mXmppConnection.getChatManager().createChat(mJid, new MyMessageListener());
//		try {
//			chat.sendMessage("howdy how");
//		} catch (XMPPException e) {
//			return;
//		}

	}

//	private static class MyPackeListener implements PacketListener {
//		public void processPacket(Packet packet) {
//			Log.i(TAG, "Received packet: " + packet.toXML());
//		}
//	}

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
		if (mXmppConnection != null)
			mXmppConnection.disconnect();

//		// Tell the user we stopped.
//        Toast.makeText(this, R.string.remote_service_stopped, Toast.LENGTH_SHORT).show();
		Log.i(TAG, "on destroy");
	}

	/** Handler of incoming messages from msg service. */
	class MsgHandler extends Handler {
		@Override
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case MsgService.MSG_REGISTER:
//				Log.i(TAG, "client added");
//				mClients.add(msg.replyTo);
				Log.i(TAG, "msgService registered");
				mMsgService = msg.replyTo;
//				android.os.Message messengerMsg = android.os.Message.obtain(null, MsgService.MSG_SET_MESSENGER);
//				messengerMsg.replyTo = mModuleMessenger;
//				msgSend(mMsgService, messengerMsg);
				break;
			case MsgService.MSG_UNREGISTER:
//				Log.i(TAG, "client removed");
//				mClients.remove(msg.replyTo);
				Log.i(TAG, "msgService unregistered");
				mMsgService = null;
				break;
			case MsgService.MSG_XMPP_LOGIN:
				Log.i(TAG, "login");
//				SharedPreferences sharedPref = getSharedPreferences("org.dobots.dodedodo.login", Context.MODE_PRIVATE);
//				String jid = sharedPref.getString("jid", "default@default.com");
//				String pw = sharedPref.getString("password", "default");
//				Log.i(TAG, "jid=" + jid + " pw=" + pw);
				xmppConnect();
				break;
			case MsgService.MSG_SET_MESSENGER:
				Log.i(TAG, "set messenger: " + msg.replyTo.toString());
				mPortsOut.add(msg.replyTo); // TODO: remove them again..
				break;
			case MsgService.MSG_GET_MESSENGER:
//				PortKey key = new PortKey();
//				key.moduleName = msg.getData().getString("module");
//				key.moduleId = msg.getData().getInt("id");
//				key.portName = msg.getData().getString("port");
				String key = msg.getData().getString("port");
				Messenger messenger = new Messenger(new ModuleMsgHandler(key));
				PortIn portIn = new PortIn(msg.getData().getString("device"), messenger);
				mPortsIn.put(key, portIn);
				Log.i(TAG, "get messenger " + key);
//				Log.i(TAG, "get messenger " + key.moduleName + "[" + key.moduleId + "]:" + key.portName + " " + messenger.toString());
				
				android.os.Message messengerMsg = android.os.Message.obtain(null, MsgService.MSG_SET_MESSENGER);
				messengerMsg.replyTo = messenger;
				messengerMsg.setData(msg.getData());
				msgSend(mMsgService, messengerMsg);
				
				break;
			case MsgService.MSG_PORT_DATA:
				Log.i(TAG, "port data on wrong messenger");
				break;
//			case MSG_SEND:
//				if (!xmppSend(mJid, msg.getData().getString("body"))) {
//					android.os.Message reply = android.os.Message.obtain(null, XMPPService.MSG_NOT_LOGGED_IN);
//					if (msg.replyTo == null) {
//						Log.i(TAG, "msg.replyTo is null!!");
//					}
//					msgSend(msg.replyTo, reply);
//				}
//				break;
			default:
				super.handleMessage(msg);
			}
		}
	}
	
	
	/** Handler of incoming messages from modules. */
	class ModuleMsgHandler extends Handler {
		String portName;
		public ModuleMsgHandler(String name) {
			portName = name;
		}
		@Override
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
//			case MsgService.MSG_REGISTER_MODULE:
//				Log.i(TAG, "module added");
//				mClients.add(msg.replyTo);
//				break;
//			case MsgService.MSG_UNREGISTER_MODULE:
//				Log.i(TAG, "client removed");
//				mClients.remove(msg.replyTo);
//				break;
			case MsgService.MSG_PORT_DATA:
				Log.i(TAG, "received data from " + portName + " datatype: " + msg.getData().getInt("datatype"));
				
				String xmppMsg = new String(portName);
				switch (msg.getData().getInt("datatype")) {
				case MsgService.DATATYPE_FLOAT:
					xmppMsg += " " + msg.getData().getFloat("data");
					break;
				case MsgService.DATATYPE_FLOAT_ARRAY:
					for (float f : msg.getData().getFloatArray("data")) {
						xmppMsg += " " + f;
					}
					break;
				case MsgService.DATATYPE_STRING:
					xmppMsg += " " + msg.getData().getString("data");
					break;
				case MsgService.DATATYPE_IMAGE:
					break;
				case MsgService.DATATYPE_BINARY:
					break;
				}
				
				String jid = new String(mJid + "/" + mPortsIn.get(portName).mDevice);
				Log.i(TAG, "Sending to " + jid + ": " + xmppMsg);
				if (!xmppSend(jid, xmppMsg)) {
//					android.os.Message reply = android.os.Message.obtain(null, XMPPService.MSG_NOT_LOGGED_IN);
//					if (msg.replyTo == null) {
//						Log.i(TAG, "msg.replyTo is null!!");
//					}
//					msgSend(msg.replyTo, reply);
					// Do nothing
				}
				
				
				
//				xmppSend(jid, xmppMsg);
				
				break;
			default:
				super.handleMessage(msg);
			}
		}
	}
	
	
	private void msgSend(Messenger messenger, android.os.Message msg) {
		try {
			messenger.send(msg);
		} catch (RemoteException e) {
			// do nothing?
		}
	}
	
	private void msgBroadcast(android.os.Message msg) {
		for (int i=mPortsOut.size()-1; i>=0; i--) {
			try {
				mPortsOut.get(i).send(msg);
			} catch (RemoteException e) {
				// The client is dead: remove it from the list.
				// We are going through the list from back to front, so this is safe to do inside the loop.
				mPortsOut.remove(i);
			}
		}
	}
	
	private boolean xmppSend(String jid, String body) {
		if (mXmppConnection == null)
			return false;
		if (!mXmppConnection.isConnected())
			return false;
		
		Message xmppMsg = new Message();
		xmppMsg.setTo(jid);
		xmppMsg.setBody(body);
		mXmppConnection.sendPacket(xmppMsg);
		return true;
	}
	
	private boolean xmppConnect() {
		Log.i(TAG, "Connecting..");
		
		SharedPreferences sharedPref = getSharedPreferences("org.dobots.dodedodo.login", Context.MODE_PRIVATE);
		String jid = sharedPref.getString("jid", "");
		String password = sharedPref.getString("password", "");
		Log.i(TAG, "jid=" + jid + " pw=" + password);
		
		if (TextUtils.isEmpty(jid) || TextUtils.isEmpty(password))
			return false;
		
		String[] split = jid.split("@");
		if (split.length != 2)
			return false;
		
		String username = split[0];
		String host = split[1];
		
		Log.i(TAG, "host=" + host + " user=" + username + " pw=" + password);
		
		ConnectionConfiguration connConfig = new ConnectionConfiguration(host, PORT);
		connConfig.setSASLAuthenticationEnabled(true);
		connConfig.setReconnectionAllowed(true);
		connConfig.setCompressionEnabled(true);
		connConfig.setRosterLoadedAtLogin(false);
//		connConfig.setDebuggerEnabled(true);
		mXmppConnection = new XMPPConnection(connConfig);
		
//		mXmppConnection.DEBUG_ENABLED = true;

		try {
			mXmppConnection.connect();
			if (!mXmppConnection.isAuthenticated())
				mXmppConnection.login(username, password, android.os.Build.MODEL);
		}
		catch (final XMPPException e) {
			Log.e(TAG, "Could not connect to Xmpp server.", e);
			return false;
		}
		
		if (!mXmppConnection.isConnected()) {
			Log.e(TAG, "Could not connect to the Xmpp server.");
			return false;
		}
		
		// We need this to get the file transfer work
		ServiceDiscoveryManager sdm = ServiceDiscoveryManager.getInstanceFor(mXmppConnection);
		if (sdm == null)
			sdm = new ServiceDiscoveryManager(mXmppConnection);
		sdm.addFeature("http://jabber.org/protocol/disco#info");
		sdm.addFeature("jabber:iq:privacy");
				
		mFileTransferManager = new FileTransferManager(mXmppConnection);
		FileTransferNegotiator.setServiceEnabled(mXmppConnection, true);
		

		Log.i(TAG, "Yey! We're connected to the Xmpp server!");
		mJid = jid;
		
//		android.os.Message loginMsg = android.os.Message.obtain(null, XMPPService.MSG_LOGGED_IN);
		android.os.Message loginMsg = android.os.Message.obtain(null, MsgService.MSG_XMPP_LOGGED_IN);
		//msgBroadcast(loginMsg);

//		mXmppConnection.getChatManager().addChatListener(new ChatManagerListener() {
//
//			@Override
//			public void chatCreated(final Chat chat, final boolean createdLocally) {
//				if (!createdLocally) {
//					chat.addMessageListener(new MyMessageListener());
//				}
//			}
//		});


		
//		OutgoingFileTransfer transfer = mFileTransferManager.createOutgoingFileTransfer(mJid);
//		try {
//			transfer.sendFile(new File("/mnt/sdcard/DCIM/2011-11-27 02.11.06.jpg"), "test");
//		} catch (XMPPException e) {
//			// Do nothing?
//		}
		
		// TODO: seperate thread please! (maybe all services should be on a seperate thread, since they may interfere with the UI)

//		while (!transfer.isDone()) {
//			if (transfer.getStatus().equals(Status.error)) {
//				Log.i(TAG,"ERROR!!! " + transfer.getError());
//			} else if (transfer.getStatus().equals(Status.cancelled) || transfer.getStatus().equals(Status.refused)) {
//				Log.i(TAG,"Cancelled!!! " + transfer.getError());
//			}
//			try {
//				Thread.sleep(100L);
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//			}
//		}
//		if (transfer.getStatus().equals(Status.refused) || transfer.getStatus().equals(Status.error) || transfer.getStatus().equals(Status.cancelled)) {
//			Log.i(TAG,"refused cancelled error " + transfer.getError());
//		} else {
//			Log.i(TAG,"Success");
//		}
		

		// Accept all incoming chats
		// TODO: only allow certain JIDs
//		mXmppConnection.getRoster().setSubscriptionMode(Roster.SubscriptionMode.accept_all);



		// Warning: FromContainsFilter seems to use *<string>* for matching!
//		PacketFilter filter = new AndFilter(new PacketTypeFilter(Message.class), new FromContainsFilter(mJid));
		PacketFilter filter = new PacketTypeFilter(Message.class);
//		mPacketCollector = mXmppConnection.createPacketCollector(null); // Polling method

//		Log.i(TAG, "Sent presence: " + mXmppConnection.isSendPresence());
		mXmppConnection.addPacketListener(mPacketListener, filter);
//		mXmppConnection.addPacketListener(new MyPackeListener(), null);
		return true;
	}

//	public static String getThreadSignature() {
//		final Thread t = Thread.currentThread();
//		return new StringBuilder(t.getName()).append("[id=").append(t.getId()).append(", priority=")
//				.append(t.getPriority()).append("]").toString();
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
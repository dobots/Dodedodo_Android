package org.dobots.dodedodo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
//import android.app.Dialog;
//import android.app.DialogFragment;
//import android.support.v4.app.DialogFragment
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import org.dobots.dodedodo.R;

class ToInstallModule {
	public String mModuleName;
	public String mInstallUrl;
	public String mPackageName;
	
	public ToInstallModule(String name, String url, String packageName) {
		mModuleName = name;
		mInstallUrl = url;
		mPackageName = packageName;
	}
	
	public String toString() {
		return mModuleName;
	}
	
//	public boolean equals(Object obj) {
//		if (this == obj)
//			return true;
//		if (obj == null)
//			return false;
//		if (getClass() != obj.getClass())
//			return false;
//		UnInstalledModule i = (UnInstalledModule)obj;
//		if (mModuleName == null || i.mModuleName == null)
//			return false;
//		if (mModuleName.equals(i.mModuleName))
//			return true;
//		return false;
//	}
//	
//	public int hashCode() {
//		return mModuleName.hashCode();
//	}
}

class ModuleItem {
	public String mModuleName;
	public int mId;
	public String mPackageName;
	
	public ModuleItem(String name, int id, String packageName) {
		mModuleName = name;
		mId = id;
		mPackageName = packageName;
	}
	public ModuleItem(String name, int id) {
		mModuleName = name;
		mId = id;
		mPackageName = null;
	}
	
	// TODO: Add ID once supported
	public String toString() {
		String[] moduleNameSplit = mModuleName.split("/");
		return moduleNameSplit[moduleNameSplit.length-1];
//		return mModuleName;
	}
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ModuleItem i = (ModuleItem)obj;
		if (mModuleName == null || i.mModuleName == null)
			return false;
		if (mId == i.mId && mModuleName.equals(i.mModuleName))
			return true;
		return false;
	}
	public int hashCode() {
		return (mModuleName + mId).hashCode();
	}
}

public class MainActivity extends Activity {
	private static final String TAG = "MainActivity";
	private static final String MODULE_NAME = "UI";
	//	private boolean mBounded;
	//	private XMPPService mService;

	
	/** Flag indicating whether we have called bind on the service. */
	boolean mMsgServiceIsBound;
	Messenger mToMsgService = null;
	final Messenger mFromMsgService = new Messenger(new IncomingMsgHandler());
	
//	final Messenger mPortMessenger = new Messenger(new PortMsgHandler());
//	Messenger mPortOutMessenger = null;
	
	TextView mTextNotificationsView;
	TextView mTextHeaderView;
//	EditText mEditText;
//	Button mButtonSend;
//	Button mButtonClose;
	Button mButtonLogin;
	ListView mModuleListView;
//	ArrayList<String> mModuleListStrings;
//	ArrayAdapter<String> mModuleListAdapter;
	ArrayAdapter<ModuleItem> mModuleListAdapter;
	private HashMap<String, ToInstallModule> mToInstallModules = new HashMap<String, ToInstallModule>();
	String mStatus;

	// onCreate -> onStart -> onResume
	// onPause -> onResume
	// onPause -> onStop -> onRestart -> onStart -> onResume
	// onPause -> onStop -> onDestroy

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i(TAG,"onCreate");
		setContentView(R.layout.activity_main);
		
		mTextNotificationsView = (TextView) findViewById(R.id.textNotifications);
		mTextHeaderView = (TextView) findViewById(R.id.textHead);
//		mEditText = (EditText) findViewById(R.id.messageInput);
//		mButtonSend = (Button) findViewById(R.id.buttonSend);
//		mButtonClose = (Button) findViewById(R.id.buttonClose);
		mButtonLogin = (Button) findViewById(R.id.buttonLogin);
		mModuleListView = (ListView) findViewById(R.id.listView);
		
//		TextView textView = new TextView(this);
//		textView.setText("bla");
//		mModuleListView.addFooterView(textView);
//		mModuleListStrings = new ArrayList<String>(0);
//		mModuleListAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mModuleListStrings);
		
//		mModuleListAdapter = new ArrayAdapter<String>(this, R.layout.my_list_item);
		mModuleListAdapter = new ArrayAdapter<ModuleItem>(this, R.layout.my_list_item);
		mModuleListView.setAdapter(mModuleListAdapter);
//		mModuleListView.setOnItemLongClickListener(listener)
		mModuleListView.setOnItemClickListener(new OnItemClickListener() {
		    public void onItemClick(AdapterView parent, View v, int position, long id) {
		        // Do something in response to the click
		    	ModuleItem m = mModuleListAdapter.getItem(position);
		    	Log.i(TAG, "clicked position=" + position + " name=" + m.mModuleName + " package=" + m.mPackageName);
		    	if (m.mPackageName != null) {
		    		Intent intent = new Intent();
		    		intent.setAction(Intent.ACTION_MAIN);
		    		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		    		intent.addCategory(Intent.CATEGORY_DEFAULT);
		    		intent.setPackage(m.mPackageName);
		    		if (isCallableActivity(intent))
		    			startActivity(intent);
		    	}
		    }
		});
		
		mTextNotificationsView.setMovementMethod(LinkMovementMethod.getInstance());
		
		mButtonLogin.setOnClickListener(new View.OnClickListener() {
		    public void onClick(View v) {
		    	loginScreen();
		    }
		});
		
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		String jid = sharedPref.getString("jid", null);
		String pw = sharedPref.getString("password", null);
		if (jid == null || pw == null) {
			loginScreen();
		}
		
//		startService(new Intent(this, XMPPService.class));
		
		doBindService();
	}

	@Override
	public void onStart() {
		super.onStart();
		Log.i(TAG,"onStart");
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.i(TAG,"onResume");
		
		//SharedPreferences sharedPref = getSharedPreferences("org.dobots.dodedodo.login", Context.MODE_PRIVATE);
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		String jid = sharedPref.getString("jid", null);
		String pw = sharedPref.getString("password", null);
		if (jid == null || pw == null) {
			mStatus = "Please login";
			updateNotificationsText();
		}
		
		boolean screen = sharedPref.getBoolean("screen_on", false);
		if (screen) {
			getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		else {
			getWindow().clearFlags(LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.i(TAG,"onPause");
	}

	@Override
	public void onStop() {
		super.onStop();
		Log.i(TAG,"onStop");
	}
	
//	void onSaveInstanceState

	@Override
	protected void onDestroy() {
		super.onDestroy();
		Log.i(TAG, "onDestroy " + mMsgServiceIsBound);
		doUnbindService();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_settings:{
			// Show options activity
			Intent intent = new Intent(this, SettingsActivity.class);
			startActivityForResult(intent, SETTINGS_REPLY);
			return true;
		}
		case R.id.action_info:{
			// Show info activity
			Intent intent = new Intent(this, InfoActivity.class);
			startActivity(intent);
			return true;
		}
		case R.id.action_close:{
			// Ask to stop all modules and close dodedodo
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.dialog_close)
				.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						// Stop modules and close dodedodo
						Message msg = Message.obtain(null, AimProtocol.MSG_STOP);
//						Bundle bundle = new Bundle();
//						bundle.putString("package", getPackageName());
//						bundle.putString("module", MODULE_NAME);
//						bundle.putInt("id", 0);
//						msg.setData(bundle);
						msgSend(msg);
						
						finish();
					}
				})
				.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						// User cancelled the dialog
					}
				});
			builder.create().show();
			return true;
		}
		default:
			return super.onOptionsItemSelected(item);
		}
	}


	private ServiceConnection mMsgServiceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been established, giving us the service object
			// we can use to interact with the service.  We are communicating with our service through an IDL 
			// interface, so get a client-side representation of that from the raw service object.
			mToMsgService = new Messenger(service);
//			mCallbackText.setText("Attached to MsgService: " + mToMsgService.toString());
//			mTextNotifications.setText("Connected.");

			Message msg = Message.obtain(null, AimProtocol.MSG_REGISTER);
			Bundle bundle = new Bundle();
			bundle.putString("package", getPackageName());
			bundle.putString("module", MODULE_NAME);
			bundle.putInt("id", 0);
			msg.setData(bundle);
			msgSend(msg);
			
//	        Toast.makeText(Binding.this, R.string.remote_service_connected, Toast.LENGTH_SHORT).show();
			Log.i(TAG, "Connected to MsgService: " + mToMsgService.toString());
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been unexpectedly disconnected: its process crashed.
			mToMsgService = null;
			mStatus = "Disconnected.";
			updateNotificationsText();
			//mTextNotificationsView.setText("Disconnected.");

//	        Toast.makeText(Binding.this, R.string.remote_service_disconnected, Toast.LENGTH_SHORT).show();
			Log.i(TAG, "Disconnected from MsgService");
		}
	};

	// Handle messages from MsgService
	class IncomingMsgHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
//			case AimProtocol.MSG_SET_MESSENGER:
//				Log.i(TAG, "set port: " + msg.getData().getString("port") + " to: " + msg.replyTo.toString());
//				if (msg.getData().getString("port").equals("out"))
//					mPortOutMessenger = msg.replyTo;
//				break;
			case AimProtocol.MSG_XMPP_LOGGED_IN:
				mStatus = "Connected.";
				updateNotificationsText();
				//mTextNotificationsView.setText("Connected.");
				mButtonLogin.setText("Logout");
				break;
			case AimProtocol.MSG_XMPP_CONNECT_FAIL:
				mStatus =  "Failed to connect. Check if you are connected to the internet. Or maybe you used the wrong username or password?";
				updateNotificationsText();
				//mTextNotificationsView.setText("Failed to connect. Maybe you used the wrong username or password?");
				mButtonLogin.setText("Login");
				break;
			case AimProtocol.MSG_NOT_INSTALLED:{
				Log.i(TAG, "Not installed: " + msg.getData().getString("package"));
				
				String name = msg.getData().getString("module");
				String url = msg.getData().getString("url");
				String packageName = msg.getData().getString("package");
				
				mToInstallModules.put(name, new ToInstallModule(name, url, packageName));
				updateNotificationsText();

				break;
			}
			case AimProtocol.MSG_STATUS_NUM_MODULES:{
				int numRunningModules = msg.getData().getInt("numRunningModules", -1);
				if (numRunningModules > -1)
					mTextHeaderView.setText(numRunningModules + " modules running");
				break;
			}
			case AimProtocol.MSG_STATUS_STARTED_MODULE:{
//				mModuleListStrings.add(msg.getData().getString("module"));
//				mModuleListAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mModuleListStrings);
//				mModuleListAdapter.add(msg.getData().getString("module"));
				Bundle b = msg.getData();
				ModuleItem m = new ModuleItem(b.getString("module"), b.getInt("id"), b.getString("package"));
				mModuleListAdapter.add(m); // TODO: check if not already added
//				String packageName = msg.getData().getString("package", null);
//				mModuleListView.setAdapter(mModuleListAdapter);
				Log.i(TAG, "Added module: " + b.getString("module"));
				break;
			}
			case AimProtocol.MSG_STATUS_STOPPED_MODULE:{
//				mModuleListStrings.remove(msg.getData().getString("module"));
//				mModuleListAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mModuleListStrings);
				
//				mModuleListAdapter.remove(msg.getData().getString("module"));
				Bundle b = msg.getData();
//				mModuleListAdapter.
				ModuleItem m = new ModuleItem(b.getString("module"), b.getInt("id"));
				mModuleListAdapter.remove(m); // TODO: check if exists?
//				mModuleListView.setAdapter(mModuleListAdapter);
				Log.i(TAG, "Removed module: " + b.getString("module"));
				break;
			}
			
			default:
				super.handleMessage(msg);
			}
		}
	}
	
//	// Handle messages from a port in
//	class PortMsgHandler extends Handler {
//		@Override
//		public void handleMessage(Message msg) {
//			switch (msg.what) {
//			case MsgService.MSG_PORT_DATA:
//				Log.i(TAG, "Received:" + msg.getData().getString("data"));
//				mCallbackText.setText(msg.getData().getString("data"));
//				break;
//			default:
//				super.handleMessage(msg);
//			}
//		}
//	}

	void doBindService() {
		// Establish a connection with the service.  We use an explicit class name because there is no reason to be 
		// able to let other applications replace our component.
		bindService(new Intent(this, MsgService.class), mMsgServiceConnection, Context.BIND_AUTO_CREATE);
		mMsgServiceIsBound = true;
		mStatus = "Connecting..";
		updateNotificationsText();
		//mTextNotificationsView.setText("Connecting..");
	}

	void doUnbindService() {
		if (mMsgServiceIsBound) {
			// If we have received the service, and registered with it, then now is the time to unregister.
			if (mToMsgService != null) {
				Message msg = Message.obtain(null, AimProtocol.MSG_UNREGISTER);
				Bundle bundle = new Bundle();
				bundle.putString("module", MODULE_NAME);
				bundle.putInt("id", 0);
				msg.setData(bundle);
				msgSend(msg);
			}
			// Detach our existing connection.
			unbindService(mMsgServiceConnection);
			mMsgServiceIsBound = false;
			mStatus = "Disconnecting..";
			updateNotificationsText();
			//mTextNotificationsView.setText("Disconnecting..");
		}
	}

	private static final int LOGIN_REPLY = 0;
	private static final int SETTINGS_REPLY = 1;

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case LOGIN_REPLY:
				if (resultCode == RESULT_OK) {
					sendLogin();
				}
				break;
			case SETTINGS_REPLY:{
//				SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
//				Log.i(TAG, "debug_mode=" + sharedPref.getBoolean("debug_mode", false));
				break;
			}
		}
	}

	protected void msgSend(Message msg) {
		if (!mMsgServiceIsBound)
			return;
		if (mToMsgService == null) {
			Log.i(TAG, "mToMsgService == null");
			return;
		}
		try {
			msg.replyTo = mFromMsgService;
			mToMsgService.send(msg);
		} catch (RemoteException e) {
			Log.i(TAG, "failed to send msg to service. " + e);
			// There is nothing special we need to do if the service has crashed.
		}
	}
	
	protected void msgSend(Messenger messenger, Message msg) {
		if (messenger == null)
			return;
		try {
			msg.replyTo = mFromMsgService;
			messenger.send(msg);
		} catch (RemoteException e) {
			Log.i(TAG, "failed to send msg to service. " + e);
			// There is nothing special we need to do if the service has crashed.
		}
	}

//	public void sendMessage() {
//		// Do something in response to button click
//		String text = mEditText.getText().toString();
//		if (TextUtils.isEmpty(text))
//			return;
////		Message msg = Message.obtain(null, XMPPService.MSG_SEND);
////		Bundle bundle = new Bundle();
////		bundle.putString("body", text);
////		msg.setData(bundle);
////		msgSend(msg);
//		Message msg = Message.obtain(null, AimProtocol.MSG_PORT_DATA);
//		Bundle bundle = new Bundle();
//		bundle.putInt("datatype", AimProtocol.DATATYPE_STRING);
//		bundle.putString("data", text);
//		msg.setData(bundle);
//		msgSend(mPortOutMessenger, msg);
//		mEditText.getText().clear();
//	}
	
	public void updateNotificationsText() {
		// From http://stackoverflow.com/questions/2734270/how-do-i-make-links-in-a-textview-clickable
		
		//String link = new String(mTextNotificationsView.getText().toString());
		String link = new String(mStatus);
		if (!mToInstallModules.isEmpty()) {
			link += "<br>Please install:";
			for (ToInstallModule m : mToInstallModules.values()) {
				link += "<br>- <a href=\"" + m.mInstallUrl + "\">" + m.mModuleName + "</a>";
			}
			//link += "</ul>";
		}
		mTextNotificationsView.setText(Html.fromHtml(link));
//		mCallbackText.setMovementMethod(LinkMovementMethod.getInstance());
	}
	
	public void loginScreen() {
		Intent intent = new Intent(this, LoginActivity.class);
		startActivityForResult(intent, LOGIN_REPLY);
	}
	
	public void sendLogin() {
		Message msg = Message.obtain(null, AimProtocol.MSG_USER_LOGIN);
		msgSend(msg);
	}
	
	private boolean isCallableActivity(Intent intent) {
	    List<ResolveInfo> list = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
//	    List<ResolveInfo> list = getPackageManager().queryIntentActivities(intent, 0);
	    if (list == null)
	    	return false;
	    return list.size() > 0;
	}
}



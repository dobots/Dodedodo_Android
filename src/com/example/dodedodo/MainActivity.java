package com.example.dodedodo;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import org.dobots.dodedodo.R;

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
	Messenger mPortOutMessenger = null;
	
	TextView mCallbackText;
	EditText mEditText;
	Button mButtonSend;
	Button mButtonClose;
	Button mButtonLogin;


	// onCreate -> onStart -> onResume
	// onPause -> onResume
	// onPause -> onStop -> onRestart -> onStart -> onResume
	// onPause -> onStop -> onDestroy

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.i(TAG,"onCreate");
		setContentView(R.layout.activity_main);
		
		mCallbackText = (TextView) findViewById(R.id.messageOutput);
		mEditText = (EditText) findViewById(R.id.messageInput);
		mButtonSend = (Button) findViewById(R.id.buttonSend);
		mButtonClose = (Button) findViewById(R.id.buttonClose);
		mButtonLogin = (Button) findViewById(R.id.buttonLogin);
		
		mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
		    @Override
		    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		        if (actionId == R.id.sendMsg || actionId == EditorInfo.IME_ACTION_SEND) {
		            sendMessage();
		            return true;
		        }
		        return false;
		    }
		});
		
		mButtonSend.setOnClickListener(new View.OnClickListener() {
		    public void onClick(View v) {
		        sendMessage();
		    }
		});
		
		mButtonLogin.setOnClickListener(new View.OnClickListener() {
		    public void onClick(View v) {
		        login();
		    }
		});
		
		Intent intent = new Intent(this, LoginActivity.class);
		startActivityForResult(intent, LOGIN_REPLY);
		
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


	private ServiceConnection mMsgServiceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been established, giving us the service object
			// we can use to interact with the service.  We are communicating with our service through an IDL 
			// interface, so get a client-side representation of that from the raw service object.
			mToMsgService = new Messenger(service);
			mCallbackText.setText("Attached to MsgService: " + mToMsgService.toString());

			Message msg = Message.obtain(null, MsgService.MSG_REGISTER);
			Bundle bundle = new Bundle();
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
			mCallbackText.setText("Service disconnected.");

//	        Toast.makeText(Binding.this, R.string.remote_service_disconnected, Toast.LENGTH_SHORT).show();
			Log.i(TAG, "Disconnected from MsgService");
		}
	};

	// Handle messages from MsgService
	class IncomingMsgHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MsgService.MSG_SET_MESSENGER:
				Log.i(TAG, "set port: " + msg.getData().getString("port") + " to: " + msg.replyTo.toString());
				if (msg.getData().getString("port").equals("out"))
					mPortOutMessenger = msg.replyTo;
				break;
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
		mCallbackText.setText("Binding to service.");
	}

	void doUnbindService() {
		if (mMsgServiceIsBound) {
			// If we have received the service, and registered with it, then now is the time to unregister.
			if (mToMsgService != null) {
				Message msg = Message.obtain(null, MsgService.MSG_UNREGISTER);
				Bundle bundle = new Bundle();
				bundle.putString("module", MODULE_NAME);
				bundle.putInt("id", 0);
				msg.setData(bundle);
				msgSend(msg);
			}
			// Detach our existing connection.
			unbindService(mMsgServiceConnection);
			mMsgServiceIsBound = false;
			mCallbackText.setText("Unbinding from service.");
		}
	}

	private static final int LOGIN_REPLY = 0;

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case LOGIN_REPLY:
			if (resultCode == RESULT_OK) {
//				String jid = data.getExtras().getString("jid");
//				String pw = data.getExtras().getString("password");
//				SharedPreferences sharedPref = getSharedPreferences("com.example.dodedodo.login", Context.MODE_PRIVATE);
//				String jid = sharedPref.getString("jid", "default@default.com");
//				String pw = sharedPref.getString("password", "default");
//				Log.i(TAG, "jid=" + jid + " pw=" + pw);
				login();
			}
		}
	}

	protected void msgSend(Message msg) {
		if (!mMsgServiceIsBound)
			return;
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

	public void sendMessage() {
		// Do something in response to button click
		String text = mEditText.getText().toString();
		if (TextUtils.isEmpty(text))
			return;
//		Message msg = Message.obtain(null, XMPPService.MSG_SEND);
//		Bundle bundle = new Bundle();
//		bundle.putString("body", text);
//		msg.setData(bundle);
//		msgSend(msg);
		Message msg = Message.obtain(null, MsgService.MSG_PORT_DATA);
		Bundle bundle = new Bundle();
		bundle.putInt("datatype", MsgService.DATATYPE_STRING);
		bundle.putString("data", text);
		msg.setData(bundle);
		msgSend(mPortOutMessenger, msg);
		mEditText.getText().clear();
	}
	
	public void login() {
		Message msg = Message.obtain(null, MsgService.MSG_USER_LOGIN);
		msgSend(msg);
	}



}

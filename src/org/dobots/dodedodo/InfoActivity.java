package org.dobots.dodedodo;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.Menu;
import android.widget.TextView;

public class InfoActivity extends Activity {
	
	private SharedPreferences mSharedPref;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_info);
		
		TextView versionText = (TextView) findViewById(R.id.textViewVersion);
		TextView resourceText = (TextView) findViewById(R.id.textViewResource);
		TextView jidText = (TextView) findViewById(R.id.textViewJid);
		
		mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		
		PackageManager manager = this.getPackageManager();
		try {
			PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0);
			versionText.setText(info.versionName);
		} catch(PackageManager.NameNotFoundException e) {
			versionText.setText("error");
		}
		
//		SharedPreferences sharedPref = getSharedPreferences("org.dobots.dodedodo.login", Context.MODE_PRIVATE);
		String resource = mSharedPref.getString("resource", "");
		resourceText.setText(resource);
		
		String jid = mSharedPref.getString("jid", "");
		jidText.setText(jid);
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
//		getMenuInflater().inflate(R.menu.info, menu);
//		return true;
		return false;
	}

}

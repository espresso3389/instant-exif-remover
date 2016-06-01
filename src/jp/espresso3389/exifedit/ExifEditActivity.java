package jp.espresso3389.exifedit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class ExifEditActivity extends Activity {
	
	Handler mHandler = new Handler();
	final int REQCODE_ACTION_GET_CONTENT = 1214;
	final int REQCODE_ACTION_EDIT = 1215;
	AlertDialog.Builder mChooser = null;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }
    
    @Override
	protected void onResume() {
		super.onResume();

        mHandler.post(new Runnable() {

			public void run() {
				processIntent();
			}
        	
        });
    }

	/**
     * The main routine, which processes the intent received.
     */
    void processIntent() {
    	if (mChooser != null)
    		return;
    	
        Intent intent = getIntent();
        String action = intent.getAction();
        if (action.equals(Intent.ACTION_GET_CONTENT)) {
        	processActionGetContent(intent);
        } else if(action.equals(Intent.ACTION_SEND)) {
        	processActionSend(intent);
        } else if(action.equals(Intent.ACTION_EDIT)) {
        	processActionEdit(intent);
        }

    }
    
    void processActionGetContent(Intent intent) {
    	
    	// We should not use the original intent for ACTION_GET_CONTENT;
    	// otherwise, chooser dialog does not work properly.
    	Intent gcIntent = new Intent(Intent.ACTION_GET_CONTENT);
    	gcIntent.setType(intent.getType());
    	
    	showChooser(gcIntent, true, REQCODE_ACTION_GET_CONTENT);
    }
    
    void processActionSend(Intent intent) {
    	
    	Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
    	Intent sendIntent = new Intent(Intent.ACTION_SEND);
    	sendIntent.setType(intent.getType());
    	sendIntent.putExtra(Intent.EXTRA_STREAM, ExifEditContentProvider.createUriForContent(uri));
    	
    	showChooser(sendIntent, true, 0);
    }

    void processActionEdit(Intent intent) {
    	
    	/*
		Intent editIntent = new Intent(Intent.ACTION_EDIT);
		editIntent.setDataAndType(uri, intent.getType());
		
		showChooser(editIntent, true, REQCODE_ACTION_EDIT);
		*/
    	
    	final Uri orig = intent.getData();
		final File src = ExifEditUtils.getFileFromUri(this, orig);
		if (src == null) {
			setResult(RESULT_CANCELED, null);
	    	finish();
	    	return;
		}
		
		new AlertDialog.Builder(this)
			.setTitle(R.string.title_save)
			.setMessage(R.string.message_save)
			.setCancelable(true)
			.setPositiveButton(R.string.cap_overwrite, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					setResult(editJpegFile(orig, null) ? RESULT_OK : RESULT_CANCELED, null);
					finish();
					
				} })
			.setNeutralButton(R.string.cap_save_with_newname, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					setResult(editJpegFile(orig, ExifEditUtils.createNewFile(src)) ? RESULT_OK : RESULT_CANCELED, null);
					finish();
				} })
			.setOnCancelListener(new DialogInterface.OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					setResult(RESULT_CANCELED, null);
			    	finish();
				} })
			.show();
    }
    
    boolean editJpegFile(Uri orig, File dest) {
		Uri uri = ExifEditContentProvider.createUriForContent(orig);
    	
		boolean overwrite = false;
		if (dest == null) {
			dest = ExifEditUtils.getFileFromUri(this, orig);
			overwrite = true;
		}
		
    	File tmp = null;
		FileOutputStream fs = null;
		try {
			if (overwrite) {
				tmp = ExifEditUtils.createNewFile(dest);
				fs = new FileOutputStream(tmp);
			} else {
				fs = new FileOutputStream(dest);
			}
			ExifEditContentProvider.processJpegFile(this, fs, uri, ExifEditContentProvider.getProfessProfile(uri));
			fs.close();
			
			if (overwrite) {
				Log.i("processActionEdit", String.format("Overwriting %s...", dest));
				dest.delete();
				tmp.renameTo(dest);
			}
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			if (fs != null)
				try {
					fs.close();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			if (tmp != null)
				tmp.delete();
			return false;
		}
    }
    
	/**
     * Show chooser dialog without the app self.
     * @param intent Intent to choose the apps.
     * @param forResult Whether to wait for the result or not.
     * @param requestCode Request code sent to the app.
     */
    void showChooser(final Intent intent, final boolean forResult, final int requestCode) {
    	final ActivityListAdapter ala = new ActivityListAdapter(intent);
    	mChooser = new AlertDialog.Builder(ExifEditActivity.this);
    	mChooser.setTitle(getResources().getText(R.string.choose_app));
    	mChooser.setCancelable(true);
    	mChooser.setAdapter(ala, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
    		    dialog.dismiss();
    		    mChooser = null;
    		    Intent appIntent = ala.getIntent(which);
    		    if (forResult)
    		    	startActivityForResult(appIntent, requestCode);
    		    else
    		    	startActivity(appIntent);
			}
    	}).setOnCancelListener(new DialogInterface.OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				mChooser = null;
				onActivityResult(requestCode, RESULT_CANCELED, intent);
			}
		});
    	mChooser.create().show();
    }
    
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQCODE_ACTION_GET_CONTENT) {
			if (resultCode == RESULT_OK)
				setResult(RESULT_OK, processJpegFile(data));
			else
				setResult(resultCode, null);
	    	finish();
		} else if (requestCode == REQCODE_ACTION_EDIT) {
			if (resultCode == RESULT_OK)
				setResult(RESULT_OK, data);
			else
				setResult(resultCode, null);
			finish();
		} else {
			finish();
		}
	}
	
    Intent processJpegFile(Intent data) {
    	data.setData(ExifEditContentProvider.createUriForContent(data.getData()));
    	return data;
    }
    
    class ActivityListAdapter extends BaseAdapter {

    	Intent mIntent;
    	List<ResolveInfo> mItems = new ArrayList<ResolveInfo>();
    	LayoutInflater mInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    	PackageManager mPackMan = getPackageManager();

    	public ActivityListAdapter(Intent intent) {
    		mIntent = intent;

    		String packName = ExifEditActivity.this.getPackageName();
    		
    		for (ResolveInfo info : mPackMan.queryIntentActivities(mIntent, 0)) {
    			if (info.activityInfo.packageName.equals(packName))
    				continue;
    			mItems.add(info);
    		}
    		
    		// Sort in ascending order
    		Collections.sort(mItems, new Comparator<ResolveInfo>() {
				public int compare(ResolveInfo a, ResolveInfo b) {
					String as = a.loadLabel(mPackMan).toString();
					String bs = b.loadLabel(mPackMan).toString();
					return as.compareToIgnoreCase(bs);
				}});
    	}
    	
    	public Intent getIntent(int index) {
    		ResolveInfo info = mItems.get(index);
    		Intent newIntent = new Intent(mIntent);
    		newIntent.setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
    		return newIntent;
    	}
    	
    	public View getView(int position, View convertView, ViewGroup parent) {
    		
    		if (convertView == null)
    			convertView = mInflater.inflate(R.layout.actlistitem, null);
    		
    		ResolveInfo info = mItems.get(position);

    		TextView tv = (TextView)convertView.findViewById(R.id.title);
    		tv.setText(info.loadLabel(mPackMan));

    		ImageView imageView = (ImageView)convertView.findViewById(R.id.thumbnail);
    		imageView.setTag(position);
    		imageView.setImageDrawable(info.activityInfo.loadIcon(mPackMan));

    		return convertView;
    	}

    	public int getCount() {
    		return mItems.size();
    	}

    	public Object getItem(int position) {
    		return mItems.get(position);
    	}

    	public long getItemId(int position) {
    		// Basically, this method should return unique item identifier.
    		return position;
    	}
    }
    
}
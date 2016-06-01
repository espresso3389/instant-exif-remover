package jp.espresso3389.exifedit;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.util.Log;

public class ExifEditContentProvider extends ContentProvider {

	public static final String AUTHORITIES = "jp.espresso3389.exifedit.provider";
	private static final String URI_HEADER = "content://" + ExifEditContentProvider.AUTHORITIES + "/";
	private static final String DEFAULT_PROFILE = "default";
	
	public static Uri createUriForContent(Uri uri) {
    	String raw_uri = uri.toString();
    	Log.i("ExifEditContentProvider.createUriForContent", String.format("URI: %s", uri));
    	if (!raw_uri.startsWith("content://"))
    		return uri; // ???
    	
    	raw_uri = URI_HEADER + DEFAULT_PROFILE + "/" + raw_uri.substring(10);
    	Log.i("ExifEditContentProvider.createUriForContent", String.format("REDIRECT_URI: %s", raw_uri));
    	return Uri.parse(raw_uri);
	}

	/**
	 * Get the original content URI from ExifEditContentProvider URI.
	 * @param uri ExifEditContentProvider URI.
	 * @return The original content URI.
	 */
	public static Uri getOriginalContentUri(Uri uri) {
		String raw_uri = uri.toString();
		if (!raw_uri.startsWith(URI_HEADER))
			return null;
		
		raw_uri = raw_uri.substring(URI_HEADER.length());
		int spos = raw_uri.indexOf('/');
		if (spos < 0)
			return null;
		
		return Uri.parse("content://" + raw_uri.substring(spos + 1));
	}
	
	/**
	 * Get the process profile from ExifEditContentProvider URI.
	 * @param uri ExifEditContentProvider URI.
	 * @return Profile name.
	 */
	public static String getProfessProfile(Uri uri) {
		String raw_uri = uri.toString();
		if (!raw_uri.startsWith(URI_HEADER))
			return DEFAULT_PROFILE;
		
		raw_uri = raw_uri.substring(URI_HEADER.length());
		int spos = raw_uri.indexOf('/');
		if (spos < 0)
			return DEFAULT_PROFILE;
		
		return raw_uri.substring(0, spos);
	}
	
	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode)
			throws FileNotFoundException {
		
		final Uri imageUri = getOriginalContentUri(uri);
		if (imageUri == null)
			throw new FileNotFoundException(String.format("URI scheme not supported: %s", uri));
		if (!mode.equals("r"))
			throw new FileNotFoundException(String.format("Resource is readonly: %s", uri));
		
		final String profile = getProfessProfile(uri);
		
		/*
		// Efficient version, which uses pipe and background thread
		// At least, not work on 2.2 and it does not even work on 2.3 due to some issue on
		// writing to pipe; broken pipe issue on over 64K write with certain apps such as Gmail.
		final ParcelFileDescriptor[] pfds = ExifEditUtils.createPipe();
		if (pfds != null && pfds.length == 2) {
			try {
				(new Thread(new Runnable() {
					public void run() {
						processJpegFile(
							getContext(),
							new ParcelFileDescriptor.AutoCloseOutputStream(pfds[1]),
							imageUri,
							profile);
					}
				})).start();
				
				return pfds[0];
			}
			catch (Exception e) {
				e.printStackTrace();
				throw new FileNotFoundException(e.getMessage());
			}
		}
		*/
		
		// Conventional, the older way to use temporary file between apps.
		File tmp = null;
		FileOutputStream fs = null;
		try {
			tmp = File.createTempFile("work", ".tmp", getContext().getFilesDir());
			fs = new FileOutputStream(tmp);
			ParcelFileDescriptor pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY);
			tmp.delete();	
			processJpegFile(getContext(), fs, imageUri, getProfessProfile(uri));
			return pfd;

		} catch (IOException e) {
			e.printStackTrace();
			throw new FileNotFoundException(e.getMessage());
		} finally {
			if (fs != null)
				try {
					fs.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			if (tmp != null)
				tmp.delete();
		}
	}
	
	public static void processJpegFile(Context context, OutputStream os, Uri uri, String profile) {
		InputStream is = null;
		try {
			
			ParcelFileDescriptor pfdSrc = context.getContentResolver().openFileDescriptor(uri, "r");
			is = new BufferedInputStream(new ParcelFileDescriptor.AutoCloseInputStream(pfdSrc));
			os = new BufferedOutputStream(os);
			
			ExifEditJpegProcessor.processJpegFile(is, os, profile);
			
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				Log.i("ExifEditContentProvider.processJpegFile", "Closing pipe streams...");
				is.close();
				os.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		Log.i("ExifEditContentProvider.query", uri.toString());
		
		uri = getOriginalContentUri(uri);
		
		Cursor c = getContext().getContentResolver().query(uri, projection, selection, selectionArgs, sortOrder);
		if (c == null || !c.moveToFirst())
			return c;
		
		List<String> cols = new ArrayList<String>();
		List<Object> objs = new ArrayList<Object>();
		int idx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
		if(idx >= 0) {
			cols.add(MediaStore.MediaColumns.DISPLAY_NAME);
			objs.add(c.getString(idx));
		}
		
		cols.add("process_profile");
		objs.add(getProfessProfile(uri));

		MatrixCursor mc = new MatrixCursor(cols.toArray(new String[cols.size()]));
		mc.addRow(objs);
		
		return mc;
	}

	//
	// The methods below mean nothing for our purpose. Leave them as they are.
	//
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		Log.i("ExifEditContentProvider.delete", uri.toString());
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		Log.i("ExifEditContentProvider.getType", uri.toString());
		
		uri = getOriginalContentUri(uri);
		if (uri != null)
			return getContext().getContentResolver().getType(uri);
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		Log.i("ExifEditContentProvider.insert", uri.toString());
		return null;
	}

	@Override
	public boolean onCreate() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		Log.i("ExifEditContentProvider.update", uri.toString());
		return 0;
	}
	
}
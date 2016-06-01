package jp.espresso3389.exifedit;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;

public class ExifEditUtils {

	// TODO: keep compatibility with Android 2.2 (API level 8)
	static {
		initCompatibility();
	}
	
	static Method mParcelFileDescriptor_createPipe;
	
	static void initCompatibility() {
		try {
			mParcelFileDescriptor_createPipe = ParcelFileDescriptor.class.getMethod("createPipe");
		} catch (Exception e) {
			Log.i("initCompatibility", "We could not use ParcelFileDescriptor.pipe.");
			e.printStackTrace();
		}
	}
	
	/**
	 * Portable {@link ParcelFileDescriptor.createPipe} implementation.
	 * On Android 2.2, the function returns {@code null} and the code can determine whether to use
	 * the pipe/thread approach or the other (by temporary file).
	 * @return Array of {@link ParcelFileDescriptor}, which has two entries like the original {@link ParcelFileDescriptor.createPipe}.
	 * {@code null} if some errors.
	 */
	public static ParcelFileDescriptor[] createPipe() {
		/*
		if (mParcelFileDescriptor_createPipe != null) {
			try {
				return (ParcelFileDescriptor[])mParcelFileDescriptor_createPipe.invoke(null, (Object[])null);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		*/
		// FIXME: anyway, the pipe/thread approach does not work...
		return null;
	}
	
	/**
	 * Obtain {@link File} from {@link Uri}.
	 * @param context Context to resolve the URI.
	 * @param uri URI to resolve.
	 * @return {@link File} corresponding to the URI. {@code null} for failure.
	 */
	public static File getFileFromUri(Context context, Uri uri) {
		try {
			if (uri.getScheme().equals("file"))
				return new File(uri.getPath());
			
			Cursor c = context.getContentResolver().query(uri, null, null, null, null); 
			c.moveToFirst();
			File f = new File(c.getString(c.getColumnIndex(MediaStore.MediaColumns.DATA)));
			Log.i("getFileFromUri", String.format("%s -> %s", uri, f.getAbsoluteFile()));
			return f;
		}
		catch (Exception e) {
			Log.i("getFileFromUri", String.format("No file path for %s", uri));
			return null;
		}
	}
	
    public static File createNewFile(File orig) {
	    	try {
			String fn = orig.getName();
			String ext = ".tmp";
			int extPos = fn.lastIndexOf('.');
			if (extPos > 0) {
				ext = fn.substring(extPos);
				fn = fn.substring(0, extPos);
			}
	    	return File.createTempFile(fn, ext, orig.getParentFile());
    	} catch (IOException e) {
    		return null;
    	}
    }
}
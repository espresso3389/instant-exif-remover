package jp.espresso3389.exifedit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ExifEditJpegProcessor {

	static final int RST0 = 0xd0, RST7 = 0xd7;
	static final int SOI = 0xd8;
	static final int EOI = 0xd9;
	static final int SOS = 0xda;
	static final int APP0 = 0xe0, APP15 = 0xef;
	static final byte[] SIG_ICC_PROFILE = "ICC_PROFILE".getBytes();
	static final byte[] SIG_EXIF = "Exif".getBytes();
	
	static boolean checkDataPrefix(byte[] data, byte[] ref, boolean shouldZeroTerminated) {
		
		if (!shouldZeroTerminated && data.length < ref.length)
			return false;
		else if(shouldZeroTerminated && data.length <= ref.length)
			return false;
		
		for (int i = 0; i < ref.length; i++)
			if (ref[i] != data[i])
				return false;
		if(shouldZeroTerminated && data[ref.length] != 0)
			return false;
		return true;
	}
	
	static boolean interpretExif(byte[] data, int size, OutputStream os) {
		return false;
	}
	
	/**
	 * Process marker data.
	 * @param marker Marker which identifies the data.
	 * @param data Data to be processed.
	 * @param size The size of the data. Please note that the array size may be larger than the data size.
	 * @param os Output stream, on which the process can write optional data.
	 * @return {@code true} if the data should be written out to the output; otherwise {@code false}.
	 */
	static boolean processMarker(int marker, byte[] data, int size, OutputStream os) {
		// Currently, only ICC profile should be kept
		if (marker == APP0 + 2 && checkDataPrefix(data, SIG_ICC_PROFILE, true))
			return true;
		if (marker == APP0 + 1 && checkDataPrefix(data, SIG_ICC_PROFILE, true))
			return interpretExif(data, size, os);
		return false;
	}
	
	/**
	 * Process the input JPEG file.
	 * @param is Input stream, which contains JPEG file.
	 * @param os Output stream to write the processed JPEG file.
	 * @param profile Process profile.
	 * @throws IOException
	 */
	public static void processJpegFile(InputStream is, OutputStream os, String profile) throws IOException {
		byte[] buf = new byte[256 * 256];
		
		boolean isDataStream = false;
		for (;;) {
			int m;
			for (;;) {
				int c = is.read();
				if (c < 0)
					throw new IOException("Unexpected EOF.");
				if (c == 0xff) {
					m = is.read();
					if (m == 0) {
						os.write(0xff);
						os.write(m);
						continue;
					}
					isDataStream = false;
					break;
				}
				else if (isDataStream) {
					os.write(c);
				}
			}
			
			int size = -1;
			if (m >= RST0 && m <= RST7) {
				size = 0;
				isDataStream = true;
			} else if (m == SOI || m == EOI) {
				size = 0;
			} else if (m == SOS) {
				isDataStream = true;
			}
			
			if (size < 0) {
				size = is.read() * 256;
				size += is.read();
			}
			
			if (size > 2)
				is.read(buf, 0, size - 2);
			
			// remove metadata
			if (m >= APP0 && m <= APP15 && !processMarker(m, buf, size - 2, os))
				continue;
			
			os.write(0xff);
			os.write(m);
			if (size > 2) {
				os.write(size / 256);
				os.write(size & 255);
				os.write(buf, 0, size - 2);
			}
			
			if (m == EOI)
				break;
		}
	}
}
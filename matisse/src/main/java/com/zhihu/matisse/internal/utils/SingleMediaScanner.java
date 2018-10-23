package com.zhihu.matisse.internal.utils;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;

/**
 * @author 工藤
 * @email gougou@16fan.com
 * com.zhihu.matisse.internal.utils
 * create at 2018/10/23  11:32
 * description:通知系统扫描媒体文件
 */
public class SingleMediaScanner implements MediaScannerConnection.MediaScannerConnectionClient {

	private MediaScannerConnection mMs;
	private String path;
	private ScanListener listener;

	public interface ScanListener {

		/**
		 * 扫描结束的回调
		 */
		void onScanFinish();
	}

	public SingleMediaScanner(Context context, String path, ScanListener l) {
		this.path = path;
		this.listener = l;
		this.mMs = new MediaScannerConnection(context, this);
		this.mMs.connect();
	}

	@Override public void onMediaScannerConnected() {
		mMs.scanFile(path, null);
	}

	@Override public void onScanCompleted(String path, Uri uri) {
		mMs.disconnect();
		if (listener != null) {
			listener.onScanFinish();
		}
	}
}

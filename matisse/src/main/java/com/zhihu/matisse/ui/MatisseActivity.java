/*
 * Copyright 2017 Zhihu Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zhihu.matisse.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;


import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.TextView;

import com.zhihu.matisse.MimeType;
import com.zhihu.matisse.R;
import com.zhihu.matisse.internal.entity.Album;
import com.zhihu.matisse.internal.entity.Item;
import com.zhihu.matisse.internal.entity.SelectionSpec;
import com.zhihu.matisse.internal.loader.AlbumMediaLoader;
import com.zhihu.matisse.internal.model.AlbumCollection;
import com.zhihu.matisse.internal.model.SelectedItemCollection;
import com.zhihu.matisse.internal.ui.AlbumPreviewActivity;
import com.zhihu.matisse.internal.ui.BasePreviewActivity;
import com.zhihu.matisse.internal.ui.MediaSelectionFragment;
import com.zhihu.matisse.internal.ui.SelectedPreviewActivity;
import com.zhihu.matisse.internal.ui.adapter.AlbumMediaAdapter;
import com.zhihu.matisse.internal.ui.adapter.AlbumsAdapter;
import com.zhihu.matisse.internal.ui.widget.AlbumsSpinner;
import com.zhihu.matisse.internal.utils.MediaStoreCompat;
import com.zhihu.matisse.internal.utils.PathUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import com.zhihu.matisse.internal.utils.SingleMediaScanner;
import com.zhihu.matisse.listener.SelectionDelegate;

import java.util.ArrayList;

/**
 * Main Activity to display albums and media content (images/videos) in each album
 * and also support media selecting operations.
 */
public class MatisseActivity extends AppCompatActivity implements
        AlbumCollection.AlbumCallbacks, AdapterView.OnItemSelectedListener,
        MediaSelectionFragment.SelectionProvider, View.OnClickListener,
        AlbumMediaAdapter.CheckStateListener, AlbumMediaAdapter.OnMediaClickListener,
        AlbumMediaAdapter.OnPhotoCapture {

    public static final String EXTRA_RESULT_SELECTION = "extra_result_selection";
    public static final String EXTRA_RESULT_SELECTION_PATH = "extra_result_selection_path";
    public static final String EXTRA_RESULT_DELETED_ITEM_COUNT = "extra_result_deleted_item_count";
    private static final int REQUEST_CODE_PREVIEW = 23;
    private static final int REQUEST_CODE_CAPTURE = 24;
    private static final int REQUEST_CODE_CAPTURE_IMAGE = 25;
    private static final int REQUEST_CODE_CAPTURE_VIDEO = 26;
    private final String TAG = MatisseActivity.this.getClass().getSimpleName();
    private final AlbumCollection mAlbumCollection = new AlbumCollection();
    private MediaStoreCompat mMediaStoreCompat;
    private SelectedItemCollection mSelectedCollection = new SelectedItemCollection(this);
    private SelectionSpec mSpec;

    private AlbumsSpinner mAlbumsSpinner;
    private AlbumsAdapter mAlbumsAdapter;
    private TextView mButtonPreview;
    private TextView mButtonApply;
    private View mContainer;
    private View mEmptyView;
    private Fragment fragment;
    private ContentObserver mObserver;
    private Handler mHandler;
    private Album mAlbum;
    private Boolean isDontShow = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // programmatically set theme before super.onCreate()
        mSpec = SelectionSpec.getInstance();
        setTheme(mSpec.themeId);
        super.onCreate(savedInstanceState);

        if (!mSpec.hasInited) {
            // When hasInited == false, indicate that MatisseActivity is restarting
            // after app process was killed.
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        setContentView(R.layout.activity_matisse);

        if (mSpec.needOrientationRestriction()) {
            setRequestedOrientation(mSpec.orientation);
        }

        if (mSpec.capture) {
            mMediaStoreCompat = new MediaStoreCompat(this);
            if (mSpec.captureStrategy == null)
                throw new RuntimeException("Don't forget to set CaptureStrategy.");
            mMediaStoreCompat.setCaptureStrategy(mSpec.captureStrategy);
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        Drawable navigationIcon = toolbar.getNavigationIcon();
        TypedArray ta = getTheme().obtainStyledAttributes(new int[]{R.attr.album_element_color});
        int color = ta.getColor(0, 0);
        ta.recycle();
        if(navigationIcon != null) {
            navigationIcon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        }
        mButtonPreview = findViewById(R.id.button_preview);
        if(SelectionSpec.getInstance().enablePreview) {
            mButtonPreview.setVisibility(View.VISIBLE);
        } else {
            mButtonPreview.setVisibility(View.GONE);
        }
        mButtonApply = (TextView) findViewById(R.id.button_apply);
        mButtonPreview.setOnClickListener(this);
        mButtonApply.setOnClickListener(this);
        mContainer = findViewById(R.id.container);
        mEmptyView = findViewById(R.id.empty_view);

        mSelectedCollection.onCreate(savedInstanceState);
        ArrayList<Item> selectionItems = getIntent().getParcelableArrayListExtra(SelectedItemCollection.STATE_SELECTION);
        if (selectionItems != null) {
            mSelectedCollection.setDefaultSelection(selectionItems);
        }
        updateBottomToolbar();

        mAlbumsAdapter = new AlbumsAdapter(this, null, false);
        mAlbumsSpinner = new AlbumsSpinner(this);
        mAlbumsSpinner.setOnItemSelectedListener(this);
        mAlbumsSpinner.setSelectedTextView((TextView) findViewById(R.id.selected_album));
        mAlbumsSpinner.setPopupAnchorView(findViewById(R.id.toolbar));
        mAlbumsSpinner.setAdapter(mAlbumsAdapter);
        mAlbumCollection.onCreate(this, this);
        mAlbumCollection.onRestoreInstanceState(savedInstanceState);
        mAlbumCollection.loadAlbums();

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mSelectedCollection.onSaveInstanceState(outState);
        mAlbumCollection.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        mAlbumCollection.onDestroy();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_CANCELED);
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK)
            return;

        if (requestCode == REQUEST_CODE_PREVIEW) {
            Bundle resultBundle = data.getBundleExtra(BasePreviewActivity.EXTRA_RESULT_BUNDLE);
            ArrayList<Item> selected = resultBundle.getParcelableArrayList(SelectedItemCollection.STATE_SELECTION);
            int collectionType = resultBundle.getInt(SelectedItemCollection.STATE_COLLECTION_TYPE,
                    SelectedItemCollection.COLLECTION_UNDEFINED);
            if (data.getBooleanExtra(BasePreviewActivity.EXTRA_RESULT_APPLY, false)) {
                Intent result = new Intent();
                ArrayList<Uri> selectedUris = new ArrayList<>();
                ArrayList<String> selectedPaths = new ArrayList<>();
                if (selected != null) {
                    for (Item item : selected) {
                        selectedUris.add(item.getContentUri());
                        selectedPaths.add(PathUtils.getPath(this, item.getContentUri()));
                    }
                }
                result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION, selectedUris);
                result.putStringArrayListExtra(EXTRA_RESULT_SELECTION_PATH, selectedPaths);
                setResult(RESULT_OK, result);
                finish();
            } else {
                mSelectedCollection.overwrite(selected, collectionType);
                Fragment mediaSelectionFragment = getSupportFragmentManager().findFragmentByTag(
                        MediaSelectionFragment.class.getSimpleName());
                if (mediaSelectionFragment instanceof MediaSelectionFragment) {
                    ((MediaSelectionFragment) mediaSelectionFragment).refreshMediaGrid();
                }
                updateBottomToolbar();
            }
        } else if (requestCode == REQUEST_CODE_CAPTURE || requestCode == REQUEST_CODE_CAPTURE_IMAGE || requestCode == REQUEST_CODE_CAPTURE_VIDEO) {
/*
    Note: deprecated usage of camera
            Log.d(TAG, "i should be able to get the data here");

            Uri contentUri = mMediaStoreCompat.getCurrentPhotoUri();
            String path = mMediaStoreCompat.getCurrentPhotoPath();
            ArrayList<Uri> selected = new ArrayList<>();
            selected.add(contentUri);
            ArrayList<String> selectedPath = new ArrayList<>();
            selectedPath.add(path);

            this.getContentResolver().notifyChange(contentUri, this.mObserver);

            Intent result = new Intent();
            result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION, selected);
            result.putStringArrayListExtra(EXTRA_RESULT_SELECTION_PATH, selectedPath);
            setResult(RESULT_OK, result);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                MatisseActivity.this.revokeUriPermission(contentUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // reload albums
            mAlbumCollection.loadAlbums();

            new SingleMediaScanner(this.getApplicationContext(), path, new SingleMediaScanner.ScanListener() {
                @Override public void onScanFinish() {
                    Log.i("SingleMediaScanner", "scan finish!");
                }
            });

            ArrayList<Uri> selectedUris = (ArrayList<Uri>) mSelectedCollection.asListOfUri();
//            // add condition here where to select or not and broadcast message for the prompts
//            if (selectedUris.size() < mSpec.maxImageSelectable) {
//                // broadcast message for camera roll
//                ArrayList<Item> tempSelection = AlbumMediaLoader.querySelection(this, selectedUris);
//                int nVideo = selectedVideos(tempSelection);
//                Boolean isSelected = false;
//                try {
//
//                    if (newlySelection.get(0).mimeType.equals(MimeType.MP4.toString())) {
//                        // check current selected count video
//                        if (nVideo < mSpec.maxVideoSelectable) {
//                            isSelected = true;
//                        }
//                    } else {
//                        isSelected = true;
//                    }
//                    if (isSelected) {
//                    }
//                } catch (Exception e) {
//                    // do nothing
//                }
//            }
//            ArrayList<Uri> newlyCapture = new ArrayList<Uri>();
//            newlyCapture.add(contentUri);
//            ArrayList<Item> newlySelection = AlbumMediaLoader.querySelection(this, newlyCapture);
//            selectedUris.add(contentUri);
//            this.onUpdate(newlySelection.get(0));

            ArrayList<Item> selection = AlbumMediaLoader.querySelection(this, selectedUris);
            int collectionType = mSelectedCollection.getCollectionType();
            mSelectedCollection.overwrite(selection, collectionType);

            if (fragment != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .remove(fragment)
                        .commitAllowingStateLoss();
            }
            fragment = MediaSelectionFragment.newInstance(mAlbum);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container, fragment, MediaSelectionFragment.class.getSimpleName())
                    .commitAllowingStateLoss();
            updateBottomToolbar();
 */
        }

    }

    /**
     * Convert metadata to degrees
     */
    public static int getOrientation(ExifInterface exif) {
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                return 90;
            case ExifInterface.ORIENTATION_ROTATE_180:
                return 180;
            case ExifInterface.ORIENTATION_ROTATE_270:
                return 270;
            default:
                return 0;
        }
    }

    protected String getMimeType(Context context, Uri uri){
        ContentResolver cR = context.getContentResolver();
        String crType = cR.getType(uri);
        return crType;
    }

    protected String getMimeType(String path) {
        int i = path.lastIndexOf('.');
        String extension = "";
        if (i > 0) {
            extension = "file."+path.substring(i+1);
        }

        String type = "";
        String ext = MimeTypeMap.getFileExtensionFromUrl(extension);
        if (ext != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        }

        return (type == null) ? "" : type;
    }

    private void updateBottomToolbar() {
        int selectedCount = mSelectedCollection.count();
        if (selectedCount == 0) {
            mButtonPreview.setEnabled(false);
            mButtonApply.setEnabled(false);
            mButtonApply.setText(getString(R.string.button_apply_default));
        } else if (selectedCount == 1) {
            mButtonPreview.setEnabled(true);
            mButtonApply.setText(R.string.button_apply_default);
            mButtonApply.setEnabled(true);
            if(!mSpec.allowsMultipleSelection) {
                this.onFinishSelection();
            }
        } else {
            mButtonPreview.setEnabled(true);
            mButtonApply.setEnabled(true);
            mButtonApply.setText(getString(R.string.button_apply, selectedCount));
        }
    }

    private int selectedVideos(ArrayList<Item> mItems){
        int nVideo = 0;
        for (Item i : mItems) {
            if (i.isVideo()) nVideo++;
        }
        return nVideo;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button_preview) {
            Intent intent = new Intent(this, SelectedPreviewActivity.class);
            intent.putExtra(BasePreviewActivity.EXTRA_DEFAULT_BUNDLE, mSelectedCollection.getDataWithBundle());
            startActivityForResult(intent, REQUEST_CODE_PREVIEW);
        } else if (v.getId() == R.id.button_apply) {
            this.onFinishSelection();
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        mAlbumCollection.setStateCurrentSelection(position);
        mAlbumsAdapter.getCursor().moveToPosition(position);
        Album album = Album.valueOf(mAlbumsAdapter.getCursor());
        if (album.isAll() && SelectionSpec.getInstance().capture) {
            album.addCaptureCount();
        }
        onAlbumSelected(album);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    @Override
    public void onAlbumLoad(final Cursor cursor) {
        mAlbumsAdapter.swapCursor(cursor);
        // select default album.
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {

            @Override
            public void run() {
                cursor.moveToPosition(mAlbumCollection.getCurrentSelection());
                mAlbumsSpinner.setSelection(MatisseActivity.this,
                        mAlbumCollection.getCurrentSelection());
                Album album = Album.valueOf(cursor);
                if (album.isAll() && SelectionSpec.getInstance().capture) {
                    album.addCaptureCount();
                }
                onAlbumSelected(album);
            }
        });
    }

    @Override
    public void onAlbumReset() {
        mAlbumsAdapter.swapCursor(null);
    }

    private void onAlbumSelected(Album album) {
        mAlbum = album;
        if (album.isAll() && album.isEmpty()) {
            mContainer.setVisibility(View.GONE);
            mEmptyView.setVisibility(View.VISIBLE);
        } else {
            mContainer.setVisibility(View.VISIBLE);
            mEmptyView.setVisibility(View.GONE);
            if (fragment != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .remove(fragment)
                        .commitAllowingStateLoss();
            }
            fragment = MediaSelectionFragment.newInstance(album);
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.container, fragment, MediaSelectionFragment.class.getSimpleName())
                    .commitAllowingStateLoss();
        }
    }

    private void onFinishSelection() {
        Intent result = new Intent();

        ArrayList<Uri> selectedUris = (ArrayList<Uri>) mSelectedCollection.asListOfUri();
        ArrayList<String> selectedPaths = (ArrayList<String>) mSelectedCollection.asListOfString();

        int brokenItems = 0;
        int removedItems = 0;
        for (int i = selectedUris.size()-1; i >= 0; i--){
            if (getMimeType(MatisseActivity.this, selectedUris.get(i)) != null) {
                String mimeType = getMimeType(MatisseActivity.this, selectedUris.get(i));
                if (mimeType.contains("video")) {
                    //precheck
                    MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                    mediaMetadataRetriever.setDataSource(this, selectedUris.get(i));
                    long durationMs = 0;
                    String duration = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (duration != null) {
                        durationMs = Long.parseLong(duration);
                    }
                    if (durationMs <= 0) {
                        //prompt
                        brokenItems++;
                    }
                }
            } else {
                selectedPaths.remove(i);
                selectedUris.remove(i);
                removedItems++;
            }
        }

        if(brokenItems > 0) {

            Resources res = getResources();
            String alert_title = res.getQuantityString(R.plurals.alert_title_unsupport_items, brokenItems, brokenItems);
            new AlertDialog.Builder(this)
                    .setMessage(alert_title)
                    .setPositiveButton(android.R.string.yes, null)
                    .show();
            return;
        }

        result.putParcelableArrayListExtra(EXTRA_RESULT_SELECTION, selectedUris);
        result.putStringArrayListExtra(EXTRA_RESULT_SELECTION_PATH, selectedPaths);
        result.putExtra(EXTRA_RESULT_DELETED_ITEM_COUNT, removedItems);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onUpdate(Item item) {
        if (item.mimeType.equals(MimeType.MP4.toString())) {
            if (!mSpec.isDontShowVideoAlert && mSpec.hasFeatureEnabled && (item.duration/1000) > mSpec.maxVideoLength) {
                new android.app.AlertDialog.Builder(this).
                        setTitle(mSpec.alertTitle).
                        setMessage(String.format(mSpec.alertBody, mSpec.maxVideoLength)).
                        setPositiveButton(mSpec.alertPBtn, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                mSpec.isDontShowVideoAlert = true;
                                mSpec.delegate.onTapItem(null, true);
                            }
                        }).
                        setCancelable(false).
                        show();
            }
            mSpec.delegate.onTapItem(item, false);
        }
        // notify bottom toolbar that check state changed.
        updateBottomToolbar();
    }

    @Override
    public void onMediaClick(Album album, Item item, int adapterPosition) {
        Intent intent = new Intent(this, AlbumPreviewActivity.class);
        intent.putExtra(AlbumPreviewActivity.EXTRA_ALBUM, album);
        intent.putExtra(AlbumPreviewActivity.EXTRA_ITEM, item);
        intent.putExtra(BasePreviewActivity.EXTRA_DEFAULT_BUNDLE, mSelectedCollection.getDataWithBundle());
        startActivityForResult(intent, REQUEST_CODE_PREVIEW);
    }

    @Override
    public SelectedItemCollection provideSelectedItemCollection() {
        return mSelectedCollection;
    }

    @Override
    public void capture() {
//        mSpec.onCameraSelected.cameraSelected();
        if (mMediaStoreCompat != null) {
            if(mSpec.onlyShowImages()) {
                mMediaStoreCompat.dispatchCaptureIntent(MatisseActivity.this, MediaStore.ACTION_IMAGE_CAPTURE, REQUEST_CODE_CAPTURE_IMAGE);
            } else {
                String[] options = {getResources().getString(R.string.photo), getResources().getString(R.string.video)};

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getResources().getString(R.string.capture));
                builder.setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                mMediaStoreCompat.dispatchCaptureIntent(MatisseActivity.this, MediaStore.ACTION_IMAGE_CAPTURE, REQUEST_CODE_CAPTURE_IMAGE);
                                break;
                            case 1:
                                mMediaStoreCompat.dispatchCaptureIntent(MatisseActivity.this, MediaStore.ACTION_VIDEO_CAPTURE, REQUEST_CODE_CAPTURE_VIDEO);
                                break;
                        }
                    }
                });
                builder.show();
            }

        }
    }

}
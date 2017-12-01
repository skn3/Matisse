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
package com.zhihu.matisse.engine.impl;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.request.Request;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SizeReadyCallback;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.zhihu.matisse.R;
import com.zhihu.matisse.engine.ImageEngine;

/**
 * {@link ImageEngine} implementation using Glide.
 */

public class GlideEngine implements ImageEngine {

    public GlideEngine() {
    }

    public void loadThumbnail(Context context, int resize, Drawable placeholder, ImageView imageView, Uri uri) {
        RequestOptions options = (new RequestOptions()).centerCrop().placeholder(placeholder).error(R.drawable.error).override(resize, resize);
        Glide.with(context).asBitmap().load(uri).apply(options).into(imageView);
    }

    public void loadGifThumbnail(Context context, int resize, Drawable placeholder, ImageView imageView, Uri uri) {
        RequestOptions options = (new RequestOptions()).centerCrop().placeholder(placeholder).error(R.drawable.error).override(resize, resize);
        Glide.with(context).asBitmap().load(uri).apply(options).into(imageView);
    }

    @SuppressLint("CheckResult")
    @Override
    public void loadImage(Context context, int resizeX, int resizeY, final SubsamplingScaleImageView imageView, Uri uri) {

        RequestOptions options = (new RequestOptions()).centerCrop().override(resizeX, resizeY).priority(Priority.HIGH);
        Glide.with(context)
                .load(uri)
                .apply(options)
                .into(new Target<Drawable>() {
                    @Override
                    public void onLoadStarted(@Nullable Drawable placeholder) {

                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {

                    }

                    @Override
                    public void onResourceReady(Drawable resource, Transition<? super Drawable> transition) {
                        imageView.setImage(ImageSource.bitmap(((BitmapDrawable) resource).getBitmap()));
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {

                    }

                    @Override
                    public void getSize(SizeReadyCallback cb) {

                    }

                    @Override
                    public void removeCallback(SizeReadyCallback cb) {

                    }

                    @Override
                    public void setRequest(@Nullable Request request) {

                    }

                    @Nullable
                    @Override
                    public Request getRequest() {
                        return null;
                    }

                    @Override
                    public void onStart() {

                    }

                    @Override
                    public void onStop() {

                    }

                    @Override
                    public void onDestroy() {

                    }
                });
    }

    public void loadGifImage(Context context, int resizeX, int resizeY, ImageView imageView, Uri uri) {
        RequestOptions options = (new RequestOptions()).centerCrop().override(resizeX, resizeY).priority(Priority.HIGH);
        Glide.with(context).asGif().load(uri).apply(options).into(imageView);
    }


    public boolean supportAnimatedGif() {
        return true;
    }

    @Override
    public void pauseLoad(Context context, String tag) {
        Glide.with(context).pauseRequests();
    }

    @Override
    public void resumeLoad(Context context, String tag) {
        Glide.with(context).resumeRequests();
    }
}

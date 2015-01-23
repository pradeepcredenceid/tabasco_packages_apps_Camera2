/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.camera.one.v2;

import com.android.camera.one.v2.camera2proxy.ForwardingImageProxy;
import com.android.camera.one.v2.camera2proxy.ForwardingImageReader;
import com.android.camera.one.v2.camera2proxy.ImageProxy;
import com.android.camera.one.v2.camera2proxy.ImageReaderProxy;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Defers closing the underlying {@link ImageReaderProxy} until all images have
 * been released.
 * <p>
 * TODO Rename to something nicer
 */
@ThreadSafe
public final class CloseWhenDoneImageReader extends ForwardingImageReader implements
        ImageReaderProxy {
    private class ImageDecorator extends ForwardingImageProxy {
        public ImageDecorator(ImageProxy proxy) {
            super(proxy);
        }

        @Override
        public void close() {
            super.close();
            decrementImageCount();
        }
    }

    private final Object mLock;
    @GuardedBy("mLock")
    private boolean mClosed;
    @GuardedBy("mLock")
    private int mOpenImages;

    public CloseWhenDoneImageReader(ImageReaderProxy delegate) {
        super(delegate);
        mLock = new Object();
        mClosed = false;
        mOpenImages = 0;
    }

    private void decrementImageCount() {
        synchronized (mLock) {
            mOpenImages--;
            if (mClosed && mOpenImages == 0) {
                super.close();
            }
        }
    }

    @Override
    @Nullable
    public ImageProxy acquireNextImage() {
        synchronized (mLock) {
            if (!mClosed) {
                ImageProxy image = super.acquireNextImage();
                if (image != null) {
                    mOpenImages++;
                    return new ImageDecorator(image);
                }
            }
        }
        return null;
    }

    @Override
    @Nullable
    public ImageProxy acquireLatestImage() {
        synchronized (mLock) {
            if (!mClosed) {
                ImageProxy image = super.acquireLatestImage();
                if (image != null) {
                    mOpenImages++;
                    return new ImageDecorator(image);
                }
            }
        }
        return null;
    }

    @Override
    public void close() {
        synchronized (mLock) {
            mClosed = true;
            if (mOpenImages == 0) {
                super.close();
            }
        }
    }
}
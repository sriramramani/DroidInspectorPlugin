/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.sriramramani.droid.inspector.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.opengl.GLData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import com.sriramramani.droid.inspector.model.Node;
import com.sriramramani.droid.inspector.ui.CanvasView.ToolbarEvent.Type;

public class CanvasView extends Composite
                        implements SelectionListener {
    private final InspectorCanvas mCanvas;

    private final ToolItem mToggle3D;
    private final ToolItem mToggleBounds;
    private final ToolItem mToggleDepth;
    private final ToolItem mToggleOverdraw;
    private final ToolItem mToggleSplitContent;
    private final ToolItem mReset;

    private boolean mIsOrtho = false;

    static class ToolbarEvent {
        static enum Type {
            TOGGLE_3D,
            TOGGLE_BOUNDS,
            TOGGLE_DEPTH,
            TOGGLE_OVERDRAW,
            TOGGLE_SPLIT_CONTENT,
            RESET
        };

        public Type type;

        public ToolbarEvent(Type type) {
            this.type = type;
        }
    }

    public CanvasView(Composite parent, int style) {
        super(parent, style);
        setLayout(new GridLayout(1, true));

        ToolBar toolbar = new ToolBar(this, SWT.HORIZONTAL | SWT.SHADOW_OUT);

        mToggle3D = addToolItem("3D", toolbar);

        new ToolItem(toolbar, SWT.SEPARATOR);

        mToggleBounds = addToolItem("Bounds", toolbar);

        new ToolItem(toolbar, SWT.SEPARATOR);

        mToggleDepth = addToolItem("Depth", toolbar);

        new ToolItem(toolbar, SWT.SEPARATOR);

        mToggleOverdraw = addToolItem("Overdraw", toolbar);

        new ToolItem(toolbar, SWT.SEPARATOR);

        mToggleSplitContent = addToolItem("Split Content", toolbar);

        new ToolItem(toolbar, SWT.SEPARATOR);

        mReset = addToolItem("Reset", toolbar);

        GLData data = new GLData();
        data.doubleBuffer = true;
        data.depthSize = 24;
        data.redSize = 8;
        data.greenSize = 8;
        data.blueSize = 8;
        data.alphaSize = 8;
        data.stencilSize = 8;
        mCanvas = new InspectorCanvas(this, SWT.NONE, data);
        mCanvas.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

        refreshToolbar();
    }

    private ToolItem addToolItem(String name, ToolBar toolbar) {
        ToolItem item = new ToolItem(toolbar, SWT.PUSH);
        item.setText(name);
        item.addSelectionListener(this);
        return item;
    }

    public void initWithNode(Node node) {
        if (node != null) {
            mCanvas.initWithNode(node);
        }
    }

    public InspectorCanvas getCanvasView() {
        return mCanvas;
    }

    public void refresh() {
        mCanvas.refresh();
    }

    private void refreshToolbar() {
        if (mIsOrtho) {
            mToggleBounds.setEnabled(true);
            mToggleDepth.setEnabled(false);
            mToggleOverdraw.setEnabled(true);
            mToggleSplitContent.setEnabled(false);
        } else {
            mToggleBounds.setEnabled(false);
            mToggleDepth.setEnabled(true);
            mToggleOverdraw.setEnabled(false);
            mToggleSplitContent.setEnabled(true);
        }

        mToggle3D.setText(mIsOrtho ? "3D" : "2D");
    }

    @Override
    public void widgetSelected(SelectionEvent e) {
        final ToolbarEvent event;

        if (e.widget == mToggle3D) {
            mIsOrtho = !mIsOrtho;
            refreshToolbar();
            event = new ToolbarEvent(Type.TOGGLE_3D);
        } else if (e.widget == mToggleBounds){
            event = new ToolbarEvent(Type.TOGGLE_BOUNDS);
        } else if (e.widget == mToggleDepth){
            event = new ToolbarEvent(Type.TOGGLE_DEPTH);
        } else if (e.widget == mToggleOverdraw){
            event = new ToolbarEvent(Type.TOGGLE_OVERDRAW);
        } else if (e.widget == mToggleSplitContent){
            event = new ToolbarEvent(Type.TOGGLE_SPLIT_CONTENT);
        } else if (e.widget == mReset){
            event = new ToolbarEvent(Type.RESET);
        } else {
            event = null;
        }

        if (event != null) {
            mCanvas.handleToolbarEvent(event);
        }
    }

    @Override
    public void widgetDefaultSelected(SelectionEvent event) {
        // Do nothing.
    }
}

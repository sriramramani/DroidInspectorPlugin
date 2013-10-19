/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.sriramramani.droid.inspector.editors;

import java.io.File;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.EditorPart;

import com.sriramramani.droid.inspector.model.Node;
import com.sriramramani.droid.inspector.model.XMLParser;
import com.sriramramani.droid.inspector.ui.BoxModelView;
import com.sriramramani.droid.inspector.ui.BoxModelView.INodeDisplayChangedListener;
import com.sriramramani.droid.inspector.ui.CanvasView;
import com.sriramramani.droid.inspector.ui.HierarchyTreeViewer.INodeCheckedStateChangedListener;
import com.sriramramani.droid.inspector.ui.HierarchyTreeViewer.ISelectedNodeChangedListener;
import com.sriramramani.droid.inspector.ui.HierarchyView;
import com.sriramramani.droid.inspector.ui.InspectorCanvas.INodeSelectionChangedListener;

public class DroidInspectorEditor extends EditorPart {
    private String mFilePath;
    private CanvasView mCanvas;

    private HierarchyView mHierarchy;

    private BoxModelView mBoxModel;
    private Node mRoot = null;

    public DroidInspectorEditor() {
        super();
    }

    public void dispose() {
        super.dispose();
        mCanvas = null;
        mHierarchy = null;
        mBoxModel = null;
        mRoot = null;
    }

    @Override
    public void doSave(IProgressMonitor monitor) {
        // Nothing to save.
    }

    @Override
    public void doSaveAs() {
        // Nothing to save.
    }

    @Override
    public void init(IEditorSite site, IEditorInput input) throws PartInitException {
        if (!(input instanceof IURIEditorInput)) {
            throw new PartInitException("Overdraw Viewer: unsupported input type.");
        }

        setSite(site);
        setInput(input);

        mFilePath = ((IURIEditorInput) input).getURI().getPath();
        setPartName("Droid Inspector");
    }

    @Override
    public boolean isDirty() {
        return false;
    }

    @Override
    public boolean isSaveAsAllowed() {
        return false;
    }

    @Override
    public void createPartControl(Composite parent) {
        // Split the layout in 7:3 ratio.
        GridLayout grid = new GridLayout(10, true);
        grid.horizontalSpacing = 5;
        grid.verticalSpacing = 0;
        parent.setLayout(grid);

        SashForm layout = new SashForm(parent, SWT.HORIZONTAL);
        layout.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 7, 1));

        mCanvas = new CanvasView(layout, SWT.NONE);

        SashForm sidePanel = new SashForm(parent, SWT.VERTICAL);
        sidePanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));

        grid = new GridLayout(1, true);
        grid.horizontalSpacing = 5;
        grid.verticalSpacing = 0;
        sidePanel.setLayout(grid);

        try {
            mRoot = new XMLParser().parse(new File(mFilePath));
        } catch (Exception e) {
            return;
        }

        // Wrap in it another node for tree-viewer.
        Node node = new Node();
        node.name = "device";
        node.addChild(mRoot);

        // Make the canvas draw.
        mCanvas.initWithNode(mRoot);

        // Top half
        mHierarchy = new HierarchyView(sidePanel, SWT.BORDER);
        mHierarchy.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 7));
        mHierarchy.addSelectedNodeChangedListener(new ISelectedNodeChangedListener() {
            @Override
            public void onSelectedNodeChanged(Node node) {
                mBoxModel.setValues(node);
                mCanvas.refresh();
            }
        });
        mHierarchy.addNodeCheckedStateChangedListener(new INodeCheckedStateChangedListener() {
            @Override
            public void onNodeCheckedStateChanged(Node node) {
                mBoxModel.setValues(node);
                mRoot.calculateMaxBounds();
                mRoot.calculateDepth();
                mCanvas.refresh();
            }
        });
        mHierarchy.initialize(node);

        mCanvas.getCanvasView().addNodeSelectionChangedListener(new INodeSelectionChangedListener() {
            @Override
            public void nodeSelectionChanged(Node node) {
                mHierarchy.setSelection(new StructuredSelection(node));
            }
        });

        // Bottom half.
        mBoxModel = new BoxModelView(sidePanel, SWT.BORDER);
        mBoxModel.addNodeDisplayChangedListener(new INodeDisplayChangedListener() {
            @Override
            public void onNodeDisplayChanged(Node node) {
                mCanvas.refresh();
                mHierarchy.refresh(mRoot);
            }
        });

        mHierarchy.setSelection(new StructuredSelection(mRoot));
    }

    @Override
    public void setFocus() {
    }
}

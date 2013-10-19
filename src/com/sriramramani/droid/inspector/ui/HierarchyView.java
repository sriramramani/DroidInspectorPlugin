/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.sriramramani.droid.inspector.ui;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;

import com.sriramramani.droid.inspector.model.Node;
import com.sriramramani.droid.inspector.ui.HierarchyTreeViewer.INodeCheckedStateChangedListener;
import com.sriramramani.droid.inspector.ui.HierarchyTreeViewer.ISelectedNodeChangedListener;

public class HierarchyView extends Composite {
    private HierarchyTreeViewer mTree;

    private INodeCheckedStateChangedListener mCheckedListener;
    public HierarchyView(Composite parent, int style) {
        super(parent, style);

        GridLayout layout = new GridLayout(1, true);
        layout.horizontalSpacing = 0; // pixels
        layout.verticalSpacing = 5; // pixels
        setLayout(layout);

        ToolBar toolbar = new ToolBar(this, SWT.HORIZONTAL | SWT.SHADOW_OUT);

        ToolItem showAll = new ToolItem(toolbar, SWT.PUSH);
        showAll.setText("Show All");
        showAll.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                Node node = (Node) mTree.getInput();
                if (node != null) {
                    makeAllVisible(node);
                    Node root = node.children.get(0);
                    mTree.setChecked(root, true);
                    mTree.setGrayed(root, false);

                    if (mCheckedListener != null) {
                        mCheckedListener.onNodeCheckedStateChanged(node);
                    }
                }
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent event) {
            }
        });

        // Add a separator.
        new ToolItem(toolbar, SWT.SEPARATOR);

        ToolItem expandAll = new ToolItem(toolbar, SWT.PUSH);
        expandAll.setText("Expand All");
        expandAll.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                mTree.expandAll();
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent event) {
            }
        });

        // Add a separator.
        new ToolItem(toolbar, SWT.SEPARATOR);

        ToolItem showPackage = new ToolItem(toolbar, SWT.PUSH);
        showPackage.setText("Package");
        showPackage.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                mTree.togglePackage();
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent event) {
            }
        });

        // Add a separator.
        new ToolItem(toolbar, SWT.SEPARATOR);

        ToolItem showId = new ToolItem(toolbar, SWT.PUSH);
        showId.setText("ID");
        showId.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent event) {
                mTree.toggleID();
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent event) {
            }
        });

        mTree = new HierarchyTreeViewer(this, SWT.H_SCROLL | SWT.V_SCROLL);
        Tree tree = mTree.getTree();
        tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
    }

    public void initialize(Node node) {
        if (node == null) {
            mTree.setInput(null);
            return;
        }

        mTree.setInput(node);
        mTree.expandAll();
        mTree.setChecked(node.children.get(0), true);
    }

    public void addSelectedNodeChangedListener(ISelectedNodeChangedListener listener) {
        mTree.addSelectedNodeChangedListener(listener);
    }

    public void addNodeCheckedStateChangedListener(INodeCheckedStateChangedListener listener) {
        mCheckedListener = listener;
        mTree.addNodeCheckedStateChangedListener(listener);
    }

    public void setSelection(ISelection selection) {
        mTree.setSelection(selection);
    }

    public void refresh(Node node) {
        // Do it from the root. Directly calling on the node,
        // would have to take care of its current grayed state too.
        boolean current = mTree.getChecked(node);
        mTree.setChecked(node, !current);
        mTree.setChecked(node, current);
    }

    private void makeAllVisible(Node node) {
        int count = node.children.size();
        for (int i = 0; i < count; i++) {
            Node child = node.children.get(i);
            child.show(true);
            makeAllVisible(child);
        }
    }
}

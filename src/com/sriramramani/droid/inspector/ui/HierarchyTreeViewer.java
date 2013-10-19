/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.sriramramani.droid.inspector.ui;

import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.dialogs.ContainerCheckedTreeViewer;

import com.sriramramani.droid.inspector.model.Node;

public class HierarchyTreeViewer extends ContainerCheckedTreeViewer {

    // Currently selected node.
    private Node mSelectedNode = null;

    private final NodeLabelProvider mLabelProvider = new NodeLabelProvider();

    public static interface ISelectedNodeChangedListener {
        public void onSelectedNodeChanged(Node node);
    }

    public static interface INodeCheckedStateChangedListener {
        public void onNodeCheckedStateChanged(Node node);
    }

    private ISelectedNodeChangedListener mSelectedNodeChangedListener;
    private INodeCheckedStateChangedListener mNodeCheckedStateChangedListener;

    public HierarchyTreeViewer(Composite parent) {
        this(parent, 0);
    }

    public HierarchyTreeViewer(Composite parent, int style) {
        super(parent, style);

        // Setup listeners.
        setContentProvider(new NodeContentProvider());
        setLabelProvider(mLabelProvider);
        addSelectionChangedListener(new SelectionChangedListener());
        addCheckStateListener(new NodeCheckStateListener());
    }

    public HierarchyTreeViewer(Tree tree) {
        super(tree);

        // Setup listeners.
        setContentProvider(new NodeContentProvider());
        setLabelProvider(mLabelProvider);
        addSelectionChangedListener(new SelectionChangedListener());
        addCheckStateListener(new NodeCheckStateListener());
    }

    @Override
    protected void doCheckStateChanged(Object element) {
        Widget widget = findItem(element);
        if (!(widget instanceof TreeItem)) {
            return;
        }

        TreeItem item = (TreeItem) widget;
        boolean shouldGray = updateChildrenItems(item);
        item.setGrayed(shouldGray);

        updateParentItems(item.getParentItem());
    }

    /**
     * ischecked will say if parent was checked or not.
     * For this parent, each child.
     * checked if parent is checked && isVisible
     * grayed if any of its children is not checked.
     *
     * @param parent
     * @return
     */
    private boolean updateChildrenItems(TreeItem parent) {
        // Update children.
        Item[] children = getChildren(parent);
        boolean isChecked = parent.getChecked();
        int count = children.length;

        // Parent needn't be grayed by default.
        boolean parentGray = false;

        for (int i = 0; i < count; i++) {
            TreeItem child = (TreeItem) children[i];

            if (child.getData() != null && child.getData() instanceof Node) {
                Node node = (Node) child.getData();

                // Parent's new state & child's visible.
                final boolean childState = isChecked && node.isShowing();
                child.setChecked(childState);

                // Child should be grayed if either background or content is not shown.
                boolean childGray = !node.isBackgroundShown || !node.isContentShown;

                // Will be true if any of its children was not visible.
                childGray = childGray || updateChildrenItems(child);
                child.setGrayed(childGray);

                // If this child was grayed, parent should also be.
                parentGray = parentGray || childGray || !childState;
            }
        }

        return parentGray;
    }

    private void updateParentItems(TreeItem parent) {
        if (parent == null) {
            return;
        }

        TreeItem item = (TreeItem) parent;
        if (item.getData() == null) {
            return;
        }

        Item[] children = getChildren(item);
        boolean containsUnchecked = false;
        for (int i = 0; i < children.length; i++) {
            TreeItem child = (TreeItem) children[i];
            // Either child is unchecked, or child is (checked and) grayed.
            containsUnchecked |= (!child.getChecked() || (child.getChecked() && child.getGrayed()));
        }

        item.setGrayed(containsUnchecked);
        updateParentItems(parent.getParentItem());
    }

    public void addSelectedNodeChangedListener(ISelectedNodeChangedListener listener) {
        mSelectedNodeChangedListener = listener;
    }

    public void addNodeCheckedStateChangedListener(INodeCheckedStateChangedListener listener) {
        mNodeCheckedStateChangedListener = listener;
    }

    public void togglePackage() {
        mLabelProvider.togglePackage();
        refresh();
    }

    public void toggleID() {
        mLabelProvider.toggleID();
        refresh();
    }

    private class NodeCheckStateListener implements ICheckStateListener {
        @Override
        public void checkStateChanged(CheckStateChangedEvent event) {
            if (event.getElement() == null)
                return;

            Node node = (Node) event.getElement();
            node.show(event.getChecked());

            // Show background and content back again.
            if (event.getChecked()) {
                node.isBackgroundShown = node.isContentShown = true;
            }

            if (mNodeCheckedStateChangedListener != null) {
                mNodeCheckedStateChangedListener.onNodeCheckedStateChanged(node);
            }
        }
    }

    private class SelectionChangedListener implements ISelectionChangedListener {
        @Override
        public void selectionChanged(SelectionChangedEvent event) {
            if (event.getSelection().isEmpty()) {
                if (mSelectedNode != null) {
                    mSelectedNode.isSelected = false;
                }
                mSelectedNode = null;
                return;
            }

            Node node = (Node) (((IStructuredSelection) event.getSelection())).getFirstElement();
            if (mSelectedNode != null) {
                mSelectedNode.isSelected = false;
            }
            node.isSelected = true;
            mSelectedNode = node;

            if (mSelectedNodeChangedListener != null) {
                mSelectedNodeChangedListener.onSelectedNodeChanged(mSelectedNode);
            }
        }
    }

    private static class NodeLabelProvider extends LabelProvider
                                           implements IColorProvider {

        private static final String ANDROID_WIDGET = "android.widget";
        private boolean mShowPackage = false;
        private boolean mShowId = false;

        public NodeLabelProvider() {
        }

        @Override
        public Image getImage(Object element) {
            return null;
        }

        @Override
        public String getText(Object element) {
            final Node node = (Node) element;
            final String id = mShowId ? node.id : "";

            String name;
            if (mShowPackage) {
                if (node.name.startsWith(ANDROID_WIDGET)) {
                    name = node.name.substring(node.name.lastIndexOf('.') + 1);
                } else {
                    name = node.name;
                }
            } else {
                name = node.name.substring(node.name.lastIndexOf('.') + 1);
            }

            return name + " " + id;
        }

        @Override
        public Color getForeground(Object element) {
            Node node = (Node) element;
            if (node.isVisible()) {
                return Display.getDefault().getSystemColor(SWT.COLOR_TITLE_FOREGROUND);
            } else {
                return Display.getDefault().getSystemColor(SWT.COLOR_TITLE_INACTIVE_FOREGROUND);
            }
        }

        @Override
        public Color getBackground(Object element) {
            return null;
        }

        public void togglePackage() {
            mShowPackage = !mShowPackage;
        }

        public void toggleID() {
            mShowId = !mShowId;
        }
    }

    private static class NodeContentProvider implements ITreeContentProvider {
        @Override
        public void dispose() {
            // Nothing to dispose.
        }

        @Override
        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        }

        @Override
        public Object[] getElements(Object inputElement) {
            Node node = (Node) inputElement;
            return node.children.toArray();
        }

        @Override
        public Object[] getChildren(Object parentElement) {
            Node node = (Node) parentElement;
            return node.children.toArray();
        }

        @Override
        public Object getParent(Object element) {
            return ((Node) element).parent;
        }

        @Override
        public boolean hasChildren(Object element) {
            return ((Node) element).children.size() > 0;
        }
    }
}

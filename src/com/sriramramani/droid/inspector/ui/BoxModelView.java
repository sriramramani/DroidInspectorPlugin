/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.sriramramani.droid.inspector.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

import com.sriramramani.droid.inspector.model.Node;

public class BoxModelView extends Composite implements SelectionListener {
    private Button mBackground;
    private Button mContent;
    private Box mBox;

    private Node mNode;
    private Rectangle mBounds;
    private int[] mPadding;
    private int[] mMargin;
    private int[] mDrawablePadding;
    private int[] mInternalPadding;
    private float mDensity = 2.0f;
    private boolean mIsDp;

    public static interface INodeDisplayChangedListener {
        public void onNodeDisplayChanged(Node node);
    }

    private INodeDisplayChangedListener mListener;

    public BoxModelView(Composite parent, int style) {
        super(parent, style);

        mPadding = new int[4];
        mMargin = new int[4];
        mDrawablePadding = new int[4];
        mInternalPadding = new int[4];

        GridLayout layout = new GridLayout(1, true);
        layout.horizontalSpacing = 0; // pixels
        layout.verticalSpacing = 5; // pixels
        setLayout(layout);
        setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_WHITE));

        Composite row = new Composite(this, SWT.NONE);
        row.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 1, 1));

        RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
        rowLayout.wrap = false;
        rowLayout.fill = true;
        rowLayout.pack = true;
        rowLayout.justify = true;
        row.setLayout(rowLayout);

        mBackground = new Button(row, SWT.CHECK);
        mBackground.setText("Background");
        mBackground.setToolTipText("Show/Hide Background");
        mBackground.addSelectionListener(this);
        mBackground.setSelection(true);

        mContent = new Button(row, SWT.CHECK);
        mContent.setText("Content");
        mContent.setToolTipText("Show/Hide Content");
        mContent.addSelectionListener(this);
        mContent.setSelection(true);

        Label separator = new Label(this, SWT.HORIZONTAL | SWT.SEPARATOR);
        separator.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        mBox = new Box(this, SWT.NONE);
        mBox.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
    }

    @Override
    public void widgetSelected(SelectionEvent event) {
        if (mNode == null) {
            return;
        }

        Button button = (Button) event.widget;
        if (button == mBackground) {
            if (button.getSelection()) {
                mNode.isBackgroundShown = true;
            } else {
                mNode.isBackgroundShown = false;
            }
        } else if (button == mContent) {
            if (button.getSelection()) {
                mNode.isContentShown = true;
            } else {
                mNode.isContentShown = false;
            }
        }

        if (mListener != null) {
            mListener.onNodeDisplayChanged(mNode);
        }
    }

    @Override
    public void widgetDefaultSelected(SelectionEvent event) {
        // Do nothing.
    }

    public void setValues(Node node) {
        mNode = node;
        mBounds = new Rectangle(node.bounds.x, node.bounds.y, node.bounds.width, node.bounds.height);
        mPadding = node.padding;
        mMargin = node.margin;
        mDrawablePadding = node.drawablePadding;

        mInternalPadding[0] = mPadding[0] - mDrawablePadding[0];
        mInternalPadding[1] = mPadding[1] - mDrawablePadding[1];
        mInternalPadding[2] = mPadding[2] - mDrawablePadding[2];
        mInternalPadding[3] = mPadding[3] - mDrawablePadding[3];

        mBackground.setEnabled(node.hasBackground());
        mBackground.setSelection(node.isBackgroundShown);

        mContent.setEnabled(node.hasContent());
        mContent.setSelection(node.isContentShown);

        mBox.redraw();
    }

    public void addNodeDisplayChangedListener(INodeDisplayChangedListener listener) {
        mListener = listener;
    }


    private class Box extends Composite
                      implements PaintListener,
                                 MouseMoveListener,
                                    MouseTrackListener {
        private static final int BOX = 30; // pixels
        private static final int TWO_BOX = 2 * BOX;
        private static final int THREE_BOX = 3 * BOX;

        private static final int SPACE = 7; // pixels
        private static final int TWO_SPACE = 2 * SPACE;

        public Box(Composite parent, int style) {
            super(parent, style);

            // This draws a custom box model.
            addPaintListener(this);
            addMouseTrackListener(this);
        }

        private String[] getStrings(int[] array) {
            final float density = (mIsDp ? mDensity : 1.0f);
            return new String[] {
                Integer.toString((int) (array[0] / density)),
                Integer.toString((int) (array[1] / density)),
                Integer.toString((int) (array[2] / density)),
                Integer.toString((int) (array[3] / density)),
            };
        }

        private String[] getInternalBounds() {
            final float density = (mIsDp ? mDensity : 1.0f);
            final int internalWidth = mBounds.width - mPadding[0] - mPadding[2];
            final int internalHeight = mBounds.height - mPadding[1] - mPadding[3];

            final String width = Integer.toString((int) (internalWidth / density));
            final String height = Integer.toString((int) (internalHeight / density));

            return new String[] { width, height };
        }

        private void drawBox(PaintEvent e, int depth) {
            e.gc.drawRectangle(e.x + depth,
                               e.y + depth,
                               e.x + e.width - (2 * depth),
                               e.y + e.height - (2 * depth));
        }

        private void drawValues(PaintEvent e, String[] text, int depth) {
            final int twoDepth = (2 * depth);
            // left
            drawText(e.gc, text[0],
                     e.x + depth - BOX, e.y + depth,
                     BOX,               e.y + e.height - twoDepth);

            // right
            drawText(e.gc, text[2],
                     e.x + e.width - depth, e.y + depth,
                     BOX,                   e.y + e.height - twoDepth);

            // top
            drawText(e.gc, text[1],
                     e.x + depth,              e.y + depth - BOX,
                     e.x + e.width - twoDepth, BOX);


            // bottom
            drawText(e.gc, text[3],
                     e.x + depth,              e.y + e.height - depth,
                     e.x + e.width - twoDepth, BOX);
        }

        @Override
        public void paintControl(PaintEvent e) {
            final GC gc = e.gc;
            final int x = e.x;
            final int y = e.y;
            final int w = e.width;
            final int h = e.height;

            // Set defaults.
            gc.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_BLACK));

            // Outermost line separating view and its margin.
            gc.setLineWidth(2);
            drawBox(e, BOX);

            // A small shadow.
            gc.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_GRAY));
            // Bottom.
            gc.drawLine(x + 0 + BOX + 2, y + h - BOX + 2,
                        x + w - BOX + 2, y + h - BOX + 2);

            // Right.
            gc.drawLine(x + w - BOX + 2, y + 0 + BOX + 2,
                        x + w - BOX + 2, y + h - BOX + 2);

            // Reverting back to black.
            gc.setForeground(Display.getCurrent().getSystemColor(SWT.COLOR_BLACK));

            // Draw all the margin texts.
            drawValues(e, getStrings(mMargin), BOX);

            // Next level.
            // Drawable padding line.
            gc.setLineWidth(1);
            gc.setLineStyle(SWT.LINE_DASH);
            drawBox(e, TWO_BOX);

            // Draw all the drawable padding texts.
            drawValues(e, getStrings(mDrawablePadding), TWO_BOX);

            // Next level.
            // Internal padding line.
            gc.setLineWidth(1);
            gc.setLineStyle(SWT.LINE_SOLID);
            drawBox(e, THREE_BOX);

            // Draw all the internal padding texts.
            drawValues(e, getStrings(mInternalPadding), THREE_BOX);

            // Internal size.
            final String[] bounds = getInternalBounds();
            drawText(gc, bounds[0] + " x " + bounds[1],
                     x + THREE_BOX, y + THREE_BOX,
                     x + w - (2 * THREE_BOX), y + h - (2 * THREE_BOX));
        }

        private void drawText(GC gc, String text, int x, int y, int width, int height) {
            final int length = text.length();
            final int avgCharWidth = gc.getFontMetrics().getAverageCharWidth();
            final int fontHeight = gc.getFontMetrics().getHeight();

            x += SPACE;
            y += SPACE;
            width -= TWO_SPACE;
            height -= TWO_SPACE;

            int numLines = 0;
            StringBuffer buffer = new StringBuffer();
            // Eyeballing.
            int lineWidth = 0;
            int maxLineWidth = 0;
            for (int i = 0; i < length; i++) {
                lineWidth += avgCharWidth;
                if (lineWidth < width) {
                    buffer.append(text.charAt(i));
                } else {
                    buffer.append("\n");
                    buffer.append(text.charAt(i));
                    if (lineWidth > maxLineWidth)
                        maxLineWidth = lineWidth;
                    lineWidth = 0;
                    numLines++;
                }
            }

            numLines += 1;
            if (lineWidth > maxLineWidth)
                maxLineWidth = lineWidth;

            final int totalHeight = numLines * fontHeight;

            gc.drawText(buffer.toString(), x + (width - maxLineWidth)/2, y + (height - totalHeight)/2);
        }

        private boolean isWithinBounds(MouseEvent e, Rectangle bounds, int depth) {
            return (e.x > depth &&
                    e.y > depth &&
                    e.x < bounds.width - depth &&
                    e.y < bounds.height - depth);
        }

        private String getDirection(MouseEvent e, Rectangle bounds, int depth) {
            if (e.x <= depth) {
                return "left";
            } else if (e.x > bounds.width - depth) {
                return "right";
            } else if (e.y <= depth) {
                return "top";
            } else if (e.y > bounds.height - depth) {
                return "bottom";
            } else {
                return "";
            }
        }

        @Override
        public void mouseMove(MouseEvent e) {
            Rectangle bounds = getBounds();
            if (isWithinBounds(e, bounds, BOX)) {
                if (isWithinBounds(e, bounds, TWO_BOX)) {
                    if (isWithinBounds(e, bounds, THREE_BOX)) {
                        // Internal Bounds.
                        final String[] internalBounds = getInternalBounds();
                        setToolTipText(internalBounds[0] + " x " + internalBounds[1]);
                    } else {
                        // Internal Padding.
                        setToolTipText("padding-" + getDirection(e, bounds, THREE_BOX));
                    }
                } else {
                    // Drawable Padding.
                    setToolTipText("drawble-padding-" + getDirection(e, bounds, TWO_BOX));
                }
            } else {
                // Margin.
                setToolTipText("margin-" + getDirection(e, bounds, BOX));
            }
        }

        @Override
        public void mouseEnter(MouseEvent e) {
            addMouseMoveListener(this);
        }

        @Override
        public void mouseExit(MouseEvent e) {
            removeMouseMoveListener(this);
        }

        @Override
        public void mouseHover(MouseEvent e) {
            // Do nothing.
        }
    }
}

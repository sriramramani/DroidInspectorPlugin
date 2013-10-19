/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.sriramramani.droid.inspector.ui;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.apache.commons.codec.binary.Base64;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.events.MouseWheelListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.opengl.GLCanvas;
import org.eclipse.swt.opengl.GLData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.util.Point;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector2f;
import org.lwjgl.util.vector.Vector3f;

import com.sriramramani.droid.inspector.model.Node;
import com.sriramramani.droid.inspector.model.Node.Color;
import com.sriramramani.droid.inspector.model.Node.ContentType;
import com.sriramramani.droid.inspector.model.Node.Drawable;
import com.sriramramani.droid.inspector.ui.CanvasView.ToolbarEvent;

import de.matthiasmann.twl.utils.PNGDecoder;
import de.matthiasmann.twl.utils.PNGDecoder.Format;

public class InspectorCanvas extends GLCanvas
                            implements Listener,
                                       MouseListener,
                                       MouseMoveListener,
                                       MouseTrackListener,
                                       MouseWheelListener {

    public interface INodeSelectionChangedListener {
        public void nodeSelectionChanged(Node node);
    }

    private static final float MAX_ROTATION = 89.9f;
    private static final float ZOOM_FACTOR = 2.0f;

    private static final int TOUCH_SLOP = 2; // pixels

    private static final float[] CLEAR_COLOR = new float[] { 0.2f, 0.2f, 0.2f, 1.0f };

    private static enum ColorType {
        COLOR_WHITE,
        COLOR_BLACK,
        BOUNDS_SELECTION,
        BOUNDS_NORMAL,
        LAYER_BACKGROUND,
        LAYER_CONTENT,
        LAYER_NONE,
        OVERDRAW_BLUE,
        OVERDRAW_GREEN,
        OVERDRAW_RED_LOW,
        OVERDRAW_RED_HIGH
    };

    private INodeSelectionChangedListener mNodeSelectionChangedListener = null;

    // Toolbar options.
    private boolean mIsOrtho = false;
    private boolean mShowDepth = true;
    private boolean mShowBounds = true;
    private boolean mShowOverdraw = true;
    private boolean mSplitContent = true;

    private boolean mRotateNodes = true;
    private boolean mIsPicking = false;

    private float mZNear = 1.0f;
    private float mZFar = 4000.0f;

    private Vector3f mCamera;
    private Vector2f mRotate;
    private Vector2f mTranslate;
    private Vector2f mOrthoTranslate;

    // Scale factor in 2D. Bound by [smallest-scale/2 ... 2.0f].
    private float mOrthoScale = 1.0f;

    // Last known mouse position that was tracked (either by down or move event).
    private Point mMousePosition;

    // Mouse position of the down event.
    private Point mMouseDown;

    // Node under the mouse down position. Pick this node, if the user's intent was a click.
    private Node mPickNode;

    private float mDepth = 0.0f;

    private Matrix4f mTransform;

    // Root of the tree.
    private Node mNode = null;

    public InspectorCanvas(Composite parent, int style, GLData data) {
        super(parent, style, data);
        setCurrent();

        // Clear the canvas.
        GL11.glClearColor(CLEAR_COLOR[0], CLEAR_COLOR[1], CLEAR_COLOR[2], CLEAR_COLOR[3]);
        GL11.glClearDepth(1.0f);
        GL11.glLineWidth(1.0f);
        GL11.glPointSize(1.0f);

        GL11.glShadeModel(GL11.GL_FLAT);

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_NICEST);

        GL11.glHint(GL11.GL_PERSPECTIVE_CORRECTION_HINT, GL11.GL_NICEST);

        GL11.glDisable(GL11.GL_LIGHTING);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);

        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.01f);

        GL11.glEnable(GL11.GL_STENCIL_TEST);
        GL11.glStencilFunc(GL11.GL_ALWAYS, 0x1, 0xf);
        GL11.glStencilOp(GL11.GL_INCR, GL11.GL_KEEP, GL11.GL_INCR);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        reset();

        mTransform = new Matrix4f();
        mTransform.setIdentity();

        addListener(SWT.Resize, this);
        addListener(SWT.Paint, this);
        addMouseListener(this);
        addMouseWheelListener(this);
    }

    @Override
    public void handleEvent(Event event) {
        switch (event.type) {
            case SWT.Resize:
                doResize();
                break;

            case SWT.Paint:
                doPaint();
                break;

            default:
                break;
        }
    }

    public void handleToolbarEvent(ToolbarEvent event) {
        switch (event.type) {
            case TOGGLE_3D:
                mIsOrtho = !mIsOrtho;
                doResize();
                break;

            case TOGGLE_BOUNDS:
                mShowBounds = !mShowBounds;
                break;

            case TOGGLE_DEPTH:
                mShowDepth = !mShowDepth;
                break;

            case TOGGLE_OVERDRAW:
                mShowOverdraw = !mShowOverdraw;
                break;

            case TOGGLE_SPLIT_CONTENT:
                mSplitContent = !mSplitContent;
                break;

            case RESET:
                reset();

            default:
                break;
        }

        doPaint();
    }

    private void reset() {
        mCamera = new Vector3f(0.0f, 0.0f, mZFar/2.0f);
        mTranslate = new Vector2f(0.0f, 0.0f);
        mRotate = new Vector2f(0.0f, 60.0f);

        mOrthoTranslate = new Vector2f(0.0f, 0.0f);
        mOrthoScale = 1.0f;
    }

    private void doResize() {
        // Get the aspect ratio.
        Rectangle bounds = getBounds();
        float aspectRatio = (float) bounds.width / (float) bounds.height;

        // Set current.
        setCurrent();

        // Reset viewport.
        GL11.glViewport(0, 0, bounds.width, bounds.height);

        // Set the projection matrix.
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();

        if (mIsOrtho) {
            // Changing top and bottom to make it draw in 4th quadrant.
            GL11.glOrtho(0, bounds.width, bounds.height, 0, mZNear, mZFar);
        } else {
            // Perspective is viewing-angle, aspect-ratio, (-z, z).
            GLU.gluPerspective(45.0f, aspectRatio, mZNear, mZFar);
        }

        // Set the model view matrix.
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        refresh();
    }

    private void doPaint() {
        if (isDisposed() || mNode == null) {
            return;
        }

        setCurrent();

        // Clear the color, depth and stencil buffers.
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);

        GL11.glLoadIdentity();

        GLU.gluLookAt(0.0f, 0.0f, mCamera.z,
                      0.0f, 0.0f, 0.0f,
                      0.0f, 1.0f, 0.0f);

        // Transformations happen in the reverse.
        if (mIsOrtho) {
            // User's translation.
            GL11.glTranslatef(mOrthoTranslate.x, mOrthoTranslate.y, 0.0f);

            // Rotate 180 degrees.
            GL11.glRotatef(180.0f, 1.0f, 0.0f, 0.0f);

            // Center the nodes.
            final Rectangle bounds = getBounds();
            final float scaledWidth = mNode.bounds.width * mOrthoScale;
            final float scaledHeight = mNode.bounds.height * mOrthoScale;
            GL11.glTranslatef((bounds.width - scaledWidth)/2, 0.0f, 0.0f);

            // Scale based on viewport size.
            GL11.glTranslatef(scaledWidth/2, -scaledHeight/2, 0.0f);
            GL11.glScalef(mOrthoScale, mOrthoScale, 0.0f);
            GL11.glTranslatef(-scaledWidth/2, scaledHeight/2, 0.0f);

        } else {
            // Translate all the nodes.
            GL11.glTranslatef(mTranslate.x, mTranslate.y, 0.0f);

            // Rotate.
            GL11.glRotatef(mRotate.x, 1.0f, 0.0f, 0.0f);
            GL11.glRotatef(mRotate.y, 0.0f, 1.0f, 0.0f);

            // Center the nodes.
            GL11.glTranslatef(-mNode.bounds.width/2, mNode.bounds.height/2, 0.0f);
        }

        final float absX = Math.abs(mRotate.x);
        final float absY = Math.abs(mRotate.y);
        mDepth = Math.max(absX, absY) * 5 / 9.0f;

        drawHierarchy(mNode);

        if (!mIsPicking && mIsOrtho && mShowOverdraw) {
            for (int i = 2; i <= 5; i++) {
                drawOverdraw(i);
            }
        }

        GL11.glFlush();

        if (!mIsPicking) {
            swapBuffers();
        }
    }

    private void drawOverdraw(int level) {
        GL11.glPushAttrib(GL11.GL_STENCIL_BUFFER_BIT);

        GL11.glStencilFunc(level == 5 ? GL11.GL_LEQUAL : GL11.GL_EQUAL, level, 0xf);
        GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

        GL11.glTranslatef(mNode.bounds.x, -mNode.bounds.y, 0.0f);

        if (level == 2) {
            loadColor(ColorType.OVERDRAW_BLUE);
        } else if (level == 3) {
            loadColor(ColorType.OVERDRAW_GREEN);
        } else if (level == 4) {
            loadColor(ColorType.OVERDRAW_RED_LOW);
        } else if (level > 4) {
            loadColor(ColorType.OVERDRAW_RED_HIGH);
        }

        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex3f(0.0f, 0.0f, 0.0f);
            GL11.glVertex3f(mNode.bounds.width, 0.0f, 0.0f);
            GL11.glVertex3f(mNode.bounds.width, -mNode.bounds.height, 0.0f);
            GL11.glVertex3f(0.0f, -mNode.bounds.height, 0.0f);
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    public void refresh() {
        if (mShowOverdraw) {
            GL11.glPushAttrib(GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 0x1, 0xf);
            GL11.glStencilOp(GL11.GL_INCR, GL11.GL_KEEP, GL11.GL_INCR);
        }

        // Do the actual paint.
        doPaint();

        if (mShowOverdraw) {
            GL11.glPopAttrib();
        }
    }

    @Override
    public void setCurrent() {
        super.setCurrent();
        try {
            GLContext.useContext(InspectorCanvas.this);
        } catch(LWJGLException e) {
            e.printStackTrace();
        }
    }

    public void initWithNode(Node node) {
        mNode = node;

        // Prepare the textures.
        prepareTextures(mNode);

        // Prepare the display lists.
        prepareDisplayLists(mNode);

        // Paint it.
        doPaint();
    }

    // Prepare textures for the node hierarchy.
    private void prepareTextures(Node node) {
        if (node == null || node.bounds.width == 0 || node.bounds.height == 0) {
            return;
        }

        final Drawable background = node.getBackground();
        if (background.type == ContentType.IMAGE) {
            background.texureId = bindTexture(node, background.image);
        }

        final Drawable content = node.getContent();
        if (content.type == ContentType.IMAGE) {
            content.texureId = bindTexture(node, content.image);
        }

        for (Node child : node.children) {
            prepareTextures(child);
        }
    }

    private int bindTexture(Node node, String imageData) {
        int textureId = GL11.glGenTextures();
        byte[] bitmap = Base64.decodeBase64(imageData);
        try {
            PNGDecoder decoder = new PNGDecoder(new ByteArrayInputStream(bitmap));
            int width = decoder.getWidth();
            int height = decoder.getHeight();
            ByteBuffer buffer = ByteBuffer.allocateDirect(4 * width * height);
            decoder.decode(buffer, width * 4, Format.RGBA);
            buffer.flip();

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA,
                              width, height, 0,
                              GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return textureId;
    }

    // Prepare display lists for the node hierarchy.
    private void prepareDisplayLists(Node node) {
        if (node == null || node.bounds.width == 0 || node.bounds.height == 0) {
            return;
        }

        // Background.
        final Drawable background = node.getBackground();
        if (background.type != ContentType.NONE) {
            // Begin list.
            background.displayListId = GL11.glGenLists(1);
            GL11.glNewList(background.displayListId, GL11.GL_COMPILE);

            if (background.type == ContentType.COLOR) {
                drawColor(node, background.color);
            } else if (background.type == ContentType.IMAGE && background.texureId != -1) {
                drawImage(node, background.texureId, true);
            }

            // End list.
            GL11.glEndList();
        }

        // Content.
        final Drawable content = node.getContent();
        if (content.type != ContentType.NONE && content.texureId != -1) {
            // Begin list.
            content.displayListId = GL11.glGenLists(1);
            GL11.glNewList(content.displayListId, GL11.GL_COMPILE);

            drawImage(node, content.texureId, false);

            // End list.
            GL11.glEndList();
        }

        for (Node child : node.children) {
            prepareDisplayLists(child);
        }
    }

    private void drawImage(Node node, int textureId, boolean isBackground) {
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        loadColor(ColorType.COLOR_WHITE);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0f, 0.0f);
            GL11.glVertex3f(0.0f, 0.0f, 0.0f);
            GL11.glTexCoord2f(1.0f, 0.0f);
            GL11.glVertex3f(node.bounds.width, 0.0f, 0.0f);
            GL11.glTexCoord2f(1.0f, 1.0f);
            GL11.glVertex3f(node.bounds.width, -node.bounds.height, 0.0f);
            GL11.glTexCoord2f(0.0f, 1.0f);
            GL11.glVertex3f(0.0f, -node.bounds.height, 0.0f);
        GL11.glEnd();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    private void drawColor(Node node, Color color) {
        GL11.glColor4f(color.red, color.green, color.blue, color.alpha);
        drawFrontFace(node, 0.0f, GL11.GL_FILL);
    }

    private void drawDepth(Node node, float depth, int mode) {
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, mode);
        GL11.glBegin(mode == GL11.GL_FILL ? GL11.GL_QUAD_STRIP : GL11.GL_LINES);

            // Top-left.
            GL11.glVertex3f(0.0f, 0.0f, 0.0f);
            GL11.glVertex3f(0.0f, 0.0f, depth);

            // Top-right.
            GL11.glVertex3f(node.bounds.width, 0.0f, 0.0f);
            GL11.glVertex3f(node.bounds.width, 0.0f, depth);

            // Bottom-right.
            GL11.glVertex3f(node.bounds.width, -node.bounds.height, 0.0f);
            GL11.glVertex3f(node.bounds.width, -node.bounds.height, depth);

            // Bottom-left.
            GL11.glVertex3f(0.0f, -node.bounds.height, 0.0f);
            GL11.glVertex3f(0.0f, -node.bounds.height, depth);

            // Complete the quad strip.
            if (mode == GL11.GL_FILL) {
                // Top-left.
                GL11.glVertex3f(0.0f, 0.0f, 0.0f);
                GL11.glVertex3f(0.0f, 0.0f, depth);
            }

        GL11.glEnd();
    }

    private void drawFrontFace(Node node, float depth, int mode) {
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, mode);
        GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex3f(0.0f, 0.0f, depth);
            GL11.glVertex3f(node.bounds.width, 0.0f, depth);
            GL11.glVertex3f(node.bounds.width, -node.bounds.height, depth);
            GL11.glVertex3f(0.0f, -node.bounds.height, depth);
        GL11.glEnd();
    }

    private void drawDepthCube(Node node, float depth) {
        if (node.isSelected) {
            loadColor(ColorType.BOUNDS_SELECTION);
            drawDepth(node, -mDepth, GL11.GL_FILL);
            loadColor(ColorType.BOUNDS_NORMAL);
            drawDepth(node, -depth, GL11.GL_LINE);
            return;
        }

        final Drawable background = node.getBackground();
        final Drawable content = node.getContent();
        final boolean hasBackground = node.isBackgroundShown && (background.displayListId != -1);
        final boolean hasContent = node.isContentShown && (content.displayListId != -1);

        if (hasBackground && hasContent) {
            // Draw both.
            float halfDepth = mDepth / 2.0f;
            GL11.glTranslatef(0.0f, 0.0f, -halfDepth);

            loadColor(ColorType.LAYER_BACKGROUND);
            drawDepth(node, -halfDepth, GL11.GL_FILL);

            GL11.glTranslatef(0.0f, 0.0f, halfDepth);

            loadColor(ColorType.LAYER_CONTENT);
            drawDepth(node, -halfDepth, GL11.GL_FILL);
        } else if (hasContent) {
            loadColor(ColorType.LAYER_CONTENT);
            drawDepth(node, -mDepth, GL11.GL_FILL);
        } else if (hasBackground) {
            loadColor(ColorType.LAYER_BACKGROUND);
            drawDepth(node, -mDepth, GL11.GL_FILL);
        } else {
            loadColor(ColorType.LAYER_NONE);
            drawDepth(node, -mDepth, GL11.GL_FILL);
        }

        // Draw a boundary.
        loadColor(ColorType.BOUNDS_NORMAL);
        drawDepth(node, -depth, GL11.GL_LINE);
    }

    // Given a node, draw it on the screen.
    private void drawHierarchy(Node node) {
        if (node == null ||
            node.bounds.width == 0 ||
            node.bounds.height == 0 ||
            !node.isShowing() ||
            !node.isVisible()) {
            return;
        }

        // Give a 3d depth.
        GL11.glPushMatrix();

        final float depth = node.depth * mDepth;

        // Node's translation.
        GL11.glTranslatef(node.bounds.x, -node.bounds.y, depth);

        final Drawable background = node.getBackground();
        final Drawable content = node.getContent();
        final boolean hasBackground = node.isBackgroundShown && (background.displayListId != -1);
        final boolean hasContent = node.isContentShown && (content.displayListId != -1);

        if (mIsPicking) {
            GL11.glColor4f(node.pickColor[0],
                           node.pickColor[1],
                           node.pickColor[2],
                           node.pickColor[3]);

            drawFrontFace(node, 0.0f, GL11.GL_FILL);

            // Draw the depth, only if we show in actual mode.
            // If not, if we are splitting content, draw a layer for it.
            if (mShowDepth) {
                drawDepth(node, -mDepth, GL11.GL_FILL);
            } else if (mSplitContent && hasBackground && hasContent) {
                drawFrontFace(node, -mDepth/2.0f, GL11.GL_FILL);
            }
        } else {
            if (!mIsOrtho && mShowDepth) {
                GL11.glPushAttrib(GL11.GL_STENCIL_BUFFER_BIT);
                GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);
                drawDepthCube(node, depth);
                GL11.glPopAttrib();
            }

            if (hasBackground && hasContent) {
                // Both background and content are available.
                // Draw background at a depth if needed.
                if (mSplitContent)
                    GL11.glTranslatef(0.0f, 0.0f, -mDepth/2.0f);

                GL11.glCallList(background.displayListId);

                if (mSplitContent)
                    GL11.glTranslatef(0.0f, 0.0f, mDepth/2.0f);

                GL11.glCallList(content.displayListId);
            } else if (hasBackground) {
                GL11.glCallList(background.displayListId);
            } else if (hasContent) {
                GL11.glCallList(content.displayListId);
            }

            // Stencil shouldn't know about bounds.
            GL11.glPushAttrib(GL11.GL_STENCIL_BUFFER_BIT | GL11.GL_LINE_BIT);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

            // Show bounds.
            if (!mIsOrtho && mShowDepth) {
                loadColor(ColorType.BOUNDS_NORMAL);
            } else {
                if (node.isSelected) {
                    GL11.glLineWidth(2.0f);
                    loadColor(ColorType.BOUNDS_SELECTION);
                } else {
                    loadColor(ColorType.BOUNDS_NORMAL);
                }
            }

            if (node.isSelected || !mIsOrtho || mShowBounds) {
                drawFrontFace(node, 0.0f, GL11.GL_LINE);
            }

            // Show a bounding box for split content in perspective mode.
            if (!mIsOrtho && !mShowDepth && mSplitContent && hasBackground && hasContent) {
                drawFrontFace(node, -mDepth/2.0f, GL11.GL_LINE);
            }

            GL11.glPopAttrib();
        }

        for (Node child : node.children) {
            drawHierarchy(child);
        }

        GL11.glPopMatrix();
    }

    private void loadColor(ColorType type) {
        switch (type) {
            case COLOR_WHITE:
                GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                break;

            case COLOR_BLACK:
                GL11.glColor4f(0.0f, 0.0f, 0.0f, 1.0f);
                break;

            case BOUNDS_SELECTION:
                GL11.glColor4f(1.0f, 0.45f, 0.45f, 1.0f);
                break;

            case BOUNDS_NORMAL:
                GL11.glColor4f(0.33f, 0.33f, 0.33f, 1.0f);
                break;

            case LAYER_BACKGROUND:
                GL11.glColor4f(0.50f, 0.658f, 0.733f, 0.5f);
                break;

            case LAYER_CONTENT:
                GL11.glColor4f(0.976f, 0.823f, 0.592f, 0.5f);
                break;

            case LAYER_NONE:
                GL11.glColor4f(0.85f, 0.85f, 0.85f, 0.5f);
                break;

            case OVERDRAW_BLUE:
                GL11.glColor4f(0.7f, 0.7f, 1.0f, 0.7f);
                break;

            case OVERDRAW_GREEN:
                GL11.glColor4f(0.7f, 1.0f, 0.7f, 0.7f);
                break;

            case OVERDRAW_RED_LOW:
                GL11.glColor4f(1.0f, 0.7f, 0.7f, 0.7f);
                break;

            case OVERDRAW_RED_HIGH:
                GL11.glColor4f(1.0f, 0.3f, 0.3f, 0.7f);
                break;

        }
    }

    private Node pickNodeAt(Point point) {
        if (point == null)
            return null;

        // Draw the hierarchy in picking mode.
        mIsPicking = true;
        doPaint();
        mIsPicking = false;

        // Find the pixel at the mouse down location.
        // This frame buffer is not transferred to the display.
        IntBuffer viewport = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        FloatBuffer result = BufferUtils.createFloatBuffer(4);
        GL11.glReadPixels(point.getX(), viewport.get(3) - point.getY(), 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, result);

        // Find the node with this pixel color.
        return findNodeWithPickColor(mNode, result.get(0), result.get(1), result.get(2));
    }

    private Node findNodeWithPickColor(Node node, float red, float green, float blue) {
        if (isApprox(red, node.pickColor[0]) &&
            isApprox(green, node.pickColor[1]) &&
            isApprox(blue, node.pickColor[2])) {
            return node;
        }

        for (Node child : node.children) {
            Node result = findNodeWithPickColor(child, red, green, blue);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private boolean isApprox(float first, float original) {
        // The float values could be off by 10%.
        return (first > (original - 0.01f)) && (first < (original + 0.01f));
    }

    private void selectNode(Node node) {
        if (node != null && mNodeSelectionChangedListener != null) {
                mNodeSelectionChangedListener.nodeSelectionChanged(node);
        }
    }

    @Override
    public void mouseDown(MouseEvent e) {
        addMouseTrackListener(this);
        addMouseMoveListener(this);

        final Point point = new Point(e.x, e.y);
        mMouseDown = point;
        mMousePosition = point;

        // Pick the node right under the mouse.
        mPickNode = pickNodeAt(point);

        // Perform rotation if the click was not on a node.
        mRotateNodes = (mPickNode == null);
    }

    @Override
    public void mouseMove(MouseEvent e) {
        final Point point = new Point(e.x, e.y);
        final float deltaX = (mMousePosition.getX() - e.x);
        final float deltaY = (mMousePosition.getY() - e.y);

        if (mIsOrtho) {
            mOrthoTranslate.x -= deltaX;
            mOrthoTranslate.y -= deltaY;
        } else {
            if (mRotateNodes) {
                mRotate.x -= (deltaY * 2 * MAX_ROTATION / mNode.bounds.height);
                mRotate.y -= (deltaX * 2 * MAX_ROTATION / mNode.bounds.width);

                if (mRotate.x > MAX_ROTATION) {
                    mRotate.x = MAX_ROTATION;
                } else if (mRotate.x < -MAX_ROTATION) {
                    mRotate.x = -MAX_ROTATION;
                }

                if (mRotate.y > MAX_ROTATION) {
                    mRotate.y = MAX_ROTATION;
                } else if (mRotate.y < -MAX_ROTATION) {
                    mRotate.y = -MAX_ROTATION;
                }
            } else {
                mTranslate.x -= deltaX;
                mTranslate.y += deltaY;
            }
        }

        mMousePosition = point;
        doPaint();
    }

    @Override
    public void mouseUp(MouseEvent e) {
        removeMouseTrackListener(this);
        removeMouseMoveListener(this);

        final float deltaY = (mMouseDown.getX() - e.x);
        final float deltaX = (mMouseDown.getY() - e.y);

        if (Math.abs(deltaX) < TOUCH_SLOP &&
            Math.abs(deltaY) < TOUCH_SLOP) {
            selectNode(mPickNode);
        }

        mMouseDown = null;
        mMousePosition = null;
    }

    @Override
    public void mouseScrolled(MouseEvent e) {
        if (mIsOrtho) {
            Rectangle bounds = getBounds();
            final float scaleX = (float) bounds.width / (float) mNode.maxBounds.width;
            final float scaleY = (float) bounds.height / (float) mNode.maxBounds.height;
            final float minScale = Math.min(scaleX, scaleY) / 2.0f;
            mOrthoScale += (e.count * 0.01f);
            if (mOrthoScale < minScale) {
                mOrthoScale = minScale;
            } else if (mOrthoScale > 2.0f) {
                mOrthoScale = 2.0f;
            }
        } else {
            mCamera.z += (e.count * ZOOM_FACTOR);
            if (mCamera.z >= mZFar) {
                mCamera.z = mZFar - 0.1f;
            } else if (mCamera.z <= mZNear) {
                mCamera.z = mZNear + 0.1f;
            }
        }

        doPaint();
    }

    @Override
    public void mouseExit(MouseEvent e) {
        removeMouseTrackListener(this);
        removeMouseMoveListener(this);
    }

    @Override
    public void mouseEnter(MouseEvent e) {
        // Do Nothing.
    }

    @Override
    public void mouseHover(MouseEvent e) {
        // Do Nothing.
    }

    @Override
    public void mouseDoubleClick(MouseEvent e) {
        // Do nothing.
    }

    public void addNodeSelectionChangedListener(INodeSelectionChangedListener listener) {
        mNodeSelectionChangedListener = listener;
    }

    public static float[] getClearColor() {
        return CLEAR_COLOR;
    }
}

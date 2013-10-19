/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.sriramramani.droid.inspector.model;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import com.sriramramani.droid.inspector.ui.InspectorCanvas;

public final class XMLParser {
    private static final Pattern BOUNDS_PATTERN = Pattern.compile("\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]");

    // This gives us 1000 different colors.
    private static final float[] COLOR_ARRAY = { 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 1.0f };
    private int red, green, blue;

    private Node mRoot = null;

    public Node parse(File file) {
        final float[] clearColor = InspectorCanvas.getClearColor();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = null;
        try {
            parser = factory.newSAXParser();
            parser.parse(file, new DefaultHandler() {
                private Node mParent = null;
                private Node mCurrent = null;
                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) {
                    mParent = mCurrent;
                    Node node = null;
                    if (attributes.getIndex("name") >= 0) {
                        node = new Node();
                        node.name = attributes.getValue("name");
                        node.id = attributes.getValue("id");

                        // Set the parent.
                        node.parent = mParent;

                        String bounds = attributes.getValue("bounds");
                        int[] rectBounds = new int[4];
                        getBounds(rectBounds, bounds);
                        node.bounds = new Rectangle(rectBounds[0], rectBounds[1], rectBounds[2], rectBounds[3]);

                        if (node.parent != null) {
                            node.deviceLeft = node.parent.deviceLeft + node.bounds.x;
                            node.deviceTop = node.parent.deviceTop + node.bounds.y;
                        }

                        String padding = attributes.getValue("padding");
                        getBounds(node.padding, padding);

                        String margin = attributes.getValue("margin");
                        if (margin != null) {
                            getBounds(node.margin, margin);
                        }

                        int visibility = Integer.parseInt(attributes.getValue("visibility"));
                        if (visibility == 1)
                            node.visibility = Node.Visibility.VISIBLE;
                        else if (visibility == -1)
                            node.visibility = Node.Visibility.INVISIBLE;
                        else
                            node.visibility = Node.Visibility.GONE;

                        if (attributes.getIndex("background") > 0) {
                            node.setBackground(attributes.getValue("background"));
                        } else {
                            node.setBackground(null);
                        }

                        if (attributes.getIndex("content") > 0) {
                            node.setContent(attributes.getValue("content"));
                        } else {
                            node.setContent(null);
                        }

                        if (attributes.getIndex("drawable-padding") > 0) {
                            String drawablePadding = attributes.getValue("drawable-padding");
                            if (drawablePadding != null) {
                                getBounds(node.drawablePadding, drawablePadding);
                            }
                        }

                        node.scrollX = Float.parseFloat(attributes.getValue("scroll-x"));
                        node.scrollY = Float.parseFloat(attributes.getValue("scroll-y"));

                        if (attributes.getIndex("scale-x") > 0) {
                            node.scaleX = Float.parseFloat(attributes.getValue("scale-x"));
                        }

                        if (attributes.getIndex("scale-y") > 0) {
                            node.scaleY = Float.parseFloat(attributes.getValue("scale-y"));
                        }

                        if (attributes.getIndex("rotation-x") > 0) {
                            node.rotationX = Float.parseFloat(attributes.getValue("rotation-x"));
                        }

                        if (attributes.getIndex("rotation-y") > 0) {
                            node.rotationY = Float.parseFloat(attributes.getValue("rotation-y"));
                        }

                        if (attributes.getIndex("translation-x") > 0) {
                            node.translationX = Float.parseFloat(attributes.getValue("translation-x"));
                        }

                        if (attributes.getIndex("translation-y") > 0) {
                            node.translationY = Float.parseFloat(attributes.getValue("translation-y"));
                        }

                        float[] pickColor = getNextColor();
                        if (pickColor[0] == clearColor[0] &&
                            pickColor[1] == clearColor[1] &&
                            pickColor[2] == clearColor[2]) {
                            pickColor = getNextColor();
                        }

                        node.pickColor = pickColor;
                    }

                    if (node != null)
                        mCurrent = node;

                    if (mCurrent != null && mRoot == null) {
                        mRoot = mCurrent;
                    }

                    if (mCurrent != null && mParent != null) {
                        mParent.addChild(mCurrent);
                    }
                }

                @Override
                public void endElement(String uri, String localName, String qName) {
                    if (mParent != null) {
                        mCurrent = mParent;
                        mParent = mParent.parent;
                    }
                }
            });
        } catch (Exception e) {
            MessageBox box = new MessageBox(new Shell(Display.getCurrent()), SWT.ERROR);
            box.setText("Parse Error");
            box.setMessage("Unable to parse the file from the device.");
            box.open();
        }

        mRoot.calculateMaxBounds();
        mRoot.calculateDepth();
        return mRoot;
    }

    private void getBounds(int[] bounds, String attribute) {
        Matcher matcher = BOUNDS_PATTERN.matcher(attribute);
        if (matcher.matches()) {
            bounds[0] = Integer.parseInt(matcher.group(1));
            bounds[1] = Integer.parseInt(matcher.group(2));
            bounds[2] = Integer.parseInt(matcher.group(3));
            bounds[3] = Integer.parseInt(matcher.group(4));
        }
    }

    private float[] getNextColor() {
        blue++;
        if (blue == COLOR_ARRAY.length) {
            blue = 0;
            green++;
            if (green == COLOR_ARRAY.length) {
                green = 0;
                red++;
            }
        }

        return new float[] {
                COLOR_ARRAY[red],
                COLOR_ARRAY[green],
                COLOR_ARRAY[blue],
                1.0f
        };
    }

}

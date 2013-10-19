/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.sriramramani.droid.inspector.model;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.swt.graphics.Rectangle;

public class Node {
    // Color drawable pattern as #AARRGGBB
    private static final Pattern COLOR_DRAWABLE = Pattern.compile("#(\\w{2})(\\w{2})(\\w{2})(\\w{2})");

    private static final String BASE64_IDENTIFIER = "data:image/png;base64,";
    private static final int BASE64_IDENTIFIER_LENGTH = BASE64_IDENTIFIER.length();

    public static enum Visibility {
        VISIBLE,
        GONE,
        INVISIBLE
    };

    public static enum ContentType {
        NONE,
        IMAGE,
        COLOR
    };

    public static class Color {
        public float red;
        public float green;
        public float blue;
        public float alpha;
    }

    public static class Drawable {
        public ContentType type = ContentType.NONE;
        public Color color;
        public String image;
        public int texureId = -1;
        public int displayListId = -1;
    }

    public String id;
    public String name;
    public Rectangle bounds;

    // In certain cases like ListViews, the last row might be drawn, making the content
    // to be bigger than actual bounds.
    public Rectangle maxBounds;

    public int[] padding;
    public int[] margin;
    public int[] drawablePadding;

    private Drawable background;
    private Drawable content;

    public Node parent;
    public List<Node> children;

    Visibility visibility;

    // Whether the selected node in the hierarchy. Depends on selected state in TreeView.
    public boolean isSelected = false;

    // Whether shown in the 3D view. Depends on checked state in TreeView.
    private boolean isShown = true;

    public boolean isBackgroundShown = true;
    public boolean isContentShown = true;

    public int deviceTop;
    public int deviceLeft;

    public float scrollX = 1.0f;
    public float scrollY = 1.0f;

    public float scaleX = 1.0f;
    public float scaleY = 1.0f;

    public float rotationX = 0.0f;
    public float rotationY = 0.0f;

    public float translationX = 0.0f;
    public float translationY = 0.0f;

    // The depth from parent at which to draw this node.
    // Depth of 0, is same as parent. Depth of 1 is one z-level above parent.
    public int depth;

    public float[] pickColor;

    public Node() {
        children = new LinkedList<Node>();
        padding = new int[4];
        margin = new int[4];
        drawablePadding = new int[4];
    }

    public void addChild(Node child) {
        children.add(child);
    }

    private boolean isValid(String data) {
        return (data != null && !data.isEmpty() && !data.equals("null"));
    }

    public void setBackground(String data) {
        background = new Drawable();

        if (!isValid(data))
            return;

        Matcher matcher = COLOR_DRAWABLE.matcher(data);
        if (matcher.matches()) {
            background.type = ContentType.COLOR;
            background.color = new Color();
            background.color.alpha = (float) Integer.parseInt(matcher.group(1), 16) / 255;
            background.color.red = (float) Integer.parseInt(matcher.group(2), 16) / 255;
            background.color.green = (float) Integer.parseInt(matcher.group(3), 16) / 255;
            background.color.blue = (float) Integer.parseInt(matcher.group(4), 16) / 255;
        } else {
            background.type = ContentType.IMAGE;
            background.image = data.substring(BASE64_IDENTIFIER_LENGTH);
        }
    }

    public void setContent(String data) {
        content = new Drawable();

        if (!isValid(data))
            return;

        content.type = ContentType.IMAGE;
        content.image = data.substring(BASE64_IDENTIFIER_LENGTH);
    }

    public Drawable getBackground() {
        return background;
    }

    public Drawable getContent() {
        return content;
    }

    public void show(boolean doShow) {
        isShown = doShow;
    }

    public boolean isShowing() {
        return isShown;
    }

    public boolean isVisible() {
        return (visibility == Visibility.VISIBLE);
    }

    /*
     * Returns whether the 3D view will show this or not.
     */
    private boolean wouldShow() {
        return isShowing() && isVisible();
    }

    public boolean hasBackground() {
        return (background != null && background.displayListId != -1);
    }

    public boolean hasContent() {
        return (content != null && content.displayListId != -1);
    }

    public void calculateMaxBounds() {
        maxBounds = bounds;

        if (children == null) {
            return;
        }

        Rectangle totalBounds = new Rectangle(bounds.x, bounds.y, bounds.width, bounds.height);
        for (Node child : children) {
            // Calculate child's bounds.
            child.calculateMaxBounds();

            if (!wouldShow()) {
                continue;
            }

            Rectangle bounds = child.maxBounds;
            if (bounds.x < 0) {
                totalBounds.x = bounds.x;
            }

            if (bounds.y < 0){
                totalBounds.y = bounds.y;
            }

            if ((bounds.x + bounds.width) > totalBounds.width) {
                totalBounds.width = (bounds.x + bounds.width - totalBounds.x);
            }

            if ((bounds.y + bounds.height) > totalBounds.height) {
                totalBounds.height = (bounds.y + bounds.height - totalBounds.y);
            }
        }

        maxBounds = totalBounds;
    }

    /**
     * If this node overlaps with any of its previous siblings,
     * then it's depth is (1 + prev-sibling's max-depth).
     * If not, the depth defaults to 1.
     */
    public void calculateDepth() {
        // Ask children to calculate first.
        for (Node node : children) {
            node.calculateDepth();
        }

        // Default depth.
        depth = 1;

        if (parent == null || !wouldShow()) {
            return;
        }

        for (Node child : parent.children) {
            if (child == this) {
                break;
            }

            if (child.wouldShow() && (maxBounds.intersects(child.maxBounds))) {
                // There is an overlapping, make this depth the maximum of so-far and the sibling's max-depth.
                depth = Math.max(depth, child.getMaxDepth() + 1);
            }
        }
    }

    /*
     * Returns the maximum depth of a tree rooted at this node.
     */
    public int getMaxDepth() {
        if (children == null) {
            return depth;
        }

        int maxDepth = 0;
        List<Rectangle> areas = new ArrayList<Rectangle>();
        for (Node child : children) {
            if (!child.wouldShow()) {
                continue;
            }

            int childDepth = child.getMaxDepth();
            Rectangle bounds = child.maxBounds;
            for (Rectangle area : areas) {
                if (area.intersects(bounds)) {
                    maxDepth = Math.max(maxDepth, childDepth);
                }
            }

            areas.add(bounds);
            maxDepth = Math.max(maxDepth, childDepth);
        }

        return maxDepth + depth;
    }
}

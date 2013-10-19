/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.sriramramani.droid.inspector.model;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.Point;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;

public class ArcBall {
    private float mWidth;

    private Vector3f mStartVector;
    private Vector3f mEndVector;

    private Quaternion mLastRotation;
    private Quaternion mCurrentRotation;


    public ArcBall(float width, float height) {
        mStartVector = new Vector3f();
        mEndVector = new Vector3f();

        mLastRotation = new Quaternion();
        mCurrentRotation = new Quaternion();

        setBounds(width, height);
    }

    public void setBounds(float width, float height) {
        mWidth = width;
    }

    public Vector3f mapToSphere(Point point) {
        Vector3f vector = new Vector3f();
        //float radius = Math.min(mWidth, mHeight);
        vector.x = (float) (point.getX()/(mWidth * 0.5) - 1.0f);
        // Using width here feels better.
        vector.y = (float) -(point.getY()/(mWidth * 0.5) - 1.0f);
        vector.z = 0.0f;

        // Point outside sphere. (length > radius).
        if (vector.lengthSquared() > 1.0f) {
            //Return the "normalized" vector, a point on the sphere
            vector.normalise();
        } else {
            //Return a vector to a point mapped inside the sphere sqrt(radius squared - length)
            vector.z = (float) Math.sqrt(1.0f - vector.lengthSquared());
        }

        return vector;
    }

    public void click(Point point) {
        //mPressVector = new Vector3f(point.x, point.y, 0.0f);
        mStartVector = mapToSphere(point);
    }

    public void drag(Point point) {
        //Map the point to the sphere.
        mEndVector = mapToSphere(point);

        if (mStartVector == null || mEndVector == null)
            return;

        //Compute the vector perpendicular to the begin and end vectors.
        Vector3f cross = Vector3f.cross(mStartVector, mEndVector, null);
        float dot = Vector3f.dot(mStartVector, mEndVector);

        if (cross.length() > 1.0e-5) {
            mCurrentRotation =  new Quaternion(cross.x, cross.y, cross.z, dot);
            mCurrentRotation = Quaternion.mul(mCurrentRotation, mLastRotation, null);
        }
    }

    public void release() {
        mStartVector = null;
        mEndVector = null;
        mLastRotation = mCurrentRotation;
    }

    public FloatBuffer getTransformation() {
        Matrix4f transform = QuaternionToMatrix4f(mCurrentRotation);

        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        buffer.put(new float[] {
                transform.m00, transform.m01, transform.m02, transform.m03,
                transform.m10, transform.m11, transform.m12, transform.m13,
                transform.m20, transform.m21, transform.m22, transform.m23,
                transform.m30, transform.m31, transform.m32, transform.m33
        });
        buffer.flip();

        return buffer;
    }

    private static Matrix4f QuaternionToMatrix4f(Quaternion q) {
        float n, s;
        float xs, ys, zs;
        float wx, wy, wz;
        float xx, xy, xz;
        float yy, yz, zz;

        n = (q.x * q.x) + (q.y * q.y) + (q.z * q.z) + (q.w * q.w);
        s = (n > 0.0f) ? (2.0f / n) : 0.0f;

        xs = q.x * s;  ys = q.y * s;  zs = q.z * s;
        wx = q.w * xs; wy = q.w * ys; wz = q.w * zs;
        xx = q.x * xs; xy = q.x * ys; xz = q.x * zs;
        yy = q.y * ys; yz = q.y * zs; zz = q.z * zs;

        Matrix4f matrix = new Matrix4f();
        matrix.setIdentity();
        matrix.m00 = 1.0f - 2 * (yy + zz);
        matrix.m01 = 2 * (xy - wz);
        matrix.m02 = 2 * (xz + wy);

        matrix.m10 = 2 * (xy + wz);
        matrix.m11 = 1.0f - 2 * (xx + zz);
        matrix.m12 = 2 * (yz - wx);

        matrix.m20 = 2 * (xz - wy);
        matrix.m21 = 2 * (yz + wx);
        matrix.m22 = 1.0f - 2 * (xx + yy);

        matrix.transpose();
        return matrix;
    }
}

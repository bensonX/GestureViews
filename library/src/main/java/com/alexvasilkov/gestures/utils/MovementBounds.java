package com.alexvasilkov.gestures.utils;

import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import com.alexvasilkov.gestures.Settings;
import com.alexvasilkov.gestures.State;
import com.alexvasilkov.gestures.StateController;

/**
 * Class that encapsulates logic related to movement bounds restriction. It will also apply view's gravity provided
 * by {@link com.alexvasilkov.gestures.Settings#getGravity()} method.
 * Movement bounds can be represented using regular rectangle most of the time. But if fit method set to
 * {@link com.alexvasilkov.gestures.Settings.Fit#OUTSIDE} and view have rotation != 0 than movement bounds
 * will be a rotated rectangle, and that will complicate restrictions logic a bit, since {@link android.graphics.Rect}
 * sides can only be parallel to coordinates axis.
 */
public class MovementBounds {

    // Temporary objects
    private static final Matrix MATRIX = new Matrix();
    private static final float[] POINT_ARR = new float[2];
    private static final PointF POINT_F = new PointF();

    private static final Rect RECT_TMP = new Rect();
    private static final RectF RECT_TMP_F = new RectF();
    private static final RectF RECT_TMP_AREA = new RectF();

    private static final RectF RECT_EXT_BOUNDS = new RectF();
    private static final Rect RECT_POS = new Rect();
    private static final Rect RECT_MOV_AREA = new Rect();

    // Movement bounds parameters
    private final RectF mBounds = new RectF();
    private float mRotation;
    private float mPivotX, mPivotY;


    /**
     * Restricts x & y coordinates to current bounds (see {@link #setup(com.alexvasilkov.gestures.State, com.alexvasilkov.gestures.Settings)}).
     */
    public PointF restrict(float x, float y, float overscrollX, float overscrollY) {
        POINT_ARR[0] = x;
        POINT_ARR[1] = y;

        if (mRotation != 0f) {
            // Rotating given point so we can apply rectangular bounds.
            MATRIX.setRotate(-mRotation, mPivotX, mPivotY);
            MATRIX.mapPoints(POINT_ARR);
        }

        // Applying restrictions
        POINT_ARR[0] = StateController.restrict(POINT_ARR[0], mBounds.left - overscrollX, mBounds.right + overscrollX);
        POINT_ARR[1] = StateController.restrict(POINT_ARR[1], mBounds.top - overscrollY, mBounds.bottom + overscrollY);

        if (mRotation != 0f) {
            // Rotating restricted point back to original coordinates
            MATRIX.setRotate(mRotation, mPivotX, mPivotY);
            MATRIX.mapPoints(POINT_ARR);
        }

        POINT_F.set(POINT_ARR[0], POINT_ARR[1]);
        return POINT_F;
    }

    public PointF restrict(float x, float y) {
        return restrict(x, y, 0f, 0f);
    }

    /**
     * Note: do not store returned rect since it will be reused again later by this method.
     */
    public RectF getExternalBounds() {
        if (mRotation == 0f) {
            RECT_EXT_BOUNDS.set(mBounds);
        } else {
            MATRIX.setRotate(mRotation, mPivotX, mPivotY);
            MATRIX.mapRect(RECT_EXT_BOUNDS, mBounds);
        }
        return RECT_EXT_BOUNDS;
    }

    public void union(float x, float y) {
        POINT_ARR[0] = x;
        POINT_ARR[1] = y;

        if (mRotation != 0f) {
            // Rotating given point so we can add it to bounds
            MATRIX.setRotate(-mRotation, mPivotX, mPivotY);
            MATRIX.mapPoints(POINT_ARR);
        }

        mBounds.union(POINT_ARR[0], POINT_ARR[1]);
    }

    /**
     * Calculating bounds for {@link State#x} & {@link State#y} values to keep view within viewport
     * and taking into account view's gravity (see {@link Settings#setGravity(int)})
     */
    public void setup(State state, Settings settings) {
        RectF area = RECT_TMP_AREA;
        area.set(getMovementAreaWithGravity(settings));
        final Rect pos;

        if (settings.getFitMethod() == Settings.Fit.OUTSIDE) {
            // For OUTSIDE fit method we will rotate area rect instead of view rect,
            // that will help us correctly fit movement area inside view's rect
            mRotation = state.getRotation();
            mPivotX = area.centerX();
            mPivotY = area.centerY();

            state.get(MATRIX);
            MATRIX.postRotate(-mRotation, mPivotX, mPivotY);
            pos = getPositionWithGravity(MATRIX, settings);

            MATRIX.setRotate(-mRotation, mPivotX, mPivotY);
            MATRIX.mapRect(area);
        } else {
            mRotation = 0f;

            state.get(MATRIX);
            pos = getPositionWithGravity(MATRIX, settings);
        }

        // Calculating movement bounds for top-left corner of the scaled view

        // horizontal bounds
        if (area.width() < pos.width()) {
            // view is bigger than movement area -> restricting view movement with moving area
            mBounds.left = area.left - (pos.width() - area.width());
            mBounds.right = area.left;
        } else {
            // view is smaller than viewport -> positioning view according to calculated gravity
            // and restricting view movement in this direction
            mBounds.left = mBounds.right = pos.left;
        }

        // vertical bounds
        if (area.height() < pos.height()) {
            // view is bigger than viewport -> restricting view movement with viewport bounds
            mBounds.top = area.top - (pos.height() - area.height());
            mBounds.bottom = area.top;
        } else {
            // view is smaller than viewport -> positioning view according to calculated gravity
            // and restricting view movement in this direction
            mBounds.top = mBounds.bottom = pos.top;
        }

        // We should also adjust bounds position, since top-left corner of rotated view's rectangle will be somewhere
        // on the edge.
        // Note: for OUTSIDE fit method view's rotation was skipped above, so we will not need to adjust bounds here.
        if (settings.getFitMethod() != Settings.Fit.OUTSIDE) {
            state.get(MATRIX);

            RECT_TMP_F.set(0, 0, settings.getViewW(), settings.getViewH());
            MATRIX.mapRect(RECT_TMP_F);

            POINT_ARR[0] = POINT_ARR[1] = 0f;
            MATRIX.mapPoints(POINT_ARR);

            mBounds.offset(POINT_ARR[0] - RECT_TMP_F.left, POINT_ARR[1] - RECT_TMP_F.top);
        }
    }

    public void set(MovementBounds bounds) {
        mBounds.set(bounds.mBounds);
        mRotation = bounds.mRotation;
        mPivotX = bounds.mPivotX;
        mPivotY = bounds.mPivotY;
    }


    public static void setupInitialMovement(State state, Settings settings) {
        state.get(MATRIX);
        Rect pos = getPositionWithGravity(MATRIX, settings);
        state.translateTo(pos.left, pos.top);
    }

    /**
     * Returns view position within the viewport area with gravity applied, not taking into account view's position
     * specified by matrix.
     */
    private static Rect getPositionWithGravity(Matrix matrix, Settings settings) {
        RECT_TMP_F.set(0, 0, settings.getViewW(), settings.getViewH());
        matrix.mapRect(RECT_TMP_F);
        final int w = Math.round(RECT_TMP_F.width());
        final int h = Math.round(RECT_TMP_F.height());

        // Calculating view position basing on gravity
        RECT_TMP.set(0, 0, settings.getViewportW(), settings.getViewportH());
        Gravity.apply(settings.getGravity(), w, h, RECT_TMP, RECT_POS);

        return RECT_POS;
    }

    public static Rect getMovementAreaWithGravity(Settings settings) {
        // Calculating movement area position basing on gravity
        RECT_TMP.set(0, 0, settings.getViewportW(), settings.getViewportH());
        Gravity.apply(settings.getGravity(), settings.getMovementAreaW(), settings.getMovementAreaH(),
                RECT_TMP, RECT_MOV_AREA);
        return RECT_MOV_AREA;
    }

}
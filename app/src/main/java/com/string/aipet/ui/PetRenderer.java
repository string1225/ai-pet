package com.string.aipet.ui;

import android.graphics.Canvas;
import com.string.aipet.model.PetState;

/** Register another renderer to add a species without changing the house or learning flow. */
public interface PetRenderer {
    void draw(Canvas canvas, PetState state, float x, float y, float time, float reaction);
}

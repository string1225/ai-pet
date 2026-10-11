package com.string.aipet.ui;

import com.string.aipet.model.PetRegistry;
import java.util.HashMap;
import java.util.Map;

public final class RendererRegistry {
    private static final Map<String, PetRenderer> RENDERERS = new HashMap<>();
    static { register(PetRegistry.SNAKE, new SnakeRenderer()); }
    private RendererRegistry() {}
    public static void register(String id, PetRenderer renderer) {
        PetRegistry.get(id);
        if (RENDERERS.containsKey(id)) throw new IllegalArgumentException("Duplicate renderer: " + id);
        RENDERERS.put(id, renderer);
    }
    public static PetRenderer get(String id) {
        PetRenderer renderer = RENDERERS.get(id);
        if (renderer == null) throw new IllegalStateException("Missing renderer: " + id);
        return renderer;
    }
}

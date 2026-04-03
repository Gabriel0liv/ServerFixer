package com.gabri.serverfixes.network;

public enum ContextTargetType {
    CONTAINER_SLOT(0),
    ENTITY(1),
    BLOCK_ENTITY(2);

    private final int id;

    ContextTargetType(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }

    public static ContextTargetType fromId(int id) {
        for (ContextTargetType value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        return CONTAINER_SLOT;
    }
}

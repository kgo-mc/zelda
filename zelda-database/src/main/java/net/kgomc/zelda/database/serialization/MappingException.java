package net.kgomc.zelda.database.serialization;

import javax.annotation.Nullable;

public class MappingException extends RuntimeException {
    public MappingException(String s, @Nullable Exception e) {
        super(s, e);
    }
}

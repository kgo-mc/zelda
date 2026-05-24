package net.kgomc.zelda.ui.annotation;

import java.lang.annotation.*;

/**
 * Fires on any click on the annotated slot code.
 * Method signature: {@code void method(ClickContext ctx)}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnClick {
    /** Slot code(s) this handler fires for. */
    String[] value();
}
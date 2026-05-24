package net.kgomc.zelda.ui.annotation;

import java.lang.annotation.*;

/** Fires on right-click only. Method: {@code void method(ClickContext ctx)} */
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
public @interface OnRightClick {
    String[] value();
}
package net.kgomc.zelda.ui.annotation;

import java.lang.annotation.*;

/** Fires on middle-click only. Method: {@code void method(ClickContext ctx)} */
@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
public @interface OnMiddleClick {
    String[] value();
}